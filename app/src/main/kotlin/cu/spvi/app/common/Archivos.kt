package cu.spvi.app.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Archivos que la app genera para compartir o guardar. Interfaz para poder probar los ViewModels en JVM.
 * Los temporales viven en `cacheDir/compartir` (privado; el sistema puede borrarlo) y se exponen a otras apps
 * SOLO mediante FileProvider con permiso de lectura temporal: no hace falta ningún permiso de almacenamiento.
 */
interface ArchivosApp {
    /** Archivo nuevo en la carpeta de compartir (sobrescribe si existe). [nombre] se sanea. */
    fun temporal(nombre: String): File
    /** Destino elegido por el usuario con "Guardar en el dispositivo" (SAF, content://). */
    fun abrirDestino(uri: String): OutputStream?
    /**
     * Archivo elegido con "Abrir" (SAF, content://): Descargas, Drive, OneDrive, WhatsApp Documents… o la copia local
     * de un archivo recibido ([copiarRecibido]). null si no se puede leer.
     */
    fun abrirOrigen(uri: String): InputStream?

    /**
     * Prompt 14: copia YA el archivo que otra app compartió con SPVI (el permiso de lectura de esa URI es temporal)
     * a la carpeta privada de compartir. Devuelve la URI local y el nombre visible, o null si no se pudo leer o
     * supera [MAX_RECIBIDO]. Bloqueante: llamar fuera del hilo principal.
     */
    fun copiarRecibido(uri: String): Recibido? = null

    data class Recibido(val uri: String, val nombre: String?)

    companion object {
        /** Mismo tope que el respaldo (BackupCipher.MAX_PLANO). */
        const val MAX_RECIBIDO = 128L * 1024 * 1024
    }
}

@Singleton
class ArchivosAppImpl @Inject constructor(@ApplicationContext private val context: Context) : ArchivosApp {
    private val dir: File get() = File(context.cacheDir, CARPETA).apply { mkdirs() }

    override fun temporal(nombre: String): File {
        limpiarViejos()
        return File(dir, NombresArchivo.sanear(nombre))
    }

    override fun abrirDestino(uri: String): OutputStream? =
        runCatching { context.contentResolver.openOutputStream(Uri.parse(uri), "wt") }.getOrNull()

    override fun abrirOrigen(uri: String): InputStream? = runCatching {
        val u = Uri.parse(uri)
        if (u.scheme == "file") {
            // Solo copias locales de archivos recibidos: nunca una ruta arbitraria.
            val f = File(u.path ?: return null).canonicalFile
            if (f.parentFile != dir.canonicalFile) return null
            f.inputStream()
        } else {
            context.contentResolver.openInputStream(u)
        }
    }.getOrNull()

    override fun copiarRecibido(uri: String): ArchivosApp.Recibido? = runCatching {
        val u = Uri.parse(uri)
        if (u.scheme != "content") return null
        val nombre = nombreVisible(u)
        val destino = temporal("recibido_${System.currentTimeMillis()}.spvi")
        val ok = context.contentResolver.openInputStream(u)?.use { input ->
            destino.outputStream().use { out -> copiarAcotado(input, out) }
        } ?: false
        if (!ok) { destino.delete(); return null }
        ArchivosApp.Recibido(Uri.fromFile(destino).toString(), nombre)
    }.getOrNull()

    private fun copiarAcotado(input: InputStream, out: OutputStream): Boolean {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return true
            total += n
            if (total > ArchivosApp.MAX_RECIBIDO) return false
            out.write(buf, 0, n)
        }
    }

    /** Nombre que muestra la app de origen ("SPVI_respaldo_2026-09-30.spvi"); solo para el diálogo. */
    private fun nombreVisible(u: Uri): String? = runCatching {
        context.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.take(120)

    /** Lo compartido hace más de un día ya no hace falta. */
    private fun limpiarViejos() {
        val limite = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dir.listFiles().orEmpty().filter { it.lastModified() < limite }.forEach { it.delete() }
    }

    companion object { const val CARPETA = "compartir" }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ArchivosModule {
    @Binds abstract fun archivos(impl: ArchivosAppImpl): ArchivosApp
    @Binds abstract fun tarjetas(impl: cu.spvi.app.inventario.TarjetasAndroid): cu.spvi.app.inventario.RenderizadorTarjetas
    @Binds abstract fun tabla(impl: ImagenTablaAndroid): RenderizadorTabla
}

object NombresArchivo {
    private val PROHIBIDOS = Regex("[^A-Za-z0-9._-]+")

    /** "Ficha: Café Serrano.pdf" → "Ficha_Cafe_Serrano.pdf". Sin rutas ni caracteres raros. */
    fun sanear(nombre: String): String {
        val plano = java.text.Normalizer.normalize(nombre, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val limpio = plano.replace(PROHIBIDOS, "_").trim('_', '.').take(80)
        return limpio.ifEmpty { "spvi" }
    }
}

/** Intents de Compartir (selector del sistema: WhatsApp, Telegram, Bluetooth, Drive…). */
object Compartir {
    private fun autoridad(context: Context) = "${context.packageName}.archivos"

    fun archivos(context: Context, archivos: List<File>, mime: String, asunto: String): Boolean {
        if (archivos.isEmpty()) return false
        val uris = archivos.map { FileProvider.getUriForFile(context, autoridad(context), it) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }.apply {
            type = mime
            putExtra(Intent.EXTRA_SUBJECT, asunto)
            clipData = ClipData.newUri(context.contentResolver, asunto, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return lanzar(context, intent, asunto)
    }

    // 0.26.0: sin «compartir como texto» (solo archivos). La licencia usa sus propios enlaces (wa.me / smsto).
    private fun lanzar(context: Context, intent: Intent, titulo: String): Boolean = try {
        context.startActivity(Intent.createChooser(intent, titulo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
