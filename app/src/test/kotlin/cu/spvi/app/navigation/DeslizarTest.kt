package cu.spvi.app.navigation

import cu.spvi.designsystem.component.BloqueoGestos
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

    @Test fun elBloqueoCuentaLosComponentesQueLoPiden() {
        val b = BloqueoGestos()
        assertFalse(b.activo)
        assertTrue(b.cuenta == 0)
    }
}
