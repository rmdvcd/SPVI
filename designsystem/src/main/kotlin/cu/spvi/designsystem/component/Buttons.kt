package cu.spvi.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import cu.spvi.designsystem.token.SpviFontScale
import cu.spvi.designsystem.token.SpviMotion
import cu.spvi.designsystem.token.SpviSize
import cu.spvi.designsystem.token.SpviSpacing

/** Botón solo icono: 12dp a cada lado de un icono de 24dp → 48 × 48dp, el área táctil mínima (Fitts). */
private val PaddingSoloIcono = PaddingValues(horizontal = SpviSpacing.md - SpviSpacing.xs / 2)

/**
 * P18 (A01): altura de los botones. Normalmente va de 40 a 48dp. Con letra grande del sistema
 * ([SpviFontScale.GRANDE]) se quita el máximo: la etiqueta puede ocupar 2 líneas y nunca se recorta (WCAG 1.4.4).
 * P24: los botones solo icono miden siempre 48dp de alto (área táctil completa).
 */
@Composable
private fun Modifier.alturaBoton(soloIcono: Boolean): Modifier = when {
    soloIcono -> heightIn(min = SpviSize.touchTarget).widthIn(min = SpviSize.touchTarget)
    letraGrande() -> heightIn(min = SpviSize.buttonMin)
    else -> heightIn(min = SpviSize.buttonMin, max = SpviSize.buttonMax)
}

/** true si el usuario eligió letra grande en el sistema (≥ [SpviFontScale.GRANDE]). */
@Composable
@ReadOnlyComposable
fun letraGrande(): Boolean = LocalDensity.current.fontScale >= SpviFontScale.GRANDE

/**
 * P24: los botones de SPVI son **solo icono**.
 * - [text] no se pinta: es la descripción que lee TalkBack y el tooltip que aparece al mantener pulsado.
 * - El ancho se ajusta al contenido con un margen ligero (48dp con icono solo). Nunca ocupan todo el ancho.
 * - P25: sin excepciones (también «Nueva venta»). La acción principal se distingue por ir rellena y centrada.
 */
@Composable
fun SpviPrimaryButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    ConTooltip(text) {
        Button(
            onClick = onClick,
            modifier = modifier.alturaBoton(true).animateContentSize(SpviMotion.muelle()),
            enabled = enabled && !loading,
            shape = CircleShape,
            contentPadding = PaddingSoloIcono,
        ) { ButtonContent(text, icon, loading) }
    }
}

/** Acción secundaria: contorno, sin relleno. Solo icono (ver [SpviPrimaryButton]). */
@Composable
fun SpviSecondaryButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    ConTooltip(text) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.alturaBoton(true),
            enabled = enabled && !loading,
            shape = CircleShape,
            contentPadding = PaddingSoloIcono,
            border = ButtonDefaults.outlinedButtonBorder(enabled),
        ) { ButtonContent(text, icon, loading) }
    }
}

/** Acción terciaria: sin fondo ni borde. Solo icono (ver [SpviPrimaryButton]). */
@Composable
fun SpviTextButton(text: String, onClick: () -> Unit, icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true) {
    ConTooltip(text) {
        TextButton(
            onClick = onClick,
            modifier = modifier.alturaBoton(true),
            enabled = enabled,
            shape = CircleShape,
            contentPadding = PaddingSoloIcono,
        ) { ButtonContent(text, icon, loading = false) }
    }
}

/** Tooltip con la descripción del botón al mantenerlo pulsado (los botones solo icono no tienen texto visible). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConTooltip(texto: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(texto) } },
        state = rememberTooltipState(),
        content = content,
    )
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector, loading: Boolean) {
    if (loading) {
        CircularProgressIndicator(
            Modifier.size(SpviSize.icon).semantics { contentDescription = text },
            strokeWidth = SpviSize.strokeRegular, color = LocalContentColor.current,
        )
    } else {
        Icon(icon, contentDescription = text, modifier = Modifier.size(SpviSize.icon))
    }
}

enum class IconActionStyle { Standard, Tonal, Filled }

/**
 * Botón SOLO icono (Confirmar, Cancelar, Editar, Agregar, Eliminar, Compartir, Importar, Exportar, WhatsApp, SMS…).
 * - Área táctil de 48dp.
 * - [contentDescription] obligatorio: lo lee TalkBack y aparece como tooltip al mantener pulsado.
 * - [selected] = resaltado invertido (fondo primary, icono onPrimary), animado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpviIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    style: IconActionStyle = IconActionStyle.Standard,
    containerColor: Color? = null,
) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when {
        selected -> cs.primary to cs.onPrimary
        containerColor != null -> containerColor to contentColorFor(containerColor).takeOrElse(Color.White)
        style == IconActionStyle.Filled -> cs.primary to cs.onPrimary
        // 0.21.1 (H2): el tonal usa el terciario (petróleo). El naranja (secondaryContainer) queda solo para avisos.
        style == IconActionStyle.Tonal -> cs.tertiaryContainer to cs.onTertiaryContainer
        else -> Color.Transparent to cs.onSurfaceVariant
    }
    val animBg by animateColorAsState(bg, SpviMotion.tween(), label = "iconActionBg")
    val animFg by animateColorAsState(fg, SpviMotion.tween(), label = "iconActionFg")

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(contentDescription) } },
        state = rememberTooltipState(),
    ) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(SpviSize.touchTarget).semantics { this.selected = selected },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = animBg, contentColor = animFg),
        ) { Icon(icon, contentDescription = contentDescription) }
    }
}

private fun Color.takeOrElse(fallback: Color) = if (this == Color.Unspecified) fallback else this

/**
 * Fila de acciones de formularios, diálogos y ventanas emergentes.
 * P24: **centradas** y con separación media (16dp), en orden Cancelar → Confirmar (Jakob: el orden de Android).
 */
@Composable
fun SpviButtonRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpviSpacing.md, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * P24: barra inferior fija de los formularios (Guardar, Siguiente…). Sustituye al «Guardar» repetido arriba y abajo:
 * una sola acción, siempre visible y al alcance del pulgar (Fitts), centrada (ver [SpviButtonRow]).
 */
@Composable
fun SpviBarraAcciones(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SpviButtonRow(Modifier.navigationBarsPadding().padding(horizontal = SpviSpacing.md, vertical = SpviSpacing.xs), content = content)
        }
    }
}
