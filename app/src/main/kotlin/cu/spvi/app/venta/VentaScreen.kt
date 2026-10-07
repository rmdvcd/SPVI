package cu.spvi.app.venta

import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import cu.spvi.domain.model.nombreCompleto
import cu.spvi.app.common.LocalPermisosApp
import cu.spvi.designsystem.component.FiltroEntrada
import cu.spvi.designsystem.component.BannerTone
import cu.spvi.designsystem.component.SpviStatusBanner
import cu.spvi.app.notificacion.rememberPedirPermisoAviso
import cu.spvi.designsystem.component.SpviTextoAjustable
import cu.spvi.designsystem.component.SpviBarraAcciones
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.spvi.app.common.SecureWindow
import cu.spvi.app.inicio.TipoVenta
import cu.spvi.app.registros.TextosTurno
import cu.spvi.app.registros.hora
import cu.spvi.core.money.Money
import cu.spvi.designsystem.component.CardTone
import cu.spvi.designsystem.component.IconActionStyle
import cu.spvi.designsystem.component.SpviButtonRow
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviChip
import cu.spvi.designsystem.component.SpviDialog
import cu.spvi.designsystem.component.SpviEmptyState
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
import cu.spvi.designsystem.component.rememberSpviHaptics
import cu.spvi.designsystem.component.spviAnimateItem
import cu.spvi.designsystem.component.spviContentWidth
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.theme.SpviTextos
import cu.spvi.designsystem.ilustracion.SpviIlustracion
import cu.spvi.designsystem.token.SpviSize
import cu.spvi.designsystem.token.SpviSpacing
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.service.PagoQr
import cu.spvi.domain.usecase.ObservarPermisoVenta.Permiso
import java.time.ZoneId

object VentaTags {
    const val BLOQUEO = "venta_bloqueo"
    const val ABRIR_TURNO = "venta_abrir_turno"
    const val CARRITO = "venta_carrito"
    const val CONTINUAR = "venta_continuar"
    const val COMPROBANTE = "venta_comprobante"
    const val CONFIRMAR = "venta_confirmar"
    const val CANCELAR = "venta_cancelar"
    const val QR = "venta_qr"
    const val PAGO_RECIBIDO = "venta_pago_recibido"
    const val PEGAR_SMS = "venta_pegar_sms"
    const val ERROR = "venta_error"
    const val TOTAL = "venta_total"
    const val TURNO = "venta_turno"
    fun metodo(m: MetodoPago) = "venta_metodo_${m.name}"
    fun mas(id: Long) = "venta_mas_$id"
    fun menos(id: Long) = "venta_menos_$id"
    fun cantidad(id: Long) = "venta_cantidad_$id"
    fun campo(c: CampoCliente) = "venta_campo_${c.name}"
    fun sugerencia(ci: String) = "venta_sugerencia_$ci"
    const val CLIENTE_FIJO = "venta_cliente_fijo"
}

/** Acciones de la pantalla (una clase evita 20 lambdas sueltas en cada firma). */
class AccionesVenta(
    val onAtras: () -> Unit = {},
    val onAbrirTurno: () -> Unit = {},
    val onElegir: () -> Unit = {},
    val onCantidad: (Long, Long) -> Unit = { _, _ -> },
    val onQuitar: (Long) -> Unit = {},
    val onMetodo: (MetodoPago) -> Unit = {},
    val onContinuar: () -> Unit = {},
    val onConfirmarEfectivo: () -> Unit = {},
    val onDescartar: () -> Unit = {},
    val onCancelarSalir: () -> Unit = {},
    val onPagoRecibido: () -> Unit = {},
    val onConfigurarPago: () -> Unit = {},
    val onCampo: (CampoCliente, String) -> Unit = { _, _ -> },
    val onPegarSms: () -> Unit = {},
    val onConfirmarTransferencia: () -> Unit = {},
    /** 0.27.0 (N2). */
    val onElegirCliente: (cu.spvi.domain.model.ClienteFijo) -> Unit = {},
    val onClienteFijo: (Boolean) -> Unit = {},
)

/**
 * Venta completa (SPVI.txt «Venta»): Inventario (checkbox) → cantidades + método → comprobante (efectivo) o QR
 * (transferencia) → datos del cliente y nº de transacción → registro. Sin turno abierto se bloquea en cualquier paso.
 */
@Composable
fun VentaScreen(
    tipo: TipoVenta,
    onBack: () -> Unit,
    onElegirProductos: (TipoVenta, List<Long>) -> Unit,
    onTerminada: (String) -> Unit,
    onConfigurarPago: () -> Unit,
    seleccion: List<Long>?,
    onSeleccionConsumida: () -> Unit,
    viewModel: VentaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val pedirPermisoAviso = rememberPedirPermisoAviso()
    LaunchedEffect(seleccion) {
        if (seleccion != null) { viewModel.recibirSeleccion(seleccion); onSeleccionConsumida() }
    }
    val haptics = rememberSpviHaptics()
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoVenta.Mensaje -> snackbar.showSnackbar(e.texto)
                is EventoVenta.ElegirProductos -> onElegirProductos(e.tipo, e.preseleccion)
                is EventoVenta.Terminada -> { haptics.exito(); onTerminada(e.mensaje) } // P18 (A06)
                EventoVenta.Salir -> onBack()
                EventoVenta.ConfigurarPago -> onConfigurarPago()
            }
        }
    }
    BackHandler(onBack = viewModel::atras)
    // 0.25.0 (§5.2): mismo diálogo de fondo que en Inicio.
    val cajaVm: cu.spvi.app.caja.CajaViewModel = hiltViewModel()
    val caja by cajaVm.state.collectAsStateWithLifecycle()
    var pedirFondo by rememberSaveable { mutableStateOf(false) }
    if (pedirFondo) cu.spvi.app.caja.DialogoFondo(
        sugerido = caja.sugerido, trabajando = state.abriendo,
        onConfirmar = { f -> pedirFondo = false; pedirPermisoAviso(); viewModel.abrirTurno(f) },
        onDismiss = { pedirFondo = false },
        fondo = caja.fondo, onPedirFondo = { pedirFondo = false; cajaVm.pedirFondo() },
    )
    VentaContent(
        tipo = tipo,
        state = state,
        snackbar = snackbar,
        acciones = AccionesVenta(
            onAtras = viewModel::atras, onAbrirTurno = { cajaVm.cargarSugerido(); pedirFondo = true }, onElegir = viewModel::elegirProductos,
            onCantidad = viewModel::cambiarCantidad, onQuitar = viewModel::quitar, onMetodo = viewModel::elegirMetodo,
            onContinuar = viewModel::continuar, onConfirmarEfectivo = viewModel::confirmarEfectivo,
            onDescartar = viewModel::descartar, onCancelarSalir = viewModel::cancelarSalir,
            onPagoRecibido = viewModel::pagoRecibido,
            onConfigurarPago = viewModel::configurarPago,
            onCampo = viewModel::editarCliente,
            onElegirCliente = viewModel::elegirCliente, onClienteFijo = viewModel::clienteFijo,
            // El portapapeles se lee SOLO aquí, al tocar «Pegar SMS» (nunca en segundo plano).
            onPegarSms = { viewModel.pegarSms(clipboard.getText()?.text) },
            onConfirmarTransferencia = viewModel::confirmarTransferencia,
        ),
    )
}

@Composable
fun VentaContent(
    tipo: TipoVenta,
    state: VentaUiState,
    acciones: AccionesVenta,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    zona: ZoneId = ZoneId.systemDefault(),
) {
    val titulo = when (state.paso) {
        PasoVenta.CARRITO -> TextosVenta.titulo(tipo == TipoVenta.SERVICIO)
        PasoVenta.COMPROBANTE -> TextosVenta.COMPROBANTE
        PasoVenta.QR -> TextosVenta.COBRO_TRANSFERENCIA
        PasoVenta.CLIENTE -> TextosVenta.DATOS_CLIENTE
    }
    Scaffold(
        topBar = { SpviTopBar(title = titulo, onBack = acciones.onAtras) },
        snackbarHost = { SpviSnackbarHost(snackbar) },
        bottomBar = { if (state.permiso is Permiso.Permitido) BarraAcciones(state, acciones) },
    ) { padding ->
        Column(Modifier.fillMaxSize().spviContentWidth().padding(padding)) {
            if (state.trabajando) SpviLinearProgress(Modifier.fillMaxWidth())
            Box(Modifier.weight(1f)) {
                when (state.permiso) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SpviLoading() }
                    Permiso.SinTurno -> Bloqueo(state, acciones)
                    is Permiso.Permitido -> Column {
                        // 0.20.0 (H5, P43): el cierre pedido por el encargado espera a que termine esta venta.
                        if (state.cierrePedido) {
                            SpviStatusBanner(
                                TextosVenta.CIERRE_PEDIDO, BannerTone.Aviso,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = SpviSpacing.md, vertical = SpviSpacing.xs),
                            )
                        }
                        if (state.paso != PasoVenta.CARRITO || state.lineas.isNotEmpty()) TotalVenta(state)
                        when (state.paso) {
                            PasoVenta.CARRITO -> {
                                SpviSecondaryText(
                                    "Turno abierto · " + listOfNotNull("Desde las ${hora(state.permiso.turno.abiertoEn, zona)}", state.permiso.turno.abiertoPor.ifBlank { null }).joinToString(" · "),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = SpviSpacing.md).testTag(VentaTags.TURNO),
                                )
                                PasoCarrito(state, acciones)
                            }
                            PasoVenta.COMPROBANTE -> PasoComprobante(state)
                            PasoVenta.QR -> { SecureWindow(); PasoQr(state, acciones) } // P18 (A13): tarjeta del vendedor
                            PasoVenta.CLIENTE -> { SecureWindow(); PasoCliente(state, acciones) }
                        }
                    }
                }
            }
        }
    }
    if (state.confirmarSalir) {
        SpviDialog(
            title = TextosVenta.DESCARTAR_TITULO, text = TextosVenta.DESCARTAR_TEXTO,
            onDismiss = acciones.onCancelarSalir, onConfirm = acciones.onDescartar,
            confirmDescription = "Descartar", destructive = true,
        )
    }
}

@Composable
private fun Bloqueo(state: VentaUiState, acciones: AccionesVenta) {
    SpviEmptyState(
        title = TextosTurno.SIN_TURNO_TITULO,
        detail = TextosTurno.SIN_TURNO_DETALLE + if (state.lineas.isNotEmpty()) " Lo elegido se conserva." else "",
        ilustracion = SpviIlustracion.SinTurno,
        modifier = Modifier.testTag(VentaTags.BLOQUEO).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        SpviPrimaryButton(
            text = if (state.abriendo) TextosTurno.ABRIENDO else TextosTurno.ABRIR,
            onClick = acciones.onAbrirTurno, icon = SpviIcons.Turno, loading = state.abriendo, enabled = !state.abriendo,
            modifier = Modifier.testTag(VentaTags.ABRIR_TURNO),
        )
    }
}

// ---------------- Carrito ----------------

@Composable
private fun PasoCarrito(state: VentaUiState, acciones: AccionesVenta) {
    if (state.lineas.isEmpty()) {
        SpviEmptyState(title = TextosVenta.CARRITO_VACIO_TITULO, detail = TextosVenta.CARRITO_VACIO_DETALLE, ilustracion = SpviIlustracion.Carrito) {
            SpviPrimaryButton(TextosVenta.ELEGIR, onClick = acciones.onElegir, icon = SpviIcons.Inventario)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag(VentaTags.CARRITO),
        contentPadding = PaddingValues(SpviSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
    ) {
        items(state.lineas, key = { it.producto.id }) { l -> Box(spviAnimateItem()) { LineaEditable(l, acciones.onCantidad, acciones.onQuitar) } } // P18 (A18)
        item {
            SpviButtonRow(Modifier.padding(top = SpviSpacing.xs)) {
                SpviSecondaryButton(TextosVenta.AGREGAR, onClick = acciones.onElegir, icon = SpviIcons.AgregarCarrito)
            }
        }
        item {
            SelectorMetodo(state.metodo, acciones.onMetodo)
        }
        item { ErrorPaso(state.error) }
    }
}

/** Fila editable del carrito. 0.25.1: también la usa Modificar venta (Registros). */
@Composable
internal fun LineaEditable(l: LineaCarrito, onCantidad: (Long, Long) -> Unit, onQuitar: (Long) -> Unit) {
    val id = l.producto.id
    SpviCard {
        SpviListItem(
            title = l.producto.nombreCompleto,
            // P24: fila = dato principal (precio por unidad y subtotal). Las existencias de un artículo solo se muestran
            // al llegar al máximo; un Elaborado muestra siempre «Alcanza para N» (P26).
            // 0.28.0: el precio («12.00 CUP c/u») va en seminegrita.
            subtitleResaltado = SpviTextos.resaltar(
                TextosVenta.unidad(l) + if (TextosVenta.avisarExistencias(l)) " · ${TextosVenta.disponibles(l)}" else "",
                Money.format(l.producto.precioVenta),
            ),
            subtitleMaxLines = 2,
            value = Money.format(l.subtotalEstimado),
            indicatorColor = null,
            leading = l.producto.fotoUri?.let { uri -> { cu.spvi.app.common.Miniatura(uri) } }, // 0.21.0 (C11)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            SpviIconAction(
                SpviIcons.Quitar, "Una menos de ${l.producto.nombreCompleto}", onClick = { onCantidad(id, l.cantidad - 1) },
                enabled = l.cantidad > 1, style = IconActionStyle.Tonal, modifier = Modifier.testTag(VentaTags.menos(id)),
            )
            SpviTextField(
                filtro = FiltroEntrada.ENTERO,
                value = l.cantidad.toString(),
                onValueChange = { t -> Carrito.parseCantidad(t)?.let { onCantidad(id, it) } },
                label = "Cantidad", clearable = false, textAlign = TextAlign.Center,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(SpviSize.campoCantidad).testTag(VentaTags.cantidad(id)),
            )
            SpviIconAction(
                SpviIcons.Agregar, "Una más de ${l.producto.nombreCompleto}", onClick = { onCantidad(id, l.cantidad + 1) },
                enabled = l.cantidad < l.maximo, style = IconActionStyle.Tonal, modifier = Modifier.testTag(VentaTags.mas(id)),
            )
            Box(Modifier.weight(1f))
            SpviIconAction(SpviIcons.Eliminar, "Quitar ${l.producto.nombreCompleto} de la venta", onClick = { onQuitar(id) })
        }
    }
}

// ---------------- Comprobante (efectivo) ----------------

@Composable
private fun PasoComprobante(state: VentaUiState) {
    val cot = state.cotizacion ?: return
    LazyColumn(Modifier.fillMaxSize().testTag(VentaTags.COMPROBANTE), contentPadding = PaddingValues(SpviSpacing.md)) {
        item {
            SpviCard {
                cot.detalles.forEach { d ->
                    SpviListItem(
                        title = d.nombre,
                        // 0.28.0: los importes («2 × 12.00 CUP», «antes 10.00 CUP») van en seminegrita.
                        subtitleResaltado = SpviTextos.resaltar(
                            TextosVenta.linea(d.cantidad, d.precioUnitario) +
                                if (d.precioUnitario != d.precioBase) " · " + TextosVenta.ajuste(d.precioBase, d.precioUnitario) else "",
                            Money.format(d.precioUnitario), Money.format(d.precioBase),
                        ),
                        value = Money.format(d.subtotal),
                        indicatorColor = null,
                    )
                }
                SpviSecondaryText("Pago: ${TextosVenta.metodo(MetodoPago.EFECTIVO)}")
            }
        }
        item { ErrorPaso(state.error) }
    }
}

/**
 * P28: el total aparece UNA sola vez por paso, centrado y arriba (antes se repetía en la barra inferior, en el
 * comprobante, en el QR y en los datos del cliente). En el QR se llama «Importe a transferir».
 */
@Composable
private fun TotalVenta(state: VentaUiState) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SpviSpacing.md, vertical = SpviSpacing.xs).testTag(VentaTags.TOTAL)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpviSecondaryText(TextosVenta.etiquetaTotal(state.paso, state.cotizacion == null), textAlign = TextAlign.Center)
        SpviTextoAjustable(
            Money.format(state.total), style = SpviTextos.datoEn(MaterialTheme.typography.headlineSmall),
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------- QR (transferencia) ----------------

@Composable
private fun PasoQr(state: VentaUiState, acciones: AccionesVenta) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(SpviSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SpviSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            when (val qr = state.qr) {
                is PagoQr.Resultado.Ok -> QrCodigo(
                    contenido = qr.contenido,
                    descripcion = "Código QR de cobro para Transfermóvil, tarjeta terminada en ${qr.tarjeta.takeLast(4)}",
                    modifier = Modifier.testTag(VentaTags.QR),
                )
                null -> SpviLoading()
                PagoQr.Resultado.SinTarjeta -> SpviCard(tone = CardTone.Tonal) {
                    SpviSecondaryText(TextosVenta.SIN_TARJETA)
                    SpviButtonRow { SpviSecondaryButton(TextosVenta.CONFIGURAR_PAGO, onClick = acciones.onConfigurarPago, icon = SpviIcons.PagoElectronico) }
                }
            }
        }
        item { SpviSecondaryText(TextosVenta.QR_AYUDA, textAlign = TextAlign.Center) }
    }
}

/** P28: título y chips centrados como grupo. 0.25.1: también en Modificar venta. */
@Composable
internal fun SelectorMetodo(metodo: MetodoPago, onMetodo: (MetodoPago) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(TextosVenta.METODO, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = SpviSpacing.md).semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs), modifier = Modifier.padding(top = SpviSpacing.xs)) {
            MetodoPago.entries.forEach { m ->
                SpviChip(
                    label = TextosVenta.metodo(m), selected = metodo == m, onClick = { onMetodo(m) },
                    icon = if (m == MetodoPago.EFECTIVO) SpviIcons.Efectivo else SpviIcons.Transferencia,
                    modifier = Modifier.testTag(VentaTags.metodo(m)),
                )
            }
        }
    }
}

// ---------------- Datos del cliente ----------------

/** Campos del cliente de una transferencia (como elementos de una lista). 0.25.1: también en Modificar venta. */
internal fun LazyListScope.camposCliente(
    f: FormCliente,
    onCampo: (CampoCliente, String) -> Unit,
    /** 0.27.0 (N2): sugerencias de clientes fijos, justo debajo del nombre (solo en Nueva venta). */
    debajoDelNombre: (LazyListScope.() -> Unit)? = null,
) {
    val errores = f.visibles()
    CampoCliente.entries.forEach { c ->
        item(key = c.name) {
            SpviTextField(
                filtro = when (c) { CampoCliente.CI -> FiltroEntrada.CARNE; CampoCliente.NUMERO -> FiltroEntrada.TRANSACCION; else -> FiltroEntrada.LIBRE },
                value = when (c) {
                    CampoCliente.NOMBRE -> f.nombre
                    CampoCliente.CI -> f.ci
                    CampoCliente.TELEFONO -> f.telefono
                    CampoCliente.NUMERO -> f.numero
                },
                onValueChange = { onCampo(c, it) },
                label = c.etiqueta,
                isError = c in errores, errorText = errores[c], validarAlSalir = true, forzarError = f.intento,
                keyboardOptions = when (c) {
                    CampoCliente.NOMBRE -> KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    CampoCliente.CI -> KeyboardOptions(keyboardType = KeyboardType.Number)
                    CampoCliente.TELEFONO -> KeyboardOptions(keyboardType = KeyboardType.Phone)
                    CampoCliente.NUMERO -> KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                },
                modifier = Modifier.fillMaxWidth().testTag(VentaTags.campo(c)),
            )
        }
        if (c == CampoCliente.NOMBRE) debajoDelNombre?.invoke(this)
    }
}

@Composable
private fun PasoCliente(state: VentaUiState, acciones: AccionesVenta) {
    val f = state.cliente
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(SpviSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
    ) {
        camposCliente(f, acciones.onCampo, debajoDelNombre = {
            // 0.27.0 (N2): hasta 3 clientes fijos; tocar uno rellena carné y teléfono.
            items(state.sugerencias, key = { "cliente_${it.ci}" }) { c ->
                SpviListItem(
                    title = c.nombreApellidos, subtitle = TextosVenta.sugerencia(c), indicatorColor = null,
                    leading = { Icon(SpviIcons.Perfil, contentDescription = null) },
                    onClick = { acciones.onElegirCliente(c) },
                    modifier = Modifier.testTag(VentaTags.sugerencia(c.ci)),
                )
            }
        })
        item(key = "cliente_fijo") {
            SpviListItem(
                title = TextosVenta.CLIENTE_FIJO, subtitle = TextosVenta.CLIENTE_FIJO_AYUDA, subtitleMaxLines = 3, indicatorColor = null,
                leading = { Checkbox(checked = f.fijo, onCheckedChange = null) },
                onClick = { acciones.onClienteFijo(!f.fijo) },
                modifier = Modifier.testTag(VentaTags.CLIENTE_FIJO),
            )
        }
        item {
            // P24 (proximidad): el botón de pegar va centrado junto a su explicación.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                SpviSecondaryButton(TextosVenta.PEGAR_SMS, onClick = acciones.onPegarSms, icon = SpviIcons.Pegar, modifier = Modifier.testTag(VentaTags.PEGAR_SMS))
                // 0.20.0 (H3): en una app secundaria el SMS llega al teléfono del dueño, no al del empleado.
                SpviSecondaryText(TextosVenta.PEGAR_AYUDA, textAlign = TextAlign.Center, modifier = Modifier.padding(top = SpviSpacing.xs))
            }
        }
        state.avisoSms?.let { a ->
            item {
                SpviCard(tone = CardTone.Tonal) {
                    Text(a, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
        item { ErrorPaso(state.error) }
    }
}

@Composable
private fun ErrorPaso(error: String?) {
    if (error == null) return
    Text(
        error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = SpviSpacing.xs).testTag(VentaTags.ERROR).semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

// ---------------- Barra inferior (P28: solo iconos, centrados y separados 16dp; el total va arriba) ----------------

@Composable
private fun BarraAcciones(state: VentaUiState, acciones: AccionesVenta) {
    if (state.paso == PasoVenta.CARRITO && state.lineas.isEmpty()) return
    val habilitado = !state.trabajando && !state.bloqueada
    SpviBarraAcciones {
        when (state.paso) {
            PasoVenta.CARRITO -> SpviPrimaryButton(
                "Continuar", onClick = acciones.onContinuar, enabled = state.puedeContinuar, loading = state.trabajando,
                icon = if (state.metodo == MetodoPago.EFECTIVO) SpviIcons.Efectivo else SpviIcons.Transferencia,
                modifier = Modifier.testTag(VentaTags.CONTINUAR),
            )
            PasoVenta.COMPROBANTE -> {
                SpviIconAction(SpviIcons.Cancelar, "Cancelar la venta", onClick = acciones.onDescartar, style = IconActionStyle.Tonal, modifier = Modifier.testTag(VentaTags.CANCELAR))
                SpviIconAction(SpviIcons.Confirmar, "Confirmar la venta", onClick = acciones.onConfirmarEfectivo, enabled = habilitado, style = IconActionStyle.Filled, modifier = Modifier.testTag(VentaTags.CONFIRMAR))
            }
            PasoVenta.QR -> {
                SpviIconAction(SpviIcons.Cancelar, "Cancelar la venta", onClick = acciones.onDescartar, style = IconActionStyle.Tonal, modifier = Modifier.testTag(VentaTags.CANCELAR))
                SpviIconAction(SpviIcons.Confirmar, TextosVenta.PAGO_RECIBIDO, onClick = acciones.onPagoRecibido, enabled = habilitado, style = IconActionStyle.Filled, modifier = Modifier.testTag(VentaTags.PAGO_RECIBIDO))
            }
            PasoVenta.CLIENTE -> {
                SpviIconAction(SpviIcons.Cancelar, "Volver al código QR", onClick = acciones.onAtras, style = IconActionStyle.Tonal, modifier = Modifier.testTag(VentaTags.CANCELAR))
                SpviIconAction(SpviIcons.Confirmar, "Registrar la venta", onClick = acciones.onConfirmarTransferencia, enabled = habilitado, style = IconActionStyle.Filled, modifier = Modifier.testTag(VentaTags.CONFIRMAR))
            }
        }
    }
}
