package cu.spvi.licencia.contract

import kotlinx.serialization.Serializable

/** Datos del solicitante (objeto anidado `usuario`; GL acepta además los mismos campos en la raíz). */
@Serializable
data class DatosUsuario(
    val nombre: String,
    val apellidos: String,
    val ci: String,
    val telefono: String,
    val via: Via,
)
