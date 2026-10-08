package cu.spvi.domain.seed

import cu.spvi.domain.model.Categorias
import cu.spvi.domain.model.ClaseArticulo
import cu.spvi.domain.model.UnidadMedida
import kotlin.random.Random

/** Fila del catálogo fijo «Bodega cubana» para un producto vendible. Precios en pesos CUP enteros. */
data class ProductoSeed(
    val clave: String,
    val categoria: String,
    val nombre: String,
    val costoPesos: Long,
    val ventaPesos: Long,
    val nivelBajo: Long? = null,
    val nivelCritico: Long? = null,
)

/** Insumo comprable (materia prima de elaborados o de servicios con insumos). Precio por unidad de medida. */
data class InsumoSeed(val clave: String, val nombre: String, val unidad: UnidadMedida, val precioPesos: Long)

/** Una línea de receta: clave del insumo + cantidad en milésimas de su unidad. */
data class LineaRecetaSeed(val insumo: String, val milesimas: Long)

/** Servicio ofrecido. Importe en pesos CUP enteros. */
data class ServicioSeed(
    val clave: String,
    val nombre: String,
    val tipo: String,
    val importePesos: Long,
    val insumos: List<LineaRecetaSeed> = emptyList(),
)

/** Cliente fijo ficticio: nombre + CI de 11 dígitos + teléfono de 8 dígitos. */
data class ClienteSeed(val nombre: String, val ci: String, val telefono8: String)

/** Tarjeta demo para cobros por transferencia (número solo dígitos). */
data class TarjetaSeed(val numero: String, val alias: String)

/** Teléfono demo para cobros por transferencia (8 dígitos). */
data class TelefonoSeed(val numero8: String, val alias: String)

/**
 * Catálogo fijo «Bodega cubana» (0.29.0): datos ficticios en CUP para saturar la app con el seed.
 * Todo es determinista; la aleatoriedad del historial vive en [GeneradorSeed].
 */
object CatalogoBodega {
    /** Nombres de los vendedores que se rotan por rachas (los apellidos/CI del perfil base los pone el orquestador). */
    val VENDEDORES = listOf("María", "Jorge")

    val INSUMOS = listOf(
        InsumoSeed("harina-trigo", "Harina de trigo", UnidadMedida.KILOGRAMO, 180),
        InsumoSeed("azucar-blanca", "Azúcar blanca", UnidadMedida.KILOGRAMO, 200),
        InsumoSeed("aceite-vegetal", "Aceite vegetal", UnidadMedida.LITRO, 800),
        InsumoSeed("sal-fina", "Sal fina", UnidadMedida.KILOGRAMO, 120),
        InsumoSeed("levadura", "Levadura", UnidadMedida.GRAMO, 2),
        InsumoSeed("carne-cerdo", "Carne de cerdo", UnidadMedida.KILOGRAMO, 900),
        InsumoSeed("coco-rallado", "Coco rallado", UnidadMedida.KILOGRAMO, 500),
        InsumoSeed("leche-polvo", "Leche en polvo", UnidadMedida.KILOGRAMO, 1200),
        InsumoSeed("hilo-bobina", "Hilo (bobina)", UnidadMedida.UNIDAD, 150),
        InsumoSeed("gasolina", "Gasolina", UnidadMedida.LITRO, 300),
        InsumoSeed("detergente-liquido", "Detergente líquido", UnidadMedida.LITRO, 400),
    )

    val PRODUCTOS = listOf(
        ProductoSeed("arroz", "Granos y cereales", "Arroz (lb)", 150, 180, nivelBajo = 50, nivelCritico = 10),
        ProductoSeed("frijoles-negros", "Granos y cereales", "Frijoles negros (lb)", 180, 220),
        ProductoSeed("azucar-refino", "Granos y cereales", "Azúcar refino (lb)", 160, 200),
        ProductoSeed("aceite-bote", "Aceites y grasas", "Aceite (botella 1 L)", 700, 850, nivelBajo = 20, nivelCritico = 5),
        ProductoSeed("cafe-mezcla", "Bebidas", "Café mezcla (paquete)", 250, 320),
        ProductoSeed("sal-paquete", "Condimentos y especias", "Sal (paquete)", 100, 130),
        ProductoSeed("pan-suave", "Panadería y dulcería", "Pan suave (unidad)", 25, 35),
        ProductoSeed("galletas-dulces", "Panadería y dulcería", "Galletas dulces (paquete)", 120, 150),
        ProductoSeed("cake", "Panadería y dulcería", "Cake (unidad)", 200, 260),
        ProductoSeed("refresco-lata", "Bebidas", "Refresco (lata)", 110, 150),
        ProductoSeed("jugo-mango", "Bebidas", "Jugo de mango (caja)", 90, 120),
        ProductoSeed("malta", "Bebidas", "Malta (botella)", 130, 170),
        ProductoSeed("leche-entera", "Lácteos", "Leche entera (litro)", 220, 280),
        ProductoSeed("queso-blanco", "Lácteos", "Queso blanco (lb)", 550, 680),
        ProductoSeed("yogurt-natural", "Lácteos", "Yogurt natural (vaso)", 80, 110),
        ProductoSeed("picadillo", "Cárnicos y embutidos", "Picadillo (lb)", 380, 460),
        ProductoSeed("pollo-congelado", "Cárnicos y embutidos", "Pollo congelado (lb)", 320, 400),
        ProductoSeed("salchichas", "Cárnicos y embutidos", "Salchichas (paquete)", 280, 350),
        ProductoSeed("pure-tomate", "Conservas", "Puré de tomate (caja)", 140, 180),
        ProductoSeed("sardinas", "Conservas", "Sardinas (lata)", 160, 200),
        ProductoSeed("comino", "Condimentos y especias", "Comino (sobre)", 60, 90),
        ProductoSeed("vinagre", "Condimentos y especias", "Vinagre (botella)", 110, 150),
        ProductoSeed("caramelos", "Confituras y snacks", "Caramelos (paquete)", 70, 100),
        ProductoSeed("jabon-bano", "Aseo personal", "Jabón de baño (unidad)", 90, 130, nivelBajo = 30, nivelCritico = 8),
        ProductoSeed("champu", "Aseo personal", "Champú (pomo)", 320, 420),
        ProductoSeed("pasta-dental", "Aseo personal", "Pasta dental (tubo)", 210, 280),
        ProductoSeed("desodorante", "Aseo personal", "Desodorante (unidad)", 260, 340),
        ProductoSeed("detergente-polvo", "Limpieza del hogar", "Detergente en polvo (paquete)", 240, 310),
        ProductoSeed("cloro", "Limpieza del hogar", "Cloro (litro)", 130, 170),
        ProductoSeed("cigarros-fuertes", "Cigarros y tabacos", "Cigarros fuertes (caja)", 150, 190),
        ProductoSeed("pan-lechon", Categorias.ELABORADO, "Pan con lechón (unidad)", 180, 280),
        ProductoSeed("dulce-coco", Categorias.ELABORADO, "Dulce de coco (unidad)", 90, 150),
    )

    /** Recetas de los 2 elaborados (clave producto → líneas). Cantidades en milésimas. */
    val RECETAS: Map<String, List<LineaRecetaSeed>> = mapOf(
        "pan-lechon" to listOf(
            LineaRecetaSeed("harina-trigo", 200),
            LineaRecetaSeed("levadura", 10_000),
            LineaRecetaSeed("carne-cerdo", 150),
            LineaRecetaSeed("aceite-vegetal", 20),
            LineaRecetaSeed("sal-fina", 5),
        ),
        "dulce-coco" to listOf(
            LineaRecetaSeed("coco-rallado", 60),
            LineaRecetaSeed("azucar-blanca", 50),
            LineaRecetaSeed("leche-polvo", 20),
        ),
    )

    val SERVICIOS = listOf(
        ServicioSeed("corte-pelo", "Corte de pelo", "Belleza", 300),
        ServicioSeed("arreglo-ropa", "Arreglo de ropa", "Costura", 250, listOf(LineaRecetaSeed("hilo-bobina", 1_000))),
        ServicioSeed("entrega-domicilio", "Entrega a domicilio", "Mensajería", 150, listOf(LineaRecetaSeed("gasolina", 300))),
        ServicioSeed("lavado-ropa", "Lavado de ropa (lb)", "Lavandería", 120, listOf(LineaRecetaSeed("detergente-liquido", 100))),
        ServicioSeed("afilado", "Afilado de cuchillos", "Reparación", 100),
        ServicioSeed("manicura", "Manicura", "Belleza", 350),
        ServicioSeed("planchado", "Planchado (pieza)", "Lavandería", 80),
        ServicioSeed("reparacion-calzado", "Reparación de calzado", "Reparación", 400),
    )

    val CLIENTES = listOf(
        ClienteSeed("María Pérez", "90051512345", "52123456"),
        ClienteSeed("Jorge Hernández", "85082323456", "53123456"),
        ClienteSeed("Ana García", "92010134567", "54123456"),
        ClienteSeed("Luis Fernández", "78123045678", "55123456"),
        ClienteSeed("Carmen Rodríguez", "95070256789", "56123456"),
        ClienteSeed("Pedro Martínez", "80091567890", "57123456"),
        ClienteSeed("Rosa López", "88110478901", "58123456"),
        ClienteSeed("Miguel Sánchez", "76032989012", "59123456"),
        ClienteSeed("Elena Díaz", "93121790123", "50123456"),
        ClienteSeed("Carlos Ruiz", "84060801234", "51123456"),
        ClienteSeed("Lucía Torres", "90112512345", "52123457"),
        ClienteSeed("Rafael Moreno", "79091823456", "53123457"),
    )

    val TARJETAS = listOf(
        TarjetaSeed("9225123456789012", "Tarjeta MLC María"),
        TarjetaSeed("9226987654321098", "Tarjeta CUP Jorge"),
    )

    val TELEFONOS = listOf(
        TelefonoSeed("52123456", "Pago María"),
        TelefonoSeed("53123456", "Pago Jorge"),
    )

    /** Prefijo de las claves de clientes eventuales (`"EVT:<nombre>"`); lo demás es nombre de fijo. */
    const val PREFIJO_EVENTUAL = "EVT:"

    /** ¿La clave es un elaborado (tiene receta)? */
    fun esElaborado(clave: String): Boolean = clave in RECETAS

    /** ¿La clave es un cliente fijo (no eventual)? */
    fun esFijo(clave: String): Boolean = !clave.startsWith(PREFIJO_EVENTUAL)

    /** Nombre mostrable del cliente a partir de su clave. */
    fun nombreCliente(clave: String): String =
        if (esFijo(clave)) clave else clave.removePrefix(PREFIJO_EVENTUAL)

    /** Un cliente fijo al azar (la clave es su nombre). */
    fun claveFija(r: Random): String = CLIENTES[r.nextInt(CLIENTES.size)].nombre

    /**
     * `n` claves de productos sin repetir, con como máximo 1 elaborado
     * (el orquestador descuenta insumos por receta y más de uno complica el stock).
     */
    fun clavesProductos(r: Random, n: Int): List<Pair<String, ClaseArticulo>> {
        val orden = PRODUCTOS.indices.shuffled(r).toMutableList()
        val elegidas = orden.take(n).toMutableList()
        val elaborados = elegidas.filter { esElaborado(PRODUCTOS[it].clave) }
        if (elaborados.size > 1) {
            val suplente = orden.first { i -> i !in elegidas && !esElaborado(PRODUCTOS[i].clave) }
            elegidas[elegidas.indexOf(elaborados[1])] = suplente
        }
        return elegidas.map { PRODUCTOS[it].clave to ClaseArticulo.PRODUCTO }
    }

    /** `n` claves de servicios sin repetir. */
    fun clavesServicios(r: Random, n: Int): List<Pair<String, ClaseArticulo>> =
        SERVICIOS.indices.shuffled(r).take(n).map { SERVICIOS[it].clave to ClaseArticulo.SERVICIO }
}
