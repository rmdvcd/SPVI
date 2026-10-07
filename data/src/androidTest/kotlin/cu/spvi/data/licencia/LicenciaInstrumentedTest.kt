package cu.spvi.data.licencia

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.spvi.core.time.Clock
import cu.spvi.data.local.SecureDataStore
import cu.spvi.licencia.ActivationResult
import cu.spvi.licencia.LicenseManager
import cu.spvi.licencia.LicenseState
import cu.spvi.licencia.LicenseTrust
import cu.spvi.licencia.SolicitudInput
import cu.spvi.licencia.contract.TipoLicencia
import cu.spvi.licencia.contract.Via
import cu.spvi.licencia.crypto.EcP256
import cu.spvi.data.security.AndroidDeviceKey
import cu.spvi.data.security.KeystoreAead
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Extremo a extremo con piezas REALES de Android: Keystore (clave del dispositivo y AEAD), DataStore
 * cifrado y ANDROID_ID. Solo el emisor es de prueba ([TestEmisor]).
 */
@RunWith(AndroidJUnit4::class)
class LicenciaInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val aead = KeystoreAead(context)
    private val ds = SecureDataStore(context, aead)
    private val deviceKey = AndroidDeviceKey(context, aead)
    private val deviceId = DeviceIdProvider(context).deviceId
    private val emisor = TestEmisor()
    private var ahora = Instant.parse("2026-09-30T12:00:00Z")
    private val clock = Clock { ahora }

    private fun limpiar() = runBlocking {
        listOf(DataStoreLicenseStore.K_LICENSE, DataStoreLicenseStore.K_TRIAL, DataStoreLicenseStore.K_LAST, DataStoreLicenseStore.K_MIGRADO, DataStoreLicenseStore.K_REVOCADO)
            .forEach { ds.put(it, null) }
    }

    @Before fun antes() = limpiar()
    @After fun despues() = limpiar()

    private fun manager() = LicenseManager(
        DataStoreLicenseStore(ds), deviceKey, deviceId,
        emisor.ecdh.public.encoded, listOf(emisor.firma.public.encoded), clock,
    )

    private fun input(tipo: TipoLicencia) = SolicitudInput("María", "Pérez González", "85010112345", "52345678", tipo, Via.SMS)

    @Test fun solicitudActivacionYReinicio() = runBlocking<Unit> {
        val m = manager()
        assertTrue(m.state() is LicenseState.Trial)
        val req = emisor.abrir(m.buildRequest(input(TipoLicencia.MENSUAL)).texto)
        assertEquals(deviceId, req.deviceId)
        assertEquals(EcP256.fingerprint(deviceKey.publicSpki()), EcP256.fingerprint(EcP256.descomprimir(cu.spvi.licencia.crypto.B64.urlDec(req.devicePub)).encoded))

        val r = m.activate(emisor.emitir(req, ahora))
        assertTrue("$r", r is ActivationResult.Accepted)

        // "Reinicio": todo se relee del DataStore cifrado y se re-verifica.
        val ev = manager().evaluate()
        assertTrue(ev.state is LicenseState.Active)
        assertEquals(TipoLicencia.MENSUAL, ev.license!!.tipo)
        val guardada = DataStoreLicenseStore(ds).license()!!
        assertEquals(TipoLicencia.MENSUAL, guardada.tipo)
        assertEquals(deviceId, guardada.deviceId)
    }

    @Test fun venceYBloquea() = runBlocking<Unit> {
        val m = manager()
        val req = emisor.abrir(m.buildRequest(input(TipoLicencia.MENSUAL)).texto)
        m.activate(emisor.emitir(req, ahora))
        ahora = ahora.plus(Duration.ofDays(31))
        val s = manager().state()
        assertEquals(LicenseState.Expired(TipoLicencia.MENSUAL), s)
        assertFalse(s.unlocked)
    }

    @Test fun cederLaLicenciaPersisteYElMensajeViejoNoReactiva() = runBlocking<Unit> {
        val m = manager()
        val req = emisor.abrir(m.buildRequest(input(TipoLicencia.ANUAL)).texto)
        val mensaje = emisor.emitir(req, ahora)
        assertTrue(m.activate(mensaje) is ActivationResult.Accepted)
        ahora = ahora.plus(Duration.ofHours(1))
        m.transferOut()
        // "Reinicio": la marca y el borrado están en el DataStore cifrado.
        val store = DataStoreLicenseStore(ds)
        assertNull(store.license())
        assertEquals(ahora, store.migratedAt())
        assertEquals(LicenseState.TrialExpired, manager().state())
        assertEquals(ActivationResult.Outdated, manager().activate(mensaje))
    }

    @Test fun licenciaDeOtroDispositivoSeRechaza() = runBlocking<Unit> {
        val m = manager()
        val req = emisor.abrir(m.buildRequest(input(TipoLicencia.ANUAL)).texto)
        assertEquals(ActivationResult.Rejected, m.activate(emisor.emitir(req, ahora, deviceId = "SPVI:otro0000000000")))
        assertNull(DataStoreLicenseStore(ds).license())
    }

    @Test fun repositorioUsaLaClaveFijadaDelBuild() = runBlocking<Unit> {
        val repo = LicenciaRepositoryImpl(
            DataStoreLicenseStore(ds), deviceKey, DeviceIdProvider(context), clock, Dispatchers.IO,
            object : cu.spvi.licencia.prueba.RegistroExterno {
                override suspend fun sincronizar(trialStart: Long?, lastSeen: Long?, trialDays: Int) =
                    cu.spvi.licencia.prueba.combinarCopias<Int>(emptyMap())
            },
            object : cu.spvi.licencia.prueba.DetectorRetroceso { override suspend fun retrocedio(ahoraMs: Long) = false },
        )
        val lic = repo.refrescar()
        val fijada = LicenseTrust.ecdhSpki()
        assertEquals(fijada?.let(EcP256::fingerprint), lic.huellaEmisor)
        assertEquals(fijada != null, lic.puedeSolicitar)
        assertEquals(LicenseTrust.canValidate, lic.validacionDisponible)
    }
}
