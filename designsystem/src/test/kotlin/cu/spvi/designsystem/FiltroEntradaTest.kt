package cu.spvi.designsystem

import cu.spvi.designsystem.component.FiltroEntrada
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.21.0 (C10): cada filtro de entrada deja pasar solo lo que podría ser válido, y es idempotente. */
class FiltroEntradaTest {
    @Test fun numericos() {
        assertEquals("52345678", FiltroEntrada.TELEFONO.aplicar("+53 5234-5678x"))  // solo dígitos; recorta a 8
        assertEquals("9200123412341234", FiltroEntrada.TARJETA.aplicar("9200 1234 1234 1234 99"))
        assertEquals("85010112345", FiltroEntrada.CARNE.aplicar("850101-12345"))
        assertEquals("12", FiltroEntrada.ENTERO.aplicar("-1.2"))
        assertEquals("100", FiltroEntrada.PORCENTAJE.aplicar("1000%"))
    }

    @Test fun decimales() {
        assertEquals("12.50", FiltroEntrada.DINERO.aplicar("12.505"))
        assertEquals("12.53", FiltroEntrada.DINERO.aplicar("12,5.3"))     // un solo separador (el segundo se ignora); la coma pasa a punto
        assertEquals("2.5", FiltroEntrada.DECIMAL.aplicar("2,5"))       // 0.21.6: antes quedaba «2,5» y Cantidad.parse lo leía como 25
        assertEquals("1.250", FiltroEntrada.DECIMAL.aplicar("1.2509"))
        assertEquals("", FiltroEntrada.DINERO.aplicar("abc"))
    }

    @Test fun textos() {
        assertEquals("Pan de bono", FiltroEntrada.NOMBRE.aplicar("Pan\tde   bono"))
        assertEquals("línea 1\nlínea 2", FiltroEntrada.TEXTO.aplicar("línea 1\nlínea 2\u0007"))
        assertEquals(500, FiltroEntrada.TEXTO.aplicar("x".repeat(900)).length)
        assertEquals("MM10040FEJ987", FiltroEntrada.TRANSACCION.aplicar("mm10040fej987 "))
        assertEquals(5000, FiltroEntrada.LIBRE.aplicar("y".repeat(5000)).length)
    }

    @Test fun idempotentes() {
        val muestras = listOf("+53 5234 5678", "12,505.1", "Hola\u0000 mundo  ", "x".repeat(700), "ab-CD.9 ")
        FiltroEntrada.entries.forEach { f ->
            muestras.forEach { m -> val una = f.aplicar(m); assertEquals("$f «$m»", una, f.aplicar(una)) }
        }
    }
}
