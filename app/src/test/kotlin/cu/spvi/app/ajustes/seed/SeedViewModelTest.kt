package cu.spvi.app.ajustes.seed

import androidx.lifecycle.viewModelScope
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Seed debug: el ViewModel publica el progreso día a día y termina en Ok o en Error con mensaje. */
@OptIn(ExperimentalCoroutinesApi::class)
class SeedViewModelTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    @Test fun ejecutaYPublicaProgresoYOk() = runTest {
        val vistos = mutableListOf<Pair<Int, Int>>()
        val vm = SeedViewModel({ p -> repeat(3) { d -> vistos += d + 1 to 3; p(d + 1, 3) }; AppResult.Ok(Unit) }, UnconfinedTestDispatcher(testScheduler))
        vm.iniciar()
        advanceUntilIdle()
        assertTrue(vm.estado.value is SeedViewModel.Estado.Ok)
        assertTrue(vistos.isNotEmpty())
        vm.viewModelScope.cancel()
    }

    @Test fun errorMapeaMensaje() = runTest {
        val vm = SeedViewModel({ AppResult.Err(AppError.Almacenamiento) }, UnconfinedTestDispatcher(testScheduler))
        vm.iniciar()
        advanceUntilIdle()
        assertTrue((vm.estado.value as SeedViewModel.Estado.Error).mensaje.isNotBlank())
        vm.viewModelScope.cancel()
    }
}
