package cu.spvi.app.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.spvi.domain.repository.PrincipalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 0.21.0 (C6): cuántos empleados piden cerrar su turno (aviso de Inicio en la principal; se resuelven en Apps
 * vinculadas). Aparte de [InicioViewModel] para no mezclar la vinculación con los gráficos. En una secundaria
 * la tabla de empleados está vacía: siempre 0.
 */
@HiltViewModel
class SolicitudesCierreViewModel @Inject constructor(principal: PrincipalRepository) : ViewModel() {
    val pendientes: StateFlow<Int> = principal.observarEmpleados()
        .map { l -> l.count { it.solicitaCierre } }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 0.26.0 (P73 §4): nombres de los empleados que piden el fondo para abrir turno («Luis pide abrir turno»). */
    val aperturas: StateFlow<List<String>> = principal.observarEmpleados()
        .map { l -> l.filter { it.pideApertura }.map { it.nombre } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 0.26.0: secundarias con una app anterior a la 0.26.0 (escriben su propio fondo): «Actualiza la app de X». */
    val desactualizadas: StateFlow<List<String>> = principal.observarEmpleados()
        .map { l -> l.filter { it.appDesactualizada }.map { it.nombre } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
