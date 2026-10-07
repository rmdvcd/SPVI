package cu.spvi.app.migrar

import cu.spvi.domain.repository.LicenciaRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.AutorizacionMigracion
import cu.spvi.domain.usecase.CompletarMigracion
import cu.spvi.domain.usecase.ConstruirSolicitudMigracion
import cu.spvi.domain.usecase.VerificarAutorizacionMigracion
import cu.spvi.licencia.contract.Via
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

data class MigrarForm(
    val destino: String = "",
    val errorDestino: String? = null,
    val autorizacion: String = "",
    val resultado: AutorizacionMigracion? = null,
    val confirmando: Boolean = false,
    val confirmacion: String = "",
    val ocupado: Boolean = false,
)

data class MigrarUiState(val deviceId: String? = null, val form: MigrarForm = MigrarForm()) {
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
) : ViewModel() {

    private val form = MutableStateFlow(MigrarForm())
    private val _eventos = Channel<EventoMigrar>(Channel.BUFFERED)
    val eventos: Flow<EventoMigrar> = _eventos.receiveAsFlow()

    val state: StateFlow<MigrarUiState> = combine(licenciaRepo.snapshot, form) { l, f -> MigrarUiState(l?.deviceId, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MigrarUiState())

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

    private fun ejecutar(bloque: suspend () -> Unit) {
        if (form.value.ocupado) return
        form.update { it.copy(ocupado = true) }
        viewModelScope.launch {
            try { bloque() } finally { form.update { it.copy(ocupado = false) } }
        }
    }
}
