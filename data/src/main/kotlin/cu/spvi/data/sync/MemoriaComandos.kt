package cu.spvi.data.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 0.21.7 (P-e, punto 2): en la PRINCIPAL, recuerda el resultado de los últimos comandos de las secundarias por
 * (empleado, [Comando.clave]). Si la respuesta se perdió por la red y la secundaria reenvía el mismo comando, se
 * devuelve el resultado guardado **sin aplicarlo otra vez** (un ajuste de +5 no suma 10; un producto no se crea dos veces).
 *
 * - Sin clave (secundaria anterior a 0.21.7) se ejecuta siempre, como antes.
 * - Se guarda también un resultado con error: el reenvío recibe exactamente la respuesta original.
 * - Un solo cerrojo: si el original aún se está ejecutando por la conexión vieja, el reenvío espera y recibe su resultado.
 * - En memoria (suposición): el reenvío llega en segundos; si la principal se reinicia entre medias, se pierde la memoria
 *   y el caso queda como antes de 0.21.7.
 */
class MemoriaComandos(private val capacidad: Int = CAPACIDAD) {
    private val cerrojo = Mutex()
    private val hechos = object : LinkedHashMap<String, ResultadoComando>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResultadoComando>?): Boolean = size > capacidad
    }

    suspend fun unaVez(empleadoId: Long, peticionId: Long, clave: String?, ejecutar: suspend () -> ResultadoComando): ResultadoComando {
        if (clave.isNullOrBlank() || clave.length > MAX_CLAVE) return ejecutar()
        val k = "$empleadoId:$clave"
        return cerrojo.withLock {
            hechos[k]?.copy(id = peticionId) ?: ejecutar().also { hechos[k] = it }
        }
    }

    companion object {
        const val CAPACIDAD = 200
        const val MAX_CLAVE = 64
    }
}
