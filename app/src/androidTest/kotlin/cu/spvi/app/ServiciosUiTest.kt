package cu.spvi.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.spvi.app.inicio.EstadoCarga
import cu.spvi.app.servicios.AccionesServicios
import cu.spvi.app.servicios.ServiciosContent
import cu.spvi.app.servicios.ServiciosTags
import cu.spvi.app.servicios.ServiciosUiState
import cu.spvi.app.servicios.TextosServicios
import cu.spvi.core.money.Cup
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.domain.model.Servicio
import cu.spvi.domain.model.ServicioDisponible
import cu.spvi.domain.model.VistaServicios
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiciosUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun progresoDeBusquedaEsAccesibleYConservaLaListaAnterior() {
        val servicio = ServicioDisponible(
            Servicio(id = 1, nombre = "Corte", tipo = "Peluquería", importe = Cup.ofPesos(100), creadoEn = Instant.EPOCH),
            lineas = emptyList(),
            costo = null,
        )
        val state = ServiciosUiState(
            vista = EstadoCarga.Exito(VistaServicios(listOf(servicio), total = 1, tipos = listOf("Peluquería"))),
            buscando = true,
        )
        rule.setContent { SpviTheme(darkTheme = false) { ServiciosContent(state, AccionesServicios()) } }

        rule.onNodeWithTag(ServiciosTags.BUSCANDO).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(TextosServicios.BUSCANDO).assertCountEquals(1)
        rule.onNodeWithTag(ServiciosTags.LISTA).assertIsDisplayed()
        rule.onNodeWithText("Corte").assertIsDisplayed()
    }
}
