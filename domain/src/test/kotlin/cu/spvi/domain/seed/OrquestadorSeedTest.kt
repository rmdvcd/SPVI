package cu.spvi.domain.seed

import cu.spvi.core.money.Cup
import cu.spvi.core.money.sumOfCup
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.FakeInsumos
import cu.spvi.domain.FakeMantenimiento
import cu.spvi.domain.FakePerfil
import cu.spvi.domain.FakePrecios
import cu.spvi.domain.FakePreferencias
import cu.spvi.domain.FakeProductos
import cu.spvi.domain.FakeServicios
import cu.spvi.domain.FakeTurnos
import cu.spvi.domain.FakeVentas
import cu.spvi.domain.model.ElaboradoEnVenta
import cu.spvi.domain.model.Venta
import cu.spvi.domain.repository.VentaRepository
import cu.spvi.domain.usecase.CotizarVenta
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun armado(diario: MutableList<String>): OrquestadorSeedParts {
    val productos = FakeProductos()
    val insumos = FakeInsumos()
    productos.insumos = insumos
    val turnos = FakeTurnos()
    val ventas = FakeVentas(productos, turnos)
    val precios = FakePrecios()
    val perfil = FakePerfil()
    val preferencias = FakePreferencias()
    val mantenimiento = FakeMantenimiento(diario)
    val servicios = FakeServicios()
    val orquestador = OrquestadorSeed(
        CotizarVenta(productos, precios, insumos, servicios),
        turnos, ventas, productos, insumos, servicios, precios, perfil, preferencias, mantenimiento,
    )
    return OrquestadorSeedParts(orquestador, turnos, ventas, productos, insumos)
}

private data class OrquestadorSeedParts(
    val orquestador: OrquestadorSeed,
    val turnos: FakeTurnos,
    val ventas: FakeVentas,
    val productos: FakeProductos,
    val insumos: FakeInsumos,
)

class OrquestadorSeedTest {

    @Test fun ordenYFondoEncadenadoYVendedores() = runTest {
        val diario = mutableListOf<String>()
        val (orquestador, turnos) = armado(diario)
        val progresos = mutableListOf<Pair<Int, Int>>()
        val r = orquestador.ejecutar({ d: Int, t: Int -> progresos += d to t }, dias = 8)
        assertTrue(r is AppResult.Ok)
        assertEquals(listOf("borrar"), diario) // borrarTodo lo primero
        val cerrados = turnos.turnos.value.filterNot { it.abierto }
        assertEquals(7, cerrados.size) // 8 días, 1 descanso
        cerrados.zipWithNext { a, b -> assertEquals(a.contado, b.fondo) }
        assertTrue(cerrados.all { it.abiertoPor == "María Fernández" }) // 8 días < racha de 14
        assertEquals(8, progresos.last().first) // último día publicado
    }

    @Test fun stockNuncaNegativoYDeterminista() = runTest {
        val (o1, _, v1, p1, i1) = armado(mutableListOf())
        val (o2, _, v2, _, _) = armado(mutableListOf())
        assertTrue(o1.ejecutar({ _, _ -> }, dias = 8) is AppResult.Ok)
        assertTrue(o2.ejecutar({ _, _ -> }, dias = 8) is AppResult.Ok)
        assertEquals(v1.ventas.size, v2.ventas.size)
        assertEquals(v1.ventas.sumOfCup { it.total }, v2.ventas.sumOfCup { it.total })
        assertTrue(p1.items.value.values.all { it.cantidad >= 0 })
        assertTrue(i1.items.value.values.all { it.cantidad.milesimas >= 0 })
    }

    @Test fun rotacionDeVendedoresPorRachas() = runTest {
        val diario = mutableListOf<String>()
        val (orquestador, turnos) = armado(diario)
        // 16 días cruzan la racha de 14: María abre los primeros, Jorge los últimos.
        assertTrue(orquestador.ejecutar({ _, _ -> }, dias = 16) is AppResult.Ok)
        assertEquals(
            setOf("María Fernández", "Jorge Pérez"),
            turnos.turnos.value.map { it.abiertoPor }.toSet(),
        )
    }

    @Test fun falloAMitadAbortaYReintentoRecupera() = runTest {
        val productos = FakeProductos()
        val insumos = FakeInsumos()
        productos.insumos = insumos
        val turnos = FakeTurnos()
        val reales = FakeVentas(productos, turnos)
        val ventas = VentasQueFallanUnaVez(reales)
        val precios = FakePrecios()
        val servicios = FakeServicios()
        val orquestador = OrquestadorSeed(
            CotizarVenta(productos, precios, insumos, servicios),
            turnos, ventas, productos, insumos, servicios, precios,
            FakePerfil(), FakePreferencias(), FakeMantenimiento(mutableListOf()),
        )
        assertTrue(orquestador.ejecutar({ _, _ -> }, dias = 8) is AppResult.Err)
        // El reintento parte de borrarTodo: en producción borra la BD; aquí se emula limpiando los fakes.
        ventas.fallos = 0
        turnos.turnos.value = emptyList()
        turnos.movimientos.clear()
        turnos.caja.clear()
        reales.ventas.clear()
        assertTrue(orquestador.ejecutar({ _, _ -> }, dias = 8) is AppResult.Ok)
        assertTrue(turnos.turnos.value.none { it.abierto })
    }
}

/** Falla las primeras `fallos` llamadas a registrar y luego delega al fake real. */
private class VentasQueFallanUnaVez(private val real: FakeVentas) : VentaRepository by real {
    var fallos = 1
    override suspend fun registrar(venta: Venta, elaborados: List<ElaboradoEnVenta>): AppResult<Long> {
        if (fallos > 0) {
            fallos--
            return AppResult.Err(AppError.NoEncontrado)
        }
        return real.registrar(venta, elaborados)
    }
}
