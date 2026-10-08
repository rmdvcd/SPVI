package cu.spvi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cu.spvi.core.money.Cup
import cu.spvi.data.db.SpviDatabase
import cu.spvi.data.db.entity.DetalleVentaEntity
import cu.spvi.data.db.entity.TurnoEntity
import cu.spvi.data.db.entity.VentaEntity
import cu.spvi.data.repository.VentaRepositoryImpl
import cu.spvi.domain.model.Granularidad
import cu.spvi.domain.model.PeriodoPreset
import cu.spvi.domain.model.TotalesCubo
import cu.spvi.domain.model.VentanaCubo
import cu.spvi.domain.model.validas
import cu.spvi.domain.service.Estadisticas
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SQL real de los gráficos: límites, anulaciones, cubos vacíos, centavos y turnos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VentaAgregadosRoomTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        SpviDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val ventas = VentaRepositoryImpl(db)

    @After fun cerrarBd() = db.close()

    @Test fun `agregados SQL coinciden con la serie pura en marzo de La Habana`() = runBlocking {
        val zona = ZoneId.of("America/Havana")
        val periodo = Estadisticas.rango(PeriodoPreset.MES, Instant.parse("2026-03-20T17:00:00Z"), zona)
        val turno = nuevoTurno(periodo.desde)
        val otroTurno = nuevoTurno(periodo.desde)

        insertarVenta(
            turno,
            periodo.desde,
            listOf(
                linea("Línea 1", cantidad = 2, precioCent = 12_345, costoCent = 5_000),
                linea("Línea 2", cantidad = 1, precioCent = 4_000, costoCent = 1_500),
            ),
        )
        insertarVenta(
            turno,
            LocalDate.of(2026, 3, 8).atTime(12, 0).atZone(zona).toInstant(),
            listOf(linea("Día DST", cantidad = 3, precioCent = 99, costoCent = 45)),
        )
        insertarVenta(turno, LocalDate.of(2026, 3, 9).atTime(12, 0).atZone(zona).toInstant(), listOf(linea("Anulada", 1, 50_000, 10_000)), anulada = true)
        insertarVenta(turno, periodo.hasta, listOf(linea("Límite final", 1, 70_000, 20_000)))
        insertarVenta(otroTurno, periodo.desde, listOf(linea("Otro turno", 1, 90_000, 30_000)))

        val granularidad = Estadisticas.granularidad(periodo.desde, periodo.hasta)
        val ventanas = Estadisticas.ventanas(periodo.desde, periodo.hasta, zona)
        val sql = Estadisticas.serieAgregada(granularidad, ventanas, ventas.totalesPorCubos(ventanas, turno))
        val memoria = Estadisticas.serie(ventas.deTurno(turno).validas(), periodo.desde, periodo.hasta, zona)

        assertEquals(Granularidad.DIA, granularidad)
        assertEquals(31, sql.puntos.size)
        assertEquals(memoria, sql)
        assertEquals(Cup(28_987), sql.puntos.fold(Cup.ZERO) { acc, punto -> acc + punto.ventas })
        assertEquals(Cup(11_635), sql.puntos.fold(Cup.ZERO) { acc, punto -> acc + punto.costo })
        assertTrue("los días sin ventas deben conservarse como cubos vacíos", sql.puntos.any { it.ventas == Cup.ZERO && it.costo == Cup.ZERO })
        assertEquals(
            LocalDate.of(2026, 3, 9).atStartOfDay(zona).toInstant(),
            ventanas[8].inicio,
        )
    }

    @Test fun `límites submilisegundo se comparan con precisión de fecha almacenada`() = runBlocking {
        val turno = nuevoTurno(Instant.EPOCH)
        insertarVenta(turno, Instant.ofEpochMilli(1_000), listOf(linea("Antes", 1, 100, 40)))
        insertarVenta(turno, Instant.ofEpochMilli(1_001), listOf(linea("Dentro 1", 1, 100, 40)))
        insertarVenta(turno, Instant.ofEpochMilli(2_000), listOf(linea("Dentro 2", 1, 100, 40)))
        insertarVenta(turno, Instant.ofEpochMilli(2_001), listOf(linea("Después", 1, 100, 40)))

        val ventana = VentanaCubo(
            inicio = Instant.EPOCH,
            desde = Instant.ofEpochMilli(1_000).plusNanos(500_000),
            hasta = Instant.ofEpochMilli(2_000).plusNanos(500_000),
        )
        val resultado = ventas.totalesPorCubos(listOf(ventana), turno).single()

        assertEquals(Cup(200), resultado.ventas)
        assertEquals(Cup(80), resultado.costo)
    }

    @Test fun `lotes SQL conservan todos los cubos sin ventas`() = runBlocking {
        val inicio = Instant.parse("2026-01-01T00:00:00Z")
        val ventanas = (0..250).map { indice ->
            val desde = inicio.plusSeconds(indice * 3_600L)
            VentanaCubo(desde, desde, desde.plusSeconds(3_600))
        }

        val resultado: List<TotalesCubo> = ventas.totalesPorCubos(ventanas)

        assertEquals(251, resultado.size)
        assertEquals(ventanas.map { it.inicio }, resultado.map { it.inicio })
        assertTrue(resultado.all { it.ventas == Cup.ZERO && it.costo == Cup.ZERO })
    }

    private suspend fun nuevoTurno(abierto: Instant): Long = db.turnoDao().insertar(
        TurnoEntity(
            abiertoEn = abierto.toEpochMilli(),
            cerradoEn = null,
            numVentas = null,
            unidades = null,
            totalCent = null,
            efectivoCent = null,
            transferenciaCent = null,
            costoCent = null,
            numMovimientos = null,
            abiertoPor = "Prueba",
        ),
    )

    private suspend fun insertarVenta(
        turnoId: Long,
        fecha: Instant,
        lineas: List<DetalleVentaEntity>,
        anulada: Boolean = false,
    ) {
        val totalCent = lineas.sumOf { it.precioUnitarioCent * it.cantidad }
        val costoCent = lineas.sumOf { it.costoUnitarioCent * it.cantidad }
        val ventaId = db.ventaDao().insertarVenta(
            VentaEntity(
                turnoId = turnoId,
                fecha = fecha.toEpochMilli(),
                metodoPago = "EFECTIVO",
                totalCent = totalCent,
                costoCent = costoCent,
                unidades = lineas.sumOf { it.cantidad },
                anuladaEn = if (anulada) fecha.toEpochMilli() else null,
            ),
        )
        db.ventaDao().insertarDetalles(lineas.map { it.copy(id = 0, ventaId = ventaId) })
    }

    private fun linea(nombre: String, cantidad: Long, precioCent: Long, costoCent: Long) = DetalleVentaEntity(
        ventaId = 0,
        productoId = 1,
        nombre = nombre,
        categoria = "Pruebas",
        cantidad = cantidad,
        precioBaseCent = precioCent,
        precioUnitarioCent = precioCent,
        costoUnitarioCent = costoCent,
    )
}
