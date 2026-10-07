package cu.spvi.data.sync

import cu.spvi.data.db.dao.registrar
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.db.entity.EmpleadoEntity
import cu.spvi.data.db.entity.MovimientoEntity
import cu.spvi.data.db.entity.MovimientoCajaEntity
import cu.spvi.data.dto.MovimientoCajaDto
import cu.spvi.domain.model.TipoMovimientoCaja
import cu.spvi.data.db.entity.PerfilEntity
import cu.spvi.data.db.entity.ServicioInsumoEntity
import cu.spvi.data.db.tx
import cu.spvi.data.dto.PerfilDto
import cu.spvi.data.dto.toDomain
import cu.spvi.data.dto.toDto
import cu.spvi.data.mapper.perfilDe
import cu.spvi.data.mapper.productosEntities
import cu.spvi.data.mapper.toDomain
import cu.spvi.data.mapper.toEntities
import cu.spvi.data.mapper.toEntity
import cu.spvi.data.mapper.toReceta
import cu.spvi.domain.model.PermisoEmpleado
import cu.spvi.domain.model.TipoEntidad
import cu.spvi.domain.repository.PreferenciasRepository
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * P37: lectura y escritura de la base de datos para la sincronización. Todo lo que cambia datos va en UNA transacción.
 *
 * Principal: arma el catálogo para cada secundaria y aplica sus turnos y ventas sin duplicar (uuid).
 * Secundaria: arma lo pendiente, marca lo recibido y sustituye su copia del catálogo por la de la principal, volviendo a
 * descontar las ventas que la principal aún no tiene (así las existencias locales nunca «resucitan» lo ya vendido).
 */
@Singleton
class AlmacenSync @Inject constructor(
    private val db: SpviDatabase,
    private val preferencias: PreferenciasRepository,
    private val clock: Clock,
) {
    private val sync get() = db.syncDao()

    // ================================================================== principal

    suspend fun instantanea(empleado: EmpleadoEntity, nombreNegocio: String, licencia: LicenciaDto): AppResult<Instantanea> = db.tx {
        val perfilDao = db.perfilDao()
        val perfil = perfilDe(perfilDao.obtener(), perfilDao.tarjetas(), perfilDao.telefonos())
        val servicios = db.servicioDao().let { dao ->
            val lineas = dao.todosInsumos().groupBy({ it.servicioId }, { it.toDomain() })
            dao.todos().map { it.toDomain().toDto(lineas[it.id].orEmpty()) }
        }
        Instantanea(
            nombreNegocio = nombreNegocio,
            empleado = EmpleadoInfo(empleado.nombre, PermisoEmpleado.deNombres(empleado.permisos.split(',')).map { it.name }.sorted()),
            licencia = licencia,
            productos = db.productoDao().todos().map { it.toDomain().toDto() },
            recetas = db.recetaDao().todas().groupBy { it.productoId }.map { (id, l) -> l.toReceta(id).toDto() },
            insumos = db.insumoDao().todos().map { it.toDomain().toDto() },
            servicios = servicios,
            preajustes = db.preciosDao().todos().map { it.toDomain().toDto() },
            // Solo lo necesario para cobrar por transferencia: ni nombre ni carné del dueño, y (0.20.0, H4) solo la
            // tarjeta y el teléfono con los que cobra ESTE empleado.
            perfil = perfil.paraEmpleado(empleado.tarjetaId, empleado.telefonoId, empleado.telefono).toDto().copy(nombre = "", apellidos = "", ci = ""),
            preferencias = preferencias.preferencias.first().toDto(),
        )
    }

    /** Aplica el lote de una secundaria. Devuelve lo que quedó guardado (la secundaria solo marca eso como enviado). */
    suspend fun aplicarLote(empleadoId: Long, lote: Lote): AppResult<Recibidos> = db.tx {
        val ahora = clock.now().toEpochMilli()
        val turnos = mutableListOf<TurnoRecibido>()
        val cerradosAhora = mutableListOf<Long>()
        for (ts in lote.turnos) {
            val nuevo = ts.turno.toDomain().toEntity().copy(id = 0, uuid = ts.uuid, empleadoId = empleadoId, sincronizado = true)
            val existente = sync.turnoPorUuid(ts.uuid)
            when {
                existente == null -> {
                    val id = sync.insertarTurno(nuevo)
                    if (nuevo.cerradoEn != null) cerradosAhora += id
                }
                existente.empleadoId != empleadoId -> continue // uuid de otra app: no se toca
                existente.cerradoEn == null && nuevo.cerradoEn != null -> {
                    sync.actualizarTurno(nuevo.copy(id = existente.id))
                    cerradosAhora += existente.id
                }
                // 0.25.0: turno aún abierto con el conteo declarado al pedir el cierre (o el fondo, si cambió).
                existente.cerradoEn == null && (existente.contadoCent != nuevo.contadoCent || existente.fondoCent != nuevo.fondoCent) ->
                    sync.actualizarTurno(existente.copy(contadoCent = nuevo.contadoCent, fondoCent = nuevo.fondoCent ?: existente.fondoCent))
                else -> Unit // ya estaba igual (reenvío)
            }
            turnos += TurnoRecibido(ts.uuid, nuevo.cerradoEn != null)
        }
        val ventas = mutableListOf<String>()
        val ventaDao = db.ventaDao()
        for (vs in lote.ventas) {
            if (sync.ventaPorUuid(vs.uuid) != null) { ventas += vs.uuid; continue }
            val turno = sync.turnoPorUuid(vs.turnoUuid)?.takeIf { it.empleadoId == empleadoId } ?: continue
            val venta = vs.venta.toDomain().copy(turnoId = turno.id)
            val ventaId = ventaDao.insertarVenta(venta.toEntity().copy(id = 0, uuid = vs.uuid, empleadoId = empleadoId, sincronizado = true))
            ventaDao.insertarDetalles(venta.detalles.map { it.toEntity(ventaId).copy(id = 0) })
            venta.transaccion?.let { ventaDao.insertarTransaccion(it.toEntity(ventaId).copy(id = 0)) }
            // 0.27.0 (N2): el cliente fijo marcado en una secundaria queda también en la principal.
            venta.transaccion?.takeIf { it.clienteFijo }?.let { t ->
                db.clienteFijoDao().registrar(t.cliente.nombreApellidos, t.cliente.ci, t.cliente.telefono, venta.fecha.toEpochMilli())
            }
            for (m in vs.movimientos) {
                val existencia = if (m.entidad == TipoEntidad.PRODUCTO.name) {
                    sync.forzarStockProducto(m.entidadId, m.delta, ahora)
                    db.productoDao().cantidad(m.entidadId) ?: 0
                } else {
                    sync.forzarStockInsumo(m.entidadId, m.delta, ahora)
                    db.insumoDao().cantidad(m.entidadId) ?: 0
                }
                db.movimientoDao().insertar(
                    MovimientoEntity(
                        fecha = m.fecha, tipo = m.tipo, entidad = m.entidad, entidadId = m.entidadId, nombre = m.nombre,
                        delta = m.delta, existencia = existencia, turnoId = turno.id, ventaId = ventaId,
                        nota = if (existencia < 0) listOfNotNull(m.nota, NOTA_SIN_EXISTENCIAS).joinToString(" · ") else m.nota,
                    ),
                )
            }
            ventas += vs.uuid
        }
        // 0.25.0: entradas y salidas de efectivo (sin duplicar por uuid).
        val caja = mutableListOf<String>()
        val cajaDao = db.cajaDao()
        for (cs in lote.caja) {
            if (cajaDao.porUuid(cs.uuid) != null) { caja += cs.uuid; continue }
            val turno = sync.turnoPorUuid(cs.turnoUuid)?.takeIf { it.empleadoId == empleadoId } ?: continue
            if (cs.mov.importeCent <= 0) { caja += cs.uuid; continue } // inválido: se descarta (y se confirma para no reenviarlo)
            cajaDao.insertar(
                MovimientoCajaEntity(
                    turnoId = turno.id, fecha = cs.mov.fecha, tipo = cs.mov.tipo, importeCent = cs.mov.importeCent,
                    motivo = cs.mov.motivo.take(cu.spvi.domain.model.MovimientoCaja.MOTIVO_MAX), hechoPor = cs.mov.hechoPor,
                    uuid = cs.uuid, sincronizado = true,
                ),
            )
            caja += cs.uuid
        }
        // 0.25.0: el resumen de un turno que llega cerrado se recalcula AQUÍ, con las ventas que la principal tiene por
        // válidas (pudo anular alguna que la secundaria aún no sabía) y la caja ya recibida. El conteo es el del empleado.
        cerradosAhora.forEach { recalcularCierre(it) }
        sync.marcarSincronizado(empleadoId, ahora)
        Recibidos(turnos, ventas, caja)
    }

    private suspend fun recalcularCierre(turnoId: Long) {
        val turnoDao = db.turnoDao()
        val t = turnoDao.obtener(turnoId) ?: return
        val r = turnoDao.resumen(turnoId)
        val m = db.movimientoDao().contarDeTurnoPorEntidad(turnoId)
        val caja = db.cajaDao()
        sync.actualizarTurno(
            t.copy(
                numVentas = r.numVentas, unidades = r.unidades, totalCent = r.totalCent, efectivoCent = r.efectivoCent,
                transferenciaCent = r.transferenciaCent, costoCent = r.costoCent, numMovimientos = m.producto + m.insumo,
                ventasEfectivo = r.ventasEfectivo, ventasTransferencia = r.ventasTransferencia,
                movimientosProducto = m.producto, movimientosInsumo = m.insumo, numAnuladas = r.numAnuladas,
                entradasCent = caja.suma(turnoId, TipoMovimientoCaja.ENTRADA.name), salidasCent = caja.suma(turnoId, TipoMovimientoCaja.SALIDA.name),
            ),
        )
    }

    /** 0.25.0: confirma los cambios que la secundaria ya aplicó. */
    suspend fun cambiosConfirmados(empleadoId: Long, uuids: List<String>) {
        if (uuids.isNotEmpty()) uuids.chunked(500).forEach { db.ventaDao().cambiosConfirmados(empleadoId, it) }
    }

    /** 0.25.0: ventas de [empleadoId] anuladas o corregidas aquí que aún no confirmó. */
    suspend fun cambiosPara(empleadoId: Long): List<CambioVenta> {
        val ventaDao = db.ventaDao()
        val lista = ventaDao.cambiosParaEmpleado(empleadoId)
        if (lista.isEmpty()) return emptyList()
        val turnoUuid = mutableMapOf<Long, String?>()
        val ventaUuid = mutableMapOf<Long, String?>()
        return lista.mapNotNull { vc ->
            val v = vc.venta
            val uuid = v.uuid ?: return@mapNotNull null
            val tu = turnoUuid.getOrPut(v.turnoId) { db.turnoDao().obtener(v.turnoId)?.uuid } ?: return@mapNotNull null
            val corrige = v.corrigeVentaId?.let { id -> ventaUuid.getOrPut(id) { ventaDao.obtener(id)?.venta?.uuid } }
            CambioVenta(
                uuid = uuid, turnoUuid = tu, anuladaEn = v.anuladaEn, motivo = v.motivoAnulacion, anuladaPor = v.anuladaPor,
                // Venta creada aquí (corrección): viaja completa. Una venta original anulada solo lleva la anulación.
                nueva = if (v.corrigeVentaId != null) vc.toDomain().toDto().copy(id = 0, turnoId = 0, uuid = uuid, corrigeVentaId = null) else null,
                corrigeUuid = corrige,
            )
        }
    }

    // ================================================================== secundaria

    suspend fun lotePendiente(): Lote {
        val turnos = sync.turnosPendientes().filter { it.uuid != null }
        val uuidTurno = mutableMapOf<Long, String>()
        val ventas = sync.ventasPendientes().filter { it.venta.uuid != null }
        val movs = if (ventas.isEmpty()) emptyMap() else
            ventas.map { it.venta.id }.chunked(500).flatMap { sync.movimientosDeVentas(it) }.groupBy { it.ventaId }
        val ventasSync = ventas.mapNotNull { v ->
            val tu = uuidTurno.getOrPut(v.venta.turnoId) { db.turnoDao().obtener(v.venta.turnoId)?.uuid ?: return@mapNotNull null }
            VentaSync(v.venta.uuid!!, tu, v.toDomain().toDto(), movs[v.venta.id].orEmpty().map { it.toDomain().toDto() })
        }
        val caja = db.cajaDao().pendientes().filter { it.uuid != null }.mapNotNull { m ->
            val tu = uuidTurno.getOrPut(m.turnoId) { db.turnoDao().obtener(m.turnoId)?.uuid ?: return@mapNotNull null }
            CajaSync(m.uuid!!, tu, MovimientoCajaDto(0, 0, m.fecha, m.tipo, m.importeCent, m.motivo, m.hechoPor, m.uuid))
        }
        return Lote(turnos.map { TurnoSync(it.uuid!!, it.toDomain().toDto()) }, ventasSync, caja)
    }

    /**
     * 0.25.0: aplica los cambios que hizo la principal en ventas de esta secundaria (anulaciones y ventas corregidas).
     * Idempotente por uuid. No toca existencias: el catálogo (con lo devuelto) llega de la principal en la instantánea.
     * Devuelve los uuids aplicados (o ya presentes), que se confirman en la siguiente sincronización.
     */
    suspend fun aplicarCambios(cambios: List<CambioVenta>): AppResult<List<String>> = db.tx {
        val ventaDao = db.ventaDao()
        val hechos = mutableListOf<String>()
        for (c in cambios) {
            val existente = ventaDao.porUuid(c.uuid)
            if (existente == null && c.nueva != null) {
                val turno = sync.turnoPorUuid(c.turnoUuid)
                if (turno == null) continue // aún no existe aquí: se reintentará
                val corrige = c.corrigeUuid?.let { ventaDao.porUuid(it)?.id }
                val venta = c.nueva.toDomain().copy(turnoId = turno.id, corrigeVentaId = corrige)
                val id = ventaDao.insertarVenta(venta.toEntity().copy(id = 0, uuid = c.uuid, sincronizado = true))
                ventaDao.insertarDetalles(venta.detalles.map { it.toEntity(id).copy(id = 0) })
                venta.transaccion?.let { ventaDao.insertarTransaccion(it.toEntity(id).copy(id = 0)) }
            } else if (existente != null && c.anuladaEn != null && existente.anuladaEn == null) {
                ventaDao.anular(existente.id, c.anuladaEn, c.motivo.orEmpty(), c.anuladaPor.orEmpty(), bajar = false)
            }
            hechos += c.uuid
        }
        hechos.toList()
    }

    suspend fun pendientes(): Int = sync.pendientes()

    /** Marca lo recibido y, si llegó catálogo nuevo, lo pone en lugar del local. */
    suspend fun aplicarEnSecundaria(r: Recibidos, inst: Instantanea?): AppResult<Unit> {
        val res = db.tx {
            r.ventas.chunked(500).forEach { sync.marcarVentasRecibidas(it) }
            r.caja.chunked(500).forEach { db.cajaDao().marcarRecibidos(it) }
            r.turnos.forEach { sync.marcarTurnoRecibido(it.uuid, it.cerrado) }
            if (inst != null) reemplazarCatalogo(inst)
        }
        if (res is AppResult.Ok && inst?.preferencias != null) {
            val actual = preferencias.preferencias.first()
            // 0.21.0 (C12): también los módulos del negocio (lo que el dueño no usa tampoco aparece aquí).
            val recibidas = inst.preferencias.toDomain()
            preferencias.reemplazar(actual.copy(niveles = recibidas.niveles, modulos = recibidas.modulos))
        }
        return res
    }

    private suspend fun reemplazarCatalogo(inst: Instantanea) {
        val m = db.mantenimientoDao()
        m.borrarRecetas(); m.borrarServicioInsumos(); m.borrarPreajusteProductos(); m.borrarPreajustes()
        m.borrarServicios(); m.borrarProductos(); m.borrarInsumos(); m.borrarTarjetas(); m.borrarTelefonos(); m.borrarPerfil()

        // Las fotos son archivos del teléfono principal: aquí no existen.
        db.productoDao().insertarTodos(inst.productos.map { it.toDomain().toEntity().copy(fotoUri = null) })
        db.insumoDao().insertarTodos(inst.insumos.map { it.toDomain().toEntity() })
        db.recetaDao().insertarTodas(inst.recetas.flatMap { it.toDomain().toEntities() })
        val servicios = inst.servicios.map { it.toDomain() }
        db.servicioDao().insertarTodos(servicios.map { it.first.toEntity().copy(fotoUri = null) })
        db.servicioDao().insertarInsumos(servicios.flatMap { (s, l) -> l.map { ServicioInsumoEntity(s.id, it.insumoId, it.cantidad.milesimas) } })

        val perfil = (inst.perfil ?: PerfilDto("", "", "")).toDomain()
        val perfilDao = db.perfilDao()
        perfil.tarjetas.forEach { perfilDao.insertarTarjeta(it.toEntity()) }
        perfil.telefonos.forEach { perfilDao.insertarTelefono(it.toEntity()) }
        // El «usuario» de esta app es el empleado: su nombre queda en los turnos que abre y cierra.
        perfilDao.guardar(PerfilEntity(nombre = inst.empleado.nombre, apellidos = "", ci = "",
            pagoTarjetaId = perfil.pagoTarjetaId, pagoTelefonoId = perfil.pagoTelefonoId))

        val existentes = inst.productos.filterNot { it.eliminado }.map { it.id }.toSet()
        val precios = db.preciosDao()
        inst.preajustes.map { it.toDomain() }.forEach { pa ->
            precios.guardar(pa.toEntity())
            precios.insertarProductos(pa.copy(productoIds = pa.productoIds intersect existentes).productosEntities())
        }

        // Ventas hechas aquí que la principal aún no tiene: se vuelven a descontar.
        val ahora = clock.now().toEpochMilli()
        sync.movimientosPendientes().forEach { mv ->
            if (mv.entidad == TipoEntidad.PRODUCTO.name) sync.forzarStockProducto(mv.entidadId, mv.delta, ahora)
            else sync.forzarStockInsumo(mv.entidadId, mv.delta, ahora)
        }
    }

    companion object {
        const val NOTA_SIN_EXISTENCIAS = "Vendido en otra app sin existencias suficientes: revisa el conteo"

        /** Huella del catálogo (la principal no lo reenvía si la secundaria ya tiene el mismo). */
        fun hash(inst: Instantanea): String =
            MessageDigest.getInstance("SHA-256").digest(SyncJson.encodeToString(Instantanea.serializer(), inst).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
