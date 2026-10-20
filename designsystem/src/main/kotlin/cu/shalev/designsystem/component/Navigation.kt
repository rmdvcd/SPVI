package cu.shalev.designsystem.component

import androidx.compose.ui.unit.sp
import cu.shalev.designsystem.theme.ShalevMarca
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevElevation

@Immutable
data class ShalevNavItem(val key: String, val icon: ImageVector, val label: String)

/**
 * Barra inferior: SOLO iconos sólidos, sin texto visible. Seleccionado = resaltado invertido
 * (indicador primary + icono onPrimary). El nombre sigue disponible para TalkBack y como tooltip.
 */
@Composable
fun ShalevNavigationBar(
    items: List<ShalevNavItem>,
    selectedKey: String?,
    onSelect: (ShalevNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    NavigationBar(modifier = modifier, containerColor = cs.surfaceContainer, tonalElevation = ShalevElevation.none) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.key == selectedKey,
                onClick = { onSelect(item) },
                icon = { TooltipIcon(item.icon, item.label) },
                label = null,
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = cs.onPrimary,
                    indicatorColor = cs.primary,
                    unselectedIconColor = cs.onSurfaceVariant,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipIcon(icon: ImageVector, label: String) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) { Icon(icon, contentDescription = label) }
}

/**
 * Barra superior estándar, centrada (0.27.0, T1): título en el centro y sin logo; Atrás a la izquierda y las acciones
 * (ShalevIconAction) a la derecha. Un título largo pasa a 2 líneas como mucho y nunca tapa las acciones.
 * [marca] = true dibuja el título con la tipografía de marca (solo cuando el título es el nombre de la app, «Shalev»).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShalevTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    marca: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                title,
                style = if (marca) MaterialTheme.typography.titleLarge.copy(fontFamily = ShalevMarca.fuente, letterSpacing = 2.sp)
                else MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(ShalevIcons.Atras, contentDescription = "Atrás") }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}
