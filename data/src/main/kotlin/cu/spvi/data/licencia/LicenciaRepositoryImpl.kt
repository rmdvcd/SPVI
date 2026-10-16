package cu.spvi.data.licencia

import cu.spvi.core.result.runCatchingCancelable
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.result.appCatching
import cu.spvi.core.time.Clock
import cu.spvi.domain.di.IoDispatcher
import cu.spvi.domain.model.Licencia
import cu.spvi.domain.model.SubTokenEmitido
import cu.spvi.domain.repository.LicenciaRepository
import cu.spvi.licencia.ActivationResult
import cu.spvi.licencia.LicenseManager
import cu.spvi.licencia.LicenseStore
import cu.spvi.licencia.LicenseTrust
import cu.spvi.licencia.ModoDesarrollo
import cu.spvi.licencia.SolicitudInput
import cu.spvi.licencia.crypto.B64
import cu.spvi.licencia.crypto.DeviceKey
import cu.spvi.licencia.crypto.EcP256
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Adaptador entre el motor puro de :licencia y la app.
 *  - El [LicenseManager] se construye perezosamente (Keystore = E/S). Todo bajo un mutex: evaluación,
 *    solicitud y activación nunca se solapan.
 *  - Clave ECDH: SOLO la fijada en el build ([LicenseTrust.ecdhSpki]).
 *  - Clave de firma: SOLO la fijada en el build ([LicenseTrust.signSpkis]).
 *  - Los errores hacia la UI son genéricos: nunca se expone qué comprobación criptográfica falló.
 */
@Singleton
class LicenciaRepositoryImpl @Inject constructor(
    private val store: LicenseStore,
    private val deviceKey: DeviceKey,
    private val deviceIds: DeviceIdProvider,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
    /** 0.26.0 (P74): la prueba no se reinicia al reinstalar; reloj monótono además de `lastSeen`. */
    private val registroPrueba: cu.spvi.licencia.prueba.RegistroExterno,
    private val detectorReloj: cu.spvi.licencia.prueba.DetectorRetroceso,
    /**
     * 0.31.0 (mitigación 7): firma los sub-tokens de las apps secundarias. Es una clave de firma propia, distinta de la
     * del dispositivo: rotarla solo obliga a volver a vincular las secundarias.
     */
    private val firmante: cu.spvi.licencia.secondary.FirmanteLicencia,
) : LicenciaRepository {

    private val mutex = Mutex()
    private var manager: LicenseManager? = null
    /** Huella de la ECDH fijada (null = el build no la trae y no se pueden pedir licencias). */
    private val huellaEmisor: String? = LicenseTrust.ecdhSpki()?.let(EcP256::fingerprint)
    private val _snapshot = MutableStateFlow<Licencia?>(null)
    override val snapshot: StateFlow<Licencia?> = _snapshot.asStateFlow()

    private suspend fun managerLocked(): LicenseManager {
        manager?.let { return it }
        return LicenseManager(
            store = store,
            deviceKey = deviceKey,
            deviceId = deviceIds.deviceId,
            glEcdhSpki = LicenseTrust.ecdhSpki(),
            glSignSpkis = LicenseTrust.signSpkis(),
            clock = clock,
            registroExterno = registroPrueba,
            detectorRetroceso = detectorReloj,
            // Mitigación 1 (Opción A): la clave maestra de desarrollo solo vale en builds de depuración. El valor lo
            // pone el arranque de la app con su BuildConfig.DEBUG; sin nadie que lo active queda en false (fail-closed).
            esDebug = ModoDesarrollo.esDebug,
            firmante = firmante,
        ).also { manager = it }
    }

    override suspend fun refrescar(): Licencia = withContext(io) {
        mutex.withLock {
            val m = managerLocked()
            val ev = m.evaluate()
            Licencia(
                estado = ev.state,
                deviceId = m.deviceId,
                puedeSolicitar = m.canRequest,
                huellaEmisor = huellaEmisor,
                instalada = ev.license,
                validacionDisponible = LicenseTrust.canValidate,
            ).also { _snapshot.value = it }
        }
    }

    override suspend fun construirSolicitud(input: SolicitudInput): AppResult<cu.spvi.licencia.SolicitudGenerada> = withContext(io) {
        mutex.withLock {
            appCatching(onError = { if (it is IllegalArgumentException) AppError.Validacion("solicitud", AppError.Regla.FORMATO) else AppError.Cripto }) {
                // 0.22.0 (L-e): si hay una licencia con vencimiento instalada, la solicitud dice cuál se renueva
                // (GL hace que la nueva empiece al terminar esa; sin perder días).
                val m = managerLocked()
                // 0.25.0 (§3): una recuperación no renueva (son excluyentes): GL reemite la licencia indicada.
                m.buildRequest(if (input.recupera != null) input.copy(renueva = null) else input.copy(renueva = m.licenciaRenovable()))
            }
        }
    }

    override suspend fun activar(mensaje: String): ActivationResult = withContext(io) {
        mutex.withLock {
            runCatchingCancelable { managerLocked().activate(mensaje) }.getOrDefault(ActivationResult.Rejected)
        }
    }

    /**
     * 0.31.0 (mitigación 7, Opción A): permiso firmado para la app secundaria [dispositivo] (su `deviceId`) en la
     * ranura [ranura]. `null` = no se puede emitir: sin licencia desbloqueada, cuota 0 o ranura fuera de la cuota.
     */
    override suspend fun emitirSubToken(dispositivo: String, ranura: Int): SubTokenEmitido? = withContext(io) {
        mutex.withLock {
            val m = managerLocked()
            val token = runCatchingCancelable { m.emitirSubToken(dispositivo, ranura) }.getOrNull() ?: return@withLock null
            SubTokenEmitido(token, B64.enc(firmante.publicSpki()), ranura, m.cuotaSecundarias())
        }
    }

    /** 0.25.0 (§3): lista pública de revocadas (firmada por GL). true = la licencia de este teléfono quedó revocada. */
    override suspend fun aplicarRevocaciones(texto: String): Boolean = withContext(io) {
        mutex.withLock { runCatchingCancelable { managerLocked().aplicarRevocaciones(texto) }.getOrDefault(false) }
    } // sin refrescar: quien llama borra los datos y después llama a refrescar (como al revocar la licencia)
}
