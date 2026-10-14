package cu.spvi.app.migrar

import cu.spvi.data.sync.RedLocal
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 0.31.0 (P6): direcciones IPv4 de este teléfono en la red local. El emisor las necesita para decirle al receptor
 * dónde conectarse (van en el código QR). Interfaz para poder probar los ViewModels en JVM: la implementación real
 * lee las interfaces de red del sistema.
 */
interface DireccionesLocales {
    /** IPv4 privadas de las interfaces activas (wifi del local o zona wifi de este teléfono). Vacía si no hay red. */
    fun ipv4(): List<String>
}

@Singleton
class DireccionesLocalesAndroid @Inject constructor(private val red: RedLocal) : DireccionesLocales {
    override fun ipv4(): List<String> = red.direcciones()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MigrarModule {
    @Binds abstract fun direcciones(impl: DireccionesLocalesAndroid): DireccionesLocales
}
