package cu.spvi.app.common

import cu.spvi.core.result.AppError

/**
 * P37: mensajes de los errores propios de Principal/Secundaria. Las pantallas los usan como respaldo de sus propios
 * textos (null = no es un error de vinculación: la pantalla muestra su mensaje genérico).
 */
object TextosSync {
    const val SIN_PERMISO = "El dueño no te dio permiso para esto. Pídeselo desde su app."
    const val SIN_RED = "Conéctate a la wifi del local o activa la zona wifi de este teléfono."

    fun sinPrincipal(pendientes: Int): String = buildString {
        append("No se encuentra la app principal. Conecta los dos teléfonos a la misma wifi.")
        if (pendientes > 0) append(if (pendientes == 1) " Hay 1 envío pendiente." else " Hay $pendientes envíos pendientes.")
    }
}

fun mensajeSync(e: AppError): String? = when (e) {
    AppError.SinPermiso -> TextosSync.SIN_PERMISO
    is AppError.SinPrincipal -> TextosSync.sinPrincipal(e.pendientes)
    is AppError.VinculacionRechazada -> e.motivo
    AppError.Red.SinConexion -> TextosSync.SIN_RED
    else -> null
}
