package cu.spvi.app.respaldo

import cu.spvi.app.FakeArchivos
import cu.spvi.app.FakeExportador
import cu.spvi.app.PerfilRepo
import cu.spvi.app.PreciosRepo
import cu.spvi.app.ProdRepo
import cu.spvi.app.RelojFijo
import cu.spvi.app.common.Entrada
import cu.spvi.app.common.EntradaCompartida
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.repository.EtapaRespaldo
import cu.spvi.domain.repository.InfoRespaldo
import cu.spvi.domain.repository.RespaldoRepository
import cu.spvi.domain.repository.ResumenRespaldo
import cu.spvi.domain.service.FormatoExport
import cu.spvi.domain.usecase.ExportarRespaldo
import cu.spvi.domain.usecase.ImportarRespaldo
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Archivo "CIFRADO" = respaldo v3 intacto; "CORTADO" = incompleto; "DANADO" = bytes cambiados;
 * "V2" = formato anterior (ya no se abre); cualquier otra cosa = no es de SPVI.
 */
private class RespaldoRepo : RespaldoRepository {
    var exportados = 0
    var contrasenaVista: String? = null
    var fallarExport = false
    var importadoSinContrasena: String? = null
    val etapas = mutableListOf<EtapaRespaldo>()
    override suspend fun exportar(contrasena: CharArray, destino: OutputStream, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> {
        if (fallarExport) return AppResult.Err(AppError.Almacenamiento)
        listOf(EtapaRespaldo.PREPARANDO, EtapaRespaldo.CIFRANDO, EtapaRespaldo.ESCRIBIENDO).forEach { etapas += it; progreso(it) }
        exportados++; contrasenaVista = String(contrasena)
        destino.write("CIFRADO".toByteArray())
        return AppResult.Ok(ResumenRespaldo(3, 2, 10, 5))
    }
    override suspend fun inspeccionar(origen: InputStream): AppResult<InfoRespaldo> = when (String(origen.readBytes())) {
        "CIFRADO" -> AppResult.Ok(InfoRespaldo(3, Instant.parse("2026-09-29T15:00:00Z"), 7))
        "ABIERTO" -> AppResult.Ok(InfoRespaldo(4, Instant.parse("2026-09-29T15:00:00Z"), 7, conContrasena = false))
        "V2" -> AppResult.Err(AppError.FormatoInvalido("respaldo de una versión anterior de SPVI (2)"))
        "CORTADO" -> AppResult.Err(AppError.ArchivoDanado(incompleto = true))
        "DANADO" -> AppResult.Err(AppError.ArchivoDanado(incompleto = false))
        else -> AppResult.Err(AppError.FormatoInvalido("no es un respaldo SPVI"))
    }
    override suspend fun importar(origen: InputStream, contrasena: CharArray, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> {
        val datos = String(origen.readBytes())
        if (datos == "ABIERTO") {                                                    // 0.27.0 (T10): sin contraseña
            importadoSinContrasena = String(contrasena)
            return AppResult.Ok(ResumenRespaldo(1, 0, 2, 0))
        }
        if (datos != "CIFRADO") return AppResult.Err(AppError.FormatoInvalido("x"))
        listOf(EtapaRespaldo.LEYENDO, EtapaRespaldo.VERIFICANDO, EtapaRespaldo.DESCIFRANDO).forEach { etapas += it; progreso(it) }
        if (String(contrasena) != "correcta1") return AppResult.Err(AppError.ContrasenaIncorrecta)
        etapas += EtapaRespaldo.IMPORTANDO; progreso(EtapaRespaldo.IMPORTANDO)
        return AppResult.Ok(ResumenRespaldo(1, 0, 2, 0))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RespaldoTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private val repo = RespaldoRepo()
    private val archivos = FakeArchivos()
    private val exportador = FakeExportador()
    private val entrada = EntradaCompartida()
    private val zona = ZoneId.of("America/Havana")

    private fun vm() = RespaldoViewModel(
        ExportarRespaldo(repo), ImportarRespaldo(repo), repo,
        archivos, entrada, RelojFijo(),
    )

    // ---------- lógica

    @Test fun nombreSinDatosPersonales() {
        assertEquals("SPVI_respaldo_2026-09-30.spvi", nombreRespaldo(LocalDate.of(2026, 9, 30)))
    }

    @Test fun contrasenaMinimaYRepetida() {
        assertEquals(TextosRespaldo.ERROR_CONTRASENA, ExportForm(true, "corta", "corta").errorContrasena)
        assertEquals(TextosRespaldo.ERROR_REPETIR, ExportForm(true, "larga123", "larga124").errorRepetir)
        assertTrue(ExportForm(true, "larga123", "larga123").valido)
    }

    @Test fun resumenSoloConteos() {
        assertEquals("Respaldo guardado: 0 productos, 0 insumos, 0 ventas", resumenRespaldo(ResumenRespaldo(0, 0, 0, 0), false))
        assertEquals(
            "Respaldo importado: 1 producto, 0 insumos, 2 ventas",
            resumenRespaldo(ResumenRespaldo(1, 0, 2, 0), true),
        )
    }

    @Test fun etapasConPasoYTotal() {
        assertEquals(Progreso(2, 3, "Cifrando con tu contraseña…"), EtapasRespaldo.progreso(EtapaRespaldo.CIFRANDO))
        val p = EtapasRespaldo.progreso(EtapaRespaldo.DESCIFRANDO)
        assertEquals("Etapa 3 de 4 · Comprobando la contraseña…", p.etiqueta)
        assertEquals(0.75f, p.fraccion)
    }

    @Test fun fallosExplicadosSinCulparALaContrasena() {
        assertEquals("El archivo está incompleto", fallo(AppError.ArchivoDanado(true)).titulo)
        assertEquals("El archivo está dañado", fallo(AppError.ArchivoDanado(false)).titulo)
        assertEquals("Respaldo de una versión más nueva", fallo(AppError.FormatoInvalido("respaldo de una versión más nueva de SPVI (3)")).titulo)
        assertEquals("Respaldo de una versión anterior", fallo(AppError.FormatoInvalido("respaldo de una versión anterior de SPVI (2)")).titulo)
        assertEquals(TextosRespaldo.FORMATO, fallo(AppError.FormatoInvalido("no es un respaldo SPVI")).titulo)
        listOf(AppError.ArchivoDanado(true), AppError.FormatoInvalido("x"), AppError.Almacenamiento).forEach {
            assertFalse(fallo(it).exito)
            assertTrue(fallo(it).detalle.endsWith("Tus datos no se han tocado."))
        }
        // El archivo ya se comprobó intacto antes de pedir la contraseña: el error solo puede ser la contraseña.
        assertEquals(TextosRespaldo.CONTRASENA_INCORRECTA, mensajeRespaldo(AppError.ContrasenaIncorrecta))
    }

    @Test fun describeElArchivoYAvisaQueSeReemplaza() {
        val info = InfoRespaldo(3, Instant.parse("2026-09-29T15:00:00Z"), 7)
        assertEquals("Respaldo del 29/09/2026", describirArchivo(info, zona))
        assertEquals("Respaldo de SPVI", describirArchivo(info.copy(creadoEn = null), zona))
        assertEquals("Respaldo de SPVI", describirArchivo(null, zona))
        assertTrue(TextosRespaldo.AVISO_IMPORTAR.contains("TODOS"))
    }

    // ---------- exportar

    @Test fun guardarEnTelefonoPideDestinoEscribeCifradoYConfirma() = runTest {
        val vm = vm(); val ev = mutableListOf<EventoRespaldo>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { ev += it } }
        vm.editarExport { it.copy(conContrasena = true) }
        vm.guardarEnTelefono() // protegido pero sin contraseña: errores, nada más
        assertTrue(vm.state.value.export.mostrarErrores)
        assertTrue(ev.isEmpty())
        vm.editarExport { it.copy(conContrasena = true, contrasena = "secreta12", repetir = "secreta12") }
        vm.guardarEnTelefono()
        assertEquals(EventoRespaldo.ElegirDestino("SPVI_respaldo_2026-09-30.spvi"), ev.single())
        vm.destinoElegido("content://doc/1")
        assertArrayEquals("CIFRADO".toByteArray(), archivos.destinos["content://doc/1"]!!.toByteArray())
        assertEquals(1, repo.exportados)
        assertEquals("secreta12", repo.contrasenaVista)
        assertEquals(listOf(EtapaRespaldo.PREPARANDO, EtapaRespaldo.CIFRANDO, EtapaRespaldo.ESCRIBIENDO), repo.etapas)
        // Confirmación: diálogo con el nombre y el recordatorio de la contraseña; progreso terminado.
        val r = vm.state.value.resultado!!
        assertTrue(r.exito)
        assertEquals("Respaldo guardado", r.titulo)
        assertTrue(r.detalle.startsWith("SPVI_respaldo_2026-09-30.spvi\nRespaldo guardado: 3 productos, 2 insumos, 10 ventas."))
        assertNull(vm.state.value.progreso)
        assertFalse(vm.state.value.ocupado)
        // La contraseña no se queda en pantalla.
        assertEquals(ExportForm(), vm.state.value.export)
        vm.cerrarResultado()
        assertNull(vm.state.value.resultado)
        // P18 (pregunta 3): el asistente queda en el paso 3 «Respaldo listo» hasta «Hacer otro respaldo».
        assertEquals(PasosRespaldo.textoHecho("SPVI_respaldo_2026-09-30.spvi", enviado = false), vm.state.value.exportado)
        vm.nuevoRespaldo()
        assertNull(vm.state.value.exportado)
    }

    @Test fun exportarProtegeConContrasenaPorDefecto() = runTest {
        val vm = vm(); val ev = mutableListOf<EventoRespaldo>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { ev += it } }
        assertTrue(vm.state.value.export.conContrasena)
        assertFalse(vm.state.value.export.valido) // obliga a escribir y repetir la contraseña antes de elegir destino
        vm.guardarEnTelefono()
        assertTrue(vm.state.value.export.mostrarErrores)
        assertTrue(ev.isEmpty())
    }

    @Test fun exportarSinContrasenaSoloTrasDesactivarlaYConAviso() = runTest {
        val vm = vm(); val ev = mutableListOf<EventoRespaldo>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { ev += it } }
        assertTrue(vm.state.value.export.conContrasena)
        vm.editarExport { it.copy(conContrasena = false, contrasena = "ignorada", repetir = "distinta") }
        assertTrue(vm.state.value.export.valido)
        vm.guardarEnTelefono()
        assertEquals(EventoRespaldo.ElegirDestino("SPVI_respaldo_2026-09-30.spvi"), ev.single())
        vm.destinoElegido("content://doc/2")
        assertEquals("", repo.contrasenaVista)
        assertTrue(vm.state.value.resultado!!.detalle.contains(TextosRespaldo.SIN_CONTRASENA_AVISO))
        assertTrue(vm.state.value.exportado!!.contains(TextosRespaldo.SIN_CONTRASENA_AVISO))
    }

    @Test fun cancelarElSelectorNoHaceNada() = runTest {
        val vm = vm()
        vm.destinoElegido(null)
        assertEquals(0, repo.exportados)
    }

    @Test fun compartirCreaArchivoTemporalCifrado() = runTest {
        val vm = vm(); val ev = mutableListOf<EventoRespaldo>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { ev += it } }
        vm.editarExport { it.copy(conContrasena = true, contrasena = "secreta12", repetir = "secreta12") }
        vm.compartir()
        val c = ev.single() as EventoRespaldo.Compartir
        assertEquals("SPVI_respaldo_2026-09-30.spvi", c.archivo.name)
        assertEquals(TextosRespaldo.MIME, c.mime)
        assertEquals("CIFRADO", c.archivo.readText())
        assertTrue(vm.state.value.exportado!!.contains("enviarlo"))
    }

    @Test fun siFallaCompartirSeBorraElTemporal() = runTest {
        repo.fallarExport = true
        val vm = vm(); val ev = mutableListOf<EventoRespaldo>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { ev += it } }
        vm.editarExport { it.copy(conContrasena = true, contrasena = "secreta12", repetir = "secreta12") }
        vm.compartir()
        assertEquals(EventoRespaldo.Mensaje(TextosRespaldo.NO_SE_PUDO_GUARDAR), ev.single())
        assertTrue(archivos.dir.listFiles().orEmpty().isEmpty())
    }

    // ---------- importar

    @Test fun primeroSeCompruebaElArchivoLuegoLaContrasenaConProgresoYConfirmacion() = runTest {
        archivos.origenes["content://r"] = "CIFRADO".toByteArray()
        val vm = vm()
        vm.archivoElegido("content://r", "SPVI_completo_2026-09-29.spvi")
        val imp = vm.state.value.importacion!!
        assertEquals(3, imp.info!!.version)
        assertEquals("SPVI_completo_2026-09-29.spvi", imp.nombre)
        assertTrue(repo.etapas.isEmpty()) // aún no se ha descifrado nada
        vm.editarImport("mala")
        vm.confirmarImport()
        assertEquals(TextosRespaldo.CONTRASENA_INCORRECTA, vm.state.value.importacion!!.error)
        assertEquals("", vm.state.value.importacion!!.contrasena)
        repo.etapas.clear()
        vm.editarImport("correcta1")
        vm.confirmarImport()
        assertNull(vm.state.value.importacion)
        assertEquals(EtapaRespaldo.entries.drop(3), repo.etapas)
        assertEquals(ResultadoRespaldo(true, "Respaldo importado", "Respaldo importado: 1 producto, 0 insumos, 2 ventas."), vm.state.value.resultado)
        assertNull(vm.state.value.progreso)
    }

    @Test fun archivoCortadoDanadoOAjenoSeExplicaSinPedirContrasena() = runTest {
        val vm = vm()
        mapOf(
            "CORTADO" to "El archivo está incompleto", "DANADO" to "El archivo está dañado",
            "V2" to "Respaldo de una versión anterior", "JPEG" to TextosRespaldo.FORMATO,
        ).forEach { (datos, titulo) ->
            archivos.origenes["content://$datos"] = datos.toByteArray()
            vm.archivoElegido("content://$datos")
            assertNull(vm.state.value.importacion)
            assertEquals(titulo, vm.state.value.resultado!!.titulo)
            vm.cerrarResultado()
        }
        vm.archivoElegido("content://no-existe")
        assertEquals(TextosRespaldo.NO_SE_PUDO_LEER, vm.state.value.resultado!!.titulo)
        assertTrue(repo.etapas.isEmpty())
    }

    @Test fun importarSinContrasenaNoLeeElArchivo() = runTest {
        archivos.origenes["content://r"] = "CIFRADO".toByteArray()
        val vm = vm()
        vm.archivoElegido("content://r")
        vm.confirmarImport()
        assertNotNull(vm.state.value.importacion!!.error)
        assertTrue(repo.etapas.isEmpty())
    }

    /** 0.27.0 (T10): un archivo sin contraseña se importa tras confirmar, sin pedirla. */
    @Test fun archivoSinContrasenaNoLaPide() = runTest {
        archivos.origenes["content://a"] = "ABIERTO".toByteArray()
        val vm = vm()
        vm.archivoElegido("content://a")
        val imp = vm.state.value.importacion!!
        assertFalse(PasosRespaldo.pideContrasena(imp))
        assertEquals(4, PasosRespaldo.pasoImportar(imp))
        vm.confirmarImport()
        assertEquals("", repo.importadoSinContrasena)
        assertNull(vm.state.value.importacion)
        assertTrue(vm.state.value.resultado!!.exito)
    }

    @Test fun archivoRecibidoDeOtraAppAbreLaImportacionYSeConsume() = runTest {
        archivos.origenes["file:///cache/compartir/recibido_1.spvi"] = "CIFRADO".toByteArray()
        entrada.publicar(Entrada.Archivo("file:///cache/compartir/recibido_1.spvi", "SPVI_completo_2026-09-29.spvi"))
        val vm = vm()
        assertEquals("SPVI_completo_2026-09-29.spvi", vm.state.value.importacion!!.nombre)
        assertNull(entrada.entrada.value) // consumido: no se reabre al volver
        // El texto compartido (SMS) no es para Respaldo: se deja a la venta.
        entrada.publicar(Entrada.Texto("Nro. Transaccion: BR601ADLM8997"))
        assertNotNull(entrada.entrada.value)
    }
}
