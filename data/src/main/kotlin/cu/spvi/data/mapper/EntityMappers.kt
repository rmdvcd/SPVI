package cu.spvi.data.mapper

import cu.spvi.domain.model.ClaseArticulo
import cu.spvi.domain.model.Servicio
import cu.spvi.data.db.entity.ServicioInsumoEntity
import cu.spvi.data.db.entity.ServicioEntity
import cu.spvi.core.money.Cup
import cu.spvi.core.quantity.Cantidad
import cu.spvi.data.db.entity.DetalleVentaEntity
import cu.spvi.data.db.entity.MovimientoCajaEntity
import cu.spvi.domain.model.Anulacion
import cu.spvi.domain.model.MovimientoCaja
import cu.spvi.domain.model.TipoMovimientoCaja
import cu.spvi.data.db.entity.InsumoEntity
import cu.spvi.data.db.entity.MovimientoEntity
import cu.spvi.data.db.entity.PerfilEntity
import cu.spvi.data.db.entity.PreajusteCompleto
import cu.spvi.data.db.entity.PreajusteEntity
import cu.spvi.data.db.entity.PreajusteProductoEntity
import cu.spvi.data.db.entity.ProductoEntity
import cu.spvi.data.db.entity.RecetaLineaEntity
import cu.spvi.data.db.entity.TarjetaEntity
import cu.spvi.data.db.entity.TelefonoEntity
import cu.spvi.data.db.entity.TransaccionEntity
import cu.spvi.data.db.entity.TurnoEntity
import cu.spvi.data.db.entity.VentaCompleta
import cu.spvi.data.db.entity.VentaEntity
import cu.spvi.domain.model.DatosCliente
import cu.spvi.domain.model.DetalleVenta
import cu.spvi.domain.model.Insumo
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.model.MovimientoInventario
import cu.spvi.domain.model.Perfil
import cu.spvi.domain.model.PreajustePrecios
import cu.spvi.domain.model.Producto
import cu.spvi.domain.model.Receta
import cu.spvi.domain.model.RecetaLinea
import cu.spvi.domain.model.ResumenTurno
import cu.spvi.domain.model.TarjetaBancaria
import cu.spvi.domain.model.Telefono
import cu.spvi.domain.model.TipoEntidad
import cu.spvi.domain.model.TipoMovimiento
import cu.spvi.domain.model.Transaccion
import cu.spvi.domain.model.Turno
import cu.spvi.domain.model.UnidadMedida
import cu.spvi.domain.model.Venta
import java.time.Instant
import java.time.LocalDate

/** Mapeo explícito entidad Room ↔ modelo de dominio. Enums desconocidos (datos corruptos) caen a un valor seguro. */

internal fun Instant.ms(): Long = toEpochMilli()
internal fun Long.instant(): Instant = Instant.ofEpochMilli(this)
internal inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

// ---------- Producto / Receta ----------
fun ProductoEntity.toDomain() = Producto(
    id = id, categoria = categoria, nombre = nombre, descripcion = descripcion, fotoUri = fotoUri,
    fechaCaducidad = fechaCaducidad?.let(LocalDate::ofEpochDay),
    precioCosto = Cup(precioCostoCent), precioVenta = Cup(precioVentaCent), cantidad = cantidad,
    nivelBajo = nivelBajo, nivelCritico = nivelCritico,
    creadoEn = creadoEn.instant(), actualizadoEn = actualizadoEn.instant(), eliminado = eliminado,
)

fun Producto.toEntity() = ProductoEntity(
    id = id, categoria = categoria, nombre = nombre, descripcion = descripcion, fotoUri = fotoUri,
    fechaCaducidad = fechaCaducidad?.toEpochDay(), precioCostoCent = precioCosto.centavos,
    precioVentaCent = precioVenta.centavos, cantidad = cantidad, nivelBajo = nivelBajo, nivelCritico = nivelCritico,
    creadoEn = creadoEn.ms(), actualizadoEn = actualizadoEn.ms(), eliminado = eliminado,
)

fun List<RecetaLineaEntity>.toReceta(productoId: Long) =
    Receta(productoId, map { RecetaLinea(it.insumoId, Cantidad(it.cantidadMil)) })

fun Receta.toEntities(productoId: Long = this.productoId) =
    lineas.map { RecetaLineaEntity(productoId, it.insumoId, it.cantidad.milesimas) }

// ---------- Insumo ----------
fun InsumoEntity.toDomain() = Insumo(
    id = id, nombre = nombre, unidad = enumOr(unidad, UnidadMedida.UNIDAD), precio = Cup(precioCent),
    cantidad = Cantidad(cantidadMil), nivelBajo = nivelBajoMil?.let(::Cantidad), nivelCritico = nivelCriticoMil?.let(::Cantidad),
    creadoEn = creadoEn.instant(), actualizadoEn = actualizadoEn.instant(), precioVenta = precioVentaCent?.let(::Cup),
)

fun Insumo.toEntity() = InsumoEntity(
    id = id, nombre = nombre, unidad = unidad.name, precioCent = precio.centavos, cantidadMil = cantidad.milesimas,
    nivelBajoMil = nivelBajo?.milesimas, nivelCriticoMil = nivelCritico?.milesimas,
    creadoEn = creadoEn.ms(), actualizadoEn = actualizadoEn.ms(), precioVentaCent = precioVenta?.centavos,
)

// ---------- Servicio (v4) ----------
fun ServicioEntity.toDomain() = Servicio(
    id = id, nombre = nombre, tipo = tipo, importe = Cup(importeCent), descripcion = descripcion, fotoUri = fotoUri,
    creadoEn = creadoEn.instant(), actualizadoEn = actualizadoEn.instant(), eliminado = eliminado,
)

fun Servicio.toEntity() = ServicioEntity(
    id = id, nombre = nombre, tipo = tipo, importeCent = importe.centavos, descripcion = descripcion, fotoUri = fotoUri,
    creadoEn = creadoEn.ms(), actualizadoEn = actualizadoEn.ms(), eliminado = eliminado,
)

fun ServicioInsumoEntity.toDomain() = RecetaLinea(insumoId = insumoId, cantidad = Cantidad(cantidadMil))

// ---------- Turno ----------
fun TurnoEntity.toDomain(): Turno {
    val resumen = if (cerradoEn != null && numVentas != null) ResumenTurno(
        numVentas = numVentas, unidades = unidades ?: 0, total = Cup(totalCent ?: 0),
        totalEfectivo = Cup(efectivoCent ?: 0), totalTransferencia = Cup(transferenciaCent ?: 0),
        costo = Cup(costoCent ?: 0), numMovimientos = numMovimientos ?: 0,
        ventasEfectivo = ventasEfectivo ?: 0, ventasTransferencia = ventasTransferencia ?: 0,
        // Turnos cerrados antes de v2: sin desglose, todos los movimientos cuentan como de producto.
        movimientosProducto = movimientosProducto ?: (numMovimientos ?: 0), movimientosInsumo = movimientosInsumo ?: 0,
        entradasCaja = Cup(entradasCent ?: 0), salidasCaja = Cup(salidasCent ?: 0), numAnuladas = numAnuladas ?: 0,
    ) else null
    return Turno(
        id, abiertoEn.instant(), cerradoEn?.instant(), resumen, abiertoPor.orEmpty(), cerradoPor, empleadoId,
        fondo = fondoCent?.let(::Cup), contado = contadoCent?.let(::Cup),
    )
}

fun Turno.toEntity() = TurnoEntity(
    id = id, abiertoEn = abiertoEn.ms(), cerradoEn = cerradoEn?.ms(),
    numVentas = resumen?.numVentas, unidades = resumen?.unidades, totalCent = resumen?.total?.centavos,
    efectivoCent = resumen?.totalEfectivo?.centavos, transferenciaCent = resumen?.totalTransferencia?.centavos,
    costoCent = resumen?.costo?.centavos, numMovimientos = resumen?.numMovimientos,
    abiertoPor = abiertoPor.ifEmpty { null }, cerradoPor = cerradoPor,
    ventasEfectivo = resumen?.ventasEfectivo, ventasTransferencia = resumen?.ventasTransferencia,
    movimientosProducto = resumen?.movimientosProducto, movimientosInsumo = resumen?.movimientosInsumo,
    empleadoId = empleadoId,
    fondoCent = fondo?.centavos, contadoCent = contado?.centavos,
    entradasCent = resumen?.entradasCaja?.centavos, salidasCent = resumen?.salidasCaja?.centavos, numAnuladas = resumen?.numAnuladas,
)

// ---------- Caja (0.25.0) ----------
fun MovimientoCajaEntity.toDomain() = MovimientoCaja(
    id = id, turnoId = turnoId, fecha = fecha.instant(), tipo = enumOr(tipo, TipoMovimientoCaja.ENTRADA),
    importe = Cup(importeCent), motivo = motivo, hechoPor = hechoPor, uuid = uuid,
)

fun MovimientoCaja.toEntity() = MovimientoCajaEntity(
    id = id, turnoId = turnoId, fecha = fecha.ms(), tipo = tipo.name, importeCent = importe.centavos,
    motivo = motivo, hechoPor = hechoPor, uuid = uuid,
)

// ---------- Venta ----------
fun DetalleVentaEntity.toDomain() = DetalleVenta(
    id = id, ventaId = ventaId, productoId = productoId, nombre = nombre, categoria = categoria, cantidad = cantidad,
    precioBase = Cup(precioBaseCent), precioUnitario = Cup(precioUnitarioCent), costoUnitario = Cup(costoUnitarioCent),
    clase = enumOr(clase, ClaseArticulo.PRODUCTO),
)

fun DetalleVenta.toEntity(ventaId: Long) = DetalleVentaEntity(
    id = id, ventaId = ventaId, productoId = productoId, nombre = nombre, categoria = categoria, cantidad = cantidad,
    precioBaseCent = precioBase.centavos, precioUnitarioCent = precioUnitario.centavos, costoUnitarioCent = costoUnitario.centavos,
    clase = clase.name,
)

fun TransaccionEntity.toDomain() = Transaccion(
    id = id, ventaId = ventaId, fecha = fecha.instant(), importe = Cup(importeCent), numero = numero,
    cliente = DatosCliente(clienteNombre, clienteCi, clienteTelefono), tarjetaCobro = tarjetaCobro, telefonoCobro = telefonoCobro,
    clienteFijo = clienteFijo,
)

fun Transaccion.toEntity(ventaId: Long) = TransaccionEntity(
    id = id, ventaId = ventaId, fecha = fecha.ms(), importeCent = importe.centavos, numero = numero,
    clienteNombre = cliente.nombreApellidos, clienteCi = cliente.ci, clienteTelefono = cliente.telefono,
    tarjetaCobro = tarjetaCobro, telefonoCobro = telefonoCobro, clienteFijo = clienteFijo,
)

fun VentaCompleta.toDomain() = Venta(
    id = venta.id, turnoId = venta.turnoId, fecha = venta.fecha.instant(),
    metodoPago = enumOr(venta.metodoPago, MetodoPago.EFECTIVO),
    detalles = detalles.sortedBy { it.id }.map { it.toDomain() }, transaccion = transaccion?.toDomain(),
    anulacion = venta.anuladaEn?.let { Anulacion(it.instant(), venta.motivoAnulacion.orEmpty(), venta.anuladaPor.orEmpty()) },
    corrigeVentaId = venta.corrigeVentaId,
)

/** Totales desnormalizados calculados desde los detalles (fuente de verdad). */
fun Venta.toEntity() = VentaEntity(
    id = id, turnoId = turnoId, fecha = fecha.ms(), metodoPago = metodoPago.name,
    totalCent = total.centavos, costoCent = costoTotal.centavos, unidades = unidades,
    anuladaEn = anulacion?.en?.ms(), motivoAnulacion = anulacion?.motivo, anuladaPor = anulacion?.por,
    corrigeVentaId = corrigeVentaId,
)

// ---------- Movimiento ----------
fun MovimientoEntity.toDomain() = MovimientoInventario(
    id = id, fecha = fecha.instant(), tipo = enumOr(tipo, TipoMovimiento.AJUSTE), entidad = enumOr(entidad, TipoEntidad.PRODUCTO),
    entidadId = entidadId, nombre = nombre, delta = delta, existenciaResultante = existencia,
    turnoId = turnoId, ventaId = ventaId, nota = nota, hechoPor = hechoPor,
)

fun MovimientoInventario.toEntity() = MovimientoEntity(
    id = id, fecha = fecha.ms(), tipo = tipo.name, entidad = entidad.name, entidadId = entidadId, nombre = nombre,
    delta = delta, existencia = existenciaResultante, turnoId = turnoId, ventaId = ventaId, nota = nota, hechoPor = hechoPor,
)

// ---------- Perfil ----------
fun perfilDe(p: PerfilEntity?, tarjetas: List<TarjetaEntity>, telefonos: List<TelefonoEntity>) = Perfil(
    nombre = p?.nombre.orEmpty(), apellidos = p?.apellidos.orEmpty(), ci = p?.ci.orEmpty(),
    tarjetas = tarjetas.map { TarjetaBancaria(it.id, it.numero, it.alias) },
    telefonos = telefonos.map { Telefono(it.id, it.numero, it.alias) },
    pagoTarjetaId = p?.pagoTarjetaId, pagoTelefonoId = p?.pagoTelefonoId,
)

fun TarjetaBancaria.toEntity() = TarjetaEntity(id, numero, alias)
fun Telefono.toEntity() = TelefonoEntity(id, numero, alias)

// ---------- Precios ----------
fun PreajusteCompleto.toDomain() = PreajustePrecios(
    id = preajuste.id, nombre = preajuste.nombre, puntosBasicos = preajuste.puntosBasicos,
    productoIds = productos.map { it.productoId }.toSet(),
    metodoPago = preajuste.metodoPago?.let { enumOr<MetodoPago>(it, MetodoPago.EFECTIVO) },
    importeMinimo = preajuste.importeMinimoCent?.let(::Cup), activo = preajuste.activo,
)

fun PreajustePrecios.toEntity() = PreajusteEntity(id, nombre, puntosBasicos, metodoPago?.name, importeMinimo?.centavos, activo)
fun PreajustePrecios.productosEntities(id: Long = this.id) = productoIds.map { PreajusteProductoEntity(id, it) }

