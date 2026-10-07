package cu.spvi.app.migrar

import cu.spvi.app.LicRepo
import cu.spvi.app.PerfilRepo
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.AutorizacionMigracion
import cu.spvi.domain.model.Perfil
import cu.spvi.domain.repository.MantenimientoRepository
import cu.spvi.domain.usecase.CompletarMigracion
import cu.spvi.domain.usecase.ConstruirSolicitudMigracion
import cu.spvi.domain.usecase.VerificarAutorizacionMigracion
import cu.spvi.licencia.LicenseState
import cu.spvi.licencia.contract.Via
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MigrarTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private val lic = LicRepo()
    private var borrados = 0
    private val mant = object : MantenimientoRepository {
        override suspend fun borrarTodo(): AppResult<Unit> { borrados++; return AppResult.Ok(Unit) }
    }
    private fun vm() = MigrarViewModel(
        lic, ConstruirSolicitudMigracion(lic, PerfilRepo(Perfil(nombre = "Ana"))),
        VerificarAutorizacionMigracion(lic), CompletarMigracion(lic, mant),
    )

    // ---------- lógica

    @Test fun textosDeAutorizacion() {
        assertTrue(AutorizacionMigracion.AUTORIZADA.texto().ok)
        AutorizacionMigracion.entries.filter { it != AutorizacionMigracion.AUTORIZADA }.forEach { assertFalse(it.texto().ok) }
    }

    @Test fun confirmacionEscrita() {
        assertTrue(confirmacionValida(" borrar "))
        assertFalse(confirmacionValida("BORRA"))
        assertFalse(confirmacionValida(""))
    }

    // ---------- ViewModel

    @Test fun solicitudConDestinoValidoAbreElCanalElegido() = runTest {
        val vm = vm(); val ev = mutableListOf<EventoMigrar>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { ev += it } }
        vm.enviarSolicitud(Via.SMS)
        assertEquals(TextosMigrar.ERROR_DESTINO_VACIO, vm.state.value.form.errorDestino)
        vm.editarDestino("hola")
        vm.enviarSolicitud(Via.SMS)
        assertEquals(TextosMigrar.ERROR_DESTINO_FORMATO, vm.state.value.form.errorDestino)
        vm.editarDestino("spvi:ffeeddccbbaa9988")
        assertNull(vm.state.value.form.errorDestino)
        vm.enviarSolicitud(Via.WHATSAPP)
        val e = ev.single() as EventoMigrar.Enviar
        assertEquals(Via.WHATSAPP, e.via)
        assertTrue(e.texto.contains("ID del teléfono nuevo: SPVI:ffeeddccbbaa9988"))
    }

    @Test fun borrarSoloConAutorizacionValidaYPalabraEscrita() = runTest {
        val vm = vm(); val ev = mutableListOf<EventoMigrar>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { ev += it } }

        lic.autorizacion = AutorizacionMigracion.DE_ESTE_TELEFONO
        vm.editarAutorizacion("mi licencia"); vm.comprobar()
        assertFalse(vm.state.value.autorizada)
        vm.pedirBorrado()
        assertFalse(vm.state.value.form.confirmando)

        lic.autorizacion = AutorizacionMigracion.AUTORIZADA
        vm.editarAutorizacion("licencia del nuevo")
        assertNull(vm.state.value.form.resultado) // cambiar el texto invalida la comprobación
        vm.comprobar()
        assertTrue(vm.state.value.autorizada)
        vm.pedirBorrado()
        assertTrue(vm.state.value.form.confirmando)

        vm.confirmarBorrado() // sin escribir BORRAR
        assertEquals(0, borrados); assertFalse(lic.cedida)

        vm.editarConfirmacion("borrar")
        vm.confirmarBorrado()
        assertTrue(lic.cedida)
        assertEquals(1, borrados)
        assertEquals(LicenseState.TrialExpired, lic.flow.value!!.estado)
        assertEquals(EventoMigrar.Mensaje(TextosMigrar.COMPLETADA), ev.last())
        assertEquals(MigrarForm(), vm.state.value.form)
    }

    @Test fun elIdDeEsteTelefonoSeMuestra() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals("SPVI:x", vm.state.value.deviceId)
    }

    // ---- P18 (L9): cabecera SpviStepper ----

    @Test fun pasoDeLaCabeceraSigueElAvance() {
        assertEquals(1, pasoMigrar("", null, "", autorizada = false))
        assertEquals(1, pasoMigrar("SPVI:abc", "Ese es el ID de este teléfono.", "", autorizada = false))
        assertEquals(2, pasoMigrar(" SPVI:abc ", null, "", autorizada = false))
        assertEquals(3, pasoMigrar("SPVI:abc", null, "licencia pegada", autorizada = false))
        assertEquals(4, pasoMigrar("SPVI:abc", null, "licencia pegada", autorizada = true))
    }

    @Test fun tituloDelPasoSinNumero() {
        assertEquals("Pide la migración", tituloPasoMigrar(1))
        assertEquals("Borra este teléfono", tituloPasoMigrar(4))
        assertEquals(4, TextosMigrar.TOTAL_PASOS)
    }
}
