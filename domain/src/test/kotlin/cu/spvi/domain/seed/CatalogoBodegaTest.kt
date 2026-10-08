package cu.spvi.domain.seed

import cu.spvi.domain.model.Categorias
import org.junit.Assert.*
import org.junit.Test

class CatalogoBodegaTest {
    @Test fun recetasReferencianInsumosExistentes() {
        val insumos = CatalogoBodega.INSUMOS.map { it.clave }.toSet()
        val usadas = (CatalogoBodega.RECETAS.values.flatten() + CatalogoBodega.SERVICIOS.flatMap { it.insumos }).map { it.insumo }.toSet()
        assertTrue(usadas.isNotEmpty())
        assertEquals(emptySet<String>(), usadas - insumos)
    }

    @Test fun elaboradosConRecetaYSinFotoNiCodigo() {
        val elaborados = CatalogoBodega.PRODUCTOS.filter { it.categoria == Categorias.ELABORADO }
        assertEquals(2, elaborados.size)
        elaborados.forEach { assertTrue(CatalogoBodega.RECETAS.containsKey(it.clave)) }
    }

    @Test fun clientesConFormatoCubano() {
        assertEquals(12, CatalogoBodega.CLIENTES.size)
        CatalogoBodega.CLIENTES.forEach {
            assertTrue(it.ci.matches(Regex("^[0-9]{11}$")))
            assertTrue(it.telefono8.matches(Regex("^[0-9]{8}$")))
        }
    }

    @Test fun serviciosYZonasDeCobro() {
        assertEquals(8, CatalogoBodega.SERVICIOS.size)
        assertEquals(3, CatalogoBodega.SERVICIOS.count { it.insumos.isNotEmpty() })
        assertEquals(2, CatalogoBodega.TARJETAS.size)
    }

    @Test fun productosConCostoMenorQueVenta() {
        CatalogoBodega.PRODUCTOS.forEach {
            assertTrue("${it.clave}: costo ${it.costoPesos} >= venta ${it.ventaPesos}", it.costoPesos < it.ventaPesos)
        }
    }

    /**
     * El costo real de un elaborado (o de un servicio con insumos) sale de su receta
     * (Σ precio × milésimas / 1000), no del costo declarado: la receta también debe dar ganancia.
     */
    @Test fun recetasConCostoMenorQueVenta() {
        val precio = CatalogoBodega.INSUMOS.associate { it.clave to it.precioPesos }
        fun costoMilesimas(lineas: List<LineaRecetaSeed>) = lineas.sumOf { precio[it.insumo]!! * it.milesimas }
        CatalogoBodega.PRODUCTOS.filter { it.categoria == Categorias.ELABORADO }.forEach {
            val costo = costoMilesimas(CatalogoBodega.RECETAS[it.clave]!!)
            assertTrue("${it.clave}: receta $costo/1000 >= venta ${it.ventaPesos}", costo < it.ventaPesos * 1000)
        }
        CatalogoBodega.SERVICIOS.filter { it.insumos.isNotEmpty() }.forEach {
            val costo = costoMilesimas(it.insumos)
            assertTrue("${it.clave}: insumos $costo/1000 >= importe ${it.importePesos}", costo < it.importePesos * 1000)
        }
    }
}
