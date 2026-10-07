package cu.spvi.app.ayuda

/** Un tema del manual: título corto y pasos de una línea, en lenguaje llano (usuario sin conocimientos previos). */
data class TemaAyuda(val titulo: String, val pasos: List<String>)

/** Manual breve de SPVI. Texto puro (testeado): sin jerga, frases cortas, un paso por línea. */
object AyudaContenido {
    /**
     * 0.21.0 (C12): temas visibles según los módulos del negocio (lo desactivado tampoco aparece en la Ayuda).
     * Los temas generales (primeros pasos, copia, licencia…) se muestran siempre.
     */
    fun temasPara(modulos: Set<cu.spvi.domain.model.Modulo>): List<TemaAyuda> {
        val inv = cu.spvi.domain.model.Modulo.VENTAS in modulos || cu.spvi.domain.model.Modulo.INVENTARIO in modulos
        val ventas = cu.spvi.domain.model.Modulo.VENTAS in modulos
        val serv = cu.spvi.domain.model.Modulo.SERVICIOS in modulos
        return temas.filter { t ->
            when (t.titulo) {
                "Agregar productos", "Insumos y elaborados" -> inv
                "Vender", "Cobrar por transferencia", "Ver lo vendido" -> ventas || serv
                "Servicios" -> serv
                else -> true
            }
        }
    }

    const val INTRO = "SPVI te ayuda a vender, controlar tu inventario y ver cuánto ganas. Toca un tema para ver los pasos."

    val temas: List<TemaAyuda> = listOf(
        TemaAyuda(
            "Primeros pasos",
            listOf(
                "Abajo tienes 5 botones: Inicio, Inventario, Servicios, Registros y Ajustes.",
                "Desliza a la izquierda o a la derecha para pasar de una sección a otra.",
                "En Ajustes → Completar configuración escribe tus datos y elige los avisos.",
                "Tienes 7 días de prueba gratis. Después hace falta una licencia.",
                "Opcional: Ajustes → Acceso con clave pide tu huella o el PIN del teléfono al abrir SPVI.",
            ),
        ),
        TemaAyuda(
            "Agregar productos",
            listOf(
                "Ve a Inventario y toca +.",
                "Escribe el nombre, el precio de compra, el precio de venta y la cantidad.",
                "Si ya hay otro con el mismo nombre, escribe en Descripción qué lo distingue (p. ej. Lata 350 ml).",
                "Toca el botón ✓ (Guardar) arriba a la derecha.",
            ),
        ),
        TemaAyuda(
            "Vender",
            listOf(
                "Activa el turno en Inicio (o toca Abrir turno al vender). Sin turno no se puede vender.",
                "Toca Nueva venta, elige Productos o Servicios (van por separado) y la cantidad.",
                "Elige cómo te pagan: efectivo o transferencia.",
                "Al terminar el día desactiva el interruptor: SPVI guarda un resumen del turno.",
                "Si sales de SPVI con el turno abierto, verás un aviso arriba en la barra del teléfono.",
            ),
        ),
        TemaAyuda(
            "Cobrar por transferencia",
            listOf(
                "En Ajustes → Pago electrónico agrega tu teléfono y tu tarjeta.",
                "Toca el que quieras usar para cobrar: queda marcado \"En uso\".",
                "En la venta por transferencia anota los datos del cliente y el nº de transacción.",
            ),
        ),
        TemaAyuda(
            "Insumos y elaborados",
            listOf(
                "Los insumos (harina, azúcar…) están en Inventario, en la categoría Insumos.",
                "Para crear uno: Inventario → + → Escribir y elige la categoría Insumos.",
                "Si le pones precio de venta a un insumo, también lo puedes vender.",
                "Crea el producto elaborado con su receta. Al venderlo, sus insumos se descuentan solos.",
                "Verás para cuántos alcanzan los insumos; si no alcanzan, no se puede vender.",
            ),
        ),
        TemaAyuda(
            "Servicios",
            listOf(
                "Sirve para lo que ofreces que no es un producto (cortes de pelo, arreglos, entregas…).",
                "Ve a Servicios y toca +. Escribe el nombre, el tipo y el importe.",
                "Si el servicio gasta insumos, agrégalos: se descuentan al venderlo.",
                "Para venderlo: Nueva venta → Servicios. En Inicio verás los más y menos vendidos.",
            ),
        ),
        TemaAyuda(
            "Ver lo vendido",
            listOf(
                "En Registros ves ventas, servicios, transferencias, movimientos y turnos.",
                "Toca Filtrar para elegir las fechas (hoy, 7 días, este mes…) o el importe.",
                "Toca Compartir para enviar el resumen por WhatsApp u otra app.",
            ),
        ),
        TemaAyuda(
            "Guardar una copia",
            listOf(
                "Ajustes → Respaldo → Exportar y toca Siguiente.",
                "Opcional: activa Proteger con contraseña, escríbela dos veces y anótala.",
                "Toca Guardar en el teléfono o Enviar a otra app (Drive, Telegram…).",
                "Para recuperarla: Respaldo → Elegir archivo (y la contraseña, si la tiene).",
            ),
        ),
        TemaAyuda(
            "Licencia",
            listOf(
                "Ajustes → Licencia muestra cuántos días te quedan. Sigue los 3 pasos.",
                "Paso 1: revisa tus datos. Paso 2: elige el tipo, WhatsApp o SMS, y toca Solicitar. Solo pulsa Enviar.",
                "Paso 3: copia todo el mensaje que te respondan, toca Pegar y luego Activar.",
                "Solo cuenta la línea que empieza por SPVI2:. Por SMS puede llegar en 2 o 3 mensajes: pégalos todos.",
                "Para renovar con lo mismo, toca Renovar igual. Si renuevas antes de que venza no pierdes días.",
            ),
        ),
        TemaAyuda(
            "Cambiar de teléfono",
            listOf(
                "Ajustes → Migrar y sigue los 4 pasos en orden.",
                "No borres este teléfono hasta que el nuevo tenga tus datos y su licencia.",
                "Una app principal no pasa a secundaria: exporta el respaldo y borra los datos de SPVI.",
            ),
        ),
        TemaAyuda(
            "Apps de tus empleados",
            listOf(
                "En tu teléfono: Ajustes → Apps vinculadas → +. Tu licencia dice cuántas secundarias puedes tener.",
                "En el del empleado: Usar esta app como secundaria, escribe su teléfono y escanea tu código (misma wifi).",
                "Su teléfono va en su QR de cobro: el SMS de la transferencia le llega a él.",
                "El empleado no cierra su turno: lo solicita y tú lo apruebas o rechazas. Sin red sigue vendiendo.",
                "Toca su app para sus permisos, su tarjeta de cobro o «Pedir cierre» de su turno abierto.",
            ),
        ),
        TemaAyuda(
            "¿Necesitas ayuda?",
            listOf("Ajustes → Soporte: escribe al desarrollador por WhatsApp o SMS."),
        ),
    )
}
