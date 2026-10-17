package cu.spvi.data

import cu.spvi.core.money.Cup
import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.dto.*
import cu.spvi.data.local.SpviJson
import cu.spvi.data.mapper.toEntity
import cu.spvi.data.mapper.toDomain
import cu.spvi.domain.model.*
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class ConteoCierrePersistenciaTest {
    private val conteo = ConteoCierre(mapOf(20000 to 2L, 5000 to 1L), Cup(75),
        listOf(LineaConteo(TipoEntidad.INSUMO, 1, "Harina", "kg", 2000, 1500, 1000)), "Merma", "Ana", 2000)
    private val turno = Turno(id = 1, abiertoEn = Instant.EPOCH, contado = conteo.efectivo, conteoCierre = conteo)

    @Test fun conservaConteoEnRoomJsonYRespaldo() {
        assertEquals(conteo, conteo.aJson().conteoDeJson())
        assertEquals(turno, turno.toEntity().toDomain())
        val json = SpviJson.encodeToString(turno.toDto())
        assertEquals(turno, SpviJson.decodeFromString<TurnoDto>(json).toDomain())
    }
    @Test fun unTurnoAnteriorNoInventaConteo() {
        val t = SpviJson.decodeFromString<TurnoDto>("""{"id":1,"abiertoEn":0,"contadoCent":100}""").toDomain()
        assertNull(t.conteoCierre)
        assertEquals(Cup(100), t.contado)
    }
    @Test fun cierreRemotoEsperaLaAprobacionDelInventarioAunqueYaHayaEfectivo() {
        val pendiente = turno.copy(conteoCierre = conteo.copy(aprobadoPor = null, aprobadoEn = null), fondo = Cup.ZERO)
        assertFalse(cu.spvi.data.sync.CierreRemoto.conteoListo(pendiente))
        assertTrue(cu.spvi.data.sync.CierreRemoto.conteoListo(pendiente.copy(conteoCierre = conteo)))
        assertTrue(cu.spvi.data.sync.CierreRemoto.conteoListo(pendiente.copy(conteoCierre = null)))
    }
    @Test fun migracionSoloAnadeColumnaOpcionalSinBorrarDatos() {
        assertEquals(12, SpviDatabase.MIGRACION_12_13.startVersion)
        assertEquals(13, SpviDatabase.MIGRACION_12_13.endVersion)
        assertEquals(listOf("ALTER TABLE `turno` ADD COLUMN `conteoCierreJson` TEXT"), SpviDatabase.SQL_12_13)
    }
}
