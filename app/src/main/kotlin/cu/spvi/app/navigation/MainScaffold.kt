package cu.spvi.app.navigation

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import cu.spvi.app.ajustes.AjustesScreen
import cu.spvi.app.ayuda.AyudaScreen
import cu.spvi.app.catalogo.CatalogoScreen
import cu.spvi.app.common.EntradaViewModel
import cu.spvi.app.insumos.InsumoFormScreen
import cu.spvi.app.servicios.ServicioFormScreen
import cu.spvi.app.servicios.ServiciosScreen
import cu.spvi.app.servicios.ServiciosViewModel
import cu.spvi.app.vinculacion.qr.QrVinculacionScreen
import cu.spvi.app.inicio.InicioScreen
import cu.spvi.app.inicio.TipoVenta
import cu.spvi.app.inventario.InventarioScreen
import cu.spvi.app.inventario.InventarioViewModel
import cu.spvi.app.inventario.SeleccionVenta
import cu.spvi.app.licencia.LicenciaScreen
import cu.spvi.app.vinculacion.VinculacionScreen
import cu.spvi.app.migrar.MigrarScreen
import cu.spvi.app.onboarding.ModoWizard
import cu.spvi.app.onboarding.OnboardingScreen
import cu.spvi.app.pagos.PagoElectronicoScreen
import cu.spvi.app.perfil.PerfilScreen
import cu.spvi.app.precios.PreciosScreen
import cu.spvi.app.producto.ProductoFormScreen
import cu.spvi.app.registros.RegistrosScreen
import cu.spvi.app.registros.TurnoDetalleScreen
import cu.spvi.app.respaldo.RespaldoScreen
import cu.spvi.app.soporte.SoporteScreen
import cu.spvi.app.venta.VentaScreen
import cu.spvi.designsystem.component.SpviNavItem
import cu.spvi.designsystem.component.SpviNavigationBar
import cu.spvi.designsystem.token.SpviMotion
import cu.spvi.domain.model.Licencia
import cu.spvi.domain.model.PasoConfiguracion

@Composable
fun MainScaffold(snapshot: Licencia) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination
    // Inventario/Servicios en modo «elegir qué vender» son pantallas completas (sin barra inferior).
    val enSeleccionVenta = entry?.enSeleccionVenta() == true
    // P37: permisos de esta app (todo en la principal; lo que permitió el dueño en una secundaria).
    val permisosVm: cu.spvi.app.common.PermisosAppViewModel = hiltViewModel()
    val permisos by permisosVm.permisos.collectAsStateWithLifecycle()
    // 0.21.0 (C12): solo las secciones de los módulos del negocio.
    val secciones = remember(permisos.modulos) { topLevelVisibles(permisos) }
    val selected = secciones.firstOrNull { current.isIn(it.route) }?.takeUnless { enSeleccionVenta }
    val items = remember(secciones) { secciones.map { SpviNavItem(it.name, it.icon, it.label) } }
    // P29: deslizar a izquierda/derecha cambia de sección.
    val bloqueoGestos = remember { cu.spvi.designsystem.component.BloqueoGestos() }
    val enRutaRaiz = current != null && TopLevel.entries.any { current.hasRoute(it.route::class) }
    val deslizar = deslizarPermitido(enRutaRaiz, enSeleccionVenta, bloqueoGestos.activo)

    // Prompt 14: un .spvi abierto/compartido desde otra app → Respaldo (lo consume su ViewModel).
    val entradaVm: EntradaViewModel = hiltViewModel()
    val archivoPendiente by entradaVm.archivoPendiente.collectAsStateWithLifecycle()
    LaunchedEffect(archivoPendiente) {
        if (archivoPendiente && nav.currentDestination?.hasRoute(Route.Respaldo::class) != true) {
            nav.navigate(Route.Respaldo) { launchSingleTop = true }
        }
    }
    // 0.25.0: un mensaje de licencia compartido con SPVI abre Licencia, que lo activa sola.
    val licenciaPendiente by entradaVm.licenciaPendiente.collectAsStateWithLifecycle()
    LaunchedEffect(licenciaPendiente) {
        if (licenciaPendiente && nav.currentDestination?.hasRoute(Route.Licencia::class) != true) {
            nav.navigate(Route.Licencia) { launchSingleTop = true }
        }
    }

    // 0.26.0 (P73 §6): plazo de la actualización vencido → bloqueo de toda la app. Nunca durante una venta, la
    // vinculación por QR ni en Respaldo (salida «Exportar respaldo», suposición 3).
    val bloqueoVm: cu.spvi.app.actualizacion.ActualizacionViewModel = hiltViewModel()
    val actualizacion by bloqueoVm.estado.collectAsStateWithLifecycle()
    val permitidaConBloqueo = enSeleccionVenta || current?.hasRoute(Route.Venta::class) == true ||
        current?.hasRoute(Route.QrVinculacion::class) == true || current?.hasRoute(Route.Respaldo::class) == true
    val bloquear = actualizacion.bloqueada && !permitidaConBloqueo

    CompositionLocalProvider(cu.spvi.app.common.LocalPermisosApp provides permisos, cu.spvi.designsystem.component.LocalBloqueoGestos provides bloqueoGestos) {
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().then(if (bloquear) Modifier.clearAndSetSemantics { } else Modifier)) {
    Scaffold(
        bottomBar = {
            // Solo en los 5 destinos principales; Venta/Licencia/Perfil son pantallas completas.
            AnimatedVisibility(
                visible = selected != null,
                enter = slideInVertically(SpviMotion.muelleEntrada()) { it },
                exit = slideOutVertically(SpviMotion.muelle()) { it },
            ) {
                SpviNavigationBar(
                    items = items,
                    selectedKey = selected?.name,
                    onSelect = { item -> nav.navigateTopLevel(TopLevel.valueOf(item.key).route) },
                )
            }
        },
    ) { padding ->
        // 0.21.0 (C12): si en el recorrido inicial se eligió «Secundaria», se abre la vinculación al entrar.
        LaunchedEffect(Unit) {
            if (cu.spvi.app.onboarding.TourPendiente.consumirVincularSecundaria()) nav.navigateTo(Route.VincularSecundaria)
        }
        // 0.25.0 (§3.4, §6.2): consulta semanal (revocaciones + versión) al abrir la app; nada en segundo plano.
        val actualizacionVm: cu.spvi.app.actualizacion.ActualizacionViewModel = hiltViewModel()
        LaunchedEffect(Unit) { actualizacionVm.alAbrir() }
        NavHost(
            navController = nav,
            startDestination = Route.Inicio,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .deslizarEntreSecciones(activo = deslizar && selected != null) { dir ->
                    val actual = selected ?: return@deslizarEntreSecciones
                    secciones.getOrNull(secciones.indexOf(actual) + dir)?.let { nav.navigateTopLevel(it.route) }
                },
            // P18 (A17): entre pestañas principales, fundido + zoom sutil; al entrar en una pantalla de detalle,
            // desliza 1/4 del ancho hacia la izquierda y al volver, hacia la derecha (muelle de entrada blando en el
            // recorrido, FastOutSlowIn en el fundido). Con «Quitar animaciones» del sistema Compose las acorta a 0.
            enterTransition = { if (entrePestanas()) SpviMotion.screenEnter else SpviMotion.screenForwardEnter },
            exitTransition = { if (entrePestanas()) SpviMotion.screenExit else SpviMotion.screenForwardExit },
            popEnterTransition = { if (entrePestanas()) SpviMotion.screenEnter else SpviMotion.screenBackEnter },
            popExitTransition = { if (entrePestanas()) SpviMotion.screenExit else SpviMotion.screenBackExit },
        ) {
            composable<Route.Inicio> { e ->
                val mensaje = e.savedStateHandle.getStateFlow<String?>(KEY_INICIO_MENSAJE, null).collectAsStateWithLifecycle().value
                InicioScreen(
                    onNavigate = nav::navigateTo,
                    mensajeInicial = mensaje,
                    onMensajeMostrado = { e.savedStateHandle[KEY_INICIO_MENSAJE] = null },
                )
            }
            composable<Route.Inventario> { e ->
                // El filtro por alerta llega como argumento "alerta" y lo lee el ViewModel (SavedStateHandle).
                val mensaje = e.savedStateHandle.getStateFlow<String?>(KEY_PRODUCTO_GUARDADO, null).collectAsStateWithLifecycle().value
                InventarioScreen(
                    onNavigate = nav::navigateTo,
                    mensajeInicial = mensaje,
                    onMensajeMostrado = { e.savedStateHandle[KEY_PRODUCTO_GUARDADO] = null },
                    // Modo venta: devuelve la selección a la venta por el back stack.
                    onSeleccionVenta = { ids ->
                        nav.previousBackStackEntry?.savedStateHandle?.set(KEY_VENTA_SELECCION, ids.toLongArray())
                        nav.popBackStack()
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<Route.ProductoForm> {
                ProductoFormScreen(
                    onBack = { nav.popBackStack() },
                    onGuardado = { msg ->
                        // Vuelve a Inventario con el aviso para el snackbar.
                        val volvio = nav.popBackStack<Route.Inventario>(inclusive = false)
                        if (!volvio) nav.navigate(Route.Inventario()) { popUpTo<Route.ProductoForm> { inclusive = true } }
                        nav.currentBackStackEntry?.savedStateHandle?.set(KEY_PRODUCTO_GUARDADO, msg)
                    },
                    // P31: la categoría «Insumos» sustituye este formulario por el de insumo (Atrás no vuelve aquí).
                    onCrearInsumo = { nombre ->
                        nav.navigate(Route.InsumoForm(nombre = nombre.ifBlank { null })) { popUpTo<Route.ProductoForm> { inclusive = true } }
                    },
                )
            }
            composable<Route.Servicios> { e ->
                val mensaje = e.savedStateHandle.getStateFlow<String?>(KEY_SERVICIO_MENSAJE, null).collectAsStateWithLifecycle().value
                ServiciosScreen(
                    onNavigate = nav::navigateTo,
                    mensajeInicial = mensaje,
                    onMensajeMostrado = { e.savedStateHandle[KEY_SERVICIO_MENSAJE] = null },
                    // Modo venta: devuelve la selección a la venta por el back stack.
                    onSeleccionVenta = { ids ->
                        nav.previousBackStackEntry?.savedStateHandle?.set(KEY_VENTA_SELECCION, ids.toLongArray())
                        nav.popBackStack()
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<Route.ServicioForm> {
                ServicioFormScreen(
                    onBack = { nav.popBackStack() },
                    onGuardado = { msg ->
                        nav.previousBackStackEntry?.savedStateHandle?.set(KEY_SERVICIO_MENSAJE, msg)
                        nav.popBackStack()
                    },
                )
            }
            composable<Route.InsumoForm> {
                InsumoFormScreen(
                    onBack = { nav.popBackStack() },
                    onGuardado = { msg ->
                        // P29: los insumos viven en Inventario: se vuelve allí con el aviso (P31: también si se llegó
                        // desde el escáner o desde Inicio, como hace el formulario de producto).
                        val volvio = nav.popBackStack<Route.Inventario>(inclusive = false)
                        if (!volvio) nav.navigate(Route.Inventario()) { popUpTo<Route.InsumoForm> { inclusive = true } }
                        nav.currentBackStackEntry?.savedStateHandle?.set(KEY_PRODUCTO_GUARDADO, msg)
                    },
                )
            }
            composable<Route.Registros> {
                RegistrosScreen(onNavigate = nav::navigateTo)
            }
            composable<Route.TurnoDetalle> { TurnoDetalleScreen(onBack = { nav.popBackStack() }) }
            composable<Route.Ajustes> { AjustesScreen(onNavigate = nav::navigateTo) }
            composable<Route.Venta> { e ->
                val tipo = e.toRoute<Route.Venta>().tipo
                val seleccion = e.savedStateHandle.getStateFlow<LongArray?>(KEY_VENTA_SELECCION, null).collectAsStateWithLifecycle().value
                VentaScreen(
                    tipo = TipoVenta.entries.firstOrNull { it.name == tipo } ?: TipoVenta.VENTA,
                    onBack = { nav.popBackStack() },
                    onElegirProductos = { t, pre ->
                        val csv = SeleccionVenta.csv(pre).ifEmpty { null }
                        // P29: la venta de servicios elige en Servicios; la de productos, en Inventario.
                        nav.navigate(if (t == TipoVenta.SERVICIO) Route.Servicios(venta = true, seleccion = csv) else Route.Inventario(venta = t.name, seleccion = csv))
                    },
                    onTerminada = { msg ->
                        // Vuelve a Inicio (gráficos al día) con el aviso de venta registrada.
                        if (!nav.popBackStack<Route.Inicio>(inclusive = false)) nav.navigateTopLevel(Route.Inicio)
                        nav.currentBackStackEntry?.savedStateHandle?.set(KEY_INICIO_MENSAJE, msg)
                    },
                    onConfigurarPago = { nav.navigate(Route.PagoElectronico) },
                    seleccion = seleccion?.toList(),
                    onSeleccionConsumida = { e.savedStateHandle[KEY_VENTA_SELECCION] = null },
                )
            }
            composable<Route.Precios> { PreciosScreen(onBack = { nav.popBackStack() }) }
            composable<Route.PagoElectronico> { PagoElectronicoScreen(onBack = { nav.popBackStack() }) }
            composable<Route.Licencia> {
                LicenciaScreen(onBack = { nav.popBackStack() })
            }
            composable<Route.Vinculacion> { e ->
                val qr = e.savedStateHandle.getStateFlow<String?>(KEY_QR_LEIDO, null).collectAsStateWithLifecycle().value
                VinculacionScreen(
                    onBack = { nav.popBackStack() },
                    onEscanear = { nav.navigateTo(Route.QrVinculacion) },
                    qrLeido = qr,
                    onQrConsumido = { e.savedStateHandle[KEY_QR_LEIDO] = null },
                )
            }
            composable<Route.VincularSecundaria> { e ->
                val qr = e.savedStateHandle.getStateFlow<String?>(KEY_QR_LEIDO, null).collectAsStateWithLifecycle().value
                VinculacionScreen(
                    onBack = { nav.popBackStack() },
                    onEscanear = { nav.navigateTo(Route.QrVinculacion) },
                    qrLeido = qr,
                    onQrConsumido = { e.savedStateHandle[KEY_QR_LEIDO] = null },
                    abrirSecundaria = true,
                )
            }
            composable<Route.QrVinculacion> {
                QrVinculacionScreen(
                    onBack = { nav.popBackStack() },
                    onQrLeido = { c ->
                        nav.previousBackStackEntry?.savedStateHandle?.set(KEY_QR_LEIDO, c)
                        nav.popBackStack()
                    },
                )
            }
            composable<Route.ConfiguracionInicial> { entry ->
                val paso = entry.toRoute<Route.ConfiguracionInicial>().paso
                OnboardingScreen(
                    modo = ModoWizard.RETOMAR,
                    soloPaso = PasoConfiguracion.entries.firstOrNull { it.name == paso },
                    onTerminar = { nav.popBackStack() },
                )
            }
            composable<Route.Perfil> { PerfilScreen(onBack = { nav.popBackStack() }) }
            composable<Route.Respaldo> { RespaldoScreen(onBack = { nav.popBackStack() }) }
            composable<Route.Migrar> {
                MigrarScreen(
                    onBack = { nav.popBackStack() },
                    onRespaldoCompleto = { nav.navigate(Route.Respaldo) },
                )
            }
            composable<Route.Ayuda> { AyudaScreen(onBack = { nav.popBackStack() }) }
            composable<Route.Soporte> { SoporteScreen(onBack = { nav.popBackStack() }) }
            composable<Route.Catalogo> { CatalogoScreen(onBack = { nav.popBackStack() }) }
        }
    }
    }
    if (bloquear) {
        cu.spvi.app.actualizacion.BloqueoActualizacionRuta(
            estado = actualizacion, actualizacion = bloqueoVm,
            onExportarRespaldo = { nav.navigate(Route.Respaldo) { launchSingleTop = true } },
        )
    }
    }
    }
}

private const val KEY_PRODUCTO_GUARDADO = "producto_guardado"
private const val KEY_QR_LEIDO = "qr_leido"
private const val KEY_SERVICIO_MENSAJE = "servicio_mensaje"
private const val KEY_VENTA_SELECCION = "venta_seleccion"
private const val KEY_INICIO_MENSAJE = "inicio_mensaje"

private fun NavDestination?.isIn(route: Route): Boolean =
    this?.hierarchy?.any { it.hasRoute(route::class) } == true

private fun NavBackStackEntry.esPrincipal(): Boolean =
    !enSeleccionVenta() && TopLevel.entries.any { destination.isIn(it.route) }

/** Inventario (venta = TipoVenta) o Servicios (venta = true) abiertos para elegir qué vender. */
private fun NavBackStackEntry.enSeleccionVenta(): Boolean =
    if (destination.hasRoute(Route.Servicios::class)) arguments?.getBoolean(ServiciosViewModel.KEY_VENTA) == true
    else arguments?.getString(InventarioViewModel.KEY_VENTA) != null

private fun AnimatedContentTransitionScope<NavBackStackEntry>.entrePestanas(): Boolean =
    initialState.esPrincipal() && targetState.esPrincipal()

/** Cambio de pestaña: una sola instancia por destino, conserva estado. */
private fun NavHostController.navigateTopLevel(route: Route) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun NavHostController.navigateTo(route: Route) = navigate(route) { launchSingleTop = true }
