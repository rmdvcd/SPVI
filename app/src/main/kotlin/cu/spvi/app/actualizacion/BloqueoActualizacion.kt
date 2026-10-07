package cu.spvi.app.actualizacion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import cu.spvi.designsystem.component.SpviButtonRow
import cu.spvi.designsystem.component.SpviLinearProgress
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSecondaryButton
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviSpacing
import java.time.ZoneId

object BloqueoActualizacionTags {
    const val PANTALLA = "bloqueo_actualizacion"
    const val ACTUALIZAR = "bloqueo_actualizacion_actualizar"
    const val RESPALDO = "bloqueo_actualizacion_respaldo"
    const val CERRAR_TURNO = "bloqueo_actualizacion_cerrar_turno"
}

/**
 * 0.26.0 (P73 §6): pantalla de bloqueo cuando vence el plazo de 30 días de una actualización pendiente. Tapa TODA la
 * app (MainScaffold la pone encima del NavHost, salvo durante una venta y en Respaldo). Salidas (suposiciones 3 y 4):
 * - «Actualizar» (GitHub, o «Actualizar desde la principal» en una secundaria);
 * - «Exportar respaldo» (solo la principal: el respaldo es suyo);
 * - «Cerrar turno» si hay uno abierto (en la secundaria se SOLICITA, como siempre).
 * Sin contenido de la app detrás: Atrás no hace nada (la app sigue en Recientes con FLAG_SECURE).
 */
@Composable
fun BloqueoActualizacion(
    estado: EstadoActualizacion,
    turnoAbierto: Boolean,
    onActualizar: () -> Unit,
    onCancelar: () -> Unit,
    onExportarRespaldo: () -> Unit,
    onCerrarTurno: () -> Unit,
    modifier: Modifier = Modifier,
    zona: ZoneId = ZoneId.systemDefault(),
) {
    val T = TextosActualizacion
    BackHandler(enabled = true) { }
    Surface(modifier.fillMaxSize().testTag(BloqueoActualizacionTags.PANTALLA), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SpviSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SpviSpacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(SpviIcons.Bloqueo, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(SpviSpacing.xs))
            Text(
                T.BLOQUEO_TITULO, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            estado.limite?.let {
                Text(T.bloqueoDetalle(estado.version, it, zona), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            if (estado.esSecundaria) SpviSecondaryText(T.BLOQUEO_SECUNDARIA, textAlign = TextAlign.Center)
            if (estado.necesitaPermiso) SpviSecondaryText(T.PERMISO, textAlign = TextAlign.Center)
            if (estado.progreso != null) {
                SpviSecondaryText(T.DESCARGANDO + " ${(estado.progreso * 100).toInt()} %", maxLines = 1)
                SpviLinearProgress(modifier = Modifier.fillMaxWidth(), progress = estado.progreso)
                SpviButtonRow { SpviSecondaryButton(T.CANCELAR, onClick = onCancelar, icon = SpviIcons.Cancelar) }
            } else {
                SpviButtonRow {
                    SpviPrimaryButton(
                        if (estado.esSecundaria) T.ACTUALIZAR_DESDE_PRINCIPAL else T.ACTUALIZAR, onClick = onActualizar, icon = SpviIcons.Importar,
                        modifier = Modifier.testTag(BloqueoActualizacionTags.ACTUALIZAR),
                    )
                }
            }
            if (!estado.esSecundaria) SpviButtonRow {
                SpviSecondaryButton(T.EXPORTAR_RESPALDO, onClick = onExportarRespaldo, icon = SpviIcons.Respaldo, modifier = Modifier.testTag(BloqueoActualizacionTags.RESPALDO))
            }
            if (turnoAbierto) SpviButtonRow {
                SpviSecondaryButton(T.CERRAR_TURNO, onClick = onCerrarTurno, icon = SpviIcons.Turno, modifier = Modifier.testTag(BloqueoActualizacionTags.CERRAR_TURNO))
            }
        }
    }
}

/**
 * Conexión con los ViewModels: el turno se cierra (o se solicita, en la secundaria) con el mismo diálogo de conteo que
 * Inicio ([cu.spvi.app.caja.DialogoContado]) y la misma lógica ([cu.spvi.app.inicio.InicioViewModel]).
 */
@Composable
fun BloqueoActualizacionRuta(
    estado: EstadoActualizacion,
    actualizacion: ActualizacionViewModel,
    onExportarRespaldo: () -> Unit,
) {
    val inicio: cu.spvi.app.inicio.InicioViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val s by inicio.state.collectAsStateWithLifecycle()
    BloqueoActualizacion(
        estado = estado,
        turnoAbierto = s.turnoAbierto,
        onActualizar = actualizacion::actualizar,
        onCancelar = actualizacion::cancelar,
        onExportarRespaldo = onExportarRespaldo,
        onCerrarTurno = { inicio.cambiarTurno(false) },
    )
    when (s.dialogo) {
        cu.spvi.app.inicio.DialogoInicio.CerrarTurno -> cu.spvi.app.caja.DialogoContado(
            titulo = cu.spvi.app.inicio.TextosInicio.CERRAR_TURNO_TITULO,
            texto = cu.spvi.app.inicio.TextosInicio.CERRAR_TURNO_TEXTO,
            confirmar = TextosActualizacion.CERRAR_TURNO,
            arqueo = s.arqueo,
            trabajando = s.cambiandoTurno,
            onConfirmar = inicio::confirmarCierreTurno,
            onDismiss = inicio::cerrarDialogo,
        )
        cu.spvi.app.inicio.DialogoInicio.SolicitarCierre -> cu.spvi.app.caja.DialogoContado(
            titulo = cu.spvi.app.inicio.TextosInicio.SOLICITAR_CIERRE_TITULO,
            texto = cu.spvi.app.inicio.TextosInicio.SOLICITAR_CIERRE_TEXTO,
            confirmar = "Solicitar cierre",
            arqueo = s.arqueo,
            trabajando = false,
            onConfirmar = inicio::confirmarSolicitudCierre,
            onDismiss = inicio::cerrarDialogo,
        )
        else -> Unit
    }
}
