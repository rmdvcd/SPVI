package cu.spvi.app.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ancho del panel persistente, o null si la ventana es compacta (< 600 dp). */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun calculateListDetailPaneWidth(): Dp? {
    val activity = LocalContext.current.findActivity() ?: return null
    return when (calculateWindowSizeClass(activity).widthSizeClass) {
        WindowWidthSizeClass.Compact -> null
        WindowWidthSizeClass.Medium -> 280.dp
        WindowWidthSizeClass.Expanded -> 360.dp
        else -> 360.dp // Las clases de ancho personalizadas también aprovechan el panel persistente.
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
