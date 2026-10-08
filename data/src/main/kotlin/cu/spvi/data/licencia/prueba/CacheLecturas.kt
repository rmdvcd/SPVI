package cu.spvi.data.licencia.prueba

/** Cache de lecturas costosas en memoria; no persiste datos del registro ni sobrevive al proceso. */
internal class CacheLecturas<T>(private val vidaMs: Long = VIDA_MS) {
    private data class Entrada<T>(val valor: T, val instanteMonotono: Long, val diaLocal: Long, val contexto: Any?)

    private var entrada: Entrada<T>? = null

    @Synchronized
    fun obtener(instanteMonotono: Long, diaLocal: Long, contexto: Any? = null, cargar: () -> T): T {
        val actual = entrada
        if (actual != null && actual.diaLocal == diaLocal && actual.contexto == contexto) {
            val edad = instanteMonotono - actual.instanteMonotono
            if (edad >= 0L && edad < vidaMs) return actual.valor
        }
        return cargar().also { entrada = Entrada(it, instanteMonotono, diaLocal, contexto) }
    }

    @Synchronized
    fun reemplazar(instanteMonotono: Long, diaLocal: Long, valor: T, contexto: Any? = null) {
        entrada = Entrada(valor, instanteMonotono, diaLocal, contexto)
    }

    @Synchronized
    fun invalidar() {
        entrada = null
    }

    companion object {
        const val VIDA_MS = 6 * 60 * 60 * 1000L
    }
}
