// Arnés de prueba EN VIVO del escáner (no forma parte del APK ni de la verificación offline).
// Ejecuta el código REAL de la app: CodigoBarras (dominio) → BuscarProductoEnLinea + CadenaFuentes →
// FuentesRed.cadena(Red.cliente(), BasesUrl.PRODUCCION) (data, OkHttp + kotlinx.serialization) y aplica al
// resultado los mismos pasos que EscanerViewModel / ProductoFormViewModel antes de Validadores.producto.
// Uso: ver tools/escaner/probar.sh
package cu.spvi.tools.escaner

import cu.spvi.core.money.Cup
import cu.spvi.core.result.AppResult
import cu.spvi.data.network.FuentesRed
import cu.spvi.data.network.Red
import cu.spvi.domain.model.CodigoBarras
import cu.spvi.domain.model.Producto
import cu.spvi.domain.model.ProductoEnLinea
import cu.spvi.domain.model.ResultadoBusquedaEnLinea
import cu.spvi.domain.repository.CadenaFuentes
import cu.spvi.domain.repository.FuenteProductos
import cu.spvi.domain.model.NombreEnLinea
import cu.spvi.domain.usecase.BuscarProductoEnLinea
import cu.spvi.domain.usecase.DescargarFoto
import cu.spvi.domain.validation.Validadores
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.Request

/** Envuelve cada base para registrar qué respondió y cuánto tardó (la lógica de la cadena es la real). */
class Grabadora(private val f: FuenteProductos, private val log: MutableList<String>) : FuenteProductos {
    override val fuente = f.fuente
    override suspend fun buscar(codigo: String): AppResult<ProductoEnLinea?> {
        val t0 = System.nanoTime()
        val r = try { f.buscar(codigo) } catch (e: Throwable) { log += "${fuente.name}:EXC(${e.javaClass.simpleName})"; throw e }
        val ms = (System.nanoTime() - t0) / 1_000_000
        log += "${fuente.name}:" + when (r) {
            is AppResult.Ok -> if (r.value == null) "no" else "SI"
            is AppResult.Err -> "ERR(${r.error})"
        } + "/${ms}ms"
        return r
    }
}

fun esc(s: String?) = s?.replace("\\", "\\\\")?.replace("\t", "\\t")?.replace("\n", "\\n")?.replace("\r", "\\r") ?: ""

fun main(args: Array<String>) = runBlocking {
    val entrada = File(args[0]); val salida = File(args[1])
    val pausaMs = args.getOrNull(2)?.toLong() ?: 11_000L // UPCitemdb trial: ~6 consultas/min y ~100/día
    val client = Red.cliente()
    val real = FuentesRed.cadena(client)
    val filas = mutableListOf(listOf("grupo", "entrada", "normalizado", "consulta", "resultado", "fuente", "nombre", "nombre_formulario",
        "validacion", "observaciones_nombre", "imagen", "imagen_http", "bases", "ms_total", "esperado").joinToString("\t"))
    for (linea in entrada.readLines()) {
        if (linea.isBlank() || linea.startsWith("#")) continue
        val (grupo, crudo, esperado) = linea.split("\t").let { Triple(it[0], it[1], it.getOrElse(2) { "" }) }
        val raw = crudo.replace("\\t", "\t").replace("\\n", "\n")
        val norm = CodigoBarras.normalizar(raw)
        val consultable = CodigoBarras.paraConsulta(norm)?.let { if (it == norm) "sí" else "sí, como $it" } ?: "no"
        val log = mutableListOf<String>()
        val uso = BuscarProductoEnLinea(CadenaFuentes(real.fuentes.map { Grabadora(it, log) }))
        val t0 = System.nanoTime()
        val r = uso(raw)
        val ms = (System.nanoTime() - t0) / 1_000_000
        var nombre = ""; var fuente = ""; var img = ""; var imgHttp = ""; var form = ""; var valid = ""; val obs = mutableListOf<String>()
        val tipo = r.javaClass.simpleName
        if (r is ResultadoBusquedaEnLinea.Encontrado) {
            val p = r.producto
            nombre = p.nombre; fuente = p.fuente.name; img = p.imagenUrl ?: ""
            // EscanerViewModel: nombre.take(80) → «Guardar»: nombre.trim() → Route → ProductoFormViewModel (sin filtro)
            form = NombreEnLinea.limpiar(p.nombre.take(80).trim()) // EscanerViewModel take(80) → ProductoFormViewModel (0.21.4: limpieza)
            if (p.nombre.length > 80) obs += "largo=${p.nombre.length}>80 (cortado" + (if (p.nombre[79].isLetterOrDigit() && p.nombre[80].isLetterOrDigit()) " a mitad de palabra)" else ")")
            if (Regex("[\\u0000-\\u001F\\u007F]").containsMatchIn(p.nombre)) obs += "caracteres de control"
            if (Regex("\\s{2,}").containsMatchIn(p.nombre)) obs += "espacios dobles"
            if (Regex("&(#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);").containsMatchIn(p.nombre)) obs += "entidad HTML"
            if (p.nombre.any { it.code > 0xFFFF || Character.getType(it) == Character.SURROGATE.toInt() }) obs += "emoji/no-BMP"
            if (p.nombre == p.nombre.uppercase() && p.nombre.any(Char::isLetter)) obs += "todo mayúsculas"
            val prod = Producto(categoria = "General", nombre = form, precioCosto = Cup(0), precioVenta = Cup(100), cantidad = 1,
                codigo = CodigoBarras.normalizar(norm), creadoEn = Instant.EPOCH)
            valid = Validadores.producto(prod).joinToString(",") { "${it.campo}:${it.regla}" }.ifEmpty { "OK" }
            if (img.isNotEmpty()) {
                // Igual que DescargarFoto: hasta 3 URL (principal + alternativas), http → https; gana la primera imagen válida.
                val candidatas = (listOf(img) + p.imagenesAlternativas).mapNotNull { DescargarFoto.aHttps(it) }.distinct().take(3)
                val intentos = mutableListOf<String>()
                for (u in candidatas) {
                    val r = try {
                        client.newCall(Request.Builder().url(u).get().build()).execute().use { resp ->
                            val tipo = resp.header("Content-Type").orEmpty()
                            val n = resp.body?.bytes()?.size ?: 0
                            if (resp.isSuccessful && tipo.startsWith("image")) "OK $tipo ${n}B" else "${resp.code} $tipo"
                        }
                    } catch (e: Exception) { "ERROR ${e.javaClass.simpleName}" }
                    intentos += "${runCatching { java.net.URI(u).host }.getOrNull() ?: u.take(40)}: $r"
                    if (r.startsWith("OK")) break
                }
                imgHttp = intentos.joinToString(" | ").ifEmpty { "DESCARTADA" }
            }
        }
        filas += listOf(grupo, esc(raw), norm, consultable, tipo, fuente, esc(nombre), esc(form), valid, obs.joinToString("; "), img, imgHttp,
            log.joinToString(" "), ms, esperado).joinToString("\t")
        println("${norm.padEnd(16)} $tipo ${fuente} «${esc(nombre)}» ${log.joinToString(" ")} ${ms}ms $imgHttp ${obs.joinToString("; ")}")
        salida.writeText(filas.joinToString("\n") + "\n")
        if (log.any { it.startsWith("UPCITEMDB") }) Thread.sleep(pausaMs)
    }
    client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
}
