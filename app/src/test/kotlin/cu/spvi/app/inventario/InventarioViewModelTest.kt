package cu.spvi.app.inventario

import androidx.lifecycle.SavedStateHandle
import cu.spvi.app.FakeArchivos
import cu.spvi.app.FakeExportador
import cu.spvi.app.FakeFotos
import cu.spvi.app.FakeRenderer
import cu.spvi.app.FakeTabla
import cu.spvi.app.InsRepo
import cu.spvi.app.PrefRepo
import cu.spvi.app.ProdRepo
import cu.spvi.app.RelojFijo
import cu.spvi.app.inicio.EstadoCarga
import cu.spvi.app.navigation.Route
import cu.spvi.app.prod
import cu.spvi.domain.model.FiltroInventario
import cu.spvi.domain.model.TipoAlerta
import cu.spvi.domain.model.TipoArticulo
import cu.spvi.domain.service.FormatoExport
import cu.spvi.domain.usecase.EliminarProductos
import cu.spvi.domain.usecase.ExportarFicha
import cu.spvi.domain.usecase.ExportarInventario
import cu.spvi.domain.usecase.LimpiarFotos
import cu.spvi.domain.usecase.ObservarInventario
import cu.spvi.domain.usecase.ObtenerFichaProducto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InventarioViewModelTest {
    private val productos = ProdRepo()
    private val prefs = PrefRepo()
    private val reloj = RelojFijo()
    private val archivos = FakeArchivos()
    private val exportador = FakeExportador()
    private val renderer = FakeRenderer(archivos)
    private val tablaPng = FakeTabla(archivos)
    private val eventos = mutableListOf<EventoInventario>()
    private val insumosInv = cu.spvi.app.InsRepo()

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private fun TestScope.vm(alerta: String? = null): InventarioViewModel {
        val vm = InventarioViewModel(
            saved = SavedStateHandle(if (alerta != null) mapOf("alerta" to alerta) else emptyMap()),
            observarInventario = ObservarInventario(productos, insumosInv, prefs, reloj, Dispatchers.Unconfined),
            productosRepo = productos,
            obtenerFicha = ObtenerFichaProducto(productos, InsRepo(), prefs, reloj),
            eliminarProductos = EliminarProductos(productos),
            insumosRepo = insumosInv,
            obtenerFichaInsumo = cu.spvi.domain.usecase.ObtenerFichaInsumo(insumosInv, productos, prefs, cu.spvi.app.ServRepo()),
            eliminarInsumos = cu.spvi.domain.usecase.EliminarInsumos(insumosInv),
            exportarInventario = ExportarInventario(exportador),
            exportarFicha = ExportarFicha(exportador),
            tarjetas = renderer,
            imagenTabla = tablaPng,
            archivos = archivos,
            clock = reloj,
            limpiarFotos = LimpiarFotos(productos, FakeFotos(), cu.spvi.app.ServRepo()),
        )
        // 0.30.0 (F1): en la app el buscador espera 250 ms; los tests no quieren depender del reloj virtual.
        vm.debounceBusqueda = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { eventos += it } }
        return vm
    }

    private fun cargar() {
        productos.items.value = listOf(
            prod(1, "Refresco de cola", cantidad = 20),
            prod(2, "Galletas", cantidad = 3, categoria = "Dulces"),
            prod(3, "Café molido", cantidad = 0, categoria = "Víveres"),
        )
    }

    @Test fun vacioSinProductosYOrdenCronologico() = runTest {
        val vm = vm()
        assertTrue(vm.state.value.vista is EstadoCarga.Vacio)
        cargar()
        assertEquals(listOf(1L, 2L, 3L), vm.state.value.items.map { it.producto.id }) // más reciente primero
        assertEquals(3, vm.state.value.total)
    }

    @Test fun alertaDeInicioPreseleccionaElFiltro() = runTest {
        cargar()
        // 0.21.0 (C1): el Café a 0 está «Sin existencia».
        val vm = vm(alerta = TipoAlerta.SIN_EXISTENCIA.name)
        assertEquals(TipoAlerta.SIN_EXISTENCIA, vm.state.value.filtro.alerta)
        assertEquals(listOf(3L), vm.state.value.items.map { it.producto.id })
        vm.quitarFiltros()
        assertEquals(3, vm.state.value.items.size)
    }

    @Test fun buscadorYFiltroDeCategoria() = runTest {
        cargar()
        val vm = vm()
        vm.buscar("CAFE")
        assertEquals(listOf(3L), vm.state.value.items.map { it.producto.id }) // sin tildes ni mayúsculas
        vm.buscar("")
        vm.aplicarFiltro(FiltroInventario(categoria = "Dulces"))
        assertEquals(listOf(2L), vm.state.value.items.map { it.producto.id })
        assertNull(vm.state.value.hoja)
        vm.buscar("zzz")
        assertTrue(vm.state.value.vista is EstadoCarga.Exito) // hay productos: "Sin resultados", no "vacío"
        assertTrue(vm.state.value.items.isEmpty())
    }

    /** 0.30.0 (F1): las pulsaciones se agrupan y la lista actual permanece visible hasta recibir el nuevo resultado. */
    @Test fun elBuscadorAgrupaLasPulsacionesYMantieneLaListaDuranteLaConsulta() = runTest {
        cargar()
        val vm = vm()
        vm.debounceBusqueda = 250 // el retardo real de la app
        var consultas = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.map { it.buscando }.distinctUntilChanged().collect { buscando -> if (buscando) consultas++ }
        }
        val antesDeEscribir = consultas
        listOf("r", "re", "ref", "refr", "refre", "refres", "refresc", "refresco").forEach { vm.buscar(it) }
        runCurrent()
        assertEquals("las pulsaciones solo activan una búsqueda", antesDeEscribir + 1, consultas)
        assertTrue(vm.state.value.buscando)
        assertEquals("la lista anterior se conserva durante el debounce", listOf(1L, 2L, 3L), vm.state.value.items.map { it.producto.id })
        advanceTimeBy(300)
        runCurrent()
        assertEquals("una consulta para las 8 pulsaciones", antesDeEscribir + 1, consultas)
        assertFalse(vm.state.value.buscando)
        assertEquals(listOf(1L), vm.state.value.items.map { it.producto.id })
    }

    @Test fun seleccionSobreviveALaBusquedaYMarcarTodoAlterna() = runTest {
        cargar()
        val vm = vm()
        vm.alternar(1)
        vm.buscar("galle")
        assertEquals(setOf(1L), vm.state.value.seleccion)
        vm.seleccionarTodo()
        assertEquals(setOf(1L, 2L), vm.state.value.seleccion)
        assertTrue(vm.state.value.todosVisiblesSeleccionados)
        vm.seleccionarTodo() // desmarca solo lo visible
        assertEquals(setOf(1L), vm.state.value.seleccion)
        vm.limpiarSeleccion()
        assertTrue(vm.state.value.seleccion.isEmpty())
    }

    @Test fun exportarPdfSinSeleccionUsaLoVisible() = runTest {
        cargar()
        val vm = vm()
        vm.buscar("a") // refresco de cola, galletas, café molido → los 3
        vm.aplicarFiltro(FiltroInventario(categoria = "Víveres"))
        vm.exportar(FormatoSalida.PDF)
        val e = eventos.filterIsInstance<EventoInventario.Compartir>().single()
        assertEquals(FormatoExport.PDF.mime, e.mime)
        assertEquals("SPVI_inventario_2026-09-30.pdf", e.archivos.single().name)
        assertEquals("PDF", e.archivos.single().readText())
        assertEquals(1, exportador.llamadas.single().first.single().filas.size)
        assertEquals(false, vm.state.value.trabajando)
    }

    @Test fun exportarExcelConSeleccionSoloLaSeleccion() = runTest {
        cargar()
        val vm = vm()
        vm.alternar(1); vm.alternar(3)
        vm.buscar("galle") // la selección no se ve, pero se exporta ella
        vm.exportar(FormatoSalida.EXCEL)
        val (tablas, formato) = exportador.llamadas.single()
        assertEquals(FormatoExport.XLSX, formato)
        assertEquals(2, tablas.single().filas.size)
        assertTrue(eventos.filterIsInstance<EventoInventario.Compartir>().single().archivos.single().name.endsWith(".xlsx"))
    }

    @Test fun tarjetasImagenYTextoParaClientes() = runTest {
        cargar()
        val vm = vm()
        vm.alternar(2)
        vm.exportar(FormatoSalida.TARJETAS)
        assertEquals(listOf(2L), renderer.renderizados.single().map { it.id })
        assertEquals("image/png", eventos.filterIsInstance<EventoInventario.Compartir>().single().mime)
        // Imagen = lista de precios en PNG (Prompt 14): sin costo ni existencias.
        eventos.clear()
        vm.exportar(FormatoSalida.IMAGEN)
        val t0 = tablaPng.renderizadas.single()
        assertEquals(listOf("Categoría", "Producto", "Precio"), t0.columnas)
        assertEquals(1, t0.filas.size)
        val png = eventos.filterIsInstance<EventoInventario.Compartir>().single()
        assertEquals("image/png", png.mime)
        assertEquals("SPVI_precios_2026-09-30_1.png", png.archivos.single().name)
        assertEquals(1, renderer.renderizados.size) // la imagen no usa el renderizador de tarjetas
    }

    @Test fun errorAlExportarSeAvisaSinDetalles() = runTest {
        cargar()
        exportador.fallar = true
        val vm = vm()
        vm.exportar(FormatoSalida.PDF)
        assertEquals(TextosInventario.ERROR_EXPORTAR, (eventos.single() as EventoInventario.Mensaje).texto)
    }

    @Test fun guardarEnElDispositivoPideDestinoYEscribe() = runTest {
        cargar()
        val vm = vm()
        vm.pedirGuardar(FormatoSalida.IMAGEN) // solo PDF/Excel se guardan
        assertTrue(eventos.isEmpty())
        vm.pedirGuardar(FormatoSalida.PDF)
        val g = eventos.single() as EventoInventario.GuardarComo
        assertEquals("application/pdf", g.mime)
        assertTrue(g.nombre.endsWith(".pdf"))
        vm.guardarEn("content://destino/1")
        assertEquals("PDF", archivos.destinos.getValue("content://destino/1").toString())
        assertEquals(TextosInventario.GUARDADO_EN_DISPOSITIVO, (eventos.last() as EventoInventario.Mensaje).texto)
        // Cancelar el selector no hace nada
        eventos.clear()
        vm.pedirGuardar(FormatoSalida.EXCEL); vm.guardarEn(null)
        assertEquals(1, eventos.size)
        vm.guardarEn("content://tarde") // sin petición pendiente: se ignora
        assertEquals(1, archivos.destinos.size)
    }

    @Test fun eliminarSeleccionConConfirmacionYPoda() = runTest {
        cargar()
        productos.fallarEliminar = setOf(3)
        val vm = vm()
        vm.alternar(1); vm.alternar(3)
        vm.pedirEliminarSeleccion()
        assertEquals(ConfirmarEliminar.Seleccion(2), vm.state.value.confirmar)
        vm.cancelarEliminar()
        assertEquals(3, productos.items.value.size)
        vm.pedirEliminarSeleccion(); vm.confirmarEliminar()
        assertEquals(listOf(2L, 3L), productos.items.value.map { it.id })
        assertTrue(vm.state.value.seleccion.isEmpty())
        assertEquals(TextosInventario.eliminados(1, 1), (eventos.last() as EventoInventario.Mensaje).texto)
    }

    @Test fun seleccionSePodaSiElProductoDesaparece() = runTest {
        cargar()
        val vm = vm()
        vm.alternar(2)
        productos.items.value = productos.items.value.filterNot { it.id == 2L }
        assertTrue(vm.state.value.seleccion.isEmpty())
    }

    @Test fun fichaEditarCompartirYEliminar() = runTest {
        cargar()
        val vm = vm()
        vm.abrirFicha(2)
        assertEquals("Galletas", vm.state.value.ficha?.producto?.nombre)
        vm.compartirFicha(FormatoSalida.PDF)
        val c = eventos.filterIsInstance<EventoInventario.Compartir>().single()
        assertEquals("Ficha Galletas.pdf", c.archivos.single().name)
        vm.pedirEliminarFicha()
        assertEquals(ConfirmarEliminar.Uno(2, "Galletas"), vm.state.value.confirmar)
        vm.confirmarEliminar()
        assertNull(vm.state.value.ficha)
        assertEquals(TextosInventario.ELIMINADO, (eventos.last() as EventoInventario.Mensaje).texto)
        vm.abrirFicha(2)
        assertEquals(TextosInventario.FICHA_NO_DISPONIBLE, (eventos.last() as EventoInventario.Mensaje).texto)
    }

    @Test fun agregarYEditarNavegan() = runTest {
        cargar()
        val vm = vm()
        vm.agregar()
        vm.abrirFicha(1); vm.editar(1)
        assertNull(vm.state.value.ficha)
        assertEquals(
            listOf<Route>(Route.ProductoForm(), Route.ProductoForm(id = 1)),
            eventos.filterIsInstance<EventoInventario.Navegar>().map { it.route },
        )
        assertNotNull(vm)
    }

    @Test fun agregarConElFiltroInsumosAbreElFormularioDeInsumo() = runTest {
        cargar()
        val vm = vm()
        vm.aplicarFiltro(FiltroInventario(tipo = TipoArticulo.INSUMOS))
        vm.agregar()
        assertEquals(
            listOf<Route>(Route.InsumoForm()),
            eventos.filterIsInstance<EventoInventario.Navegar>().map { it.route },
        )
    }
}
