package cu.spvi.app.actualizacion

import cu.spvi.app.actualizacion.RespaldoPrevio.Restauracion
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.EstadoApp
import cu.spvi.domain.model.InfoApp
import cu.spvi.domain.model.LicenciaRecuperable
import cu.spvi.domain.repository.EstadoAppRepository
import cu.spvi.domain.repository.EtapaRespaldo
import cu.spvi.domain.repository.InfoRespaldo
import cu.spvi.domain.repository.RespaldoRepository
import cu.spvi.domain.repository.ResumenRespaldo
import cu.spvi.domain.usecase.ExportarRespaldo
import cu.spvi.domain.usecase.ImportarRespaldo
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 0.30.1: respaldo automático antes de actualizar y restauración al abrir la versión nueva. */
class RespaldoPrevioTest {
    private lateinit var dir: File
    private val repo = RepoFalso()
    private val estado = EstadoMem()
    private lateinit var previo: RespaldoPrevio

    @Before fun preparar() {
        dir = Files.createTempDirectory("previo").toFile()
        previo = RespaldoPrevio(File(dir, "respaldo_actualizacion"), ExportarRespaldo(repo), ImportarRespaldo(repo), estado)
    }

    @After fun limpiar() { dir.deleteRecursively() }

    @Test fun crearGuardaElRespaldoSinContrasenaYNoCuentaComoRespaldoExportado() = runTest {
        val antes = Instant.parse("2026-09-01T00:00:00Z")
        estado.flujo.value = EstadoApp(ultimoRespaldo = antes)
        assertTrue(previo.crear("0.30.0/51"))
        assertTrue(previo.existe)
        assertEquals(0, repo.contrasenaExportada) // vacía: respaldo sin contraseña
        assertEquals(antes, estado.flujo.value.ultimoRespaldo) // el recordatorio mensual no se reinicia
    }

    @Test fun sinRespaldoNoSeActualiza() = runTest {
        repo.falla = true
        assertFalse(previo.crear("0.30.0/51"))
        assertFalse(previo.existe)
        assertTrue(File(dir, "respaldo_actualizacion").listFiles().orEmpty().isEmpty()) // nada a medias
    }

    @Test fun trasActualizarSeRestauraYSeBorra() = runTest {
        previo.crear("0.30.0/51")
        assertEquals(Restauracion.RESTAURADO, previo.restaurarSiCorresponde("0.30.1/52"))
        assertEquals("datos", repo.importado)
        assertEquals(0, repo.contrasenaImportada)
        assertFalse(previo.existe)
        // una segunda apertura no vuelve a restaurar
        repo.importado = null
        assertEquals(Restauracion.NADA_QUE_RESTAURAR, previo.restaurarSiCorresponde("0.30.1/52"))
        assertNull(repo.importado)
    }

    @Test fun siLaVersionNoCambioElRespaldoSeDescartaSinRestaurar() = runTest {
        previo.crear("0.30.0/51")
        assertEquals(Restauracion.NADA_QUE_RESTAURAR, previo.restaurarSiCorresponde("0.30.0/51"))
        assertNull(repo.importado)
        assertFalse(previo.existe)
    }

    @Test fun siFallaLaRestauracionElRespaldoSeConserva() = runTest {
        previo.crear("0.30.0/51")
        repo.fallaImportar = true
        assertEquals(Restauracion.FALLO, previo.restaurarSiCorresponde("0.30.1/52"))
        assertTrue(previo.existe)
        repo.fallaImportar = false
        assertEquals(Restauracion.RESTAURADO, previo.restaurarSiCorresponde("0.30.1/52"))
    }

    @Test fun restaurarNoDejaUnaLicenciaRecuperableNueva() = runTest {
        previo.crear("0.30.0/51")
        repo.dejaLicenciaRecuperable = { estado.flujo.value = estado.flujo.value.copy(licenciaRecuperable = LicenciaRecuperable("x", "MENSUAL", 0, null, "123")) }
        previo.restaurarSiCorresponde("0.30.1/52")
        assertNull(estado.flujo.value.licenciaRecuperable)
    }

    @Test fun descartarBorraTodo() = runTest {
        previo.crear("0.30.0/51")
        previo.descartar()
        assertFalse(previo.existe)
        assertTrue(File(dir, "respaldo_actualizacion").listFiles().orEmpty().isEmpty())
    }

    @Test fun laClaveDistingueCompilacionesConElMismoNombre() {
        assertEquals("0.30.0/51", RespaldoPrevio.clave(InfoApp("0.30.0", 51, "")))
        assertTrue(RespaldoPrevio.clave(InfoApp("0.30.0", 51, "")) != RespaldoPrevio.clave(InfoApp("0.30.0", 52, "")))
    }

    @Test fun lasNotasNoMencionanRepositorioNiGithub() {
        val notas = "Mejoras en Inicio\nDescarga en github.com/x/y\nVer el repositorio\nNueva tarjeta de Licencia"
        val visibles = TextosActualizacion.notasVisibles(notas)
        assertEquals("Mejoras en Inicio\nNueva tarjeta de Licencia", visibles)
        listOf(TextosActualizacion.BUSCAR_DETALLE, TextosActualizacion.SIN_REPO, TextosActualizacion.GUARDANDO_RESPALDO,
            TextosActualizacion.ERROR_RESPALDO_PREVIO, TextosActualizacion.RESPALDO_RESTAURADO, TextosActualizacion.RESPALDO_NO_RESTAURADO,
            TextosActualizacion.disponible("0.30.1", 1_048_576), TextosActualizacion.desdePrincipal("0.30.1"),
        ).forEach { texto ->
            assertFalse(texto, texto.contains("github", ignoreCase = true) || texto.contains("repositorio", ignoreCase = true))
        }
    }

    private class RepoFalso : RespaldoRepository {
        var falla = false
        var fallaImportar = false
        var contrasenaExportada = -1
        var contrasenaImportada = -1
        var importado: String? = null
        var dejaLicenciaRecuperable: () -> Unit = {}

        override suspend fun exportar(contrasena: CharArray, destino: OutputStream, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> {
            contrasenaExportada = contrasena.size
            if (falla) return AppResult.Err(AppError.Almacenamiento)
            destino.write("datos".toByteArray())
            return AppResult.Ok(ResumenRespaldo(0, 0, 0, 0))
        }

        override suspend fun inspeccionar(origen: InputStream): AppResult<InfoRespaldo> = AppResult.Err(AppError.Almacenamiento)

        override suspend fun importar(origen: InputStream, contrasena: CharArray, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> {
            contrasenaImportada = contrasena.size
            if (fallaImportar) return AppResult.Err(AppError.Almacenamiento)
            importado = String(origen.readBytes())
            dejaLicenciaRecuperable()
            return AppResult.Ok(ResumenRespaldo(0, 0, 0, 0))
        }
    }

    private class EstadoMem : EstadoAppRepository {
        val flujo = MutableStateFlow(EstadoApp())
        override val estado: Flow<EstadoApp> = flujo
        override suspend fun actual() = flujo.value
        override suspend fun editar(cambio: (EstadoApp) -> EstadoApp) { flujo.value = cambio(flujo.value) }
        override suspend fun borrar() { flujo.value = EstadoApp() }
    }
}
