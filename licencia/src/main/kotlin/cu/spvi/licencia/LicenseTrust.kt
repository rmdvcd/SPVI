package cu.spvi.licencia

import cu.spvi.licencia.crypto.EcP256

/**
 * Anclas de confianza de GL, FIJADAS EN EL BUILD (decisión D3, enmienda D3.1).
 * No son secretas; SÍ son críticas: la de firma es la única que puede validar licencias.
 *  - SIGN_KEYS: obligatorias. NUNCA se leen de la configuración ni de archivos importables.
 *  - ECDH_KEY: también fijada (Prompt 17: se eliminó la pantalla «Clave del emisor»). Sin ella no se pueden
 *    cifrar solicitudes y el panel de Licencia avisa de que hace falta una versión actualizada.
 *
 * Origen: pestaña "Claves" de GL → «Copiar ambas claves» (contrato v1, kid gl-sign-v1).
 *  - 2026-10-15 (2.ª rotación, 0.30.0): par VIGENTE, leído de la pantalla del teléfono GL
 *    (serial 4bc9f8f3) por captura + árbol UI; huellas recalculadas desde el Base64 y coincidentes.
 *    El par de la mañana del mismo día dejó de existir en ese teléfono (reinstalación o borrado
 *    de datos) y GL ya no abre solicitudes cifradas hacia él.
 *  - 2026-10-15 (1.ª rotación, 0.30.0): par anterior del mismo día; su firma se conserva SOLO
 *    para que las licencias ya emitidas con ella sigan verificando; su ECDH ya no se usa.
 *  - 2026-10-05 (0.27.0, docs/GL_CONTEXTO_LICENCIAS.md): par anterior; GL lo regeneró al desinstalarse
 *    y reinstalarse. Su clave de firma se conserva SOLO para que las licencias ya emitidas con ella
 *    sigan verificando; su ECDH ya no se usa (GL no puede abrir solicitudes hacia ella).
 *  - 2026-09-30: par anterior al anterior (teléfono del emisor serial 4bc9f8f3). Vale lo mismo:
 *    solo firma, para lo ya emitido.
 * La huella se pega TAL CUAL la copia GL ("sha256:2a3f:9fce:…"); también se admite hex plano. LicenseTrustTest
 * comprueba que parsean como P-256 y que la huella coincide.
 *
 * ATENCIÓN: GL genera el par en el Keystore de SU teléfono. Si se borran los datos de GL (`pm clear`), se
 * reinstala o se cambia de teléfono, sale otro par y hay que publicar un build nuevo:
 *  - Firma: AÑADIR la clave nueva a [SIGN_KEYS] SIN borrar la antigua, para que las licencias ya emitidas
 *    sigan verificando.
 *  - ECDH: SUSTITUIR [ECDH_KEY] (las solicitudes nuevas deben poder abrirse con el par nuevo).
 * Ambas cambian a la vez, así que fijar también la ECDH no añade builds extra.
 */
object LicenseTrust {

    data class PinnedKey(val spkiB64: String, val sha256: String)

    // SPKI ECDH pública de GL (alias gl.ecdh.p256.v1). Vacía o con huella distinta = no se pueden pedir licencias.
    val ECDH_KEY = PinnedKey(
        spkiB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAERQubhCNRWzHHGhcIaC9XI+55wTc0NxJObuaYQr7uPbRNUxuZviwS7SSBP6HcstrnwjIlgZ+FVSIr0ResFZq3Lg==",
        sha256 = "sha256:eb52:2ee8:42bf:64a1:7641:fa0e:c525:a94b:1c92:148e:9664:355b:8f41:feea:cddf:fea1",
    )

    // SPKI de firma ECDSA de GL (alias gl.sign.ec.p256.v1, kid = gl-sign-v1). Rotación: añadir, no borrar.
    // La primera es la vigente (2026-10-15, 2.ª rotación); las siguientes, anteriores (solo verifican lo ya emitido).
    val SIGN_KEYS: List<PinnedKey> = listOf(
        PinnedKey(
            spkiB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPjc7tw29A0TaynecwlYDphDAtavpfNQ6njeGIX59hIDO+ID9KxTLo4ZaxlPJ/enkCzlBo+5JEMn5AUN8bneC7Q==",
            sha256 = "sha256:e693:6c20:2461:57af:0f91:2313:94a4:80a5:0d8c:d150:34a1:091b:08af:b121:2828:0a6e",
        ),
        // 2026-10-15 (1.ª rotación, anterior).
        PinnedKey(
            spkiB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE/Kx1wh2WpM/JcPeNs7e7a5QvRMARHAPreFZ9YauzFguI85mT7/wC/jmKxR1ml5SGrGWCfA88Qo+PQ4efSUSsdg==",
            sha256 = "sha256:d9c4:d821:1880:bc79:dda6:f92a:0c0d:f6a4:dc0d:a00a:191f:9c05:229d:4c1f:3be7:998e",
        ),
        // 2026-10-05 (anterior).
        PinnedKey(
            spkiB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1LFKFD7Af1xhbza5CrD7ArwOwq5h4qG70edEp54zozXoUa/r7P7xRQwwV82rXcy0mPznM3TNMk8wGxYCwR7ubg==",
            sha256 = "sha256:58d4:3aac:6f1b:099a:e86c:4375:b7c7:ffb5:8631:2399:246c:1f26:f861:e302:9c83:8c4e",
        ),
        // 2026-09-30 (anterior).
        PinnedKey(
            spkiB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+UpBZNnTcsbL5yR5LQRD1qATSub6tV7dgaVKNgXjFUbJFbxWmUQWOQvqsOBZmt7bKDdu3iLW02Z/RtiPfa3OWQ==",
            sha256 = "sha256:8a91:bcfb:19dc:af1b:af6e:0bb7:c82a:9410:e9b5:aa1d:fea0:cab8:241c:52d1:bcbd:6888",
        ),
    )

    /** DER validado o null si falta o no coincide con la huella. */
    fun ecdhSpki(): ByteArray? = ECDH_KEY.validated()

    fun signSpkis(): List<ByteArray> = SIGN_KEYS.mapNotNull { it.validated() }

    /** El build puede VALIDAR licencias (necesita al menos una clave de firma correcta). */
    val canValidate: Boolean get() = signSpkis().isNotEmpty()

    fun PinnedKey.validated(): ByteArray? = runCatching {
        if (spkiB64.isBlank()) return null
        val der = EcP256.decodeSpkiText(spkiB64)
        EcP256.publicKey(der)
        der.takeIf { EcP256.fingerprint(it) == normalizarHuella(sha256) }
    }.getOrNull()

    /** "sha256:7B51:9e55 …" (formato de GL) o hex plano → 64 hex en minúsculas. */
    fun normalizarHuella(texto: String): String =
        texto.trim().lowercase().removePrefix("sha256:").filter { it in '0'..'9' || it in 'a'..'f' }
}
