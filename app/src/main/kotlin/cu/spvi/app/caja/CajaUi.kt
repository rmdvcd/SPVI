package cu.spvi.app.caja

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import cu.spvi.designsystem.theme.SpviTextos
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import cu.spvi.core.money.Cup
import cu.spvi.core.money.Money
import cu.spvi.designsystem.component.FiltroEntrada
import cu.spvi.designsystem.component.SpviDialog
import cu.spvi.designsystem.component.SpviSecondaryText
import cu.spvi.designsystem.component.SpviTab
import cu.spvi.designsystem.component.SpviTabs
import cu.spvi.designsystem.component.SpviTextButton
import cu.spvi.designsystem.component.SpviTextField
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.designsystem.token.SpviSpacing
import cu.spvi.domain.model.Arqueo
import cu.spvi.domain.model.EstadoArqueo
import cu.spvi.domain.model.MovimientoCaja
import cu.spvi.domain.model.TipoMovimientoCaja

/** 0.25.0: textos del arqueo de caja (compartidos por Inicio, Venta, Registros y la principal). */
object TextosCaja {
    const val FONDO = "Fondo de caja (CUP)"
    const val FONDO_TITULO = "Abrir turno"
    const val FONDO_TEXTO = "Escribe el efectivo con el que empieza la caja (puede ser 0)."
    const val FONDO_SUGERIDO = "Propuesto: lo contado al cerrar el turno anterior."
    /** 0.26.0 (P73 §4): en una secundaria, el fondo lo asigna el encargado. */
    const val FONDO_ASIGNADO_TEXTO = "El encargado asignó el fondo de caja de este turno."
    const val FONDO_PEDIR_TEXTO = "Pide al encargado el fondo de caja. Sin él no se puede abrir el turno."
    const val FONDO_PEDIDO_TEXTO = "Fondo pedido. Llegará al sincronizar, en cuanto el encargado lo asigne."
    const val PEDIR_FONDO = "Pedir fondo"
    const val FONDO_PEDIDO = "Fondo pedido al encargado."
    const val FONDO_FALTA = "Pide al encargado el fondo de caja para abrir el turno."
    const val CONTADO = "Efectivo contado (CUP)"
    const val CUADRA_BOTON = "Cuadra (contado = esperado)"
    const val MOVIMIENTO_TITULO = "Entrada / salida de efectivo"
    const val ENTRADA = "Entrada"
    const val SALIDA = "Salida"
    const val IMPORTE = "Importe (CUP)"
    const val MOTIVO = "Motivo"
    const val MOTIVO_AYUDA = "De 3 a 60 caracteres. No se borra: un error se corrige con el movimiento contrario."
    const val IMPORTE_INVALIDO = "Escribe un importe válido (máximo 2 decimales)."
    const val SIN_ARQUEO = "Sin arqueo (turno de una versión anterior de SPVI)"
    const val MOVIMIENTO_GUARDADO = "Movimiento de caja registrado."

    fun esperado(c: Cup) = "Efectivo esperado: ${Money.format(c)}"

    fun estado(e: EstadoArqueo?): String = when (e) {
        EstadoArqueo.CUADRA -> "Cuadra"
        EstadoArqueo.SOBRANTE -> "Sobrante"
        EstadoArqueo.FALTANTE -> "Faltante"
        null -> "Sin contar"
    }

    /** «Esperado X · Contado Y · Faltante Z» (la principal antes de aprobar un cierre). */
    fun resumen(a: Arqueo): String {
        val base = "Esperado ${Money.format(a.esperado)}"
        val c = a.contado ?: return "$base · Sin contar"
        val d = a.diferencia ?: Cup.ZERO
        return "$base · Contado ${Money.format(c)} · ${estado(a.estado)}" +
            if (d.centavos != 0L) " ${Money.format(if (d.isNegative) -d else d)}" else ""
    }

    fun tipo(t: TipoMovimientoCaja) = if (t == TipoMovimientoCaja.ENTRADA) ENTRADA else SALIDA

    fun linea(m: MovimientoCaja): String = "${tipo(m.tipo)} ${Money.format(m.importe)} · ${m.motivo}"
}

/**
 * 0.26.0 (P73 §4): cómo se fija el fondo al abrir. [Libre] = lo escribe quien abre (la principal, o una secundaria con
 * una principal 0.25). [Asignado] = el que asignó el encargado (solo lectura). [SinAsignar] = hay que pedirlo.
 */
sealed interface FondoApertura {
    data object Libre : FondoApertura
    data class Asignado(val fondo: Cup) : FondoApertura
    data class SinAsignar(val pedido: Boolean) : FondoApertura

    companion object {
        fun de(s: cu.spvi.domain.model.EstadoSecundaria): FondoApertura {
            if (!s.vinculada || !s.principalAsignaFondo) return Libre
            return s.fondoAsignado?.let(::Asignado) ?: SinAsignar(s.fondoPedido)
        }
    }
}

object CajaTags {
    const val FONDO = "caja_fondo"
    const val FONDO_ASIGNADO = "caja_fondo_asignado"
    const val CONTADO = "caja_contado"
    const val CUADRA = "caja_cuadra"
    const val IMPORTE = "caja_importe"
    const val MOTIVO = "caja_motivo"
    const val CONFIRMAR = "caja_confirmar"
}

/** Lógica pura de los diálogos (testeable sin Compose). */
object CajaForm {
    /** Texto inicial del fondo: el sugerido sin «CUP», o vacío (hay que escribirlo, aunque sea 0). */
    fun textoInicial(c: Cup?): String = c?.let { Money.format(it).removeSuffix(" CUP").replace(",", "") } ?: ""

    /** null = vacío o inválido; los negativos no valen. */
    fun importe(texto: String): Cup? = Money.parse(texto)?.takeUnless { it.isNegative }

    fun motivoValido(m: String): Boolean = m.trim().replace(Regex("\\s+"), " ").length in MovimientoCaja.MOTIVO_MIN..MovimientoCaja.MOTIVO_MAX
}

/** Color del estado del arqueo: faltante en rojo (error), el resto normal. */
@Composable
fun colorArqueo(e: EstadoArqueo?): Color =
    if (e == EstadoArqueo.FALTANTE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

/**
 * 0.25.0 (§5.2): diálogo único de apertura (Inicio y «Abrir turno» al vender). El fondo es obligatorio (puede ser 0);
 * se propone [sugerido] (lo contado al cerrar el turno anterior de esta app).
 */
@Composable
fun DialogoFondo(
    sugerido: Cup?,
    trabajando: Boolean,
    onConfirmar: (Cup) -> Unit,
    onDismiss: () -> Unit,
    fondo: FondoApertura = FondoApertura.Libre,
    onPedirFondo: () -> Unit = {},
) {
    when (fondo) {
        FondoApertura.Libre -> Unit
        is FondoApertura.Asignado -> {
            SpviDialog(
                title = TextosCaja.FONDO_TITULO, text = TextosCaja.FONDO_ASIGNADO_TEXTO, onDismiss = onDismiss,
                onConfirm = { onConfirmar(fondo.fondo) }, confirmDescription = "Abrir turno",
                confirmEnabled = !trabajando, confirmLoading = trabajando, confirmTag = CajaTags.CONFIRMAR,
            ) {
                Fila(TextosCaja.FONDO, Money.format(fondo.fondo), modifier = Modifier.testTag(CajaTags.FONDO_ASIGNADO))
            }
            return
        }
        is FondoApertura.SinAsignar -> {
            SpviDialog(
                title = TextosCaja.FONDO_TITULO,
                text = if (fondo.pedido) TextosCaja.FONDO_PEDIDO_TEXTO else TextosCaja.FONDO_PEDIR_TEXTO,
                onDismiss = onDismiss,
                onConfirm = if (fondo.pedido) null else onPedirFondo,
                confirmDescription = TextosCaja.PEDIR_FONDO, confirmIcon = SpviIcons.Enviar, confirmTag = CajaTags.CONFIRMAR,
                dismissDescription = "Cerrar",
            )
            return
        }
    }
    var texto by rememberSaveable(sugerido) { androidx.compose.runtime.mutableStateOf(CajaForm.textoInicial(sugerido)) }
    val valor = CajaForm.importe(texto)
    SpviDialog(
        title = TextosCaja.FONDO_TITULO,
        text = TextosCaja.FONDO_TEXTO,
        onDismiss = onDismiss,
        onConfirm = { valor?.let(onConfirmar) },
        confirmDescription = "Abrir turno",
        confirmEnabled = valor != null && !trabajando,
        confirmLoading = trabajando,
        confirmTag = CajaTags.CONFIRMAR,
    ) {
        SpviTextField(
            value = texto, onValueChange = { texto = it.take(16) }, label = "${TextosCaja.FONDO} *",
            supportingText = if (sugerido != null) TextosCaja.FONDO_SUGERIDO else null,
            isError = texto.isNotBlank() && valor == null, errorText = TextosCaja.IMPORTE_INVALIDO,
            filtro = FiltroEntrada.DINERO, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag(CajaTags.FONDO),
        )
    }
}

/**
 * 0.25.0 (§5.2): conteo al cerrar (o al pedir el cierre en una secundaria). Muestra en vivo el esperado y la diferencia;
 * «Cuadra» rellena el contado con el esperado. No se confirma con el campo vacío. [arqueo] null = turno sin fondo
 * (anterior a 0.25.0): se pide igualmente el contado, sin diferencia.
 */
@Composable
fun DialogoContado(
    titulo: String,
    texto: String,
    confirmar: String,
    arqueo: Arqueo?,
    trabajando: Boolean,
    onConfirmar: (Cup) -> Unit,
    onDismiss: () -> Unit,
    dismissDescription: String = "Cancelar",
) {
    var valorTexto by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val contado = CajaForm.importe(valorTexto)
    SpviDialog(
        title = titulo,
        text = texto,
        onDismiss = onDismiss,
        onConfirm = { contado?.let(onConfirmar) },
        confirmDescription = confirmar,
        confirmEnabled = contado != null && !trabajando,
        confirmLoading = trabajando,
        confirmTag = CajaTags.CONFIRMAR,
        dismissDescription = dismissDescription,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            arqueo?.let { a ->
                // 0.28.0: los importes («Efectivo esperado: 1 250.00 CUP») van en seminegrita.
                Text(
                    SpviTextos.resaltar(TextosCaja.esperado(a.esperado), Money.format(a.esperado)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SpviSecondaryText(
                    SpviTextos.resaltar(
                        "Fondo ${Money.format(a.fondo)} + efectivo ${Money.format(a.ventasEfectivo)} + entradas ${Money.format(a.entradas)} − salidas ${Money.format(a.salidas)}",
                        Money.format(a.fondo), Money.format(a.ventasEfectivo), Money.format(a.entradas), Money.format(a.salidas),
                    ),
                    maxLines = 3,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
                SpviTextField(
                    value = valorTexto, onValueChange = { valorTexto = it.take(16) }, label = "${TextosCaja.CONTADO} *",
                    isError = valorTexto.isNotBlank() && contado == null, errorText = TextosCaja.IMPORTE_INVALIDO,
                    filtro = FiltroEntrada.DINERO, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag(CajaTags.CONTADO),
                )
                if (arqueo != null) SpviTextButton(
                    TextosCaja.CUADRA_BOTON, onClick = { valorTexto = CajaForm.textoInicial(arqueo.esperado) }, icon = SpviIcons.Listo,
                    modifier = Modifier.testTag(CajaTags.CUADRA),
                )
            }
            if (arqueo != null && contado != null) {
                val a = arqueo.copy(contado = contado)
                val d = a.diferencia ?: Cup.ZERO
                // 0.28.0: el importe («Faltante: 25.00 CUP») va en seminegrita.
                val texto = "${TextosCaja.estado(a.estado)}${if (d.centavos != 0L) ": " + Money.format(if (d.isNegative) -d else d) else ""}"
                Text(
                    SpviTextos.resaltar(texto, if (d.centavos != 0L) Money.format(if (d.isNegative) -d else d) else ""),
                    color = colorArqueo(a.estado), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** 0.25.0 (§5.2): entrada o salida de efectivo (pestañas Tipo, Importe y Motivo). */
@Composable
fun DialogoMovimientoCaja(trabajando: Boolean, onConfirmar: (TipoMovimientoCaja, Cup, String) -> Unit, onDismiss: () -> Unit) {
    var tipo by rememberSaveable { mutableIntStateOf(1) }
    var importe by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var motivo by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val valor = CajaForm.importe(importe)?.takeIf { it.centavos > 0 }
    val ok = valor != null && CajaForm.motivoValido(motivo) && !trabajando
    val t = if (tipo == 0) TipoMovimientoCaja.ENTRADA else TipoMovimientoCaja.SALIDA
    SpviDialog(
        title = TextosCaja.MOVIMIENTO_TITULO,
        onDismiss = onDismiss,
        onConfirm = { if (valor != null) onConfirmar(t, valor, motivo) },
        confirmDescription = "Registrar ${TextosCaja.tipo(t).lowercase()}",
        confirmEnabled = ok,
        confirmLoading = trabajando,
        confirmTag = CajaTags.CONFIRMAR,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
            SpviTabs(listOf(SpviTab(TextosCaja.ENTRADA), SpviTab(TextosCaja.SALIDA)), tipo, { tipo = it })
            SpviTextField(
                value = importe, onValueChange = { importe = it.take(16) }, label = "${TextosCaja.IMPORTE} *",
                isError = importe.isNotBlank() && valor == null, errorText = TextosCaja.IMPORTE_INVALIDO,
                filtro = FiltroEntrada.DINERO, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag(CajaTags.IMPORTE),
            )
            SpviTextField(
                filtro = FiltroEntrada.NOMBRE,
                value = motivo, onValueChange = { motivo = it.take(MovimientoCaja.MOTIVO_MAX) }, label = "${TextosCaja.MOTIVO} *",
                supportingText = TextosCaja.MOTIVO_AYUDA,
                modifier = Modifier.fillMaxWidth().testTag(CajaTags.MOTIVO),
            )
        }
    }
}

/** Filas «Fondo · Ventas en efectivo · Entradas · Salidas · Esperado · Contado · Diferencia» (detalle del turno). */
@Composable
fun TablaArqueo(a: Arqueo?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
        if (a == null) { SpviSecondaryText(TextosCaja.SIN_ARQUEO, maxLines = 2); return@Column }
        Fila("Fondo", Money.format(a.fondo))
        Fila("Ventas en efectivo", Money.format(a.ventasEfectivo))
        Fila("Entradas", Money.format(a.entradas))
        Fila("Salidas", Money.format(a.salidas))
        Fila("Esperado", Money.format(a.esperado))
        Fila("Contado", a.contado?.let(Money::format) ?: "—")
        val d = a.diferencia
        Fila(TextosCaja.estado(a.estado), d?.let { Money.format(if (it.isNegative) -it else it) } ?: "—", colorArqueo(a.estado))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Fila(etiqueta: String, valor: String, color: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    // 0.27.1 (T2, capturas al 200 %): ni el importe («12,35 / 0.00 / CUP») ni la etiqueta («Esperad / o») se parten.
    // Si los dos no caben en una línea, el importe baja a la siguiente.
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = SpviSpacing.xs))
        Text(valor, style = SpviTextos.datoEn(MaterialTheme.typography.bodyMedium), color = color, softWrap = false, maxLines = 1)
    }
}
