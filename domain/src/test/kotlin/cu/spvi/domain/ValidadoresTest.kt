package cu.spvi.domain

import cu.spvi.core.money.Cup
import cu.spvi.core.quantity.Cantidad
import cu.spvi.core.result.AppError.Regla
import cu.spvi.domain.model.Categorias
import cu.spvi.domain.model.DatosCliente
import cu.spvi.domain.model.PreajustePrecios
import cu.spvi.domain.model.Receta
import cu.spvi.domain.model.RecetaLinea
import cu.spvi.domain.validation.Validadores
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidadoresTest {
    private fun campos(l: List<cu.spvi.core.result.AppError.Validacion>) = l.map { it.campo }.toSet()

    @Test fun `producto valido no tiene errores`() = assertTrue(Validadores.producto(producto(1, cad = LocalDate.of(2027, 1, 1))).isEmpty())

    @Test fun `producto con errores los reporta todos`() {
        val p = producto(1).copy(nombre = " ", precioVenta = Cup.ZERO, cantidad = -1, nivelBajo = 2, nivelCritico = 3)
        assertEquals(setOf("nombre", "precioVenta", "cantidad", "nivelCritico"), campos(Validadores.producto(p)))
    }

    @Test fun `precio de venta debe superar estrictamente el costo`() {
        val base = producto(1, venta = 60, costo = 60)
        assertEquals(Regla.RANGO, Validadores.producto(base).single { it.campo == "precioVenta" }.regla)
        assertEquals(Regla.RANGO, Validadores.producto(base.copy(precioVenta = Cup.ofPesos(59))).single { it.campo == "precioVenta" }.regla)
        assertTrue(Validadores.producto(base.copy(precioVenta = Cup.ofPesos(61))).none { it.campo == "precioVenta" })

        val i = insumo(2, precio = 40).copy(precioVenta = Cup.ofPesos(40))
        assertEquals(Regla.RANGO, Validadores.insumo(i).single { it.campo == "precioVenta" }.regla)
        assertTrue(Validadores.insumo(i.copy(precioVenta = Cup.ofPesos(41))).none { it.campo == "precioVenta" })
    }

    @Test fun `elaborado no admite foto ni caducidad`() {
        val p = producto(1, categoria = Categorias.ELABORADO, cantidad = 0).copy(fotoUri = "x", fechaCaducidad = LocalDate.MAX)
        val e = Validadores.producto(p)
        assertEquals(setOf("fotoUri", "fechaCaducidad"), campos(e))
        assertTrue(e.all { it.regla == Regla.NO_PERMITIDO })
        // P26: tampoco existencias ni niveles propios.
        val conStock = producto(1, categoria = Categorias.ELABORADO, cantidad = 3, bajo = 2)
        assertEquals(setOf("cantidad", "niveles"), campos(Validadores.producto(conStock)))
    }

    @Test fun `receta requiere lineas positivas sin insumos repetidos`() {
        assertEquals(Regla.REQUERIDO, Validadores.receta(null).single().regla)
        assertEquals(Regla.RANGO, Validadores.receta(Receta(1, listOf(RecetaLinea(1, Cantidad.ZERO)))).single().regla)
        assertEquals(Regla.NO_PERMITIDO, Validadores.receta(Receta(1, listOf(RecetaLinea(1, Cantidad(1)), RecetaLinea(1, Cantidad(2))))).single().regla)
    }

    @Test fun `cliente de transferencia`() {
        assertTrue(Validadores.cliente(DatosCliente("María José Pérez", "85010112345", "51234567")).isEmpty())
        assertEquals(setOf("clienteNombre", "clienteCi", "clienteTelefono"), campos(Validadores.cliente(DatosCliente("M4ria", "123", "12"))))
        assertNull(Validadores.numeroTransaccion("TMW1234567"))
        assertEquals(Regla.FORMATO, Validadores.numeroTransaccion("12-34")!!.regla)
    }

    @Test fun `cuentas bancarias se normalizan`() {
        assertEquals("9200129912345678", Validadores.normalizarCuenta("9200 1299 1234 5678"))
        assertEquals("9200129912345678", Validadores.normalizarCuenta("9200-1299-1234-5678"))
        assertNull(Validadores.normalizarCuenta("1234"))
    }

    @Test fun `preajuste`() {
        assertEquals(setOf("nombre", "puntosBasicos", "productoIds"), campos(Validadores.preajuste(PreajustePrecios(nombre = "", puntosBasicos = 0, productoIds = emptySet()))))
        assertEquals(setOf("puntosBasicos"), campos(Validadores.preajuste(PreajustePrecios(nombre = "x", puntosBasicos = 20_000, productoIds = setOf(1)))))
    }
}
