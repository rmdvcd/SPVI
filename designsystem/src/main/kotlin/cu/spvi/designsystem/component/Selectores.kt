package cu.spvi.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviMotion
import cu.spvi.designsystem.token.SpviRadius
import cu.spvi.designsystem.token.SpviSize
import cu.spvi.designsystem.token.SpviSpacing

/**
 * P24: lista desplegable (combobox) de solo lectura. Sustituye a las filas de chips cuando hay que elegir UNA opción
 * de una lista corta (Período de Inicio). Ocupa una sola fila sea cual sea el número de opciones (Hick: se ve la
 * elegida, el resto solo al abrir) y se comporta como el desplegable estándar de Android (Jakob).
 *
 * P25: aspecto de la app, no de campo de formulario:
 * - Superficie tonal con radio 16dp, la misma que las tarjetas de acceso de Inicio (Pago electrónico, Precios).
 * - Icono en un círculo primaryContainer; encima, la etiqueta pequeña y, debajo, la opción elegida en negrita.
 * - Flecha que gira al abrir. Menú con radio 16dp; la opción elegida va resaltada (primaryContainer + ✓).
 *
 * TalkBack: «Período, Hoy, lista desplegable»; al abrir, cada opción es un elemento de menú.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SpviComboBox(
    label: String,
    opciones: List<T>,
    seleccion: T,
    etiqueta: (T) -> String,
    onSeleccion: (T) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    tagOpcion: ((T) -> String)? = null,
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val giro by animateFloatAsState(if (abierto) 180f else 0f, SpviMotion.muelle(), label = "flecha")
    val forma = RoundedCornerShape(SpviRadius.lg)
    ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }, modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = forma,
            color = if (dark) cs.surfaceContainerHigh else cs.surfaceContainer,
            border = if (abierto) BorderStroke(SpviSize.strokeRegular, cs.primary) else null,
            modifier = modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .heightIn(min = SpviSize.textField)
                .semantics(mergeDescendants = true) { role = Role.DropdownList; stateDescription = etiqueta(seleccion) },
        ) {
            Row(
                Modifier.padding(horizontal = SpviSpacing.md, vertical = SpviSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpviSpacing.md),
            ) {
                leadingIcon?.let {
                    Box(
                        Modifier.size(SpviSize.touchTarget - SpviSpacing.xs).clip(CircleShape).background(cs.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(it, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(SpviSize.iconSmall)) }
                }
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text(etiqueta(seleccion), style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Icon(SpviIcons.Desplegar, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.rotate(giro))
            }
        }
        ExposedDropdownMenu(
            expanded = abierto,
            onDismissRequest = { abierto = false },
            shape = forma,
            containerColor = if (dark) cs.surfaceContainerHighest else cs.surfaceContainerLow,
        ) {
            opciones.forEach { o ->
                val elegida = o == seleccion
                DropdownMenuItem(
                    text = {
                        Text(
                            etiqueta(o), style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (elegida) FontWeight.SemiBold else null,
                            color = if (elegida) cs.onPrimaryContainer else cs.onSurface,
                        )
                    },
                    onClick = { abierto = false; onSeleccion(o) },
                    trailingIcon = { if (elegida) Icon(SpviIcons.Confirmar, contentDescription = null, tint = cs.onPrimaryContainer) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier = Modifier
                        .padding(horizontal = SpviSpacing.xs)
                        .clip(RoundedCornerShape(SpviRadius.md))
                        .background(if (elegida) cs.primaryContainer else Color.Transparent)
                        .heightIn(min = SpviSize.touchTarget)
                        .semantics { selected = elegida }
                        .then(if (tagOpcion != null) Modifier.testTag(tagOpcion(o)) else Modifier),
                )
            }
        }
    }
}

/**
 * P24: texto que **no se parte ni se corta** mientras pueda evitarse: si no cabe en [maxLines], reduce el tamaño
 * poco a poco hasta [minimo] (11sp, escalado con la letra del sistema); solo por debajo de ese mínimo aparece «…».
 * Para importes, celdas de tabla, etiquetas de gráficos y chips. Mientras se ajusta no se dibuja (sin parpadeo).
 */
@Composable
fun SpviTextoAjustable(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    minimo: TextUnit = 11.sp,
) {
    val base = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    var escala by remember(text, style) { mutableFloatStateOf(1f) }
    var listo by remember(text, style) { mutableStateOf(false) }
    Text(
        text = text,
        modifier = modifier.drawWithContent { if (listo) drawContent() },
        style = style.copy(fontSize = base * escala, lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * escala else style.lineHeight),
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { r ->
            if (r.hasVisualOverflow && base.value * escala * PASO >= minimo.value) escala *= PASO else listo = true
        },
    )
}

/** Reducción de cada intento de [SpviTextoAjustable]: un 8 %. */
private const val PASO = 0.92f
