package cu.spvi.app.respaldo

import cu.spvi.core.time.Dates
import cu.spvi.domain.repository.RespaldoRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.spvi.app.common.ArchivosApp
import cu.spvi.app.common.ExportadorArchivos
import cu.spvi.app.common.Entrada
import cu.spvi.app.common.EntradaCompartida
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.domain.repository.EtapaRespaldo
import cu.spvi.domain.usecase.ExportarRespaldo
import cu.spvi.domain.usecase.ImportarRespaldo
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RespaldoUiState(
    val export: ExportForm = ExportForm(),
    val importacion: ImportForm? = null,
    /** Operación en curso (paso n de m). Mientras no es null, todo lo demás queda deshabilitado. */
    val progreso: Progreso? = null,
    /** Diálogo final de éxito o de fallo. */
    val resultado: ResultadoRespaldo? = null,
    /** Paso 3 de exportar («Respaldo listo»): texto con el archivo creado. null = asistente en los pasos 1–2. */
    val exportado: String? = null,
) {
    val ocupado: Boolean get() = progreso != null
}

sealed interface EventoRespaldo {
    /** Abrir "Guardar como" del sistema con este nombre sugerido (respaldo .spvi). */
    data class ElegirDestino(val nombre: String) : EventoRespaldo
    /** Abrir el selector de Compartir (WhatsApp, Telegram, Zapya, Bluetooth, Drive, OneDrive…) con este archivo. */
    data class Compartir(val archivo: File, val mime: String = TextosRespaldo.MIME, val asunto: String = "Respaldo de SPVI") : EventoRespaldo
    data class Mensaje(val texto: String) : EventoRespaldo
}

/**
 * Ajustes → Respaldo. Las contraseñas solo viven en el estado de la pantalla: se pasan como CharArray al
 * caso de uso (que las borra de memoria) y se vacían del formulario al terminar. Nunca se registran.
 *
 * Prompt 14: importar = (1) comprobar el archivo SIN contraseña (ajeno, cortado o dañado se explica antes de
 * pedir nada), (2) contraseña, (3) progreso por pasos, (4) diálogo de confirmación. Recibe también los `.spvi`
 * que otra app comparte con SPVI ([EntradaCompartida]).
 */
@HiltViewModel
class RespaldoViewModel @Inject constructor(
    private val exportar: ExportarRespaldo,
    private val importar: ImportarRespaldo,
    private val respaldoRepo: RespaldoRepository,
    private val archivos: ArchivosApp,
    private val entrada: EntradaCompartida,
    private val clock: Clock,
) : ViewModel() {

    private val zona: ZoneId = ZoneId.systemDefault()
    private val _state = MutableStateFlow(RespaldoUiState())
    val state: StateFlow<RespaldoUiState> = _state.asStateFlow()
    private val _eventos = Channel<EventoRespaldo>(Channel.BUFFERED)
    val eventos: Flow<EventoRespaldo> = _eventos.receiveAsFlow()

    init {
        // Un .spvi abierto/compartido desde otra app (MainScaffold trae aquí al usuario).
        viewModelScope.launch {
            entrada.entrada.collect { e ->
                if (e is Entrada.Archivo && entrada.consumir(e)) {
                    if (_state.value.ocupado) mensaje(TextosRespaldo.OCUPADO) else archivoElegido(e.uri, e.nombre)
                }
            }
        }
    }

    fun editarExport(f: (ExportForm) -> ExportForm) = _state.update { it.copy(export = f(it.export)) }
    fun cerrarResultado() = _state.update { it.copy(resultado = null) }

    /** «Hacer otro respaldo»: vuelve al paso 1 del asistente de exportar. */
    fun nuevoRespaldo() = _state.update { it.copy(exportado = null) }

    /** Enviar / guardar compartido con las demás pantallas (P17 S5). El destino lo pide la UI con su propio evento. */
    private val exportador = ExportadorArchivos<Unit>(archivos)

    private fun hoy() = Dates.localDate(clock.now(), zona)
    private fun nombre() = nombreRespaldo(hoy())

    // ------------------------------------------------------------ exportar

    /** Valida y pide a la UI el destino ("Guardar en el teléfono"). */
    fun guardarEnTelefono() {
        if (!validarExport()) return
        _eventos.trySend(EventoRespaldo.ElegirDestino(nombre()))
    }

    /** Destino elegido en el selector del sistema (null = cancelado: no pasa nada). */
    fun destinoElegido(uri: String?) {
        if (uri == null) return
        val nombre = nombre()
        val con = _state.value.export.conContrasena
        ejecutar(EtapasRespaldo.progreso(EtapaRespaldo.PREPARANDO)) {
            when (val r = exportador.aDestino(uri) { exportar(_state.value.export.contrasenaFinal.toCharArray(), it, ::avanzar) }) {
                is AppResult.Ok -> { limpiarExport(); _state.update { it.copy(resultado = exitoRespaldo(r.value, importado = false, nombre, con), exportado = PasosRespaldo.textoHecho(nombre, enviado = false, con)) } }
                is AppResult.Err -> mensaje(mensajeRespaldo(r.error))
            }
        }
    }

    /** Crea el archivo cifrado en la carpeta privada de compartir y lo entrega al selector del sistema. */
    fun compartir() {
        if (!validarExport()) return
        val con = _state.value.export.conContrasena
        ejecutar(EtapasRespaldo.progreso(EtapaRespaldo.PREPARANDO)) {
            when (val r = exportador.aTemporal(nombre()) { exportar(_state.value.export.contrasenaFinal.toCharArray(), it, ::avanzar) }) {
                is AppResult.Ok -> { limpiarExport(); _state.update { it.copy(exportado = PasosRespaldo.textoHecho(nombre(), enviado = true, con)) }; _eventos.send(EventoRespaldo.Compartir(r.value.archivo)) }
                is AppResult.Err -> mensaje(mensajeRespaldo(r.error))
            }
        }
    }

    // ------------------------------------------------------------ importar

    /**
     * Archivo elegido con "Abrir" o recibido de otra app (null = cancelado). Primero se comprueba sin contraseña:
     * si no es de SPVI, está cortado o dañado, se explica y no se pide nada.
     */
    fun archivoElegido(uri: String?, nombre: String? = null) {
        if (uri == null) return
        ejecutar(EtapasRespaldo.unico(EtapasRespaldo.COMPROBANDO)) {
            val input = archivos.abrirOrigen(uri)
                ?: return@ejecutar _state.update { it.copy(resultado = fallo(AppError.Almacenamiento)) }
            when (val r = input.use { respaldoRepo.inspeccionar(it) }) {
                is AppResult.Ok -> _state.update { it.copy(importacion = ImportForm(uri, r.value, nombre = nombre)) }
                is AppResult.Err -> _state.update { it.copy(resultado = fallo(r.error)) }
            }
        }
    }

    fun editarImport(contrasena: String) =
        _state.update { s -> s.copy(importacion = s.importacion?.copy(contrasena = contrasena, error = null)) }

    fun cancelarImport() = _state.update { it.copy(importacion = null) }

    fun confirmarImport() {
        val imp = _state.value.importacion ?: return
        val pide = PasosRespaldo.pideContrasena(imp)
        if (pide && imp.contrasena.isEmpty()) {
            _state.update { it.copy(importacion = imp.copy(error = TextosRespaldo.CONTRASENA_INCORRECTA)) }
            return
        }
        ejecutar(EtapasRespaldo.progreso(EtapaRespaldo.LEYENDO)) {
            val input = archivos.abrirOrigen(imp.uri)
            if (input == null) {
                _state.update { it.copy(importacion = null, resultado = fallo(AppError.Almacenamiento)) }
                return@ejecutar
            }
            when (val r = input.use { importar(it, (if (pide) imp.contrasena else "").toCharArray(), ::avanzar) }) {
                is AppResult.Ok -> _state.update { it.copy(importacion = null, resultado = exitoRespaldo(r.value, importado = true, imp.nombre)) }
                // Contraseña mal escrita: se queda el diálogo abierto para reintentar.
                is AppResult.Err -> if (r.error == AppError.ContrasenaIncorrecta) {
                    _state.update { it.copy(importacion = imp.copy(contrasena = "", error = TextosRespaldo.CONTRASENA_INCORRECTA)) }
                } else {
                    _state.update { it.copy(importacion = null, resultado = fallo(r.error)) }
                }
            }
        }
    }

    // ------------------------------------------------------------ util

    /** Llamado desde el hilo de E/S del repositorio: StateFlow es seguro entre hilos. */
    private fun avanzar(e: EtapaRespaldo) = _state.update { it.copy(progreso = EtapasRespaldo.progreso(e)) }

    private fun validarExport(): Boolean {
        val e = _state.value.export
        if (_state.value.ocupado) return false
        if (!e.valido) { _state.update { it.copy(export = e.copy(mostrarErrores = true)) }; return false }
        return true
    }

    private fun limpiarExport() = _state.update { it.copy(export = ExportForm()) }

    private suspend fun mensaje(t: String) = _eventos.send(EventoRespaldo.Mensaje(t))

    private fun ejecutar(inicial: Progreso, bloque: suspend () -> Unit) {
        if (_state.value.ocupado) return
        _state.update { it.copy(progreso = inicial, resultado = null) }
        viewModelScope.launch {
            try { bloque() } finally { _state.update { it.copy(progreso = null) } }
        }
    }
}
