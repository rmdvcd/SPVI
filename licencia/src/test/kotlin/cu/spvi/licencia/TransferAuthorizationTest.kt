package cu.spvi.licencia

import cu.spvi.licencia.contract.TipoLicencia
import cu.spvi.licencia.contract.Via
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Migrar: autorización = licencia de GL para OTRO dispositivo; ceder la licencia bloquea este. */
class TransferAuthorizationTest {
    private val t0 = Instant.parse("2026-09-30T12:00:00Z")
    private val gl = FakeGl()
    private val clock = MutableClock(t0)

    private val store = InMemoryStore()
    private val device = InMemoryDeviceKey()
    private val origen = LicenseManager(store, device, "SPVI:0a1b2c3d4e5f6071", gl.ecdh.public.encoded, listOf(gl.signing.public.encoded), clock)

    private val destino = LicenseManager(
        InMemoryStore(), InMemoryDeviceKey(), "SPVI:ffeeddccbbaa9988", gl.ecdh.public.encoded, listOf(gl.signing.public.encoded), clock,
    )

    private val input = SolicitudInput("María", "Pérez González", "85010112345", "5234 5678", TipoLicencia.ANUAL, Via.WHATSAPP)

    private fun licenciaPara(m: LicenseManager, emitidaEn: Instant = t0) =
        gl.issue(gl.openRequest(m.buildRequest(input).texto), emitidaEn).message

    @Test fun licenciaDeOtroTelefonoAutoriza() = runBlocking {
        assertEquals(TransferAuthorization.AUTHORIZED, origen.checkTransferAuthorization(licenciaPara(destino)))
    }

    @Test fun laLicenciaPropiaNoAutoriza() = runBlocking {
        val propia = licenciaPara(origen)
        // Sin instalar: se reconoce porque se descifra con la clave de este dispositivo.
        assertEquals(TransferAuthorization.SAME_DEVICE, origen.checkTransferAuthorization(propia))
        // Instalada: también.
        assertTrue(origen.activate(propia) is ActivationResult.Accepted)
        assertEquals(TransferAuthorization.SAME_DEVICE, origen.checkTransferAuthorization(propia))
    }

    @Test fun firmaAjenaORuidoSeRechazan() = runBlocking {
        val falsa = gl.issue(gl.openRequest(destino.buildRequest(input).texto), t0, signWith = cu.spvi.licencia.crypto.EcP256.generate()).message
        assertEquals(TransferAuthorization.REJECTED, origen.checkTransferAuthorization(falsa))
        assertEquals(TransferAuthorization.NOT_FOUND, origen.checkTransferAuthorization("hola, ¿qué tal?"))
    }

    @Test fun cederBorraLaLicenciaYBloqueaAunqueQuedePrueba() = runBlocking {
        assertTrue(origen.activate(licenciaPara(origen)) is ActivationResult.Accepted)
        clock.t = t0.plus(Duration.ofHours(1))
        origen.transferOut()
        assertNull(store.lic)
        assertEquals(clock.t, store.migrated)
        assertEquals(LicenseState.TrialExpired, origen.state())
    }

    @Test fun elMensajeViejoNoReactivaTrasMigrar() = runBlocking {
        val vieja = licenciaPara(origen)
        assertTrue(origen.activate(vieja) is ActivationResult.Accepted)
        clock.t = t0.plus(Duration.ofDays(2))
        origen.transferOut()
        assertEquals(ActivationResult.Outdated, origen.activate(vieja))
        assertNull(store.lic)
        assertEquals(LicenseState.TrialExpired, origen.state())
    }

    @Test fun unaLicenciaNuevaEmitidaDespuesSiDesbloquea() = runBlocking {
        clock.t = t0.plus(Duration.ofDays(2))
        origen.transferOut()
        clock.t = t0.plus(Duration.ofDays(3))
        val nueva = licenciaPara(origen, emitidaEn = clock.t)
        assertTrue(origen.activate(nueva) is ActivationResult.Accepted)
        assertNotNull(store.lic)
        assertTrue(origen.state().unlocked)
    }

    @Test fun licenciaAnteriorQueSobrevivaALaMarcaSeIgnora() = runBlocking {
        // Simula que el proceso murió entre la marca y el borrado: la licencia sigue guardada.
        assertTrue(origen.activate(licenciaPara(origen)) is ActivationResult.Accepted)
        store.migrated = t0.plus(Duration.ofMinutes(5))
        clock.t = t0.plus(Duration.ofMinutes(10))
        assertEquals(LicenseState.TrialExpired, origen.state())
    }
}
