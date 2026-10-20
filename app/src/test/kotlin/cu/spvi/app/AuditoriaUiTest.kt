package cu.spvi.app

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cu.spvi.app.common.descripcionFiltro
import cu.spvi.app.precios.AccionesPrecios
import cu.spvi.app.precios.PreajusteForm
import cu.spvi.app.precios.PreciosContent
import cu.spvi.app.precios.PreciosUiState
import cu.spvi.designsystem.component.SpviAccordionCard
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviIconAction
import cu.spvi.designsystem.component.SpviListItem
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.theme.SpviTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Auditoría de UI de la 0.31.0: comprueba los arreglos de semántica y el formulario de Precios a 360 dp.
 * Son pruebas de comportamiento, no capturas: fallan si el título deja de ser encabezado, si un componente
 * no conmutable anuncia «seleccionado = false» o si el chip de Transferencia vuelve a aplastarse.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class AuditoriaUiTest {
    @get:Rule val rule = createComposeRule()

    private val accionesPrecios = AccionesPrecios(
        onNuevo = {}, onEditar = {}, onActivar = { _, _ -> }, onCambiar = {}, onProducto = {}, onElegirVisibles = {},
        onGuardar = {}, onCerrar = {}, onBorrar = {}, onConfirmarBorrado = {}, onCancelarBorrado = {},
    )

    @Test
    fun elTituloDeUnaCardEsEncabezado() {
        rule.setContent { SpviTheme { SpviCard(title = "Tus datos") {} } }

        rule.onNodeWithText("Tus datos")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun elAcordeonOcultaElResumenAlAbrirse() {
        val abierto = mutableStateOf(false)
        rule.setContent {
            SpviTheme {
                SpviAccordionCard(
                    title = "Ventas",
                    subtitle = "1,000.00 CUP",
                    expanded = abierto.value,
                    onToggle = { abierto.value = !abierto.value },
                ) { androidx.compose.material3.Text("Detalle") }
            }
        }

        rule.onNodeWithText("1,000.00 CUP").assertExists()
        rule.onNodeWithText("Ventas").performClick()
        rule.onNodeWithText("1,000.00 CUP").assertDoesNotExist()
    }

    @Test
    fun unBotonNoConmutableNoAnunciaSeleccion() {
        rule.setContent { SpviTheme { SpviIconAction(SpviIcons.Agregar, "Agregar", onClick = {}) } }

        val config = rule.onNodeWithContentDescription("Agregar").fetchSemanticsNode().config
        assertFalse(config.contains(SemanticsProperties.Selected))
    }

    @Test
    fun unBotonConmutableSiAnunciaSeleccion() {
        rule.setContent { SpviTheme { SpviIconAction(SpviIcons.Filtrar, "Filtrar", onClick = {}, selected = true) } }

        val config = rule.onNodeWithContentDescription("Filtrar").fetchSemanticsNode().config
        assertTrue(config[SemanticsProperties.Selected])
    }

    @Test
    fun unaFilaNoSeleccionableNoAnunciaSeleccion() {
        rule.setContent { SpviTheme { SpviListItem(title = "Producto", onClick = {}) } }

        val config = rule.onNodeWithText("Producto").fetchSemanticsNode().config
        assertFalse(config.contains(SemanticsProperties.Selected))
    }

    @Test
    fun laDescripcionDelFiltroIncluyeElConteo() {
        assertEquals("Filtrar", descripcionFiltro("Filtrar", 0))
        assertEquals("Filtrar, 1 filtro activo", descripcionFiltro("Filtrar", 1))
        assertEquals("Filtrar, 2 filtros activos", descripcionFiltro("Filtrar", 2))
    }

    @Test
    fun elChipDeTransferenciaNoSeAplastaEnElFormularioDePrecios() {
        rule.setContent {
            SpviTheme {
                PreciosContent(
                    state = PreciosUiState(
                        cargado = true,
                        form = PreajusteForm(nombre = "Transferencia +5 %", porcentaje = "5", productoIds = setOf(1)),
                    ),
                    onBack = {},
                    acciones = accionesPrecios,
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)

        val ancho = rule.onNodeWithText("Transferencia").fetchSemanticsNode().boundsInRoot.width
        val minimo = with(rule.density) { 100.dp.toPx() }
        assertTrue("el chip de Transferencia quedó aplastado: ${ancho}px (mínimo $minimo)", ancho >= minimo)
    }
}
