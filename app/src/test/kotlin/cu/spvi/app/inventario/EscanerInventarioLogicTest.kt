package cu.spvi.app.inventario

import cu.spvi.core.money.Cup
import cu.spvi.core.quantity.Cantidad
import cu.spvi.domain.model.UnidadMedida
import cu.spvi.core.result.AppError.Regla
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Instante fijo de las pruebas (los modelos llevan `creadoEn`). */
private val AHORA: Instant = Instant.parse("2026-01-01T00:00:00Z")

/**
 * Lógica pura del escáner OCR: reconstrucción de la tabla por coordenadas y parseo de valores.
 * El reconocimiento (ML Kit) no entra aquí: llega como [LineaOcr] con su caja en píxeles.
 */
class EscanerInventarioLogicTest {

    private fun linea(texto: String, izquierda: Int, arriba: Int, ancho: Int = 200, alto: Int = 30) =
        LineaOcr(texto, izquierda, arriba, izquierda + ancho, arriba + alto)

    @Test fun tablaDeDosColumnasSeReconstruyePorGeometria() {
        val lineas = listOf(
            linea("Refresco de lata", 40, 100, 260),
            linea("48", 700, 100, 40),
            linea("Galletas de chocolate", 40, 160, 340),
            linea("12", 700, 160, 40),
        )
        val t = tablaOcr(lineas)
        assertTrue(t.estructura)
        assertEquals(2, t.columnas)
        assertEquals(
            listOf(listOf("Refresco de lata", "48"), listOf("Galletas de chocolate", "12")),
            t.filas,
        )
        assertNull(t.encabezado)
    }

    @Test fun textoSueltoSinColumnasQuedaUnaLineaPorRegistro() {
        val lineas = listOf(
            linea("Lista de la bodega, marzo", 20, 100, 880),
            linea("Revisar existencias el lunes", 20, 170, 860),
        )
        val t = tablaOcr(lineas)
        assertFalse(t.estructura)
        assertEquals(1, t.columnas)
        assertEquals(listOf(listOf("Lista de la bodega, marzo"), listOf("Revisar existencias el lunes")), t.filas)
    }

    @Test fun elEncabezadoReconocidoNoEsUnRegistroYGuiaElMapeo() {
        val lineas = listOf(
            linea("Producto", 40, 40, 100),
            linea("Cantidad", 700, 40, 90),
            linea("Precio costo", 900, 40, 110),
            linea("Refresco de lata", 40, 100, 260),
            linea("48", 700, 100, 40),
            linea("120.00", 900, 100, 70),
            linea("Galletas", 40, 160, 120),
            linea("12", 700, 160, 40),
            linea("85.50", 900, 160, 60),
        )
        val t = tablaOcr(lineas)
        assertTrue(t.estructura)
        assertEquals(3, t.columnas)
        assertEquals(listOf("Producto", "Cantidad", "Precio costo"), t.encabezado)
        assertEquals(
            listOf(listOf("Refresco de lata", "48", "120.00"), listOf("Galletas", "12", "85.50")),
            t.filas,
        )
        val elegidos = listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD, CampoEscaneo.COSTO, CampoEscaneo.VENTA)
        // El mapeo es por columna: VENTA se eligió pero ninguna columna la alimenta (queda vacía en la revisión).
        assertEquals(
            listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD, CampoEscaneo.COSTO),
            mapeoAutomatico(t, elegidos),
        )
    }

    @Test fun sinEncabezadoElMapeoUsaLosCamposElegidosEnOrden() {
        val tabla = TablaOcr(filas = listOf(listOf("Refresco", "48")), columnas = 2, estructura = true)
        assertEquals(
            listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD),
            mapeoAutomatico(tabla, listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD)),
        )
    }

    @Test fun lasFilasSeArmanConElMapeoYLosCamposElegidosSiempreExisten() {
        val tabla = TablaOcr(
            filas = listOf(listOf("Refresco de lata", "48", "no usar"), listOf("", "12", "")),
            columnas = 3,
            estructura = true,
        )
        val filas = filasDesde(
            tabla,
            listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD, null),
            listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD, CampoEscaneo.COSTO),
        )
        assertEquals(2, filas.size)
        assertEquals(listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD, CampoEscaneo.COSTO), filas[0].valores.keys.toList())
        assertEquals("Refresco de lata", filas[0].valores[CampoEscaneo.NOMBRE])
        assertEquals("48", filas[0].valores[CampoEscaneo.CANTIDAD])
        assertEquals("", filas[0].valores[CampoEscaneo.COSTO])
        assertEquals("12", filas[1].valores[CampoEscaneo.CANTIDAD])
    }

    @Test fun dosColumnasAlMismoCampoSeUnenYLasFilasVaciasSeOmiten() {
        val unidas = filasDesde(
            TablaOcr(filas = listOf(listOf("Refresco", "de lata")), columnas = 2, estructura = true),
            listOf(CampoEscaneo.NOMBRE, CampoEscaneo.NOMBRE),
            listOf(CampoEscaneo.NOMBRE),
        )
        assertEquals("Refresco de lata", unidas.single().valores[CampoEscaneo.NOMBRE])
        assertTrue(
            filasDesde(
                TablaOcr(filas = listOf(listOf("", ""), listOf("Sal", "")), columnas = 2, estructura = true),
                listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD),
                listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CANTIDAD),
            ).single().valores[CampoEscaneo.NOMBRE] == "Sal",
        )
    }

    @Test fun unidadesDeProductoComoEnElFormulario() {
        assertEquals(12L, unidadesOcr(" 12 "))
        assertEquals(48L, unidadesOcr("48"))
        assertEquals(12L, unidadesOcr("12 unidades"))
        assertEquals(1200L, unidadesOcr("1,200"))
        assertNull(unidadesOcr("1,5"))
        assertNull(unidadesOcr("1.200"))
        assertNull(unidadesOcr(""))
        assertNull(unidadesOcr("sin cantidad"))
    }

    @Test fun cantidadesDeInsumoConDecimales() {
        assertEquals(Cantidad(1500), milesimasOcr("1,5"))
        assertEquals(Cantidad(1200), milesimasOcr("1,2 kg"))
        assertEquals(Cantidad(2000), milesimasOcr("2"))
        assertEquals(Cantidad(1200), milesimasOcr("1.200")) // convención de la app: el punto es decimal (1,2)
        assertNull(milesimasOcr("mucho"))
    }

    @Test fun importesConSimboloYUnidad() {
        assertEquals(Cup(145000), precioOcr("1,450.00 CUP"))
        assertEquals(Cup(15000), precioOcr("$ 150"))
        assertEquals(Cup(8550), precioOcr("85.50"))
        assertEquals(Cup(8550), precioOcr("85,50"))
        assertEquals(Cup(12000), precioOcr("120 CUP/unidad"))
        assertNull(precioOcr("—"))
    }

    @Test fun revisionDeProductoConVentaBajoElCosto() {
        val fila = FilaEscaneo(
            mapOf(
                CampoEscaneo.NOMBRE to "Refresco",
                CampoEscaneo.CATEGORIA to "Otros",
                CampoEscaneo.CANTIDAD to "48",
                CampoEscaneo.COSTO to "100.00",
                CampoEscaneo.VENTA to "80.00",
            ),
        )
        assertEquals(
            ErrorEscaneo.ValorInvalido(2, CampoEscaneo.VENTA, Regla.RANGO),
            revisionDeFila(2, TipoEscaneo.PRODUCTO, fila, AHORA),
        )
    }

    @Test fun revisionDeProductoIncompleto() {
        val sinNombre = FilaEscaneo(mapOf(CampoEscaneo.NOMBRE to "  ", CampoEscaneo.CANTIDAD to "1"))
        assertEquals(ErrorEscaneo.FaltaNombre(3), revisionDeFila(3, TipoEscaneo.PRODUCTO, sinNombre, AHORA))
        val fila = { cantidad: String, categoria: String ->
            FilaEscaneo(
                mapOf(
                    CampoEscaneo.NOMBRE to "Refresco",
                    CampoEscaneo.CATEGORIA to categoria,
                    CampoEscaneo.CANTIDAD to cantidad,
                    CampoEscaneo.COSTO to "1.00",
                    CampoEscaneo.VENTA to "2.00",
                ),
            )
        }
        assertEquals(ErrorEscaneo.ValorInvalido(4, CampoEscaneo.CANTIDAD), revisionDeFila(4, TipoEscaneo.PRODUCTO, fila("1,5", "Otros"), AHORA))
        assertEquals(
            ErrorEscaneo.ValorInvalido(5, CampoEscaneo.CATEGORIA, Regla.NO_PERMITIDO),
            revisionDeFila(5, TipoEscaneo.PRODUCTO, fila("1", "Insumos"), AHORA),
        )
        assertEquals(ErrorEscaneo.ValorInvalido(6, CampoEscaneo.CATEGORIA, Regla.REQUERIDO), revisionDeFila(6, TipoEscaneo.PRODUCTO, fila("1", ""), AHORA))
    }

    @Test fun revisionDeInsumoConUnidadYVentaOpcional() {
        val fila = FilaEscaneo(
            mapOf(
                CampoEscaneo.NOMBRE to "Leche en polvo",
                CampoEscaneo.CANTIDAD to "2,5",
                CampoEscaneo.COSTO to "120.00",
                CampoEscaneo.UNIDAD to "kg",
                CampoEscaneo.VENTA to "150.00",
            ),
        )
        assertNull(revisionDeFila(1, TipoEscaneo.INSUMO, fila, AHORA))
        val insumo = insumoDeFila(fila, AHORA)!!
        assertEquals(UnidadMedida.KILOGRAMO, insumo.unidad)
        assertEquals(Cantidad(2500), insumo.cantidad)
        assertEquals(Cup(12000), insumo.precio)
        assertEquals(Cup(15000), insumo.precioVenta)
        val sinUnidad = FilaEscaneo(
            mapOf(
                CampoEscaneo.NOMBRE to "Leche",
                CampoEscaneo.CANTIDAD to "1",
                CampoEscaneo.COSTO to "120.00",
                CampoEscaneo.UNIDAD to "caja",
            ),
        )
        assertEquals(ErrorEscaneo.ValorInvalido(2, CampoEscaneo.UNIDAD), revisionDeFila(2, TipoEscaneo.INSUMO, sinUnidad, AHORA))
        val unidadEscrita = sinUnidad.valores + (CampoEscaneo.UNIDAD to "Kilogramos")
        assertEquals(UnidadMedida.KILOGRAMO, insumoDeFila(unidadEscrita.let(::FilaEscaneo), AHORA)!!.unidad)
    }

    @Test fun elProductoElaboradoNoSeImportaComoExistencia() {
        val fila = FilaEscaneo(
            mapOf(
                CampoEscaneo.NOMBRE to "Dulce de coco",
                CampoEscaneo.CATEGORIA to "Elaborado",
                CampoEscaneo.CANTIDAD to "10",
                CampoEscaneo.COSTO to "1.00",
                CampoEscaneo.VENTA to "2.00",
            ),
        )
        assertEquals(
            ErrorEscaneo.ValorInvalido(1, CampoEscaneo.CATEGORIA, Regla.NO_PERMITIDO),
            revisionDeFila(1, TipoEscaneo.PRODUCTO, fila, AHORA),
        )
    }

    @Test fun revisionDelLoteEnteroInformaLaFilaMala() {
        val campos = mapOf(
            CampoEscaneo.CATEGORIA to "Otros",
            CampoEscaneo.CANTIDAD to "10",
            CampoEscaneo.COSTO to "5.00",
            CampoEscaneo.VENTA to "7.00",
        )
        val buena = FilaEscaneo(campos + (CampoEscaneo.NOMBRE to "Sal"))
        val mala = FilaEscaneo(campos + (CampoEscaneo.NOMBRE to ""))
        assertNull(revisionDeLote(TipoEscaneo.PRODUCTO, listOf(buena), AHORA))
        assertEquals(ErrorEscaneo.FaltaNombre(2), revisionDeLote(TipoEscaneo.PRODUCTO, listOf(buena, mala), AHORA))
    }

    @Test fun lasColumnasQueElOcrPegaEnUnaLineaSeSeparanPorHuecos() {
        // En el teléfono, ML Kit devolvió el encabezado como una sola línea: «Nombre Categoria».
        val celdas = celdasDeLinea(
            listOf(
                LineaOcr("Nombre", 40, 40, 130, 70),
                LineaOcr("Categoria", 272, 40, 350, 70),
            ),
        )
        assertEquals(listOf("Nombre", "Categoria"), celdas.map { it.texto })
        assertEquals(272, celdas[1].izquierda)
        assertEquals(350, celdas[1].derecha)
    }

    @Test fun unHuecoDePalabraNoSeparaLaCelda() {
        // Del teléfono: «Jabon» + «de tocador» con 26 px de hueco y celdas de 20 px de alto (Consolas 22).
        val celdas = celdasDeLinea(
            listOf(
                LineaOcr("Jabon", 48, 285, 120, 305),
                LineaOcr("de tocador", 146, 285, 308, 305),
            ),
        )
        assertEquals(listOf("Jabon de tocador"), celdas.map { it.texto })
    }

    @Test fun lasPalabrasDeUnaMismaCeldaNoSeSeparan() {
        val celdas = celdasDeLinea(
            listOf(
                LineaOcr("Precio", 900, 40, 990, 70),
                LineaOcr("costo", 1000, 40, 1080, 70),
            ),
        )
        assertEquals(listOf("Precio costo"), celdas.map { it.texto })
        assertEquals(900, celdas.single().izquierda)
        assertEquals(1080, celdas.single().derecha)
        assertEquals(1, celdasDeLinea(listOf(LineaOcr("Sal", 10, 10, 60, 40))).size)
    }

    @Test fun unNombreLargoNoSeComeLaColumnaVecina() {
        // En una lista impresa el nombre desborda su columna; las columnas se alinean por su borde izquierdo.
        val lineas = listOf(
            linea("Arroz grano largo", 40, 40, 290),
            linea("Alimentos", 272, 40, 110),
            linea("Aceite girasol 1L", 40, 100, 290),
            linea("Bebidas", 272, 100, 90),
        )
        val t = tablaOcr(lineas)
        assertTrue(t.estructura)
        assertEquals(2, t.columnas)
        assertEquals(
            listOf(listOf("Arroz grano largo", "Alimentos"), listOf("Aceite girasol 1L", "Bebidas")),
            t.filas,
        )
    }

    @Test fun elTituloAntesDelEncabezadoNoEsRegistroYElMapeoLoUsa() {
        val lineas = listOf(
            linea("Inventario de productos", 40, 30, 460), // título: una sola celda, más ancha que una columna
            linea("Nombre", 40, 110, 90),
            linea("Categoria", 272, 110, 78),
            linea("Cantidad", 504, 110, 96),
            linea("Arroz grano largo", 40, 170, 210),
            linea("Alimentos", 272, 170, 110),
            linea("12", 504, 170, 26),
        )
        val t = tablaOcr(lineas)
        assertTrue(t.estructura)
        assertEquals(3, t.columnas)
        assertEquals(listOf("Nombre", "Categoria", "Cantidad"), t.encabezado)
        assertEquals(listOf(listOf("Arroz grano largo", "Alimentos", "12")), t.filas)
        assertEquals(
            listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CATEGORIA, CampoEscaneo.CANTIDAD),
            mapeoAutomatico(t, listOf(CampoEscaneo.NOMBRE, CampoEscaneo.CATEGORIA, CampoEscaneo.CANTIDAD)),
        )
    }
}
