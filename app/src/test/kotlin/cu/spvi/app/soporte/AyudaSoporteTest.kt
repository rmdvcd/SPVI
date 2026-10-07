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
        assertTrue(temas.size in 6..12)
        assertEquals(temas.size, temas.map { it.titulo }.toSet().size)
        temas.forEach { t ->
            assertTrue(t.titulo, t.pasos.isNotEmpty() && t.pasos.size <= 5)
            t.pasos.forEach { p -> assertTrue("Paso demasiado largo: $p", p.length <= 110) }
        }
        // Cubre lo imprescindible para un usuario sin conocimientos previos.
        val todo = temas.joinToString(" ") { it.titulo + " " + it.pasos.joinToString(" ") }
        listOf("Abrir turno", "Nueva venta", "Inventario", "Respaldo", "Licencia", "Migrar", "Soporte").forEach {
            assertTrue("Falta $it", todo.contains(it))
        }
    }
}
