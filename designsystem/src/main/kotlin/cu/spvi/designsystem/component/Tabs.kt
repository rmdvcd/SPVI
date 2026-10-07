package cu.spvi.designsystem.component

import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import cu.spvi.designsystem.token.SpviRadius
import cu.spvi.designsystem.token.SpviSize
import cu.spvi.designsystem.token.SpviSpacing

/** Una pestaña: texto visible y, opcionalmente, un número (p. ej. elementos en alerta), que se lee tras el nombre. */
data class SpviTab(val label: String, val count: Int? = null, val testTag: String? = null) {
    val texto: String get() = if (count != null) "$label ($count)" else label
}

/**
 * P18 (A11): pestañas reales en lugar de chips. Elaboración (Insumos / Elaborados) y Registros las usan.
 *
 * - TalkBack anuncia «pestaña, seleccionada, 2 de 3»: rol Tab más información de colección.
 * - Seleccionada = resaltado invertido (fondo primary, texto onPrimary), igual que la barra inferior. No depende
 *   solo del color: el estado seleccionado también va en la semántica.
 * - Altura mínima de 48dp; si no cabe (letra grande, «Transferencias»), el texto ocupa 2 líneas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpviTabs(
    tabs: List<SpviTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val indice = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val mod = modifier.semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = tabs.size) }
    // P29: con más de 4 pestañas (Registros) no caben a 360dp sin partir palabras: fila desplazable.
    val contenido: @Composable () -> Unit = {
        tabs.forEachIndexed { i, tab ->
            val seleccionada = i == selectedIndex
            Tab(
                selected = seleccionada,
                onClick = { onSelect(i) },
                modifier = Modifier
                    .padding(horizontal = SpviSpacing.xs / 2, vertical = SpviSpacing.xs / 2)
                    .clip(RoundedCornerShape(SpviRadius.sm))
                    .background(if (seleccionada) cs.primary else Color.Transparent)
                    .heightIn(min = SpviSize.touchTarget)
                    .then(if (tab.testTag != null) Modifier.testTag(tab.testTag) else Modifier)
                    .semantics { collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = i, columnSpan = 1) },
                selectedContentColor = cs.onPrimary,
                unselectedContentColor = cs.onSurfaceVariant,
                text = {
                    Text(
                        tab.texto, style = MaterialTheme.typography.labelLarge, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
    if (tabs.size > MAX_FIJAS) {
        SecondaryScrollableTabRow(
            selectedTabIndex = indice, modifier = mod, containerColor = cs.surface, contentColor = cs.onSurface,
            edgePadding = SpviSpacing.xs, indicator = {}, divider = {}, tabs = contenido,
        )
    } else {
        SecondaryTabRow(
            selectedTabIndex = indice, modifier = mod, containerColor = cs.surface, contentColor = cs.onSurface,
            indicator = {}, divider = {}, tabs = contenido,
        )
    }
}

private const val MAX_FIJAS = 4
