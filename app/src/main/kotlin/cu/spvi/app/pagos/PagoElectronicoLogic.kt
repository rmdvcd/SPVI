package cu.spvi.app.pagos

import cu.spvi.core.result.AppError
import cu.spvi.core.validation.Phone
import cu.spvi.domain.validation.Validadores

enum class TipoCuentaPago { TELEFONO, TARJETA }

object TextosPago {
    const val TITULO = "Pago electrónico"
    const val EXPLICACION = "Cuando un cliente paga por transferencia, el código QR lleva la tarjeta o cuenta que recibe " +
        "el dinero y el teléfono que recibe el mensaje de confirmación. Elige cuáles usar."
    const val EN_USO = "En uso"
    const val ERROR_TELEFONO = "Escribe un teléfono válido, por ejemplo 5123 4567"
    const val ERROR_TARJETA = "Escribe solo los números de la tarjeta o cuenta (12 a 20 dígitos)"
    const val DUPLICADO = "Ese número ya está en la lista"
    const val ERROR_GENERICO = "No se pudo guardar. Inténtalo de nuevo."
    const val GUARDADO = "Pago electrónico guardado"
}

/** Validación en tiempo real (vacío = sin error: los campos nuevos son opcionales). */
fun errorTelefono(texto: String): String? =
    if (texto.isBlank() || Phone.normalize(texto) != null) null else TextosPago.ERROR_TELEFONO

fun errorTarjeta(texto: String): String? =
    if (texto.isBlank() || Validadores.normalizarCuenta(texto) != null) null else TextosPago.ERROR_TARJETA

fun errorNumero(tipo: TipoCuentaPago, texto: String): String? = when {
    texto.isBlank() -> if (tipo == TipoCuentaPago.TELEFONO) TextosPago.ERROR_TELEFONO else TextosPago.ERROR_TARJETA
    tipo == TipoCuentaPago.TELEFONO -> errorTelefono(texto)
    else -> errorTarjeta(texto)
}

/** "9205129900001234" → "9205 1299 0000 1234" (solo en la hoja de edición; en listas va enmascarado). */
fun formatoCuenta(numero: String): String = numero.chunked(4).joinToString(" ")

/** Error de dominio → texto simple (sin detalles internos). */
fun mensajePago(e: AppError): String = when (e) {
    is AppError.Validacion -> if (e.campo == "telefonos") TextosPago.ERROR_TELEFONO else TextosPago.ERROR_TARJETA
    is AppError.Duplicado -> TextosPago.DUPLICADO
    else -> TextosPago.ERROR_GENERICO
}

/** Ventana de alta/edición de un elemento de las listas de Ajustes. [id] null = nuevo. */
data class EdicionPago(
    val tipo: TipoCuentaPago,
    val id: Long? = null,
    val numero: String = "",
    val alias: String = "",
    val mostrarErrores: Boolean = false,
    val errorServidor: String? = null,
) {
    val error: String? get() = errorServidor ?: errorNumero(tipo, numero)
}
