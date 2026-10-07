package cu.spvi.data.di

import cu.spvi.domain.di.IoDispatcher
import cu.spvi.domain.repository.ServicioRepository
import android.content.Context
import cu.spvi.core.time.Clock
import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.licencia.DataStoreLicenseStore
import cu.spvi.data.licencia.LicenciaRepositoryImpl
import cu.spvi.data.repository.PerfilRepositoryImpl
import cu.spvi.data.repository.ConfiguracionInicialRepositoryImpl
import cu.spvi.data.repository.PreferenciasRepositoryImpl
import cu.spvi.data.repository.RegistroRepositoryImpl
import cu.spvi.data.respaldo.RespaldoRepositoryImpl
import cu.spvi.domain.repository.ExportadorDocumentos
import cu.spvi.domain.repository.InsumoRepository
import cu.spvi.domain.repository.LicenciaRepository
import cu.spvi.domain.repository.PerfilRepository
import cu.spvi.domain.repository.PreciosRepository
import cu.spvi.domain.repository.ConfiguracionInicialRepository
import cu.spvi.domain.repository.PreferenciasRepository
import cu.spvi.domain.repository.ProductoRepository
import cu.spvi.domain.repository.RegistroRepository
import cu.spvi.domain.repository.RespaldoRepository
import cu.spvi.domain.repository.TurnoRepository
import cu.spvi.domain.repository.VentaRepository
import cu.spvi.licencia.LicenseStore
import cu.spvi.data.security.DatabasePassphrase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun licenseStore(impl: DataStoreLicenseStore): LicenseStore
    @Binds abstract fun licenciaRepository(impl: LicenciaRepositoryImpl): LicenciaRepository
    // 0.26.0 (P74): registro de la prueba fuera de la app + reloj monótono.
    @Binds abstract fun registroExterno(impl: cu.spvi.data.licencia.prueba.RegistroPruebaAndroid): cu.spvi.licencia.prueba.RegistroExterno
    @Binds abstract fun pruebaRepository(impl: cu.spvi.data.licencia.prueba.RegistroPruebaAndroid): cu.spvi.domain.repository.PruebaRepository
    @Binds abstract fun detectorRetroceso(impl: cu.spvi.data.licencia.prueba.DetectorRelojAndroid): cu.spvi.licencia.prueba.DetectorRetroceso
    @Binds abstract fun perfilRepository(impl: PerfilRepositoryImpl): PerfilRepository
    @Binds abstract fun preferenciasRepository(impl: PreferenciasRepositoryImpl): PreferenciasRepository
    @Binds abstract fun configuracionInicialRepository(impl: ConfiguracionInicialRepositoryImpl): ConfiguracionInicialRepository
    @Binds abstract fun ajustesDispositivoRepository(impl: cu.spvi.data.repository.AjustesDispositivoRepositoryImpl): cu.spvi.domain.repository.AjustesDispositivoRepository
    @Binds abstract fun clienteFijoRepository(impl: cu.spvi.data.repository.ClienteFijoRepositoryImpl): cu.spvi.domain.repository.ClienteFijoRepository
    @Binds abstract fun productoRepository(impl: cu.spvi.data.sync.ProductoRepositorySegunTipo): ProductoRepository
    @Binds abstract fun insumoRepository(impl: cu.spvi.data.sync.InsumoRepositorySegunTipo): InsumoRepository
    @Binds abstract fun servicioRepository(impl: cu.spvi.data.sync.ServicioRepositorySegunTipo): ServicioRepository
    @Binds abstract fun ventaRepository(impl: cu.spvi.data.sync.VentaRepositorySegunTipo): VentaRepository
    @Binds abstract fun turnoRepository(impl: cu.spvi.data.sync.TurnoRepositorySegunTipo): TurnoRepository
    @Binds abstract fun registroRepository(impl: RegistroRepositoryImpl): RegistroRepository
    @Binds abstract fun preciosRepository(impl: cu.spvi.data.sync.PreciosRepositorySegunTipo): PreciosRepository
    @Binds abstract fun respaldoRepository(impl: RespaldoRepositoryImpl): RespaldoRepository
    @Binds abstract fun mantenimientoRepository(impl: cu.spvi.data.repository.MantenimientoRepositoryImpl): cu.spvi.domain.repository.MantenimientoRepository
    @Binds abstract fun exportador(impl: cu.spvi.data.sync.ExportadorSegunTipo): ExportadorDocumentos

    // P37: Principal / Secundaria
    @Binds abstract fun tipoAppRepository(impl: cu.spvi.data.sync.TipoAppRepositoryImpl): cu.spvi.domain.repository.TipoAppRepository
    @Binds abstract fun principalRepository(impl: cu.spvi.data.sync.PrincipalRepositoryImpl): cu.spvi.domain.repository.PrincipalRepository
    @Binds abstract fun secundariaRepository(impl: cu.spvi.data.sync.SecundariaRepositoryImpl): cu.spvi.domain.repository.SecundariaRepository
    @Binds abstract fun puertaTurno(impl: cu.spvi.data.sync.PuertaTurnoImpl): cu.spvi.domain.repository.PuertaTurno
    @Binds abstract fun estadoApp(impl: cu.spvi.data.local.EstadoAppRepositoryImpl): cu.spvi.domain.repository.EstadoAppRepository
    @Binds abstract fun actualizaciones(impl: cu.spvi.data.network.ActualizacionesRepositoryImpl): cu.spvi.domain.repository.ActualizacionesRepository

    companion object {
        @Provides fun clock(): Clock = Clock.System
        @Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO

        /** La passphrase (envuelta por el Keystore) se obtiene una vez; la instancia vive toda la app. */
        @Provides @Singleton
        fun database(@ApplicationContext context: Context, passphrase: DatabasePassphrase): SpviDatabase =
            SpviDatabase.crear(context, passphrase.get())
    }
}
