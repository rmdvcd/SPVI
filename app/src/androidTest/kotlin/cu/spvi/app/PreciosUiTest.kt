package cu.spvi.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.spvi.app.precios.AccionesPrecios
import cu.spvi.app.precios.PreciosContent
import cu.spvi.app.precios.PreciosTags
import cu.spvi.app.precios.PreciosUiState
import cu.spvi.app.precios.PreajusteForm
import cu.spvi.designsystem.theme.SpviTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreciosUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun productosMarcadosQuedanFijadosSobreElBuscadorSinDuplicarse() {
        val productos = listOf(prod(1, "Refresco"), prod(2, "Pan"))
        val state = PreciosUiState(
            cargado = true,
            productos = productos,
            form = PreajusteForm(porcentaje = "10", productoIds = linkedSetOf(1L)),
        )
        val acciones = AccionesPrecios(
            onNuevo = {}, onEditar = {}, onActivar = { _, _ -> }, onCambiar = {}, onProducto = {},
            onElegirVisibles = {}, onGuardar = {}, onCerrar = {}, onBorrar = {},
            onConfirmarBorrado = {}, onCancelarBorrado = {},
        )
        rule.setContent { SpviTheme(darkTheme = false) { PreciosContent(state, onBack = {}, acciones = acciones) } }

        rule.onNodeWithTag(PreciosTags.FIJADOS).assertIsDisplayed()
        rule.onNodeWithTag(PreciosTags.BUSCAR).assertIsDisplayed()
        rule.onAllNodesWithTag(PreciosTags.producto(1)).assertCountEquals(1)
        val fijados = rule.onNodeWithTag(PreciosTags.FIJADOS).fetchSemanticsNode().boundsInRoot.top
        val buscador = rule.onNodeWithTag(PreciosTags.BUSCAR).fetchSemanticsNode().boundsInRoot.top
        assertTrue("la selección debe quedar encima del buscador", fijados < buscador)
    }
}
