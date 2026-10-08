package cu.spvi.app.integracion

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cu.spvi.core.result.AppResult
import cu.spvi.data.repository.MantenimientoRepositoryImpl
import cu.spvi.domain.model.Periodo
import cu.spvi.domain.model.validas
import cu.spvi.domain.seed.OrquestadorSeed
import cu.spvi.domain.service.Estadisticas
import cu.spvi.domain.usecase.CotizarVenta
import cu.spvi.domain.usecase.ObtenerGraficosPeriodo
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T1.4: compara la agregación Room/SQL con la referencia pura sobre el seed completo de 548 días. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class GraficosSqlVsMemoriaTest {

    private val zona = ZoneId.of("America/Havana")

    @Test fun `la serie SQL coincide centavo por centavo con Estadisticas en el seed`() = runBlocking {
        val entorno = EntornoIntegracion()
        try {
            entorno.ahora = Instant.now()
            val mantenimiento = MantenimientoRepositoryImpl(
                ApplicationProvider.getApplicationContext<Context>(),
                entorno.db,
                entorno.prefs,
                Dispatchers.IO,
            )
            val orquestador = OrquestadorSeed(
                cotizar = CotizarVenta(entorno.productos, entorno.precios, entorno.insumos, entorno.servicios),
                turnos = entorno.turnos,
                ventas = entorno.ventas,
                productos = entorno.productos,
                insumos = entorno.insumos,
                servicios = entorno.servicios,
                precios = entorno.precios,
                perfil = entorno.perfil,
                preferencias = entorno.prefs,
                mantenimiento = mantenimiento,
            )
            val generado = orquestador.ejecutar({ _, _ -> }, dias = OrquestadorSeed.DIAS)
            assertTrue("el seed falló: $generado", generado is AppResult.Ok)

            val hasta = entorno.clock.now()
            val desde = hasta.minus(Duration.ofDays(365))
            val periodo = Periodo.Rango(desde, hasta)
            val ventasDelAnio = entorno.ventas.entre(desde, hasta).validas()
            assertTrue("el seed debe aportar al menos 500 ventas válidas: ${ventasDelAnio.size}", ventasDelAnio.size > 500)

            val esperado = Estadisticas.serie(ventasDelAnio, desde, hasta, zona)
            val resultado = ObtenerGraficosPeriodo(entorno.ventas, entorno.turnos, entorno.clock, Dispatchers.IO)(periodo, zone = zona)
            val actual = when (resultado) {
                is AppResult.Ok -> resultado.value.serie
                is AppResult.Err -> error("el caso de uso devolvió un error: ${resultado.error}")
            }

            assertEquals(esperado, actual)
        } finally {
            entorno.cerrar()
        }
    }
}
