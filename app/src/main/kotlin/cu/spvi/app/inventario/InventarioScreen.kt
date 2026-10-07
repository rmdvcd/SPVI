package cu.spvi.app.inventario

import cu.spvi.domain.model.nombreCompleto
import cu.spvi.app.common.LocalPermisosApp
import cu.spvi.domain.model.IdArticulo
import cu.spvi.domain.model.FichaInsumo
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import cu.spvi.app.common.Compartir
import cu.spvi.app.inicio.EstadoCarga
import cu.spvi.app.navigation.Route
import cu.spvi.core.money.Money
import cu.spvi.designsystem.component.FiltroEntrada
import cu.spvi.designsystem.component.CardTone
import cu.spvi.designsystem.component.IconActionStyle
import cu.spvi.designsystem.component.SpviBadge
import cu.spvi.designsystem.component.SpviBottomSheet
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviChip
import cu.spvi.designsystem.component.SpviDialog
import cu.spvi.designsystem.component.SpviEmptyState
import cu.spvi.designsystem.component.SpviFab
import cu.spvi.designsystem.component.SpviIconAction
import cu.spvi.designsystem.component.SpviLinearProgress
import cu.spvi.designsystem.component.SpviListItem
import cu.spvi.designsystem.theme.SpviTextos
import cu.spvi.designsystem.component.SpviLoading
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSecondaryButton
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviSnackbarHost
import cu.spvi.designsystem.component.SpviTextField
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.component.spviAnimateItem
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.ilustracion.SpviIlustracion
import cu.spvi.designsystem.token.SpviElevation
import cu.spvi.designsystem.token.SpviRadius
import cu.spvi.designsystem.token.SpviSize
import cu.spvi.designsystem.token.SpviSpacing
import cu.spvi.domain.model.FichaProducto
import cu.spvi.domain.model.FiltroInventario
import cu.spvi.domain.model.ItemInventario
import cu.spvi.domain.model.TipoArticulo
import cu.spvi.domain.service.FormatoExport
import cu.spvi.domain.service.TablasExport

/** Acciones de la pantalla (una sola clase para no arrastrar 20 lambdas y poder probar el Content sin Hilt). */
class AccionesInventario(
    val onBuscar: (String) -> Unit = {},
    val onFiltro: (FiltroInventario) -> Unit = {},
    val onQuitarFiltros: () -> Unit = {},
    val onReintentar: () -> Unit = {},
    val onAlternar: (Long) -> Unit = {},
    val onSeleccionarTodo: () -> Unit = {},
    val onLimpiarSeleccion: () -> Unit = {},
    val onAbrirFicha: (Long) -> Unit = {},
    val onCerrarFicha: () -> Unit = {},
    val onEditar: (Long) -> Unit = {},
    val onHoja: (HojaInventario) -> Unit = {},
    val onCerrarHoja: () -> Unit = {},
    val onAgregar: () -> Unit = {},
    /** P29: crear un insumo (categoría «Insumos»). */
    val onEliminarFicha: () -> Unit = {},
    val onEliminarSeleccion: () -> Unit = {},
    val onConfirmarEliminar: () -> Unit = {},
    val onCancelarEliminar: () -> Unit = {},
    val onExportar: (FormatoSalida) -> Unit = {},
    val onGuardar: (FormatoSalida) -> Unit = {},
    val onCompartirFicha: (FormatoSalida) -> Unit = {},
    /** Modo venta (Prompt 13). */
    val onContinuarVenta: () -> Unit = {},
    val onAtras: () -> Unit = {},
    /** P18 (A23): «Ayuda» desde el estado vacío. */
    val onAyuda: (() -> Unit)? = null,
)

@Composable
fun InventarioScreen(
    onNavigate: (Route) -> Unit,
    mensajeInicial: String? = null,
    onMensajeMostrado: () -> Unit = {},
    /** Modo venta: el usuario confirmó la selección (ids en orden de marcado). */
    onSeleccionVenta: (List<Long>) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: InventarioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    // CreateDocument fija el tipo MIME al crearse: uno por formato guardable.
    val guardarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExport.PDF.mime)) { viewModel.guardarEn(it?.toString()) }
    val guardarXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExport.XLSX.mime)) { viewModel.guardarEn(it?.toString()) }
    LaunchedEffect(mensajeInicial) {
        if (mensajeInicial != null) { onMensajeMostrado(); snackbar.showSnackbar(mensajeInicial) }
    }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoInventario.Compartir ->
                    if (!Compartir.archivos(context, e.archivos, e.mime, e.asunto)) snackbar.showSnackbar(TextosInventario.ERROR_COMPARTIR)
                is EventoInventario.GuardarComo -> (if (e.mime == FormatoExport.XLSX.mime) guardarXlsx else guardarPdf).launch(e.nombre)
                is EventoInventario.Navegar -> onNavigate(e.route)
                is EventoInventario.Mensaje -> snackbar.showSnackbar(e.texto)
                is EventoInventario.SeleccionVenta -> onSeleccionVenta(e.ids)
            }
        }
    }
    InventarioContent(
        state = state,
        snackbar = snackbar,
        acciones = AccionesInventario(
            onAyuda = { onNavigate(Route.Ayuda) },
            onBuscar = viewModel::buscar, onFiltro = viewModel::aplicarFiltro, onQuitarFiltros = viewModel::quitarFiltros,
            onReintentar = viewModel::reintentar, onAlternar = viewModel::alternar, onSeleccionarTodo = viewModel::seleccionarTodo,
            onLimpiarSeleccion = viewModel::limpiarSeleccion, onAbrirFicha = viewModel::abrirFicha, onCerrarFicha = viewModel::cerrarFicha,
            onEditar = viewModel::editar, onHoja = viewModel::mostrarHoja, onCerrarHoja = viewModel::cerrarHoja,
            onAgregar = viewModel::agregar,
            onEliminarFicha = viewModel::pedirEliminarFicha,
            onEliminarSeleccion = viewModel::pedirEliminarSeleccion, onConfirmarEliminar = viewModel::confirmarEliminar,
            onCancelarEliminar = viewModel::cancelarEliminar, onExportar = viewModel::exportar, onGuardar = viewModel::pedirGuardar,
            onCompartirFicha = viewModel::compartirFicha,
            onContinuarVenta = viewModel::confirmarSeleccionVenta, onAtras = onBack,
        ),
    )
}

@Composable
fun InventarioContent(
    state: InventarioUiState,
    acciones: AccionesInventario,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val venta = state.modoVenta
    Scaffold(
        topBar = {
            if (venta != null) {
                SpviTopBar(title = SeleccionVenta.titulo(venta), onBack = acciones.onAtras, actions = { BotonFiltro(state, acciones) })
            } else SpviTopBar(title = TextosInventario.TITULO, actions = {
                BotonFiltro(state, acciones)
                if (LocalPermisosApp.current.exportar) SpviIconAction(
                    SpviIcons.Exportar, TextosInventario.EXPORTAR, onClick = { acciones.onHoja(HojaInventario.EXPORTAR) },
                    enabled = state.items.isNotEmpty() || state.seleccion.isNotEmpty(), modifier = Modifier.testTag(InventarioTags.EXPORTAR),
                )
            })
        },
        bottomBar = { if (venta != null) BarraContinuarVenta(state, acciones) },
        // 0.26.0 (P73): el «+» va en la esquina inferior (estándar M3); la lista deja SpviSize.fabClearance abajo
        // para que la última fila (importes a la derecha) no quede tapada.
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            if (venta == null && state.seleccion.isEmpty() && LocalPermisosApp.current.editarInventario) {
                SpviFab(
                    SpviIcons.Agregar, TextosInventario.AGREGAR, onClick = acciones.onAgregar,
                    modifier = Modifier.testTag(InventarioTags.AGREGAR),
                )
            }
        },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.trabajando) SpviLinearProgress(Modifier.fillMaxWidth())
            Buscador(state, acciones)
            if (venta == null && state.seleccion.isNotEmpty()) BarraSeleccion(state, acciones)
            cu.spvi.designsystem.component.BloquearGestos(state.seleccion.isNotEmpty()) // 0.27.0 (T5)
            Box(Modifier.weight(1f)) {
                when (val v = state.vista) {
                    EstadoCarga.Idle, EstadoCarga.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SpviLoading() }
                    is EstadoCarga.Error -> SpviEmptyState(title = "Algo salió mal", detail = v.mensaje, ilustracion = SpviIlustracion.Error) {
                        SpviSecondaryButton("Reintentar", icon = SpviIcons.Reintentar, onClick = acciones.onReintentar)
                    }
                    // P24: sin botón «Agregar» aquí: repetía el botón flotante (+), que sigue visible.
                    is EstadoCarga.Vacio -> SpviEmptyState(
                        title = TextosInventario.VACIO_TITULO, detail = TextosInventario.VACIO_DETALLE, ayuda = acciones.onAyuda,
                        ilustracion = SpviIlustracion.Inventario,
                    )
                    is EstadoCarga.Exito -> if (v.datos.items.isEmpty()) {
                        SpviEmptyState(
                            title = TextosInventario.SIN_RESULTADOS_TITULO, detail = TextosInventario.SIN_RESULTADOS_DETALLE,
                            ilustracion = SpviIlustracion.SinResultados,
                        ) {
                            SpviSecondaryButton(
                                TextosInventario.QUITAR_FILTROS, icon = SpviIcons.QuitarFiltros, onClick = { acciones.onBuscar(""); acciones.onQuitarFiltros() },
                                modifier = Modifier.testTag(InventarioTags.QUITAR_FILTROS),
                            )
                        }
                    } else {
                        Tabla(state, acciones)
                    }
                }
            }
        }
    }

    state.ficha?.let { Ficha(it, state, acciones) }
    state.fichaInsumo?.let { FichaDeInsumo(it, state, acciones) }
    when (state.hoja) {
        HojaInventario.FILTRO -> HojaFiltro(state, acciones)
        HojaInventario.EXPORTAR -> HojaExportar(state, acciones)
        HojaInventario.COMPARTIR_FICHA -> if (state.ficha != null) HojaCompartirFicha(acciones)
        null -> Unit
    }
    state.confirmar?.let { c ->
        SpviDialog(
            title = when (c) {
                is ConfirmarEliminar.Uno -> TextosInventario.ELIMINAR_UNO
                is ConfirmarEliminar.Seleccion -> TextosInventario.eliminarVarios(c.cantidad)
            },
            text = (if (c is ConfirmarEliminar.Uno) "${c.nombre}. " else "") + TextosInventario.ELIMINAR_DETALLE,
            onDismiss = acciones.onCancelarEliminar,
            onConfirm = acciones.onConfirmarEliminar,
            confirmDescription = "Eliminar",
            destructive = true,
        )
    }
}

@Composable
private fun Buscador(state: InventarioUiState, acciones: AccionesInventario) {
    Column(Modifier.padding(horizontal = SpviSpacing.md), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
        SpviTextField(
            filtro = FiltroEntrada.BUSQUEDA,
            value = state.filtro.texto, onValueChange = acciones.onBuscar, label = "Buscar",
            placeholder = TextosInventario.BUSCAR, leadingIcon = SpviIcons.Buscar,
            modifier = Modifier.fillMaxWidth().testTag(InventarioTags.BUSCAR),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.md, Alignment.CenterHorizontally)) { // P28: contador (+ filtro) centrados
            if (state.vista is EstadoCarga.Exito) SpviSecondaryText(TextosInventario.contador(state.items.size, state.total))
            resumenFiltro(state.filtro)?.let { r ->
                SpviChip(label = r, selected = true, onClick = acciones.onQuitarFiltros, icon = SpviIcons.Limpiar, supportingLabel = "Quitar filtro")
            }
        }
    }
}

@Composable
private fun BotonFiltro(state: InventarioUiState, acciones: AccionesInventario) {
    BadgedBox(badge = { if (state.filtro.activos > 0) SpviBadge(state.filtro.activos) }) {
        SpviIconAction(
            SpviIcons.Filtrar, TextosInventario.FILTRAR, onClick = { acciones.onHoja(HojaInventario.FILTRO) },
            selected = state.filtro.activos > 0, modifier = Modifier.testTag(InventarioTags.FILTRO),
        )
    }
}

/** Modo venta: nº marcado + Continuar (ventana de cantidades y método de pago). */
@Composable
private fun BarraContinuarVenta(state: InventarioUiState, acciones: AccionesInventario) {
    Surface(tonalElevation = SpviElevation.tonalBar) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = SpviSpacing.md, vertical = SpviSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpviSpacing.md, Alignment.CenterHorizontally), // P28: grupo centrado
        ) {
            if (state.seleccion.isNotEmpty()) SpviIconAction(SpviIcons.Cancelar, "Quitar selección", onClick = acciones.onLimpiarSeleccion)
            Text(SeleccionVenta.continuar(state.seleccion.size), style = MaterialTheme.typography.titleSmall)
            SpviPrimaryButton(
                text = "Continuar", icon = SpviIcons.Siguiente, onClick = acciones.onContinuarVenta,
                enabled = state.seleccion.isNotEmpty(), modifier = Modifier.testTag(InventarioTags.CONTINUAR_VENTA),
            )
        }
    }
}

@Composable
private fun BarraSeleccion(state: InventarioUiState, acciones: AccionesInventario) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SpviSpacing.xs).testTag(InventarioTags.SELECCION),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpviIconAction(SpviIcons.Cancelar, "Quitar selección", onClick = acciones.onLimpiarSeleccion)
        Text(TextosInventario.seleccionados(state.seleccion.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        SpviIconAction(
            SpviIcons.Confirmar, if (state.todosVisiblesSeleccionados) "Desmarcar todo" else "Marcar todo",
            onClick = acciones.onSeleccionarTodo, selected = state.todosVisiblesSeleccionados,
            modifier = Modifier.testTag(InventarioTags.SELECCIONAR_TODO),
        )
        val permisos = LocalPermisosApp.current
        if (permisos.exportar) SpviIconAction(SpviIcons.Exportar, "Exportar selección", onClick = { acciones.onHoja(HojaInventario.EXPORTAR) })
        if (permisos.editarInventario) SpviIconAction(
            SpviIcons.Eliminar, "Eliminar selección", onClick = acciones.onEliminarSeleccion,
            modifier = Modifier.testTag(InventarioTags.ELIMINAR_SELECCION),
        )
    }
}

@Composable
private fun Tabla(state: InventarioUiState, acciones: AccionesInventario) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(InventarioTags.LISTA),
        contentPadding = PaddingValues(start = SpviSpacing.md, end = SpviSpacing.md, top = SpviSpacing.xs, bottom = SpviSize.fabClearance),
        verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs / 2),
    ) {
        items(state.items, key = { it.producto.id }) { i ->
            Fila(i, marcado = i.producto.id in state.seleccion, acciones = acciones, modoVenta = state.modoVenta != null, modifier = spviAnimateItem())
        }
    }
}

@Composable
private fun Fila(i: ItemInventario, marcado: Boolean, acciones: AccionesInventario, modoVenta: Boolean = false, modifier: Modifier = Modifier) {
    val estado = if (modoVenta && !SeleccionVenta.vendible(i)) SeleccionVenta.motivo(i) else descripcionEstado(i)
    SpviListItem(
        title = i.producto.nombreCompleto, // 0.24.0: «Nombre · Descripción»
        // 0.28.0: la fecha de caducidad («Vence 03/10/2026») va en seminegrita.
        subtitleResaltado = subtituloFila(i)?.let { subt ->
            SpviTextos.resaltar(subt, cu.spvi.app.common.FechasUi.texto(i.producto.fechaCaducidad))
        },
        subtitleMaxLines = if (i.nombreRepetido) 2 else 1,
        value = valorFila(i),
        valueMaxLines = if (i.alcanza != null) 2 else 1, // Elaborado: «Alcanza para N» / «Sin insumos suficientes» enteros
        indicatorColor = colorDe(tonoDe(i)),
        selected = marcado,
        leading = {
            cu.spvi.app.common.ConMiniatura(i.producto.fotoUri) { // 0.21.0 (C11)
                Checkbox(
                    checked = marcado,
                    onCheckedChange = { acciones.onAlternar(i.producto.id) },
                    modifier = Modifier.testTag(InventarioTags.check(i.producto.id))
                        .semantics { contentDescription = "Seleccionar ${i.producto.nombreCompleto}" },
                )
            }
        },
        // En modo venta tocar la fila marca/desmarca (no se edita a mitad de una venta).
        onClick = { if (modoVenta) acciones.onAlternar(i.producto.id) else acciones.onAbrirFicha(i.producto.id) },
        modifier = modifier.testTag(InventarioTags.fila(i.producto.id)).semantics { if (estado != null) stateDescription = estado },
    )
}

@Composable
private fun colorDe(t: TonoFila): Color = when (t) {
    TonoFila.NORMAL -> MaterialTheme.colorScheme.primary
    TonoFila.AVISO -> MaterialTheme.colorScheme.tertiary
    TonoFila.PELIGRO -> MaterialTheme.colorScheme.error
}

// ---------------- Ficha ----------------

@Composable
private fun Ficha(f: FichaProducto, state: InventarioUiState, acciones: AccionesInventario) {
    val p = f.producto
    SpviBottomSheet(
        onDismiss = acciones.onCerrarFicha,
        title = p.nombreCompleto,
        footer = {
            val permisos = LocalPermisosApp.current
            if (permisos.editarInventario) SpviIconAction(SpviIcons.Editar, "Editar", onClick = { acciones.onEditar(p.id) }, style = IconActionStyle.Tonal, modifier = Modifier.testTag(InventarioTags.FICHA_EDITAR))
            if (permisos.exportar) SpviIconAction(
                SpviIcons.Compartir, "Compartir", onClick = { acciones.onHoja(HojaInventario.COMPARTIR_FICHA) },
                style = IconActionStyle.Tonal, enabled = !state.trabajando, modifier = Modifier.testTag(InventarioTags.FICHA_COMPARTIR),
            )
            if (permisos.editarInventario) SpviIconAction(SpviIcons.Eliminar, "Eliminar", onClick = acciones.onEliminarFicha, style = IconActionStyle.Tonal, modifier = Modifier.testTag(InventarioTags.FICHA_ELIMINAR))
        },
    ) {
        Column(Modifier.testTag(InventarioTags.FICHA), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            p.fotoUri?.let { uri ->
                AsyncImage(
                    model = uri, contentDescription = "Foto de ${p.nombre}", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(SpviRadius.lg)),
                )
            }
            descripcionEstado(ItemInventario(p, f.nivel, f.caducidad))?.let {
                SpviCard(tone = CardTone.Tonal) { Text("Atención: $it", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
            TablasExport.camposFicha(f, paraClientes = false).drop(1).forEach { (campo, valor) ->
                SpviListItem(title = campo, value = valor, indicatorColor = null, valueMaxLines = 4) // ficha: receta y descripción enteras
            }
            SpviListItem(
                title = "Ganancia por unidad", value = Money.format(f.ganancia), indicatorColor = null,
                valueColor = if (f.ganancia.isNegative) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }
    }
}

/** P29: ficha de un insumo (antes en Elaboración). 0.26.0: ya no se comparte (era solo texto). */
@Composable
private fun FichaDeInsumo(f: FichaInsumo, state: InventarioUiState, acciones: AccionesInventario) {
    val i = f.insumo
    val id = IdArticulo.deInsumo(i.id)
    SpviBottomSheet(
        onDismiss = acciones.onCerrarFicha,
        title = i.nombre,
        footer = {
            val permisos = LocalPermisosApp.current
            if (permisos.editarInventario) SpviIconAction(SpviIcons.Editar, "Editar", onClick = { acciones.onEditar(id) }, style = IconActionStyle.Tonal, modifier = Modifier.testTag(InventarioTags.FICHA_EDITAR))
            if (permisos.editarInventario) SpviIconAction(SpviIcons.Eliminar, "Eliminar", onClick = acciones.onEliminarFicha, style = IconActionStyle.Tonal, modifier = Modifier.testTag(InventarioTags.FICHA_ELIMINAR))
        },
    ) {
        Column(Modifier.testTag(InventarioTags.FICHA), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            descripcionNivel(f.nivel)?.let {
                SpviCard(tone = CardTone.Tonal) { Text("Atención: $it", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
            TablasExport.camposInsumo(f).drop(1).forEach { (campo, valor) ->
                SpviListItem(title = campo, value = valor, indicatorColor = null, valueMaxLines = 4)
            }
            if (f.usadoEn.isEmpty()) SpviSecondaryText("Todavía no lo usa ninguna receta ni servicio.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---------------- Hojas ----------------

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HojaFiltro(state: InventarioUiState, acciones: AccionesInventario) {
    var borrador by remember(state.filtro) { mutableStateOf(state.filtro) }
    SpviBottomSheet(
        onDismiss = acciones.onCerrarHoja,
        title = TextosInventario.FILTRAR,
        footer = {
            SpviSecondaryButton("Quitar filtros", icon = SpviIcons.QuitarFiltros, onClick = { borrador = FiltroInventario(texto = borrador.texto) })
            SpviPrimaryButton("Aplicar", icon = SpviIcons.Aplicar, onClick = { acciones.onFiltro(borrador) })
        },
    ) {
        Seccion("Estado")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            SpviChip("Todos", selected = borrador.alerta == null, onClick = { borrador = borrador.copy(alerta = null) })
            (ALERTAS_PRODUCTO + ALERTAS_INSUMO + ALERTAS_GENERALES).forEach { a ->
                SpviChip(etiqueta(a), selected = borrador.alerta == a, onClick = { borrador = borrador.copy(alerta = a) })
            }
        }
        Seccion("Tipo")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            TipoArticulo.entries.forEach { t -> SpviChip(etiqueta(t), selected = borrador.tipo == t, onClick = { borrador = borrador.copy(tipo = t) }) }
        }
        if (state.categorias.isNotEmpty()) {
            Seccion("Categoría")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                SpviChip("Todas", selected = borrador.categoria == null, onClick = { borrador = borrador.copy(categoria = null) })
                state.categorias.forEach { c ->
                    SpviChip(c, selected = borrador.categoria.equals(c, ignoreCase = true), onClick = { borrador = borrador.copy(categoria = c) })
                }
            }
        }
    }
}

@Composable
private fun HojaExportar(state: InventarioUiState, acciones: AccionesInventario) {
    val n = if (state.seleccion.isNotEmpty()) state.seleccion.size else state.items.size
    SpviBottomSheet(onDismiss = acciones.onCerrarHoja, title = TextosInventario.EXPORTAR) {
        SpviSecondaryText(
            if (state.seleccion.isNotEmpty()) "${TextosInventario.productos(n)} seleccionados" else "Lo que ves: ${TextosInventario.productos(n)}",
        )
        FormatoSalida.EXPORTAR.forEach { f ->
            SpviListItem(
                title = f.etiqueta, subtitle = f.destinatario, indicatorColor = null,
                leading = cu.spvi.app.common.iconoFormato(f)?.let { ic -> { Icon(ic, contentDescription = null, modifier = Modifier.size(SpviSize.icon)) } },
                modifier = Modifier.testTag(InventarioTags.formato(f)),
                trailing = {
                    Row {
                        SpviIconAction(SpviIcons.Compartir, "Compartir ${f.etiqueta}", onClick = { acciones.onExportar(f) })
                        if (f.guardable) SpviIconAction(SpviIcons.Exportar, "Guardar ${f.etiqueta} en el dispositivo", onClick = { acciones.onGuardar(f) })
                    }
                },
            )
        }
    }
}

@Composable
private fun HojaCompartirFicha(acciones: AccionesInventario) {
    SpviBottomSheet(onDismiss = acciones.onCerrarHoja, title = "Compartir") {
        SpviSecondaryText("Imagen y texto: sin costo ni existencias.")
        FormatoSalida.COMPARTIR_FICHA.forEach { f ->
            SpviListItem(
                title = f.etiqueta, subtitle = if (f == FormatoSalida.PDF) FormatoSalida.USO_INTERNO else null,
                indicatorColor = null, onClick = { acciones.onCompartirFicha(f) },
                leading = { Icon(cu.spvi.app.common.iconoFormato(f) ?: SpviIcons.Compartir, contentDescription = null, modifier = Modifier.size(SpviSize.icon)) },
                modifier = Modifier.testTag(InventarioTags.formato(f)),
            )
        }
    }
}

@Composable
private fun Seccion(t: String) {
    Text(t, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = SpviSpacing.xs).semantics { heading() })
}

/** Opacidad del velo bajo el «+» abierto (la de los diálogos de Material 3). */
