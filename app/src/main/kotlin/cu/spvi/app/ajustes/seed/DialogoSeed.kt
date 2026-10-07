package cu.spvi.app.ajustes.seed

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
import cu.spvi.designsystem.component.SpviDialog
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviSpacing

/** Diálogo debug para generar la BD de prueba (solo debug; en release no hay fila ni diálogo). */
@Composable
fun DialogoSeed(vm: SeedViewModel, onCerrar: () -> Unit) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    SpviDialog(
        title = "Generar datos de prueba",
        onDismiss = onCerrar,
        onConfirm = null,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                when (val e = estado) {
                    is SeedViewModel.Estado.Idle -> {
                        Text(
                            "Borra TODO lo actual y genera la bodega de prueba (año y medio de ventas).",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            SpviPrimaryButton("Generar", onClick = vm::iniciar, icon = SpviIcons.Reiniciar)
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
                            SpviPrimaryButton("Reintentar", onClick = vm::iniciar, icon = SpviIcons.Reintentar)
                        }
                    }
                }
            }
        },
    )
}
