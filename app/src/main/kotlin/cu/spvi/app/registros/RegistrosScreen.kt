package cu.spvi.app.registros

import androidx.compose.foundation.layout.size
import cu.spvi.designsystem.token.SpviSize
import androidx.compose.material3.Icon
import cu.spvi.app.common.LocalPermisosApp
import cu.spvi.app.common.calculateListDetailPaneWidth
import cu.spvi.designsystem.component.FiltroEntrada
import cu.spvi.designsystem.component.SpviTextoAjustable
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.spvi.app.common.CampoFecha
import cu.spvi.app.common.Compartir
import cu.spvi.app.common.SecureWindow
import cu.spvi.app.inicio.EstadoCarga
import cu.spvi.app.navigation.Route
import cu.spvi.designsystem.component.IconActionStyle
import cu.spvi.designsystem.component.SpviBadge
import cu.spvi.designsystem.component.SpviBottomSheet
import cu.spvi.designsystem.component.SpviChip
import cu.spvi.designsystem.component.SpviEmptyState
import cu.spvi.designsystem.component.SpviIconAction
import cu.spvi.designsystem.component.SpviLinearProgress
import cu.spvi.designsystem.component.SpviListItem
import cu.spvi.designsystem.component.SpviLoading
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSecondaryButton
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviSnackbarHost
import cu.spvi.designsystem.component.SpviTab
import cu.spvi.designsystem.component.SpviTabs
import cu.spvi.designsystem.component.SpviTextField
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.component.spviAnimateItem
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.ilustracion.SpviIlustracion
import cu.spvi.designsystem.theme.SpviTextos
import cu.spvi.designsystem.token.SpviSpacing
import cu.spvi.domain.model.FiltroRegistros
import cu.spvi.domain.model.ItemMovimiento
import cu.spvi.domain.model.PeriodoRegistro
import cu.spvi.domain.model.Transaccion
import cu.spvi.domain.model.Turno
import cu.spvi.domain.model.Venta
import kotlinx.coroutines.launch
import cu.spvi.core.money.Money
import cu.spvi.domain.model.VistaRegistro
import cu.spvi.domain.service.FormatoExport
import cu.spvi.domain.service.TablasExport
import cu.spvi.domain.usecase.TipoRegistro
import java.time.ZoneId

class AccionesRegistros(
    val onPestana: (PestanaRegistros) -> Unit = {},
    val onBuscar: (String) -> Unit = {},
    val onAbrirFiltro: () -> Unit = {},
    val onCerrarFiltro: () -> Unit = {},
    val onAplicarFiltro: (FiltroRegistros) -> Unit = {},
    /** true = también vacía el buscador. */
    val onQuitarFiltros: (Boolean) -> Unit = {},
    val onReintentar: () -> Unit = {},
    val onAbrirExportar: () -> Unit = {},
    val onCerrarExportar: () -> Unit = {},
    val onEnviar: (FormatoExport) -> Unit = {},
    val onGuardar: (FormatoExport) -> Unit = {},
    val onAbrirVenta: (Long) -> Unit = {},
    val onAbrirTransferencia: (Long) -> Unit = {},
    val onAbrirMovimiento: (Long) -> Unit = {},
    val onCerrarFicha: () -> Unit = {},
    val onVerVenta: () -> Unit = {},
    val onTurno: (Long) -> Unit = {},
    val onReintentarTurnos: () -> Unit = {},
    /** P18 (A23): «Ayuda» desde el estado vacío. */
    val onAyuda: (() -> Unit)? = null,
    /** 0.25.0 (§6bis): anular / modificar en la ficha de una venta (solo principal y turno abierto). */
    val accionesVenta: (@Composable (Venta) -> Unit)? = null,
)

/** Registros (barra inferior): Ventas, Transferencias recibidas, Movimientos, Clientes (0.27.0) y Turnos en una sola pantalla. */
@Composable
fun RegistrosScreen(
    onNavigate: (Route) -> Unit,
    viewModel: RegistrosViewModel = hiltViewModel(),
    turnosViewModel: TurnosViewModel = hiltViewModel(),
    clientesViewModel: ClientesFijosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val turnos by turnosViewModel.state.collectAsStateWithLifecycle()
    val clientes by clientesViewModel.state.collectAsStateWithLifecycle()
    // Datos de clientes (nombre, carné, número de transferencia): sin capturas ni miniatura en Recientes mientras se ven.
    if (state.datos is VistaRegistro.Transferencias || state.ficha is FichaRegistro.DeTransferencia) SecureWindow()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    // CreateDocument fija el tipo MIME al crearse: uno por formato guardable.
    val guardarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExport.PDF.mime)) { viewModel.guardarEn(it?.toString()) }
    val guardarXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExport.XLSX.mime)) { viewModel.guardarEn(it?.toString()) }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoRegistros.CompartirArchivo ->
                    if (!Compartir.archivos(context, listOf(e.archivo), e.mime, e.asunto)) snackbar.showSnackbar(TextosRegistros.ERROR_COMPARTIR)
                is EventoRegistros.GuardarComo -> (if (e.mime == FormatoExport.XLSX.mime) guardarXlsx else guardarPdf).launch(e.nombre)
                is EventoRegistros.Mensaje -> snackbar.showSnackbar(e.texto)
            }
        }
    }
    // 0.28.0: sin swipe entre pestañas: el gesto pasa a la sección vecina como en las demás.
    val orden = pestanasVisibles(LocalPermisosApp.current)
    // 0.21.0 (C12): si la pestaña elegida es de un módulo desactivado, se pasa a la primera visible.
    LaunchedEffect(orden, state.pestana) { if (state.pestana !in orden) viewModel.pestana(orden.first()) }
    RegistrosContent(
        state = state,
        turnos = turnos,
        snackbar = snackbar,
        clientes = clientes,
        accionesClientes = AccionesClientes(
            onAbrir = clientesViewModel::abrir, onCerrar = clientesViewModel::cerrar, onPedirQuitar = clientesViewModel::pedirQuitar,
            onCancelarQuitar = clientesViewModel::cancelarQuitar, onQuitar = clientesViewModel::quitar,
        ),
        acciones = AccionesRegistros(
            onAyuda = { onNavigate(Route.Ayuda) },
            onPestana = viewModel::pestana, onBuscar = viewModel::buscar, onAbrirFiltro = viewModel::abrirFiltro,
            onCerrarFiltro = viewModel::cerrarFiltro, onAplicarFiltro = viewModel::aplicarFiltro, onQuitarFiltros = viewModel::quitarFiltros,
            onReintentar = viewModel::reintentar, onAbrirVenta = viewModel::abrirVenta,
            onAbrirExportar = viewModel::abrirExportar, onCerrarExportar = viewModel::cerrarExportar,
            onEnviar = viewModel::enviar, onGuardar = viewModel::pedirGuardar,
            onAbrirTransferencia = viewModel::abrirTransferencia, onAbrirMovimiento = viewModel::abrirMovimiento,
            onCerrarFicha = viewModel::cerrarFicha, onVerVenta = viewModel::verVentaDeTransferencia,
            onTurno = { onNavigate(Route.TurnoDetalle(it)) }, onReintentarTurnos = turnosViewModel::reintentar,
            accionesVenta = { v ->
                val accionesVm: VentaAccionesViewModel = hiltViewModel()
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                AccionesFichaVenta(
                    v, accionesVm,
                    onMensaje = { m -> scope.launch { snackbar.showSnackbar(m) } },
                    onHecho = { viewModel.cerrarFicha(); viewModel.reintentar() },
                )
            },
        ),
    )
}

@Composable
fun RegistrosContent(
    state: RegistrosUiState,
    turnos: EstadoCarga<List<Turno>>,
    acciones: AccionesRegistros,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    zona: ZoneId = ZoneId.systemDefault(),
    clientes: ClientesUiState = ClientesUiState(cargando = false),
    accionesClientes: AccionesClientes = AccionesClientes(),
) {
    val tipo = state.tipo
    val anchoPanelDetalle = calculateListDetailPaneWidth()
    val panelDeDetalle = anchoPanelDetalle != null && tipo != null && state.ficha != null
    BackHandler(enabled = panelDeDetalle) { acciones.onCerrarFicha() }
    Scaffold(
        topBar = {
            SpviTopBar(title = TextosRegistros.TITULO, actions = {
                if (tipo != null) {
                    val n = state.filtro.activos
                    BadgedBox(badge = { if (n > 0) SpviBadge(n) }) {
                        SpviIconAction(
                            SpviIcons.Filtrar, if (tipo == TipoRegistro.MOVIMIENTOS) TextosRegistros.FILTRAR_FECHA else TextosRegistros.FILTRAR,
                            onClick = acciones.onAbrirFiltro, selected = n > 0, modifier = Modifier.testTag(RegistrosTags.FILTRO),
                        )
                    }
                    val exportar = LocalPermisosApp.current.exportar
                    if (exportar) SpviIconAction(
                        SpviIcons.Exportar, TextosRegistros.EXPORTAR, onClick = acciones.onAbrirExportar,
                        enabled = state.puedeCompartir && !state.exportando, modifier = Modifier.testTag(RegistrosTags.EXPORTAR),
                    )
                }
            })
        },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.exportando) {
                SpviLinearProgress(Modifier.testTag(RegistrosTags.EXPORTANDO).semantics { contentDescription = TextosRegistros.EXPORTANDO })
            }
            if (state.buscando) {
                SpviLinearProgress(
                    Modifier.fillMaxWidth().testTag(RegistrosTags.BUSCANDO)
                        .semantics {
                            contentDescription = TextosRegistros.BUSCANDO
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
            Pestanas(state.pestana, acciones, pestanasVisibles(LocalPermisosApp.current))
            when {
                state.pestana == PestanaRegistros.CLIENTES -> ClientesFijosTab(clientes, accionesClientes, zona)
                tipo == null -> Turnos(turnos, acciones, zona)
                tipo != null && panelDeDetalle -> Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = SpviSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { Tabla(state, tipo, acciones, zona) }
                    Box(Modifier.width(anchoPanelDetalle ?: 280.dp).fillMaxHeight()) {
                        state.ficha?.let { Ficha(it, acciones, zona, enPanel = true) }
                    }
                }
                else -> Tabla(state, tipo, acciones, zona)
            }
        }
    }
    if (!panelDeDetalle) state.ficha?.let { Ficha(it, acciones, zona) }
    // 0.21.0 (C8): en la secundaria solo están los registros de su empleado: sin filtro «Vendedor».
    val vendedores = if (LocalPermisosApp.current.esSecundaria) emptyList() else state.vendedores
    if (state.hojaFiltro && tipo != null) HojaFiltro(state.filtro, tipo, acciones, vendedores)
    if (state.hojaExportar && tipo != null) HojaExportar(state, tipo, acciones)
}

/** Prompt 14: PDF o Excel de lo que se ve; cada formato se puede enviar a otra app o guardar en el teléfono. */
@Composable
private fun HojaExportar(state: RegistrosUiState, tipo: TipoRegistro, acciones: AccionesRegistros) {
    SpviBottomSheet(onDismiss = acciones.onCerrarExportar, title = TextosRegistros.tituloExportar(tipo)) {
        SpviSecondaryText(TextosRegistros.alcanceExportar(tipo, state.datos?.cantidad ?: 0, detalleFiltro(state)))
        if (tipo == TipoRegistro.TRANSACCIONES) SpviSecondaryText(TextosRegistros.AVISO_DATOS_CLIENTES)
        listOf(FormatoExport.PDF, FormatoExport.XLSX).forEach { f ->
            val etiqueta = if (f == FormatoExport.PDF) "PDF" else "Excel"
            SpviListItem(
                title = etiqueta, indicatorColor = null,
                leading = { Icon(cu.spvi.app.common.iconoFormato(f), contentDescription = null, modifier = Modifier.size(SpviSize.icon)) },
                modifier = Modifier.testTag(RegistrosTags.formato(f)),
                trailing = {
                    Row {
                        SpviIconAction(
                            SpviIcons.Compartir, "Enviar $etiqueta a otra app", onClick = { acciones.onEnviar(f) },
                            modifier = Modifier.testTag(RegistrosTags.enviar(f)),
                        )
                        SpviIconAction(
                            SpviIcons.Exportar, "Guardar $etiqueta en el teléfono", onClick = { acciones.onGuardar(f) },
                            modifier = Modifier.testTag(RegistrosTags.guardar(f)),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun Pestanas(actual: PestanaRegistros, acciones: AccionesRegistros, orden: List<PestanaRegistros>) {
    SpviTabs(
        tabs = orden.map { SpviTab(it.etiqueta, testTag = RegistrosTags.pestana(it)) },
        selectedIndex = orden.indexOf(actual).coerceAtLeast(0),
        onSelect = { acciones.onPestana(orden[it]) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = SpviSpacing.xs),
    )
}

// ---------------- Tablas ----------------

@Composable
private fun Tabla(state: RegistrosUiState, tipo: TipoRegistro, acciones: AccionesRegistros, zona: ZoneId) {
    Column(Modifier.fillMaxSize()) {
        Buscador(state, tipo, acciones)
        Box(Modifier.weight(1f)) {
            when (val v = state.vista) {
                EstadoCarga.Idle, EstadoCarga.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SpviLoading() }
                is EstadoCarga.Error -> SpviEmptyState(title = "Algo salió mal", detail = v.mensaje, ilustracion = SpviIlustracion.Error) {
                    SpviSecondaryButton("Reintentar", icon = SpviIcons.Reintentar, onClick = acciones.onReintentar, modifier = Modifier.testTag(RegistrosTags.REINTENTAR))
                }
                is EstadoCarga.Vacio -> SpviEmptyState(
                    title = TextosRegistros.vacioTitulo(tipo), detail = TextosRegistros.vacioDetalle(tipo), ayuda = acciones.onAyuda,
                    ilustracion = SpviIlustracion.Registros,
                )
                is EstadoCarga.Exito -> if (v.datos.cantidad == 0) {
                    SpviEmptyState(
                        title = TextosRegistros.SIN_RESULTADOS_TITULO, detail = TextosRegistros.SIN_RESULTADOS_DETALLE,
                        ilustracion = SpviIlustracion.SinResultados,
                    ) {
                        SpviSecondaryButton(
                            TextosRegistros.QUITAR_FILTROS, icon = SpviIcons.QuitarFiltros, onClick = { acciones.onQuitarFiltros(true) },
                            modifier = Modifier.testTag(RegistrosTags.QUITAR_FILTROS),
                        )
                    }
                } else {
                    Lista(v.datos, acciones, zona)
                }
            }
        }
    }
}

@Composable
private fun Buscador(state: RegistrosUiState, tipo: TipoRegistro, acciones: AccionesRegistros) {
    Column(Modifier.padding(horizontal = SpviSpacing.md), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
        SpviTextField(
            filtro = FiltroEntrada.BUSQUEDA,
            value = state.filtro.texto, onValueChange = acciones.onBuscar, label = "Buscar",
            placeholder = TextosRegistros.buscar(tipo), leadingIcon = SpviIcons.Buscar,
            modifier = Modifier.fillMaxWidth().testTag(RegistrosTags.BUSCAR),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.md, Alignment.CenterHorizontally)) { // P28: contador (+ filtro) centrados
            state.datos?.let {
                // 0.28.0: el importe del resumen («3 ventas · 1 250.00 CUP») va en seminegrita.
                val total = when (it) {
                    is VistaRegistro.Ventas -> Money.format(it.total)
                    is VistaRegistro.Transferencias -> Money.format(it.total)
                    is VistaRegistro.Movimientos -> null
                }
                val texto = TextosRegistros.resumen(it)
                if (total == null) SpviSecondaryText(texto, modifier = Modifier.testTag(RegistrosTags.RESUMEN))
                else SpviSecondaryText(SpviTextos.resaltar(texto, total), modifier = Modifier.testTag(RegistrosTags.RESUMEN))
            }
            resumenFiltro(state.filtro, tipo)?.let { r ->
                SpviChip(label = r, selected = true, onClick = { acciones.onQuitarFiltros(false) }, icon = SpviIcons.Limpiar, supportingLabel = "Quitar filtro")
            }
        }
    }
}

@Composable
private fun Lista(datos: VistaRegistro, acciones: AccionesRegistros, zona: ZoneId) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(RegistrosTags.LISTA),
        contentPadding = PaddingValues(horizontal = SpviSpacing.md, vertical = SpviSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs / 2),
    ) {
        when (datos) {
            is VistaRegistro.Ventas -> ventas(datos.items, acciones, zona)
            is VistaRegistro.Transferencias -> transferencias(datos.items, acciones, zona)
            is VistaRegistro.Movimientos -> movimientos(datos.items, acciones, zona)
        }
    }
}

private fun LazyListScope.ventas(xs: List<Venta>, acciones: AccionesRegistros, zona: ZoneId) {
    items(xs, key = { "v${it.id}" }) { v ->
        SpviListItem(
            tituloEsDato = true, title = tituloFila(v, zona), subtitle = subtituloFila(v), value = valorFila(v), indicatorColor = null,
            onClick = { acciones.onAbrirVenta(v.id) }, modifier = spviAnimateItem().testTag(RegistrosTags.venta(v.id)),
        )
    }
}

private fun LazyListScope.transferencias(xs: List<Transaccion>, acciones: AccionesRegistros, zona: ZoneId) {
    items(xs, key = { "t${it.id}" }) { t ->
        SpviListItem(
            tituloEsDato = true, title = tituloFila(t, zona), subtitle = subtituloFila(t), value = valorFila(t), indicatorColor = null,
            onClick = { acciones.onAbrirTransferencia(t.id) }, modifier = spviAnimateItem().testTag(RegistrosTags.transferencia(t.id)),
        )
    }
}

private fun LazyListScope.movimientos(xs: List<ItemMovimiento>, acciones: AccionesRegistros, zona: ZoneId) {
    items(xs, key = { "m${it.movimiento.id}" }) { i ->
        val estado = descripcionMovimiento(i)
        SpviListItem(
            tituloEsDato = true, title = tituloFila(i, zona), subtitle = subtituloFila(i), value = valorFila(i),
            indicatorColor = colorMovimiento(i),
            onClick = { acciones.onAbrirMovimiento(i.movimiento.id) },
            modifier = spviAnimateItem().testTag(RegistrosTags.movimiento(i.movimiento.id)).semantics { stateDescription = estado },
        )
    }
}

@Composable
private fun colorMovimiento(i: ItemMovimiento): Color =
    if (esEntrada(i)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

// ---------------- Turnos (Prompt 7) ----------------

@Composable
private fun Turnos(state: EstadoCarga<List<Turno>>, acciones: AccionesRegistros, zona: ZoneId) {
    Box(Modifier.fillMaxSize()) {
        when (state) {
            EstadoCarga.Idle, EstadoCarga.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SpviLoading() }
            is EstadoCarga.Error -> SpviEmptyState(title = "Algo salió mal", detail = state.mensaje, ilustracion = SpviIlustracion.Error) {
                SpviSecondaryButton("Reintentar", icon = SpviIcons.Reintentar, onClick = acciones.onReintentarTurnos, modifier = Modifier.testTag(RegistrosTags.REINTENTAR))
            }
            is EstadoCarga.Vacio -> SpviEmptyState(
                title = TextosTurno.SIN_TURNOS_TITULO, detail = TextosTurno.SIN_TURNOS_DETALLE, ilustracion = SpviIlustracion.SinTurno,
            )
            is EstadoCarga.Exito -> ListaTurnos(state.datos, acciones.onTurno, zona)
        }
    }
}

@Composable
private fun ListaTurnos(turnos: List<Turno>, onTurno: (Long) -> Unit, zona: ZoneId) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(RegistrosTags.LISTA),
        contentPadding = PaddingValues(SpviSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
    ) {
        items(turnos, key = { it.id }) { t ->
            SpviListItem(
                title = tituloTurno(t, zona), tituloEsDato = true,
                subtitleResaltado = SpviTextos.resaltar(subtituloTurno(t, zona), horasTurno(t, zona)),
                value = valorTurno(t),
                valueColor = if (t.abierto) MaterialTheme.colorScheme.primary else Color.Unspecified,
                indicatorColor = if (t.abierto) MaterialTheme.colorScheme.primary else null,
                selected = t.abierto,
                onClick = { onTurno(t.id) },
                modifier = spviAnimateItem().testTag(RegistrosTags.turno(t.id)),
            )
        }
    }
}

// ---------------- Ventana del elemento ----------------

@Composable
private fun Ficha(f: FichaRegistro, acciones: AccionesRegistros, zona: ZoneId, enPanel: Boolean = false) {
    val (titulo, campos) = when (f) {
        is FichaRegistro.DeVenta -> TablasExport.tituloVenta(f.venta, zona) to TablasExport.camposVenta(f.venta, zona)
        is FichaRegistro.DeTransferencia -> TablasExport.tituloTransaccion(f.transaccion, zona) to TablasExport.camposTransaccion(f.transaccion, zona)
        is FichaRegistro.DeMovimiento -> TablasExport.tituloMovimiento(f.item, zona) to TablasExport.camposMovimiento(f.item, zona)
    }
    SpviBottomSheet(
        onDismiss = acciones.onCerrarFicha,
        title = titulo,
        enPanel = enPanel,
    ) {
        // P28: el total (o el importe de la transferencia) va UNA vez, centrado arriba; no se repite en la tabla.
        val total = campos.firstOrNull { it.first == "Total" || it.first == "Importe" }
        Column(Modifier.testTag(RegistrosTags.FICHA), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            total?.let { (etiqueta, valor) ->
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                    SpviSecondaryText(etiqueta, textAlign = TextAlign.Center)
                    SpviTextoAjustable(valor, style = SpviTextos.datoEn(MaterialTheme.typography.headlineSmall), textAlign = TextAlign.Center)
                }
            }
            val lineas = if (f is FichaRegistro.DeVenta) TablasExport.lineasVenta(f.venta) else emptyList()
            val detalle = lineas.map { it.first }.toSet() // ya se muestran con el unitario debajo, no en campos
            (campos - setOfNotNull(total)).filterNot { it.first in detalle }.forEach { (campo, valor) ->
                SpviListItem(title = campo, value = valor, indicatorColor = null, valueMaxLines = 4)
            } // nota y cliente enteros
            lineas.forEach { (titulo, subtitulo, valor) ->
                SpviListItem(title = titulo, subtitle = subtitulo, value = valor, indicatorColor = null, valueMaxLines = 4)
            }
            if (f is FichaRegistro.DeVenta) acciones.accionesVenta?.invoke(f.venta)
            if (f is FichaRegistro.DeTransferencia) {
                SpviSecondaryButton(
                    TextosRegistros.VER_VENTA, icon = SpviIcons.Comprobante, onClick = acciones.onVerVenta,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag(RegistrosTags.FICHA_VER_VENTA),
                )
            }
        }
    }
}

// ---------------- Hoja Filtrar ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HojaFiltro(actual: FiltroRegistros, tipo: TipoRegistro, acciones: AccionesRegistros, vendedores: List<String> = emptyList()) {
    var form by remember(actual) { mutableStateOf(FiltroRegistrosLogic.desde(actual)) }
    var errores by remember(actual) { mutableStateOf(emptyMap<String, String>()) }
    val conImporte = tipo != TipoRegistro.MOVIMIENTOS
    SpviBottomSheet(
        onDismiss = acciones.onCerrarFiltro,
        title = if (conImporte) TextosRegistros.FILTRAR else TextosRegistros.FILTRAR_FECHA,
        footer = {
            SpviSecondaryButton(TextosRegistros.QUITAR_FILTROS, icon = SpviIcons.QuitarFiltros, onClick = { form = FormFiltroRegistros(); errores = emptyMap() })
            SpviPrimaryButton(
                "Aplicar", icon = SpviIcons.Aplicar,
                onClick = {
                    val (filtro, e) = FiltroRegistrosLogic.aplicar(form, actual, tipo)
                    errores = e
                    if (filtro != null) acciones.onAplicarFiltro(filtro)
                },
                modifier = Modifier.testTag(RegistrosTags.FILTRO_APLICAR),
            )
        },
    ) {
        Seccion("Fecha")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            PeriodoRegistro.entries.forEach { p ->
                SpviChip(
                    etiqueta(p), selected = form.periodo == p, onClick = { form = form.copy(periodo = p); errores = errores - CamposFiltroRegistros.FECHAS },
                    modifier = Modifier.testTag(RegistrosTags.periodo(p)),
                )
            }
        }
        if (form.periodo == PeriodoRegistro.PERSONALIZADO) {
            CampoFecha(form.desde, { form = form.copy(desde = it) }, label = "Desde", tag = RegistrosTags.FILTRO_DESDE, modifier = Modifier.fillMaxWidth())
            CampoFecha(
                form.hasta, { form = form.copy(hasta = it) }, label = "Hasta", tag = RegistrosTags.FILTRO_HASTA, modifier = Modifier.fillMaxWidth(),
                supportingText = errores[CamposFiltroRegistros.FECHAS] ?: "Incluye el día completo. Deja una vacía para no poner límite.",
            )
        }
        Seccion("Importe")
        if (conImporte) {
            SpviTextField(
                filtro = FiltroEntrada.DINERO,
                value = form.importeMin, onValueChange = { form = form.copy(importeMin = it); errores = errores - CamposFiltroRegistros.MIN },
                label = "Desde (CUP)", placeholder = "Sin mínimo",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = CamposFiltroRegistros.MIN in errores, errorText = errores[CamposFiltroRegistros.MIN],
                modifier = Modifier.fillMaxWidth().testTag(RegistrosTags.FILTRO_MIN),
            )
            SpviTextField(
                filtro = FiltroEntrada.DINERO,
                value = form.importeMax, onValueChange = { form = form.copy(importeMax = it); errores = errores - CamposFiltroRegistros.MAX },
                label = "Hasta (CUP)", placeholder = "Sin máximo",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = CamposFiltroRegistros.MAX in errores, errorText = errores[CamposFiltroRegistros.MAX],
                modifier = Modifier.fillMaxWidth().testTag(RegistrosTags.FILTRO_MAX),
            )
        } else {
            SpviSecondaryText(TextosRegistros.SIN_IMPORTE_EN_MOVIMIENTOS)
        }
        // 0.20.0 (H1): con apps secundarias, quién vendió. Con un solo nombre no aporta nada (Hick) y no se muestra.
        if (conImporte && (vendedores.size > 1 || form.vendedor != null)) {
            Seccion(TextosRegistros.VENDEDOR)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                SpviChip(TextosRegistros.TODOS_VENDEDORES, selected = form.vendedor == null, onClick = { form = form.copy(vendedor = null) })
                (vendedores + listOfNotNull(form.vendedor)).distinct().forEach { v ->
                    SpviChip(v, selected = form.vendedor == v, onClick = { form = form.copy(vendedor = v) }, modifier = Modifier.testTag(RegistrosTags.vendedor(v)))
                }
            }
        }
    }
}

@Composable
private fun Seccion(t: String) {
    Text(t, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = SpviSpacing.xs).semantics { heading() })
}
