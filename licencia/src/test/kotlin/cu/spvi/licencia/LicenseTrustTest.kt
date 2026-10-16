package cu.spvi.licencia

import cu.spvi.licencia.crypto.EcP256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anclas de confianza del build (claves públicas de GL del 2026-10-15 2.ª rotación,
 * docs/GL_CONTEXTO_LICENCIAS.md; se conservan las firmas del 2026-10-15 1.ª rotación,
 * del 2026-10-05 y del 2026-09-30 para las licencias ya emitidas).
 *  - Firma: OBLIGATORIA. Si falta o no coincide con su huella, ninguna licencia valida: el build no debe salir.
 *  - ECDH: fijada; si se fija, debe parsear como P-256 y coincidir con su huella.
 * Las huellas esperadas se copian aquí A MANO desde GL: si alguien cambia una clave por error, esto falla.
 */
class LicenseTrustTest {

    private val huellaEcdhGl = "sha256:eb52:2ee8:42bf:64a1:7641:fa0e:c525:a94b:1c92:148e:9664:355b:8f41:feea:cddf:fea1"
    private val huellaFirmaGl = "sha256:e693:6c20:2461:57af:0f91:2313:94a4:80a5:0d8c:d150:34a1:091b:08af:b121:2828:0a6e"
    private val huellaFirma1015a = "sha256:d9c4:d821:1880:bc79:dda6:f92a:0c0d:f6a4:dc0d:a00a:191f:9c05:229d:4c1f:3be7:998e"
    private val huellaFirmaAnterior = "sha256:58d4:3aac:6f1b:099a:e86c:4375:b7c7:ffb5:8631:2399:246c:1f26:f861:e302:9c83:8c4e"
    private val huellaFirma0930 = "sha256:8a91:bcfb:19dc:af1b:af6e:0bb7:c82a:9410:e9b5:aa1d:fea0:cab8:241c:52d1:bcbd:6888"
    private val huellaEcdh1015a = "sha256:1d7f:515c:459f:954e:b2ba:e4cd:6479:e05c:2468:eb4d:2b40:af0f:10c9:21f6:16a4:39b1"
    private val huellaEcdhAnterior = "sha256:7b51:9e55:7de4:87a8:7824:4fbb:1497:0cc7:2a52:d40e:bed3:d919:0c37:28a4:b533:538c"
    private val huellaEcdh1005 = "sha256:2a3f:9fce:b105:7ee8:563c:04cd:e4d1:00e7:058b:c7b8:0410:024b:e658:efe1:5905:666d"

    @Test fun ecdhKeyIsPinnedAndMatchesFingerprint() {
        assertTrue("LicenseTrust.ECDH_KEY vacía: sin ella no se pueden pedir licencias", LicenseTrust.ECDH_KEY.spkiB64.isNotBlank())
        assertNotNull("LicenseTrust.ECDH_KEY no parsea o no coincide con su huella", LicenseTrust.ecdhSpki())
    }

    @Test fun everySigningKeyIsPinnedAndMatchesFingerprint() {
        assertTrue("LicenseTrust.SIGN_KEYS vacío", LicenseTrust.SIGN_KEYS.isNotEmpty())
        assertTrue(
            "Alguna clave de firma falta o no coincide con su huella",
            LicenseTrust.signSpkis().size == LicenseTrust.SIGN_KEYS.size,
        )
        assertTrue(LicenseTrust.canValidate)
    }

    @Test fun lasClavesSonLasExportadasPorGl() {
        assertEquals(LicenseTrust.normalizarHuella(huellaEcdhGl), EcP256.fingerprint(LicenseTrust.ecdhSpki()!!))
        assertEquals(LicenseTrust.normalizarHuella(huellaFirmaGl), EcP256.fingerprint(LicenseTrust.signSpkis().first()))
    }

    /** Rotación (2026-10-15, 2.ª): las firmas anteriores se AÑADEN (licencias ya emitidas); la ECDH anterior se SUSTITUYE. */
    @Test fun laRotacionConservaLaFirmaAnteriorYSustituyeLaEcdh() {
        val huellas = LicenseTrust.signSpkis().map(EcP256::fingerprint)
        assertEquals(listOf(huellaFirmaGl, huellaFirma1015a, huellaFirmaAnterior, huellaFirma0930).map(LicenseTrust::normalizarHuella), huellas)
        val ecdh = EcP256.fingerprint(LicenseTrust.ecdhSpki()!!)
        assertFalse(ecdh == LicenseTrust.normalizarHuella(huellaEcdh1015a))
        assertFalse(ecdh == LicenseTrust.normalizarHuella(huellaEcdhAnterior))
        assertFalse(ecdh == LicenseTrust.normalizarHuella(huellaEcdh1005))
    }

    @Test fun firmaYCifradoNoSonLaMismaClave() {
        assertFalse(LicenseTrust.signSpkis().any { it.contentEquals(LicenseTrust.ecdhSpki()!!) })
    }

    @Test fun huellaEnFormatoGlOHexPlano() {
        val hex = "eb522ee842bf64a17641fa0ec525a94b1c92148e9664355b8f41feeacddffea1"
        assertEquals(hex, LicenseTrust.normalizarHuella(huellaEcdhGl))
        assertEquals(hex, LicenseTrust.normalizarHuella(" SHA256:EB52:2EE8:42BF:64A1:7641:FA0E:C525:A94B:1C92:148E:9664:355B:8F41:FEEA:CDDF:FEA1 "))
        assertEquals(hex, LicenseTrust.normalizarHuella(hex))
    }

    @Test fun unaHuellaEquivocadaInvalidaLaClave() {
        val mala = LicenseTrust.PinnedKey(LicenseTrust.ECDH_KEY.spkiB64, huellaFirmaGl)
        with(LicenseTrust) { assertNull(mala.validated()) }
    }
}
