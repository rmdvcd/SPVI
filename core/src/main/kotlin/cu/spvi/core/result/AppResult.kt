package cu.spvi.core.result

/**
 * Errores tipados de toda la app. Los repositorios y casos de uso devuelven [AppResult] y nunca lanzan hacia la UI.
 * Los textos al usuario se resuelven en presentation (sin datos sensibles en los errores).
 */
sealed interface AppError {

    /** Regla incumplida por un campo. [campo] es el nombre lógico ("nombre", "precioVenta", …) para marcar el TextField. */
    data class Validacion(val campo: String, val regla: Regla = Regla.FORMATO) : AppError

    enum class Regla { REQUERIDO, FORMATO, RANGO, NO_PERMITIDO }

    data object NoEncontrado : AppError
    data class Duplicado(val campo: String) : AppError

    /** No se puede eliminar/modificar porque otra entidad lo usa (p. ej. insumo en una receta). */
    data class EnUso(val detalle: String) : AppError

    /** [faltantes] = nombres de los artículos sin existencias suficientes. */
    data class StockInsuficiente(val faltantes: List<String> = emptyList()) : AppError

    data object TurnoCerrado : AppError
    data object TurnoYaAbierto : AppError
    data object LicenciaBloqueada : AppError
    data object Cripto : AppError

    /** Respaldo: contraseña incorrecta o archivo alterado (AES-GCM no autentica). */
    data object ContrasenaIncorrecta : AppError

    /**
     * Respaldo v2: el archivo llegó cortado ([incompleto], p. ej. una descarga o un envío por Bluetooth interrumpidos)
     * o con bytes cambiados. Se detecta ANTES de pedir la contraseña (longitud + suma de control en la cabecera).
     */
    data class ArchivoDanado(val incompleto: Boolean) : AppError

    /** Archivo importado con formato o versión no soportados. */
    data class FormatoInvalido(val detalle: String) : AppError

    data object Almacenamiento : AppError

    /** P37: esta app secundaria no tiene permiso del dueño para esa acción. */
    data object SinPermiso : AppError

    /**
     * P37: la acción necesita hablar con la app principal y no se pudo (sin red local, principal cerrada o lejos).
     * [pendientes] = turnos y ventas de esta app aún sin enviar.
     */
    data class SinPrincipal(val pendientes: Int = 0) : AppError

    /** P37: vinculación rechazada. [motivo] ya es texto para el usuario (QR vencido, usado, de otro negocio…). */
    data class VinculacionRechazada(val motivo: String) : AppError

    sealed interface Red : AppError {
        data object SinConexion : Red
        data object Timeout : Red
        data class Http(val code: Int) : Red
    }

    data class Desconocido(val causa: Throwable? = null) : AppError
}

sealed interface AppResult<out T> {
    data class Ok<T>(val value: T) : AppResult<T>
    data class Err(val error: AppError) : AppResult<Nothing>
}

val AppResult<*>.isOk: Boolean get() = this is AppResult.Ok

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Ok)?.value

fun <T> AppResult<T>.errorOrNull(): AppError? = (this as? AppResult.Err)?.error

inline fun <T, R> AppResult<T>.map(f: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Ok -> AppResult.Ok(f(value))
    is AppResult.Err -> this
}

inline fun <T, R> AppResult<T>.flatMap(f: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Ok -> f(value)
    is AppResult.Err -> this
}

inline fun <T> AppResult<T>.onOk(f: (T) -> Unit): AppResult<T> = also { if (it is AppResult.Ok) f(it.value) }
inline fun <T> AppResult<T>.onErr(f: (AppError) -> Unit): AppResult<T> = also { if (it is AppResult.Err) f(it.error) }

/** Lista de errores de validación → primer error o Ok(Unit). */
fun List<AppError.Validacion>.toResult(): AppResult<Unit> =
    firstOrNull()?.let { AppResult.Err(it) } ?: AppResult.Ok(Unit)

fun <T> T.ok(): AppResult<T> = AppResult.Ok(this)
fun AppError.err(): AppResult<Nothing> = AppResult.Err(this)

/** Envuelve código que puede lanzar, traduciendo a [AppError]. Nunca se traga la cancelación. */
inline fun <T> appCatching(onError: (Throwable) -> AppError = { AppError.Desconocido(it) }, block: () -> T): AppResult<T> =
    try { AppResult.Ok(block()) } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (e: Throwable) { AppResult.Err(onError(e)) }
