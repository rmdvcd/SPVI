package cu.spvi.app.root

import androidx.lifecycle.viewModelScope
import cu.spvi.app.LicRepo
import cu.spvi.app.PrefRepo
import cu.spvi.app.RelojFijo
import cu.spvi.app.common.Entrada
import cu.spvi.app.common.EntradaCompartida
import cu.spvi.app.common.EntradaViewModel
import cu.spvi.domain.usecase.ProgramaLicencia
import cu.spvi.domain.usecase.VigilarLicencia
import cu.spvi.licencia.LicenseState
import cu.spvi.licencia.contract.TipoLicencia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Puerta de la app con el ViewModel real (la transición pura está en RootStateTest).
 * La vigilancia periódica corre en tiempo virtual; al final se cancela el viewModelScope (bucle infinito).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private val lic = LicRepo()
    private val prefs = PrefRepo()
    private val reloj = RelojFijo()

    private fun vm() = RootViewModel(lic, prefs, VigilarLicencia(lic, reloj), cu.spvi.app.TipoRepo())

    @Test fun primeraVezOnboardingYLuegoMain() = runTest {
        val vm = vm()
        assertEquals(RootState.Onboarding, vm.state.value)
        prefs.state.value = prefs.state.value.copy(onboardingCompletado = true)
        assertTrue(vm.state.value is RootState.Main)
        vm.viewModelScope.cancel()
    }

    @Test fun licenciaVencidaBloqueaAunqueFalteElOnboarding() = runTest {
        lic.flow.value = lic.flow.value!!.copy(estado = LicenseState.TrialExpired)
        val vm = vm()
        assertTrue(vm.state.value is RootState.Bloqueo)
        vm.viewModelScope.cancel()
    }

    @Test fun venceConLaAppAbiertaYSeBloqueaSinReiniciar() = runTest {
        prefs.state.value = prefs.state.value.copy(onboardingCompletado = true)
        val vm = vm()
        assertTrue(vm.state.value is RootState.Main)
        lic.flow.value = lic.flow.value!!.copy(estado = LicenseState.Expired(TipoLicencia.MENSUAL))
        assertEquals(RootState.Bloqueo(lic.flow.value!!), vm.state.value)
        // Activar otra licencia desbloquea en caliente.
        lic.flow.value = lic.flow.value!!.copy(estado = LicenseState.Perpetual("lic-p"))
        assertTrue(vm.state.value is RootState.Main)
        vm.viewModelScope.cancel()
    }

    @Test fun vigilanciaPeriodicaYRefrescoAlVolverAPrimerPlano() = runTest {
        val vm = vm()
        runCurrent()
        val inicial = lic.refrescos
        assertTrue("la vigilancia evalúa al arrancar", inicial >= 1)
        advanceTimeBy(ProgramaLicencia.MAXIMO.toMillis() + 1)
        assertEquals(inicial + 1, lic.refrescos) // prueba: revisión cada 15 min como máximo
        vm.refrescar()
        assertEquals(inicial + 2, lic.refrescos)
        vm.viewModelScope.cancel()
    }
}

/** Archivo recibido de otra app (Prompt 14): MainScaffold navega a Respaldo mientras esté pendiente. */
@OptIn(ExperimentalCoroutinesApi::class)
class EntradaViewModelTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    @Test fun soloLosArchivosQuedanPendientesHastaConsumirse() = runTest {
        val entrada = EntradaCompartida()
        val vm = EntradaViewModel(entrada)
        assertFalse(vm.archivoPendiente.value)
        entrada.publicar(Entrada.Texto("Nro. Transaccion: BR601ADLM8997"))
        assertFalse(vm.archivoPendiente.value) // el SMS es para la venta, no para Respaldo
        val archivo = Entrada.Archivo("file:///cache/compartir/recibido_1.spvi", "SPVI_completo.spvi")
        entrada.publicar(archivo)
        assertTrue(vm.archivoPendiente.value)
        assertTrue(entrada.consumir(archivo))
        assertFalse(vm.archivoPendiente.value)
        assertFalse(entrada.consumir(archivo)) // una sola vez
        vm.viewModelScope.cancel()
    }
}
