package cu.spvi.domain.service

import cu.spvi.core.money.Cup
import cu.spvi.core.money.sumOfCup
import cu.spvi.core.quantity.Cantidad
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.Insumo
import cu.spvi.domain.model.Receta

object Recetas {

    /** Costo de 1 unidad del Elaborado = Σ precio del insumo × cantidad (HALF_UP por línea). */
    fun costo(receta: Receta, insumos: Map<Long, Insumo>): AppResult<Cup> {
        val lineas = receta.lineas.map { l -> (insumos[l.insumoId] ?: return AppResult.Err(AppError.NoEncontrado)) to l.cantidad }
        return AppResult.Ok(lineas.sumOfCup { (i, c) -> i.precio.porMilesimas(c.milesimas) })
    }

    fun consumo(receta: Receta, unidades: Long): Map<Long, Cantidad> =
        receta.lineas.associate { it.insumoId to it.cantidad * unidades }

    /** Nombres de los insumos sin existencias suficientes para [consumo]. */
    fun faltantes(consumo: Map<Long, Cantidad>, insumos: Map<Long, Insumo>): List<String> =
        consumo.mapNotNull { (id, c) -> insumos[id]?.takeIf { it.cantidad < c }?.nombre ?: if (id !in insumos) "#$id" else null }

    /**
     * Unidades completas que alcanzan con las existencias actuales (mínimo entre insumos). Sin receta o con un
     * insumo desconocido → 0. Una línea con cantidad 0 no limita.
     */
    fun maxUnidades(receta: Receta, insumos: Map<Long, Insumo>): Long {
        if (receta.lineas.isEmpty()) return 0
        return receta.lineas.filter { it.cantidad.milesimas > 0 }.minOfOrNull { l ->
            val i = insumos[l.insumoId] ?: return 0
            (i.cantidad.milesimas.coerceAtLeast(0)) / l.cantidad.milesimas
        } ?: 0
    }
}
