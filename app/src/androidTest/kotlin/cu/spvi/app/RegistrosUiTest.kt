package cu.spvi.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.spvi.app.inicio.EstadoCarga
import cu.spvi.app.registros.AccionesRegistros
import cu.spvi.app.registros.FichaRegistro
import cu.spvi.app.registros.PestanaRegistros
import cu.spvi.app.registros.RegistrosContent
import cu.spvi.app.registros.RegistrosTags
import cu.spvi.app.registros.RegistrosUiState
import cu.spvi.app.registros.TextosRegistros
import cu.spvi.core.money.Cup
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.domain.model.DatosCliente
import cu.spvi.domain.model.DetalleVenta
import cu.spvi.domain.model.FiltroRegistros
import cu.spvi.domain.model.ItemMovimiento
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.model.MovimientoInventario
import cu.spvi.domain.model.PeriodoRegistro
import cu.spvi.domain.model.TipoEntidad
import cu.spvi.domain.model.TipoMovimiento
import cu.spvi.domain.model.Transaccion
import cu.spvi.domain.model.Venta
import cu.spvi.domain.model.VistaRegistro
import cu.spvi.domain.service.FormatoExport
import cu.spvi.domain.usecase.TipoRegistro
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrosUiTest {
    @get:Rule val rule = createComposeRule()

    private val zona = ZoneId.of("America/Havana")
    private val t0 = Instant.parse("2026-09-30T16:00:00Z")
    private val venta = Venta(
        id = 12, turnoId = 1, fecha = t0, metodoPago = MetodoPago.EFECTIVO,
        detalles = listOf(DetalleVenta(productoId = 1, nombre = "Refresco", categoria = "Bebidas", cantidad = 2,
            precioBase = Cup.ofPesos(725), precioUnitario = Cup.ofPesos(725), costoUnitario = Cup.ofPesos(400))),
    )
    private val transaccion = Transaccion(
        id = 3, ventaId = 12, fecha = t0, importe = Cup.ofPesos(1450), numero = "TM123",
        cliente = DatosCliente("Ana Díaz", "90020212345", "53000000"),
    )
    private val mov = ItemMovimiento(
        MovimientoInventario(id = 5, fecha = t0, tipo = TipoMovimiento.CONSUMO, entidad = TipoEntidad.INSUMO, entidadId = 2,
            nombre = "Harina", delta = -500, existenciaResultante = 9500), "kg",
    )

    private fun ventas(filtro: FiltroRegistros = FiltroRegistros(), ficha: FichaRegistro? = null, hoja: Boolean = false) = RegistrosUiState(
        filtros = mapOf(TipoRegistro.VENTAS to filtro), vista = EstadoCarga.Exito(VistaRegistro.Ventas(listOf(venta))), ficha = ficha, hojaFiltro = hoja,
    )

    private fun pantalla(state: RegistrosUiState, acciones: AccionesRegistros = AccionesRegistros()) =
        rule.setContent { SpviTheme(darkTheme = false) { RegistrosContent(state, EstadoCarga.Vacio(), acciones, zona = zona) } }

    @Test fun ventasConResumenYTapAbreLaVentana() {
        var abierta: Long? = null
        pantalla(ventas(), AccionesRegistros(onAbrirVenta = { abierta = it }))
        rule.onNodeWithText("30/09/2026 12:00").assertIsDisplayed() // identificada por fecha, no por número
        rule.onNodeWithTag(RegistrosTags.RESUMEN).assertIsDisplayed()
        rule.onNodeWithText("1 venta · 1,450.00 CUP").assertIsDisplayed()
        rule.onNodeWithTag(RegistrosTags.venta(12)).performClick()
        assertEquals(12L, abierta)
    }

    @Test fun progresoDeBusquedaEsAccesibleYConservaLaTabla() {
        pantalla(ventas().copy(buscando = true))
        rule.onNodeWithTag(RegistrosTags.BUSCANDO).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(TextosRegistros.BUSCANDO).assertCountEquals(1)
        rule.onNodeWithTag(RegistrosTags.LISTA).assertIsDisplayed()
    }

    @Test fun pestanasBuscadorFiltroYCompartir() {
        val hechos = mutableListOf<String>()
        pantalla(
            ventas(),
            AccionesRegistros(
                onPestana = { hechos += it.name }, onBuscar = { hechos += "buscar:$it" },
                onAbrirFiltro = { hechos += "filtro" }, onAbrirExportar = { hechos += "exportar" },
            ),
        )
        rule.onNodeWithTag(RegistrosTags.pestana(PestanaRegistros.MOVIMIENTOS)).performClick()
        rule.onNodeWithTag(RegistrosTags.BUSCAR).performTextInput("pan")
        rule.onNodeWithTag(RegistrosTags.FILTRO).performClick()
        rule.onNodeWithTag(RegistrosTags.EXPORTAR).assertIsEnabled().performClick()
        rule.onNodeWithTag(RegistrosTags.COMPARTIR).assertDoesNotExist() // 0.26.0: sin «Compartir como texto»
        assertEquals(listOf("MOVIMIENTOS", "buscar:pan", "filtro", "exportar"), hechos)
    }

    @Test fun sinDatosNoSeComparte() {
        pantalla(RegistrosUiState(vista = EstadoCarga.Vacio(VistaRegistro.Ventas(emptyList()))))
        rule.onNodeWithText(TextosRegistros.vacioTitulo(TipoRegistro.VENTAS)).assertIsDisplayed()
        rule.onNodeWithTag(RegistrosTags.EXPORTAR).assertIsNotEnabled()
    }

    @Test fun sinResultadosOfreceQuitarFiltros() {
        var todo: Boolean? = null
        val state = RegistrosUiState(
            filtros = mapOf(TipoRegistro.VENTAS to FiltroRegistros(texto = "zzz")),
            vista = EstadoCarga.Exito(VistaRegistro.Ventas(emptyList())),
        )
        pantalla(state, AccionesRegistros(onQuitarFiltros = { todo = it }))
        rule.onNodeWithTag(RegistrosTags.QUITAR_FILTROS).performClick()
        assertEquals(true, todo)
    }

    @Test fun ventanaDeVentaMuestraCamposSinCompartirTexto() {
        pantalla(ventas(ficha = FichaRegistro.DeVenta(venta)))
        rule.onNodeWithTag(RegistrosTags.FICHA).assertExists()
        rule.onNodeWithText("Ganancia").assertExists() // la ventana es interna
        rule.onAllNodesWithContentDescription("Compartir como texto").assertCountEquals(0)
    }

    @Test fun ventanaDeTransferenciaLlevaASuVenta() {
        var ver = false
        val state = RegistrosUiState(
            pestana = PestanaRegistros.TRANSFERENCIAS,
            vista = EstadoCarga.Exito(VistaRegistro.Transferencias(listOf(transaccion))),
            ficha = FichaRegistro.DeTransferencia(transaccion),
        )
        pantalla(state, AccionesRegistros(onVerVenta = { ver = true }))
        rule.onNodeWithText("90020212345").assertExists() // completo en la ventana; oculto solo al compartir
        rule.onNodeWithTag(RegistrosTags.FICHA_VER_VENTA).performClick()
        assertTrue(ver)
    }

    @Test fun movimientoConUnidadYEstadoParaTalkBack() {
        var abierto: Long? = null
        val state = RegistrosUiState(pestana = PestanaRegistros.MOVIMIENTOS, vista = EstadoCarga.Exito(VistaRegistro.Movimientos(listOf(mov))))
        pantalla(state, AccionesRegistros(onAbrirMovimiento = { abierto = it }))
        rule.onNodeWithText("-0.5 kg", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(RegistrosTags.movimiento(5)).performClick()
        assertEquals(5L, abierto)
    }

    @Test fun hojaFiltroValidaImportesAntesDeAplicar() {
        var aplicado: FiltroRegistros? = null
        pantalla(ventas(hoja = true), AccionesRegistros(onAplicarFiltro = { aplicado = it }))
        rule.onNodeWithTag(RegistrosTags.periodo(PeriodoRegistro.HOY)).performClick()
        rule.onNodeWithTag(RegistrosTags.FILTRO_MIN).performTextReplacement("abc")
        rule.onNodeWithTag(RegistrosTags.FILTRO_APLICAR).performClick()
        rule.onNodeWithText(TextosRegistros.IMPORTE_INVALIDO).assertExists()
        assertEquals(null, aplicado)
        rule.onNodeWithTag(RegistrosTags.FILTRO_MIN).performTextReplacement("100")
        rule.onNodeWithTag(RegistrosTags.FILTRO_APLICAR).performClick()
        assertEquals(FiltroRegistros(periodo = PeriodoRegistro.HOY, importeMin = Cup.ofPesos(100)), aplicado)
    }

    @Test fun hojaFiltroDeMovimientosSinImporte() {
        val state = RegistrosUiState(pestana = PestanaRegistros.MOVIMIENTOS, vista = EstadoCarga.Exito(VistaRegistro.Movimientos(listOf(mov))), hojaFiltro = true)
        pantalla(state)
        rule.onNodeWithTag(RegistrosTags.FILTRO_MIN).assertDoesNotExist()
        rule.onNodeWithText(TextosRegistros.SIN_IMPORTE_EN_MOVIMIENTOS).assertExists()
    }

    @Test fun enTurnosNoHayBuscadorNiCompartir() {
        pantalla(RegistrosUiState(pestana = PestanaRegistros.TURNOS))
        rule.onNodeWithTag(RegistrosTags.BUSCAR).assertDoesNotExist()
        rule.onNodeWithTag(RegistrosTags.COMPARTIR).assertDoesNotExist()
    }

    // ---------- Exportar PDF / Excel (Prompt 14)

    @Test fun exportarAbreLaHojaYCadaFormatoSeEnviaOSeGuarda() {
        var abrir = 0
        val enviados = mutableListOf<FormatoExport>(); val guardados = mutableListOf<FormatoExport>()
        pantalla(
            ventas().copy(hojaExportar = true),
            AccionesRegistros(onAbrirExportar = { abrir++ }, onEnviar = { enviados += it }, onGuardar = { guardados += it }),
        )
        rule.onNodeWithTag(RegistrosTags.EXPORTAR).assertIsEnabled().performClick()
        assertEquals(1, abrir)
        rule.onNodeWithText(TextosRegistros.tituloExportar(TipoRegistro.VENTAS)).assertIsDisplayed()
        rule.onNodeWithText(TextosRegistros.alcanceExportar(TipoRegistro.VENTAS, 1, null)).assertIsDisplayed()
        rule.onNodeWithTag(RegistrosTags.enviar(FormatoExport.XLSX)).performClick()
        rule.onNodeWithTag(RegistrosTags.guardar(FormatoExport.PDF)).performClick()
        assertEquals(listOf(FormatoExport.XLSX), enviados)
        assertEquals(listOf(FormatoExport.PDF), guardados)
    }

    @Test fun exportandoMuestraProgresoYDeshabilitaExportar() {
        pantalla(ventas().copy(exportando = true))
        rule.onNodeWithTag(RegistrosTags.EXPORTANDO).assertExists()
        rule.onNodeWithTag(RegistrosTags.EXPORTAR).assertIsNotEnabled()
    }

    @Test fun exportarTransferenciasAvisaDeDatosDeClientes() {
        pantalla(
            RegistrosUiState(
                pestana = PestanaRegistros.TRANSFERENCIAS, hojaExportar = true,
                vista = EstadoCarga.Exito(VistaRegistro.Transferencias(listOf(transaccion))),
            ),
        )
        rule.onNodeWithText(TextosRegistros.AVISO_DATOS_CLIENTES).assertIsDisplayed()
    }
}
