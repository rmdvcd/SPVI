package cu.spvi.app.licencia

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.spvi.app.common.Contacto
import cu.spvi.app.common.SecureWindow
import cu.spvi.core.money.Money
import cu.spvi.designsystem.component.FiltroEntrada
import cu.spvi.designsystem.component.CardTone
import cu.spvi.designsystem.component.IconActionStyle
import cu.spvi.designsystem.component.SpviButtonRow
import cu.spvi.designsystem.component.SpviCard
import cu.spvi.designsystem.component.SpviChip
import cu.spvi.designsystem.component.SpviIconAction
import cu.spvi.designsystem.component.SpviLoading
import cu.spvi.designsystem.component.SpviPrimaryButton
import cu.spvi.designsystem.component.SpviSecondaryButton
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviSnackbarHost
import cu.spvi.designsystem.component.SpviStepper
import cu.spvi.designsystem.component.SpviTextButton
import cu.spvi.designsystem.component.SpviTextField
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.component.spviContentWidth
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.designsystem.theme.SpviTextos
import cu.spvi.designsystem.token.SpviSpacing
import cu.spvi.domain.model.Licencia
import cu.spvi.licencia.contract.TipoLicencia
import cu.spvi.licencia.contract.Via
import java.time.Instant

/** Acciones del panel (agrupadas para que [LicenciaContent] sea sin estado y testeable sin Hilt). */
class LicenciaAcciones(
    val onBack: (() -> Unit)?,
    val onEditar: ((LicenciaForm) -> LicenciaForm) -> Unit,
    val onAbrirEdicion: () -> Unit,
    val onCerrarEdicion: () -> Unit,
    val onSolicitar: () -> Unit,
    val onActivar: () -> Unit,
    val onPegar: () -> Unit,
    /** Solo en el bloqueo: abre Soporte desde el aviso (fuera del bloqueo, Soporte está en Ajustes). */
    val onSoporte: (() -> Unit)? = null,
    /** 0.22.0 (L-c): leer el QR de la licencia con la cámara o desde una imagen. */
    /** 0.22.0 (L-e): mismo tipo y secundarias que la instalada; true = datos válidos (salta a «Pide la licencia»). */
    val onRenovarIgual: () -> Boolean = { false },
)

/**
 * Panel de Licencia (con estado). Única pantalla operativa durante el bloqueo (con Soporte).
 * FLAG_SECURE: muestra CI y teléfono.
 */
@Composable
fun LicenciaScreen(
    onBack: (() -> Unit)?,
    bloqueada: Boolean = false,
    onSoporte: (() -> Unit)? = null,
    viewModel: LicenciaViewModel = hiltViewModel(),
) {
    SecureWindow()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // 0.25.0: mensaje de licencia compartido con SPVI → se rellena y se activa sin pegarlo (también en el bloqueo).
    val entradaVm: cu.spvi.app.common.EntradaViewModel = hiltViewModel()
    val licenciaCompartida by entradaVm.licenciaPendiente.collectAsStateWithLifecycle()
    LaunchedEffect(licenciaCompartida, state.licencia != null) {
        if (licenciaCompartida && state.licencia != null) entradaVm.tomarLicencia()?.let { texto ->
            viewModel.editar { it.copy(mensajeLicencia = texto) }
            viewModel.activarLicencia()
        }
    }
    // 0.25.0 (§3): tras importar un respaldo con licencia, el ID a recuperar ya viene escrito.
    val recuperacionVm: RecuperacionViewModel = hiltViewModel()
    val recuperable by recuperacionVm.recuperable.collectAsStateWithLifecycle()
    LaunchedEffect(recuperable, state.licencia?.instalada) {
        val r = recuperable
        if (r != null && state.licencia?.permiteRecuperar() == true && state.form.recupera.isBlank()) viewModel.editar { it.copy(recupera = r.id) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is LicenciaEvento.Mensaje -> snackbar.showSnackbar(e.texto)
                is LicenciaEvento.Enviar -> {
                    // 0.23.1: solo texto por los dos canales (sin imágenes que manejar en el teléfono).
                    val ok = when (e.via) {
                        Via.WHATSAPP -> Contacto.whatsapp(context, e.texto)
                        Via.SMS -> Contacto.sms(context, e.texto)
                    }
                    if (!ok) snackbar.showSnackbar("No hay una app disponible para enviar el mensaje")
                }
            }
        }
    }

    Scaffold(
        topBar = { SpviTopBar(title = "Licencia", onBack = onBack) },
        snackbarHost = { SpviSnackbarHost(snackbar) },
    ) { padding ->
        LicenciaContent(
            state = state,
            ahora = remember(state.licencia) { Instant.now() },
            bloqueada = bloqueada,
            acciones = LicenciaAcciones(
                onBack = onBack,
                onEditar = viewModel::editar,
                onAbrirEdicion = viewModel::abrirEdicion,
                onCerrarEdicion = viewModel::cerrarEdicion,
                onSolicitar = viewModel::solicitarLicencia,
                onActivar = viewModel::activarLicencia,
                onPegar = { clipboard.getText()?.text?.let { t -> viewModel.editar { it.copy(mensajeLicencia = t) } } },
                onSoporte = onSoporte,
                onRenovarIgual = viewModel::renovarIgual,
            ),
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

object LicenciaTags {
    const val SECUNDARIAS = "licencia.secundarias"
    const val SECUNDARIAS_MENOS = "licencia.secundarias_menos"
    const val SECUNDARIAS_MAS = "licencia.secundarias_mas"
    const val PRECIO_TOTAL = "licencia.precio_total"
    const val AVISO_BLOQUEO = "licencia.aviso_bloqueo"
    const val SOPORTE = "licencia.soporte"
    const val BOTON_SOLICITAR = "licencia.solicitar"
    const val BOTON_ACTIVAR = "licencia.activar"
    const val RESUMEN_DATOS = "licencia.resumen"
    const val FORM_DATOS = "licencia.form"
    const val STEPPER = "licencia.stepper"
    const val YA_TENGO = "licencia.ya_tengo"
    const val RENOVAR_IGUAL = "licencia.renovar_igual"
    const val SIN_PERDER_DIAS = "licencia.sin_perder_dias"
    const val RECUPERA = "licencia.recupera"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LicenciaContent(
    state: LicenciaUiState,
    ahora: Instant,
    bloqueada: Boolean,
    acciones: LicenciaAcciones,
    modifier: Modifier = Modifier,
) {
    val lic = state.licencia ?: return SpviLoading(modifier)
    val form = state.form
    val accion = lic.accionSolicitud()
    Column(
        modifier.fillMaxSize().spviContentWidth().imePadding().verticalScroll(rememberScrollState()).padding(SpviSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SpviSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally, // P28: lo que va solo en su fila, centrado
    ) {
        if (bloqueada) {
            SpviCard(tone = CardTone.Tonal, modifier = Modifier.testTag(LicenciaTags.AVISO_BLOQUEO)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                    Icon(SpviIcons.Bloqueo, contentDescription = null)
                    Text("SPVI está bloqueado hasta que actives una licencia válida.", style = MaterialTheme.typography.bodyMedium)
                }
                SpviSecondaryText("Tus datos siguen guardados.")
                acciones.onSoporte?.let { soporte ->
                    SpviButtonRow { SpviTextButton("Contactar soporte", icon = SpviIcons.Soporte, onClick = soporte, modifier = Modifier.testTag(LicenciaTags.SOPORTE)) }
                }
            }
        }

        EstadoLicenciaCard(lic, ahora)
        var paso by rememberSaveable { mutableStateOf(pasoInicialLicencia(form)) }
        // 0.22.0 (L-e): renovar con lo mismo en un toque (tipo y secundarias de la instalada).
        val instalada = lic.instalada
        if (lic.renovable() && instalada != null && paso != PasoLicencia.SOLICITAR) {
            SpviSecondaryButton(
                text = TextosRenovacion.BOTON, icon = SpviIcons.Reintentar,
                onClick = { paso = if (acciones.onRenovarIgual()) PasoLicencia.SOLICITAR else PasoLicencia.DATOS },
                modifier = Modifier.testTag(LicenciaTags.RENOVAR_IGUAL),
            )
            SpviSecondaryText(
                SpviTextos.resaltar(TextosRenovacion.detalle(instalada), Money.cup(instalada.tipo.precio(instalada.secundarias))),
                textAlign = TextAlign.Center,
            )
        }

        lic.avisos().forEach { aviso ->
            SpviCard(tone = CardTone.Tonal) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                    Icon(SpviIcons.Alerta, contentDescription = null)
                    Text(aviso.titulo, style = MaterialTheme.typography.titleSmall)
                }
                Text(aviso.detalle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        SpviStepper(
            actual = paso.ordinal + 1, total = PasoLicencia.entries.size, titulo = paso.titulo,
            onAtras = paso.anterior?.let { a -> { paso = a } },
            onSiguiente = paso.siguiente?.let { s ->
                {
                    if (puedeAvanzar(paso, form)) {
                        if (paso == PasoLicencia.DATOS && form.editando) acciones.onCerrarEdicion()
                        paso = s
                    } else {
                        acciones.onEditar { it.copy(mostrarErrores = true, editando = true) }
                    }
                }
            },
            modifier = Modifier.testTag(LicenciaTags.STEPPER),
        )

        when (paso) {
            PasoLicencia.DATOS -> DatosCard(state, acciones)
            PasoLicencia.SOLICITAR -> {
                // P28: sin tarjeta «Costos»: los chips de tipo ya muestran cada precio (y el actual lo dice el estado).
                SpviCard(title = accion.titulo) {
                    if (accion == AccionSolicitud.NINGUNA) {
                        SpviSecondaryText("Tu licencia no vence. No necesitas renovarla.")
                    } else {
                        SpviSecondaryText("Tipo de licencia")
                        // 0.28.0: un tipo debajo del otro, a todo lo ancho.
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                            TipoLicencia.entries.forEach { t ->
                                SpviChip(
                                    label = t.etiqueta, supportingLabel = Money.cup(t.precioCup), supportingEsDato = true,
                                    selected = form.tipo == t, onClick = { acciones.onEditar { it.copy(tipo = t) } },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        // 0.21.0 (C4): cuántas apps secundarias (empleados) cubre; cada una suma un importe fijo.
                        SelectorSecundarias(form, acciones)
                        // 0.25.0 (§3): recuperar la licencia de otro teléfono; 0.25.1 (C): también con la instalada vencida.
                        if (lic.permiteRecuperar()) {
                            SpviSecondaryText(TextosRecuperacion.TITULO)
                            SpviTextField(
                                filtro = FiltroEntrada.LIBRE,
                                value = form.recupera, onValueChange = { v -> acciones.onEditar { it.copy(recupera = v.take(200)) } },
                                label = TextosRecuperacion.CAMPO, supportingText = TextosRecuperacion.DETALLE,
                                isError = !form.recuperaOk, errorText = TextosRecuperacion.ID_INVALIDO,
                                modifier = Modifier.fillMaxWidth().testTag(LicenciaTags.RECUPERA),
                            )
                        }
                        SpviSecondaryText("Enviar por")
                        SpviButtonRow {
                            SpviIconAction(
                                SpviIcons.WhatsApp, "Enviar por WhatsApp", onClick = { acciones.onEditar { it.copy(via = Via.WHATSAPP) } },
                                selected = form.via == Via.WHATSAPP,
                                containerColor = SpviTheme.colors.whatsapp.takeIf { form.via != Via.WHATSAPP },
                            )
                            SpviIconAction(
                                SpviIcons.Sms, "Enviar por SMS", onClick = { acciones.onEditar { it.copy(via = Via.SMS) } },
                                selected = form.via == Via.SMS, style = IconActionStyle.Tonal,
                            )
                        }
                        SpviSecondaryText(
                            if (form.via == Via.WHATSAPP) "Se abrirá WhatsApp con tu solicitud de licencia."
                            else "Se abrirá Mensajes con tu solicitud de licencia.",
                        )
                        if (lic.renovable()) {
                            SpviSecondaryText(
                                TextosRenovacion.SIN_PERDER_DIAS, textAlign = TextAlign.Center,
                                modifier = Modifier.testTag(LicenciaTags.SIN_PERDER_DIAS),
                            )
                        }
                        SpviButtonRow {
                            SpviPrimaryButton(
                                text = accion.boton, icon = SpviIcons.Enviar, onClick = acciones.onSolicitar,
                                enabled = lic.puedeSolicitar, loading = state.ocupado,
                                modifier = Modifier.testTag(LicenciaTags.BOTON_SOLICITAR),
                            )
                        }
                    }
                }
                if (accion != AccionSolicitud.NINGUNA) {
                    SpviSecondaryText("Cuando recibas la respuesta, actívala en el paso siguiente.", textAlign = TextAlign.Center)
                }
            }
            PasoLicencia.ACTIVAR -> {
                SpviCard(title = "Activar licencia") {
                    SpviSecondaryText(TextosActivacion.INSTRUCCION, textAlign = TextAlign.Center)
                    SpviTextField(
                        filtro = FiltroEntrada.LIBRE,
                        value = form.mensajeLicencia, onValueChange = { v -> acciones.onEditar { it.copy(mensajeLicencia = v) } },
                        label = "Mensaje de licencia", singleLine = false, minLines = 3, maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SpviButtonRow {
                        SpviIconAction(SpviIcons.Pegar, "Pegar", style = IconActionStyle.Tonal, onClick = acciones.onPegar)
                        SpviPrimaryButton(
                            text = "Activar", icon = SpviIcons.Confirmar, onClick = acciones.onActivar,
                            enabled = form.mensajeLicencia.isNotBlank() && lic.validacionDisponible, loading = state.ocupado,
                            modifier = Modifier.testTag(LicenciaTags.BOTON_ACTIVAR),
                        )
                    }
                }
            }
        }
        if (paso != PasoLicencia.ACTIVAR) {
            SpviTextButton(
                "Ya tengo el mensaje de licencia", icon = SpviIcons.Licencia, onClick = { paso = PasoLicencia.ACTIVAR },
                modifier = Modifier.testTag(LicenciaTags.YA_TENGO),
            )
        }
    }
}

@Composable
private fun DatosCard(state: LicenciaUiState, acciones: LicenciaAcciones) {
    val form = state.form
    SpviCard(title = "Tus datos") {
        if (!form.editando) {
            Column(Modifier.testTag(LicenciaTags.RESUMEN_DATOS), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${form.nombre} ${form.apellidos}".trim(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                SpviSecondaryText("CI: ${form.ci}")
                SpviSecondaryText("Teléfono: ${form.telefono}")
                SpviSecondaryText("De tu Perfil y de Pago electrónico.") // P28: los teléfonos ya no están en Perfil
                SpviButtonRow { SpviTextButton(text = "Editar", icon = SpviIcons.Editar, onClick = acciones.onAbrirEdicion) }
            }
        } else {
            DatosForm(state, acciones)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DatosForm(state: LicenciaUiState, acciones: LicenciaAcciones) {
    val form = state.form
    val err = form.mostrarErrores
    Column(Modifier.testTag(LicenciaTags.FORM_DATOS), verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.perfilVacio) SpviSecondaryText("Tu Perfil está vacío: estos datos se guardarán también en él al solicitar.")
        SpviTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = form.nombre, onValueChange = { v -> acciones.onEditar { it.copy(nombre = v) } },
            imeAction = ImeAction.Next,
            label = "Nombre", isError = !form.nombreOk, errorText = "Solo letras, 2 a 80", validarAlSalir = true, forzarError = err,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        SpviTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = form.apellidos, onValueChange = { v -> acciones.onEditar { it.copy(apellidos = v) } },
            imeAction = ImeAction.Next,
            label = "Apellidos", isError = !form.apellidosOk, errorText = "Solo letras, 2 a 80", validarAlSalir = true, forzarError = err,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        SpviTextField(
            filtro = FiltroEntrada.CARNE,
            value = form.ci, onValueChange = { v -> acciones.onEditar { it.copy(ci = v) } },
            imeAction = ImeAction.Next,
            label = "Carné de identidad", isError = !form.ciOk, errorText = "5 a 20 letras o dígitos", validarAlSalir = true, forzarError = err,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.telefonosPerfil.isNotEmpty()) {
            SpviSecondaryText("Tus teléfonos")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs, Alignment.CenterHorizontally)) {
                state.telefonosPerfil.forEach { n ->
                    SpviChip(label = n, selected = form.telefonoSeleccionado(n), onClick = { acciones.onEditar { it.conTelefono(n) } })
                }
            }
        }
        SpviTextField(
            filtro = FiltroEntrada.TELEFONO,
            value = form.telefono, onValueChange = { v -> acciones.onEditar { it.conTelefono(v) } },
            imeAction = ImeAction.Done,
            label = "Teléfono", placeholder = "5XXXXXXX", isError = !form.telefonoOk, errorText = "Número no válido", validarAlSalir = true, forzarError = err,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.perfilVacio) {
            SpviSecondaryButton(text = "Listo", icon = SpviIcons.Listo, onClick = acciones.onCerrarEdicion)
        }
    }
}

/** 0.21.0 (C4): selector − [n] + de secundarias con el precio total actualizado al momento. */
@Composable
private fun SelectorSecundarias(form: LicenciaForm, acciones: LicenciaAcciones) {
    SpviSecondaryText(TextosLicencia.SECUNDARIAS)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SpviIconAction(
            SpviIcons.Quitar, "Una secundaria menos", onClick = { acciones.onEditar { it.conSecundarias(it.secundarias - 1) } },
            enabled = form.secundarias > 0, style = IconActionStyle.Tonal, modifier = Modifier.testTag(LicenciaTags.SECUNDARIAS_MENOS),
        )
        Text(
            form.secundarias.toString(), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag(LicenciaTags.SECUNDARIAS).semantics { contentDescription = TextosLicencia.secundarias(form.secundarias) },
        )
        SpviIconAction(
            SpviIcons.Agregar, "Una secundaria más", onClick = { acciones.onEditar { it.conSecundarias(it.secundarias + 1) } },
            enabled = form.secundarias < cu.spvi.licencia.contract.GlContract.SECUNDARIAS_MAX, style = IconActionStyle.Tonal,
            modifier = Modifier.testTag(LicenciaTags.SECUNDARIAS_MAS),
        )
    }
    SpviSecondaryText(
        // 0.28.0: los importes («Total: 1 500.00 CUP…») van en seminegrita.
        SpviTextos.resaltar(
            TextosLicencia.precioTotal(form),
            Money.cup(form.tipo.precioCup), Money.cup(form.precio), Money.cup(form.tipo.precioSecundariaCup),
        ),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag(LicenciaTags.PRECIO_TOTAL),
    )
}

/** 0.21.0 (C4): textos del selector de secundarias (puros, probados en JVM). */
object TextosLicencia {
    const val SECUNDARIAS = "Apps secundarias (empleados)"
    fun secundarias(n: Int): String = when (n) { 0 -> "Sin apps secundarias"; 1 -> "1 app secundaria"; else -> "$n apps secundarias" }
    fun precioTotal(f: LicenciaForm): String =
        if (f.secundarias == 0) "Total: ${Money.cup(f.tipo.precioCup)}"
        else "Total: ${Money.cup(f.precio)} (${Money.cup(f.tipo.precioCup)} + ${f.secundarias} × ${Money.cup(f.tipo.precioSecundariaCup)})"
}
