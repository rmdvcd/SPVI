package cu.spvi.designsystem

import cu.spvi.designsystem.token.SpviMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.28.1: las transiciones entre ventanas y las ventanas emergentes se tienen que apreciar en el teléfono (antes el
 * deslizamiento se completaba en <200 ms y el cambio de pestaña era solo un fundido). Fija el ajuste para que no se
 * vuelva a endurecer sin querer: muelle de entrada blando con leve asentamiento y zoom sutil entre pestañas.
 */
class MovimientoApreciableTest {
    @Test fun muelleDeEntradaBlandoConLeveAsentamiento() {
        val muelle = SpviMotion.muelleEntrada<Float>()
        assertEquals(350f, muelle.stiffness)
        assertEquals(0.85f, muelle.dampingRatio)
        // Con asentamiento (amortiguación < 1), no el muelle seco de las salidas.
        assertTrue(muelle.dampingRatio < 1f)
        assertEquals(1f, SpviMotion.muelle<Float>().dampingRatio)
        assertTrue(muelle.dampingRatio < SpviMotion.muelle<Float>().dampingRatio)
    }

    @Test fun zoomDePestanasSutil() {
        assertEquals(0.96f, SpviMotion.ZOOM_ENTRADA)
    }
}
