package cu.spvi.data

import cu.spvi.data.dto.PreferenciasDto
import cu.spvi.data.dto.toDomain
import cu.spvi.data.dto.toDto
import cu.spvi.data.repository.ConfiguracionInicialRepositoryImpl.Companion.codificar
import cu.spvi.data.repository.ConfiguracionInicialRepositoryImpl.Companion.decodificar
import cu.spvi.domain.model.ConfiguracionInicial
import cu.spvi.domain.model.PasoConfiguracion
import cu.spvi.domain.model.Preferencias
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfiguracionInicialCodecTest {

    @Test fun idaYVuelta() {
        val c = ConfiguracionInicial(setOf(PasoConfiguracion.ALERTAS, PasoConfiguracion.PRUEBA), camaraSolicitada = true)
        assertEquals(c, decodificar(codificar(c)))
    }

    @Test fun ausenteOIlegibleEsEstadoInicial() {
        assertEquals(ConfiguracionInicial(), decodificar(null))
        assertEquals(ConfiguracionInicial(), decodificar("{roto"))
    }

    @Test fun pasosDesconocidosSeIgnoran() {
        val c = decodificar("""{"confirmados":["ALERTAS","PASO_FUTURO"]}""")
        assertEquals(setOf(PasoConfiguracion.ALERTAS), c.confirmados)
    }

    @Test fun preferenciasSinConsentimientoVanYVienen() {
        val p = Preferencias()
        assertEquals(p, p.toDto().toDomain())
    }
}
