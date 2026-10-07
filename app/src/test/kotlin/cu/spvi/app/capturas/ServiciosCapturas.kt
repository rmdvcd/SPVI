package cu.spvi.app.capturas

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import cu.spvi.app.inicio.EstadoCarga
import cu.spvi.app.producto.LineaRecetaForm
import cu.spvi.app.servicios.AccionesServicioForm
import cu.spvi.app.servicios.AccionesServicios
import cu.spvi.app.servicios.ServicioForm
import cu.spvi.app.servicios.ServicioFormContent
import cu.spvi.app.servicios.ServicioFormUiState
import cu.spvi.app.servicios.ServiciosContent
import cu.spvi.app.servicios.ServiciosUiState
import cu.spvi.core.money.Cup
import cu.spvi.core.quantity.Cantidad
import cu.spvi.domain.model.LineaServicio
import cu.spvi.domain.model.Servicio
import cu.spvi.domain.model.ServicioDisponible
import cu.spvi.domain.model.VistaServicios
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** P29: Servicios (lista, vacío, modo venta, ficha) y su formulario. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = Captura.TELEFONO, application = Application::class)
class ServiciosCapturas {
    @get:Rule val rule = createComposeRule()

    private val t0 = Captura.AHORA.minusSeconds(10L * 86_400)
    private fun s(id: Long, nombre: String, tipo: String, pesos: Long) =
        Servicio(id = id, nombre = nombre, tipo = tipo, importe = Cup.ofPesos(pesos), creadoEn = t0)

    private val items = listOf(
        ServicioDisponible(s(1, "Corte de pelo", "Barbería", 300), emptyList(), null),
        ServicioDisponible(
            s(2, "Lavado y peinado", "Barbería", 450),
            listOf(LineaServicio(7, "Champú", "ml", Cantidad.enteras(30), Cantidad.enteras(900))),
            Cup.ofPesos(60),
        ),
        ServicioDisponible(s(3, "Entrega a domicilio", "Mensajería", 150), emptyList(), null),
    )
    private val lista = ServiciosUiState(vista = EstadoCarga.Exito(VistaServicios(items, 3, listOf("Barbería", "Mensajería"))))

    private fun servicios(id: String, st: ServiciosUiState) = rule.capturar(id) { ServiciosContent(st, AccionesServicios()) }

    @Test fun lista() = servicios("09a_servicios_lista", lista)
    @Test fun vacio() = servicios("09b_servicios_vacio", ServiciosUiState(vista = EstadoCarga.Vacio(VistaServicios(emptyList(), 0, emptyList()))))
    @Test fun modoVenta() = servicios("09c_servicios_venta", lista.copy(modoVenta = true, seleccion = setOf(1L)))
    @Test fun ficha() = servicios("09d_servicios_ficha", lista.copy(ficha = items[1]))

    @Test fun formulario() = rule.capturar("09e_servicio_form") {
        ServicioFormContent(
            ServicioFormUiState(
                form = ServicioForm(
                    nombre = "Lavado y peinado", tipo = "Barbería", importe = "450",
                    insumos = listOf(LineaRecetaForm(7, "Champú", "ml", Cup.ofPesos(2), "30")),
                ),
                tipos = listOf("Barbería", "Mensajería"),
            ),
            AccionesServicioForm(),
        )
    }
}
