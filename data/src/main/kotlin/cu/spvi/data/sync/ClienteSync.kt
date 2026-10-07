package cu.spvi.data.sync

import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.domain.model.CodigoVinculacion
import cu.spvi.domain.model.EstadoConexion
import cu.spvi.domain.model.ModoSincronizacion
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * P37: cliente de la app SECUNDARIA.
 *  - Automática: mantiene la conexión, envía cada venta al momento y sincroniza cuando la principal avisa de cambios
 *    (o cada [LATIDO_MS] como mínimo). Sin conexión reintenta con espera creciente (5 s → 60 s).
 *  - Manual: solo conecta al pedirlo (botón, abrir turno o una acción que necesita a la principal) y luego cierra.
 */
@OptIn(ExperimentalCoroutinesApi::class) // select { onTimeout }
@Singleton
class ClienteSync @Inject constructor(
    private val almacen: AlmacenSync,
    private val config: ConfigSync,
    private val red: RedLocal,
    private val clock: Clock,
    /** 0.25.0: recepción del APK que reparte la principal. */
    private val apkLocal: ApkLocal,
    private val info: cu.spvi.domain.model.InfoApp,
) {
    /** 0.25.0: cambios de ventas ya aplicados aquí, pendientes de confirmar a la principal (si se pierden, se reaplican: idempotente). */
    @Volatile private var cambiosAplicados: List<String> = emptyList()

    private val _apkPrincipal = MutableStateFlow<VersionApk?>(null)
    /** 0.25.0: APK más nuevo que ofrece la principal (null = ninguno). */
    val apkPrincipal: StateFlow<VersionApk?> = _apkPrincipal.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _conexion = MutableStateFlow<EstadoConexion>(EstadoConexion.Desconectada)
    val conexion: StateFlow<EstadoConexion> = _conexion.asStateFlow()

    /** El dueño quitó esta app (rechazo autenticado o mensaje en sesión). Lo atiende el repositorio: borra y vuelve a PRINCIPAL. */
    var alSerQuitada: (suspend () -> Unit)? = null

    private val conectando = Mutex()
    private val ronda = Mutex()
    private val disparos = Channel<Unit>(Channel.CONFLATED)
    /**
     * Sesión vigente. 0.21.7 (P-e, punto 1): quien ve un error cierra SOLO la sesión que usó (compare-and-set, ver
     * [cerrarSesion]); antes cerraba la que hubiera en ese momento, que podía ser una nueva y sana de otra corrutina.
     */
    private val actual = AtomicReference<SesionCliente?>(null)
    private var automatico: Job? = null

    // ---------------------------------------------------------------- modo automático

    @Synchronized
    fun iniciarAutomatico() {
        if (automatico?.isActive == true) return
        automatico = scope.launch {
            var espera = ESPERA_MIN_MS
            while (true) {
                when (sincronizar()) {
                    is AppResult.Ok -> {
                        espera = ESPERA_MIN_MS
                        select<Unit> {
                            disparos.onReceive { }
                            onTimeout(LATIDO_MS) { }
                        }
                    }
                    is AppResult.Err -> {
                        val d = config.secundariaActual() ?: break
                        if (d.modoSincronizacion != ModoSincronizacion.AUTOMATICA) break
                        select<Unit> {
                            disparos.onReceive { }
                            onTimeout(espera) { }
                        }
                        espera = (espera * 2).coerceAtMost(ESPERA_MAX_MS)
                    }
                }
            }
        }
    }

    @Synchronized
    fun detenerAutomatico() {
        automatico?.cancel()
        automatico = null
        cerrarSesion()
    }

    /** Hay algo nuevo que enviar (venta, turno): en modo automático se envía ya. */
    fun disparar() { disparos.trySend(Unit) }

    // ---------------------------------------------------------------- operaciones

    /** Una ronda completa: envía lo pendiente, recibe lo guardado y, si cambió, el catálogo. */
    suspend fun sincronizar(): AppResult<Unit> = ronda.withLock {
        val d = config.secundariaActual() ?: return@withLock AppResult.Err(AppError.NoEncontrado)
        val s = sesionAbierta(d) ?: return@withLock AppResult.Err(AppError.SinPrincipal(almacen.pendientes()))
        try {
            val lote = almacen.lotePendiente()
            val confirmar = cambiosAplicados
            val r = s.pedir { id ->
                Sincronizar(
                    id, lote, d.hash, telefono = d.telefono, solicitaCierre = d.cierreSolicitado,
                    cambiosAplicados = confirmar, versionCode = info.versionCode,
                    pideFondo = d.pideFondo, fondoUsado = d.fondoUsado,
                )
            } as? SincronizarOk ?: throw ProtocoloException("respuesta inesperada")
            when (val a = almacen.aplicarEnSecundaria(r.recibidos, r.instantanea)) {
                is AppResult.Err -> return@withLock a
                is AppResult.Ok -> Unit
            }
            // 0.25.0: ventas anuladas/corregidas en la principal; se confirman en la próxima ronda (que se pide ya).
            val hechos = if (r.cambiosVentas.isEmpty()) emptyList()
            else (almacen.aplicarCambios(r.cambiosVentas) as? AppResult.Ok)?.value.orEmpty()
            cambiosAplicados = hechos
            _apkPrincipal.value = r.apk?.takeIf { apkLocal.masNuevo(it) }
            config.editarSecundaria { x ->
                val inst = r.instantanea
                val base = x.copy(
                    ultimaSincronizacion = clock.now().toEpochMilli(), cierrePendiente = r.cerrarTurno,
                    // 0.21.0 (C6): aprobada (llega cerrarTurno) o rechazada: la solicitud ya está resuelta.
                    cierreSolicitado = x.cierreSolicitado && !r.cerrarTurno && !r.cierreRechazado,
                    avisoRechazo = x.avisoRechazo || (r.cierreRechazado && x.cierreSolicitado),
                ).conFondo(r)
                if (inst == null) base else base.copy(
                    hash = r.hash, nombreNegocio = inst.nombreNegocio, nombreEmpleado = inst.empleado.nombre,
                    permisos = inst.empleado.permisos, licencia = inst.licencia,
                )
            }
            if (hechos.isNotEmpty() && d.modoSincronizacion == ModoSincronizacion.AUTOMATICA) disparar()
            if (d.modoSincronizacion == ModoSincronizacion.MANUAL && hechos.isEmpty()) cerrarSesion(s)
            AppResult.Ok(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cerrarSesion(s)
            AppResult.Err(AppError.SinPrincipal(almacen.pendientes()))
        }
    }

    /**
     * 0.25.0: descarga por bloques el APK que ofrece la principal y comprueba su SHA-256. Reanudable: si se corta, sigue
     * desde lo ya recibido (mismo APK). [progreso] (bytes, total). Error SinPrincipal sin conexión; Validacion("sha256")
     * si no coincide (el archivo se borra).
     */
    suspend fun descargarApk(progreso: (Long, Long) -> Unit): AppResult<java.io.File> = withContext(Dispatchers.IO) {
        val v = _apkPrincipal.value ?: return@withContext AppResult.Err(AppError.NoEncontrado)
        val d = config.secundariaActual() ?: return@withContext AppResult.Err(AppError.NoEncontrado)
        val destino = apkLocal.recepcion
        val marca = java.io.File(destino.parentFile, "recibida.sha256")
        destino.parentFile?.mkdirs()
        if (!marca.isFile || marca.readText() != v.sha256 || destino.length() > v.bytes) { destino.delete(); marca.writeText(v.sha256) }
        try {
            java.io.RandomAccessFile(destino, "rw").use { f ->
                var hecho = f.length()
                f.seek(hecho)
                while (hecho < v.bytes) {
                    val s = sesionAbierta(d) ?: return@withContext AppResult.Err(AppError.SinPrincipal(0))
                    val b = s.pedir { id -> PedirApk(id, v.sha256, hecho) } as? BloqueApk ?: throw ProtocoloException("respuesta inesperada")
                    if (b.error != null || b.desde != hecho) {
                        destino.delete(); marca.delete(); _apkPrincipal.value = null
                        return@withContext AppResult.Err(AppError.NoEncontrado)
                    }
                    val datos = java.util.Base64.getDecoder().decode(b.datos)
                    if (datos.isEmpty()) throw ProtocoloException("bloque vacío")
                    f.write(datos)
                    hecho += datos.size
                    progreso(hecho, v.bytes)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext AppResult.Err(AppError.SinPrincipal(0))
        }
        if (!ApkLocal.sha256(destino).equals(v.sha256, ignoreCase = true)) {
            destino.delete(); marca.delete()
            return@withContext AppResult.Err(AppError.Validacion("sha256", AppError.Regla.FORMATO))
        }
        AppResult.Ok(destino)
    }

    /** Acción remota sobre el catálogo de la principal. Ok(valor) = id creado, existencia… */
    suspend fun comando(accion: Accion): AppResult<Long?> {
        val d = config.secundariaActual() ?: return AppResult.Err(AppError.NoEncontrado)
        val r = enviarComando(d, accion) ?: return AppResult.Err(AppError.SinPrincipal(almacen.pendientes()))
        val error = r.error
        if (error != null) return AppResult.Err(EjecutorComandos.error(error))
        // Trae el catálogo ya cambiado para que la pantalla lo muestre al volver.
        sincronizar()
        return AppResult.Ok(r.valor)
    }

    /**
     * Primera conexión con el QR: acuerdo de clave ECDH autenticado con el token. Devuelve los datos para guardar
     * (todavía no guarda nada: quien llama decide cuándo borrar los datos del teléfono).
     */
    suspend fun vincular(c: CodigoVinculacion): AppResult<DatosSecundaria> = withContext(Dispatchers.IO) {
        val socket = conectarA(c.direcciones, c.puerto) ?: red.buscar(c.negocioId)?.let { (h, p) -> conectarA(listOf(h), p) }
            ?: return@withContext AppResult.Err(AppError.SinPrincipal())
        try {
            socket.soTimeout = 15_000
            val input = BufferedInputStream(socket.getInputStream())
            val out = BufferedOutputStream(socket.getOutputStream())
            val par = CriptoSync.parNuevo()
            val pubC = par.public.encoded
            Saludos.enviar(out, Vincular(negocio = c.negocioId, empleado = c.empleadoId, pub = CriptoSync.enc(pubC),
                mac = CriptoSync.enc(CriptoSync.macVinculoSecundaria(c.token, pubC))))
            when (val r = Saludos.recibir(input)) {
                is VincularOk -> {
                    val pubS = CriptoSync.dec(r.pub)
                    if (!CriptoSync.iguales(CriptoSync.macVinculoPrincipal(c.token, pubS, pubC), CriptoSync.dec(r.mac))) {
                        return@withContext AppResult.Err(AppError.VinculacionRechazada(TEXTO_CODIGO_INVALIDO))
                    }
                    val clave = CriptoSync.claveEmpleado(par, pubS, c.token)
                    val datos = DatosSecundaria(
                        negocioId = c.negocioId, nombreNegocio = r.nombreNegocio.ifBlank { c.nombreNegocio },
                        empleadoId = c.empleadoId, nombreEmpleado = c.nombreEmpleado, clave = CriptoSync.enc(clave),
                        direcciones = listOfNotNull(socket.inetAddress?.hostAddress).ifEmpty { c.direcciones }, puerto = socket.port,
                    )
                    clave.fill(0)
                    AppResult.Ok(datos)
                }
                is Rechazo -> AppResult.Err(AppError.VinculacionRechazada(textoRechazo(r.motivo)))
                else -> AppResult.Err(AppError.VinculacionRechazada(TEXTO_CODIGO_INVALIDO))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Err(AppError.SinPrincipal())
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * 0.21.7 (P-e, punto 2): cada acción lleva una clave única. Si su respuesta se pierde (conexión caída o sin respuesta)
     * y la principal recuerda los comandos ([HolaOk.comandosUnicos]), se reenvía UNA vez con la misma clave por una
     * sesión nueva: si ya se había aplicado, la principal devuelve el resultado guardado sin repetirlo. Con una principal
     * anterior no se reenvía (podría aplicarse dos veces) y se informa del fallo, como antes. null = sin principal.
     */
    private suspend fun enviarComando(d: DatosSecundaria, accion: Accion): ResultadoComando? {
        val clave = UUID.randomUUID().toString()
        var intento = 0
        while (true) {
            val s = sesionAbierta(d) ?: return null
            try {
                return s.pedir { id -> Comando(id, accion, clave.takeIf { s.comandosUnicos }) } as? ResultadoComando
                    ?: throw ProtocoloException("respuesta inesperada")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cerrarSesion(s)
                if (!s.comandosUnicos || ++intento > REENVIOS_COMANDO) return null
            }
        }
    }

    /** Cierre intencionado (detener el modo automático, desvincular): cierra la sesión vigente, sea cual sea. */
    fun cerrarSesion() {
        actual.getAndSet(null)?.cerrar()
        _conexion.value = EstadoConexion.Desconectada
    }

    /** Tras un error en [s]: la cierra, y solo si sigue siendo la vigente la olvida y cambia el estado (P-e). */
    private fun cerrarSesion(s: SesionCliente) {
        s.cerrar()
        if (actual.compareAndSet(s, null)) _conexion.value = EstadoConexion.Desconectada
    }

    // ---------------------------------------------------------------- conexión

    private suspend fun sesionAbierta(d: DatosSecundaria): SesionCliente? = conectando.withLock {
        actual.get()?.takeIf { it.viva }?.let { return@withLock it }
        _conexion.value = EstadoConexion.Conectando
        val nueva = withContext(Dispatchers.IO) { abrir(d) }
        actual.set(nueva)
        _conexion.value = if (nueva != null) EstadoConexion.Conectada(clock.now())
        else EstadoConexion.SinConexion(TEXTO_SIN_CONEXION)
        nueva
    }

    private suspend fun abrir(d: DatosSecundaria): SesionCliente? {
        var socket = conectarA(d.direcciones, d.puerto)
        if (socket == null) {
            // ¿La principal cambió de dirección (otra wifi, zona wifi reiniciada)? Se busca por nombre en la red.
            val (h, p) = red.buscar(d.negocioId) ?: return null
            socket = conectarA(listOf(h), p) ?: return null
            config.editarSecundaria { it.copy(direcciones = listOf(h), puerto = p) }
        }
        return try {
            socket.soTimeout = ServidorSync.LECTURA_MS
            val input = BufferedInputStream(socket.getInputStream())
            val out = BufferedOutputStream(socket.getOutputStream())
            val nonceC = CriptoSync.aleatorio(CriptoSync.NONCE_BYTES)
            Saludos.enviar(out, Hola(negocio = d.negocioId, empleado = d.empleadoId, nonce = CriptoSync.enc(nonceC)))
            val clave = CriptoSync.dec(d.clave)
            try {
                when (val r = Saludos.recibir(input)) {
                    is HolaOk -> {
                        val canal = CanalCifrado.paraSecundaria(input, out, CriptoSync.clavesSesion(clave, nonceC, CriptoSync.dec(r.nonce)))
                        SesionCliente(socket, canal, r.comandosUnicos).also { it.iniciar() }
                    }
                    is Rechazo -> {
                        // Solo un rechazo firmado con NUESTRA clave borra los datos (nadie en la red puede falsificarlo).
                        val mac = r.mac
                        if (r.motivo == Rechazo.QUITADA && mac != null &&
                            CriptoSync.iguales(CriptoSync.macRechazo(clave, nonceC, Rechazo.QUITADA), CriptoSync.dec(mac))
                        ) {
                            scope.launch { alSerQuitada?.invoke() }
                        }
                        socket.close()
                        null
                    }
                    else -> { socket.close(); null }
                }
            } finally {
                clave.fill(0) // 0.21.6: en todos los casos (antes quedaba en memoria si la respuesta fallaba)
            }
        } catch (e: CancellationException) {
            runCatching { socket.close() }
            throw e
        } catch (e: Exception) {
            runCatching { socket.close() }
            null
        }
    }

    private fun conectarA(hosts: List<String>, puerto: Int): Socket? {
        for (h in hosts) {
            val s = Socket()
            try {
                s.connect(InetSocketAddress(h, puerto), CONECTAR_MS)
                s.tcpNoDelay = true
                return s
            } catch (e: IOException) {
                runCatching { s.close() }
            }
        }
        return null
    }

    /** Una conexión abierta: una corrutina lee y reparte respuestas por id; las escrituras van en serie. */
    private inner class SesionCliente(
        private val socket: Socket,
        private val canal: CanalCifrado,
        /** La principal recuerda los comandos por clave (0.21.7): se pueden reenviar sin duplicarlos. */
        val comandosUnicos: Boolean,
    ) {
        private val escritura = Mutex()
        private val esperando = ConcurrentHashMap<Long, CompletableDeferred<Mensaje>>()
        private val ids = AtomicLong(1)
        private var lector: Job? = null
        private var latido: Job? = null
        @Volatile var viva = true
            private set

        fun iniciar() {
            lector = scope.launch {
                try {
                    while (true) {
                        when (val m = canal.recibir()) {
                            is SincronizarOk -> esperando.remove(m.id)?.complete(m)
                            is ResultadoComando -> esperando.remove(m.id)?.complete(m)
                            is Pong -> esperando.remove(m.id)?.complete(m)
                            is BloqueApk -> esperando.remove(m.id)?.complete(m)
                            Aviso -> disparar()
                            Quitada -> { alSerQuitada?.invoke(); break }
                            else -> Unit
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // conexión perdida
                } finally {
                    cerrar()
                    if (actual.compareAndSet(this@SesionCliente, null)) {
                        _conexion.value = EstadoConexion.SinConexion(TEXTO_SIN_CONEXION)
                        disparar() // en automático, reintenta
                    }
                }
            }
            latido = scope.launch {
                while (viva) {
                    delay(LATIDO_MS)
                    try { pedir { Ping(it) } } catch (e: CancellationException) { throw e } catch (e: Exception) { /* la lectura detecta la caída */ }
                }
            }
        }

        suspend fun pedir(crear: (Long) -> Mensaje): Mensaje {
            val id = ids.getAndIncrement()
            val espera = CompletableDeferred<Mensaje>()
            esperando[id] = espera
            try {
                escritura.withLock { withContext(Dispatchers.IO) { canal.enviar(crear(id)) } }
                // 0.21.6: sin respuesta es un fallo de E/S, no una cancelación. Con withTimeout salía una
                // TimeoutCancellationException que `sincronizar` relanzaba como cancelación: la sesión quedaba abierta
                // y el bucle automático terminaba sin reintentar.
                return withTimeoutOrNull(RESPUESTA_MS) { espera.await() } ?: throw IOException("sin respuesta de la principal")
            } finally {
                esperando.remove(id)
            }
        }

        fun cerrar() {
            if (!viva) return
            viva = false
            runCatching { socket.close() }
            esperando.values.forEach { it.completeExceptionally(IOException("conexión cerrada")) }
            esperando.clear()
            latido?.cancel()
        }
    }

    companion object {
        const val CONECTAR_MS = 3_000
        const val RESPUESTA_MS = 60_000L
        /** 0.21.7 (P-e): reenvíos de un comando sin respuesta (solo con una principal que recuerda los comandos). */
        const val REENVIOS_COMANDO = 1
        const val LATIDO_MS = 30_000L
        const val ESPERA_MIN_MS = 5_000L
        const val ESPERA_MAX_MS = 60_000L

        const val TEXTO_SIN_CONEXION = "No se encuentra la app principal en esta red"
        const val TEXTO_CODIGO_INVALIDO = "Este código no es válido. Pide al dueño que genere uno nuevo."

        fun textoRechazo(motivo: String): String = when (motivo) {
            Rechazo.CODIGO_VENCIDO -> "El código venció (dura 10 minutos). Pide al dueño que genere uno nuevo."
            Rechazo.OTRO_NEGOCIO -> "Ese código es de otra app principal."
            Rechazo.VERSION -> "Las dos apps deben tener la misma versión de SPVI. Actualiza la más antigua."
            Rechazo.LIMITE -> "La app principal ya tiene el máximo de apps secundarias."
            Rechazo.QUITADA -> "El dueño quitó esta app de su lista."
            else -> TEXTO_CODIGO_INVALIDO
        }
    }
}

/**
 * 0.26.0 (P73 §4): aplica el fondo que envía la principal. Un fondo cuyo identificador ya se usó al abrir un turno no
 * vuelve a ofrecerse (la principal aún no recibió el aviso); el identificador usado se olvida cuando la principal deja
 * de enviarlo. Si llega un fondo, la petición «Pedir fondo» queda resuelta.
 */
internal fun DatosSecundaria.conFondo(r: SincronizarOk): DatosSecundaria {
    val f = r.fondo
    val nuevo = f?.takeIf { it.token != fondoUsado }
    return copy(
        principalAsignaFondo = r.asignaFondos,
        fondoCent = nuevo?.cent, fondoToken = nuevo?.token,
        fondoUsado = fondoUsado?.takeIf { f != null && f.token == it },
        pideFondo = pideFondo && nuevo == null && r.asignaFondos,
    )
}
