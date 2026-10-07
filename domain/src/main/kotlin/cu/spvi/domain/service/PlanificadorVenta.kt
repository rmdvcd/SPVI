package cu.spvi.domain.service

import cu.spvi.domain.model.nombreCompleto
import cu.spvi.domain.model.ClaseArticulo
import cu.spvi.core.money.Cup
import cu.spvi.core.money.sumOfCup
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.DetalleVenta
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.model.PreajustePrecios
import cu.spvi.domain.model.Producto

/**
 * [productoId] = id dentro de su tabla según [clase] (P29: producto, insumo o servicio). Los insumos se venden en
 * unidades enteras de su medida.
 */
data class LineaSolicitada(val productoId: Long, val cantidad: Long, val clase: ClaseArticulo = ClaseArticulo.PRODUCTO)

/** Resultado de cotizar: lo que ve el usuario en el comprobante/QR antes de confirmar. */
data class Cotizacion(
    val metodoPago: MetodoPago,
    val detalles: List<DetalleVenta>,
    /** Puntos básicos aplicados por producto (solo los que tienen ajuste). */
    val ajustes: Map<Long, Int>,
    /** Elaborados de la venta (productoId → unidades): se venden descontando sus insumos (P26). */
    val elaborados: Map<Long, Long> = emptyMap(),
) {
    val totalBase: Cup get() = detalles.sumOfCup { it.precioBase * it.cantidad }
    val total: Cup get() = detalles.sumOfCup { it.subtotal }
    val costo: Cup get() = detalles.sumOfCup { it.costo }
}

/**
 * Cálculo puro de una venta: agrupa líneas repetidas, valida cantidades y existencias, aplica preajustes
 * y congela precio/costo. La persistencia vuelve a verificar stock dentro de la transacción.
 *
 * P26: un Elaborado no tiene existencias propias (su `cantidad` se ignora). Se puede vender mientras
 * [alcanza] (unidades que permiten sus insumos) cubra lo pedido; su costo es el de la receta en ese momento
 * ([costosReceta]). Todas sus unidades quedan en [Cotizacion.elaborados].
 */
object PlanificadorVenta {

    fun cotizar(
        lineas: List<LineaSolicitada>,
        productos: Map<Long, Producto>,
        metodoPago: MetodoPago,
        preajustes: List<PreajustePrecios>,
        /** productoId → unidades que alcanzan con los insumos actuales (solo Elaborados; sin entrada = 0). */
        alcanza: Map<Long, Long> = emptyMap(),
        /** productoId → costo por unidad según la receta actual (solo Elaborados; sin entrada = precioCosto). */
        costosReceta: Map<Long, Cup> = emptyMap(),
    ): AppResult<Cotizacion> {
        if (lineas.isEmpty()) return AppResult.Err(AppError.Validacion("lineas", AppError.Regla.REQUERIDO))
        if (lineas.any { it.cantidad <= 0 }) return AppResult.Err(AppError.Validacion("cantidad", AppError.Regla.RANGO))

        val agrupadas = lineas.groupBy { it.productoId }.mapValues { (_, l) -> l.sumOf { it.cantidad } }
        val items = agrupadas.map { (id, cant) ->
            val p = productos[id]?.takeUnless { it.eliminado } ?: return AppResult.Err(AppError.NoEncontrado)
            p to cant
        }
        val elaborados = items.filter { it.first.esElaborado }.associate { (p, cant) -> p.id to cant }
        val faltantes = items.filter { (p, cant) ->
            cant > (if (p.esElaborado) alcanza[p.id] ?: 0 else p.cantidad)
        }.map { it.first.nombreCompleto }
        if (faltantes.isNotEmpty()) return AppResult.Err(AppError.StockInsuficiente(faltantes))

        val totalBase = items.sumOfCup { (p, cant) -> p.precioVenta * cant }
        val aplicables = preajustes.filter { pa ->
            pa.activo && (pa.metodoPago == null || pa.metodoPago == metodoPago) && (pa.importeMinimo == null || totalBase >= pa.importeMinimo)
        }
        val ajustes = mutableMapOf<Long, Int>()
        val detalles = items.map { (p, cant) ->
            val bp = aplicables.filter { p.id in it.productoIds }.sumOf { it.puntosBasicos }.coerceAtLeast(-10_000)
            if (bp != 0) ajustes[p.id] = bp
            DetalleVenta(
                productoId = p.id,
                nombre = p.nombreCompleto, // 0.24.0: «Nombre · Descripción» congelado en la venta
                categoria = p.categoria,
                cantidad = cant,
                precioBase = p.precioVenta,
                precioUnitario = if (bp == 0) p.precioVenta else p.precioVenta.ajustar(bp),
                costoUnitario = if (p.esElaborado) costosReceta[p.id] ?: p.precioCosto else p.precioCosto,
            )
        }
        return AppResult.Ok(Cotizacion(metodoPago, detalles, ajustes, elaborados))
    }
}
