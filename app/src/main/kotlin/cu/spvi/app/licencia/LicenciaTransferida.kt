package cu.spvi.app.licencia

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.padding
import cu.spvi.designsystem.component.SpviEmptyState
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.ilustracion.SpviIlustracion

object TextosTransferida {
    const val TITULO = "Licencia transferida"
    const val DETALLE = "La licencia de SPVI se recuperó en otro teléfono. Por seguridad, aquí se borraron los datos del " +
        "negocio y la app quedó bloqueada. Puedes desinstalarla."
    const val DESINSTALAR = "Desinstalar SPVI"
    const val TAG = "licencia_transferida"
}

/**
 * 0.25.0 (§3.4): pantalla final del teléfono ANTIGUO tras una recuperación automática de la licencia. Android no permite
 * desinstalar en silencio: el botón abre el diálogo del sistema y el usuario confirma.
 */
@Composable
fun LicenciaTransferida() {
    val context = LocalContext.current
    Scaffold(topBar = { SpviTopBar(title = "SPVI", marca = true) }) { padding ->
        SpviEmptyState(
            title = TextosTransferida.TITULO,
            detail = TextosTransferida.DETALLE,
            ilustracion = SpviIlustracion.NoExiste,
            modifier = Modifier.padding(padding).testTag(TextosTransferida.TAG),
            action = {
                SpviPrimaryButton(
                    TextosTransferida.DESINSTALAR, icon = SpviIcons.Eliminar,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            },
        )
    }
}
