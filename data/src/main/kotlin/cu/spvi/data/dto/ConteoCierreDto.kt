package cu.spvi.data.dto

import cu.spvi.core.money.Cup
import cu.spvi.data.local.SpviJson
import cu.spvi.domain.model.ConteoCierre
import cu.spvi.domain.model.LineaConteo
import cu.spvi.domain.model.TipoEntidad
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable
data class LineaConteoDto(
    val entidad: String, val id: Long, val nombre: String, val unidad: String,
    val esperado: Long, val contado: Long, val actualizadoEn: Long,
)
@Serializable
data class ConteoCierreDto(
    val billetes: Map<Int, Long> = emptyMap(), val otroCent: Long = 0,
    val inventario: List<LineaConteoDto> = emptyList(), val motivo: String = "",
    val aprobadoPor: String? = null, val aprobadoEn: Long? = null,
)
fun ConteoCierre.toDto() = ConteoCierreDto(billetes, otroEfectivo.centavos,
    inventario.map { LineaConteoDto(it.entidad.name, it.id, it.nombre, it.unidad, it.esperado, it.contado, it.actualizadoEn) },
    motivo, aprobadoPor, aprobadoEn)
fun ConteoCierreDto.toDomain() = ConteoCierre(billetes, Cup(otroCent),
    inventario.map { LineaConteo(TipoEntidad.valueOf(it.entidad), it.id, it.nombre, it.unidad, it.esperado, it.contado, it.actualizadoEn) },
    motivo, aprobadoPor, aprobadoEn)
fun ConteoCierre.aJson(): String = SpviJson.encodeToString(toDto())
fun String.conteoDeJson(): ConteoCierre = SpviJson.decodeFromString<ConteoCierreDto>(this).toDomain()
