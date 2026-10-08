package cu.spvi.designsystem.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviElevation
import cu.spvi.designsystem.token.SpviMotion
import cu.spvi.designsystem.token.SpviRadius
import cu.spvi.designsystem.token.SpviSize
import cu.spvi.designsystem.token.SpviSpacing

/**
 * Snackbar sobre inverseSurface (contraste AA en ambos temas).
 * P18 (A15): todos los mensajes de la app pasan por aquí, con el mismo aspecto, y cada uno que aparece va acompañado
 * de una vibración muy breve (respeta el ajuste del sistema) para quien no está mirando la pantalla.
 */
@Composable
fun SpviSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val haptics = rememberSpviHaptics()
    val actual = state.currentSnackbarData
    LaunchedEffect(actual) { if (actual != null) haptics.seleccion() }
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(SpviRadius.md),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
        )
    }
}

/**
 * Diálogo con el título centrado arriba (0.27.0: sin logo). Confirmar/Cancelar son iconos (SPVI.txt) con descripción accesible.
 * [destructive] pinta la confirmación en color de error (eliminar).
 *
 * P24: ventana emergente **centrada**; título centrado arriba; los botones Cancelar y Confirmar, centrados y con
 * separación media (16dp). La confirmación va rellena (Von Restorff) y Cancelar sin fondo. El contenido largo se
 * desplaza dentro del diálogo y los botones siguen siempre a la vista.
 */
@Composable
fun SpviDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)?,
    modifier: Modifier = Modifier,
    text: String? = null,
    confirmDescription: String = "Confirmar",
    dismissDescription: String = "Cancelar",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmLoading: Boolean = false,
    confirmTag: String? = null,
    confirmIcon: ImageVector? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    SpviVentana(
        onDismiss = onDismiss,
        title = title,
        modifier = modifier,
        footer = { cerrar ->
            SpviIconAction(SpviIcons.Cancelar, dismissDescription, cerrar)
            if (onConfirm != null) {
                if (confirmLoading) {
                    Box(Modifier.size(SpviSize.touchTarget), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(SpviSize.icon).semantics { contentDescription = confirmDescription })
                    }
                } else {
                    SpviIconAction(
                        icon = confirmIcon ?: if (destructive) SpviIcons.Eliminar else SpviIcons.Confirmar,
                        contentDescription = confirmDescription,
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        style = IconActionStyle.Filled,
                        containerColor = if (destructive) cs.error else null,
                        modifier = if (confirmTag != null) Modifier.testTag(confirmTag) else Modifier,
                    )
                }
            }
        },
        divisor = false,
    ) {
        when {
            content != null -> content()
            text != null -> Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Ventana emergente centrada para formularios rápidos, filtros, exportar, etc. (antes, hoja inferior).
 * Cabecera con el título centrado (0.27.0: sin logo), contenido desplazable y [footer] FIJO con las acciones, centradas.
 *
 * P18 (A03): con [sinGuardar] = true, cerrar SIN querer (tocar fuera o el botón atrás) no descarta lo escrito:
 * pregunta «¿Salir sin guardar?». El botón Cancelar del pie es una decisión explícita y cierra directamente.
 *
 * P24: todas las ventanas emergentes de la app son centradas. Ancho y alto intrínsecos al contenido, con límites
 * mínimos/máximos del viewport; con el teclado abierto se encoge para que los campos sigan visibles.
 */
@Composable
fun SpviBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    sinGuardar: Boolean = false,
    enPanel: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var preguntar by rememberSaveable { mutableStateOf(false) }
    if (preguntar) {
        SpviDialog(
            title = SpviSheetTextos.SALIR_TITULO,
            text = SpviSheetTextos.SALIR_TEXTO,
            onDismiss = { preguntar = false },
            onConfirm = { preguntar = false; onDismiss() },
            confirmDescription = SpviSheetTextos.SALIR,
            dismissDescription = SpviSheetTextos.SEGUIR,
            destructive = true,
        )
    }
    if (enPanel) {
        SpviDetailPane(
            title = title,
            onClose = { if (sinGuardar) preguntar = true else onDismiss() },
            footer = footer,
            content = content,
        )
        return
    }
    SpviVentana(
        onDismiss = { if (sinGuardar) preguntar = true else onDismiss() },
        title = title,
        footer = if (footer == null) null else { _ -> footer(this) },
        animarCierre = !sinGuardar,
        content = content,
    )
}

/**
 * Panel persistente de detalle para ventanas medianas/expandidas. Conserva el mismo contenido y acciones que la hoja
 * modal, pero deja la lista visible al lado y reserva el desplazamiento solo para el contenido.
 */
@Composable
private fun SpviDetailPane(
    title: String?,
    onClose: () -> Unit,
    footer: (@Composable RowScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(SpviRadius.xl),
        color = cs.surfaceContainerHigh,
        tonalElevation = SpviElevation.mid,
        shadowElevation = SpviElevation.high,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = SpviSpacing.md, end = SpviSpacing.xs, top = SpviSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                title?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                }
                SpviIconAction(SpviIcons.Cancelar, SpviSheetTextos.CERRAR_DETALLE, onClose)
            }
            HorizontalDivider(color = cs.outlineVariant)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = SpviSpacing.md, vertical = SpviSpacing.md),
                verticalArrangement = Arrangement.spacedBy(SpviSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CompositionLocalProvider(LocalSpviCentrado provides true) { content() }
            }
            footer?.let {
                HorizontalDivider(color = cs.outlineVariant)
                SpviButtonRow(Modifier.padding(horizontal = SpviSpacing.md, vertical = SpviSpacing.xs)) { it(this) }
            }
        }
    }
}

/**
 * Base común de [SpviDialog] y [SpviBottomSheet]: superficie centrada, radio 24dp y título centrado arriba.
 *
 * 0.27.0 (T1): sin logo en la cabecera; el título va arriba del todo, centrado.
 * 0.27.0 (N4): la ventana sube desde abajo hasta el centro (con fundido y el velo oscuro que aparece detrás) y, al
 * cerrarse con Cancelar, tocando fuera o con el botón atrás, hace el recorrido inverso antes de desaparecer. Con
 * [animarCierre] = false (p. ej. una hoja con cambios sin guardar, que antes pregunta) la petición de cierre llega
 * directa. Si quien la usa ignora el cierre, la ventana vuelve a subir.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun SpviVentana(
    onDismiss: () -> Unit,
    title: String?,
    modifier: Modifier = Modifier,
    footer: (@Composable RowScope.(cerrar: () -> Unit) -> Unit)?,
    divisor: Boolean = true,
    animarCierre: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visible = remember { MutableTransitionState(false) }
    val cerrarActual by rememberUpdatedState(onDismiss)
    var pendiente by remember { mutableStateOf(false) }
    val cerrar: () -> Unit = {
        if (!animarCierre) cerrarActual()
        else if (!pendiente) { pendiente = true; visible.targetState = false }
    }
    LaunchedEffect(Unit) { visible.targetState = true }
    BloquearGestos() // 0.27.0 (T5)
    LaunchedEffect(visible.currentState, visible.isIdle) {
        if (pendiente && visible.isIdle && !visible.currentState) {
            pendiente = false
            cerrarActual()
            visible.targetState = true
        }
    }
    Dialog(onDismissRequest = cerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // El velo lo dibuja la propia ventana (con fundido); se quita el oscurecido fijo del sistema.
        val vista = LocalView.current
        SideEffect { (vista.parent as? DialogWindowProvider)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) }
        val alto = LocalConfiguration.current.screenHeightDp.dp
        val cs = MaterialTheme.colorScheme
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(SpviMotion.tween(SpviMotion.VELO)),
            exit = fadeOut(SpviMotion.tween(SpviMotion.VELO)),
        ) {
            // La ventana del diálogo ocupa toda la pantalla: tocar fuera de la tarjeta cierra (la tarjeta consume sus toques).
            Box(
                Modifier.fillMaxSize().background(cs.scrim.copy(alpha = ALFA_VELO))
                    .pointerInput(Unit) { detectTapGestures { cerrar() } }.imePadding().padding(SpviSpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = modifier.animateEnterExit(enter = SpviMotion.ventanaEntra, exit = SpviMotion.ventanaSale)
                        .width(IntrinsicSize.Max).widthIn(min = SpviSize.dialogMinWidth, max = SpviSize.contentMaxWidth)
                        .height(IntrinsicSize.Min).heightIn(max = alto)
                        .pointerInput(Unit) { detectTapGestures { } },
                    shape = RoundedCornerShape(SpviRadius.xl),
                    color = cs.surfaceContainerHigh,
                    tonalElevation = SpviElevation.mid,
                    shadowElevation = SpviElevation.high,
                ) {
                    Column(Modifier.padding(top = SpviSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                        title?.let {
                            Text(
                                it, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = SpviSpacing.lg).padding(bottom = SpviSpacing.xs).semantics { heading() },
                            )
                        }
                        Column(
                            Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = SpviSpacing.lg, vertical = SpviSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(SpviSpacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally, // P28: lo que va solo en su fila, centrado
                        ) {
                            CompositionLocalProvider(LocalSpviCentrado provides true) { content() }
                        }
                        footer?.let { pie ->
                            if (divisor) HorizontalDivider(color = cs.outlineVariant)
                            SpviButtonRow(Modifier.padding(horizontal = SpviSpacing.lg, vertical = SpviSpacing.md)) { pie(this, cerrar) }
                        } ?: Spacer(Modifier.height(SpviSpacing.lg))
                    }
                }
            }
        }
    }
}

/** Opacidad del velo que oscurece la pantalla detrás de una ventana emergente (la del oscurecido de Material). */
private const val ALFA_VELO = 0.32f

/** Textos de la confirmación al cerrar una hoja con cambios. */
object SpviSheetTextos {
    const val CERRAR_DETALLE = "Cerrar detalle"
    const val SALIR_TITULO = "¿Salir sin guardar?"
    const val SALIR_TEXTO = "Se perderán los cambios que escribiste."
    const val SALIR = "Salir sin guardar"
    const val SEGUIR = "Seguir editando"
}

/** Botón flotante de acción principal con los colores y elevación del tema. */
@Composable
fun SpviFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = cs.primary,
        contentColor = cs.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(SpviElevation.mid, SpviElevation.high),
    ) { Icon(icon, contentDescription = contentDescription) }
}
