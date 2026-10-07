package cu.spvi.app

import cu.spvi.app.caja.FondoApertura
import cu.spvi.app.inicio.TextosInicio
import cu.spvi.core.money.Cup
import cu.spvi.domain.model.EstadoSecundaria
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.26.0 (P73 §4): qué diálogo de apertura ve cada app y los avisos de Inicio en la principal. */
class FondoAperturaTest {
    @Test fun laPrincipalYLasSecundariasDeUnaPrincipal025EscribenSuFondo() {
        assertEquals(FondoApertura.Libre, FondoApertura.de(EstadoSecundaria()))
        assertEquals(FondoApertura.Libre, FondoApertura.de(EstadoSecundaria(vinculada = true, principalAsignaFondo = false, fondoAsignado = Cup.ZERO)))
    }

    @Test fun laSecundariaAbreConElFondoAsignadoOLoPide() {
        val s = EstadoSecundaria(vinculada = true, principalAsignaFondo = true)
        assertEquals(FondoApertura.SinAsignar(pedido = false), FondoApertura.de(s))
        assertEquals(FondoApertura.SinAsignar(pedido = true), FondoApertura.de(s.copy(fondoPedido = true)))
        assertEquals(FondoApertura.Asignado(Cup.ofPesos(800)), FondoApertura.de(s.copy(fondoAsignado = Cup.ofPesos(800), fondoPedido = true)))
    }

    @Test fun avisosDeInicioEnLaPrincipal() {
        assertEquals("Luis pide abrir turno: asígnale el fondo", TextosInicio.aperturas(listOf("Luis")))
        assertEquals("2 empleados piden abrir turno: asígnales el fondo", TextosInicio.aperturas(listOf("Luis", "Ana")))
        assertEquals("Actualiza la app de Luis", TextosInicio.desactualizadas(listOf("Luis")))
        assertEquals("Actualiza las apps de Luis, Ana y Eva", TextosInicio.desactualizadas(listOf("Luis", "Ana", "Eva")))
    }
}
