package cu.spvi.app.actualizacion

import android.content.Context
import androidx.lifecycle.ViewModel
import cu.spvi.core.time.Clock
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.Actualizaciones
import cu.spvi.domain.model.ApkEnPrincipal
import cu.spvi.domain.model.InfoActualizacion
import cu.spvi.domain.model.InfoApp
import cu.spvi.domain.repository.ActualizacionesRepository
import cu.spvi.domain.repository.EstadoAppRepository
import cu.spvi.domain.repository.SecundariaRepository
import cu.spvi.domain.usecase.ComprobarActualizaciones
import cu.spvi.domain.usecase.EstadoActualizacionObligatoria
import cu.spvi.domain.model.ActualizacionObligatoria
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado visible de las actualizaciones (tarjeta de Inicio y Ajustes). */
data class EstadoActualizacion(
    /** Versión más nueva publicada en GitHub. */
    val disponible: InfoActualizacion? = null,
    /** Secundaria: la principal tiene un APK más nuevo para repartir por la red local. */
    val desdePrincipal: ApkEnPrincipal? = null,
    /** 0..1 mientras descarga; null = sin descarga. */
    val progreso: Float? = null,
    val buscando: Boolean = false,
    /** Hace falta activar «Permitir de esta fuente» para SPVI. */
    val necesitaPermiso: Boolean = false,
    /** APK comprobado y listo (se puede volver a abrir el instalador si el usuario lo cerró). */
    val listo: Boolean = false,
    val repoConfigurado: Boolean = false,
    /** 0.26.0 (P73 §6): aviso con fecha límite o bloqueo (plazo de 30 días vencido). */
    val obligatoria: ActualizacionObligatoria.Estado = ActualizacionObligatoria.Estado.Ninguna,
    val esSecundaria: Boolean = false,
    /** Última consulta a GitHub que terminó correctamente (las fallidas no la modifican). */
    val ultimaComprobacion: java.time.Instant? = null,
    val diasSinComprobar: Long? = null,
    val avisoSinComprobar: Boolean = false,
) {
    /** Pantalla de bloqueo en toda la app (MainScaffold la omite durante una venta). */
    val bloqueada: Boolean get() = obligatoria is ActualizacionObligatoria.Estado.Bloqueo
    /** Fecha límite (aviso o bloqueo); null = la versión no es instalable desde la app (solo aviso). */
    val limite: java.time.Instant? get() = when (val o = obligatoria) {
        is ActualizacionObligatoria.Estado.Aviso -> o.limite
        is ActualizacionObligatoria.Estado.Bloqueo -> o.limite
        ActualizacionObligatoria.Estado.Ninguna -> null
    }
    /** La tarjeta de Inicio se ve si hay versión y el usuario no la aplazó hoy. */
    val tarjetaVisible: Boolean get() = hayAviso && (obligatoria as? ActualizacionObligatoria.Estado.Aviso)?.oculto != true

    val hayAviso: Boolean get() = disponible != null || desdePrincipal != null
    val version: String? get() = desdePrincipal?.version ?: disponible?.version
    val bytes: Long get() = desdePrincipal?.bytes ?: disponible?.bytes ?: 0
}

object TextosActualizacion {
    fun disponible(version: String, bytes: Long) =
        "Versión $version disponible${if (bytes > 0) " (${"%.1f".format(java.util.Locale.US, bytes / 1_048_576.0)} MB)" else ""}."
    fun desdePrincipal(version: String) = "Versión $version disponible desde la app principal (red local, sin gastar datos)."
    const val ACTUALIZAR = "Actualizar"
    /** 0.26.0: «Más tarde» solo oculta el aviso hasta mañana; la fecha límite no cambia. */
    const val DESCARTAR = "Más tarde"
    fun obligatoriaDesde(limite: java.time.Instant, zona: java.time.ZoneId = java.time.ZoneId.systemDefault()) =
        "Obligatoria desde el ${java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(limite.atZone(zona))}."
    const val BLOQUEO_TITULO = "Actualiza SPVI para seguir"
    fun bloqueoDetalle(version: String?, limite: java.time.Instant, zona: java.time.ZoneId = java.time.ZoneId.systemDefault()) =
        "La versión ${version ?: "nueva"} es obligatoria desde el ${java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(limite.atZone(zona))}. " +
            "Hasta actualizar, SPVI no se puede usar. Tus datos no se tocan."
    const val BLOQUEO_SECUNDARIA = "Pon los dos teléfonos en la misma wifi y toca «Actualizar desde la principal»."
    const val ACTUALIZAR_DESDE_PRINCIPAL = "Actualizar desde la principal"
    const val SIN_CONEXION_PRINCIPAL = "Aún no se ve la app principal. Conéctate a su misma wifi y sincroniza."
    const val EXPORTAR_RESPALDO = "Exportar respaldo"
    const val CERRAR_TURNO = "Cerrar turno"
    const val CANCELAR = "Cancelar descarga"
    const val DESCARGANDO = "Descargando la actualización…"
    const val PERMISO = "Para instalar, activa «Permitir de esta fuente» para SPVI y vuelve a tocar Actualizar."
    const val SIN_NOVEDADES = "Tienes la versión más reciente."
    const val SIN_REPO = "Las actualizaciones automáticas aún no están configuradas en esta versión."
    const val ERROR_RED = "No se pudo consultar ahora. Se reintentará al abrir la app."
    const val ERROR_DESCARGA = "La descarga no terminó o el archivo no coincide. Vuelve a intentarlo."
    const val INSTALACION_CANCELADA = "La instalación no se completó. Puedes volver a tocar Actualizar."
    const val BUSCAR = "Actualizaciones"
    const val BUSCAR_DETALLE = "Una vez por semana, al abrir la app, mira en GitHub si hay una versión nueva. No envía ningún dato. " +
        "Las versiones nuevas son obligatorias: puedes aplazarlas hasta 30 días."
    const val BUSCAR_AHORA = "Buscar ahora"
    fun ultimaComprobacion(ultima: java.time.Instant, zona: java.time.ZoneId = java.time.ZoneId.systemDefault()) =
        "Última comprobación correcta: ${java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(ultima.atZone(zona))}."
    const val SIN_COMPROBACION_CORRECTA = "Todavía no se ha completado una comprobación correcta."
    fun avisoSinComprobar(ultima: java.time.Instant?, dias: Long?): String = when {
        ultima == null -> "Aún no se ha confirmado si hay actualizaciones. Conéctate y toca «Buscar ahora»."
        dias == null -> "La fecha del teléfono es anterior a la última comprobación. Corrígela y vuelve a buscar."
        else -> "No se ha podido confirmar si hay actualizaciones desde hace $dias días. Conéctate y toca «Buscar ahora»."
    }
}

/**
 * 0.25.0 (§6): consulta semanal al abrir la app, descarga (GitHub o, en una secundaria, la principal por la red local),
 * comprobación e instalación. Singleton para que Inicio y Ajustes compartan la misma descarga; sin trabajo en segundo
 * plano (si la app se cierra, la descarga se reanuda al volver).
 */
@Singleton
class GestorActualizacion @Inject constructor(
    @ApplicationContext private val context: Context,
    private val comprobar: ComprobarActualizaciones,
    private val github: ActualizacionesRepository,
    private val estadoApp: EstadoAppRepository,
    private val secundaria: SecundariaRepository,
    private val instalador: InstaladorApk,
    private val info: InfoApp,
    private val obligatoria: EstadoActualizacionObligatoria,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val local = MutableStateFlow(EstadoActualizacion(repoConfigurado = github.repoConfigurado))
    private val mensajes = Channel<String>(Channel.BUFFERED)
    val eventos: Flow<String> = mensajes.receiveAsFlow()
    private var comprobado = false
    private var descarga: Job? = null
    private var apkListo: File? = null

    /** Re-evalúa el plazo cada hora con la app abierta (el día 30 llega sin reiniciar). */
    private val reloj = flow { while (true) { emit(Unit); delay(3_600_000) } }

    val estado: StateFlow<EstadoActualizacion> = combine(
        local,
        estadoApp.estado.catch { emit(cu.spvi.domain.model.EstadoApp()) },
        secundaria.estado,
        reloj,
    ) { l, e, s, _ ->
        val ahora = clock.now()
        val hayNueva = obligatoria.hayNueva(e, info.versionName, s.vinculada, github.repoConfigurado, s.apk.takeIf { s.vinculada }, info.versionCode)
        // 0.26.0 (§6): primera detección → empieza el plazo; ya actualizada → se borra. Una más nueva no lo reinicia.
        if (ActualizacionObligatoria.necesitaRegistro(e, info.versionName, hayNueva)) {
            scope.launch { seguro { obligatoria.registrar(info.versionName, hayNueva) } }
        }
        l.copy(
            disponible = Actualizaciones.aviso(e, info.versionName),
            desdePrincipal = s.apk?.takeIf { s.vinculada && it.versionCode > info.versionCode },
            obligatoria = obligatoria.estado(e, info.versionName, hayNueva),
            esSecundaria = s.vinculada,
            ultimaComprobacion = e.ultimaComprobacion,
            diasSinComprobar = Actualizaciones.diasDesdeComprobacion(ahora, e),
            avisoSinComprobar = Actualizaciones.avisoSinComprobacion(ahora, e, github.repoConfigurado),
        )
    }.stateIn(scope, SharingStarted.Eagerly, local.value)

    /** Al abrir la app (una vez por proceso). Borra APK descargados que ya no hacen falta. */
    fun alAbrir() {
        if (comprobado) return
        comprobado = true
        scope.launch {
            limpiarDescargasViejas()
            seguro { comprobar(info.versionName) }
        }
    }

    /** «Buscar ahora» (Ajustes). */
    fun buscarAhora() {
        if (!github.repoConfigurado) { mensajes.trySend(TextosActualizacion.SIN_REPO); return }
        if (local.value.buscando) return
        scope.launch {
            local.update { it.copy(buscando = true) }
            val r = seguro { comprobar(info.versionName, forzar = true) }
            local.update { it.copy(buscando = false) }
            mensajes.trySend(
                when {
                    r == null || r.error -> TextosActualizacion.ERROR_RED
                    r.aviso != null -> TextosActualizacion.disponible(r.aviso!!.version, r.aviso!!.bytes)
                    else -> TextosActualizacion.SIN_NOVEDADES
                },
            )
        }
    }

    /** «Más tarde»: oculta la tarjeta hasta mañana. La fecha límite no se mueve (§6). */
    fun descartar() {
        scope.launch { seguro { obligatoria.aplazar() } }
    }

    fun cancelar() {
        descarga?.cancel()
        descarga = null
        local.update { it.copy(progreso = null) }
    }

    /**
     * Descarga (o reutiliza) y abre el instalador. Solo se lanza desde Inicio o Ajustes, nunca durante una venta (§6.3).
     */
    fun actualizar() {
        if (descarga?.isActive == true) return
        if (!instalador.puedeInstalar()) {
            local.update { it.copy(necesitaPermiso = true) }
            mensajes.trySend(TextosActualizacion.PERMISO)
            runCatching { context.startActivity(instalador.intentPermiso()) }
            return
        }
        local.update { it.copy(necesitaPermiso = false) }
        apkListo?.takeIf { it.exists() }?.let { instalar(it); return }
        val e = estado.value
        descarga = scope.launch {
            local.update { it.copy(progreso = 0f) }
            val progreso: (Long, Long) -> Unit = { hecho, total -> if (total > 0) local.update { it.copy(progreso = (hecho.toFloat() / total).coerceIn(0f, 1f)) } }
            val r: AppResult<File>? = when {
                // Una sola descarga por internet: la secundaria la pide primero a la principal.
                e.desdePrincipal != null -> seguro { secundaria.descargarApk(progreso) }
                e.disponible != null -> seguro { github.descargar(e.disponible, progreso) }
                else -> { if (e.esSecundaria) mensajes.trySend(TextosActualizacion.SIN_CONEXION_PRINCIPAL); null }
            }
            local.update { it.copy(progreso = null) }
            when (r) {
                is AppResult.Ok -> {
                    val rechazo = instalador.comprobar(r.value)
                    if (rechazo != null) { r.value.delete(); mensajes.trySend(rechazo.texto) } else { apkListo = r.value; local.update { it.copy(listo = true) }; instalar(r.value) }
                }
                is AppResult.Err -> mensajes.trySend(if (r.error is AppError.Red) TextosActualizacion.ERROR_RED else TextosActualizacion.ERROR_DESCARGA)
                null -> Unit
            }
        }
    }

    private fun instalar(apk: File) {
        instalador.instalar(apk) { ok -> if (!ok) mensajes.trySend(TextosActualizacion.INSTALACION_CANCELADA) }
    }

    /** Tras actualizar, los APK descargados de versiones iguales o anteriores sobran (§6.3 paso 4). */
    private fun limpiarDescargasViejas() {
        val dir = File(context.cacheDir, "actualizacion")
        dir.listFiles()?.forEach { f ->
            val v = f.name.removePrefix("SPVI-").removeSuffix(".apk")
            // recibida.apk (de la principal) se conserva: su descarga se reanuda.
            if (f.name != "recibida.apk" && !Actualizaciones.esMasNueva(v, info.versionName)) runCatching { f.delete() }
        }
    }

    private suspend fun <T> seguro(bloque: suspend () -> T): T? = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

/** Puente para Compose: Inicio y Ajustes leen el mismo [GestorActualizacion]. */
@HiltViewModel
class ActualizacionViewModel @Inject constructor(private val gestor: GestorActualizacion) : ViewModel() {
    val estado: StateFlow<EstadoActualizacion> = gestor.estado
    val eventos: Flow<String> = gestor.eventos
    fun alAbrir() = gestor.alAbrir()
    fun actualizar() = gestor.actualizar()
    fun descartar() = gestor.descartar()
    fun cancelar() = gestor.cancelar()
    fun buscarAhora() = gestor.buscarAhora()
}
