package cu.spvi.data.repository

import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.db.abortar
import cu.spvi.data.db.tx
import cu.spvi.data.mapper.productosEntities
import cu.spvi.data.mapper.toDomain
import cu.spvi.data.mapper.toEntity
import cu.spvi.domain.model.PreajustePrecios
import cu.spvi.domain.repository.PreciosRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PreciosRepositoryImpl @Inject constructor(private val db: SpviDatabase) : PreciosRepository {

    private val dao = db.preciosDao()
    private val productos = db.productoDao()

    override fun observarPreajustes(): Flow<List<PreajustePrecios>> = dao.observarPreajustes().map { l -> l.map { it.toDomain() } }
    override suspend fun preajustesActivos() = dao.activos().map { it.toDomain() }

    /** Los productos inexistentes o eliminados se descartan en silencio (pudieron borrarse mientras se editaba). */
    override suspend fun guardarPreajuste(p: PreajustePrecios): AppResult<Long> = db.tx {
        val id = if (p.id == 0L) dao.insertar(p.toEntity().copy(id = 0)) else p.id.also { dao.guardar(p.toEntity()) }
        dao.borrarProductos(id)
        val validos = p.productoIds.toList().chunked(900).flatMap { productos.obtenerVarios(it) }
            .filterNot { it.eliminado }.map { it.id }.toSet()
        dao.insertarProductos(p.copy(productoIds = validos).productosEntities(id))
        id
    }

    override suspend fun eliminarPreajuste(id: Long): AppResult<Unit> = db.tx {
        if (dao.borrar(id) == 0) abortar(AppError.NoEncontrado)
    }
}
