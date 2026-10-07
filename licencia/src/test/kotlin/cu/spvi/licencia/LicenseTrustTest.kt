package cu.spvi.licencia

import cu.spvi.licencia.crypto.EcP256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anclas de confianza del build (claves públicas de GL del 2026-10-05, docs/GL_CONTEXTO_LICENCIAS.md; se conserva la
 * clave de firma anterior, del 2026-09-30, para las licencias ya emitidas).
 *  - Firma: OBLIGATORIA. Si falta o no coincide con su huella, ninguna licencia valida: el build no debe salir.
 *  - ECDH: fijada; si se fija, debe parsear como P-256 y coincidir con su huella.
 * Las huellas esperadas se copian aquí A MANO desde GL: si alguien cambia una clave por error, esto falla.
 */
class LicenseTrustTest {

    private val huellaEcdhGl = "sha256:2a3f:9fce:b105:7ee8:563c:04cd:e4d1:00e7:058b:c7b8:0410:024b:e658:efe1:5905:666d"
    private val huellaFirmaGl = "sha256:58d4:3aac:6f1b:099a:e86c:4375:b7c7:ffb5:8631:2399:246c:1f26:f861:e302:9c83:8c4e"
    private val huellaFirmaAnterior = "sha256:8a91:bcfb:19dc:af1b:af6e:0bb7:c82a:9410:e9b5:aa1d:fea0:cab8:241c:52d1:bcbd:6888"
    private val huellaEcdhAnterior = "sha256:7b51:9e55:7de4:87a8:7824:4fbb:1497:0cc7:2a52:d40e:bed3:d919:0c37:28a4:b533:538c"

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

    /** Rotación (2026-10-05): la firma anterior se AÑADE (licencias ya emitidas); la ECDH anterior se SUSTITUYE. */
    @Test fun laRotacionConservaLaFirmaAnteriorYSustituyeLaEcdh() {
        val huellas = LicenseTrust.signSpkis().map(EcP256::fingerprint)
        assertEquals(listOf(huellaFirmaGl, huellaFirmaAnterior).map(LicenseTrust::normalizarHuella), huellas)
        assertFalse(EcP256.fingerprint(LicenseTrust.ecdhSpki()!!) == LicenseTrust.normalizarHuella(huellaEcdhAnterior))
    }

    @Test fun firmaYCifradoNoSonLaMismaClave() {
        assertFalse(LicenseTrust.signSpkis().any { it.contentEquals(LicenseTrust.ecdhSpki()!!) })
    }

    @Test fun huellaEnFormatoGlOHexPlano() {
        val hex = "2a3f9fceb1057ee8563c04cde4d100e7058bc7b80410024be658efe15905666d"
        assertEquals(hex, LicenseTrust.normalizarHuella(huellaEcdhGl))
        assertEquals(hex, LicenseTrust.normalizarHuella(" SHA256:2A3F:9FCE:B105:7EE8:563C:04CD:E4D1:00E7:058B:C7B8:0410:024B:E658:EFE1:5905:666D "))
        assertEquals(hex, LicenseTrust.normalizarHuella(hex))
    }

    @Test fun unaHuellaEquivocadaInvalidaLaClave() {
        val mala = LicenseTrust.PinnedKey(LicenseTrust.ECDH_KEY.spkiB64, huellaFirmaGl)
        with(LicenseTrust) { assertNull(mala.validated()) }
    }
}
