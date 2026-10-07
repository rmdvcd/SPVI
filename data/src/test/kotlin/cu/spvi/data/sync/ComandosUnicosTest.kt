package cu.spvi.data.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.21.7 (P-e, punto 2): un comando reenviado con la misma clave no se aplica dos veces. */
class ComandosUnicosTest {

    @Test fun `el reenvio con la misma clave devuelve el resultado guardado sin aplicar otra vez`() = runBlocking {
        val m = MemoriaComandos()
        var aplicados = 0
        val ajustar = { id: Long -> suspend { aplicados++; ResultadoComando(id, valor = 15) } }
        val r1 = m.unaVez(3, peticionId = 7, clave = "a-1", ejecutar = ajustar(7))
        val r2 = m.unaVez(3, peticionId = 1, clave = "a-1", ejecutar = ajustar(1)) // sesión nueva: id de petición 1
        assertEquals(1, aplicados)                    // el +5 se aplicó una sola vez
        assertEquals(ResultadoComando(7, valor = 15), r1)
        assertEquals(ResultadoComando(1, valor = 15), r2) // misma respuesta, con el id de la nueva petición
    }

    @Test fun `sin clave se ejecuta siempre y las claves son por empleado`() = runBlocking {
        val m = MemoriaComandos()
        var aplicados = 0
        val cmd = suspend { aplicados++; ResultadoComando(1) }
        m.unaVez(3, 1, null, cmd); m.unaVez(3, 1, null, cmd)   // secundaria anterior a 0.21.7: como antes
        assertEquals(2, aplicados)
        m.unaVez(3, 1, "x", cmd); m.unaVez(4, 1, "x", cmd)     // misma clave, otro empleado: es otro comando
        assertEquals(4, aplicados)
        m.unaVez(3, 1, "y".repeat(MemoriaComandos.MAX_CLAVE + 1), cmd) // clave anómala: no se guarda
        m.unaVez(3, 1, "y".repeat(MemoriaComandos.MAX_CLAVE + 1), cmd)
        assertEquals(6, aplicados)
    }

    @Test fun `el error tambien se recuerda y la memoria tiene tope`() = runBlocking {
        val m = MemoriaComandos(capacidad = 2)
        var aplicados = 0
        val falla = suspend { aplicados++; ResultadoComando(1, error = ErroresRemotos.PERMISO) }
        assertEquals(ErroresRemotos.PERMISO, m.unaVez(3, 1, "e", falla).error)
        assertEquals(ErroresRemotos.PERMISO, m.unaVez(3, 2, "e", falla).error)
        assertEquals(1, aplicados)
        val ok = suspend { aplicados++; ResultadoComando(1) }
        m.unaVez(3, 1, "f", ok); m.unaVez(3, 1, "g", ok)        // con tope 2, «e» sale
        m.unaVez(3, 1, "e", falla)
        assertEquals(4, aplicados)
    }

    @Test fun `campos nuevos opcionales compatibles con 0_21_6`() {
        val c = SyncJson.encodeToString(Mensaje.serializer(), Comando(5, EliminarProducto(9), clave = "k"))
        assertEquals("k", (SyncJson.decodeFromString(Mensaje.serializer(), c) as Comando).clave)
        val viejo = SyncJson.encodeToString(Mensaje.serializer(), Comando(5, EliminarProducto(9)))
        assertFalse(viejo.contains("clave"))
        val h = SyncJson.encodeToString(Saludo.serializer(), HolaOk("n", comandosUnicos = true))
        assertTrue((SyncJson.decodeFromString(Saludo.serializer(), h) as HolaOk).comandosUnicos)
        // Principal 0.21.6 o anterior: sin el campo → la secundaria no reenvía.
        assertFalse((SyncJson.decodeFromString(Saludo.serializer(), h.replace(",\"comandosUnicos\":true", "")) as HolaOk).comandosUnicos)
    }
}
