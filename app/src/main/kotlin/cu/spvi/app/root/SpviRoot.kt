package cu.spvi.app.root

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.spvi.app.bloqueo.BloqueoGate
import cu.spvi.app.navigation.MainScaffold
import cu.spvi.app.onboarding.ModoWizard
import cu.spvi.app.onboarding.OnboardingScreen
import cu.spvi.designsystem.component.SpviLoading
import cu.spvi.designsystem.token.SpviMotion

/**
 * Bloqueo y Onboarding son "puertas", no destinos del NavHost: así ningún back/deep link puede
 * saltárselas. Main contiene el NavHost con los módulos navegables.
 */
@Composable
fun SpviRoot(viewModel: RootViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refrescar()
        onPauseOrDispose { }
    }
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { SpviMotion.screenEnter togetherWith SpviMotion.screenExit },
        label = "root",
    ) { s ->
        when (s) {
            RootState.Loading -> SpviLoading()
            // 0.25.0 (§3.4): teléfono antiguo de una recuperación: datos borrados, pedir desinstalar.
            is RootState.Bloqueo -> if (s.snapshot.estado == cu.spvi.licencia.LicenseState.Revoked) cu.spvi.app.licencia.LicenciaTransferida() else BloqueoGate()
            // El asistente marca el onboarding como visto; RootViewModel pasa a Main solo.
            RootState.Onboarding -> OnboardingScreen(modo = ModoWizard.PRIMERA_VEZ, onTerminar = {})
            is RootState.Main -> MainScaffold(s.snapshot)
            is RootState.BloqueoSecundaria -> cu.spvi.app.vinculacion.BloqueoSecundaria()
        }
    }
    // 0.26.0 (P74): permiso de fotos para que la prueba no se reinicie al reinstalar (una vez, recién instalada).
    if (state is RootState.Onboarding || (state is RootState.Main && !(state as RootState.Main).snapshot.estado.let { it is cu.spvi.licencia.LicenseState.Active || it is cu.spvi.licencia.LicenseState.Perpetual })) {
        cu.spvi.app.licencia.prueba.PermisoRegistroPrueba()
    }
}
