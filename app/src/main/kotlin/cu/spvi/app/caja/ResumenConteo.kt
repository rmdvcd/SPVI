package cu.spvi.app.caja

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import cu.spvi.core.money.Cup
import cu.spvi.core.money.Money
import cu.spvi.designsystem.theme.SpviTextos
import cu.spvi.designsystem.token.SpviSpacing
import cu.spvi.domain.model.ConteoCierre
import cu.spvi.domain.service.TablasExport

/** El mismo desglose que se guarda y exporta, visible antes de aprobar el conteo de un empleado. */
@Composable
fun ResumenConteo(conteo: ConteoCierre) {
    Column(verticalArrangement = Arrangement.spacedBy(SpviSpacing.xs)) {
        Text("Conteo de cierre", style = MaterialTheme.typography.titleSmall)
        conteo.billetes.filterValues { it != 0L }.toSortedMap().forEach { (d, n) ->
            Text("$d CUP × $n = ${Money.format(Cup(d.toLong() * 100 * n))}", style = SpviTextos.datoEn(MaterialTheme.typography.bodyMedium))
        }
        Text("Otro efectivo: ${Money.format(conteo.otroEfectivo)}", style = MaterialTheme.typography.bodyMedium)
        TablasExport.filasConteo(conteo).forEach { fila ->
            Text("${fila[0]} (${fila[1]}): esperado ${fila[2]}, contado ${fila[3]}, diferencia ${fila[4]}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(if (conteo.aprobadoEn == null) "Pendiente de aprobación de inventario" else "Inventario aprobado por ${conteo.aprobadoPor}", style = MaterialTheme.typography.bodyMedium)
        if (conteo.motivo.isNotBlank()) Text("Motivo: ${conteo.motivo}", style = MaterialTheme.typography.bodyMedium)
    }
}
