package cu.spvi.domain.repository

import cu.spvi.core.result.AppResult
import cu.spvi.domain.service.FormatoExport
import cu.spvi.domain.service.TablaExport
import java.io.OutputStream

/** Genera el archivo (PDF/XLSX/TXT) de una o varias tablas. Implementado en :data. */
interface ExportadorDocumentos {
    val formatos: Set<FormatoExport>
    suspend fun exportar(tablas: List<TablaExport>, formato: FormatoExport, destino: OutputStream): AppResult<Unit>
}
