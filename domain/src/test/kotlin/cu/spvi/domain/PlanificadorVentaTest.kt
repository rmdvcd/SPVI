package cu.spvi.domain

import cu.spvi.core.money.Cup
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.model.PreajustePrecios
import cu.spvi.domain.service.Cotizacion
import cu.spvi.domain.service.LineaSolicitada
import cu.spvi.domain.service.PlanificadorVenta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanificadorVentaTest {
    private val ps = listOf(producto(1, venta = 100, costo = 60, cantidad = 5), producto(2, venta = 50, costo = 20, cantidad = 2)).associateBy { it.id }

    private fun ok(r: AppResult<Cotizacion>) = (r as AppResult.Ok).value

    @Test fun `agrupa lineas repetidas y congela precio y costo`() {
        val c = ok(PlanificadorVenta.cotizar(listOf(LineaSolicitada(1, 1), LineaSolicitada(1, 2), LineaSolicitada(2, 1)), ps, MetodoPago.EFECTIVO, emptyList()))
        assertEquals(3L, c.detalles.first { it.productoId == 1L }.cantidad)
        assertEquals(Cup.ofPesos(350), c.total)
        assertEquals(Cup.ofPesos(200), c.costo)
        assertTrue(c.ajustes.isEmpty())
    }

    @Test fun `stock insuficiente lista los nombres`() {
        val r = PlanificadorVenta.cotizar(listOf(LineaSolicitada(1, 6), LineaSolicitada(2, 3)), ps, MetodoPago.EFECTIVO, emptyList())
        assertEquals(AppError.StockInsuficiente(listOf("P1", "P2")), (r as AppResult.Err).error)
    }

    @Test fun `cantidades invalidas o venta vacia`() {
        assertTrue((PlanificadorVenta.cotizar(emptyList(), ps, MetodoPago.EFECTIVO, emptyList()) as AppResult.Err).error is AppError.Validacion)
        assertTrue((PlanificadorVenta.cotizar(listOf(LineaSolicitada(1, 0)), ps, MetodoPago.EFECTIVO, emptyList()) as AppResult.Err).error is AppError.Validacion)
        assertEquals(AppError.NoEncontrado, (PlanificadorVenta.cotizar(listOf(LineaSolicitada(9, 1)), ps, MetodoPago.EFECTIVO, emptyList()) as AppResult.Err).error)
    }

    @Test fun `preajustes por metodo e importe minimo se suman`() {
        val pre = listOf(
            PreajustePrecios(1, "Transferencia +10", 1000, setOf(1), metodoPago = MetodoPago.TRANSFERENCIA),
            PreajustePrecios(2, "Mayorista -5", -500, setOf(1, 2), importeMinimo = Cup.ofPesos(300)),
            PreajustePrecios(3, "Inactivo", 5000, setOf(1), activo = false),
        )
        val lineas = listOf(LineaSolicitada(1, 3), LineaSolicitada(2, 1)) // base 350 ≥ 300
        val tr = ok(PlanificadorVenta.cotizar(lineas, ps, MetodoPago.TRANSFERENCIA, pre))
        assertEquals(mapOf(1L to 500, 2L to -500), tr.ajustes)
        assertEquals(Cup.ofPesos(105), tr.detalles.first { it.productoId == 1L }.precioUnitario)
        assertEquals(Cup.ofPesos(100), tr.detalles.first { it.productoId == 1L }.precioBase)
        assertEquals(Cup(4750), tr.detalles.first { it.productoId == 2L }.precioUnitario)

        val ef = ok(PlanificadorVenta.cotizar(listOf(LineaSolicitada(1, 1)), ps, MetodoPago.EFECTIVO, pre)) // base 100 < 300
        assertTrue(ef.ajustes.isEmpty())
    }

    @Test fun `descuentos no permiten vender al costo ni por debajo`() {
        listOf(-4000, -5000).forEach { descuento ->
            val pre = listOf(PreajustePrecios(1, "Descuento", descuento, setOf(1L)))
            val r = PlanificadorVenta.cotizar(listOf(LineaSolicitada(1, 1)), ps, MetodoPago.EFECTIVO, pre)
            assertEquals(AppError.Validacion("precioVenta", AppError.Regla.RANGO), (r as AppResult.Err).error)
        }
    }

    @Test fun `producto eliminado no se vende`() {
        val m = ps + (1L to ps.getValue(1).copy(eliminado = true))
        assertEquals(AppError.NoEncontrado, (PlanificadorVenta.cotizar(listOf(LineaSolicitada(1, 1)), m, MetodoPago.EFECTIVO, emptyList()) as AppResult.Err).error)
    }
}
