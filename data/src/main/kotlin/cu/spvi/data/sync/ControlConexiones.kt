package cu.spvi.data.sync

import cu.spvi.core.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Limita solo los saludos pendientes de autenticación (no las sesiones ya autenticadas). Una reserva se suelta al
 * demostrar la clave, al fallar o al cerrarse la conexión. Los fallos aplican un enfriamiento exponencial por IP y,
 * cuando se conoce, por id de empleado.
 */
internal class ControlConexiones(
    private val clock: Clock,
    private val maxPendientesIp: Int = MAX_PENDIENTES_IP,
    private val maxPendientesEmpleado: Int = MAX_PENDIENTES_EMPLEADO,
) {
    private data class Estado(
        var pendientes: Int = 0,
        var fallos: Int = 0,
        var bloqueadoHasta: Instant? = null,
        var tocado: Instant,
    )

    private val candado = Any()
    private val ips = LinkedHashMap<String, Estado>()
    private val empleados = LinkedHashMap<Long, Estado>()

    fun iniciar(ip: String): Intento? = synchronized(candado) {
        val ahora = clock.now()
        depurar(ahora)
        val estado = ips.estado(ip, ahora)
        if (estado.bloqueado(ahora) || estado.pendientes >= maxPendientesIp) return@synchronized null
        estado.pendientes++
        estado.tocado = ahora
        Intento(ip)
    }

    inner class Intento internal constructor(private val ip: String) {
        private val terminado = AtomicBoolean(false)
        private var empleadoId: Long? = null

        /** Reserva además una plaza para este empleado. Debe llamarse antes de leer su fila de la base de datos. */
        fun identificar(empleado: Long): Boolean = synchronized(candado) {
            if (terminado.get()) return@synchronized false
            val anterior = empleadoId
            if (anterior != null) return@synchronized anterior == empleado
            val ahora = clock.now()
            val estado = empleados.estado(empleado, ahora)
            if (estado.bloqueado(ahora) || estado.pendientes >= maxPendientesEmpleado) return@synchronized false
            estado.pendientes++
            estado.tocado = ahora
            empleadoId = empleado
            true
        }

        /** El primer mensaje cifrado autenticado termina el intento y borra los enfriamientos acumulados. */
        fun autenticar() = terminar(exito = true)

        /** Un saludo inválido, una clave incorrecta o un tiempo de espera consume un intento. */
        fun fallar() = terminar(exito = false)

        /** Cierra una reserva que no llegó a comprobar credenciales (p. ej. cancelación del servidor). */
        fun liberar() = terminar(exito = null)

        private fun terminar(exito: Boolean?) = synchronized(candado) {
            if (!terminado.compareAndSet(false, true)) return@synchronized
            val ahora = clock.now()
            val estadoIp = ips[ip]
            estadoIp?.let {
                it.pendientes = (it.pendientes - 1).coerceAtLeast(0)
                it.tocado = ahora
                when (exito) {
                    true -> it.limpiarFallos()
                    false -> it.penalizar(ahora)
                    null -> Unit
                }
            }
            empleadoId?.let { id ->
                empleados[id]?.let {
                    it.pendientes = (it.pendientes - 1).coerceAtLeast(0)
                    it.tocado = ahora
                    when (exito) {
                        true -> it.limpiarFallos()
                        false -> it.penalizar(ahora)
                        null -> Unit
                    }
                }
            }
            recortar(ips, MAX_ESTADOS)
            recortar(empleados, MAX_ESTADOS)
        }
    }

    private fun LinkedHashMap<String, Estado>.estado(clave: String, ahora: Instant) =
        getOrPut(clave) { Estado(tocado = ahora) }.also { it.tocado = ahora }

    private fun LinkedHashMap<Long, Estado>.estado(clave: Long, ahora: Instant) =
        getOrPut(clave) { Estado(tocado = ahora) }.also { it.tocado = ahora }

    private fun Estado.bloqueado(ahora: Instant): Boolean = bloqueadoHasta?.isAfter(ahora) == true

    private fun Estado.limpiarFallos() {
        fallos = 0
        bloqueadoHasta = null
    }

    private fun Estado.penalizar(ahora: Instant) {
        fallos = (fallos + 1).coerceAtMost(MAX_FALLOS)
        val exponente = (fallos - 1).coerceAtMost(MAX_EXPONENTE)
        val espera = minOf(ESPERA_INICIAL.multipliedBy(1L shl exponente), ESPERA_MAXIMA)
        bloqueadoHasta = ahora.plus(espera)
    }

    private fun depurar(ahora: Instant) {
        val limite = ahora.minus(ANTIGUEDAD_MAXIMA)
        ips.entries.removeAll { (_, estado) -> estado.pendientes == 0 && estado.tocado.isBefore(limite) }
        empleados.entries.removeAll { (_, estado) -> estado.pendientes == 0 && estado.tocado.isBefore(limite) }
        recortar(ips, MAX_ESTADOS)
        recortar(empleados, MAX_ESTADOS)
    }

    private fun <K> recortar(mapa: LinkedHashMap<K, Estado>, maximo: Int) {
        while (mapa.size > maximo) {
            val clave = mapa.entries.firstOrNull { it.value.pendientes == 0 }?.key ?: return
            mapa.remove(clave)
        }
    }

    companion object {
        const val MAX_PENDIENTES_IP = 4
        const val MAX_PENDIENTES_EMPLEADO = 2
        const val MAX_ESTADOS = 256
        const val MAX_FALLOS = 16
        const val MAX_EXPONENTE = 9
        val ESPERA_INICIAL: Duration = Duration.ofSeconds(1)
        val ESPERA_MAXIMA: Duration = Duration.ofMinutes(5)
        val ANTIGUEDAD_MAXIMA: Duration = Duration.ofHours(1)
    }
}
