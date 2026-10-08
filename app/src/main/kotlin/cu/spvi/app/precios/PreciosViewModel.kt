package cu.spvi.app.precios

import cu.spvi.app.common.mensajeSync
import cu.spvi.domain.repository.ProductoRepository
import cu.spvi.domain.repository.PreciosRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.PreajustePrecios
import cu.spvi.domain.model.Producto
import cu.spvi.domain.usecase.ActivarPreajuste
import cu.spvi.domain.usecase.GuardarPreajuste
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

data class PreciosUiState(
    val cargado: Boolean = false,
    val preajustes: List<PreajustePrecios> = emptyList(),
    val productos: List<Producto> = emptyList(),
    val form: PreajusteForm? = null,
    val borrar: PreajustePrecios? = null,
    val guardando: Boolean = false,
) {
    val errores: Map<CampoPrecio, String> get() = form?.takeIf { it.mostrarErrores }?.let(::validar).orEmpty()
    val visibles: List<Producto> get() = form?.let { filtrar(productos, it.busqueda) }.orEmpty()
    val vistaPrevia: String? get() = form?.let { vistaPrevia(it, productos) }
    val avisoMargen: Boolean get() = form?.let { ajusteNoSuperaCosto(it, productos) } == true
    fun existentes(p: PreajustePrecios): Int = productos.count { it.id in p.productoIds }
}

/**
 * "Precios" de Inicio: preajustes que suben/bajan el precio de los productos elegidos según el método de
 * pago o la envergadura (importe) de la venta. Se aplican al cotizar (PlanificadorVenta), no cambian el
 * precio guardado.
 */
@HiltViewModel
class PreciosViewModel @Inject constructor(
    private val preciosRepo: PreciosRepository,
    private val productosRepo: ProductoRepository,
    private val guardarPreajuste: GuardarPreajuste,
    private val activarPreajuste: ActivarPreajuste,
) : ViewModel() {

    private val local = MutableStateFlow(PreciosUiState())
    private val preajustes = preciosRepo.observarPreajustes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val productos = productosRepo.observarTodos().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val mensajesCh = Channel<String>(Channel.BUFFERED)
    val mensajes: Flow<String> = mensajesCh.receiveAsFlow()

    val state: StateFlow<PreciosUiState> = combine(preajustes, productos, local) { pa, ps, l ->
        l.copy(cargado = true, preajustes = pa, productos = ps)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreciosUiState())

    fun nuevo() = local.update { it.copy(form = PreajusteForm()) }

    fun editar(p: PreajustePrecios) = local.update { it.copy(form = desde(p)) }

    fun cambiar(f: (PreajusteForm) -> PreajusteForm) = local.update { s -> s.copy(form = s.form?.let(f)) }

    fun alternarProducto(id: Long) = cambiar { it.copy(productoIds = if (id in it.productoIds) it.productoIds - id else it.productoIds + id) }

    /** "Elegir todos" actúa sobre los productos VISIBLES (los de la búsqueda actual). */
    fun elegirVisibles(elegir: Boolean) {
        cambiar { f ->
            val ids = filtrar(productos.value, f.busqueda).map { it.id }.toSet()
            f.copy(productoIds = if (elegir) f.productoIds + ids else f.productoIds - ids)
        }
    }

    fun cerrar() = local.update { it.copy(form = null) }

    fun guardar() {
        val f = local.value.form ?: return
        if (validar(f).isNotEmpty()) { cambiar { it.copy(mostrarErrores = true) }; return }
        ejecutar {
            when (val r = guardarPreajuste(aPreajuste(f))) {
                is AppResult.Ok -> {
                    local.update { it.copy(form = null) }
                    mensajesCh.trySend(if (f.nuevo) "Ajuste creado" else "Ajuste guardado")
                }
                is AppResult.Err -> mensajesCh.trySend(mensajeSync(r.error) ?: TextosPrecios.ERROR_GENERICO)
            }
        }
    }

    fun activar(p: PreajustePrecios, activo: Boolean) = ejecutar {
        val r = activarPreajuste(p.id, activo)
        if (r is AppResult.Err) mensajesCh.trySend(mensajeSync(r.error) ?: TextosPrecios.ERROR_GENERICO)
    }

    fun pedirBorrado() = local.update { s -> s.copy(borrar = preajustes.value.firstOrNull { it.id == s.form?.id }) }

    fun cancelarBorrado() = local.update { it.copy(borrar = null) }

    fun confirmarBorrado() {
        val p = local.value.borrar ?: return
        local.update { it.copy(borrar = null, form = null) }
        ejecutar {
            when (val r = preciosRepo.eliminarPreajuste(p.id)) {
                is AppResult.Ok -> mensajesCh.trySend("Ajuste eliminado")
                is AppResult.Err -> mensajesCh.trySend(mensajeSync(r.error) ?: TextosPrecios.ERROR_GENERICO)
            }
        }
    }

    private fun ejecutar(bloque: suspend () -> Unit) {
        if (local.value.guardando) return
        viewModelScope.launch {
            local.update { it.copy(guardando = true) }
            try { bloque() } finally { local.update { it.copy(guardando = false) } }
        }
    }
}
