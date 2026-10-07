package cu.spvi.licencia

import cu.spvi.licencia.contract.IdLicencia
import cu.spvi.licencia.contract.ListaRevocaciones

import cu.spvi.core.validation.Phone
import cu.spvi.core.validation.Validators
import cu.spvi.licencia.contract.Envelope
import cu.spvi.licencia.contract.GlJson
import cu.spvi.licencia.contract.LicenciaCorta
import cu.spvi.licencia.contract.LicensePayload
import cu.spvi.licencia.contract.MensajesLicencia
import cu.spvi.licencia.contract.SolicitudCifrada
import cu.spvi.licencia.contract.RequestPayload
import cu.spvi.licencia.contract.TipoLicencia
import cu.spvi.licencia.contract.Via
import cu.spvi.licencia.crypto.B64
import cu.spvi.licencia.crypto.DeviceKey
import cu.spvi.licencia.crypto.EcP256
import cu.spvi.licencia.crypto.GlLicenseOpener
import cu.spvi.licencia.crypto.fail
import cu.spvi.core.time.Clock
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import cu.spvi.licencia.contract.GlContract

/**
 * Licencia guardada: el envelope COMPLETO + los metadatos que pide el contrato (checklist §5: id, venceEn,
 * tipo, deviceId). Los metadatos son una caché de lectura: en cada arranque se vuelve a verificar el
 * envelope y, si no coinciden con lo descifrado, la licencia se descarta. Nunca un flag "isLicensed".
 * 0.23.0: [envelopeJson] guarda la licencia corta cifrada (`SPVI2:…`, se reconoce por el prefijo); así el
 * almacenamiento no cambia. Una licencia larga (envelope v1) instalada antes se sigue re-verificando hasta que venza.
 */
data class StoredLicense(
    val envelopeJson: String,
    val licenseId: String,
    val emitidaEn: String,
    val tipo: TipoLicencia? = null,
    val venceEn: String? = null,
    val deviceId: String? = null,
)

/** Licencia instalada y verificada (para el panel de Licencia). */
data class InstalledLicense(
    val id: String,
    val tipo: TipoLicencia,
    val emitidaEn: Instant,
    val venceEn: Instant?,
    /** 0.21.0 (C4): apps secundarias que cubre (ausente en la licencia = [GlContract.SECUNDARIAS_DEFECTO]). */
    val secundarias: Int = GlContract.SECUNDARIAS_DEFECTO,
)

/** Resultado de evaluar el estado: [license] solo si hay una licencia auténtica instalada. */
data class Evaluation(val state: LicenseState, val license: InstalledLicense?)

interface LicenseStore {
    suspend fun license(): StoredLicense?
    suspend fun saveLicense(license: StoredLicense)
    suspend fun trialStart(): Instant?
    suspend fun setTrialStart(instant: Instant)
    suspend fun lastSeen(): Instant?
    suspend fun setLastSeen(instant: Instant)
    /** Instante en que este dispositivo cedió su licencia a otro (Ajustes → Migrar). null = nunca. */
    suspend fun migratedAt(): Instant?
    /** Borra la licencia guardada y registra la migración. Lo emitido antes de [at] ya no se acepta aquí. */
    suspend fun markMigrated(at: Instant)
    /**
     * 0.25.0: instante en que la licencia de este teléfono apareció en la lista pública de revocadas (se recuperó en
     * otro teléfono). null = nunca. Se borra al activar una licencia emitida después.
     */
    suspend fun revokedAt(): Instant? = null
    /** 0.25.0: como [markMigrated] y además deja constancia de la revocación (pantalla «Licencia transferida»). */
    suspend fun markRevoked(at: Instant) = markMigrated(at)
    suspend fun clearRevoked() = Unit
}

data class SolicitudInput(
    val nombre: String,
    val apellidos: String,
    val ci: String,
    val telefono: String,
    val tipo: TipoLicencia,
    val via: Via,
    /** 0.21.0 (C4): apps secundarias que se pagan con esta licencia. */
    val secundarias: Int = GlContract.SECUNDARIAS_DEFECTO,
    /** 0.22.0 (L-e): id de la licencia instalada que se renueva ([LicenseManager.licenciaRenovable]); null = primera. */
    val renueva: String? = null,
    /** 0.25.0: id de la licencia que se RECUPERA (teléfono nuevo o reinstalado). Excluyente con [renueva]. */
    val recupera: String? = null,
)

/**
 * 0.23.0: solicitud lista para enviar. [texto] = texto introductorio + renglón en blanco + [codigo]; [codigo] = la
 * solicitud cifrada `SPVIR1:…` (lo que va en el QR por WhatsApp).
 */
data class SolicitudGenerada(val texto: String, val codigo: String)

sealed interface ActivationResult {
    data class Accepted(val state: LicenseState) : ActivationResult
    data object NotFound : ActivationResult
    data object Rejected : ActivationResult
    /** Auténtica pero más antigua que la instalada: se ignora. */
    data object Outdated : ActivationResult
}

/**
 * Autorización de Migrar: la licencia que el desarrollador emite al teléfono NUEVO. El contrato v1 no tiene
 * un mensaje de "migración", así que la autorización es esa licencia: firmada por GL (se verifica sin
 * descifrar) y NO descifrable con la clave de este dispositivo (es de otro).
 */
enum class TransferAuthorization {
    /** Licencia auténtica de GL para otro dispositivo. */
    AUTHORIZED,
    /** Es la licencia de ESTE teléfono (no autoriza nada). */
    SAME_DEVICE,
    /** No hay una licencia corta (`SPVI2:`) en el texto. */
    NOT_FOUND,
    /** Licencia mal formada o sin firma válida del emisor. */
    REJECTED,
}

class LicenseManager(
    private val store: LicenseStore,
    private val deviceKey: DeviceKey,
    val deviceId: String,
    glEcdhSpki: ByteArray?,
    glSignSpkis: List<ByteArray>,
    private val clock: Clock,
    private val random: SecureRandom = SecureRandom(),
    private val trialDays: Int = TRIAL_DAYS,
    /** 0.26.0 (P74): copias de la fecha de la prueba fuera de la app (sobreviven a desinstalar). null = solo la interna. */
    private val registroExterno: cu.spvi.licencia.prueba.RegistroExterno? = null,
    /** 0.26.0 (P74): reloj monótono (mismo arranque); se suma a la detección por `lastSeen`. */
    private val detectorRetroceso: cu.spvi.licencia.prueba.DetectorRetroceso? = null,
) {
    private val glEcdh = glEcdhSpki?.let(EcP256::publicKey)
    private val signKeys = glSignSpkis.map(EcP256::publicKey)
    private val opener = GlLicenseOpener(signKeys, deviceKey)

    /** false si el build no trae las claves de GL: no se pueden emitir solicitudes. */
    val canRequest: Boolean = glEcdh != null && glSignSpkis.isNotEmpty()

    val glEcdhFingerprint: String? = glEcdhSpki?.let(EcP256::fingerprint)

    init {
        require(deviceId.startsWith(cu.spvi.licencia.contract.DEVICE_ID_PREFIX) && Validators.deviceId(deviceId)) {
            "deviceId inválido"
        }
    }

    suspend fun state(): LicenseState = evaluate().state

    /**
     * Estado + licencia instalada. Orden: reloj (anti-retroceso) → licencia guardada (re-verificada
     * criptográficamente) → periodo de prueba.
     */
    suspend fun evaluate(): Evaluation {
        val now = clock.now()
        // 0.26.0 (P74): antes de nada, juntar con el registro externo: gana el inicio de prueba MÁS ANTIGUO y la última
        // fecha vista MÁS RECIENTE (así ni reinstalar reinicia la prueba ni esconde un reloj atrasado). Las copias que no
        // descifran se ignoran y se reescriben: nunca bloquean.
        registroExterno?.let { reg ->
            // Una sola pasada por arranque/refresco: si aún no hay inicio de prueba, el de ahora (el mismo que se guardaría abajo).
            val inicio = store.trialStart() ?: now
            val ext = runCatching { reg.sincronizar(inicio.toEpochMilli(), store.lastSeen()?.toEpochMilli(), trialDays) }.getOrNull()
            ext?.firstInstall?.let(Instant::ofEpochMilli)?.let { f -> if (store.trialStart()?.isAfter(f) != false) store.setTrialStart(f) }
            ext?.lastSeen?.let(Instant::ofEpochMilli)?.let { l -> if (store.lastSeen()?.isBefore(l) != false) store.setLastSeen(l) }
        }
        val last = store.lastSeen()
        val migrated = store.migratedAt()
        val stored = store.license()
        val verified = stored?.let { verifyStored(it, now) }
            ?.takeIf { migrated == null || it.second.emitidaEn.isAfter(migrated) }
        if (last != null && now.isBefore(last.minus(CLOCK_TOLERANCE))) {
            return Evaluation(LicenseState.ClockTampered, verified?.second)
        }
        val detector = detectorRetroceso
        if (detector != null && runCatching { detector.retrocedio(now.toEpochMilli()) }.getOrDefault(false)) {
            return Evaluation(LicenseState.ClockTampered, verified?.second)
        }
        if (last == null || now.isAfter(last)) store.setLastSeen(now)

        verified?.let { return Evaluation(it.first, it.second) }

        // Tras migrar, este teléfono ya no tiene periodo de prueba: solo una licencia emitida después.
        if (migrated != null) return Evaluation(if (store.revokedAt() != null) LicenseState.Revoked else LicenseState.TrialExpired, null)
        val start = store.trialStart() ?: now.also { store.setTrialStart(it) }
        val left = trialDays - Duration.between(start, now).toDays()
        return Evaluation(if (left > 0) LicenseState.Trial(left.toInt()) else LicenseState.TrialExpired, null)
    }

    /**
     * 0.23.0: construye el mensaje de solicitud listo para enviar por [SolicitudInput.via]: texto introductorio con los
     * datos del solicitante + renglón en blanco + solicitud cifrada (`SPVIR1:`, [SolicitudCifrada]).
     */
    fun buildRequest(input: SolicitudInput): SolicitudGenerada {
        val gl = requireNotNull(glEcdh) { "Claves de GL no configuradas en este build" }
        val telefono = requireNotNull(Phone.normalize(input.telefono)) { "Teléfono inválido" }
        require(Validators.nombre(input.nombre)) { "Nombre inválido" }
        require(Validators.nombre(input.apellidos)) { "Apellidos inválidos" }
        require(Validators.ci(input.ci)) { "Carnet de identidad inválido" }
        require(input.secundarias in 0..GlContract.SECUNDARIAS_MAX) { "Número de apps secundarias inválido" }
        require(input.renueva == null || input.renueva.length in 1..64) { "Licencia a renovar inválida" }
        require(input.recupera == null || IdLicencia.extraer(input.recupera) == input.recupera.trim().lowercase()) { "Licencia a recuperar inválida" }
        require(input.renueva == null || input.recupera == null) { "Renovar y recuperar son excluyentes" }

        val nonce = B64.urlNoPad(ByteArray(16).also(random::nextBytes))
        val payload = RequestPayload(
            nombre = input.nombre.trim(),
            apellidos = input.apellidos.trim(),
            ci = input.ci.trim().uppercase(),
            via = input.via,
            telefono = telefono,
            deviceId = deviceId,
            tipo = input.tipo,
            solicitadaEn = clock.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            nonce = nonce,
            devicePub = B64.urlNoPad(EcP256.comprimir(deviceKey.publicSpki())),
            secundarias = input.secundarias,
            renueva = input.renueva,
            recupera = input.recupera?.trim()?.lowercase(),
        )
        val plain = GlJson.encoder.encodeToString(RequestPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        val codigo = SolicitudCifrada.sellar(plain, gl)
        plain.fill(0)
        return SolicitudGenerada(MensajesLicencia.solicitud(payload, codigo), codigo)
    }

    /**
     * 0.22.0 (L-e): id de la licencia que se renovaría con una solicitud nueva: la instalada, auténtica y con
     * vencimiento (activa o vencida). null si no hay licencia, si es perpetua o si no verifica.
     */
    suspend fun licenciaRenovable(): String? = evaluate().license?.takeIf { it.venceEn != null }?.id

    /**
     * Procesa el mensaje de licencia pegado, compartido o leído de un QR. 0.23.0: solo se acepta la licencia corta
     * cifrada (`SPVI2:…`); el texto de arriba es informativo y se ignora. Una licencia larga (envelope v1) ya no se
     * activa: [ActivationResult.NotFound] (no hay licencia de SPVI válida en el texto).
     */
    suspend fun activate(message: String): ActivationResult {
        val corta = LicenciaCorta.extraer(message) ?: return ActivationResult.NotFound
        return activarCorta(corta)
    }

    private suspend fun activarCorta(codigo: LicenciaCorta.Codigo): ActivationResult {
        val payload = runCatching { abrirCorta(codigo) }.getOrNull() ?: return ActivationResult.Rejected
        val state = runCatching {
            LicenseValidator.evaluate(payload, payload.id, deviceId, deviceKey.publicSpki(), clock.now())
        }.getOrNull() ?: return ActivationResult.Rejected
        return instalar(payload, state, envelopeJson = codigo.texto, licenseId = payload.id)
    }

    /** No se instala una licencia más antigua que la actual ni anterior a la migración. */
    private suspend fun instalar(payload: LicensePayload, state: LicenseState, envelopeJson: String, licenseId: String): ActivationResult {
        val current = store.license()
        if (current != null && Instant.parse(payload.emitidaEn).isBefore(Instant.parse(current.emitidaEn))) {
            return ActivationResult.Outdated
        }
        // Una licencia anterior a la migración (p. ej. el mensaje viejo aún en WhatsApp) ya no vale aquí.
        val migrated = store.migratedAt()
        if (migrated != null && !Instant.parse(payload.emitidaEn).isAfter(migrated)) {
            return ActivationResult.Outdated
        }
        store.clearRevoked()
        store.saveLicense(
            StoredLicense(
                envelopeJson = envelopeJson,
                licenseId = licenseId,
                emitidaEn = payload.emitidaEn,
                tipo = payload.tipo,
                venceEn = payload.venceEn,
                deviceId = payload.deviceId,
            ),
        )
        return ActivationResult.Accepted(state)
    }

    /**
     * Licencia corta cifrada → payload equivalente (sin datos personales). Falla (genérico) si la firma no es de GL, si
     * no se descifra con la clave de ESTE teléfono (Keystore) o si la huella no es la de este deviceId y esta clave.
     */
    private fun abrirCorta(codigo: LicenciaCorta.Codigo): LicensePayload {
        if (!firmaCortaValida(codigo)) fail()
        val cuerpo = LicenciaCorta.descifrar(codigo, deviceKey)
        val c = LicenciaCorta.leer(cuerpo) ?: fail()
        if (!MessageDigest.isEqual(c.huella, huellaPropia())) fail()
        return LicensePayload(
            nombre = "", apellidos = "", ci = "", deviceId = deviceId, tipo = c.tipo,
            id = c.id, emitidaEn = c.emitidaEn.toString(), venceEn = c.venceEn?.toString(), estado = c.estado,
            secundarias = c.secundarias,
        )
    }

    private fun huellaPropia(): ByteArray = LicenciaCorta.huella(deviceId, EcP256.comprimir(deviceKey.publicSpki()))

    private fun firmaCortaValida(codigo: LicenciaCorta.Codigo): Boolean = runCatching {
        val der = EcP256.rawToDer(codigo.firma)
        signKeys.any { EcP256.verify(it, codigo.mensajeFirmado, der) }
    }.getOrDefault(false)

    /** Comprueba la autorización de Migrar pegada por el usuario. No guarda nada. */
    suspend fun checkTransferAuthorization(message: String): TransferAuthorization {
        // 0.23.0: la autorización es la licencia corta cifrada que GL emitió al teléfono NUEVO. La firma se comprueba sin
        // descifrar; si además se descifra con la clave de este teléfono y lleva su huella, es la propia.
        val codigo = LicenciaCorta.extraer(message) ?: return TransferAuthorization.NOT_FOUND
        if (!firmaCortaValida(codigo)) return TransferAuthorization.REJECTED
        val propia = runCatching {
            LicenciaCorta.leer(LicenciaCorta.descifrar(codigo, deviceKey))?.let { MessageDigest.isEqual(it.huella, huellaPropia()) }
        }.getOrNull() == true
        return if (propia) TransferAuthorization.SAME_DEVICE else TransferAuthorization.AUTHORIZED
    }

    /**
     * 0.25.0: aplica la lista pública de revocadas (texto de `revocadas.json`). Si su firma es de GL y contiene la
     * licencia guardada en este teléfono, la invalida ([LicenseStore.markRevoked]) y devuelve true. Cualquier otro caso
     * (sin licencia, lista falsa o corrupta, licencia no incluida) no cambia nada.
     */
    suspend fun aplicarRevocaciones(texto: String): Boolean {
        val id = store.license()?.licenseId ?: return false
        val datos = ListaRevocaciones.verificar(texto, signKeys) ?: return false
        if (!ListaRevocaciones.contiene(datos, id)) return false
        store.markRevoked(clock.now())
        return true
    }

    /** 0.25.0: true = la licencia de este teléfono se revocó (se recuperó en otro). */
    suspend fun revocada(): Boolean = store.revokedAt() != null && store.migratedAt() != null &&
        evaluate().state == LicenseState.Revoked

    /** Cede la licencia: la borra y bloquea este dispositivo hasta que se active una licencia emitida después. */
    suspend fun transferOut() {
        store.markMigrated(clock.now())
    }

    private fun openPayload(env: Envelope, licenseId: String): LicensePayload {
        val plain = opener.open(env, licenseId)
        return GlJson.decoder.decodeFromString(LicensePayload.serializer(), plain.toString(Charsets.UTF_8))
    }

    /** null = no verifica (alterada, otra clave de dispositivo, metadatos incoherentes…): se ignora. */
    private fun verifyStored(stored: StoredLicense, now: Instant): Pair<LicenseState, InstalledLicense>? = runCatching {
        val p = if (stored.envelopeJson.startsWith(LicenciaCorta.PREFIJO)) {
            abrirCorta(LicenciaCorta.extraer(stored.envelopeJson) ?: fail())
        } else {
            openPayload(GlJson.envelopeDecoder.decodeFromString(Envelope.serializer(), stored.envelopeJson), stored.licenseId)
        }
        val state = LicenseValidator.evaluate(p, stored.licenseId, deviceId, deviceKey.publicSpki(), now)
        val coherente = p.emitidaEn == stored.emitidaEn &&
            (stored.tipo == null || stored.tipo == p.tipo) &&
            (stored.venceEn == null || stored.venceEn == p.venceEn) &&
            (stored.deviceId == null || stored.deviceId == p.deviceId)
        if (!coherente) return@runCatching null
        state to InstalledLicense(
            p.id, p.tipo, Instant.parse(p.emitidaEn), p.venceEn?.let(Instant::parse), p.secundarias ?: GlContract.SECUNDARIAS_DEFECTO,
        )
    }.getOrNull()

    companion object {
        const val TRIAL_DAYS = 7
        /** Margen para ajustes de hora/zona antes de considerar manipulación del reloj. */
        val CLOCK_TOLERANCE: Duration = Duration.ofHours(2)
    }
}
