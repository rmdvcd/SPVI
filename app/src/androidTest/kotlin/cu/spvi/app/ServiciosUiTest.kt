package cu.spvi.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.spvi.app.inicio.EstadoCarga
import cu.spvi.app.servicios.AccionesServicios
import cu.spvi.app.servicios.HojaServicios
import cu.spvi.app.servicios.ServiciosContent
import cu.spvi.app.servicios.ServiciosTags
import cu.spvi.app.servicios.ServiciosUiState
import cu.spvi.core.money.Cup
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.domain.model.FiltroServicios
import cu.spvi.domain.model.Servicio
import cu.spvi.domain.model.ServicioDisponible
import cu.spvi.domain.model.VistaServicios
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiciosUiTest {
    @get:Rule val rule = createComposeRule()

    private val t0 = Instant.parse("2026-09-30T16:00:00Z")
    private fun servicio(id: Long, nombre: String, tipo: String) = ServicioDisponible(
        Servicio(id = id, nombre = nombre, tipo = tipo, importe = Cup.ofPesos(500), creadoEn = t0),
        lineas = emptyList(),
        costo = null,
    )

    private val servicios = listOf(
        servicio(1, "Corte de cabello", "Belleza"),
        servicio(2, "Entrega a domicilio", "Entrega"),
    )

    private fun estado(
        seleccion: Set<Long> = emptySet(),
        hoja: HojaServicios? = null,
    ) = ServiciosUiState(
        vista = EstadoCarga.Exito(VistaServicios(servicios, servicios.size, listOf("Belleza", "Entrega"))),
        seleccion = seleccion,
        hoja = hoja,
        elementosFijados = servicios.filter { it.servicio.id in seleccion },
    )

    private fun mostrar(state: ServiciosUiState, acciones: AccionesServicios = AccionesServicios()) {
        rule.setContent { SpviTheme(darkTheme = false) { ServiciosContent(state, acciones) } }
    }

    @Test fun serviciosSeleccionadosSeFijanAntesDelBuscadorYNoSeRepiten() {
        mostrar(estado(seleccion = setOf(1)))
        rule.onNodeWithTag(ServiciosTags.FIJADOS).assertIsDisplayed()
        rule.onNodeWithTag(ServiciosTags.fila(1)).assertIsDisplayed()
        rule.onAllNodesWithTag(ServiciosTags.fila(1)).assertCountEquals(1)
        val arriba = rule.onNodeWithTag(ServiciosTags.FIJADOS).fetchSemanticsNode().boundsInRoot.top
        val buscador = rule.onNodeWithTag(ServiciosTags.BUSCAR).fetchSemanticsNode().boundsInRoot.top
        assertTrue("la selección debe quedar sobre el buscador", arriba < buscador)
    }

    @Test fun filtroDeServiciosUsaCombobox() {
        var aplicado: FiltroServicios? = null
        mostrar(estado(hoja = HojaServicios.FILTRO), AccionesServicios(onFiltro = { aplicado = it }))
        rule.onNodeWithTag(ServiciosTags.FILTRO_TIPO).performClick()
        rule.onNodeWithTag(ServiciosTags.tipoOpcion("Belleza")).performClick()
        rule.onNodeWithTag(ServiciosTags.FILTRO_APLICAR).performClick()
        assertEquals(FiltroServicios(tipo = "Belleza"), aplicado)
    }
}
