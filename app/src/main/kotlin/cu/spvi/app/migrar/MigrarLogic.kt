package cu.spvi.app.migrar

import cu.spvi.core.result.AppError
import cu.spvi.domain.model.AutorizacionMigracion
import cu.spvi.domain.usecase.CamposMigracion

object TextosMigrar {
    const val TITULO = "Migrar a otro teléfono"
    const val EXPLICACION = "Pasa tus datos y tu licencia a un teléfono nuevo. Al final, SPVI se borra de este teléfono. " +
        "Hace falta la autorización del desarrollador."
    const val PASO1 = "1. Pide la migración"
    const val PASO1_TEXTO = "Instala SPVI en el teléfono nuevo y ábrelo. Allí entra en Ajustes → Migrar a otro teléfono, " +
        "mantén pulsado el \"ID de este teléfono\" para copiarlo y escríbelo o pégalo aquí."
    const val PASO2 = "2. Copia tus datos"
    const val PASO2_TEXTO = "Crea un respaldo completo (la contraseña es opcional) y envíalo al teléfono nuevo. Allí: Ajustes → Respaldo → Importar."
    /** 0.20.0 (H7): el respaldo lleva las fichas de los empleados, pero no sus claves. */
    const val PASO2_EMPLEADOS = "Si tienes apps de empleados vinculadas, sincronízalas antes: en el teléfono nuevo habrá que vincularlas otra vez y se borran sus datos no enviados."
    const val PASO3 = "3. Autorización del desarrollador"
    const val PASO3_TEXTO = "El desarrollador te enviará la licencia del teléfono nuevo. Actívala allí y pega aquí el mismo mensaje."
    const val PASO4 = "4. Borra este teléfono"
    const val PASO4_TEXTO = "Se borran todos los datos de SPVI de este teléfono y su licencia deja de valer. No se puede deshacer."
    const val PALABRA = "BORRAR"
    const val TOTAL_PASOS = 4
    const val ERROR_DESTINO_VACIO = "Escribe el ID del teléfono nuevo"
    const val ERROR_DESTINO_FORMATO = "Debe empezar por SPVI: (lo ves en Ajustes → Migrar a otro teléfono, en el teléfono nuevo)"
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
