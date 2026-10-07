package cu.spvi.domain.model

import cu.spvi.core.money.Cup

/**
 * Preajuste de precios ("Precios" en Inicio): ±[puntosBasicos] (1000 = +10 %) sobre los [productoIds]
 * cuando se cumplen sus condiciones:
 *  - [metodoPago]: solo con ese método (null = cualquiera).
 *  - [importeMinimo]: "envergadura de la venta" = total de la venta antes de ajustes ≥ este importe (null = sin mínimo).
 * Varios preajustes aplicables a un mismo producto SE SUMAN (suposición documentada).
 */
data class PreajustePrecios(
    val id: Long = 0,
    val nombre: String,
    val puntosBasicos: Int,
    val productoIds: Set<Long>,
    val metodoPago: MetodoPago? = null,
    val importeMinimo: Cup? = null,
    val activo: Boolean = true,
)
