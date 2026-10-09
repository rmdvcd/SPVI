package cu.spvi.app.actualizacion

import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.InfoApp
import cu.spvi.domain.repository.EstadoAppRepository
import cu.spvi.domain.usecase.ExportarRespaldo
import cu.spvi.domain.usecase.ImportarRespaldo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.CancellationException
import javax.inject.Singleton

/**
 * 0.30.1: respaldo automático alrededor de cada actualización, para no perder los datos del usuario.
 *
 * 1. [crear] se llama justo antes de abrir el instalador: guarda un respaldo completo (el mismo formato `.spvi`, sin
 *    contraseña) en la carpeta PRIVADA de la app y anota qué versión estaba instalada. Si no se puede crear, no se
 *    actualiza.
 * 2. [restaurarSiCorresponde] se llama al abrir la app: si la versión instalada ya no es la anotada, la actualización
 *    terminó y se restaura el respaldo; después se borra. Si la versión es la misma (se canceló la instalación) el
 *    respaldo sobra y se borra.
 *
 * Es un respaldo interno: no cuenta como «respaldo exportado» para el recordatorio mensual de Inicio, ni deja una
 * licencia «recuperable» en el estado de la app. Nunca sale del teléfono ni va a los registros.
 */
class RespaldoPrevio(
    private val dir: File,
    private val exportar: ExportarRespaldo,
    private val importar: ImportarRespaldo,
    private val estadoApp: EstadoAppRepository,
) {
    enum class Restauracion { NADA_QUE_RESTAURAR, RESTAURADO, FALLO }

    private val archivo get() = File(dir, "previo.spvi")
    private val marca get() = File(dir, "previo.version")

    /** Hay un respaldo previo guardado (y pendiente de restaurar o de descartar). */
    val existe: Boolean get() = archivo.exists()

    /** Crea el respaldo antes de instalar. [version] identifica la versión instalada ahora. true = listo. */
    suspend fun crear(version: String): Boolean {
        val tmp = File(dir, "previo.tmp")
        val ultimo = intentar { estadoApp.actual().ultimoRespaldo }
        val resultado = intentar {
            dir.mkdirs()
            tmp.outputStream().use { exportar(CharArray(0), it) }
        }
        // Exportar reinicia el recordatorio mensual; este respaldo es interno, así que se deja como estaba.
        intentar { estadoApp.editar { it.copy(ultimoRespaldo = ultimo) } }
        if (resultado !is AppResult.Ok) { tmp.delete(); return false }
        val listo = runCatching {
            archivo.delete()
            tmp.renameTo(archivo) && run { marca.writeText(version); true }
        }.getOrDefault(false)
        if (!listo) descartar()
        return listo
    }

    /** Restaura el respaldo si la app acaba de actualizarse. [version] = la versión instalada ahora. */
    suspend fun restaurarSiCorresponde(version: String): Restauracion {
        if (!archivo.exists()) return Restauracion.NADA_QUE_RESTAURAR
        val anterior = runCatching { marca.readText().trim() }.getOrNull()
        if (anterior.isNullOrEmpty() || anterior == version) {
            descartar() // la instalación no se hizo: el respaldo ya no hace falta
            return Restauracion.NADA_QUE_RESTAURAR
        }
        val recuperable = intentar { estadoApp.actual().licenciaRecuperable }
        val resultado = intentar { archivo.inputStream().use { importar(it, CharArray(0)) } }
        // Importar guarda la licencia del respaldo como «recuperable»; aquí la licencia ya está instalada.
        intentar { estadoApp.editar { it.copy(licenciaRecuperable = recuperable) } }
        return if (resultado is AppResult.Ok) {
            descartar()
            Restauracion.RESTAURADO
        } else {
            Restauracion.FALLO // el archivo se conserva y se reintenta al abrir otra vez; la base de datos no se tocó
        }
    }

    /** Borra el respaldo previo (instalación cancelada o ya restaurado). */
    fun descartar() {
        runCatching { archivo.delete() }
        runCatching { marca.delete() }
        runCatching { File(dir, "previo.tmp").delete() }
    }

    /** Como `runCatching`, pero sin tragarse la cancelación de la corrutina. */
    private suspend inline fun <T> intentar(bloque: () -> T): T? = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Clave de versión que se anota: el nombre y el código (dos compilaciones con el mismo nombre no se confunden). */
        fun clave(info: InfoApp): String = "${info.versionName}/${info.versionCode}"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RespaldoPrevioModule {
    @Provides @Singleton
    fun respaldoPrevio(
        @ApplicationContext context: android.content.Context,
        exportar: ExportarRespaldo,
        importar: ImportarRespaldo,
        estadoApp: EstadoAppRepository,
    ): RespaldoPrevio = RespaldoPrevio(File(context.filesDir, "respaldo_actualizacion"), exportar, importar, estadoApp)
}
