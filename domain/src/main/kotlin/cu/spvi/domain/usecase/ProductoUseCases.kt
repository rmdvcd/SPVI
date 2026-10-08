package cu.spvi.domain.usecase

import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.result.map
import cu.spvi.core.result.toResult
import cu.spvi.core.time.Clock
import cu.spvi.domain.model.Categorias
import cu.spvi.domain.model.Identificacion
import cu.spvi.domain.model.identidad
import cu.spvi.domain.model.Producto
import cu.spvi.domain.model.Receta
import cu.spvi.domain.repository.InsumoRepository
import cu.spvi.domain.repository.ProductoRepository
import cu.spvi.domain.service.Recetas
import cu.spvi.domain.validation.Validadores
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ObtenerCategorias @Inject constructor(private val repo: ProductoRepository) {
    suspend operator fun invoke(): List<String> = Categorias.combinar(repo.categoriasEnUso())
}

/** Crea o edita un producto: normaliza campos, aplica las reglas de Elaborado y valida sus datos. */
class GuardarProducto @Inject constructor(
    private val productos: ProductoRepository,
    private val insumos: InsumoRepository,
    private val clock: Clock,
) {
    /** [cantidadLeida]: existencia mostrada al abrir la ficha; null = manda la cantidad escrita. */
    suspend operator fun invoke(
        producto: Producto, receta: Receta?, cantidadLeida: Long? = null,
    ): AppResult<Long> {
        val now = clock.now()
        var p = producto.copy(
            nombre = producto.nombre.trim(),
            categoria = producto.categoria.trim(),
            descripcion = producto.descripcion?.trim()?.ifEmpty { null },
        )
        val r: Receta?
        if (p.esElaborado) {
            // P26: sin existencias ni niveles propios (se vende mientras alcancen los insumos).
            p = p.copy(categoria = Categorias.ELABORADO, fotoUri = null, fechaCaducidad = null, cantidad = 0, nivelBajo = null, nivelCritico = null)
            val recetaEntrada = receta ?: return AppResult.Err(AppError.Validacion("receta", AppError.Regla.REQUERIDO))
            Validadores.receta(recetaEntrada).toResult().let { if (it is AppResult.Err) return it }
            r = recetaEntrada.copy(productoId = p.id)
            val costo = Recetas.costo(r, insumos.obtenerVarios(r.lineas.map { it.insumoId }).associateBy { it.id })
            p = p.copy(precioCosto = (costo as? AppResult.Ok)?.value ?: return costo as AppResult.Err)
        } else {
            r = null
        }
        Validadores.producto(p).toResult().let { if (it is AppResult.Err) return it }
        errorIdentificacion(p.id, p.nombre, p.descripcion, productos.observarTodos().first().map { it.identidad })
            ?.let { return AppResult.Err(it) }

        return if (p.id == 0L) {
            productos.crear(p.copy(creadoEn = now, actualizadoEn = now), r)
        } else {
            val actual = productos.obtener(p.id)?.takeUnless { it.eliminado } ?: return AppResult.Err(AppError.NoEncontrado)
            productos.actualizar(p.copy(creadoEn = actual.creadoEn, actualizadoEn = now), r, cantidadLeida).map { p.id }
        }
    }
}

/**
 * 0.24.0: error de la regla «mismo nombre → descripción distinta» ([Identificacion]), o null si se distingue.
 * Falta descripción → Validacion("descripcion", REQUERIDO); descripción repetida → Duplicado("descripcion").
 */
fun errorIdentificacion(id: Long, nombre: String, descripcion: String?, otros: List<Triple<Long, String, String?>>): AppError? =
    when (Identificacion.conflicto(id, nombre, descripcion, otros)) {
        Identificacion.Conflicto.FALTA_DESCRIPCION -> AppError.Validacion("descripcion", AppError.Regla.REQUERIDO)
        Identificacion.Conflicto.DESCRIPCION_REPETIDA -> AppError.Duplicado("descripcion")
        null -> null
    }
