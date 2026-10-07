package cu.spvi.app.vinculacion

import kotlinx.coroutines.flow.map
import cu.spvi.domain.model.Telefono
import cu.spvi.domain.model.TarjetaBancaria
import cu.spvi.domain.repository.PerfilRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.spvi.core.result.AppResult
import cu.spvi.data.sync.CodigoQr
import cu.spvi.domain.model.CodigoVinculacion
import cu.spvi.domain.model.Empleado
import cu.spvi.domain.model.EstadoPrincipal
import cu.spvi.domain.model.EstadoSecundaria
import cu.spvi.domain.model.ModoSincronizacion
import cu.spvi.domain.model.PermisoEmpleado
import cu.spvi.domain.model.TipoApp
import cu.spvi.domain.repository.PrincipalRepository
import cu.spvi.domain.repository.SecundariaRepository
import cu.spvi.domain.repository.TipoAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Formulario «Agregar app» / «Permisos» de una secundaria. [id] = 0 → nueva. */
data class FormEmpleado(
    val id: Long = 0,
    val nombre: String = "",
    val permisos: Set<PermisoEmpleado> = PermisoEmpleado.PREDETERMINADOS,
    val errorNombre: String? = null,
    /** 0.20.0 (H4): con qué cobra por transferencia (ids del Perfil). null = los predeterminados. */
    val tarjetaId: Long? = null,
    val telefonoId: Long? = null,
)

/**
 * 0.20.0: lo que la pantalla puede pedir. Lo implementa [VinculacionViewModel]; las capturas Roborazzi pasan
 * un objeto vacío para pintar [VinculacionContent] sin Hilt.
 */
interface AccionesVinculacion {
    fun agregar() {}
    fun editar(e: Empleado) {}
    fun cerrarForm() {}
    fun nombre(v: String) {}
    fun permiso(p: PermisoEmpleado, activo: Boolean) {}
    fun tarjeta(id: Long?) {}
    fun telefono(id: Long?) {}
    fun guardarForm() {}
    fun nuevoCodigo() {}
    fun cerrarQr() {}
    fun pedirQuitar() {}
    fun cancelarQuitar() {}
    fun confirmarQuitar() {}
    fun pedirCierre() {}
    fun cancelarCierre() {}
    fun confirmarCierre() {}
    /** 0.21.0 (C6): solicitud de cierre del empleado. */
    fun aprobarCierre() {}
    fun rechazarCierre() {}
    /** 0.21.0 (C2): teléfono del empleado antes de escanear el QR. */
    fun telefonoSecundaria(v: String) {}
    fun pedirSecundaria() {}
    fun cancelarSecundaria() {}
    fun confirmarSecundaria() {}
    fun sincronizar() {}
    fun modo(m: ModoSincronizacion) {}
    fun pedirDesvincular() {}
    fun cancelarDesvincular() {}
    fun confirmarDesvincular() {}
    /** 0.26.0 (P73 §4): fondo del próximo turno de la secundaria abierta en la ficha. */
    fun asignarFondo() {}
    fun cancelarFondo() {}
    fun confirmarFondo(fondo: cu.spvi.core.money.Cup) {}
}

/** 0.26.0 (P73 §4): diálogo «Asignar fondo». [sugerido] = lo contado al cerrar su último turno. */
data class AsignacionFondo(val empleado: Empleado, val sugerido: cu.spvi.core.money.Cup?)

/** QR listo para mostrar. [contenido] = texto del QR (lleva una clave de un solo uso: nunca se registra). */
data class QrVinculacion(val nombreEmpleado: String, val contenido: String, val codigo: CodigoVinculacion)

data class VinculacionUi(
    val tipo: TipoApp = TipoApp.PRINCIPAL,
    val empleados: List<Empleado> = emptyList(),
    val principal: EstadoPrincipal = EstadoPrincipal(),
    val secundaria: EstadoSecundaria = EstadoSecundaria(),
    val form: FormEmpleado? = null,
    val qr: QrVinculacion? = null,
    val quitar: Empleado? = null,
    val confirmarSecundaria: Boolean = false,
    val confirmarDesvincular: Boolean = false,
    val trabajando: Boolean = false,
    /** 0.20.0 (H5): confirmación «¿Pedir el cierre del turno de …?». */
    val cerrarTurno: Empleado? = null,
    /** 0.20.0 (H4): opciones de cobro (Pago electrónico del dueño). */
    val tarjetas: List<TarjetaBancaria> = emptyList(),
    val telefonos: List<Telefono> = emptyList(),
    val tarjetaPredeterminada: TarjetaBancaria? = null,
    val telefonoPredeterminado: Telefono? = null,
    /** 0.21.0 (C4): apps secundarias que cubre la licencia. */
    val limite: Int = cu.spvi.domain.model.Vinculacion.SECUNDARIAS_DEFECTO,
    /** 0.21.0 (C2): teléfono que escribe el empleado al pasar a secundaria. */
    val telefonoSecundaria: String = "",
    val errorTelefono: String? = null,
    val asignarFondo: AsignacionFondo? = null,
    /** 0.27.0 (T9): en el recorrido inicial se eligió «Secundaria» (si no, esta app es principal para siempre). */
    val eligioSecundaria: Boolean = false,
) {
    /** 0.27.0 (T9): se ofrece «Usar esta app como secundaria» solo si la regla lo permite. */
    val puedeSerSecundaria: Boolean get() = cu.spvi.domain.model.Vinculacion.puedeVincularseComoSecundaria(tipo, eligioSecundaria, empleados.size)
    /** 0.21.0 (C4/C5): solo la principal agrega, y hasta el límite de la licencia. */
    val puedeAgregar: Boolean get() = tipo == TipoApp.PRINCIPAL && cu.spvi.domain.model.Vinculacion.puedeAgregar(empleados.size, limite)
    /** Ficha abierta en el formulario (datos vivos: turno abierto, cierre pedido). */
    val empleadoForm: Empleado? get() = form?.takeIf { it.id != 0L }?.let { f -> empleados.firstOrNull { it.id == f.id } }
}

private data class Local(
    val form: FormEmpleado? = null,
    val qr: QrVinculacion? = null,
    val quitar: Empleado? = null,
    val confirmarSecundaria: Boolean = false,
    val confirmarDesvincular: Boolean = false,
    val trabajando: Boolean = false,
    val cerrarTurno: Empleado? = null,
    val telefonoSecundaria: String = "",
    val errorTelefono: String? = null,
    val asignarFondo: AsignacionFondo? = null,
)

@HiltViewModel
class VinculacionViewModel @Inject constructor(
    private val tipoRepo: TipoAppRepository,
    private val principal: PrincipalRepository,
    private val secundaria: SecundariaRepository,
    perfilRepo: PerfilRepository,
    private val licencia: cu.spvi.domain.repository.LicenciaRepository,
    ajustesDispositivo: cu.spvi.domain.repository.AjustesDispositivoRepository,
) : ViewModel(), AccionesVinculacion {
    private val local = MutableStateFlow(Local())
    private val mensajesCh = Channel<String>(Channel.BUFFERED)
    val mensajes: Flow<String> = mensajesCh.receiveAsFlow()

    /** Abrir el escáner en modo vinculación (lo hace la pantalla con la navegación). */
    private val escanearCh = Channel<Unit>(Channel.CONFLATED)
    val escanear: Flow<Unit> = escanearCh.receiveAsFlow()

    /** 0.27.0 (T9): leído siempre (aunque la pantalla no esté suscrita), para aplicar la regla en las acciones. */
    private val eligioSecundaria: StateFlow<Boolean> =
        ajustesDispositivo.ajustes.map { it.eligioSecundaria }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private fun puedeSerSecundaria(): Boolean = cu.spvi.domain.model.Vinculacion.puedeVincularseComoSecundaria(
        tipoRepo.tipo.value, eligioSecundaria.value, state.value.empleados.size,
    )

    private val base = combine(tipoRepo.tipo, principal.observarEmpleados(), principal.estado, secundaria.estado, ajustesDispositivo.ajustes) { t, e, p, s, a ->
        VinculacionUi(t, e, p, s, eligioSecundaria = a.eligioSecundaria)
    }

    val state: StateFlow<VinculacionUi> = combine(base, local, perfilRepo.perfil, licencia.snapshot) { b, l, pf, lic ->
        b.copy(
            limite = lic?.secundariasPermitidas ?: cu.spvi.domain.model.Vinculacion.SECUNDARIAS_DEFECTO,
            telefonoSecundaria = l.telefonoSecundaria, errorTelefono = l.errorTelefono,
            form = l.form, qr = l.qr, quitar = l.quitar, confirmarSecundaria = l.confirmarSecundaria,
            confirmarDesvincular = l.confirmarDesvincular, trabajando = l.trabajando, cerrarTurno = l.cerrarTurno,
            asignarFondo = l.asignarFondo,
            tarjetas = pf.tarjetas, telefonos = pf.telefonos, tarjetaPredeterminada = pf.tarjetaPago, telefonoPredeterminado = pf.telefonoPago,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VinculacionUi())

    // ---------------------------------------------------------------- principal

    override fun agregar() = local.update { it.copy(form = FormEmpleado()) }
    override fun editar(e: Empleado) = local.update { it.copy(form = FormEmpleado(e.id, e.nombre, e.permisos, tarjetaId = e.tarjetaId, telefonoId = e.telefonoId)) }
    override fun cerrarForm() = local.update { it.copy(form = null) }
    override fun nombre(v: String) = local.update { l -> l.copy(form = l.form?.copy(nombre = v.take(60), errorNombre = null)) }
    override fun permiso(p: PermisoEmpleado, activo: Boolean) = local.update { l ->
        l.copy(form = l.form?.let { f -> f.copy(permisos = if (activo) f.permisos + p else f.permisos - p) })
    }
    override fun tarjeta(id: Long?) = local.update { l -> l.copy(form = l.form?.copy(tarjetaId = id)) }
    override fun telefono(id: Long?) = local.update { l -> l.copy(form = l.form?.copy(telefonoId = id)) }

    override fun guardarForm() {
        val f = local.value.form ?: return
        trabajar {
            if (f.id == 0L) {
                when (val r = principal.agregarEmpleado(f.nombre, f.permisos)) {
                    is AppResult.Ok -> {
                        if (f.tarjetaId != null || f.telefonoId != null) principal.cambiarCobro(r.value.empleadoId, f.tarjetaId, f.telefonoId)
                        local.update { it.copy(form = null, qr = qr(r.value)) }
                    }
                    is AppResult.Err -> {
                        val campo = VinculacionLogic.errorNombre(r.error)
                        if (campo != null) local.update { l -> l.copy(form = l.form?.copy(errorNombre = campo)) }
                        else mensajesCh.trySend(VinculacionLogic.error(r.error))
                    }
                }
            } else {
                val r = principal.cambiarPermisos(f.id, f.permisos).let { r ->
                    if (r is AppResult.Ok) principal.cambiarCobro(f.id, f.tarjetaId, f.telefonoId) else r
                }
                when (r) {
                    is AppResult.Ok -> { local.update { it.copy(form = null) }; mensajesCh.trySend(VinculacionLogic.GUARDADO) }
                    is AppResult.Err -> mensajesCh.trySend(VinculacionLogic.error(r.error))
                }
            }
        }
    }

    override fun nuevoCodigo() {
        val f = local.value.form ?: return
        trabajar {
            when (val r = principal.nuevoCodigo(f.id)) {
                is AppResult.Ok -> local.update { it.copy(form = null, qr = qr(r.value)) }
                is AppResult.Err -> mensajesCh.trySend(VinculacionLogic.error(r.error))
            }
        }
    }

    override fun cerrarQr() = local.update { it.copy(qr = null) }

    override fun pedirQuitar() = local.update { l -> l.copy(quitar = state.value.empleados.firstOrNull { it.id == l.form?.id }, form = null) }
    override fun cancelarQuitar() = local.update { it.copy(quitar = null) }
    override fun confirmarQuitar() {
        val e = local.value.quitar ?: return
        local.update { it.copy(quitar = null) }
        trabajar {
            when (val r = principal.quitarEmpleado(e.id)) {
                is AppResult.Ok -> mensajesCh.trySend("App de ${e.nombre} quitada")
                is AppResult.Err -> mensajesCh.trySend(VinculacionLogic.error(r.error))
            }
        }
    }

    // ---------------------------------------------------------------- 0.20.0 (H5): pedir el cierre del turno

    override fun pedirCierre() = local.update { l -> l.copy(cerrarTurno = state.value.empleadoForm, form = null) }
    override fun cancelarCierre() = local.update { it.copy(cerrarTurno = null) }
    override fun confirmarCierre() {
        val e = local.value.cerrarTurno ?: return
        local.update { it.copy(cerrarTurno = null) }
        trabajar {
            when (val r = principal.pedirCierre(e.id)) {
                is AppResult.Ok -> mensajesCh.trySend(VinculacionLogic.cierrePedido(e.nombre, e.id in state.value.principal.conectadas))
                is AppResult.Err -> mensajesCh.trySend(
                    if (r.error == cu.spvi.core.result.AppError.TurnoCerrado) VinculacionLogic.SIN_TURNO else VinculacionLogic.error(r.error),
                )
            }
        }
    }

    // ---------------------------------------------------------------- 0.21.0 (C6): solicitud del empleado

    override fun aprobarCierre() = resolverSolicitud(aprobar = true)
    override fun rechazarCierre() = resolverSolicitud(aprobar = false)

    private fun resolverSolicitud(aprobar: Boolean) {
        val e = state.value.empleadoForm ?: return
        local.update { it.copy(form = null) }
        trabajar {
            val r = if (aprobar) principal.aprobarCierre(e.id) else principal.rechazarCierre(e.id)
            when (r) {
                is AppResult.Ok -> mensajesCh.trySend(
                    if (aprobar) VinculacionLogic.cierrePedido(e.nombre, e.id in state.value.principal.conectadas)
                    else VinculacionLogic.cierreRechazado(e.nombre),
                )
                is AppResult.Err -> mensajesCh.trySend(
                    if (r.error == cu.spvi.core.result.AppError.TurnoCerrado) VinculacionLogic.SIN_TURNO else VinculacionLogic.error(r.error),
                )
            }
        }
    }

    // ---------------------------------------------------------------- 0.26.0 (P73 §4): fondo de caja

    override fun asignarFondo() {
        val e = state.value.empleadoForm ?: return
        viewModelScope.launch {
            val s = runCatching { principal.fondoSugerido(e.id) }.getOrNull()
            local.update { it.copy(form = null, asignarFondo = AsignacionFondo(e, e.fondoAsignado ?: s)) }
        }
    }

    override fun cancelarFondo() = local.update { it.copy(asignarFondo = null) }

    override fun confirmarFondo(fondo: cu.spvi.core.money.Cup) {
        val a = local.value.asignarFondo ?: return
        local.update { it.copy(asignarFondo = null) }
        trabajar {
            when (val r = principal.asignarFondo(a.empleado.id, fondo)) {
                is AppResult.Ok -> mensajesCh.trySend(VinculacionLogic.fondoEnviado(a.empleado.nombre, a.empleado.id in state.value.principal.conectadas))
                is AppResult.Err -> mensajesCh.trySend(VinculacionLogic.error(r.error))
            }
        }
    }

    // ---------------------------------------------------------------- pasar a secundaria

    override fun pedirSecundaria() {
        if (!puedeSerSecundaria()) { mensajesCh.trySend(VinculacionLogic.PRINCIPAL_NO_SECUNDARIA); return } // 0.27.0 (T9)
        local.update { it.copy(confirmarSecundaria = true, errorTelefono = null) }
    }
    override fun cancelarSecundaria() = local.update { it.copy(confirmarSecundaria = false) }
    override fun telefonoSecundaria(v: String) = local.update {
        it.copy(telefonoSecundaria = cu.spvi.designsystem.component.FiltroEntrada.TELEFONO.aplicar(v), errorTelefono = null)
    }
    override fun confirmarSecundaria() {
        // 0.21.0 (C2): el teléfono del empleado es obligatorio (su QR de cobro lo usa).
        if (!cu.spvi.domain.model.Vinculacion.telefonoValido(local.value.telefonoSecundaria)) {
            local.update { it.copy(errorTelefono = VinculacionLogic.ERROR_TELEFONO) }
            return
        }
        local.update { it.copy(confirmarSecundaria = false) }
        escanearCh.trySend(Unit)
    }

    /** Texto leído por el escáner en modo VINCULAR. */
    fun qrLeido(contenido: String) = trabajar {
        if (!puedeSerSecundaria()) { mensajesCh.trySend(VinculacionLogic.PRINCIPAL_NO_SECUNDARIA); return@trabajar } // 0.27.0 (T9)
        when (val r = secundaria.vincular(contenido, local.value.telefonoSecundaria)) {
            is AppResult.Ok -> mensajesCh.trySend(VinculacionLogic.VINCULADA)
            is AppResult.Err -> mensajesCh.trySend(VinculacionLogic.error(r.error))
        }
    }

    // ---------------------------------------------------------------- secundaria

    override fun sincronizar() = trabajar {
        when (val r = secundaria.sincronizarAhora()) {
            is AppResult.Ok -> mensajesCh.trySend(VinculacionLogic.SINCRONIZADA)
            is AppResult.Err -> mensajesCh.trySend(VinculacionLogic.error(r.error))
        }
    }

    override fun modo(m: ModoSincronizacion) { viewModelScope.launch { secundaria.cambiarModo(m) } }

    override fun pedirDesvincular() = local.update { it.copy(confirmarDesvincular = true) }
    override fun cancelarDesvincular() = local.update { it.copy(confirmarDesvincular = false) }
    override fun confirmarDesvincular() {
        local.update { it.copy(confirmarDesvincular = false) }
        trabajar {
            if (secundaria.desvincular() is AppResult.Err) mensajesCh.trySend(VinculacionLogic.error(cu.spvi.core.result.AppError.Almacenamiento))
        }
    }

    private fun qr(c: CodigoVinculacion) = QrVinculacion(c.nombreEmpleado, CodigoQr.codificar(c), c)

    private fun trabajar(bloque: suspend () -> Unit) {
        if (local.value.trabajando) return
        local.update { it.copy(trabajando = true) }
        viewModelScope.launch {
            try { bloque() } finally { local.update { it.copy(trabajando = false) } }
        }
    }
}
