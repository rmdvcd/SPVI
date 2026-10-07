package cu.spvi.designsystem.component

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviSize
import cu.spvi.designsystem.token.SpviSpacing

/** Textos del asistente: funciones puras para poder probarlas en JVM. */
object SpviStepperTextos {
    fun paso(actual: Int, total: Int): String = "Paso $actual de $total"
    fun anuncio(actual: Int, total: Int, titulo: String): String = "${paso(actual, total)}: $titulo"
    const val ATRAS = "Paso anterior"
    const val SIGUIENTE = "Paso siguiente"
}

/**
 * P18 (A08, A09): cabecera de asistente (Licencia, Respaldo, Onboarding, Migrar).
 *
 * - Muestra «Paso 2 de 3», el título del paso y una barra de progreso.
 * - Al cambiar de paso, TalkBack lo anuncia («Paso 2 de 3: Enviar solicitud») gracias a una región viva.
 * - Atrás y Siguiente son botones de icono de 48dp. Si no se pasan, no se muestran; si falta solo uno, su hueco
 *   se reserva para que el título no salte. Sin ninguno (Onboarding, Migrar, Importar), el título usa todo el ancho.
 * - [siguienteHabilitado] = false hasta que el paso tenga datos válidos.
 */
@Composable
fun SpviStepper(
    actual: Int,
    total: Int,
    titulo: String,
    modifier: Modifier = Modifier,
    onAtras: (() -> Unit)? = null,
    onSiguiente: (() -> Unit)? = null,
    siguienteHabilitado: Boolean = true,
) {
    val conBotones = onAtras != null || onSiguiente != null
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            if (onAtras != null) {
                SpviIconAction(SpviIcons.Atras, SpviStepperTextos.ATRAS, onClick = onAtras)
            } else if (conBotones) {
                Spacer(Modifier.size(SpviSize.touchTarget))
            }
            Column(
                Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = SpviStepperTextos.anuncio(actual, total, titulo)
                    liveRegion = LiveRegionMode.Polite
                    heading()
                },
                horizontalAlignment = Alignment.CenterHorizontally, // P28: centrado entre Atrás y Siguiente
            ) {
                SpviSecondaryText(SpviStepperTextos.paso(actual, total), textAlign = TextAlign.Center)
                Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            if (onSiguiente != null) {
                SpviIconAction(
                    SpviIcons.Abrir, SpviStepperTextos.SIGUIENTE, onClick = onSiguiente,
                    enabled = siguienteHabilitado, style = IconActionStyle.Filled,
                )
            } else if (conBotones) {
                Spacer(Modifier.size(SpviSize.touchTarget))
            }
        }
        SpviLinearProgress(Modifier.fillMaxWidth(), progress = actual.toFloat() / total.coerceAtLeast(1))
    }
}
