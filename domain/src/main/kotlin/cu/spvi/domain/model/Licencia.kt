package cu.spvi.domain.model

import cu.spvi.licencia.InstalledLicense
import cu.spvi.licencia.LicenseState
import cu.spvi.licencia.contract.TipoLicencia as ContratoTipoLicencia
import cu.spvi.licencia.contract.Via
import cu.spvi.licencia.secundariasPermitidas

/*
 * Los tipos de licencia son parte del contrato firmado con GL: la única fuente de verdad es :licencia.
 * El dominio los re-exporta con nombres de negocio en vez de duplicarlos (duplicar = riesgo de divergencia).
 */
typealias EstadoLicencia = LicenseState
typealias TipoLicencia = ContratoTipoLicencia
typealias ViaSolicitud = Via
typealias LicenciaInstalada = InstalledLicense

/**
 * Foto del estado de licencia para la UI. No contiene secretos: solo datos públicos o del propio usuario.
 * - [huellaEmisor]: SHA-256 de la clave ECDH de GL fijada en el build (null = el build no la trae: no se puede solicitar).
 * - [validacionDisponible]: false si el build no trae la clave de FIRMA de GL (no se podría validar ninguna licencia).
 * - [instalada]: licencia auténtica guardada (también si está vencida o revocada).
 */
data class Licencia(
    val estado: EstadoLicencia,
    val deviceId: String,
    val puedeSolicitar: Boolean,
    val huellaEmisor: String?,
    val instalada: LicenciaInstalada? = null,
    val validacionDisponible: Boolean = true,
) {
    val faltaClaveEmisor: Boolean get() = huellaEmisor == null
    /** 0.21.0 (C4): apps secundarias que cubre la licencia (o la prueba). */
    val secundariasPermitidas: Int get() = estado.secundariasPermitidas
}
