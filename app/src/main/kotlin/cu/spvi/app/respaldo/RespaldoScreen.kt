package cu.spvi.app.respaldo

import cu.spvi.designsystem.component.SpviListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.spvi.app.common.Compartir
import cu.spvi.app.common.SecureWindow
import cu.spvi.designsystem.component.FiltroEntrada
import cu.spvi.designsystem.component.CardTone
import cu.spvi.designsystem.component.SpviButtonRow
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviDialog
import cu.spvi.designsystem.component.SpviLinearProgress
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSecondaryButton
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviSnackbarHost
import cu.spvi.designsystem.component.SpviStepper
import cu.spvi.designsystem.component.SpviTextField
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.component.spviContentWidth
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviSpacing
import java.time.ZoneId

object RespaldoTags {
    const val CONTRASENA = "respaldo.contrasena"
    const val PROTEGER = "respaldo.proteger"
    const val SIN_CONTRASENA = "respaldo.sin_contrasena"
    const val REPETIR = "respaldo.repetir"
    const val GUARDAR = "respaldo.guardar"
    const val COMPARTIR = "respaldo.compartir"
    const val IMPORTAR = "respaldo.importar"
    const val CONTRASENA_IMPORTAR = "respaldo.contrasena_importar"
    const val PROGRESO = "respaldo.progreso"
    const val RESULTADO = "respaldo.resultado"
    const val ARCHIVO = "respaldo.archivo"
    const val STEPPER = "respaldo.stepper"
    const val PASO_IMPORTAR = "respaldo.paso_importar"
    const val STEPPER_IMPORTAR = "respaldo.stepper_importar"
    const val HECHO = "respaldo.hecho"
    const val OTRO_RESPALDO = "respaldo.otro_respaldo"
}

class AccionesRespaldo(
    val onEditarExport: ((ExportForm) -> ExportForm) -> Unit,
    val onGuardar: () -> Unit,
    val onCompartir: () -> Unit,
    val onElegirArchivo: () -> Unit,
    val onContrasenaImport: (String) -> Unit,
    val onConfirmarImport: () -> Unit,
    val onCancelarImport: () -> Unit,
    val onCerrarResultado: () -> Unit = {},
    val onNuevoRespaldo: () -> Unit = {},
)

/**
 * Ajustes → Respaldo: exportar/importar la configuración o la base de datos completa, cifrada con contraseña.
 * Guardar en el teléfono (SAF) o enviar por otras apps (selector del sistema: WhatsApp, Telegram, Zapya,
 * Bluetooth, Drive, OneDrive…); importar desde cualquier proveedor de archivos o recibiéndolo de otra app.
 * 0.26.0: el respaldo se exporta solo como `.spvi` cifrado (sin el PDF de configuración). Sin permisos de almacenamiento. FLAG_SECURE: se escriben contraseñas.
 */
@Composable
fun RespaldoScreen(onBack: () -> Unit, viewModel: RespaldoViewModel = hiltViewModel()) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TextosRespaldo.MIME)) {
        viewModel.destinoElegido(it?.toString())
    }
    // "*/*": WhatsApp, Telegram o Drive no siempre conservan un MIME reconocible para .spvi.
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.archivoElegido(it?.toString()) }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoRespaldo.ElegirDestino -> guardar.launch(e.nombre)
                is EventoRespaldo.Compartir ->
                    if (!Compartir.archivos(context, listOf(e.archivo), e.mime, e.asunto)) snackbar.showSnackbar(TextosRespaldo.SIN_APP)
                is EventoRespaldo.Mensaje -> snackbar.showSnackbar(e.texto)
            }
        }
    }
    RespaldoContent(
        state = state,
        onBack = onBack,
        acciones = AccionesRespaldo(
            onEditarExport = viewModel::editarExport,
            onGuardar = viewModel::guardarEnTelefono, onCompartir = viewModel::compartir,
            onElegirArchivo = { abrir.launch(arrayOf("*/*")) },
            onContrasenaImport = viewModel::editarImport, onConfirmarImport = viewModel::confirmarImport,
            onCancelarImport = viewModel::cancelarImport,
            onCerrarResultado = viewModel::cerrarResultado,
            onNuevoRespaldo = viewModel::nuevoRespaldo,
        ),
        snackbar = snackbar,
    )
}

@Composable
fun RespaldoContent(
    state: RespaldoUiState,
    onBack: () -> Unit,
    acciones: AccionesRespaldo,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    zona: ZoneId = ZoneId.systemDefault(),
) {
    Scaffold(
        topBar = { SpviTopBar(title = TextosRespaldo.TITULO, onBack = onBack) },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().spviContentWidth().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(SpviSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SpviSpacing.md),
        ) {
            state.progreso?.let { TarjetaProgreso(it) }

            SpviCard(tone = CardTone.Tonal) {
                Text(TextosRespaldo.EXPLICACION, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                SpviSecondaryText(TextosRespaldo.SIN_LICENCIA)
            }

            SpviCard(title = "Exportar") {
                Text(TextosRespaldo.CONTENIDO, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                val e = state.export
                var elegido by rememberSaveable { mutableIntStateOf(1) }
                val paso = PasosRespaldo.pasoExportar(elegido, e, hecho = state.exportado != null)
                SpviStepper(
                    actual = paso, total = PasosRespaldo.TOTAL_EXPORTAR, titulo = PasosRespaldo.tituloExportar(paso),
                    onAtras = if (paso == 2) ({ elegido = 1 }) else null,
                    onSiguiente = if (paso == 1) ({
                        if (e.valido) elegido = 2 else acciones.onEditarExport { it.copy(mostrarErrores = true) }
                    }) else null,
                    modifier = Modifier.testTag(RespaldoTags.STEPPER),
                )
                if (paso == 1) {
                    // Protección por contraseña activada de forma predeterminada; al apagarla se muestra el riesgo explícito.
                    SpviListItem(
                        title = TextosRespaldo.PROTEGER,
                        indicatorColor = null,
                        leading = { Icon(SpviIcons.Bloqueo, contentDescription = null) },
                        onClick = { acciones.onEditarExport { it.copy(conContrasena = !it.conContrasena, mostrarErrores = false) } },
                        trailing = { Switch(checked = e.conContrasena, onCheckedChange = null) },
                        modifier = Modifier.testTag(RespaldoTags.PROTEGER),
                    )
                    if (!e.conContrasena) {
                        SpviSecondaryText(TextosRespaldo.SIN_CONTRASENA_AVISO, modifier = Modifier.testTag(RespaldoTags.SIN_CONTRASENA))
                    }
                }
                if (paso == 1 && e.conContrasena) {
                    CampoContrasena(
                        e.contrasena, { v -> acciones.onEditarExport { it.copy(contrasena = v) } }, "Contraseña",
                        error = e.errorContrasena, validarAlSalir = true, forzarError = e.mostrarErrores,
                        ayuda = "Anótala en un lugar seguro: sin ella no se puede recuperar",
                        tag = RespaldoTags.CONTRASENA, imeAction = ImeAction.Next,
                    )
                    CampoContrasena(
                        e.repetir, { v -> acciones.onEditarExport { it.copy(repetir = v) } }, "Repite la contraseña",
                        error = e.errorRepetir, validarAlSalir = true, forzarError = e.mostrarErrores, tag = RespaldoTags.REPETIR,
                        imeAction = ImeAction.Done,
                        onImeAction = { if (e.valido) elegido = 2 else acciones.onEditarExport { it.copy(mostrarErrores = true) } },
                    )
                } else if (paso == 1) {
                    // Sin contraseña no hay campos: el paso 1 es solo el interruptor y el aviso de arriba.
                } else if (paso == 3) {
                    Text(
                        state.exportado.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(RespaldoTags.HECHO),
                    )
                    SpviButtonRow {
                        SpviSecondaryButton(
                            PasosRespaldo.OTRO_RESPALDO, onClick = { elegido = 1; acciones.onNuevoRespaldo() }, icon = SpviIcons.Respaldo,
                            modifier = Modifier.testTag(RespaldoTags.OTRO_RESPALDO),
                        )
                    }
                } else {
                    SpviSecondaryText(if (e.conContrasena) TextosRespaldo.CONTRASENA_LISTA else TextosRespaldo.LISTO_SIN_CONTRASENA)
                    SpviButtonRow {
                        SpviSecondaryButton(
                            "Enviar a otra app", onClick = acciones.onCompartir, enabled = !state.ocupado, icon = SpviIcons.Compartir,
                            modifier = Modifier.testTag(RespaldoTags.COMPARTIR),
                        )
                        SpviPrimaryButton(
                            "Guardar en el teléfono", onClick = acciones.onGuardar, enabled = !state.ocupado, icon = SpviIcons.Exportar,
                            modifier = Modifier.testTag(RespaldoTags.GUARDAR),
                        )
                    }
                    SpviSecondaryText(TextosRespaldo.ENVIAR_OTRA_APP)
                }
            }

            SpviCard(title = "Importar") {
                val comprobando = state.progreso?.texto == EtapasRespaldo.COMPROBANDO && state.importacion == null
                val pasoImp = PasosRespaldo.pasoImportarTarjeta(comprobando, state.importacion)
                SpviStepper(
                    actual = pasoImp, total = PasosRespaldo.TOTAL_IMPORTAR, titulo = PasosRespaldo.tituloImportar(pasoImp),
                    modifier = Modifier.testTag(RespaldoTags.STEPPER_IMPORTAR),
                )
                Text(TextosRespaldo.IMPORTAR_AYUDA, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                SpviButtonRow {
                    SpviSecondaryButton(
                        "Elegir archivo", onClick = acciones.onElegirArchivo, enabled = !state.ocupado, icon = SpviIcons.ElegirArchivo,
                        modifier = Modifier.testTag(RespaldoTags.IMPORTAR),
                    )
                }
            }
        }
    }

    state.importacion?.let { imp ->
        SpviDialog(
            title = "Importar respaldo",
            onDismiss = acciones.onCancelarImport,
            onConfirm = acciones.onConfirmarImport.takeUnless { state.ocupado },
            confirmDescription = "Importar",
            destructive = true,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                    Text(
                        listOfNotNull(describirArchivo(imp.info, zona), imp.nombre).joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag(RespaldoTags.ARCHIVO),
                    )
                    SpviSecondaryText(PasosRespaldo.COMPROBADO)
                    SpviSecondaryText(
                        PasosRespaldo.textoImportar(imp),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(RespaldoTags.PASO_IMPORTAR),
                    )
                    Text(TextosRespaldo.AVISO_IMPORTAR, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (PasosRespaldo.pideContrasena(imp)) {
                        CampoContrasena(
                            imp.contrasena, acciones.onContrasenaImport, "Contraseña del respaldo",
                            error = imp.error, tag = RespaldoTags.CONTRASENA_IMPORTAR, imeAction = ImeAction.Done,
                            onImeAction = { if (!state.ocupado && imp.contrasena.isNotEmpty()) acciones.onConfirmarImport() },
                        )
                    } else {
                        SpviSecondaryText(TextosRespaldo.SIN_CONTRASENA_AVISO) // 0.27.0 (T10): no se pide contraseña
                    }
                    state.progreso?.let { p ->
                        SpviLinearProgress(progress = p.fraccion)
                        SpviSecondaryText(p.etiqueta)
                    }
                }
            },
        )
    }

    state.resultado?.let { r ->
        SpviDialog(
            title = r.titulo,
            text = r.detalle,
            onDismiss = acciones.onCerrarResultado,
            onConfirm = acciones.onCerrarResultado,
            confirmDescription = TextosRespaldo.ACEPTAR,
            dismissDescription = "Cerrar",
            modifier = Modifier.testTag(RespaldoTags.RESULTADO),
        )
    }
}

/** Paso n de m con barra determinada. Región "viva": TalkBack anuncia cada paso. */
@Composable
private fun TarjetaProgreso(p: Progreso) {
    SpviCard(tone = CardTone.Tonal, modifier = Modifier.testTag(RespaldoTags.PROGRESO)) {
        Text(
            p.etiqueta, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        SpviLinearProgress(progress = p.fraccion)
    }
}

@Composable
private fun CampoContrasena(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    error: String?,
    tag: String,
    ayuda: String? = null,
    imeAction: ImeAction? = null,
    onImeAction: (() -> Unit)? = null,
    validarAlSalir: Boolean = false,
    forzarError: Boolean = false,
) {
    SpviTextField(
        filtro = FiltroEntrada.LIBRE,
        value = valor, onValueChange = onCambio, label = etiqueta, imeAction = imeAction, onImeAction = onImeAction,
        validarAlSalir = validarAlSalir, forzarError = forzarError,
        isError = error != null, errorText = error, supportingText = ayuda,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.testTag(tag),
    )
}
