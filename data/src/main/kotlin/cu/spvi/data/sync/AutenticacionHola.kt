package cu.spvi.data.sync

/**
 * Rechazo deliberadamente indistinguible para negocio ajeno, empleado inexistente o clave local ilegible/ausente.
 * La conexión con un empleado dado de baja solo se identifica después de que el saludo pertenezca al negocio y su
 * clave esté disponible; entonces se conserva el rechazo firmado QUITADA que ya entiende el protocolo v1.
 */
internal fun rechazoIdentidadHola(negocioCoincide: Boolean, empleadoConClave: Boolean): Rechazo? =
    if (negocioCoincide && empleadoConClave) null else Rechazo(Rechazo.DESCONOCIDA)
