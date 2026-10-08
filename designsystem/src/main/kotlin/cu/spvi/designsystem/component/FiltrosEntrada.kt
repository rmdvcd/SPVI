package cu.spvi.designsystem.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * 0.21.0 (C10, P46): filtros de entrada comunes a TODOS los campos. [SpviTextField] aplica el suyo a cada pulsación,
 * así que un carácter no permitido simplemente no aparece (el usuario no tiene que borrar nada). La validación de
 * negocio (obligatorio, rango, Luhn…) sigue en el dominio; esto solo impide escribir lo que nunca sería válido.
 *
 * Suposición documentada: se recorta silenciosamente al máximo de caracteres (pegar un texto largo no da error).
 * Sin filtro explícito, cada campo usa [TEXTO]: sin caracteres de control y como mucho [MAX_TEXTO] caracteres.
 */
enum class FiltroEntrada(val teclado: KeyboardOptions, val maximo: Int) {
    /** Texto libre de una o varias líneas (descripción, nota). Quita caracteres de control (salvo el salto de línea). */
    TEXTO(KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), 500), // = MAX_TEXTO
    /** Textos largos que se pegan tal cual (mensaje de licencia, SMS, contraseña): solo quita caracteres de control. */
    LIBRE(KeyboardOptions.Default, 20_000),
    /** Nombres de personas, productos, categorías…: sin saltos de línea ni símbolos de control; espacios simples. */
    NOMBRE(KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), 80),
    /** Buscadores: una línea, sin control. */
    BUSQUEDA(KeyboardOptions(capitalization = KeyboardCapitalization.None), 60),
    /** Móvil cubano sin prefijo: solo dígitos, 8. */
    TELEFONO(KeyboardOptions(keyboardType = KeyboardType.Phone), 8),
    /** Tarjeta bancaria: solo dígitos, 16. */
    TARJETA(KeyboardOptions(keyboardType = KeyboardType.Number), 16),
    /** Carné de identidad: solo dígitos, 11. */
    CARNE(KeyboardOptions(keyboardType = KeyboardType.Number), 11),
    /** Cantidades y niveles enteros (sin signo). */
    ENTERO(KeyboardOptions(keyboardType = KeyboardType.Number), 9),
    /** Cantidades de insumos: dígitos y UN separador decimal (punto o coma, que se guarda como punto), hasta 3 decimales. */
    DECIMAL(KeyboardOptions(keyboardType = KeyboardType.Decimal), 12),
    /** Importes CUP: decimal con punto/coma y miles agrupados tipo «1,450.00»; hasta 2 decimales. */
    DINERO(KeyboardOptions(keyboardType = KeyboardType.Decimal), 12),
    /** Identificador ASCII corto: letras, dígitos y - . _ sin espacios. */
    CODIGO(KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters), 64),
    /** Nº de transacción de Transfermóvil: letras y dígitos, en mayúsculas. */
    TRANSACCION(KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters), 30),
    /** Porcentaje 0–100, hasta 2 decimales (el dominio comprueba el rango). */
    PORCENTAJE(KeyboardOptions(keyboardType = KeyboardType.Decimal), 6),
    ;

    /** Lo que queda del texto escrito o pegado. Idempotente: aplicar dos veces da lo mismo. */
    fun aplicar(s: String): String = when (this) {
        TEXTO, LIBRE -> s.filter { it == '\n' || !it.isISOControl() }
        NOMBRE, BUSQUEDA -> s.map { if (it.isISOControl()) ' ' else it }.joinToString("").replace(Regex(" {2,}"), " ")
        // Al pegar «+53 5XXXXXXX» se quita el prefijo del país (el campo es el móvil de 8 cifras).
        TELEFONO -> s.filter { it in '0'..'9' }.let { d -> if (d.length > 8 && d.startsWith("53")) d.drop(2) else d }
        TARJETA, CARNE, ENTERO -> s.filter { it in '0'..'9' }
        PORCENTAJE -> decimal(s, 2)
        DECIMAL -> decimal(s, 3)
        DINERO -> dinero(s)
        CODIGO -> s.filter { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '.' || it == '_' } // = dominio (3–64)
        TRANSACCION -> s.filter { it.isLetterOrDigit() && it.code < 128 }.uppercase()
    }.take(maximo)

    companion object {
        const val MAX_TEXTO = 500

        private val GRUPOS_MILES = Regex("^[0-9]{1,3}(?:,[0-9]{3})+$")
        private val GRUPOS_MILES_DECIMAL = Regex("^[0-9]{1,3}(?:,[0-9]{3})+\\.[0-9]{0,2}$")
        private val DECIMAL_COMA = Regex("^[0-9]+,[0-9]{0,2}$")

        /** CUP: reconoce miles tipo «1,450.00» y coma decimal «2,5» sin convertir un pegado mal formado en otro importe. */
        private fun dinero(s: String): String {
            val texto = s.trim().removeSuffix("CUP").trim()
            if (texto.any { it !in '0'..'9' && it != ',' && it != '.' }) return ""
            if (',' in texto && '.' in texto) {
                return when {
                    GRUPOS_MILES_DECIMAL.matches(texto) -> texto.replace(",", "")
                    texto.indexOf('.') < texto.indexOf(',') && texto.substringAfter(',').isEmpty() ->
                        decimal(texto.substringBefore(','), 2) // ignora solo un separador extra al final mientras se escribe
                    else -> "" // coma y punto en orden/formato inválido; no adivinar el importe pegado
                }
            }
            if (',' in texto) {
                return when {
                    GRUPOS_MILES.matches(texto) -> texto.replace(",", "")
                    DECIMAL_COMA.matches(texto) -> texto.replace(',', '.')
                    else -> ""
                }
            }
            return decimal(texto, 2)
        }

        /** Dígitos y como mucho un separador decimal, con [decimales] cifras detrás; permite estados parciales como «0.». */
        private fun decimal(s: String, decimales: Int): String {
            val out = StringBuilder()
            var separador = false
            var tras = 0
            for (c in s) {
                if ((c == '.' || c == ',') && separador) break
                when {
                    c in '0'..'9' -> if (!separador) out.append(c) else if (tras < decimales) { out.append(c); tras++ }
                    (c == '.' || c == ',') && !separador -> {
                        if (out.isEmpty()) out.append('0')
                        separador = true
                        out.append('.')
                    }
                }
            }
            return out.toString()
        }
    }
}
