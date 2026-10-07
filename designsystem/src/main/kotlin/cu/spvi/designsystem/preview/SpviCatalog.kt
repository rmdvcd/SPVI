package cu.spvi.designsystem.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cu.spvi.designsystem.component.BannerTone
import cu.spvi.designsystem.component.CardTone
import cu.spvi.designsystem.component.IconActionStyle
import cu.spvi.designsystem.component.SpviAlertCounter
import cu.spvi.designsystem.component.SpviBadge
import cu.spvi.designsystem.component.SpviButtonRow
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviChip
import cu.spvi.designsystem.component.SpviEmptyState
import cu.spvi.designsystem.component.SpviIconAction
import cu.spvi.designsystem.component.SpviLinearProgress
import cu.spvi.designsystem.component.SpviListItem
import cu.spvi.designsystem.component.SpviNavItem
import cu.spvi.designsystem.component.SpviNavigationBar
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSecondaryButton
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviStatusBanner
import cu.spvi.designsystem.component.SpviStepper
import cu.spvi.designsystem.component.SpviTab
import cu.spvi.designsystem.component.SpviTabs
import cu.spvi.designsystem.component.SpviTextField
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.theme.AlertTone
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.designsystem.token.SpviSpacing

/** Catálogo visual del design system (previews y pantalla de diagnóstico en debug). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpviCatalog() {
    var text by remember { mutableStateOf("") }
    var chip by remember { mutableStateOf(0) }
    var nav by remember { mutableStateOf("inicio") }
    Column(
        Modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpviSpacing.md),
    ) {
        SpviTopBar("SPVI", marca = true)
        Column(Modifier.padding(horizontal = SpviSpacing.md), verticalArrangement = Arrangement.spacedBy(SpviSpacing.md)) {
            SpviCard(title = "Botones") {
                SpviButtonRow {
                    SpviSecondaryButton("Cancelar", icon = SpviIcons.Cancelar, onClick = {})
                    SpviPrimaryButton("Guardar", icon = SpviIcons.Guardar, onClick = {})
                }
                SpviButtonRow {
                    SpviIconAction(SpviIcons.Editar, "Editar", onClick = {})
                    SpviIconAction(SpviIcons.Compartir, "Compartir", onClick = {}, style = IconActionStyle.Tonal)
                    SpviIconAction(SpviIcons.Eliminar, "Eliminar", onClick = {}, containerColor = MaterialTheme.colorScheme.error)
                    SpviIconAction(SpviIcons.WhatsApp, "WhatsApp", onClick = {}, containerColor = SpviTheme.colors.whatsapp)
                    SpviIconAction(SpviIcons.Confirmar, "Confirmar", onClick = {}, selected = true)
                }
            }
            SpviCard(title = "Campos") {
                SpviTextField(text, { text = it }, label = "Nombre", placeholder = "Ej.: María")
                SpviTextField("12", {}, label = "Carnet de identidad", isError = true, errorText = "5–20 letras o números")
            }
            SpviCard(title = "Chips y badges") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                    listOf("Mensual", "Semestral", "Anual").forEachIndexed { i, l ->
                        SpviChip(l, selected = chip == i, onClick = { chip = i })
                    }
                    SpviBadge(7)
                }
            }
            SpviTabs(listOf(SpviTab("Insumos", 2), SpviTab("Elaborados")), selectedIndex = chip.coerceAtMost(1), onSelect = { chip = it })
            SpviStepper(actual = 2, total = 3, titulo = "Enviar solicitud", onAtras = {}, onSiguiente = {})
            SpviStatusBanner("Licencia activa: 12 días", BannerTone.Info)
            SpviStatusBanner("Tu licencia vence en 5 días", BannerTone.Aviso, actionIcon = SpviIcons.Abrir, actionDescription = "Renovar", onAction = {})
            SpviStatusBanner("Tu licencia vence hoy", BannerTone.Critico, detail = "Renuévala para seguir vendiendo")
            SpviEmptyState("Sin productos", "Agrega tu primer producto con el botón +", ayuda = {})
            SpviAlertCounter("Stock inventario bajo", 4, AlertTone.StockBajo, onClick = {})
            SpviCard(title = "Ventas", titleCentered = true, tone = CardTone.Tonal) {
                SpviListItem("Refresco de cola", subtitle = "Bebidas", value = "1,450.00 CUP")
                SpviListItem("Pizza napolitana", subtitle = "Elaborado", value = "900.00 CUP", indicatorColor = MaterialTheme.colorScheme.secondary)
                SpviLinearProgress(progress = 0.6f)
                SpviSecondaryText("Texto secundario al 65 %")
            }
        }
        SpviNavigationBar(
            items = listOf(
                SpviNavItem("inicio", SpviIcons.Inicio, "Inicio"),
                SpviNavItem("inventario", SpviIcons.Inventario, "Inventario"),
                SpviNavItem("servicios", SpviIcons.Servicios, "Servicios"),
                SpviNavItem("registros", SpviIcons.Registros, "Registros"),
                SpviNavItem("ajustes", SpviIcons.Ajustes, "Ajustes"),
            ),
            selectedKey = nav,
            onSelect = { nav = it.key },
        )
    }
}

@Preview(name = "Claro", showBackground = true, heightDp = 1100)
@Composable
private fun CatalogLightPreview() = SpviTheme(darkTheme = false) { SpviCatalog() }

@Preview(name = "Oscuro", showBackground = true, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CatalogDarkPreview() = SpviTheme(darkTheme = true) { SpviCatalog() }
