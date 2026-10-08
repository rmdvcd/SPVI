package cu.spvi.data.sync

import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Conexión autenticada de una app secundaria. */
internal class SesionActiva(val empleadoId: Long, val socket: Socket, val canal: CanalCifrado) {
    private val escritura = Mutex()

    suspend fun enviar(mensaje: Mensaje) = escritura.withLock {
        withContext(Dispatchers.IO) { canal.enviar(mensaje) }
    }

    fun cerrar() {
        runCatching { socket.close() }
        canal.borrarClaves()
    }
}

/**
 * Sustituye una sesión únicamente después de que [recibirYReemplazar] haya descifrado y autenticado una trama.
 * Mantiene condicional el borrado de la sesión al desconectar, para que una conexión vieja no quite la nueva.
 */
internal class SesionesActivas {
    private val porEmpleado = ConcurrentHashMap<Long, SesionActiva>()

    suspend fun recibirYReemplazar(
        nueva: SesionActiva,
        recibir: suspend () -> Mensaje,
    ): Mensaje {
        val primerMensaje = recibir() // GCM autentica la clave, el sentido y la secuencia antes de modificar el mapa.
        poner(nueva)
        return primerMensaje
    }

    fun poner(sesion: SesionActiva) {
        porEmpleado.put(sesion.empleadoId, sesion)?.takeIf { it !== sesion }?.cerrar()
    }

    fun actual(empleadoId: Long): SesionActiva? = porEmpleado[empleadoId]

    fun quitarSiActual(sesion: SesionActiva): Boolean = porEmpleado.remove(sesion.empleadoId, sesion)

    fun quitar(empleadoId: Long): SesionActiva? = porEmpleado.remove(empleadoId)

    fun todas(): List<SesionActiva> = porEmpleado.values.toList()

    fun cerrarTodas() {
        porEmpleado.values.forEach(SesionActiva::cerrar)
        porEmpleado.clear()
    }
}
