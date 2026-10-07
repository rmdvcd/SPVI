package cu.spvi.app.ajustes.seed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Genera la BD de prueba (seed «Bodega cubana»): publica el progreso día a día y termina en Ok o Error. */
@HiltViewModel
class SeedViewModel @Inject constructor(
    private val ejecutar: EjecutorSeed,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    sealed interface Estado {
        data object Idle : Estado
        data class Ejecutando(val dia: Int, val total: Int) : Estado
        data object Ok : Estado
        data class Error(val mensaje: String) : Estado
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Idle)
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    /** Arranca la generación en IO. Ignora la llamada si ya está ejecutando. */
    fun iniciar() {
        if (_estado.value is Estado.Ejecutando) return
        viewModelScope.launch(io) {
            _estado.value = Estado.Ejecutando(0, 0)
            when (val r = ejecutar { dia, total -> _estado.value = Estado.Ejecutando(dia, total) }) {
                is AppResult.Ok -> _estado.value = Estado.Ok
                is AppResult.Err -> _estado.value = Estado.Error(mensaje(r.error))
            }
        }
    }
}

/** Texto al usuario para un fallo del seed (sin datos sensibles). */
private fun mensaje(e: AppError): String = when (e) {
    AppError.Almacenamiento -> "No se pudo escribir los datos de prueba."
    is AppError.Validacion -> "Datos de prueba inválidos."
    else -> "No se pudo generar los datos de prueba. Inténtalo de nuevo."
}
