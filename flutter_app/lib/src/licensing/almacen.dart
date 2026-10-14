import 'package:shared_preferences/shared_preferences.dart';

/// Dónde vive el documento de licencia (el equivalente de `licencia.bin` en las otras dos apps).
///
/// El documento lleva la clave privada P-256 del equipo y el estado (marca de reloj, licencia instalada, lista de
/// revocadas). En Android esa clave es del Keystore; en iOS, el Keychain exige un plugin nativo, así que se guarda
/// dentro del contenedor de la app: lo protegen el propio sandbox de iOS y la protección de datos del sistema. No se
/// añadió ningún plugin para no cambiar los Pods del proyecto (compila en Codemagic sin pasos nuevos).
abstract class AlmacenLicencia {
  Future<String?> leer();
  Future<void> guardar(String documento);
  Future<void> borrar();
}

/// Almacén real: `shared_preferences` (una sola clave con todo el documento en JSON).
class AlmacenPreferencias implements AlmacenLicencia {
  static const String clave = 'spvi.licencia.documento';

  @override
  Future<String?> leer() async => (await SharedPreferences.getInstance()).getString(clave);

  @override
  Future<void> guardar(String documento) async {
    await (await SharedPreferences.getInstance()).setString(clave, documento);
  }

  @override
  Future<void> borrar() async {
    await (await SharedPreferences.getInstance()).remove(clave);
  }
}

/// Almacén en memoria: para los tests, sin tocar disco ni preferencias.
class AlmacenEnMemoria implements AlmacenLicencia {
  AlmacenEnMemoria([this._documento]);

  String? _documento;

  String? get documento => _documento;

  @override
  Future<String?> leer() async => _documento;

  @override
  Future<void> guardar(String documento) async => _documento = documento;

  @override
  Future<void> borrar() async => _documento = null;
}
