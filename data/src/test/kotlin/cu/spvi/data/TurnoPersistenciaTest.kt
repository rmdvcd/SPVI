package cu.spvi.data

import cu.spvi.core.money.Cup
import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.db.entity.TurnoEntity
import cu.spvi.data.dto.TurnoDto
import cu.spvi.data.dto.toDomain
import cu.spvi.data.dto.toDto
import cu.spvi.data.local.SpviJson
import cu.spvi.data.mapper.toDomain
import cu.spvi.data.mapper.toEntity
import cu.spvi.domain.model.ResumenTurno
import cu.spvi.domain.model.Turno
import java.lang.reflect.Modifier
import java.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Turno de venta: migración 1→2, mapeo Room y respaldo (JVM, sin dispositivo). */
class TurnoPersistenciaTest {

    private val columnasV1 = setOf(
        "id", "abiertoEn", "cerradoEn", "numVentas", "unidades", "totalCent", "efectivoCent",
        "transferenciaCent", "costoCent", "numMovimientos",
    )

    private val cerrado = Turno(
        id = 3, abiertoEn = Instant.parse("2026-09-30T12:00:00Z"), cerradoEn = Instant.parse("2026-09-30T21:00:00Z"),
        resumen = ResumenTurno(4, 9, Cup.ofPesos(900), Cup.ofPesos(600), Cup.ofPesos(300), Cup.ofPesos(500), 7, 3, 1, 5, 2),
        abiertoPor = "Ana Pérez", cerradoPor = "Luis",
    )

    @Test fun esquemaV3ConsolidadoSoloRecreaLasVersionesDeDesarrollo() {
        // P29: v4 (servicios + precio de venta de insumos + clase en detalle_venta) con migración explícita 3→4.
        // P38: v5 (Principal/Secundaria: tabla empleado + uuid/empleadoId/sincronizado) con migración explícita 4→5.
        // 0.20.0: v6 (empleado: tarjetaId, telefonoId, cierreSolicitadoEn) con migración explícita 5→6.
        assertEquals(11, SpviDatabase.VERSION)
        // 0.27.0 (N2): v10 = clientes fijos (tabla nueva) y la marca en `transaccion`, con migración 9→10.
        assertEquals(9, SpviDatabase.MIGRACION_9_10.startVersion)
        assertEquals(10, SpviDatabase.MIGRACION_9_10.endVersion)
        assertTrue(SpviDatabase.SQL_9_10.none { it.contains("DROP") || it.contains("DELETE") })
        // 0.26.0: v9 (empleado: fondo asignado, petición de apertura, versionCode) con migración 8→9.
        assertEquals(8, SpviDatabase.MIGRACION_8_9.startVersion)
        assertEquals(9, SpviDatabase.MIGRACION_8_9.endVersion)
        assertTrue(SpviDatabase.SQL_8_9.all { it.startsWith("ALTER TABLE `empleado` ADD COLUMN") })
        // 0.25.0: v8 (caja, fondo/contado, anulación de ventas) con migración 7→8.
        assertEquals(7, SpviDatabase.MIGRACION_7_8.startVersion)
        assertEquals(8, SpviDatabase.MIGRACION_7_8.endVersion)
        // 0.21.0: v7 (empleado: telefono, cierrePedidoPorEmpleadoEn; movimiento: hechoPor) con migración 6→7.
        assertEquals(6, SpviDatabase.MIGRACION_6_7.startVersion)
        assertEquals(7, SpviDatabase.MIGRACION_6_7.endVersion)
        assertEquals(3, SpviDatabase.SQL_6_7.size)
        assertTrue(SpviDatabase.SQL_6_7.all { it.startsWith("ALTER TABLE `") && "ADD COLUMN" in it })
        assertEquals(5, SpviDatabase.MIGRACION_5_6.startVersion)
        assertEquals(6, SpviDatabase.MIGRACION_5_6.endVersion)
        assertEquals(3, SpviDatabase.SQL_5_6.size)
        assertTrue(SpviDatabase.SQL_5_6.all { it.startsWith("ALTER TABLE `empleado` ADD COLUMN") })
        assertEquals(4, SpviDatabase.MIGRACION_4_5.startVersion)
        assertEquals(5, SpviDatabase.MIGRACION_4_5.endVersion)
        assertTrue(SpviDatabase.SQL_4_5.any { it.contains("`empleado`") })
        assertEquals(3, SpviDatabase.MIGRACION_3_4.startVersion)
        assertEquals(4, SpviDatabase.MIGRACION_3_4.endVersion)
        assertTrue(SpviDatabase.SQL_3_4.any { it.contains("CREATE TABLE IF NOT EXISTS `servicio`") || it.contains("CREATE TABLE `servicio`") })
        // P17: solo las BD de desarrollo (v1/v2, sin clientes) se recrean; cualquier otra migración debe ser explícita.
        assertArrayEquals(intArrayOf(1, 2), SpviDatabase.DESARROLLO)
        assertTrue(SpviDatabase.VERSION !in SpviDatabase.DESARROLLO)
        val campos = TurnoEntity::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
        assertTrue(campos.containsAll(columnasV1))
    }

    @Test fun roomIdaYVueltaConservaUsuarioYDesglose() {
        assertEquals(cerrado, cerrado.toEntity().toDomain())
        val abierto = Turno(id = 4, abiertoEn = Instant.parse("2026-10-01T12:00:00Z"), abiertoPor = "Ana")
        assertEquals(abierto, abierto.toEntity().toDomain())
    }

    @Test fun turnoCerradoAntesDeV2SeLeeSinDesglose() {
        val viejo = TurnoEntity(1, 1_000, 2_000, 3, 5, 45_000, 45_000, 0, 24_000, 7).toDomain()
        assertEquals("", viejo.abiertoPor)
        assertNull(viejo.cerradoPor)
        val r = viejo.resumen!!
        assertEquals(7, r.numMovimientos)
        assertEquals(7, r.movimientosProducto) // sin desglose: todo cuenta como producto
        assertEquals(0, r.movimientosInsumo)
        assertEquals(Cup.ofPesos(210), r.ganancia)
    }

    @Test fun respaldoIdaYVueltaYRespaldosAntiguos() {
        val json = SpviJson.encodeToString(TurnoDto.serializer(), cerrado.toDto())
        assertEquals(cerrado, SpviJson.decodeFromString(TurnoDto.serializer(), json).toDomain())

        val antiguo = """{"id":1,"abiertoEn":1000,"cerradoEn":2000,"resumen":{"numVentas":2,"unidades":3,"totalCent":300,"efectivoCent":300,"transferenciaCent":0,"costoCent":100,"numMovimientos":2}}"""
        val t = SpviJson.decodeFromString(TurnoDto.serializer(), antiguo).toDomain()
        assertEquals("", t.abiertoPor)
        assertEquals(2, t.resumen!!.movimientosProducto)
        assertEquals(0, t.resumen!!.ventasEfectivo)
    }
}
