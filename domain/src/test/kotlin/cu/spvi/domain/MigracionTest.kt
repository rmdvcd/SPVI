package cu.spvi.domain

import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.AutorizacionMigracion
import cu.spvi.domain.model.MensajeMigracion
import cu.spvi.domain.model.Perfil
import cu.spvi.domain.usecase.CamposMigracion
import cu.spvi.domain.usecase.CompletarMigracion
import cu.spvi.domain.usecase.ConstruirSolicitudMigracion
import cu.spvi.domain.usecase.VerificarAutorizacionMigracion
import cu.spvi.licencia.LicenseState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MigracionTest {
    private val lic = FakeLicencia()
    private val mant = FakeMantenimiento(lic.diario)
    private val perfil = FakePerfil(Perfil(nombre = "María", apellidos = "Pérez", ci = "85010112345"))

    @Test fun normalizaElIdDelTelefonoNuevo() {
        assertEquals("SPVI:ffeeddccbbaa9988", MensajeMigracion.normalizarId(" spvi:ffee ddcc\nbbaa 9988 "))
        assertNull(MensajeMigracion.normalizarId("ffeeddccbbaa9988"))
        assertNull(MensajeMigracion.normalizarId("SPVI:"))
        assertNull(MensajeMigracion.normalizarId("SPVI:ab#cd!ef"))
    }

    @Test fun solicitudLlevaIdsYNombrePeroNoElCarnet() = runBlocking {
        val r = ConstruirSolicitudMigracion(lic, perfil)("spvi:ffeeddccbbaa9988") as AppResult.Ok
        assertTrue(r.value.contains("ID del teléfono actual: SPVI:abc12345"))
        assertTrue(r.value.contains("ID del teléfono nuevo: SPVI:ffeeddccbbaa9988"))
        assertTrue(r.value.contains("Licencia actual: sin licencia"))
        assertTrue(r.value.contains("Nombre: María Pérez"))
        assertFalse(r.value.contains("85010112345"))
    }

    @Test fun solicitudValidaElDestino() = runBlocking {
        val uc = ConstruirSolicitudMigracion(lic, perfil)
        assertEquals(AppError.Validacion(CamposMigracion.DESTINO, AppError.Regla.REQUERIDO), (uc("  ") as AppResult.Err).error)
        assertEquals(AppError.Validacion(CamposMigracion.DESTINO, AppError.Regla.FORMATO), (uc("1234") as AppResult.Err).error)
        // El propio ID no es un destino.
        assertEquals(AppError.Validacion(CamposMigracion.DESTINO, AppError.Regla.NO_PERMITIDO), (uc("SPVI:abc12345") as AppResult.Err).error)
    }

    @Test fun verificarTextoVacioNoConsultaAlRepositorio() = runBlocking {
        assertEquals(AutorizacionMigracion.NO_ENCONTRADA, VerificarAutorizacionMigracion(lic)(" "))
        assertTrue(lic.diario.isEmpty())
    }

    @Test fun completarCedeAntesDeBorrarYReevalua() = runBlocking {
        val antes = lic.refrescos
        assertEquals(AppResult.Ok(Unit), CompletarMigracion(lic, mant)("licencia del nuevo"))
        assertEquals(listOf("verificar", "ceder", "borrar"), lic.diario)
        assertEquals(antes + 1, lic.refrescos)
        assertEquals(LicenseState.TrialExpired, lic.snapshot.value!!.estado)
    }

    @Test fun sinAutorizacionNoSeTocaNada() = runBlocking {
        for (a in listOf(AutorizacionMigracion.DE_ESTE_TELEFONO, AutorizacionMigracion.RECHAZADA, AutorizacionMigracion.NO_ENCONTRADA)) {
            lic.diario.clear(); lic.autorizacion = a
            val r = CompletarMigracion(lic, mant)("x") as AppResult.Err
            assertEquals(AppError.Validacion(CamposMigracion.AUTORIZACION, AppError.Regla.NO_PERMITIDO), r.error)
            assertEquals(listOf("verificar"), lic.diario)
        }
    }

    @Test fun siNoSePuedeCederNoSeBorra() = runBlocking {
        lic.cesion = AppResult.Err(AppError.Almacenamiento)
        assertEquals(AppResult.Err(AppError.Almacenamiento), CompletarMigracion(lic, mant)("x"))
        assertEquals(listOf("verificar", "ceder"), lic.diario)
    }

    @Test fun fallarAlBorrarDejaElTelefonoBloqueadoIgual() = runBlocking {
        mant.resultado = AppResult.Err(AppError.Almacenamiento)
        assertEquals(AppResult.Err(AppError.Almacenamiento), CompletarMigracion(lic, mant)("x"))
        assertEquals(LicenseState.TrialExpired, lic.snapshot.value!!.estado)
    }
}
