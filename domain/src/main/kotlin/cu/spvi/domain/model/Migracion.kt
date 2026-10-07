package cu.spvi.domain.model

import cu.spvi.licencia.contract.DEVICE_ID_PREFIX
import cu.spvi.core.validation.Validators

/**
 * Resultado de comprobar la autorización de Migrar (la licencia que el desarrollador emite al teléfono nuevo).
 * Ver DECISIONES §O5 / ARQUITECTURA §C5: el contrato GL v1 no tiene un mensaje de "migración".
 */
enum class AutorizacionMigracion {
    /** Licencia auténtica del desarrollador para OTRO teléfono: se puede borrar este. */
    AUTORIZADA,
    /** Es la licencia de este mismo teléfono: no autoriza la migración. */
    DE_ESTE_TELEFONO,
    /** En el texto pegado no hay ninguna licencia. */
    NO_ENCONTRADA,
    /** Texto alterado o no firmado por el desarrollador. */
    RECHAZADA,
}

/** Solicitud de migración que se envía al desarrollador por WhatsApp/SMS (canal NO confidencial). */
object MensajeMigracion {

    /**
     * "spvi:3f2a 9c0d…" → "SPVI:3f2a9c0d…". null si no tiene forma de ID de SPVI.
     * Admite espacios y saltos de línea (al copiar a mano) y el prefijo en minúsculas.
     */
    fun normalizarId(raw: String): String? {
        val limpio = raw.filterNot { it.isWhitespace() }
        if (!limpio.startsWith(DEVICE_ID_PREFIX, ignoreCase = true)) return null
        val id = DEVICE_ID_PREFIX + limpio.substring(DEVICE_ID_PREFIX.length)
        return id.takeIf { Validators.deviceId(it) && it.length > DEVICE_ID_PREFIX.length }
    }

    /**
     * Texto mínimo: IDs de dispositivo, ID de la licencia actual y nombre. SIN carnet ni cuentas: el desarrollador
     * ya los tiene de la solicitud de licencia original y el canal no es confidencial.
     */
    fun construir(origen: String, licenciaId: String?, destino: String, nombre: String?): String = buildString {
        appendLine("SPVI · Solicitud de migración")
        appendLine("Quiero pasar SPVI a otro teléfono y borrarlo de este.")
        nombre?.takeIf { it.isNotBlank() }?.let { appendLine("Nombre: ${it.trim()}") }
        appendLine("ID del teléfono actual: $origen")
        appendLine("Licencia actual: ${licenciaId ?: "sin licencia"}")
        append("ID del teléfono nuevo: $destino")
    }
}
