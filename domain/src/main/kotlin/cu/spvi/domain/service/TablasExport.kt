package cu.spvi.domain.service

import cu.spvi.domain.model.nombreCompleto
import cu.spvi.core.money.Money
import cu.spvi.core.money.Percent
import cu.spvi.core.quantity.Cantidad
import cu.spvi.core.time.Dates
import cu.spvi.domain.model.FichaInsumo
import cu.spvi.domain.model.FichaProducto
import cu.spvi.domain.model.Insumo
import cu.spvi.domain.model.ItemMovimiento
import cu.spvi.domain.model.MovimientoInventario
import cu.spvi.domain.model.Perfil
import cu.spvi.domain.model.PreajustePrecios
import cu.spvi.domain.model.Producto
import cu.spvi.domain.model.TipoEntidad
import cu.spvi.domain.model.Transaccion
import cu.spvi.domain.model.Venta
import java.time.ZoneId

/** Tabla neutral: la misma fuente alimenta PDF, Excel e imagen. */
data class TablaExport(
    val titulo: String,
    val columnas: List<String>,
    val filas: List<List<String>>,
    val pie: List<String> = emptyList(),
) {
    init { require(filas.all { it.size == columnas.size }) { "fila con número de columnas distinto" } }
}

enum class FormatoExport(val extension: String, val mime: String) {
    PDF("pdf", "application/pdf"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}

object TablasExport {

    fun ventas(ventas: List<Venta>, zone: ZoneId = ZoneId.systemDefault()) = TablaExport(
        titulo = "Ventas",
        // 0.20.0 (H1): «Vendedor» = quien abrió el turno de la venta (el dueño o el empleado de una app secundaria).
        // 0.25.0: «Estado» = Válida / Anulada (las anuladas no suman en el total).
        columnas = listOf("Fecha", "Vendedor", "Método", "Artículos", "Unidades", "Importe", "Costo", "Ganancia", "Estado"),
        filas = ventas.map { v ->
            listOf(
                Dates.dayTime(v.fecha, zone), v.vendedor, Estadisticas.etiqueta(v.metodoPago),
                v.detalles.joinToString("; ") { "${it.nombre} ×${it.cantidad}" }, v.unidades.toString(),
                Money.format(v.total), Money.format(v.costoTotal), Money.format(v.ganancia), estado(v),
            )
        },
        pie = listOf("Total: " + Money.format(ventas.filterNot { it.anulada }.fold(cu.spvi.core.money.Cup.ZERO) { a, v -> a + v.total })),
    )

    /** [ocultarCi]: en el texto compartido el carné solo muestra sus 3 últimas cifras (ver [ciOculto]). */
    fun transacciones(ts: List<Transaccion>, zone: ZoneId = ZoneId.systemDefault(), ocultarCi: Boolean = false) = TablaExport(
        titulo = "Transferencias recibidas",
        columnas = listOf("Fecha", "Nº transacción", "Cliente", "CI", "Teléfono", "Importe"),
        filas = ts.map { t ->
            listOf(
                Dates.dayTime(t.fecha, zone), t.numero, t.cliente.nombreApellidos,
                if (ocultarCi) ciOculto(t.cliente.ci) else t.cliente.ci, t.cliente.telefono,
                Money.format(t.importe),
            )
        },
        pie = listOf("Total: " + Money.format(ts.fold(cu.spvi.core.money.Cup.ZERO) { a, t -> a + t.importe })),
    )

    /** [simbolos]: unidad de cada insumo por id («kg»); los productos van en unidades. */
    fun movimientos(ms: List<MovimientoInventario>, zone: ZoneId = ZoneId.systemDefault(), simbolos: Map<Long, String> = emptyMap()) = TablaExport(
        titulo = "Movimientos de inventario e insumos",
        columnas = listOf("Fecha", "Tipo", "Entidad", "Nombre", "Cambio", "Existencia", "Hecho por", "Nota"),
        filas = ms.map { m ->
            val u = unidad(m, simbolos)
            listOf(
                Dates.dayTime(m.fecha, zone), Estadisticas.etiqueta(m.tipo), entidad(m.entidad), m.nombre,
                cantidad(m.entidad, m.delta, signo = true) + u, cantidad(m.entidad, m.existenciaResultante) + u, m.hechoPor, m.nota.orEmpty(),
            )
        },
    )

    /** Tabla de Movimientos tal como se ve en Registros (con la unidad de cada insumo). */
    fun movimientosItems(xs: List<ItemMovimiento>, zone: ZoneId = ZoneId.systemDefault()) = movimientos(
        xs.map { it.movimiento }, zone,
        xs.filter { it.movimiento.entidad == TipoEntidad.INSUMO && it.simbolo.isNotBlank() }.associate { it.movimiento.entidadId to it.simbolo },
    )

    // ---------------- Elementos de Registros (ventana al tocar y «Compartir → Texto») ----------------
    // Ventas, transferencias, movimientos y turnos se identifican por su FECHA (nunca por el id interno): la fecha va
    // en el título y por eso no se repite como campo.

    fun tituloVenta(v: Venta, zone: ZoneId = ZoneId.systemDefault()) =
        "${if (v.esServicio) "Servicio" else "Venta"} del ${Dates.dayTime(v.fecha, zone)}" // P29
    fun tituloTransaccion(t: Transaccion, zone: ZoneId = ZoneId.systemDefault()) = "Transferencia del ${Dates.dayTime(t.fecha, zone)}"
    fun tituloMovimiento(i: ItemMovimiento, zone: ZoneId = ZoneId.systemDefault()) = "Movimiento del ${Dates.dayTime(i.movimiento.fecha, zone)}"

    /**
     * Campos de una venta. [interno] = ventana de la app (con costo, ganancia y turno). El texto compartido
     * usa `interno = false`: sirve de comprobante para el cliente y no revela márgenes.
     */
    fun camposVenta(v: Venta, zone: ZoneId = ZoneId.systemDefault(), interno: Boolean = true): List<Pair<String, String>> = buildList {
        add("Pago" to Estadisticas.etiqueta(v.metodoPago))
        // 0.20.0 (H1): solo en la ficha (interno); el texto compartido con el cliente no lleva nombres del negocio.
        if (interno && v.vendedor.isNotBlank()) add("Vendedor" to v.vendedor)
        v.detalles.forEach { d ->
            add("${d.cantidad} × ${d.nombre}" to "${Money.format(d.precioUnitario)} c/u · ${Money.format(d.subtotal)}")
        }
        add("Unidades" to v.unidades.toString())
        add("Total" to Money.format(v.total))
        if (interno) {
            add("Costo" to Money.format(v.costoTotal))
            add("Ganancia" to Money.format(v.ganancia))
        }
        v.transaccion?.let { t ->
            add("Nº transacción" to t.numero)
            add("Cliente" to t.cliente.nombreApellidos)
        }
        if (interno) {
            v.anulacion?.let { a ->
                add("Estado" to "Anulada el ${Dates.dayTime(a.en, zone)}")
                add("Motivo" to a.motivo)
                if (a.por.isNotBlank()) add("Anulada por" to a.por)
            }
            v.corrigeVentaId?.let { add("Corrige" to "Venta #$it") }
        }
    }

    /**
     * Líneas de la venta para la ficha de Registros: el unitario va DEBAJO del producto (subtítulo), no a la
     * derecha. Triple(título `"2 × Pan"`, subtítulo `"$10.00 c/u"`, valor `"$20.00"`).
     */
    fun lineasVenta(v: Venta): List<Triple<String, String?, String>> =
        v.detalles.map { d -> Triple("${d.cantidad} × ${d.nombre}", "${Money.format(d.precioUnitario)} c/u", Money.format(d.subtotal)) }

    /** 0.25.0: «Válida», «Anulada» o «Corrige #N». */
    fun estado(v: Venta): String = when {
        v.anulada -> "Anulada"
        v.corrigeVentaId != null -> "Corrige #${v.corrigeVentaId}"
        else -> "Válida"
    }

    /** 0.25.0: arqueo de caja de un turno (pestaña Caja, PDF y Excel del turno). Vacío en turnos sin arqueo. */
    fun camposArqueo(a: cu.spvi.domain.model.Arqueo?): List<Pair<String, String>> = if (a == null) {
        listOf("Arqueo" to SIN_ARQUEO)
    } else buildList {
        add("Fondo inicial" to Money.format(a.fondo))
        add("Ventas en efectivo" to Money.format(a.ventasEfectivo))
        add("Entradas de efectivo" to Money.format(a.entradas))
        add("Salidas de efectivo" to Money.format(a.salidas))
        add("Esperado en caja" to Money.format(a.esperado))
        add("Contado" to (a.contado?.let(Money::format) ?: "Sin contar"))
        a.diferencia?.let { d -> add(etiquetaDiferencia(a.estado) to Money.format(if (d.isNegative) -d else d)) }
    }

    fun etiquetaDiferencia(e: cu.spvi.domain.model.EstadoArqueo?): String = when (e) {
        cu.spvi.domain.model.EstadoArqueo.SOBRANTE -> "Sobrante"
        cu.spvi.domain.model.EstadoArqueo.FALTANTE -> "Faltante"
        cu.spvi.domain.model.EstadoArqueo.CUADRA, null -> "Diferencia (cuadra)"
    }

    /** 0.25.0: movimientos de efectivo del turno. */
    fun caja(ms: List<cu.spvi.domain.model.MovimientoCaja>, zone: ZoneId = ZoneId.systemDefault()) = TablaExport(
        titulo = "Caja",
        columnas = listOf("Fecha", "Tipo", "Importe", "Motivo", "Hecho por"),
        filas = ms.map { m ->
            listOf(
                Dates.dayTime(m.fecha, zone), if (m.tipo == cu.spvi.domain.model.TipoMovimientoCaja.ENTRADA) "Entrada" else "Salida",
                Money.format(m.importe), m.motivo, m.hechoPor,
            )
        },
    )

    const val SIN_ARQUEO = "Sin arqueo (turno anterior a 0.25.0)"

    // ---------------- 0.25.1: PDF / Excel del turno ----------------

    /** «Turno del 02/10/2026 08:30» y, si sigue abierto, « (provisional)». */
    fun tituloTurno(t: cu.spvi.domain.model.Turno, zone: ZoneId = ZoneId.systemDefault()): String =
        "Turno del ${Dates.dayTime(t.abiertoEn, zone)}" + if (t.abierto) " (provisional)" else ""

    /** «SPVI_Turno_2026-10-02_0830.pdf». */
    fun nombreArchivoTurno(t: cu.spvi.domain.model.Turno, extension: String, zone: ZoneId = ZoneId.systemDefault()): String =
        "SPVI_Turno_" + java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm").format(t.abiertoEn.atZone(zone)) + ".$extension"

    /**
     * El turno completo, en el orden de la pantalla: resumen, arqueo, entradas/salidas de efectivo, ventas (con Estado)
     * e inventario. Las tablas sin filas se omiten (el resumen y el arqueo siempre van).
     */
    fun turno(d: cu.spvi.domain.model.DetalleTurno, zone: ZoneId = ZoneId.systemDefault()): List<TablaExport> {
        val t = d.turno
        val r = d.resumen
        val resumen = TablaExport(
            titulo = tituloTurno(t, zone),
            columnas = listOf("Dato", "Valor"),
            filas = buildList {
                add(listOf("Apertura", Dates.dayTime(t.abiertoEn, zone) + t.abiertoPor.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()))
                t.cerradoEn?.let { c -> add(listOf("Cierre", Dates.dayTime(c, zone) + t.cerradoPor?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty())) }
                add(listOf("Total vendido", Money.format(r.total)))
                add(listOf("Efectivo", Money.format(r.totalEfectivo)))
                add(listOf("Transferencia", Money.format(r.totalTransferencia)))
                add(listOf("Ventas", r.numVentas.toString()))
                if (r.numAnuladas > 0) add(listOf("Ventas anuladas (no suman)", r.numAnuladas.toString()))
                add(listOf("Unidades vendidas", r.unidades.toString()))
                add(listOf("Costo", Money.format(r.costo)))
                add(listOf("Ganancia neta", Money.format(r.ganancia)))
            },
        )
        val arqueo = TablaExport("Arqueo de caja", listOf("Dato", "Valor"), camposArqueo(d.arqueo).map { listOf(it.first, it.second) })
        val simbolos = d.insumos.associate { it.insumoId to it.simbolo }
        return listOf(resumen, arqueo, caja(d.caja, zone), ventas(d.ventas, zone), movimientos(d.movimientos, zone, simbolos))
            .filterIndexed { i, tabla -> i < 2 || tabla.filas.isNotEmpty() }
    }

    fun camposTransaccion(t: Transaccion, zone: ZoneId = ZoneId.systemDefault(), ocultarCi: Boolean = false): List<Pair<String, String>> = buildList {
        add("Nº transacción" to t.numero)
        add("Importe" to Money.format(t.importe))
        add("Cliente" to t.cliente.nombreApellidos)
        add("CI" to if (ocultarCi) ciOculto(t.cliente.ci) else t.cliente.ci)
        add("Teléfono" to t.cliente.telefono)
        t.tarjetaCobro?.takeIf { it.isNotBlank() }?.let { add("Cobrado en la tarjeta" to it) }
        t.telefonoCobro?.takeIf { it.isNotBlank() }?.let { add("Teléfono de cobro" to it) }
        if (!ocultarCi) add("Vendedor" to t.vendedor) // vacío = se filtra abajo
    }.filter { it.second.isNotBlank() }

    fun camposMovimiento(i: ItemMovimiento, zone: ZoneId = ZoneId.systemDefault()): List<Pair<String, String>> = buildList {
        val m = i.movimiento
        val u = if (m.entidad == TipoEntidad.INSUMO && i.simbolo.isNotBlank()) " ${i.simbolo}" else ""
        add("Tipo" to Estadisticas.etiqueta(m.tipo))
        add(entidad(m.entidad) to m.nombre)
        add("Cambio" to cantidad(m.entidad, m.delta, signo = true) + u)
        add("Existencia después" to cantidad(m.entidad, m.existenciaResultante) + u)
        if (m.hechoPor.isNotBlank()) add("Hecho por" to m.hechoPor) // 0.21.0 (C7)
        m.nota?.takeIf { it.isNotBlank() }?.let { add("Nota" to it) }
    }

    /** «•••••••••23»: el carné cubano empieza por la fecha de nacimiento; al compartir basta con las 3 últimas cifras. */
    fun ciOculto(ci: String): String {
        val t = ci.trim()
        if (t.length <= 3) return t
        return "•".repeat(t.length - 3) + t.takeLast(3)
    }

    private fun entidad(e: TipoEntidad) = if (e == TipoEntidad.PRODUCTO) "Producto" else "Insumo"

    private fun unidad(m: MovimientoInventario, simbolos: Map<Long, String>): String =
        if (m.entidad == TipoEntidad.INSUMO) simbolos[m.entidadId]?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty() else ""

    fun inventario(ps: List<Producto>) = TablaExport(
        titulo = "Inventario",
        columnas = listOf("Categoría", "Nombre", "Descripción", "Cantidad", "Precio costo", "Precio venta", "Caducidad"),
        filas = ps.filterNot { it.eliminado }.map { p ->
            listOf(
                // P26: un Elaborado no tiene existencias propias → Cantidad vacía.
                p.categoria, p.nombre, p.descripcion.orEmpty(), if (p.esElaborado) "" else p.cantidad.toString(), Money.format(p.precioCosto),
                Money.format(p.precioVenta), p.fechaCaducidad?.let { Dates.day(it) }.orEmpty(),
            )
        },
    )

    /**
     * Prompt 14 · Inventario → Imagen: lista de precios PARA CLIENTES (se comparte por mensajería), ordenada por
     * categoría y nombre. Como las tarjetas: nunca costo ni existencias.
     */
    fun listaPrecios(ps: List<Producto>) = TablaExport(
        titulo = "Lista de precios",
        columnas = listOf("Categoría", "Producto", "Precio"),
        filas = ps.filterNot { it.eliminado }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Producto::categoria).thenBy(String.CASE_INSENSITIVE_ORDER, Producto::nombreCompleto))
            .map { listOf(it.categoria, it.nombreCompleto, Money.format(it.precioVenta)) },
    )

    /** Ficha completa de un producto (Compartir → PDF, uso interno): tabla Campo / Valor, solo campos con dato. */
    fun ficha(f: FichaProducto) = TablaExport(
        titulo = "Ficha: ${f.producto.nombreCompleto}",
        columnas = listOf("Campo", "Valor"),
        filas = camposFicha(f, paraClientes = false).map { listOf(it.first, it.second) },
    )

    /** Atributos en el orden del Prompt Maestro. [paraClientes] omite costo, cantidad, niveles y receta. */
    fun camposFicha(f: FichaProducto, paraClientes: Boolean): List<Pair<String, String>> {
        val p = f.producto
        return buildList {
            add("Nombre" to p.nombre)
            add("Categoría" to p.categoria)
            p.descripcion?.takeIf { it.isNotBlank() }?.let { add("Descripción" to it) }
            if (!paraClientes && p.esElaborado && f.receta.isNotEmpty()) {
                add("Receta" to f.receta.joinToString("; ") { "${it.nombre} ${Cantidad.format(it.cantidad)} ${it.simbolo}" })
            }
            p.fechaCaducidad?.let { add("Caducidad" to Dates.day(it)) }
            if (!paraClientes) add("Precio costo" to Money.format(p.precioCosto))
            add((if (paraClientes) "Precio" else "Precio venta") to Money.format(p.precioVenta))
            if (!paraClientes) {
                if (p.esElaborado) f.alcanza?.let { add("Alcanza para" to it.toString()) } else add("Cantidad" to p.cantidad.toString())
                p.nivelBajo?.let { add("Nivel bajo" to it.toString()) }
                p.nivelCritico?.let { add("Nivel crítico" to it.toString()) }
            }
        }
    }

    /** Lista de Elaboración (y respaldo en Registros): los 5 atributos del insumo; la unidad acompaña a la cantidad. */
    fun insumos(xs: List<Insumo>) = TablaExport(
        titulo = "Insumos",
        columnas = listOf("Nombre", "Precio (costo)", "Cantidad", "Nivel bajo", "Nivel crítico"),
        filas = xs.map { i ->
            listOf(
                i.nombre, Money.format(i.precio) + " / " + i.unidad.simbolo, conUnidad(i.cantidad, i),
                i.nivelBajo?.let { conUnidad(it, i) }.orEmpty(), i.nivelCritico?.let { conUnidad(it, i) }.orEmpty(),
            )
        },
    )

    /** Ficha del insumo (pantalla): campos con dato, en el orden de SPVI.txt, y los Elaborados que lo usan. */
    fun camposInsumo(f: FichaInsumo): List<Pair<String, String>> = buildList {
        val i = f.insumo
        add("Nombre" to i.nombre)
        add("Precio (costo)" to Money.format(i.precio) + " / " + i.unidad.simbolo)
        i.precioVenta?.let { add("Precio de venta" to Money.format(it) + " / " + i.unidad.simbolo) }
        add("Cantidad" to conUnidad(i.cantidad, i))
        i.nivelBajo?.let { add("Nivel bajo" to conUnidad(it, i)) }
        i.nivelCritico?.let { add("Nivel crítico" to conUnidad(it, i)) }
        add("Valor en existencia" to Money.format(f.valorExistencias))
        if (f.usadoEn.isNotEmpty()) add("Usado en" to f.usadoEn.joinToString("; ") { "${it.nombre} (${conUnidad(it.cantidad, i)} ${if (it.servicio) "por vez" else "por unidad"})" })
    }

    private fun conUnidad(c: Cantidad, i: Insumo) = "${Cantidad.format(c)} ${i.unidad.simbolo}"

    fun perfil(p: Perfil) = TablaExport(
        titulo = "Perfil",
        columnas = listOf("Dato", "Valor"),
        filas = buildList {
            add(listOf("Nombre y apellidos", "${p.nombre} ${p.apellidos}".trim()))
            add(listOf("Carnet de identidad", p.ci))
            p.tarjetas.forEach { add(listOf("Tarjeta/cuenta" + (it.alias?.let { a -> " ($a)" } ?: ""), it.numero)) }
            p.telefonos.forEach { add(listOf("Teléfono" + (it.alias?.let { a -> " ($a)" } ?: ""), it.numero)) }
        },
    )

    fun preajustes(ps: List<PreajustePrecios>, productos: Map<Long, Producto>) = TablaExport(
        titulo = "Preajustes de precios",
        columnas = listOf("Nombre", "Ajuste", "Método", "Importe mínimo", "Productos", "Activo"),
        filas = ps.map { p ->
            listOf(
                p.nombre, Percent.format(p.puntosBasicos), p.metodoPago?.let(Estadisticas::etiqueta) ?: "Cualquiera",
                p.importeMinimo?.let(Money::format) ?: "—", p.productoIds.mapNotNull { productos[it]?.nombreCompleto }.sorted().joinToString("; "),
                if (p.activo) "Sí" else "No",
            )
        },
    )

    private fun cantidad(e: TipoEntidad, v: Long, signo: Boolean = false): String {
        val s = if (e == TipoEntidad.PRODUCTO) v.toString() else Cantidad.format(Cantidad(v))
        return if (signo && v > 0) "+$s" else s
    }
}
