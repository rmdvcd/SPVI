package cu.shalev.app.navigation

import cu.shalev.designsystem.component.BloqueoGestos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.27.0 (T5): deslizar entre secciones solo en las 5 rutas raíz y sin nada que lo bloquee. */
class DeslizarTest {
    @Test fun soloEnRutaRaizYSinBloqueos() {
        assertTrue(deslizarPermitido(enRutaRaiz = true, enSeleccionVenta = false, bloqueado = false))
        assertFalse(deslizarPermitido(enRutaRaiz = false, enSeleccionVenta = false, bloqueado = false)) // subpantalla
        assertFalse(deslizarPermitido(enRutaRaiz = true, enSeleccionVenta = true, bloqueado = false))  // elegir qué vender
        assertFalse(deslizarPermitido(enRutaRaiz = true, enSeleccionVenta = false, bloqueado = true))  // diálogo, «+», foco…
    }

    // 0.31.0 (P10): el deslizamiento desliza lateral; el toque en la barra inferior funde.
    @Test fun elDeslizamientoLlevaDeslizamientoLateralYElToqueFundido() {
        assertEquals(CambioSeccion.ADELANTE, cambioSeccion(1))
        assertEquals(CambioSeccion.ATRAS, cambioSeccion(-1))
        assertEquals(CambioSeccion.FUNDIDO, cambioSeccion(0))
    }

    @Test fun elBloqueoCuentaLosComponentesQueLoPiden() {
        val b = BloqueoGestos()
        assertFalse(b.activo)
        assertTrue(b.cuenta == 0)
    }
}
