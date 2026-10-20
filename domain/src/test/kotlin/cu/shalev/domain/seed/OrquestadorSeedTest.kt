package cu.shalev.domain.seed

import cu.shalev.core.money.Cup
import cu.shalev.core.money.sumOfCup
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.FakeFotos
import cu.shalev.domain.FakeInsumos
import cu.shalev.domain.FakeMantenimiento
import cu.shalev.domain.FakePerfil
import cu.shalev.domain.FakePrecios
import cu.shalev.domain.FakePreferencias
import cu.shalev.domain.FakeProductos
import cu.shalev.domain.FakeServicios
import cu.shalev.domain.FakeTurnos
import cu.shalev.domain.FakeVentas
import cu.shalev.domain.model.ElaboradoEnVenta
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.VentaRepository
import cu.shalev.domain.usecase.CotizarVenta
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
    val fotos = FakeFotos()
    val orquestador = OrquestadorSeed(
        CotizarVenta(productos, precios, insumos, servicios),
        turnos, ventas, productos, insumos, servicios, precios, perfil, preferencias, mantenimiento, fotos,
    )
    return OrquestadorSeedParts(orquestador, turnos, ventas, productos, insumos, fotos)
}

private data class OrquestadorSeedParts(
    val orquestador: OrquestadorSeed,
    val turnos: FakeTurnos,
    val ventas: FakeVentas,
    val productos: FakeProductos,
    val insumos: FakeInsumos,
    val fotos: FakeFotos = FakeFotos(),
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

    @Test fun fotosIncluidasEnProductosYServicios() = runTest {
        val (orquestador, _, _, productos, _, fotos) = armado(mutableListOf())
        assertTrue(orquestador.ejecutar({ _, _ -> }, dias = 4) is AppResult.Ok)
        val conFoto = CatalogoBodega.PRODUCTOS.filterNot { CatalogoBodega.esElaborado(it.clave) }
        conFoto.forEach { s ->
            val p = productos.items.value.values.first { it.nombre == s.nombre }
            assertEquals("file:///incluidas/${s.clave}.jpg", p.fotoUri)
        }
        // Elaborados: no admiten foto, así que ni se pide la imagen.
        CatalogoBodega.PRODUCTOS.filter { CatalogoBodega.esElaborado(it.clave) }
            .forEach { s -> assertTrue("no debe pedir foto de ${s.clave}", s.clave !in fotos.incluidas) }
        CatalogoBodega.SERVICIOS.forEach { s -> assertTrue(s.clave in fotos.incluidas) }
        // Insumos: sin foto en el modelo, así que tampoco se pide.
        val clavesInsumos = CatalogoBodega.INSUMOS.map { it.clave }
        assertTrue(fotos.incluidas.none { it in clavesInsumos })
    }

    @Test fun unaFotoQueFaltaNoAbortaElSeed() = runTest {
        val (orquestador, _, _, productos, _, fotos) = armado(mutableListOf())
        fotos.fallarIncluidas = true
        assertTrue(fotos.incluidas.isEmpty())
        assertTrue(orquestador.ejecutar({ _, _ -> }, dias = 4) is AppResult.Ok)
        assertTrue(fotos.incluidas.isNotEmpty())
        assertTrue(productos.items.value.values.all { it.fotoUri == null })
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
            FakePerfil(), FakePreferencias(), FakeMantenimiento(mutableListOf()), FakeFotos(),
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
