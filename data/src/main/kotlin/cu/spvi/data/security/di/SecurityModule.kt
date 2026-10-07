package cu.spvi.data.security.di

import cu.spvi.data.security.AndroidDeviceKey
import cu.spvi.licencia.crypto.DeviceKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    abstract fun deviceKey(impl: AndroidDeviceKey): DeviceKey
}
