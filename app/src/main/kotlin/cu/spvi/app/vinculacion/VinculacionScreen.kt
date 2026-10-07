package cu.spvi.app.vinculacion

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import cu.spvi.designsystem.component.FiltroEntrada
import cu.spvi.designsystem.component.IconActionStyle
import cu.spvi.designsystem.component.SpviIconAction
import cu.spvi.designsystem.component.SpviComboBox
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
import androidx.compose.ui.unit.dp
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
import cu.spvi.app.venta.QrCodigo
import cu.spvi.core.time.Dates
import cu.spvi.app.common.AjustesRed
import cu.spvi.core.money.Money
import cu.spvi.designsystem.component.BannerTone
import cu.spvi.designsystem.component.SpviButtonRow
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviDialog
import cu.spvi.designsystem.component.SpviEmptyState
import cu.spvi.designsystem.component.SpviFab
import cu.spvi.designsystem.component.SpviListItem
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSnackbarHost
import cu.spvi.designsystem.component.SpviStatusBanner
import cu.spvi.designsystem.theme.SpviTextos
import cu.spvi.designsystem.component.SpviTextButton
import cu.spvi.designsystem.component.SpviTextField
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviSpacing
import cu.spvi.domain.model.EstadoConexion
import cu.spvi.domain.model.ModoSincronizacion
import cu.spvi.domain.model.PermisoEmpleado
import cu.spvi.domain.model.TipoApp
import cu.spvi.domain.model.Vinculacion
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
    VinculacionContent(state, viewModel, onBack, snackbar)
}

/** Sin estado propio: lo usan la pantalla real y las capturas Roborazzi (con [AccionesVinculacion] vacío). */
@Composable
fun VinculacionContent(s: VinculacionUi, vm: AccionesVinculacion, onBack: () -> Unit = {}, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    val esPrincipal = s.tipo == TipoApp.PRINCIPAL
    Scaffold(
        topBar = { SpviTopBar(title = VinculacionLogic.TITULO, onBack = onBack) },
        snackbarHost = { SpviSnackbarHost(snackbar) },
        floatingActionButton = {
            if (s.puedeAgregar) { // 0.21.0 (C4/C5): solo la principal y hasta el límite de la licencia
                SpviFab(SpviIcons.AgregarEmpleado, "Agregar app de un empleado", vm::agregar, Modifier.testTag(VinculacionTags.AGREGAR))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
        ) {
            if (esPrincipal) principal(s, vm) else secundaria(s, vm)
        }
    }

    s.form?.let { f -> FormDialog(f, s, vm) }
    s.qr?.let { q ->
        SpviDialog(
            title = "Código para ${q.nombreEmpleado}",
            onDismiss = vm::cerrarQr,
            onConfirm = null,
            dismissDescription = "Cerrar",
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                    QrCodigo(q.contenido, "Código QR de vinculación para ${q.nombreEmpleado}", Modifier.testTag(VinculacionTags.QR))
                    // 0.28.0: la hora («Vence a las 09:30 · un solo uso») va en seminegrita.
                    Text(
                        SpviTextos.resaltar(
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
        SpviDialog(
            title = "¿Quitar la app de ${e.nombre}?",
            text = VinculacionLogic.CONFIRMAR_QUITAR,
            onDismiss = vm::cancelarQuitar,
            onConfirm = vm::confirmarQuitar,
            confirmDescription = VinculacionLogic.QUITAR,
            confirmIcon = SpviIcons.Desvincular,
            destructive = true,
        )
    }
    s.asignarFondo?.let { a -> DialogoAsignarFondo(a, s.trabajando, vm) }
    s.cerrarTurno?.let { e ->
        SpviDialog(
            title = "¿Pedir el cierre del turno de ${e.nombre}?",
            text = VinculacionLogic.CONFIRMAR_CIERRE,
            onDismiss = vm::cancelarCierre,
            onConfirm = vm::confirmarCierre,
            confirmDescription = VinculacionLogic.PEDIR_CIERRE,
            confirmIcon = SpviIcons.Salir,
        )
    }
    if (s.confirmarSecundaria) {
        SpviDialog(
            title = VinculacionLogic.USAR_COMO_SECUNDARIA,
            onDismiss = vm::cancelarSecundaria,
            onConfirm = vm::confirmarSecundaria,
            confirmDescription = "Escanear código",
            confirmIcon = SpviIcons.Escanear,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                    Text(VinculacionLogic.CONFIRMAR_SECUNDARIA, style = MaterialTheme.typography.bodyMedium)
                    // 0.21.0 (C2): el teléfono del empleado va en su QR de cobro.
                    SpviTextField(
                        filtro = FiltroEntrada.TELEFONO,
                        value = s.telefonoSecundaria, onValueChange = vm::telefonoSecundaria,
                        label = VinculacionLogic.TELEFONO_SECUNDARIA, leadingIcon = SpviIcons.Telefono,
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
        SpviDialog(
            title = "¿Desvincular esta app?",
            text = VinculacionLogic.CONFIRMAR_DESVINCULAR +
                if (p > 0) " Atención: ${VinculacionLogic.pendientes(p)}; se perderán. Sincroniza antes." else "",
            onDismiss = vm::cancelarDesvincular,
            onConfirm = vm::confirmarDesvincular,
            confirmDescription = VinculacionLogic.DESVINCULAR,
            confirmIcon = SpviIcons.Desvincular,
            destructive = true,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.principal(s: VinculacionUi, vm: AccionesVinculacion) {
    item {
        Text(
            VinculacionLogic.PRINCIPAL_DETALLE, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = SpviSpacing.md, vertical = SpviSpacing.xs),
        )
    }
    if (s.principal.escuchando) {
        item {
            val d = s.principal.direcciones
            val context = LocalContext.current
            SpviStatusBanner(
                text = if (d.isEmpty()) "Sin red local" else "Red local activa",
                detail = if (d.isEmpty()) "Conéctate a una wifi o activa la zona wifi de este teléfono." else "Este teléfono: ${d.joinToString(", ")}",
                tone = if (d.isEmpty()) BannerTone.Aviso else BannerTone.Info,
                modifier = Modifier.padding(horizontal = SpviSpacing.md).testTag(VinculacionTags.RED),
                // La app no puede encender la zona wifi: abre la pantalla del sistema (sin permisos).
                actionIcon = SpviIcons.RedLocal, actionDescription = VinculacionLogic.ABRIR_ZONA_WIFI,
                onAction = { AjustesRed.abrirZonaWifi(context) },
            )
        }
    }
    if (s.empleados.isEmpty()) {
        item { SpviEmptyState(title = VinculacionLogic.SIN_SECUNDARIAS, detail = VinculacionLogic.SIN_SECUNDARIAS_DETALLE) }
    } else {
        item {
            Text(
                VinculacionLogic.tituloLista(s.empleados.size, s.limite), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = SpviSpacing.xs),
            )
        }
        val ahora = Instant.now()
        items(s.empleados, key = { it.id }) { e ->
            val conectada = e.id in s.principal.conectadas
            SpviListItem(
                title = e.nombre,
                // 0.28.0: las horas y fechas («Turno abierto desde las 09:30», «Última vez: …») van en seminegrita.
                subtitleResaltado = SpviTextos.resaltar(
                    VinculacionLogic.subtituloEmpleado(e, conectada, ahora),
                    VinculacionLogic.turnoEmpleado(e, conectada).orEmpty(),
                    VinculacionLogic.estadoEmpleado(e, conectada, ahora),
                ),
                subtitleMaxLines = 2,
                indicatorColor = null,
                leading = { Icon(SpviIcons.Vinculacion, contentDescription = null) },
                trailing = { Icon(SpviIcons.Abrir, contentDescription = null) },
                onClick = { vm.editar(e) },
                modifier = Modifier.testTag(VinculacionTags.empleado(e.id)),
            )
        }
    }
    // 0.21.0 (C5) / 0.27.0 (T9): una principal nunca pasa a secundaria; solo la app que eligió «Secundaria» al
    // instalarse (y aún sin vincular) ve esta opción.
    if (s.puedeSerSecundaria) item {
        SpviButtonRow(Modifier.padding(vertical = SpviSpacing.md)) {
            SpviTextButton(
                text = VinculacionLogic.USAR_COMO_SECUNDARIA, icon = SpviIcons.Escanear, onClick = vm::pedirSecundaria,
                enabled = !s.trabajando, modifier = Modifier.testTag(VinculacionTags.SECUNDARIA),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.secundaria(s: VinculacionUi, vm: AccionesVinculacion) {
    val sec = s.secundaria
    item {
        SpviCard(Modifier.padding(horizontal = SpviSpacing.md), title = "Secundaria de ${sec.nombreNegocio.ifBlank { "la app principal" }}") {
            Text("Empleado: ${sec.nombreEmpleado}", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            val ultima = sec.ultimaSincronizacion
            val textoSinc = VinculacionLogic.pendientes(sec.pendientes) +
                (ultima?.let { " · Última vez: ${Dates.dayTime(it)}" } ?: "")
            // 0.28.0: la fecha («Última vez: 30/09/2026 09:30») va en seminegrita.
            Text(
                SpviTextos.resaltar(textoSinc, ultima?.let { Dates.dayTime(it) }.orEmpty()),
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    item {
        val c = sec.conexion
        val sinConexion = c is EstadoConexion.SinConexion
        val context = LocalContext.current
        SpviStatusBanner(
            text = VinculacionLogic.conexion(c),
            tone = if (sinConexion) BannerTone.Aviso else BannerTone.Info,
            modifier = Modifier.padding(horizontal = SpviSpacing.md).testTag(VinculacionTags.RED),
            actionIcon = if (sinConexion) SpviIcons.RedLocal else null,
            actionDescription = if (sinConexion) VinculacionLogic.ABRIR_WIFI else null,
            onAction = if (sinConexion) ({ AjustesRed.abrirWifi(context) }) else null,
        )
    }
    item {
        SpviButtonRow(Modifier.padding(vertical = SpviSpacing.xs)) {
            SpviPrimaryButton(
                text = VinculacionLogic.SINCRONIZAR, icon = SpviIcons.Sincronizar, onClick = vm::sincronizar,
                loading = s.trabajando, enabled = !s.trabajando, modifier = Modifier.testTag(VinculacionTags.SINCRONIZAR),
            )
        }
    }
    item {
        SpviCard(Modifier.padding(horizontal = SpviSpacing.md), title = "Sincronización") {
            // 0.27.0 (capturas): separación entre opciones y altura táctil mínima de 48 dp.
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                ModoSincronizacion.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = sec.modo == m, role = Role.RadioButton, onClick = { vm.modo(m) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
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
        SpviCard(Modifier.padding(horizontal = SpviSpacing.md), title = "Lo que puedes hacer") {
            val p = sec.permisos
            Text(
                if (p.isEmpty()) "Solo consultar" else PermisoEmpleado.entries.filter { it in p }.joinToString("\n") { "• " + it.etiqueta },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    item {
        SpviButtonRow(Modifier.padding(vertical = SpviSpacing.md)) {
            SpviTextButton(
                text = VinculacionLogic.DESVINCULAR, icon = SpviIcons.Desvincular, onClick = vm::pedirDesvincular,
                enabled = !s.trabajando, modifier = Modifier.testTag(VinculacionTags.DESVINCULAR),
            )
        }
    }
}

@Composable
private fun FormDialog(f: FormEmpleado, s: VinculacionUi, vm: AccionesVinculacion) {
    val nueva = f.id == 0L
    val trabajando = s.trabajando
    val empleado = s.empleadoForm
    SpviDialog(
        title = if (nueva) "Agregar app de un empleado" else f.nombre,
        onDismiss = vm::cerrarForm,
        onConfirm = vm::guardarForm,
        confirmDescription = if (nueva) "Crear código" else "Guardar",
        confirmEnabled = !trabajando && (!nueva || f.nombre.isNotBlank()),
        confirmLoading = trabajando,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                if (nueva) {
                    SpviTextField(
                        filtro = FiltroEntrada.NOMBRE,
                        value = f.nombre, onValueChange = vm::nombre, label = "Nombre del empleado",
                        isError = f.errorNombre != null, errorText = f.errorNombre, modifier = Modifier.fillMaxWidth(),
                    )
                }
                // 0.20.0 (H5): turno abierto de esta app y «Pedir cierre» (arriba: es lo urgente, Von Restorff).
                empleado?.let { e -> VinculacionLogic.turnoEmpleado(e, e.id in s.principal.conectadas)?.let { texto ->
                    SpviListItem(
                        title = texto,
                        // 0.28.0: la hora («Turno abierto desde las 09:30») va en seminegrita.
                        titleResaltado = SpviTextos.resaltar(
                            texto, e.turnoAbiertoDesde?.let { Dates.time(it) }.orEmpty(),
                        ),
                        // 0.25.0 (§5.3): antes de aprobar, el dueño ve «Esperado X · Contado Y · Faltante Z».
                        subtitleResaltado = e.arqueoTurno?.let { ar ->
                            SpviTextos.resaltar(
                                cu.spvi.app.caja.TextosCaja.resumen(ar),
                                Money.format(ar.esperado),
                                ar.contado?.let(Money::format).orEmpty(),
                                ar.diferencia?.let { d -> Money.format(if (d.isNegative) -d else d) }.orEmpty(),
                            )
                        },
                        indicatorColor = null,
                        leading = { Icon(SpviIcons.Turno, contentDescription = null) },
                        trailing = when {
                            e.cierrePedido -> null
                            // 0.21.0 (C6): el empleado lo pidió: Aprobar (rellena, la acción esperada) o Rechazar.
                            e.solicitaCierre -> ({
                                Row(horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                                    SpviIconAction(
                                        SpviIcons.Cancelar, VinculacionLogic.RECHAZAR_CIERRE, onClick = vm::rechazarCierre, enabled = !trabajando,
                                        modifier = Modifier.testTag(VinculacionTags.RECHAZAR_CIERRE),
                                    )
                                    SpviIconAction(
                                        SpviIcons.Guardar, VinculacionLogic.APROBAR_CIERRE, onClick = vm::aprobarCierre, enabled = !trabajando,
                                        style = IconActionStyle.Filled, modifier = Modifier.testTag(VinculacionTags.APROBAR_CIERRE),
                                    )
                                }
                            })
                            else -> ({
                                SpviIconAction(
                                    SpviIcons.Salir, VinculacionLogic.PEDIR_CIERRE, onClick = vm::pedirCierre, enabled = !trabajando,
                                    style = IconActionStyle.Tonal, modifier = Modifier.testTag(VinculacionTags.PEDIR_CIERRE),
                                )
                            })
                        },
                        subtitleMaxLines = 2,
                    )
                } }
                // 0.26.0 (P73 §4): fondo del próximo turno (sin él, su app no abre turno).
                empleado?.let { e -> VinculacionLogic.fondoEmpleado(e)?.let { texto ->
                    SpviListItem(
                        title = VinculacionLogic.FONDO_PROXIMO,
                        // 0.28.0: el importe («1 250.00 CUP · se usa al abrir el turno») va en seminegrita.
                        subtitleResaltado = SpviTextos.resaltar(
                            texto, e.fondoAsignado?.let { Money.format(it) }.orEmpty(),
                        ),
                        subtitleMaxLines = 2,
                        indicatorColor = null,
                        leading = { Icon(SpviIcons.Efectivo, contentDescription = null) },
                        trailing = if (e.appDesactualizada) null else ({
                            SpviIconAction(
                                SpviIcons.Editar, VinculacionLogic.ASIGNAR_FONDO, onClick = vm::asignarFondo, enabled = !trabajando,
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
                        Modifier.fillMaxWidth().toggleable(value = activo, role = Role.Checkbox, onValueChange = { vm.permiso(p, it) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
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
                        SpviComboBox(
                            label = VinculacionLogic.COBRO_TARJETA,
                            opciones = listOf<Long?>(null) + s.tarjetas.map { it.id },
                            seleccion = f.tarjetaId?.takeIf { id -> s.tarjetas.any { it.id == id } },
                            etiqueta = { id ->
                                if (id == null) VinculacionLogic.predeterminada(s.tarjetaPredeterminada?.enmascarado)
                                else s.tarjetas.first { it.id == id }.let { t -> listOfNotNull(t.alias, t.enmascarado).joinToString(" · ") }
                            },
                            onSeleccion = vm::tarjeta,
                            leadingIcon = SpviIcons.PagoElectronico,
                            modifier = Modifier.fillMaxWidth().testTag(VinculacionTags.COBRO_TARJETA),
                        )
                    }
                    if (!nueva) {
                        SpviListItem(
                            title = VinculacionLogic.COBRO_TELEFONO,
                            subtitle = empleado?.telefono ?: VinculacionLogic.SIN_TELEFONO,
                            indicatorColor = null,
                            leading = { Icon(SpviIcons.Telefono, contentDescription = null) },
                            modifier = Modifier.testTag(VinculacionTags.COBRO_TELEFONO),
                        )
                    }
                    Text(VinculacionLogic.COBRO_DETALLE, style = MaterialTheme.typography.bodySmall)
                }
                if (!nueva) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SpviSpacing.lg, Alignment.CenterHorizontally)) {
                        SpviTextButton(text = VinculacionLogic.NUEVO_CODIGO, icon = SpviIcons.Vinculacion, onClick = vm::nuevoCodigo, enabled = !trabajando)
                        SpviTextButton(text = VinculacionLogic.QUITAR, icon = SpviIcons.Desvincular, onClick = vm::pedirQuitar, enabled = !trabajando)
                    }
                }
            }
        },
    )
}

/** 0.26.0 (P73 §4): «Asignar fondo» del próximo turno de una secundaria (puede ser 0; propone lo último contado). */
@Composable
private fun DialogoAsignarFondo(a: AsignacionFondo, trabajando: Boolean, vm: AccionesVinculacion) {
    var texto by rememberSaveable(a.empleado.id) { mutableStateOf(cu.spvi.app.caja.CajaForm.textoInicial(a.sugerido)) }
    val valor = cu.spvi.app.caja.CajaForm.importe(texto)
    SpviDialog(
        title = "${VinculacionLogic.ASIGNAR_FONDO}: ${a.empleado.nombre}",
        text = VinculacionLogic.ASIGNAR_FONDO_TEXTO,
        onDismiss = vm::cancelarFondo,
        onConfirm = { valor?.let(vm::confirmarFondo) },
        confirmDescription = VinculacionLogic.ASIGNAR_FONDO,
        confirmEnabled = valor != null && !trabajando,
        confirmLoading = trabajando,
        confirmTag = VinculacionTags.CONFIRMAR_FONDO,
    ) {
        SpviTextField(
            value = texto, onValueChange = { texto = it.take(16) }, label = "${cu.spvi.app.caja.TextosCaja.FONDO} *",
            supportingText = if (a.sugerido != null) cu.spvi.app.caja.TextosCaja.FONDO_SUGERIDO else null,
            isError = texto.isNotBlank() && valor == null, errorText = cu.spvi.app.caja.TextosCaja.IMPORTE_INVALIDO,
            filtro = FiltroEntrada.DINERO,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag(VinculacionTags.FONDO_IMPORTE),
        )
    }
}
