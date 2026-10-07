package cu.spvi.app.caja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.spvi.core.money.Cup
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.domain.model.Arqueo
import cu.spvi.domain.model.TipoApp
import cu.spvi.domain.model.TipoMovimientoCaja
import cu.spvi.domain.repository.EstadoAppRepository
import cu.spvi.domain.repository.TipoAppRepository
import cu.spvi.domain.repository.TurnoRepository
import cu.spvi.domain.usecase.FondoSugerido
import cu.spvi.domain.model.RecordatorioRespaldo
import cu.spvi.domain.usecase.RegistrarMovimientoCaja
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CajaUiState(
    /** Arqueo del turno abierto de esta app (null = sin turno o turno sin fondo). */
    val arqueo: Arqueo? = null,
    /** Fondo propuesto al abrir (null = sin turno anterior con arqueo). */
    val sugerido: Cup? = null,
    val dialogoMovimiento: Boolean = false,
    val trabajando: Boolean = false,
    /** Recordatorio mensual de respaldo (solo principal). */
    val avisoRespaldo: RecordatorioRespaldo.Aviso? = null,
    /** 0.26.0 (P73 §4): en una secundaria, el fondo lo asigna el encargado. */
    val fondo: FondoApertura = FondoApertura.Libre,
)

/**
 * 0.25.0 (§4, §5): arqueo del turno abierto, fondo propuesto, entradas/salidas de efectivo y recordatorio de respaldo.
 * ViewModel aparte para no tocar los constructores de Inicio/Venta (sus tests y la integración siguen igual).
 */
@HiltViewModel
class CajaViewModel @Inject constructor(
    turnos: TurnoRepository,
    private val fondoSugerido: FondoSugerido,
    private val registrar: RegistrarMovimientoCaja,
    private val estadoApp: EstadoAppRepository,
    tipoApp: TipoAppRepository,
    private val clock: Clock,
    private val secundaria: cu.spvi.domain.repository.SecundariaRepository,
) : ViewModel() {

    private val local = MutableStateFlow(CajaUiState())
    private val mensajes = Channel<String>(Channel.BUFFERED)
    val eventos: Flow<String> = mensajes.receiveAsFlow()

    val state: StateFlow<CajaUiState> = combine(
        local,
        turnos.observarArqueoActivo().catch { emit(null) },
        estadoApp.estado.catch { emit(cu.spvi.domain.model.EstadoApp()) },
        tipoApp.tipo,
        secundaria.estado,
    ) { l, a, e, t, s ->
        l.copy(arqueo = a, avisoRespaldo = RecordatorioRespaldo.aviso(clock.now(), e, t == TipoApp.PRINCIPAL), fondo = FondoApertura.de(s))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CajaUiState())

    init {
        cargarSugerido()
        // §4: la cuenta de 30 días empieza en la primera apertura con esta versión (no se avisa el primer día).
        viewModelScope.launch {
            runCatching {
                if (estadoApp.actual().cuentaRespaldoDesde == null) estadoApp.editar { it.copy(cuentaRespaldoDesde = it.cuentaRespaldoDesde ?: clock.now()) }
            }
        }
    }

    /** Se llama al mostrar el diálogo de apertura (el turno anterior puede haber cambiado). */
    fun cargarSugerido() {
        viewModelScope.launch {
            val s = try { fondoSugerido() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            local.update { it.copy(sugerido = s) }
        }
    }

    /** 0.26.0 (P73 §4): «Pedir fondo» en la secundaria (se envía al sincronizar; sin conexión, espera). */
    fun pedirFondo() {
        viewModelScope.launch {
            val r = try { secundaria.pedirFondo() } catch (e: CancellationException) { throw e } catch (e: Exception) { AppResult.Err(AppError.Desconocido()) }
            mensajes.trySend(
                when {
                    r is AppResult.Ok -> TextosCaja.FONDO_PEDIDO
                    r is AppResult.Err && r.error == AppError.TurnoYaAbierto -> "Ya hay un turno abierto."
                    else -> "No se pudo pedir el fondo. Inténtalo de nuevo."
                },
            )
        }
    }

    fun abrirMovimiento() = local.update { it.copy(dialogoMovimiento = true) }
    fun cerrarMovimiento() = local.update { it.copy(dialogoMovimiento = false) }

    fun registrarMovimiento(tipo: TipoMovimientoCaja, importe: Cup, motivo: String) {
        if (local.value.trabajando) return
        viewModelScope.launch {
            local.update { it.copy(trabajando = true) }
            val r = try { registrar(tipo, importe, motivo) } catch (e: CancellationException) { throw e } catch (e: Exception) { AppResult.Err(AppError.Desconocido()) }
            local.update { it.copy(trabajando = false, dialogoMovimiento = r is AppResult.Err) }
            mensajes.trySend(
                when {
                    r is AppResult.Ok -> TextosCaja.MOVIMIENTO_GUARDADO
                    r is AppResult.Err && r.error == AppError.TurnoCerrado -> "No hay turno abierto."
                    else -> "No se pudo registrar el movimiento. Revisa el importe y el motivo."
                },
            )
        }
    }
}
