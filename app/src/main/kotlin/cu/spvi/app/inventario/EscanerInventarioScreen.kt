package cu.spvi.app.inventario

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import cu.spvi.app.R
import cu.spvi.app.common.SelectorFoto
import cu.spvi.app.insumos.InsumoFormLogic
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppError.Regla
import cu.spvi.designsystem.component.BannerTone
import cu.spvi.designsystem.component.FiltroEntrada
import cu.spvi.designsystem.component.SpviBarraAcciones
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviChip
import cu.spvi.designsystem.component.SpviIconAction
import cu.spvi.designsystem.component.SpviLinearProgress
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSecondaryButton
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviStatusBanner
import cu.spvi.designsystem.component.SpviTextButton
import cu.spvi.designsystem.component.SpviTextField
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviSpacing
import cu.spvi.domain.model.Categorias
import cu.spvi.domain.model.UnidadMedida

private const val PASO_IMAGEN = 0
private const val PASO_TABLA = 1
private const val PASO_REVISION = 2

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

/** Campos marcables del asistente, en su orden: lo que no se marque no se extrae ni se guarda. */
private fun camposDe(tipo: TipoEscaneo): List<CampoEscaneo> = when (tipo) {
    TipoEscaneo.PRODUCTO -> listOf(
        CampoEscaneo.NOMBRE, CampoEscaneo.CATEGORIA, CampoEscaneo.DESCRIPCION,
        CampoEscaneo.CANTIDAD, CampoEscaneo.COSTO, CampoEscaneo.VENTA,
    )
    TipoEscaneo.INSUMO -> listOf(
        CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD, CampoEscaneo.COSTO, CampoEscaneo.VENTA, CampoEscaneo.UNIDAD,
    )
}

/** Campos que el dominio exige: no se pueden desmarcar. */
private fun obligatoriosDe(tipo: TipoEscaneo): Set<CampoEscaneo> = when (tipo) {
    TipoEscaneo.PRODUCTO -> setOf(
        CampoEscaneo.NOMBRE, CampoEscaneo.CATEGORIA, CampoEscaneo.CANTIDAD, CampoEscaneo.COSTO, CampoEscaneo.VENTA,
    )
    TipoEscaneo.INSUMO -> setOf(
        CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD, CampoEscaneo.COSTO, CampoEscaneo.UNIDAD,
    )
}

private fun filtroDe(campo: CampoEscaneo, tipo: TipoEscaneo): FiltroEntrada = when (campo) {
    CampoEscaneo.NOMBRE, CampoEscaneo.CATEGORIA, CampoEscaneo.DESCRIPCION, CampoEscaneo.UNIDAD -> FiltroEntrada.NOMBRE
    CampoEscaneo.CANTIDAD -> if (tipo == TipoEscaneo.PRODUCTO) FiltroEntrada.ENTERO else FiltroEntrada.CANTIDAD
    CampoEscaneo.COSTO, CampoEscaneo.VENTA -> FiltroEntrada.DINERO
}

/** ¿La fila debe marcar este campo como erróneo? */
private fun campoConError(error: ErrorEscaneo?, campo: CampoEscaneo): Boolean = when (error) {
    is ErrorEscaneo.ValorInvalido -> error.campo == campo
    is ErrorEscaneo.FaltaNombre -> campo == CampoEscaneo.NOMBRE
    else -> false
}

/** Texto en español del error: el ViewModel no usa textos, los resuelve la pantalla. */
private fun textoError(context: Context, error: ErrorEscaneo): String = when (error) {
    ErrorEscaneo.SinTexto -> context.getString(R.string.scan_no_text)
    ErrorEscaneo.ImagenIlegible -> context.getString(R.string.scan_process_error)
    is ErrorEscaneo.FaltaNombre -> context.getString(R.string.scan_missing_name, error.fila)
    is ErrorEscaneo.ValorInvalido -> when {
        error.regla == null ->
            context.getString(R.string.scan_error_unread, context.getString(error.campo.recursoEtiqueta()), error.fila)

        error.campo == CampoEscaneo.VENTA && error.regla == Regla.RANGO ->
            context.getString(R.string.scan_error_price, error.fila)

        error.campo == CampoEscaneo.CATEGORIA && error.regla == Regla.NO_PERMITIDO ->
            context.getString(R.string.scan_error_category_special, error.fila)

        else -> context.getString(R.string.scan_error_field, context.getString(error.campo.recursoEtiqueta()), error.fila)
    }

    is ErrorEscaneo.Rechazado -> {
        val causa = error.error
        val repetido = causa is AppError.Duplicado || (causa is AppError.Validacion && causa.campo == "descripcion")
        if (repetido) {
            context.getString(R.string.scan_save_duplicated, error.nombre)
        } else {
            context.getString(R.string.scan_save_error, error.nombre)
        }
    }
}

@Composable
fun EscanerInventarioScreen(
    onBack: () -> Unit,
    onGuardado: (String) -> Unit,
    viewModel: EscanerInventarioViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var paso by rememberSaveable { mutableIntStateOf(PASO_IMAGEN) }
    var uri by rememberSaveable { mutableStateOf<String?>(null) }
    var tipoCodigo by rememberSaveable { mutableStateOf(TipoEscaneo.PRODUCTO.name) }
    val tipo = remember(tipoCodigo) { TipoEscaneo.valueOf(tipoCodigo) }
    var marcados by rememberSaveable(tipoCodigo) { mutableStateOf(obligatoriosDe(tipo).map { it.name }) }
    val campos = remember(marcados) { CampoEscaneo.entries.filter { it.name in marcados } }
    var aviso by remember { mutableStateOf<String?>(null) }
    val tabla = viewModel.tabla
    // Sin columnas detectadas no hay nada que mapear: se pasa directo a la revisión (con su aviso). Si además no hay
    // filas, el paso de la tabla muestra el error y deja volver.
    LaunchedEffect(tabla, paso) {
        if (paso == PASO_TABLA && tabla != null && !tabla.estructura && viewModel.filas.isNotEmpty()) paso = PASO_REVISION
    }
    BackHandler(enabled = paso == PASO_REVISION) {
        paso = if (tabla?.estructura == true) PASO_TABLA else PASO_IMAGEN
    }
    Scaffold(topBar = { SpviTopBar(title = stringResource(R.string.scan_title), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (paso) {
                PASO_IMAGEN -> PasoImagen(
                    tipo = tipo,
                    campos = camposDe(tipo),
                    marcados = marcados,
                    uri = uri,
                    procesando = viewModel.procesando,
                    onTipo = { tipoCodigo = it.name },
                    onMarcar = { campo -> marcados = if (campo.name in marcados) marcados - campo.name else marcados + campo.name },
                    onUri = { uri = it },
                    onProcesar = {
                        uri?.let { elegida ->
                            aviso = null
                            viewModel.procesar(context, Uri.parse(elegida), tipo, campos)
                            paso = PASO_TABLA
                        }
                    },
                )

                PASO_TABLA -> PasoTabla(
                    procesando = viewModel.procesando,
                    tabla = tabla,
                    mapeo = viewModel.mapeo,
                    campos = campos,
                    error = viewModel.error,
                    onCancelar = viewModel::cancelar,
                    onMapeo = viewModel::aplicarMapeo,
                    onContinuar = { paso = PASO_REVISION },
                    onVolver = { paso = PASO_IMAGEN },
                )

                else -> PasoRevision(
                    viewModel = viewModel,
                    tipo = tipo,
                    campos = campos,
                    aviso = aviso,
                    onAviso = { aviso = it },
                    onTipo = { paso = PASO_IMAGEN },
                    onGuardado = onGuardado,
                )
            }
        }
    }
}

@Composable
private fun PasoImagen(
    tipo: TipoEscaneo,
    campos: List<CampoEscaneo>,
    marcados: List<String>,
    uri: String?,
    procesando: Boolean,
    onTipo: (TipoEscaneo) -> Unit,
    onMarcar: (CampoEscaneo) -> Unit,
    onUri: (String?) -> Unit,
    onProcesar: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SpviSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
    ) {
        Text(stringResource(R.string.scan_choose_type), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            TipoEscaneo.entries.forEach { opcion ->
                SpviChip(
                    label = stringResource(if (opcion == TipoEscaneo.PRODUCTO) R.string.scan_products else R.string.scan_supplies),
                    selected = tipo == opcion,
                    onClick = { onTipo(opcion) },
                )
            }
        }
        Text(stringResource(R.string.scan_choose_fields), style = MaterialTheme.typography.titleMedium)
        campos.forEach { campo ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = campo.name in marcados,
                    onCheckedChange = { onMarcar(campo) },
                    enabled = campo !in obligatoriosDe(tipo),
                )
                Text(stringResource(campo.recursoEtiqueta()))
            }
        }
        SelectorFoto(
            uri = uri,
            descripcion = stringResource(R.string.scan_photo),
            importando = false,
            onFoto = onUri,
            onQuitar = { onUri(null) },
            modifier = Modifier.fillMaxWidth(),
        )
        SpviPrimaryButton(
            text = stringResource(R.string.scan_process),
            onClick = onProcesar,
            icon = SpviIcons.Escanear,
            enabled = uri != null && !procesando,
        )
    }
}

@Composable
private fun PasoTabla(
    procesando: Boolean,
    tabla: TablaOcr?,
    mapeo: List<CampoEscaneo?>,
    campos: List<CampoEscaneo>,
    error: ErrorEscaneo?,
    onCancelar: () -> Unit,
    onMapeo: (List<CampoEscaneo?>) -> Unit,
    onContinuar: () -> Unit,
    onVolver: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SpviSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
    ) {
        if (procesando) {
            SpviLinearProgress()
            SpviTextButton(text = stringResource(R.string.scan_cancel), onClick = onCancelar, icon = SpviIcons.Cancelar)
            return@Column
        }
        error?.let { SpviStatusBanner(text = textoError(context, it), tone = BannerTone.Critico) }
        val detectada = tabla
        if (detectada == null || detectada.filas.isEmpty()) {
            SpviSecondaryButton(text = stringResource(R.string.scan_back), onClick = onVolver, icon = SpviIcons.Atras)
            return@Column
        }
        Text(stringResource(R.string.scan_columns_title), style = MaterialTheme.typography.titleMedium)
        SpviSecondaryText(stringResource(R.string.scan_columns_found, detectada.columnas))
        SpviSecondaryText(stringResource(R.string.scan_columns_hint))
        val ignorar = stringResource(R.string.scan_column_ignore)
        // Etiqueta visible de cada campo, para poder elegir en la lista (el valor guardado es el campo).
        val opciones: List<Pair<String, CampoEscaneo?>> =
            listOf(ignorar to null) + campos.map { stringResource(it.recursoEtiqueta()) to it }
        val muestra = detectada.filas.first()
        repeat(detectada.columnas) { columna ->
            val titulo = detectada.encabezado?.getOrNull(columna)?.takeIf { it.isNotBlank() }
                ?: muestra.getOrNull(columna)?.takeIf { it.isNotBlank() }
                ?: ignorar
            SpviSecondaryText(stringResource(R.string.scan_column_sample, columna + 1, titulo))
            CampoSeleccion(
                label = stringResource(R.string.scan_column_label, columna + 1),
                valor = mapeo.getOrNull(columna)?.let { stringResource(it.recursoEtiqueta()) } ?: ignorar,
                opciones = opciones.map { it.first },
                onValor = { elegido ->
                    val campo = opciones.firstOrNull { it.first == elegido }?.second
                    onMapeo(mapeo.toMutableList().also { it[columna] = campo })
                },
                isError = false,
                errorText = null,
                forzarError = false,
                editable = false,
            )
        }
        SpviPrimaryButton(text = stringResource(R.string.scan_continue), onClick = onContinuar, icon = SpviIcons.Siguiente)
    }
}

@Composable
private fun PasoRevision(
    viewModel: EscanerInventarioViewModel,
    tipo: TipoEscaneo,
    campos: List<CampoEscaneo>,
    aviso: String?,
    onAviso: (String?) -> Unit,
    onTipo: () -> Unit,
    onGuardado: (String) -> Unit,
) {
    val context = LocalContext.current
    val error = viewModel.error
    Column(Modifier.fillMaxSize().padding(SpviSpacing.md), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.scan_review), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            SpviChip(
                label = stringResource(if (tipo == TipoEscaneo.PRODUCTO) R.string.scan_products else R.string.scan_supplies),
                selected = false,
                onClick = onTipo,
                icon = SpviIcons.Editar,
            )
        }
        SpviTextButton(text = stringResource(R.string.scan_add_row), onClick = viewModel::agregar, icon = SpviIcons.Agregar)
        if (viewModel.tabla?.estructura == false) {
            SpviStatusBanner(text = stringResource(R.string.scan_structure_fallback), tone = BannerTone.Aviso)
        }
        aviso?.let { SpviStatusBanner(text = it, tone = BannerTone.Aviso) }
        error?.let { SpviStatusBanner(text = textoError(context, it), tone = BannerTone.Critico) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            itemsIndexed(viewModel.filas) { index, fila ->
                FilaRevision(
                    index = index,
                    fila = fila,
                    tipo = tipo,
                    campos = campos,
                    error = error?.takeIf { viewModel.filaConError == index },
                    onCampo = { campo, valor -> viewModel.actualizar(index, campo, valor) },
                    onEliminar = { viewModel.eliminar(index) },
                )
            }
        }
        SpviBarraAcciones {
            SpviPrimaryButton(
                text = stringResource(R.string.scan_confirm),
                onClick = {
                    onAviso(null)
                    viewModel.guardar { resumen ->
                        val fallo = resumen.error
                        if (fallo == null) {
                            onGuardado(context.getString(R.string.scan_added_count, resumen.insertadas))
                        } else if (resumen.insertadas > 0) {
                            onAviso(
                                context.getString(
                                    R.string.scan_partial_failure,
                                    resumen.insertadas,
                                    textoError(context, fallo),
                                ),
                            )
                        }
                    }
                },
                icon = SpviIcons.Guardar,
                enabled = viewModel.filas.isNotEmpty() && !viewModel.procesando,
                loading = viewModel.guardando,
            )
        }
    }
}

@Composable
private fun FilaRevision(
    index: Int,
    fila: FilaEscaneo,
    tipo: TipoEscaneo,
    campos: List<CampoEscaneo>,
    error: ErrorEscaneo?,
    onCampo: (CampoEscaneo, String) -> Unit,
    onEliminar: () -> Unit,
) {
    val context = LocalContext.current
    SpviCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.scan_row_number, index + 1), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            SpviIconAction(icon = SpviIcons.Eliminar, contentDescription = stringResource(R.string.scan_remove_row), onClick = onEliminar)
        }
        campos.forEach { campo ->
            val conError = campoConError(error, campo)
            val mensaje = if (conError && error != null) textoError(context, error) else null
            val etiqueta = stringResource(campo.recursoEtiqueta()) + if (campo in obligatoriosDe(tipo)) " *" else ""
            val valor = fila.valor(campo)
            when (campo) {
                CampoEscaneo.CATEGORIA -> CampoSeleccion(
                    label = etiqueta,
                    valor = valor,
                    opciones = Categorias.PREDEFINIDAS,
                    onValor = { onCampo(campo, it) },
                    isError = conError,
                    errorText = mensaje,
                    forzarError = conError,
                    editable = true,
                    supportingText = stringResource(R.string.scan_category_pick),
                )

                CampoEscaneo.UNIDAD -> CampoSeleccion(
                    label = etiqueta,
                    valor = valor,
                    opciones = UnidadMedida.entries.map { InsumoFormLogic.etiqueta(it) },
                    onValor = { onCampo(campo, it) },
                    isError = conError,
                    errorText = mensaje,
                    forzarError = conError,
                    editable = false,
                    supportingText = stringResource(R.string.scan_unit_pick),
                )

                else -> SpviTextField(
                    filtro = filtroDe(campo, tipo),
                    value = valor,
                    onValueChange = { onCampo(campo, it) },
                    label = etiqueta,
                    isError = conError,
                    errorText = mensaje,
                    validarAlSalir = true,
                    forzarError = conError,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Campo con lista de opciones: con sugerencias y escritura libre (categoría) o de solo lectura (unidad, columna). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoSeleccion(
    label: String,
    valor: String,
    opciones: List<String>,
    onValor: (String) -> Unit,
    isError: Boolean,
    errorText: String?,
    forzarError: Boolean,
    editable: Boolean,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    var abierto by remember { mutableStateOf(false) }
    val candidatas = if (editable) opciones.filter { valor.isBlank() || it.contains(valor.trim(), ignoreCase = true) } else opciones
    ExposedDropdownMenuBox(expanded = abierto && candidatas.isNotEmpty(), onExpandedChange = { abierto = it }) {
        SpviTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = valor,
            onValueChange = { onValor(it); abierto = true },
            label = label,
            readOnly = !editable,
            isError = isError,
            errorText = errorText,
            supportingText = supportingText,
            validarAlSalir = true,
            forzarError = forzarError,
            modifier = modifier.fillMaxWidth().menuAnchor(if (editable) MenuAnchorType.PrimaryEditable else MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = abierto && candidatas.isNotEmpty(), onDismissRequest = { abierto = false }) {
            candidatas.forEach { opcion ->
                DropdownMenuItem(text = { Text(opcion) }, onClick = { onValor(opcion); abierto = false })
            }
        }
    }
}
