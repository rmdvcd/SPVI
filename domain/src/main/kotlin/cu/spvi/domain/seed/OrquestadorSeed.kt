package cu.spvi.domain.seed

import cu.spvi.core.money.Cup
import cu.spvi.core.quantity.Cantidad
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.Anulacion
import cu.spvi.domain.model.ClaseArticulo
import cu.spvi.domain.model.DatosCliente
import cu.spvi.domain.model.Insumo
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.model.Modulo
import cu.spvi.domain.model.NivelesMinimos
import cu.spvi.domain.model.Perfil
import cu.spvi.domain.model.PreajustePrecios
import cu.spvi.domain.model.Producto
import cu.spvi.domain.model.Receta
import cu.spvi.domain.model.RecetaLinea
import cu.spvi.domain.model.ResumenTurno
import cu.spvi.domain.model.Servicio
import cu.spvi.domain.model.TarjetaBancaria
import cu.spvi.domain.model.Telefono
import cu.spvi.domain.model.Venta
import cu.spvi.domain.repository.InsumoRepository
import cu.spvi.domain.repository.MantenimientoRepository
import cu.spvi.domain.repository.PerfilRepository
import cu.spvi.domain.repository.PreciosRepository
import cu.spvi.domain.repository.PreferenciasRepository
import cu.spvi.domain.repository.ProductoRepository
import cu.spvi.domain.repository.ServicioRepository
import cu.spvi.domain.repository.TurnoRepository
import cu.spvi.domain.repository.VentaRepository
import cu.spvi.domain.service.LineaSolicitada
import cu.spvi.domain.usecase.CotizarVenta
import cu.spvi.domain.usecase.DatosTransferencia
import cu.spvi.domain.usecase.transaccion
import cu.spvi.domain.usecase.validarTransferencia
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Seed «Bodega cubana» (0.29.0, solo debug): borra todo y genera 548 días de historial ficticio coherente
 * corriendo [GeneradorSeed] contra los repositorios reales, con la misma secuencia que [cu.spvi.domain.usecase.RegistrarVenta]
 * pero con fechas explícitas. Fail fast: cualquier `Err` aborta con ese error (no deja stock a medias).
 *
 * Limitaciones conocidas: ALTA/AJUSTE/BAJA de inventario salen con la fecha actual (los repos usan `clock.now()`);
 * sin fotos en productos/servicios.
 */
class OrquestadorSeed @Inject constructor(
    private val cotizar: CotizarVenta,
    private val turnos: TurnoRepository,
    private val ventas: VentaRepository,
    private val productos: ProductoRepository,
    private val insumos: InsumoRepository,
    private val servicios: ServicioRepository,
    private val precios: PreciosRepository,
    private val perfil: PerfilRepository,
    private val preferencias: PreferenciasRepository,
    private val mantenimiento: MantenimientoRepository,
) {
    suspend fun ejecutar(onProgreso: (dia: Int, total: Int) -> Unit, dias: Int = DIAS): AppResult<Unit> {
        val generador = GeneradorSeed()
        val planes = (0 until dias).map { generador.planDia(it) }

        when (val r = mantenimiento.borrarTodo()) {
            is AppResult.Err -> return r
            is AppResult.Ok -> {}
        }
        preferencias.completarOnboarding()
        preferencias.guardarModulos(Modulo.TODOS)
        preferencias.guardarNiveles(NivelesMinimos())
        perfil.guardar(perfilBase())
        val cobrado = perfil.perfil.first()
        perfil.guardar(cobrado.copy(
            pagoTarjetaId = cobrado.tarjetas.firstOrNull()?.id,
            pagoTelefonoId = cobrado.telefonos.firstOrNull()?.id,
        ))

        val base = LocalDate.now(ZONA).minusDays(dias.toLong())
        val apertura0 = base.atTime(8, 0).atZone(ZONA).toInstant()
        val demandaInsumos = demandaInsumos(planes)
        val idsInsumos = when (val r = crearInsumos(demandaInsumos, apertura0)) {
            is AppResult.Err -> return r
            is AppResult.Ok -> r.value
        }
        val idsProductos = when (val r = crearProductos(planes, idsInsumos, apertura0)) {
            is AppResult.Err -> return r
            is AppResult.Ok -> r.value
        }
        val idsServicios = when (val r = crearServicios(idsInsumos, apertura0)) {
            is AppResult.Err -> return r
            is AppResult.Ok -> r.value
        }

        var fondo = Cup.ofPesos(FONDO_INICIAL_PESOS)
        for (i in 0 until dias) {
            val plan = planes[i]
            val fecha = base.plusDays(i.toLong())
            val apertura = fecha.atTime(8, 0).atZone(ZONA).toInstant()
            val cierre = fecha.atTime(20, 0).atZone(ZONA).toInstant()
            val (nombre, apellidos) = VENDEDORES[(i / RACHA_DIAS) % VENDEDORES.size]
            val vendedor = "$nombre $apellidos"
            val actual = perfil.perfil.first()
            perfil.guardar(actual.copy(nombre = nombre, apellidos = apellidos))
            if (plan.descansa) {
                onProgreso(i + 1, dias)
                continue
            }
            val turno = when (val r = turnos.abrir(apertura, vendedor, fondo)) {
                is AppResult.Err -> return r
                is AppResult.Ok -> r.value
            }
            val cobro = perfil.perfil.first()
            plan.ventas.forEachIndexed { n, v ->
                val fechaVenta = apertura.plusSeconds((n + 1) * SEGUNDOS_JORNADA / (plan.ventas.size + 1))
                val lineas = v.lineas.map { l ->
                    val id = if (l.clase == ClaseArticulo.SERVICIO) idsServicios[l.clave]!! else idsProductos[l.clave]!!
                    LineaSolicitada(id, l.cantidad, l.clase)
                }
                val transferencia = if (v.metodo == MetodoPago.TRANSFERENCIA) {
                    DatosTransferencia(datosCliente(v.clienteClave!!), v.numeroTransaccion, v.clienteFijo)
                } else null
                when (val r = validarTransferencia(v.metodo, transferencia)) {
                    is AppResult.Err -> return r
                    is AppResult.Ok -> {}
                }
                val cot = when (val r = cotizar.planificar(lineas, v.metodo)) {
                    is AppResult.Err -> return r
                    is AppResult.Ok -> r.value
                }
                val elaborados = when (val r = cot.elaborados()) {
                    is AppResult.Err -> return r
                    is AppResult.Ok -> r.value
                }
                val tx = transferencia?.let {
                    transaccion(it, fechaVenta, cot.cotizacion.total, cobro.tarjetaPago?.numero, cobro.telefonoPago?.numero)
                }
                val id = when (val r = ventas.registrar(
                    Venta(turnoId = turno.id, fecha = fechaVenta, metodoPago = v.metodo, detalles = cot.cotizacion.detalles, transaccion = tx),
                    elaborados,
                )) {
                    is AppResult.Err -> return r
                    is AppResult.Ok -> r.value
                }
                if (v.anular) {
                    when (val r = ventas.anular(id, Anulacion(fechaVenta, "Cliente desistió", vendedor))) {
                        is AppResult.Err -> return r
                        is AppResult.Ok -> {}
                    }
                }
            }
            plan.caja.forEach { c ->
                when (val r = turnos.registrarCaja(c.tipo, Cup.ofPesos(c.importePesos), c.motivo, vendedor, cierre)) {
                    is AppResult.Err -> return r
                    is AppResult.Ok -> {}
                }
            }
            val resumen = ResumenTurno.calcular(
                ventas.deTurno(turno.id), turnos.movimientosDe(turno.id), turnos.cajaDe(turno.id),
            )
            val esperado = (fondo + resumen.totalEfectivo + resumen.entradasCaja) - resumen.salidasCaja
            val contado = esperado + Cup.ofPesos(plan.descuadrePesos)
            val cerrado = when (val r = turnos.cerrar(cierre, vendedor, contado)) {
                is AppResult.Err -> return r
                is AppResult.Ok -> r.value
            }
            fondo = cerrado.contado ?: contado
            onProgreso(i + 1, dias)
        }

        // Al final para no alterar las cotizaciones del historial (planificar lee los preajustes activos).
        when (val r = precios.guardarPreajuste(
            PreajustePrecios(
                nombre = "Fines de semana +5%",
                puntosBasicos = 500,
                productoIds = CatalogoBodega.PRODUCTOS.take(5).map { idsProductos[it.clave]!! }.toSet(),
                activo = true,
            ),
        )) {
            is AppResult.Err -> return r
            is AppResult.Ok -> {}
        }
        return AppResult.Ok(Unit)
    }

    private fun perfilBase() = Perfil(
        nombre = "Bodega",
        apellidos = "La Esquina",
        ci = "62010112345",
        tarjetas = CatalogoBodega.TARJETAS.map { TarjetaBancaria(0, it.numero, it.alias) },
        telefonos = CatalogoBodega.TELEFONOS.map { Telefono(0, "+53" + it.numero8, it.alias) },
    )

    /** Demanda total de cada producto (unidades) en los planes: stock inicial = demanda + 20 % (mínimo 50). */
    private fun demandaProductos(planes: List<PlanDia>): Map<String, Long> =
        planes.flatMap { it.ventas }.flatMap { it.lineas }
            .filter { it.clase == ClaseArticulo.PRODUCTO }
            .groupBy { it.clave }.mapValues { (_, l) -> l.sumOf { it.cantidad } }

    /** Demanda total de cada insumo (milésimas) por recetas de elaborados y servicios con insumos: + 20 %. */
    private fun demandaInsumos(planes: List<PlanDia>): Map<String, Long> {
        val total = mutableMapOf<String, Long>()
        planes.flatMap { it.ventas }.flatMap { it.lineas }.forEach { l ->
            val receta = if (l.clase == ClaseArticulo.SERVICIO) {
                CatalogoBodega.SERVICIOS.first { it.clave == l.clave }.insumos
            } else {
                CatalogoBodega.RECETAS[l.clave].orEmpty()
            }
            receta.forEach { total[it.insumo] = (total[it.insumo] ?: 0) + it.milesimas * l.cantidad }
        }
        return total
    }

    private suspend fun crearInsumos(demanda: Map<String, Long>, creadoEn: Instant): AppResult<Map<String, Long>> {
        val ids = mutableMapOf<String, Long>()
        CatalogoBodega.INSUMOS.forEach { s ->
            // Mínimo 1 unidad de medida: ningún insumo del catálogo queda en cero aunque no se demande.
            val milesimas = ((demanda[s.clave] ?: 0) * 120 / 100).coerceAtLeast(1_000)
            val id = when (val r = insumos.crear(
                Insumo(0, s.nombre, s.unidad, Cup.ofPesos(s.precioPesos), Cantidad(milesimas), creadoEn = creadoEn),
            )) {
                is AppResult.Err -> return r
                is AppResult.Ok -> r.value
            }
            ids[s.clave] = id
        }
        return AppResult.Ok(ids)
    }

    private suspend fun crearProductos(
        planes: List<PlanDia>, idsInsumos: Map<String, Long>, creadoEn: Instant,
    ): AppResult<Map<String, Long>> {
        val demanda = demandaProductos(planes)
        val ids = mutableMapOf<String, Long>()
        CatalogoBodega.PRODUCTOS.forEach { s ->
            val elaborado = CatalogoBodega.esElaborado(s.clave)
            val receta = if (elaborado) {
                Receta(0, CatalogoBodega.RECETAS[s.clave]!!.map { RecetaLinea(idsInsumos[it.insumo]!!, Cantidad(it.milesimas)) })
            } else null
            // El elaborado no admite existencias (ni foto, caducidad, código ni niveles).
            val cantidad = if (elaborado) 0 else maxOf(50, (demanda[s.clave] ?: 0) * 120 / 100)
            val id = when (val r = productos.crear(
                Producto(
                    0, s.categoria, s.nombre,
                    precioCosto = Cup.ofPesos(s.costoPesos), precioVenta = Cup.ofPesos(s.ventaPesos),
                    cantidad = cantidad,
                    nivelBajo = if (elaborado) null else s.nivelBajo,
                    nivelCritico = if (elaborado) null else s.nivelCritico,
                    creadoEn = creadoEn,
                ),
                receta,
            )) {
                is AppResult.Err -> return r
                is AppResult.Ok -> r.value
            }
            ids[s.clave] = id
        }
        return AppResult.Ok(ids)
    }

    private suspend fun crearServicios(idsInsumos: Map<String, Long>, creadoEn: Instant): AppResult<Map<String, Long>> {
        val ids = mutableMapOf<String, Long>()
        CatalogoBodega.SERVICIOS.forEach { s ->
            val id = when (val r = servicios.crear(
                Servicio(0, s.nombre, s.tipo, Cup.ofPesos(s.importePesos), creadoEn = creadoEn),
                s.insumos.map { RecetaLinea(idsInsumos[it.insumo]!!, Cantidad(it.milesimas)) },
            )) {
                is AppResult.Err -> return r
                is AppResult.Ok -> r.value
            }
            ids[s.clave] = id
        }
        return AppResult.Ok(ids)
    }

    private fun datosCliente(clave: String): DatosCliente {
        val nombre = CatalogoBodega.nombreCliente(clave)
        val fijo = CatalogoBodega.CLIENTES.firstOrNull { it.nombre == nombre }
        return if (fijo != null) DatosCliente(fijo.nombre, fijo.ci, fijo.telefono8)
        else DatosCliente(nombre, ciEventual(nombre), telefonoEventual(nombre))
    }

    /** CI de 11 dígitos derivado del nombre (determinista; el formato no valida fecha). */
    private fun ciEventual(nombre: String): String {
        val n = (nombre.hashCode().toLong() and 0xFFFFFFFFL) % 1_000_000_000L
        return "62%09d".format(n)
    }

    /** Teléfono cubano de 8 dígitos derivado del nombre (determinista). */
    private fun telefonoEventual(nombre: String): String {
        val n = (nombre.hashCode().toLong() ushr 16 and 0xFFFFFFL) % 10_000_000L
        return "5%07d".format(n)
    }

    companion object {
        const val DIAS = 548
        const val FONDO_INICIAL_PESOS = 2000L
        val ZONA: ZoneId = ZoneId.of("America/Havana")
        private const val SEGUNDOS_JORNADA = 12L * 3600L
        private const val RACHA_DIAS = 14
        /** Vendedores por rachas de 14 días (nombre + apellidos del perfil). */
        private val VENDEDORES = CatalogoBodega.VENDEDORES.zip(listOf("Fernández", "Pérez"))
    }
}
