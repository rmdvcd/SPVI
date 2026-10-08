package cu.spvi.data.licencia.prueba

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheLecturasTest {
    @Test fun reutilizaLecturasEnElMismoDiaYRefrescaAlCambiarElDia() {
        val cache = CacheLecturas<Int>()
        var consultas = 0
        fun leer(tiempo: Long, dia: Long) = cache.obtener(tiempo, dia) { ++consultas }

        assertEquals(1, leer(100, 20))
        assertEquals(1, leer(200, 20))
        assertEquals("misma evaluación/día: no vuelve a consultar MediaStore", 1, consultas)
        assertEquals(2, leer(300, 21))
        assertEquals(2, consultas)
    }

    @Test fun refrescaAlVencerSeisHorasYAlRetrocederElRelojMonotono() {
        val cache = CacheLecturas<Int>()
        var consultas = 0
        fun leer(tiempo: Long) = cache.obtener(tiempo, 20) { ++consultas }

        leer(1_000)
        leer(1_000 + CacheLecturas.VIDA_MS - 1)
        assertEquals(1, consultas)
        leer(1_000 + CacheLecturas.VIDA_MS)
        assertEquals(2, consultas)
        leer(500) // no reutiliza una entrada cuya marca monotónica está en el futuro
        assertEquals(3, consultas)
    }

    @Test fun releeSiCambiaElPermisoDeLectura() {
        val cache = CacheLecturas<Int>()
        var consultas = 0
        assertEquals(1, cache.obtener(10, 20, contexto = false) { ++consultas })
        assertEquals(2, cache.obtener(11, 20, contexto = true) { ++consultas })
        assertEquals(2, cache.obtener(12, 20, contexto = true) { ++consultas })
        assertEquals(2, consultas)
    }

    @Test fun escrituraActualizaLaEntradaSinInvalidarNiReconsultar() {
        val cache = CacheLecturas<Int>()
        var consultas = 0
        cache.obtener(10, 20) { ++consultas }
        cache.reemplazar(11, 20, 7)
        assertEquals(7, cache.obtener(12, 20) { ++consultas })
        assertEquals("la caché refleja la escritura de SPVI", 1, consultas)
    }

    @Test fun procesoNuevoEInvalidacionExplicitaEmpiezanConUnaLecturaFresca() {
        val cache = CacheLecturas<Int>()
        var consultas = 0
        cache.obtener(10, 20) { ++consultas }
        cache.invalidar()
        assertEquals(2, cache.obtener(11, 20) { ++consultas })

        val procesoNuevo = CacheLecturas<Int>()
        assertEquals(3, procesoNuevo.obtener(12, 20) { ++consultas })
        assertEquals(3, consultas)
    }
}
