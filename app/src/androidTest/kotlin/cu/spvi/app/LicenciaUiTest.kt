package cu.spvi.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.spvi.app.licencia.LicenciaAcciones
import cu.spvi.app.licencia.LicenciaContent
import cu.spvi.app.licencia.LicenciaForm
import cu.spvi.app.licencia.LicenciaTags
import cu.spvi.app.licencia.LicenciaUiState
import cu.spvi.designsystem.component.SpviStepperTextos
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.domain.model.Licencia
import cu.spvi.domain.model.Perfil
import cu.spvi.domain.model.Telefono
import cu.spvi.licencia.LicenseState
import cu.spvi.licencia.contract.TipoLicencia
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pantallas sin estado: no requieren Hilt ni Keystore. */
@RunWith(AndroidJUnit4::class)
class LicenciaUiTest {
    @get:Rule val rule = createComposeRule()

    private val ahora = Instant.parse("2026-09-30T12:00:00Z")
    private val huella = "ab".repeat(32)
    private val vencida = Licencia(LicenseState.Expired(TipoLicencia.MENSUAL), "SPVI:abc", true, huella)
    private val perfil = Perfil("María", "Pérez González", "85010112345", telefonos = listOf(Telefono(1, "52345678")))

    private var solicitudes = 0
    private fun acciones() = LicenciaAcciones(
        onBack = {}, onEditar = {}, onAbrirEdicion = {}, onCerrarEdicion = {},
        onSolicitar = { solicitudes++ }, onActivar = {}, onPegar = {},
    )

    private fun licencia(state: LicenciaUiState, bloqueada: Boolean = false) = rule.setContent {
        SpviTheme(darkTheme = false) { LicenciaContent(state, ahora, bloqueada, acciones()) }
    }

    private fun siguiente() = rule.onNodeWithContentDescription(SpviStepperTextos.SIGUIENTE).performScrollTo().performClick()

    // ---------- Bloqueo ----------

    @Test fun bloqueoAbreLicenciaConMotivoYSoporte() {
        var soporte = 0
        val acc = LicenciaAcciones(
            onBack = null, onEditar = {}, onAbrirEdicion = {}, onCerrarEdicion = {},
            onSolicitar = {}, onActivar = {}, onPegar = {}, onSoporte = { soporte++ },
        )
        rule.setContent {
            SpviTheme(darkTheme = false) {
                LicenciaContent(LicenciaUiState(vencida, LicenciaForm().desdePerfil(perfil), perfilVacio = false), ahora, true, acc)
            }
        }
        rule.onNodeWithTag(LicenciaTags.AVISO_BLOQUEO).assertIsDisplayed()
        rule.onNodeWithText("Licencia Mensual vencida").assertIsDisplayed()
        rule.onNodeWithTag(LicenciaTags.SOPORTE).performClick()
        assertEquals(1, soporte)
    }

    // ---------- Panel de Licencia ----------

    @Test fun panelEnBloqueoMuestraAviso() {
        licencia(LicenciaUiState(vencida, LicenciaForm().desdePerfil(perfil), perfilVacio = false), bloqueada = true)
        rule.onNodeWithTag(LicenciaTags.AVISO_BLOQUEO).assertIsDisplayed()
    }

    @Test fun perfilVacioMuestraFormulario() {
        licencia(LicenciaUiState(vencida, LicenciaForm().desdePerfil(Perfil()), perfilVacio = true))
        rule.onNodeWithTag(LicenciaTags.FORM_DATOS).performScrollTo().assertIsDisplayed()
    }

    @Test fun perfilCompletoMuestraResumen() {
        licencia(LicenciaUiState(vencida, LicenciaForm().desdePerfil(perfil), listOf("52345678"), perfilVacio = false))
        rule.onNodeWithTag(LicenciaTags.RESUMEN_DATOS).performScrollTo().assertIsDisplayed()
        siguiente()
        rule.onNodeWithText("Solicitar renovación").assertExists()
    }

    @Test fun buildSinClaveDelEmisorRemiteAlDesarrolladorYDeshabilitaSolicitar() {
        val sinClave = vencida.copy(huellaEmisor = null, puedeSolicitar = false)
        licencia(LicenciaUiState(sinClave, LicenciaForm().desdePerfil(perfil), perfilVacio = false))
        rule.onNodeWithText("Esta versión no puede pedir licencias").assertIsDisplayed()
        siguiente()
        rule.onNodeWithTag(LicenciaTags.BOTON_SOLICITAR).performScrollTo().assertIsNotEnabled()
    }

    @Test fun buildSinValidacionSeDistingueDeFaltaDeClave() {
        licencia(LicenciaUiState(vencida.copy(validacionDisponible = false), LicenciaForm().desdePerfil(perfil), perfilVacio = false))
        rule.onNodeWithText("Esta versión no puede validar licencias").assertIsDisplayed()
        rule.onNodeWithText("Esta versión no puede pedir licencias").assertDoesNotExist()
    }

    @Test fun solicitarInvocaLaAccion() {
        licencia(LicenciaUiState(vencida, LicenciaForm().desdePerfil(perfil), perfilVacio = false))
        siguiente()
        rule.onNodeWithTag(LicenciaTags.BOTON_SOLICITAR).performScrollTo().assertIsEnabled().performClick()
        assertTrue(solicitudes == 1)
    }

    // ---------- P18 (A08): asistente en 3 pasos ----------

    @Test fun asistenteAnunciaElPasoYAvanzaHastaActivar() {
        licencia(LicenciaUiState(vencida, LicenciaForm().desdePerfil(perfil), perfilVacio = false))
        rule.onNodeWithContentDescription("Paso 1 de 3: Revisa tus datos").assertExists()
        rule.onNodeWithTag(LicenciaTags.BOTON_ACTIVAR).assertDoesNotExist()
        siguiente()
        rule.onNodeWithContentDescription("Paso 2 de 3: Pide la licencia").assertExists()
        siguiente()
        rule.onNodeWithContentDescription("Paso 3 de 3: Activa la licencia").assertExists()
        rule.onNodeWithTag(LicenciaTags.BOTON_ACTIVAR).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription(SpviStepperTextos.SIGUIENTE).assertDoesNotExist()
        rule.onNodeWithContentDescription(SpviStepperTextos.ATRAS).performClick()
        rule.onNodeWithContentDescription("Paso 2 de 3: Pide la licencia").assertExists()
    }

    @Test fun datosInvalidosNoAvanzanYMarcanErrores() {
        var editado: LicenciaForm? = null
        val form = LicenciaForm().desdePerfil(Perfil())
        val acc = LicenciaAcciones(
            onBack = {}, onEditar = { editado = it(form) }, onAbrirEdicion = {}, onCerrarEdicion = {},
            onSolicitar = {}, onActivar = {}, onPegar = {},
        )
        rule.setContent { SpviTheme(darkTheme = false) { LicenciaContent(LicenciaUiState(vencida, form, perfilVacio = true), ahora, false, acc) } }
        siguiente()
        rule.onNodeWithContentDescription("Paso 1 de 3: Revisa tus datos").assertExists()
        assertTrue(editado?.mostrarErrores == true)
    }

    @Test fun yaTengoElMensajeSaltaAActivar() {
        licencia(LicenciaUiState(vencida, LicenciaForm().desdePerfil(perfil), perfilVacio = false))
        rule.onNodeWithTag(LicenciaTags.YA_TENGO).performScrollTo().performClick()
        rule.onNodeWithTag(LicenciaTags.BOTON_ACTIVAR).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(LicenciaTags.YA_TENGO).assertDoesNotExist()
    }

}
