package cu.spvi.data.repository

import cu.spvi.core.result.AppError
import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.db.abortar
import cu.spvi.data.db.entity.MovimientoEntity
import cu.spvi.data.dto.aJson
import cu.spvi.data.dto.conteoDeJson
import cu.spvi.domain.model.Categorias
import cu.spvi.domain.model.ConteoCierre
import cu.spvi.domain.model.LineaConteo
import cu.spvi.domain.model.TipoEntidad

/** Se ejecuta siempre dentro de la transacción del cierre o de la aprobación. */
internal suspend fun SpviDatabase.instantaneaConteo(): List<LineaConteo> =
    productoDao().todos().filter { !it.eliminado && !Categorias.esElaborado(it.categoria) }.map {
        LineaConteo(TipoEntidad.PRODUCTO, it.id, it.nombre, "u", it.cantidad, it.cantidad, it.actualizadoEn)
    } + insumoDao().todos().map {
        LineaConteo(TipoEntidad.INSUMO, it.id, it.nombre, it.unidad.let { u -> cu.spvi.domain.model.UnidadMedida.valueOf(u).simbolo },
            it.cantidadMil, it.cantidadMil, it.actualizadoEn)
    }

internal suspend fun SpviDatabase.comprobarConteo(c: ConteoCierre, compararFecha: Boolean = true) {
    if (!c.valido()) abortar(AppError.Validacion("conteo", AppError.Regla.RANGO))
    val actuales = instantaneaConteo().associateBy { it.entidad to it.id }
    if (actuales.keys != c.inventario.map { it.entidad to it.id }.toSet()) abortar(AppError.Validacion("inventarioCambió", AppError.Regla.RANGO))
    for (l in c.inventario) {
        val actual = actuales[l.entidad to l.id]!!
        if (actual.esperado != l.esperado || (compararFecha && actual.actualizadoEn != l.actualizadoEn))
            abortar(AppError.Validacion("inventarioCambió", AppError.Regla.RANGO))
    }
}

internal suspend fun SpviDatabase.aplicarConteo(turnoId: Long, usuario: String, ahora: Long) {
    val turno = turnoDao().obtener(turnoId) ?: abortar(AppError.NoEncontrado)
    val c = turno.conteoCierreJson?.conteoDeJson() ?: return // compatibilidad con turnos anteriores
    if (c.aprobadoEn != null) return // aprobación idempotente: nunca repetir un ajuste
    comprobarConteo(c, compararFecha = turno.empleadoId == null)
    for (l in c.inventario.filter { it.contado != it.esperado }) {
        val delta = l.diferencia
        val n = if (l.entidad == TipoEntidad.PRODUCTO) productoDao().sumarStock(l.id, delta, ahora)
            else insumoDao().sumarStock(l.id, delta, ahora)
        if (n != 1) abortar(AppError.NoEncontrado)
        movimientoDao().insertar(MovimientoEntity(
            fecha = ahora, tipo = "AJUSTE", entidad = l.entidad.name, entidadId = l.id, nombre = l.nombre,
            delta = delta, existencia = l.contado, turnoId = turnoId, ventaId = null,
            nota = "Conciliación de cierre · Esperado ${cantidadConteo(l.esperado, l)} · Contado ${cantidadConteo(l.contado, l)} · ${c.motivo.trim()}", hechoPor = usuario,
        ))
    }
    syncDao().actualizarTurno(turno.copy(conteoCierreJson = c.copy(aprobadoPor = usuario, aprobadoEn = ahora).aJson(), sincronizado = false))
}

private fun cantidadConteo(n: Long, l: LineaConteo): String =
    (if (l.entidad == TipoEntidad.INSUMO) cu.spvi.core.quantity.Cantidad.format(cu.spvi.core.quantity.Cantidad(n)) else n.toString()) + " ${l.unidad}"
