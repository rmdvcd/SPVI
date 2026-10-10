package cu.spvi.app.pagos

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.spvi.app.common.SecureWindow
import cu.spvi.app.inicio.formatoTelefono
import cu.spvi.designsystem.component.FiltroEntrada
import cu.spvi.designsystem.component.IconActionStyle
import cu.spvi.designsystem.component.SpviBottomSheet
import cu.spvi.designsystem.component.SpviDialog
import cu.spvi.designsystem.component.SpviIconAction
import cu.spvi.designsystem.component.SpviListItem
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSecondaryButton
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviSnackbarHost
import cu.spvi.designsystem.component.SpviTextField
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.component.spviAnimateItem
import cu.spvi.designsystem.component.spviContentWidth
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviSpacing

object PagoTags {
    fun opcion(tipo: TipoCuentaPago, id: Long?) = "pago_${tipo.name}_${id ?: "ninguno"}"
    fun bancoPreview(banco: BancoCubano) = "pago_banco_preview_${banco.name}"
}

private data class ElementoPago(val id: Long, val texto: String, val alias: String?, val banco: BancoCubano? = null)

// =====================================================================
// Pago electrónico (desde Inicio y desde Ajustes): listas editables.
// =====================================================================

@Composable
fun PagoElectronicoScreen(onBack: () -> Unit, viewModel: PagoElectronicoViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e -> if (e is EventoPago.Mensaje) snackbar.showSnackbar(e.texto) }
    }
    SecureWindow() // números de tarjeta y teléfonos
    PagoElectronicoContent(
        state = state,
        onBack = onBack,
        acciones = AccionesListaPago(
            onElegir = viewModel::elegir, onNuevo = viewModel::nuevo, onEditar = viewModel::editar,
            onBorrar = viewModel::pedirBorrado, onCambiarEdicion = viewModel::editarEdicion,
            onGuardarEdicion = viewModel::guardarEdicion, onCerrarEdicion = viewModel::cerrarEdicion,
            onConfirmarBorrado = viewModel::confirmarBorrado, onCancelarBorrado = viewModel::cancelarBorrado,
        ),
        snackbar = snackbar,
    )
}

class AccionesListaPago(
    val onElegir: (TipoCuentaPago, Long) -> Unit,
    val onNuevo: (TipoCuentaPago) -> Unit,
    val onEditar: (TipoCuentaPago, Long) -> Unit,
    val onBorrar: (TipoCuentaPago, Long) -> Unit,
    val onCambiarEdicion: ((EdicionPago) -> EdicionPago) -> Unit,
    val onGuardarEdicion: () -> Unit,
    val onCerrarEdicion: () -> Unit,
    val onConfirmarBorrado: () -> Unit,
    val onCancelarBorrado: () -> Unit,
)

@Composable
fun PagoElectronicoContent(
    state: PagoUiState,
    onBack: () -> Unit,
    acciones: AccionesListaPago,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = { SpviTopBar(title = TextosPago.TITULO, onBack = onBack) },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().spviContentWidth().padding(padding),
            contentPadding = PaddingValues(vertical = SpviSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
        ) {
            lista(
                tipo = TipoCuentaPago.TELEFONO, titulo = "Teléfonos",
                items = state.perfil.telefonos.map { ElementoPago(it.id, formatoTelefono(it.numero), it.alias) },
                seleccionado = state.perfil.pagoTelefonoId, acciones = acciones,
            )
            lista(
                tipo = TipoCuentaPago.TARJETA, titulo = "Tarjetas y cuentas",
                items = state.perfil.tarjetas.map { ElementoPago(it.id, it.enmascarado, it.alias, bancoPorTarjeta(it.numero)) },
                seleccionado = state.perfil.pagoTarjetaId, acciones = acciones,
            )
        }
    }

    DialogosListaPago(state, acciones)
}

/** Ventana de alta/edición + confirmación de borrado. */
@Composable
private fun DialogosListaPago(state: PagoUiState, acciones: AccionesListaPago) {
    state.edicion?.let { e -> EdicionSheet(e, state.guardando, acciones) }

    state.borrar?.let { (tipo, _) ->
        SpviDialog(
            title = if (tipo == TipoCuentaPago.TELEFONO) "¿Eliminar este teléfono?" else "¿Eliminar esta tarjeta o cuenta?",
            text = "Se quita de la lista. Las ventas ya registradas no cambian.",
            onDismiss = acciones.onCancelarBorrado,
            onConfirm = acciones.onConfirmarBorrado,
            confirmDescription = "Eliminar",
            destructive = true,
        )
    }
}

/**
 * Lista editable de teléfonos o tarjetas. P28: es el ÚNICO sitio donde se ven y se eligen (antes también en
 * Perfil y en una ventana de Inicio). Tocar un elemento alterna su selección; solo se guarda un id por tipo.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.lista(
    tipo: TipoCuentaPago,
    titulo: String,
    items: List<ElementoPago>,
    seleccionado: Long?,
    acciones: AccionesListaPago,
) {
    item(key = "h_$tipo") {
        Row(
            Modifier.fillMaxWidth().padding(start = SpviSpacing.md, end = SpviSpacing.xs, top = SpviSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            SpviIconAction(
                SpviIcons.Agregar, if (tipo == TipoCuentaPago.TELEFONO) "Agregar teléfono" else "Agregar tarjeta o cuenta",
                onClick = { acciones.onNuevo(tipo) }, style = IconActionStyle.Tonal,
            )
        }
    }
    if (items.isEmpty()) {
        item(key = "v_$tipo") {
            SpviSecondaryText("La lista está vacía. Toca + para agregar.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = SpviSpacing.md))
        }
    }
    items(items, key = { "${tipo}_${it.id}" }) { item ->
        val enUso = item.id == seleccionado
        val subtitulo = listOfNotNull(item.alias, item.banco?.sigla).joinToString(" · ").ifBlank { null }
        SpviListItem(
            title = item.texto,
            subtitle = subtitulo,
            selected = enUso,
            indicatorColor = null, // La casilla indica el elemento elegido.
            leading = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = enUso,
                        modifier = Modifier.semantics { contentDescription = "Usar ${item.texto}" },
                        onCheckedChange = { acciones.onElegir(tipo, item.id) },
                    )
                    androidx.compose.material3.Icon(
                        if (tipo == TipoCuentaPago.TELEFONO) SpviIcons.Telefono else SpviIcons.PagoElectronico,
                        contentDescription = null,
                    )
                }
            },
            onClick = { acciones.onElegir(tipo, item.id) },
            modifier = spviAnimateItem().testTag(PagoTags.opcion(tipo, item.id)), // P18 (A18)
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpviIconAction(SpviIcons.Editar, "Editar ${item.texto}", onClick = { acciones.onEditar(tipo, item.id) })
                    SpviIconAction(SpviIcons.Eliminar, "Eliminar ${item.texto}", onClick = { acciones.onBorrar(tipo, item.id) })
                }
            },
        )
    }
}

/** Icono monocromático común para todas las tarjetas. */
@Composable
private fun IconoTarjeta(banco: BancoCubano, modifier: Modifier = Modifier) {
    androidx.compose.material3.Icon(
        SpviIcons.PagoElectronico, contentDescription = banco.nombre,
        modifier = modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EdicionSheet(e: EdicionPago, guardando: Boolean, acciones: AccionesListaPago) {
    val telefono = e.tipo == TipoCuentaPago.TELEFONO
    val inicial = remember { e }
    SpviBottomSheet(
        onDismiss = acciones.onCerrarEdicion,
        sinGuardar = e.numero != inicial.numero || e.alias != inicial.alias,
        title = when {
            e.id == null && telefono -> "Nuevo teléfono"
            e.id == null -> "Nueva tarjeta o cuenta"
            telefono -> "Editar teléfono"
            else -> "Editar tarjeta o cuenta"
        },
        footer = {
            SpviSecondaryButton("Cancelar", icon = SpviIcons.Cancelar, onClick = acciones.onCerrarEdicion)
            SpviPrimaryButton("Guardar", icon = SpviIcons.Guardar, onClick = acciones.onGuardarEdicion, loading = guardando, enabled = e.error == null)
        },
    ) {
        SpviTextField(
            filtro = if (telefono) FiltroEntrada.TELEFONO else FiltroEntrada.TARJETA,
            value = e.numero,
            onValueChange = { v -> acciones.onCambiarEdicion { it.copy(numero = v) } },
            label = if (telefono) "Número de teléfono" else "Número de tarjeta o cuenta",
            placeholder = if (telefono) "5123 4567" else "9205 0000 0000 0000",
            leadingIcon = if (telefono) SpviIcons.Telefono else SpviIcons.PagoElectronico,
            isError = e.error != null, validarAlSalir = true, forzarError = e.mostrarErrores,
            errorText = e.error,
            keyboardOptions = KeyboardOptions(keyboardType = if (telefono) KeyboardType.Phone else KeyboardType.Number),
        )
        if (!telefono) {
            bancoPorTarjeta(e.numero)?.let { banco ->
                Row(
                    Modifier.fillMaxWidth().testTag(PagoTags.bancoPreview(banco)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconoTarjeta(banco)
                    Column(Modifier.padding(start = SpviSpacing.xs)) {
                        Text(banco.nombre, style = MaterialTheme.typography.titleSmall)
                        SpviSecondaryText(TextosPago.BANCO_ESTIMADO)
                    }
                }
            }
        }
        SpviTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = e.alias,
            onValueChange = { v -> acciones.onCambiarEdicion { it.copy(alias = v) } },
            label = "Nombre para reconocerlo (opcional)",
            placeholder = if (telefono) "Mi móvil" else "Tarjeta del banco",
        )
    }
}
