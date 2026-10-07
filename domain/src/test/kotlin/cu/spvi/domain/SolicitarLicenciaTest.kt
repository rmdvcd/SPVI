package cu.spvi.domain

import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.Perfil
import cu.spvi.domain.model.Telefono
import cu.spvi.domain.usecase.SolicitarLicencia
import cu.spvi.licencia.SolicitudInput
import cu.spvi.licencia.contract.TipoLicencia
import cu.spvi.licencia.contract.Via
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SolicitarLicenciaTest {

    private val input = SolicitudInput(" Ana ", "Pérez Díaz", "85010112345", "51234567", TipoLicencia.MENSUAL, Via.WHATSAPP)

    @Test fun `solicitud correcta actualiza el perfil y antepone el telefono normalizado`() = runBlocking {
        val perfil = FakePerfil(Perfil(telefonos = listOf(Telefono(1, "+5359999999"))))
        SolicitarLicencia(FakeLicencia(solicitud = AppResult.Ok(cu.spvi.licencia.SolicitudGenerada("msg\n\nSPVIR1:x", "SPVIR1:x"))), perfil)(input)
        val p = perfil.state.value
        assertEquals("Ana", p.nombre)
        assertEquals("Pérez Díaz", p.apellidos)
        assertEquals(listOf("+5351234567", "+5359999999"), p.telefonos.map { it.numero })
    }

    @Test fun `telefono ya presente no se duplica`() = runBlocking {
        val perfil = FakePerfil(Perfil(telefonos = listOf(Telefono(1, "+5351234567"))))
        SolicitarLicencia(FakeLicencia(solicitud = AppResult.Ok(cu.spvi.licencia.SolicitudGenerada("msg\n\nSPVIR1:x", "SPVIR1:x"))), perfil)(input)
        assertEquals(1, perfil.state.value.telefonos.size)
    }

    @Test fun `solicitud fallida no toca el perfil`() = runBlocking {
        val original = Perfil(nombre = "Luis")
        val perfil = FakePerfil(original)
        SolicitarLicencia(FakeLicencia(solicitud = AppResult.Err(AppError.Cripto)), perfil)(input)
        assertEquals(original, perfil.state.value)
    }
}
