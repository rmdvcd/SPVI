package cu.spvi.app.integracion

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.spvi.app.inicio.InicioScreen
import cu.spvi.app.inicio.InicioTags
import cu.spvi.core.result.AppResult
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.domain.model.DetalleVenta
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.model.Producto
import cu.spvi.domain.model.Venta
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Prompt 15 — apertura y cierre de turno desde Inicio (pantalla real + Room en memoria). */
@RunWith(AndroidJUnit4::class)
class FlujoTurnoTest {
    @get:Rule val rule = createComposeRule()
    private val env = EntornoIntegracion()

    @After fun fin() = env.cerrar()

    private fun interruptor() = rule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(InicioTags.TURNO)))

    private fun mostrar() {
        val vm = env.inicioVm()
        rule.setContent { SpviTheme { InicioScreen(onNavigate = {}, viewModel = vm) } }
        rule.onNodeWithTag(InicioTags.TURNO).performScrollTo()
    }

    @Test fun abrirYCerrarTurnoCongelaElResumen() {
        mostrar()
        assertNull(runBlocking { env.turnos.activo() })

        interruptor().performClick()
        rule.waitUntil(5_000) { runBlocking { env.turnos.activo() } != null }
        val abierto = runBlocking { env.turnos.activo()!! }
        assertEquals("Ana", abierto.abiertoPor)

        // Una venta dentro del turno (por el repositorio: la UI de venta tiene su propio flujo).
        runBlocking {
            val id = (env.productos.crear(Producto(categoria = "Bebidas", nombre = "Refresco", precioCosto = cup(60), precioVenta = cup(100), cantidad = 5, creadoEn = env.ahora), null) as AppResult.Ok).value
            env.ventas.registrar(Venta(turnoId = abierto.id, fecha = env.ahora, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(
                DetalleVenta(productoId = id, nombre = "Refresco", categoria = "Bebidas", cantidad = 3, precioBase = cup(100), precioUnitario = cup(100), costoUnitario = cup(60)))))
        }

        interruptor().performClick()                                          // pide confirmación
        rule.onNodeWithContentDescription("Cerrar turno").performClick()
        rule.waitUntil(5_000) { runBlocking { env.turnos.activo() } == null }
        val cerrado = runBlocking { env.turnos.ultimoCerrado()!! }
        assertEquals(abierto.id, cerrado.id)
        assertEquals(1, cerrado.resumen!!.numVentas)
        assertEquals(cup(300), cerrado.resumen!!.total)
    }

    @Test fun cancelarElDialogoNoCierraElTurno() {
        runBlocking { env.turnos.abrir(env.ahora, "Ana") }
        mostrar()
        rule.waitUntil(5_000) { rule.onAllNodes(isToggleable() and hasAnyAncestor(hasTestTag(InicioTags.TURNO))).fetchSemanticsNodes().isNotEmpty() }
        interruptor().performClick()
        rule.onNodeWithContentDescription("Cerrar turno").assertExists()
        rule.onNodeWithContentDescription("Cancelar").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Cerrar turno").assertDoesNotExist()
        assertTrue(runBlocking { env.turnos.activo() } != null)
    }
}
