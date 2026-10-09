package cu.spvi.app.venta

import cu.spvi.domain.model.nombreCompleto
import cu.spvi.app.common.mensajeSync
import cu.spvi.domain.model.ServicioDisponible
import cu.spvi.domain.model.IdArticulo
import cu.spvi.domain.model.ClaseArticulo
import cu.spvi.app.inventario.textoAlcance
import cu.spvi.core.money.Cup
import cu.spvi.core.money.Money
import cu.spvi.core.money.sumOfCup
import cu.spvi.core.result.AppError
import cu.spvi.domain.model.DatosCliente
import cu.spvi.domain.model.MetodoPago
import cu.spvi.domain.model.Producto
import cu.spvi.domain.service.LineaSolicitada
import cu.spvi.domain.validation.Validadores

/**
 * Pasos de la venta (SPVI.txt «Venta»):
 * CARRITO (cantidades + método) → COMPROBANTE (efectivo) o QR (transferencia) → CLIENTE (datos + nº de transacción).
 */
enum class PasoVenta { CARRITO, COMPROBANTE, QR, CLIENTE }

/**
 * Línea del carrito con el producto vivo (existencias y precio actuales).
 * [alcanza] (P26, solo Elaborados): unidades que permiten sus insumos ahora; un Elaborado no tiene existencias.
 */
data class LineaCarrito(
    val producto: Producto,
    val cantidad: Long,
    val alcanza: Long? = null,
    /** P29: un insumo llega con id negativo ([IdArticulo]); un servicio, como «producto» adaptado ([deServicio]). */
    val clase: ClaseArticulo = if (IdArticulo.esInsumo(producto.id)) ClaseArticulo.INSUMO else ClaseArticulo.PRODUCTO,
) {
    val esElaborado: Boolean get() = producto.esElaborado
    val esServicio: Boolean get() = clase == ClaseArticulo.SERVICIO
    /** Tope: existencias; en un Elaborado o un servicio con insumos, lo que alcanzan; un servicio sin insumos no tiene tope. */
    val maximo: Long get() = when {
        esServicio -> alcanza ?: Carrito.MAX_CANTIDAD
        esElaborado -> alcanza ?: 0
        else -> producto.cantidad
    }
    val subtotalEstimado: Cup get() = producto.precioVenta * cantidad
}

/** Reglas del carrito: puras, sin E/S. El carrito = productoId → cantidad, en orden de selección. */
object Carrito {
    const val MAX_CANTIDAD = 9_999L

    /** Nueva selección del Inventario: conserva las cantidades de lo que sigue elegido; lo nuevo entra con 1. */
    fun desdeSeleccion(ids: List<Long>, previo: Map<Long, Long>): Map<Long, Long> =
        ids.distinct().associateWithTo(linkedMapOf()) { previo[it] ?: 1L }

    fun cambiar(carrito: Map<Long, Long>, id: Long, cantidad: Long, maximo: Long?): Map<Long, Long> {
        if (id !in carrito) return carrito
        val tope = (maximo ?: MAX_CANTIDAD).coerceIn(1, MAX_CANTIDAD)
        return LinkedHashMap(carrito).apply { put(id, cantidad.coerceIn(1, tope)) }
    }

    fun quitar(carrito: Map<Long, Long>, id: Long): Map<Long, Long> = LinkedHashMap(carrito).apply { remove(id) }

    /**
     * Une carrito + productos vivos. Un producto borrado mientras se vendía desaparece del carrito.
     * [alcanza] = productoId → unidades que permiten los insumos (Elaborados).
     */
    fun lineas(
        carrito: Map<Long, Long>,
        productos: Map<Long, Producto>,
        alcanza: Map<Long, Long> = emptyMap(),
        servicios: Boolean = false,
    ): List<LineaCarrito> =
        carrito.mapNotNull { (id, n) ->
            productos[id]?.takeUnless { it.eliminado }?.let { p ->
                when {
                    servicios -> LineaCarrito(p, n, alcanza[id], ClaseArticulo.SERVICIO)
                    p.esElaborado -> LineaCarrito(p, n, alcanza[id] ?: 0)
                    else -> LineaCarrito(p, n)
                }
            }
        }

    /** P29: la solicitud al dominio lleva el id de cada tabla (el insumo, en positivo) y su clase. */
    fun solicitud(lineas: List<LineaCarrito>): List<LineaSolicitada> = lineas.map {
        when (it.clase) {
            ClaseArticulo.INSUMO -> LineaSolicitada(IdArticulo.insumoId(it.producto.id), it.cantidad, ClaseArticulo.INSUMO)
            else -> LineaSolicitada(it.producto.id, it.cantidad, it.clase)
        }
    }

    /**
     * P29: un servicio se muestra en el carrito con la misma fila que un producto: nombre, tipo como categoría,
     * importe como precio y costo de sus insumos. Las existencias no se usan (el tope va en [LineaCarrito.alcanza]).
     */
    fun deServicio(d: ServicioDisponible): Producto = Producto(
        id = d.servicio.id,
        categoria = d.servicio.tipo,
        nombre = d.servicio.nombreCompleto, // 0.24.0
        fotoUri = d.servicio.fotoUri,
        precioCosto = d.costo ?: Cup.ZERO,
        precioVenta = d.servicio.importe,
        cantidad = 0,
        creadoEn = d.servicio.creadoEn,
        actualizadoEn = d.servicio.actualizadoEn,
    )

    fun totalEstimado(lineas: List<LineaCarrito>): Cup = lineas.sumOfCup { it.subtotalEstimado }

    /** Texto tecleado en el campo de cantidad → número (solo dígitos). Vacío o 0 → null (se ignora). */
    fun parseCantidad(texto: String): Long? = texto.filter(Char::isDigit).take(4).toLongOrNull()?.takeIf { it > 0 }

    /** Guardar/restaurar el carrito en SavedStateHandle (muerte del proceso): [id, n, id, n…]. */
    fun aplanar(c: Map<Long, Long>): LongArray = c.flatMap { (k, v) -> listOf(k, v) }.toLongArray()
    fun restaurar(a: LongArray?): Map<Long, Long> =
        a?.toList()?.chunked(2)?.filter { it.size == 2 && it[1] > 0 }?.associateTo(linkedMapOf()) { it[0] to it[1] } ?: linkedMapOf()
}

/** Formulario de la transferencia (SPVI.txt: Nombre y Apellidos, CI, Teléfono, Número de transacción). */
data class FormCliente(
    val nombre: String = "",
    val ci: String = "",
    val telefono: String = "",
    val numero: String = "",
    /** Tras pulsar Confirmar se muestran todos los errores; antes, solo los de campos ya tocados. */
    val intento: Boolean = false,
    val tocados: Set<CampoCliente> = emptySet(),
    /** 0.27.0 (N2): «Cliente fijo»: al registrar la venta se guarda (o actualiza) por carné. */
    val fijo: Boolean = false,
) {
    fun datos() = DatosCliente(nombreApellidos = nombre, ci = ci, telefono = telefono)

    /** 0.27.0 (N2): sugerencia elegida → rellena nombre, carné y teléfono y marca «Cliente fijo». */
    fun conCliente(c: cu.spvi.domain.model.ClienteFijo): FormCliente = copy(
        nombre = c.nombreApellidos.take(CampoCliente.NOMBRE.max), ci = c.ci.take(CampoCliente.CI.max),
        telefono = c.telefono.take(CampoCliente.TELEFONO.max), fijo = true,
        tocados = tocados + CampoCliente.NOMBRE + CampoCliente.CI + CampoCliente.TELEFONO,
    )

    /** Errores por campo (mensajes sin jerga). Vacío = se puede confirmar. */
    fun errores(): Map<CampoCliente, String> = buildMap {
        Validadores.cliente(datos()).forEach { v -> CampoCliente.deCampo(v.campo)?.let { put(it, TextosVenta.error(it, v.regla)) } }
        Validadores.numeroTransaccion(numero)?.let { put(CampoCliente.NUMERO, TextosVenta.error(CampoCliente.NUMERO, it.regla)) }
    }

    fun visibles(): Map<CampoCliente, String> = errores().filterKeys { intento || it in tocados }

    fun con(campo: CampoCliente, valor: String): FormCliente {
        // Primero se limpia (guiones, espacios…) y DESPUÉS se recorta: «850101-12345» → 11 dígitos completos.
        val f = when (campo) {
            CampoCliente.NOMBRE -> copy(nombre = valor.take(campo.max))
            CampoCliente.CI -> copy(ci = valor.filter(Char::isDigit).take(campo.max))
            CampoCliente.TELEFONO -> copy(telefono = valor.filter { it.isDigit() || it == '+' || it == ' ' }.take(campo.max))
            CampoCliente.NUMERO -> copy(numero = valor.filter(Char::isLetterOrDigit).uppercase().take(campo.max))
        }
        return f.copy(tocados = tocados + campo)
    }
}

enum class CampoCliente(val campo: String, val etiqueta: String, val max: Int) {
    NOMBRE("clienteNombre", "Nombre y apellidos", 80),
    CI("clienteCi", "Carné de identidad", 11),
    TELEFONO("clienteTelefono", "Teléfono", 16),
    NUMERO("numeroTransaccion", "Nº de transacción", 30),
    ;

    companion object { fun deCampo(c: String) = entries.firstOrNull { it.campo == c } }
}

object TextosVenta {
    /** 0.20.0 (H5): el encargado pidió cerrar el turno desde la app principal. */
    const val CIERRE_PEDIDO = "El encargado pidió cerrar tu turno: se cerrará al terminar esta venta."
    const val TITULO_VENTA = "Nueva venta"
    const val TITULO_SERVICIOS = "Venta de servicios"
    const val CARRITO_VACIO_TITULO = "Aún no has elegido productos"
    const val CARRITO_VACIO_DETALLE = "Elige en el inventario lo que vas a vender."
    const val ELEGIR = "Elegir productos"
    const val AGREGAR = "Agregar o quitar productos"
    const val METODO = "Método de pago"
    const val COMPROBANTE = "Comprobante de pago"
    const val COBRO_TRANSFERENCIA = "Cobro por transferencia"
    const val DATOS_CLIENTE = "Datos de la transferencia"
    const val SIN_TARJETA = "Elige en Pago electrónico la tarjeta que recibe los pagos."
    const val CONFIGURAR_PAGO = "Configurar Pago electrónico"
    /** 0.27.0 (N2). */
    const val CLIENTE_FIJO = "Cliente fijo"
    const val CLIENTE_FIJO_AYUDA = "Guarda sus datos para rellenarlos solos la próxima vez."
    fun sugerencia(c: cu.spvi.domain.model.ClienteFijo): String = "Carné ${c.ci}\nTel. ${c.telefono}"
    const val QR_AYUDA = "El QR oficial no incluye el importe. El cliente debe escribir el que aparece debajo."
    const val PAGO_RECIBIDO = "Ya pagó: pedir datos"
    const val PEGAR_SMS = "Pegar SMS"
    const val PEGAR_AYUDA = "Si no se detectó automáticamente, comparte el SMS de PAGOxMOVIL con SPVI o pégalo."
    const val CAPTURA_SMS_TITULO = "Captura automática opcional"
    const val CAPTURA_SMS_ACTIVA_TITULO = "Captura automática activa"
    const val CAPTURA_SMS_AYUDA = "Al autorizar Acceso a notificaciones en Ajustes, SPVI puede completar el número desde el SMS " +
        "mientras esta transferencia está activa. Android da acceso a todas las notificaciones, pero SPVI solo procesa " +
        "mensajes con una transacción reconocible; no lee el buzón ni guarda el SMS."
    const val CAPTURA_SMS_ACTIVA = "Si el texto del SMS no aparece en la notificación, comparte el mensaje o usa «Pegar SMS»."
    const val CAPTURA_SMS_ACTIVAR = "Activar en Ajustes"
    const val CAPTURA_SMS_ADMINISTRAR = "Administrar en Ajustes"
    /** 0.20.0 (H3): el SMS del banco le llega al dueño; el empleado copia el nº del SMS del cliente. */
    const val SMS_SIN_NUMERO = "No se encontró un nº de transacción en el texto. Escríbelo a mano."
    const val PORTAPAPELES_VACIO = "No hay texto copiado. Copia el SMS de PAGOxMOVIL y vuelve a tocar el botón de pegar."
    const val DESCARTAR_TITULO = "¿Descartar la venta?"
    const val DESCARTAR_TEXTO = "Se perderán los productos elegidos."
    const val ERROR = "No se pudo completar la venta. Inténtalo de nuevo."

    fun titulo(servicios: Boolean) = if (servicios) TITULO_SERVICIOS else TITULO_VENTA
    fun metodo(m: MetodoPago) = if (m == MetodoPago.EFECTIVO) "Efectivo" else "Transferencia"
    /** P26: un Elaborado muestra siempre cuánto alcanza con sus insumos; un artículo, sus existencias. */
    fun disponibles(l: LineaCarrito) = if (l.esElaborado || l.esServicio) textoAlcance(l.maximo) else "Hay ${l.producto.cantidad}"
    /** Un servicio sin insumos no tiene tope: no se avisa nada. */
    fun avisarExistencias(l: LineaCarrito) = when {
        l.esServicio -> l.alcanza != null
        else -> l.esElaborado || l.cantidad >= l.maximo
    }
    fun unidad(l: LineaCarrito) = "${Money.format(l.producto.precioVenta)} c/u"
    fun linea(cantidad: Long, precio: Cup) = "$cantidad × ${Money.format(precio)}"
    /** Etiqueta del total (P28: uno solo por paso, centrado arriba). */
    fun etiquetaTotal(paso: PasoVenta, estimado: Boolean) = when {
        paso == PasoVenta.QR -> IMPORTE
        estimado -> TOTAL_ESTIMADO
        else -> TOTAL
    }
    const val IMPORTE = "Importe a transferir"
    const val TOTAL_ESTIMADO = "Total estimado"
    const val TOTAL = "Total"
    fun registrada(total: Cup, m: MetodoPago) = "Venta registrada: ${Money.format(total)} en ${metodo(m).lowercase()}."
    fun ajuste(base: Cup, total: Cup) = "Precio con ajuste: antes ${Money.format(base)}"
    fun smsOtroImporte(sms: Cup, total: Cup) =
        "Ojo: el SMS indica ${Money.format(sms)} y la venta es de ${Money.format(total)}. Revisa que sea la transferencia correcta."
    fun cantidadDe(nombre: String) = "Cantidad de $nombre"

    fun mensaje(e: AppError): String = when (e) {
        AppError.TurnoCerrado -> "El turno está cerrado: ábrelo para vender."
        is AppError.StockInsuficiente -> "No alcanza: ${e.faltantes.joinToString(", ")}. Ajusta las cantidades."
        AppError.NoEncontrado -> "Algo de la venta ya no existe. Vuelve a elegir."
        is AppError.Validacion -> when (e.campo) {
            "receta" -> "Un elaborado no tiene receta: no se puede vender."
            "lineas" -> if (e.regla == AppError.Regla.NO_PERMITIDO) "Vende los servicios aparte de los productos." else CARRITO_VACIO_TITULO + "."
            "precioVenta" -> "Revisa los precios en Inventario y los descuentos: el precio de venta debe superar el costo."
            else -> "Revisa los datos marcados."
        }
        else -> mensajeSync(e) ?: ERROR
    }

    fun error(c: CampoCliente, r: AppError.Regla): String = when (r) {
        AppError.Regla.REQUERIDO -> when (c) {
            CampoCliente.NOMBRE -> "Escribe el nombre y los apellidos del cliente."
            CampoCliente.CI -> "Escribe el carné de identidad (11 dígitos)."
            CampoCliente.TELEFONO -> "Escribe el teléfono del cliente."
            CampoCliente.NUMERO -> "Escribe o pega el nº de transacción."
        }
        else -> when (c) {
            CampoCliente.NOMBRE -> "Usa solo letras y espacios."
            CampoCliente.CI -> "El carné tiene 11 dígitos y empieza por la fecha de nacimiento."
            CampoCliente.TELEFONO -> "Teléfono no válido. Ej.: 51234567."
            CampoCliente.NUMERO -> "Solo letras y números (6 a 30). Ej.: BR601ADLM8997."
        }
    }
}
