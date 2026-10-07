package cu.spvi.app.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import cu.spvi.designsystem.component.IconActionStyle
import cu.spvi.designsystem.component.SpviIconAction
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviRadius
import cu.spvi.designsystem.token.SpviSize
import cu.spvi.designsystem.token.SpviSpacing
import java.io.File

/** Textos de la foto del artículo (0.27.0, T13). */
object TextosFoto {
    const val CAMARA = "Hacer una foto"
    const val GALERIA = "Elegir de la galería"
    const val QUITAR = "Quitar foto"
    const val SIN_FOTO = "Sin foto"
    const val PREPARANDO = "Preparando la foto…"
    const val SIN_PERMISO = "Permite la cámara en los ajustes del teléfono para hacer fotos."
}

object SelectorFotoTags {
    const val CAMARA = "foto.camara"
    const val GALERIA = "foto.galeria"
    const val QUITAR = "foto.quitar"
}

/**
 * 0.27.0 (T13): la foto es lo primero del formulario (Producto, Insumo y Servicio): miniatura centrada con un
 * marcador si no hay foto y, debajo, botones de solo icono: Cámara, Galería y, con foto, Quitar.
 *
 * - **Cámara:** `TakePicture` hacia un archivo temporal de `cache/fotos/` expuesto con el FileProvider de la app.
 *   Como el manifiesto declara `CAMERA`, el permiso se pide justo antes. Los temporales se borran al hacer otra
 *   foto y al salir del formulario; la foto definitiva la copia (reducida y en JPEG) [onFoto] → `FotoRepository`.
 * - **Galería:** el selector de fotos del sistema (`PickVisualMedia`), sin permisos. `READ_MEDIA_IMAGES` no se usa aquí.
 * [onFoto] recibe el `content://` elegido; null = cancelado (no hace nada).
 */
@Composable
fun SelectorFoto(
    uri: String?,
    descripcion: String,
    importando: Boolean,
    onFoto: (String) -> Unit,
    onQuitar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val alFoto by rememberUpdatedState(onFoto)
    var pendiente by rememberSaveable { mutableStateOf<String?>(null) }
    var sinPermiso by remember { mutableStateOf(false) }

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { u -> alFoto(u.toString()) } }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pendiente
        pendiente = null
        if (ok && u != null) alFoto(u)
    }
    fun abrirCamara() {
        val u = FotosTemporales.nuevaUri(context)
        pendiente = u.toString()
        camara.launch(u)
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        sinPermiso = !concedido
        if (concedido) abrirCamara()
    }
    DisposableEffect(Unit) { onDispose { if (pendiente == null) FotosTemporales.limpiar(context) } }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
        val forma = RoundedCornerShape(SpviRadius.lg)
        if (uri != null) {
            AsyncImage(
                model = uri, contentDescription = "Foto de $descripcion", contentScale = ContentScale.Crop,
                modifier = Modifier.size(SpviSize.fotoFormulario).clip(forma),
            )
        } else {
            Box(
                Modifier.size(SpviSize.fotoFormulario).clip(forma).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SpviIcons.Galeria, contentDescription = TextosFoto.SIN_FOTO, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            SpviIconAction(
                SpviIcons.Camara, TextosFoto.CAMARA, enabled = !importando, style = IconActionStyle.Tonal,
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) abrirCamara()
                    else permiso.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.testTag(SelectorFotoTags.CAMARA),
            )
            SpviIconAction(
                SpviIcons.Galeria, TextosFoto.GALERIA, enabled = !importando, style = IconActionStyle.Tonal,
                onClick = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.testTag(SelectorFotoTags.GALERIA),
            )
            if (uri != null) {
                SpviIconAction(SpviIcons.Eliminar, TextosFoto.QUITAR, onClick = onQuitar, enabled = !importando, modifier = Modifier.testTag(SelectorFotoTags.QUITAR))
            }
        }
        if (importando) SpviSecondaryText(TextosFoto.PREPARANDO)
        if (sinPermiso) SpviSecondaryText(TextosFoto.SIN_PERMISO, modifier = Modifier.fillMaxWidth())
    }
}

/** Temporales de la cámara en `cache/fotos/` (ruta del FileProvider «fotos»). Nunca se comparten con otras apps salvo la cámara. */
object FotosTemporales {
    private const val CARPETA = "fotos"

    fun nuevaUri(context: Context): android.net.Uri {
        limpiar(context)
        val dir = File(context.cacheDir, CARPETA).apply { mkdirs() }
        val f = File(dir, "captura_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.archivos", f)
    }

    fun limpiar(context: Context) {
        runCatching { File(context.cacheDir, CARPETA).listFiles()?.forEach { it.delete() } }
    }
}
