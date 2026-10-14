package cu.spvi.licencia

import cu.spvi.licencia.contract.CodigoLicencia
import cu.spvi.licencia.contract.Envelope
import cu.spvi.licencia.contract.GlContract
import cu.spvi.licencia.contract.GlJson
import cu.spvi.licencia.contract.RequestPayloadV1
import cu.spvi.licencia.contract.SolicitudEnvelope
import cu.spvi.licencia.contract.TipoLicencia
import cu.spvi.licencia.contract.Via
import cu.spvi.licencia.crypto.B64
import cu.spvi.licencia.crypto.EcP256
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.interfaces.ECPublicKey
import java.time.Instant
import kotlinx.serialization.json.Json

/**
 * Flujo **v1** del contrato GL (`SISTEMA_LICENCIAS_CLIENTES.md` §4–§6): solicitud y licencia dentro de un envelope
 * JSON ECIES P-256 con AAD canonicalizada. SPVI usa la vía compacta (`SPVIR1:`/`SPVI2:`), y además entiende esta.
 */
class EnvelopeV1Test {
    private val t0 = Instant.parse("2026-10-15T14:30:00Z")
    private val gl = FakeGl()
    private val clock = MutableClock(t0)
    private val store = InMemoryStore()
    private val device = InMemoryDeviceKey()
    private val deviceId = "SPVI:0f1e2d3c4b5a6978"

    private val tolerante = Json { ignoreUnknownKeys = true }

    private fun manager(esDebug: Boolean = false) = LicenseManager(
        store, device, deviceId, gl.ecdh.public.encoded, listOf(gl.signing.public.encoded), clock, esDebug = esDebug,
    )

    private fun solicitud(m: LicenseManager, tipo: TipoLicencia = TipoLicencia.MENSUAL, secundarias: Int = 2) = SolicitudEnvelope.datos(
        deviceId = m.deviceId, dispositivo = SolicitudEnvelope.dispositivo("Xiaomi", "Redmi Note 11"),
        nombre = "Carlos", apellidos = "Fernández Ruiz", ci = "89051212345", telefono = "+5352345678",
        via = Via.WHATSAPP, tipo = tipo, secundarias = secundarias, solicitadaEn = t0, devicePubSpki = device.publicSpki(),
    )

    // ---------- Solicitud ----------

    @Test fun glPuedeAbrirLaSolicitudV1() {
        val envJson = SolicitudEnvelope.sellar(solicitud(manager()), gl.ecdh.public as ECPublicKey)
        val env = GlJson.envelopeDecoder.decodeFromString(Envelope.serializer(), envJson)
        assertEquals(1, env.v)
        assertEquals(GlContract.ALG, env.alg)
        assertEquals(GlContract.KID, env.kid)

        val plano = SolicitudEnvelope.abrir(env, gl.ecdh.private, GlContract.requestAad(env.epk))
        val p = tolerante.decodeFromString(RequestPayloadV1.serializer(), plano.toString(Charsets.UTF_8))
        assertEquals(1, p.v)
        assertEquals("SPVI", p.appName)
        assertEquals("Carlos", p.nombre)
        assertEquals("Fernández Ruiz", p.apellidos)
        assertEquals(Via.WHATSAPP, p.via)
        assertEquals(TipoLicencia.MENSUAL, p.tipo)
        assertEquals(2, p.secundarias)
        assertEquals(deviceId, p.deviceId)
        assertEquals("Xiaomi Redmi Note 11", p.dispositivo)
        assertEquals(t0.toString(), p.solicitadaEn)
        // `devicePub` es la clave PERSISTENTE del dispositivo (el contrato la exige: mitigación 5).
        assertEquals(B64.enc(device.publicSpki()), p.devicePub)
        // El bloque `usuario` duplica los datos de contacto, como en el esquema del contrato.
        assertEquals("89051212345", p.usuario.ci)
        assertEquals("+5352345678", p.usuario.telefono)
    }

    @Test fun laFirmaEsDeLaEfimeraYSeCompruebaSinDescifrar() {
        val envJson = SolicitudEnvelope.sellar(solicitud(manager()), gl.ecdh.public as ECPublicKey)
        val env = GlJson.envelopeDecoder.decodeFromString(Envelope.serializer(), envJson)
        val transcript = B64.dec(env.epk) + B64.dec(env.iv) + B64.dec(env.ct) + B64.dec(env.tag)
        // Firma con la efímera del envelope (lo que GL acepta para una solicitud).
        assertTrue(EcP256.verify(EcP256.publicKey(B64.dec(env.epk)), transcript, B64.dec(env.sig)))
        // Un byte cambiado en el cifrado invalida la firma.
        val alterado = env.copy(ct = B64.enc(B64.dec(env.ct).also { it[0] = (it[0].toInt() xor 1).toByte() }))
        val transcriptAlterado = B64.dec(alterado.epk) + B64.dec(alterado.iv) + B64.dec(alterado.ct) + B64.dec(alterado.tag)
        assertFalse(EcP256.verify(EcP256.publicKey(B64.dec(alterado.epk)), transcriptAlterado, B64.dec(alterado.sig)))
    }

    @Test fun conLaAadHeredadaNoSeAbreLaSolicitud() {
        val envJson = SolicitudEnvelope.sellar(solicitud(manager()), gl.ecdh.public as ECPublicKey)
        val env = GlJson.envelopeDecoder.decodeFromString(Envelope.serializer(), envJson)
        // La AAD con barras (antes de la mitigación 8) ya no vale para una solicitud nueva: GCM falla.
        assertTrue(runCatching { SolicitudEnvelope.abrir(env, gl.ecdh.private, GlContract.requestAadHeredada(env.epk)) }.isFailure)
    }

    @Test fun elEnvelopeSeExtraeDelMensajeCompleto() {
        val envJson = SolicitudEnvelope.sellar(solicitud(manager()), gl.ecdh.public as ECPublicKey)
        val mensaje = "Solicitud de licencia SPVI\nNombre: Carlos Fernández Ruiz\n\n$envJson"
        assertEquals(envJson, SolicitudEnvelope.extraerEnvelope(mensaje))
        assertTrue(SolicitudEnvelope.esEnvelope(mensaje))
        assertFalse(SolicitudEnvelope.esEnvelope("no hay nada aquí"))
        assertNull(SolicitudEnvelope.extraerEnvelope("SPVIR1:AAAA"))
    }

    // ---------- Licencia ----------
    // Decisión de la 0.23.0 (regla del dueño): SPVI **no activa** la licencia larga; solo la corta `SPVI2:`. Una
    // larga instalada por una versión anterior se sigue **re-verificando** entera en cada evaluación (firma de GL,
    // descifrado con la clave del equipo, `deviceId`, vigencia y el código de 16 caracteres del contrato).

    private fun peticion(m: LicenseManager) = gl.openRequest(m.buildRequest(
        SolicitudInput("Carlos", "Fernández Ruiz", "89051212345", "5234 5678", TipoLicencia.MENSUAL, Via.WHATSAPP, secundarias = 2),
    ).codigo)

    @Test fun elMensajeDeLicenciaLargaYaNoSeActiva() = runBlocking {
        val m = manager()
        val emitida = gl.issueLarga(peticion(m), emitidaEn = t0)
        // El mensaje trae las 3 líneas del contrato: cobro, `ID:` y el envelope.
        assertTrue(emitida.message.contains("\nID: ${emitida.id}\n"))
        assertEquals(ActivationResult.NotFound, m.activate(emitida.message))
        assertEquals(ActivationResult.NotFound, m.activate(emitida.codigo))
        assertNull(store.lic)
    }

    @Test fun laLicenciaLargaInstaladaSeSigueReverificando() = runBlocking {
        val m = manager()
        val emitida = gl.issueLarga(peticion(m), emitidaEn = t0)
        store.instalarLarga(emitida)
        val estado = m.state()
        assertTrue(estado is LicenseState.Active)
        assertEquals(TipoLicencia.MENSUAL, (estado as LicenseState.Active).tipo)
        assertEquals(2, estado.secundarias)
        assertEquals(emitida.id, estado.id)
        // Se guarda el envelope (no el mensaje entero) y se re-verifica en cada evaluación.
        assertTrue(store.lic!!.envelopeJson.startsWith("{\"v\":1"))
        assertTrue(m.evaluate().state.unlocked)
    }

    @Test fun laAadHeredadaDeLasLicenciasAntiguasSeSigueAceptando() = runBlocking {
        val m = manager()
        val antigua = gl.issueLarga(peticion(m), emitidaEn = t0, aadHeredada = true)
        store.instalarLarga(antigua)
        // Emitida antes de la canonicalización con longitud prefijada (mitigación 8): al abrir se reintenta con la AAD
        // heredada, y solo con esa.
        assertTrue(m.state() is LicenseState.Active)
    }

    @Test fun elCodigoDe16CaracteresDelFlujoV1SeComprueba() = runBlocking {
        val m = manager()
        val bueno = gl.issueLarga(peticion(m), emitidaEn = t0)
        assertEquals("4K7N-9X2M-B8PQ-3T5V", CodigoLicencia.formatear("4K7N-9X2M-B8PQ-3T5V"))
        // El payload lleva `licenciaObtenida` y `codigoCorto` idénticos y con la forma del contrato.
        assertEquals(bueno.larga!!.licenciaObtenida, bueno.larga!!.codigoCorto)
        assertTrue(CodigoLicencia.valido(bueno.larga!!.licenciaObtenida!!))
        store.instalarLarga(bueno)
        assertTrue(m.state() is LicenseState.Active)

        // Con un código mal formado (15 caracteres) la licencia instalada se ignora: vuelve la prueba.
        val otro = InMemoryStore()
        otro.instalarLarga(gl.issueLarga(peticion(manager()), emitidaEn = t0, codigo = "4K7N-9X2M-B8PQ-3T5"))
        val conMala = LicenseManager(otro, device, deviceId, gl.ecdh.public.encoded, listOf(gl.signing.public.encoded), clock)
        assertTrue(conMala.state() is LicenseState.Trial)
    }

    @Test fun unaLicenciaLargaDeOtraAppSeIgnora() = runBlocking {
        val m = manager()
        val ajena = gl.issueLarga(peticion(m), emitidaEn = t0, appName = "GestorPro")
        store.instalarLarga(ajena)
        assertTrue(m.state() is LicenseState.Trial)
    }

    @Test fun unaLicenciaLargaDeOtroTelefonoSeIgnora() = runBlocking {
        // Otra instalación pide su licencia; este teléfono acaba con esa licencia instalada.
        val otroDevice = InMemoryDeviceKey()
        val otro = LicenseManager(InMemoryStore(), otroDevice, "SPVI:1122334455667788", gl.ecdh.public.encoded, listOf(gl.signing.public.encoded), clock)
        val ajena = gl.issueLarga(peticion(otro), emitidaEn = t0)
        store.instalarLarga(ajena)
        assertTrue(manager().state() is LicenseState.Trial)
    }

    @Test fun unEnvelopeAlteradoSeIgnora() = runBlocking {
        val m = manager()
        val emitida = gl.issueLarga(peticion(m), emitidaEn = t0)
        store.lic = StoredLicense(
            emitida.codigo.replaceFirst("\"tag\":\"", "\"tag\":\"A"), emitida.id,
            emitida.larga!!.emitidaEn, emitida.larga!!.tipo, emitida.larga!!.venceEn, deviceId,
        )
        assertTrue(m.state() is LicenseState.Trial)
    }

    @Test fun laLicenciaLargaInstaladaVenceConSuFecha() = runBlocking {
        val m = manager()
        store.instalarLarga(gl.issueLarga(peticion(m), emitidaEn = t0))
        clock.t = t0.plus(java.time.Duration.ofDays(31))
        // Vencida: SPVI la conoce (permite renovar) pero no deja trabajar.
        assertTrue(m.state() is LicenseState.Expired)
        assertFalse(m.evaluate().state.unlocked)
    }
}
