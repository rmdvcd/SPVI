package cu.spvi.data.repository

import cu.spvi.domain.model.Identificacion
import cu.spvi.core.quantity.Cantidad
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.db.abortar
import cu.spvi.data.db.entity.MovimientoEntity
import cu.spvi.data.db.tx
import cu.spvi.data.mapper.toDomain
import cu.spvi.data.mapper.toEntity
import cu.spvi.domain.model.Insumo
import cu.spvi.domain.model.TipoEntidad
import cu.spvi.domain.model.TipoMovimiento
import cu.spvi.domain.repository.InsumoRepository
import cu.spvi.domain.service.Stock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class InsumoRepositoryImpl @Inject constructor(
    private val db: SpviDatabase,
    private val clock: Clock,
) : InsumoRepository {

    private val insumos = db.insumoDao()
    private val recetas = db.recetaDao()
    private val productos = db.productoDao()
    private val movimientos = db.movimientoDao()
    private val turnos = db.turnoDao()
    private val servicios = db.servicioDao()

    override fun observarTodos(): Flow<List<Insumo>> = insumos.observarTodos().map { l -> l.map { it.toDomain() } }
    override suspend fun obtener(id: Long) = insumos.obtener(id)?.toDomain()
    override suspend fun obtenerVarios(ids: Collection<Long>) =
        if (ids.isEmpty()) emptyList() else ids.distinct().chunked(900).flatMap { insumos.obtenerVarios(it) }.map { it.toDomain() }
    override suspend fun todos() = insumos.todos().map { it.toDomain() }

    override suspend fun crear(insumo: Insumo): AppResult<Long> = db.tx {
        val id = insumos.insertar(insumo.toEntity().copy(id = 0))
        if (insumo.cantidad.milesimas != 0L) mov(TipoMovimiento.ALTA, id, insumo.nombre, insumo.cantidad.milesimas, insumo.cantidad.milesimas)
        id
    }

    override suspend fun actualizar(insumo: Insumo): AppResult<Unit> = actualizar(insumo, null)

    override suspend fun actualizar(insumo: Insumo, cantidadLeida: Cantidad?): AppResult<Unit> = db.tx {
        val viejo = insumos.obtener(insumo.id) ?: abortar(AppError.NoEncontrado)
        val i = insumo.copy(cantidad = Cantidad(Stock.cantidadAlGuardar(cantidadLeida?.milesimas, insumo.cantidad.milesimas, viejo.cantidadMil)))
        insumos.actualizar(i.toEntity().copy(creadoEn = viejo.creadoEn))
        val delta = i.cantidad.milesimas - viejo.cantidadMil
        if (delta != 0L) mov(TipoMovimiento.AJUSTE, i.id, i.nombre, delta, i.cantidad.milesimas, "Edición")
    }

    override suspend fun eliminar(id: Long): AppResult<Unit> = db.tx {
        val i = insumos.obtener(id) ?: abortar(AppError.NoEncontrado)
        val usos = recetas.productosQueUsan(id).takeIf { it.isNotEmpty() }
            ?.let { ids -> productos.obtenerVarios(ids).filterNot { it.eliminado }.map { Identificacion.nombreCompleto(it.nombre, it.descripcion) } }.orEmpty() +
            servicios.serviciosQueUsan(id).takeIf { it.isNotEmpty() }
                ?.let { ids -> servicios.obtenerVarios(ids).filterNot { it.eliminado }.map { Identificacion.nombreCompleto(it.nombre, it.descripcion) } }.orEmpty() // P29
        if (usos.isNotEmpty()) abortar(AppError.EnUso(usos.joinToString(", ")))
        insumos.borrar(id)
        if (i.cantidadMil != 0L) mov(TipoMovimiento.BAJA, id, i.nombre, -i.cantidadMil, 0)
    }

    override suspend fun ajustarStock(id: Long, delta: Cantidad, nota: String?): AppResult<Cantidad> = db.tx {
        val i = insumos.obtener(id) ?: abortar(AppError.NoEncontrado)
        if (insumos.sumarStock(id, delta.milesimas, clock.now().toEpochMilli()) == 0) abortar(AppError.StockInsuficiente(listOf(i.nombre)))
        val nueva = insumos.cantidad(id) ?: 0
        mov(TipoMovimiento.AJUSTE, id, i.nombre, delta.milesimas, nueva, nota)
        Cantidad(nueva)
    }

    private suspend fun mov(tipo: TipoMovimiento, id: Long, nombre: String, deltaMil: Long, existenciaMil: Long, nota: String? = null) {
        movimientos.insertar(
            MovimientoEntity(
                fecha = clock.now().toEpochMilli(), tipo = tipo.name, entidad = TipoEntidad.INSUMO.name, entidadId = id,
                nombre = nombre, delta = deltaMil, existencia = existenciaMil, turnoId = turnos.turnoParaMovimiento(), ventaId = null, nota = nota,
                hechoPor = hechoPorActual(),
            ),
        )
    }
}
