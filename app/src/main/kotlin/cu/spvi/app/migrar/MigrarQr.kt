package cu.spvi.app.migrar

import java.security.SecureRandom

/**
 * 0.31.0 (P6): papel de cada teléfono en la migración por código QR.
 *  - EMISOR: el teléfono que se va (enseña el QR, envía los datos y se borra al terminar).
 *  - RECEPTOR: el teléfono nuevo (lee el QR, recibe los datos y los aplica).
 */
enum class RolMigracion { EMISOR, RECEPTOR }

/**
 * 0.31.0 (P6): lo que va dentro del código QR de la migración. Lo genera el emisor y lo lee el receptor con la
 * cámara: dónde conectarse ([host]:[puerto]) y con qué [clave] (aleatoria, de un solo uso, 32 caracteres hex).
 * Auditoría 2026-10-14 (S-02): la clave sube de 8 a 16 bytes (128 bits): es la llave maestra del apretón de
 * manos del transporte cifrado y no debe poder probarse por fuerza bruta fuera de línea.
 *
 * Texto: `SPVIM1|<EMISOR|RECEPTOR>|<host>|<puerto>|<clave>|<origen>`.
 * El [origen] es solo informativo: el identificador del teléfono emisor, para que el receptor vea a quién le está
 * recibiendo los datos. Puede contener `|`, por eso va al final.
 */
data class CodigoMigracion(
    val rol: RolMigracion,
    val host: String,
    val puerto: Int,
    val clave: String,
    val origen: String = "",
) {
    fun texto(): String = (listOf(PREFIJO, rol.name, host, puerto.toString(), clave, origen)).joinToString(SEP)

    companion object {
        const val PREFIJO = "SPVIM1"
        const val SEP = "|"
        const val LARGO_CLAVE = 32

        /** null si no es un código de SPVI, si el rol no es Emisor/Receptor o si falta o sobra algún dato. */
        fun parsear(texto: String): CodigoMigracion? {
            val t = texto.trim()
            if (!t.startsWith("$PREFIJO$SEP")) return null
            val partes = t.split(SEP)
            if (partes.size < 6) return null
            val rol = RolMigracion.entries.firstOrNull { it.name == partes[1] } ?: return null
            val puerto = partes[3].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val clave = partes[4]
            if (clave.length < LARGO_CLAVE) return null
            return CodigoMigracion(rol, partes[2], puerto, clave, partes.drop(5).joinToString(SEP))
        }

        /** Clave de un solo uso: 16 bytes aleatorios en hexadecimal (128 bits). */
        fun clave(): String {
            val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
            return b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}
