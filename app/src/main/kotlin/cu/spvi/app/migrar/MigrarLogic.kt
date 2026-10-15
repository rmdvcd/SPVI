package cu.spvi.app.migrar

import cu.spvi.core.result.AppError
import cu.spvi.domain.model.AutorizacionMigracion
import cu.spvi.domain.usecase.CamposMigracion

object TextosMigrar {
    /** 0.31.0 (P6): paso a llamarse solo «Migrar». */
    const val TITULO = "Migrar"
    const val EXPLICACION = "Pasa tus datos y tu licencia a un teléfono nuevo. Al final, SPVI se borra de este teléfono."
    const val PASO1 = "1. Pide la migración"
    const val PASO1_TEXTO = "Instala SPVI en el teléfono nuevo. Allí entra en Ajustes → Migrar, " +
        "mantén pulsado el \"ID de este teléfono\" para copiarlo y escríbelo o pégalo aquí."
    const val PASO2 = "2. Copia tus datos"
    const val PASO2_TEXTO = "Crea un respaldo y envíalo al teléfono nuevo. Allí: Ajustes → Respaldo → Importar."
    /** 0.20.0 (H7): el respaldo lleva las fichas de los empleados, pero no sus claves. */
    const val PASO2_EMPLEADOS = "Sincroniza antes tus apps de empleados: en el teléfono nuevo hay que vincularlas otra vez."
    const val PASO3 = "3. Autorización del desarrollador"
    const val PASO3_TEXTO = "El desarrollador te enviará la licencia del teléfono nuevo. Actívala allí y pega aquí el mismo mensaje."
    const val PASO4 = "4. Borra este teléfono"
    const val PASO4_TEXTO = "Se borran todos los datos de SPVI de este teléfono y su licencia deja de valer. No se puede deshacer."
    const val PALABRA = "BORRAR"

    // ------------------------------------------------------------------ 0.31.0 (P6): migración por QR

    const val QR_TITULO = "Pasar los datos por QR"
    const val QR_EXPLICACION = "Con los dos teléfonos conectados a la misma wifi (o a la zona wifi del emisor) y con " +
        "SPVI ya instalado en el nuevo, se pasan todos los datos de un teléfono a otro."
    const val EMISOR = "Emisor"
    const val EMISOR_TEXTO = "Este teléfono: enseña el código QR y envía los datos. Al terminar, " +
        "SPVI se borra aquí solo."
    const val RECEPTOR = "Receptor"
    const val RECEPTOR_TEXTO = "Lee el código QR del emisor y recibe los datos."
    const val QR_EMISOR_TITULO = "Código QR de migración"
    const val QR_EMISOR_DETALLE = "En el teléfono nuevo: Ajustes → Migrar → Receptor, y lee este código. " +
        "No se apague ninguna de las dos pantallas hasta que termine."
    const val QR_RECEPTOR_TITULO = "Lee el código QR del emisor"
    const val QR_RECEPTOR_DETALLE = "Apunta con la cámara al código que se ve en el otro teléfono."
    const val QR_ESPERANDO = "Esperando al otro teléfono…"
    const val QR_CONECTANDO = "Conectando con el emisor…"
    const val QR_TRANSFIRIENDO = "Pasando los datos…"
    const val QR_APLICANDO = "Guardando los datos recibidos…"
    const val QR_EMISOR_LISTO = "Migración terminada. SPVI se borró de este teléfono."
    const val QR_RECEPTOR_LISTO = "Migración terminada. Los datos ya están en este teléfono."
    const val QR_CAMBIAR_ROL = "Elegir otro papel"
    const val QR_EMISOR_MOSTRAR = "Mostrar el código QR"
    const val QR_CANCELAR = "Cancelar"
    const val QR_SIN_RED = "Conecta los dos teléfonos a la misma wifi (o abre la zona wifi en el emisor) y vuelve a intentarlo."
    const val QR_NO_EMISOR = "Ese código no es de un emisor. Pide al otro teléfono que entre en Ajustes → Migrar → Emisor."
    const val QR_ERROR_CLAVE = "El código QR no coincide. Pide al otro teléfono que lo muestre de nuevo y vuelve a leerlo."
    const val QR_SIN_CLAVE = "Para migrar hace falta definir antes el acceso con clave (huella, cara o PIN del teléfono)."
    const val QR_SIN_BLOQUEO = "Este teléfono no tiene bloqueo de pantalla. Ponle un PIN, un patrón o una contraseña en Ajustes del teléfono."
    const val QR_IR_AJUSTES = "Ir a Ajustes"
    const val QR_PROMPT = "Confirma que eres tú para migrar"
    const val QR_DESCRIPCION = "Código QR de migración de SPVI"

    /** Mensaje de error del intercambio en lenguaje llano. */
    fun errorQr(e: AppError): String = when (e) {
        is AppError.Validacion -> QR_ERROR_CLAVE
        is AppError.Red -> QR_SIN_RED
        else -> ERROR_GENERICO
    }
    const val TOTAL_PASOS = 4
    const val ERROR_DESTINO_VACIO = "Escribe el ID del teléfono nuevo"
    const val ERROR_DESTINO_FORMATO = "Debe empezar por SPVI: (lo ves en Ajustes → Migrar, en el teléfono nuevo)"
    const val ERROR_DESTINO_MISMO = "Ese es el ID de este teléfono. Escribe el del teléfono nuevo."
    const val ERROR_GENERICO = "No se pudo completar. Inténtalo de nuevo."
    const val COMPLETADA = "Migración terminada. Este teléfono ya no tiene datos de SPVI."
}

fun errorDestino(e: AppError): String = when {
    e is AppError.Validacion && e.campo == CamposMigracion.DESTINO -> when (e.regla) {
        AppError.Regla.REQUERIDO -> TextosMigrar.ERROR_DESTINO_VACIO
        AppError.Regla.NO_PERMITIDO -> TextosMigrar.ERROR_DESTINO_MISMO
        else -> TextosMigrar.ERROR_DESTINO_FORMATO
    }
    else -> TextosMigrar.ERROR_GENERICO
}

/** Resultado de "Comprobar" en lenguaje llano. [ok] = habilita el paso 4. */
data class TextoAutorizacion(val texto: String, val ok: Boolean)

fun AutorizacionMigracion.texto(): TextoAutorizacion = when (this) {
    AutorizacionMigracion.AUTORIZADA -> TextoAutorizacion("Autorización válida del desarrollador. Ya puedes borrar este teléfono.", true)
    AutorizacionMigracion.DE_ESTE_TELEFONO -> TextoAutorizacion("Esa es la licencia de ESTE teléfono. Pega la del teléfono nuevo.", false)
    AutorizacionMigracion.NO_ENCONTRADA -> TextoAutorizacion("No se encontró una licencia en el texto pegado.", false)
    AutorizacionMigracion.RECHAZADA -> TextoAutorizacion("La licencia no es válida. Pide al desarrollador que la envíe de nuevo.", false)
}

/** Confirmación escrita: evita borrar por un toque accidental. Ignora mayúsculas y espacios alrededor. */
fun confirmacionValida(texto: String): Boolean = texto.trim().equals(TextosMigrar.PALABRA, ignoreCase = true)

/**
 * P18 (L9): paso en curso para la cabecera `SpviStepper`. Solo orienta: las 4 tarjetas siguen visibles y el
 * borrado sigue exigiendo la autorización válida y la palabra BORRAR.
 */
fun pasoMigrar(destino: String, errorDestino: String?, autorizacion: String, autorizada: Boolean): Int = when {
    autorizada -> 4
    autorizacion.isNotBlank() -> 3
    destino.trim().startsWith("SPVI:") && errorDestino == null -> 2
    else -> 1
}

fun tituloPasoMigrar(paso: Int): String =
    listOf(TextosMigrar.PASO1, TextosMigrar.PASO2, TextosMigrar.PASO3, TextosMigrar.PASO4)[(paso - 1).coerceIn(0, 3)].substringAfter(". ")
