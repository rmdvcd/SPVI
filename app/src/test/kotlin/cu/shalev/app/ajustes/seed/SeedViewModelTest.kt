package cu.shalev.app.ajustes.seed

import androidx.lifecycle.viewModelScope
import cu.shalev.app.FakeFotos
import cu.shalev.app.PerfilRepo
import cu.shalev.app.PrefRepo
import cu.shalev.app.PreciosRepo
import cu.shalev.app.ProdRepo
import cu.shalev.app.ServRepo
import cu.shalev.app.TurnoRepo
import cu.shalev.app.VentaRepo
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.repository.MantenimientoRepository
import cu.shalev.domain.seed.OrquestadorSeed
import cu.shalev.domain.usecase.CotizarVenta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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

    /** Insumos en memoria que sí guardan (el seed crea los insumos y luego los lee por id). */
    private class InsumosGuardados : InsumoRepository {
        val items = MutableStateFlow<List<Insumo>>(emptyList())
        override fun observarTodos() = items
        override suspend fun obtener(id: Long) = items.value.firstOrNull { it.id == id }
        override suspend fun obtenerVarios(ids: Collection<Long>) = items.value.filter { it.id in ids }
        override suspend fun todos() = items.value
        override suspend fun crear(insumo: Insumo): AppResult<Long> {
            val id = (items.value.maxOfOrNull { it.id } ?: 0) + 1
            items.value = items.value + insumo.copy(id = id)
            return AppResult.Ok(id)
        }
        override suspend fun actualizar(insumo: Insumo) = AppResult.Ok(Unit)
        override suspend fun eliminar(id: Long) = AppResult.Ok(Unit)
        override suspend fun ajustarStock(id: Long, delta: Cantidad, nota: String?) = AppResult.Ok(delta)
    }

    private class MantenimientoFake(var fallar: Boolean = false) : MantenimientoRepository {
        override suspend fun borrarTodo(): AppResult<Unit> =
            if (fallar) AppResult.Err(AppError.Almacenamiento) else AppResult.Ok(Unit)
    }

    private fun orquestador(fallarBorrado: Boolean = false): OrquestadorSeed {
        val productos = ProdRepo()
        val insumos = InsumosGuardados()
        val servicios = ServRepo()
        val precios = PreciosRepo()
        return OrquestadorSeed(
            CotizarVenta(productos, precios, insumos, servicios),
            TurnoRepo(), VentaRepo(), productos, insumos, servicios, precios,
            PerfilRepo(), PrefRepo(), MantenimientoFake(fallarBorrado), FakeFotos(),
        )
    }

    @Test fun ejecutaYPublicaProgresoYOk() = runTest {
        val vm = SeedViewModel(orquestador(), UnconfinedTestDispatcher(testScheduler))
        vm.iniciar()
        advanceUntilIdle()
        assertTrue(vm.estado.value is SeedViewModel.Estado.Ok)
        vm.viewModelScope.cancel()
    }

    @Test fun errorMapeaMensaje() = runTest {
        val vm = SeedViewModel(orquestador(fallarBorrado = true), UnconfinedTestDispatcher(testScheduler))
        vm.iniciar()
        advanceUntilIdle()
        assertTrue((vm.estado.value as SeedViewModel.Estado.Error).mensaje.isNotBlank())
        vm.viewModelScope.cancel()
    }
}
