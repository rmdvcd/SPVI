package cu.spvi.data.sync

import cu.spvi.core.money.Cup
import cu.spvi.core.quantity.Cantidad
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.data.db.entity.EmpleadoEntity
import cu.spvi.data.dto.toDomain
import cu.spvi.data.repository.InsumoRepositoryImpl
import cu.spvi.data.repository.OrigenMovimiento
import cu.spvi.data.repository.PreciosRepositoryImpl
import cu.spvi.data.repository.ProductoRepositoryImpl
import cu.spvi.data.repository.ServicioRepositoryImpl
import cu.spvi.domain.model.PermisoEmpleado
import cu.spvi.domain.model.identidad
import cu.spvi.domain.usecase.errorIdentificacion
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * P37: en la PRINCIPAL, ejecuta lo que pide una secundaria con los repositorios de siempre (mismas validaciones y
 * transacciones). El permiso se comprueba AQUÍ con los datos de la principal: la secundaria no puede saltárselo.
 */
@Singleton
class EjecutorComandos @Inject constructor(
    private val productos: ProductoRepositoryImpl,
    private val insumos: InsumoRepositoryImpl,
    private val servicios: ServicioRepositoryImpl,
    private val precios: PreciosRepositoryImpl,
) {
    private val memoria = MemoriaComandos()

    /** 0.21.7 (P-e): un comando reenviado con la misma clave no se aplica dos veces ([MemoriaComandos]). */
    suspend fun resultado(c: Comando, empleado: EmpleadoEntity): ResultadoComando =
        memoria.unaVez(empleado.id, c.id, c.clave) { resultado(c.id, empleado, c.accion) }

    suspend fun resultado(id: Long, empleado: EmpleadoEntity, accion: Accion): ResultadoComando {
        val permisos = PermisoEmpleado.deNombres(empleado.permisos.split(','))
        if (permisoDe(accion) !in permisos) return ResultadoComando(id, error = ErroresRemotos.PERMISO)
        val r: AppResult<Long?> = try {
            // 0.19.3: los movimientos de stock van al turno de la secundaria, nunca al de la principal.
            withContext(OrigenMovimiento(empleado.id, empleado.nombre)) { aplicar(accion, empleado.nombre) }
        } catch (e: IllegalArgumentException) {
            AppResult.Err(AppError.Validacion("datos"))
        }
        return when (r) {
            is AppResult.Ok -> ResultadoComando(id, valor = r.value)
            is AppResult.Err -> ResultadoComando(id, error = codigo(r.error))
        }
    }

    private suspend fun aplicar(a: Accion, quien: String): AppResult<Long?> {
        val nota = { n: String? -> listOfNotNull(n?.takeIf { it.isNotBlank() }, "Desde la app de $quien").joinToString(" · ") }
        return when (a) {
            is GuardarProducto -> {
                // Las fotos no viajan: al editar desde una secundaria se conserva la de la principal.
                val p = a.producto.toDomain().let { it.copy(fotoUri = productos.obtener(it.id)?.fotoUri) }
                val receta = a.receta?.toDomain()
                // 0.24.0: la secundaria ya lo comprobó con su copia; aquí se repite por si otra app guardó antes.
                errorIdentificacion(p.id, p.nombre, p.descripcion, productos.observarTodos().first().map { it.identidad })
                    ?.let { return AppResult.Err(it) }
                if (p.id == 0L) productos.crear(p, receta).ok() else productos.actualizar(p, receta, a.cantidadLeida).conId(p.id)
            }
            is EliminarProducto -> productos.eliminar(a.id).conId(a.id)
            is AjustarStockProducto -> productos.ajustarStock(a.id, a.delta, nota(a.nota)).ok()
            is CambiarPrecios -> productos.actualizarPrecios(a.precios.mapValues { Cup(it.value) }).conId(null)
            is GuardarInsumo -> {
                val i = a.insumo.toDomain()
                if (i.id == 0L) insumos.crear(i).ok() else insumos.actualizar(i, a.cantidadLeidaMil?.let { Cantidad(it) }).conId(i.id)
            }
            is EliminarInsumo -> insumos.eliminar(a.id).conId(a.id)
            is AjustarStockInsumo -> when (val r = insumos.ajustarStock(a.id, Cantidad(a.deltaMil), nota(a.nota))) {
                is AppResult.Ok -> AppResult.Ok(r.value.milesimas)
                is AppResult.Err -> r
            }
            is GuardarServicio -> {
                val (s0, lineas) = a.servicio.toDomain()
                val s = s0.copy(fotoUri = servicios.obtener(s0.id)?.fotoUri)
                errorIdentificacion(s.id, s.nombre, s.descripcion, servicios.observarTodos().first().map { it.identidad })
                    ?.let { return AppResult.Err(it) }
                if (s.id == 0L) servicios.crear(s, lineas).ok() else servicios.actualizar(s, lineas).conId(s.id)
            }
            is EliminarServicio -> servicios.eliminar(a.id).conId(a.id)
            is GuardarPreajuste -> precios.guardarPreajuste(a.preajuste.toDomain()).ok()
            is EliminarPreajuste -> precios.eliminarPreajuste(a.id).conId(a.id)
        }
    }

    private fun AppResult<Long>.ok(): AppResult<Long?> = when (this) {
        is AppResult.Ok -> AppResult.Ok(value)
        is AppResult.Err -> this
    }

    private fun AppResult<Unit>.conId(id: Long?): AppResult<Long?> = when (this) {
        is AppResult.Ok -> AppResult.Ok(id)
        is AppResult.Err -> this
    }

    companion object {
        fun permisoDe(a: Accion): PermisoEmpleado = when (a) {
            is CambiarPrecios, is GuardarPreajuste, is EliminarPreajuste -> PermisoEmpleado.CAMBIAR_PRECIOS
            else -> PermisoEmpleado.EDITAR_INVENTARIO
        }

        fun codigo(e: AppError): String = when (e) {
            AppError.SinPermiso -> ErroresRemotos.PERMISO
            AppError.NoEncontrado -> ErroresRemotos.NO_ENCONTRADO
            is AppError.StockInsuficiente -> ErroresRemotos.STOCK
            is AppError.EnUso -> ErroresRemotos.EN_USO
            is AppError.Duplicado -> ErroresRemotos.DUPLICADO + e.campo
            is AppError.Validacion -> ErroresRemotos.VALIDACION + e.campo + ":" + e.regla.name
            else -> ErroresRemotos.OTRO
        }

        /** Inverso de [codigo] en la secundaria (los formularios muestran el mismo mensaje que en la principal). */
        fun error(codigo: String): AppError = when {
            codigo == ErroresRemotos.PERMISO -> AppError.SinPermiso
            codigo == ErroresRemotos.NO_ENCONTRADO -> AppError.NoEncontrado
            codigo == ErroresRemotos.STOCK -> AppError.StockInsuficiente()
            codigo == ErroresRemotos.EN_USO -> AppError.EnUso("principal")
            codigo.startsWith(ErroresRemotos.DUPLICADO) -> AppError.Duplicado(codigo.removePrefix(ErroresRemotos.DUPLICADO))
            codigo.startsWith(ErroresRemotos.VALIDACION) -> {
                val partes = codigo.removePrefix(ErroresRemotos.VALIDACION).split(':')
                AppError.Validacion(partes[0], AppError.Regla.entries.firstOrNull { it.name == partes.getOrNull(1) } ?: AppError.Regla.FORMATO)
            }
            else -> AppError.Desconocido()
        }
    }
}
