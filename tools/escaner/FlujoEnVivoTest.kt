// Prueba EN VIVO del flujo completo «escanear → formulario de producto nuevo → guardar» con las bases reales.
// NO forma parte de verificar.sh (necesita internet); se ejecuta con tools/escaner/probar_flujo.sh.
// Usa los ViewModels REALES (EscanerViewModel y ProductoFormViewModel), la cadena REAL de fuentes (OkHttp) y los
// dobles en memoria de las pruebas de :app para Room y fotos (la descarga real re-codifica con Bitmap de Android:
// aquí solo se registra la URL que la app intentaría descargar).
package cu.spvi.tools.escaner

import androidx.lifecycle.SavedStateHandle
import cu.spvi.app.ConfRepo
import cu.spvi.app.FakeFotos
import cu.spvi.app.InsRepo
import cu.spvi.app.PrefRepo
import cu.spvi.app.ProdRepo
import cu.spvi.app.RelojFijo
import cu.spvi.app.escaner.EscanerViewModel
import cu.spvi.app.escaner.EventoEscaner
import cu.spvi.app.escaner.FaseEscaner
import cu.spvi.app.escaner.ModoEscaner
import cu.spvi.app.producto.EventoForm
import cu.spvi.app.producto.ProductoFormViewModel
import cu.spvi.data.network.FuentesRed
import cu.spvi.data.network.Red
import cu.spvi.designsystem.component.FiltroEntrada
import cu.spvi.domain.model.ConsentimientoRed
import cu.spvi.domain.usecase.BuscarProductoEnLinea
import cu.spvi.domain.usecase.ConsultarCodigo
import cu.spvi.domain.usecase.DescargarFoto
import cu.spvi.domain.usecase.GuardarProducto
import cu.spvi.domain.usecase.ObtenerCategorias
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlujoEnVivoTest {
    private val cadena = FuentesRed.cadena(Red.cliente())

    /** (entrada tal como la entrega ML Kit o la teclea el usuario, nota). */
    private val casos = listOf(
        "3017620422003" to "OFF, nombre corto",
        "8501110080255" to "Cuba (850), OFF en español",
        "4005900756152" to "OBF",
        "5000394140851" to "OPF",
        "4005900980298" to "OBF sin imagen",
        "041333214016" to "UPCitemdb, nombre de 157 caracteres, imagen http",
        "40170725" to "UPCitemdb, EAN-8, MAYÚSCULAS, espacios dobles, corte a mitad de palabra",
        "9788497592208" to "UPCitemdb, libro con tildes",
        "04963406" to "UPC-E (Coca-Cola lata)",
        "0 41331 12466 9" to "UPC-A tecleado como en la etiqueta",
        "https://spvi.cu/p?id=7" to "texto de un QR",
        "SPVI-LOTE-2026-10-03-ABCDEFGHIJKLMNOPQRSTUVWXYZ-0123456789" to "Code128 interno de 58 caracteres",
        "2012345678903" to "circulación restringida (etiqueta de peso/precio de la tienda)",
    )

    @Test fun flujoCompletoConBasesReales() = runBlocking {
        if (System.getProperty("soloOffline") != null) return@runBlocking
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val filas = mutableListOf("entrada\tnota\tfase\taviso\tfuente\tnombre_escaner\tfoto_pedida\troute_codigo\troute_nombre\tform_codigo\tform_nombre\tcodigo_tras_editar(FiltroEntrada.CODIGO)\tguardar")
        for ((entrada, nota) in casos) {
            val productos = ProdRepo(); val prefs = PrefRepo(); val fotos = FakeFotos(); val reloj = RelojFijo()
            prefs.state.value = prefs.state.value.copy(consultasEnLinea = ConsentimientoRed.PERMITIDO)
            val esc = EscanerViewModel(
                saved = SavedStateHandle(mapOf("modo" to ModoEscaner.PRODUCTO.name)),
                configuracionRepo = ConfRepo(), preferenciasRepo = prefs,
                consultarCodigo = ConsultarCodigo(productos), buscarEnLinea = BuscarProductoEnLinea(cadena),
                descargarFoto = DescargarFoto(fotos), guardarProducto = GuardarProducto(productos, InsRepo(), reloj),
            )
            val scope = CoroutineScope(Dispatchers.Unconfined)
            val evEsc = mutableListOf<EventoEscaner>(); scope.launch { esc.eventos.collect { evEsc += it } }
            esc.escanear(); esc.codigoDetectado(entrada)
            val limite = System.currentTimeMillis() + 30_000
            while (esc.state.value.fase != FaseEscaner.RESULTADO && System.currentTimeMillis() < limite) Thread.sleep(50)
            val s = esc.state.value
            if (s.nombre.isBlank()) esc.nombre("Producto de prueba") // como haría el usuario si no se encontró
            esc.guardar(); Thread.sleep(100)
            val route = evEsc.filterIsInstance<EventoEscaner.NuevoProducto>().firstOrNull()?.route
            var formCod = ""; var formNom = ""; var trasEditar = ""; var resultado = "sin formulario"
            if (route != null) {
                val form = ProductoFormViewModel(
                    saved = SavedStateHandle(mapOf("codigo" to route.codigo, "nombre" to route.nombre, "foto" to route.foto, "caducidad" to route.caducidad)),
                    productos = productos, insumosRepo = InsRepo(), obtenerCategorias = ObtenerCategorias(productos),
                    guardarProducto = GuardarProducto(productos, InsRepo(), reloj), fotos = fotos, clock = reloj,
                )
                val evForm = mutableListOf<EventoForm>(); scope.launch { form.eventos.collect { evForm += it } }
                Thread.sleep(50)
                formCod = form.state.value.form.codigo; formNom = form.state.value.form.nombre
                trasEditar = FiltroEntrada.CODIGO.aplicar(formCod) // lo que queda si el usuario toca el campo código
                form.categoria("General"); form.precioCosto("50"); form.precioVenta("100"); form.cantidad("1")
                form.guardar(); Thread.sleep(150)
                val st = form.state.value
                val errores = (st.erroresGuardado + if (st.intentado) cu.spvi.app.producto.ProductoFormLogic.validar(st.form) else emptyMap())
                resultado = evForm.filterIsInstance<EventoForm.Guardado>().firstOrNull()?.let { "GUARDADO codigo=${productos.items.value.lastOrNull()?.codigo}" }
                    ?: "ERRORES $errores"
            }
            filas += listOf(entrada, nota, s.fase, s.aviso ?: "", s.fuente ?: "", s.nombre, fotos.descargadas.joinToString(),
                route?.codigo, route?.nombre, formCod, formNom, trasEditar, resultado).joinToString("\t") { it.toString().replace("\t", "\\t") }
            println(filas.last())
            scope.cancel()
            Thread.sleep(if (s.fuente?.name == "UPCITEMDB" || s.aviso != null) 11_000 else 500)
        }
        File(System.getProperty("salida") ?: "flujo.tsv").writeText(filas.joinToString("\n") + "\n")
    }

    /** Sin red: producto creado a mano con código «ABC-123» y luego escaneado (Code128 con el mismo texto). */
    @Test fun codigoConGuionTecleadoYLuegoEscaneado() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val productos = ProdRepo(); val reloj = RelojFijo(); val prefs = PrefRepo()
        val form = ProductoFormViewModel(SavedStateHandle(), productos, InsRepo(), ObtenerCategorias(productos), GuardarProducto(productos, InsRepo(), reloj), FakeFotos(), reloj)
        form.categoria("General"); form.nombre("Caja de clavos"); form.precioCosto("50"); form.precioVenta("100"); form.cantidad("1")
        form.codigo(FiltroEntrada.CODIGO.aplicar("ABC-123")); form.guardar(); Thread.sleep(150)
        println("guardado con codigo=" + productos.items.value.map { it.codigo })
        val esc = EscanerViewModel(SavedStateHandle(mapOf("modo" to ModoEscaner.PRODUCTO.name)), ConfRepo(), prefs, ConsultarCodigo(productos),
            BuscarProductoEnLinea(cu.spvi.domain.repository.CadenaFuentes(emptyList())), DescargarFoto(FakeFotos()), GuardarProducto(productos, InsRepo(), reloj))
        esc.escanear(); esc.codigoDetectado("ABC-123"); Thread.sleep(150)
        println("escaneado: codigo=${esc.state.value.codigo} existente=${esc.state.value.existente?.nombre} aviso=${esc.state.value.aviso}")
    }
}
