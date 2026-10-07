package cu.spvi.app

import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.activity.SystemBarStyle
import android.os.Build
import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.clearAndSetSemantics
import cu.spvi.app.acceso.AccesoViewModel
import cu.spvi.app.acceso.Autenticador
import cu.spvi.app.acceso.GuardiaAcceso
import cu.spvi.app.acceso.PantallaBloqueoClave
import cu.spvi.app.acceso.TextosAcceso
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import cu.spvi.app.common.ArchivosApp
import cu.spvi.app.common.Entrada
import cu.spvi.app.common.EntradaCompartida
import cu.spvi.app.common.FormatoFechaDispositivo
import cu.spvi.app.root.RootState
import cu.spvi.app.root.RootViewModel
import cu.spvi.app.root.SpviRoot
import cu.spvi.designsystem.theme.SpviTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Única Activity. Edge-to-edge estándar (barras transparentes, iconos según tema): la app no oculta ni
 * modifica barra de estado/navegación ni notch; solo aplica insets.
 *
 * 0.27.0 (T11): FragmentActivity (lo pide BiometricPrompt; no hace falta AppCompat). Con el acceso con clave activado,
 * la pantalla de bloqueo se dibuja ENCIMA de la app sin destruir su estado.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val rootViewModel: RootViewModel by viewModels()
    private val accesoViewModel: AccesoViewModel by viewModels()
    @Inject lateinit var guardiaAcceso: GuardiaAcceso

    /** «Compartir con SPVI» (SMS de PAGOxMOVIL, respaldo .spvi). launchMode=singleTask: llega a la venta en curso. */
    @Inject lateinit var entradaCompartida: EntradaCompartida
    @Inject lateinit var archivos: ArchivosApp

    override fun onCreate(savedInstanceState: Bundle?) {
        // La splash del sistema (#478EA1 + icono) se mantiene hasta evaluar la licencia.
        installSplashScreen().setKeepOnScreenCondition { rootViewModel.state.value is RootState.Loading }
        // P29 (barras de navegación): con 3 botones Android pinta un velo translúcido sobre la barra; la app ya dibuja
        // su propio fondo detrás (barra inferior, barra de acciones, Scaffold), así que el velo solo ensuciaba el
        // color. Barras transparentes en los 3 modos (gestos, 2 y 3 botones); los iconos se adaptan al tema.
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        FormatoFechaDispositivo.aplicar(this) // P24: fecha y hora con el formato del dispositivo
        ProcessLifecycleOwner.get().lifecycle.addObserver(guardiaAcceso) // idempotente al recrearse
        setContent {
            SpviTheme {
                // P29: en horizontal, el contenido no queda bajo el notch/cámara (Scaffold solo reserva las barras).
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
                ) {
                    val bloqueada by accesoViewModel.bloqueada.collectAsStateWithLifecycle()
                    // Mientras se lee la preferencia (null) no se dibuja la app: así nunca asoma con el candado puesto.
                    Box(Modifier.fillMaxSize().then(if (bloqueada != false) Modifier.clearAndSetSemantics { } else Modifier)) {
                        if (bloqueada != null) SpviRoot(rootViewModel)
                    }
                    if (bloqueada == true) PantallaBloqueoClave(onDesbloquear = ::pedirClave)
                }
            }
        }
        // Tras recrearse (giro) el Intent sería el mismo: solo se procesa en el primer arranque.
        if (savedInstanceState == null) recibir(intent)
    }

    /** 0.27.0 (T11): diálogo del sistema; sin bloqueo de pantalla en el teléfono, la opción se apaga sola. */
    private fun pedirClave() {
        if (!Autenticador.disponible(this)) { accesoViewModel.sinBloqueoDelTelefono(); return }
        Autenticador.pedir(this, TextosAcceso.PROMPT_TITULO, alConfirmar = accesoViewModel::desbloqueada, alFallar = {})
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        recibir(intent)
    }

    private fun recibir(intent: Intent?) {
        when (val e = EntradaCompartida.desdeIntent(intent)) {
            null -> Unit
            // El permiso de lectura de la URI compartida es temporal: se copia ya a la carpeta privada.
            is Entrada.Archivo -> lifecycleScope.launch {
                val local = withContext(Dispatchers.IO) { archivos.copiarRecibido(e.uri) }
                entradaCompartida.publicar(local?.let { Entrada.Archivo(it.uri, it.nombre) } ?: e)
            }
            else -> entradaCompartida.publicar(e)
        }
    }

    override fun onResume() {
        super.onResume()
        FormatoFechaDispositivo.aplicar(this)
        // Re-evalúa al volver (p. ej. tras cambiar la fecha del sistema o expirar mientras estaba en segundo plano).
        rootViewModel.refrescar()
    }
}
