package cu.spvi.app.servicios

import androidx.lifecycle.SavedStateHandle
import cu.spvi.app.FakeFotos
import cu.spvi.app.InsRepo
import cu.spvi.app.RelojFijo
import cu.spvi.app.ServRepo
import cu.spvi.app.T0
import cu.spvi.app.inicio.TipoTop
import cu.spvi.app.inicio.valorTop
import cu.spvi.app.inventario.SIN_INSUMOS
import cu.spvi.app.notificacion.AvisoTurno
import cu.spvi.app.producto.LineaRecetaForm
import cu.spvi.app.venta.Carrito
import cu.spvi.app.venta.LineaCarrito
import cu.spvi.app.venta.TextosVenta
import cu.spvi.core.money.Cup
import cu.spvi.core.quantity.Cantidad
import cu.spvi.domain.model.ClaseArticulo
import cu.spvi.domain.model.IdArticulo
import cu.spvi.domain.model.Insumo
import cu.spvi.domain.model.LineaServicio
import cu.spvi.domain.model.RecetaLinea
import cu.spvi.domain.model.Servicio
import cu.spvi.domain.model.ServicioDisponible
import cu.spvi.domain.model.TopItem
import cu.spvi.domain.model.comoProducto
import cu.spvi.domain.service.LineaSolicitada
import cu.spvi.domain.usecase.GuardarServicio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** P29: lógica de Servicios en la app (formulario, ficha, carrito de servicios e insumos, Inicio, aviso). */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiciosAppTest {

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private val corte = Servicio(id = 7, nombre = "Corte", tipo = "Peluquería", importe = Cup.ofPesos(250), creadoEn = T0)
    private fun disp(alcanza: Long?, lineas: Boolean = alcanza != null) = ServicioDisponible(
        corte,
        if (lineas) listOf(LineaServicio(1, "Gel", "ml", Cantidad.enteras(10), Cantidad.enteras((alcanza ?: 0) * 10))) else emptyList(),
        Cup.ofPesos(20),
    )

    @Test fun formularioObligatoriosYConversion() {
        val vacio = ServicioFormLogic.validar(ServicioForm())
        assertEquals(setOf(CamposServicio.NOMBRE, CamposServicio.TIPO, CamposServicio.IMPORTE), vacio.keys)
        val f = ServicioForm(nombre = "Corte", tipo = "Peluquería", importe = "250", insumos = listOf(LineaRecetaForm(1, "Gel", "ml", Cup.ofPesos(2), "10")))
        assertTrue(ServicioFormLogic.validar(f).isEmpty())
        val (s, lineas) = ServicioFormLogic.aServicio(f, T0)!!
        assertEquals(Cup.ofPesos(250), s.importe)
        assertEquals(listOf(RecetaLinea(1, Cantidad.enteras(10))), lineas)
        assertEquals(Cup.ofPesos(20), ServicioFormLogic.costo(f)) // 10 ml × 2 CUP
        assertEquals("El importe debe superar el costo de los insumos.", ServicioFormLogic.validar(f.copy(importe = "20"))[CamposServicio.IMPORTE])
        assertTrue(CamposServicio.INSUMOS in ServicioFormLogic.validar(f.copy(insumos = listOf(LineaRecetaForm(1, "Gel", "ml", Cup.ofPesos(2), "0")))))
        assertTrue(CamposServicio.IMPORTE in ServicioFormLogic.validar(f.copy(importe = "abc")))
    }

    @Test fun filaYFichaDelServicio() {
        assertEquals("Peluquería", ServiciosLogic.subtitulo(disp(alcanza = null)))
        assertEquals("Peluquería · $SIN_INSUMOS", ServiciosLogic.subtitulo(disp(alcanza = 0)))
        val campos = ServiciosLogic.campos(disp(alcanza = 3)).toMap()
        assertEquals("Alcanza para 3", campos["Alcanza"])
        assertEquals("10 ml por vez", campos["Gel"])
        assertEquals("Peluquería", ServiciosLogic.campos(disp(alcanza = null)).toMap()["Tipo"])
        assertFalse(ServiciosLogic.campos(disp(alcanza = null)).toMap().containsKey("Alcanza"))
    }

    @Test fun carritoDeServiciosSinTopeOConTopePorInsumos() {
        val p = Carrito.deServicio(disp(alcanza = null))
        assertEquals(corte.importe, p.precioVenta)
        val sinTope = Carrito.lineas(mapOf(7L to 2L), mapOf(7L to p), emptyMap(), servicios = true).single()
        assertTrue(sinTope.esServicio)
        assertEquals(Carrito.MAX_CANTIDAD, sinTope.maximo)
        assertFalse(TextosVenta.avisarExistencias(sinTope))
        val conTope = Carrito.lineas(mapOf(7L to 2L), mapOf(7L to p), mapOf(7L to 3L), servicios = true).single()
        assertEquals(3L, conTope.maximo)
        assertEquals("Alcanza para 3", TextosVenta.disponibles(conTope))
        assertEquals(listOf(LineaSolicitada(7, 2, ClaseArticulo.SERVICIO)), Carrito.solicitud(listOf(conTope)))
    }

    @Test fun insumoEnElCarritoViajaConSuIdReal() {
        val azucar = Insumo(id = 4, nombre = "Azúcar", precio = Cup.ofPesos(40), cantidad = Cantidad(2500), creadoEn = T0, precioVenta = Cup.ofPesos(60))
        val p = azucar.comoProducto()
        val l = Carrito.lineas(mapOf(p.id to 2L), mapOf(p.id to p)).single()
        assertEquals(ClaseArticulo.INSUMO, l.clase)
        assertEquals(2L, l.maximo) // 2.5 kg → 2 unidades enteras
        assertEquals(listOf(LineaSolicitada(4, 2, ClaseArticulo.INSUMO)), Carrito.solicitud(listOf(l)))
        assertEquals(IdArticulo.deInsumo(4), LineaCarrito(p, 1).producto.id)
    }

    @Test fun indicadoresDeServiciosEnInicioCuentanVeces() {
        val t = TopItem(7, "Corte", 1, Cup.ofPesos(250), Cup.ofPesos(230))
        assertEquals("1 vez", valorTop(TipoTop.SERVICIO_TOP, t))
        assertEquals("4 veces", valorTop(TipoTop.SERVICIO_MENOS, t.copy(unidades = 4)))
    }

    @Test fun avisoDelTurnoSinImportes() {
        assertEquals("Turno abierto desde las 9:30", AvisoTurno.texto("9:30"))
        assertFalse(AvisoTurno.texto("9:30").contains("CUP"))
    }

    @Test fun formularioGuardaElServicio() = runTest {
        val repo = ServRepo()
        val vm = ServicioFormViewModel(SavedStateHandle(), repo, InsRepo(), GuardarServicio(repo, InsRepo(), RelojFijo()), FakeFotos(), RelojFijo())
        val eventos = mutableListOf<EventoServicioForm>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { eventos += it } }
        vm.guardar()
        assertTrue(vm.state.value.intentado)
        assertEquals(3, vm.state.value.errores.size)
        assertTrue(repo.items.value.isEmpty())
        vm.nombre("Corte"); vm.tipo("Peluquería"); vm.importe("250")
        assertTrue(vm.state.value.sinGuardar)
        vm.guardar()
        assertEquals(EventoServicioForm.Guardado("Servicio guardado."), eventos.last())
        assertEquals("Corte", repo.items.value.values.single().nombre)
        assertNull(repo.items.value.values.single().descripcion)
    }
}
