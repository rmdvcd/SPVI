import 'dart:convert';
import 'dart:typed_data';

import 'package:pointycastle/export.dart';
import 'package:uuid/uuid.dart';

import 'almacen.dart';
import 'codigo.dart';
import 'crypto.dart';
import 'envelope.dart';
import 'errores.dart';
import 'licencia_corta.dart';
import 'solicitud.dart';
import 'subtokens.dart';
import 'trust.dart';

// Las pantallas solo necesitan un import del cliente; el error del contrato y el constructor de solicitudes van con él.
export 'errores.dart';
export 'solicitud.dart' show Solicitud;

/// Clase de licencia que ve la interfaz. Nunca un `bool isLicensed`: la licencia se **re-verifica** en cada consulta.
enum ClaseLicencia { prueba, activa, perpetua, bloqueada }

/// Estado que ve la interfaz.
class EstadoLicencia {
  const EstadoLicencia({
    required this.dispositivo,
    required this.clase,
    this.id,
    this.tipo,
    this.secundarias = 0,
    this.venceEn,
    this.diasRestantes = 0,
    this.motivo,
  });

  final String dispositivo;
  final ClaseLicencia clase;
  final String? id;
  final TipoLicenciaGL? tipo;
  final int secundarias;
  final DateTime? venceEn;
  final int diasRestantes;
  final String? motivo;

  bool get operativa => clase != ClaseLicencia.bloqueada;
  bool get enPrueba => clase == ClaseLicencia.prueba;
  bool get perpetua => clase == ClaseLicencia.perpetua;

  /// Texto corto para la interfaz.
  String get etiqueta => switch (clase) {
    ClaseLicencia.prueba => 'Período de prueba',
    ClaseLicencia.activa => 'Licencia activa',
    ClaseLicencia.perpetua => 'Licencia perpetua',
    ClaseLicencia.bloqueada => 'Licencia bloqueada',
  };
}

/// Resultado de intentar activar un código. [estado] solo viene cuando la activación fue aceptada.
class ResultadoActivacion {
  const ResultadoActivacion({required this.aceptada, this.estado, this.motivo});

  final bool aceptada;
  final EstadoLicencia? estado;
  final String? motivo;
}

/// Datos que se le piden al usuario para solicitar la licencia.
class DatosSolicitud {
  const DatosSolicitud({
    required this.nombre,
    required this.apellidos,
    required this.ci,
    required this.telefono,
    required this.tipo,
    this.secundarias = 0,
    this.transaccion,
    this.recupera,
    this.renueva,
  });

  final String nombre;
  final String apellidos;
  final String ci;
  final String telefono;
  final TipoLicenciaGL tipo;
  final int secundarias;
  final String? transaccion;
  final String? recupera;
  final String? renueva;
}

/// Solicitud lista para enviar: [codigo] es lo que GL necesita (`SPVIR1:…`) y [mensaje] el texto para WhatsApp.
class SolicitudGenerada {
  const SolicitudGenerada({required this.codigo, required this.mensaje, required this.precioCup});

  final String codigo;
  final String mensaje;
  final int precioCup;
}

/// Cliente de licencias de la app iOS: espejo de `LicenseManager` (Android) y de `LicenseClient` (web).
///
/// Guarda su propia identidad (clave P-256 del equipo), pide la licencia (`SPVIR1`), la activa (`SPVI2` corta; también
/// la larga del contrato v1 y la clave maestra de desarrollo) y **re-verifica** todo en cada consulta: claves de GL
/// fijadas en el build, descifrado con la clave del equipo, huella del dispositivo, vigencia, lista de revocadas y
/// marca de agua del reloj. Cualquier fallo → bloqueo con mensaje genérico.
class ClienteLicencia {
  ClienteLicencia._(this._almacen, this._reloj, this._firmaSpkis, this._ecdhGl, this._esDebug, this._datos);

  /// Prueba: 7 días desde la primera ejecución (igual que las otras dos apps).
  static const int diasPrueba = 7;

  /// Cuota de apps secundarias si no hay licencia (período de prueba).
  static const int secundariasDefecto = 5;

  /// Cuota máxima que puede traer una licencia.
  static const int secundariasMax = 10;

  /// Margen para ajustes de hora y zona antes de considerar el reloj manipulado.
  static const int toleranciaRelojSegundos = 300;

  /// Intentos de activación fallidos permitidos dentro de la ventana.
  static const int intentosMaximos = 5;
  static const int ventanaIntentosSegundos = 600;

  static const String _idMaestra = SubToken.idMaestra;
  static const String _claveRevocacion = 'SPVI-REV|';

  final AlmacenLicencia _almacen;
  final DateTime Function() _reloj;
  final List<ECPublicKey> _firmaSpkis;
  final ECPublicKey? _ecdhGl;
  final bool _esDebug;
  Map<String, dynamic> _datos;
  List<DateTime> _fallos = [];

  /// Abre (o crea) el documento de licencia del equipo.
  ///
  /// [firmaSpkis] y [ecdhGl] solo se pasan en los tests (nunca se tocan las claves reales de GL).
  static Future<ClienteLicencia> abrir({
    required AlmacenLicencia almacen,
    DateTime Function()? reloj,
    List<String>? firmaSpkis,
    ECPublicKey? ecdhGl,
    bool? esDebug,
  }) async {
    final documento = await almacen.leer();
    Map<String, dynamic> datos;
    if (documento == null || documento.isEmpty) {
      final par = P256.generar();
      final privada = par.privateKey as ECPrivateKey;
      final ahora = (reloj ?? DateTime.now)();
      datos = {
        'deviceId': 'SPVI:${const Uuid().v4().replaceAll('-', '')}',
        'privada': base64.encode(P256.bytesDe32(privada.d!)),
        'started': ahora.millisecondsSinceEpoch,
        'last': ahora.millisecondsSinceEpoch,
        'code': null,
      };
    } else {
      final leido = jsonDecode(documento);
      if (leido is! Map<String, dynamic>) falla();
      datos = leido;
    }
    final cliente = ClienteLicencia._(
      almacen,
      reloj ?? DateTime.now,
      (firmaSpkis ?? Trust.signSpkis).map(P256.claveDeSpki).toList(growable: false),
      ecdhGl ?? (firmaSpkis == null ? Trust.claveEcdh : null),
      esDebug ?? ModoDesarrollo.esDebug,
      datos,
    );
    if (documento == null || documento.isEmpty) await cliente._guardar();
    return cliente;
  }

  /// Identidad del equipo (`SPVI:` + 32 caracteres). Es la que GL usa para atar la licencia.
  String get deviceId => _datos['deviceId'] as String;

  /// Punto comprimido P-256 (33 B) que viaja en la solicitud.
  Uint8List get devicePubComprimida => P256.comprimir(_publica);

  String get devicePubBase64Url => P256.base64Url(devicePubComprimida);

  /// SPKI Base64 de la clave con la que este equipo firma los sub-tokens de las apps secundarias.
  String get firmaPublicaBase64 => base64.encode(P256.spkiDe(_publica));

  /// ¿El build trae la clave ECDH de GL? Sin ella no se pueden emitir solicitudes.
  bool get puedeSolicitar => _ecdhGl != null;

  ECPrivateKey get _privada => P256.privadaDesde(P256.escalarDe(base64.decode(_datos['privada'] as String)));

  ECPublicKey get _publica => P256.publicaDe(_privada);

  // ---------------------------------------------------------------- estado

  /// Estado actual: marca de reloj → licencia guardada (re-verificada) → período de prueba.
  Future<EstadoLicencia> estado() async {
    final ahora = _reloj();
    final marca = DateTime.fromMillisecondsSinceEpoch(_datos['last'] as int, isUtc: true);
    // Mitigación 3: la hora del equipo no puede retroceder respecto a la última vista.
    if (ahora.isBefore(marca.subtract(const Duration(seconds: toleranciaRelojSegundos)))) {
      return _bloqueada('Revisa la fecha y hora del equipo.');
    }
    if (ahora.isAfter(marca)) {
      _datos['last'] = ahora.millisecondsSinceEpoch;
      await _guardar();
    }
    final guardada = _datos['code'] as String?;
    if (guardada == null || guardada.isEmpty) return _prueba(ahora);
    final instalada = _instalada(guardada, ahora);
    if (instalada == null) return _prueba(ahora);
    if (_revocada(instalada)) {
      return _bloqueada('Licencia vencida o revocada.', id: instalada.id);
    }
    if (!instalada.vigente(ahora)) {
      return _bloqueada('La licencia no está vigente.', id: instalada.id);
    }
    return EstadoLicencia(
      dispositivo: deviceId,
      clase: instalada.tipo == TipoLicenciaGL.perpetua ? ClaseLicencia.perpetua : ClaseLicencia.activa,
      id: instalada.id,
      tipo: instalada.tipo,
      secundarias: instalada.secundarias,
      venceEn: instalada.venceEn,
      diasRestantes: instalada.venceEn == null ? 0 : _diasHasta(instalada.venceEn!, ahora),
    );
  }

  /// Estado o excepción: no se vende ni se sincroniza con una licencia bloqueada.
  Future<EstadoLicencia> requerirActiva() async {
    final estado = await this.estado();
    if (!estado.operativa) throw LicenciaInvalida(estado.motivo ?? 'La licencia no está viva.');
    return estado;
  }

  // ---------------------------------------------------------------- activación

  /// Activa lo que envíe el desarrollador, en este orden:
  ///
  /// 1. la **clave maestra de desarrollo** `GLMK-PERM-MAXS-9999`, solo en builds de depuración (mitigación 1);
  /// 2. la **licencia corta** `SPVI2:…` (el formato del teléfono);
  /// 3. la **licencia larga** del contrato v1 (envelope JSON + línea `ID:`), que se sigue aceptando.
  Future<ResultadoActivacion> activar(String codigo) async {
    if (codigo.length > 8192) return _rechazo('Código no válido.');
    final ahora = _reloj();
    _fallos = _fallos.where((momento) => ahora.difference(momento).inSeconds < ventanaIntentosSegundos).toList();
    if (_fallos.length >= intentosMaximos) {
      return _rechazo('Demasiados intentos. Espera unos minutos y vuelve a intentarlo.');
    }
    try {
      if (CodigoLicencia.esMaestra(codigo, debug: _esDebug)) {
        await _instalar('MASTER');
      } else {
        final corta = LicenciaCorta.extraer(codigo);
        if (corta != null) {
          final campos = _abrirCorta(corta);
          if (await _esMasAntigua(campos.emitidaEn)) {
            throw const LicenciaInvalida('Ya hay una licencia más reciente instalada.');
          }
          if (!campos.vigente(ahora)) throw const LicenciaInvalida('La licencia no está vigente.');
          await _instalar(corta.texto);
        } else if (Envelope.esEnvelope(codigo)) {
          final id = Envelope.extraerId(codigo);
          if (id == null) falla();
          final info = _abrirLargaEnvelope(codigo, id, ahora);
          final emitida = DateTime.tryParse('${info['emitidaEn'] ?? ''}');
          if (emitida != null && await _esMasAntigua(emitida)) {
            throw const LicenciaInvalida('Ya hay una licencia más reciente instalada.');
          }
          await _instalar(jsonEncode(Envelope.extraerEnvelope(codigo)), envelopeId: id);
        } else {
          throw const LicenciaInvalida('Escribe el código de tu licencia (empieza por SPVI2:).');
        }
      }
    } catch (error) {
      // Todo intento fallido cuenta para el limitador y se responde con el mensaje ya previsto (genérico si el fallo
      // vino de una comprobación criptográfica: nunca se le dice al usuario qué comprobación falló).
      _fallos = [..._fallos, ahora];
      return ResultadoActivacion(
        aceptada: false,
        motivo: error is LicenciaInvalida ? error.detalle : 'La licencia no es válida para esta instalación.',
      );
    }
    _fallos = [];
    return ResultadoActivacion(aceptada: true, estado: await estado());
  }

  // ---------------------------------------------------------------- solicitud

  /// Genera la solicitud cifrada `SPVIR1` y el texto para WhatsApp.
  Future<SolicitudGenerada> solicitar(DatosSolicitud datos) async {
    final gl = _ecdhGl;
    if (gl == null) throw const LicenciaInvalida('Este build no trae las claves de emisión.');
    final nombre = datos.nombre.trim();
    final apellidos = datos.apellidos.trim();
    final ci = datos.ci.trim();
    final telefono = datos.telefono.trim();
    // Misma regla que aplica GL al validar (PR #3): letras Unicode, espacio, punto, apóstrofo o guion, 2-80.
    // Mejor fallar aquí con mensaje claro que un rechazo mudo en el emisor.
    final nombreValido = RegExp(r"^[\p{L} .'-]{2,80}$", unicode: true);
    if (!nombreValido.hasMatch(nombre) || !nombreValido.hasMatch(apellidos)) {
      throw const LicenciaInvalida('Escribe nombre y apellidos válidos (solo letras, entre 2 y 80 caracteres).');
    }
    if (ci.isEmpty) throw const LicenciaInvalida('Escribe el carné de identidad.');
    if (telefono.isEmpty) throw const LicenciaInvalida('Escribe el teléfono.');
    if (datos.secundarias < 0 || datos.secundarias > secundariasMax) {
      throw const LicenciaInvalida('Elige entre 0 y 10 apps secundarias.');
    }
    final plano = Solicitud.payload(
      nombre: nombre,
      apellidos: apellidos,
      ci: ci,
      telefono: telefono,
      tipo: datos.tipo.nombre,
      secundarias: datos.secundarias,
      deviceId: deviceId,
      devicePubComprimida: devicePubComprimida,
      renueva: datos.renueva,
      recupera: datos.recupera,
    );
    final codigo = Solicitud.sellar(Uint8List.fromList(utf8.encode(jsonEncode(plano))), gl);
    final mensaje = Solicitud.mensaje(
      nombre: nombre,
      apellidos: apellidos,
      ci: ci,
      telefono: telefono,
      tipo: datos.tipo,
      secundarias: datos.secundarias,
      codigo: codigo,
      transaccion: datos.transaccion,
      recupera: datos.recupera,
    );
    return SolicitudGenerada(
      codigo: codigo,
      mensaje: mensaje,
      precioCup: Solicitud.precio(datos.tipo, datos.secundarias),
    );
  }

  /// Id de la licencia instalada si se puede renovar (no perpetua), o `null`.
  Future<String?> licenciaRenovable() async {
    final guardada = _datos['code'] as String?;
    if (guardada == null || guardada.isEmpty) return null;
    final instalada = _instalada(guardada, _reloj());
    if (instalada == null || instalada.tipo == TipoLicenciaGL.perpetua) return null;
    return instalada.id;
  }

  // ---------------------------------------------------------------- sub-tokens

  /// Emite el sub-token que autoriza a [dispositivo] como app secundaria en la ranura [ranura] (mitigación 7).
  /// `null` = no se puede emitir (bloqueada, cuota 0 o ranura fuera de la cuota).
  Future<String?> emitirSubToken(String dispositivo, int ranura) async {
    final estado = await this.estado();
    if (!estado.operativa) return null;
    final cuota = estado.secundarias;
    if (cuota <= 0 || ranura < 1 || ranura > cuota) return null;
    try {
      return SubToken.emitir(_privada, estado.id ?? SubToken.idPrueba, cuota, estado.venceEn, dispositivo, ranura);
    } catch (_) {
      return null;
    }
  }

  /// Datos del sub-token que entregó la app principal, o `null` si no autoriza a [dispositivo].
  ///
  /// La cuota con la que se comprueba es la de la licencia propia: una app sin licencia no acepta sub-tokens.
  Future<DatosSubToken?> verificarSubToken(String token, String firmaSpkiBase64, String dispositivo) async {
    final estado = await this.estado();
    if (!estado.operativa || estado.secundarias <= 0) return null;
    return SubToken.verificarBase64(token, firmaSpkiBase64, dispositivo, _reloj(), cuotaMaxima: estado.secundarias);
  }

  // ---------------------------------------------------------------- revocaciones

  /// Aplica la lista pública de revocadas (`revocadas.json`, firmada por GL). Devuelve el estado resultante.
  Future<EstadoLicencia> importarRevocaciones(String texto) async {
    try {
      final sobre = jsonDecode(texto);
      if (sobre is! Map<String, dynamic>) falla();
      final datos = sobre['datos'];
      final firma = sobre['firma'];
      if (datos is! String || datos.length > 512 * 1024 || firma is! String) falla();
      final mensaje = Uint8List.fromList(utf8.encode('SPVI-REV1|$datos'));
      final firmaBytes = base64.decode(firma);
      if (!_firmaSpkis.any((clave) => P256.verificarDer(clave, mensaje, firmaBytes))) falla();
      final documento = jsonDecode(datos);
      if (documento is! Map<String, dynamic> || documento['v'] != 1 || documento['emitidaEn'] is! int) falla();
      final revocadas = documento['revocadas'];
      if (revocadas is! List) falla();
      for (final marca in revocadas) {
        if (marca is! String || !RegExp(r'^[a-f0-9]{32}$').hasMatch(marca)) falla();
      }
      final anterior = _datos['revocations'];
      if (anterior is Map && (documento['emitidaEn'] as int) < (anterior['emitidaEn'] as int)) {
        throw const LicenciaInvalida('La lista es anterior a la ya instalada.');
      }
      _datos['revocations'] = documento;
      await _guardar();
    } catch (_) {
      throw const LicenciaInvalida('Lista de revocaciones no auténtica.');
    }
    return estado();
  }

  // ---------------------------------------------------------------- interno

  Future<void> _instalar(String codigo, {String? envelopeId}) async {
    _datos['code'] = codigo;
    _datos['activated'] = _reloj().millisecondsSinceEpoch;
    _datos['envelopeId'] = envelopeId;
    await _guardar();
  }

  Future<bool> _esMasAntigua(DateTime emitida) async {
    final actual = _datos['code'] as String?;
    if (actual == null || actual == 'MASTER') return false;
    final instalada = _instalada(actual, _reloj());
    if (instalada == null) return false;
    return emitida.isBefore(instalada.emitidaEn);
  }

  ResultadoActivacion _rechazo(String motivo) => ResultadoActivacion(aceptada: false, motivo: motivo);

  EstadoLicencia _bloqueada(String motivo, {String? id}) => EstadoLicencia(
    dispositivo: deviceId,
    clase: ClaseLicencia.bloqueada,
    id: id,
    motivo: motivo,
  );

  EstadoLicencia _prueba(DateTime ahora) {
    final inicio = DateTime.fromMillisecondsSinceEpoch(_datos['started'] as int, isUtc: true);
    final fin = inicio.add(const Duration(days: diasPrueba));
    if (!ahora.isBefore(fin)) return _bloqueada('La prueba terminó. Activa una licencia.');
    return EstadoLicencia(
      dispositivo: deviceId,
      clase: ClaseLicencia.prueba,
      secundarias: secundariasDefecto,
      venceEn: fin,
      diasRestantes: _diasHasta(fin, ahora),
    );
  }

  static int _diasHasta(DateTime momento, DateTime ahora) {
    final restante = momento.difference(ahora);
    if (restante.isNegative) return 0;
    return (restante.inSeconds / 86400).ceil();
  }

  /// Licencia instalada ya verificada, o `null` si no vale (se ignora: la app queda en prueba/bloqueada).
  _Instalada? _instalada(String guardada, DateTime ahora) {
    try {
      if (guardada == 'MASTER') {
        // La maestra es una ayuda de depuración: en release se ignora como si no existiera (mitigación 1).
        if (!_esDebug) return null;
        return _Instalada(
          id: _idMaestra,
          tipo: TipoLicenciaGL.perpetua,
          estado: EstadoLicenciaGL.perpetua,
          secundarias: secundariasMax,
          emitidaEn: DateTime.fromMillisecondsSinceEpoch((_datos['activated'] as int?) ?? 0, isUtc: true),
          venceEn: null,
          codigo: 'MASTER',
        );
      }
      if (guardada.startsWith(LicenciaCorta.prefijo)) {
        final codigo = LicenciaCorta.extraer(guardada);
        if (codigo == null) return null;
        final campos = _abrirCorta(codigo);
        return _Instalada(
          id: campos.id,
          tipo: campos.tipo,
          estado: campos.estado,
          secundarias: campos.secundarias,
          emitidaEn: campos.emitidaEn,
          venceEn: campos.venceEn,
          codigo: guardada,
        );
      }
      final id = (_datos['envelopeId'] as String?) ?? Envelope.extraerId(guardada);
      if (id == null) return null;
      final info = _abrirLargaEnvelope(guardada, id, ahora);
      final tipo = _tipoDe('${info['tipo'] ?? ''}');
      if (tipo == null) return null;
      final vence = _instante('${info['venceEn'] ?? ''}');
      return _Instalada(
        id: '${info['id'] ?? ''}',
        tipo: tipo,
        estado: '${info['estado'] ?? ''}' == 'PERPETUA' ? EstadoLicenciaGL.perpetua : EstadoLicenciaGL.activa,
        secundarias: info['secundarias'] is int ? info['secundarias'] as int : secundariasDefecto,
        emitidaEn: _instante('${info['emitidaEn'] ?? ''}') ?? DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
        venceEn: vence,
        codigo: guardada,
      );
    } catch (_) {
      return null;
    }
  }

  /// Abre la licencia corta y valida huella, estado y firma de GL. Falla (genérico) si algo no cuadra.
  CamposCorta _abrirCorta(CodigoCorta codigo) {
    if (!LicenciaCorta.firmaValida(codigo, _firmaSpkis)) falla();
    final campos = LicenciaCorta.leer(LicenciaCorta.descifrar(codigo, _privada));
    if (campos == null) falla();
    if (!LicenciaCorta.esDeEsteDispositivo(campos, deviceId, devicePubComprimida)) falla();
    if (campos.estado == EstadoLicenciaGL.revocada || campos.estado == EstadoLicenciaGL.vencida) falla();
    return campos;
  }

  /// Licencia **larga** del contrato v1: firma de GL, descifrado con la clave del equipo y validaciones.
  Map<String, dynamic> _abrirLargaEnvelope(String mensaje, String letraId, DateTime ahora) {
    final envelope = Envelope.extraerEnvelope(mensaje) ?? (jsonDecode(mensaje) as Map<String, dynamic>);
    if (!Envelope.verificarFirma(envelope, _firmaSpkis)) falla();
    Uint8List plano;
    try {
      plano = Envelope.abrir(envelope, _privada, aad: Envelope.aadLicencia(letraId));
    } catch (_) {
      plano = Envelope.abrir(envelope, _privada, aad: Envelope.aadLicenciaHeredada(letraId));
    }
    final info = jsonDecode(utf8.decode(plano));
    if (info is! Map<String, dynamic>) falla();
    if ('${info['appName'] ?? ''}'.toUpperCase() != Envelope.app) falla();
    if (info['deviceId'] != deviceId) falla();
    if ('${info['id'] ?? ''}'.toLowerCase() != letraId.toLowerCase()) falla();
    final codigo16 = info['licenciaObtenida'] ?? info['codigoCorto'];
    if (codigo16 != null && !CodigoLicencia.valido('$codigo16')) falla();
    if (info['licenciaObtenida'] != null && info['codigoCorto'] != null && info['licenciaObtenida'] != info['codigoCorto']) {
      falla();
    }
    if (info['estado'] != 'ACTIVA' && info['estado'] != 'PERPETUA') falla();
    final perpetua = info['tipo'] == 'PERPETUA';
    final vence = _instante('${info['venceEn'] ?? ''}');
    if (vence == null && !perpetua) falla();
    if (vence != null && !vence.isAfter(ahora)) throw const LicenciaInvalida('La licencia no está vigente.');
    return info;
  }

  TipoLicenciaGL? _tipoDe(String nombre) {
    for (final tipo in TipoLicenciaGL.values) {
      if (tipo.nombre == nombre.toUpperCase()) return tipo;
    }
    return null;
  }

  static DateTime? _instante(String texto) {
    if (texto.isEmpty || texto == 'null') return null;
    try {
      return DateTime.parse(texto).toUtc();
    } catch (_) {
      falla();
    }
  }

  bool _revocada(_Instalada instalada) {
    if (instalada.estado == EstadoLicenciaGL.revocada || instalada.estado == EstadoLicenciaGL.vencida) return true;
    final lista = _datos['revocations'];
    if (lista is! Map) return false;
    final revocadas = lista['revocadas'];
    if (revocadas is! List) return false;
    final huella = P256.sha256(Uint8List.fromList(utf8.encode('$_claveRevocacion${instalada.id}')));
    final marca = _hex(Uint8List.sublistView(huella, 0, 16));
    return revocadas.contains(marca);
  }

  static String _hex(Uint8List datos) {
    final salida = StringBuffer();
    for (final b in datos) {
      salida.write(b.toRadixString(16).padLeft(2, '0'));
    }
    return salida.toString();
  }

  Future<void> _guardar() async => _almacen.guardar(jsonEncode(_datos));

  /// Expuesto solo para los tests: la clave privada del equipo.
  ECPrivateKey get claveDelEquipo => _privada;
}

/// Licencia instalada y verificada (uso interno).
class _Instalada {
  const _Instalada({
    required this.id,
    required this.tipo,
    required this.estado,
    required this.secundarias,
    required this.emitidaEn,
    required this.venceEn,
    required this.codigo,
  });

  final String id;
  final TipoLicenciaGL tipo;
  final EstadoLicenciaGL estado;
  final int secundarias;
  final DateTime emitidaEn;
  final DateTime? venceEn;
  final String codigo;

  bool vigente(DateTime ahora) {
    if (estado == EstadoLicenciaGL.revocada || estado == EstadoLicenciaGL.vencida) return false;
    if (emitidaEn.isAfter(ahora.add(const Duration(seconds: 300)))) return false;
    if (venceEn != null && !ahora.isBefore(venceEn!)) return false;
    return true;
  }
}
