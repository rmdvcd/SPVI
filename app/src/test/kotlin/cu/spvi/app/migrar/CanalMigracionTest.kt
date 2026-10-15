package cu.spvi.app.migrar

import cu.spvi.core.result.AppResult
import cu.spvi.domain.repository.EtapaRespaldo
import cu.spvi.domain.repository.InfoRespaldo
import cu.spvi.domain.repository.RespaldoRepository
import cu.spvi.domain.repository.ResumenRespaldo
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auditoría 2026-10-14 (S-02): el canal de migración cifra el transporte con una clave derivada de la del QR
 * (que nunca viaja por el cable) y autentica a ambos lados con un reto-respuesta HMAC.
 */
class CanalMigracionTest {
    private val carga = ByteArray(200_000) { (it * 31 % 251).toByte() }

    private inner class RespaldoFalso : RespaldoRepository {
        var recibido: ByteArray? = null
        override suspend fun exportar(contrasena: CharArray, destino: OutputStream, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> {
            destino.write(carga)
            destino.flush()
            return AppResult.Ok(ResumenRespaldo(1, 0, 0, 0))
        }
        override suspend fun inspeccionar(origen: InputStream): AppResult<InfoRespaldo> = AppResult.Ok(InfoRespaldo(4, null, 0))
        override suspend fun importar(origen: InputStream, contrasena: CharArray, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> {
            recibido = origen.readBytes()
            return AppResult.Ok(ResumenRespaldo(1, 0, 0, 0))
        }
    }

    @Test fun transfiereCifradoDePuntaAPunta() = runBlocking {
        val emisor = RespaldoFalso()
        val receptor = RespaldoFalso()
        var puerto = 0
        val a = async { CanalMigracion(emisor).servir(0, CLAVE, { puerto = it }, {}) }
        withTimeout(10_000) { while (puerto == 0) delay(10) }
        val b = async { CanalMigracion(receptor).conectar("127.0.0.1", puerto, CLAVE) {} }
        assertTrue(b.await() is AppResult.Ok)
        assertTrue(a.await() is AppResult.Ok)
        assertArrayEquals(carga, receptor.recibido)
    }

    @Test fun claveEquivocadaNoTransfiereNada() = runBlocking {
        val emisor = RespaldoFalso()
        val receptor = RespaldoFalso()
        var puerto = 0
        val a = async { CanalMigracion(emisor).servir(0, CLAVE, { puerto = it }, {}) }
        withTimeout(10_000) { while (puerto == 0) delay(10) }
        val b = async { CanalMigracion(receptor).conectar("127.0.0.1", puerto, "00000000000000000000000000000000") {} }
        assertTrue(b.await() is AppResult.Err)
        assertTrue(a.await() is AppResult.Err)
    }

    @Test fun porElCableNoViajaElContenidoEnClaro() = runBlocking {
        val emisor = RespaldoFalso()
        var puerto = 0
        val a = async { CanalMigracion(emisor).servir(0, CLAVE, { puerto = it }, {}) }
        withTimeout(10_000) { while (puerto == 0) delay(10) }
        // Cliente correcto (supera el apretón de manos) que solo espía los bytes recibidos.
        val crudo = Socket("127.0.0.1", puerto).use { s ->
            s.soTimeout = 10_000
            val entrada = s.getInputStream().buffered()
            val salida = s.getOutputStream()
            fun linea(): String {
                val bytes = mutableListOf<Byte>()
                while (true) {
                    val b = entrada.read()
                    if (b < 0 || b == '\n'.code) break
                    bytes.add(b.toByte())
                }
                return String(bytes.toByteArray(), Charsets.UTF_8)
            }
            salida.write("SPVIM1\n".toByteArray()); salida.flush()
            val nonceS = java.util.Base64.getDecoder().decode(linea().split(" ")[1])
            val llave = java.security.MessageDigest.getInstance("SHA-256")
                .digest("SPVIM1/transporte/v1:$CLAVE".toByteArray(Charsets.UTF_8))
            val nonceC = ByteArray(16) { 7 }
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(llave, "HmacSHA256"))
            mac.update("cliente".toByteArray()); mac.update(nonceS)
            val macC = mac.doFinal(nonceC)
            salida.write(
                ("a " + java.util.Base64.getEncoder().encodeToString(nonceC) + " " +
                    java.util.Base64.getEncoder().encodeToString(macC) + "\n").toByteArray(),
            )
            salida.flush()
            linea() // ok + prueba del servidor
            linea() // base del nonce
            val capturado = ByteArray(4096)
            var total = 0
            while (total < capturado.size) {
                val n = entrada.read(capturado, total, capturado.size - total)
                if (n < 0) break
                total += n
            }
            capturado.copyOf(total)
        }
        // La carga empieza con 0,31,62,…: en claro se vería ese patrón; cifrado, no.
        val patron = ByteArray(64) { (it * 31 % 251).toByte() }
        assertFalse(crudo.toList().windowed(patron.size).any { it == patron.toList() })
        a.cancel()
    }

    private companion object {
        const val CLAVE = "0123456789abcdef0123456789abcdef"
    }
}
