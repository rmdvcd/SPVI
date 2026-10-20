package cu.shalev.app.licencia

import cu.shalev.designsystem.theme.ShalevTextos
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextAlign
import cu.shalev.core.time.Dates
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.Licencia
import cu.shalev.licencia.LicenseState
import java.time.Instant

data class EstadoUi(val icon: ImageVector, val titulo: String, val detalle: String)

fun LicenseState.ui(): EstadoUi = when (this) {
    is LicenseState.Trial -> EstadoUi(
        ShalevIcons.Prueba, "Periodo de prueba",
        if (daysLeft == 1) "Queda 1 día de prueba." else "Quedan $daysLeft días de prueba.",
    )
    LicenseState.TrialExpired -> EstadoUi(ShalevIcons.Vencido, "Prueba finalizada", "Solicita una licencia para seguir usando Shalev.")
    is LicenseState.Active -> EstadoUi(ShalevIcons.Verificado, "Licencia ${tipo.etiqueta}", "Vence el ${Dates.day(venceEn)}.")
    is LicenseState.Perpetual -> EstadoUi(ShalevIcons.Verificado, "Licencia Perpetua", "Sin vencimiento.")
    is LicenseState.Expired -> EstadoUi(ShalevIcons.Vencido, "Licencia ${tipo.etiqueta} vencida", "Solicita una renovación para seguir usando Shalev.")
    LicenseState.Revoked -> EstadoUi(ShalevIcons.Revocado, "Licencia transferida", "Se recuperó en otro teléfono. Contacta al desarrollador si no fuiste tú.")
    LicenseState.ClockTampered -> EstadoUi(
        ShalevIcons.Reloj, "Fecha del sistema incorrecta",
        "La fecha del teléfono es anterior a la última registrada. Corrígela en los ajustes del sistema.",
    )
}

/** Estado de la licencia + emisión y tiempo restante cuando aportan algo (P28: sin IDs internos). */
@Composable
fun EstadoLicenciaCard(snapshot: Licencia, ahora: Instant) {
    val ui = snapshot.estado.ui()
    ShalevCard(tone = if (snapshot.estado.unlocked) CardTone.Highlight else CardTone.Tonal) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md)) {
            Icon(ui.icon, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                Text(ui.titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                // 0.28.0: la fecha de vencimiento («Vence el 30/09/2026.») va en seminegrita.
                val fecha = (snapshot.estado as? LicenseState.Active)?.let { Dates.day(it.venceEn) }
                if (fecha == null) Text(ui.detalle, style = MaterialTheme.typography.bodyMedium)
                else Text(ShalevTextos.resaltar(ui.detalle, fecha), style = MaterialTheme.typography.bodyMedium)
            }
        }
        // P28: sin IDs (internos). Solo filas que el encabezado no dice ya; si no hay ninguna, no se dibuja nada.
        snapshot.detalle(ahora).forEach { (etiqueta, valor) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ShalevSecondaryText(etiqueta)
                Text(valor, style = ShalevTextos.datoEn(MaterialTheme.typography.bodyMedium), textAlign = TextAlign.End) // 0.26.0: vencimiento, ID… en seminegrita
            }
        }
    }
}
