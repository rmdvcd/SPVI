package cu.spvi.app.migrar

import cu.spvi.domain.repository.LicenciaRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.AutorizacionMigracion
import cu.spvi.domain.usecase.CompletarMigracion
import cu.spvi.domain.usecase.ConstruirSolicitudMigracion
import cu.spvi.domain.usecase.VerificarAutorizacionMigracion
import cu.spvi.domain.repository.AjustesDispositivoRepository
import cu.spvi.domain.repository.MantenimientoRepository
import cu.spvi.domain.repository.RespaldoRepository
import cu.spvi.licencia.contract.Via
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
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

data class MigrarForm(
    val destino: String = "",
    val errorDestino: String? = null,
    val autorizacion: String = "",
    val resultado: AutorizacionMigracion? = null,
    val confirmando: Boolean = false,
    val confirmacion: String = "",
    val ocupado: Boolean = false,
)

/**
 * 0.31.0 (P6): estado del intercambio por QR (Emisor/Receptor), aparte de los 4 pasos de siempre.
 * [claveDefinida] = el usuario tiene puesto el «acceso con clave» en Ajustes; sin eso no se deja empezar.
 */
data class MigrarQrUiState(
    val rol: RolMigracion? = null,
    val codigo: String = "",
    val origen: String = "",
    val etapa: EtapaMigracion? = null,
    val error: String? = null,
    val listo: Boolean = false,
) {
    val empezado: Boolean get() = rol != null
    val trabajando: Boolean get() = etapa != null
}

data class MigrarUiState(
    val deviceId: String? = null,
    val form: MigrarForm = MigrarForm(),
    val qr: MigrarQrUiState = MigrarQrUiState(),
    val claveDefinida: Boolean = false,
) {
    val autorizada: Boolean get() = form.resultado == AutorizacionMigracion.AUTORIZADA
    val puedeBorrar: Boolean get() = autorizada && confirmacionValida(form.confirmacion) && !form.ocupado
}

sealed interface EventoMigrar {
    data class Enviar(val via: Via, val texto: String) : EventoMigrar
    data class Mensaje(val texto: String) : EventoMigrar
}

@HiltViewModel
class MigrarViewModel @Inject constructor(
    private val licenciaRepo: LicenciaRepository,
    private val construirSolicitud: ConstruirSolicitudMigracion,
    private val verificar: VerificarAutorizacionMigracion,
    private val completar: CompletarMigracion,
    private val ajustes: AjustesDispositivoRepository,
    private val respaldo: RespaldoRepository,
    private val mantenimiento: MantenimientoRepository,
    private val red: DireccionesLocales,
) : ViewModel() {

    private val form = MutableStateFlow(MigrarForm())
    private val qr = MutableStateFlow(MigrarQrUiState())
    /** Canal de la migración en curso (emisor o receptor): se cancela al salir de la pantalla. */
    private var intercambio: Job? = null
    private val _eventos = Channel<EventoMigrar>(Channel.BUFFERED)
    val eventos: Flow<EventoMigrar> = _eventos.receiveAsFlow()

    val state: StateFlow<MigrarUiState> =
        combine(licenciaRepo.snapshot, form, qr, ajustes.ajustes) { l, f, q, a ->
            MigrarUiState(l?.deviceId, f, q, a.accesoConClave)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MigrarUiState())

    fun editarDestino(v: String) = form.update { it.copy(destino = v, errorDestino = null) }

    fun enviarSolicitud(via: Via) = ejecutar {
        when (val r = construirSolicitud(form.value.destino)) {
            is AppResult.Ok -> _eventos.send(EventoMigrar.Enviar(via, r.value))
            is AppResult.Err -> form.update { it.copy(errorDestino = errorDestino(r.error)) }
        }
    }

    /** Cambiar el texto invalida la comprobación anterior. */
    fun editarAutorizacion(v: String) = form.update { it.copy(autorizacion = v, resultado = null) }

    fun comprobar() = ejecutar {
        val r = verificar(form.value.autorizacion)
        form.update { it.copy(resultado = r) }
    }

    fun pedirBorrado() {
        if (MigrarUiState(form = form.value).autorizada) form.update { it.copy(confirmando = true, confirmacion = "") }
    }

    fun editarConfirmacion(v: String) = form.update { it.copy(confirmacion = v) }
    fun cancelarBorrado() = form.update { it.copy(confirmando = false, confirmacion = "") }

    fun confirmarBorrado() {
        if (!MigrarUiState(form = form.value).puedeBorrar) return
        ejecutar {
            when (completar(form.value.autorizacion)) {
                // La licencia ya está cedida: la raíz pasa al panel de Licencia bloqueado por sí sola.
                is AppResult.Ok -> { form.value = MigrarForm(); _eventos.send(EventoMigrar.Mensaje(TextosMigrar.COMPLETADA)) }
                is AppResult.Err -> { form.update { it.copy(confirmando = false) }; _eventos.send(EventoMigrar.Mensaje(TextosMigrar.ERROR_GENERICO)) }
            }
        }
    }

    // ------------------------------------------------------------------ 0.31.0 (P6): migración por QR

    /** Elegir Emisor o Receptor. Si ya hay un intercambio en marcha, se descarta. */
    fun elegirRol(rol: RolMigracion) {
        if (qr.value.trabajando) return
        qr.value = MigrarQrUiState(rol = rol)
    }

    /** Volver a la elección Emisor/Receptor. */
    fun cambiarRol() {
        if (qr.value.trabajando) return
        qr.value = MigrarQrUiState()
    }

    /** Emisor: abre el canal, fabrica el QR y espera al otro teléfono. */
    fun empezarEmisor() {
        if (qr.value.trabajando) return
        val host = red.ipv4().firstOrNull()
        if (host == null) {
            qr.update { it.copy(error = TextosMigrar.QR_SIN_RED) }
            return
        }
        val origen = state.value.deviceId.orEmpty()
        val clave = CodigoMigracion.clave()
        qr.update { it.copy(error = null, etapa = EtapaMigracion.Esperando, origen = origen) }
        intercambio = viewModelScope.launch {
            val r = CanalMigracion(respaldo).servir(
                puerto = 0, clave = clave,
                // El puerto lo da el sistema al abrir el socket: el QR se dibuja cuando ya se conoce.
                alEscuchar = { puerto ->
                    qr.update { it.copy(codigo = CodigoMigracion(RolMigracion.EMISOR, host, puerto, clave, origen).texto()) }
                },
                etapa = { e -> qr.update { it.copy(etapa = e) } },
            )
            when (r) {
                // El receptor confirmó: este teléfono se borra (reseteo de fábrica) y la app vuelve al inicio.
                is AppResult.Ok -> {
                    qr.update { it.copy(etapa = EtapaMigracion.Terminado, listo = true) }
                    mantenimiento.borrarTodo()
                }
                is AppResult.Err -> qr.update { it.copy(etapa = null, error = TextosMigrar.errorQr(r.error)) }
            }
        }
    }

    /** Receptor: lee el QR, se conecta al emisor y aplica aquí el respaldo recibido. */
    fun empezarReceptor(codigo: CodigoMigracion) {
        if (qr.value.trabajando) return
        if (codigo.rol != RolMigracion.EMISOR) {
            qr.update { it.copy(error = TextosMigrar.QR_NO_EMISOR) }
            return
        }
        qr.update { it.copy(error = null, etapa = EtapaMigracion.Conectando, origen = codigo.origen) }
        intercambio = viewModelScope.launch {
            val canal = CanalMigracion(respaldo)
            val r = canal.conectar(codigo.host, codigo.puerto, codigo.clave) { etapa -> qr.update { it.copy(etapa = etapa) } }
            when (r) {
                is AppResult.Ok -> qr.update { it.copy(etapa = EtapaMigracion.Terminado, listo = true) }
                is AppResult.Err -> qr.update { it.copy(etapa = null, error = TextosMigrar.errorQr(r.error)) }
            }
        }
    }

    /** Cerrar el intercambio sin llegar a enviar/recibir nada. */
    fun cancelarIntercambio() {
        intercambio?.cancel()
        intercambio = null
        qr.value = MigrarQrUiState()
    }

    private fun ejecutar(bloque: suspend () -> Unit) {
        if (form.value.ocupado) return
        form.update { it.copy(ocupado = true) }
        viewModelScope.launch {
            try { bloque() } finally { form.update { it.copy(ocupado = false) } }
        }
    }
}
