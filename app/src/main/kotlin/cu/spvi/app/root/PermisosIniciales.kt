package cu.spvi.app.root

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cu.spvi.domain.repository.ConfiguracionInicialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 0.31.0 (P9): todos los permisos que SPVI necesita, pedidos UNA vez al abrir la app por primera vez y SIN textos
 * de por medio: habla el sistema con su propio diálogo y el usuario decide. Nada de explicaciones previas ni
 * pantallas propias: si los niega, cada función se los vuelve a pedir donde hacen falta (cámara, fotos…).
 *
 * La lista sale de los permisos PELIGROSOS del manifiesto (los demás los da el sistema al instalar):
 *  - CAMERA: vincular por QR, fotos de productos y leer el QR de migración.
 *  - POST_NOTIFICATIONS: el aviso de turno abierto.
 *  - Fotos (READ_MEDIA_IMAGES desde Android 13; READ_EXTERNAL_STORAGE antes): el registro de prueba.
 */
object PermisosIniciales {
    fun lista(): List<String> = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

@HiltViewModel
class PermisosInicialesViewModel @Inject constructor(
    private val configuracion: ConfiguracionInicialRepository,
) : ViewModel() {
    /** null = todavía no se sabe (se está leyendo). */
    val pedir: StateFlow<Boolean?> = configuracion.estado.map { !it.permisosInicialesPedidos }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun yaPedidos() = viewModelScope.launch { configuracion.marcarPermisosInicialesPedidos() }
}

/**
 * 0.31.0 (P9): dispara el diálogo del sistema UNA vez (la primera apertura), sin decir nada antes. Se monta en
 * [cu.spvi.app.root.SpviRoot] por encima de lo que se esté viendo.
 */
@Composable
fun PermisosIniciales(viewModel: PermisosInicialesViewModel = hiltViewModel()) {
    val pedir by viewModel.pedir.collectAsStateWithLifecycle()
    var lanzado by rememberSaveable { mutableStateOf(false) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Da igual lo que conteste: no se vuelve a pedir en masa ni se insiste.
        viewModel.yaPedidos()
    }
    LaunchedEffect(pedir) {
        if (pedir == true && !lanzado) {
            lanzado = true
            lanzador.launch(PermisosIniciales.lista().toTypedArray())
        }
    }
}
