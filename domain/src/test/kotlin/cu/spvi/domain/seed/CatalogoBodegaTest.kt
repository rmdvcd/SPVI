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
}
