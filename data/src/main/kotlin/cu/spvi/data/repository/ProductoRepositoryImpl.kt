package cu.spvi.data.repository

import cu.spvi.domain.model.nombreCompleto
import cu.spvi.domain.model.Identificacion
import cu.spvi.core.money.Cup
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.db.abortar
import cu.spvi.data.db.entity.MovimientoEntity
import cu.spvi.data.db.tx
import cu.spvi.data.mapper.toDomain
import cu.spvi.data.mapper.toEntities
import cu.spvi.data.mapper.toEntity
import cu.spvi.data.mapper.toReceta
import cu.spvi.domain.model.Producto
import cu.spvi.domain.model.Receta
import cu.spvi.domain.model.TipoEntidad
import cu.spvi.domain.model.TipoMovimiento
import cu.spvi.domain.repository.ProductoRepository
import cu.spvi.domain.service.Stock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class ProductoRepositoryImpl @Inject constructor(
    private val db: SpviDatabase,
    private val clock: Clock,
) : ProductoRepository {

    private val productos = db.productoDao()
    private val recetas = db.recetaDao()
    private val movimientos = db.movimientoDao()
    private val turnos = db.turnoDao()

    override fun observarTodos(): Flow<List<Producto>> = productos.observarActivos().map { l -> l.map { it.toDomain() } }
    override fun observar(id: Long): Flow<Producto?> = productos.observar(id).map { it?.toDomain() }.distinctUntilChanged()
    override suspend fun obtener(id: Long) = productos.obtener(id)?.toDomain()
    override suspend fun obtenerVarios(ids: Collection<Long>) =
        if (ids.isEmpty()) emptyList() else ids.distinct().chunked(MAX_VARS).flatMap { productos.obtenerVarios(it) }.map { it.toDomain() }
    override suspend fun categoriasEnUso() = productos.categorias()

    override suspend fun crear(producto: Producto, receta: Receta?): AppResult<Long> = db.tx {
        val id = productos.insertar(producto.toEntity().copy(id = 0, eliminado = false))
        receta?.let { recetas.insertarTodas(it.toEntities(id)) }
        if (producto.cantidad != 0L) mov(TipoMovimiento.ALTA, id, producto.nombreCompleto, producto.cantidad, producto.cantidad)
        id
    }

    override suspend fun actualizar(producto: Producto, receta: Receta?): AppResult<Unit> = actualizar(producto, receta, null)

    override suspend fun actualizar(producto: Producto, receta: Receta?, cantidadLeida: Long?): AppResult<Unit> = db.tx {
        val viejo = productos.obtener(producto.id)?.takeUnless { it.eliminado } ?: abortar(AppError.NoEncontrado)
        val p = producto.copy(cantidad = Stock.cantidadAlGuardar(cantidadLeida, producto.cantidad, viejo.cantidad))
        productos.actualizar(p.toEntity().copy(creadoEn = viejo.creadoEn, eliminado = false))
        recetas.borrarDe(p.id)
        receta?.let { recetas.insertarTodas(it.toEntities(p.id)) }
        val delta = p.cantidad - viejo.cantidad
        if (delta != 0L) mov(TipoMovimiento.AJUSTE, p.id, p.nombreCompleto, delta, p.cantidad, "Edición")
    }

    override suspend fun eliminar(id: Long): AppResult<Unit> = db.tx {
        val p = productos.obtener(id)?.takeUnless { it.eliminado } ?: abortar(AppError.NoEncontrado)
        productos.actualizar(p.copy(eliminado = true, cantidad = 0, actualizadoEn = clock.now().toEpochMilli()))
        recetas.borrarDe(id)
        if (p.cantidad != 0L) mov(TipoMovimiento.BAJA, id, Identificacion.nombreCompleto(p.nombre, p.descripcion), -p.cantidad, 0)
    }

    override suspend fun ajustarStock(id: Long, delta: Long, nota: String?): AppResult<Long> = db.tx {
        val p = productos.obtener(id)?.takeUnless { it.eliminado } ?: abortar(AppError.NoEncontrado)
        if (productos.sumarStock(id, delta, clock.now().toEpochMilli()) == 0) abortar(AppError.StockInsuficiente(listOf(Identificacion.nombreCompleto(p.nombre, p.descripcion))))
        val nueva = productos.cantidad(id) ?: 0
        mov(TipoMovimiento.AJUSTE, id, Identificacion.nombreCompleto(p.nombre, p.descripcion), delta, nueva, nota)
        nueva
    }

    override suspend fun actualizarPrecios(nuevos: Map<Long, Cup>): AppResult<Unit> = db.tx {
        val ahora = clock.now().toEpochMilli()
        nuevos.forEach { (id, precio) ->
            val actual = productos.obtener(id)?.takeUnless { it.eliminado } ?: abortar(AppError.NoEncontrado)
            if (precio <= Cup(actual.precioCostoCent)) abortar(AppError.Validacion("precioVenta", AppError.Regla.RANGO))
            if (productos.actualizarPrecio(id, precio.centavos, ahora) == 0) abortar(AppError.NoEncontrado)
        }
    }

    override suspend fun receta(productoId: Long): Receta? =
        recetas.lineas(productoId).takeIf { it.isNotEmpty() }?.toReceta(productoId)

    override suspend fun productosQueUsan(insumoId: Long): List<Producto> =
        obtenerVarios(recetas.productosQueUsan(insumoId)).filterNot { it.eliminado }

    /** Debe llamarse dentro de una transacción: asocia el movimiento al turno abierto en ese instante. */
    private suspend fun mov(
        tipo: TipoMovimiento, entidadId: Long, nombre: String, delta: Long, existencia: Long,
        nota: String? = null, entidad: TipoEntidad = TipoEntidad.PRODUCTO,
    ) {
        movimientos.insertar(
            MovimientoEntity(
                fecha = clock.now().toEpochMilli(), tipo = tipo.name, entidad = entidad.name, entidadId = entidadId,
                nombre = nombre, delta = delta, existencia = existencia, turnoId = turnos.turnoParaMovimiento(), ventaId = null, nota = nota,
                hechoPor = hechoPorActual(),
            ),
        )
    }

    private companion object {
        /** Límite de variables SQLite (999 en versiones antiguas) para cláusulas IN. */
        const val MAX_VARS = 900
    }
}
