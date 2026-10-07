package cu.spvi.domain.usecase

import cu.spvi.core.time.Dates
import cu.spvi.domain.repository.ServicioRepository
import cu.spvi.domain.service.Recetas
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.domain.model.FichaProducto
import cu.spvi.domain.model.FiltroInventario
import cu.spvi.domain.model.LineaFicha
import cu.spvi.domain.model.Producto
import cu.spvi.domain.model.VistaInventario
import cu.spvi.domain.repository.ExportadorDocumentos
import cu.spvi.domain.repository.FotoRepository
import cu.spvi.domain.repository.InsumoRepository
import cu.spvi.domain.repository.PreferenciasRepository
import cu.spvi.domain.repository.ProductoRepository
import cu.spvi.domain.service.FormatoExport
import cu.spvi.domain.service.InventarioFiltro
import cu.spvi.domain.service.Stock
import cu.spvi.domain.service.TablasExport
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId
import cu.spvi.domain.model.Preferencias
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Tabla del Inventario reactiva: productos × preferencias (niveles, días de aviso) × filtro. */
class ObservarInventario @Inject constructor(
    private val productos: ProductoRepository,
    private val insumos: InsumoRepository,
    private val preferencias: PreferenciasRepository,
    private val clock: Clock,
) {
    operator fun invoke(filtro: Flow<FiltroInventario>, zone: ZoneId = ZoneId.systemDefault()): Flow<VistaInventario> =
        combine(productos.observarTodos(), alcanceElaborados(productos, insumos), insumos.observarTodos(), preferencias.preferencias, filtro) { ps, al, xs, pref, f ->
            InventarioFiltro.aplicar(ps, f, pref.niveles, Dates.localDate(clock.now(), zone), Preferencias.DIAS_AVISO_CADUCIDAD, al, xs)
        }
}

/** Ficha al tocar un producto: datos + receta resuelta (Elaborado) + indicadores. */
class ObtenerFichaProducto @Inject constructor(
    private val productos: ProductoRepository,
    private val insumos: InsumoRepository,
    private val preferencias: PreferenciasRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: Long, zone: ZoneId = ZoneId.systemDefault()): AppResult<FichaProducto> {
        val p = productos.obtener(id)?.takeUnless { it.eliminado } ?: return AppResult.Err(AppError.NoEncontrado)
        val pref = preferencias.preferencias.first()
        var alcanza: Long? = null
        val lineas = if (p.esElaborado) {
            val r = productos.receta(id)
            val xs = r?.let { rec -> insumos.obtenerVarios(rec.lineas.map { it.insumoId }).associateBy { it.id } }.orEmpty()
            alcanza = r?.let { Recetas.maxUnidades(it, xs) } ?: 0
            r?.lineas.orEmpty().mapNotNull { l ->
                xs[l.insumoId]?.let { LineaFicha(it.id, it.nombre, l.cantidad, it.unidad.simbolo, it.precio.porMilesimas(l.cantidad.milesimas)) }
            }
        } else emptyList()
        val hoy = Dates.localDate(clock.now(), zone)
        return AppResult.Ok(FichaProducto(p, lineas, Stock.nivel(p, pref.niveles), InventarioFiltro.caducidad(p, hoy, Preferencias.DIAS_AVISO_CADUCIDAD), alcanza))
    }
}

/** Borrado de los productos marcados. Continúa aunque uno falle y devuelve cuántos se eliminaron. */
class EliminarProductos @Inject constructor(private val repo: ProductoRepository) {
    data class Resultado(val eliminados: Int, val fallidos: Int)

    suspend operator fun invoke(ids: Collection<Long>): Resultado {
        var ok = 0
        ids.distinct().forEach { if (repo.eliminar(it) is AppResult.Ok) ok++ }
        return Resultado(ok, ids.distinct().size - ok)
    }
}

/** Botón Exportar: la selección (o, sin selección, lo que se ve filtrado) en PDF, Excel o texto. */
class ExportarInventario @Inject constructor(private val exportador: ExportadorDocumentos) {
    suspend operator fun invoke(productos: List<Producto>, formato: FormatoExport, destino: OutputStream): AppResult<Unit> {
        if (productos.isEmpty()) return AppResult.Err(AppError.Validacion("productos", AppError.Regla.REQUERIDO))
        return exportador.exportar(listOf(TablasExport.inventario(productos)), formato, destino)
    }
}

/** Compartir la ficha en PDF (tabla Campo/Valor). El texto y la imagen se generan en presentación. */
class ExportarFicha @Inject constructor(private val exportador: ExportadorDocumentos) {
    suspend operator fun invoke(ficha: FichaProducto, destino: OutputStream): AppResult<Unit> =
        exportador.exportar(listOf(TablasExport.ficha(ficha)), FormatoExport.PDF, destino)
}

/** Limpieza de fotos huérfanas (productos borrados o formularios abandonados). */
class LimpiarFotos @Inject constructor(
    private val productos: ProductoRepository,
    private val fotos: FotoRepository,
    private val servicios: ServicioRepository,
) {
    suspend operator fun invoke() {
        // P29: también las fotos de los servicios están en uso.
        val enUso = (productos.observarTodos().first().mapNotNull { it.fotoUri } +
            servicios.observarTodos().first().mapNotNull { it.fotoUri }).toSet()
        fotos.limpiarHuerfanas(enUso)
    }
}
