package cu.spvi.app.migrar

import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.repository.EtapaRespaldo
import cu.spvi.domain.repository.RespaldoRepository
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 0.31.0 (P6): qué está haciendo el intercambio entre los dos teléfonos (lo ve el usuario en pantalla).
 *  - [Esperando]: el emisor tiene el QR en pantalla y falta el otro teléfono.
 *  - [Conectando]: el receptor está llamando al emisor.
 *  - [Transfiriendo]: van los datos (progreso 0f–1f).
 *  - [Aplicando]: el receptor ya los tiene y está escribiéndolos en su base de datos.
 *  - [Terminado]: el receptor confirmó; el emisor se borra a continuación.
 */
/** Tamaño de cada marco cifrado del transporte (32 KiB de claro por marco, máximo). */
private const val TROZO = 32 * 1024

sealed interface EtapaMigracion {
    data object Esperando : EtapaMigracion
    data object Conectando : EtapaMigracion
    data class Transfiriendo(val progreso: Float) : EtapaMigracion
    data object Aplicando : EtapaMigracion
    data object Terminado : EtapaMigracion
}

/**
 * 0.31.0 (P6): canal directo entre los dos teléfonos (la misma wifi o la zona wifi que abre el emisor) para pasar el
 * respaldo de SPVI de uno a otro. Sin servidores ni nube: un socket TCP con la clave del QR como contraseña.
 *
 * Auditoría 2026-10-14 (S-02): antes el `.spvi` viajaba «sin contraseña», y ese modo solo OFUSCA (su secreto se
 * deriva de constantes publicadas en el código), así que un observador pasivo de la wifi podía capturar el flujo y
 * reconstruir todos los datos. Ahora el transporte va cifrado de verdad y la clave del QR NUNCA viaja por el cable:
 * apretón de manos con reto-respuesta HMAC (nonces de ambos lados) que autentica a los dos y deriva la clave de
 * sesión; el respaldo viaja en marcos AES-256-GCM (nonce aleatorio por conexión + contador por marco). Un
 * observador pasivo solo ve nonces, MAC y cifrado; un intermediario activo sin la clave no supera el reto.
 * La clave del QR sube a 128 bits para que capturar el apretón de manos no permita probarla fuera de línea.
 *
 *  - [servir] (emisor): escucha, comprueba la clave, envía el respaldo cifrado y espera la confirmación.
 *  - [conectar] (receptor): llama al emisor, recibe el respaldo cifrado y lo aplica en este teléfono.
 */
class CanalMigracion(private val respaldo: RespaldoRepository) {

    companion object {
        private const val SALUDO = "SPVIM1"
        private const val SALUDO_OK = "ok"
        private const val CONFIRMADO = "listo"
        private const val FALLIDO = "error"
        private const val ESPERA_MS = 180_000
        /** Tiempo de la llamada del receptor al emisor (la wifi tarda un poco en asociarse). */
        private const val CONEXION_MS = 20_000
        private val SIN_CONTRASENA = charArrayOf()
    }

    /**
     * Emisor. [puerto] 0 = el que el sistema tenga libre; el elegido se avisa por [alEscuchar] para poder meterlo en
     * el código QR (que se dibuja con el puerto real, no con uno inventado).
     */
    suspend fun servir(
        puerto: Int,
        clave: String,
        alEscuchar: (Int) -> Unit,
        etapa: (EtapaMigracion) -> Unit,
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        etapa(EtapaMigracion.Esperando)
        var servidor: ServerSocket? = null
        try {
            servidor = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(puerto))
                soTimeout = ESPERA_MS
            }
            alEscuchar(servidor.localPort)
            // El timeout lo pone [ServerSocket.soTimeout] (accept() no es interrumpible).
            val socket = runCatching { servidor.accept() }.getOrNull()
                ?: return@withContext AppResult.Err(AppError.Red.Timeout)
            socket.use { s ->
                s.soTimeout = ESPERA_MS
                // Un solo flujo de entrada para todo: leer líneas byte a byte evita que un lector con búfer se
                // «coma» el principio de los marcos cifrados (bug latente del diseño anterior, S-02).
                val entrada = BufferedInputStream(s.getInputStream())
                val salida = BufferedOutputStream(s.getOutputStream())
                // Apretón de manos (S-02): la clave no viaja; se prueba con HMAC sobre nonces de ambos lados.
                if (leerLinea(entrada) != SALUDO) return@withContext AppResult.Err(claveInvalida())
                val nonceS = ByteArray(16).also { SecureRandom().nextBytes(it) }
                salida.write(("n " + Base64.getEncoder().encodeToString(nonceS) + "\n").toByteArray(Charsets.UTF_8))
                salida.flush()
                val respuesta = leerLinea(entrada)?.split(" ")
                val nonceC = respuesta?.getOrNull(1)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
                val macC = respuesta?.getOrNull(2)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
                val llave = llaveBase(clave)
                if (respuesta?.getOrNull(0) != "a" || nonceC == null || nonceC.size != 16 || macC == null ||
                    !MessageDigest.isEqual(hmac(llave, "cliente", nonceS, nonceC), macC)
                ) return@withContext AppResult.Err(claveInvalida())
                salida.write(("$SALUDO_OK " + Base64.getEncoder().encodeToString(hmac(llave, "servidor", nonceC, nonceS)) + "\n").toByteArray(Charsets.UTF_8))
                // Base del nonce de esta conexión (8 bytes aleatorios; los otros 4 son el contador de marco).
                val base = ByteArray(8).also { SecureRandom().nextBytes(it) }
                salida.write((Base64.getEncoder().encodeToString(base) + "\n").toByteArray(Charsets.UTF_8))
                salida.flush()
                etapa(EtapaMigracion.Transfiriendo(0f))
                val cifrada = SalidaCifrada(claveSesion(llave, nonceS, nonceC), base, salida)
                val enviado = respaldo.exportar(SIN_CONTRASENA, cifrada) { e -> etapa(EtapaMigracion.Transfiriendo(avance(e))) }
                cifrada.terminar()
                salida.flush()
                // Cerrar el envío: el receptor sabe que el archivo terminó (lo lee hasta el final).
                runCatching { s.shutdownOutput() }
                if (enviado is AppResult.Err) return@withContext AppResult.Err(enviado.error)
                if (leerLinea(entrada) == CONFIRMADO) AppResult.Ok(Unit)
                else AppResult.Err(AppError.ArchivoDanado(incompleto = true))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Err(AppError.Red.SinConexion)
        } finally {
            runCatching { servidor?.close() }
        }
    }

    /** Receptor: se conecta al emisor descrito por el QR, recibe el respaldo cifrado y lo aplica aquí. */
    suspend fun conectar(host: String, puerto: Int, clave: String, etapa: (EtapaMigracion) -> Unit): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            etapa(EtapaMigracion.Conectando)
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, puerto), CONEXION_MS)
                    s.soTimeout = ESPERA_MS
                    val entrada = BufferedInputStream(s.getInputStream())
                    val salida = BufferedOutputStream(s.getOutputStream())
                    salida.write("$SALUDO\n".toByteArray(Charsets.UTF_8))
                    salida.flush()
                    val reto = leerLinea(entrada)?.split(" ")
                    val nonceS = reto?.getOrNull(1)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
                    if (reto?.getOrNull(0) != "n" || nonceS == null || nonceS.size != 16) {
                        return@withContext AppResult.Err(claveInvalida())
                    }
                    val nonceC = ByteArray(16).also { SecureRandom().nextBytes(it) }
                    val llave = llaveBase(clave)
                    salida.write(
                        ("a " + Base64.getEncoder().encodeToString(nonceC) + " " +
                            Base64.getEncoder().encodeToString(hmac(llave, "cliente", nonceS, nonceC)) + "\n").toByteArray(Charsets.UTF_8),
                    )
                    salida.flush()
                    val ok = leerLinea(entrada)?.split(" ")
                    val macS = ok?.getOrNull(1)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
                    if (ok?.getOrNull(0) != SALUDO_OK || macS == null ||
                        !MessageDigest.isEqual(hmac(llave, "servidor", nonceC, nonceS), macS)
                    ) return@withContext AppResult.Err(claveInvalida())
                    val base = leerLinea(entrada)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
                        ?: return@withContext AppResult.Err(AppError.ArchivoDanado(incompleto = true))
                    etapa(EtapaMigracion.Transfiriendo(0f))
                    val recibido = respaldo.importar(EntradaCifrada(claveSesion(llave, nonceS, nonceC), base, entrada), SIN_CONTRASENA) { e ->
                        etapa(EtapaMigracion.Transfiriendo(avance(e)))
                    }
                    if (recibido is AppResult.Err) {
                        runCatching { salida.write("$FALLIDO\n".toByteArray(Charsets.UTF_8)); salida.flush() }
                        return@withContext AppResult.Err(recibido.error)
                    }
                    etapa(EtapaMigracion.Aplicando)
                    salida.write("$CONFIRMADO\n".toByteArray(Charsets.UTF_8))
                    salida.flush()
                    etapa(EtapaMigracion.Terminado)
                    AppResult.Ok(Unit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppResult.Err(AppError.Red.SinConexion)
            }
        }

    private fun claveInvalida() = AppError.Validacion("clave", AppError.Regla.NO_PERMITIDO)

    /** Llave base del apretón de manos: SHA-256 de la clave del QR con dominio propio. Nunca sale del teléfono. */
    private fun llaveBase(clave: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("SPVIM1/transporte/v1:$clave".toByteArray(Charsets.UTF_8))

    /** HMAC-SHA256(etiqueta‖nonceA‖nonceB) con la llave base: prueba de posesión y derivación. */
    private fun hmac(llave: ByteArray, etiqueta: String, a: ByteArray, b: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(llave, "HmacSHA256"))
        mac.update(etiqueta.toByteArray(Charsets.UTF_8))
        mac.update(a)
        return mac.doFinal(b)
    }

    /** Clave AES de los marcos: ligada a los dos nonces de ESTA conexión (no sirve para otra). */
    private fun claveSesion(llave: ByteArray, nonceS: ByteArray, nonceC: ByteArray): SecretKeySpec =
        SecretKeySpec(hmac(llave, "sesion", nonceS, nonceC), "AES")

    /** Lee una línea ASCII/UTF-8 byte a byte (sin búfer que se adelante a los marcos cifrados). null = fin de flujo. */
    private fun leerLinea(entrada: InputStream): String? {
        val bytes = ArrayList<Byte>(64)
        while (true) {
            val b = entrada.read()
            if (b < 0) return if (bytes.isEmpty()) null else String(bytes.toByteArray(), Charsets.UTF_8).trim()
            if (b == '\n'.code) return String(bytes.toByteArray(), Charsets.UTF_8).trim()
            bytes.add(b.toByte())
            if (bytes.size > 512) return null
        }
    }

    /** Las etapas del respaldo no son un porcentaje: se reparten a ojo para que la barra avance sin mentir mucho. */
    private fun avance(e: EtapaRespaldo): Float = when (e) {
        EtapaRespaldo.PREPARANDO -> 0.05f
        EtapaRespaldo.CIFRANDO -> 0.25f
        EtapaRespaldo.ESCRIBIENDO -> 0.8f
        EtapaRespaldo.LEYENDO -> 0.3f
        EtapaRespaldo.VERIFICANDO -> 0.45f
        EtapaRespaldo.DESCIFRANDO -> 0.6f
        EtapaRespaldo.IMPORTANDO -> 0.9f
    }

    /**
     * Salida que cifra por marcos: [largo 4B][AES-GCM(trozo)]… y un marco de largo 0 como fin. Cada marco usa
     * nonce = base(8B)‖contador(4B) y el contador también como AAD: no se puede reordenar ni recortar el flujo.
     */
    private class SalidaCifrada(
        private val clave: SecretKeySpec,
        private val base: ByteArray,
        private val salida: OutputStream,
    ) : OutputStream() {
        private val bufer = ByteArray(TROZO)
        private var lleno = 0
        private var contador = 0

        override fun write(b: Int) {
            bufer[lleno++] = b.toByte()
            if (lleno == bufer.size) vaciar()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var i = off
            var quedan = len
            while (quedan > 0) {
                val n = minOf(quedan, bufer.size - lleno)
                b.copyInto(bufer, lleno, i, i + n)
                lleno += n; i += n; quedan -= n
                if (lleno == bufer.size) vaciar()
            }
        }

        override fun flush() {
            if (lleno > 0) vaciar()
            salida.flush()
        }

        /** Marco final (largo 0) + flush. No cierra la salida subyacente (el socket aún espera la confirmación). */
        fun terminar() {
            if (lleno > 0) vaciar()
            salida.write(byteArrayOf(0, 0, 0, 0))
            salida.flush()
        }

        private fun vaciar() {
            val nonce = base + byteArrayOf(
                (contador ushr 24).toByte(), (contador ushr 16).toByte(), (contador ushr 8).toByte(), contador.toByte(),
            )
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, clave, GCMParameterSpec(128, nonce))
            cipher.updateAAD(nonce)
            val cifrado = cipher.doFinal(bufer, 0, lleno)
            salida.write(
                byteArrayOf(
                    (cifrado.size ushr 24).toByte(), (cifrado.size ushr 16).toByte(),
                    (cifrado.size ushr 8).toByte(), cifrado.size.toByte(),
                ),
            )
            salida.write(cifrado)
            lleno = 0
            contador++
        }
    }

    /** Entrada inversa de [SalidaCifrada]: descifra marco a marco; un tag inválido corta con error de lectura. */
    private class EntradaCifrada(
        private val clave: SecretKeySpec,
        private val base: ByteArray,
        private val entrada: InputStream,
    ) : InputStream() {
        private var plano: ByteArray = ByteArray(0)
        private var pos = 0
        private var fin = false
        private var contador = 0

        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= plano.size) {
                if (fin || !siguienteMarco()) return -1
            }
            val n = minOf(len, plano.size - pos)
            plano.copyInto(b, off, pos, pos + n)
            pos += n
            return n
        }

        private fun siguienteMarco(): Boolean {
            val cabecera = leerExacto(4) ?: return false
            val largo = ((cabecera[0].toInt() and 0xFF) shl 24) or ((cabecera[1].toInt() and 0xFF) shl 16) or
                ((cabecera[2].toInt() and 0xFF) shl 8) or (cabecera[3].toInt() and 0xFF)
            if (largo == 0) {
                fin = true
                return false
            }
            require(largo in 17..(TROZO + 16)) { "marco de migración inválido" }
            val cifrado = leerExacto(largo) ?: throw java.io.IOException("migración cortada")
            val nonce = base + byteArrayOf(
                (contador ushr 24).toByte(), (contador ushr 16).toByte(), (contador ushr 8).toByte(), contador.toByte(),
            )
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, clave, GCMParameterSpec(128, nonce))
            cipher.updateAAD(nonce)
            plano = cipher.doFinal(cifrado) // AEADBadTagException (IOException) si lo tocaron o la clave no es
            pos = 0
            contador++
            return true
        }

        private fun leerExacto(n: Int): ByteArray? {
            val out = ByteArray(n)
            var leidos = 0
            while (leidos < n) {
                val r = entrada.read(out, leidos, n - leidos)
                if (r < 0) return null
                leidos += r
            }
            return out
        }
    }
}
