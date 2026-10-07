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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.max
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
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
 * P24: todas las ventanas emergentes de la app son centradas. Ancho del 92 % de la pantalla (máximo 560dp) y alto
 * máximo del 90 %; con el teclado abierto se encoge para que los campos sigan visibles.
 */
@Composable
fun SpviBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    sinGuardar: Boolean = false,
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
    SpviVentana(
        onDismiss = { if (sinGuardar) preguntar = true else onDismiss() },
        title = title,
        footer = if (footer == null) null else { _ -> footer(this) },
        animarCierre = !sinGuardar,
        content = content,
    )
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
                        .fillMaxWidth(ANCHO_VENTANA).widthIn(max = SpviSize.contentMaxWidth).heightIn(max = alto * ALTO_VENTANA)
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

/** Fracción del ancho y del alto de la pantalla que puede ocupar una ventana emergente. */
private const val ANCHO_VENTANA = 0.92f
private const val ALTO_VENTANA = 0.9f

/** Textos de la confirmación al cerrar una hoja con cambios. */
object SpviSheetTextos {
    const val SALIR_TITULO = "¿Salir sin guardar?"
    const val SALIR_TEXTO = "Se perderán los cambios que escribiste."
    const val SALIR = "Salir sin guardar"
    const val SEGUIR = "Seguir editando"
}

/** FAB de una sola acción (p. ej. "Agregar insumo"): mismos colores y elevación que [SpviExpandableFab]. */
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

@Immutable
data class SpviFabAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * FAB expandible: el principal rota 45° (graphicsLayer) y las acciones entran con slide + fade.
 * Cada acción: icono con descripción + etiqueta corta visible (oculta a TalkBack para no duplicar).
 */
@Composable
fun SpviExpandableFab(
    actions: List<SpviFabAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = SpviIcons.Agregar,
    contentDescription: String = "Acciones rápidas",
) {
    val cs = MaterialTheme.colorScheme
    BloquearGestos(expanded) // 0.27.0 (T5): con el menú del «+» abierto no se desliza entre secciones
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, SpviMotion.muelle(), label = "fabRotation")
    // P30/P31: el FAB va centrado abajo. 0.21.0 (C13): las acciones se apilan en vertical y TODOS los botones
    // (mini y principal) comparten el mismo eje vertical; las etiquetas quedan a la izquierda (ver [EjeFab]).
    EjeFab(modifier, SpviSpacing.md) {
        actions.forEach { action ->
            AnimatedVisibility(visible = expanded, enter = SpviMotion.enterVertical, exit = SpviMotion.exitVertical) {
                FilaAccionFab(action) { onExpandedChange(false); action.onClick() }
            }
        }
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            containerColor = cs.primary,
            contentColor = cs.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(SpviElevation.mid, SpviElevation.high),
        ) {
            Icon(
                icon,
                contentDescription = if (expanded) "Cerrar acciones" else contentDescription,
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

/**
 * 0.21.0 (C13): columna cuyo eje es el CENTRO DEL BOTÓN, no el centro de cada fila. Cada fila de acción mide
 * simétrica respecto a su mini botón ([FilaAccionFab]); aquí se colocan todas, y el FAB principal, con ese centro en
 * la misma x, así que «Escanear», «Escribir» y Cancelar quedan exactamente alineados en vertical.
 */
@Composable
private fun EjeFab(modifier: Modifier, separacion: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { medibles, restricciones ->
        val libre = restricciones.copy(minWidth = 0, minHeight = 0)
        // Las acciones ocultas (AnimatedVisibility cerrado) miden 0 y no reservan hueco.
        val piezas = medibles.map { it.measure(libre) }.filter { it.height > 0 }
        val hueco = separacion.roundToPx()
        val ancho = piezas.maxOfOrNull { it.width } ?: 0
        val alto = piezas.sumOf { it.height } + hueco * (piezas.size - 1).coerceAtLeast(0)
        layout(ancho, alto) {
            var y = 0
            piezas.forEach { p ->
                p.placeRelative((ancho - p.width) / 2, y)
                y += p.height + hueco
            }
        }
    }
}

/**
 * P31: una acción del FAB expandible: etiqueta visible a la izquierda y mini botón a la derecha.
 *
 * Medida propia en vez de `Row(fillMaxWidth)` + `weight`: el hueco del FAB del Scaffold no garantiza
 * un ancho acotado, y con pesos la etiqueta podía quedar con ancho 0 (se veían solo los iconos).
 * Aquí el ancho es simétrico respecto al mini botón, así que la columna centrada lo deja justo
 * encima del botón principal, tenga la etiqueta el largo que tenga.
 */
@Composable
private fun FilaAccionFab(action: SpviFabAction, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Layout(
        content = {
            Surface(
                shape = RoundedCornerShape(SpviRadius.sm),
                color = cs.surfaceContainerHigh,
                contentColor = cs.onSurface,
                shadowElevation = SpviElevation.low,
                modifier = Modifier.clearAndSetSemantics { }, // TalkBack ya lee la descripción del botón
            ) {
                Text(
                    action.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = SpviSpacing.xs, vertical = SpviSpacing.xs),
                )
            }
            SmallFloatingActionButton(
                onClick = onClick,
                containerColor = cs.tertiaryContainer, // 0.21.1 (H2): como el botón tonal; el naranja es solo para avisos
                contentColor = cs.onTertiaryContainer,
            ) { Icon(action.icon, contentDescription = action.label) }
        },
    ) { medibles, restricciones ->
        val libre = restricciones.copy(minWidth = 0, minHeight = 0)
        val boton = medibles[1].measure(libre)
        val separacion = SpviSpacing.xs.roundToPx()
        val maxEtiqueta = if (restricciones.hasBoundedWidth) {
            ((restricciones.maxWidth - boton.width) / 2 - separacion).coerceAtLeast(0)
        } else Constraints.Infinity
        val etiqueta = medibles[0].measure(libre.copy(maxWidth = maxEtiqueta))
        val mitad = max(boton.width / 2 + separacion + etiqueta.width, boton.width / 2)
        val alto = max(boton.height, etiqueta.height)
        layout(mitad * 2, alto) {
            boton.placeRelative(mitad - boton.width / 2, (alto - boton.height) / 2)
            etiqueta.placeRelative(mitad - boton.width / 2 - separacion - etiqueta.width, (alto - etiqueta.height) / 2)
        }
    }
}

