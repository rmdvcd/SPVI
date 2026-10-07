package cu.spvi.app.servicios

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
import cu.spvi.domain.model.Insumo

object ServicioFormTags {
    const val NOMBRE = "servicio_nombre"
    const val TIPO = "servicio_tipo"
    const val IMPORTE = "servicio_importe"
    const val DESCRIPCION = "servicio_descripcion"
    const val FOTO = "servicio_foto"
    const val INSUMOS = "servicio_insumos"
    const val AGREGAR_INSUMO = "servicio_agregar_insumo"
    const val GUARDAR = "servicio_guardar"
    fun cantidadInsumo(id: Long) = "servicio_insumo_$id"
}

class AccionesServicioForm(
    val onNombre: (String) -> Unit = {},
    val onTipo: (String) -> Unit = {},
    val onImporte: (String) -> Unit = {},
    val onDescripcion: (String) -> Unit = {},
    val onElegirFoto: () -> Unit = {},
    val onQuitarFoto: () -> Unit = {},
    /** 0.27.0 (T13): foto hecha con la cámara o elegida en la galería (content://). */
    val onFoto: (String) -> Unit = {},
    val onMostrarInsumos: (Boolean) -> Unit = {},
    val onAgregarInsumo: (Insumo) -> Unit = {},
    val onCantidadInsumo: (Long, String) -> Unit = { _, _ -> },
    val onQuitarInsumo: (Long) -> Unit = {},
    val onGuardar: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun ServicioFormScreen(onBack: () -> Unit, onGuardado: (String) -> Unit, viewModel: ServicioFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Selector de fotos del sistema: no necesita permisos de almacenamiento.
    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.fotoElegida(it?.toString()) }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoServicioForm.Guardado -> onGuardado(e.mensaje)
                is EventoServicioForm.Mensaje -> snackbar.showSnackbar(e.texto)
            }
        }
    }
    ServicioFormContent(
        state = state,
        snackbar = snackbar,
        acciones = AccionesServicioForm(
            onNombre = viewModel::nombre, onTipo = viewModel::tipo, onImporte = viewModel::importe, onDescripcion = viewModel::descripcion,
            onElegirFoto = { elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onQuitarFoto = viewModel::quitarFoto, onFoto = { viewModel.fotoElegida(it) }, onMostrarInsumos = viewModel::mostrarInsumos, onAgregarInsumo = viewModel::agregarInsumo,
            onCantidadInsumo = viewModel::cantidadInsumo, onQuitarInsumo = viewModel::quitarInsumo, onGuardar = viewModel::guardar, onBack = onBack,
        ),
    )
}

@Composable
fun ServicioFormContent(state: ServicioFormUiState, acciones: AccionesServicioForm, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    BackHandler(enabled = state.hojaInsumos) { acciones.onMostrarInsumos(false) }
    val atras = rememberSalidaProtegida(state.sinGuardar && !state.hojaInsumos, acciones.onBack)
    Scaffold(
        topBar = { SpviTopBar(title = if (state.esNuevo) "Nuevo servicio" else "Editar servicio", onBack = atras) },
        bottomBar = {
            if (!state.noEncontrado && !state.cargando) {
                SpviBarraAcciones {
                    SpviPrimaryButton(
                        if (state.esNuevo) "Guardar servicio" else "Guardar cambios", onClick = acciones.onGuardar, loading = state.guardando,
                        icon = SpviIcons.Guardar, modifier = Modifier.testTag(ServicioFormTags.GUARDAR),
                    )
                }
            }
        },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.cargando -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { SpviLoading() }
            state.noEncontrado -> Box(Modifier.padding(padding)) {
                SpviEmptyState(title = "Este servicio ya no existe", detail = "Puede que se haya eliminado.", ilustracion = SpviIlustracion.NoExiste) {
                    SpviSecondaryButton("Volver", icon = SpviIcons.Atras, onClick = acciones.onBack)
                }
            }
            else -> Formulario(state, acciones, Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }
    if (state.hojaInsumos) HojaInsumos(state, acciones)
}

@Composable
private fun Formulario(state: ServicioFormUiState, a: AccionesServicioForm, modifier: Modifier) {
    val f = state.form
    val err = state.errores
    Column(
        modifier.fillMaxSize().spviContentWidth().imePadding().verticalScroll(rememberScrollState()).padding(SpviSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SpviSpacing.md),
    ) {
        // 0.27.0 (T13): la foto es lo primero.
        Foto(f.fotoUri, f.nombre, state.importandoFoto, a)
        SpviSecondaryText("* Obligatorio")
        SpviTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = f.nombre, onValueChange = a.onNombre, label = "Nombre *", placeholder = "Corte de pelo",
            isError = CamposServicio.NOMBRE in err, errorText = err[CamposServicio.NOMBRE], validarAlSalir = true, forzarError = state.intentado,
            imeAction = ImeAction.Next, modifier = Modifier.fillMaxWidth().testTag(ServicioFormTags.NOMBRE),
        )
        CampoTipo(f.tipo, state.tipos, err[CamposServicio.TIPO], state.intentado, a.onTipo)
        SpviTextField(
            filtro = FiltroEntrada.DINERO,
            value = f.importe, onValueChange = a.onImporte, label = "Importe (CUP) *", placeholder = "0.00",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = CamposServicio.IMPORTE in err, errorText = err[CamposServicio.IMPORTE], validarAlSalir = true, forzarError = state.intentado,
            supportingText = "Lo que cobras cada vez", imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth().testTag(ServicioFormTags.IMPORTE),
        )
        SpviTextField(
            filtro = FiltroEntrada.TEXTO,
            // 0.24.0: una línea; obligatoria solo si ya hay otro servicio con el mismo nombre.
            value = f.descripcion, onValueChange = a.onDescripcion, label = "Descripción", placeholder = "A domicilio, 30 minutos",
            supportingText = cu.spvi.app.producto.ProductoFormLogic.ayudaDescripcion(f.descripcion),
            isError = CamposServicio.DESCRIPCION in err, errorText = err[CamposServicio.DESCRIPCION], validarAlSalir = true, forzarError = state.intentado,
            modifier = Modifier.fillMaxWidth().testTag(ServicioFormTags.DESCRIPCION),
        )
        Insumos(state, a)
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
private fun CampoTipo(valor: String, tipos: List<String>, error: String?, forzarError: Boolean, onValor: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val opciones = tipos.filter { valor.isBlank() || it.contains(valor.trim(), ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = abierto && opciones.isNotEmpty(), onExpandedChange = { abierto = it }) {
        SpviTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = valor, onValueChange = { onValor(it); abierto = true }, label = "Tipo *", placeholder = "Peluquería",
            isError = error != null, errorText = error, validarAlSalir = true, forzarError = forzarError, supportingText = "Elige o escribe uno nuevo",
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable).testTag(ServicioFormTags.TIPO),
        )
        ExposedDropdownMenu(expanded = abierto && opciones.isNotEmpty(), onDismissRequest = { abierto = false }) {
            opciones.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { onValor(t); abierto = false }) }
        }
    }
}

@Composable
private fun Foto(uri: String?, nombre: String, importando: Boolean, a: AccionesServicioForm) {
    // 0.27.0 (T13): miniatura centrada + Cámara / Galería / Quitar (solo icono).
    cu.spvi.app.common.SelectorFoto(
        uri = uri, descripcion = nombre.ifBlank { "servicio" }, importando = importando,
        onFoto = a.onFoto, onQuitar = a.onQuitarFoto, modifier = Modifier.testTag(ServicioFormTags.FOTO),
    )
}

@Composable
private fun Insumos(state: ServicioFormUiState, a: AccionesServicioForm) {
    val err = state.errores[CamposServicio.INSUMOS]
    Column(Modifier.testTag(ServicioFormTags.INSUMOS), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
        Text("Insumos que gasta (opcional)", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
        SpviSecondaryText("Por cada vez que lo prestas. Se descuentan al venderlo.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        state.form.insumos.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                SpviTextField(
                    filtro = FiltroEntrada.DECIMAL,
                    value = l.cantidad, onValueChange = { a.onCantidadInsumo(l.insumoId, it) }, label = "${l.nombre} (${l.simbolo})",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), clearable = false,
                    modifier = Modifier.weight(1f).testTag(ServicioFormTags.cantidadInsumo(l.insumoId)),
                )
                SpviIconAction(SpviIcons.Quitar, "Quitar ${l.nombre}", onClick = { a.onQuitarInsumo(l.insumoId) })
            }
        }
        if (err != null) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (state.form.insumos.isNotEmpty()) {
            SpviCard(tone = CardTone.Tonal) {
                Text(
                    "Costo de insumos: ${state.costo?.let(Money::format) ?: "—"}", style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        SpviButtonRow {
            SpviSecondaryButton("Agregar insumo", onClick = { a.onMostrarInsumos(true) }, icon = SpviIcons.AgregarALista, modifier = Modifier.testTag(ServicioFormTags.AGREGAR_INSUMO))
        }
    }
}

@Composable
private fun HojaInsumos(state: ServicioFormUiState, a: AccionesServicioForm) {
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
            lista.forEach { i -> SpviListItem(title = i.nombre, indicatorColor = null, onClick = { a.onAgregarInsumo(i) }) }
        }
    }
}
