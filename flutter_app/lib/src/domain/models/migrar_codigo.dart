import 'package:intl/intl.dart';

/// Rol de cada teléfono en el paso de datos por QR (petición 5).
///
/// Es el equivalente a `MigrarRol.kt` de Android: el **emisor** genera el código y entrega el
/// respaldo; el **receptor** lo lee y lo aplica. El código nunca viaja en claro: dentro lleva el
/// respaldo `.spvi` cifrado de siempre.
enum RolMigracion {
  /// Genera el código y envía los datos. Su teléfono se borra al terminar.
  emisor,

  /// Lee el código del emisor y restaura los datos.
  receptor;

  String get etiqueta => this == RolMigracion.emisor ? 'Emisor' : 'Receptor';
}

/// Código de migración `SPVIM1|<rol>|<host>|<puerto>|<clave>|<origen>`.
///
/// Es el equivalente de `MigrarQr.kt` de Android. Se mantiene **puro** (sin Flutter, sin red) para
/// poder probarlo en la máquina virtual de Dart como se hace en `MigrarTest.kt`.
class CodigoMigracion {
  const CodigoMigracion._();

  /// Prefijo que identifica el código y su versión.
  static const String prefijo = 'SPVIM1';

  /// Separador de campos.
  static const String separador = '|';

  /// Número de campos obligatorios (el 6.º, el origen, es opcional por compatibilidad).
  static const int camposMinimos = 5;

  /// Genera el código que muestra el emisor.
  static String generar({
    required RolMigracion rol,
    required String host,
    required int puerto,
    required String clave,
    String origen = '',
  }) {
    return [
      prefijo,
      rol.name,
      host,
      '$puerto',
      clave,
      origen,
    ].join(separador);
  }

  /// Lee un código. Devuelve `null` si no es de SPVI o viene incompleto.
  ///
  /// Tolera un campo extra al final (versiones antiguas que añadían el origen por detrás) y
  /// cualquier texto que el escáner pegue por delante o por detrás.
  static Codigo? parsear(String texto) {
    final limpio = texto.trim();
    if (!limpio.contains(prefijo)) return null;
    final cuerpo = limpio.substring(limpio.indexOf(prefijo));
    final campos = cuerpo.split(separador);
    if (campos.length < camposMinimos + 1) return null;
    RolMigracion? rol;
    for (final candidato in RolMigracion.values) {
      if (candidato.name == campos[1]) rol = candidato;
    }
    if (rol == null) return null;
    final puerto = int.tryParse(campos[3]);
    if (puerto == null || puerto <= 0) return null;
    if (campos[4].isEmpty) return null;
    return Codigo(
      rol: rol,
      host: campos[2],
      puerto: puerto,
      clave: campos[4],
      origen: campos.length > 5 ? campos[5] : '',
    );
  }

  /// Clave de un solo uso que lleva el código. No se guarda en ningún sitio.
  static String claveNueva() {
    final ahora = DateTime.now().microsecondsSinceEpoch;
    return '${ahora.toRadixString(36)}${(ahora % 9973).toRadixString(36)}';
  }

  /// Texto que se ve en pantalla, en grupos, para que se pueda copiar o dictar.
  static String paraMostrar(String codigo) {
    final grupos = <String>[];
    for (var i = 0; i < codigo.length; i += 4) {
      grupos.add(codigo.substring(i, i + 4 > codigo.length ? codigo.length : i + 4));
    }
    return grupos.join(' ');
  }
}

/// Un código ya leído.
class Codigo {
  final RolMigracion rol;
  final String host;
  final int puerto;
  final String clave;

  /// Identidad del teléfono que generó el código (para mostrarla antes de restaurar).
  final String origen;

  const Codigo({
    required this.rol,
    required this.host,
    required this.puerto,
    required this.clave,
    this.origen = '',
  });

  @override
  String toString() => 'Codigo(${rol.name}, $host:$puerto)';
}

/// Mensajes de la pantalla de Migrar. Igual que `TextosMigrar` de Android: todos en español y
/// reunidos aquí para poder probarlos sin montar la interfaz.
class TextosMigrar {
  const TextosMigrar._();

  static const String titulo = 'Migrar';
  static const String emisor = 'Emisor';
  static const String receptor = 'Receptor';
  static const String explicacion =
      'Pasa los datos de un teléfono a otro por la red local. Elige qué hace cada teléfono: '
      'el emisor entrega la copia y el receptor la recibe.';
  static const String sinClave =
      'Para migrar necesitas el acceso con clave. Actívalo en Ajustes y vuelve a intentarlo.';
  static const String claveDefinida = 'Protegido con tu clave o huella del teléfono.';
  static const String pasoUno = 'Elige el rol de este teléfono';
  static const String pasoDos = 'Conecta los dos teléfonos';
  static const String pasoTres = 'Comprueba antes de continuar';
  static const String pasoCuatro = 'Terminado';
  static const String mostrarCodigo = 'Mostrar el código de migración';
  static const String leerCodigo = 'Leer el código del otro teléfono';
  static const String codigoEmisor = 'Código del emisor';
  static const String codigoReceptor = 'Código del receptor';
  static const String pegarCodigo = 'Pega aquí el código del otro teléfono';
  static const String codigoInvalido = 'Ese código no es de SPVI o está incompleto.';
  static const String empezar = 'Empezar la migración';
  static const String esperando = 'Esperando al otro teléfono…';
  static const String recibiendo = 'Recibiendo los datos…';
  static const String enviando = 'Enviando los datos…';
  static const String listoEmisor = 'Datos entregados. Este teléfono quedará vacío.';
  static const String listoReceptor = 'Datos recibidos y restaurados.';
  static const String sinConexion = 'Sin conexión con el otro teléfono.';
  static const String cancelar = 'Cancelar';
  static const String cerrar = 'Cerrar';
}

/// Formato de los mensajes con números, para no repetir `NumberFormat` por la interfaz.
final NumberFormat _entero = NumberFormat('#,##0', 'es');

/// Traduce un resultado de migración a texto (equivalente de `MigrarEtapa.etiqueta`).
String etapaMigracion(int etapa) {
  switch (etapa) {
    case 0:
      return 'Preparando';
    case 1:
      return 'Conectando';
    case 2:
      return 'Enviando los datos';
    case 3:
      return 'Recibiendo los datos';
    case 4:
      return 'Comprobando';
    case 5:
      return 'Terminado';
    default:
      return 'Preparando';
  }
}

/// Porcentaje legible de una fracción 0..1.
String porcentaje(double fraccion) {
  final valor = (fraccion.clamp(0.0, 1.0) * 100).round();
  return '${_entero.format(valor)} %';
}
