package cu.spvi.app.common

import androidx.compose.ui.graphics.vector.ImageVector
import cu.spvi.app.inventario.FormatoSalida
import cu.spvi.designsystem.icon.SpviIcons
import cu.spvi.domain.service.FormatoExport

/** 0.27.0 (T7): PDF y Excel llevan su icono en todas las hojas de exportar. Imagen y Tarjetas conservan los suyos. */
fun iconoFormato(f: FormatoExport): ImageVector = if (f == FormatoExport.PDF) SpviIcons.Pdf else SpviIcons.Excel

fun iconoFormato(f: FormatoSalida): ImageVector? = when (f) {
    FormatoSalida.PDF -> SpviIcons.Pdf
    FormatoSalida.EXCEL -> SpviIcons.Excel
    else -> null
}
