package cu.spvi.domain

import cu.spvi.core.money.Cup
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.Empleado
import cu.spvi.domain.model.Perfil
import cu.spvi.domain.model.Vinculacion
import cu.spvi.domain.repository.PuertaTurno
import cu.spvi.domain.usecase.AbrirTurno
import cu.spvi.domain.usecase.UsuarioActual
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.26.0 (P73 §4): el fondo de cada turno de una secundaria lo asigna el dueño. */
class FondoAsignadoTest {
    private val turnos = FakeTurnos()
    private val usuario = UsuarioActual(FakePerfil(Perfil(nombre = "Luis")))
    private val clock = FixedClock()

    private class Puerta(var obligado: AppResult<Cup?>) : PuertaTurno {
        var usados = 0
        override suspend fun antesDeAbrir(): AppResult<Unit> = AppResult.Ok(Unit)
        override suspend fun fondoObligado(): AppResult<Cup?> = obligado
        override suspend fun fondoUsado() { usados++ }
    }

    @Test fun laSecundariaAbreConElFondoAsignadoAunqueSeEscribaOtro() = runBlocking {
        val p = Puerta(AppResult.Ok(Cup.ofPesos(1500)))
        val r = AbrirTurno(turnos, usuario, clock, p)(Cup.ofPesos(99))
        assertTrue(r is AppResult.Ok)
        assertEquals(Cup.ofPesos(1500), turnos.activo()?.fondo)
        assertEquals(1, p.usados)
    }

    @Test fun sinFondoAsignadoNoSeAbreElTurno() = runBlocking {
        val p = Puerta(AppResult.Err(AppError.Validacion("fondo", AppError.Regla.REQUERIDO)))
        assertEquals(AppResult.Err(AppError.Validacion("fondo", AppError.Regla.REQUERIDO)), AbrirTurno(turnos, usuario, clock, p)(Cup.ZERO))
        assertNull(turnos.activo())
        assertEquals(0, p.usados)
    }

    @Test fun laPrincipalYLasSecundariasDeUnaPrincipal025EscribenSuFondo() = runBlocking {
        val p = Puerta(AppResult.Ok(null))
        assertTrue(AbrirTurno(turnos, usuario, clock, p)(Cup.ofPesos(300)) is AppResult.Ok)
        assertEquals(Cup.ofPesos(300), turnos.activo()?.fondo)
        assertEquals(0, p.usados) // no hay fondo asignado que gastar
        // La puerta por defecto (principal) no obliga a nada.
        assertEquals(AppResult.Ok(null), PuertaTurno.SIEMPRE.fondoObligado())
    }

    @Test fun peticionYAvisoDeActualizarEnLaFicha() {
        val t0 = Instant.parse("2026-10-04T12:00:00Z")
        val base = Empleado(1, "Luis", emptySet(), t0, vinculadoEn = t0, ultimaSincronizacion = t0, versionCode = Vinculacion.VERSION_FONDO_ASIGNADO)
        assertFalse(base.pideApertura); assertFalse(base.appDesactualizada)
        val pide = base.copy(aperturaSolicitadaEn = t0)
        assertTrue(pide.pideApertura)
        // Resuelta al asignar el fondo o si ya abrió su turno.
        assertFalse(pide.copy(fondoAsignado = Cup.ZERO).pideApertura)
        assertFalse(pide.copy(turnoAbiertoDesde = t0).pideApertura)
        // 0.25.x (versionCode 47) o sin versionCode: «Actualiza la app de Luis». Sin sincronizar aún: no se sabe.
        assertTrue(base.copy(versionCode = 47).appDesactualizada)
        assertTrue(base.copy(versionCode = null).appDesactualizada)
        assertFalse(base.copy(versionCode = null, ultimaSincronizacion = null).appDesactualizada)
        assertEquals(48, Vinculacion.VERSION_FONDO_ASIGNADO)
    }
}
