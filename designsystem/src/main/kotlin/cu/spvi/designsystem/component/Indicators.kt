package cu.spvi.designsystem.component

import cu.spvi.designsystem.theme.SpviTextos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import cu.spvi.designsystem.token.SpviSpacing
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import cu.spvi.designsystem.token.SpviSize

/** Badge numérico. Con 0 no se muestra. */
@Composable
fun SpviBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Badge(modifier = modifier) {
        Text(if (count > 99) "99+" else count.toString(), fontWeight = SpviTextos.PESO_DATO)
    }
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
                // 0.30.1: margen vertical para que las dos líneas no queden pegadas al borde del chip.
                Column(Modifier.padding(vertical = SpviSpacing.xs)) {
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
fun SpviLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().semantics { contentDescription = "Cargando" }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * 0.31.0 (P5): progreso circular (determinado si [progress] no es null). Para el cuadro de «Actualizar», donde el
 * resultado se lee de un vistazo: la barra mientras trabaja y el mensaje cuando termina.
 */
@Composable
fun SpviCircularProgress(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    contentDescription: String = "Progreso",
) {
    if (progress == null) {
        CircularProgressIndicator(
            modifier.size(SpviSize.progresoCircular).semantics { this.contentDescription = contentDescription },
        )
    } else {
        CircularProgressIndicator(
            progress = { progress },
            modifier = modifier.size(SpviSize.progresoCircular).semantics { this.contentDescription = contentDescription },
        )
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
