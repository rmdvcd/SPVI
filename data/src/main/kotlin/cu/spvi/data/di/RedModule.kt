package cu.spvi.data.di

import cu.spvi.data.foto.FotoRepositoryImpl
import cu.spvi.data.network.Red
import cu.spvi.domain.repository.FotoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * INTERNET solo para GitHub (actualizaciones y lista de revocadas) y la red local (sincronización):
 * este es el ÚNICO cliente HTTP de la app. Coil no tiene módulo de red (solo muestra archivos locales).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RedModule {
    @Binds abstract fun fotos(impl: FotoRepositoryImpl): FotoRepository

    companion object {
        @Provides @Singleton fun okHttp(): OkHttpClient = Red.cliente()
    }
}
