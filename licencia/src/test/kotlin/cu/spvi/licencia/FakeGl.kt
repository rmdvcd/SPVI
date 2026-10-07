package cu.spvi.licencia

import cu.spvi.licencia.contract.Envelope
import cu.spvi.licencia.contract.EstadoLicencia
import cu.spvi.licencia.contract.GlContract
import cu.spvi.licencia.contract.GlJson
import cu.spvi.licencia.contract.LicensePayload
import cu.spvi.licencia.contract.LicenciaCorta
import cu.spvi.licencia.contract.MensajesLicencia
import cu.spvi.licencia.contract.RequestPayload
import cu.spvi.licencia.contract.SolicitudCifrada
import cu.spvi.licencia.contract.TipoLicencia
import cu.spvi.licencia.crypto.AesGcm
import cu.spvi.licencia.crypto.B64
import cu.spvi.licencia.crypto.DeviceKey
import cu.spvi.licencia.crypto.EcP256
import cu.spvi.licencia.crypto.Hkdf
import cu.spvi.core.time.Clock
import java.security.KeyPair
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json

/**
 * Emisor simulado (0.23.0): hace lo que debe hacer GL según `docs/GL_PROMPT_0.23.md`: descifra la solicitud compacta
 * (`SPVIR1:`) y responde SIEMPRE con la licencia corta cifrada (`SPVI2:`) en el formato «características + renglón +
 * código». [issueLarga] emite la licencia larga v1 de antes (para probar que ya no se activa y que la instalada sigue).
 */
class FakeGl {
    val ecdh: KeyPair = EcP256.generate()
    val signing: KeyPair = EcP256.generate()
    private val rnd = SecureRandom()

    /** Decodificación estricta del payload: una clave fuera de RequestPayload v2 hace fallar la prueba. */
    private val strict = Json { ignoreUnknownKeys = false }

    fun openRequest(mensaje: String): RequestPayload {
        val plain = SolicitudCifrada.abrir(mensaje, ecdh.private)
        return strict.decodeFromString(RequestPayload.serializer(), plain.toString(Charsets.UTF_8))
    }

    private fun dias(t: TipoLicencia): Long? = when (t) {
        TipoLicencia.MENSUAL -> 30L; TipoLicencia.SEMESTRAL -> 180L; TipoLicencia.ANUAL -> 365L; TipoLicencia.PERPETUA -> null
    }

    /**
     * Licencia corta cifrada, como la escribirá GL. [deviceIdOverride]/[huellaDe] cambian la huella (otro teléfono o
     * falsa); [cifrarPara] cifra hacia otra clave; [venceEn] fija el vencimiento (renovaciones).
     */
    fun issue(
        req: RequestPayload,
        emitidaEn: Instant,
        estado: EstadoLicencia? = null,
        id: String = UUID.randomUUID().toString(),
        signWith: KeyPair = signing,
        deviceIdOverride: String? = null,
        venceEn: Instant? = null,
        huellaDe: ByteArray? = null,
        cifrarPara: ECPublicKey? = null,
        secundarias: Int = req.secundarias,
    ): Issued {
        val pubComprimida = B64.urlDec(req.devicePub)
        val dias = dias(req.tipo)
        val campos = LicenciaCorta.Campos(
            id = id,
            huella = huellaDe ?: LicenciaCorta.huella(deviceIdOverride ?: req.deviceId, pubComprimida),
            tipo = req.tipo,
            estado = estado ?: if (dias == null) EstadoLicencia.PERPETUA else EstadoLicencia.ACTIVA,
            secundarias = secundarias,
            emitidaEn = emitidaEn,
            venceEn = venceEn ?: dias?.let { emitidaEn.plus(Duration.ofDays(it)) },
        )
        val codigo = LicenciaCorta.emitir(campos, cifrarPara ?: EcP256.descomprimir(pubComprimida), { EcP256.sign(signWith.private, it) })
        return Issued(id, MensajesLicencia.licencia(campos, codigo), codigo)
    }

    /** Licencia LARGA v1 (antes de 0.23.0): envelope JSON con el id fuera. */
    fun issueLarga(req: RequestPayload, emitidaEn: Instant, id: String = UUID.randomUUID().toString()): Issued {
        val devicePub = EcP256.descomprimir(B64.urlDec(req.devicePub))
        val dias = dias(req.tipo)
        val lic = LicensePayload(
            nombre = req.nombre, apellidos = req.apellidos, ci = req.ci, via = req.via, telefono = req.telefono,
            deviceId = req.deviceId, tipo = req.tipo, solicitadaEn = req.solicitadaEn,
            nonce = B64.urlNoPad(ByteArray(16).also(rnd::nextBytes)), devicePub = B64.enc(devicePub.encoded),
            id = id, emitidaEn = emitidaEn.toString(),
            venceEn = dias?.let { emitidaEn.plus(Duration.ofDays(it)).toString() },
            estado = if (dias == null) EstadoLicencia.PERPETUA else EstadoLicencia.ACTIVA,
            secundarias = req.secundarias,
        )
        val eph = EcP256.generate()
        val epkDer = eph.public.encoded
        val iv = ByteArray(12).also(rnd::nextBytes)
        val key = Hkdf.sha256(EcP256.ecdh(eph.private, devicePub), iv, GlContract.HKDF_INFO_LICENSE.toByteArray(), 32)
        val out = AesGcm.encrypt(key, iv, GlContract.licenseAad(id), GlJson.encoder.encodeToString(LicensePayload.serializer(), lic).toByteArray())
        val ct = out.copyOfRange(0, out.size - 16); val tag = out.copyOfRange(out.size - 16, out.size)
        val sig = EcP256.sign(signing.private, epkDer + iv + ct + tag)
        val env = Envelope(1, GlContract.ALG, B64.enc(epkDer), B64.enc(iv), B64.enc(ct), B64.enc(tag), B64.enc(sig), GlContract.KID)
        val json = GlJson.encoder.encodeToString(Envelope.serializer(), env)
        return Issued(id, "Licencia SPVI $id\n$json", json, lic)
    }

    /** [codigo]: el código `SPVI2:` (o el JSON del envelope en las largas). */
    data class Issued(val id: String, val message: String, val codigo: String, val larga: LicensePayload? = null)
}

/** Instala una licencia LARGA como lo hacía SPVI ≤ 0.22 (para probar que la instalada se sigue re-verificando). */
fun InMemoryStore.instalarLarga(iss: FakeGl.Issued) {
    val p = iss.larga!!
    lic = StoredLicense(iss.codigo, iss.id, p.emitidaEn, p.tipo, p.venceEn, p.deviceId)
}

class InMemoryDeviceKey : DeviceKey {
    private val kp = EcP256.generate()
    override fun publicSpki(): ByteArray = kp.public.encoded
    override fun agree(peer: PublicKey): ByteArray = EcP256.ecdh(kp.private, peer)
}

class InMemoryStore : LicenseStore {
    var lic: StoredLicense? = null
    var trial: Instant? = null
    var last: Instant? = null
    override suspend fun license() = lic
    override suspend fun saveLicense(license: StoredLicense) { lic = license }
    override suspend fun trialStart() = trial
    override suspend fun setTrialStart(instant: Instant) { trial = instant }
    override suspend fun lastSeen() = last
    override suspend fun setLastSeen(instant: Instant) { last = instant }
    var migrated: Instant? = null
    override suspend fun migratedAt() = migrated
    override suspend fun markMigrated(at: Instant) { lic = null; migrated = at }
}

class MutableClock(var t: Instant) : Clock {
    override fun now() = t
    fun plusDays(d: Long) { t = t.plus(Duration.ofDays(d)) }
}
