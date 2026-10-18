package cu.spvi.app.inventario

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import cu.spvi.app.R
import cu.spvi.app.common.SelectorFoto
import cu.spvi.core.money.Cup
import cu.spvi.core.money.Money
import cu.spvi.core.quantity.Cantidad
import cu.spvi.core.time.Clock
import cu.spvi.domain.model.*
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

private enum class TipoEscaneo { PRODUCTO, INSUMO }
enum class CampoEscaneo { NOMBRE, CATEGORIA, DESCRIPCION, CANTIDAD, COSTO, VENTA, UNIDAD }

@androidx.annotation.StringRes
private fun CampoEscaneo.recursoEtiqueta(): Int = when (this) {
    CampoEscaneo.NOMBRE -> R.string.scan_field_name
    CampoEscaneo.CATEGORIA -> R.string.scan_field_category
    CampoEscaneo.DESCRIPCION -> R.string.scan_field_description
    CampoEscaneo.CANTIDAD -> R.string.scan_field_quantity
    CampoEscaneo.COSTO -> R.string.scan_field_cost
    CampoEscaneo.VENTA -> R.string.scan_field_price
    CampoEscaneo.UNIDAD -> R.string.scan_field_unit
}
data class FilaEscaneo(val valores: Map<CampoEscaneo, String> = emptyMap())

@HiltViewModel
class EscanerInventarioViewModel @Inject constructor(
    private val guardarProducto: GuardarProducto,
    private val guardarInsumo: GuardarInsumo,
    private val clock: Clock,
) : ViewModel() {
    var filas by mutableStateOf<List<FilaEscaneo>>(emptyList()); private set
    var procesando by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var avisoEstructura by mutableStateOf(false); private set
    var guardando by mutableStateOf(false); private set
    private var tarea: Job? = null
    fun cancelar() { tarea?.cancel(); tarea = null; procesando = false }

    fun procesar(context: android.content.Context, uri: Uri, tipo: String, campos: Set<String>) {
        if (procesando) return
        procesando = true; error = null
        tarea = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val image = InputImage.fromFilePath(context, uri)
                    val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    try { client.process(image).await().textBlocks.flatMap { it.lines }.map { it.text }.filter { it.isNotBlank() } }
                    finally { client.close() }
                }
                // Fallback offline: ML Kit conserva bloques/líneas y permite reconstruir registros delimitados.
                // Si no hay tabla estructurada disponible, cada línea se ofrece como registro editable.
                avisoEstructura = true
                val allowed = campos.mapNotNull { runCatching { CampoEscaneo.valueOf(it) }.getOrNull() }.toSet()
                filas = result.map { line ->
                    val cells = line.split(Regex("\\t+|\\s*;\\s*|\\s*\\|\\s*"))
                    FilaEscaneo(allowed.associateWith { campo ->
                        val column = allowed.indexOf(campo)
                        cells.getOrNull(column)?.trim().orEmpty().ifEmpty { if (campo == CampoEscaneo.NOMBRE) line.trim() else "" }
                    })
                }
                if (filas.isEmpty()) error = context.getString(R.string.scan_no_text)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.localizedMessage ?: context.getString(R.string.scan_process_error) }
            finally { procesando = false }
        }
    }
    fun actualizar(index: Int, campo: String, value: String) {
        val c = runCatching { CampoEscaneo.valueOf(campo) }.getOrNull() ?: return
        filas = filas.toMutableList().also { it[index] = FilaEscaneo(it[index].valores + (c to value)) }
    }
    fun agregar() { filas = filas + FilaEscaneo(mapOf(CampoEscaneo.NOMBRE to "")) }
    fun setError(message: String?) { error = message }
    fun eliminar(i: Int) { filas = filas.filterIndexed { index, _ -> index != i } }
    fun guardar(context: android.content.Context, tipo: String, onDone: (Int, String?) -> Unit) {
        if (guardando) return
        guardando = true
        viewModelScope.launch {
            var added = 0; var failure: String? = null
            var processed = 0
            for (f in filas) {
                val v = f.valores
                val nombre = v[CampoEscaneo.NOMBRE].orEmpty().trim()
                if (nombre.isBlank()) { failure = context.getString(R.string.scan_missing_name); break }
                val cantidadTxt = v[CampoEscaneo.CANTIDAD].orEmpty().replace(",", "").trim()
                val cantidad = cantidadTxt.toLongOrNull()
                val costo = Money.parse(v[CampoEscaneo.COSTO].orEmpty())
                if (tipo == TipoEscaneo.PRODUCTO.name) {
                    val categoria = v[CampoEscaneo.CATEGORIA].orEmpty().ifBlank { "Otros" }
                    val venta = Money.parse(v[CampoEscaneo.VENTA].orEmpty())
                    if (costo == null || venta == null || cantidad == null || cantidad < 0) { failure = context.getString(R.string.scan_invalid_product_values); break }
                    val p = Producto(categoria = categoria, nombre = nombre, descripcion = v[CampoEscaneo.DESCRIPCION], precioCosto = costo, precioVenta = venta, cantidad = cantidad, creadoEn = clock.now())
                    when (val r = guardarProducto(p, null)) { is cu.spvi.core.result.AppResult.Ok -> added++; is cu.spvi.core.result.AppResult.Err -> { failure = context.getString(R.string.scan_save_error, nombre, r.error.toString()) } }
                } else {
                    val qty = Cantidad.parse(v[CampoEscaneo.CANTIDAD].orEmpty())
                    if (costo == null || qty == null) { failure = context.getString(R.string.scan_invalid_supply_values); break }
                    val unit = UnidadMedida.entries.firstOrNull { it.simbolo.equals(v[CampoEscaneo.UNIDAD], true) } ?: UnidadMedida.UNIDAD
                    val i = Insumo(nombre = nombre, unidad = unit, precio = costo, cantidad = qty, creadoEn = clock.now())
                    when (val r = guardarInsumo(i)) { is cu.spvi.core.result.AppResult.Ok -> added++; is cu.spvi.core.result.AppResult.Err -> { failure = context.getString(R.string.scan_save_error, nombre, r.error.toString()) } }
                }
                if (failure != null) break
                processed++
            }
            // Si una fila falla después de guardar otras, quita las ya insertadas para que un reintento no las duplique.
            if (processed > 0) filas = filas.drop(processed)
            guardando = false
            onDone(added, failure)
        }
    }
}

@Composable
fun EscanerInventarioScreen(onBack: () -> Unit, onGuardado: (String) -> Unit, viewModel: EscanerInventarioViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var tipo by remember { mutableStateOf(TipoEscaneo.PRODUCTO.name) }
    var uri by remember { mutableStateOf<String?>(null) }
    var etapa by remember { mutableIntStateOf(0) }
    val seleccionados = remember(tipo) { mutableStateListOf<String>().apply { addAll(if (tipo == TipoEscaneo.PRODUCTO.name) listOf("NOMBRE", "CATEGORIA", "CANTIDAD", "COSTO", "VENTA") else listOf("NOMBRE", "CANTIDAD", "COSTO", "UNIDAD")) } }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { uri = it.toString() } }
    Scaffold(topBar = { cu.spvi.designsystem.component.SpviTopBar(title = stringResource(R.string.scan_title), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (etapa == 0) {
                Text(stringResource(R.string.scan_choose_type), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tipo == TipoEscaneo.PRODUCTO.name, onClick = { tipo = TipoEscaneo.PRODUCTO.name }, label = { Text(stringResource(R.string.scan_products)) })
                    FilterChip(selected = tipo == TipoEscaneo.INSUMO.name, onClick = { tipo = TipoEscaneo.INSUMO.name }, label = { Text(stringResource(R.string.scan_supplies)) })
                }
                Text(stringResource(R.string.scan_choose_fields), style = MaterialTheme.typography.titleMedium)
                val campos = if (tipo == TipoEscaneo.PRODUCTO.name) CampoEscaneo.entries.filter { it != CampoEscaneo.UNIDAD } else listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD, CampoEscaneo.COSTO, CampoEscaneo.VENTA, CampoEscaneo.UNIDAD)
                val obligatorios = if (tipo == TipoEscaneo.PRODUCTO.name) setOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD, CampoEscaneo.COSTO, CampoEscaneo.VENTA) else setOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD, CampoEscaneo.COSTO)
                campos.forEach { campo -> Row { Checkbox(campo.name in seleccionados, { checked -> if (checked) seleccionados.add(campo.name) else seleccionados.remove(campo.name) }, enabled = campo !in obligatorios); Text(stringResource(campo.recursoEtiqueta()), Modifier.padding(top = 12.dp)) } }
                SelectorFoto(uri, stringResource(R.string.scan_title), false, { uri = it }, {}, Modifier.fillMaxWidth())
                TextButton(onClick = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.scan_gallery)) }
                Button(onClick = { uri?.let { viewModel.procesar(context, Uri.parse(it), tipo, seleccionados.toSet()); etapa = 1 } }, enabled = uri != null && !viewModel.procesando) { Text(stringResource(R.string.scan_process)) }
            } else {
                if (viewModel.procesando) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = viewModel::cancelar) { Text(stringResource(R.string.scan_cancel)) }
                }
                viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (viewModel.avisoEstructura && viewModel.filas.isNotEmpty()) Text(stringResource(R.string.scan_structure_fallback), color = MaterialTheme.colorScheme.tertiary)
                Text(stringResource(R.string.scan_review), style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(viewModel.filas) { index, row ->
                        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.valores.forEach { (campo, value) -> OutlinedTextField(value, { viewModel.actualizar(index, campo.name, it) }, label = { Text(stringResource(campo.recursoEtiqueta())) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                            TextButton(onClick = { viewModel.eliminar(index) }) { Text(stringResource(R.string.scan_remove_row)) }
                        } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::agregar) { Text(stringResource(R.string.scan_add_row)) }
                    TextButton(onClick = { etapa = 0 }) { Text(stringResource(R.string.scan_back)) }
                }
                Button(onClick = { viewModel.guardar(context, tipo) { n, err -> if (err != null) viewModel.setError(if (n > 0) context.getString(R.string.scan_partial_failure, n, err) else err) else onGuardado(context.getString(R.string.scan_added_count, n)) } }, enabled = viewModel.filas.isNotEmpty() && !viewModel.procesando && !viewModel.guardando) {
                    if (viewModel.guardando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.scan_confirm))
                }
            }
        }
    }
}
