package cu.spvi.domain.usecase

import cu.spvi.domain.model.nombreCompleto
import cu.spvi.domain.repository.ServicioRepository
import cu.spvi.domain.service.Recetas
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.ElaboradoDisponible
import cu.spvi.domain.model.FichaInsumo
import cu.spvi.domain.model.FiltroInsumos
import cu.spvi.domain.model.Producto
import cu.spvi.domain.model.Receta
import cu.spvi.domain.model.UsoEnReceta
import cu.spvi.domain.model.VistaInsumos
import cu.spvi.domain.repository.InsumoRepository
import cu.spvi.domain.repository.PreferenciasRepository
import cu.spvi.domain.repository.ProductoRepository
import cu.spvi.domain.service.InsumosFiltro
import cu.spvi.domain.service.Stock
import cu.spvi.domain.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/*
 * Elaboración reutiliza los casos de uso existentes para escribir: GuardarInsumo (crear/editar y recalcular
 * el costo de los Elaborados) y ExportarInsumos. Aquí solo se añaden las lecturas que la pantalla necesita.
 * P26: no hay acción de producir; los insumos de un Elaborado se descuentan al venderlo.
 */

/** Recetas de los Elaborados activos. Se recalcula cuando cambian los productos (editar receta los emite). */
internal fun recetasActivas(productos: ProductoRepository): Flow<List<Pair<Producto, Receta?>>> =
    productos.observarTodos().map { ps ->
        ps.filter { it.esElaborado && !it.eliminado }.map { it to productos.receta(it.id) }
    }

/** productoId → «Alcanza para N» de cada Elaborado activo; se recalcula con productos, recetas e insumos (P26). */
internal fun alcanceElaborados(productos: ProductoRepository, insumos: InsumoRepository): Flow<Map<Long, Long>> =
    combine(recetasActivas(productos), insumos.observarTodos()) { rs, xs ->
        val mapa = xs.associateBy { it.id }
        rs.associate { (p, r) -> p.id to (r?.let { Recetas.maxUnidades(it, mapa) } ?: 0L) }
    }

/** Lista de insumos reactiva: insumos × recetas × preferencias (niveles) × filtro. */
class ObservarElaboracion @Inject constructor(
    private val insumos: InsumoRepository,
    private val productos: ProductoRepository,
    private val preferencias: PreferenciasRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** 0.30.0 (F1): cruza insumos × recetas y filtra la lista completa; fuera del hilo del colector. */
    operator fun invoke(filtro: Flow<FiltroInsumos>): Flow<VistaInsumos> =
        combine(insumos.observarTodos(), recetasActivas(productos), preferencias.preferencias, filtro) { xs, rs, pref, f ->
            InsumosFiltro.aplicar(xs, InsumosFiltro.usos(rs.mapNotNull { it.second }), f, pref.niveles)
        }.flowOn(io)
}

/** Sección Elaborados: cada Elaborado con su receta, costo y «Alcanza para N» (más recientes primero). */
class ObservarElaborados @Inject constructor(
    private val insumos: InsumoRepository,
    private val productos: ProductoRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** 0.30.0 (F1): «Alcanza para N» recorre todas las recetas por cada cambio de insumos; fuera del hilo del colector. */
    operator fun invoke(): Flow<List<ElaboradoDisponible>> =
        combine(recetasActivas(productos), insumos.observarTodos()) { rs, xs ->
            val mapa = xs.associateBy { it.id }
            rs.map { (p, r) -> InsumosFiltro.disponible(p, r, mapa) }
        }.flowOn(io)
}

/** Ficha al tocar un insumo: datos, nivel y en qué Elaborados se usa (y cuánto por unidad). */
class ObtenerFichaInsumo @Inject constructor(
    private val insumos: InsumoRepository,
    private val productos: ProductoRepository,
    private val preferencias: PreferenciasRepository,
    private val servicios: ServicioRepository,
) {
    suspend operator fun invoke(id: Long): AppResult<FichaInsumo> {
        val i = insumos.obtener(id) ?: return AppResult.Err(AppError.NoEncontrado)
        val usos = productos.productosQueUsan(id).filterNot { it.eliminado }.mapNotNull { p ->
            productos.receta(p.id)?.lineas?.firstOrNull { it.insumoId == id }?.let { UsoEnReceta(p.id, p.nombreCompleto, it.cantidad) }
        }.sortedBy { it.nombre.lowercase() } + servicios.serviciosQueUsan(id).mapNotNull { s ->
            // P29: también los servicios que lo gastan.
            servicios.insumos(s.id).firstOrNull { it.insumoId == id }?.let { UsoEnReceta(s.id, s.nombreCompleto, it.cantidad, servicio = true) }
        }.sortedBy { it.nombre.lowercase() }
        return AppResult.Ok(FichaInsumo(i, Stock.nivel(i, preferencias.preferencias.first().niveles), usos))
    }
}

/**
 * Borrado de los insumos marcados. Sigue aunque alguno falle. Los que usa una receta no se borran
 * (el repositorio responde EnUso) y se devuelven sus nombres para explicarlo.
 */
class EliminarInsumos @Inject constructor(private val repo: InsumoRepository) {
    data class Resultado(val eliminados: Int, val enUso: List<String>, val fallidos: Int)

    suspend operator fun invoke(ids: Collection<Long>): Resultado {
        var ok = 0
        var fallidos = 0
        val enUso = mutableListOf<String>()
        ids.distinct().forEach { id ->
            val nombre = repo.obtener(id)?.nombre
            when (val r = repo.eliminar(id)) {
                is AppResult.Ok -> ok++
                is AppResult.Err -> if (r.error is AppError.EnUso && nombre != null) enUso += nombre else fallidos++
            }
        }
        return Resultado(ok, enUso, fallidos)
    }
}
