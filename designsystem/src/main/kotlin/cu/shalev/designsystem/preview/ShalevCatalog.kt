package cu.shalev.designsystem.preview

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cu.shalev.designsystem.component.BannerTone
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevBadge
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevChip
import cu.shalev.designsystem.component.ShalevEmptyState
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevLinearProgress
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevNavItem
import cu.shalev.designsystem.component.ShalevNavigationBar
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevStatusBanner
import cu.shalev.designsystem.component.ShalevStepper
import cu.shalev.designsystem.component.ShalevTab
import cu.shalev.designsystem.component.ShalevTabs
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.designsystem.token.ShalevSpacing

/** Catálogo visual del design system (previews y pantalla de diagnóstico en debug). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShalevCatalog() {
    var text by remember { mutableStateOf("") }
    var chip by remember { mutableStateOf(0) }
    var nav by remember { mutableStateOf("inicio") }
    Column(
        Modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
    ) {
        ShalevTopBar("Shalev", marca = true)
        Column(Modifier.padding(horizontal = ShalevSpacing.md), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md)) {
            ShalevCard(title = "Botones") {
                ShalevButtonRow {
                    ShalevSecondaryButton("Cancelar", icon = ShalevIcons.Cancelar, onClick = {})
                    ShalevPrimaryButton("Guardar", icon = ShalevIcons.Guardar, onClick = {})
                }
                ShalevButtonRow {
                    ShalevIconAction(ShalevIcons.Editar, "Editar", onClick = {})
                    ShalevIconAction(ShalevIcons.Compartir, "Compartir", onClick = {}, style = IconActionStyle.Tonal)
                    ShalevIconAction(ShalevIcons.Eliminar, "Eliminar", onClick = {}, containerColor = MaterialTheme.colorScheme.error)
                    ShalevIconAction(ShalevIcons.WhatsApp, "WhatsApp", onClick = {}, containerColor = ShalevTheme.colors.whatsapp)
                    ShalevIconAction(ShalevIcons.Confirmar, "Confirmar", onClick = {}, selected = true)
                }
            }
            ShalevCard(title = "Campos") {
                ShalevTextField(text, { text = it }, label = "Nombre", placeholder = "Ej.: María")
                ShalevTextField("12", {}, label = "Carnet de identidad", isError = true, errorText = "5–20 letras o números")
            }
            ShalevCard(title = "Chips y badges") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                    listOf("Mensual", "Semestral", "Anual").forEachIndexed { i, l ->
                        ShalevChip(l, selected = chip == i, onClick = { chip = i })
                    }
                    ShalevBadge(7)
                }
            }
            ShalevTabs(listOf(ShalevTab("Insumos", 2), ShalevTab("Elaborados")), selectedIndex = chip.coerceAtMost(1), onSelect = { chip = it })
            ShalevStepper(actual = 2, total = 3, titulo = "Enviar solicitud", onAtras = {}, onSiguiente = {})
            ShalevStatusBanner("Licencia activa: 12 días", BannerTone.Info)
            ShalevStatusBanner("Tu licencia vence en 5 días", BannerTone.Aviso, actionIcon = ShalevIcons.Abrir, actionDescription = "Renovar", onAction = {})
            ShalevStatusBanner("Tu licencia vence hoy", BannerTone.Critico, detail = "Renuévala para seguir vendiendo")
            ShalevEmptyState("Sin productos", "Agrega tu primer producto con el botón +", ayuda = {})
            ShalevCard(onClick = {}, tone = CardTone.Error) {
                Text("4", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onError)
                ShalevSecondaryText("Stock inventario bajo")
            }
            var acordeonAbierto by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
            cu.shalev.designsystem.component.ShalevAccordionCard(
                title = "Ventas por hora", expanded = acordeonAbierto, onToggle = { acordeonAbierto = !acordeonAbierto },
            ) {
                ShalevSecondaryText("Contenido que se muestra al expandir")
            }
            ShalevCard(title = "Ventas", titleCentered = true, tone = CardTone.Tonal) {
                ShalevListItem("Refresco de cola", subtitle = "Bebidas", value = "1,450.00 CUP")
                ShalevListItem("Pizza napolitana", subtitle = "Elaborado", value = "900.00 CUP", indicatorColor = MaterialTheme.colorScheme.secondary)
                ShalevLinearProgress(progress = 0.6f)
                ShalevSecondaryText("Texto secundario al 65 %")
            }
        }
        ShalevNavigationBar(
            items = listOf(
                ShalevNavItem("inicio", ShalevIcons.Inicio, "Inicio"),
                ShalevNavItem("inventario", ShalevIcons.Inventario, "Inventario"),
                ShalevNavItem("servicios", ShalevIcons.Servicios, "Servicios"),
                ShalevNavItem("registros", ShalevIcons.Registros, "Registros"),
                ShalevNavItem("ajustes", ShalevIcons.Ajustes, "Ajustes"),
            ),
            selectedKey = nav,
            onSelect = { nav = it.key },
        )
    }
}

@Preview(name = "Claro", showBackground = true, heightDp = 1100)
@Composable
private fun CatalogLightPreview() = ShalevTheme(darkTheme = false) { ShalevCatalog() }

@Preview(name = "Oscuro", showBackground = true, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CatalogDarkPreview() = ShalevTheme(darkTheme = true) { ShalevCatalog() }
