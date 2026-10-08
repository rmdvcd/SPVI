package cu.spvi.app.integracion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.domain.model.Periodo
import cu.spvi.domain.model.Serie
import cu.spvi.domain.model.validas
import cu.spvi.domain.service.Estadisticas
import cu.spvi.domain.seed.OrquestadorSeed
import cu.spvi.data.repository.MantenimientoRepositoryImpl
import cu.spvi.domain.usecase.CotizarVenta
import cu.spvi.domain.usecase.ObtenerGraficosPeriodo
import cu.spvi.domain.usecase.ObtenerResumenGeneral
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T1.5 (F1) — Rendimiento de Inicio con **18 meses** de datos (el seed real de la app, 548 días).
 *
 * Qué mide y por qué así:
 *
 * 1. **Agregación (lo que se congelaba):** `Estadisticas.serie` sobre un año de ventas, ejecutada a propósito en el
 *    hilo del test. Es el trabajo que antes ocurría en el hilo de UI porque los casos de uso no cambiaban de
 *    contexto. Umbral 250 ms: si esto se pasa, la pantalla se nota; si el teléfono lo hace en 30 ms, hay margen.
 * 2. **Carga completa con el arreglo:** `ObtenerResumenGeneral` (30 días) y `ObtenerGraficosPeriodo` (365 días)
 *    tal como los llama Inicio, ahora en `withContext(io)`. Umbral 3000 ms (generoso a propósito: esto incluye
 *    disco cifrado con SQLCipher y en un teléfono de gama baja puede tardar; el objetivo del test es que no se
 *    degrade a segundos, no ganar una carrera).
 *
 * Se ejecuta solo con `./gradlew spviInstrumentedTests` (necesita teléfono o emulador). El CI **no** lo ejecuta
 * todavía: en cuanto haya emulador en el CI, este es el test que hay que encender primero.
 *
 * Referencia de 0.30.0 (PC de desarrollo, emulador API 35): la agregación, entre 10 y 40 ms; el resumen, ~150 ms;
 * los gráficos del año, ~400 ms. Teléfono real: pendiente (T0.6).
 */
@RunWith(AndroidJUnit4::class)
class RendimientoInicioTest {

    private val zona: ZoneId = ZoneId.of("America/Havana")

    @Test
    fun inicioConDieciochoMesesDeDatos() = runBlocking {
        val entorno = EntornoIntegracion()
        try {
            // Fecha real del teléfono: el seed genera hasta «ayer» con LocalDate.now(), no con el reloj del entorno.
            entorno.ahora = Instant.now()
            val reloj: Clock = entorno.clock

            val mantenimiento = MantenimientoRepositoryImpl(ApplicationProvider.getApplicationContext(), entorno.db, entorno.prefs, Dispatchers.IO)
            val orquestador = OrquestadorSeed(
                cotizar = CotizarVenta(entorno.productos, entorno.precios, entorno.insumos, entorno.servicios),
                turnos = entorno.turnos, ventas = entorno.ventas, productos = entorno.productos,
                insumos = entorno.insumos, servicios = entorno.servicios, precios = entorno.precios,
                perfil = entorno.perfil, preferencias = entorno.prefs, mantenimiento = mantenimiento,
            )
            val generado = orquestador.ejecutar({ _, _ -> }, dias = OrquestadorSeed.DIAS)
            assertTrue("el seed falló: $generado", generado is AppResult.Ok)

            val ahora = reloj.now()
            val desde365 = ahora.minus(Duration.ofDays(365))
            val rango = Periodo.Rango(desde365, ahora)

            // Datos reales de la BD para las comprobaciones.
            val ventasDelAnio = entorno.ventas.entre(desde365, ahora)
            val validas = ventasDelAnio.validas()
            assertTrue("hacen falta miles de ventas para que la prueba diga algo: ${validas.size}", validas.size > 500)

            // 1) La agregación pura, en el hilo del test (lo que antes corría en Main).
            val tAgregacion = medirSync { Estadisticas.serie(validas, desde365, ahora, zona) }

            // 2) Camino real: los dos casos de uso de Inicio, ya con withContext(io).
            val obtenerResumen = ObtenerResumenGeneral(entorno.ventas, entorno.productos, entorno.insumos, entorno.servicios, reloj, Dispatchers.IO)
            val obtenerGraficos = ObtenerGraficosPeriodo(entorno.ventas, entorno.turnos, reloj, Dispatchers.IO)
            val tResumen = medir { obtenerResumen(zona) }
            var serieSql: Serie? = null
            val tGraficos = medir {
                obtenerGraficos(rango, zone = zona).also { resultado ->
                    if (resultado is AppResult.Ok) serieSql = resultado.value.serie
                }
            }
            assertEquals("SQL debe coincidir centavo por centavo con la serie pura del seed", Estadisticas.serie(validas, desde365, ahora, zona), serieSql)

            val informe = """
                |RendimientoInicioTest — 18 meses (${ventasDelAnio.size} ventas en el año, ${validas.size} válidas)
                |  agregación de un año (antes en el hilo de UI): ${tAgregacion} ms  (umbral 250)
                |  ObtenerResumenGeneral (30 días):               ${tResumen} ms  (umbral 3000)
                |  ObtenerGraficosPeriodo (365 días):            ${tGraficos} ms  (umbral 3000)
            """.trimMargin()
            println(informe)

            assertTrue("la agregación de un año tardó ${tAgregacion} ms (umbral 250)", tAgregacion < 250)
            assertTrue("el resumen tardó ${tResumen} ms (umbral 3000)", tResumen < 3000)
            assertTrue("los gráficos del año tardaron ${tGraficos} ms (umbral 3000)", tGraficos < 3000)
        } finally {
            entorno.cerrar()
        }
    }

    /** Tiempo en milisegundos de [bloque]; se calienta una vez para no medir el primer acceso a disco. */
    private fun <T> medirSync(bloque: () -> T): Long {
        bloque()
        val inicio = System.nanoTime()
        bloque()
        return Duration.ofNanos(System.nanoTime() - inicio).toMillis()
    }

    private suspend fun <T> medir(bloque: suspend () -> T): Long {
        bloque()
        val inicio = System.nanoTime()
        bloque()
        return Duration.ofNanos(System.nanoTime() - inicio).toMillis()
    }
}
