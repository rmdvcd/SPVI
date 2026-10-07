package cu.spvi.app.vinculacion.qr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import cu.spvi.app.common.AccionPermiso
import cu.spvi.app.common.EstadoPermiso
import cu.spvi.app.common.abrirAjustesDeLaApp
import cu.spvi.app.common.explicacionCamara
import cu.spvi.app.common.rememberPermisoCamara
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviRadius
import cu.spvi.designsystem.token.SpviSpacing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.CardTone
import cu.spvi.designsystem.component.SpviButtonRow
import cu.spvi.designsystem.component.SpviSnackbarHost
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

object QrTags {
    const val CAMARA = "qr.camara"
    const val EXPLICACION = "qr.explicacion"
    const val ACCION_PERMISO = "qr.accion_permiso"
}

/** Lector QR mínimo (solo vinculación): cámara → texto → atrás. Lo inválido lo informa quien lo usa. */
@Composable
fun QrVinculacionScreen(
    onBack: () -> Unit,
    onQrLeido: (String) -> Unit = {},
    viewModel: QrVinculacionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val permiso = rememberPermisoCamara(false, onSolicitado = {})
    val camaraOk = permiso.estado == EstadoPermiso.CONCEDIDO
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoQr.QrLeido -> onQrLeido(e.texto)
            }
        }
    }
    Scaffold(
        topBar = { SpviTopBar(title = "Escanear QR", onBack = onBack) },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(SpviSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SpviSpacing.md),
        ) {
            if (!camaraOk) {
                val exp = explicacionCamara(permiso.estado)
                SpviCard(tone = CardTone.Tonal, modifier = Modifier.testTag(QrTags.EXPLICACION)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.md)) {
                        Icon(SpviIcons.Camara, contentDescription = null)
                        Text(exp.titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    }
                    Text(exp.detalle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    exp.boton?.let {
                        SpviButtonRow {
                            SpviPrimaryButton(
                                text = it,
                                icon = if (exp.accion == AccionPermiso.ABRIR_AJUSTES) SpviIcons.Ajustes else SpviIcons.Camara,
                                onClick = {
                                    when (exp.accion) {
                                        AccionPermiso.SOLICITAR -> permiso.solicitar()
                                        AccionPermiso.ABRIR_AJUSTES -> abrirAjustesDeLaApp(context)
                                        AccionPermiso.NINGUNA -> Unit
                                    }
                                },
                                modifier = Modifier.testTag(QrTags.ACCION_PERMISO),
                            )
                        }
                    }
                }
            } else {
                CamaraEscaner(
                    onQr = viewModel::qrDetectado, onError = onBack,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(SpviRadius.lg)).testTag(QrTags.CAMARA),
                )
                SpviSecondaryText("Apunta al código QR de vinculación.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
