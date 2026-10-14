package cu.spvi.app.migrar

import cu.spvi.app.common.LocalPermisosApp
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.spvi.app.common.AccionPermiso
import cu.spvi.app.common.Contacto
import cu.spvi.app.common.EstadoPermiso
import cu.spvi.app.common.abrirAjustesDeLaApp
import cu.spvi.app.common.actividadDe
import cu.spvi.app.common.explicacionCamara
import cu.spvi.app.common.rememberPermisoCamara
import cu.spvi.app.common.SecureWindow
import cu.spvi.designsystem.component.FiltroEntrada
import cu.spvi.designsystem.component.CardTone
import cu.spvi.designsystem.component.IconActionStyle
import cu.spvi.designsystem.component.SpviButtonRow
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviDialog
import cu.spvi.designsystem.component.SpviIconAction
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSecondaryButton
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviSnackbarHost
import cu.spvi.designsystem.component.SpviStepper
import cu.spvi.designsystem.component.SpviTextField
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.component.spviContentWidth
import cu.spvi.app.acceso.Autenticador
import cu.spvi.app.venta.QrCodigo
import cu.spvi.app.vinculacion.qr.CamaraEscaner
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.designsystem.token.SpviSpacing
import androidx.fragment.app.FragmentActivity
import cu.spvi.licencia.contract.Via
import kotlinx.coroutines.launch

object MigrarTags {
    const val QR = "migrar.qr"
    const val QR_EMISOR = "migrar.qr.emisor"
    const val QR_RECEPTOR = "migrar.qr.receptor"
    const val QR_CODIGO = "migrar.qr.codigo"
    const val QR_ESTADO = "migrar.qr.estado"
    const val QR_ACCION = "migrar.qr.accion"
    const val QR_LECTOR = "migrar.qr.lector"
    const val STEPPER = "migrar.stepper"
    const val DESTINO = "migrar.destino"
    const val AUTORIZACION = "migrar.autorizacion"
    const val COMPROBAR = "migrar.comprobar"
    const val RESULTADO = "migrar.resultado"
    const val BORRAR = "migrar.borrar"
    const val CONFIRMACION = "migrar.confirmacion"
}

class AccionesMigrar(
    /** 0.31.0 (P6): elegir Emisor/Receptor y manejar el intercambio por QR. */
    val onRol: (RolMigracion) -> Unit,
    val onCambiarRol: () -> Unit,
    val onEmpezarEmisor: () -> Unit,
    val onQrLeido: (String) -> Unit,
    val onCancelarIntercambio: () -> Unit,
    val onDestino: (String) -> Unit,
    val onPegarDestino: () -> Unit,
    val onEnviar: (Via) -> Unit,
    val onRespaldo: () -> Unit,
    val onAutorizacion: (String) -> Unit,
    val onPegarAutorizacion: () -> Unit,
    val onComprobar: () -> Unit,
    val onPedirBorrado: () -> Unit,
    val onConfirmacion: (String) -> Unit,
    val onConfirmarBorrado: () -> Unit,
    val onCancelarBorrado: () -> Unit,
)

/**
 * Ajustes → Migrar. Cuatro pasos numerados; el borrado solo se habilita con la autorización del desarrollador
 * verificada y escribiendo BORRAR. El portapapeles solo se lee al pulsar "Pegar". FLAG_SECURE.
 */
@Composable
fun MigrarScreen(onBack: () -> Unit, onRespaldoCompleto: () -> Unit, viewModel: MigrarViewModel = hiltViewModel()) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val ambito = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoMigrar.Enviar -> {
                    val ok = if (e.via == Via.WHATSAPP) Contacto.whatsapp(context, e.texto) else Contacto.sms(context, e.texto)
                    if (!ok) snackbar.showSnackbar("No hay una app disponible para enviar el mensaje")
                }
                is EventoMigrar.Mensaje -> snackbar.showSnackbar(e.texto)
            }
        }
    }
    // 0.31.0 (P6): antes de empezar el intercambio se confirma la identidad con la clave o biometría definida en Ajustes.
    val autorizar = rememberAutorizarMigracion(
        mensaje = { texto -> ambito.launch { snackbar.showSnackbar(texto) } },
    )
    MigrarContent(
        state = state,
        onBack = onBack,
        acciones = AccionesMigrar(
            onRol = viewModel::elegirRol,
            onCambiarRol = viewModel::cambiarRol,
            onEmpezarEmisor = { autorizar(viewModel::empezarEmisor) },
            onQrLeido = { texto ->
                CodigoMigracion.parsear(texto)?.let(viewModel::empezarReceptor)
                    ?: run { ambito.launch { snackbar.showSnackbar(TextosMigrar.QR_NO_EMISOR) } }
            },
            onCancelarIntercambio = viewModel::cancelarIntercambio,
            onDestino = viewModel::editarDestino,
            onPegarDestino = { clipboard.getText()?.text?.let { viewModel.editarDestino(it.trim()) } },
            onEnviar = viewModel::enviarSolicitud,
            onRespaldo = onRespaldoCompleto,
            onAutorizacion = viewModel::editarAutorizacion,
            onPegarAutorizacion = { clipboard.getText()?.text?.let(viewModel::editarAutorizacion) },
            onComprobar = viewModel::comprobar,
            onPedirBorrado = viewModel::pedirBorrado,
            onConfirmacion = viewModel::editarConfirmacion,
            onConfirmarBorrado = viewModel::confirmarBorrado,
            onCancelarBorrado = viewModel::cancelarBorrado,
        ),
        snackbar = snackbar,
        autorizar = autorizar,
    )
}

@Composable
fun MigrarContent(
    state: MigrarUiState,
    onBack: () -> Unit,
    acciones: AccionesMigrar,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    /** 0.31.0 (P6): confirma la identidad (clave o biometría) antes de abrir el intercambio por QR. */
    autorizar: (() -> Unit) -> Unit = {},
) {
    val f = state.form
    Scaffold(
        topBar = { SpviTopBar(title = TextosMigrar.TITULO, onBack = onBack) },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().spviContentWidth().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(SpviSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SpviSpacing.md),
        ) {
            SpviCard(tone = CardTone.Tonal) {
                Text(TextosMigrar.EXPLICACION, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                // P28: el ID ya no aparece en Licencia; aquí se puede copiar (mantener pulsado) para el teléfono antiguo.
                state.deviceId?.let { SelectionContainer { SpviSecondaryText("ID de este teléfono: $it", textAlign = TextAlign.Center) } }
            }

            TarjetaMigracionQr(state, acciones, autorizar, onIrAAjustes = onBack)

            val paso = pasoMigrar(f.destino, f.errorDestino, f.autorizacion, state.autorizada)
            SpviStepper(
                actual = paso, total = TextosMigrar.TOTAL_PASOS, titulo = tituloPasoMigrar(paso),
                modifier = Modifier.testTag(MigrarTags.STEPPER),
            )

            SpviCard(title = TextosMigrar.PASO1) {
                Text(TextosMigrar.PASO1_TEXTO, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                    SpviTextField(
                        filtro = FiltroEntrada.LIBRE,
                        value = f.destino, onValueChange = acciones.onDestino,
                        label = "ID del teléfono nuevo", placeholder = "SPVI:…",
                        isError = f.errorDestino != null, errorText = f.errorDestino,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                        modifier = Modifier.weight(1f).testTag(MigrarTags.DESTINO),
                    )
                    SpviIconAction(SpviIcons.Pegar, "Pegar ID", onClick = acciones.onPegarDestino, style = IconActionStyle.Tonal)
                }
                SpviSecondaryText("Enviar la solicitud al desarrollador por")
                SpviButtonRow {
                    SpviIconAction(
                        SpviIcons.WhatsApp, "Enviar solicitud por WhatsApp", onClick = { acciones.onEnviar(Via.WHATSAPP) },
                        enabled = !f.ocupado, containerColor = SpviTheme.colors.whatsapp,
                    )
                    SpviIconAction(
                        SpviIcons.Sms, "Enviar solicitud por SMS", onClick = { acciones.onEnviar(Via.SMS) },
                        enabled = !f.ocupado, style = IconActionStyle.Tonal,
                    )
                }
            }

            SpviCard(title = TextosMigrar.PASO2) {
                Text(TextosMigrar.PASO2_TEXTO, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (LocalPermisosApp.current.tipo == cu.spvi.domain.model.TipoApp.PRINCIPAL) {
                    Text(TextosMigrar.PASO2_EMPLEADOS, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                SpviButtonRow { SpviSecondaryButton("Crear respaldo completo", onClick = acciones.onRespaldo, icon = SpviIcons.Respaldo) }
            }

            SpviCard(title = TextosMigrar.PASO3) {
                Text(TextosMigrar.PASO3_TEXTO, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                    SpviTextField(
                        filtro = FiltroEntrada.LIBRE,
                        value = f.autorizacion, onValueChange = acciones.onAutorizacion,
                        label = "Licencia del teléfono nuevo", singleLine = false, minLines = 3, maxLines = 6,
                        modifier = Modifier.weight(1f).testTag(MigrarTags.AUTORIZACION),
                    )
                    SpviIconAction(SpviIcons.Pegar, "Pegar licencia", onClick = acciones.onPegarAutorizacion, style = IconActionStyle.Tonal)
                }
                SpviButtonRow {
                    SpviPrimaryButton(
                        "Comprobar", onClick = acciones.onComprobar, enabled = f.autorizacion.isNotBlank(), loading = f.ocupado && !f.confirmando,
                        icon = SpviIcons.Verificado, modifier = Modifier.testTag(MigrarTags.COMPROBAR),
                    )
                }
                f.resultado?.texto()?.let { r ->
                    Row(
                        Modifier.testTag(MigrarTags.RESULTADO).semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
                    ) {
                        Icon(if (r.ok) SpviIcons.Verificado else SpviIcons.Alerta, contentDescription = null)
                        Text(r.texto, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            SpviCard(title = TextosMigrar.PASO4) {
                Text(TextosMigrar.PASO4_TEXTO, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (!state.autorizada) SpviSecondaryText("Se activa cuando la autorización del paso 3 sea válida.")
                SpviButtonRow {
                    SpviPrimaryButton(
                        "Borrar SPVI de este teléfono", onClick = acciones.onPedirBorrado, enabled = state.autorizada,
                        icon = SpviIcons.BorrarTodo, modifier = Modifier.testTag(MigrarTags.BORRAR),
                    )
                }
            }
        }
    }

    if (f.confirmando) {
        SpviDialog(
            title = "¿Borrar SPVI de este teléfono?",
            onDismiss = acciones.onCancelarBorrado,
            onConfirm = acciones.onConfirmarBorrado.takeIf { state.puedeBorrar },
            confirmDescription = "Borrar",
            destructive = true,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                    Text(
                        "Asegúrate de que el respaldo ya está importado en el teléfono nuevo. Escribe ${TextosMigrar.PALABRA} para confirmar.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SpviTextField(
                        filtro = FiltroEntrada.NOMBRE,
                        value = f.confirmacion, onValueChange = acciones.onConfirmacion, label = "Escribe ${TextosMigrar.PALABRA}",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                        modifier = Modifier.testTag(MigrarTags.CONFIRMACION),
                    )
                }
            },
        )
    }
}

/**
 * 0.31.0 (P6): Ajustes → Migrar → pasar los datos por QR. Se elige el papel (Emisor o Receptor) y se hace el
 * intercambio; la clave o biometría definida en Ajustes se pide antes de empezar. FLAG_SECURE lo pone la pantalla.
 */
@Composable
private fun TarjetaMigracionQr(
    state: MigrarUiState,
    acciones: AccionesMigrar,
    autorizar: (() -> Unit) -> Unit,
    onIrAAjustes: () -> Unit,
) {
    val qr = state.qr
    SpviCard(title = TextosMigrar.QR_TITULO, modifier = Modifier.testTag(MigrarTags.QR)) {
        Text(
            TextosMigrar.QR_EXPLICACION, style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        when {
            // Sin el acceso con clave definido no se empieza: se le dice al usuario que lo defina.
            qr.empezado && !state.claveDefinida -> {
                Text(
                    TextosMigrar.QR_SIN_CLAVE, style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                SpviButtonRow {
                    SpviPrimaryButton(
                        TextosMigrar.QR_IR_AJUSTES, onClick = onIrAAjustes, icon = SpviIcons.Ajustes,
                        modifier = Modifier.testTag(MigrarTags.QR_ACCION),
                    )
                }
            }
            !qr.empezado -> SpviButtonRow {
                SpviSecondaryButton(
                    TextosMigrar.EMISOR, onClick = { acciones.onRol(RolMigracion.EMISOR) }, icon = SpviIcons.Sincronizar,
                    modifier = Modifier.weight(1f).testTag(MigrarTags.QR_EMISOR),
                )
                SpviSecondaryButton(
                    TextosMigrar.RECEPTOR, onClick = { acciones.onRol(RolMigracion.RECEPTOR) }, icon = SpviIcons.Camara,
                    modifier = Modifier.weight(1f).testTag(MigrarTags.QR_RECEPTOR),
                )
            }
            qr.rol == RolMigracion.EMISOR -> EmisorMigracionQr(state, acciones, autorizar)
            else -> ReceptorMigracionQr(state, acciones, autorizar)
        }
    }
}

/** Emisor: muestra el QR (con la clave y el puerto) y cómo va el envío. */
@Composable
private fun EmisorMigracionQr(state: MigrarUiState, acciones: AccionesMigrar, autorizar: (() -> Unit) -> Unit) {
    val qr = state.qr
    if (qr.codigo.isNotEmpty()) {
        QrCodigo(
            qr.codigo, TextosMigrar.QR_DESCRIPCION,
            modifier = Modifier.testTag(MigrarTags.QR_CODIGO),
        )
        SpviSecondaryText(
            TextosMigrar.QR_EMISOR_DETALLE, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
    EstadoMigracionQr(qr)
    SpviButtonRow {
        when {
            // Terminado: este teléfono se borra solo, no hay nada que tocar.
            qr.listo -> Unit
            qr.trabajando -> SpviSecondaryButton(
                TextosMigrar.QR_CANCELAR, onClick = acciones.onCancelarIntercambio, icon = SpviIcons.Cancelar,
                modifier = Modifier.testTag(MigrarTags.QR_ACCION),
            )
            // Sin QR a la vista: el botón que lo fabrica (tras confirmar la identidad).
            qr.codigo.isEmpty() -> SpviPrimaryButton(
                TextosMigrar.QR_EMISOR_MOSTRAR, onClick = { autorizar(acciones.onEmpezarEmisor) }, icon = SpviIcons.Escanear,
                modifier = Modifier.testTag(MigrarTags.QR_ACCION),
            )
            else -> SpviSecondaryButton(
                TextosMigrar.QR_CAMBIAR_ROL, onClick = acciones.onCambiarRol, icon = SpviIcons.Ajustes,
                modifier = Modifier.testTag(MigrarTags.QR_ACCION),
            )
        }
    }
}

/** Receptor: lee el QR del emisor con la cámara y muestra cómo va la recepción. */
@Composable
private fun ReceptorMigracionQr(state: MigrarUiState, acciones: AccionesMigrar, autorizar: (() -> Unit) -> Unit) {
    val qr = state.qr
    var leyendo by rememberSaveable { mutableStateOf(false) }
    if (leyendo && !qr.trabajando) {
        LectorQrMigracion(
            onQr = { texto -> leyendo = false; acciones.onQrLeido(texto) },
            onVolver = { leyendo = false },
        )
    } else {
        SpviButtonRow {
            if (qr.trabajando) {
                SpviSecondaryButton(
                    TextosMigrar.QR_CANCELAR, onClick = acciones.onCancelarIntercambio, icon = SpviIcons.Cancelar,
                    modifier = Modifier.testTag(MigrarTags.QR_ACCION),
                )
            } else {
                SpviSecondaryButton(
                    TextosMigrar.QR_RECEPTOR_TITULO, onClick = { autorizar { leyendo = true } }, icon = SpviIcons.Escanear,
                    modifier = Modifier.testTag(MigrarTags.QR_ACCION),
                )
                SpviSecondaryButton(
                    TextosMigrar.QR_CAMBIAR_ROL, onClick = acciones.onCambiarRol, icon = SpviIcons.Ajustes,
                )
            }
        }
        if (qr.trabajando) EstadoMigracionQr(qr)
    }
}

/** Lo que está pasando ahora mismo (se lee en voz alta cuando cambia). */
@Composable
private fun EstadoMigracionQr(qr: MigrarQrUiState) {
    val texto = qr.etapa.textoQr(qr.rol)
    if (texto != null) {
        Text(
            texto, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag(MigrarTags.QR_ESTADO).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    qr.error?.let {
        Text(
            it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
    if (qr.listo) {
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SpviIcons.Verificado, contentDescription = null)
            Text(
                if (qr.rol == RolMigracion.EMISOR) TextosMigrar.QR_EMISOR_LISTO else TextosMigrar.QR_RECEPTOR_LISTO,
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Cámara para leer el QR del emisor. El permiso se pide aquí dentro, no en toda la pantalla de Migrar. */
@Composable
private fun LectorQrMigracion(onQr: (String) -> Unit, onVolver: () -> Unit) {
    val context = LocalContext.current
    val permiso = rememberPermisoCamara(yaSolicitado = false, onSolicitado = {})
    if (permiso.estado == EstadoPermiso.CONCEDIDO) {
        CamaraEscaner(
            onQr = onQr, onError = onVolver,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).testTag(MigrarTags.QR_LECTOR),
        )
        SpviSecondaryText(
            TextosMigrar.QR_RECEPTOR_DETALLE, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    } else {
        val exp = explicacionCamara(permiso.estado)
        Text(exp.titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(exp.detalle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        exp.boton?.let {
            SpviButtonRow {
                SpviPrimaryButton(
                    it, onClick = {
                        when (exp.accion) {
                            AccionPermiso.SOLICITAR -> permiso.solicitar()
                            AccionPermiso.ABRIR_AJUSTES -> abrirAjustesDeLaApp(context)
                            AccionPermiso.NINGUNA -> onVolver()
                        }
                    }, icon = SpviIcons.Camara,
                )
            }
        }
    }
}

/**
 * 0.31.0 (P6): pide la huella, la cara o el PIN del teléfono (el que el usuario definió en Ajustes → Acceso con
 * clave) antes de abrir el intercambio. Devuelve la acción a autorizar: si el teléfono no tiene bloqueo de pantalla,
 * no se puede autorizar nada y se avisa por [mensaje].
 */
@Composable
private fun rememberAutorizarMigracion(mensaje: (String) -> Unit): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val avisar by rememberUpdatedState(mensaje)
    return remember(context) {
        {
            val bloque = it
            if (!Autenticador.disponible(context)) {
                avisar(TextosMigrar.QR_SIN_BLOQUEO)
                return@remember
            }
            val activity = actividadDe(context) as? FragmentActivity
            if (activity == null) {
                avisar(TextosMigrar.ERROR_GENERICO)
                return@remember
            }
            Autenticador.pedir(
                activity, TextosMigrar.QR_PROMPT,
                alConfirmar = { bloque() }, alFallar = {},
            )
        }
    }
}

/** 0.31.0 (P6): texto de la etapa en curso (null si no hay ninguna). */
private fun EtapaMigracion?.textoQr(rol: RolMigracion?): String? = when (this) {
    null -> null
    EtapaMigracion.Esperando -> TextosMigrar.QR_ESPERANDO
    EtapaMigracion.Conectando -> TextosMigrar.QR_CONECTANDO
    is EtapaMigracion.Transfiriendo -> TextosMigrar.QR_TRANSFIRIENDO
    EtapaMigracion.Aplicando -> TextosMigrar.QR_APLICANDO
    EtapaMigracion.Terminado -> if (rol == RolMigracion.EMISOR) TextosMigrar.QR_EMISOR_LISTO else TextosMigrar.QR_RECEPTOR_LISTO
}
