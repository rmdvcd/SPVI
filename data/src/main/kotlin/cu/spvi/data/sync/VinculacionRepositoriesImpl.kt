package cu.spvi.data.sync

import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.db.entity.EmpleadoEntity
import cu.spvi.data.di.IoDispatcher
import cu.spvi.domain.model.CodigoVinculacion
import cu.spvi.domain.model.Empleado
import cu.spvi.domain.model.EstadoPrincipal
import cu.spvi.domain.model.EstadoSecundaria
import cu.spvi.domain.model.LicenciaPrincipal
import cu.spvi.domain.model.ModoSincronizacion
import cu.spvi.domain.model.PermisoEmpleado
import cu.spvi.domain.model.PermisosApp
import cu.spvi.domain.model.TipoApp
import cu.spvi.domain.model.Vinculacion
import cu.spvi.domain.repository.LicenciaRepository
import cu.spvi.domain.repository.MantenimientoRepository
import cu.spvi.domain.repository.PerfilRepository
import cu.spvi.domain.repository.PreferenciasRepository
import cu.spvi.domain.repository.PrincipalRepository
import cu.spvi.domain.repository.PuertaTurno
import cu.spvi.domain.repository.SecundariaRepository
import cu.spvi.domain.repository.TipoAppRepository
import cu.spvi.domain.usecase.UsuarioActual
import cu.spvi.licencia.LicenseState
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private fun EmpleadoEntity.toDomain() = Empleado(
    id = id, nombre = nombre, permisos = PermisoEmpleado.deNombres(permisos.split(',')),
    creadoEn = Instant.ofEpochMilli(creadoEn), vinculadoEn = vinculadoEn?.let(Instant::ofEpochMilli),
    ultimaSincronizacion = ultimaSincronizacion?.let(Instant::ofEpochMilli),
    codigoVence = codigoVence?.let(Instant::ofEpochMilli),
    tarjetaId = tarjetaId, telefonoId = telefonoId,
    cierreSolicitadoEn = cierreSolicitadoEn?.let(Instant::ofEpochMilli),
    telefono = telefono,
    // 0 = rechazo pendiente de avisar: para la principal ya no es una solicitud.
    cierrePedidoPorEmpleadoEn = cierrePedidoPorEmpleadoEn?.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
    fondoAsignado = fondoAsignadoCent?.let { cu.spvi.core.money.Cup(it) },
    aperturaSolicitadaEn = aperturaSolicitadaEn?.let(Instant::ofEpochMilli),
    versionCode = versionCode,
)

private fun Set<PermisoEmpleado>.csv() = map { it.name }.sorted().joinToString(",")

// ======================================================================================== tipo de app

@Singleton
class TipoAppRepositoryImpl @Inject constructor(
    private val config: ConfigSync,
    private val preferencias: PreferenciasRepository,
    @IoDispatcher io: CoroutineDispatcher,
) : TipoAppRepository {
    private val scope = CoroutineScope(SupervisorJob() + io)

    override val tipo: StateFlow<TipoApp> = config.tipo.stateIn(scope, SharingStarted.Eagerly, TipoApp.PRINCIPAL)

    /** 0.21.0 (C12): con los módulos del negocio (en la secundaria, los que mandó la principal). */
    override val permisos: Flow<PermisosApp> = combine(config.tipo, config.secundaria, preferencias.preferencias) { t, d, p ->
        if (t == TipoApp.PRINCIPAL || d == null) PermisosApp.PRINCIPAL.copy(modulos = p.modulos)
        else PermisosApp(TipoApp.SECUNDARIA, d.permisosEmpleado, p.modulos)
    }.distinctUntilChanged()

    override val licenciaPrincipal: Flow<LicenciaPrincipal?> = combine(config.tipo, config.secundaria) { t, d ->
        if (t == TipoApp.SECUNDARIA) d?.licenciaPrincipal ?: LicenciaPrincipal(LicenciaPrincipal.Clase.BLOQUEADA) else null
    }
}

// ======================================================================================== principal

@Singleton
class PrincipalRepositoryImpl @Inject constructor(
    private val db: SpviDatabase,
    private val config: ConfigSync,
    private val servidor: ServidorSync,
    private val red: RedLocal,
    private val licencia: LicenciaRepository,
    private val perfil: PerfilRepository,
    private val clock: Clock,
) : PrincipalRepository {
    private val sync get() = db.syncDao()

    override fun observarEmpleados(): Flow<List<Empleado>> =
        combine(sync.observarEmpleados(), sync.observarTurnosDeEmpleados()) { l, turnos ->
            val porEmpleado = turnos.associateBy { it.empleadoId }
            l.map { e ->
                val tu = porEmpleado[e.id]
                e.toDomain().copy(
                    turnoAbiertoDesde = tu?.let { Instant.ofEpochMilli(it.abiertoEn) },
                    arqueoTurno = tu?.fondoCent?.let { f ->
                        cu.spvi.domain.model.Arqueo(
                            cu.spvi.core.money.Cup(f), cu.spvi.core.money.Cup(tu.efectivoCent), cu.spvi.core.money.Cup(tu.entradasCent),
                            cu.spvi.core.money.Cup(tu.salidasCent), tu.contadoCent?.let { c -> cu.spvi.core.money.Cup(c) },
                        )
                    },
                )
            }
        }
    override val estado: StateFlow<EstadoPrincipal> get() = servidor.estado

    override suspend fun agregarEmpleado(nombre: String, permisos: Set<PermisoEmpleado>): AppResult<CodigoVinculacion> {
        licenciaPermite()?.let { return AppResult.Err(it) }
        noEsSecundaria()?.let { return AppResult.Err(it) }
        val actuales = sync.empleados()
        when (Vinculacion.validarNombre(nombre, actuales.map { it.nombre })) {
            Vinculacion.ErrorNombre.VACIO -> return AppResult.Err(AppError.Validacion("nombre", AppError.Regla.REQUERIDO))
            Vinculacion.ErrorNombre.LARGO -> return AppResult.Err(AppError.Validacion("nombre", AppError.Regla.RANGO))
            Vinculacion.ErrorNombre.REPETIDO -> return AppResult.Err(AppError.Duplicado("nombre"))
            null -> Unit
        }
        // 0.21.0 (C4): el tope lo pone la licencia (X secundarias pagadas; prueba = 5).
        if (!Vinculacion.puedeAgregar(actuales.size, limiteSecundarias())) return AppResult.Err(AppError.SinPermiso)
        val id = sync.insertarEmpleado(
            EmpleadoEntity(
                nombre = nombre.trim(), permisos = permisos.csv(), creadoEn = clock.now().toEpochMilli(),
                vinculadoEn = null, ultimaSincronizacion = null, clave = null, codigoToken = null, codigoVence = null, activo = true,
            ),
        )
        return nuevoCodigo(id)
    }

    override suspend fun nuevoCodigo(empleadoId: Long): AppResult<CodigoVinculacion> {
        licenciaPermite()?.let { return AppResult.Err(it) }
        noEsSecundaria()?.let { return AppResult.Err(it) }
        val e = sync.empleado(empleadoId)?.takeIf { it.activo } ?: return AppResult.Err(AppError.NoEncontrado)
        // 0.21.0 (C4): una ficha pendiente (p. ej. restaurada) solo se vincula si la licencia cubre otra secundaria.
        if (e.vinculadoEn == null && sync.empleados().count { it.vinculadoEn != null } >= limiteSecundarias()) {
            return AppResult.Err(AppError.SinPermiso)
        }
        servidor.iniciar()
        val est = withTimeoutOrNull(3_000) { servidor.estado.first { it.escuchando } }
            ?: return AppResult.Err(AppError.Red.SinConexion)
        val direcciones = red.direcciones().ifEmpty { est.direcciones }
        if (direcciones.isEmpty()) return AppResult.Err(AppError.Red.SinConexion)
        val token = CriptoSync.aleatorio(CodigoQr.TOKEN_BYTES)
        val vence = clock.now().plus(Vinculacion.VALIDEZ_CODIGO)
        // Un QR nuevo anula la clave anterior: el teléfono viejo deja de poder sincronizar.
        servidor.expulsar(e.id)
        sync.actualizarEmpleado(e.copy(clave = null, vinculadoEn = null, codigoToken = CriptoSync.enc(token), codigoVence = vence.toEpochMilli()))
        return AppResult.Ok(
            CodigoVinculacion(
                negocioId = config.negocioId(), nombreNegocio = UsuarioActual.nombreDe(perfil.perfil.first()),
                direcciones = direcciones, puerto = est.puerto, empleadoId = e.id, nombreEmpleado = e.nombre,
                token = token, venceEn = vence,
            ),
        )
    }

    override suspend fun cambiarPermisos(empleadoId: Long, permisos: Set<PermisoEmpleado>): AppResult<Unit> {
        val e = sync.empleado(empleadoId)?.takeIf { it.activo } ?: return AppResult.Err(AppError.NoEncontrado)
        sync.actualizarEmpleado(e.copy(permisos = permisos.csv()))
        servidor.avisar()
        return AppResult.Ok(Unit)
    }

    override suspend fun cambiarCobro(empleadoId: Long, tarjetaId: Long?, telefonoId: Long?): AppResult<Unit> {
        if (sync.cambiarCobro(empleadoId, tarjetaId, telefonoId) == 0) return AppResult.Err(AppError.NoEncontrado)
        servidor.avisar()
        return AppResult.Ok(Unit)
    }

    override suspend fun pedirCierre(empleadoId: Long): AppResult<Unit> {
        if (db.turnoDao().activoDeEmpleado(empleadoId) == null) return AppResult.Err(AppError.TurnoCerrado)
        if (sync.pedirCierre(empleadoId, clock.now().toEpochMilli()) == 0) return AppResult.Err(AppError.NoEncontrado)
        servidor.avisar()
        return AppResult.Ok(Unit)
    }

    override suspend fun aprobarCierre(empleadoId: Long): AppResult<Unit> {
        val r = pedirCierre(empleadoId)
        if (r is AppResult.Ok) sync.marcarSolicitudCierre(empleadoId, null) // la petición del dueño toma el relevo
        return r
    }

    override suspend fun rechazarCierre(empleadoId: Long): AppResult<Unit> {
        val e = sync.empleado(empleadoId)?.takeIf { it.activo } ?: return AppResult.Err(AppError.NoEncontrado)
        if (e.cierrePedidoPorEmpleadoEn == null) return AppResult.Ok(Unit)
        sync.marcarSolicitudCierre(empleadoId, RECHAZO_PENDIENTE)
        servidor.avisar()
        return AppResult.Ok(Unit)
    }

    override suspend fun asignarFondo(empleadoId: Long, fondo: cu.spvi.core.money.Cup): AppResult<Unit> {
        if (fondo.isNegative) return AppResult.Err(AppError.Validacion("fondo", AppError.Regla.RANGO))
        if (sync.asignarFondo(empleadoId, fondo.centavos, clock.now().toEpochMilli()) == 0) return AppResult.Err(AppError.NoEncontrado)
        servidor.avisar()
        return AppResult.Ok(Unit)
    }

    override suspend fun fondoSugerido(empleadoId: Long): cu.spvi.core.money.Cup? =
        db.turnoDao().ultimoCerradoDeEmpleado(empleadoId)?.let { t -> (t.contadoCent ?: t.fondoCent)?.let { cu.spvi.core.money.Cup(it) } }

    override suspend fun quitarEmpleado(empleadoId: Long): AppResult<Unit> {
        val e = sync.empleado(empleadoId) ?: return AppResult.Err(AppError.NoEncontrado)
        // Se conserva la clave: así, al volver a conectar, esa app recibe un «quitada» firmado y borra sus datos.
        sync.actualizarEmpleado(e.copy(activo = false, codigoToken = null, codigoVence = null))
        servidor.expulsar(empleadoId)
        return AppResult.Ok(Unit)
    }

    companion object {
        /** 0.21.0 (C6): valor de `cierrePedidoPorEmpleadoEn` para «rechazada, falta avisar al empleado». */
        const val RECHAZO_PENDIENTE = 0L
    }

    private fun limiteSecundarias(): Int = licencia.snapshot.value?.secundariasPermitidas ?: Vinculacion.SECUNDARIAS_DEFECTO

    /** 0.21.0 (C5, P46): una app secundaria no crea secundarias (solo hay una principal por negocio). */
    private suspend fun noEsSecundaria(): AppError? =
        if (config.tipo.first() == TipoApp.SECUNDARIA) AppError.SinPermiso else null

    private fun licenciaPermite(): AppError? = when (licencia.snapshot.value?.estado) {
        is LicenseState.Trial, is LicenseState.Active, is LicenseState.Perpetual -> null
        else -> AppError.LicenciaBloqueada
    }
}

// ======================================================================================== secundaria

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SecundariaRepositoryImpl @Inject constructor(
    private val db: SpviDatabase,
    private val config: ConfigSync,
    private val cliente: ClienteSync,
    private val mantenimiento: MantenimientoRepository,
    private val preferencias: PreferenciasRepository,
    @IoDispatcher io: CoroutineDispatcher,
    /** 0.25.0: conteo declarado al solicitar el cierre. */
    private val turnos: cu.spvi.data.repository.TurnoRepositoryImpl,
) : SecundariaRepository {
    private val scope = CoroutineScope(SupervisorJob() + io)

    init {
        cliente.alSerQuitada = { olvidar() }
    }

    override val estado: StateFlow<EstadoSecundaria> =
        config.secundaria.flatMapLatest { d ->
            if (d == null) flowOf(EstadoSecundaria())
            else combine(cliente.conexion, db.syncDao().observarPendientes(), cliente.apkPrincipal) { c, p, apk ->
                EstadoSecundaria(
                    vinculada = true,
                    nombreNegocio = d.nombreNegocio, nombreEmpleado = d.nombreEmpleado, permisos = d.permisosEmpleado,
                    modo = d.modoSincronizacion, conexion = c, ultimaSincronizacion = d.ultimaSincronizacion?.let(Instant::ofEpochMilli),
                    pendientes = p, licencia = d.licenciaPrincipal,
                    cierrePendiente = d.cierrePendiente, turnoCerradoPorPrincipal = d.avisoCierre,
                    cierreSolicitado = d.cierreSolicitado, cierreRechazado = d.avisoRechazo, telefono = d.telefono,
                    apk = apk?.let { cu.spvi.domain.model.ApkEnPrincipal(it.nombre, it.code, it.bytes) },
                    principalAsignaFondo = d.principalAsignaFondo, fondoAsignado = d.fondoCent?.let { cu.spvi.core.money.Cup(it) },
                    fondoPedido = d.pideFondo,
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, EstadoSecundaria())

    override suspend fun vincular(contenidoQr: String, telefono: String): AppResult<Unit> {
        // 0.21.0 (C2): el teléfono del empleado va en su QR de cobro (el SMS de Transfermóvil le llega a él).
        if (!Vinculacion.telefonoValido(telefono)) return AppResult.Err(AppError.Validacion("telefono", AppError.Regla.FORMATO))
        val codigo = CodigoQr.decodificar(contenidoQr)
            ?: return AppResult.Err(AppError.VinculacionRechazada(ClienteSync.TEXTO_CODIGO_INVALIDO))
        if (!codigo.venceEn.isAfter(Instant.now())) {
            return AppResult.Err(AppError.VinculacionRechazada(ClienteSync.textoRechazo(Rechazo.CODIGO_VENCIDO)))
        }
        val datos = when (val r = cliente.vincular(codigo)) {
            is AppResult.Ok -> r.value.copy(telefono = telefono.trim())
            is AppResult.Err -> return r
        }
        // Vinculada: desde aquí este teléfono trabaja con los datos de la principal.
        val borrado = mantenimiento.borrarTodo()
        if (borrado is AppResult.Err) return borrado
        db.syncDao().empleados().forEach { db.syncDao().actualizarEmpleado(it.copy(activo = false, clave = null)) }
        config.hacerSecundaria(datos)
        preferencias.completarOnboarding()
        val primera = cliente.sincronizar()
        if (datos.modoSincronizacion == ModoSincronizacion.AUTOMATICA) cliente.iniciarAutomatico()
        // Si la primera descarga falla, la app ya está vinculada: lo reintenta sola (o con «Sincronizar ahora»).
        return if (primera is AppResult.Err && primera.error !is AppError.SinPrincipal) primera else AppResult.Ok(Unit)
    }

    override suspend fun sincronizarAhora(): AppResult<Unit> = cliente.sincronizar()

    override suspend fun cambiarModo(modo: ModoSincronizacion) {
        config.editarSecundaria { it.copy(modo = modo.name) }
        if (modo == ModoSincronizacion.AUTOMATICA) cliente.iniciarAutomatico() else cliente.detenerAutomatico()
    }

    override suspend fun desvincular(): AppResult<Unit> = olvidar()

    override suspend fun descartarAvisoCierre() = config.editarSecundaria { it.copy(avisoCierre = false, avisoRechazo = false) }

    override suspend fun descargarApk(progreso: (Long, Long) -> Unit): AppResult<java.io.File> = cliente.descargarApk(progreso)

    override suspend fun pedirFondo(): AppResult<Unit> {
        if (db.turnoDao().activo() != null) return AppResult.Err(AppError.TurnoYaAbierto)
        config.editarSecundaria { it.copy(pideFondo = it.fondoCent == null) }
        cliente.disparar()
        // Sin conexión no es un error: la petición espera y se envía en la próxima sincronización.
        if (config.secundariaActual()?.modoSincronizacion == ModoSincronizacion.MANUAL) cliente.sincronizar()
        return AppResult.Ok(Unit)
    }

    override suspend fun solicitarCierre(contado: cu.spvi.core.money.Cup): AppResult<Unit> {
        if (db.turnoDao().activo() == null) return AppResult.Err(AppError.TurnoCerrado)
        // 0.25.0 (§5.3): el conteo viaja con la solicitud (en el turno abierto, que vuelve a quedar pendiente de enviar).
        when (val r = turnos.declararContado(contado)) {
            is AppResult.Err -> return r
            is AppResult.Ok -> Unit
        }
        config.editarSecundaria { it.copy(cierreSolicitado = true, avisoRechazo = false) }
        cliente.disparar()
        // Sin conexión no es un error: la solicitud espera y se envía en la próxima sincronización.
        if (config.secundariaActual()?.modoSincronizacion == ModoSincronizacion.MANUAL) cliente.sincronizar()
        return AppResult.Ok(Unit)
    }

    private suspend fun olvidar(): AppResult<Unit> {
        cliente.detenerAutomatico()
        val r = mantenimiento.borrarTodo()
        config.hacerPrincipal()
        return r
    }
}

// ======================================================================================== turno

/** «No se empieza un turno nuevo sin sincronizar»: solo aplica a la secundaria. */
@Singleton
class PuertaTurnoImpl @Inject constructor(
    private val config: ConfigSync,
    private val cliente: ClienteSync,
) : PuertaTurno {
    override suspend fun antesDeAbrir(): AppResult<Unit> =
        if (config.tipo.first() == TipoApp.PRINCIPAL) AppResult.Ok(Unit) else cliente.sincronizar()

    /** 0.26.0 (P73 §4): con una principal 0.26+, la secundaria abre solo con el fondo que le asignó el dueño. */
    override suspend fun fondoObligado(): AppResult<cu.spvi.core.money.Cup?> {
        if (config.tipo.first() == TipoApp.PRINCIPAL) return AppResult.Ok(null)
        val d = config.secundariaActual() ?: return AppResult.Ok(null)
        if (!d.principalAsignaFondo) return AppResult.Ok(null)
        val f = d.fondoCent ?: return AppResult.Err(AppError.Validacion("fondo", AppError.Regla.REQUERIDO))
        return AppResult.Ok(cu.spvi.core.money.Cup(f))
    }

    override suspend fun fondoUsado() {
        config.editarSecundaria { it.copy(fondoUsado = it.fondoToken ?: it.fondoUsado, fondoCent = null, fondoToken = null, pideFondo = false) }
        cliente.disparar()
    }
}

// ======================================================================================== arranque

/**
 * Pone en marcha la sincronización al abrir la app: en la principal, el servidor (si tiene secundarias); en una
 * secundaria en modo automático, el cliente. Lo llama SpviApplication.onCreate.
 */
@Singleton
class ArranqueSync @Inject constructor(
    private val db: SpviDatabase,
    private val config: ConfigSync,
    private val servidor: ServidorSync,
    private val cliente: ClienteSync,
    private val secundaria: SecundariaRepository, // instancia el manejador de «quitada»
    private val cierre: CierreRemoto,
    @IoDispatcher io: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + io)

    /**
     * 0.19.2: lo que necesita el servicio en primer plano de la principal (ServicioSync en :app). Solo emite
     * `principalConSecundarias = true` en la app PRINCIPAL con al menos una secundaria vinculada.
     */
    val atencion: Flow<AtencionSync> = combine(
        config.tipo,
        db.syncDao().observarEmpleados().map { it.isNotEmpty() }.distinctUntilChanged(),
        db.syncDao().observarTurnosAbiertos(),
        servidor.estado.map { it.conectadas.size }.distinctUntilChanged(),
    ) { tipo, hayEmpleados, abiertos, conectadas ->
        val principal = tipo == TipoApp.PRINCIPAL && hayEmpleados
        AtencionSync(principal, if (principal) abiertos else 0, if (principal) conectadas else 0)
    }.distinctUntilChanged()

    fun iniciar() {
        cierre.iniciar()
        scope.launch {
            combine(config.tipo, config.secundaria, db.syncDao().observarEmpleados()) { t, d, e -> Triple(t, d?.modoSincronizacion, e.isNotEmpty()) }
                .distinctUntilChanged()
                .collect { (tipo, modo, hayEmpleados) ->
                    if (tipo == TipoApp.PRINCIPAL) {
                        cliente.detenerAutomatico()
                        if (hayEmpleados) servidor.iniciar() else servidor.detener()
                    } else {
                        servidor.detener()
                        if (modo == ModoSincronizacion.AUTOMATICA) cliente.iniciarAutomatico() else cliente.detenerAutomatico()
                    }
                }
        }
    }
}

/** 0.19.2: estado que decide si la principal mantiene el servicio en primer plano y qué dice la notificación. */
data class AtencionSync(
    val principalConSecundarias: Boolean = false,
    /** Turnos abiertos en la BD de la principal (el suyo y los de las secundarias). */
    val turnosAbiertos: Int = 0,
    /** Secundarias conectadas ahora mismo. */
    val conectadas: Int = 0,
)
