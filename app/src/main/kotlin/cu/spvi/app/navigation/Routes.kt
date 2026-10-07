package cu.spvi.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import cu.spvi.designsystem.icon.SpviIcons
import kotlinx.serialization.Serializable

/**
 * Rutas type-safe (Navigation 2.8). Bloqueo y Onboarding NO están aquí: son puertas de [cu.spvi.app.root.SpviRoot].
 */
sealed interface Route {
    @Serializable data object Inicio : Route
    /** [alerta] = nombre de TipoAlerta para abrir la lista filtrada desde Inicio (null = sin filtro). */
    /**
     * [venta] = nombre de TipoVenta → modo «elegir qué vender» (Prompt 13): checkbox + Continuar, sin exportar ni
     * borrar; [seleccion] = ids ya elegidos (CSV) al volver a «Agregar productos».
     */
    @Serializable data class Inventario(val alerta: String? = null, val venta: String? = null, val seleccion: String? = null) : Route
    /**
     * P29: Servicios (sustituye a Elaboración). [venta] = true → modo «elegir qué vender» de una venta de servicios
     * (checkbox + Continuar); [seleccion] = ids ya elegidos (CSV).
     */
    @Serializable data class Servicios(val venta: Boolean = false, val seleccion: String? = null) : Route
    /** Crear (id = 0) o editar un servicio. */
    @Serializable data class ServicioForm(val id: Long = 0) : Route
    @Serializable data object Registros : Route
    /** Registro de un turno (Registros → Turnos o "Ver" tras cerrar en Inicio). */
    @Serializable data class TurnoDetalle(val id: Long) : Route
    @Serializable data object Ajustes : Route
    /** [tipo] = nombre de TipoVenta elegido en el diálogo "Nueva venta". */
    @Serializable data class Venta(val tipo: String = "VENTA") : Route
    @Serializable data object Precios : Route
    @Serializable data object PagoElectronico : Route
    @Serializable data object Licencia : Route
    /** P37: Principal / Secundaria (Ajustes → Sistema, debajo de Licencia). */
    @Serializable data object Vinculacion : Route
    /** 0.21.0 (C12): la misma pantalla, abriendo directamente «Usar como secundaria» (teléfono + QR del dueño). */
    @Serializable data object VincularSecundaria : Route
    /** Lector QR mínimo (solo vinculación): devuelve el texto tal cual; lo inválido lo informa quien lo usa. */
    @Serializable data object QrVinculacion : Route
    /**
     * Crear (id = 0) o editar producto: [nombre] inicial y [foto]/[categoria] opcionales;
     * el usuario completa el resto.
     */
    @Serializable data class ProductoForm(
        val id: Long = 0,
        val nombre: String? = null,
        val foto: String? = null,
        /** Categoría inicial de un producto nuevo (p. ej. "Elaborado"). */
        val categoria: String? = null,
    ) : Route
    /**
     * Crear (id = 0) o editar un insumo (P29: viven en Inventario, categoría «Insumos»).
     * P31: [nombre] llega del formulario de producto al elegir la categoría «Insumos».
     */
    @Serializable data class InsumoForm(val id: Long = 0, val nombre: String? = null) : Route
    /** Asistente desde Ajustes. [paso] = nombre de PasoConfiguracion para abrir solo ese paso (null = pendientes). */
    @Serializable data class ConfiguracionInicial(val paso: String? = null) : Route
    @Serializable data object Perfil : Route
    /** Respaldo siempre completo (P17): base de datos + configuración. También lo abre Migrar. */
    @Serializable data object Respaldo : Route
    @Serializable data object Migrar : Route
    @Serializable data object Ayuda : Route
    @Serializable data object Soporte : Route
    @Serializable data object Catalogo : Route
}

/** Los 5 destinos de la barra inferior, en el orden de SPVI.txt. */
enum class TopLevel(val route: Route, val icon: ImageVector, val label: String) {
    INICIO(Route.Inicio, SpviIcons.Inicio, "Inicio"),
    INVENTARIO(Route.Inventario(), SpviIcons.Inventario, "Inventario"),
    SERVICIOS(Route.Servicios(), SpviIcons.Servicios, "Servicios"),
    REGISTROS(Route.Registros, SpviIcons.Registros, "Registros"),
    AJUSTES(Route.Ajustes, SpviIcons.Ajustes, "Ajustes"),
}

/**
 * 0.21.0 (C12): secciones de la barra inferior según los módulos del negocio. Inventario solo con Ventas o Inventario;
 * Servicios solo con Servicios. Inicio, Registros y Ajustes siempre (el deslizar sigue este mismo orden).
 */
fun topLevelVisibles(p: cu.spvi.domain.model.PermisosApp): List<TopLevel> = TopLevel.entries.filter {
    when (it) {
        TopLevel.INVENTARIO -> p.verInventario
        TopLevel.SERVICIOS -> p.verServicios
        else -> true
    }
}
