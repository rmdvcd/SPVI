package cu.spvi.data.sync

import cu.spvi.core.time.Clock
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlConexionesTest {
    private var ahora = Instant.parse("2026-10-08T12:00:00Z")
    private val control = ControlConexiones(Clock { ahora })

    @Test fun limitaLosSaludosPendientesPorIpYEmpleado() {
        val intentos = List(ControlConexiones.MAX_PENDIENTES_IP) { control.iniciar("192.168.1.5")!! }
        assertNull("el límite por IP evita más saludos simultáneos", control.iniciar("192.168.1.5"))

        assertTrue(intentos[0].identificar(41))
        assertTrue(intentos[1].identificar(41))
        assertFalse("un empleado no puede abrir intentos ilimitados", intentos[2].identificar(41))

        intentos.forEach { it.liberar() }
        val siguiente = control.iniciar("192.168.1.5")
        assertNotNull(siguiente)
        siguiente!!.liberar()
    }

    @Test fun aplicaEsperaExponencialYLaClaveValidaLaReinicia() {
        val primero = control.iniciar("10.0.0.7")!!
        assertTrue(primero.identificar(9))
        primero.fallar()
        assertNull(control.iniciar("10.0.0.7")) // 1 s

        ahora = ahora.plusSeconds(1)
        val segundo = control.iniciar("10.0.0.7")!!
        assertTrue(segundo.identificar(9))
        segundo.fallar()
        assertNull(control.iniciar("10.0.0.7")) // 2 s más

        ahora = ahora.plusSeconds(2)
        val tercero = control.iniciar("10.0.0.7")!!
        assertTrue(tercero.identificar(9))
        tercero.autenticar()

        val reiniciado = control.iniciar("10.0.0.7")
        assertNotNull("un intento autenticado borra el enfriamiento", reiniciado)
        reiniciado!!.liberar()
    }
}
