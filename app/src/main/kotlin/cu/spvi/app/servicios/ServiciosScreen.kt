package cu.spvi.app.servicios

import androidx.compose.foundation.layout.size
import cu.spvi.domain.model.nombreCompleto
import cu.spvi.app.common.LocalPermisosApp
import cu.spvi.app.common.calculateListDetailPaneWidth
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import cu.spvi.app.common.Compartir
import cu.spvi.app.inicio.EstadoCarga
import cu.spvi.app.inventario.FormatoSalida
import cu.spvi.app.navigation.Route
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
import cu.spvi.domain.model.FiltroServicios
import cu.spvi.domain.model.ServicioDisponible
import cu.spvi.domain.service.FormatoExport

/** Acciones de la pantalla (una clase para no arrastrar 25 lambdas y poder probar el Content sin Hilt). */
class AccionesServicios(
    val onBuscar: (String) -> Unit = {},
    val onFiltro: (FiltroServicios) -> Unit = {},
    val onQuitarFiltros: () -> Unit = {},
    val onReintentar: () -> Unit = {},
    val onAlternar: (Long) -> Unit = {},
    val onSeleccionarTodo: () -> Unit = {},
    val onLimpiarSeleccion: () -> Unit = {},
    val onAbrirFicha: (Long) -> Unit = {},
    val onCerrarFicha: () -> Unit = {},
    val onEditar: (Long) -> Unit = {},
    val onHoja: (HojaServicios) -> Unit = {},
    val onCerrarHoja: () -> Unit = {},
    val onAgregar: () -> Unit = {},
    val onEliminarFicha: () -> Unit = {},
    val onEliminarSeleccion: () -> Unit = {},
    val onConfirmarEliminar: () -> Unit = {},
    val onCancelarEliminar: () -> Unit = {},
    val onExportar: (FormatoSalida) -> Unit = {},
    val onGuardar: (FormatoSalida) -> Unit = {},
    val onContinuarVenta: () -> Unit = {},
    val onAtras: () -> Unit = {},
    val onAyuda: (() -> Unit)? = null,
)

@Composable
fun ServiciosScreen(
    onNavigate: (Route) -> Unit,
    mensajeInicial: String? = null,
    onMensajeMostrado: () -> Unit = {},
    onSeleccionVenta: (List<Long>) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: ServiciosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val guardarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExport.PDF.mime)) { viewModel.guardarEn(it?.toString()) }
    val guardarXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExport.XLSX.mime)) { viewModel.guardarEn(it?.toString()) }
    LaunchedEffect(mensajeInicial) {
        if (mensajeInicial != null) { onMensajeMostrado(); snackbar.showSnackbar(mensajeInicial) }
    }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoServicios.Compartir ->
                    if (!Compartir.archivos(context, listOf(e.archivo), e.mime, e.asunto)) snackbar.showSnackbar(TextosServicios.ERROR_COMPARTIR)
                is EventoServicios.GuardarComo -> (if (e.mime == FormatoExport.XLSX.mime) guardarXlsx else guardarPdf).launch(e.nombre)
                is EventoServicios.Navegar -> onNavigate(e.route)
                is EventoServicios.Mensaje -> snackbar.showSnackbar(e.texto)
                is EventoServicios.SeleccionVenta -> onSeleccionVenta(e.ids)
            }
        }
    }
    ServiciosContent(
        state = state,
        snackbar = snackbar,
        acciones = AccionesServicios(
            onAyuda = { onNavigate(Route.Ayuda) },
            onBuscar = viewModel::buscar, onFiltro = viewModel::aplicarFiltro, onQuitarFiltros = viewModel::quitarFiltros,
            onReintentar = viewModel::reintentar, onAlternar = viewModel::alternar, onSeleccionarTodo = viewModel::seleccionarTodo,
            onLimpiarSeleccion = viewModel::limpiarSeleccion, onAbrirFicha = viewModel::abrirFicha, onCerrarFicha = viewModel::cerrarFicha,
            onEditar = viewModel::editar, onHoja = viewModel::mostrarHoja,
            onCerrarHoja = viewModel::cerrarHoja, onAgregar = viewModel::agregar, onEliminarFicha = viewModel::pedirEliminarFicha,
            onEliminarSeleccion = viewModel::pedirEliminarSeleccion, onConfirmarEliminar = viewModel::confirmarEliminar,
            onCancelarEliminar = viewModel::cancelarEliminar, onExportar = viewModel::exportar, onGuardar = viewModel::pedirGuardar,
            onContinuarVenta = viewModel::confirmarSeleccionVenta, onAtras = onBack,
        ),
    )
}

@Composable
fun ServiciosContent(
    state: ServiciosUiState,
    acciones: AccionesServicios,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val venta = state.modoVenta
    val anchoPanelDetalle = calculateListDetailPaneWidth()
    val panelDeDetalle = anchoPanelDetalle != null && !venta
    BackHandler(enabled = panelDeDetalle && state.ficha != null) { acciones.onCerrarFicha() }
    Scaffold(
        topBar = {
            if (venta) {
                SpviTopBar(title = TextosServicios.ELIGE, onBack = acciones.onAtras, actions = { BotonFiltro(state, acciones) })
            } else SpviTopBar(title = TextosServicios.TITULO, actions = {
                BotonFiltro(state, acciones)
                if (LocalPermisosApp.current.exportar) SpviIconAction(
                    SpviIcons.Exportar, TextosServicios.EXPORTAR, onClick = { acciones.onHoja(HojaServicios.EXPORTAR) },
                    enabled = state.items.isNotEmpty() || state.seleccion.isNotEmpty(), modifier = Modifier.testTag(ServiciosTags.EXPORTAR),
                )
            })
        },
        bottomBar = { if (venta) BarraContinuarVenta(state, acciones) },
        // 0.26.0 (P73): el «+» va en la esquina inferior (estándar M3); la lista deja SpviSize.fabClearance abajo
        // para que la última fila (importes a la derecha) no quede tapada.
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            if (!venta && state.seleccion.isEmpty() && LocalPermisosApp.current.editarInventario) {
                SpviFab(SpviIcons.Agregar, TextosServicios.AGREGAR, onClick = acciones.onAgregar, modifier = Modifier.testTag(ServiciosTags.AGREGAR))
            }
        },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.trabajando) SpviLinearProgress(Modifier.fillMaxWidth())
            if (state.buscando) SpviLinearProgress(
                Modifier.fillMaxWidth().testTag(ServiciosTags.BUSCANDO)
                    .semantics {
                        contentDescription = TextosServicios.BUSCANDO
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            Buscador(state, acciones)
            if (!venta && state.seleccion.isNotEmpty()) BarraSeleccion(state, acciones)
            cu.spvi.designsystem.component.BloquearGestos(state.seleccion.isNotEmpty()) // 0.27.0 (T5)
            Box(Modifier.weight(1f)) {
                if (panelDeDetalle && state.ficha != null) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = SpviSpacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
                    ) {
                        Box(Modifier.weight(1f).fillMaxHeight()) { ContenidoLista(state, acciones) }
                        Box(Modifier.width(anchoPanelDetalle ?: 280.dp).fillMaxHeight()) {
                            Ficha(state.ficha, state, acciones, enPanel = true)
                        }
                    }
                } else {
                    ContenidoLista(state, acciones)
                }
            }
        }
    }

    if (!panelDeDetalle) state.ficha?.let { Ficha(it, state, acciones) }
    when (state.hoja) {
        HojaServicios.FILTRO -> HojaFiltro(state, acciones)
        HojaServicios.EXPORTAR -> HojaExportar(state, acciones)
        null -> Unit
    }
    state.confirmar?.let { c ->
        SpviDialog(
            title = when (c) {
                is ConfirmarEliminarServicio.Uno -> TextosServicios.ELIMINAR_UNO
                is ConfirmarEliminarServicio.Seleccion -> TextosServicios.eliminarVarios(c.cantidad)
            },
            text = (if (c is ConfirmarEliminarServicio.Uno) "${c.nombre}. " else "") + TextosServicios.ELIMINAR_DETALLE,
            onDismiss = acciones.onCancelarEliminar,
            onConfirm = acciones.onConfirmarEliminar,
            confirmDescription = "Eliminar",
            destructive = true,
        )
    }
}

@Composable
private fun ContenidoLista(state: ServiciosUiState, acciones: AccionesServicios) {
    when (val v = state.vista) {
        EstadoCarga.Idle, EstadoCarga.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SpviLoading() }
        is EstadoCarga.Error -> SpviEmptyState(title = "Algo salió mal", detail = v.mensaje, ilustracion = SpviIlustracion.Error) {
            SpviSecondaryButton("Reintentar", icon = SpviIcons.Reintentar, onClick = acciones.onReintentar)
        }
        // Sin botón propio: «Agregar» ya está en el botón flotante (no se repite).
        is EstadoCarga.Vacio -> SpviEmptyState(
            title = TextosServicios.VACIO_TITULO, detail = TextosServicios.VACIO_DETALLE,
            ilustracion = SpviIlustracion.Servicios, ayuda = acciones.onAyuda,
        )
        is EstadoCarga.Exito -> if (v.datos.items.isEmpty()) {
            SpviEmptyState(title = TextosServicios.SIN_RESULTADOS_TITULO, detail = TextosServicios.SIN_RESULTADOS_DETALLE, ilustracion = SpviIlustracion.SinResultados) {
                SpviSecondaryButton(
                    TextosServicios.QUITAR_FILTROS, icon = SpviIcons.QuitarFiltros, onClick = { acciones.onBuscar(""); acciones.onQuitarFiltros() },
                    modifier = Modifier.testTag(ServiciosTags.QUITAR_FILTROS),
                )
            }
        } else {
            Lista(state, acciones)
        }
    }
}

@Composable
private fun BotonFiltro(state: ServiciosUiState, acciones: AccionesServicios) {
    BadgedBox(badge = { if (state.filtro.activos > 0) SpviBadge(state.filtro.activos) }) {
        SpviIconAction(
            SpviIcons.Filtrar, TextosServicios.FILTRAR, onClick = { acciones.onHoja(HojaServicios.FILTRO) },
            selected = state.filtro.activos > 0, enabled = state.tipos.isNotEmpty() || state.filtro.activos > 0,
            modifier = Modifier.testTag(ServiciosTags.FILTRO),
        )
    }
}

@Composable
private fun Buscador(state: ServiciosUiState, acciones: AccionesServicios) {
    Column(Modifier.padding(horizontal = SpviSpacing.md), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
        SpviTextField(
            filtro = FiltroEntrada.BUSQUEDA,
            value = state.filtro.texto, onValueChange = acciones.onBuscar, label = "Buscar",
            placeholder = TextosServicios.BUSCAR, leadingIcon = SpviIcons.Buscar,
            modifier = Modifier.fillMaxWidth().testTag(ServiciosTags.BUSCAR),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.md, Alignment.CenterHorizontally)) {
            if (state.vista is EstadoCarga.Exito) SpviSecondaryText(TextosServicios.contador(state.items.size, state.total))
            ServiciosLogic.resumenFiltro(state.filtro)?.let { r ->
                SpviChip(label = r, selected = true, onClick = acciones.onQuitarFiltros, icon = SpviIcons.Limpiar, supportingLabel = "Quitar filtro")
            }
        }
    }
}

@Composable
private fun BarraContinuarVenta(state: ServiciosUiState, acciones: AccionesServicios) {
    Surface(tonalElevation = SpviElevation.tonalBar) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = SpviSpacing.md, vertical = SpviSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpviSpacing.md, Alignment.CenterHorizontally),
        ) {
            if (state.seleccion.isNotEmpty()) SpviIconAction(SpviIcons.Cancelar, "Quitar selección", onClick = acciones.onLimpiarSeleccion)
            Text(TextosServicios.elegidos(state.seleccion.size), style = MaterialTheme.typography.titleSmall)
            SpviPrimaryButton(
                text = "Continuar", icon = SpviIcons.Siguiente, onClick = acciones.onContinuarVenta,
                enabled = state.seleccion.isNotEmpty(), modifier = Modifier.testTag(ServiciosTags.CONTINUAR),
            )
        }
    }
}

@Composable
private fun BarraSeleccion(state: ServiciosUiState, acciones: AccionesServicios) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SpviSpacing.xs).testTag(ServiciosTags.SELECCION),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpviIconAction(SpviIcons.Cancelar, "Quitar selección", onClick = acciones.onLimpiarSeleccion)
        Text(TextosServicios.seleccionados(state.seleccion.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        SpviIconAction(
            SpviIcons.Confirmar, if (state.todosVisiblesSeleccionados) "Desmarcar todo" else "Marcar todo",
            onClick = acciones.onSeleccionarTodo, selected = state.todosVisiblesSeleccionados,
            modifier = Modifier.testTag(ServiciosTags.SELECCIONAR_TODO),
        )
        val permisos = LocalPermisosApp.current
        if (permisos.exportar) SpviIconAction(SpviIcons.Exportar, "Exportar selección", onClick = { acciones.onHoja(HojaServicios.EXPORTAR) })
        if (permisos.editarInventario) SpviIconAction(
            SpviIcons.Eliminar, "Eliminar selección", onClick = acciones.onEliminarSeleccion,
            modifier = Modifier.testTag(ServiciosTags.ELIMINAR_SELECCION),
        )
    }
}

@Composable
private fun Lista(state: ServiciosUiState, acciones: AccionesServicios) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(ServiciosTags.LISTA),
        contentPadding = PaddingValues(start = SpviSpacing.md, end = SpviSpacing.md, top = SpviSpacing.xs, bottom = SpviSize.fabClearance),
        verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs / 2),
    ) {
        items(state.items, key = { it.servicio.id }) { s ->
            Fila(
                s, marcado = s.servicio.id in state.seleccion, acciones = acciones, modoVenta = state.modoVenta,
                repetido = s.servicio.id in state.datos?.porDiferenciar.orEmpty(), modifier = spviAnimateItem(),
            )
        }
    }
}

@Composable
private fun Fila(s: ServicioDisponible, marcado: Boolean, acciones: AccionesServicios, modoVenta: Boolean, repetido: Boolean = false, modifier: Modifier = Modifier) {
    val id = s.servicio.id
    val agotado = s.alcanza == 0L
    SpviListItem(
        title = s.servicio.nombreCompleto,
        subtitle = ServiciosLogic.subtitulo(s, repetido),
        subtitleMaxLines = if (repetido) 2 else 1,
        value = ServiciosLogic.valor(s),
        indicatorColor = if (agotado) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        selected = marcado,
        leading = {
            cu.spvi.app.common.ConMiniatura(s.servicio.fotoUri) { // 0.21.0 (C11)
                Checkbox(
                    checked = marcado,
                    onCheckedChange = { acciones.onAlternar(id) },
                    modifier = Modifier.testTag(ServiciosTags.check(id)).semantics { contentDescription = "Seleccionar ${s.servicio.nombreCompleto}" },
                )
            }
        },
        onClick = { if (modoVenta) acciones.onAlternar(id) else acciones.onAbrirFicha(id) },
        modifier = modifier.testTag(ServiciosTags.fila(id)).semantics { if (agotado) stateDescription = TextosServicios.NO_VENDIBLE },
    )
}

// ---------------- Ficha ----------------

@Composable
private fun Ficha(f: ServicioDisponible, state: ServiciosUiState, acciones: AccionesServicios, enPanel: Boolean = false) {
    val s = f.servicio
    SpviBottomSheet(
        onDismiss = acciones.onCerrarFicha,
        title = s.nombreCompleto,
        enPanel = enPanel,
        footer = {
            val permisos = LocalPermisosApp.current
            if (permisos.editarInventario) SpviIconAction(SpviIcons.Editar, "Editar", onClick = { acciones.onEditar(s.id) }, style = IconActionStyle.Tonal, modifier = Modifier.testTag(ServiciosTags.FICHA_EDITAR))
            if (permisos.editarInventario) SpviIconAction(SpviIcons.Eliminar, "Eliminar", onClick = acciones.onEliminarFicha, style = IconActionStyle.Tonal, modifier = Modifier.testTag(ServiciosTags.FICHA_ELIMINAR))
        },
    ) {
        Column(Modifier.testTag(ServiciosTags.FICHA), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            s.fotoUri?.let { uri ->
                AsyncImage(
                    model = uri, contentDescription = "Foto de ${s.nombre}", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(SpviRadius.lg)),
                )
            }
            if (f.alcanza == 0L) {
                SpviCard(tone = CardTone.Tonal) {
                    Text("Atención: ${TextosServicios.NO_VENDIBLE}", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            ServiciosLogic.campos(f).forEach { (campo, valor) ->
                SpviListItem(title = campo, value = valor, indicatorColor = null, valueMaxLines = 4)
            }
            if (!f.consumeInsumos) SpviSecondaryText(TextosServicios.SIN_INSUMOS_FICHA, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---------------- Hojas ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HojaFiltro(state: ServiciosUiState, acciones: AccionesServicios) {
    var borrador by remember(state.filtro) { mutableStateOf(state.filtro) }
    SpviBottomSheet(
        onDismiss = acciones.onCerrarHoja,
        title = TextosServicios.FILTRAR,
        footer = {
            SpviSecondaryButton("Quitar filtros", icon = SpviIcons.QuitarFiltros, onClick = { borrador = FiltroServicios(texto = borrador.texto) })
            SpviPrimaryButton("Aplicar", icon = SpviIcons.Aplicar, onClick = { acciones.onFiltro(borrador) })
        },
    ) {
        Text("Tipo", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs, Alignment.CenterHorizontally)) {
            SpviChip("Todos", selected = borrador.tipo == null, onClick = { borrador = borrador.copy(tipo = null) })
            state.tipos.forEach { t -> SpviChip(t, selected = borrador.tipo == t, onClick = { borrador = borrador.copy(tipo = t) }) }
        }
    }
}

@Composable
private fun HojaExportar(state: ServiciosUiState, acciones: AccionesServicios) {
    val n = if (state.seleccion.isNotEmpty()) state.seleccion.size else state.items.size
    SpviBottomSheet(onDismiss = acciones.onCerrarHoja, title = TextosServicios.EXPORTAR) {
        SpviSecondaryText(
            if (state.seleccion.isNotEmpty()) "${TextosServicios.servicios(n).replaceFirstChar { it.uppercase() }} seleccionados"
            else "Lo que ves: ${TextosServicios.servicios(n)}",
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        FORMATOS_EXPORTAR_SERVICIOS.forEach { f ->
            SpviListItem(
                title = f.etiqueta, indicatorColor = null,
                leading = cu.spvi.app.common.iconoFormato(f)?.let { ic -> { androidx.compose.material3.Icon(ic, contentDescription = null, modifier = Modifier.size(cu.spvi.designsystem.token.SpviSize.icon)) } },
                modifier = Modifier.testTag(ServiciosTags.formato(f)),
                trailing = {
                    Row {
                        SpviIconAction(SpviIcons.Compartir, "Compartir ${f.etiqueta}", onClick = { acciones.onExportar(f) })
                        SpviIconAction(SpviIcons.Exportar, "Guardar ${f.etiqueta} en el dispositivo", onClick = { acciones.onGuardar(f) })
                    }
                },
            )
        }
    }
}
