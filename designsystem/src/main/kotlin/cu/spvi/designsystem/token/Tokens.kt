package cu.spvi.designsystem.token

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Rejilla de 8dp: todo margen y separación es múltiplo de 8. */
object SpviSpacing {
    val xs = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object SpviRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp   // Cards
    val xl = 24.dp   // Sheets y diálogos
}

/** Solo afecta a sombras en modo claro; en oscuro la elevación es tonal (superficies más claras). */
object SpviElevation {
    val none = 0.dp
    val low = 1.dp    // Cards
    val mid = 3.dp    // FAB en reposo, menús
    val high = 6.dp   // FAB presionado
    /** Barras inferiores de acción (total de la venta, botones de formulario): elevación tonal. */
    val tonalBar = 3.dp
}

object SpviOpacity {
    const val secondary = ColorTokens.SECONDARY_ALPHA
    const val placeholder = 0.6f
    const val disabled = 0.38f
}

object SpviSize {
    /** 0.21.0 (C11): miniatura de la foto en filas de listas (redondeada con [SpviRadius.sm]). */
    val miniatura = 40.dp
    val buttonMin = 40.dp
    val buttonMax = 48.dp
    val textField = 56.dp
    val touchTarget = 48.dp
    val icon = 24.dp
    val iconSmall = 18.dp
    val logoSmall = 32.dp
    val logoDialog = 40.dp
    val logoLarge = 72.dp
    val indicatorWidth = 4.dp
    val indicatorHeight = 32.dp
    val chartHeight = 160.dp
    val donut = 136.dp
    val donutStroke = 24.dp
    val legendDot = 12.dp

    // P18 (A14): medidas que antes estaban escritas a mano en las pantallas.
    /** Margen inferior de las listas para que el FAB no tape la última fila. */
    val fabClearance = 88.dp
    /** Lado del código QR de cobro (se ve bien a 30–40 cm). */
    val qr = 240.dp
    /** Foto del producto en formularios y en el escáner. */
    val fotoMiniatura = 88.dp
    /** Campo de cantidad (hasta 5 cifras, con letra grande). */
    val campoCantidad = 112.dp
    /** P18 (A22): ancho máximo de lectura de formularios y asistentes en tablet u horizontal. */
    val contentMaxWidth = 600.dp
    /** 0.27.0 (T3): círculo de las medallas del Top 3. */
    val medalla = 30.dp
    /** 0.27.0 (T13): foto del artículo arriba del formulario. */
    val fotoFormulario = 112.dp

    // P19: trazos internos de componentes (antes literales en Charts y Buttons).
    /** Línea base de los gráficos. */
    val strokeHairline = 1.dp
    /** Trazo del gráfico de área y del spinner de los botones. */
    val strokeRegular = 2.dp
    /** Ancho máximo de una barra del gráfico de barras. */
    val chartBarMax = 28.dp
}

/**
 * P18 (A01, A02): a partir de este factor de letra del sistema, los componentes cambian de disposición. Los botones
 * dejan de tener altura máxima y las filas ponen el valor debajo del título. 1.3 = «Grande» en la mayoría de los
 * teléfonos.
 */
object SpviFontScale {
    const val GRANDE = 1.3f
}

/**
 * Motion: fundidos de 150–250 ms (0.27.0, T4) con FastOutSlowInEasing; todo lo que cambia de tamaño, posición o giro,
 * con [muelle]. Todas las animaciones de la app salen de aquí. Ninguna fuerza la frecuencia de refresco:
 * `animate*AsState`, `Animatable` y las transiciones siguen el refresco real de la pantalla (60, 90 o 120 Hz), y
 * «Quitar animaciones» del sistema las anula. 0.28.0: los deslizamientos y giros son muelles físicos (sin rebote),
 * idénticos a cualquier tasa de refresco y interrumpibles sin saltos; los fundidos y colores siguen con `tween`.
 * 0.28.1: las entradas usan [muelleEntrada] (blando, con leve asentamiento) y recorridos largos (1/4); las pestañas
 * añaden un zoom sutil al fundido; las salidas siguen inmediatas con [muelle].
 */
object SpviMotion {
    const val FAST = 150
    const val SHORT = 200
    const val MEDIUM = 250
    /** 0.27.0 (T4): tope de 250 ms (antes 300). */
    const val LONG = 250
    val easing = FastOutSlowInEasing

    fun <T> tween(durationMillis: Int = MEDIUM): TweenSpec<T> = tween(durationMillis, easing = easing)

    /** Formularios, modales y elementos que aparecen: el movimiento es muelle, el fundido tween. */
    val enterVertical = slideInVertically(muelleEntrada()) { it / 4 } + fadeIn(tween(MEDIUM))
    val exitVertical = slideOutVertically(muelle()) { it / 4 } + fadeOut(tween(SHORT))

    /**
     * Cambio entre pestañas de la barra inferior (mismo nivel): fundido + zoom sutil (sin dirección, porque no hay
     * avance ni retroceso). 0.28.1: el fundido solo era imperceptible entre pantallas oscuras; el zoom lo delata.
     */
    val screenEnter = fadeIn(tween(MEDIUM)) + scaleIn(tween(MEDIUM), initialScale = ZOOM_ENTRADA)
    val screenExit = fadeOut(tween(SHORT)) + scaleOut(tween(SHORT), targetScale = ZOOM_ENTRADA)

    /**
     * P18 (A17): navegación con dirección. Avanzar entra desde la derecha (1/4 del ancho) y volver desde la
     * izquierda; siempre acompañado de fundido. 0.28.1: el recorrido era 1/10 y se completaba en <200 ms (imperceptible
     * en el teléfono); ahora 1/4 con muelle de entrada blando (~0,4 s con leve asentamiento). Con «Quitar
     * animaciones» del sistema, Compose usa duración 0.
     */
    val screenForwardEnter: EnterTransition = slideInHorizontally(muelleEntrada()) { it / 4 } + fadeIn(tween(MEDIUM))
    val screenForwardExit: ExitTransition = slideOutHorizontally(muelle()) { -it / 4 } + fadeOut(tween(SHORT))
    val screenBackEnter: EnterTransition = slideInHorizontally(muelleEntrada()) { -it / 4 } + fadeIn(tween(MEDIUM))
    val screenBackExit: ExitTransition = slideOutHorizontally(muelle()) { it / 4 } + fadeOut(tween(SHORT))

    /** P18 (A18): filas de lista que aparecen, desaparecen o se reordenan (`Modifier.animateItem`). */
    val listFade: FiniteAnimationSpec<Float> = tween(SHORT, easing = easing)
    val listPlacement: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /**
     * 0.27.0 (T4): lo que cambia de tamaño o de posición usa un muelle sin rebote. Sigue el refresco real de la
     * pantalla (60, 90 o 120 Hz) y se puede interrumpir sin saltos.
     */
    fun <T> muelle(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /**
     * 0.28.1: muelle de ENTRADA para recorridos visibles (ventanas, ventanas emergentes, barra inferior). Más blando
     * que [muelle] y con un leve asentamiento (amortiguación 0,85 < 1): el deslizamiento dura ~0,4 s y se nota el
     * «llegar». Las salidas siguen con [muelle] para que retroceder se sienta inmediato.
     */
    fun <T> muelleEntrada(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 350f)

    /** 0.28.1: escala inicial/final del zoom sutil de [screenEnter]/[screenExit]. */
    const val ZOOM_ENTRADA = 0.96f

    /**
     * 0.27.0 (N4): las ventanas emergentes suben desde abajo hasta el centro con un fundido y, al cerrarse, hacen el
     * recorrido inverso (bajan y se desvanecen). El recorrido es la altura de la propia ventana. 0.28.1: la entrada
     * usa [muelleEntrada] para que el «llegar» se aprecie; la salida sigue inmediata con [muelle].
     */
    val ventanaEntra: EnterTransition =
        slideInVertically(muelleEntrada()) { it } + fadeIn(tween(SHORT))
    val ventanaSale: ExitTransition = slideOutVertically(muelle()) { it } + fadeOut(tween(SHORT, easing = easing))

    /** Duración del fundido del velo oscuro que hay detrás de una ventana emergente. */
    const val VELO = SHORT
}
