package cu.spvi.app.inventario

import cu.spvi.core.money.Cup
import cu.spvi.core.money.Money
import cu.spvi.core.quantity.Cantidad
import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppError.Regla
import cu.spvi.domain.model.Insumo
import cu.spvi.domain.model.Producto
import cu.spvi.domain.model.UnidadMedida
import cu.spvi.domain.validation.Validadores
import java.time.Instant

/**
 * 0.31.0 (escáner OCR): campos que la revisión puede extraer de una imagen.
 */
enum class CampoEscaneo { NOMBRE, CATEGORIA, DESCRIPCION, CANTIDAD, COSTO, VENTA, UNIDAD }

/** Una fila en revisión: los valores capturados (o corregidos) por campo seleccionado. */
data class FilaEscaneo(val valores: Map<CampoEscaneo, String> = emptyMap())

/**
 * Una línea reconocida por ML Kit con su caja en píxeles de la imagen. Es lo único que la lógica pura necesita del
 * OCR: sin tipos de Android ni de ML Kit, para poder probarla en JVM.
 */
data class LineaOcr(val texto: String, val izquierda: Int, val arriba: Int, val derecha: Int, val abajo: Int)

/**
 * Tabla reconstruida por geometría. [estructura] = se detectaron al menos dos columnas con celdas en más de una fila;
 * si no, cada línea es un registro (fallback honesto: una tabla no se puede adivinar).
 * [encabezado] = textos de la fila reconocida como nombres de campo (esa fila y las de encima no están en [filas]).
 */
data class TablaOcr(
    val filas: List<List<String>>,
    val columnas: Int,
    val estructura: Boolean,
    val encabezado: List<String>? = null,
)

/** Reconstruye filas y columnas a partir de las líneas del OCR (por coordenadas, no por tabuladores). */
fun tablaOcr(lineas: List<LineaOcr>): TablaOcr {
    val visibles = lineas.filter { it.texto.isNotBlank() }
    if (visibles.isEmpty()) return TablaOcr(emptyList(), 1, false)
    val filas = agruparEnFilas(visibles)
    val bandas = bandasDeColumnas(filas)
    // Sin dos columnas de verdad no se inventa una tabla: cada fila es un registro (nombre = línea completa).
    if (bandas.size < 2) {
        return TablaOcr(filas.map { fila -> listOf(fila.joinToString(" ") { it.texto.trim() }) }, 1, false)
    }
    val matriz = filas.map { fila ->
        val celdas = MutableList(bandas.size) { "" }
        for (l in fila) {
            // La celda va a la columna de inicio izquierdo más cercano (una celda puede desbordar su columna).
            val columna = bandas.indices.minByOrNull { i -> kotlin.math.abs(l.izquierda - bandas[i][0]) } ?: 0
            val texto = l.texto.trim()
            celdas[columna] = if (celdas[columna].isBlank()) texto else "${celdas[columna]} $texto"
        }
        celdas.toList()
    }
    // Encabezado: la primera fila con dos o más celdas que nombran un campo; lo de arriba (un título, una fecha) no
    // es un registro. Puede no ser la primera fila: las listas impresas suelen llevar título.
    val pos = matriz.take(3).indexOfFirst { fila -> fila.count { campoDeEncabezado(it) != null } >= 2 }
    return if (pos >= 0) {
        TablaOcr(filas = matriz.drop(pos + 1), columnas = bandas.size, estructura = true, encabezado = matriz[pos])
    } else {
        TablaOcr(filas = matriz, columnas = bandas.size, estructura = true)
    }
}

/**
 * Celdas dentro de una línea del OCR: ML Kit a veces pega dos columnas en una sola línea («Nombre Categoria»), así
 * que se corta donde el hueco entre palabras duplica el alto del texto. Un hueco de palabra (≈1,3 × alto) no corta:
 * en el teléfono, cortar por ahí partió «Jabon de tocador» y aparecieron columnas que no existen.
 */
fun celdasDeLinea(palabras: List<LineaOcr>): List<LineaOcr> {
    if (palabras.size <= 1) return palabras
    val orden = palabras.sortedBy { it.izquierda }
    val alto = altoTipico(orden)
    val celdas = mutableListOf<LineaOcr>()
    var texto = orden.first().texto.trim()
    var izquierda = orden.first().izquierda
    var derecha = orden.first().derecha
    var arriba = orden.first().arriba
    var abajo = orden.first().abajo
    for (p in orden.drop(1)) {
        if (p.izquierda - derecha > alto * 2) {
            celdas.add(LineaOcr(texto, izquierda, arriba, derecha, abajo))
            texto = p.texto.trim(); izquierda = p.izquierda; derecha = p.derecha; arriba = p.arriba; abajo = p.abajo
        } else {
            texto = if (texto.isEmpty()) p.texto.trim() else "$texto ${p.texto.trim()}"
            derecha = maxOf(derecha, p.derecha); arriba = minOf(arriba, p.arriba); abajo = maxOf(abajo, p.abajo)
        }
    }
    celdas.add(LineaOcr(texto, izquierda, arriba, derecha, abajo))
    return celdas
}

/** Agrupa por solape vertical (>50 % de la caja menor) y ordena cada fila de izquierda a derecha. */
private fun agruparEnFilas(lineas: List<LineaOcr>): List<List<LineaOcr>> {
    val filas = mutableListOf<MutableList<LineaOcr>>()
    for (l in lineas.sortedBy { it.arriba }) {
        val actual = filas.lastOrNull()
        if (actual != null && actual.any { solapanVerticalmente(it, l) }) actual.add(l) else filas.add(mutableListOf(l))
    }
    return filas.map { it.sortedBy { linea -> linea.izquierda } }
}

/** Alto típico (mediana) de un grupo de celdas: referencia para distinguir un hueco de palabra de uno de columna. */
private fun altoTipico(celdas: List<LineaOcr>): Int =
    celdas.map { it.abajo - it.arriba }.sorted()[celdas.size / 2].coerceAtLeast(1)

private fun solapanVerticalmente(a: LineaOcr, b: LineaOcr): Boolean {
    val solape = minOf(a.abajo, b.abajo) - maxOf(a.arriba, b.arriba)
    val altoMenor = minOf(a.abajo - a.arriba, b.abajo - b.arriba).coerceAtLeast(1)
    return solape > 0 && solape * 2 >= altoMenor
}

/**
 * Columnas por borde izquierdo: en una lista impresa todas las celdas de una columna empiezan a la misma x, aunque
 * una sea más larga que el ancho de su columna (desbordar no la convierte en otra columna). Solo cuentan las filas de
 * dos o más celdas: una fila de una sola celda (un título) no evidencia columnas.
 */
private fun bandasDeColumnas(filas: List<List<LineaOcr>>): List<IntArray> {
    val candidatas = filas.filter { it.size >= 2 }.flatten()
    if (candidatas.isEmpty()) return emptyList()
    val alto = altoTipico(candidatas)
    val bandas = mutableListOf<IntArray>()
    for (l in candidatas.sortedBy { it.izquierda }) {
        val b = bandas.minByOrNull { kotlin.math.abs(it[0] - l.izquierda) }
            ?.takeIf { kotlin.math.abs(it[0] - l.izquierda) <= alto }
        if (b == null) bandas.add(intArrayOf(l.izquierda, l.derecha))
        else b[1] = maxOf(b[1], l.derecha)
    }
    return bandas.sortedBy { it[0] }
}

/**
 * Sinónimos de encabezado aceptados. El orden importa: primero las frases compuestas («precio costo» antes que
 * «precio», que solo se refiere a la venta).
 */
private val SINONIMOS: List<Pair<String, CampoEscaneo>> = listOf(
    "precio costo" to CampoEscaneo.COSTO,
    "precio de costo" to CampoEscaneo.COSTO,
    "precio compra" to CampoEscaneo.COSTO,
    "costo" to CampoEscaneo.COSTO,
    "coste" to CampoEscaneo.COSTO,
    "precio de venta" to CampoEscaneo.VENTA,
    "precio venta" to CampoEscaneo.VENTA,
    "precio" to CampoEscaneo.VENTA,
    "pvp" to CampoEscaneo.VENTA,
    "importe" to CampoEscaneo.VENTA,
    "venta" to CampoEscaneo.VENTA,
    "categoria" to CampoEscaneo.CATEGORIA,
    "rubro" to CampoEscaneo.CATEGORIA,
    "descripcion" to CampoEscaneo.DESCRIPCION,
    "detalle" to CampoEscaneo.DESCRIPCION,
    "cantidad" to CampoEscaneo.CANTIDAD,
    "cant" to CampoEscaneo.CANTIDAD,
    "existencia" to CampoEscaneo.CANTIDAD,
    "stock" to CampoEscaneo.CANTIDAD,
    "unidad" to CampoEscaneo.UNIDAD,
    "medida" to CampoEscaneo.UNIDAD,
    "producto" to CampoEscaneo.NOMBRE,
    "nombre" to CampoEscaneo.NOMBRE,
    "articulo" to CampoEscaneo.NOMBRE,
    "denominacion" to CampoEscaneo.NOMBRE,
)

/** Campo sugerido por el texto de un encabezado («precio costo» → COSTO); null si no se reconoce. */
fun campoDeEncabezado(texto: String): CampoEscaneo? {
    val t = sinTildes(texto)
    if (t.isBlank()) return null
    return SINONIMOS.firstOrNull { (palabra, _) -> t.contains(palabra) }?.second
}

private fun sinTildes(texto: String): String =
    java.text.Normalizer.normalize(texto.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .trim()

/**
 * Mapeo columna → campo. Con encabezados reconocidos manda el encabezado (la columna queda entendida aunque su campo
 * no se haya elegido); los campos elegidos que falten van a las columnas libres en orden. Sin encabezado, los campos
 * elegidos se asignan de izquierda a derecha.
 */
fun mapeoAutomatico(tabla: TablaOcr, campos: List<CampoEscaneo>): List<CampoEscaneo?> {
    val mapeo = MutableList<CampoEscaneo?>(tabla.columnas) { null }
    val entendidas = mutableSetOf<Int>()
    tabla.encabezado?.forEachIndexed { i, texto ->
        val campo = campoDeEncabezado(texto) ?: return@forEachIndexed
        if (i >= mapeo.size) return@forEachIndexed
        entendidas.add(i)
        if (campo in campos) mapeo[i] = campo
    }
    var desde = 0
    for (campo in campos) {
        if (mapeo.contains(campo)) continue
        val libres = mapeo.indices.filter { it !in entendidas }
        val libre = libres.drop(desde).firstOrNull() ?: break
        mapeo[libre] = campo
        desde = libres.indexOf(libre) + 1
    }
    return mapeo
}

/** Filas en revisión según el mapeo: cada campo seleccionado existe (aunque quede vacío) y las filas vacías se omiten. */
fun filasDesde(tabla: TablaOcr, mapeo: List<CampoEscaneo?>, campos: List<CampoEscaneo>): List<FilaEscaneo> =
    tabla.filas.mapNotNull { celdas ->
        val valores = campos.associateWith { "" }.toMutableMap()
        celdas.forEachIndexed { i, celda ->
            val campo = mapeo.getOrNull(i) ?: return@forEachIndexed
            val limpio = celda.trim()
            if (limpio.isEmpty()) return@forEachIndexed
            val previo = valores[campo].orEmpty()
            valores[campo] = if (previo.isBlank()) limpio else "$previo $limpio"
        }
        FilaEscaneo(valores).takeIf { f -> f.valores.values.any { it.isNotBlank() } }
    }

/**
 * Primer número de la celda ya en el formato que acepta la app. El OCR pega unidades al número («12 unidades») y,
 * según quién imprimió la lista, la coma puede ser decimal («85,50») o de millares («1,450.00»).
 */
private fun numeroOcr(texto: String): String? {
    val token = Regex("\\d{1,15}(?:[.,]\\d{1,3})*").find(texto)?.value ?: return null
    if (token.count { it == ',' } == 1 && !token.contains('.')) {
        val (entera, decimales) = token.split(',')
        return if (decimales.length == 3) entera + decimales else "$entera.$decimales"
    }
    return token.replace(",", "")
}

/** Unidades enteras de un producto: igual que el formulario (solo unidades completas). */
fun unidadesOcr(texto: String): Long? {
    val c = numeroOcr(texto)?.let { Cantidad.parse(it) } ?: return null
    return if (c.milesimas % 1000L == 0L) c.milesimas / 1000L else null
}

/** Cantidad de un insumo (milésimas), con la misma convención de separadores que [numeroOcr]. */
fun milesimasOcr(texto: String): Cantidad? = numeroOcr(texto)?.let { Cantidad.parse(it) }

/** Importe en CUP; tolera el símbolo y la unidad pegada por el OCR («1,450.00 CUP», «$ 150»). */
fun precioOcr(texto: String): Cup? = numeroOcr(texto)?.let { Money.parse(it) }

/** Tipo de registro del lote: el asistente lo elige antes de procesar la imagen. */
enum class TipoEscaneo { PRODUCTO, INSUMO }

/** Motivo por el que una fila no se puede guardar; la pantalla lo traduce a texto (el ViewModel no usa textos). */
sealed interface ErrorEscaneo {
    /** La imagen no tenía texto legible. */
    data object SinTexto : ErrorEscaneo

    /** No se pudo leer la imagen (archivo movido, formato no soportado). */
    data object ImagenIlegible : ErrorEscaneo

    /** Falta el nombre en la fila [fila] (contando desde 1). */
    data class FaltaNombre(val fila: Int) : ErrorEscaneo

    /** [campo] no se pudo leer o incumple una regla del dominio en la fila [fila] (desde 1). */
    data class ValorInvalido(val fila: Int, val campo: CampoEscaneo, val regla: Regla? = null) : ErrorEscaneo

    /** El dominio rechazó la fila al guardarla (p. ej. nombre repetido). */
    data class Rechazado(val nombre: String, val error: AppError) : ErrorEscaneo
}

/** Unidad de medida por símbolo o nombre («kg», «Kilogramos»); null si no se reconoce (no se inventa una unidad). */
fun unidadOcr(texto: String): UnidadMedida? {
    val t = sinTildes(texto).trimEnd('.', ',', ';', ':')
    if (t.isBlank()) return null
    return when {
        t == "u" || t.startsWith("unidad") || t.startsWith("pieza") -> UnidadMedida.UNIDAD
        t == "kg" || t.startsWith("kilo") -> UnidadMedida.KILOGRAMO
        t == "g" || t.startsWith("gramo") -> UnidadMedida.GRAMO
        t == "l" || t.startsWith("litro") -> UnidadMedida.LITRO
        t == "ml" || t.startsWith("mili") -> UnidadMedida.MILILITRO
        else -> null
    }
}

/** Valor de un campo en la fila ("" si el usuario no lo eligió al escanear). */
fun FilaEscaneo.valor(campo: CampoEscaneo): String = valores[campo].orEmpty()

/** Producto listo para guardar; null si algún campo no se puede leer (tras [revisionDeFila] no ocurre). */
fun productoDeFila(f: FilaEscaneo, ahora: Instant): Producto? {
    val costo = precioOcr(f.valor(CampoEscaneo.COSTO)) ?: return null
    val venta = precioOcr(f.valor(CampoEscaneo.VENTA)) ?: return null
    val cantidad = unidadesOcr(f.valor(CampoEscaneo.CANTIDAD)) ?: return null
    return Producto(
        categoria = f.valor(CampoEscaneo.CATEGORIA).trim(),
        nombre = f.valor(CampoEscaneo.NOMBRE).trim(),
        descripcion = f.valor(CampoEscaneo.DESCRIPCION).trim().ifEmpty { null },
        precioCosto = costo,
        precioVenta = venta,
        cantidad = cantidad,
        creadoEn = ahora,
    )
}

/** Insumo listo para guardar; null si algún campo no se puede leer. */
fun insumoDeFila(f: FilaEscaneo, ahora: Instant): Insumo? {
    val unidad = unidadOcr(f.valor(CampoEscaneo.UNIDAD)) ?: return null
    val precio = precioOcr(f.valor(CampoEscaneo.COSTO)) ?: return null
    val cantidad = milesimasOcr(f.valor(CampoEscaneo.CANTIDAD)) ?: return null
    val venta = f.valor(CampoEscaneo.VENTA).takeIf { it.isNotBlank() }?.let { precioOcr(it) ?: return null }
    return Insumo(
        nombre = f.valor(CampoEscaneo.NOMBRE).trim(),
        unidad = unidad,
        precio = precio,
        cantidad = cantidad,
        precioVenta = venta,
        creadoEn = ahora,
    )
}

/** Revisa una fila con las reglas del dominio; [fila] cuenta desde 1 para el mensaje. */
fun revisionDeFila(fila: Int, tipo: TipoEscaneo, f: FilaEscaneo, ahora: Instant): ErrorEscaneo? {
    if (f.valor(CampoEscaneo.NOMBRE).isBlank()) return ErrorEscaneo.FaltaNombre(fila)
    return when (tipo) {
        TipoEscaneo.PRODUCTO -> {
            unidadesOcr(f.valor(CampoEscaneo.CANTIDAD)) ?: return ErrorEscaneo.ValorInvalido(fila, CampoEscaneo.CANTIDAD)
            precioOcr(f.valor(CampoEscaneo.COSTO)) ?: return ErrorEscaneo.ValorInvalido(fila, CampoEscaneo.COSTO)
            precioOcr(f.valor(CampoEscaneo.VENTA)) ?: return ErrorEscaneo.ValorInvalido(fila, CampoEscaneo.VENTA)
            val p = productoDeFila(f, ahora) ?: return ErrorEscaneo.ValorInvalido(fila, CampoEscaneo.NOMBRE)
            // Un Elaborado se crea con su receta: el escáner no puede darle existencia.
            if (p.esElaborado) return ErrorEscaneo.ValorInvalido(fila, CampoEscaneo.CATEGORIA, Regla.NO_PERMITIDO)
            Validadores.producto(p).firstOrNull()
                ?.let { return ErrorEscaneo.ValorInvalido(fila, campoEscaneoDe(it.campo), it.regla) }
            null
        }
        TipoEscaneo.INSUMO -> {
            milesimasOcr(f.valor(CampoEscaneo.CANTIDAD)) ?: return ErrorEscaneo.ValorInvalido(fila, CampoEscaneo.CANTIDAD)
            precioOcr(f.valor(CampoEscaneo.COSTO)) ?: return ErrorEscaneo.ValorInvalido(fila, CampoEscaneo.COSTO)
            unidadOcr(f.valor(CampoEscaneo.UNIDAD)) ?: return ErrorEscaneo.ValorInvalido(fila, CampoEscaneo.UNIDAD)
            val i = insumoDeFila(f, ahora) ?: return ErrorEscaneo.ValorInvalido(fila, CampoEscaneo.NOMBRE)
            Validadores.insumo(i).firstOrNull()
                ?.let { return ErrorEscaneo.ValorInvalido(fila, campoEscaneoDe(it.campo), it.regla) }
            null
        }
    }
}

/** Primera fila que no sirve, o null si el lote entero se puede insertar (nada se guarda si alguna falla). */
fun revisionDeLote(tipo: TipoEscaneo, filas: List<FilaEscaneo>, ahora: Instant): ErrorEscaneo? =
    filas.withIndex().firstNotNullOfOrNull { (i, f) -> revisionDeFila(i + 1, tipo, f, ahora) }

/** Fila (0-based, para marcar en la revisión) señalada por el error, si es de una fila concreta. */
val ErrorEscaneo.filaSenalada: Int?
    get() = when (this) {
        is ErrorEscaneo.FaltaNombre -> fila - 1
        is ErrorEscaneo.ValorInvalido -> fila - 1
        else -> null
    }

/** Campo de formulario equivalente al nombre de propiedad del dominio (como en `ProductoFormLogic`). */
private fun campoEscaneoDe(campo: String): CampoEscaneo = when (campo) {
    "nombre" -> CampoEscaneo.NOMBRE
    "categoria" -> CampoEscaneo.CATEGORIA
    "descripcion" -> CampoEscaneo.DESCRIPCION
    "cantidad" -> CampoEscaneo.CANTIDAD
    "precioCosto", "precio" -> CampoEscaneo.COSTO
    "precioVenta" -> CampoEscaneo.VENTA
    else -> CampoEscaneo.NOMBRE
}
