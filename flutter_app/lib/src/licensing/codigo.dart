import 'trust.dart';

/// Código de licencia del contrato GL: 16 caracteres `[0-9A-Z]` en 4 grupos de 4 (`XXXX-XXXX-XXXX-XXXX`).
///
/// Espejo de `CodigoLicencia.kt` (Android) y de `licensing/codigo.py` (web). La app iOS no lo activa (su formato es
/// `SPVI2:`), pero sí lo **valida** cuando viaja dentro de una licencia larga (`licenciaObtenida`, §5.1 del contrato).
class CodigoLicencia {
  CodigoLicencia._();

  static const int largo = 16;
  static const String _maestraNormalizada = 'GLMKPERMMAXS9999';
  static final RegExp _alfabeto = RegExp(r'^[0-9A-Z]{16}$');
  static final RegExp _separadores = RegExp(r'[\s\-]');

  /// Sin guiones ni espacios y en mayúsculas; `null` si no son 16 caracteres de `[0-9A-Z]`.
  static String? normalizar(String? texto) {
    if (texto == null) return null;
    final limpio = _separadores.replaceAll(texto.trim().toUpperCase(), '');
    return _alfabeto.hasMatch(limpio) ? limpio : null;
  }

  static bool valido(String? texto) => normalizar(texto) != null;

  /// `XXXX-XXXX-XXXX-XXXX`; `null` si el texto no es un código del contrato.
  static String? formatear(String? texto) {
    final limpio = normalizar(texto);
    if (limpio == null) return null;
    return [for (var i = 0; i < largo; i += 4) limpio.substring(i, i + 4)].join('-');
  }

  /// Clave maestra de desarrollo: **solo** vale en builds de depuración (mitigación 1, Opción A).
  static bool esMaestra(String texto, {bool? debug}) =>
      (debug ?? ModoDesarrollo.esDebug) && normalizar(texto) == _maestraNormalizada;
}
