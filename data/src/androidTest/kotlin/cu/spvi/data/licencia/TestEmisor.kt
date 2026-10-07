package cu.spvi.data.licencia

import cu.spvi.licencia.contract.EstadoLicencia
import cu.spvi.licencia.contract.GlJson
import cu.spvi.licencia.contract.LicenciaCorta
import cu.spvi.licencia.contract.MensajesLicencia
import cu.spvi.licencia.contract.RequestPayload
import cu.spvi.licencia.contract.SolicitudCifrada
import cu.spvi.licencia.contract.TipoLicencia
import cu.spvi.licencia.crypto.B64
import cu.spvi.licencia.crypto.EcP256
import java.security.KeyPair
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Emisor mínimo de prueba (mismo papel que FakeGl en :licencia), 0.23.0: abre la solicitud `SPVIR1:` del mensaje y
 * emite la licencia corta cifrada `SPVI2:` con el mensaje «características + renglón + código».
 */
class TestEmisor {
    val ecdh: KeyPair = EcP256.generate()
    val firma: KeyPair = EcP256.generate()

    fun abrir(mensaje: String): RequestPayload {
        val plano = SolicitudCifrada.abrir(mensaje, ecdh.private)
        return GlJson.decoder.decodeFromString(RequestPayload.serializer(), plano.toString(Charsets.UTF_8))
    }

    fun emitir(req: RequestPayload, emitidaEn: Instant, deviceId: String = req.deviceId): String {
        val dias = when (req.tipo) {
            TipoLicencia.MENSUAL -> 30L; TipoLicencia.SEMESTRAL -> 180L; TipoLicencia.ANUAL -> 365L; TipoLicencia.PERPETUA -> null
        }
        val pub = B64.urlDec(req.devicePub)
        val campos = LicenciaCorta.Campos(
            id = UUID.randomUUID().toString(),
            huella = LicenciaCorta.huella(deviceId, pub),
            tipo = req.tipo,
            estado = if (dias == null) EstadoLicencia.PERPETUA else EstadoLicencia.ACTIVA,
            secundarias = req.secundarias,
            emitidaEn = emitidaEn,
            venceEn = dias?.let { emitidaEn.plus(Duration.ofDays(it)) },
        )
        val codigo = LicenciaCorta.emitir(campos, EcP256.descomprimir(pub), { EcP256.sign(firma.private, it) })
        return MensajesLicencia.licencia(campos, codigo)
    }
}
