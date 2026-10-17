package cu.spvi.app.ajustes.seed

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Expone el dispatcher para hacer el seed testeable. */
@Module
@InstallIn(ViewModelComponent::class)
object SeedModule {
    @Provides fun ioSeed(): CoroutineDispatcher = Dispatchers.IO
}
