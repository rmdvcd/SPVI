package cu.spvi.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.spvi.app.ajustes.AjustesContent
import cu.spvi.app.ajustes.AjustesTags
import cu.spvi.app.ajustes.AjustesUiState
import cu.spvi.app.navigation.Route
import cu.spvi.app.onboarding.ModoWizard
import cu.spvi.app.onboarding.NivelesForm
import cu.spvi.app.onboarding.OnboardingAcciones
import cu.spvi.app.onboarding.OnboardingContent
import cu.spvi.app.onboarding.OnboardingTags
import cu.spvi.app.onboarding.OnboardingUiState
import cu.spvi.app.onboarding.PasoWizard
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.domain.model.Licencia
import cu.spvi.domain.model.PasoConfiguracion
import cu.spvi.domain.model.ResumenConfiguracion
import cu.spvi.licencia.LicenseState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pantallas sin estado del asistente, el escáner y Ajustes (sin Hilt). */
@RunWith(AndroidJUnit4::class)
class OnboardingUiTest {
    @get:Rule val rule = createComposeRule()

    private val plan = listOf(PasoWizard.BIENVENIDA, PasoWizard.DATOS, PasoWizard.ALERTAS, PasoWizard.PRUEBA)
    private val lic = Licencia(LicenseState.Trial(7), "SPVI:x", false, null)
    private val clics = mutableListOf<String>()

    private fun acciones() = OnboardingAcciones(
        onSiguiente = { clics += "siguiente" }, onAtras = { clics += "atras" },
        onOmitirPaso = { clics += "omitirPaso" }, onOmitirTodo = { clics += "omitirTodo" },
        onDatos = {}, onNivel = { _, _ -> }, onSumarNivel = { _, _ -> clics += "sumar" }, onRestablecerNiveles = {},
    )

    private fun wizard(indice: Int, modo: ModoWizard = ModoWizard.PRIMERA_VEZ, niveles: NivelesForm = NivelesForm(), errores: Boolean = false) =
        rule.setContent {
            SpviTheme(darkTheme = false) {
                OnboardingContent(
                    OnboardingUiState(cargando = false, modo = modo, pasos = plan, indice = indice, licencia = lic, niveles = niveles, mostrarErroresNiveles = errores),
                    acciones(),
                )
            }
        }

    @Test fun bienvenidaPermiteSaltarTodo() {
        wizard(0)
        rule.onNodeWithTag(OnboardingTags.paso(PasoWizard.BIENVENIDA)).assertIsDisplayed()
        rule.onNodeWithText("Bienvenido a SPVI").assertIsDisplayed()
        rule.onNodeWithTag(OnboardingTags.OMITIR_TODO).performClick()
        assertEquals(listOf("omitirTodo"), clics)
    }

    @Test fun progresoYOmitirPaso() {
        wizard(1)
        rule.onNodeWithContentDescription("Paso 1 de 3", substring = true).assertExists() // P18 (L9): SpviStepper
        rule.onNodeWithTag(OnboardingTags.OMITIR_PASO).performClick()
        rule.onNodeWithContentDescription("Atrás").performClick()
        assertEquals(listOf("omitirPaso", "atras"), clics)
    }

    @Test fun alertasMuestranValoresRecomendadosYBotonesAccesibles() {
        wizard(2)
        rule.onNodeWithTag(OnboardingTags.paso(PasoWizard.ALERTAS)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Sumar 1 a Bajo").assertCountEquals(2) // producto e insumo
        rule.onAllNodesWithContentDescription("Sumar 1 a Bajo")[0].performClick()
        rule.onNodeWithTag(OnboardingTags.SIGUIENTE).performClick()
        assertEquals(listOf("sumar", "siguiente"), clics)
    }

    @Test fun alertasInvalidasMuestranElMotivo() {
        wizard(2, niveles = NivelesForm(productoBajo = "1", productoCritico = "3"), errores = true)
        rule.onAllNodesWithText(NivelesForm.CRITICO_MAYOR).onFirst().assertExists()
    }

    @Test fun pruebaExplicaDiasYQueNoSePierdenDatos() {
        wizard(3)
        rule.onAllNodesWithText("Tienes 7 días de prueba gratis").onFirst().assertIsDisplayed()
        rule.onNodeWithText("Tus datos NO se borran", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(OnboardingTags.SIGUIENTE).assertTextContains("Empezar a usar SPVI")
    }

    // ---------- Ajustes ----------

    @Test fun ajustesMuestraPendientesYAbreElAsistente() {
        var destino: Route? = null
        val resumen = ResumenConfiguracion(PasoConfiguracion.entries.toList(), listOf(PasoConfiguracion.ALERTAS, PasoConfiguracion.PRUEBA))
        rule.setContent {
            SpviTheme(darkTheme = false) { AjustesContent(AjustesUiState(resumen), { destino = it }, {}) }
        }
        rule.onNodeWithText("Faltan 2 pasos").assertIsDisplayed()
        rule.onNodeWithTag(AjustesTags.CONFIGURACION).performClick()
        assertEquals(Route.ConfiguracionInicial(), destino)
    }
}
