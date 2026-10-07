package cu.spvi.app.vinculacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.spvi.designsystem.component.SpviButtonRow
import cu.spvi.designsystem.component.SpviDialog
import cu.spvi.designsystem.component.SpviEmptyState
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSnackbarHost
import cu.spvi.designsystem.component.SpviTextButton
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviSpacing

/**
 * P37: puerta de la app SECUNDARIA cuando la licencia de la principal no está activa. Aquí no se activa nada (la
 * licencia es de la principal): se sincroniza para recibir la licencia nueva, o se desvincula.
 */
@Composable
fun BloqueoSecundaria(viewModel: VinculacionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.mensajes.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        topBar = { SpviTopBar(title = "SPVI", marca = true) },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(SpviSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SpviSpacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpviEmptyState(
                title = TITULO_BLOQUEO,
                detail = DETALLE_BLOQUEO,
            )
            SpviButtonRow {
                SpviPrimaryButton(
                    text = VinculacionLogic.SINCRONIZAR, icon = SpviIcons.Sincronizar, onClick = viewModel::sincronizar,
                    loading = state.trabajando, enabled = !state.trabajando,
                )
            }
            SpviButtonRow {
                SpviTextButton(text = VinculacionLogic.DESVINCULAR, icon = SpviIcons.Desvincular, onClick = viewModel::pedirDesvincular, enabled = !state.trabajando)
            }
        }
    }
    if (state.confirmarDesvincular) {
        SpviDialog(
            title = "¿Desvincular esta app?",
            text = VinculacionLogic.CONFIRMAR_DESVINCULAR,
            onDismiss = viewModel::cancelarDesvincular,
            onConfirm = viewModel::confirmarDesvincular,
            confirmDescription = VinculacionLogic.DESVINCULAR,
            confirmIcon = SpviIcons.Desvincular,
            destructive = true,
        )
    }
}

private const val TITULO_BLOQUEO = "La licencia de la app principal no está activa"
private const val DETALLE_BLOQUEO =
    "Esta app trabaja con la licencia de la app principal. Pide al dueño que la active y luego toca «Sincronizar ahora» con los dos teléfonos en la misma wifi."
