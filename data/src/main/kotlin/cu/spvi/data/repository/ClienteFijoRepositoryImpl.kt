package cu.spvi.data.repository

import cu.spvi.core.money.Cup
import cu.spvi.data.db.SpviDatabase
import cu.spvi.domain.model.ClienteFijo
import cu.spvi.domain.repository.ClienteFijoRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 0.27.0 (N2): clientes fijos con sus compras (transferencias de ventas no anuladas con su carné). */
@Singleton
class ClienteFijoRepositoryImpl @Inject constructor(private val db: SpviDatabase) : ClienteFijoRepository {
    private val dao = db.clienteFijoDao()

    override fun observar(): Flow<List<ClienteFijo>> = combine(dao.observar(), dao.observarCompras()) { clientes, compras ->
        val porCi = compras.associateBy { it.ci }
        clientes.map { e ->
            val c = porCi[e.ci]
            ClienteFijo(
                id = e.id, nombreApellidos = e.nombreApellidos, ci = e.ci, telefono = e.telefono,
                creadoEn = Instant.ofEpochMilli(e.creadoEn), actualizadoEn = Instant.ofEpochMilli(e.actualizadoEn),
                compras = c?.compras ?: 0, total = Cup(c?.totalCent ?: 0), ultimaCompra = c?.ultima?.let(Instant::ofEpochMilli),
            )
        }
    }

    override suspend fun quitar(ci: String) { dao.quitar(ci) }
}
