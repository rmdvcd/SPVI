package cu.spvi.app.actualizacion

import android.app.Application
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.domain.model.EstadoApp
import cu.spvi.domain.model.EstadoSecundaria
import cu.spvi.domain.model.InfoApp
import cu.spvi.domain.model.Licencia
import cu.spvi.domain.repository.ActualizacionesRepository
import cu.spvi.domain.repository.EstadoAppRepository
import cu.spvi.domain.repository.EtapaRespaldo
import cu.spvi.domain.repository.InfoRespaldo
import cu.spvi.domain.repository.LicenciaRepository
import cu.spvi.domain.repository.MantenimientoRepository
import cu.spvi.domain.repository.RespaldoRepository
import cu.spvi.domain.repository.ResumenRespaldo
import cu.spvi.domain.repository.SecundariaRepository
import cu.spvi.domain.usecase.ComprobarActualizaciones
import cu.spvi.domain.usecase.EstadoActualizacionObligatoria
import cu.spvi.domain.usecase.ExportarRespaldo
import cu.spvi.domain.usecase.ImportarRespaldo
import cu.spvi.licencia.ActivationResult
import cu.spvi.licencia.SolicitudGenerada
import cu.spvi.licencia.SolicitudInput
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 0.31.0 (P5): cerrar el cuadro de «Actualizar» a mitad del proceso no lo deja colgado:
 * el trabajo en curso se cancela y el resultado tardío no reabre el cuadro solo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProcesoActualizarTest {
    private lateinit var dir: File
    private lateinit var estadoApp: EstadoMem
    private lateinit var github: GithubPuerta

    @Before fun preparar() {
        dir = Files.createTempDirectory("proceso").toFile()
        estadoApp = EstadoMem()
        github = GithubPuerta()
    }

    @After fun limpiar() { dir.deleteRecursively() }

    private fun gestor(): GestorActualizacion {
        val contexto = RuntimeEnvironment.getApplication()
        val comprobar = ComprobarActualizaciones(estadoApp, github, LicenciaVacia(), MantenimientoNulo(), Clock { Instant.now() })
        val respaldo = RespaldoPrevio(File(dir, "previo"), ExportarRespaldo(RespaldoNulo()), ImportarRespaldo(RespaldoNulo()), estadoApp)
        return GestorActualizacion(
            contexto, comprobar, github, estadoApp, SecundariaSuelta(), InstaladorApk(contexto),
            InfoApp("0.30.0", 51, "dueno/repo"), EstadoActualizacionObligatoria(estadoApp, Clock { Instant.now() }), respaldo,
        )
    }

    @Test fun cerrarDuranteLaComprobacionNoReabreElCuadro() = runBlocking {
        val g = gestor()
        g.actualizarAhora()
        withTimeout(10_000) { g.estado.first { it.proceso is ProcesoActualizar.EnCurso } }
        g.cerrarProceso()
        withTimeout(10_000) { g.estado.first { it.proceso == null } }
        github.puerta.complete(AppResult.Err(AppError.Red.SinConexion))
        delay(2_000) // margen para que un resultado tardío aterrice
        assertNull("el cuadro se reabrió solo tras cerrar", g.estado.value.proceso)
    }

    @Test fun sinCerrarMuestraElResultadoYCierra() = runBlocking {
        val g = gestor()
        g.actualizarAhora()
        withTimeout(10_000) { g.estado.first { it.proceso is ProcesoActualizar.EnCurso } }
        github.puerta.complete(AppResult.Err(AppError.Red.SinConexion))
        withTimeout(10_000) { g.estado.first { it.proceso == ProcesoActualizar.SinConexion } }
        g.cerrarProceso()
        withTimeout(10_000) { g.estado.first { it.proceso == null } }
        assertTrue(github.llamadas == 1)
    }

    /** La comprobación se queda esperando hasta que el test la suelta (red lenta). */
    private class GithubPuerta : ActualizacionesRepository {
        override val repoConfigurado = true
        val puerta = CompletableDeferred<AppResult<cu.spvi.domain.model.InfoActualizacion?>>()
        var llamadas = 0

        override suspend fun ultimaVersion(): AppResult<cu.spvi.domain.model.InfoActualizacion?> {
            llamadas++
            return puerta.await()
        }

        override suspend fun listaRevocaciones(): AppResult<String> = AppResult.Err(AppError.Red.SinConexion)
        override suspend fun descargar(info: cu.spvi.domain.model.InfoActualizacion, progreso: (Long, Long) -> Unit): AppResult<File> =
            AppResult.Err(AppError.Red.SinConexion)
    }

    private class EstadoMem : EstadoAppRepository {
        val flujo = MutableStateFlow(EstadoApp())
        override val estado: Flow<EstadoApp> = flujo
        override suspend fun actual() = flujo.value
        override suspend fun editar(cambio: (EstadoApp) -> EstadoApp) { flujo.value = cambio(flujo.value) }
        override suspend fun borrar() { flujo.value = EstadoApp() }
    }

    private class LicenciaVacia : LicenciaRepository {
        override val snapshot: StateFlow<Licencia?> = MutableStateFlow(null)
        override suspend fun refrescar(): Licencia = error("sin licencia")
        override suspend fun construirSolicitud(input: SolicitudInput): AppResult<SolicitudGenerada> = error("sin licencia")
        override suspend fun activar(mensaje: String): ActivationResult = error("sin licencia")
    }

    private class MantenimientoNulo : MantenimientoRepository {
        override suspend fun borrarTodo(): AppResult<Unit> = AppResult.Ok(Unit)
    }

    private class SecundariaSuelta : SecundariaRepository {
        override val estado = MutableStateFlow(EstadoSecundaria())
        override suspend fun vincular(contenidoQr: String, telefono: String): AppResult<Unit> = AppResult.Err(AppError.Red.SinConexion)
        override suspend fun solicitarCierre(contado: cu.spvi.core.money.Cup): AppResult<Unit> = AppResult.Err(AppError.Red.SinConexion)
        override suspend fun pedirFondo(): AppResult<Unit> = AppResult.Err(AppError.Red.SinConexion)
        override suspend fun sincronizarAhora(): AppResult<Unit> = AppResult.Err(AppError.Red.SinConexion)
        override suspend fun cambiarModo(modo: cu.spvi.domain.model.ModoSincronizacion) = Unit
        override suspend fun desvincular(): AppResult<Unit> = AppResult.Err(AppError.Red.SinConexion)
        override suspend fun descartarAvisoCierre() = Unit
    }

    private class RespaldoNulo : RespaldoRepository {
        override suspend fun exportar(contrasena: CharArray, destino: OutputStream, progreso: (EtapaRespaldo) -> Unit) =
            AppResult.Ok(ResumenRespaldo(0, 0, 0, 0))

        override suspend fun inspeccionar(origen: InputStream): AppResult<InfoRespaldo> = AppResult.Err(AppError.Red.SinConexion)
        override suspend fun importar(origen: InputStream, contrasena: CharArray, progreso: (EtapaRespaldo) -> Unit) =
            AppResult.Err(AppError.Red.SinConexion)
    }
}
