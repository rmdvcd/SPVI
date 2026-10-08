package cu.spvi.app.registros

import androidx.compose.foundation.layout.size
import cu.spvi.designsystem.token.SpviSize
import androidx.compose.material3.Icon
import cu.spvi.designsystem.component.SpviTextoAjustable
import cu.spvi.designsystem.theme.SpviTextos
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.spvi.app.inicio.EstadoCarga
import cu.spvi.core.money.Money
import cu.spvi.designsystem.component.CardTone
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviEmptyState
import cu.spvi.designsystem.component.SpviListItem
import cu.spvi.designsystem.component.SpviLoading
import cu.spvi.designsystem.component.SpviSecondaryButton
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviTab
import cu.spvi.designsystem.component.SpviTabs
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.ilustracion.SpviIlustracion
import cu.spvi.designsystem.token.SpviSpacing
import cu.spvi.domain.model.DetalleTurno
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.model.TipoEntidad
import java.time.Instant
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import cu.spvi.app.common.Compartir
import cu.spvi.app.common.LocalPermisosApp
import cu.spvi.designsystem.component.SpviBottomSheet
import cu.spvi.designsystem.component.SpviIconAction
import cu.spvi.designsystem.component.SpviLinearProgress
import cu.spvi.designsystem.component.SpviSnackbarHost
import cu.spvi.domain.service.FormatoExport
import java.time.ZoneId

object TurnoDetalleTags {
    const val ARQUEO = "turno_arqueo"
    const val MOV_CAJA = "turno_mov_caja"
    const val LISTA = "turno_lista"
    const val TOTAL = "turno_detalle.total"
    const val CABECERA = "turno_cabecera"
    const val PROVISIONAL = "turno_provisional"
    const val TOTALES = "turno_totales"
    const val METODOS = "turno_metodos"
    const val VENDIDOS = "turno_vendidos"
    const val INSUMOS = "turno_insumos"
    const val MOV_PRODUCTOS = "turno_mov_productos"
    const val MOV_INSUMOS = "turno_mov_insumos"
    const val VENTAS = "turno_ventas"
    const val REINTENTAR = "turno_reintentar"
    fun pestana(p: PestanaTurno) = "turno_pestana_${p.name.lowercase()}"
    const val COMPARTIR = "turno_compartir"
    const val EXPORTANDO = "turno_exportando"
    fun formato(f: FormatoExport) = "turno_formato_${f.extension}"
    fun enviar(f: FormatoExport) = "turno_enviar_${f.extension}"
    fun guardar(f: FormatoExport) = "turno_guardar_${f.extension}"
}

/** 0.25.1: acciones de «Compartir turno» (PDF o Excel; enviar a otra app o guardar en el teléfono). */
class AccionesExportarTurno(
    val onAbrir: () -> Unit,
    val onCerrar: () -> Unit,
    val onEnviar: (FormatoExport) -> Unit,
    val onGuardar: (FormatoExport) -> Unit,
)

@Composable
fun TurnoDetalleScreen(onBack: () -> Unit, viewModel: TurnoDetalleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportacion by viewModel.exportacion.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refrescar() }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
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
    TurnoDetalleContent(
        state, onBack = onBack, onReintentar = viewModel::reintentar, exportacion = exportacion, snackbar = snackbar,
        exportar = AccionesExportarTurno(viewModel::abrirExportar, viewModel::cerrarExportar, viewModel::enviar, viewModel::pedirGuardar),
    )
}

@Composable
fun TurnoDetalleContent(
    state: EstadoCarga<DetalleTurno>,
    onBack: () -> Unit,
    onReintentar: () -> Unit,
    zona: ZoneId = ZoneId.systemDefault(),
    ahora: Instant = Instant.now(),
    pestanaInicial: PestanaTurno = PestanaTurno.RESUMEN,
    exportacion: ExportacionTurnoUi = ExportacionTurnoUi(),
    exportar: AccionesExportarTurno? = null,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val titulo = (state as? EstadoCarga.Exito)?.datos?.turno?.let { tituloDetalleTurno(it, zona) } ?: "Turno"
    // P38: sin el permiso «Exportar» (secundaria) no se comparte; la secundaria solo tiene sus propios turnos.
    val puedeExportar = exportar != null && state is EstadoCarga.Exito && LocalPermisosApp.current.exportar
    Scaffold(
        topBar = {
            SpviTopBar(title = titulo, onBack = onBack, actions = {
                if (puedeExportar) exportar?.let { acciones ->
                    SpviIconAction(
                        SpviIcons.Compartir, TextosTurno.COMPARTIR, onClick = acciones.onAbrir, enabled = !exportacion.exportando,
                        modifier = Modifier.testTag(TurnoDetalleTags.COMPARTIR),
                    )
                }
            })
        },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (exportacion.exportando) {
                SpviLinearProgress(Modifier.testTag(TurnoDetalleTags.EXPORTANDO).semantics { contentDescription = TextosRegistros.EXPORTANDO })
            }
            Box(Modifier.fillMaxSize()) {
                when (state) {
                    EstadoCarga.Idle, EstadoCarga.Cargando, is EstadoCarga.Vacio ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SpviLoading() }
                    is EstadoCarga.Error -> SpviEmptyState(
                        title = "Algo salió mal", detail = state.mensaje,
                        ilustracion = if (state.mensaje == TextosTurno.NO_ENCONTRADO) SpviIlustracion.NoExiste else SpviIlustracion.Error,
                    ) {
                        if (state.mensaje != TextosTurno.NO_ENCONTRADO) {
                            SpviSecondaryButton("Reintentar", icon = SpviIcons.Reintentar, onClick = onReintentar, modifier = Modifier.testTag(TurnoDetalleTags.REINTENTAR))
                        }
                    }
                    is EstadoCarga.Exito -> Detalle(state.datos, zona, ahora, pestanaInicial)
                }
            }
        }
    }
    if (puedeExportar && exportacion.hoja) {
        val datos = (state as? EstadoCarga.Exito)?.datos
        if (datos != null) exportar?.let { acciones -> HojaExportarTurno(datos, acciones) }
    }
}

/** 0.25.1: PDF o Excel del turno completo; cada formato se envía a otra app o se guarda en el teléfono. */
@Composable
private fun HojaExportarTurno(d: DetalleTurno, a: AccionesExportarTurno) {
    SpviBottomSheet(onDismiss = a.onCerrar, title = TextosTurno.COMPARTIR) {
        SpviSecondaryText(TextosTurno.alcanceExportar(d))
        listOf(FormatoExport.PDF, FormatoExport.XLSX).forEach { f ->
            val etiqueta = if (f == FormatoExport.PDF) "PDF" else "Excel"
            SpviListItem(
                title = etiqueta, indicatorColor = null,
                leading = { Icon(cu.spvi.app.common.iconoFormato(f), contentDescription = null, modifier = Modifier.size(SpviSize.icon)) },
                modifier = Modifier.testTag(TurnoDetalleTags.formato(f)),
                trailing = {
                    Row {
                        SpviIconAction(SpviIcons.Compartir, "Enviar $etiqueta a otra app", onClick = { a.onEnviar(f) }, modifier = Modifier.testTag(TurnoDetalleTags.enviar(f)))
                        SpviIconAction(SpviIcons.Exportar, "Guardar $etiqueta en el teléfono", onClick = { a.onGuardar(f) }, modifier = Modifier.testTag(TurnoDetalleTags.guardar(f)))
                    }
                },
            )
        }
    }
}

/** P24: el detalle tenía 7 tablas seguidas; ahora se agrupan en pestañas (Resumen · Ventas · Inventario). */
enum class PestanaTurno(val etiqueta: String) { RESUMEN("Resumen"), VENTAS("Ventas"), INVENTARIO("Inventario"), CAJA("Caja") }

@Composable
private fun Detalle(d: DetalleTurno, zona: ZoneId, ahora: Instant, pestanaInicial: PestanaTurno) {
    var pestana by rememberSaveable { mutableStateOf(pestanaInicial) }
    val orden = PestanaTurno.entries
    Column(Modifier.fillMaxSize()) {
        SpviTabs(
            tabs = listOf(
                SpviTab(PestanaTurno.RESUMEN.etiqueta, testTag = TurnoDetalleTags.pestana(PestanaTurno.RESUMEN)),
                SpviTab(PestanaTurno.VENTAS.etiqueta, count = d.ventas.size, testTag = TurnoDetalleTags.pestana(PestanaTurno.VENTAS)),
                SpviTab(
                    PestanaTurno.INVENTARIO.etiqueta, count = d.movimientosProducto.size + d.movimientosInsumo.size,
                    testTag = TurnoDetalleTags.pestana(PestanaTurno.INVENTARIO),
                ),
                // 0.25.0 (§5.2): arqueo de caja y entradas/salidas de efectivo.
                SpviTab(PestanaTurno.CAJA.etiqueta, count = d.caja.size.takeIf { it > 0 }, testTag = TurnoDetalleTags.pestana(PestanaTurno.CAJA)),
            ),
            selectedIndex = orden.indexOf(pestana),
            onSelect = { pestana = orden[it] },
            modifier = Modifier.fillMaxWidth().padding(horizontal = SpviSpacing.xs),
        )
        LazyColumn(
            Modifier.fillMaxSize().testTag(TurnoDetalleTags.LISTA),
            contentPadding = PaddingValues(SpviSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SpviSpacing.md),
        ) {
            when (pestana) {
                PestanaTurno.RESUMEN -> resumen(d, zona, ahora)
                PestanaTurno.VENTAS -> ventas(d, zona)
                PestanaTurno.INVENTARIO -> inventario(d, zona)
                PestanaTurno.CAJA -> caja(d, zona)
            }
        }
    }
}

private fun LazyListScope.resumen(d: DetalleTurno, zona: ZoneId, ahora: Instant) {
    val r = d.resumen
    val t = d.turno
    // P28: el total, una sola vez, centrado y arriba (como en Venta).
    item(key = "total") {
        Column(Modifier.fillMaxWidth().testTag(TurnoDetalleTags.TOTAL).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
            SpviSecondaryText("Total vendido", textAlign = TextAlign.Center)
            SpviTextoAjustable(Money.format(r.total), style = SpviTextos.datoEn(MaterialTheme.typography.headlineSmall), textAlign = TextAlign.Center)
        }
    }
    // P28: sin la fila «Abierto/Cerrado · 08:05 – 19:45»: repetía las horas de Apertura y Cierre. Que está abierto lo
    // dicen el color de la tarjeta y la nota de debajo; que está cerrado, la fila Cierre.
    item(key = "cabecera") {
        SpviCard(modifier = Modifier.testTag(TurnoDetalleTags.CABECERA), tone = if (t.abierto) CardTone.Highlight else CardTone.Default) {
            SpviListItem(title = "Apertura", subtitle = t.abiertoPor.ifBlank { null }, value = fechaHora(t.abiertoEn, zona), indicatorColor = null)
            t.cerradoEn?.let { c ->
                SpviListItem(title = "Cierre", subtitle = t.cerradoPor, value = fechaHora(c, zona), indicatorColor = null)
            }
            SpviListItem(title = "Duración", value = duracionTexto(t.duracion(ahora)), indicatorColor = null)
        }
    }
    if (d.provisional) {
        item(key = "provisional") {
            SpviSecondaryText(TextosTurno.PROVISIONAL, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag(TurnoDetalleTags.PROVISIONAL))
        }
    }
    item(key = "totales") {
        SpviCard(title = "Totales", titleCentered = true, modifier = Modifier.testTag(TurnoDetalleTags.TOTALES)) {
            // P28: sin «Ventas» ni «Movimientos de inventario»: los números ya están en las pestañas de arriba,
            // y el total vendido va arriba, centrado.
            SpviListItem(title = "Unidades vendidas", value = r.unidades.toString(), indicatorColor = null)
            SpviListItem(title = "Costo", value = Money.format(r.costo), indicatorColor = null)
            SpviListItem(
                title = "Ganancia neta", value = Money.format(r.ganancia), indicatorColor = null,
                valueColor = if (r.ganancia.centavos < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
    item(key = "metodos") {
        SpviCard(title = "Métodos de pago", titleCentered = true, modifier = Modifier.testTag(TurnoDetalleTags.METODOS)) {
            SpviListItem(title = etiqueta(MetodoPago.EFECTIVO), value = Money.format(r.totalEfectivo), indicatorColor = null)
            SpviListItem(title = etiqueta(MetodoPago.TRANSFERENCIA), value = Money.format(r.totalTransferencia), indicatorColor = null)
        }
    }
    if (d.vacio) {
        item(key = "vacio") { SpviSecondaryText(TextosTurno.SIN_ACTIVIDAD, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
}

private fun LazyListScope.ventas(d: DetalleTurno, zona: ZoneId) {
    if (d.ventas.isEmpty()) {
        item(key = "sin_ventas") { SpviEmptyState(title = TextosTurno.SIN_VENTAS, ilustracion = SpviIlustracion.Carrito) }
        return
    }
    seccion("vendidos", "Productos vendidos", TurnoDetalleTags.VENDIDOS, d.vendidos.isNotEmpty()) {
        d.vendidos.forEach { SpviListItem(title = it.nombre, value = unidadesTexto(it.unidades), indicatorColor = null) }
    }
    seccion("ventas", "Ventas", TurnoDetalleTags.VENTAS, true) {
        d.ventas.forEach { v ->
            // Los artículos ya no se ven en la fila (solo hora y total); TalkBack los sigue leyendo.
            // 0.25.0: la anulada sigue en la lista, marcada (no suma en los totales).
            SpviListItem(
                title = tituloVenta(v, zona), tituloEsDato = true, value = Money.format(v.total), indicatorColor = null,
                subtitle = v.anulacion?.let { "ANULADA · ${it.motivo}" } ?: v.corrigeVentaId?.let { "Corrige #$it" },
                valueColor = if (v.anulada) MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.ui.graphics.Color.Unspecified,
                subtitleMaxLines = 2,
                modifier = Modifier.semantics { stateDescription = (if (v.anulada) "Anulada. " else "") + subtituloVenta(v) },
            )
        }
    }
}

private fun LazyListScope.caja(d: DetalleTurno, zona: ZoneId) {
    item(key = "arqueo") {
        SpviCard(title = "Arqueo de caja", titleCentered = true, modifier = Modifier.testTag(TurnoDetalleTags.ARQUEO)) {
            cu.spvi.app.caja.TablaArqueo(d.arqueo)
        }
    }
    seccion("mov_caja", "Entradas y salidas de efectivo", TurnoDetalleTags.MOV_CAJA, d.caja.isNotEmpty()) {
        d.caja.forEach { m ->
            SpviListItem(
                title = m.motivo,
                subtitleResaltado = SpviTextos.resaltar(
                    listOf(cu.spvi.app.caja.TextosCaja.tipo(m.tipo), hora(m.fecha, zona), m.hechoPor).filter { it.isNotBlank() }.joinToString(" · "),
                    hora(m.fecha, zona),
                ),
                value = (if (m.tipo == cu.spvi.domain.model.TipoMovimientoCaja.SALIDA) "−" else "+") + Money.format(m.importe),
                indicatorColor = null, subtitleMaxLines = 2,
            )
        }
    }
}

private fun LazyListScope.inventario(d: DetalleTurno, zona: ZoneId) {
    if (d.insumos.isEmpty() && d.movimientosProducto.isEmpty() && d.movimientosInsumo.isEmpty()) {
        item(key = "sin_mov") { SpviEmptyState(title = TextosTurno.SIN_MOVIMIENTOS, ilustracion = SpviIlustracion.Inventario) }
        return
    }
    seccion("insumos", "Insumos (variación neta)", TurnoDetalleTags.INSUMOS, d.insumos.isNotEmpty()) {
        d.insumos.forEach { SpviListItem(title = it.nombre, value = cantidadInsumo(it.cantidad, it.simbolo), indicatorColor = null) }
    }
    seccion("mov_productos", "Movimientos de productos", TurnoDetalleTags.MOV_PRODUCTOS, d.movimientosProducto.isNotEmpty()) {
        d.movimientosProducto.forEach { m ->
            SpviListItem(
                title = m.nombre, subtitleResaltado = SpviTextos.resaltar(subtituloMovimiento(m, zona), hora(m.fecha, zona)),
                value = cantidadConSigno(m.delta, TipoEntidad.PRODUCTO), indicatorColor = null,
            )
        }
    }
    seccion("mov_insumos", "Movimientos de insumos", TurnoDetalleTags.MOV_INSUMOS, d.movimientosInsumo.isNotEmpty()) {
        val simbolos = d.insumos.associate { it.insumoId to it.simbolo }
        d.movimientosInsumo.forEach { m ->
            SpviListItem(
                title = m.nombre, subtitleResaltado = SpviTextos.resaltar(subtituloMovimiento(m, zona), hora(m.fecha, zona)),
                value = cantidadConSigno(m.delta, TipoEntidad.INSUMO, simbolos[m.entidadId].orEmpty()), indicatorColor = null,
            )
        }
    }
}

/** Sección en tarjeta; oculta si no hay datos. */
private fun LazyListScope.seccion(key: String, titulo: String, tag: String, visible: Boolean, content: @Composable () -> Unit) {
    if (!visible) return
    item(key = key) {
        SpviCard(title = titulo, titleCentered = true, modifier = Modifier.testTag(tag)) { content() }
    }
}
