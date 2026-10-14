package cu.spvi.app.migrar

import cu.spvi.app.LicRepo
import cu.spvi.app.PerfilRepo
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.AutorizacionMigracion
import cu.spvi.domain.model.Perfil
import cu.spvi.domain.repository.AjustesDispositivoRepository
import cu.spvi.domain.repository.MantenimientoRepository
import cu.spvi.domain.repository.RespaldoRepository
import cu.spvi.domain.repository.ResumenRespaldo
import cu.spvi.domain.repository.InfoRespaldo
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
    private val respaldo = object : RespaldoRepository {
        override suspend fun exportar(
            contrasena: CharArray, destino: java.io.OutputStream, progreso: (cu.spvi.domain.repository.EtapaRespaldo) -> Unit,
        ): AppResult<ResumenRespaldo> = AppResult.Ok(ResumenRespaldo(0, 0, 0, 0))
        override suspend fun inspeccionar(origen: java.io.InputStream): AppResult<InfoRespaldo> = AppResult.Ok(InfoRespaldo(4, null, 0))
        override suspend fun importar(
            origen: java.io.InputStream, contrasena: CharArray, progreso: (cu.spvi.domain.repository.EtapaRespaldo) -> Unit,
        ): AppResult<ResumenRespaldo> = AppResult.Ok(ResumenRespaldo(0, 0, 0, 0))
    }
    private val red = object : DireccionesLocales {
        override fun ipv4(): List<String> = listOf("192.168.1.20")
    }
    private val ajustes = object : AjustesDispositivoRepository {
        override val ajustes: kotlinx.coroutines.flow.Flow<cu.spvi.domain.model.AjustesDispositivo> =
            kotlinx.coroutines.flow.MutableStateFlow(cu.spvi.domain.model.AjustesDispositivo(accesoConClave = true))
        override suspend fun guardarAccesoConClave(activo: Boolean) = Unit
        override suspend fun guardarEligioSecundaria(secundaria: Boolean) = Unit
    }
    private fun vm() = MigrarViewModel(
        lic, ConstruirSolicitudMigracion(lic, PerfilRepo(Perfil(nombre = "Ana"))),
        VerificarAutorizacionMigracion(lic), CompletarMigracion(lic, mant),
        ajustes, respaldo, mant, red,
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

    // ---------- 0.31.0 (P6): migración por QR ----------

    @Test fun elCodigoVaYVieneConLosMismosDatos() {
        val c = CodigoMigracion(RolMigracion.EMISOR, "192.168.1.20", 45123, "a1b2c3d4e5f60718", "SPVI:7F3A")
        val texto = c.texto()
        assertTrue(texto.startsWith(CodigoMigracion.PREFIJO))
        assertEquals(c, CodigoMigracion.parsear(texto))
    }

    @Test fun elNombreDelNegocioPuedeLlevarSeparadores() {
        val c = CodigoMigracion(RolMigracion.EMISOR, "10.0.0.5", 1, CodigoMigracion.clave(), "Café | Bar")
        assertEquals("Café | Bar", CodigoMigracion.parsear(c.texto())!!.origen)
    }

    @Test fun loQueNoEsUnCodigoDeMigracionSeRechaza() {
        assertNull(CodigoMigracion.parsear(""))
        assertNull(CodigoMigracion.parsear("SPVI-VINC1:otra cosa"))
        assertNull(CodigoMigracion.parsear("SPVIM1|EMISOR|host|0|clave-de-16-carac|origen"))
        assertNull(CodigoMigracion.parsear("SPVIM1|NI_EMISOR_NI_RECEPTOR|host|80|clave-de-16-carac|origen"))
        assertNull(CodigoMigracion.parsear("SPVIM1|EMISOR|host|80|corta|origen"))
    }

    @Test fun laClaveEsLargaYNoSeRepite() {
        val claves = (1..20).map { CodigoMigracion.clave() }
        claves.forEach { assertEquals(CodigoMigracion.LARGO_CLAVE, it.length) }
        assertEquals(20, claves.distinct().size)
    }

    @Test fun elegirRolNoEmpiezaNadaTodavia() {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.elegirRol(RolMigracion.EMISOR)
        assertTrue(vm.state.value.qr.empezado)
        assertFalse(vm.state.value.qr.trabajando)
        assertTrue(vm.state.value.claveDefinida)
        vm.cambiarRol()
        assertFalse(vm.state.value.qr.empezado)
    }

    @Test fun sinRedLocalElEmisorAvisaYNoAbreElCanal() {
        val vm = MigrarViewModel(
            lic, ConstruirSolicitudMigracion(lic, PerfilRepo(Perfil(nombre = "Ana"))),
            VerificarAutorizacionMigracion(lic), CompletarMigracion(lic, mant),
            ajustes, respaldo, mant, object : DireccionesLocales { override fun ipv4() = emptyList() },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.empezarEmisor()
        assertEquals(TextosMigrar.QR_SIN_RED, vm.state.value.qr.error)
        assertFalse(vm.state.value.qr.trabajando)
    }

    @Test fun elReceptorRechazaUnCodigoQueNoEsDeEmisor() {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.empezarReceptor(CodigoMigracion(RolMigracion.RECEPTOR, "192.168.1.20", 45123, "a1b2c3d4e5f60718"))
        assertEquals(TextosMigrar.QR_NO_EMISOR, vm.state.value.qr.error)
        assertFalse(vm.state.value.qr.trabajando)
    }

    @Test fun elMensajeDeErrorDelIntercambioEsClaro() {
        assertEquals(TextosMigrar.QR_ERROR_CLAVE, TextosMigrar.errorQr(cu.spvi.core.result.AppError.Validacion("clave")))
        assertEquals(TextosMigrar.QR_SIN_RED, TextosMigrar.errorQr(cu.spvi.core.result.AppError.Red.SinConexion))
        assertEquals(TextosMigrar.ERROR_GENERICO, TextosMigrar.errorQr(cu.spvi.core.result.AppError.Cripto))
    }

    @Test fun cancelarVuelveAElegirPapel() {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.elegirRol(RolMigracion.RECEPTOR)
        vm.cancelarIntercambio()
        assertFalse(vm.state.value.qr.empezado)
        assertNull(vm.state.value.qr.etapa)
    }
}
