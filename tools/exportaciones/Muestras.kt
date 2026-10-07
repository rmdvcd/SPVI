// SPVI · muestras de TODAS las exportaciones con el código real de :domain y :data (sin Android).
// Escribe en <salida>: *.xlsx (XlsxWriter real, fastexcel) y tablas.json
// (las mismas TablaExport que reciben PdfWriter, ImagenTabla y Tarjetas; tools/exportaciones/render.py las dibuja
// con las mismas medidas, colores y reglas de truncado).
// Uso: ver tools/exportaciones/generar.sh
package cu.spvi.tools.exportaciones

import cu.spvi.app.servicios.ServiciosLogic
import cu.spvi.core.money.Cup
import cu.spvi.core.quantity.Cantidad
import cu.spvi.data.export.XlsxWriter
import cu.spvi.domain.model.*
import cu.spvi.domain.service.TablaExport
import cu.spvi.domain.service.TablasExport
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val Z = ZoneId.of("America/Havana")
private fun t(s: String): Instant = java.time.LocalDateTime.parse(s).atZone(Z).toInstant()
private fun cup(p: Long) = Cup.ofPesos(p)
private val T0 = t("2026-09-01T09:00")

val productos = listOf(
    Producto(1, "Bebidas", "Cerveza Cristal", "Lata 350 ml", null, null, cup(180), cup(260), 48, 12, 6, "7501031311309", T0),
    Producto(2, "Bebidas", "Cerveza Cristal", "Botella 330 ml", null, null, cup(200), cup(290), 9, 12, 6, null, T0),
    Producto(3, "Bebidas", "Refresco de lata", "Tukola 355 ml", null, null, cup(110), cup(150), 60, 24, 10, "8500001234561", T0),
    Producto(4, "Bebidas", "Agua natural", "1,5 L", null, null, cup(120), cup(180), 30, null, null, null, T0),
    Producto(5, "Dulces", "Galletas", "Paquete 200 g", null, LocalDate.of(2026, 12, 15), cup(80), cup(120), 40, 10, 5, null, T0),
    Producto(6, "Dulces", "Turrón de maní", null, null, LocalDate.of(2026, 11, 30), cup(60), cup(100), 3, 10, 5, null, T0),
    Producto(7, "Aseo", "Jabón de baño", "Nácar 120 g", null, null, cup(90), cup(140), 25, null, null, null, T0),
    Producto(8, "Elaborado", "Pan con jamón", null, null, null, cup(95), cup(200), 0, null, null, null, T0),
)

fun det(p: Producto, n: Long) = DetalleVenta(productoId = p.id, nombre = p.nombreCompleto, categoria = p.categoria, cantidad = n,
    precioBase = p.precioVenta, precioUnitario = p.precioVenta, costoUnitario = p.precioCosto)

val cliente = DatosCliente("María Pérez González", "85010112345", "52345678")
val trans1 = Transaccion(1, 14, t("2026-10-02T11:05"), cup(1440), "MM10040FEJ987", cliente, vendedor = "Luis (caja 2)")
val trans2 = Transaccion(2, 18, t("2026-10-02T15:40"), cup(780), "MM10040GHK112", DatosCliente("Jorge Díaz Ruiz", "90061554321", "54112233"), vendedor = "Ana Pérez")

val ventas = listOf(
    Venta(12, 7, t("2026-10-02T09:20"), MetodoPago.EFECTIVO, listOf(det(productos[2], 2), det(productos[4], 1)), vendedor = "Ana Pérez",
        anulacion = Anulacion(t("2026-10-02T09:31"), Anulacion.motivoModificada(13, "Era un solo refresco"), "Ana Pérez")),
    Venta(13, 7, t("2026-10-02T09:31"), MetodoPago.EFECTIVO, listOf(det(productos[2], 1), det(productos[4], 1)), vendedor = "Ana Pérez", corrigeVentaId = 12),
    Venta(14, 8, t("2026-10-02T11:05"), MetodoPago.TRANSFERENCIA, listOf(det(productos[0], 5), det(productos[6], 1)), trans1, vendedor = "Luis (caja 2)"),
    Venta(15, 7, t("2026-10-02T12:10"), MetodoPago.EFECTIVO, listOf(det(productos[7], 3), det(productos[3], 2)), vendedor = "Ana Pérez"),
    Venta(16, 7, t("2026-10-02T13:45"), MetodoPago.EFECTIVO, listOf(det(productos[1], 4)), vendedor = "Ana Pérez",
        anulacion = Anulacion(t("2026-10-02T13:50"), "El cliente devolvió las cervezas", "Ana Pérez")),
    Venta(17, 7, t("2026-10-02T14:30"), MetodoPago.EFECTIVO, listOf(det(productos[5], 2), det(productos[2], 3)), vendedor = "Ana Pérez"),
    Venta(18, 8, t("2026-10-02T15:40"), MetodoPago.TRANSFERENCIA, listOf(det(productos[0], 3)), trans2, vendedor = "Ana Pérez"),
)

val movimientos = listOf(
    MovimientoInventario(1, t("2026-10-02T08:15"), TipoMovimiento.AJUSTE, TipoEntidad.PRODUCTO, 1, productos[0].nombreCompleto, 24, 56, nota = "Llegó el pedido", hechoPor = "Ana Pérez"),
    MovimientoInventario(2, t("2026-10-02T09:20"), TipoMovimiento.VENTA, TipoEntidad.PRODUCTO, 3, productos[2].nombreCompleto, -2, 64, 7, 12, hechoPor = "Ana Pérez"),
    MovimientoInventario(3, t("2026-10-02T09:31"), TipoMovimiento.ANULACION, TipoEntidad.PRODUCTO, 3, productos[2].nombreCompleto, 2, 66, 7, 12, hechoPor = "Ana Pérez"),
    MovimientoInventario(4, t("2026-10-02T12:10"), TipoMovimiento.CONSUMO, TipoEntidad.INSUMO, 101, "Pan de flauta", -3000, 17000, 7, 15, hechoPor = "Ana Pérez"),
    MovimientoInventario(5, t("2026-10-02T12:10"), TipoMovimiento.CONSUMO, TipoEntidad.INSUMO, 102, "Jamón", -150, 1850, 7, 15, hechoPor = "Ana Pérez"),
    MovimientoInventario(6, t("2026-10-02T16:00"), TipoMovimiento.ALTA, TipoEntidad.PRODUCTO, 7, productos[6].nombreCompleto, 25, 25, hechoPor = "Ana Pérez"),
)
val simbolos = mapOf(101L to "u", 102L to "kg")
val items = movimientos.map { ItemMovimiento(it, simbolos[it.entidadId].orEmpty()) }

val jamon = Insumo(102, "Jamón", UnidadMedida.KILOGRAMO, cup(1200), Cantidad(1850), Cantidad(1000), Cantidad(500), T0)

val servicios = listOf(
    ServicioDisponible(Servicio(1, "Recarga de saldo", "Telefonía", cup(500), "Cubacel 500 CUP", creadoEn = T0), emptyList(), null),
    ServicioDisponible(Servicio(2, "Corte de pelo", "Barbería", cup(400), null, creadoEn = T0),
        listOf(LineaServicio(201, "Navaja desechable", "u", Cantidad(1000), Cantidad(30000))), cup(25)),
    ServicioDisponible(Servicio(3, "Impresión", "Copias", cup(20), "Hoja carta B/N", creadoEn = T0),
        listOf(LineaServicio(202, "Hoja carta", "u", Cantidad(1000), Cantidad(480000))), cup(5)),
)

val perfil = Perfil("Ana", "Pérez Rodríguez", "88020367890",
    listOf(TarjetaBancaria(1, "9227069995328054", "BANDEC")), listOf(Telefono(1, "52345678", "Personal")), 1, 1)
val preajustes = listOf(
    PreajustePrecios(1, "Descuento por transferencia", -500, setOf(1, 2), MetodoPago.TRANSFERENCIA, null, true),
    PreajustePrecios(2, "Recargo fin de semana", 1000, setOf(8), null, cup(1000), false),
)

// Turno 7 (cerrado): PDF/Excel del turno (0.25.1, A).
val caja = listOf(
    MovimientoCaja(1, 7, t("2026-10-02T10:15"), TipoMovimientoCaja.ENTRADA, cup(200), "Cambio del banco", "Ana Pérez"),
    MovimientoCaja(2, 7, t("2026-10-02T12:40"), TipoMovimientoCaja.SALIDA, cup(300), "Pago al proveedor de pan", "Ana Pérez"),
)

val turno7 = Turno(7, t("2026-10-02T08:00"), t("2026-10-02T17:05"), abiertoPor = "Ana Pérez", cerradoPor = "Ana Pérez", fondo = cup(1500), contado = cup(3250))
val detalle7 = DetalleTurno(
    turno7, ventas.filter { it.turnoId == 7L }, movimientos.filter { it.turnoId == 7L },
    listOf(InsumoEnTurno(101, "Pan de flauta", Cantidad(3000), "u"), InsumoEnTurno(102, "Jamón", Cantidad(150), "kg")), caja,
)

private fun js(s: String) = buildString {
    append('"'); s.forEach { c -> when (c) { '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\t' -> append("\\t"); '\r' -> Unit; else -> append(c) } }; append('"')
}
private fun js(l: List<String>) = l.joinToString(",", "[", "]") { js(it) }
private fun js(t: TablaExport) = "{\"titulo\":${js(t.titulo)},\"columnas\":${js(t.columnas)},\"filas\":${t.filas.joinToString(",", "[", "]") { js(it) }},\"pie\":${js(t.pie)}}"

fun main(args: Array<String>) {
    java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-ES"))
    val out = File(args[0]).apply { mkdirs() }
    val ventasV = TablasExport.ventas(ventas, Z)
    val transV = TablasExport.transacciones(listOf(trans1, trans2), Z)
    val movV = TablasExport.movimientosItems(items, Z)
    val inv = TablasExport.inventario(productos)
    val ficha = FichaProducto(productos[0], emptyList(), NivelStock.NORMAL, EstadoCaducidad.SIN_FECHA)
    val fichaElab = FichaProducto(productos[7], listOf(LineaFicha(101, "Pan de flauta", Cantidad(1000), "u", cup(40)), LineaFicha(102, "Jamón", Cantidad(50), "kg", cup(60))),
        NivelStock.NORMAL, EstadoCaducidad.SIN_FECHA, alcanza = 12)
    val fichaIns = FichaInsumo(jamon, NivelStock.NORMAL, listOf(UsoEnReceta(8, "Pan con jamón", Cantidad(50))))
    val serv = ServiciosLogic.tabla(servicios)

    // Archivos (PDF y Excel): mismas listas de tablas que cada caso de uso.
    val docs = linkedMapOf(
        "registros_ventas" to listOf(ventasV),
        "registros_transferencias" to listOf(transV),
        "registros_movimientos" to listOf(movV),
        "inventario" to listOf(inv),
        "ficha_producto" to listOf(TablasExport.ficha(fichaElab)),
        "servicios" to listOf(serv),
        // 0.26.0 (P73 §1): el «Documento PDF» de configuración del Respaldo se quitó.
        "turno" to TablasExport.turno(detalle7, Z),
    )
    val pdfSolo = setOf("ficha_producto")
    docs.forEach { (n, ts) -> if (n !in pdfSolo) File(out, "$n.xlsx").outputStream().use { XlsxWriter.escribir(ts, it) } }

    // 0.26.0 (P73 §3): sin exportaciones de texto (solo la licencia se envía como texto).

    // Datos para el render (PDF / PNG) y para las capturas del Excel.
    val tarjetas = productos.take(6).map { "{\"nombre\":${js(it.nombreCompleto)},\"categoria\":${js(it.categoria)},\"precio\":${js(cu.spvi.core.money.Money.format(it.precioVenta))},\"inicial\":${js(it.nombre.trim().take(1).uppercase())}}" }
    File(out, "tablas.json").writeText(buildString {
        append("{\"docs\":{")
        append(docs.entries.joinToString(",") { (n, ts) -> "${js(n)}:${ts.joinToString(",", "[", "]") { js(it) }}" })
        append("},\"pdfSolo\":${js(pdfSolo.toList())}")
        append(",\"precios\":${js(TablasExport.listaPrecios(productos))}")
        append(",\"tarjetas\":${tarjetas.joinToString(",", "[", "]")}")
        append(",\"turno\":{\"arqueo\":${js(TablasExport.camposArqueo(Arqueo(cup(1500), cup(3300), cup(200), cup(300), cup(4650))).map { "${it.first}\t${it.second}" })},\"caja\":${js(TablasExport.caja(caja, Z))}}")
        append("}")
    })
    println("Muestras en ${out.absolutePath}: ${docs.size - pdfSolo.size} xlsx")
}
