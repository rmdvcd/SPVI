package cu.spvi.app.ayuda

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import cu.spvi.designsystem.component.CardTone
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviSpacing

object AyudaTags {
    fun tema(i: Int) = "ayuda.tema_$i"
}

/** Ajustes → Ayuda: manual breve. Sin estado de negocio (no necesita ViewModel); solo qué tema está abierto. */
@Composable
fun AyudaScreen(onBack: () -> Unit) {
    var abierto by rememberSaveable { mutableStateOf<Int?>(null) }
    val temas = AyudaContenido.temasPara(cu.spvi.app.common.LocalPermisosApp.current.modulos) // 0.21.0 (C12)
    Scaffold(topBar = { SpviTopBar(title = "Ayuda", onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(SpviSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
        ) {
            item { SpviCard(tone = CardTone.Tonal) { Text(AyudaContenido.INTRO, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
            itemsIndexed(temas) { i, tema ->
                val expandido = abierto == i
                SpviCard(
                    onClick = { abierto = if (expandido) null else i },
                    modifier = Modifier.testTag(AyudaTags.tema(i)).semantics { stateDescription = if (expandido) "Abierto" else "Cerrado" },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                        Icon(SpviIcons.Ayuda, contentDescription = null)
                        Text(tema.titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                        Icon(if (expandido) SpviIcons.Quitar else SpviIcons.Agregar, contentDescription = null)
                    }
                    if (expandido) {
                        tema.pasos.forEachIndexed { n, paso ->
                            Text("${n + 1}. $paso", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
