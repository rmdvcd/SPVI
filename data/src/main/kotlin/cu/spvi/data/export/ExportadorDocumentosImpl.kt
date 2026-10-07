package cu.spvi.data.export

import cu.spvi.core.result.AppError
import cu.spvi.core.result.AppResult
import cu.spvi.core.result.appCatching
import cu.spvi.data.di.IoDispatcher
import cu.spvi.domain.repository.ExportadorDocumentos
import cu.spvi.domain.service.FormatoExport
import cu.spvi.domain.service.TablaExport
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class ExportadorDocumentosImpl @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) : ExportadorDocumentos {

    override val formatos: Set<FormatoExport> = FormatoExport.entries.toSet()

    override suspend fun exportar(tablas: List<TablaExport>, formato: FormatoExport, destino: OutputStream): AppResult<Unit> =
        withContext(io) {
            appCatching(onError = { if (it is java.io.IOException) AppError.Almacenamiento else AppError.Desconocido(it) }) {
                when (formato) {
                    FormatoExport.XLSX -> XlsxWriter.escribir(tablas, destino)
                    FormatoExport.PDF -> PdfWriter.escribir(tablas, destino)
                }
            }
        }
}
