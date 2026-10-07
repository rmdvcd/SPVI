package cu.spvi.domain

import cu.spvi.core.money.Cup
import cu.spvi.core.quantity.Cantidad
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.model.MovimientoInventario
import cu.spvi.domain.model.Perfil
import cu.spvi.domain.model.ResumenTurno
import cu.spvi.domain.model.TipoEntidad
import cu.spvi.domain.model.TipoMovimiento
import cu.spvi.domain.model.UnidadMedida
import cu.spvi.domain.service.LineaSolicitada
import cu.spvi.domain.usecase.AbrirTurno
import cu.spvi.domain.usecase.CerrarTurno
import cu.spvi.domain.usecase.CotizarVenta
import cu.spvi.domain.usecase.ObservarHistorialTurnos
import cu.spvi.domain.usecase.ObservarPermisoVenta
import cu.spvi.domain.usecase.ObtenerDetalleTurno
import cu.spvi.domain.usecase.RegistrarVenta
import cu.spvi.domain.usecase.UsuarioActual
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnoUseCasesTest {
    private val clock = FixedClock()
    private val productos = FakeProductos()
    private val insumos = FakeInsumos()
    private val turnos = FakeTurnos()
    private val ventas = FakeVentas(productos, turnos)
    private val perfil = FakePerfil(Perfil(nombre = " Ana ", apellidos = "Pérez  López"))
    private val precios = FakePrecios()
    private val usuario = UsuarioActual(perfil)
    private val abrir = AbrirTurno(turnos, usuario, clock)
    private val cerrar = CerrarTurno(turnos, usuario, clock)
    private val registrar = RegistrarVenta(CotizarVenta(productos, precios, FakeInsumos(), FakeServicios()), turnos, ventas, perfil, clock)
    private val detalle = ObtenerDetalleTurno(turnos, ventas, insumos)

    private fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Ok)?.value ?: error("esperaba Ok y fue $r")
    private fun err(r: AppResult<*>): AppError = (r as? AppResult.Err)?.error ?: error("esperaba Err y fue $r")

    private fun movInsumo(id: Long, nombre: String, milesimas: Long, turnoId: Long) = MovimientoInventario(
        fecha = clock.now, tipo = TipoMovimiento.CONSUMO, entidad = TipoEntidad.INSUMO, entidadId = id, nombre = nombre,
        delta = milesimas, existenciaResultante = 0, turnoId = turnoId,
    )

    @Test fun `el usuario es el nombre del Perfil o el valor por defecto`() {
        assertEquals("Ana Pérez López", UsuarioActual.nombreDe(Perfil(nombre = " Ana ", apellidos = "Pérez  López")))
        assertEquals(UsuarioActual.SIN_NOMBRE, UsuarioActual.nombreDe(Perfil()))
        assertEquals(UsuarioActual.MAX, UsuarioActual.nombreDe(Perfil(nombre = "x".repeat(200))).length)
    }

    @Test fun `abrir y cerrar congelan hora y usuario`() = runBlocking {
        val t = ok(abrir(Cup.ZERO))
        assertEquals(clock.now, t.abiertoEn)
        assertEquals("Ana Pérez López", t.abiertoPor)
        clock.now = clock.now.plusSeconds(3600)
        perfil.state.value = Perfil(nombre = "Luis")
        val c = ok(cerrar(Cup.ZERO))
        assertEquals(clock.now, c.cerradoEn)
        assertEquals("Luis", c.cerradoPor)
        assertEquals("Ana Pérez López", c.abiertoPor)
        assertFalse(c.abierto)
        assertEquals(3600, c.duracion(clock.now.plusSeconds(99)).seconds)
    }

    @Test fun `cerrar sin turno abierto falla y abrir dos veces tambien`() = runBlocking {
        assertEquals(AppError.TurnoCerrado, err(cerrar(Cup.ZERO)))
        ok(abrir(Cup.ZERO))
        assertEquals(AppError.TurnoYaAbierto, err(abrir(Cup.ZERO)))
    }

    @Test fun `no se vende fuera de turno ni antes de abrir ni despues de cerrar`() = runBlocking {
        productos.put(producto(1, cantidad = 10))
        assertEquals(AppError.TurnoCerrado, err(registrar(listOf(LineaSolicitada(1, 1)), MetodoPago.EFECTIVO)))
        ok(abrir(Cup.ZERO)); ok(registrar(listOf(LineaSolicitada(1, 1)), MetodoPago.EFECTIVO)); ok(cerrar(Cup.ZERO))
        assertEquals(AppError.TurnoCerrado, err(registrar(listOf(LineaSolicitada(1, 1)), MetodoPago.EFECTIVO)))
        assertEquals(9L, productos.obtener(1)!!.cantidad) // solo la venta dentro del turno
        assertEquals(1, ventas.ventas.size)
    }

    @Test fun `el cierre registra totales metodos de pago y movimientos`() = runBlocking {
        productos.put(producto(1, "Refresco", cantidad = 10, venta = 100, costo = 60), producto(2, "Pan", cantidad = 10, venta = 50, costo = 20))
        val t = ok(abrir(Cup.ZERO))
        ok(registrar(listOf(LineaSolicitada(1, 2)), MetodoPago.EFECTIVO))
        ok(registrar(listOf(LineaSolicitada(1, 1), LineaSolicitada(2, 3)), MetodoPago.EFECTIVO))
        turnos.movimientos += movInsumo(500, "Harina", -1_500, t.id)
        val r = ok(cerrar(Cup.ZERO)).resumen!!
        assertEquals(2, r.numVentas)
        assertEquals(6L, r.unidades)
        assertEquals(Cup.ofPesos(450), r.total)
        assertEquals(Cup.ofPesos(450), r.totalEfectivo)
        assertEquals(Cup.ZERO, r.totalTransferencia)
        assertEquals(2, r.ventasEfectivo)
        assertEquals(0, r.ventasTransferencia)
        assertEquals(Cup.ofPesos(240), r.costo)
        assertEquals(Cup.ofPesos(210), r.ganancia)
        assertEquals(3, r.movimientosProducto) // una salida por línea vendida
        assertEquals(1, r.movimientosInsumo)
        assertEquals(4, r.numMovimientos)
    }

    @Test fun `detalle del turno con vendidos e insumos agrupados`() = runBlocking {
        productos.put(producto(1, "Refresco"), producto(2, "Pan", venta = 50))
        insumos.put(insumo(500, "Harina").copy(unidad = UnidadMedida.KILOGRAMO), insumo(501, "Azúcar"))
        val t = ok(abrir(Cup.ZERO))
        ok(registrar(listOf(LineaSolicitada(2, 1)), MetodoPago.EFECTIVO))
        ok(registrar(listOf(LineaSolicitada(1, 2), LineaSolicitada(2, 1)), MetodoPago.EFECTIVO))
        turnos.movimientos += listOf(movInsumo(500, "Harina", -1_000, t.id), movInsumo(500, "Harina", -250, t.id), movInsumo(501, "Azúcar", -100, t.id))
        turnos.movimientos += movInsumo(501, "Azúcar", -9_999, turnoId = 99) // de otro turno: no cuenta

        val abierto = ok(detalle(t.id))
        assertTrue(abierto.provisional)
        assertEquals(ResumenTurno.calcular(abierto.ventas, abierto.movimientos), abierto.resumen)

        ok(cerrar(Cup.ZERO))
        val d = ok(detalle(t.id))
        assertFalse(d.provisional)
        assertEquals(listOf("Refresco" to 2L, "Pan" to 2L), d.vendidos.map { it.nombre to it.unidades }) // empate: mayor importe primero
        assertEquals(Cup.ofPesos(200), d.vendidos.first { it.nombre == "Refresco" }.total)
        assertEquals(listOf("Harina", "Azúcar"), d.insumos.map { it.nombre })
        assertEquals(Cantidad(-1_250), d.insumos.first().cantidad)
        assertEquals("kg", d.insumos.first().simbolo)
        assertEquals(3, d.movimientosProducto.size)
        assertEquals(3, d.movimientosInsumo.size)
        assertEquals(d.turno.resumen, d.resumen)
    }

    @Test fun `detalle de un turno inexistente`() = runBlocking {
        assertEquals(AppError.NoEncontrado, err(detalle(42)))
    }

    @Test fun `historial con el abierto primero y permiso de venta`() = runBlocking {
        val permiso = ObservarPermisoVenta(turnos)
        assertEquals(ObservarPermisoVenta.Permiso.SinTurno, permiso().first())
        ok(abrir(Cup.ZERO)); clock.now = clock.now.plusSeconds(60); ok(cerrar(Cup.ZERO))
        clock.now = clock.now.plusSeconds(60); val t2 = ok(abrir(Cup.ZERO))
        assertEquals(ObservarPermisoVenta.Permiso.Permitido(t2), permiso().first())
        val h = ObservarHistorialTurnos(turnos)().first()
        assertEquals(listOf(2L, 1L), h.map { it.id })
        assertTrue(h.first().abierto)
        ok(cerrar(Cup.ZERO))
        assertEquals(ObservarPermisoVenta.Permiso.SinTurno, permiso().first())
        assertNull(turnos.activo())
    }
}
