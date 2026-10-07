package cu.spvi.designsystem

import android.view.HapticFeedbackConstants
import cu.spvi.designsystem.component.SpviHaptics
import cu.spvi.designsystem.component.SpviStepperTextos
import cu.spvi.designsystem.component.SpviTab
import cu.spvi.designsystem.component.SpviValidacion
import cu.spvi.designsystem.component.TipoHaptico
import cu.spvi.designsystem.theme.SpviTypography
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/** P18: partes puras de los componentes nuevos (sin Compose ni dispositivo). */
class P18ComponentesTest {
    @Test fun hapticaUsaConfirmYRejectDesdeAndroid11YEquivalentesAntes() {
        assertEquals(HapticFeedbackConstants.CONFIRM, SpviHaptics.constante(TipoHaptico.Exito, 30))
        assertEquals(HapticFeedbackConstants.REJECT, SpviHaptics.constante(TipoHaptico.Error, 35))
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, SpviHaptics.constante(TipoHaptico.Exito, 26))
        assertEquals(HapticFeedbackConstants.LONG_PRESS, SpviHaptics.constante(TipoHaptico.Error, 29))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, SpviHaptics.constante(TipoHaptico.Seleccion, 26))
    }

    @Test fun textosDelAsistente() {
        assertEquals("Paso 2 de 3", SpviStepperTextos.paso(2, 3))
        assertEquals("Paso 2 de 3: Enviar solicitud", SpviStepperTextos.anuncio(2, 3, "Enviar solicitud"))
    }

    @Test fun pestanaConContadorLoLeeTrasElNombre() {
        assertEquals("Insumos", SpviTab("Insumos").texto)
        assertEquals("Insumos (2)", SpviTab("Insumos", 2).texto)
    }

    // ---- Pregunta 2 (opción 2): texto de lectura +1sp ----

    @Test fun tipografiaDeLecturaSubida1sp() {
        assertEquals(14f, SpviTypography.bodyMedium.fontSize.value)
        assertEquals(13f, SpviTypography.bodySmall.fontSize.value)
        assertEquals(15f, SpviTypography.bodyLarge.fontSize.value)
        assertEquals(11f, SpviTypography.labelSmall.fontSize.value)
    }

    // ---- A16: regla común de validación ----

    @Test fun sinValidarAlSalirElErrorSeVeAlMomento() {
        assertTrue(SpviValidacion.errorVisible(hayError = true, validarAlSalir = false, forzar = false, salio = false))
        assertFalse(SpviValidacion.errorVisible(hayError = false, validarAlSalir = false, forzar = true, salio = true))
    }

    @Test fun conValidarAlSalirSoloTrasSalirDelCampoEditadoOAlGuardar() {
        assertFalse(SpviValidacion.errorVisible(true, validarAlSalir = true, forzar = false, salio = false))
        assertTrue(SpviValidacion.errorVisible(true, validarAlSalir = true, forzar = false, salio = true))
        assertTrue(SpviValidacion.errorVisible(true, validarAlSalir = true, forzar = true, salio = false))
    }

    @Test fun campoVacioSinFocoSoloSeMarcaAlGuardar() {
        assertFalse(SpviValidacion.errorVisible(true, true, forzar = false, salio = true, vacioSinFoco = true))
        assertTrue(SpviValidacion.errorVisible(true, true, forzar = true, salio = true, vacioSinFoco = true))
    }

    @Test fun salirSinHaberEscritoNoCuentaYUnaVezSalidoSeQueda() {
        assertFalse(SpviValidacion.salioTrasEditar(salio = false, teniaFoco = true, tieneFoco = false, editado = false))
        assertFalse(SpviValidacion.salioTrasEditar(salio = false, teniaFoco = false, tieneFoco = true, editado = true))
        assertTrue(SpviValidacion.salioTrasEditar(salio = false, teniaFoco = true, tieneFoco = false, editado = true))
        assertTrue(SpviValidacion.salioTrasEditar(salio = true, teniaFoco = false, tieneFoco = true, editado = true))
    }
}
