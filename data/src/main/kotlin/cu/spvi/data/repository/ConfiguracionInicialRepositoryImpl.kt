package cu.spvi.data.repository

import cu.spvi.data.local.SecureDataStore
import cu.spvi.data.local.SpviJson
import cu.spvi.domain.model.ConfiguracionInicial
import cu.spvi.domain.model.PasoConfiguracion
import cu.spvi.domain.repository.ConfiguracionInicialRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * Progreso del asistente en el DataStore cifrado (AES-GCM, clave del Keystore; AAD = "setup.v1").
 * Es estado de ESTE dispositivo: no entra en los respaldos (un teléfono nuevo vuelve a ofrecer el asistente
 * y a pedir la cámara en contexto). Valor ilegible = asistente sin empezar (nunca bloquea nada).
 */
@Singleton
class ConfiguracionInicialRepositoryImpl @Inject constructor(private val ds: SecureDataStore) : ConfiguracionInicialRepository {

    private val mutex = Mutex()

    override val estado: Flow<ConfiguracionInicial> = ds.observe(KEY).map { decodificar(it) }.distinctUntilChanged()

    override suspend fun confirmar(paso: PasoConfiguracion) = editar { it.copy(confirmados = it.confirmados + paso) }

    override suspend fun marcarCamaraSolicitada() = editar { it.copy(camaraSolicitada = true) }

    private suspend fun editar(f: (ConfiguracionInicial) -> ConfiguracionInicial) = mutex.withLock {
        val nuevo = f(decodificar(ds.get(KEY)))
        ds.put(KEY, codificar(nuevo))
    }

    @Serializable
    internal data class Guardada(val confirmados: List<String> = emptyList(), val camaraSolicitada: Boolean = false)

    internal companion object {
        const val KEY = "setup.v1"

        fun codificar(c: ConfiguracionInicial): String = SpviJson.encodeToString(
            Guardada.serializer(), Guardada(c.confirmados.map { it.name }.sorted(), c.camaraSolicitada),
        )

        /** Pasos desconocidos (versión futura) se ignoran; JSON ilegible = estado inicial. */
        fun decodificar(raw: String?): ConfiguracionInicial {
            if (raw == null) return ConfiguracionInicial()
            val g = runCatching { SpviJson.decodeFromString(Guardada.serializer(), raw) }.getOrNull() ?: return ConfiguracionInicial()
            return ConfiguracionInicial(
                confirmados = g.confirmados.mapNotNull { n -> PasoConfiguracion.entries.firstOrNull { it.name == n } }.toSet(),
                camaraSolicitada = g.camaraSolicitada,
            )
        }
    }
}
