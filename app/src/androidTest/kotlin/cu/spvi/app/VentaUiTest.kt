package cu.spvi.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.spvi.app.inicio.TipoVenta
import cu.spvi.app.venta.AccionesVenta
import cu.spvi.app.venta.CampoCliente
import cu.spvi.app.venta.FormCliente
import cu.spvi.app.venta.LineaCarrito
import cu.spvi.app.venta.PasoVenta
import cu.spvi.app.venta.TextosVenta
import cu.spvi.app.venta.VentaContent
import cu.spvi.app.venta.VentaTags
import cu.spvi.app.venta.VentaUiState
import cu.spvi.core.money.Cup
import cu.spvi.core.money.Money
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.domain.model.DetalleVenta
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.model.Producto
import cu.spvi.domain.model.Turno
import cu.spvi.domain.service.Cotizacion
import cu.spvi.domain.service.PagoQr
import cu.spvi.domain.usecase.ObservarPermisoVenta.Permiso
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Prompt 13 sin Hilt: carrito, comprobante (iconos Confirmar/Cancelar), QR y datos del cliente. */
@RunWith(AndroidJUnit4::class)
class VentaUiTest {
    @get:Rule val rule = createComposeRule()
    private val zona = ZoneId.of("America/Havana")
    private val t0 = Instant.parse("2026-09-30T12:30:00Z")
    private val abierto = Permiso.Permitido(Turno(1, t0, abiertoPor = "Ana"))
    private val refresco = Producto(
        id = 1, categoria = "Bebidas", nombre = "Refresco", precioCosto = Cup.ofPesos(60), precioVenta = Cup.ofPesos(100),
        cantidad = 5, creadoEn = t0,
    )
    private val detalle = DetalleVenta(
        productoId = 1, nombre = "Refresco", categoria = "Bebidas", cantidad = 2,
        precioBase = Cup.ofPesos(100), precioUnitario = Cup.ofPesos(100), costoUnitario = Cup.ofPesos(60),
    )

    @Test fun carritoVacioOfreceElegirProductos() {
        var elegir = 0
        rule.setContent { SpviTheme { VentaContent(TipoVenta.VENTA, VentaUiState(abierto), AccionesVenta(onElegir = { elegir++ }), zona = zona) } }
        rule.onNodeWithText(TextosVenta.CARRITO_VACIO_TITULO).assertIsDisplayed()
        rule.onNodeWithContentDescription(TextosVenta.ELEGIR).performClick()
        assertEquals(1, elegir)
    }

    @Test fun carritoAjustaCantidadesYMetodo() {
        val cambios = mutableListOf<Pair<Long, Long>>(); var metodo: MetodoPago? = null
        val s = VentaUiState(abierto, lineas = listOf(LineaCarrito(refresco, 5)))
        rule.setContent {
            SpviTheme {
                VentaContent(TipoVenta.VENTA, s, AccionesVenta(onCantidad = { id, n -> cambios += id to n }, onMetodo = { metodo = it }), zona = zona)
            }
        }
        rule.onNodeWithTag(VentaTags.mas(1)).assertIsNotEnabled()          // 5 de 5: no se puede más
        rule.onNodeWithTag(VentaTags.menos(1)).performClick()
        assertEquals(listOf(1L to 4L), cambios)
        rule.onNodeWithTag(VentaTags.metodo(MetodoPago.TRANSFERENCIA)).performClick()
        assertEquals(MetodoPago.TRANSFERENCIA, metodo)
        rule.onNodeWithText("500.00 CUP").assertExists()
    }

    @Test fun comprobanteConConfirmarYCancelar() {
        var confirmar = 0; var cancelar = 0
        val s = VentaUiState(
            abierto, lineas = listOf(LineaCarrito(refresco, 2)), paso = PasoVenta.COMPROBANTE,
            cotizacion = Cotizacion(MetodoPago.EFECTIVO, listOf(detalle), emptyMap()),
        )
        rule.setContent {
            SpviTheme { VentaContent(TipoVenta.VENTA, s, AccionesVenta(onConfirmarEfectivo = { confirmar++ }, onDescartar = { cancelar++ }), zona = zona) }
        }
        rule.onNodeWithText(TextosVenta.linea(2, Cup.ofPesos(100))).assertIsDisplayed()
        rule.onNodeWithTag(VentaTags.CONFIRMAR).performClick()
        rule.onNodeWithTag(VentaTags.CANCELAR).performClick()
        assertEquals(1, confirmar); assertEquals(1, cancelar)
    }

    @Test fun turnoCerradoEnElComprobanteMuestraElBloqueo() {
        val s = VentaUiState(
            Permiso.SinTurno, lineas = listOf(LineaCarrito(refresco, 2)), paso = PasoVenta.COMPROBANTE,
            cotizacion = Cotizacion(MetodoPago.EFECTIVO, listOf(detalle), emptyMap()),
        )
        rule.setContent { SpviTheme { VentaContent(TipoVenta.VENTA, s, AccionesVenta(), zona = zona) } }
        rule.onNodeWithTag(VentaTags.BLOQUEO).assertIsDisplayed()
        rule.onNodeWithTag(VentaTags.CONFIRMAR).assertDoesNotExist()
    }

    @Test fun qrMuestraElImporteYPasaADatos() {
        var recibido = 0
        val s = VentaUiState(
            abierto, lineas = listOf(LineaCarrito(refresco, 2)), metodo = MetodoPago.TRANSFERENCIA, paso = PasoVenta.QR,
            cotizacion = Cotizacion(MetodoPago.TRANSFERENCIA, listOf(detalle), emptyMap()),
            qr = PagoQr.Resultado.Ok("TRANSFERMOVIL_ETECSA,TRANSFERENCIA,9248129970876454,51815604,", "9248129970876454", "51815604"),
        )
        rule.setContent { SpviTheme { VentaContent(TipoVenta.VENTA, s, AccionesVenta(onPagoRecibido = { recibido++ }), zona = zona) } }
        rule.onNodeWithTag(VentaTags.QR).assertIsDisplayed()
        // P28: el importe va una sola vez, centrado arriba, con la etiqueta «Importe a transferir».
        rule.onNodeWithText(TextosVenta.IMPORTE).assertIsDisplayed()
        rule.onNodeWithText(Money.format(Cup.ofPesos(200))).assertIsDisplayed()
        rule.onNodeWithTag(VentaTags.PAGO_RECIBIDO).performClick()
        assertEquals(1, recibido)
    }

    @Test fun datosDelClienteMuestranErroresYPegarSms() {
        var pegar = 0
        val s = VentaUiState(
            abierto, lineas = listOf(LineaCarrito(refresco, 2)), metodo = MetodoPago.TRANSFERENCIA, paso = PasoVenta.CLIENTE,
            cliente = FormCliente(intento = true),
        )
        rule.setContent { SpviTheme { VentaContent(TipoVenta.VENTA, s, AccionesVenta(onPegarSms = { pegar++ }), zona = zona) } }
        rule.onNodeWithTag(VentaTags.campo(CampoCliente.NUMERO)).assertExists()
        rule.onNodeWithText("Escribe o pega el nº de transacción.").assertExists()
        rule.onNodeWithTag(VentaTags.PEGAR_SMS).performClick()
        assertEquals(1, pegar)
    }
}
