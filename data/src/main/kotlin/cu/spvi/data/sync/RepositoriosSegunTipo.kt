package cu.spvi.data.sync

import cu.spvi.core.money.Cup
import cu.spvi.core.quantity.Cantidad
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.data.dto.toDto
import cu.spvi.data.repository.InsumoRepositoryImpl
import cu.spvi.data.repository.PreciosRepositoryImpl
import cu.spvi.data.repository.ProductoRepositoryImpl
import cu.spvi.data.repository.ServicioRepositoryImpl
import cu.spvi.data.repository.TurnoRepositoryImpl
import cu.spvi.data.repository.VentaRepositoryImpl
import cu.spvi.domain.model.ElaboradoEnVenta
import cu.spvi.domain.model.Insumo
import cu.spvi.domain.model.PermisoEmpleado
import cu.spvi.domain.model.PreajustePrecios
import cu.spvi.domain.model.Producto
import cu.spvi.domain.model.Receta
import cu.spvi.domain.model.RecetaLinea
import cu.spvi.domain.model.Servicio
import cu.spvi.domain.model.TipoApp
import cu.spvi.domain.model.Turno
import cu.spvi.domain.model.Venta
import cu.spvi.domain.repository.InsumoRepository
import cu.spvi.domain.repository.PreciosRepository
import cu.spvi.domain.repository.ProductoRepository
import cu.spvi.domain.repository.ServicioRepository
import cu.spvi.domain.repository.TurnoRepository
import cu.spvi.domain.repository.VentaRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * P37: los repositorios que ve la app según su tipo.
 *  - PRINCIPAL: exactamente los de siempre.
 *  - SECUNDARIA: lecturas locales (copia del catálogo); los cambios al catálogo se piden a la principal (que comprueba
 *    el permiso con SUS datos) y llegan de vuelta con la sincronización. Vender y turnos son locales y se envían.
 * El permiso también se mira aquí para responder al momento sin red; la UI además oculta lo que no se puede hacer.
 */
@Singleton
class GuardiaSync @Inject constructor(private val config: ConfigSync, val cliente: ClienteSync) {
    /** null = esta app es la principal (hacer en local). */
    suspend fun secundaria(): DatosSecundaria? =
        if (config.tipo.first() == TipoApp.SECUNDARIA) config.secundariaActual() else null

    suspend fun <T> remoto(p: PermisoEmpleado, local: suspend () -> AppResult<T>, accion: () -> Accion, conv: (Long?) -> T): AppResult<T> {
        val d = secundaria() ?: return local()
        if (p !in d.permisosEmpleado) return AppResult.Err(AppError.SinPermiso)
        return when (val r = cliente.comando(accion())) {
            is AppResult.Ok -> AppResult.Ok(conv(r.value))
            is AppResult.Err -> r
        }
    }
}

private fun Producto.sinFoto() = copy(fotoUri = null)
private fun Servicio.sinFoto() = copy(fotoUri = null)

@Singleton
class ProductoRepositorySegunTipo @Inject constructor(
    private val local: ProductoRepositoryImpl,
    private val g: GuardiaSync,
) : ProductoRepository by local {
    override suspend fun crear(producto: Producto, receta: Receta?): AppResult<Long> =
        g.remoto(PermisoEmpleado.EDITAR_INVENTARIO, { local.crear(producto, receta) },
            { GuardarProducto(producto.sinFoto().toDto(), receta?.toDto()) }) { it ?: 0L }

    override suspend fun actualizar(producto: Producto, receta: Receta?): AppResult<Unit> = actualizar(producto, receta, null)

    // 0.21.6: hay que sobrescribirla también; con la delegación `by local` escribiría en local en una secundaria.
    override suspend fun actualizar(producto: Producto, receta: Receta?, cantidadLeida: Long?): AppResult<Unit> =
        g.remoto(PermisoEmpleado.EDITAR_INVENTARIO, { local.actualizar(producto, receta, cantidadLeida) },
            { GuardarProducto(producto.sinFoto().toDto(), receta?.toDto(), cantidadLeida) }) { }

    override suspend fun eliminar(id: Long): AppResult<Unit> =
        g.remoto(PermisoEmpleado.EDITAR_INVENTARIO, { local.eliminar(id) }, { EliminarProducto(id) }) { }

    override suspend fun ajustarStock(id: Long, delta: Long, nota: String?): AppResult<Long> =
        g.remoto(PermisoEmpleado.EDITAR_INVENTARIO, { local.ajustarStock(id, delta, nota) },
            { AjustarStockProducto(id, delta, nota) }) { it ?: 0L }

    override suspend fun actualizarPrecios(nuevos: Map<Long, Cup>): AppResult<Unit> =
        g.remoto(PermisoEmpleado.CAMBIAR_PRECIOS, { local.actualizarPrecios(nuevos) },
            { CambiarPrecios(nuevos.mapValues { it.value.centavos }) }) { }
}

@Singleton
class InsumoRepositorySegunTipo @Inject constructor(
    private val local: InsumoRepositoryImpl,
    private val g: GuardiaSync,
) : InsumoRepository by local {
    override suspend fun crear(insumo: Insumo): AppResult<Long> =
        g.remoto(PermisoEmpleado.EDITAR_INVENTARIO, { local.crear(insumo) }, { GuardarInsumo(insumo.toDto()) }) { it ?: 0L }

    override suspend fun actualizar(insumo: Insumo): AppResult<Unit> = actualizar(insumo, null)

    override suspend fun actualizar(insumo: Insumo, cantidadLeida: Cantidad?): AppResult<Unit> =
        g.remoto(PermisoEmpleado.EDITAR_INVENTARIO, { local.actualizar(insumo, cantidadLeida) },
            { GuardarInsumo(insumo.toDto(), cantidadLeida?.milesimas) }) { }

    override suspend fun eliminar(id: Long): AppResult<Unit> =
        g.remoto(PermisoEmpleado.EDITAR_INVENTARIO, { local.eliminar(id) }, { EliminarInsumo(id) }) { }

    override suspend fun ajustarStock(id: Long, delta: Cantidad, nota: String?): AppResult<Cantidad> =
        g.remoto(PermisoEmpleado.EDITAR_INVENTARIO, { local.ajustarStock(id, delta, nota) },
            { AjustarStockInsumo(id, delta.milesimas, nota) }) { Cantidad(it ?: 0L) }
}

@Singleton
class ServicioRepositorySegunTipo @Inject constructor(
    private val local: ServicioRepositoryImpl,
    private val g: GuardiaSync,
) : ServicioRepository by local {
    override suspend fun crear(servicio: Servicio, insumos: List<RecetaLinea>): AppResult<Long> =
        g.remoto(PermisoEmpleado.EDITAR_INVENTARIO, { local.crear(servicio, insumos) },
            { GuardarServicio(servicio.sinFoto().toDto(insumos)) }) { it ?: 0L }

    override suspend fun actualizar(servicio: Servicio, insumos: List<RecetaLinea>): AppResult<Unit> =
        g.remoto(PermisoEmpleado.EDITAR_INVENTARIO, { local.actualizar(servicio, insumos) },
            { GuardarServicio(servicio.sinFoto().toDto(insumos)) }) { }

    override suspend fun eliminar(id: Long): AppResult<Unit> =
        g.remoto(PermisoEmpleado.EDITAR_INVENTARIO, { local.eliminar(id) }, { EliminarServicio(id) }) { }
}

@Singleton
class PreciosRepositorySegunTipo @Inject constructor(
    private val local: PreciosRepositoryImpl,
    private val g: GuardiaSync,
) : PreciosRepository by local {
    override suspend fun guardarPreajuste(p: PreajustePrecios): AppResult<Long> =
        g.remoto(PermisoEmpleado.CAMBIAR_PRECIOS, { local.guardarPreajuste(p) }, { GuardarPreajuste(p.toDto()) }) { it ?: 0L }

    override suspend fun eliminarPreajuste(id: Long): AppResult<Unit> =
        g.remoto(PermisoEmpleado.CAMBIAR_PRECIOS, { local.eliminarPreajuste(id) }, { EliminarPreajuste(id) }) { }
}

/** Vender es local también en la secundaria (funciona sin red); solo se mira el permiso y se avisa al cliente. */
@Singleton
class VentaRepositorySegunTipo @Inject constructor(
    private val local: VentaRepositoryImpl,
    private val g: GuardiaSync,
) : VentaRepository by local {
    override suspend fun registrar(venta: Venta, elaborados: List<ElaboradoEnVenta>): AppResult<Long> {
        val d = g.secundaria() ?: return local.registrar(venta, elaborados)
        val p = if (venta.esServicio) PermisoEmpleado.VENDER_SERVICIOS else PermisoEmpleado.VENDER_PRODUCTOS
        if (p !in d.permisosEmpleado) return AppResult.Err(AppError.SinPermiso)
        return local.registrar(venta, elaborados).also { if (it is AppResult.Ok) g.cliente.disparar() }
    }

    /** 0.25.0 (§6bis): anular y modificar solo en la app principal. */
    override suspend fun anular(ventaId: Long, anulacion: cu.spvi.domain.model.Anulacion): AppResult<Unit> =
        if (g.secundaria() != null) AppResult.Err(AppError.SinPermiso) else local.anular(ventaId, anulacion)

    override suspend fun modificar(
        ventaId: Long, anulacion: cu.spvi.domain.model.Anulacion, nueva: Venta, elaborados: List<ElaboradoEnVenta>,
    ): AppResult<Long> =
        if (g.secundaria() != null) AppResult.Err(AppError.SinPermiso) else local.modificar(ventaId, anulacion, nueva, elaborados)
}

@Singleton
class TurnoRepositorySegunTipo @Inject constructor(
    private val local: TurnoRepositoryImpl,
    private val g: GuardiaSync,
) : TurnoRepository by local {
    override suspend fun abrir(ahora: Instant, usuario: String, fondo: cu.spvi.core.money.Cup?): AppResult<Turno> =
        local.abrir(ahora, usuario, fondo).also { if (it is AppResult.Ok) g.cliente.disparar() }

    override suspend fun cerrar(ahora: Instant, usuario: String, contado: cu.spvi.core.money.Cup?): AppResult<Turno> =
        local.cerrar(ahora, usuario, contado).also { if (it is AppResult.Ok) g.cliente.disparar() }

    override suspend fun registrarCaja(
        tipo: cu.spvi.domain.model.TipoMovimientoCaja, importe: cu.spvi.core.money.Cup, motivo: String, hechoPor: String, ahora: Instant,
    ): AppResult<Long> = local.registrarCaja(tipo, importe, motivo, hechoPor, ahora).also { if (it is AppResult.Ok) g.cliente.disparar() }

    override suspend fun declararContado(contado: cu.spvi.core.money.Cup): AppResult<Unit> =
        local.declararContado(contado).also { if (it is AppResult.Ok) g.cliente.disparar() }
}

/** Exportar/compartir tablas y PDF: en una secundaria solo con el permiso EXPORTAR. */
@Singleton
class ExportadorSegunTipo @Inject constructor(
    private val local: cu.spvi.data.export.ExportadorDocumentosImpl,
    private val g: GuardiaSync,
) : cu.spvi.domain.repository.ExportadorDocumentos by local {
    override suspend fun exportar(
        tablas: List<cu.spvi.domain.service.TablaExport>,
        formato: cu.spvi.domain.service.FormatoExport,
        destino: java.io.OutputStream,
    ): AppResult<Unit> {
        val d = g.secundaria()
        if (d != null && PermisoEmpleado.EXPORTAR !in d.permisosEmpleado) return AppResult.Err(AppError.SinPermiso)
        return local.exportar(tablas, formato, destino)
    }
}
