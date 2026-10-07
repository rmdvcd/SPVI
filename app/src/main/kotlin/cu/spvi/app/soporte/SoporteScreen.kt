package cu.spvi.app.soporte

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import cu.spvi.app.BuildConfig
import cu.spvi.app.common.Contacto
import cu.spvi.core.contact.DeveloperContact
import cu.spvi.designsystem.component.SpviButtonRow
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviLogo
import cu.spvi.designsystem.component.SpviListItem
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSecondaryButton
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviSnackbarHost
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.component.spviContentWidth
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviSize
import cu.spvi.designsystem.token.SpviSpacing
import kotlinx.coroutines.launch

/** Filas de la ficha (una sola fuente: DeveloperContact, la misma que usa Licencia). Pura, testeada. */
object SoporteInfo {
    val filas: List<Pair<String, String>> = listOf(
        "Desarrollador" to DeveloperContact.NOMBRE,
        "CI" to DeveloperContact.CI,
        "Teléfono" to DeveloperContact.TELEFONO_LOCAL,
        "Especialidad" to DeveloperContact.ESPECIALIDAD,
    )

    /** 0.27.0 (T12): icono de cada fila de la ficha. */
    fun icono(etiqueta: String): ImageVector = when (etiqueta) {
        "Desarrollador" -> SpviIcons.Desarrollador
        "CI" -> SpviIcons.Identidad
        "Teléfono" -> SpviIcons.Telefono
        else -> SpviIcons.Especialidad
    }

    /** 0.27.0 (T12): el pie sigue siendo cierto con la contraseña del respaldo opcional. */
    const val PIE = "Nunca te pediremos tus contraseñas ni datos de tu tarjeta."
}

object SoporteTags {
    const val FICHA = "soporte.ficha"
}

/**
 * Ajustes → Soporte (también accesible desde el bloqueo). Sin datos del usuario: no necesita FLAG_SECURE.
 * [onBack] = null en el bloqueo si no hay a dónde volver.
 */
@Composable
fun SoporteScreen(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sinApp = { scope.launch { snackbar.showSnackbar("No hay una app disponible para enviar el mensaje") } }
    SoporteContent(
        onBack = onBack,
        version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        onWhatsapp = { if (!Contacto.whatsapp(context)) sinApp() },
        onSms = { if (!Contacto.sms(context)) sinApp() },
        snackbar = snackbar,
    )
}

@Composable
fun SoporteContent(
    onBack: (() -> Unit)?,
    version: String,
    onWhatsapp: () -> Unit,
    onSms: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = { SpviTopBar(title = "Soporte", onBack = onBack) },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().spviContentWidth().padding(padding).verticalScroll(rememberScrollState()).padding(SpviSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpviSpacing.md),
        ) {
            SpviLogo(size = SpviSize.logoLarge, contentDescription = null)
            Text(
                "¿Tienes dudas o problemas? Escribe al desarrollador.", style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() },
            )
            SpviCard(modifier = Modifier.fillMaxWidth().testTag(SoporteTags.FICHA)) {
                SelectionContainer {
                    // 0.27.0 (T12): filas con icono a la izquierda (patrón de SpviListItem); el valor se puede seleccionar.
                    Column {
                        SoporteInfo.filas.forEach { (etiqueta, valor) ->
                            SpviListItem(
                                title = valor, subtitle = etiqueta, indicatorColor = null, valueMaxLines = 3, subtitleMaxLines = 2,
                                leading = { Icon(SoporteInfo.icono(etiqueta), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            )
                        }
                    }
                }
            }
            SpviButtonRow {
                SpviSecondaryButton("Enviar SMS", onClick = onSms, icon = SpviIcons.Sms)
                SpviPrimaryButton("Escribir por WhatsApp", onClick = onWhatsapp, icon = SpviIcons.WhatsApp)
            }
            SpviSecondaryText(
                "${SoporteInfo.PIE}\nSPVI $version",
                textAlign = TextAlign.Center,
            )
        }
    }
}
