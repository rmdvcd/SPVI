package cu.spvi.app.catalogo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.preview.SpviCatalog

/** Solo en builds debug (enlace en Ajustes): muestra todos los componentes con el tema actual. */
@Composable
fun CatalogoScreen(onBack: () -> Unit) {
    Scaffold(topBar = { SpviTopBar(title = "Design system", onBack = onBack) }) { padding ->
        Box(Modifier.padding(padding)) { SpviCatalog() }
    }
}
