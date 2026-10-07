package cu.spvi.app.ajustes

import androidx.lifecycle.viewModelScope
import cu.spvi.app.ConfRepo
import cu.spvi.app.LicRepo
import cu.spvi.app.PerfilRepo
import cu.spvi.app.PrefRepo
import cu.spvi.domain.model.ConfiguracionInicial
import cu.spvi.domain.model.Perfil
import cu.spvi.domain.model.PlanConfiguracion
import cu.spvi.domain.usecase.ObservarResumenConfiguracion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/** Ajustes: resumen de la configuración pendiente. */
@OptIn(ExperimentalCoroutinesApi::class)
class AjustesViewModelTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private val perfil = PerfilRepo()
    private val lic = LicRepo()
    private val conf = ConfRepo()
    private val prefs = PrefRepo()

    @Test fun resumenSeRefleja() = runTest {
        val vm = AjustesViewModel(ObservarResumenConfiguracion(perfil, lic, conf), prefs, cu.spvi.app.TipoRepo(), cu.spvi.app.PrincipalRepoFake(), cu.spvi.app.SecundariaRepoFake())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} } // WhileSubscribed
        val inicial = PlanConfiguracion.resumen(Perfil(), lic.flow.value, ConfiguracionInicial())
        assertEquals(inicial, vm.state.value.resumen)

        perfil.state.value = Perfil(nombre = "María", apellidos = "Pérez", ci = "85010112345")
        assertNotEquals(inicial, vm.state.value.resumen) // el Perfil cuenta en el plan
        vm.viewModelScope.cancel()
    }
}
