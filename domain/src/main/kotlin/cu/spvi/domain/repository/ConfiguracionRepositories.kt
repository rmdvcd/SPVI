package cu.spvi.domain.repository

import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.AutorizacionMigracion
import cu.spvi.domain.model.Licencia
import cu.spvi.domain.model.NivelesMinimos
import cu.spvi.domain.model.Perfil
import cu.spvi.domain.model.PreajustePrecios
import cu.spvi.domain.model.ConfiguracionInicial
import cu.spvi.domain.model.PasoConfiguracion
import cu.spvi.domain.model.Preferencias
import cu.spvi.licencia.ActivationResult
import cu.spvi.licencia.SolicitudInput
import java.io.InputStream
import java.time.Instant
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LicenciaRepository {
    /** null mientras se evalúa por primera vez (splash). */
    val snapshot: StateFlow<Licencia?>
    suspend fun refrescar(): Licencia
    suspend fun construirSolicitud(input: SolicitudInput): AppResult<cu.spvi.licencia.SolicitudGenerada>
    suspend fun activar(mensaje: String): ActivationResult
    /** 0.25.0: aplica la lista pública de revocadas; true = la licencia de este teléfono quedó revocada. */
    suspend fun aplicarRevocaciones(texto: String): Boolean = false

    /** Migrar: comprueba la licencia emitida al teléfono nuevo (sin guardarla ni descifrarla). */
    suspend fun verificarAutorizacionMigracion(mensaje: String): AutorizacionMigracion
    /**
     * Migrar: cede la licencia de este dispositivo (irreversible). NO re-evalúa [snapshot]: quien llama termina
     * de borrar los datos y después llama a [refrescar] (si no, el bloqueo cerraría la pantalla a mitad).
     */
    suspend fun cederLicencia(): AppResult<Unit>
}

interface PerfilRepository {
    val perfil: Flow<Perfil>
    /** Reemplaza el perfil completo (datos + listas) en una transacción. Asigna ids a tarjetas/teléfonos nuevos. */
    suspend fun guardar(perfil: Perfil)
}

interface PreciosRepository {
    fun observarPreajustes(): Flow<List<PreajustePrecios>>
    suspend fun preajustesActivos(): List<PreajustePrecios>
    suspend fun guardarPreajuste(p: PreajustePrecios): AppResult<Long>
    suspend fun eliminarPreajuste(id: Long): AppResult<Unit>
}

interface PreferenciasRepository {
    val preferencias: Flow<Preferencias>
    val onboardingCompletado: Flow<Boolean>
    suspend fun completarOnboarding()
    suspend fun guardarNiveles(n: NivelesMinimos)
    /** 0.21.0 (C12): se normaliza con [cu.spvi.domain.model.Modulo.normalizar]. */
    suspend fun guardarModulos(m: Set<cu.spvi.domain.model.Modulo>)
    suspend fun guardarEmpleadosPrevistos(n: Int)
    /** Restauración desde un respaldo. */
    suspend fun reemplazar(p: Preferencias)
}

/** 0.27.0: ajustes de este teléfono (DataStore cifrado; fuera del respaldo). */
interface AjustesDispositivoRepository {
    val ajustes: kotlinx.coroutines.flow.Flow<cu.spvi.domain.model.AjustesDispositivo>
    suspend fun guardarAccesoConClave(activo: Boolean)
    suspend fun guardarEligioSecundaria(valor: Boolean)
}

/** Progreso del asistente de primera ejecución (DataStore cifrado, propio de este dispositivo). */
interface ConfiguracionInicialRepository {
    val estado: Flow<ConfiguracionInicial>
    suspend fun confirmar(paso: PasoConfiguracion)
    suspend fun marcarCamaraSolicitada()
}

/** Qué trajo o llevó un respaldo. Siempre es completo: base de datos + configuración (P17: un solo tipo). */
data class ResumenRespaldo(val productos: Int, val insumos: Int, val ventas: Int, val movimientos: Int)

/** Migrar: borrado de TODOS los datos del negocio de este dispositivo (BD, preferencias, fotos, temporales). */
/** 0.25.0: estado propio de la app (recordatorio de respaldo, versiones, licencia recuperable). No va en el respaldo. */
interface EstadoAppRepository {
    val estado: Flow<cu.spvi.domain.model.EstadoApp>
    suspend fun actual(): cu.spvi.domain.model.EstadoApp
    suspend fun editar(cambio: (cu.spvi.domain.model.EstadoApp) -> cu.spvi.domain.model.EstadoApp)
    suspend fun borrar()
}

/**
 * 0.25.0: GitHub Releases (repositorio público configurado en el build). Sin datos del negocio ni identificadores.
 * [repoConfigurado] false = marcador vacío: no se consulta nada.
 */
interface ActualizacionesRepository {
    val repoConfigurado: Boolean
    /** Última Release publicada (null si no hay ninguna). */
    suspend fun ultimaVersion(): AppResult<cu.spvi.domain.model.InfoActualizacion?>
    /** Texto de `revocadas.json` (Release `revocaciones`). */
    suspend fun listaRevocaciones(): AppResult<String>
    /**
     * Descarga (reanudable) el APK a la caché privada y comprueba su SHA-256. [progreso] (bytes, total).
     * Error Validacion("sha256") si no coincide (el archivo se borra).
     */
    suspend fun descargar(info: cu.spvi.domain.model.InfoActualizacion, progreso: (Long, Long) -> Unit): AppResult<java.io.File>
}

interface MantenimientoRepository {
    suspend fun borrarTodo(): AppResult<Unit>
}

/** Etapas visibles de exportar/importar un respaldo (la derivación de la clave tarda 1–3 s en un teléfono modesto). */
enum class EtapaRespaldo { PREPARANDO, CIFRANDO, ESCRIBIENDO, LEYENDO, VERIFICANDO, DESCIFRANDO, IMPORTANDO }

/**
 * Lo que se sabe de un archivo `.spvi` SIN la contraseña: versión del formato y fecha de creación (van en la
 * cabecera autenticada; no son datos personales). [creadoEn] null si la cabecera no la trae.
 */
/** [conContrasena] = false (0.27.0, T10): el archivo se abre sin pedir contraseña. */
data class InfoRespaldo(val version: Int, val creadoEn: Instant?, val bytes: Long, val conContrasena: Boolean = true)

/**
 * Respaldo portable y completo: JSON versionado, comprimido y cifrado con contraseña del usuario
 * (PBKDF2-HMAC-SHA256 + AES-256-GCM). La BD SQLCipher está atada al Keystore y no es portable por sí misma.
 */
interface RespaldoRepository {
    /** [contrasena] vacía = sin contraseña (0.27.0, T10: opcional, apagada por defecto). */
    suspend fun exportar(
        contrasena: CharArray, destino: OutputStream, progreso: (EtapaRespaldo) -> Unit = {},
    ): AppResult<ResumenRespaldo>

    /** Comprueba el archivo sin contraseña: ¿es de SPVI?, ¿está completo e intacto? No modifica nada. */
    suspend fun inspeccionar(origen: InputStream): AppResult<InfoRespaldo>

    /** Reemplaza TODOS los datos (si el archivo no tiene contraseña, [contrasena] se ignora) (base de datos y preferencias) en una sola transacción de Room (todo o nada). */
    suspend fun importar(origen: InputStream, contrasena: CharArray, progreso: (EtapaRespaldo) -> Unit = {}): AppResult<ResumenRespaldo>
}
