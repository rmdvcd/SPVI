package cu.spvi.data.sync

import cu.spvi.core.result.runCatchingCancelable
import androidx.room.InvalidationTracker
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.db.entity.EmpleadoEntity
import cu.spvi.domain.model.EstadoPrincipal
import cu.spvi.domain.model.LicenciaPrincipal
import cu.spvi.domain.repository.LicenciaRepository
import cu.spvi.domain.repository.PerfilRepository
import cu.spvi.domain.usecase.UsuarioActual
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import cu.spvi.domain.model.TipoApp

/**
 * P37: servidor de la app PRINCIPAL en la red local (TCP, puerto [PUERTO] o el siguiente libre).
 * Vive mientras el proceso de la app viva; desde 0.19.2 el servicio en primer plano ServicioSync (:app) mantiene ese proceso mientras haya turnos abiertos. Si la principal se
 * cierra, las secundarias siguen vendiendo hasta cerrar su turno y envían todo al volver a conectar.
 *
 * Cada conexión: saludo → (vinculación | sesión cifrada). En sesión atiende Sincronizar, Comando y Ping, y avisa a
 * todas las secundarias conectadas cuando cambia el catálogo, la licencia o sus permisos.
 */
@Singleton
class ServidorSync @Inject constructor(
    private val db: SpviDatabase,
    private val almacen: AlmacenSync,
    private val ejecutor: EjecutorComandos,
    private val config: ConfigSync,
    private val red: RedLocal,
    private val licencia: LicenciaRepository,
    private val perfil: PerfilRepository,
    private val clock: Clock,
    /** 0.25.0: APK de actualización que se reparte a las secundarias. */
    private val apk: ApkLocal,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _estado = MutableStateFlow(EstadoPrincipal())
    val estado: StateFlow<EstadoPrincipal> = _estado.asStateFlow()

    private val sesiones = ConcurrentHashMap<Long, Sesion>()
    /** Límite global: cubre handshakes y sesiones ya autenticadas; las conexiones sobrantes se cierran al aceptar. */
    private val conexiones = Semaphore(MAX_CONEXIONES)
    private val vinculando = Mutex()
    private val avisos = Channel<Unit>(Channel.CONFLATED)
    private var trabajo: Job? = null
    @Volatile private var socket: ServerSocket? = null

    private class Sesion(val empleadoId: Long, val socket: Socket, val canal: CanalCifrado) {
        val escritura = Mutex()
        suspend fun enviar(m: Mensaje) = escritura.withLock { withContext(Dispatchers.IO) { canal.enviar(m) } }
        fun cerrar() { runCatching { socket.close() }; canal.borrarClaves() }
    }

    private val observador = object : InvalidationTracker.Observer(arrayOf(
        "producto", "insumo", "servicio", "receta_linea", "servicio_insumo", "preajuste", "preajuste_producto",
        "perfil", "tarjeta", "telefono",
    )) {
        override fun onInvalidated(tables: Set<String>) { avisar() }
    }

    /** Idempotente. Lo llama ArranqueSync cuando esta app es PRINCIPAL y tiene (o está agregando) secundarias. */
    @Synchronized
    fun iniciar() {
        if (trabajo?.isActive == true) return
        trabajo = scope.launch {
            val ss = abrirSocket() ?: run { _estado.update { it.copy(escuchando = false) }; return@launch }
            socket = ss
            db.invalidationTracker.addObserver(observador)
            red.anunciar(config.negocioId(), ss.localPort)
            _estado.update { it.copy(escuchando = true, puerto = ss.localPort, direcciones = red.direcciones()) }
            launch { refrescarDirecciones() }
            launch { repartirAvisos() }
            launch { licencia.snapshot.map { it?.estado }.distinctUntilChanged().collect { avisar() } }
            try {
                while (isActive) {
                    val s = withContext(Dispatchers.IO) { ss.accept() }
                    if (!conexiones.tryAcquire()) {
                        runCatching { s.close() }
                        continue
                    }
                    launch {
                        try { atender(s) } finally { conexiones.release() }
                    }
                }
            } catch (e: IOException) {
                // socket cerrado al detener
            } finally {
                db.invalidationTracker.removeObserver(observador)
                red.dejarDeAnunciar()
                runCatching { ss.close() }
                sesiones.values.forEach { it.cerrar() }
                sesiones.clear()
                _estado.value = EstadoPrincipal()
            }
        }
    }

    @Synchronized
    fun detener() {
        runCatching { socket?.close() }
        socket = null
        trabajo?.cancel()
        trabajo = null
    }

    /** Algo cambió (catálogo, permisos, licencia): las secundarias conectadas sincronizan. */
    fun avisar() { avisos.trySend(Unit) }

    /** El dueño quitó esta secundaria: si está conectada se le dice ahora; si no, al volver a conectar. */
    fun expulsar(empleadoId: Long) {
        val s = sesiones.remove(empleadoId) ?: return
        scope.launch {
            runCatching { s.enviar(Quitada) }
            s.cerrar()
            _estado.update { it.copy(conectadas = it.conectadas - empleadoId) }
        }
    }

    private fun abrirSocket(): ServerSocket? {
        for (p in PUERTO until PUERTO + 10) {
            try {
                return ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p)) }
            } catch (e: IOException) {
                continue
            }
        }
        return null
    }

    private suspend fun refrescarDirecciones() {
        while (true) {
            delay(10_000)
            val d = red.direcciones()
            if (d != _estado.value.direcciones) _estado.update { it.copy(direcciones = d) }
        }
    }

    private suspend fun repartirAvisos() {
        for (u in avisos) {
            delay(800) // agrupa ráfagas (una venta toca varias tablas)
            sesiones.values.toList().forEach { s -> runCatchingCancelable { s.enviar(Aviso) }.onFailure { s.cerrar() } }
        }
    }

    // ---------------------------------------------------------------- conexión

    private suspend fun atender(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            // Handshake corto para que una conexión que no envía nada no ocupe una plaza durante 90 s.
            socket.soTimeout = HANDSHAKE_MS
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            when (val s = withContext(Dispatchers.IO) { Saludos.recibir(input) }) {
                is Vincular -> { vincular(s, output); socket.close() }
                is Hola -> sesion(s, socket, input, output)
                else -> socket.close()
            }
        } catch (e: CancellationException) {
            runCatching { socket.close() }
            throw e
        } catch (e: Exception) {
            runCatching { socket.close() }
        }
    }

    private suspend fun vincular(v: Vincular, out: OutputStream) = vinculando.withLock {
        val rechazar = { motivo: String -> Saludos.enviar(out, Rechazo(motivo)) }
        if (v.v != VERSION_PROTOCOLO) return@withLock rechazar(Rechazo.VERSION)
        // 0.21.0 (C5): una sola principal por negocio; una secundaria nunca acepta vínculos.
        if (config.tipo.first() != TipoApp.PRINCIPAL) return@withLock rechazar(Rechazo.DESCONOCIDA)
        if (v.negocio != config.negocioId()) return@withLock rechazar(Rechazo.OTRO_NEGOCIO)
        val e = db.syncDao().empleado(v.empleado)?.takeIf { it.activo } ?: return@withLock rechazar(Rechazo.CODIGO_INVALIDO)
        val token = e.codigoToken?.let(CriptoSync::dec) ?: return@withLock rechazar(Rechazo.CODIGO_INVALIDO)
        val vence = e.codigoVence ?: 0
        if (clock.now().toEpochMilli() >= vence) return@withLock rechazar(Rechazo.CODIGO_VENCIDO)
        val pubC = CriptoSync.dec(v.pub)
        if (!CriptoSync.iguales(CriptoSync.macVinculoSecundaria(token, pubC), CriptoSync.dec(v.mac))) {
            return@withLock rechazar(Rechazo.CODIGO_INVALIDO)
        }
        val par = CriptoSync.parNuevo()
        val pubS = par.public.encoded
        val clave = CriptoSync.claveEmpleado(par, pubC, token)
        // El token se gasta aquí: un segundo intento con el mismo QR ya no vale.
        db.syncDao().actualizarEmpleado(
            e.copy(clave = CriptoSync.enc(clave), codigoToken = null, codigoVence = null, vinculadoEn = clock.now().toEpochMilli()),
        )
        clave.fill(0)
        withContext(Dispatchers.IO) {
            Saludos.enviar(out, VincularOk(CriptoSync.enc(pubS), CriptoSync.enc(CriptoSync.macVinculoPrincipal(token, pubS, pubC)), nombreNegocio()))
        }
    }

    private suspend fun sesion(h: Hola, socket: Socket, input: InputStream, out: OutputStream) {
        val nonceC = CriptoSync.dec(h.nonce)
        if (h.v != VERSION_PROTOCOLO) return Saludos.enviar(out, Rechazo(Rechazo.VERSION)).also { socket.close() }
        if (config.tipo.first() != TipoApp.PRINCIPAL) return Saludos.enviar(out, Rechazo(Rechazo.DESCONOCIDA)).also { socket.close() } // C5
        if (nonceC.size != CriptoSync.NONCE_BYTES) return socket.close()
        if (h.negocio != config.negocioId()) return Saludos.enviar(out, Rechazo(Rechazo.OTRO_NEGOCIO)).also { socket.close() }
        val e = db.syncDao().empleado(h.empleado)
        val clave = e?.clave?.let(CriptoSync::dec)
        if (e == null || clave == null) return Saludos.enviar(out, Rechazo(Rechazo.DESCONOCIDA)).also { socket.close() }
        if (!e.activo) {
            val mac = CriptoSync.enc(CriptoSync.macRechazo(clave, nonceC, Rechazo.QUITADA))
            return Saludos.enviar(out, Rechazo(Rechazo.QUITADA, mac)).also { socket.close() }
        }
        val nonceS = CriptoSync.aleatorio(CriptoSync.NONCE_BYTES)
        withContext(Dispatchers.IO) { Saludos.enviar(out, HolaOk(CriptoSync.enc(nonceS), comandosUnicos = true)) }
        val canal = CanalCifrado.paraPrincipal(input, out, CriptoSync.clavesSesion(clave, nonceC, nonceS))
        clave.fill(0)

        // No se sustituye una sesión existente hasta comprobar el primer mensaje GCM. Un saludo falso o
        // una conexión que se queda colgada no puede expulsar al empleado que ya está sincronizando.
        val primero = try {
            withContext(Dispatchers.IO) { canal.recibir() }
        } catch (e: CancellationException) {
            canal.borrarClaves()
            throw e
        } catch (e: Exception) {
            canal.borrarClaves()
            throw e
        }
        socket.soTimeout = LECTURA_MS
        val s = Sesion(e.id, socket, canal)
        sesiones.put(e.id, s)?.takeIf { it !== s }?.cerrar()
        _estado.update { it.copy(conectadas = it.conectadas + e.id) }
        try {
            var seguir = procesarMensaje(e, s, primero)
            while (seguir) {
                val m = withContext(Dispatchers.IO) { canal.recibir() }
                seguir = procesarMensaje(e, s, m)
            }
        } finally {
            s.cerrar()
            if (sesiones.remove(e.id, s)) _estado.update { it.copy(conectadas = it.conectadas - e.id) }
        }
    }

    /** Procesa solo mensajes de una sesión cuyo primer mensaje cifrado ya autenticó el canal. */
    private suspend fun procesarMensaje(e: EmpleadoEntity, s: Sesion, m: Mensaje): Boolean {
        val actual = db.syncDao().empleado(e.id)
        if (actual == null || !actual.activo) {
            runCatching { s.enviar(Quitada) }
            return false
        }
        when (m) {
            is Sincronizar -> {
                val respuesta = responder(actual, m) ?: return false
                s.enviar(respuesta)
            }
            is Comando -> s.enviar(ejecutor.resultado(m, actual))
            is Ping -> s.enviar(Pong(m.id))
            is PedirApk -> s.enviar(withContext(Dispatchers.IO) { apk.bloque(m.id, m.sha256, m.desde) })
            else -> Unit
        }
        return true
    }

    /** null = error al guardar: se corta la conexión y la secundaria reintenta (nada se marcó como recibido). */
    private suspend fun responder(e: EmpleadoEntity, m: Sincronizar): SincronizarOk? {
        val recibidos = when (val r = almacen.aplicarLote(e.id, m.lote)) {
            is AppResult.Ok -> r.value
            is AppResult.Err -> return null
        }
        // 0.25.0: lo que la secundaria ya aplicó deja de enviarse; lo demás va (o vuelve a ir) en esta respuesta.
        runCatchingCancelable { almacen.cambiosConfirmados(e.id, m.cambiosAplicados) }
        val cambios = runCatchingCancelable { almacen.cambiosPara(e.id) }.getOrDefault(emptyList())
        // 0.21.0 (C2): teléfono del empleado (solo si es válido y cambió).
        m.telefono?.trim()?.takeIf { it != e.telefono && cu.spvi.domain.model.Vinculacion.telefonoValido(it) }
            ?.let { db.syncDao().cambiarTelefono(e.id, it) }
        val rechazado = solicitudCierre(e, m.solicitaCierre)
        val fondo = fondoDe(e, m)
        val lic = LicenciaPrincipal.de(
            licencia.snapshot.value?.estado ?: cu.spvi.licencia.LicenseState.TrialExpired, clock.now(),
        ).aDto()
        val inst = when (val r = almacen.instantanea(e, nombreNegocio(), lic)) {
            is AppResult.Ok -> r.value
            is AppResult.Err -> return null
        }
        val hash = AlmacenSync.hash(inst)
        return SincronizarOk(
            m.id, recibidos, hash, inst.takeIf { hash != m.hash }, cerrarTurno = cierrePendiente(e), cierreRechazado = rechazado,
            cambiosVentas = cambios, apk = runCatching { apk.ofrecerA(m.versionCode) }.getOrNull(),
            asignaFondos = true, fondo = fondo,
        )
    }

    /**
     * 0.26.0 (P73 §4): el fondo de caja de cada turno de una secundaria lo asigna el dueño. Gasta el fondo con el que la
     * secundaria abrió su turno ([Sincronizar.fondoUsado]), anota o retira su petición «Pedir fondo» y guarda su
     * versionCode (para avisar «actualiza la app» a las 0.25.x, que siguen escribiendo su propio fondo). Devuelve el
     * fondo asignado y sin usar.
     */
    private suspend fun fondoDe(e: EmpleadoEntity, m: Sincronizar): FondoAsignado? {
        val dao = db.syncDao()
        if (m.versionCode != e.versionCode) dao.cambiarVersion(e.id, m.versionCode)
        m.fondoUsado?.let { dao.fondoGastado(e.id, it) }
        var x = dao.empleado(e.id) ?: return null
        val conTurno = db.turnoDao().activoDeEmpleado(e.id) != null
        if (m.pideFondo && x.fondoAsignadoCent == null && !conTurno) {
            if (x.aperturaSolicitadaEn == null) dao.marcarAperturaSolicitada(e.id, clock.now().toEpochMilli())
        } else if (x.aperturaSolicitadaEn != null) {
            dao.marcarAperturaSolicitada(e.id, null)
        }
        x = dao.empleado(e.id) ?: return null
        val cent = x.fondoAsignadoCent ?: return null
        return FondoAsignado(cent, x.fondoAsignadoEn ?: return null)
    }

    /**
     * 0.21.0 (C6): solicitud de cierre del empleado. Devuelve true si hay un rechazo que avisarle (y lo da por avisado).
     * Nada se cierra aquí: solo el dueño lo aprueba (y entonces va por [cierrePendiente]).
     */
    private suspend fun solicitudCierre(e: EmpleadoEntity, solicita: Boolean): Boolean {
        val dao = db.syncDao()
        if (e.cierrePedidoPorEmpleadoEn == PrincipalRepositoryImpl.RECHAZO_PENDIENTE) {
            dao.marcarSolicitudCierre(e.id, null)
            return true
        }
        if (solicita && e.cierreSolicitadoEn == null && db.turnoDao().activoDeEmpleado(e.id) != null) {
            dao.registrarSolicitudCierre(e.id, clock.now().toEpochMilli())
        } else if (!solicita && e.cierrePedidoPorEmpleadoEn != null) {
            dao.marcarSolicitudCierre(e.id, null) // la secundaria ya no la mantiene (p. ej. su turno se cerró)
        }
        return false
    }

    /**
     * 0.20.0 (H5): ¿hay que pedirle a esta app que cierre el turno? Se mira DESPUÉS de aplicar el lote: si ya llegó su
     * turno cerrado (o no tiene ninguno abierto), la petición está cumplida y se borra.
     */
    private suspend fun cierrePendiente(e: EmpleadoEntity): Boolean {
        if (e.cierreSolicitadoEn == null) return false
        if (db.turnoDao().activoDeEmpleado(e.id) != null) return true
        db.syncDao().olvidarCierre(e.id)
        return false
    }

    private suspend fun nombreNegocio(): String = UsuarioActual.nombreDe(perfil.perfil.first())

    companion object {
        const val PUERTO = 47_811
        /** Hay como máximo diez secundarias vinculadas; se deja margen para reconexiones concurrentes. */
        const val MAX_CONEXIONES = 12
        const val HANDSHAKE_MS = 10_000
        /** La secundaria hace ping cada 30 s; sin noticias en 90 s se da la conexión por perdida. */
        const val LECTURA_MS = 90_000
    }
}
