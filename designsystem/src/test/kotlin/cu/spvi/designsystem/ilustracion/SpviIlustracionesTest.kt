package cu.spvi.designsystem.ilustracion

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

/** P24: las 9 ilustraciones (unDraw, MIT) se construyen en claro y oscuro con el color primario del tema. */
class SpviIlustracionesTest {
    @Test fun todasSeConstruyenEnClaroYOscuro() {
        SpviIlustracion.entries.forEach { tipo ->
            assertTrue("$tipo sin trazados", trazos(tipo) > 0)
            listOf(false, true).forEach { oscuro ->
                val v = construir(tipo, Color(0xFF2B4FA3), oscuro, 180.dp)
                assertTrue("$tipo: viewport", v.viewportWidth > 0f && v.viewportHeight > 0f)
                assertTrue("$tipo: ancho", v.defaultWidth == 180.dp)
            }
        }
    }
}
