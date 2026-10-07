package cu.spvi.app.migrar

import cu.spvi.app.common.LocalPermisosApp
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
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
import cu.spvi.app.common.Contacto
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
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.designsystem.token.SpviSpacing
import cu.spvi.licencia.contract.Via

object MigrarTags {
    const val STEPPER = "migrar.stepper"
    const val DESTINO = "migrar.destino"
    const val AUTORIZACION = "migrar.autorizacion"
    const val COMPROBAR = "migrar.comprobar"
    const val RESULTADO = "migrar.resultado"
    const val BORRAR = "migrar.borrar"
    const val CONFIRMACION = "migrar.confirmacion"
}

class AccionesMigrar(
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
    MigrarContent(
        state = state,
        onBack = onBack,
        acciones = AccionesMigrar(
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
    )
}

@Composable
fun MigrarContent(
    state: MigrarUiState,
    onBack: () -> Unit,
    acciones: AccionesMigrar,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
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
