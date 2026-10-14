/// Error único del sistema de licencias.
///
/// Contrato GL: cualquier fallo → rechazar, con un mensaje **genérico**. Lo que falló (firma, descifrado, huella,
/// vigencia…) nunca se le cuenta al usuario, para no darle pistas a quien intente fabricar una licencia.
class LicenciaInvalida implements Exception {
  const LicenciaInvalida([this.detalle = 'Licencia no válida para esta instalación.']);
  final String detalle;
  @override
  String toString() => detalle;
}

/// Lanza el rechazo genérico. Se usa como `fail()` en el resto del proyecto.
Never falla() => throw const LicenciaInvalida();
