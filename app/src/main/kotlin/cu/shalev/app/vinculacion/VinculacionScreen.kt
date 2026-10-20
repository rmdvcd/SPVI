package cu.shalev.app.vinculacion

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevComboBox
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.venta.QrCodigo
import cu.shalev.core.time.Dates
import cu.shalev.app.common.AjustesRed
import cu.shalev.core.money.Money
import cu.shalev.core.money.Cup
import cu.shalev.domain.model.Empleado
import cu.shalev.designsystem.component.BannerTone
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevEmptyState
import cu.shalev.designsystem.component.ShalevFab
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.ShalevStatusBanner
import cu.shalev.designsystem.theme.ShalevTextos
import cu.shalev.designsystem.component.ShalevTextButton
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.EstadoConexion
import cu.shalev.domain.model.ModoSincronizacion
import cu.shalev.domain.model.PermisoEmpleado
import cu.shalev.domain.model.TipoApp
import cu.shalev.domain.model.Vinculacion
import java.time.Instant

object VinculacionTags {
    /** 0.26.0 (P73 §4). */
    const val FONDO = "vinculacion.fondo"
    const val ASIGNAR_FONDO = "vinculacion.asignar_fondo"
    const val FONDO_IMPORTE = "vinculacion.fondo_importe"
    const val CONFIRMAR_FONDO = "vinculacion.confirmar_fondo"
    const val AGREGAR = "vinculacion.agregar"
    const val SECUNDARIA = "vinculacion.secundaria"
    const val SINCRONIZAR = "vinculacion.sincronizar"
    const val DESVINCULAR = "vinculacion.desvincular"
    const val QR = "vinculacion.qr"
    const val RED = "vinculacion.red"
    const val PEDIR_CIERRE = "vinculacion.pedir_cierre"
    const val COBRO_TARJETA = "vinculacion.cobro_tarjeta"
    const val COBRO_TELEFONO = "vinculacion.cobro_telefono"
    const val TELEFONO = "vinculacion.telefono"
    const val APROBAR_CIERRE = "vinculacion.aprobar_cierre"
    const val RECHAZAR_CIERRE = "vinculacion.rechazar_cierre"
    fun empleado(id: Long) = "vinculacion.empleado.$id"
}

/**
 * P37: Ajustes → Apps vinculadas. En la principal: lista de secundarias, agregar (QR), permisos y quitar.
 * En una secundaria: estado de la conexión, pendientes, modo de sincronización, «Sincronizar ahora» y desvincular.
 * [qrLeido] llega del escáner (modo VINCULAR) por el back stack.
 */
@Composable
fun VinculacionScreen(
    onBack: () -> Unit,
    onEscanear: () -> Unit,
    qrLeido: String?,
    onQrConsumido: () -> Unit,
    /** 0.21.0 (C12): abrir el diálogo «Usar como secundaria» al entrar (recorrido inicial). */
    abrirSecundaria: Boolean = false,
    viewModel: VinculacionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var secundariaAbierta by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(abrirSecundaria) {
        if (abrirSecundaria && !secundariaAbierta) { secundariaAbierta = true; viewModel.pedirSecundaria() }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.mensajes.collect { snackbar.showSnackbar(it) } }
    val alEscanear by rememberUpdatedState(onEscanear) // 0.21.6: la lambda vigente, no la de la primera composición
    LaunchedEffect(Unit) { viewModel.escanear.collect { alEscanear() } }
    LaunchedEffect(qrLeido) {
        if (qrLeido != null) { onQrConsumido(); viewModel.qrLeido(qrLeido) }
    }
    VinculacionContent(
        s = state, onBack = onBack, snackbar = snackbar,
        onAgregar = viewModel::agregar, onEditar = viewModel::editar, onCerrarForm = viewModel::cerrarForm,
        onNombre = viewModel::nombre, onPermiso = viewModel::permiso, onTarjeta = viewModel::tarjeta,
        onGuardarForm = viewModel::guardarForm, onNuevoCodigo = viewModel::nuevoCodigo,
        onCerrarQr = viewModel::cerrarQr, onPedirQuitar = viewModel::pedirQuitar,
        onCancelarQuitar = viewModel::cancelarQuitar, onConfirmarQuitar = viewModel::confirmarQuitar,
        onPedirCierre = viewModel::pedirCierre, onCancelarCierre = viewModel::cancelarCierre,
        onConfirmarCierre = viewModel::confirmarCierre, onAprobarCierre = viewModel::aprobarCierre,
        onRechazarCierre = viewModel::rechazarCierre, onTelefonoSecundaria = viewModel::telefonoSecundaria,
        onPedirSecundaria = viewModel::pedirSecundaria, onCancelarSecundaria = viewModel::cancelarSecundaria,
        onConfirmarSecundaria = viewModel::confirmarSecundaria, onSincronizar = viewModel::sincronizar,
        onModo = viewModel::modo, onPedirDesvincular = viewModel::pedirDesvincular,
        onCancelarDesvincular = viewModel::cancelarDesvincular, onConfirmarDesvincular = viewModel::confirmarDesvincular,
        onAsignarFondo = viewModel::asignarFondo, onCancelarFondo = viewModel::cancelarFondo,
        onConfirmarFondo = viewModel::confirmarFondo,
    )
}

/** Sin estado propio: lo usan la pantalla real y las capturas Roborazzi (las acciones por defecto no hacen nada). */
@Composable
fun VinculacionContent(
    s: VinculacionUi,
    onBack: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onAgregar: () -> Unit = {},
    onEditar: (Empleado) -> Unit = {},
    onCerrarForm: () -> Unit = {},
    onNombre: (String) -> Unit = {},
    onPermiso: (PermisoEmpleado, Boolean) -> Unit = { _, _ -> },
    onTarjeta: (Long?) -> Unit = {},
    onGuardarForm: () -> Unit = {},
    onNuevoCodigo: () -> Unit = {},
    onCerrarQr: () -> Unit = {},
    onPedirQuitar: () -> Unit = {},
    onCancelarQuitar: () -> Unit = {},
    onConfirmarQuitar: () -> Unit = {},
    onPedirCierre: () -> Unit = {},
    onCancelarCierre: () -> Unit = {},
    onConfirmarCierre: () -> Unit = {},
    onAprobarCierre: () -> Unit = {},
    onRechazarCierre: () -> Unit = {},
    onTelefonoSecundaria: (String) -> Unit = {},
    onPedirSecundaria: () -> Unit = {},
    onCancelarSecundaria: () -> Unit = {},
    onConfirmarSecundaria: () -> Unit = {},
    onSincronizar: () -> Unit = {},
    onModo: (ModoSincronizacion) -> Unit = {},
    onPedirDesvincular: () -> Unit = {},
    onCancelarDesvincular: () -> Unit = {},
    onConfirmarDesvincular: () -> Unit = {},
    onAsignarFondo: () -> Unit = {},
    onCancelarFondo: () -> Unit = {},
    onConfirmarFondo: (Cup) -> Unit = {},
) {
    val esPrincipal = s.tipo == TipoApp.PRINCIPAL
    Scaffold(
        topBar = { ShalevTopBar(title = VinculacionLogic.TITULO, onBack = onBack) },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
        floatingActionButton = {
            if (s.puedeAgregar) { // 0.21.0 (C4/C5): solo la principal y hasta el límite de la licencia
                ShalevFab(ShalevIcons.AgregarEmpleado, "Agregar app de un empleado", onAgregar, Modifier.testTag(VinculacionTags.AGREGAR))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            if (esPrincipal) principal(s, onEditar, onPedirSecundaria) else secundaria(s, onSincronizar, onModo, onPedirDesvincular)
        }
    }

    s.form?.let { f ->
        FormDialog(
            f, s, onCerrarForm, onGuardarForm, onNombre, onPermiso, onTarjeta, onNuevoCodigo, onPedirQuitar,
            onPedirCierre, onRechazarCierre, onAprobarCierre, onAsignarFondo,
        )
    }
    s.qr?.let { q ->
        ShalevDialog(
            title = "Código para ${q.nombreEmpleado}",
            onDismiss = onCerrarQr,
            onConfirm = null,
            dismissDescription = "Cerrar",
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                    QrCodigo(q.contenido, "Código QR de vinculación para ${q.nombreEmpleado}", Modifier.testTag(VinculacionTags.QR))
                    // 0.28.0: la hora («Vence a las 09:30 · un solo uso») va en seminegrita.
                    Text(
                        ShalevTextos.resaltar(
                            "Vence a las ${Dates.time(q.codigo.venceEn)} · un solo uso", Dates.time(q.codigo.venceEn),
                        ),
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    )
                    Text(VinculacionLogic.QR_DETALLE, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            },
        )
    }
    s.quitar?.let { e ->
        ShalevDialog(
            title = "¿Quitar la app de ${e.nombre}?",
            text = VinculacionLogic.CONFIRMAR_QUITAR,
            onDismiss = onCancelarQuitar,
            onConfirm = onConfirmarQuitar,
            confirmDescription = VinculacionLogic.QUITAR,
            confirmIcon = ShalevIcons.Desvincular,
            destructive = true,
        )
    }
    s.asignarFondo?.let { a -> DialogoAsignarFondo(a, s.trabajando, onCancelarFondo, onConfirmarFondo) }
    s.cerrarTurno?.let { e ->
        ShalevDialog(
            title = "¿Pedir el cierre del turno de ${e.nombre}?",
            text = VinculacionLogic.CONFIRMAR_CIERRE,
            onDismiss = onCancelarCierre,
            onConfirm = onConfirmarCierre,
            confirmDescription = VinculacionLogic.PEDIR_CIERRE,
            confirmIcon = ShalevIcons.Salir,
        )
    }
    if (s.confirmarSecundaria) {
        ShalevDialog(
            title = VinculacionLogic.USAR_COMO_SECUNDARIA,
            onDismiss = onCancelarSecundaria,
            onConfirm = onConfirmarSecundaria,
            confirmDescription = "Escanear código",
            confirmIcon = ShalevIcons.Escanear,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                    Text(VinculacionLogic.CONFIRMAR_SECUNDARIA, style = MaterialTheme.typography.bodyMedium)
                    // 0.21.0 (C2): el teléfono del empleado va en su QR de cobro.
                    ShalevTextField(
                        filtro = FiltroEntrada.TELEFONO,
                        value = s.telefonoSecundaria, onValueChange = onTelefonoSecundaria,
                        label = VinculacionLogic.TELEFONO_SECUNDARIA, leadingIcon = ShalevIcons.Telefono,
                        supportingText = VinculacionLogic.TELEFONO_SECUNDARIA_AYUDA,
                        isError = s.errorTelefono != null, errorText = s.errorTelefono,
                        
                        modifier = Modifier.fillMaxWidth().testTag(VinculacionTags.TELEFONO),
                    )
                }
            },
        )
    }
    if (s.confirmarDesvincular) {
        val p = s.secundaria.pendientes
        ShalevDialog(
            title = "¿Desvincular esta app?",
            text = VinculacionLogic.CONFIRMAR_DESVINCULAR +
                if (p > 0) " Atención: ${VinculacionLogic.pendientes(p)}; se perderán. Sincroniza antes." else "",
            onDismiss = onCancelarDesvincular,
            onConfirm = onConfirmarDesvincular,
            confirmDescription = VinculacionLogic.DESVINCULAR,
            confirmIcon = ShalevIcons.Desvincular,
            destructive = true,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.principal(
    s: VinculacionUi,
    onEditar: (Empleado) -> Unit,
    onPedirSecundaria: () -> Unit,
) {
    item {
        Text(
            VinculacionLogic.PRINCIPAL_DETALLE, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
        )
    }
    if (s.principal.escuchando) {
        item {
            val d = s.principal.direcciones
            val context = LocalContext.current
            ShalevStatusBanner(
                text = if (d.isEmpty()) "Sin red local" else "Red local activa",
                detail = if (d.isEmpty()) "Conéctate a una wifi o activa la zona wifi de este teléfono." else "Este teléfono: ${d.joinToString(", ")}",
                tone = if (d.isEmpty()) BannerTone.Aviso else BannerTone.Info,
                modifier = Modifier.padding(horizontal = ShalevSpacing.md).testTag(VinculacionTags.RED),
                // La app no puede encender la zona wifi: abre la pantalla del sistema (sin permisos).
                actionIcon = ShalevIcons.RedLocal, actionDescription = VinculacionLogic.ABRIR_ZONA_WIFI,
                onAction = { AjustesRed.abrirZonaWifi(context) },
            )
        }
    }
    if (s.empleados.isEmpty()) {
        item { ShalevEmptyState(title = VinculacionLogic.SIN_SECUNDARIAS, detail = VinculacionLogic.SIN_SECUNDARIAS_DETALLE) }
    } else {
        item {
            Text(
                VinculacionLogic.tituloLista(s.empleados.size, s.limite), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = ShalevSpacing.xs),
            )
        }
        val ahora = Instant.now()
        items(s.empleados, key = { it.id }) { e ->
            val conectada = e.id in s.principal.conectadas
            ShalevListItem(
                title = e.nombre,
                // 0.28.0: las horas y fechas («Turno abierto desde las 09:30», «Última vez: …») van en seminegrita.
                subtitleResaltado = ShalevTextos.resaltar(
                    VinculacionLogic.subtituloEmpleado(e, conectada, ahora),
                    VinculacionLogic.turnoEmpleado(e, conectada).orEmpty(),
                    VinculacionLogic.estadoEmpleado(e, conectada, ahora),
                ),
                subtitleMaxLines = 2,
                indicatorColor = null,
                leading = { Icon(ShalevIcons.Vinculacion, contentDescription = null) },
                trailing = { Icon(ShalevIcons.Abrir, contentDescription = null) },
                onClick = { onEditar(e) },
                modifier = Modifier.testTag(VinculacionTags.empleado(e.id)),
            )
        }
    }
    // 0.21.0 (C5) / 0.27.0 (T9): una principal nunca pasa a secundaria; solo la app que eligió «Secundaria» al
    // instalarse (y aún sin vincular) ve esta opción.
    if (s.puedeSerSecundaria) item {
        ShalevButtonRow(Modifier.padding(vertical = ShalevSpacing.md)) {
            ShalevTextButton(
                text = VinculacionLogic.USAR_COMO_SECUNDARIA, icon = ShalevIcons.Escanear, onClick = onPedirSecundaria,
                enabled = !s.trabajando, modifier = Modifier.testTag(VinculacionTags.SECUNDARIA),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.secundaria(
    s: VinculacionUi,
    onSincronizar: () -> Unit,
    onModo: (ModoSincronizacion) -> Unit,
    onPedirDesvincular: () -> Unit,
) {
    val sec = s.secundaria
    item {
        ShalevCard(Modifier.padding(horizontal = ShalevSpacing.md), title = "Secundaria de ${sec.nombreNegocio.ifBlank { "la app principal" }}") {
            Text("Empleado: ${sec.nombreEmpleado}", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            val ultima = sec.ultimaSincronizacion
            val textoSinc = VinculacionLogic.pendientes(sec.pendientes) +
                (ultima?.let { " · Última vez: ${Dates.dayTime(it)}" } ?: "")
            // 0.28.0: la fecha («Última vez: 30/09/2026 09:30») va en seminegrita.
            Text(
                ShalevTextos.resaltar(textoSinc, ultima?.let { Dates.dayTime(it) }.orEmpty()),
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    item {
        val c = sec.conexion
        val sinConexion = c is EstadoConexion.SinConexion
        val context = LocalContext.current
        ShalevStatusBanner(
            text = VinculacionLogic.conexion(c),
            tone = if (sinConexion) BannerTone.Aviso else BannerTone.Info,
            modifier = Modifier.padding(horizontal = ShalevSpacing.md).testTag(VinculacionTags.RED),
            actionIcon = if (sinConexion) ShalevIcons.RedLocal else null,
            actionDescription = if (sinConexion) VinculacionLogic.ABRIR_WIFI else null,
            onAction = if (sinConexion) ({ AjustesRed.abrirWifi(context) }) else null,
        )
    }
    item {
        ShalevButtonRow(Modifier.padding(vertical = ShalevSpacing.xs)) {
            ShalevPrimaryButton(
                text = VinculacionLogic.SINCRONIZAR, icon = ShalevIcons.Sincronizar, onClick = onSincronizar,
                loading = s.trabajando, enabled = !s.trabajando, modifier = Modifier.testTag(VinculacionTags.SINCRONIZAR),
            )
        }
    }
    item {
        ShalevCard(Modifier.padding(horizontal = ShalevSpacing.md), title = "Sincronización") {
            // 0.27.0 (capturas): separación entre opciones y altura táctil mínima de 48 dp.
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                ModoSincronizacion.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = ShalevSize.touchTarget).selectable(selected = sec.modo == m, role = Role.RadioButton, onClick = { onModo(m) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
                    ) {
                        RadioButton(selected = sec.modo == m, onClick = null)
                        Column {
                            Text(m.etiqueta, style = MaterialTheme.typography.bodyLarge)
                            Text(m.detalle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    item {
        ShalevCard(Modifier.padding(horizontal = ShalevSpacing.md), title = "Lo que puedes hacer") {
            val p = sec.permisos
            Text(
                if (p.isEmpty()) "Solo consultar" else PermisoEmpleado.entries.filter { it in p }.joinToString("\n") { "• " + it.etiqueta },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    item {
        ShalevButtonRow(Modifier.padding(vertical = ShalevSpacing.md)) {
            ShalevTextButton(
                text = VinculacionLogic.DESVINCULAR, icon = ShalevIcons.Desvincular, onClick = onPedirDesvincular,
                enabled = !s.trabajando, modifier = Modifier.testTag(VinculacionTags.DESVINCULAR),
            )
        }
    }
}

@Composable
private fun FormDialog(
    f: FormEmpleado,
    s: VinculacionUi,
    onCerrarForm: () -> Unit,
    onGuardarForm: () -> Unit,
    onNombre: (String) -> Unit,
    onPermiso: (PermisoEmpleado, Boolean) -> Unit,
    onTarjeta: (Long?) -> Unit,
    onNuevoCodigo: () -> Unit,
    onPedirQuitar: () -> Unit,
    onPedirCierre: () -> Unit,
    onRechazarCierre: () -> Unit,
    onAprobarCierre: () -> Unit,
    onAsignarFondo: () -> Unit,
) {
    val nueva = f.id == 0L
    val trabajando = s.trabajando
    val empleado = s.empleadoForm
    ShalevDialog(
        title = if (nueva) "Agregar app de un empleado" else f.nombre,
        onDismiss = onCerrarForm,
        onConfirm = onGuardarForm,
        confirmDescription = if (nueva) "Crear código" else "Guardar",
        confirmEnabled = !trabajando && (!nueva || f.nombre.isNotBlank()),
        confirmLoading = trabajando,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                if (nueva) {
                    ShalevTextField(
                        filtro = FiltroEntrada.NOMBRE,
                        value = f.nombre, onValueChange = onNombre, label = "Nombre del empleado",
                        isError = f.errorNombre != null, errorText = f.errorNombre, modifier = Modifier.fillMaxWidth(),
                    )
                }
                // 0.20.0 (H5): turno abierto de esta app y «Pedir cierre» (arriba: es lo urgente, Von Restorff).
                empleado?.let { e -> VinculacionLogic.turnoEmpleado(e, e.id in s.principal.conectadas)?.let { texto ->
                    ShalevListItem(
                        title = texto,
                        // 0.28.0: la hora («Turno abierto desde las 09:30») va en seminegrita.
                        titleResaltado = ShalevTextos.resaltar(
                            texto, e.turnoAbiertoDesde?.let { Dates.time(it) }.orEmpty(),
                        ),
                        // 0.25.0 (§5.3): antes de aprobar, el dueño ve «Esperado X · Contado Y · Faltante Z».
                        subtitleResaltado = e.arqueoTurno?.let { ar ->
                            ShalevTextos.resaltar(
                                cu.shalev.app.caja.TextosCaja.resumen(ar),
                                Money.format(ar.esperado),
                                ar.contado?.let(Money::format).orEmpty(),
                                ar.diferencia?.let { d -> Money.format(if (d.isNegative) -d else d) }.orEmpty(),
                            )
                        },
                        indicatorColor = null,
                        leading = { Icon(ShalevIcons.Turno, contentDescription = null) },
                        trailing = when {
                            e.cierrePedido && (e.conteoCierre == null || e.conteoCierre?.aprobadoEn != null) -> null
                            // 0.21.0 (C6): el empleado lo pidió: Aprobar (rellena, la acción esperada) o Rechazar.
                            (e.solicitaCierre || (e.cierrePedido && e.conteoCierre != null && e.conteoCierre?.aprobadoEn == null)) -> ({
                                Row(horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                                    ShalevIconAction(
                                        ShalevIcons.Cancelar, VinculacionLogic.RECHAZAR_CIERRE, onClick = onRechazarCierre, enabled = !trabajando,
                                        modifier = Modifier.testTag(VinculacionTags.RECHAZAR_CIERRE),
                                    )
                                    ShalevIconAction(
                                        ShalevIcons.Guardar, VinculacionLogic.APROBAR_CIERRE, onClick = onAprobarCierre, enabled = !trabajando,
                                        style = IconActionStyle.Filled, modifier = Modifier.testTag(VinculacionTags.APROBAR_CIERRE),
                                    )
                                }
                            })
                            else -> ({
                                ShalevIconAction(
                                    ShalevIcons.Salir, VinculacionLogic.PEDIR_CIERRE, onClick = onPedirCierre, enabled = !trabajando,
                                    style = IconActionStyle.Tonal, modifier = Modifier.testTag(VinculacionTags.PEDIR_CIERRE),
                                )
                            })
                        },
                        subtitleMaxLines = 2,
                    )
                } }
                empleado?.conteoCierre?.let { cu.shalev.app.caja.ResumenConteo(it) }
                // 0.26.0 (P73 §4): fondo del próximo turno (sin él, su app no abre turno).
                empleado?.let { e -> VinculacionLogic.fondoEmpleado(e)?.let { texto ->
                    ShalevListItem(
                        title = VinculacionLogic.FONDO_PROXIMO,
                        // 0.28.0: el importe («1 250.00 CUP · se usa al abrir el turno») va en seminegrita.
                        subtitleResaltado = ShalevTextos.resaltar(
                            texto, e.fondoAsignado?.let { Money.format(it) }.orEmpty(),
                        ),
                        subtitleMaxLines = 2,
                        indicatorColor = null,
                        leading = { Icon(ShalevIcons.Efectivo, contentDescription = null) },
                        trailing = if (e.appDesactualizada) null else ({
                            ShalevIconAction(
                                ShalevIcons.Editar, VinculacionLogic.ASIGNAR_FONDO, onClick = onAsignarFondo, enabled = !trabajando,
                                style = if (e.pideApertura) IconActionStyle.Filled else IconActionStyle.Tonal,
                                modifier = Modifier.testTag(VinculacionTags.ASIGNAR_FONDO),
                            )
                        }),
                        modifier = Modifier.testTag(VinculacionTags.FONDO),
                    )
                } }
                Text("Permisos", style = MaterialTheme.typography.titleSmall)
                PermisoEmpleado.entries.forEach { p ->
                    val activo = p in f.permisos
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = activo, role = Role.Checkbox, onValueChange = { onPermiso(p, it) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
                    ) {
                        Checkbox(checked = activo, onCheckedChange = null)
                        Column {
                            Text(p.etiqueta, style = MaterialTheme.typography.bodyLarge)
                            Text(p.detalle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                // 0.20.0 (H4): con qué tarjeta cobra este empleado (solo si hay más de una). 0.21.0 (C2): el teléfono es
                // el que escribió el empleado; aquí solo se muestra.
                if (s.tarjetas.size > 1 || !nueva) {
                    Text(VinculacionLogic.COBRO, style = MaterialTheme.typography.titleSmall)
                    if (s.tarjetas.size > 1) {
                        ShalevComboBox(
                            label = VinculacionLogic.COBRO_TARJETA,
                            opciones = listOf<Long?>(null) + s.tarjetas.map { it.id },
                            seleccion = f.tarjetaId?.takeIf { id -> s.tarjetas.any { it.id == id } },
                            etiqueta = { id ->
                                if (id == null) VinculacionLogic.predeterminada(s.tarjetaPredeterminada?.enmascarado)
                                else s.tarjetas.first { it.id == id }.let { t -> VinculacionLogic.etiquetaTarjeta(t.alias, t.enmascarado) }
                            },
                            onSeleccion = onTarjeta,
                            leadingIcon = ShalevIcons.PagoElectronico,
                            modifier = Modifier.fillMaxWidth().testTag(VinculacionTags.COBRO_TARJETA),
                        )
                    }
                    if (!nueva) {
                        ShalevListItem(
                            title = VinculacionLogic.COBRO_TELEFONO,
                            subtitle = empleado?.telefono ?: VinculacionLogic.SIN_TELEFONO,
                            indicatorColor = null,
                            leading = { Icon(ShalevIcons.Telefono, contentDescription = null) },
                            modifier = Modifier.testTag(VinculacionTags.COBRO_TELEFONO),
                        )
                    }
                    Text(VinculacionLogic.COBRO_DETALLE, style = MaterialTheme.typography.bodySmall)
                }
                if (!nueva) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.lg, Alignment.CenterHorizontally)) {
                        ShalevTextButton(text = VinculacionLogic.NUEVO_CODIGO, icon = ShalevIcons.Vinculacion, onClick = onNuevoCodigo, enabled = !trabajando)
                        ShalevTextButton(text = VinculacionLogic.QUITAR, icon = ShalevIcons.Desvincular, onClick = onPedirQuitar, enabled = !trabajando)
                    }
                }
            }
        },
    )
}

/** 0.26.0 (P73 §4): «Asignar fondo» del próximo turno de una secundaria (puede ser 0; propone lo último contado). */
@Composable
private fun DialogoAsignarFondo(
    a: AsignacionFondo,
    trabajando: Boolean,
    onCancelarFondo: () -> Unit = {},
    onConfirmarFondo: (Cup) -> Unit = {},
) {
    var texto by rememberSaveable(a.empleado.id) { mutableStateOf(cu.shalev.app.caja.CajaForm.textoInicial(a.sugerido)) }
    val valor = cu.shalev.app.caja.CajaForm.importe(texto)
    ShalevDialog(
        title = "${VinculacionLogic.ASIGNAR_FONDO}: ${a.empleado.nombre}",
        text = VinculacionLogic.ASIGNAR_FONDO_TEXTO,
        onDismiss = onCancelarFondo,
        onConfirm = { valor?.let(onConfirmarFondo) },
        confirmDescription = VinculacionLogic.ASIGNAR_FONDO,
        confirmEnabled = valor != null && !trabajando,
        confirmLoading = trabajando,
        confirmTag = VinculacionTags.CONFIRMAR_FONDO,
    ) {
        ShalevTextField(
            value = texto, onValueChange = { texto = it.take(16) }, label = "${cu.shalev.app.caja.TextosCaja.FONDO} *",
            supportingText = if (a.sugerido != null) cu.shalev.app.caja.TextosCaja.FONDO_SUGERIDO else null,
            isError = texto.isNotBlank() && valor == null, errorText = cu.shalev.app.caja.TextosCaja.IMPORTE_INVALIDO,
            filtro = FiltroEntrada.DINERO,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag(VinculacionTags.FONDO_IMPORTE),
        )
    }
}
