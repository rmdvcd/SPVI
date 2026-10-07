package cu.spvi.domain.usecase

import cu.spvi.core.time.Dates
import cu.spvi.domain.model.validas

import cu.spvi.domain.repository.ServicioRepository
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.domain.model.ConteoAlertas
import cu.spvi.domain.model.GraficosPeriodo
import cu.spvi.domain.model.OpcionPeriodo
import cu.spvi.domain.model.Periodo
import cu.spvi.domain.model.ResumenGeneral
import cu.spvi.domain.model.PeriodoPreset
import cu.spvi.domain.repository.InsumoRepository
import cu.spvi.domain.repository.PreferenciasRepository
import cu.spvi.domain.repository.ProductoRepository
import cu.spvi.domain.repository.TurnoRepository
import cu.spvi.domain.repository.VentaRepository
import cu.spvi.domain.service.Estadisticas
import cu.spvi.domain.service.Stock
import java.time.LocalDate
import java.time.ZoneId
import cu.spvi.domain.model.Preferencias
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Contadores de alertas de Inicio; se recalculan al cambiar productos, insumos o niveles. */
class ObservarAlertas @Inject constructor(
    private val productos: ProductoRepository,
    private val insumos: InsumoRepository,
    private val preferencias: PreferenciasRepository,
    private val clock: Clock,
) {
    operator fun invoke(zone: ZoneId = ZoneId.systemDefault()): Flow<ConteoAlertas> =
        combine(productos.observarTodos(), insumos.observarTodos(), preferencias.preferencias) { ps, ins, pref ->
            Stock.conteo(ps, ins, pref.niveles, Dates.localDate(clock.now(), zone), Preferencias.DIAS_AVISO_CADUCIDAD)
        }
}

/** Período por defecto del gráfico Ventas: turno abierto; si no, el último cerrado; si no hay turnos, hoy. */
class PeriodoPorDefecto @Inject constructor(private val turnos: TurnoRepository, private val clock: Clock) {
    suspend operator fun invoke(zone: ZoneId = ZoneId.systemDefault()): Periodo =
        (turnos.activo() ?: turnos.ultimoCerrado())?.let { Periodo.DeTurno(it.id) }
            ?: Estadisticas.rango(PeriodoPreset.HOY, clock.now(), zone)
}

class RangoDePreset @Inject constructor(private val clock: Clock) {
    operator fun invoke(preset: PeriodoPreset, zone: ZoneId = ZoneId.systemDefault()): Periodo.Rango =
        Estadisticas.rango(preset, clock.now(), zone)
}

/** Opción del selector → período concreto. */
class ResolverPeriodo @Inject constructor(
    private val porDefecto: PeriodoPorDefecto,
    private val rango: RangoDePreset,
) {
    suspend operator fun invoke(opcion: OpcionPeriodo, zone: ZoneId = ZoneId.systemDefault()): Periodo = when (opcion) {
        OpcionPeriodo.TURNO -> porDefecto(zone)
        OpcionPeriodo.HOY -> rango(PeriodoPreset.HOY, zone)
        OpcionPeriodo.SEMANA -> rango(PeriodoPreset.SEMANA, zone)
        OpcionPeriodo.MES -> rango(PeriodoPreset.MES, zone)
        OpcionPeriodo.ANIO -> rango(PeriodoPreset.ANIO, zone)
    }
}

/**
 * Ventas (barras) y Ganancia Neta (área costo vs venta) del período. Un turno abierto se mide hasta "ahora".
 * [pedidoTurno] = el usuario eligió TURNO: si el período resultó ser un rango es que aún no hay turnos.
 */
class ObtenerGraficosPeriodo @Inject constructor(
    private val ventas: VentaRepository,
    private val turnos: TurnoRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(periodo: Periodo, pedidoTurno: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): AppResult<GraficosPeriodo> =
        when (periodo) {
            is Periodo.DeTurno -> {
                val t = turnos.obtener(periodo.turnoId) ?: return AppResult.Err(AppError.NoEncontrado)
                val hasta = maxOf(t.cerradoEn ?: clock.now(), t.abiertoEn.plusSeconds(1))
                AppResult.Ok(GraficosPeriodo(periodo, Estadisticas.serie(ventas.deTurno(t.id).validas(), t.abiertoEn, hasta, zone), turno = t))
            }
            is Periodo.Rango -> AppResult.Ok(
                GraficosPeriodo(
                    periodo = periodo,
                    serie = Estadisticas.serie(ventas.entre(periodo.desde, periodo.hasta).validas(), periodo.desde, periodo.hasta, zone),
                    sinTurnos = pedidoTurno,
                ),
            )
        }
}

/**
 * Inventario por categorías (existencias actuales), Métodos de pago y Top 3 de los últimos
 * [ResumenGeneral.dias] días (incluido hoy). No depende del selector de período.
 */
class ObtenerResumenGeneral @Inject constructor(
    private val ventas: VentaRepository,
    private val productos: ProductoRepository,
    private val insumos: InsumoRepository,
    private val servicios: ServicioRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(zone: ZoneId = ZoneId.systemDefault()): ResumenGeneral {
        val manana = clock.now().atZone(zone).toLocalDate().plusDays(1) // 0.21.6: por fecha (cambio de hora a las 00:00)
        val lista = ventas.entre(manana.minusDays(DIAS.toLong()).atStartOfDay(zone).toInstant(), manana.atStartOfDay(zone).toInstant()).validas()
        val inventario = productos.observarTodos().first()
        return ResumenGeneral(
            categorias = Estadisticas.distribucionCategorias(inventario, insumos.observarTodos().first()),
            metodosPago = Estadisticas.distribucionMetodos(lista),
            top3 = Estadisticas.top3(lista, inventario),
            dias = DIAS,
            servicios = Estadisticas.topServicios(lista, servicios.observarTodos().first()),
            empleados = Estadisticas.topEmpleados(lista),
            clientes = Estadisticas.topClientes(lista),
        )
    }

    companion object {
        const val DIAS = 30
    }
}
