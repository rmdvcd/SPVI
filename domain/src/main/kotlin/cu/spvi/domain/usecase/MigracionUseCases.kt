package cu.spvi.domain.usecase

import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.AutorizacionMigracion
import cu.spvi.domain.model.MensajeMigracion
import cu.spvi.domain.repository.LicenciaRepository
import cu.spvi.domain.repository.MantenimientoRepository
import cu.spvi.domain.repository.PerfilRepository
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Migrar (Ajustes). Flujo sin tocar el contrato GL (ARQUITECTURA §C5):
 *  1. Solicitud al desarrollador con el ID del teléfono nuevo ([ConstruirSolicitudMigracion]).
 *  2. Respaldo COMPLETO cifrado → se importa en el teléfono nuevo (Ajustes → Respaldo).
 *  3. Autorización = la licencia que el desarrollador emite al teléfono nuevo ([VerificarAutorizacionMigracion]).
 *  4. Borrado de este teléfono ([CompletarMigracion]): cede la licencia y borra los datos.
 */
object CamposMigracion {
    const val DESTINO = "destino"
    const val AUTORIZACION = "autorizacion"
}

class ConstruirSolicitudMigracion @Inject constructor(
    private val licencias: LicenciaRepository,
    private val perfil: PerfilRepository,
) {
    suspend operator fun invoke(destinoRaw: String): AppResult<String> {
        if (destinoRaw.isBlank()) return AppResult.Err(AppError.Validacion(CamposMigracion.DESTINO, AppError.Regla.REQUERIDO))
        val destino = MensajeMigracion.normalizarId(destinoRaw)
            ?: return AppResult.Err(AppError.Validacion(CamposMigracion.DESTINO, AppError.Regla.FORMATO))
        val lic = licencias.snapshot.value ?: licencias.refrescar()
        if (destino.equals(lic.deviceId, ignoreCase = true)) {
            return AppResult.Err(AppError.Validacion(CamposMigracion.DESTINO, AppError.Regla.NO_PERMITIDO))
        }
        val p = perfil.perfil.first()
        val nombre = listOf(p.nombre, p.apellidos).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
        return AppResult.Ok(MensajeMigracion.construir(lic.deviceId, lic.instalada?.id, destino, nombre))
    }
}

class VerificarAutorizacionMigracion @Inject constructor(private val licencias: LicenciaRepository) {
    suspend operator fun invoke(mensaje: String): AutorizacionMigracion =
        if (mensaje.isBlank()) AutorizacionMigracion.NO_ENCONTRADA else licencias.verificarAutorizacionMigracion(mensaje)
}

/**
 * Paso final e irreversible. Vuelve a verificar la autorización (la UI no puede saltársela) y, en este orden:
 * cede la licencia → borra los datos → re-evalúa la licencia (el teléfono queda bloqueado).
 * Ceder primero es lo seguro: si el borrado fallara, el teléfono queda bloqueado igualmente y no hay dos
 * copias funcionando. Todo en [NonCancellable]: salir de la pantalla no deja el proceso a medias.
 */
class CompletarMigracion @Inject constructor(
    private val licencias: LicenciaRepository,
    private val mantenimiento: MantenimientoRepository,
) {
    suspend operator fun invoke(autorizacion: String): AppResult<Unit> = withContext(NonCancellable) {
        if (licencias.verificarAutorizacionMigracion(autorizacion) != AutorizacionMigracion.AUTORIZADA) {
            return@withContext AppResult.Err(AppError.Validacion(CamposMigracion.AUTORIZACION, AppError.Regla.NO_PERMITIDO))
        }
        when (val cedida = licencias.cederLicencia()) {
            is AppResult.Err -> return@withContext cedida
            is AppResult.Ok -> Unit
        }
        val borrado = mantenimiento.borrarTodo()
        licencias.refrescar()
        borrado
    }
}
