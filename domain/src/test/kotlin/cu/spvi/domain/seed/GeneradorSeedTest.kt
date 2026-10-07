package cu.spvi.domain.seed

import cu.spvi.domain.model.ClaseArticulo
import cu.spvi.domain.model.MetodoPago
import org.junit.Assert.*
import org.junit.Test

class GeneradorSeedTest {
    private val gen = GeneradorSeed()

    @Test fun deterministaMismaSemillaMismoPlan() {
        val otro = GeneradorSeed(2907L)
        (0 until 548).forEach { i -> assertEquals(gen.planDia(i), otro.planDia(i)) }
    }

    @Test fun unDiaDeDescansoCadaSiete() {
        (0 until 548).forEach { i ->
            val plan = gen.planDia(i)
            assertEquals(i % 7 == 6, plan.descansa)
            if (plan.descansa) assertTrue(plan.ventas.isEmpty())
        }
    }

    @Test fun rangosDeVentasYLineas() {
        (0 until 548).map { gen.planDia(it) }.filterNot { it.descansa }.forEach { dia ->
            assertTrue(dia.ventas.size in 6..14)
            dia.ventas.forEach { v ->
                assertTrue(v.lineas.size in 1..4)
                val clases = v.lineas.map { it.clase }.toSet()
                assertTrue(clases == setOf(ClaseArticulo.PRODUCTO) || clases == setOf(ClaseArticulo.SERVICIO))
                assertTrue(v.lineas.count { CatalogoBodega.esElaborado(it.clave) } <= 1)
                if (v.metodo == MetodoPago.TRANSFERENCIA) {
                    assertNotNull(v.clienteClave)
                    assertTrue(v.numeroTransaccion.matches(Regex("^[A-Za-z0-9]{6,30}$")))
                }
            }
        }
    }

    @Test fun mezclaEfectivoTransferencia70_30() {
        val ventas = (0 until 548).flatMap { gen.planDia(it).ventas }
        val pctTrans = ventas.count { it.metodo == MetodoPago.TRANSFERENCIA }.toDouble() / ventas.size
        assertTrue(pctTrans in 0.24..0.36)
    }

    @Test fun anuladaNuncaEsLaUnicaDelDia() {
        (0 until 548).map { gen.planDia(it) }.filterNot { it.descansa }.forEach { dia ->
            val anuladas = dia.ventas.count { it.anular }
            assertTrue(anuladas <= 1)
            if (dia.ventas.size == 1) assertEquals(0, anuladas)
        }
        val total = (0 until 548).sumOf { gen.planDia(it).ventas.count { v -> v.anular } }
        val ventas = (0 until 548).sumOf { gen.planDia(it).ventas.size }
        assertTrue(total.toDouble() / ventas in 0.005..0.03)
    }

    @Test fun cajaSemanalConMotivoValido() {
        (0 until 548).map { gen.planDia(it) }.forEach { dia ->
            dia.caja.forEach { c -> assertTrue(c.motivo.trim().length in 3..60) }
        }
        val conCaja = (0 until 548).count { gen.planDia(it).caja.isNotEmpty() }
        assertTrue(conCaja in 60..95)
    }

    @Test fun descuadreUnasDosVecesPorMes() {
        val n = (0 until 548).count { gen.planDia(it).descuadrePesos != 0L }
        assertTrue(n in 28..52)
    }
}
