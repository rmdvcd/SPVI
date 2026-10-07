package cu.spvi.domain.usecase

import cu.spvi.domain.model.Identificacion
import cu.spvi.domain.model.identidad

import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.quantity.Cantidad
import cu.spvi.core.time.Clock
import cu.spvi.domain.model.FiltroServicios
import cu.spvi.domain.model.Insumo
import cu.spvi.domain.model.LineaServicio
import cu.spvi.domain.model.Receta
import cu.spvi.domain.model.RecetaLinea
import cu.spvi.domain.model.Servicio
import cu.spvi.domain.model.ServicioDisponible
import cu.spvi.domain.model.VistaServicios
import cu.spvi.domain.repository.InsumoRepository
import cu.spvi.domain.repository.ServicioRepository
import cu.spvi.domain.service.InventarioFiltro
import cu.spvi.domain.service.Recetas
import cu.spvi.domain.validation.Validadores
import cu.spvi.core.result.toResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import cu.spvi.domain.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** P29: cálculos puros de la lista de Servicios (sin E/S). */
object ServiciosFiltro {

    fun disponible(s: Servicio, lineas: List<RecetaLinea>, insumos: Map<Long, Insumo>): ServicioDisponible {
        val ls = lineas.map { l ->
            val i = insumos[l.insumoId]
            LineaServicio(
                insumoId = l.insumoId, nombre = i?.nombre ?: "Insumo eliminado", simbolo = i?.unidad?.simbolo.orEmpty(),
                porVez = l.cantidad, disponible = i?.cantidad ?: Cantidad.ZERO, existe = i != null,
            )
        }
        val costo = if (lineas.isEmpty()) cu.spvi.core.money.Cup.ZERO
        else (Recetas.costo(Receta(s.id, lineas), insumos) as? AppResult.Ok)?.value
        return ServicioDisponible(s, ls, costo)
    }

    fun aplicar(
        servicios: List<Servicio>, recetas: Map<Long, List<RecetaLinea>>, insumos: Map<Long, Insumo>, f: FiltroServicios,
    ): VistaServicios {
        val activos = servicios.filterNot { it.eliminado }
        val q = InventarioFiltro.plano(f.texto)
        val items = activos.asSequence()
            .filter { f.tipo == null || it.tipo.trim().equals(f.tipo, ignoreCase = true) }
            .filter { s ->
                q.isEmpty() || InventarioFiltro.plano(s.nombre).contains(q) || InventarioFiltro.plano(s.tipo).contains(q) ||
                    s.descripcion?.let { InventarioFiltro.plano(it).contains(q) } == true
            }
            .map { disponible(it, recetas[it.id].orEmpty(), insumos) }
            .toList()
        return VistaServicios(items, activos.size, tipos(activos), Identificacion.serviciosPorDiferenciar(activos))
    }

    fun tipos(servicios: List<Servicio>): List<String> =
        servicios.filterNot { it.eliminado }.map { it.tipo.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }.sortedBy { InventarioFiltro.plano(it) }
}

/** Lista reactiva: servicios × insumos que consumen × existencias de insumos × filtro. */
class ObservarServicios @Inject constructor(
    private val servicios: ServicioRepository,
    private val insumos: InsumoRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** 0.30.0 (F1): cruza servicios × insumos y filtra la lista completa; fuera del hilo del colector. */
    operator fun invoke(filtro: Flow<FiltroServicios>): Flow<VistaServicios> =
        combine(servicios.observarTodos(), servicios.observarInsumos(), insumos.observarTodos(), filtro) { ss, rs, xs, f ->
            ServiciosFiltro.aplicar(ss, rs, xs.associateBy { it.id }, f)
        }.flowOn(io)

    /** Todos los activos (para la venta: importe, tope por insumos y costo). */
    fun disponibles(): Flow<Map<Long, ServicioDisponible>> =
        combine(servicios.observarTodos(), servicios.observarInsumos(), insumos.observarTodos()) { ss, rs, xs ->
            val mapa = xs.associateBy { it.id }
            ss.filterNot { it.eliminado }.associate { it.id to ServiciosFiltro.disponible(it, rs[it.id].orEmpty(), mapa) }
        }
}

/** Ficha al tocar un servicio. */
class ObtenerFichaServicio @Inject constructor(
    private val servicios: ServicioRepository,
    private val insumos: InsumoRepository,
) {
    suspend operator fun invoke(id: Long): AppResult<ServicioDisponible> {
        val s = servicios.obtener(id)?.takeUnless { it.eliminado } ?: return AppResult.Err(AppError.NoEncontrado)
        val lineas = servicios.insumos(id)
        val mapa = insumos.obtenerVarios(lineas.map { it.insumoId }).associateBy { it.id }
        return AppResult.Ok(ServiciosFiltro.disponible(s, lineas, mapa))
    }
}

/** Crea o edita un servicio y los insumos que consume (valida antes de escribir). */
class GuardarServicio @Inject constructor(
    private val servicios: ServicioRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(servicio: Servicio, insumos: List<RecetaLinea>): AppResult<Long> {
        val now = clock.now()
        val s = servicio.copy(
            nombre = servicio.nombre.trim(), tipo = servicio.tipo.trim(),
            descripcion = servicio.descripcion?.trim()?.ifEmpty { null },
        )
        Validadores.servicio(s, insumos).toResult().let { if (it is AppResult.Err) return it }
        // 0.24.0: dos servicios con el mismo nombre se distinguen por la descripción.
        errorIdentificacion(s.id, s.nombre, s.descripcion, servicios.observarTodos().first().map { it.identidad })
            ?.let { return AppResult.Err(it) }
        if (s.id == 0L) return servicios.crear(s.copy(creadoEn = now, actualizadoEn = now), insumos)
        val actual = servicios.obtener(s.id)?.takeUnless { it.eliminado } ?: return AppResult.Err(AppError.NoEncontrado)
        return when (val r = servicios.actualizar(s.copy(creadoEn = actual.creadoEn, actualizadoEn = now), insumos)) {
            is AppResult.Ok -> AppResult.Ok(s.id)
            is AppResult.Err -> r
        }
    }
}

/** Borrado (lógico) de varios servicios; sigue aunque alguno falle. */
class EliminarServicios @Inject constructor(private val servicios: ServicioRepository) {
    data class Resultado(val eliminados: Int, val fallidos: Int)

    suspend operator fun invoke(ids: Collection<Long>): Resultado {
        var ok = 0
        var mal = 0
        ids.distinct().forEach { if (servicios.eliminar(it) is AppResult.Ok) ok++ else mal++ }
        return Resultado(ok, mal)
    }
}
