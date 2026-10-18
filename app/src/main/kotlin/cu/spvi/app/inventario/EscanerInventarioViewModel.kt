package cu.spvi.app.inventario

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.domain.model.Categorias
import cu.spvi.domain.usecase.GuardarInsumo
import cu.spvi.domain.usecase.GuardarProducto
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Resultado de una importación: filas que entraron y, si quedó algo pendiente, por qué ([error] null si entró todo). */
data class ResumenImportacion(val insertadas: Int, val error: ErrorEscaneo?)

/**
 * Escáner OCR de inventario (local y sin red: ML Kit latin viene empaquetado). Aquí solo vive lo que depende de
 * Android (la imagen y el reconocimiento); la tabla, el mapeo, la revisión y el guardado por lotes están en
 * `EscanerInventarioLogic`, que se prueba en JVM.
 */
@HiltViewModel
class EscanerInventarioViewModel @Inject constructor(
    private val guardarProducto: GuardarProducto,
    private val guardarInsumo: GuardarInsumo,
    private val clock: Clock,
) : ViewModel() {

    /** Tipo de registro del lote en curso. */
    var tipo by mutableStateOf(TipoEscaneo.PRODUCTO)
        private set

    /** Campos elegidos en el paso 1 (solo los que el usuario marcó). */
    var campos by mutableStateOf<List<CampoEscaneo>>(emptyList())
        private set

    /** Tabla detectada en la última imagen (null mientras no haya resultado). */
    var tabla by mutableStateOf<TablaOcr?>(null)
        private set

    /** Mapeo columna → campo que el usuario puede ajustar. */
    var mapeo by mutableStateOf<List<CampoEscaneo?>>(emptyList())
        private set

    /** Filas en revisión: se quitan al guardarse, así un reintento no duplica lo ya insertado. */
    var filas by mutableStateOf<List<FilaEscaneo>>(emptyList())
        private set

    var procesando by mutableStateOf(false)
        private set

    var guardando by mutableStateOf(false)
        private set

    /** Último fallo (del OCR, de la revisión o del guardado); la pantalla lo traduce a texto. */
    var error by mutableStateOf<ErrorEscaneo?>(null)
        private set

    /** Índice (0-based) de la fila señalada por [error], para marcarla en la revisión. */
    var filaConError by mutableStateOf<Int?>(null)
        private set

    private var tarea: Job? = null

    /** Cancela el reconocimiento en curso; la imagen elegida sigue en el asistente. */
    fun cancelar() {
        tarea?.cancel()
        tarea = null
        procesando = false
    }

    /** Reconoce el texto de [uri] y vuelca la tabla detectada en la revisión. */
    fun procesar(context: Context, uri: Uri, tipo: TipoEscaneo, campos: List<CampoEscaneo>) {
        if (procesando) return
        procesando = true
        error = null
        filaConError = null
        tarea = viewModelScope.launch {
            try {
                val lineas = withContext(Dispatchers.Default) {
                    val imagen = InputImage.fromFilePath(context, uri) // ya aplica la orientación EXIF
                    val cliente = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    try {
                        cliente.process(imagen).await().textBlocks.flatMap { it.lines }.flatMap { linea ->
                            val palabras = linea.elements.mapNotNull { elemento ->
                                elemento.boundingBox?.let { caja ->
                                    LineaOcr(elemento.text, caja.left, caja.top, caja.right, caja.bottom)
                                }
                            }
                            val propias = linea.boundingBox?.let { caja ->
                                LineaOcr(linea.text, caja.left, caja.top, caja.right, caja.bottom)
                            }
                            // Con las palabras se puede separar una línea que el OCR pegó de dos columnas; si el OCR
                            // no las dio, la línea entera es una celda.
                            when {
                                palabras.isNotEmpty() -> celdasDeLinea(palabras)
                                propias != null -> listOf(propias)
                                else -> emptyList()
                            }.filter { it.texto.isNotBlank() }
                        }
                    } finally {
                        cliente.close()
                    }
                }
                val tabla = tablaOcr(lineas)
                aplicarTabla(tipo, campos, tabla)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = ErrorEscaneo.ImagenIlegible
            } finally {
                procesando = false
            }
        }
    }

    /** Vuelca una tabla ya reconocida (las pruebas entran directo por aquí, sin cámara). */
    fun aplicarTabla(tipo: TipoEscaneo, campos: List<CampoEscaneo>, tabla: TablaOcr) {
        this.tipo = tipo
        this.campos = campos
        this.tabla = tabla
        mapeo = mapeoAutomatico(tabla, campos)
        filas = filasDeLaTabla()
        if (filas.isEmpty()) error = ErrorEscaneo.SinTexto
    }

    /** Rearma la revisión con el mapeo columna → campo elegido por el usuario. */
    fun aplicarMapeo(nuevo: List<CampoEscaneo?>) {
        if (nuevo == mapeo) return
        mapeo = nuevo
        filas = filasDeLaTabla()
    }

    fun actualizar(index: Int, campo: CampoEscaneo, valor: String) {
        val fila = filas.getOrNull(index) ?: return
        filas = filas.toMutableList().also { it[index] = FilaEscaneo(fila.valores + (campo to valor)) }
        error = null
        filaConError = null
    }

    fun eliminar(index: Int) {
        if (index !in filas.indices) return
        filas = filas.filterIndexed { i, _ -> i != index }
        error = null
        filaConError = null
    }

    /** Añade una fila vacía con los campos elegidos (para teclear lo que la imagen no trajo). */
    fun agregar() {
        filas = filas + FilaEscaneo(campos.associateWith { "" })
        error = null
        filaConError = null
    }

    /**
     * Guarda el lote: revisa TODAS las filas con las reglas del dominio antes de insertar nada y quita cada fila al
     * guardarse, para que un reintento tras un fallo parcial no duplique lo ya insertado.
     */
    fun guardar(onDone: (ResumenImportacion) -> Unit) {
        if (guardando || filas.isEmpty()) return
        guardando = true
        viewModelScope.launch {
            val ahora = clock.now()
            revisionDeLote(tipo, filas, ahora)?.let { fallo ->
                error = fallo
                filaConError = fallo.filaSenalada
                guardando = false
                onDone(ResumenImportacion(0, fallo))
                return@launch
            }
            var insertadas = 0
            var fallo: ErrorEscaneo? = null
            while (filas.isNotEmpty()) {
                val fila = filas.first()
                val guardado = when (tipo) {
                    TipoEscaneo.PRODUCTO -> productoDeFila(fila, ahora)?.let { guardarProducto(it, null) }
                    TipoEscaneo.INSUMO -> insumoDeFila(fila, ahora)?.let { guardarInsumo(it) }
                } ?: AppResult.Err(AppError.Validacion("nombre")) // inalcanzable tras la revisión
                if (guardado is AppResult.Err) {
                    fallo = ErrorEscaneo.Rechazado(fila.valor(CampoEscaneo.NOMBRE).trim(), guardado.error)
                    break
                }
                insertadas++
                filas = filas.drop(1)
            }
            error = fallo
            filaConError = if (fallo != null) 0 else null
            guardando = false
            onDone(ResumenImportacion(insertadas, fallo))
        }
    }

    private fun filasDeLaTabla(): List<FilaEscaneo> {
        val detectada = tabla ?: return emptyList()
        return filasDesde(detectada, mapeo, campos).map { fila ->
            // La categoría que la imagen no trajo queda a la vista como «Otros», editable: nada en silencio.
            if (tipo == TipoEscaneo.PRODUCTO && fila.valor(CampoEscaneo.CATEGORIA).isBlank()) {
                FilaEscaneo(fila.valores + (CampoEscaneo.CATEGORIA to Categorias.OTROS))
            } else {
                fila
            }
        }
    }
}
