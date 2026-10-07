package cu.spvi.app.precios

import cu.spvi.app.prod
import cu.spvi.core.money.Cup
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.model.PreajustePrecios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreciosLogicTest {

    @Test fun porcentajeConSignoSegunSubirOBajar() {
        assertEquals(1000, PreajusteForm(porcentaje = "10").puntosBasicos)
        assertEquals(-1250, PreajusteForm(porcentaje = "12,5", sube = false).puntosBasicos)
        assertNull(PreajusteForm(porcentaje = "-5").puntosBasicos) // el signo lo decide Subir/Bajar
        assertNull(PreajusteForm(porcentaje = "abc").puntosBasicos)
        assertNull(PreajusteForm(porcentaje = "0").puntosBasicos)
    }

    @Test fun validacionEnPalabrasSimples() {
        val vacio = validar(PreajusteForm())
        assertEquals(TextosPrecios.ERROR_PORCENTAJE, vacio[CampoPrecio.PORCENTAJE])
        assertEquals(TextosPrecios.ERROR_PRODUCTOS, vacio[CampoPrecio.PRODUCTOS])
        assertEquals(TextosPrecios.ERROR_SUBIR, validar(PreajusteForm(porcentaje = "101", productoIds = setOf(1)))[CampoPrecio.PORCENTAJE])
        assertEquals(TextosPrecios.ERROR_BAJAR, validar(PreajusteForm(porcentaje = "91", sube = false, productoIds = setOf(1)))[CampoPrecio.PORCENTAJE])
        assertEquals(TextosPrecios.ERROR_IMPORTE, validar(PreajusteForm(porcentaje = "5", importeMinimo = "0", productoIds = setOf(1)))[CampoPrecio.IMPORTE])
        assertTrue(validar(PreajusteForm(porcentaje = "5", importeMinimo = "5,000.50", productoIds = setOf(1))).isEmpty())
    }

    @Test fun nombreAutomaticoSiNoSeEscribe() {
        val f = PreajusteForm(porcentaje = "10", metodo = MetodoPago.TRANSFERENCIA, importeMinimo = "5000", productoIds = setOf(1))
        assertEquals("+10 % con Transferencia desde 5,000.00 CUP", nombreSugerido(f))
        assertEquals("+10 % con Transferencia desde 5,000.00 CUP", aPreajuste(f).nombre)
        assertEquals("Mayoreo", aPreajuste(f.copy(nombre = "  Mayoreo ")).nombre)
        assertEquals("-5 %", nombreSugerido(PreajusteForm(porcentaje = "5", sube = false)))
    }

    @Test fun idaYVueltaFormularioPreajuste() {
        val p = PreajustePrecios(7, "X", -1250, setOf(1, 2), MetodoPago.EFECTIVO, Cup.ofPesos(1000), activo = false)
        val f = desde(p)
        assertEquals("12.5", f.porcentaje)
        assertEquals(false, f.sube)
        assertEquals("1000", f.importeMinimo)
        assertEquals(p, aPreajuste(f))
    }

    @Test fun vistaPreviaConElPrimerProductoElegido() {
        val ps = listOf(prod(1, "Refresco", venta = 100), prod(2, "Pan", venta = 50))
        assertEquals("Pan: 50.00 CUP → 55.00 CUP", vistaPrevia(PreajusteForm(porcentaje = "10", productoIds = setOf(2)), ps))
        assertEquals("Refresco: 100.00 CUP → 10.00 CUP", vistaPrevia(PreajusteForm(porcentaje = "90", sube = false, productoIds = setOf(1)), ps))
        assertNull(vistaPrevia(PreajusteForm(porcentaje = "10"), ps))
    }

    @Test fun resumenYBusqueda() {
        val p = PreajustePrecios(1, "X", 500, setOf(1, 2, 3), MetodoPago.TRANSFERENCIA, Cup.ofPesos(5000))
        assertEquals("Solo Transferencia · ventas desde 5,000.00 CUP · 2 productos", resumenPreajuste(p, existentes = 2))
        assertEquals("Cualquier pago · 1 producto", resumenPreajuste(p.copy(metodoPago = null, importeMinimo = null, productoIds = setOf(1))))
        val ps = (1L..60L).map { prod(it, if (it == 7L) "Café Serrano" else "P$it") }
        assertEquals(TextosPrecios.MAX_LISTA, filtrar(ps, "").size)
        assertEquals(listOf(7L), filtrar(ps, "café").map { it.id })
    }
}
