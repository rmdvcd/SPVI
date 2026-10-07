package cu.spvi.app

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.spvi.app.ayuda.AyudaContenido
import cu.spvi.app.ayuda.AyudaScreen
import cu.spvi.app.ayuda.AyudaTags
import cu.spvi.app.migrar.AccionesMigrar
import cu.spvi.app.migrar.MigrarContent
import cu.spvi.app.migrar.MigrarForm
import cu.spvi.app.migrar.MigrarTags
import cu.spvi.app.migrar.MigrarUiState
import cu.spvi.app.pagos.AccionesListaPago
import cu.spvi.app.pagos.PagoElectronicoContent
import cu.spvi.app.pagos.PagoTags
import cu.spvi.app.pagos.PagoUiState
import cu.spvi.app.pagos.TipoCuentaPago
import cu.spvi.app.perfil.AccionesPerfil
import cu.spvi.app.perfil.DatosPerfilForm
import cu.spvi.app.perfil.PerfilContent
import cu.spvi.app.perfil.PerfilTags
import cu.spvi.app.perfil.PerfilUiState
import cu.spvi.app.respaldo.AccionesRespaldo
import cu.spvi.app.respaldo.ExportForm
import cu.spvi.app.respaldo.ImportForm
import cu.spvi.app.respaldo.PasosRespaldo
import cu.spvi.app.respaldo.Progreso
import cu.spvi.app.respaldo.RespaldoContent
import cu.spvi.app.respaldo.RespaldoTags
import cu.spvi.app.respaldo.RespaldoUiState
import cu.spvi.app.respaldo.ResultadoRespaldo
import cu.spvi.app.respaldo.TextosRespaldo
import cu.spvi.app.soporte.SoporteContent
import cu.spvi.app.soporte.SoporteTags
import cu.spvi.designsystem.component.SpviStepperTextos
import cu.spvi.designsystem.theme.SpviTheme
import cu.spvi.domain.model.AutorizacionMigracion
import cu.spvi.domain.model.Perfil
import cu.spvi.domain.model.TarjetaBancaria
import cu.spvi.domain.model.Telefono
import cu.spvi.domain.repository.InfoRespaldo
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Contenidos sin estado de Ajustes (Prompt 12): no requieren Hilt. */
@RunWith(AndroidJUnit4::class)
class AjustesPantallasUiTest {
    @get:Rule val rule = createComposeRule()

    // ---------- Perfil

    private val perfil = Perfil(
        "Ana", "Díaz", "85010112345",
        tarjetas = listOf(TarjetaBancaria(3, "9205000000001234")), telefonos = listOf(Telefono(4, "+5352345678")),
    )
    private var elegidas = mutableListOf<Pair<TipoCuentaPago, Long>>()
    private val accionesLista = AccionesListaPago(
        onElegir = { t, id -> elegidas += t to id }, onNuevo = {}, onEditar = { _, _ -> }, onBorrar = { _, _ -> },
        onCambiarEdicion = {}, onGuardarEdicion = {}, onCerrarEdicion = {}, onConfirmarBorrado = {}, onCancelarBorrado = {},
    )

    /** P28: la lista de Pago electrónico (única) muestra la tarjeta enmascarada y tocarla la deja en uso. */
    @Test fun pagoElectronicoTocarEligeLaTarjeta() {
        rule.setContent { SpviTheme(darkTheme = false) { PagoElectronicoContent(PagoUiState(true, perfil), {}, accionesLista) } }
        rule.onNodeWithText("•••• 1234").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(PagoTags.opcion(TipoCuentaPago.TARJETA, 3)).performClick()
        assertEquals(listOf(TipoCuentaPago.TARJETA to 3L), elegidas)
    }

    /** P28: Perfil solo tiene nombre, apellidos y carné; las tarjetas viven en Pago electrónico. */
    @Test fun perfilMuestraSoloDatosPersonales() {
        var guardar = 0
        rule.setContent {
            SpviTheme(darkTheme = false) {
                PerfilContent(PerfilUiState(true, perfil, DatosPerfilForm.desde(perfil)), {}, AccionesPerfil({}, { guardar++ }, {}))
            }
        }
        rule.onNodeWithTag(PerfilTags.GUARDAR).assertIsNotEnabled() // sin cambios
        rule.onNodeWithText("•••• 1234").assertDoesNotExist()
        assertEquals(0, guardar)
    }

    @Test fun perfilConCambiosHabilitaGuardar() {
        val form = DatosPerfilForm.desde(perfil).copy(nombre = "Ana María")
        rule.setContent {
            SpviTheme(darkTheme = false) {
                PerfilContent(PerfilUiState(true, perfil, form), {}, AccionesPerfil({}, {}, {}))
            }
        }
        rule.onNodeWithTag(PerfilTags.GUARDAR).assertIsEnabled()
    }

    // ---------- Respaldo

    private fun accionesRespaldo(onGuardar: () -> Unit = {}, onImportar: () -> Unit = {}) = AccionesRespaldo(
        onEditarExport = {}, onGuardar = onGuardar, onCompartir = {}, onElegirArchivo = onImportar,
        onContrasenaImport = {}, onConfirmarImport = {}, onCancelarImport = {},
    )

    @Test fun respaldoMuestraErroresDeContrasena() {
        rule.setContent {
            SpviTheme(darkTheme = false) {
                RespaldoContent(RespaldoUiState(export = ExportForm(true, "corta", "", mostrarErrores = true)), {}, accionesRespaldo())
            }
        }
        rule.onNodeWithText(TextosRespaldo.ERROR_CONTRASENA).assertIsDisplayed()
    }

    @Test fun respaldoBotones() {
        var guardar = 0; var importar = 0
        rule.setContent {
            SpviTheme(darkTheme = false) {
                RespaldoContent(RespaldoUiState(ExportForm(true, "clave-segura", "clave-segura")), {}, accionesRespaldo({ guardar++ }, { importar++ }))
            }
        }
        // P18 (A09): Guardar está en el paso 2 del asistente.
        rule.onNodeWithTag(RespaldoTags.GUARDAR).assertDoesNotExist()
        rule.onNodeWithContentDescription(SpviStepperTextos.SIGUIENTE).performScrollTo().performClick()
        rule.onNodeWithTag(RespaldoTags.GUARDAR).performScrollTo().performClick()
        rule.onNodeWithTag(RespaldoTags.IMPORTAR).performScrollTo().performClick()
        assertEquals(1, guardar); assertEquals(1, importar)
    }

    @Test fun respaldoProgresoDeshabilitaYResultadoSeCierra() {
        var cerrar = 0
        rule.setContent {
            SpviTheme(darkTheme = false) {
                RespaldoContent(
                    RespaldoUiState(
                        progreso = Progreso(2, 3, "Cifrando con tu contraseña…"),
                        resultado = ResultadoRespaldo(true, "Respaldo guardado", "SPVI_respaldo_2026-09-30.spvi"),
                    ),
                    {}, AccionesRespaldo(
                        onEditarExport = {}, onGuardar = {}, onCompartir = {}, onElegirArchivo = {},
                        onContrasenaImport = {}, onConfirmarImport = {}, onCancelarImport = {}, onCerrarResultado = { cerrar++ },
                    ),
                )
            }
        }
        rule.onNodeWithText("Etapa 2 de 3 · Cifrando con tu contraseña…").assertExists()
        rule.onNodeWithText("Respaldo guardado").assertIsDisplayed()
        rule.onNodeWithContentDescription(TextosRespaldo.ACEPTAR).performClick()
        assertEquals(1, cerrar)
    }

    @Test fun respaldoImportarMuestraQueArchivoEsYQueSeReemplaza() {
        val info = InfoRespaldo(3, Instant.parse("2026-09-29T15:00:00Z"), 7)
        rule.setContent {
            SpviTheme(darkTheme = false) {
                RespaldoContent(
                    RespaldoUiState(importacion = ImportForm("content://r", info, nombre = "SPVI_respaldo_2026-09-29.spvi")),
                    {}, accionesRespaldo(), zona = ZoneId.of("America/Havana"),
                )
            }
        }
        rule.onNodeWithTag(RespaldoTags.ARCHIVO).assertTextContains("Respaldo del 29/09/2026", substring = true)
        rule.onNodeWithText(TextosRespaldo.AVISO_IMPORTAR).assertIsDisplayed()
        rule.onNodeWithTag(RespaldoTags.CONTRASENA_IMPORTAR).assertIsDisplayed()
    }

    @Test fun respaldoPaso3HechoYHacerOtro() {
        var otro = 0
        rule.setContent {
            SpviTheme(darkTheme = false) {
                RespaldoContent(
                    RespaldoUiState(exportado = PasosRespaldo.textoHecho("SPVI_respaldo_2026-09-30.spvi", enviado = false)),
                    {}, AccionesRespaldo(
                        onEditarExport = {}, onGuardar = {}, onCompartir = {}, onElegirArchivo = {},
                        onContrasenaImport = {}, onConfirmarImport = {}, onCancelarImport = {}, onNuevoRespaldo = { otro++ },
                    ),
                )
            }
        }
        rule.onNodeWithContentDescription("Paso 3 de 3: Respaldo listo").assertExists()
        rule.onNodeWithContentDescription("Paso 1 de 4: Elige el archivo .spvi").assertExists()
        rule.onNodeWithTag(RespaldoTags.HECHO).assertTextContains("SPVI_respaldo_2026-09-30.spvi", substring = true)
        rule.onNodeWithTag(RespaldoTags.OTRO_RESPALDO).performScrollTo().performClick()
        assertEquals(1, otro)
    }

    /** 0.26.0 (§1): el respaldo solo se exporta como .spvi; ya no hay «Documento PDF». */
    @Test fun respaldoSinDocumentoPdf() {
        rule.setContent {
            SpviTheme(darkTheme = false) {
                RespaldoContent(
                    RespaldoUiState(), {}, AccionesRespaldo(
                        onEditarExport = {}, onGuardar = {}, onCompartir = {}, onElegirArchivo = {},
                        onContrasenaImport = {}, onConfirmarImport = {}, onCancelarImport = {},
                    ),
                )
            }
        }
        rule.onAllNodesWithText("Documento PDF").assertCountEquals(0)
    }

    // ---------- Migrar

    private fun accionesMigrar(onPedir: () -> Unit = {}) = AccionesMigrar(
        onDestino = {}, onPegarDestino = {}, onEnviar = {}, onRespaldo = {}, onAutorizacion = {}, onPegarAutorizacion = {},
        onComprobar = {}, onPedirBorrado = onPedir, onConfirmacion = {}, onConfirmarBorrado = {}, onCancelarBorrado = {},
    )

    @Test fun migrarBorrarDeshabilitadoSinAutorizacion() {
        rule.setContent {
            SpviTheme(darkTheme = false) {
                MigrarContent(MigrarUiState("SPVI:0a1b2c3d4e5f6071", MigrarForm(resultado = AutorizacionMigracion.DE_ESTE_TELEFONO)), {}, accionesMigrar())
            }
        }
        rule.onNodeWithTag(MigrarTags.RESULTADO).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(MigrarTags.BORRAR).performScrollTo().assertIsNotEnabled()
    }

    @Test fun migrarConAutorizacionPideConfirmacion() {
        var pedir = 0
        rule.setContent {
            SpviTheme(darkTheme = false) {
                MigrarContent(MigrarUiState(null, MigrarForm(resultado = AutorizacionMigracion.AUTORIZADA)), {}, accionesMigrar { pedir++ })
            }
        }
        rule.onNodeWithTag(MigrarTags.BORRAR).performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, pedir)
    }

    // ---------- Ayuda y Soporte

    @Test fun ayudaDespliegaUnTema() {
        rule.setContent { SpviTheme(darkTheme = false) { AyudaScreen(onBack = {}) } }
        val tema = AyudaContenido.temas[2]
        rule.onNodeWithTag(AyudaTags.tema(2)).performScrollTo().performClick()
        rule.onNodeWithText("1. ${tema.pasos[0]}").assertIsDisplayed()
    }

    @Test fun soporteMuestraFichaYContacto() {
        var wa = 0
        rule.setContent { SpviTheme(darkTheme = false) { SoporteContent(onBack = {}, version = "0.12.0 (12)", onWhatsapp = { wa++ }, onSms = {}) } }
        rule.onNodeWithTag(SoporteTags.FICHA).assertIsDisplayed()
        rule.onNodeWithText("Ing. Ronnie Montero Duarte").assertIsDisplayed()
        rule.onNodeWithText("91040922502").assertIsDisplayed()
        rule.onNodeWithText("51815604").assertIsDisplayed()
        rule.onNodeWithText("Desarrollo de sistemas y aplicaciones multiplataforma").assertIsDisplayed()
        rule.onNodeWithContentDescription("Escribir por WhatsApp").performScrollTo().performClick()
        assertEquals(1, wa)
    }
}
