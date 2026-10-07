package cu.spvi.data

import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.dto.PreferenciasDto
import cu.spvi.data.dto.ProductoDto
import cu.spvi.data.dto.toDomain
import cu.spvi.data.local.SpviJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Migración 10→11: `codigo` sale de producto (se recrea la tabla conservando los datos). */
class Migracion1011Test {

    @Test fun migracion10_11RecreaProductoSinCodigoConservandoDatos() {
        assertEquals(11, SpviDatabase.VERSION)
        assertEquals(10, SpviDatabase.MIGRACION_10_11.startVersion)
        assertEquals(11, SpviDatabase.MIGRACION_10_11.endVersion)
        val sql = SpviDatabase.SQL_10_11
        assertTrue(sql.any { it.startsWith("CREATE TABLE IF NOT EXISTS `producto_nuevo`") && "codigo" !in it })
        assertTrue(sql.any { it.startsWith("INSERT INTO `producto_nuevo`") && "FROM `producto`" in it })
        assertTrue(sql.any { it == "DROP TABLE `producto`" })
        assertTrue(sql.any { it.startsWith("ALTER TABLE `producto_nuevo` RENAME TO `producto`") })
        assertTrue(sql.none { "codigo" in it })
        assertTrue(sql.none { it.startsWith("DELETE") })
        assertTrue(sql.none { it.startsWith("DROP") && it != "DROP TABLE `producto`" })
    }

    @Test fun respaldoViejoConCodigoYConsentimientoSeImporta() {
        val viejo = """{"id":1,"categoria":"Pan","nombre":"Pan suave","precioCostoCent":50,"precioVentaCent":100,"cantidad":10,"codigo":"7501234","creadoEn":1000,"actualizadoEn":2000}"""
        val p = SpviJson.decodeFromString(ProductoDto.serializer(), viejo).toDomain()
        assertEquals("Pan suave", p.nombre)
        val prefs = SpviJson.decodeFromString(
            PreferenciasDto.serializer(), """{"productoBajo":7,"consultasEnLinea":"DENEGADO"}""",
        ).toDomain()
        assertEquals(7, prefs.niveles.productoBajo)
    }
}
