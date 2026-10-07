package cu.spvi.domain.repository

import cu.spvi.domain.model.ClienteFijo
import kotlinx.coroutines.flow.Flow

/** 0.27.0 (N2): clientes fijos (se guardan al vender; aquí solo se consultan y se quitan). */
interface ClienteFijoRepository {
    /** Ordenados por nombre, con sus compras calculadas. */
    fun observar(): Flow<List<ClienteFijo>>
    /** Deja de ser cliente fijo (sus ventas no cambian). */
    suspend fun quitar(ci: String)
}
