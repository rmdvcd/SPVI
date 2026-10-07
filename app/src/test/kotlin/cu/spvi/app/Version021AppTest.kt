package cu.spvi.app

import cu.spvi.app.ayuda.AyudaContenido
import cu.spvi.app.inicio.TipoVenta
import cu.spvi.app.inicio.tiposVenta
import cu.spvi.app.licencia.LicenciaForm
import cu.spvi.app.licencia.TextosLicencia
import cu.spvi.app.licencia.secundariasSugeridas
import cu.spvi.app.vinculacion.VinculacionLogic
import cu.spvi.domain.model.Licencia
import cu.spvi.domain.model.Modulo
import cu.spvi.domain.model.PermisoEmpleado
import cu.spvi.domain.model.PermisosApp
import cu.spvi.domain.model.TipoApp
import cu.spvi.domain.model.TipoLicencia
import cu.spvi.licencia.InstalledLicense
import cu.spvi.licencia.LicenseState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.21.0: textos y reglas puras de la capa de presentación (selector de secundarias, módulos, ayuda). */
class Version021AppTest {
    private val prueba = Licencia(LicenseState.Trial(7), "SPVI:x", true, "h")

    @Test fun `C4 selector de secundarias y precio total`() {
        val f = LicenciaForm(tipo = TipoLicencia.MENSUAL).conSecundarias(2)
        assertEquals(8_000L, f.precio)
        assertTrue(TextosLicencia.precioTotal(f).startsWith("Total: "))
        assertTrue(TextosLicencia.precioTotal(f).contains("2 ×"))
        assertEquals(10, f.conSecundarias(50).secundarias)
        assertEquals(0, f.conSecundarias(-1).secundarias)
        assertEquals(2, f.aInput().secundarias)
        assertEquals("1 app secundaria", TextosLicencia.secundarias(1))
    }

    @Test fun `C4 secundarias sugeridas - las de la licencia o las del tour`() {
        assertEquals(3, prueba.secundariasSugeridas(3))
        assertEquals(0, prueba.secundariasSugeridas(0))
        val instalada = InstalledLicense("id", TipoLicencia.ANUAL, Instant.EPOCH, null, secundarias = 4)
        assertEquals(4, prueba.copy(instalada = instalada).secundariasSugeridas(1))
    }

    @Test fun `C4 titulo de la lista con el limite de la licencia`() {
        assertEquals("Secundarias (1 de 2)", VinculacionLogic.tituloLista(1, 2))
    }

    @Test fun `C12 la ayuda oculta los temas de modulos desactivados`() {
        val todos = AyudaContenido.temasPara(Modulo.TODOS).map { it.titulo }
        assertEquals(AyudaContenido.temas.map { it.titulo }, todos)
        val soloServicios = AyudaContenido.temasPara(setOf(Modulo.SERVICIOS)).map { it.titulo }
        assertTrue("Servicios" in soloServicios && "Vender" in soloServicios)
        assertTrue("Agregar productos" !in soloServicios && "Insumos y elaborados" !in soloServicios)
        val soloInventario = AyudaContenido.temasPara(setOf(Modulo.INVENTARIO)).map { it.titulo }
        assertTrue("Vender" !in soloInventario && "Servicios" !in soloInventario && "Agregar productos" in soloInventario)
    }

    @Test fun `C12 tipos de venta segun permisos y modulos`() {
        assertEquals(listOf(TipoVenta.VENTA, TipoVenta.SERVICIO), tiposVenta(PermisosApp.PRINCIPAL))
        assertEquals(listOf(TipoVenta.SERVICIO), tiposVenta(PermisosApp.PRINCIPAL.copy(modulos = setOf(Modulo.SERVICIOS))))
        assertEquals(emptyList<TipoVenta>(), tiposVenta(PermisosApp(TipoApp.SECUNDARIA, setOf(PermisoEmpleado.EXPORTAR))))
    }
}
