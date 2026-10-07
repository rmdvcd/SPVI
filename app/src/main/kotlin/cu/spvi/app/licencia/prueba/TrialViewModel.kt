package cu.spvi.app.licencia.prueba

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cu.spvi.designsystem.component.SpviDialog
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.domain.model.TrialState
import cu.spvi.domain.model.pedirPermisoRegistro
import cu.spvi.domain.model.trialState
import cu.spvi.domain.repository.LicenciaRepository
import cu.spvi.domain.repository.PruebaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object TextosPrueba {
    const val TITULO = "Guardar tu periodo de prueba"
    /** 0.27.0 (T8): solo lo necesario, sin explicar el mecanismo. */
    const val TEXTO = "Permite el acceso a fotos para que SPVI pueda conservar los datos de tu periodo de prueba."
    const val CONTINUAR = "Continuar"
    const val AHORA_NO = "Ahora no"
}

object TrialTags {
    const val DIALOGO = "prueba_permiso"
}

/**
 * 0.26.0 (P74): estado de la prueba para la UI ([TrialState]) y el permiso de fotos, que se pide UNA vez por
 * instalación, justo después de instalar. La pantalla bloqueante de [TrialState.Expired] y [TrialState.Tampered] es la
 * puerta de licencia que ya existe (`RootState.Bloqueo` → `BloqueoGate`): no se duplica.
 */
@HiltViewModel
class TrialViewModel @Inject constructor(
    private val licencias: LicenciaRepository,
    private val prueba: PruebaRepository,
) : ViewModel() {

    val state: StateFlow<TrialState> = combine(licencias.snapshot, prueba.info) { l, i -> l?.let { trialState(it.estado, i) } }
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.Eagerly, TrialState.Active(null))

    private val pedido = MutableStateFlow<Boolean?>(null)

    init { viewModelScope.launch { pedido.value = prueba.permisoPedido() } }

    /** true = mostrar la explicación y, si acepta, el diálogo del sistema. */
    val pedirPermiso: StateFlow<Boolean> = combine(licencias.snapshot, prueba.info, pedido) { l, i, p ->
        p != null && pedirPermisoRegistro(l?.estado, i, prueba.lecturaPermitida(), p)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val permiso: String get() = prueba.permisoLectura

    /** Tras el diálogo del sistema (conceda o no): no se vuelve a pedir y se relee el registro con el permiso nuevo. */
    fun alResponder() {
        viewModelScope.launch {
            prueba.marcarPermisoPedido(); pedido.value = true
            licencias.refrescar()
        }
    }

    fun descartar() {
        viewModelScope.launch { prueba.marcarPermisoPedido(); pedido.value = true }
    }
}

/** Explicación previa + diálogo del sistema. Se monta en SpviRoot (encima del recorrido inicial o de Inicio). */
@Composable
fun PermisoRegistroPrueba(vm: TrialViewModel = hiltViewModel()) {
    val pedir by vm.pedirPermiso.collectAsStateWithLifecycle()
    var lanzado by rememberSaveable { mutableStateOf(false) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.alResponder() }
    if (pedir && !lanzado) {
        SpviDialog(
            title = TextosPrueba.TITULO,
            text = TextosPrueba.TEXTO,
            onDismiss = { lanzado = true; vm.descartar() },
            onConfirm = { lanzado = true; lanzador.launch(vm.permiso) },
            confirmDescription = TextosPrueba.CONTINUAR,
            dismissDescription = TextosPrueba.AHORA_NO,
            confirmIcon = SpviIcons.Respaldo,
            modifier = Modifier.testTag(TrialTags.DIALOGO),
        )
    }
    LaunchedEffect(pedir) { if (!pedir) lanzado = false }
}
