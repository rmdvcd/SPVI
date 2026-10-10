package cu.spvi.app.inicio

import cu.spvi.designsystem.theme.SpviTextos
import cu.spvi.designsystem.component.BannerTone
import cu.spvi.app.notificacion.rememberPedirPermisoAviso
import androidx.compose.foundation.layout.Spacer
import cu.spvi.designsystem.component.SpviMedalla
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.spvi.app.navigation.Route
import cu.spvi.core.money.Cup
import cu.spvi.core.money.Money
import cu.spvi.core.money.Percent
import cu.spvi.core.time.Dates
import cu.spvi.designsystem.component.CardTone
import cu.spvi.designsystem.component.ChartSeries
import cu.spvi.designsystem.component.IconActionStyle
import cu.spvi.designsystem.component.SpviAccordionCard
import cu.spvi.designsystem.component.SpviAlertCounter
import cu.spvi.designsystem.component.SpviAreaChart
import cu.spvi.designsystem.component.SpviBarChart
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviComboBox
import cu.spvi.designsystem.component.SpviDialog
import cu.spvi.designsystem.component.SpviDonutChart
import cu.spvi.designsystem.component.SpviIconAction
import cu.spvi.designsystem.component.SpviListItem
import cu.spvi.designsystem.component.SpviLoading
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSecondaryButton
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviSnackbarHost
import cu.spvi.designsystem.component.SpviStatusBanner
import cu.spvi.designsystem.component.SpviTextoAjustable
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.component.spviAnimateItem
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.designsystem.token.SpviSize
import cu.spvi.designsystem.token.SpviSpacing
import cu.spvi.domain.model.GraficosPeriodo
import cu.spvi.domain.model.OpcionPeriodo
import cu.spvi.domain.model.ResumenGeneral
import cu.spvi.domain.model.TopPersona
import cu.spvi.domain.model.TopItem
import java.time.ZoneId
import kotlinx.coroutines.launch

object InicioTags {
    const val LISTA = "inicio_lista"
    const val BANNER = "inicio_banner"
    const val TURNO = "inicio_turno"
    const val CIERRE = "inicio_cierre_remoto"
    const val NUEVA_VENTA = "inicio_nueva_venta"
    const val SOLICITUD = "inicio_solicitud_cierre"
    const val SOLICITUDES = "inicio_solicitudes_cierre"
    const val APERTURAS = "inicio_aperturas"
    const val DESACTUALIZADAS = "inicio_desactualizadas"
    const val PERIODO = "inicio_periodo"
    const val PAGO = "inicio_pago"
    const val PRECIOS = "inicio_precios"
    const val VENTAS = "inicio_ventas"
    const val GANANCIA = "inicio_ganancia"
    const val INVENTARIO = "inicio_inventario"
    const val METODOS = "inicio_metodos"
    const val REINTENTAR = "inicio_reintentar"
    const val DIALOGO_VENTA = "inicio_dialogo_venta"
    const val RESPALDO = "inicio_respaldo"
    const val CAJA = "inicio_caja"
    const val ACTUALIZACION = "inicio_actualizacion"
    fun alerta(t: cu.spvi.domain.model.TipoAlerta) = "inicio_alerta_${t.name}"
    fun periodo(o: OpcionPeriodo) = "inicio_periodo_${o.name}"
    fun top(t: TipoTop) = "inicio_top_${t.name}"
    fun tipoVenta(t: TipoVenta) = "inicio_tipo_venta_${t.name}"
}

/**
 * Qué acordeones de Inicio están abiertos. Vive en [InicioContent] con `remember` (no `rememberSaveable`) y se crea
 * fuera del `LazyColumn`: el estado sobrevive al desplazamiento, pero al cambiar de ventana la pantalla sale de la
 * composición y todo vuelve a estar cerrado. [abiertosAlInicio] solo lo usan las capturas.
 */
@Stable
class AcordeonesInicio(private val abiertosAlInicio: Boolean = false) {
    private val estado = mutableStateMapOf<String, Boolean>()
    fun abierto(clave: String): Boolean = estado[clave] ?: abiertosAlInicio
    fun alternar(clave: String) { estado[clave] = !abierto(clave) }
}

class AccionesInicio(
    val onNavigate: (Route) -> Unit,
    val onTurno: (Boolean) -> Unit,
    val onNuevaVenta: () -> Unit,
    val onElegirVenta: (TipoVenta) -> Unit,
    val onConfirmarSolicitudCierre: (Cup) -> Unit = {},
    val onConfirmarCierre: (Cup) -> Unit,
    val onCerrarDialogo: () -> Unit,
    val onPeriodo: (OpcionPeriodo) -> Unit,
    val onPago: () -> Unit,
    val onReintentar: () -> Unit,
    /** 0.20.0 (H5). */
    val onDescartarAvisoCierre: () -> Unit = {},
    /** 0.25.0 (§5): apertura con fondo, entradas/salidas de efectivo y conteo del cierre pedido. */
    val onAbrirTurno: (Cup) -> Unit = {},
    val onMovimientoCaja: () -> Unit = {},
    val onDeclararContado: (Cup) -> Unit = {},
    /** 0.25.1 (D): «Ahora no» en el conteo y «Contar» en la tarjeta del cierre pedido. */
    val onPosponerConteo: () -> Unit = {},
    val onContar: () -> Unit = {},
    /** 0.25.0 (§6): tarjeta «Versión X disponible». */
    val onActualizar: () -> Unit = {},
    val onDescartarActualizacion: () -> Unit = {},
    val onCancelarDescarga: () -> Unit = {},
    /** 0.26.0 (P73 §4): secundaria sin fondo asignado. */
    val onPedirFondo: () -> Unit = {},
)

@Composable
fun InicioScreen(
    onNavigate: (Route) -> Unit,
    /** Aviso al volver de otra pantalla (p. ej. «Venta registrada: …»). */
    mensajeInicial: String? = null,
    onMensajeMostrado: () -> Unit = {},
    viewModel: InicioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(mensajeInicial) {
        if (mensajeInicial != null) { onMensajeMostrado(); snackbar.showSnackbar(mensajeInicial) }
    }
    val scope = rememberCoroutineScope()
    val pedirPermisoAviso = rememberPedirPermisoAviso()

    // Al volver (p. ej. tras una venta) se recalculan los gráficos. Nada se lee en segundo plano.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refrescar() }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoInicio.IrA -> onNavigate(e.route)
                is EventoInicio.Mensaje -> snackbar.showSnackbar(e.texto)
                is EventoInicio.TurnoCerrado -> scope.launch {
                    val r = snackbar.showSnackbar(e.texto, actionLabel = "Ver", withDismissAction = true, duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) onNavigate(Route.TurnoDetalle(e.turnoId))
                }
            }
        }
    }

    val permisos = cu.spvi.app.common.LocalPermisosApp.current
    // 0.21.0 (C6): en la principal, empleados que piden cerrar su turno (aviso que abre Apps vinculadas).
    val solicitudesVm: SolicitudesCierreViewModel = hiltViewModel()
    val solicitudes by solicitudesVm.pendientes.collectAsStateWithLifecycle()
    val aperturas by solicitudesVm.aperturas.collectAsStateWithLifecycle()
    val desactualizadas by solicitudesVm.desactualizadas.collectAsStateWithLifecycle()
    // 0.25.0 (§4, §5): caja y recordatorio de respaldo en un ViewModel aparte.
    val cajaVm: cu.spvi.app.caja.CajaViewModel = hiltViewModel()
    val caja by cajaVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(cajaVm) { cajaVm.eventos.collect { snackbar.showSnackbar(it) } }
    val actVm: cu.spvi.app.actualizacion.ActualizacionViewModel = hiltViewModel()
    val actualizacion by actVm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(actVm) { actVm.eventos.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(state.dialogo) { if (state.dialogo == DialogoInicio.AbrirTurno) cajaVm.cargarSugerido() }
    if (caja.dialogoMovimiento) cu.spvi.app.caja.DialogoMovimientoCaja(
        trabajando = caja.trabajando, onConfirmar = cajaVm::registrarMovimiento, onDismiss = cajaVm::cerrarMovimiento,
    )
    InicioContent(
        state = state,
        acciones = AccionesInicio(
            onNavigate = onNavigate,
            onTurno = { abrir -> if (abrir) pedirPermisoAviso(); viewModel.cambiarTurno(abrir) },
            // 0.21.0 (C12): con un solo tipo de venta permitido se va directo, sin el diálogo.
            onNuevaVenta = { tiposVenta(permisos).singleOrNull()?.let(viewModel::elegirVenta) ?: viewModel.nuevaVenta() },
            onElegirVenta = viewModel::elegirVenta,
            onConfirmarCierre = viewModel::confirmarCierreTurno,
            onCerrarDialogo = viewModel::cerrarDialogo,
            onPeriodo = viewModel::elegirPeriodo,
            onPago = { onNavigate(Route.PagoElectronico) }, // P28: la misma pantalla que en Ajustes
            onReintentar = viewModel::refrescar,
            onDescartarAvisoCierre = viewModel::descartarAvisoCierre,
            onConfirmarSolicitudCierre = viewModel::confirmarSolicitudCierre,
            onAbrirTurno = viewModel::abrirTurnoCon,
            onMovimientoCaja = cajaVm::abrirMovimiento,
            onDeclararContado = viewModel::declararContado,
            onPosponerConteo = viewModel::posponerConteo,
            onContar = viewModel::contarAhora,
            onActualizar = actVm::actualizar,
            onDescartarActualizacion = actVm::descartar,
            onCancelarDescarga = actVm::cancelar,
            onPedirFondo = { cajaVm.pedirFondo(); viewModel.cerrarDialogo() },
        ),
        actualizacion = actualizacion,
        solicitudesCierre = solicitudes,
        aperturas = aperturas,
        desactualizadas = desactualizadas,
        caja = caja,
        snackbar = snackbar,
    )
}

/** Contenido sin estado (testeable sin Hilt). */
@Composable
fun InicioContent(
    state: InicioUiState,
    acciones: AccionesInicio,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    zona: ZoneId = ZoneId.systemDefault(),
    solicitudesCierre: Int = 0,
    caja: cu.spvi.app.caja.CajaUiState = cu.spvi.app.caja.CajaUiState(),
    actualizacion: cu.spvi.app.actualizacion.EstadoActualizacion = cu.spvi.app.actualizacion.EstadoActualizacion(),
    /** 0.26.0 (P73 §4): en la principal, empleados que piden el fondo y apps 0.25.x por actualizar. */
    aperturas: List<String> = emptyList(),
    desactualizadas: List<String> = emptyList(),
    /** Solo para capturas: abre todos los acordeones (en la app empiezan siempre cerrados). */
    acordeonesAbiertos: Boolean = false,
) {
    val permisos = cu.spvi.app.common.LocalPermisosApp.current
    val acordeones = remember { AcordeonesInicio(acordeonesAbiertos) }
    // 0.27.0 (T2): con letra grande caben menos alertas por fila (ancho de pantalla menos el relleno lateral).
    val maxAlertas = alertasPorFila(
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 2 * SpviSpacing.md.value,
        androidx.compose.ui.platform.LocalDensity.current.fontScale,
    )
    Scaffold(
        topBar = {
            Column {
                SpviTopBar(title = "SPVI", marca = true)
                Column(Modifier.padding(horizontal = SpviSpacing.md), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                    TurnoContenedor(state, acciones, zona, ajustar = false)
                    if (!permisos.esSecundaria) SelectorPeriodo(state.opcion, acciones.onPeriodo)
                }
            }
        },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center,
        floatingActionButton = {
            if (state.turnoAbierto && permisos.vender) {
                Row(
                    Modifier.fillMaxWidth().padding(SpviSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) { AccionesVenta(state, acciones) }
            }
        },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag(InicioTags.LISTA),
            contentPadding = PaddingValues(start = SpviSpacing.md, end = SpviSpacing.md, top = SpviSpacing.md, bottom = SpviSize.touchTarget + SpviSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SpviSpacing.md),
        ) {
            state.banner?.let { texto ->
                item(key = "banner") {
                    SpviStatusBanner(
                        text = texto,
                        tone = state.bannerTono,
                        detail = detalleBanner(state.bannerTono),
                        onClick = { acciones.onNavigate(Route.Licencia) },
                        modifier = Modifier.testTag(InicioTags.BANNER).then(spviAnimateItem()),
                    )
                }
            }
            // 0.20.0 (H5): el encargado cerró (o pidió cerrar) el turno desde la app principal.
            if (state.cierrePedido || state.avisoCierre) {
                item(key = "cierre") {
                    SpviStatusBanner(
                        text = if (state.cierrePedido) TextosInicio.CIERRE_PEDIDO else TextosInicio.CIERRE_HECHO,
                        tone = BannerTone.Aviso,
                        // 0.25.1 (D): con el conteo pospuesto, la tarjeta ofrece «Contar» (también al tocarla).
                        detail = if (state.cierrePedido && state.conteoPospuesto && state.pideConteo) TextosInicio.CONTAR_PENDIENTE else null,
                        actionIcon = when {
                            state.cierrePedido && state.conteoPospuesto && state.pideConteo -> SpviIcons.Efectivo
                            state.cierrePedido -> null
                            else -> SpviIcons.Cancelar
                        },
                        actionDescription = when {
                            state.cierrePedido && state.conteoPospuesto && state.pideConteo -> TextosInicio.CONTAR
                            state.cierrePedido -> null
                            else -> "Cerrar aviso"
                        },
                        onAction = when {
                            state.cierrePedido && state.conteoPospuesto && state.pideConteo -> acciones.onContar
                            state.cierrePedido -> null
                            else -> acciones.onDescartarAvisoCierre
                        },
                        onClick = if (state.cierrePedido && state.conteoPospuesto && state.pideConteo) acciones.onContar else null,
                        modifier = Modifier.testTag(InicioTags.CIERRE).then(spviAnimateItem()),
                    )
                }
            }
            // 0.21.0 (C6): solicitud de cierre del empleado (secundaria) y su respuesta; en la principal, las pendientes.
            if (state.cierreSolicitado || state.cierreRechazado) {
                item(key = "solicitud") {
                    SpviStatusBanner(
                        text = if (state.cierreRechazado) TextosInicio.CIERRE_RECHAZADO else TextosInicio.CIERRE_SOLICITADO,
                        tone = BannerTone.Aviso,
                        actionIcon = if (state.cierreRechazado) SpviIcons.Cancelar else null,
                        actionDescription = if (state.cierreRechazado) "Cerrar aviso" else null,
                        onAction = if (state.cierreRechazado) acciones.onDescartarAvisoCierre else null,
                        modifier = Modifier.testTag(InicioTags.SOLICITUD).then(spviAnimateItem()),
                    )
                }
            }
            if (solicitudesCierre > 0 && !permisos.esSecundaria) {
                item(key = "solicitudes") {
                    SpviStatusBanner(
                        text = TextosInicio.solicitudesCierre(solicitudesCierre),
                        tone = BannerTone.Aviso,
                        onClick = { acciones.onNavigate(Route.Vinculacion) },
                        modifier = Modifier.testTag(InicioTags.SOLICITUDES).then(spviAnimateItem()),
                    )
                }
            }
            if (aperturas.isNotEmpty() && !permisos.esSecundaria) {
                item(key = "aperturas") {
                    SpviStatusBanner(
                        text = TextosInicio.aperturas(aperturas),
                        tone = BannerTone.Aviso,
                        onClick = { acciones.onNavigate(Route.Vinculacion) },
                        modifier = Modifier.testTag(InicioTags.APERTURAS).then(spviAnimateItem()),
                    )
                }
            }
            if (desactualizadas.isNotEmpty() && !permisos.esSecundaria) {
                item(key = "desactualizadas") {
                    SpviStatusBanner(
                        text = TextosInicio.desactualizadas(desactualizadas),
                        tone = BannerTone.Info,
                        onClick = { acciones.onNavigate(Route.Vinculacion) },
                        modifier = Modifier.testTag(InicioTags.DESACTUALIZADAS).then(spviAnimateItem()),
                    )
                }
            }
            // 0.25.0 (§4): recordatorio mensual de respaldo (solo principal); al tocarlo se abre Respaldo.
            caja.avisoRespaldo?.let { a ->
                item(key = "respaldo") {
                    SpviStatusBanner(
                        text = TextosInicio.avisoRespaldo(a.dias),
                        tone = BannerTone.Info,
                        onClick = { acciones.onNavigate(Route.Respaldo) },
                        modifier = Modifier.testTag(InicioTags.RESPALDO).then(spviAnimateItem()),
                    )
                }
            }
            if (actualizacion.tarjetaVisible) item(key = "actualizacion") { TarjetaActualizacion(actualizacion, acciones) }
            // 0.21.0 (C12): sin Ventas ni Inventario no hay alertas de existencias.
            if (permisos.verInventario) alertas(state.alertas, acciones, maxAlertas)
            // 0.21.0 (C9): en la secundaria, solo turno, Nueva venta y alertas; los accesos y los gráficos son del dueño.
            if (!permisos.esSecundaria) item(key = "accesos") {
                val pago: @Composable (Modifier) -> Unit = { m ->
                    // Teléfono y cuenta/tarjeta en seminegrita (solo los números, no «Tel.» ni «Cuenta»).
                    Acceso(
                        "Pago electrónico",
                        SpviTextos.resaltar(state.pago.texto, state.pago.telefono.orEmpty(), state.pago.cuenta.orEmpty()),
                        SpviIcons.PagoElectronico, acciones.onPago, m.testTag(InicioTags.PAGO),
                    )
                }
                val precios: @Composable (Modifier) -> Unit = { m ->
                    Acceso(
                        "Precios", androidx.compose.ui.text.AnnotatedString(textoPrecios(state.preajustesActivos, state.preajustesTotal)), SpviIcons.Precios,
                        { acciones.onNavigate(Route.Precios) }, m.testTag(InicioTags.PRECIOS),
                    )
                }
                // 0.27.0 (T2): con letra muy grande (1 alerta por fila) las dos tarjetas van una debajo de otra
                // para no partir «electrónico».
                if (maxAlertas < 2) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SpviSpacing.md)) {
                        pago(Modifier.fillMaxWidth())
                        precios(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(SpviSpacing.md)) {
                        pago(Modifier.weight(1f).fillMaxHeight())
                        precios(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            if (!permisos.esSecundaria) {
                graficosPeriodo(state.graficos, state.opcion, zona, acciones.onReintentar, acordeones)
                resumenGeneral(state.resumen, acciones.onReintentar, acordeones)
            }
        }
    }

    when (state.dialogo) {
        DialogoInicio.NuevaVenta -> NuevaVentaDialog(state.turnoAbierto, acciones, tiposVenta(permisos))
        DialogoInicio.AbrirTurno -> cu.spvi.app.caja.DialogoFondo(
            sugerido = caja.sugerido, trabajando = state.cambiandoTurno,
            onConfirmar = acciones.onAbrirTurno, onDismiss = acciones.onCerrarDialogo,
            fondo = caja.fondo, onPedirFondo = acciones.onPedirFondo,
        )
        DialogoInicio.CerrarTurno -> cu.spvi.app.caja.DialogoContado(
            titulo = TextosInicio.CERRAR_TURNO_TITULO,
            texto = TextosInicio.CERRAR_TURNO_TEXTO,
            confirmar = "Cerrar turno",
            arqueo = state.arqueo,
            trabajando = state.cambiandoTurno,
            onConfirmar = acciones.onConfirmarCierre,
            onDismiss = acciones.onCerrarDialogo,
        )
        DialogoInicio.SolicitarCierre -> cu.spvi.app.caja.DialogoContado(
            titulo = TextosInicio.SOLICITAR_CIERRE_TITULO,
            texto = TextosInicio.SOLICITAR_CIERRE_TEXTO,
            confirmar = "Solicitar cierre",
            arqueo = state.arqueo,
            trabajando = false,
            onConfirmar = acciones.onConfirmarSolicitudCierre,
            onDismiss = acciones.onCerrarDialogo,
        )
        null -> if (state.debeContar) cu.spvi.app.caja.DialogoContado(
            titulo = TextosInicio.CONTAR_TITULO,
            texto = TextosInicio.CONTAR_TEXTO,
            confirmar = "Enviar conteo",
            arqueo = state.arqueo,
            trabajando = false,
            onConfirmar = acciones.onDeclararContado,
            // 0.25.1 (D): «Ahora no» lo oculta; sin conteo el cierre no se aplica y vuelve al regresar a Inicio o a los 15 min.
            onDismiss = acciones.onPosponerConteo,
            dismissDescription = TextosInicio.AHORA_NO,
        )
    }
}

// ---------------- Actualización (0.25.0, §6) ----------------

@Composable
private fun TarjetaActualizacion(a: cu.spvi.app.actualizacion.EstadoActualizacion, acciones: AccionesInicio) {
    val T = cu.spvi.app.actualizacion.TextosActualizacion
    SpviCard(tone = CardTone.Tonal, modifier = Modifier.testTag(InicioTags.ACTUALIZACION)) {
        Text(
            if (a.desdePrincipal != null) T.desdePrincipal(a.version.orEmpty()) else T.disponible(a.version.orEmpty(), a.bytes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        // 0.26.0 (§6): fecha límite en seminegrita (dato).
        a.limite?.let { Text(T.obligatoriaDesde(it), style = SpviTextos.datoEn(MaterialTheme.typography.bodyMedium)) }
        a.disponible?.notas?.takeIf { a.desdePrincipal == null }?.let(T::notasVisibles)?.takeIf { it.isNotBlank() }?.let { SpviSecondaryText(it.take(400), maxLines = 4, textAlign = TextAlign.Start) }
        if (a.progreso != null) {
            SpviSecondaryText(T.DESCARGANDO + " ${(a.progreso * 100).toInt()} %", textAlign = TextAlign.Start)
            cu.spvi.designsystem.component.SpviLinearProgress(modifier = Modifier.fillMaxWidth(), progress = a.progreso)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = SpviSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(SpviSpacing.lg, Alignment.CenterHorizontally),
        ) {
            if (a.progreso == null) {
                SpviPrimaryButton(T.ACTUALIZAR, onClick = acciones.onActualizar, icon = SpviIcons.Importar)
                // 0.26.0: «Más tarde» oculta el aviso hasta mañana (también en la secundaria); el plazo no cambia.
                SpviSecondaryButton(T.DESCARTAR, onClick = acciones.onDescartarActualizacion, icon = SpviIcons.AhoraNo)
            } else {
                SpviSecondaryButton(T.CANCELAR, onClick = acciones.onCancelarDescarga, icon = SpviIcons.Cancelar)
            }
        }
    }
}

// ---------------- Turno y Nueva venta ----------------


/** «Nueva venta» (solo icono + texto) y, con el turno abierto, entrada / salida de efectivo (0.25.0, §5.2). */
@Composable
private fun AccionesVenta(state: InicioUiState, acciones: AccionesInicio) {
    SpviPrimaryButton(
        "Nueva venta", onClick = acciones.onNuevaVenta, icon = SpviIcons.Venta, enabled = state.puedeEmpezarVenta,
        loading = state.cambiandoTurno, modifier = Modifier.heightIn(min = SpviSize.touchTarget).testTag(InicioTags.NUEVA_VENTA),
    )
    if (state.turnoAbierto) SpviIconAction(
        SpviIcons.Efectivo, cu.spvi.app.caja.TextosCaja.MOVIMIENTO_TITULO, onClick = acciones.onMovimientoCaja,
        style = IconActionStyle.Tonal, enabled = !state.cambiandoTurno, modifier = Modifier.testTag(InicioTags.CAJA),
    )
}

/** Estado del turno con su interruptor; toda la fila es el interruptor. [ajustar] = ancho del contenido. */
@Composable
private fun TurnoContenedor(state: InicioUiState, acciones: AccionesInicio, zona: ZoneId, ajustar: Boolean) {
    val abierto = state.turnoAbierto
    SpviCard(ajustarAlContenido = ajustar, modifier = Modifier.testTag(InicioTags.TURNO)) {
        Row(
            (if (ajustar) Modifier else Modifier.fillMaxWidth())
                .toggleable(value = abierto, enabled = !state.cambiandoTurno, role = Role.Switch, onValueChange = acciones.onTurno),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
        ) {
            Icon(SpviIcons.Turno, contentDescription = null, tint = if (abierto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f, fill = !ajustar).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(if (abierto) TextosInicio.TURNO_ABIERTO else TextosInicio.TURNO_CERRADO, style = MaterialTheme.typography.titleMedium)
                SpviSecondaryText(
                    state.turno?.takeIf { abierto }?.let { t ->
                        // 0.28.0: la hora («Desde las 08:30») va en seminegrita.
                        SpviTextos.resaltar("Desde las ${Dates.time(t.abiertoEn, zona)}", Dates.time(t.abiertoEn, zona))
                    } ?: SpviTextos.resaltar("Ábrelo para vender"),
                    textAlign = TextAlign.Start,
                )
                // 0.25.0 (§5.2): efectivo esperado del turno abierto.
                state.arqueo?.takeIf { abierto }?.let { a ->
                    SpviSecondaryText(
                        SpviTextos.resaltar(
                            cu.spvi.app.caja.TextosCaja.esperado(a.esperado), Money.format(a.esperado),
                        ),
                        textAlign = TextAlign.Start,
                    )
                }
            }
            Switch(checked = abierto, onCheckedChange = null, enabled = !state.cambiandoTurno)
        }
    }
}

@Composable
private fun NuevaVentaDialog(turnoAbierto: Boolean, acciones: AccionesInicio, tipos: List<TipoVenta>) {
    SpviDialog(
        title = "Nueva venta",
        onDismiss = acciones.onCerrarDialogo,
        onConfirm = null,
        modifier = Modifier.testTag(InicioTags.DIALOGO_VENTA),
    ) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            if (!turnoAbierto) SpviSecondaryText(TextosInicio.AVISO_TURNO_CERRADO)
            tipos.forEach { t ->
                SpviListItem(
                    title = t.etiqueta,
                    subtitle = t.detalle,
                    indicatorColor = null,
                    leading = { Icon(if (t == TipoVenta.VENTA) SpviIcons.Venta else SpviIcons.Servicios, contentDescription = null) },
                    onClick = { acciones.onElegirVenta(t) },
                    modifier = Modifier.testTag(InicioTags.tipoVenta(t)),
                )
            }
        }
    }
}

// ---------------- Alertas ----------------

private fun LazyListScope.alertas(alertas: List<AlertaUi>, acciones: AccionesInicio, maxPorFila: Int) {
    if (alertas.isEmpty()) return
    item(key = "alertas_titulo") { Seccion("Alertas de inventario") }
    // 0.27.0 (T14): reparto equitativo (1, 2, 3, 2+2, 3+2, 3+3…); en cada fila, mismo ancho y misma altura. Una fila
    // incompleta queda centrada con el mismo ancho por tarjeta que la fila más llena.
    val reparto = repartoAlertas(alertas.size, maxPorFila)
    val porFila = reparto.firstOrNull() ?: 1
    var desde = 0
    reparto.forEachIndexed { fila, cuantas ->
        val grupo = alertas.subList(desde, desde + cuantas)
        desde += cuantas
        item(key = "alertas_$fila") {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).then(spviAnimateItem()),
                horizontalArrangement = Arrangement.spacedBy(SpviSpacing.md, Alignment.CenterHorizontally),
            ) {
                val hueco = (porFila - cuantas) / 2f
                if (hueco > 0f) Spacer(Modifier.weight(hueco))
                grupo.forEach { a ->
                    SpviAlertCounter(
                        label = a.etiqueta, count = a.cantidad, tone = a.tono,
                        onClick = { acciones.onNavigate(destinoAlerta(a.tipo)) },
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag(InicioTags.alerta(a.tipo)),
                    )
                }
                if (hueco > 0f) Spacer(Modifier.weight(hueco))
            }
        }
    }
}

@Composable
private fun Acceso(titulo: String, detalle: androidx.compose.ui.text.AnnotatedString, icono: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    SpviCard(onClick = onClick, tone = CardTone.Tonal, modifier = modifier) {
        Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        // 0.27.0 (T2): con letra grande el texto pasa a varias líneas y la tarjeta crece.
        Text(titulo, style = MaterialTheme.typography.titleSmall)
        SpviSecondaryText(detalle)
    }
}

// ---------------- Período ----------------

@Composable
private fun SelectorPeriodo(opcion: OpcionPeriodo, onPeriodo: (OpcionPeriodo) -> Unit) {
    // P24: combobox (una fila, se ve la opción elegida; el resto al abrir).
    SpviComboBox(
        label = "Período",
        opciones = OpcionPeriodo.entries,
        seleccion = opcion,
        etiqueta = { it.etiqueta() },
        onSeleccion = onPeriodo,
        leadingIcon = SpviIcons.Fecha,
        tagOpcion = { InicioTags.periodo(it) },
        modifier = Modifier.fillMaxWidth().testTag(InicioTags.PERIODO),
    )
}

// ---------------- Gráficos del período ----------------

private fun LazyListScope.graficosPeriodo(
    estado: EstadoCarga<GraficosPeriodo>, opcion: OpcionPeriodo, zona: ZoneId, onReintentar: () -> Unit, ac: AcordeonesInicio,
) {
    when (estado) {
        EstadoCarga.Idle, EstadoCarga.Cargando -> {
            item(key = "ventas") { CardGrafico("Ventas", InicioTags.VENTAS, ac) { Cargando() } }
            item(key = "ganancia") { CardGrafico("Ganancia Neta", InicioTags.GANANCIA, ac) { Cargando() } }
        }
        is EstadoCarga.Error -> item(key = "graficos_error") { CardError(estado.mensaje, onReintentar) }
        is EstadoCarga.Vacio -> {
            val g = estado.datos
            item(key = "ventas") {
                CardGrafico("Ventas", InicioTags.VENTAS, ac) {
                    g?.let { Subtitulo(descripcionPeriodo(it, opcion, zona)) }
                    Vacio(TextosInicio.SIN_VENTAS)
                }
            }
            item(key = "ganancia") { CardGrafico("Ganancia Neta", InicioTags.GANANCIA, ac) { Vacio(TextosInicio.SIN_VENTAS) } }
        }
        is EstadoCarga.Exito -> {
            val g = estado.datos
            item(key = "ventas") { VentasCard(g, opcion, zona, ac) }
            item(key = "ganancia") { GananciaCard(g, ac) }
        }
    }
}

@Composable
private fun VentasCard(g: GraficosPeriodo, opcion: OpcionPeriodo, zona: ZoneId, ac: AcordeonesInicio) {
    val puntos = g.serie.puntos
    CardGrafico("Ventas", InicioTags.VENTAS, ac) {
        // 0.28.0: la hora o fecha del turno («Turno actual, desde las 08:30») va en seminegrita.
        val t = g.turno
        Subtitulo(
            SpviTextos.resaltar(
                descripcionPeriodo(g, opcion, zona),
                t?.let { Dates.time(it.abiertoEn, zona) }.orEmpty(),
                t?.let { Dates.dayMonthTime(it.abiertoEn, zona) }.orEmpty(),
                t?.cerradoEn?.let { Dates.time(it, zona) }.orEmpty(),
            ),
        )
        SpviTextoAjustable(Money.format(g.totalVentas), style = SpviTextos.datoEn(MaterialTheme.typography.titleLarge), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        SpviBarChart(
            values = puntos.map { it.ventas.enPesos() },
            labels = puntos.map { etiquetaPunto(it.inicio, g.serie.granularidad, zona) },
            description = descripcionVentas(g, zona),
        )
    }
}

@Composable
private fun GananciaCard(g: GraficosPeriodo, ac: AcordeonesInicio) {
    val colores = SpviTheme.colors.chart
    val puntos = g.serie.puntos
    CardGrafico("Ganancia Neta", InicioTags.GANANCIA, ac) {
        // P24/P28: dos columnas del mismo ancho (Costo y Ganancia); el importe se reduce si no cabe, nunca se parte.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            // P28: sin «Venta»: es el mismo total que muestra la tarjeta Ventas justo encima.
            Dato("Costo", Money.format(g.totalCosto), Modifier.weight(1f))
            Dato("Ganancia", Money.format(g.ganancia), Modifier.weight(1f))
        }
        g.margen?.let { Subtitulo("Margen ${Percent.format(it).removePrefix("+")}") }
        SpviAreaChart(
            series = listOf(
                ChartSeries("Venta", puntos.map { it.ventas.enPesos() }, colores[0]),
                ChartSeries("Costo", puntos.map { it.costo.enPesos() }, colores[1]),
            ),
            labels = emptyList(),
            description = descripcionGanancia(g),
        )
    }
}

// ---------------- Resumen general (sin selector) ----------------

private fun LazyListScope.resumenGeneral(estado: EstadoCarga<ResumenGeneral>, onReintentar: () -> Unit, ac: AcordeonesInicio) {
    when (estado) {
        EstadoCarga.Idle, EstadoCarga.Cargando -> {
            item(key = "inventario") { CardGrafico("Inventario", InicioTags.INVENTARIO, ac) { Cargando() } }
            item(key = "metodos") { CardGrafico("Métodos de pago", InicioTags.METODOS, ac) { Cargando() } }
        }
        is EstadoCarga.Error -> item(key = "resumen_error") { CardError(estado.mensaje, onReintentar) }
        is EstadoCarga.Vacio -> estado.datos?.let { resumenCon(it, ac) } ?: item(key = "inventario") {
            CardGrafico("Inventario", InicioTags.INVENTARIO, ac) { Vacio(TextosInicio.SIN_INVENTARIO) }
        }
        is EstadoCarga.Exito -> resumenCon(estado.datos, ac)
    }
}

private fun LazyListScope.resumenCon(r: ResumenGeneral, ac: AcordeonesInicio) {
    item(key = "inventario") {
        CardGrafico("Inventario", InicioTags.INVENTARIO, ac) {
            if (r.categorias.isEmpty()) Vacio(TextosInicio.SIN_INVENTARIO) else {
                // 0.27.0 (N1): valores en milésimas (productos e insumos, cada uno en su medida) → números sin unidad.
                val slices = porcionesUi(r.categorias) { cantidadInventario(it) }
                Subtitulo(TextosInicio.EXISTENCIAS_POR_CATEGORIA)
                SpviDonutChart(slices, descripcionDona("inventario por categorías", slices), centerText = cantidadInventario(r.categorias.sumOf { it.valor }))
            }
        }
    }
    item(key = "metodos") {
        CardGrafico("Métodos de pago", InicioTags.METODOS, ac) {
            Subtitulo("Últimos ${r.dias} días")
            if (r.metodosPago.isEmpty()) Vacio(TextosInicio.SIN_PAGOS) else {
                val slices = porcionesUi(r.metodosPago) { Money.format(Cup(it)) }
                SpviDonutChart(slices, descripcionDona("métodos de pago", slices))
            }
        }
    }
    top(TipoTop.MAS_VENDIDO, r.top3.masVendidos, r.dias, ac)
    top(TipoTop.LENTO, r.top3.lentoMovimiento, r.dias, ac)
    top(TipoTop.RENTABILIDAD, r.top3.rentabilidad, r.dias, ac)
    // P29: indicadores propios de los servicios (ocultos si no se vendió ninguno en el período).
    top(TipoTop.SERVICIO_TOP, r.servicios.masVendidos, r.dias, ac)
    top(TipoTop.SERVICIO_MENOS, r.servicios.menosVendidos, r.dias, ac)
    topPersonas(TipoTop.EMPLEADO, r.empleados, r.dias, "venta", "ventas", ac)
    topPersonas(TipoTop.CLIENTE, r.clientes, r.dias, "compra", "compras", ac)
}

/** Top 3: oculto si no hay datos (SPVI.txt). */
private fun LazyListScope.top(tipo: TipoTop, items: List<TopItem>, dias: Int, ac: AcordeonesInicio) {
    if (items.isEmpty()) return
    item(key = "top_${tipo.name}") {
        SpviAccordionCard(
            title = tipo.titulo, expanded = ac.abierto(InicioTags.top(tipo)), onToggle = { ac.alternar(InicioTags.top(tipo)) },
            modifier = Modifier.testTag(InicioTags.top(tipo)).then(spviAnimateItem()),
        ) {
            Subtitulo("Últimos $dias días")
            items.forEachIndexed { i, it ->
                SpviListItem(
                    title = it.nombre,
                    subtitle = subtituloTop(tipo, it),
                    value = valorTop(tipo, it),
                    indicatorColor = null,
                    // 0.27.0 (T3): medalla oro, plata o bronce con el número del puesto.
                    leading = { puestoTop(i)?.let { p -> SpviMedalla(p) } },
                )
            }
        }
    }
}

/** Top 3 de personas (empleados y clientes): oculto si no hay datos. */
private fun LazyListScope.topPersonas(tipo: TipoTop, items: List<TopPersona>, dias: Int, singular: String, plural: String, ac: AcordeonesInicio) {
    if (items.isEmpty()) return
    item(key = "top_${tipo.name}") {
        SpviAccordionCard(
            title = tipo.titulo, expanded = ac.abierto(InicioTags.top(tipo)), onToggle = { ac.alternar(InicioTags.top(tipo)) },
            modifier = Modifier.testTag(InicioTags.top(tipo)).then(spviAnimateItem()),
        ) {
            Subtitulo("Últimos $dias días")
            items.forEachIndexed { i, it ->
                SpviListItem(
                    title = it.nombre,
                    subtitle = if (it.operaciones == 1) "1 $singular" else "${it.operaciones} $plural",
                    value = Money.format(it.importe),
                    indicatorColor = null,
                    leading = { puestoTop(i)?.let { p -> SpviMedalla(p) } },
                )
            }
        }
    }
}

// ---------------- Piezas comunes ----------------

@Composable
private fun CardGrafico(titulo: String, tag: String, ac: AcordeonesInicio, content: @Composable () -> Unit) {
    // Acordeón cerrado de inicio; la clave es el tag (único por tarjeta).
    SpviAccordionCard(title = titulo, expanded = ac.abierto(tag), onToggle = { ac.alternar(tag) }, modifier = Modifier.testTag(tag)) { content() }
}

@Composable
private fun Subtitulo(texto: String) = SpviSecondaryText(texto, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

@Composable
private fun Subtitulo(texto: androidx.compose.ui.text.AnnotatedString) =
    SpviSecondaryText(texto, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

@Composable
private fun Vacio(texto: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = SpviSpacing.lg), contentAlignment = Alignment.Center) {
        SpviSecondaryText(texto, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Cargando() {
    Box(Modifier.fillMaxWidth().height(SpviSize.chartHeight), contentAlignment = Alignment.Center) { SpviLoading() }
}

@Composable
private fun CardError(mensaje: String, onReintentar: () -> Unit) {
    SpviCard(tone = CardTone.Tonal) {
        Text(
            mensaje, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
        SpviSecondaryButton(
            "Reintentar", onClick = onReintentar, icon = SpviIcons.Reintentar,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag(InicioTags.REINTENTAR),
        )
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SpviSecondaryText(etiqueta, textAlign = TextAlign.Center)
        SpviTextoAjustable(valor, style = SpviTextos.dato, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Seccion(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
}
