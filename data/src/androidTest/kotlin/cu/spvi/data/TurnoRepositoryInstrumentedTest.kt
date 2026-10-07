package cu.spvi.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.spvi.core.money.Cup
import cu.spvi.core.quantity.Cantidad
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.db.entity.EmpleadoEntity
import cu.spvi.data.db.entity.TurnoEntity
import cu.spvi.data.repository.InsumoRepositoryImpl
import cu.spvi.data.repository.PreciosRepositoryImpl
import cu.spvi.data.repository.ServicioRepositoryImpl
import cu.spvi.data.sync.AjustarStockProducto
import cu.spvi.data.sync.EjecutorComandos
import cu.spvi.data.repository.ProductoRepositoryImpl
import cu.spvi.data.repository.TurnoRepositoryImpl
import cu.spvi.data.repository.VentaRepositoryImpl
import cu.spvi.domain.model.DetalleVenta
import cu.spvi.domain.model.Insumo
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.model.Producto
import cu.spvi.domain.model.TipoEntidad
import cu.spvi.domain.model.Venta
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Turno de venta contra Room real (en memoria; en la app la BD es SQLCipher). Comprueba que el cierre
 * congela ventas, métodos de pago, movimientos de productos e insumos y usuario, y que no se vende fuera de turno.
 */
@RunWith(AndroidJUnit4::class)
class TurnoRepositoryInstrumentedTest {

    private var ahora = Instant.parse("2026-09-30T12:00:00Z")
    private val clock = Clock { ahora }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpviDatabase::class.java).build()
    private val turnos = TurnoRepositoryImpl(db)
    private val ventas = VentaRepositoryImpl(db)
    private val productos = ProductoRepositoryImpl(db, clock)
    private val insumos = InsumoRepositoryImpl(db, clock)

    @After fun cerrarBd() = db.close()

    private fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Ok)?.value ?: error("esperaba Ok y fue $r")

    private fun venta(turnoId: Long, productoId: Long, cantidad: Long, metodo: MetodoPago) = Venta(
        turnoId = turnoId, fecha = ahora, metodoPago = metodo,
        detalles = listOf(DetalleVenta(productoId = productoId, nombre = "Refresco", categoria = "Bebidas", cantidad = cantidad,
            precioBase = Cup.ofPesos(100), precioUnitario = Cup.ofPesos(100), costoUnitario = Cup.ofPesos(60))),
    )

    @Test fun cierreCongelaElRegistroCompleto() = runBlocking {
        val p = ok(productos.crear(Producto(categoria = "Bebidas", nombre = "Refresco", precioCosto = Cup.ofPesos(60), precioVenta = Cup.ofPesos(100), cantidad = 20, creadoEn = ahora), null))
        val i = ok(insumos.crear(Insumo(nombre = "Harina", precio = Cup.ofPesos(10), cantidad = Cantidad.enteras(5), creadoEn = ahora)))

        val t = ok(turnos.abrir(ahora, "Ana Pérez"))
        assertEquals(AppError.TurnoYaAbierto, (turnos.abrir(ahora, "Ana Pérez") as AppResult.Err).error)
        ahora = ahora.plusSeconds(600)
        ok(ventas.registrar(venta(t.id, p, 2, MetodoPago.EFECTIVO)))
        ok(ventas.registrar(venta(t.id, p, 1, MetodoPago.TRANSFERENCIA)))
        ok(insumos.ajustarStock(i, Cantidad(-1_500), "merma"))
        ahora = ahora.plusSeconds(3_000)

        val c = ok(turnos.cerrar(ahora, "Luis"))
        val r = c.resumen!!
        assertEquals("Ana Pérez", c.abiertoPor)
        assertEquals("Luis", c.cerradoPor)
        assertEquals(ahora, c.cerradoEn)
        assertEquals(2, r.numVentas)
        assertEquals(3L, r.unidades)
        assertEquals(Cup.ofPesos(300), r.total)
        assertEquals(Cup.ofPesos(200), r.totalEfectivo)
        assertEquals(Cup.ofPesos(100), r.totalTransferencia)
        assertEquals(1, r.ventasEfectivo)
        assertEquals(1, r.ventasTransferencia)
        assertEquals(Cup.ofPesos(180), r.costo)
        assertEquals(2, r.movimientosProducto) // dos salidas por venta (el ALTA fue antes de abrir)
        assertEquals(1, r.movimientosInsumo)
        assertEquals(3, r.numMovimientos)

        val movs = turnos.movimientosDe(t.id)
        assertEquals(listOf(TipoEntidad.PRODUCTO, TipoEntidad.PRODUCTO, TipoEntidad.INSUMO), movs.map { it.entidad })
        assertEquals(2, ventas.deTurno(t.id).size)
        assertEquals(c, turnos.ultimoCerrado())
        assertNull(turnos.activo())
    }

    @Test fun noSeVendeFueraDeTurnoNiSeAsocianMovimientosAUnTurnoCerrado() = runBlocking {
        val p = ok(productos.crear(Producto(categoria = "Bebidas", nombre = "Refresco", precioCosto = Cup.ofPesos(60), precioVenta = Cup.ofPesos(100), cantidad = 5, creadoEn = ahora), null))
        val t = ok(turnos.abrir(ahora, "Ana"))
        ok(turnos.cerrar(ahora.plusSeconds(60), "Ana"))

        val r = ventas.registrar(venta(t.id, p, 1, MetodoPago.EFECTIVO))
        assertEquals(AppError.TurnoCerrado, (r as AppResult.Err).error)
        assertEquals(5L, productos.obtener(p)!!.cantidad) // el stock no se tocó

        ok(productos.ajustarStock(p, 3, "compra")) // fuera de turno: se permite, pero sin turno
        assertTrue(turnos.movimientosDe(t.id).isEmpty())
        assertEquals(AppError.TurnoCerrado, (turnos.cerrar(ahora, "Ana") as AppResult.Err).error)
    }

    /**
     * 0.19.3: la PRINCIPAL es un punto de venta completo. Con un turno de una secundaria abierto en su BD, abre el suyo,
     * vende, ajusta stock y cierra; los cambios que pide la secundaria van al turno de la secundaria, no al suyo.
     */
    @Test fun laPrincipalVendeConSuPropioTurnoAunqueHayaSecundariasEnTurno() = runBlocking {
        val p = ok(productos.crear(Producto(categoria = "Bebidas", nombre = "Refresco", precioCosto = Cup.ofPesos(60), precioVenta = Cup.ofPesos(100), cantidad = 20, creadoEn = ahora), null))
        val sync = db.syncDao()
        val luis = sync.insertarEmpleado(EmpleadoEntity(nombre = "Luis", permisos = "EDITAR_INVENTARIO", creadoEn = 0, vinculadoEn = 0,
            ultimaSincronizacion = null, clave = null, codigoToken = null, codigoVence = null, activo = true))
        val turnoLuis = sync.insertarTurno(TurnoEntity(abiertoEn = ahora.toEpochMilli(), cerradoEn = null, numVentas = null, unidades = null,
            totalCent = null, efectivoCent = null, transferenciaCent = null, costoCent = null, numMovimientos = null,
            abiertoPor = "Luis", uuid = "turno-luis", empleadoId = luis))

        assertNull(turnos.activo()) // el turno de Luis no es el de la principal
        val t = ok(turnos.abrir(ahora, "Ana"))
        ok(ventas.registrar(venta(t.id, p, 2, MetodoPago.EFECTIVO)))
        ok(productos.ajustarStock(p, 5, "compra"))

        val ejecutor = EjecutorComandos(productos, insumos, ServicioRepositoryImpl(db, clock), PreciosRepositoryImpl(db))
        val r = ejecutor.resultado(1, sync.empleado(luis)!!, AjustarStockProducto(p, -1, "rotura"))
        assertNull(r.error)

        val deLuis = turnos.movimientosDe(turnoLuis)
        assertEquals(1, deLuis.size)
        assertTrue(deLuis.single().nota!!.contains("Luis"))

        val c = ok(turnos.cerrar(ahora.plusSeconds(60), "Ana"))
        assertEquals(1, c.resumen!!.numVentas)
        assertEquals(2, c.resumen!!.numMovimientos) // la salida por la venta y la compra; no la rotura de Luis
        assertEquals(22L, productos.obtener(p)!!.cantidad) // 20 − 2 + 5 − 1
        assertNull(db.turnoDao().obtener(turnoLuis)!!.cerradoEn) // cerrar el suyo no toca el de Luis
    }
}
