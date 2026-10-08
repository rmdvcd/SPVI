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
        assertEquals("1000", FiltroEntrada.PORCENTAJE.aplicar("1000%"))
        assertEquals("100.25", FiltroEntrada.PORCENTAJE.aplicar("100,25%"))
    }

    @Test fun decimales() {
        assertEquals("12.50", FiltroEntrada.DINERO.aplicar("12.505"))
        assertEquals("1450.00", FiltroEntrada.DINERO.aplicar("1,450.00 CUP")) // importe pegado con miles
        assertEquals("1450", FiltroEntrada.DINERO.aplicar("1,450"))
        assertEquals("1.45", FiltroEntrada.DINERO.aplicar("1,45")) // coma decimal española
        assertEquals("", FiltroEntrada.DINERO.aplicar("12,5.3")) // separadores mezclados con agrupación inválida: se rechaza
        assertEquals("", FiltroEntrada.DINERO.aplicar("1.2,3")) // tampoco se acepta la agrupación inversa
        assertEquals("1.2", FiltroEntrada.DINERO.aplicar("1.2,")) // un separador extra final no cambia el importe mientras se escribe
        assertEquals("2.5", FiltroEntrada.DECIMAL.aplicar("2,5"))       // 0.21.6: antes quedaba «2,5» y Cantidad.parse lo leía como 25
        assertEquals("0.5", FiltroEntrada.DECIMAL.aplicar(".5"))
        assertEquals("1.250", FiltroEntrada.DECIMAL.aplicar("1.2509"))
        assertEquals("", FiltroEntrada.DINERO.aplicar("abc"))
    }

    @Test fun textos() {
        assertEquals("Pan de bono", FiltroEntrada.NOMBRE.aplicar("Pan\tde   bono"))
        assertEquals("línea 1\nlínea 2", FiltroEntrada.TEXTO.aplicar("línea 1\nlínea 2\u0007"))
        assertEquals(500, FiltroEntrada.TEXTO.aplicar("x".repeat(900)).length)
        assertEquals("ABC-12.3", FiltroEntrada.CODIGO.aplicar("ABC-12.3 ñ"))
        // 0.21.4 (E7): mismo formato que el dominio: «_» admitido y hasta 64 caracteres
        assertEquals("LOTE_7", FiltroEntrada.CODIGO.aplicar("LOTE_7"))
        assertEquals(64, FiltroEntrada.CODIGO.aplicar("A".repeat(70)).length)
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
