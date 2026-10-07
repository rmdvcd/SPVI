package cu.spvi.data.repository

import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.db.abortar
import cu.spvi.data.db.entity.ServicioInsumoEntity
import cu.spvi.data.db.tx
import cu.spvi.data.mapper.toDomain
import cu.spvi.data.mapper.toEntity
import cu.spvi.domain.model.RecetaLinea
import cu.spvi.domain.model.Servicio
import cu.spvi.domain.repository.ServicioRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** P29: servicios + insumos que consumen, siempre escritos juntos en una transacción. */
@Singleton
class ServicioRepositoryImpl @Inject constructor(
    private val db: SpviDatabase,
    private val clock: Clock,
) : ServicioRepository {

    private val dao = db.servicioDao()

    override fun observarTodos(): Flow<List<Servicio>> = dao.observarTodos().map { l -> l.map { it.toDomain() } }

    override fun observarInsumos(): Flow<Map<Long, List<RecetaLinea>>> =
        dao.observarInsumos().map { l -> l.groupBy({ it.servicioId }, { it.toDomain() }) }

    override suspend fun obtener(id: Long) = dao.obtener(id)?.toDomain()
    override suspend fun obtenerVarios(ids: Collection<Long>) =
        if (ids.isEmpty()) emptyList() else ids.distinct().chunked(900).flatMap { dao.obtenerVarios(it) }.map { it.toDomain() }
    override suspend fun insumos(servicioId: Long) = dao.insumos(servicioId).map { it.toDomain() }
    override suspend fun tiposEnUso() = dao.tiposEnUso()

    override suspend fun crear(servicio: Servicio, insumos: List<RecetaLinea>): AppResult<Long> = db.tx {
        val id = dao.insertar(servicio.toEntity().copy(id = 0, eliminado = false))
        guardarInsumos(id, insumos)
        id
    }

    override suspend fun actualizar(servicio: Servicio, insumos: List<RecetaLinea>): AppResult<Unit> = db.tx {
        val viejo = dao.obtener(servicio.id)?.takeUnless { it.eliminado } ?: abortar(AppError.NoEncontrado)
        dao.actualizar(servicio.toEntity().copy(creadoEn = viejo.creadoEn, eliminado = false))
        dao.borrarInsumosDe(servicio.id)
        guardarInsumos(servicio.id, insumos)
    }

    /** Lógico: las ventas guardan nombre e importe congelados. Se sueltan sus insumos para poder borrarlos. */
    override suspend fun eliminar(id: Long): AppResult<Unit> = db.tx {
        if (dao.marcarEliminado(id, clock.now().toEpochMilli()) == 0) abortar(AppError.NoEncontrado)
        dao.borrarInsumosDe(id)
    }

    override suspend fun serviciosQueUsan(insumoId: Long): List<Servicio> =
        obtenerVarios(dao.serviciosQueUsan(insumoId)).filterNot { it.eliminado }

    private suspend fun guardarInsumos(id: Long, insumos: List<RecetaLinea>) {
        if (insumos.isNotEmpty()) dao.insertarInsumos(insumos.map { ServicioInsumoEntity(id, it.insumoId, it.cantidad.milesimas) })
    }
}
