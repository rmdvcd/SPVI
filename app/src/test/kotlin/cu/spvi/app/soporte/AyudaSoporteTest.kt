package cu.spvi.app.soporte

import cu.spvi.app.ayuda.AyudaContenido
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AyudaSoporteTest {
    @Test fun fichaDeSoporteExacta() {
        assertEquals(
            listOf(
                "Desarrollador" to "Ing. Ronnie Montero Duarte",
                "CI" to "91040922502",
                "Teléfono" to "51815604",
                "Especialidad" to "Desarrollo de sistemas y aplicaciones multiplataforma",
            ),
            SoporteInfo.filas,
        )
    }

    @Test fun manualBreveYCompleto() {
        val temas = AyudaContenido.temas
        // 0.31.0 (P7): el manual pasó a organizarse en secciones, así que caben más temas sin ser menos breve
        // (13 repartidos en 6 secciones se leen antes que 13 en una lista). El techo sube de 12 a 14.
        assertTrue(temas.size in 6..14)
        assertEquals(temas.size, temas.map { it.titulo }.toSet().size)
        // Cada sección existe y tiene temas; los temas de una sección son de esa sección.
        val secciones = AyudaContenido.seccionesPara(cu.spvi.domain.model.Modulo.TODOS)
        assertTrue(secciones.isNotEmpty())
        secciones.forEach { (c, ts) ->
            assertTrue(c.name, ts.isNotEmpty())
            ts.forEach { assertEquals(c, it.categoria) }
        }
        temas.forEach { t ->
            assertTrue(t.titulo, t.pasos.isNotEmpty() && t.pasos.size <= 5)
            t.pasos.forEach { p -> assertTrue("Paso demasiado largo: $p", p.length <= 110) }
        }
        // Cubre lo imprescindible para un usuario sin conocimientos previos.
        val todo = temas.joinToString(" ") { it.titulo + " " + it.pasos.joinToString(" ") }
        listOf("Abrir turno", "Nueva venta", "Inventario", "Respaldo", "Licencia", "Soporte").forEach {
            assertTrue("Falta $it", todo.contains(it))
        }
    }
}
