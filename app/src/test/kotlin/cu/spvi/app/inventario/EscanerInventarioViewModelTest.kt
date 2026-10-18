package cu.spvi.app.inventario

import cu.spvi.app.InsRepo
import cu.spvi.app.ProdRepo
import cu.spvi.app.RelojFijo
import cu.spvi.app.prod
import cu.spvi.core.result.AppError
import cu.spvi.domain.model.Categorias
import cu.spvi.domain.usecase.GuardarInsumo
import cu.spvi.domain.usecase.GuardarProducto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Importación por lotes: nada se guarda si alguna fila no sirve, lo ya insertado se quita de la revisión
 * (un reintento no duplica) y la categoría vacía se ve como «Otros» (nunca en silencio).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EscanerInventarioViewModelTest {

    private val productos = ProdRepo()
    private val insumos = InsRepo()
    private val reloj = RelojFijo()

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private fun vm() = EscanerInventarioViewModel(
        guardarProducto = GuardarProducto(productos, insumos, reloj),
        guardarInsumo = GuardarInsumo(insumos, productos, reloj),
        clock = reloj,
    )

    private val camposProducto = listOf(
        CampoEscaneo.NOMBRE, CampoEscaneo.CATEGORIA, CampoEscaneo.CANTIDAD, CampoEscaneo.COSTO, CampoEscaneo.VENTA,
    )

    private fun tabla(vararg filas: List<String>) = TablaOcr(filas = filas.toList(), columnas = filas.first().size, estructura = true)

    private fun fila(nombre: String, cantidad: String = "10", costo: String = "5.00", venta: String = "7.00") =
        listOf(nombre, "Otros", cantidad, costo, venta)

    @Test fun laCategoriaVaciaQuedaVisibleComoOtros() {
        val vm = vm()
        val sinCategoria = listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD, CampoEscaneo.COSTO, CampoEscaneo.VENTA)
        vm.aplicarTabla(TipoEscaneo.PRODUCTO, sinCategoria, tabla(listOf("Refresco", "48", "100.00", "150.00")))
        assertEquals(Categorias.OTROS, vm.filas.single().valores[CampoEscaneo.CATEGORIA])
        vm.agregar()
        assertEquals(2, vm.filas.size)
        assertEquals("", vm.filas[1].valores[CampoEscaneo.NOMBRE])
        assertTrue(Categorias.PREDEFINIDAS.contains(Categorias.OTROS))
    }

    @Test fun cambiarElMapeoRearmaLasFilas() {
        val vm = vm()
        val campos = listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD)
        vm.aplicarTabla(TipoEscaneo.PRODUCTO, campos, tabla(listOf("48", "Refresco")))
        assertEquals("48", vm.filas.single().valores[CampoEscaneo.NOMBRE])
        vm.aplicarMapeo(listOf(CampoEscaneo.CANTIDAD, CampoEscaneo.NOMBRE))
        assertEquals("Refresco", vm.filas.single().valores[CampoEscaneo.NOMBRE])
        assertEquals("48", vm.filas.single().valores[CampoEscaneo.CANTIDAD])
    }

    @Test fun nadaSeInsertaSiUnaFilaNoSirve() {
        val vm = vm()
        vm.aplicarTabla(
            TipoEscaneo.PRODUCTO,
            camposProducto,
            tabla(fila("Sal"), fila("Refresco", venta = "1.00")),
        )
        var resumen: ResumenImportacion? = null
        vm.guardar { resumen = it }
        assertEquals(0, resumen!!.insertadas)
        assertEquals(ErrorEscaneo.ValorInvalido(2, CampoEscaneo.VENTA, AppError.Regla.RANGO), resumen!!.error)
        assertTrue(productos.items.value.isEmpty())
        assertEquals(2, vm.filas.size)
        assertEquals(1, vm.filaConError)
    }

    @Test fun loInsertadoSeQuitaDeLaRevisionYUnReintentoNoDuplica() {
        productos.items.value = listOf(prod(1, "Galletas"))
        val vm = vm()
        vm.aplicarTabla(
            TipoEscaneo.PRODUCTO,
            camposProducto,
            tabla(fila("Sal"), fila("Galletas"), fila("Pan")),
        )
        var resumen: ResumenImportacion? = null
        vm.guardar { resumen = it }
        assertEquals(1, resumen!!.insertadas)
        assertTrue(resumen!!.error is ErrorEscaneo.Rechazado)
        assertEquals("Galletas", (resumen!!.error as ErrorEscaneo.Rechazado).nombre)
        assertEquals(2, vm.filas.size) // quedan «Galletas» y «Pan»
        assertEquals(0, vm.filaConError)
        vm.actualizar(0, CampoEscaneo.NOMBRE, "Galletas dulces")
        vm.guardar { resumen = it }
        assertEquals(2, resumen!!.insertadas)
        assertNull(resumen!!.error)
        assertTrue(vm.filas.isEmpty())
        assertEquals(1, productos.guardados.count { it.nombre == "Sal" })
        assertEquals(listOf("Sal", "Galletas dulces", "Pan"), productos.guardados.map { it.nombre })
    }

    @Test fun elInsumoSinUnidadReconocidaNoSeGuarda() {
        val vm = vm()
        vm.aplicarTabla(
            TipoEscaneo.INSUMO,
            listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD, CampoEscaneo.COSTO, CampoEscaneo.UNIDAD),
            tabla(listOf("Leche en polvo", "2,5", "120.00", "caja")),
        )
        var resumen: ResumenImportacion? = null
        vm.guardar { resumen = it }
        assertEquals(ErrorEscaneo.ValorInvalido(1, CampoEscaneo.UNIDAD), resumen!!.error)
        assertEquals(0, resumen!!.insertadas)
    }
}
