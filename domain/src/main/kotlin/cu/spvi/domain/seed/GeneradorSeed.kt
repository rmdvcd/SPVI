package cu.spvi.domain.seed

import cu.spvi.domain.model.ClaseArticulo
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.model.TipoMovimientoCaja
import kotlin.random.Random

data class LineaPlan(val clave: String, val cantidad: Long, val clase: ClaseArticulo)
data class VentaPlan(
    val lineas: List<LineaPlan>,
    val metodo: MetodoPago,
    val clienteClave: String?,
    val clienteFijo: Boolean,
    val numeroTransaccion: String,
    val anular: Boolean,
)
data class CajaPlan(val tipo: TipoMovimientoCaja, val importePesos: Long, val motivo: String)
data class PlanDia(val descansa: Boolean, val ventas: List<VentaPlan>, val caja: List<CajaPlan>, val descuadrePesos: Long)

/**
 * Plan puro y determinista de un día del seed (semilla fija 2907L): mismos datos siempre.
 * `indice` 0..547 (hace 548 días → ayer). Descansa `indice % 7 == 6`.
 */
class GeneradorSeed(val semilla: Long = 2907L) {
    fun planDia(indice: Int): PlanDia {
        val r = Random(semilla xor (indice.toLong() * 0x9E3779B9L))
        if (indice % 7 == 6) return PlanDia(descansa = true, ventas = emptyList(), caja = emptyList(), descuadrePesos = 0)
        val nVentas = 6 + r.nextInt(9)
        val ventas = (0 until nVentas).map { n -> venta(r, indice, n) }.toMutableList()
        // ~1.5% de las ventas se anulan (15% de los días con 1 anulada); nunca la única del día.
        if (ventas.size > 1 && r.nextInt(100) < 15) {
            val i = r.nextInt(ventas.size)
            ventas[i] = ventas[i].copy(anular = true)
        }
        val caja = if (indice % 7 == 3) listOf(
            CajaPlan(if (indice % 14 == 3) TipoMovimientoCaja.ENTRADA else TipoMovimientoCaja.SALIDA,
                (500 + r.nextInt(16) * 100).toLong(), if (indice % 14 == 3) "Aporte para sencillo" else "Pago a proveedor"),
        ) else emptyList()
        val descuadre = if (indice % 28 == 10 || indice % 28 == 24) (if (r.nextBoolean()) 1 else -1) * (50 + r.nextInt(8) * 50).toLong() else 0L
        return PlanDia(descansa = false, ventas = ventas, caja = caja, descuadrePesos = descuadre)
    }

    private fun venta(r: Random, indice: Int, n: Int): VentaPlan {
        val servicios = r.nextInt(100) < 22
        val nLineas = 1 + r.nextInt(4)
        val claves = if (servicios) CatalogoBodega.clavesServicios(r, nLineas)
        else CatalogoBodega.clavesProductos(r, nLineas)
        val lineas = claves.map { (clave, clase) -> LineaPlan(clave, (1 + r.nextInt(3)).toLong(), clase) }
        val transferencia = r.nextInt(100) < 30
        // Solo las transferencias exigen cliente: 60% fijo del catálogo, 40% eventual.
        val clienteClave = if (transferencia) {
            if (r.nextInt(100) < 60) CatalogoBodega.claveFija(r)
            else CatalogoBodega.PREFIJO_EVENTUAL + nombreEventual(r.nextInt(500))
        } else null
        return VentaPlan(
            lineas = lineas,
            metodo = if (transferencia) MetodoPago.TRANSFERENCIA else MetodoPago.EFECTIVO,
            clienteClave = clienteClave,
            clienteFijo = clienteClave != null && CatalogoBodega.esFijo(clienteClave),
            // Único por día (índice + nº), alfanumérico 8: el relleno evita violar el mínimo de 6.
            numeroTransaccion = "SD" + (indice * 100 + n).toString(36).uppercase().padStart(4, '0') + "X7",
            anular = false,
        )
    }

    /** Nombres eventuales solo con letras (`Validators.nombre` no admite dígitos). Base-26: A..Z, A A.. */
    internal fun nombreEventual(k: Int): String {
        var n = k
        val letras = StringBuilder()
        do { letras.append('A' + (n % 26)); n = n / 26 - 1 } while (n >= 0)
        return "Eventual " + letras.reversed()
    }
}
