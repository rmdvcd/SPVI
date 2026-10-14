package cu.spvi.app.migrar

import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.repository.EtapaRespaldo
import cu.spvi.domain.repository.RespaldoRepository
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
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
sealed interface EtapaMigracion {
    data object Esperando : EtapaMigracion
    data object Conectando : EtapaMigracion
    data class Transfiriendo(val progreso: Float) : EtapaMigracion
    data object Aplicando : EtapaMigracion
    data object Terminado : EtapaMigracion
}

/**
 * 0.31.0 (P6): canal directo entre los dos teléfonos (la misma wifi o la zona wifi que abre el emisor) para pasar el
 * respaldo cifrado de SPVI de uno a otro. Sin servidores ni nube: un socket TCP con la clave del QR como única
 * contraseña, y el archivo viaja cifrado (el `.spvi` de siempre), así que nadie más en la red lo puede leer.
 *
 *  - [servir] (emisor): escucha, comprueba la clave, envía el respaldo y espera la confirmación del receptor. Lo que
 *    se haga después de esa confirmación (borrar este teléfono) lo decide quien llama.
 *  - [conectar] (receptor): llama al emisor, recibe el respaldo y lo aplica en este teléfono.
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
                    val lectura = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                    val salida = BufferedOutputStream(s.getOutputStream())
                    val saludo = lectura.readLine()?.trim()
                    if (saludo != "$SALUDO $clave") return@withContext AppResult.Err(claveInvalida())
                    salida.write("$SALUDO_OK\n".toByteArray(Charsets.UTF_8))
                    salida.flush()
                    etapa(EtapaMigracion.Transfiriendo(0f))
                    val enviado = respaldo.exportar(SIN_CONTRASENA, salida) { e -> etapa(EtapaMigracion.Transfiriendo(avance(e))) }
                    salida.flush()
                    // Cerrar el envío: el receptor sabe que el archivo terminó (lo lee hasta el final).
                    runCatching { s.shutdownOutput() }
                    if (enviado is AppResult.Err) return@withContext AppResult.Err(enviado.error)
                    if (lectura.readLine()?.trim() == CONFIRMADO) AppResult.Ok(Unit)
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

    /** Receptor: se conecta al emisor descrito por el QR, recibe el respaldo y lo aplica aquí. */
    suspend fun conectar(host: String, puerto: Int, clave: String, etapa: (EtapaMigracion) -> Unit): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            etapa(EtapaMigracion.Conectando)
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, puerto), CONEXION_MS)
                    s.soTimeout = ESPERA_MS
                    val salida = BufferedOutputStream(s.getOutputStream())
                    salida.write("$SALUDO $clave\n".toByteArray(Charsets.UTF_8))
                    salida.flush()
                    val entrada = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                    if (entrada.readLine()?.trim() != SALUDO_OK) return@withContext AppResult.Err(claveInvalida())
                    etapa(EtapaMigracion.Transfiriendo(0f))
                    val recibido = respaldo.importar(BufferedInputStream(s.getInputStream()), SIN_CONTRASENA) { e ->
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
}
