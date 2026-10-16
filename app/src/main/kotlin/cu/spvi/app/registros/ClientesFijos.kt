package cu.spvi.app.registros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.spvi.app.common.SecureWindow
import cu.spvi.core.money.Money
import cu.spvi.core.time.Dates
import cu.spvi.designsystem.component.SpviBottomSheet
import cu.spvi.designsystem.component.SpviDialog
import cu.spvi.designsystem.component.SpviEmptyState
import cu.spvi.designsystem.component.SpviIconAction
import cu.spvi.designsystem.component.SpviListItem
import cu.spvi.designsystem.component.SpviLoading
import cu.spvi.designsystem.component.SpviSecondaryButton
import cu.spvi.designsystem.theme.SpviTextos
import cu.spvi.designsystem.component.spviAnimateItem
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.ilustracion.SpviIlustracion
import cu.spvi.designsystem.token.SpviSpacing
import cu.spvi.domain.model.ClienteFijo
import cu.spvi.domain.repository.ClienteFijoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 0.27.0 (N2): pestaña «Clientes» de Registros. Lista de clientes fijos (se guardan al vender por transferencia con
 * «Cliente fijo» marcado), ficha con sus compras y «Quitar de clientes fijos». Sin exportar; con FLAG_SECURE.
 */
data class ClientesUiState(
    val cargando: Boolean = true,
    val clientes: List<ClienteFijo> = emptyList(),
    val fichaCi: String? = null,
    val confirmarQuitar: Boolean = false,
) {
    val ficha: ClienteFijo? get() = fichaCi?.let { ci -> clientes.firstOrNull { it.ci == ci } }
}

private data class LocalClientes(val fichaCi: String? = null, val confirmarQuitar: Boolean = false)

@HiltViewModel
class ClientesFijosViewModel @Inject constructor(private val repo: ClienteFijoRepository) : ViewModel() {
    private val local = MutableStateFlow(LocalClientes())

    val state: StateFlow<ClientesUiState> =
        combine(repo.observar().map<List<ClienteFijo>, List<ClienteFijo>?> { it }.catch { emit(emptyList()) }, local) { cs, l ->
            ClientesUiState(cargando = cs == null, clientes = cs.orEmpty(), fichaCi = l.fichaCi, confirmarQuitar = l.confirmarQuitar)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientesUiState())

    fun abrir(ci: String) = local.update { it.copy(fichaCi = ci, confirmarQuitar = false) }
    fun cerrar() = local.update { LocalClientes() }
    fun pedirQuitar() = local.update { it.copy(confirmarQuitar = true) }
    fun cancelarQuitar() = local.update { it.copy(confirmarQuitar = false) }
    fun quitar() {
        val ci = local.value.fichaCi ?: return
        viewModelScope.launch {
            runCatching { repo.quitar(ci) }
            local.update { LocalClientes() }
        }
    }
}

object TextosClientes {
    const val VACIO_TITULO = "Aún no hay clientes fijos"
    const val VACIO_DETALLE = "En una venta por transferencia, marca «Cliente fijo» para guardar sus datos."
    const val QUITAR = "Quitar de clientes fijos"
    const val QUITAR_TITULO = "¿Quitar de clientes fijos?"
    const val QUITAR_TEXTO = "Sus datos ya no se sugerirán en las ventas. Sus compras siguen en Registros."
    fun resumen(c: ClienteFijo): String =
        if (c.compras == 0) "Sin compras" else "${c.compras} ${if (c.compras == 1) "compra" else "compras"} · ${Money.format(c.total)}"
    fun campos(c: ClienteFijo, zona: ZoneId = ZoneId.systemDefault()): List<Pair<String, String>> = buildList {
        add("Carné de identidad" to c.ci)
        add("Teléfono" to c.telefono)
        add("Compras" to c.compras.toString())
        add("Total comprado" to Money.format(c.total))
        c.ultimaCompra?.let { add("Última compra" to Dates.dayTime(it, zona)) }
        add("Cliente fijo desde" to Dates.day(c.creadoEn, zona))
    }
}

object ClientesTags {
    const val LISTA = "clientes_lista"
    const val FICHA = "clientes_ficha"
    const val QUITAR = "clientes_quitar"
    const val CONFIRMAR_QUITAR = "clientes_confirmar_quitar"
    fun cliente(ci: String) = "clientes_$ci"
    /** 0.31.0 (P3): casilla de selección de la fila. */
    fun check(ci: String) = "clientes_check_$ci"
}

class AccionesClientes(
    val onAbrir: (String) -> Unit = {},
    val onCerrar: () -> Unit = {},
    val onPedirQuitar: () -> Unit = {},
    val onCancelarQuitar: () -> Unit = {},
    val onQuitar: () -> Unit = {},
)

@Composable
fun ClientesFijosTab(state: ClientesUiState, acciones: AccionesClientes, zona: ZoneId = ZoneId.systemDefault()) {
    // Nombre, carné y teléfono: sin capturas ni miniatura en Recientes.
    SecureWindow()
    when {
        state.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SpviLoading() }
        state.clientes.isEmpty() -> SpviEmptyState(
            title = TextosClientes.VACIO_TITULO, detail = TextosClientes.VACIO_DETALLE, ilustracion = SpviIlustracion.Registros,
        )
        else -> {
            // 0.31.0 (P3): los clientes también se marcan con casilla (no se exportan: la selección es local).
            var marcados by rememberSaveable { mutableStateOf(emptySet<String>()) }
            Column(Modifier.fillMaxSize()) {
                if (marcados.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = SpviSpacing.xs).testTag(RegistrosTags.SELECCION),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpviIconAction(
                            SpviIcons.Cancelar, "Quitar selección", onClick = { marcados = emptySet() },
                            modifier = Modifier.testTag(RegistrosTags.LIMPIAR_SELECCION),
                        )
                        Text(
                            "${marcados.size} seleccionados", style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize().testTag(ClientesTags.LISTA),
                    contentPadding = PaddingValues(horizontal = SpviSpacing.md, vertical = SpviSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
                ) {
                    items(state.clientes, key = { it.ci }) { c ->
                        SpviListItem(
                            title = c.nombreApellidos,
                            // 0.28.0: el importe («3 compras · 1 250.00 CUP») va en seminegrita.
                            subtitleResaltado = SpviTextos.resaltar(TextosClientes.resumen(c), Money.format(c.total)),
                            indicatorColor = null,
                            leading = {
                                Checkbox(
                                    checked = c.ci in marcados,
                                    onCheckedChange = { marcados = if (c.ci in marcados) marcados - c.ci else marcados + c.ci },
                                    modifier = Modifier.testTag(ClientesTags.check(c.ci))
                                        .semantics { contentDescription = "Seleccionar ${c.nombreApellidos}" },
                                )
                            },
                            onClick = { acciones.onAbrir(c.ci) }, modifier = spviAnimateItem().testTag(ClientesTags.cliente(c.ci)),
                        )
                    }
                }
            }
        }
    }
    state.ficha?.let { c ->
        SpviBottomSheet(onDismiss = acciones.onCerrar, title = c.nombreApellidos) {
            Column(Modifier.fillMaxWidth().testTag(ClientesTags.FICHA), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                TextosClientes.campos(c, zona).forEach { (k, v) -> SpviListItem(title = k, value = v, indicatorColor = null) }
                SpviSecondaryButton(
                    TextosClientes.QUITAR, icon = SpviIcons.Eliminar, onClick = acciones.onPedirQuitar,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag(ClientesTags.QUITAR),
                )
            }
        }
        if (state.confirmarQuitar) {
            SpviDialog(
                title = TextosClientes.QUITAR_TITULO, text = TextosClientes.QUITAR_TEXTO,
                onDismiss = acciones.onCancelarQuitar, onConfirm = acciones.onQuitar,
                confirmDescription = TextosClientes.QUITAR, destructive = true, confirmTag = ClientesTags.CONFIRMAR_QUITAR,
            )
        }
    }
}
