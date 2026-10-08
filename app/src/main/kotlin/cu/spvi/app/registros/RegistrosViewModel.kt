package cu.spvi.app.registros

import cu.spvi.core.time.Dates
import cu.spvi.domain.repository.VentaRepository
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.spvi.app.common.ArchivosApp
import cu.spvi.app.common.ExportadorArchivos
import cu.spvi.app.common.lanzarExportacion
import cu.spvi.app.inicio.EstadoCarga
import cu.spvi.core.result.AppResult
import cu.spvi.core.time.Clock
import cu.spvi.domain.model.FiltroRegistros
import cu.spvi.domain.model.ItemMovimiento
import cu.spvi.domain.model.Transaccion
import cu.spvi.domain.model.Venta
import cu.spvi.domain.model.VistaRegistro
import cu.spvi.domain.service.FormatoExport
import cu.spvi.domain.service.TablaExport
import cu.spvi.domain.service.TablasExport
import cu.spvi.domain.usecase.ExportarTablas
import cu.spvi.domain.usecase.ObservarRegistro
import cu.spvi.domain.usecase.TipoRegistro
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Elemento abierto en la ventana (hoja inferior) al tocar una fila. */
sealed interface FichaRegistro {
    data class DeVenta(val venta: Venta) : FichaRegistro
    data class DeTransferencia(val transaccion: Transaccion) : FichaRegistro
    data class DeMovimiento(val item: ItemMovimiento) : FichaRegistro
}

data class RegistrosUiState(
    val pestana: PestanaRegistros = PestanaRegistros.VENTAS,
    /** Un filtro por tabla: cambiar de pestaña no pierde la búsqueda ni el filtro de las otras. */
    val filtros: Map<TipoRegistro, FiltroRegistros> = emptyMap(),
    val vista: EstadoCarga<VistaRegistro> = EstadoCarga.Cargando,
    /** Hay un filtro pendiente de consultar para la tabla actual; las filas previas siguen visibles. */
    val buscando: Boolean = false,
    val ficha: FichaRegistro? = null,
    val hojaFiltro: Boolean = false,
    /** Prompt 14: hoja «Exportar PDF o Excel» abierta. */
    val hojaExportar: Boolean = false,
    /** Creando el PDF/Excel: barra de progreso y acciones deshabilitadas. */
    val exportando: Boolean = false,
    /** 0.20.0 (H1): nombres de quienes abrieron turnos; el filtro «Vendedor» sale solo con 2 o más. */
    val vendedores: List<String> = emptyList(),
) {
    val tipo: TipoRegistro? get() = pestana.tipo
    val filtro: FiltroRegistros get() = tipo?.let { filtros[it] } ?: FiltroRegistros()
    val datos: VistaRegistro? get() = (vista as? EstadoCarga.Exito)?.datos
    val puedeCompartir: Boolean get() = (datos?.cantidad ?: 0) > 0
}

sealed interface EventoRegistros {
    /** Enviar el PDF/Excel creado (selector del sistema: WhatsApp, Telegram, Bluetooth, Drive, OneDrive…). */
    data class CompartirArchivo(val archivo: File, val mime: String, val asunto: String) : EventoRegistros
    /** La pantalla abre «Guardar como» (SAF) y devuelve la URI a [RegistrosViewModel.guardarEn]. */
    data class GuardarComo(val nombre: String, val mime: String) : EventoRegistros
    data class Mensaje(val texto: String) : EventoRegistros
}

/**
 * Registros: Ventas, Transferencias recibidas y Movimientos de inventario e insumos (más la pestaña Turnos,
 * que sigue usando [TurnosViewModel]). Cada tabla tiene buscador y filtro por fecha; Ventas y Transferencias
 * también por importe. Tocar una fila abre su ventana; tablas en PDF o Excel (0.26.0: sin texto).
 * Prompt 14: la tabla visible (búsqueda y filtro aplicados) también se exporta en PDF o Excel, para guardar en el
 * teléfono (SAF) o enviar a otra app (FileProvider). Los elementos sueltos solo se ven en su ventana.
 * Solo lectura: reutiliza [ObservarRegistro] (repositorio de Registros), [VentaRepository.obtener] y [ExportarTablas].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RegistrosViewModel @Inject constructor(
    private val observarRegistro: ObservarRegistro,
    private val ventasRepo: VentaRepository,
    private val exportarTablas: ExportarTablas,
    private val archivos: ArchivosApp,
    private val clock: Clock,
    private val saved: SavedStateHandle,
) : ViewModel() {

    /** Zona para fechas y días del filtro (tests: fija). */
    internal var zona: ZoneId = ZoneId.systemDefault()

    /** Retardo del buscador (0.30.0, F1). Los tests lo bajan a 0 para no depender del reloj virtual. */
    internal var debounceBusqueda: Long = 250

    /**
     * P18 (A12): la pestaña, la búsqueda y los filtros sobreviven a que el sistema cierre el proceso
     * ([SavedStateHandle]). Solo se guarda esto (texto corto, fechas e importes del filtro); nunca filas ni datos de la tabla.
     */
    private val local = MutableStateFlow(
        RegistrosUiState(
            pestana = FiltrosGuardados.pestana(saved.get<String>(FiltrosGuardados.CLAVE_PESTANA)),
            filtros = FiltrosGuardados.leer { saved.get<ArrayList<String>>(it) },
        ),
    )
    private val intento = MutableStateFlow(0)
    private val eventosCh = Channel<EventoRegistros>(Channel.BUFFERED)
    val eventos: Flow<EventoRegistros> = eventosCh.receiveAsFlow()

    private data class Consulta(val tipo: TipoRegistro?, val filtro: FiltroRegistros, val intento: Int)
    private data class VistaConsultada(val consulta: Consulta, val estado: EstadoCarga<VistaRegistro>)

    /**
     * Al cambiar de tabla se muestra Cargando (nunca filas de otra tabla). Al cambiar sus filtros, las filas previas
     * siguen visibles y [RegistrosUiState.buscando] anuncia la consulta hasta que llega el resultado nuevo.
     */
    private val vista: StateFlow<VistaConsultada> =
        combine(local, intento) { l, i -> Consulta(l.tipo, l.filtro, i) }
            .distinctUntilChanged()
            // 0.30.0 (F1): agrupa pulsaciones; pestañas, filtros no textuales y reintentos sin texto pasan al instante.
            .debounce { c -> if (c.filtro.texto.isEmpty()) 0L else debounceBusqueda }
            .flatMapLatest { c ->
                val tipo = c.tipo ?: return@flatMapLatest flowOf(VistaConsultada(c, EstadoCarga.Idle))
                val sinFiltro = c.filtro.texto.isBlank() && c.filtro.activos == 0
                observarRegistro(tipo, c.filtro, zona)
                    .map<VistaRegistro, VistaConsultada> { v ->
                        VistaConsultada(c, if (v.cantidad == 0 && sinFiltro) EstadoCarga.Vacio(v) else EstadoCarga.Exito(v))
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(VistaConsultada(c, EstadoCarga.Error(TextosRegistros.ERROR_CARGA)))
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VistaConsultada(Consulta(null, FiltroRegistros(), 0), EstadoCarga.Cargando))

    private val vendedores = observarRegistro.vendedores().catch { e -> if (e is CancellationException) throw e else emit(emptyList()) }

    val state: StateFlow<RegistrosUiState> = combine(local, vista, vendedores, intento) { l, v, vs, i ->
        val consultaActual = Consulta(l.tipo, l.filtro, i)
        val mismaTabla = v.consulta.tipo == l.tipo
        val vistaActual = when {
            mismaTabla -> v.estado
            l.tipo == null -> EstadoCarga.Idle
            else -> EstadoCarga.Cargando
        }
        l.copy(
            vista = vistaActual,
            buscando = l.tipo != null && mismaTabla && v.consulta != consultaActual,
            vendedores = vs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegistrosUiState())

    // ---------------- Pestañas, buscador y filtro ----------------

    fun pestana(p: PestanaRegistros) {
        local.update { it.copy(pestana = p, ficha = null, hojaFiltro = false, hojaExportar = false) }
        saved[FiltrosGuardados.CLAVE_PESTANA] = p.name
    }

    fun buscar(texto: String) = editarFiltro { it.copy(texto = texto.take(80)) }

    fun abrirFiltro() { if (local.value.tipo != null) local.update { it.copy(hojaFiltro = true) } }
    fun cerrarFiltro() = local.update { it.copy(hojaFiltro = false) }

    /** El filtro ya validado ([FiltroRegistrosLogic.aplicar]); conserva lo escrito en el buscador. */
    fun aplicarFiltro(f: FiltroRegistros) {
        editarFiltro { actual -> f.copy(texto = actual.texto) }
        cerrarFiltro()
    }

    /** Quita fecha e importe; con [tambienTexto] también vacía el buscador («Sin resultados → Quitar filtros»). */
    fun quitarFiltros(tambienTexto: Boolean = false) = editarFiltro { if (tambienTexto) FiltroRegistros() else it.sinFiltros() }

    fun reintentar() = intento.update { it + 1 }

    private fun editarFiltro(cambio: (FiltroRegistros) -> FiltroRegistros) {
        local.update { s ->
            val tipo = s.tipo ?: return@update s
            s.copy(filtros = s.filtros + (tipo to cambio(s.filtro)))
        }
        val s = local.value
        s.tipo?.let { saved[FiltrosGuardados.clave(it)] = FiltrosGuardados.codificar(s.filtro) }
    }

    // ---------------- Ventana del elemento ----------------

    fun abrirVenta(id: Long) = abrir { v -> (v as? VistaRegistro.Ventas)?.items?.firstOrNull { it.id == id }?.let(FichaRegistro::DeVenta) }
    fun abrirTransferencia(id: Long) =
        abrir { v -> (v as? VistaRegistro.Transferencias)?.items?.firstOrNull { it.id == id }?.let(FichaRegistro::DeTransferencia) }
    fun abrirMovimiento(id: Long) =
        abrir { v -> (v as? VistaRegistro.Movimientos)?.items?.firstOrNull { it.movimiento.id == id }?.let(FichaRegistro::DeMovimiento) }

    private fun abrir(buscar: (VistaRegistro) -> FichaRegistro?) {
        val f = state.value.datos?.let(buscar) ?: return
        local.update { it.copy(ficha = f) }
    }

    fun cerrarFicha() = local.update { it.copy(ficha = null) }

    /** Desde una transferencia: abre la venta que pagó (artículos, total). */
    fun verVentaDeTransferencia() {
        val t = (local.value.ficha as? FichaRegistro.DeTransferencia)?.transaccion ?: return
        viewModelScope.launch {
            val v = try { ventasRepo.obtener(t.ventaId) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (v != null) local.update { it.copy(ficha = FichaRegistro.DeVenta(v)) }
            else emitir(EventoRegistros.Mensaje(TextosRegistros.VENTA_NO_DISPONIBLE))
        }
    }

    /** La tabla que se ve, con el filtro en el título. Los documentos llevan el CI completo (registro del negocio). */
    private fun tablaVisible(ocultarCi: Boolean): TablaExport? {
        val s = state.value
        val datos = s.datos?.takeIf { it.cantidad > 0 } ?: return null
        val tabla = when (datos) {
            is VistaRegistro.Ventas -> TablasExport.ventas(datos.items, zona)
            is VistaRegistro.Transferencias -> TablasExport.transacciones(datos.items, zona, ocultarCi = ocultarCi)
            is VistaRegistro.Movimientos -> TablasExport.movimientosItems(datos.items, zona)
        }
        return detalleFiltro(s)?.let { tabla.copy(titulo = "${tabla.titulo} ($it)") } ?: tabla
    }

    // ---------------- Exportar PDF / Excel (Prompt 14) ----------------

    fun abrirExportar() { if (state.value.puedeCompartir) local.update { it.copy(hojaExportar = true) } }
    fun cerrarExportar() = local.update { it.copy(hojaExportar = false) }

    /** Formato pendiente de «Guardar como» (la URI llega después, desde el selector del sistema). */
    private data class Pendiente(val formato: FormatoExport, val tabla: TablaExport, val nombre: String)
    private val exportador = ExportadorArchivos<Pendiente>(archivos)

    private fun nombre(tipo: TipoRegistro, f: FormatoExport) =
        TextosRegistros.nombreArchivo(tipo, Dates.localDate(clock.now(), zona), f.extension)

    /** «Enviar»: crea el archivo en la carpeta privada de compartir y abre el selector del sistema. */
    fun enviar(formato: FormatoExport) {
        val tipo = state.value.tipo ?: return
        val tabla = tablaVisible(ocultarCi = false) ?: return
        cerrarExportar()
        trabajar {
            when (val r = exportador.aTemporal(nombre(tipo, formato)) { exportarTablas(listOf(tabla), formato, it) }) {
                is AppResult.Ok -> emitir(EventoRegistros.CompartirArchivo(r.value.archivo, formato.mime, TextosRegistros.asunto(tipo)))
                is AppResult.Err -> emitir(EventoRegistros.Mensaje(TextosRegistros.ERROR_EXPORTAR))
            }
        }
    }

    /** «Guardar en el teléfono»: congela la tabla visible y pide el destino al sistema. */
    fun pedirGuardar(formato: FormatoExport) {
        val tipo = state.value.tipo ?: return
        val tabla = tablaVisible(ocultarCi = false) ?: return
        val sugerido = nombre(tipo, formato)
        exportador.recordar(Pendiente(formato, tabla, sugerido))
        cerrarExportar()
        emitir(EventoRegistros.GuardarComo(sugerido, formato.mime))
    }

    fun guardarEn(uri: String?) {
        val (formato, tabla, nombre) = exportador.tomar(uri) ?: return // nada pendiente o el usuario canceló el selector
        trabajar {
            val r = exportador.aDestino(checkNotNull(uri)) { exportarTablas(listOf(tabla), formato, it) }
            emitir(EventoRegistros.Mensaje(if (r is AppResult.Ok) TextosRegistros.guardado(nombre) else TextosRegistros.ERROR_EXPORTAR))
        }
    }

    private fun trabajar(bloque: suspend () -> Unit) {
        if (local.value.exportando) return
        lanzarExportacion(
            ocupado = { b -> local.update { it.copy(exportando = b) } },
            alFallar = { emitir(EventoRegistros.Mensaje(TextosRegistros.ERROR_EXPORTAR)) },
            bloque = bloque,
        )
    }

    private fun emitir(e: EventoRegistros) { eventosCh.trySend(e) }
}
