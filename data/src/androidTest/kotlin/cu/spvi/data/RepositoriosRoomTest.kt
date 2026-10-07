package cu.spvi.data

import cu.spvi.domain.model.TipoEntidad
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.spvi.core.money.Cup
import cu.spvi.core.quantity.Cantidad
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.repository.InsumoRepositoryImpl
import cu.spvi.data.repository.PreciosRepositoryImpl
import cu.spvi.data.repository.ProductoRepositoryImpl
import cu.spvi.data.repository.RegistroRepositoryImpl
import cu.spvi.data.repository.TurnoRepositoryImpl
import cu.spvi.data.repository.VentaRepositoryImpl
import cu.spvi.domain.model.Categorias
import cu.spvi.domain.model.DatosCliente
import cu.spvi.domain.model.DetalleVenta
import cu.spvi.domain.model.Insumo
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.model.PreajustePrecios
import cu.spvi.domain.model.ElaboradoEnVenta
import cu.spvi.domain.model.Producto
import cu.spvi.domain.model.Receta
import cu.spvi.domain.model.RecetaLinea
import cu.spvi.domain.model.TipoMovimiento
import cu.spvi.domain.model.Transaccion
import cu.spvi.domain.model.Venta
import cu.spvi.domain.repository.FiltroRegistro
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prompt 15 — repositorios de :data contra Room real EN MEMORIA (sin SQLCipher, sin archivos, sin red).
 * Complementa TurnoRepositoryInstrumentedTest (cierre de turno) con inventario, recetas, ventas en
 * Efectivo/Transferencia, producción al vuelo, atomicidad, Registros y Precios.
 */
@RunWith(AndroidJUnit4::class)
class RepositoriosRoomTest {

    private var ahora = Instant.parse("2026-09-30T12:00:00Z")
    private val clock = Clock { ahora }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpviDatabase::class.java).build()
    private val productos = ProductoRepositoryImpl(db, clock)
    private val insumos = InsumoRepositoryImpl(db, clock)
    private val turnos = TurnoRepositoryImpl(db)
    private val ventas = VentaRepositoryImpl(db)
    private val registros = RegistroRepositoryImpl(db)
    private val precios = PreciosRepositoryImpl(db)

    @After fun cerrarBd() = db.close()

    private fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Ok)?.value ?: error("esperaba Ok y fue $r")
    private fun err(r: AppResult<*>): AppError = (r as? AppResult.Err)?.error ?: error("esperaba Err y fue $r")

    private fun producto(nombre: String, cantidad: Long, categoria: String = "Bebidas", codigo: String? = null, venta: Long = 100, costo: Long = 60) =
        Producto(categoria = categoria, nombre = nombre, precioCosto = Cup.ofPesos(costo), precioVenta = Cup.ofPesos(venta),
            cantidad = cantidad, codigo = codigo, creadoEn = ahora)

    private fun detalle(p: Producto, cantidad: Long) = DetalleVenta(productoId = p.id, nombre = p.nombre, categoria = p.categoria,
        cantidad = cantidad, precioBase = p.precioVenta, precioUnitario = p.precioVenta, costoUnitario = p.precioCosto)

    private val cliente = DatosCliente("Ana Díaz Rey", "90020212345", "+5353000000")

    // ---------------- Inventario ----------------

    @Test fun productoSeCreaBuscaPorCodigoAjustaYEliminaSinPerderHistorial() = runBlocking {
        val id = ok(productos.crear(producto("Refresco", 10, codigo = "7501055363056"), null))
        assertEquals(id, productos.porCodigo("7501055363056")!!.id)
        assertNull(productos.porCodigo("0000000000000"))
        assertEquals(7L, ok(productos.ajustarStock(id, -3, "rotura")))
        assertTrue(err(productos.ajustarStock(id, -8, null)) is AppError.StockInsuficiente) // nunca negativo
        assertEquals(7L, productos.obtener(id)!!.cantidad)

        ok(productos.eliminar(id))
        assertTrue(productos.observarTodos().first().none { it.id == id })   // fuera de la lista
        assertTrue(productos.obtener(id)!!.eliminado)                         // pero sigue para el historial
        assertEquals(AppError.NoEncontrado, err(productos.ajustarStock(id, 1, null)))
        val tipos = registros.movimientos(FiltroRegistro()).first().filter { it.entidadId == id }.map { it.tipo }.toSet()
        assertTrue(TipoMovimiento.ALTA in tipos && TipoMovimiento.AJUSTE in tipos)
    }

    @Test fun insumoUsadoEnRecetaNoSePuedeEliminar() = runBlocking {
        val harina = ok(insumos.crear(Insumo(nombre = "Harina", precio = Cup.ofPesos(10), cantidad = Cantidad.enteras(5), creadoEn = ahora)))
        val pan = ok(productos.crear(producto("Pan", 0, Categorias.ELABORADO), null))
        ok(productos.actualizar(productos.obtener(pan)!!, Receta(pan, listOf(RecetaLinea(harina, Cantidad.enteras(1))))))
        assertEquals(listOf(pan), productos.productosQueUsan(harina).map { it.id })
        assertTrue(err(insumos.eliminar(harina)) is AppError.EnUso)
        assertEquals(Cantidad.enteras(1), productos.receta(pan)!!.lineas.single().cantidad)
    }

    // ---------------- Ventas ----------------

    @Test fun ventaEnEfectivoDescuentaStockYQuedaEnElTurno() = runBlocking {
        val p = productos.obtener(ok(productos.crear(producto("Refresco", 10), null)))!!
        val t = ok(turnos.abrir(ahora, "Ana"))
        val id = ok(ventas.registrar(Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(detalle(p, 4)))))
        assertEquals(6L, productos.obtener(p.id)!!.cantidad)
        val v = ventas.obtener(id)!!
        assertEquals(Cup.ofPesos(400), v.total)
        assertNull(v.transaccion)
        assertEquals(listOf(id), ventas.deTurno(t.id).map { it.id })
        assertTrue(turnos.movimientosDe(t.id).any { it.tipo == TipoMovimiento.VENTA && it.ventaId == id && it.delta == -4L })
    }

    @Test fun ventaPorTransferenciaGuardaLaTransaccionConElCliente() = runBlocking {
        val p = productos.obtener(ok(productos.crear(producto("Refresco", 10), null)))!!
        val t = ok(turnos.abrir(ahora, "Ana"))
        val tr = Transaccion(fecha = ahora, importe = Cup.ofPesos(200), numero = "BR601ADLM8997", cliente = cliente, tarjetaCobro = "9204 **** 1234")
        val id = ok(ventas.registrar(Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.TRANSFERENCIA, detalles = listOf(detalle(p, 2)), transaccion = tr)))
        val guardada = ventas.obtener(id)!!.transaccion!!
        assertEquals(id, guardada.ventaId)
        assertEquals("BR601ADLM8997", guardada.numero)
        assertEquals(cliente, guardada.cliente)
        assertEquals(listOf("BR601ADLM8997"), registros.transacciones(FiltroRegistro(texto = "90020212345")).first().map { it.numero })
    }

    @Test fun ventaConStockInsuficienteOTurnoCerradoEsTodoONada() = runBlocking {
        val a = productos.obtener(ok(productos.crear(producto("Refresco", 10), null)))!!
        val b = productos.obtener(ok(productos.crear(producto("Galletas", 1), null)))!!
        val t = ok(turnos.abrir(ahora, "Ana"))
        val venta = Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(detalle(a, 2), detalle(b, 3)))
        assertEquals(AppError.StockInsuficiente(listOf("Galletas")), err(ventas.registrar(venta)))
        assertEquals(10L, productos.obtener(a.id)!!.cantidad) // la primera línea tampoco se descontó
        assertTrue(ventas.deTurno(t.id).isEmpty())

        ok(turnos.cerrar(ahora, "Ana"))
        assertEquals(AppError.TurnoCerrado, err(ventas.registrar(venta.copy(detalles = listOf(detalle(a, 1))))))
        assertEquals(10L, productos.obtener(a.id)!!.cantidad)
    }

    /** P26: el Elaborado se vende directamente: CONSUMO de sus insumos ligado a la venta, sin tocar su existencia. */
    @Test fun ventaDeElaboradoConsumeInsumosEnLaMismaTransaccionYRollbackSiFalta() = runBlocking {
        val harina = ok(insumos.crear(Insumo(nombre = "Harina", precio = Cup.ofPesos(10), cantidad = Cantidad.enteras(2), creadoEn = ahora)))
        val refresco = productos.obtener(ok(productos.crear(producto("Refresco", 5), null)))!!
        val pan = productos.obtener(ok(productos.crear(producto("Pan", 0, Categorias.ELABORADO, costo = 10), null)))!!
        val t = ok(turnos.abrir(ahora, "Ana"))
        val e = ElaboradoEnVenta(pan.id, pan.nombre, 2, mapOf(harina to Cantidad.enteras(2)))
        val id = ok(ventas.registrar(
            Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(detalle(pan, 2), detalle(refresco, 1))), listOf(e),
        ))
        assertEquals(0L, productos.obtener(pan.id)!!.cantidad)          // sin existencias propias: no se tocó
        assertEquals(4L, productos.obtener(refresco.id)!!.cantidad)
        assertEquals(Cantidad(0), insumos.obtener(harina)!!.cantidad)
        val movs = turnos.movimientosDe(t.id).filter { it.ventaId == id }
        assertEquals(setOf(TipoMovimiento.CONSUMO, TipoMovimiento.VENTA), movs.map { it.tipo }.toSet())
        assertTrue(movs.none { it.entidadId == pan.id && it.entidad == TipoEntidad.PRODUCTO })
        assertEquals("Venta de Pan ×2", movs.single { it.tipo == TipoMovimiento.CONSUMO }.nota)

        // Ya no queda harina: la siguiente venta falla y no deja rastro (tampoco el refresco).
        val otra = ventas.registrar(
            Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(detalle(pan, 1), detalle(refresco, 1))),
            listOf(e.copy(unidades = 1, consumo = mapOf(harina to Cantidad.enteras(1)))),
        )
        assertEquals(AppError.StockInsuficiente(listOf("Harina")), err(otra))
        assertEquals(1, ventas.deTurno(t.id).size)
        assertEquals(4L, productos.obtener(refresco.id)!!.cantidad)
    }

    // ---------------- Registros ----------------

    @Test fun registrosFiltranPorFechaImporteYTexto() = runBlocking {
        val p = productos.obtener(ok(productos.crear(producto("Refresco", 50), null)))!!
        val g = productos.obtener(ok(productos.crear(producto("Galletas", 50, venta = 1000), null)))!!
        val t = ok(turnos.abrir(ahora, "Ana"))
        val ayer = ahora
        ok(ventas.registrar(Venta(turnoId = t.id, fecha = ayer, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(detalle(p, 1)))))   // 100
        ahora = ahora.plus(Duration.ofDays(1))
        ok(ventas.registrar(Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(detalle(g, 2)))))   // 2000

        assertEquals(2, registros.ventas(FiltroRegistro()).first().size)
        assertEquals(1, registros.ventas(FiltroRegistro(desde = ayer.plus(Duration.ofHours(12)))).first().size)
        assertEquals(Cup.ofPesos(100), registros.ventas(FiltroRegistro(importeMax = Cup.ofPesos(500))).first().single().total)
        assertEquals(Cup.ofPesos(2000), registros.ventas(FiltroRegistro(importeMin = Cup.ofPesos(500))).first().single().total)
        assertEquals("Galletas", registros.ventas(FiltroRegistro(texto = "gall")).first().single().detalles.single().nombre)
        // Los comodines de LIKE escritos por el usuario no amplían la búsqueda.
        assertTrue(registros.ventas(FiltroRegistro(texto = "%%x%")).first().isEmpty())
        // Más reciente primero.
        assertEquals(Cup.ofPesos(2000), registros.ventas(FiltroRegistro()).first().first().total)
    }

    // ---------------- Precios ----------------

    @Test fun preajustesSeGuardanActivanYEliminan() = runBlocking {
        val p = ok(productos.crear(producto("Refresco", 5), null))
        val id = ok(precios.guardarPreajuste(PreajustePrecios(nombre = "Mayorista", puntosBasicos = -1000, productoIds = setOf(p), metodoPago = MetodoPago.EFECTIVO)))
        ok(precios.guardarPreajuste(PreajustePrecios(nombre = "Pausado", puntosBasicos = 500, productoIds = setOf(p), activo = false)))
        assertEquals(listOf("Mayorista"), precios.preajustesActivos().map { it.nombre })
        assertEquals(setOf(p), precios.preajustesActivos().single().productoIds)
        ok(precios.eliminarPreajuste(id))
        assertTrue(precios.preajustesActivos().isEmpty())

        ok(productos.actualizarPrecios(mapOf(p to Cup.ofPesos(120))))
        assertEquals(Cup.ofPesos(120), productos.obtener(p)!!.precioVenta)
        assertEquals(AppError.NoEncontrado, err(productos.actualizarPrecios(mapOf(p to Cup.ofPesos(1), 999L to Cup.ofPesos(1)))))
        assertNotNull(productos.obtener(p))
        assertEquals(Cup.ofPesos(120), productos.obtener(p)!!.precioVenta) // atómico
    }
}
