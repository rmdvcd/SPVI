package cu.shalev.app.ajustes.seed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing

/** Diálogo debug para generar la BD de prueba (solo debug; en release no hay fila ni diálogo). */
@Composable
fun DialogoSeed(vm: SeedViewModel, onCerrar: () -> Unit) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    ShalevDialog(
        title = "Generar datos de prueba",
        onDismiss = onCerrar,
        onConfirm = null,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                when (val e = estado) {
                    is SeedViewModel.Estado.Idle -> {
                        Text(
                            "Borra TODO lo actual y genera la bodega de prueba.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            ShalevPrimaryButton("Generar", onClick = vm::iniciar, icon = ShalevIcons.Reiniciar)
                        }
                    }
                    is SeedViewModel.Estado.Ejecutando -> {
                        Text("Día ${e.dia} de ${e.total}", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { if (e.total > 0) e.dia / e.total.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is SeedViewModel.Estado.Ok -> Text("Seed listo.", style = MaterialTheme.typography.bodyMedium)
                    is SeedViewModel.Estado.Error -> {
                        Text(e.mensaje, style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            ShalevPrimaryButton("Reintentar", onClick = vm::iniciar, icon = ShalevIcons.Reintentar)
                        }
                    }
                }
            }
        },
    )
}
