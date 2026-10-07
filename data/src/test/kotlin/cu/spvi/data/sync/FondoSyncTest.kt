package cu.spvi.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.26.0 (P73 §4): el fondo asignado por el dueño viaja en campos opcionales y se gasta una sola vez. */
class FondoSyncTest {
    private val d = DatosSecundaria("n", "Negocio", 3, "Luis", "clave", listOf("192.168.43.1"), 47_811)
    private fun ok(fondo: FondoAsignado?, asigna: Boolean = true) = SincronizarOk(1, Recibidos(), "h", asignaFondos = asigna, fondo = fondo)

    @Test fun `una principal 0_25 no asigna fondos y la secundaria lo escribe como antes`() {
        val viejo = SyncJson.decodeFromString(Mensaje.serializer(), """{"t":"sync_ok","id":1,"recibidos":{},"hash":"h"}""") as SincronizarOk
        assertFalse(viejo.asignaFondos); assertNull(viejo.fondo)
        val x = d.copy(pideFondo = true).conFondo(viejo)
        assertFalse(x.principalAsignaFondo); assertNull(x.fondoCent); assertFalse(x.pideFondo)
        // Y una principal 0.25 ignora los campos nuevos de la secundaria (ignoreUnknownKeys).
        val s = Sincronizar(1, Lote(), pideFondo = true, fondoUsado = 7)
        assertTrue(SyncJson.encodeToString(Mensaje.serializer(), s).contains("\"pideFondo\":true"))
    }

    @Test fun `el fondo asignado llega y resuelve la peticion`() {
        val x = d.copy(pideFondo = true).conFondo(ok(FondoAsignado(150_000, 1000)))
        assertTrue(x.principalAsignaFondo)
        assertEquals(150_000L, x.fondoCent); assertEquals(1000L, x.fondoToken)
        assertFalse(x.pideFondo)
    }

    @Test fun `sin fondo asignado la peticion sigue en pie`() {
        val x = d.copy(pideFondo = true).conFondo(ok(null))
        assertNull(x.fondoCent); assertTrue(x.pideFondo)
    }

    @Test fun `un fondo ya usado no se vuelve a ofrecer aunque la principal aun no lo sepa`() {
        val usado = d.copy(principalAsignaFondo = true, fondoUsado = 1000)
        val x = usado.conFondo(ok(FondoAsignado(150_000, 1000)))
        assertNull(x.fondoCent); assertNull(x.fondoToken)
        assertEquals(1000L, x.fondoUsado) // se sigue enviando hasta que la principal lo gaste
        // La principal lo gastó: deja de enviarlo y la secundaria lo olvida.
        val y = x.conFondo(ok(null))
        assertNull(y.fondoUsado)
        // Un fondo NUEVO (otro identificador) sí vale para el siguiente turno.
        val z = x.conFondo(ok(FondoAsignado(50_000, 2000)))
        assertEquals(50_000L, z.fondoCent); assertNull(z.fondoUsado)
    }

    @Test fun `los datos guardados por la 0_25 se leen sin fondo`() {
        val json = SyncJson.encodeToString(DatosSecundaria.serializer(), d)
        val viejo = SyncJson.decodeFromString(DatosSecundaria.serializer(), json.replace(Regex(",\"(principalAsignaFondo|pideFondo)\":false"), ""))
        assertFalse(viejo.principalAsignaFondo); assertNull(viejo.fondoCent); assertFalse(viejo.pideFondo)
    }
}
