package cu.spvi.domain.repository

import cu.spvi.core.result.AppResult
import cu.spvi.domain.model.CodigoVinculacion
import cu.spvi.domain.model.Empleado
import cu.spvi.domain.model.EstadoPrincipal
import cu.spvi.domain.model.EstadoSecundaria
import cu.spvi.domain.model.LicenciaPrincipal
import cu.spvi.domain.model.ModoSincronizacion
import cu.spvi.domain.model.PermisoEmpleado
import cu.spvi.domain.model.PermisosApp
import cu.spvi.domain.model.TipoApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * P37: tipo de esta app (Principal/Secundaria) y lo que puede hacer. Lo persiste :data (DataStore cifrado).
 * [tipo] arranca en PRINCIPAL: las instalaciones de siempre no cambian.
 */
interface TipoAppRepository {
    val tipo: StateFlow<TipoApp>
    val permisos: Flow<PermisosApp>
    /** Solo en una secundaria: la licencia de la principal recibida en la última sincronización (null en la principal). */
    val licenciaPrincipal: Flow<LicenciaPrincipal?>
}

/** P37: lado de la app PRINCIPAL. Las secundarias se llaman «empleados» porque cada una es la app de una persona. */
interface PrincipalRepository {
    fun observarEmpleados(): Flow<List<Empleado>>
    val estado: StateFlow<EstadoPrincipal>

    /** Crea la secundaria y su QR (un solo uso, 10 min). Errores: Validacion("nombre"), SinPermiso (licencia/límite). */
    suspend fun agregarEmpleado(nombre: String, permisos: Set<PermisoEmpleado>): AppResult<CodigoVinculacion>
    /** QR nuevo para la misma persona (teléfono cambiado o QR vencido). Anula la clave anterior. */
    suspend fun nuevoCodigo(empleadoId: Long): AppResult<CodigoVinculacion>
    suspend fun cambiarPermisos(empleadoId: Long, permisos: Set<PermisoEmpleado>): AppResult<Unit>
    /** 0.20.0 (H4): tarjeta y teléfono con los que cobra ese empleado (null = los predeterminados del dueño). */
    suspend fun cambiarCobro(empleadoId: Long, tarjetaId: Long?, telefonoId: Long?): AppResult<Unit>
    /**
     * 0.20.0 (H5): pide cerrar el turno abierto de ese empleado. Se le avisa al momento si está conectado o al
     * conectar; su app lo cierra en cuanto no haya una venta en curso (P43: nunca durante una venta).
     */
    suspend fun pedirCierre(empleadoId: Long): AppResult<Unit>
    /** 0.21.0 (C6): aprueba la solicitud de cierre del empleado (= [pedirCierre]: se cierra al conectar, nunca en una venta). */
    suspend fun aprobarCierre(empleadoId: Long): AppResult<Unit>
    /** 0.21.0 (C6): rechaza la solicitud; se le avisa en la próxima sincronización y su turno sigue abierto. */
    suspend fun rechazarCierre(empleadoId: Long): AppResult<Unit>
    /**
     * 0.26.0 (P73 §4): fondo de caja del PRÓXIMO turno de esa secundaria (puede ser 0). Sin él, su app no abre turno.
     * Resuelve su petición «Pedir fondo»; se le envía al momento si está conectada o al conectar. Se gasta al abrir.
     */
    suspend fun asignarFondo(empleadoId: Long, fondo: cu.spvi.core.money.Cup): AppResult<Unit>
    /** 0.26.0: fondo que se propone al asignar: lo contado al cerrar el último turno de esa secundaria (o su fondo). */
    suspend fun fondoSugerido(empleadoId: Long): cu.spvi.core.money.Cup?
    /** Quita la secundaria: deja de sincronizar y, al conectarse, borra sus datos. Sus ventas ya recibidas se quedan. */
    suspend fun quitarEmpleado(empleadoId: Long): AppResult<Unit>
}

/** P37: lado de la app SECUNDARIA. */
interface SecundariaRepository {
    val estado: StateFlow<EstadoSecundaria>

    /**
     * Lee el QR de la principal, acuerda la clave y descarga el catálogo. Solo si todo sale bien se borran los datos
     * propios de este teléfono y la app pasa a SECUNDARIA (si algo falla, no se toca nada).
     */
    suspend fun vincular(contenidoQr: String, telefono: String): AppResult<Unit>
    /**
     * 0.21.0 (C6, P46 «a»): el empleado no cierra su turno: lo solicita. Se envía en la próxima sincronización (sin
     * conexión, espera) y el empleado sigue vendiendo hasta que el dueño lo apruebe.
     */
    suspend fun solicitarCierre(contado: cu.spvi.core.money.Cup): AppResult<Unit>
    /**
     * 0.26.0 (P73 §4): pide al dueño el fondo para abrir turno. Se envía en la próxima sincronización (sin conexión,
     * espera) y se repite hasta que llegue.
     */
    suspend fun pedirFondo(): AppResult<Unit>
    /** Envía lo pendiente y trae el catálogo. Error SinPrincipal si no hay conexión. */
    suspend fun sincronizarAhora(): AppResult<Unit>
    suspend fun cambiarModo(modo: ModoSincronizacion)
    /** Borra los datos de este teléfono y vuelve a PRINCIPAL. Lo no enviado se pierde (la UI avisa antes). */
    suspend fun desvincular(): AppResult<Unit>
    /** 0.20.0 (H5): el empleado leyó el aviso «Tu turno lo cerró el encargado» (0.21.0: o «no aprobó el cierre»). */
    suspend fun descartarAvisoCierre()
    /**
     * 0.25.0: descarga por la red local el APK que ofrece la principal ([EstadoSecundaria.apk]) y comprueba su SHA-256.
     * [progreso] (bytes, total). Devuelve el archivo listo para instalar.
     */
    suspend fun descargarApk(progreso: (Long, Long) -> Unit): AppResult<java.io.File> = AppResult.Err(cu.spvi.core.result.AppError.NoEncontrado)
}

/**
 * P37: regla «no se empieza un turno nuevo sin sincronizar». La principal siempre puede; una secundaria primero
 * envía lo pendiente y recibe el catálogo (Err SinPrincipal si no lo consigue).
 */
interface PuertaTurno {
    suspend fun antesDeAbrir(): AppResult<Unit>

    /**
     * 0.26.0 (P73 §4): fondo con el que DEBE abrirse el turno (el que asignó el dueño a esta secundaria). Ok(null) = lo
     * escribe quien abre (la principal, o una secundaria cuya principal es 0.25). Err Validacion("fondo", REQUERIDO) =
     * la secundaria aún no tiene fondo asignado. Se consulta después de [antesDeAbrir] (que trae lo último).
     */
    suspend fun fondoObligado(): AppResult<cu.spvi.core.money.Cup?> = AppResult.Ok(null)

    /** 0.26.0: el turno se abrió con el fondo obligado: se gasta (el próximo turno necesita otro). */
    suspend fun fondoUsado() {}

    companion object {
        val SIEMPRE: PuertaTurno = object : PuertaTurno {
            override suspend fun antesDeAbrir(): AppResult<Unit> = AppResult.Ok(Unit)
        }
    }
}
