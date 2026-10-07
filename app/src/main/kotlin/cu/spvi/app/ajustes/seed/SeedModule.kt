package cu.spvi.app.ajustes.seed

import cu.spvi.core.result.AppResult
import cu.spvi.domain.seed.OrquestadorSeed
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Ejecuta el seed contra los repositorios reales, informando (día actual, total de días). */
fun interface EjecutorSeed {
    suspend operator fun invoke(onProgreso: (dia: Int, total: Int) -> Unit): AppResult<Unit>
}

/** Expone el [EjecutorSeed] para Hilt (y el dispatcher para hacerlo testeable). */
@Module
@InstallIn(ViewModelComponent::class)
object SeedModule {
    @Provides fun ejecutorSeed(orquestador: OrquestadorSeed): EjecutorSeed =
        EjecutorSeed { onProgreso -> orquestador.ejecutar(onProgreso = onProgreso) }

    @Provides fun ioSeed(): CoroutineDispatcher = Dispatchers.IO
}
