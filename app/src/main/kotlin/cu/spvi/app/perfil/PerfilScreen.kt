package cu.spvi.app.perfil

import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviListItem
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.spvi.app.common.SecureWindow
import cu.spvi.app.common.rememberSalidaProtegida
import cu.spvi.designsystem.component.FiltroEntrada
import cu.spvi.designsystem.component.CardTone
import cu.spvi.designsystem.component.SpviButtonRow
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSnackbarHost
import cu.spvi.designsystem.component.SpviTextField
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.component.spviContentWidth
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviSpacing

object PerfilTags {
    const val MODULOS = "perfil.modulos"
    fun modulo(m: cu.spvi.domain.model.Modulo) = "perfil.modulo.${m.name}"
    const val NOMBRE = "perfil.nombre"
    const val APELLIDOS = "perfil.apellidos"
    const val CI = "perfil.ci"
    const val GUARDAR = "perfil.guardar"
}

class AccionesPerfil(
    val onEditar: ((DatosPerfilForm) -> DatosPerfilForm) -> Unit,
    val onGuardar: () -> Unit,
    val onDescartar: () -> Unit,
    /** 0.21.0 (C12). */
    val onModulo: (cu.spvi.domain.model.Modulo) -> Unit = {},
)

/**
 * Ajustes → Perfil: Nombre, Apellidos y CI. FLAG_SECURE: ni capturas ni miniatura en Recientes.
 * P28: los teléfonos y tarjetas ya no se repiten aquí; viven solo en Pago electrónico (decisión del usuario,
 * se aparta a propósito de SPVI.txt 119–123).
 */
@Composable
fun PerfilScreen(
    onBack: () -> Unit,
    viewModel: PerfilViewModel = hiltViewModel(),
) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.mensajes.collect { snackbar.showSnackbar(it) } }
    PerfilContent(
        state = state,
        onBack = onBack,
        acciones = AccionesPerfil(viewModel::editar, viewModel::guardar, viewModel::descartar, viewModel::alternarModulo),
        snackbar = snackbar,
    )
}

@Composable
fun PerfilContent(
    state: PerfilUiState,
    onBack: () -> Unit,
    acciones: AccionesPerfil,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val atras = rememberSalidaProtegida(state.sinGuardar, { acciones.onDescartar(); onBack() }, TextosPerfil.DESCARTAR_TEXTO)

    Scaffold(
        topBar = { SpviTopBar(title = TextosPerfil.TITULO, onBack = atras) },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().spviContentWidth().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(vertical = SpviSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs),
        ) {
            item(key = "intro") {
                SpviCard(tone = CardTone.Tonal, modifier = Modifier.padding(horizontal = SpviSpacing.md)) {
                    Text(TextosPerfil.EXPLICACION, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            item(key = "datos") { DatosPersonales(state, acciones) }
            state.modulos?.let { m -> item(key = "modulos") { ModulosNegocio(m, acciones) } }
        }
    }
}

@Composable
private fun DatosPersonales(state: PerfilUiState, acciones: AccionesPerfil) {
    val f = state.form
    SpviCard(modifier = Modifier.padding(horizontal = SpviSpacing.md)) {
        SpviTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = f.nombre, onValueChange = { v -> acciones.onEditar { it.copy(nombre = v) } },
            label = "Nombre", leadingIcon = SpviIcons.Perfil,
            isError = f.errorNombre != null, errorText = f.errorNombre, validarAlSalir = true, forzarError = f.mostrarErrores,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(PerfilTags.NOMBRE),
        )
        SpviTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = f.apellidos, onValueChange = { v -> acciones.onEditar { it.copy(apellidos = v) } },
            label = "Apellidos",
            isError = f.errorApellidos != null, errorText = f.errorApellidos, validarAlSalir = true, forzarError = f.mostrarErrores,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(PerfilTags.APELLIDOS),
        )
        SpviTextField(
            filtro = FiltroEntrada.CARNE,
            value = f.ci, onValueChange = { v -> acciones.onEditar { it.copy(ci = v) } },
            label = "Carnet de identidad (CI)", placeholder = "85010112345",
            isError = f.errorCi != null, errorText = f.errorCi, validarAlSalir = true, forzarError = f.mostrarErrores,
            supportingText = "Solo se envía al pedir la licencia",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            imeAction = ImeAction.Done, onImeAction = { if (state.sinGuardar) acciones.onGuardar() },
            modifier = Modifier.testTag(PerfilTags.CI),
        )
        SpviButtonRow {
            SpviPrimaryButton(
                "Guardar", onClick = acciones.onGuardar, enabled = state.sinGuardar, loading = state.guardando,
                icon = SpviIcons.Guardar, modifier = Modifier.testTag(PerfilTags.GUARDAR),
            )
        }
    }
}

/** 0.21.0 (C12): «Tu negocio»: qué secciones usa SPVI. Lo desmarcado desaparece de la barra inferior, Inicio y Registros. */
@Composable
private fun ModulosNegocio(modulos: Set<cu.spvi.domain.model.Modulo>, acciones: AccionesPerfil) {
    SpviCard(title = TextosPerfil.MODULOS_TITULO, modifier = Modifier.padding(horizontal = SpviSpacing.md).testTag(PerfilTags.MODULOS)) {
        SpviSecondaryText(TextosPerfil.MODULOS_EXPLICACION)
        cu.spvi.domain.model.Modulo.entries.forEach { m ->
            val marcado = m in modulos
            // No se puede desmarcar el último (la app nunca se queda sin secciones).
            val bloqueado = marcado && cu.spvi.domain.model.Modulo.alternar(modulos, m).isEmpty()
            SpviListItem(
                title = m.etiqueta, subtitle = m.detalle, indicatorColor = null, selected = marcado,
                leading = { androidx.compose.material3.Checkbox(checked = marcado, onCheckedChange = null, enabled = !bloqueado) },
                onClick = if (bloqueado) null else ({ acciones.onModulo(m) }),
                modifier = Modifier.testTag(PerfilTags.modulo(m)),
            )
        }
    }
}
