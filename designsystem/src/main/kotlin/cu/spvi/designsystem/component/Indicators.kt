package cu.spvi.designsystem.component

import cu.spvi.designsystem.theme.SpviTextos
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import cu.spvi.designsystem.theme.AlertTone
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.designsystem.theme.of
import cu.spvi.designsystem.token.SpviMotion
import cu.spvi.designsystem.token.SpviSize
import cu.spvi.designsystem.token.ColorTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign

/** Badge numérico. Con 0 no se muestra. */
@Composable
fun SpviBadge(
    count: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.error,
    contentColor: Color = MaterialTheme.colorScheme.onError,
) {
    if (count <= 0) return
    Badge(modifier = modifier, containerColor = containerColor, contentColor = contentColor) {
        Text(if (count > 99) "99+" else count.toString(), fontWeight = SpviTextos.PESO_DATO)
    }
}

/**
 * Contador de alerta de Inicio: número en el color semántico de la alerta (AA en ambos temas).
 * Contador 0 → oculto (con animación). Pulsar → navega a Inventario filtrado.
 * 0.21.1 (H4): [AlertTone.SinExistencia] va relleno (fondo error, número y texto onError) para no confundirse con
 * «Stock crítico», que usa el mismo rojo sobre la superficie.
 */
@Composable
fun SpviAlertCounter(label: String, count: Int, tone: AlertTone, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = count > 0, modifier = modifier, enter = SpviMotion.enterVertical, exit = SpviMotion.exitVertical) {
        val relleno = tone == AlertTone.SinExistencia
        SpviCard(
            onClick = onClick,
            tone = if (relleno) CardTone.Error else CardTone.Default,
            modifier = Modifier.fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = "$label: $count" },
        ) {
            // 0.27.0 (T14): contenido centrado; la tarjeta mide lo que su contenido y el texto puede ocupar 2 líneas.
            Column(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
                if (relleno) {
                    val sobreError = MaterialTheme.colorScheme.onError
                    Text(count.toString(), style = MaterialTheme.typography.headlineSmall, color = sobreError, textAlign = TextAlign.Center)
                    Text(label, style = MaterialTheme.typography.bodySmall, color = sobreError, textAlign = TextAlign.Center)
                } else {
                    Text(
                        count.toString(), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                        color = if (tone == AlertTone.Revisar) MaterialTheme.colorScheme.primary else SpviTheme.colors.of(tone),
                    )
                    SpviSecondaryText(label, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** 0.27.0 (T3): puesto del Top 3 de Inicio. */
enum class Puesto(val descripcion: String) { PRIMERO("Primer puesto"), SEGUNDO("Segundo puesto"), TERCERO("Tercer puesto") }

/**
 * 0.27.0 (T3): medalla del Top 3. El número del puesto (1, 2, 3) dentro de un círculo oro, plata o bronce; el número
 * usa el peso de dato y un color con contraste AA sobre cada medalla. TalkBack lee «Primer puesto»… Sin decoración extra.
 */
@Composable
fun SpviMedalla(puesto: Puesto, modifier: Modifier = Modifier) {
    val (fondo, texto) = when (puesto) {
        Puesto.PRIMERO -> SpviMedallaColores.oro
        Puesto.SEGUNDO -> SpviMedallaColores.plata
        Puesto.TERCERO -> SpviMedallaColores.bronce
    }
    Box(
        modifier.size(SpviSize.medalla).background(fondo, CircleShape).clearAndSetSemantics { contentDescription = puesto.descripcion },
        contentAlignment = Alignment.Center,
    ) {
        Text("${puesto.ordinal + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = SpviTextos.PESO_DATO, color = texto)
    }
}

/** Colores de las medallas (fondo, número), desde [cu.spvi.designsystem.token.ColorTokens]. */
object SpviMedallaColores {
    val oro = Color(ColorTokens.MEDALLA_ORO) to Color(ColorTokens.ON_MEDALLA_ORO)
    val plata = Color(ColorTokens.MEDALLA_PLATA) to Color(ColorTokens.ON_MEDALLA_PLATA)
    val bronce = Color(ColorTokens.MEDALLA_BRONCE) to Color(ColorTokens.ON_MEDALLA_BRONCE)
}

/** Chip de filtro/selección con resaltado invertido al seleccionarse. */
@Composable
fun SpviChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supportingLabel: String? = null,
    /** 0.28.0: el texto de apoyo también es un dato (p. ej. el precio del tipo de licencia) y va en seminegrita. */
    supportingEsDato: Boolean = false,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        label = {
            if (supportingLabel == null) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            } else {
                Column {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Text(
                        supportingLabel,
                        style = if (supportingEsDato) SpviTextos.datoEn(MaterialTheme.typography.labelSmall)
                        else MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(SpviSize.iconSmall)) } },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = cs.primary,
            selectedLabelColor = cs.onPrimary,
            selectedLeadingIconColor = cs.onPrimary,
        ),
    )
}

/** Carga a pantalla completa (o dentro de un contenedor). */
@Composable
fun SpviLoading(modifier: Modifier = Modifier, contentDescription: String = "Cargando") {
    Box(modifier.fillMaxSize().semantics { this.contentDescription = contentDescription }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Progreso lineal (determinado si [progress] no es null). */
@Composable
fun SpviLinearProgress(modifier: Modifier = Modifier, progress: Float? = null) {
    if (progress == null) {
        LinearProgressIndicator(modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(progress = { progress }, modifier = modifier.fillMaxWidth())
    }
}
