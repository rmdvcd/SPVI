package cu.spvi.app.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cu.spvi.app.BuildConfig
import cu.spvi.app.common.abrirAjustesDeLaApp
import cu.spvi.app.navigation.Route
import cu.spvi.designsystem.component.SpviBadge
import cu.spvi.designsystem.component.SpviDialog
import cu.spvi.designsystem.component.SpviListItem
import cu.spvi.designsystem.component.SpviTopBar
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviSpacing
import cu.spvi.domain.model.PasoConfiguracion
import cu.spvi.domain.model.ResumenConfiguracion
import cu.spvi.domain.repository.PreferenciasRepository
import cu.spvi.domain.repository.PrincipalRepository
import cu.spvi.domain.repository.SecundariaRepository
import cu.spvi.domain.repository.TipoAppRepository
import cu.spvi.domain.model.PermisosApp
import cu.spvi.app.vinculacion.VinculacionLogic
import cu.spvi.domain.usecase.ObservarResumenConfiguracion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AjustesUiState(
    val resumen: ResumenConfiguracion? = null,
    /** P37: en una secundaria se ocultan los ajustes que pertenecen a la principal (licencia, perfil, respaldo…). */
    val permisos: PermisosApp = PermisosApp.PRINCIPAL,
    val secundarias: Int = 0,
    val nombreNegocio: String = "",
)

@HiltViewModel
class AjustesViewModel @Inject constructor(
    observarResumen: ObservarResumenConfiguracion,
    private val preferenciasRepo: PreferenciasRepository,
    tipoRepo: TipoAppRepository,
    principalRepo: PrincipalRepository,
    secundariaRepo: SecundariaRepository,
) : ViewModel() {

    val state: StateFlow<AjustesUiState> = combine(
        observarResumen(), preferenciasRepo.preferencias, tipoRepo.permisos, principalRepo.observarEmpleados(), secundariaRepo.estado,
    ) { r, p, permisos, empleados, sec ->
        AjustesUiState(r, permisos, empleados.size, sec.nombreNegocio)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AjustesUiState())
}

/** Subtítulo de "Completar configuración" (pura, testeada en AjustesTextosTest). */
fun ResumenConfiguracion?.subtitulo(): String = when {
    this == null -> "Cargando…"
    completa -> "Todo listo"
    pendientes.size == 1 -> "Falta 1 paso"
    else -> "Faltan ${pendientes.size} pasos"
}

object AjustesTags {
    const val CONFIGURACION = "ajustes.configuracion"
    const val PAGO = "ajustes.pago"
    const val RESPALDO = "ajustes.respaldo"
    const val MIGRAR = "ajustes.migrar"
    const val AYUDA = "ajustes.ayuda"
    const val SOPORTE = "ajustes.soporte"
    const val VINCULACION = "ajustes.vinculacion"
    const val ACTUALIZACIONES = "ajustes.actualizaciones"
    const val ACTUALIZACION_ATRASADA = "ajustes.actualizacion_atrasada"
    const val SEED = "ajustes.seed"
}

/** Ajustes: configuración pendiente, Perfil, Licencia, Pago electrónico, Precios, avisos, consultas, permisos, Respaldo, Migrar, Ayuda y Soporte. */
@Composable
fun AjustesScreen(onNavigate: (Route) -> Unit, viewModel: AjustesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 0.26.0 (§6): sin interruptor (la consulta semanal es fija); queda «Buscar ahora».
    val actVm: cu.spvi.app.actualizacion.ActualizacionViewModel = hiltViewModel()
    val actualizacion by actVm.estado.collectAsStateWithLifecycle()
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(actVm) { actVm.eventos.collect { snackbar.showSnackbar(it) } }
    // 0.27.0 (T11): acceso con clave (este teléfono); cambiarlo pide antes la huella o el PIN.
    val accesoVm: cu.spvi.app.acceso.AccesoViewModel = hiltViewModel()
    val accesoClave by accesoVm.activo.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val cambiarAcceso = cu.spvi.app.acceso.rememberCambiarAccesoClave(
        guardar = { accesoVm.guardar(it) },
        mensaje = { m -> scope.launch { snackbar.showSnackbar(m) } },
    )
    AjustesContent(
        accesoClave = accesoClave,
        onAccesoClave = cambiarAcceso,
        state = state,
        onNavigate = onNavigate,
        onPermisos = { abrirAjustesDeLaApp(context) },
        mostrarCatalogo = BuildConfig.DEBUG,
        mostrarSeed = BuildConfig.DEBUG,
        actualizacion = actualizacion,
        onBuscarAhora = actVm::buscarAhora,
        snackbar = snackbar,
    )
}

@Composable
fun AjustesContent(
    state: AjustesUiState,
    onNavigate: (Route) -> Unit,
    onPermisos: () -> Unit,
    mostrarCatalogo: Boolean = false,
    mostrarSeed: Boolean = false,
    actualizacion: cu.spvi.app.actualizacion.EstadoActualizacion = cu.spvi.app.actualizacion.EstadoActualizacion(),
    onBuscarAhora: () -> Unit = {},
    snackbar: androidx.compose.material3.SnackbarHostState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() },
    accesoClave: Boolean = false,
    onAccesoClave: (Boolean) -> Unit = {},
) {
    var dialogoActualizaciones by rememberSaveable { mutableStateOf(false) }
    var dialogoSeed by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { SpviTopBar(title = "Ajustes") },
        snackbarHost = { cu.spvi.designsystem.component.SpviSnackbarHost(snackbar) },
    ) { padding ->
        val principal = !state.permisos.esSecundaria
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (principal) item {
                val pendientes = state.resumen?.pendientes?.size ?: 0
                Entry(
                    "Completar configuración", state.resumen.subtitulo(), SpviIcons.Configurar,
                    modifier = Modifier.testTag(AjustesTags.CONFIGURACION),
                    badge = pendientes.takeIf { it > 0 },
                ) { onNavigate(Route.ConfiguracionInicial()) }
            }
            if (principal) item { Entry("Perfil", "Nombre, apellidos y carné", SpviIcons.Perfil) { onNavigate(Route.Perfil) } }
            if (principal) item { Entry("Licencia", "Estado, solicitud y activación", SpviIcons.Licencia) { onNavigate(Route.Licencia) } }
            // P37: Sistema, debajo de Licencia.
            item {
                Entry(
                    VinculacionLogic.TITULO, VinculacionLogic.subtituloAjustes(state.permisos.tipo, state.secundarias, state.nombreNegocio),
                    SpviIcons.Vinculacion, modifier = Modifier.testTag(AjustesTags.VINCULACION),
                ) { onNavigate(Route.Vinculacion) }
            }
            if (principal) item {
                Entry("Pago electrónico", "Teléfonos y cuentas para cobrar por transferencia", SpviIcons.PagoElectronico, modifier = Modifier.testTag(AjustesTags.PAGO)) {
                    onNavigate(Route.PagoElectronico)
                }
            }
            if (state.permisos.cambiarPrecios) item { Entry("Precios", "Ajustes de precio por pago o por importe", SpviIcons.Precios) { onNavigate(Route.Precios) } }
            if (principal) item {
                Entry("Avisos de inventario", "Cuándo avisar de stock bajo o crítico", SpviIcons.Alerta) {
                    onNavigate(Route.ConfiguracionInicial(PasoConfiguracion.ALERTAS.name))
                }
            }
            item { Entry("Permisos del teléfono", "Cámara: se pide solo al vincular o al hacer una foto", SpviIcons.Camara, onClick = onPermisos) }
            item { cu.spvi.app.acceso.FilaAccesoClave(accesoClave, onAccesoClave) } // 0.27.0 (T11)
            if (principal) item {
                Entry("Respaldo", "Copia cifrada de todo e importar", SpviIcons.Respaldo, modifier = Modifier.testTag(AjustesTags.RESPALDO)) {
                    onNavigate(Route.Respaldo)
                }
            }
            if (principal) item {
                Entry("Migrar a otro teléfono", "Pasar datos y licencia, con autorización del desarrollador", SpviIcons.Migrar, modifier = Modifier.testTag(AjustesTags.MIGRAR)) {
                    onNavigate(Route.Migrar)
                }
            }
            item {
                Entry(
                    cu.spvi.app.actualizacion.TextosActualizacion.BUSCAR,
                    when {
                        actualizacion.version != null -> "Versión ${actualizacion.version} disponible"
                        else -> "Versión ${BuildConfig.VERSION_NAME}"
                    },
                    SpviIcons.Sincronizar, modifier = Modifier.testTag(AjustesTags.ACTUALIZACIONES),
                ) { dialogoActualizaciones = true }
            }
            item { Entry("Ayuda", "Manual breve de uso", SpviIcons.Ayuda, modifier = Modifier.testTag(AjustesTags.AYUDA)) { onNavigate(Route.Ayuda) } }
            item { Entry("Soporte", "Datos de contacto del desarrollador", SpviIcons.Soporte, modifier = Modifier.testTag(AjustesTags.SOPORTE)) { onNavigate(Route.Soporte) } }
            if (mostrarCatalogo) {
                item { Entry("Design system", "Catálogo de componentes (solo debug)", SpviIcons.Ayuda) { onNavigate(Route.Catalogo) } }
            }
            if (mostrarSeed) {
                item {
                    Entry(
                        "Generar datos de prueba", "Borra todo y genera la bodega de prueba (solo debug)", SpviIcons.Reiniciar,
                        modifier = Modifier.testTag(AjustesTags.SEED),
                    ) { dialogoSeed = true }
                }
            }
        }
    }
    if (dialogoActualizaciones) {
        val T = cu.spvi.app.actualizacion.TextosActualizacion
        SpviDialog(
            title = T.BUSCAR,
            onDismiss = { dialogoActualizaciones = false },
            onConfirm = null,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                    Text(T.BUSCAR_DETALLE, style = MaterialTheme.typography.bodyMedium)
                    if (!actualizacion.repoConfigurado) {
                        cu.spvi.designsystem.component.SpviSecondaryText(T.SIN_REPO, maxLines = 3)
                    } else {
                        cu.spvi.designsystem.component.SpviSecondaryText(
                            actualizacion.ultimaComprobacion?.let { T.ultimaComprobacion(it) } ?: T.SIN_COMPROBACION_CORRECTA,
                            maxLines = 2,
                        )
                        if (actualizacion.avisoSinComprobar) {
                            Text(
                                T.avisoSinComprobar(actualizacion.ultimaComprobacion, actualizacion.diasSinComprobar),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag(AjustesTags.ACTUALIZACION_ATRASADA),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        cu.spvi.designsystem.component.SpviPrimaryButton(
                            T.BUSCAR_AHORA, onClick = onBuscarAhora, icon = SpviIcons.Sincronizar, loading = actualizacion.buscando,
                            enabled = !actualizacion.buscando,
                        )
                    }
                }
            },
        )
    }
    if (dialogoSeed && mostrarSeed) {
        val seedVm: cu.spvi.app.ajustes.seed.SeedViewModel = hiltViewModel()
        cu.spvi.app.ajustes.seed.DialogoSeed(seedVm) { dialogoSeed = false }
    }
}

@Composable
private fun Entry(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    badge: Int? = null,
    onClick: () -> Unit,
) {
    SpviListItem(
        title = title,
        subtitle = subtitle,
        indicatorColor = null,
        leading = { Icon(icon, contentDescription = null) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                badge?.let { SpviBadge(count = it) }
                Icon(SpviIcons.Abrir, contentDescription = null)
            }
        },
        onClick = onClick,
        modifier = modifier,
    )
}

