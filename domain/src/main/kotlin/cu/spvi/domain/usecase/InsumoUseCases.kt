package cu.spvi.domain.usecase

import cu.spvi.core.quantity.Cantidad
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.result.map
import cu.spvi.core.result.toResult
import cu.spvi.core.time.Clock
import cu.spvi.domain.model.Insumo
import cu.spvi.domain.repository.InsumoRepository
import cu.spvi.domain.repository.ProductoRepository
import cu.spvi.domain.service.Recetas
import cu.spvi.domain.validation.Validadores
import cu.spvi.domain.model.Receta
import cu.spvi.domain.repository.ServicioRepository
import javax.inject.Inject

/**
 * Crea o edita un insumo. Si cambia su precio, recalcula el costo de los Elaborados que lo usan
 * (el costo de las ventas ya hechas no cambia: está congelado en cada línea).
 */
class GuardarInsumo @Inject constructor(
    private val insumos: InsumoRepository,
    private val productos: ProductoRepository,
    private val servicios: ServicioRepository,
    private val clock: Clock,
) {
    /** [cantidadLeida]: existencia mostrada al abrir la ficha (0.21.6, ver `Stock.cantidadAlGuardar`); null = manda la escrita. */
    suspend operator fun invoke(insumo: Insumo, cantidadLeida: Cantidad? = null): AppResult<Long> {
        val now = clock.now()
        val i = insumo.copy(nombre = insumo.nombre.trim())
        Validadores.insumo(i).toResult().let { if (it is AppResult.Err) return it }
        if (i.id == 0L) return insumos.crear(i.copy(creadoEn = now, actualizadoEn = now))

        val actual = insumos.obtener(i.id) ?: return AppResult.Err(AppError.NoEncontrado)
        if (i.precio > actual.precio) {
            validarCostosDependientes(i)?.let { return AppResult.Err(it) }
        }
        val r = insumos.actualizar(i.copy(creadoEn = actual.creadoEn, actualizadoEn = now), cantidadLeida)
        if (r is AppResult.Err) return r
        if (actual.precio != i.precio) recalcularElaborados(i.id)
        return AppResult.Ok(i.id)
    }

    /** Impide subir un costo si vuelve invendible por margen un Elaborado o un Servicio activo que usa el insumo. */
    private suspend fun validarCostosDependientes(candidato: Insumo): AppError? {
        for (p in productos.productosQueUsan(candidato.id)) {
            val receta = productos.receta(p.id) ?: continue
            val mapa = insumos.obtenerVarios(receta.lineas.map { it.insumoId }).associateBy { it.id } + (candidato.id to candidato)
            val costo = when (val r = Recetas.costo(receta, mapa)) {
                is AppResult.Ok -> r.value
                is AppResult.Err -> return r.error
            }
            if (p.precioVenta <= costo) return AppError.Validacion("costoElaborados", AppError.Regla.RANGO)
        }
        for (s in servicios.serviciosQueUsan(candidato.id)) {
            val lineas = servicios.insumos(s.id)
            val mapa = insumos.obtenerVarios(lineas.map { it.insumoId }).associateBy { it.id } + (candidato.id to candidato)
            val costo = when (val r = Recetas.costo(Receta(s.id, lineas), mapa)) {
                is AppResult.Ok -> r.value
                is AppResult.Err -> return r.error
            }
            if (s.importe <= costo) return AppError.Validacion("costoServicios", AppError.Regla.RANGO)
        }
        return null
    }

    private suspend fun recalcularElaborados(insumoId: Long) {
        for (p in productos.productosQueUsan(insumoId)) {
            val receta = productos.receta(p.id) ?: continue
            val mapa = insumos.obtenerVarios(receta.lineas.map { it.insumoId }).associateBy { it.id }
            val costo = (Recetas.costo(receta, mapa) as? AppResult.Ok)?.value ?: continue
            if (costo != p.precioCosto) productos.actualizar(p.copy(precioCosto = costo), receta)
        }
    }
}
