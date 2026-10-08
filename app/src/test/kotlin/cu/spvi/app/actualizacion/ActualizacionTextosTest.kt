package cu.spvi.app.actualizacion

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ActualizacionTextosTest {
    @Test fun muestraLaUltimaConsultaCorrectaYElAvisoDeDemora() {
        val ultima = Instant.parse("2026-09-17T12:00:00Z")
        assertEquals(
            "Última comprobación correcta: 17/09/2026.",
            TextosActualizacion.ultimaComprobacion(ultima, ZoneId.of("America/Havana")),
        )
        assertEquals(
            "No se ha podido confirmar si hay actualizaciones desde hace 14 días. Conéctate y toca «Buscar ahora».",
            TextosActualizacion.avisoSinComprobar(ultima, 14),
        )
    }

    @Test fun explicaLaPrimeraConsultaYElRelojAtrasado() {
        assertEquals(
            "Aún no se ha confirmado si hay actualizaciones. Conéctate y toca «Buscar ahora».",
            TextosActualizacion.avisoSinComprobar(null, null),
        )
        assertEquals(
            "La fecha del teléfono es anterior a la última comprobación. Corrígela y vuelve a buscar.",
            TextosActualizacion.avisoSinComprobar(Instant.parse("2026-10-09T00:00:00Z"), null),
        )
    }
}
