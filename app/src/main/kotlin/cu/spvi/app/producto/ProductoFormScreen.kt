package cu.spvi.app.producto

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import cu.spvi.app.common.CampoFecha
import cu.spvi.app.common.rememberSalidaProtegida
import cu.spvi.core.money.Money
import cu.spvi.designsystem.component.FiltroEntrada
import cu.spvi.designsystem.component.CardTone
import cu.spvi.designsystem.component.SpviBarraAcciones
import cu.spvi.designsystem.component.SpviBottomSheet
import cu.spvi.designsystem.component.SpviButtonRow
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviEmptyState
import cu.spvi.designsystem.component.SpviIconAction
import cu.spvi.designsystem.component.SpviListItem
import cu.spvi.designsystem.component.SpviLoading
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSecondaryButton
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviSnackbarHost
import cu.spvi.designsystem.component.SpviTextField
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.component.spviContentWidth
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.ilustracion.SpviIlustracion
import cu.spvi.designsystem.token.SpviRadius
import cu.spvi.designsystem.token.SpviSize
import cu.spvi.designsystem.token.SpviSpacing
import cu.spvi.domain.model.Categorias
import cu.spvi.domain.model.Insumo

object ProductoFormTags {
    const val GUARDAR = "producto.guardar"
    const val CATEGORIA = "producto.categoria"
    const val NOMBRE = "producto.nombre"
    const val DESCRIPCION = "producto.descripcion"
    const val FOTO = "producto.foto"
    const val RECETA = "producto.receta"
    const val AGREGAR_INSUMO = "producto.agregar_insumo"
    const val FECHA = "producto.fecha"
    const val COSTO = "producto.costo"
    const val VENTA = "producto.venta"
    const val CANTIDAD = "producto.cantidad"
    const val BAJO = "producto.bajo"
    const val CRITICO = "producto.critico"
    fun cantidadInsumo(id: Long) = "producto.insumo.$id"
}

class AccionesForm(
    val onCategoria: (String) -> Unit = {},
    val onNombre: (String) -> Unit = {},
    val onDescripcion: (String) -> Unit = {},
    val onElegirFoto: () -> Unit = {},
    val onQuitarFoto: () -> Unit = {},
    /** 0.27.0 (T13): foto hecha con la cámara o elegida en la galería (content://). */
    val onFoto: (String) -> Unit = {},
    val onFecha: (java.time.LocalDate?) -> Unit = {},
    val onCosto: (String) -> Unit = {},
    val onVenta: (String) -> Unit = {},
    val onCantidad: (String) -> Unit = {},
    val onBajo: (String) -> Unit = {},
    val onCritico: (String) -> Unit = {},
    val onMostrarInsumos: (Boolean) -> Unit = {},
    val onAgregarInsumo: (Insumo) -> Unit = {},
    val onCantidadInsumo: (Long, String) -> Unit = { _, _ -> },
    val onQuitarInsumo: (Long) -> Unit = {},
    val onGuardar: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * @param onGuardado vuelve a Inventario con el mensaje para el snackbar.
 */
@Composable
fun ProductoFormScreen(
    onBack: () -> Unit,
    onGuardado: (String) -> Unit,
    onCrearInsumo: (String) -> Unit = {},
    viewModel: ProductoFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Selector de fotos del sistema: no necesita permisos de almacenamiento.
    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.fotoElegida(it?.toString()) }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoForm.Guardado -> onGuardado(e.mensaje)
                is EventoForm.Mensaje -> snackbar.showSnackbar(e.texto)
                is EventoForm.EsInsumo -> onCrearInsumo(e.nombre)
            }
        }
    }
    ProductoFormContent(
        state = state,
        snackbar = snackbar,
        acciones = AccionesForm(
            onCategoria = viewModel::categoria, onNombre = viewModel::nombre, onDescripcion = viewModel::descripcion,
            onElegirFoto = { elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onQuitarFoto = viewModel::quitarFoto, onFoto = { viewModel.fotoElegida(it) }, onFecha = viewModel::fecha, onCosto = viewModel::precioCosto,
            onVenta = viewModel::precioVenta, onCantidad = viewModel::cantidad, onBajo = viewModel::nivelBajo,
            onCritico = viewModel::nivelCritico,
            onMostrarInsumos = viewModel::mostrarInsumos, onAgregarInsumo = viewModel::agregarInsumo,
            onCantidadInsumo = viewModel::cantidadInsumo, onQuitarInsumo = viewModel::quitarInsumo,
            onGuardar = viewModel::guardar, onBack = onBack,
        ),
    )
}

@Composable
fun ProductoFormContent(state: ProductoFormUiState, acciones: AccionesForm, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    BackHandler(enabled = state.hojaInsumos) { acciones.onMostrarInsumos(false) }
    val atras = rememberSalidaProtegida(state.sinGuardar && !state.hojaInsumos, acciones.onBack)
    Scaffold(
        topBar = {
            SpviTopBar(
                title = if (state.esNuevo) "Nuevo producto" else "Editar producto",
                onBack = atras,
            )
        },
        // P24: un único Guardar, fijo abajo y centrado (antes estaba arriba y al final del formulario).
        bottomBar = {
            if (!state.noEncontrado && !state.cargando) {
                SpviBarraAcciones {
                    SpviPrimaryButton(
                        if (state.esNuevo) "Guardar producto" else "Guardar cambios", onClick = acciones.onGuardar, loading = state.guardando,
                        icon = SpviIcons.Guardar, modifier = Modifier.testTag(ProductoFormTags.GUARDAR),
                    )
                }
            }
        },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.cargando -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { SpviLoading() }
            state.noEncontrado -> Box(Modifier.padding(padding)) {
                SpviEmptyState(title = "Este producto ya no existe", detail = "Puede que se haya eliminado.", ilustracion = SpviIlustracion.NoExiste) {
                    SpviSecondaryButton("Volver", icon = SpviIcons.Atras, onClick = acciones.onBack)
                }
            }
            else -> Formulario(state, acciones, Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }
    if (state.hojaInsumos) HojaInsumos(state, acciones)
}

@Composable
private fun Formulario(state: ProductoFormUiState, a: AccionesForm, modifier: Modifier) {
    val f = state.form
    val err = state.errores
    val numero = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val entero = KeyboardOptions(keyboardType = KeyboardType.Number)
    Column(
        modifier.fillMaxSize().spviContentWidth().imePadding().verticalScroll(rememberScrollState()).padding(SpviSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SpviSpacing.md),
    ) {
        // 0.27.0 (T13): la foto es lo primero (los Elaborados no llevan foto).
        if (!f.esElaborado) Foto(f.fotoUri, f.nombre, state.importandoFoto, a)
        SpviSecondaryText("* Obligatorio")
        CampoCategoria(f.categoria, state.categorias, err[Campos.CATEGORIA], state.intentado, a.onCategoria, ofrecerInsumos = state.esNuevo)

        SpviTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = f.nombre, onValueChange = a.onNombre, label = "Nombre *", isError = Campos.NOMBRE in err, errorText = err[Campos.NOMBRE], validarAlSalir = true, forzarError = state.intentado,
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth().testTag(ProductoFormTags.NOMBRE),
        )
        SpviTextField(
            filtro = FiltroEntrada.TEXTO,
            // 0.24.0: una línea; obligatoria solo si ya hay otro producto con el mismo nombre.
            value = f.descripcion, onValueChange = a.onDescripcion, label = "Descripción", placeholder = ProductoFormLogic.EJEMPLO_DESCRIPCION,
            supportingText = ProductoFormLogic.ayudaDescripcion(f.descripcion), imeAction = ImeAction.Next,
            isError = Campos.DESCRIPCION in err, errorText = err[Campos.DESCRIPCION], validarAlSalir = true, forzarError = state.intentado,
            modifier = Modifier.fillMaxWidth().testTag(ProductoFormTags.DESCRIPCION),
        )

        if (f.esElaborado) {
            Receta(state, a)
        } else {
            CampoFecha(
                fecha = f.fechaCaducidad, onFecha = a.onFecha, label = "Fecha de caducidad (opcional)",
                modifier = Modifier.fillMaxWidth(), tag = ProductoFormTags.FECHA,
            )
        }

        if (f.esElaborado) {
            SpviCard(tone = CardTone.Tonal) {
                Text("Precio de costo: ${state.costoReceta?.let(Money::format) ?: "—"}", style = MaterialTheme.typography.bodyLarge)
                SpviSecondaryText("Sale de la receta")
            }
        } else {
            SpviTextField(
                filtro = FiltroEntrada.DINERO,
                value = f.precioCosto, onValueChange = a.onCosto, label = "Precio de costo (CUP) *", placeholder = "0.00", keyboardOptions = numero,
                isError = Campos.PRECIO_COSTO in err, errorText = err[Campos.PRECIO_COSTO], validarAlSalir = true, forzarError = state.intentado, supportingText = "Por unidad",
                imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth().testTag(ProductoFormTags.COSTO),
            )
        }
        SpviTextField(
            filtro = FiltroEntrada.DINERO,
            value = f.precioVenta, onValueChange = a.onVenta, label = "Precio de venta (CUP) *", placeholder = "0.00", keyboardOptions = numero,
            isError = Campos.PRECIO_VENTA in err, errorText = err[Campos.PRECIO_VENTA], validarAlSalir = true, forzarError = state.intentado, supportingText = "Por unidad",
            imeAction = if (f.esElaborado) ImeAction.Done else ImeAction.Next, onImeAction = if (f.esElaborado) a.onGuardar else null,
            modifier = Modifier.fillMaxWidth().testTag(ProductoFormTags.VENTA),
        )
        // P26: un Elaborado no tiene existencias ni niveles propios (alcanza según sus insumos).
        if (!f.esElaborado) {
            SpviTextField(
                filtro = FiltroEntrada.ENTERO,
                value = f.cantidad, onValueChange = a.onCantidad, label = "Cantidad *", placeholder = "0", keyboardOptions = entero,
                isError = Campos.CANTIDAD in err, errorText = err[Campos.CANTIDAD], validarAlSalir = true, forzarError = state.intentado, supportingText = "Unidades que tienes",
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth().testTag(ProductoFormTags.CANTIDAD),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(SpviSpacing.md)) {
                SpviTextField(
                    filtro = FiltroEntrada.ENTERO,
                    value = f.nivelBajo, onValueChange = a.onBajo, label = "Nivel bajo", placeholder = "5", keyboardOptions = entero,
                    isError = Campos.NIVEL_BAJO in err, errorText = err[Campos.NIVEL_BAJO], validarAlSalir = true, forzarError = state.intentado, supportingText = "Opcional",
                    imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f).testTag(ProductoFormTags.BAJO),
                )
                SpviTextField(
                    filtro = FiltroEntrada.ENTERO,
                    value = f.nivelCritico, onValueChange = a.onCritico, label = "Nivel crítico", placeholder = "1", keyboardOptions = entero,
                    isError = Campos.NIVEL_CRITICO in err, errorText = err[Campos.NIVEL_CRITICO], validarAlSalir = true, forzarError = state.intentado, supportingText = "Opcional",
                    imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f).testTag(ProductoFormTags.CRITICO),
                )
            }
            SpviSecondaryText("Vacíos: se usan los niveles de Ajustes.")
        }
        if (state.intentado && err.isNotEmpty()) {
            Text(
                "Revisa ${if (err.size == 1) "el campo marcado" else "los ${err.size} campos marcados"} en rojo.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoCategoria(
    valor: String,
    categorias: List<String>,
    error: String?,
    forzarError: Boolean,
    onValor: (String) -> Unit,
    ofrecerInsumos: Boolean = false,
) {
    var abierto by remember { mutableStateOf(false) }
    // P31: en un artículo nuevo, «Insumos» es la primera opción (lleva al formulario de insumo).
    val todas = if (ofrecerInsumos) listOf(Categorias.INSUMOS) + categorias.filterNot(Categorias::esInsumos) else categorias
    val opciones = todas.filter { valor.isBlank() || it.contains(valor.trim(), ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = abierto && opciones.isNotEmpty(), onExpandedChange = { abierto = it }) {
        SpviTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = valor, onValueChange = { onValor(it); abierto = true }, label = "Categoría *",
            isError = error != null, errorText = error, validarAlSalir = true, forzarError = forzarError, supportingText = "Elige o escribe una nueva",
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable).testTag(ProductoFormTags.CATEGORIA),
        )
        ExposedDropdownMenu(expanded = abierto && opciones.isNotEmpty(), onDismissRequest = { abierto = false }) {
            opciones.forEach { c ->
                DropdownMenuItem(text = { Text(c) }, onClick = { onValor(c); abierto = false })
            }
        }
    }
}

@Composable
private fun Foto(uri: String?, nombre: String, importando: Boolean, a: AccionesForm) {
    // 0.27.0 (T13): miniatura centrada + Cámara / Galería / Quitar (solo icono).
    cu.spvi.app.common.SelectorFoto(
        uri = uri, descripcion = nombre.ifBlank { "producto" }, importando = importando,
        onFoto = a.onFoto, onQuitar = a.onQuitarFoto, modifier = Modifier.testTag(ProductoFormTags.FOTO),
    )
}

@Composable
private fun Receta(state: ProductoFormUiState, a: AccionesForm) {
    val err = state.errores[Campos.RECETA]
    Column(Modifier.testTag(ProductoFormTags.RECETA), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
        Text("Receta *", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
        SpviSecondaryText("Insumos de UNA unidad", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        state.form.receta.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                SpviTextField(
                    filtro = FiltroEntrada.DECIMAL,
                    value = l.cantidad, onValueChange = { a.onCantidadInsumo(l.insumoId, it) }, label = "${l.nombre} (${l.simbolo})",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), clearable = false,
                    modifier = Modifier.weight(1f).testTag(ProductoFormTags.cantidadInsumo(l.insumoId)),
                )
                SpviIconAction(SpviIcons.Quitar, "Quitar ${l.nombre}", onClick = { a.onQuitarInsumo(l.insumoId) })
            }
        }
        if (err != null) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        SpviButtonRow {
            SpviSecondaryButton("Agregar insumo", onClick = { a.onMostrarInsumos(true) }, icon = SpviIcons.AgregarALista, modifier = Modifier.testTag(ProductoFormTags.AGREGAR_INSUMO))
        }
    }
}

@Composable
private fun HojaInsumos(state: ProductoFormUiState, a: AccionesForm) {
    var buscar by rememberSaveable { mutableStateOf("") }
    SpviBottomSheet(onDismiss = { a.onMostrarInsumos(false) }, title = "Agregar insumo") {
        val lista = state.insumosDisponibles.filter { buscar.isBlank() || it.nombre.contains(buscar.trim(), ignoreCase = true) }
        if (state.insumos.isEmpty()) {
            SpviEmptyState(
                title = "Aún no tienes insumos", detail = "Créalos en Inventario → Agregar → Insumo.", ilustracion = SpviIlustracion.Elaboracion,
                modifier = Modifier.heightIn(max = SpviSize.contentMaxWidth),
            )
        } else {
            SpviTextField(filtro = FiltroEntrada.BUSQUEDA, value = buscar, onValueChange = { buscar = it }, label = "Buscar insumo", leadingIcon = SpviIcons.Buscar, modifier = Modifier.fillMaxWidth())
            if (lista.isEmpty()) SpviSecondaryText("Ningún insumo coincide.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            lista.forEach { i ->
                SpviListItem(
                    title = i.nombre, indicatorColor = null,
                    onClick = { a.onAgregarInsumo(i) },
                )
            }
        }
    }
}
