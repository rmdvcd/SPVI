package cu.spvi.licencia.contract

/**
 * Constantes del contrato v1 de GL (CONTEXTO_LICENCIAS.md).
 * NO modificar: cualquier cambio rompe la compatibilidad con el emisor.
 */
object GlContract {
    const val VERSION = 1
    /** 0.23.0: versión del payload de la solicitud (dentro de `SPVIR1:`). La licencia larga v1 solo se re-verifica. */
    const val SOLICITUD_VERSION = 2
    const val ALG = "ECIES-P256-AES256GCM-v1"
    const val KID = "gl-sign-v1"
    const val HKDF_INFO_REQUEST = "gl-req-v1"
    const val HKDF_INFO_LICENSE = "gl-lic-v1"
    const val IV_LEN = 12
    const val TAG_LEN = 16
    const val AES_KEY_LEN = 32

    /** AAD de la solicitud: `v|alg|epk|kid`, con `epk` como el MISMO string Base64 del JSON. */
    /** 0.21.0 (C4): apps secundarias cubiertas si la licencia no lo dice (licencias anteriores y la prueba). */
    const val SECUNDARIAS_DEFECTO = 5
    /** 0.21.0 (C4): máximo que se puede pedir en una solicitud. */
    const val SECUNDARIAS_MAX = 10

    fun requestAad(epkB64: String): ByteArray = "$VERSION|$ALG|$epkB64|$KID".toByteArray(Charsets.UTF_8)

    /** AAD de la licencia: `1|ECIES-P256-AES256GCM-v1|<licenseId>`. */
    fun licenseAad(licenseId: String): ByteArray = "$VERSION|$ALG|$licenseId".toByteArray(Charsets.UTF_8)
}

/** Identificador de la app: prefijo de `deviceId` (desde 0.23.0 ya no se envía `appName`). */
const val APP_NAME = "SPVI"
const val DEVICE_ID_PREFIX = "$APP_NAME:"
