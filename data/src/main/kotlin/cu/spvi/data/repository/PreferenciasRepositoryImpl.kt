package cu.spvi.data.repository

import cu.spvi.data.dto.PreferenciasDto
import cu.spvi.data.dto.toDomain
import cu.spvi.data.dto.toDto
import cu.spvi.data.local.SecureDataStore
import cu.spvi.data.local.SpviJson
import cu.spvi.domain.model.NivelesMinimos
import cu.spvi.domain.model.Preferencias
import cu.spvi.domain.repository.PreferenciasRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import cu.spvi.domain.model.Modulo

/**
 * Preferencias en el DataStore cifrado (AES-GCM con clave del Keystore, ver [SecureDataStore]):
 * un único JSON versionable en "pref.v1" (escritura atómica de todo el objeto) + el flag de onboarding.
 * Sustituye a EncryptedSharedPreferences (deprecada en security-crypto 1.1.0-alpha07).
 */
@Singleton
class PreferenciasRepositoryImpl @Inject constructor(private val ds: SecureDataStore) : PreferenciasRepository {

    private val mutex = Mutex()

    private val dto: Flow<PreferenciasDto> = ds.observe(K_PREF).map { raw ->
        raw?.let { runCatching { SpviJson.decodeFromString(PreferenciasDto.serializer(), it) }.getOrNull() } ?: PreferenciasDto()
    }

    override val onboardingCompletado: Flow<Boolean> = ds.observe(K_ONBOARDING).map { it == "1" }.distinctUntilChanged()

    override val preferencias: Flow<Preferencias> =
        combine(dto, onboardingCompletado) { d, ob -> d.toDomain(ob) }.distinctUntilChanged()

    override suspend fun completarOnboarding() = ds.put(K_ONBOARDING, "1")

    override suspend fun guardarNiveles(n: NivelesMinimos) = editar { it.copy(niveles = n) }
    override suspend fun guardarModulos(m: Set<Modulo>) = editar { it.copy(modulos = Modulo.normalizar(m)) }
    override suspend fun guardarEmpleadosPrevistos(n: Int) =
        editar { it.copy(empleadosPrevistos = n.coerceIn(0, cu.spvi.licencia.contract.GlContract.SECUNDARIAS_MAX)) }

    /** El flag de onboarding NO se toca: restaurar un respaldo no debe volver a mostrar la bienvenida. */
    override suspend fun reemplazar(p: Preferencias) = editar { p }

    private suspend fun editar(f: (Preferencias) -> Preferencias) = mutex.withLock {
        val actual = dto.first().toDomain()
        ds.put(K_PREF, SpviJson.encodeToString(PreferenciasDto.serializer(), f(actual).toDto()))
    }

    private companion object {
        const val K_PREF = "pref.v1"
        const val K_ONBOARDING = "pref.onboarding_done"
    }
}
