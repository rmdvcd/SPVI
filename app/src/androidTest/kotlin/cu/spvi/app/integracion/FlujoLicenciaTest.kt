package cu.spvi.app.integracion

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.spvi.app.licencia.LicenciaScreen
import cu.spvi.app.licencia.LicenciaTags
import cu.spvi.app.root.RootState
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.licencia.LicenseState
import cu.spvi.licencia.contract.TipoLicencia
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prompt 15 — bloqueo y activación: RootViewModel decide la puerta (como SpviRoot) y el panel de Licencia real
 * activa. Se reemplaza solo MainScaffold (necesita Hilt) por un marcador. La verificación criptográfica real de
 * la licencia GL está en :licencia (JVM) y en :data LicenciaInstrumentedTest (Keystore del dispositivo).
 */
@RunWith(AndroidJUnit4::class)
class FlujoLicenciaTest {
    @get:Rule val rule = createComposeRule()
    private val env = EntornoIntegracion()

    @After fun fin() = env.cerrar()

    private fun mostrarRaiz() {
        val root = env.rootVm()
        val licencia = env.licenciaVm()
        rule.setContent {
            SpviTheme {
                val s by root.state.collectAsStateWithLifecycle()
                when (s) {
                    is RootState.Bloqueo -> LicenciaScreen(onBack = null, bloqueada = true, viewModel = licencia)
                    is RootState.Main -> Box(Modifier.testTag(MAIN)) { Text("Inicio") }
                    else -> Unit
                }
            }
        }
    }

    private fun esperar(tag: String) = rule.waitUntil(5_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun pegarYActivar(mensaje: String) {
        rule.onNodeWithTag(LicenciaTags.YA_TENGO).performScrollTo().performClick() // P18: asistente en 3 pasos
        rule.onNode(hasSetTextAction() and hasText("Mensaje de licencia")).performScrollTo().performTextInput(mensaje)
        rule.onNodeWithTag(LicenciaTags.BOTON_ACTIVAR).performScrollTo().performClick()
    }

    @Test fun pruebaVencidaBloqueaYUnaLicenciaValidaDesbloquea() {
        env.licencia.fijar(LicenseState.TrialExpired)
        mostrarRaiz()
        esperar(LicenciaTags.AVISO_BLOQUEO)
        rule.onNodeWithTag(LicenciaTags.AVISO_BLOQUEO).assertIsDisplayed()
        rule.onNodeWithTag(MAIN).assertDoesNotExist()

        pegarYActivar("${LicMem.MENSAJE_VALIDO}\n{\"v\":1}")
        esperar(MAIN)
        assertEquals(1, env.licencia.activaciones.size)
    }

    @Test fun licenciaVencidaBloqueaYUnMensajeAjenoNoDesbloquea() {
        env.licencia.fijar(LicenseState.Expired(TipoLicencia.MENSUAL))
        mostrarRaiz()
        esperar(LicenciaTags.AVISO_BLOQUEO)
        pegarYActivar("Hola, esto no es una licencia")
        rule.waitUntil(5_000) { env.licencia.activaciones.isNotEmpty() }
        rule.waitForIdle()
        rule.onNodeWithTag(LicenciaTags.AVISO_BLOQUEO).assertExists()
        rule.onNodeWithTag(MAIN).assertDoesNotExist()
    }

    @Test fun perpetuaNuncaBloqueaYSiVenceConLaAppAbiertaSeBloquea() {
        env.licencia.fijar(LicenseState.Perpetual("lic-p"))
        mostrarRaiz()
        esperar(MAIN)
        // Una mensual que vence con la app abierta: la puerta pasa a Bloqueo sin reiniciar.
        rule.runOnIdle { env.licencia.fijar(LicenseState.Expired(TipoLicencia.MENSUAL)) }
        esperar(LicenciaTags.AVISO_BLOQUEO)
        assertTrue(rule.onAllNodesWithTag(MAIN).fetchSemanticsNodes().isEmpty())
    }

    private companion object { const val MAIN = "it.main" }
}
