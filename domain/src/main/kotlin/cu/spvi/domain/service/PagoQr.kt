package cu.spvi.domain.service

import cu.spvi.domain.model.Perfil

/**
 * Contenido del QR de cobro por transferencia (C3, cerrado en el Prompt 13).
 *
 * Formato VERIFICADO decodificando el QR que genera Transfermóvil (Mis Cuentas → tarjeta → QR, 30/9/2026):
 *
 *     TRANSFERMOVIL_ETECSA,TRANSFERENCIA,<tarjeta 16 dígitos>,<móvil 8 dígitos sin +53>,
 *
 * (con la coma final). Al escanearlo, Transfermóvil abre «Transferir Efectivo» con la tarjeta y el «Móvil a confirmar»
 * rellenos y el **monto vacío**: el QR oficial no lleva importe. SPVI NO inventa un campo de importe (podría hacer
 * que Transfermóvil rechace el código); el importe se muestra grande bajo el QR para que el cliente lo teclee.
 */
object PagoQr {
    const val PREFIJO = "TRANSFERMOVIL_ETECSA,TRANSFERENCIA"

    sealed interface Resultado {
        data class Ok(val contenido: String, val tarjeta: String, val telefono: String?) : Resultado
        /** No hay tarjeta/cuenta elegida en Pago electrónico (o no es válida). */
        data object SinTarjeta : Resultado
    }

    fun transfermovil(perfil: Perfil): Resultado {
        val tarjeta = perfil.tarjetaPago?.numero?.filter(Char::isDigit)?.takeIf { it.length in 12..20 }
            ?: return Resultado.SinTarjeta
        val movil = perfil.telefonoPago?.numero?.let(::movilLocal)
        return Resultado.Ok("$PREFIJO,$tarjeta,${movil.orEmpty()},", tarjeta, movil)
    }

    /** "+5351815604" → "51815604". Números no cubanos no caben en el campo: null (queda vacío). */
    fun movilLocal(e164: String): String? {
        val d = e164.filter(Char::isDigit)
        return when {
            d.length == 8 -> d
            d.length == 10 && d.startsWith("53") -> d.drop(2)
            else -> null
        }
    }
}
