package cu.spvi.licencia.contract

import cu.spvi.licencia.crypto.AesGcm
import cu.spvi.licencia.crypto.B64
import cu.spvi.licencia.crypto.DeviceKey
import cu.spvi.licencia.crypto.EcP256
import cu.spvi.licencia.crypto.Hkdf
import cu.spvi.licencia.crypto.fail
import java.security.KeyPair
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.time.Instant
import kotlinx.serialization.Serializable

/** Datos del solicitante (objeto anidado `usuario`; GL acepta además los mismos campos en la raíz). */
@Serializable
data class DatosUsuario(
    val nombre: String,
    val apellidos: String,
    val ci: String,
    val telefono: String,
    val via: Via,
)

/**
 * Payload de la **solicitud v1** (flujo envelope, el de «otras apps» del contrato GL). SPVI 0.23.1 usa en su lugar la
 * solicitud compacta `SPVIR1:` ([SolicitudCifrada]); esta ruta se implementa porque el sistema de licencias de GL la
 * documenta como el flujo común y hay que poder probarla y hablar con un GL que solo la entienda.
 *
 * Esquema: `SISTEMA_LICENCIAS_CLIENTES.md` §4.2.
 */
@Serializable
data class RequestPayloadV1(
    val v: Int = GlContract.VERSION,
    val deviceId: String,
    val appName: String = APP_NAME,
    /** Modelo del hardware, p. ej. `Xiaomi Redmi Note 11`. */
    val dispositivo: String,
    val nombre: String,
    val apellidos: String,
    val ci: String,
    val via: Via,
    val telefono: String,
    /** Duplicado del bloque de arriba, como en el esquema del contrato. */
    val usuario: DatosUsuario,
    val tipo: TipoLicencia,
    val secundarias: Int = GlContract.SECUNDARIAS_DEFECTO,
    val solicitadaEn: String,
    val nonce: String,
    /** SPKI Base64 (standard) de la clave P-256 **persistente** del dispositivo. Obligatorio. */
    val devicePub: String,
)

/**
 * Envelope JSON v1 (`{"v":1,"alg":"ECIES-P256-AES256GCM-v1",…}`), el sobre que viaja por WhatsApp/SMS.
 *
 * Primitivas: ECDH P-256 → HKDF-SHA256 (salt = IV, info `gl-req-v1` / `gl-lic-v1`) → AES-256-GCM; firma ECDSA
 * P-256/SHA-256 (DER) sobre `epk ‖ iv ‖ ct ‖ tag`. La AAD va **canonicalizada con longitud prefijada**
 * ([GlContract.canonical]): mitigación 8, Opción A.
 */
object SolicitudEnvelope {
    private const val LARGO_IV = 12
    private val rnd = SecureRandom()
    private val inicioEnvelope = Regex("\\{\\s*\"v\"\\s*:\\s*1\\s*,")

    /** Campos legibles de la solicitud (los que el usuario copia al mensaje, si se usa esta ruta). */
    fun datos(
        deviceId: String,
        dispositivo: String,
        nombre: String,
        apellidos: String,
        ci: String,
        telefono: String,
        via: Via,
        tipo: TipoLicencia,
        secundarias: Int,
        solicitadaEn: Instant,
        devicePubSpki: ByteArray,
    ): RequestPayloadV1 = RequestPayloadV1(
        deviceId = deviceId,
        dispositivo = dispositivo,
        nombre = nombre,
        apellidos = apellidos,
        ci = ci,
        via = via,
        telefono = telefono,
        usuario = DatosUsuario(nombre, apellidos, ci, telefono, via),
        tipo = tipo,
        secundarias = secundarias,
        solicitadaEn = solicitadaEn.toString(),
        nonce = B64.urlNoPad(ByteArray(16).also(rnd::nextBytes)),
        devicePub = B64.enc(devicePubSpki),
    )

    /** Cifra la solicitud hacia la clave ECDH de GL y devuelve el envelope JSON (una sola línea). */
    fun sellar(
        payload: RequestPayloadV1,
        glEcdh: ECPublicKey,
        efimera: KeyPair = EcP256.generate(),
        iv: ByteArray = ByteArray(LARGO_IV).also(rnd::nextBytes),
    ): String {
        require(iv.size == LARGO_IV) { "IV de 12 bytes" }
        val epkDer = efimera.public.encoded
        val epkB64 = B64.enc(epkDer)
        val aad = GlContract.requestAad(epkB64)
        val plano = GlJson.encoder.encodeToString(RequestPayloadV1.serializer(), payload).toByteArray(Charsets.UTF_8)
        val clave = Hkdf.sha256(EcP256.ecdh(efimera.private, glEcdh), iv, GlContract.HKDF_INFO_REQUEST.toByteArray(), GlContract.AES_KEY_LEN)
        val salida = try {
            AesGcm.encrypt(clave, iv, aad, plano)
        } finally {
            clave.fill(0)
        }
        val ct = salida.copyOfRange(0, salida.size - GlContract.TAG_LEN)
        val tag = salida.copyOfRange(salida.size - GlContract.TAG_LEN, salida.size)
        val firma = EcP256.sign(efimera.private, epkDer + iv + ct + tag)
        val envelope = Envelope(
            v = GlContract.VERSION, alg = GlContract.ALG, epk = epkB64, iv = B64.enc(iv), ct = B64.enc(ct),
            tag = B64.enc(tag), sig = B64.enc(firma), kid = GlContract.KID,
        )
        return GlJson.encoder.encodeToString(Envelope.serializer(), envelope)
    }

    /**
     * Saca el envelope JSON de un mensaje (el usuario pega el texto completo: cobro, `ID:` y el sobre). `null` si no
     * hay ningún envelope.
     */
    fun extraerEnvelope(texto: String): String? {
        val inicio = inicioEnvelope.find(texto)?.range?.first ?: return null
        val fin = texto.lastIndexOf('}')
        if (fin <= inicio) return null
        return texto.substring(inicio, fin + 1)
    }

    /**
     * `ID:` del mensaje de GL. La licencia larga lo lleva FUERA del sobre (la AAD del GCM lo necesita antes de
     * descifrar). `null` si el mensaje no lo trae.
     */
    fun extraerId(texto: String): String? =
        Regex("(?m)^\\s*ID\\s*:\\s*([A-Za-z0-9\\-]{8,64})\\s*$").find(texto)?.groupValues?.get(1)

    /** Comprueba que el texto es un envelope con la forma exacta de v1 (8 campos, `alg` y `kid` del contrato). */
    fun esEnvelope(texto: String): Boolean = runCatching {
        val json = extraerEnvelope(texto) ?: fail()
        val env = GlJson.envelopeDecoder.decodeFromString(Envelope.serializer(), json)
        env.v == GlContract.VERSION && env.alg == GlContract.ALG && env.kid == GlContract.KID
    }.getOrDefault(false)

    /** Datos del dispositivo tal como los pide el contrato: `MANUFACTURER MODEL` (el `:data` de cada app lo aporta). */
    fun dispositivo(fabricante: String, modelo: String): String = "$fabricante $modelo".trim()

    /**
     * Abre un envelope v1 con la clave privada ECDH de la propia app (lo usa el emisor simulado de los tests y sirve
     * de referencia para GL). No valida semántica: eso es de [LicenseValidator].
     */
    fun abrir(env: Envelope, glEcdhPrivada: java.security.PrivateKey, aad: ByteArray): ByteArray {
        if (env.v != GlContract.VERSION || env.alg != GlContract.ALG) fail()
        val epk = EcP256.publicKey(B64.dec(env.epk))
        val iv = B64.dec(env.iv)
        val ct = B64.dec(env.ct)
        val tag = B64.dec(env.tag)
        if (iv.size != LARGO_IV || tag.size != GlContract.TAG_LEN) fail()
        val clave = Hkdf.sha256(EcP256.ecdh(glEcdhPrivada, epk), iv, GlContract.HKDF_INFO_REQUEST.toByteArray(), GlContract.AES_KEY_LEN)
        return try {
            AesGcm.decrypt(clave, iv, aad, ct + tag)
        } finally {
            clave.fill(0)
        }
    }

    /** Clave del dispositivo persistente (SPKI DER) → texto Base64 como el que pide el contrato. */
    fun devicePub(deviceKey: DeviceKey): String = B64.enc(deviceKey.publicSpki())
}
