import 'dart:convert';
import 'dart:typed_data';

import 'package:pointycastle/export.dart';
import 'package:spvi_ios/src/licensing/almacen.dart';
import 'package:spvi_ios/src/licensing/cliente.dart';
import 'package:spvi_ios/src/licensing/crypto.dart';
import 'package:spvi_ios/src/licensing/licencia_corta.dart';
import 'package:spvi_ios/src/licensing/solicitud.dart';

/// Ayudas de los tests de licencias: nada de red y nunca las claves privadas reales de GL.
///
/// El "GL" de los tests es un emisor con claves propias ([EmisorDePrueba]): firma como firmaría GL, así que los
/// tests comprueban las mismas reglas sin tocar nada del emisor de verdad.

/// Texto hexadecimal → bytes.
Uint8List hex(String texto) {
  final limpio = texto.replaceAll(RegExp(r'\s'), '');
  final salida = Uint8List(limpio.length ~/ 2);
  for (var i = 0; i < salida.length; i++) {
    salida[i] = int.parse(limpio.substring(i * 2, i * 2 + 2), radix: 16);
  }
  return salida;
}

/// Bytes → texto hexadecimal.
String aHex(Uint8List datos) {
  final salida = StringBuffer();
  for (final b in datos) {
    salida.write(b.toRadixString(16).padLeft(2, '0'));
  }
  return salida.toString();
}

/// GL de mentira: sus propias claves de firma y ECDH (los tests se las inyectan al cliente).
class EmisorDePrueba {
  EmisorDePrueba() {
    final firma = P256.generar();
    _firmaPrivada = firma.privateKey as ECPrivateKey;
    firmaPublica = firma.publicKey as ECPublicKey;
    final ecdh = P256.generar();
    _ecdhPrivada = ecdh.privateKey as ECPrivateKey;
    ecdhPublica = ecdh.publicKey as ECPublicKey;
  }

  late final ECPrivateKey _firmaPrivada;
  late final ECPrivateKey _ecdhPrivada;
  late final ECPublicKey firmaPublica;
  late final ECPublicKey ecdhPublica;

  /// Clave privada de firma del emisor (solo para los tests).
  ECPrivateKey get privateFirma => _firmaPrivada;

  String get firmaSpkiBase64 => base64.encode(P256.spkiDe(firmaPublica));

  List<String> get firmaSpkis => [firmaSpkiBase64];

  /// Como GL: descifra una solicitud `SPVIR1` con su clave ECDH privada.
  Uint8List abrirSolicitud(String codigo) => Solicitud.abrir(codigo, _ecdhPrivada);

  /// Como GL: emite una licencia corta `SPVI2:` para ese dispositivo.
  String licenciaCorta({
    required String deviceId,
    required Uint8List devicePub,
    required DateTime emitidaEn,
    DateTime? venceEn,
    TipoLicenciaGL tipo = TipoLicenciaGL.mensual,
    EstadoLicenciaGL estado = EstadoLicenciaGL.activa,
    int secundarias = 0,
    String id = '7c9e6679-7425-40de-944b-e07fc1f90ae7',
  }) {
    final campos = CamposCorta(
      id: id,
      huella: LicenciaCorta.huella(deviceId, devicePub),
      tipo: tipo,
      estado: estado,
      secundarias: secundarias,
      emitidaEn: emitidaEn,
      venceEn: venceEn,
    );
    return LicenciaCorta.emitir(campos, devicePub, (datos) => P256.firmarDer(_firmaPrivada, datos));
  }

  /// Lista de revocadas firmada, como la publica GL (`revocadas.json`).
  String revocadas(List<String> huellas, {int emitidaEn = 1791000000}) {
    final datos = jsonEncode({'v': 1, 'emitidaEn': emitidaEn, 'revocadas': huellas});
    final firma = P256.firmarDer(_firmaPrivada, Uint8List.fromList(utf8.encode('SPVI-REV1|$datos')));
    return jsonEncode({'datos': datos, 'firma': base64.encode(firma)});
  }
}

/// Cliente de licencias con el emisor de prueba inyectado.
Future<ClienteLicencia> clienteDePrueba({
  required EmisorDePrueba emisor,
  AlmacenLicencia? almacen,
  DateTime Function()? reloj,
  bool? esDebug,
  bool conEcdh = true,
}) =>
    ClienteLicencia.abrir(
      almacen: almacen ?? AlmacenEnMemoria(),
      reloj: reloj,
      firmaSpkis: emisor.firmaSpkis,
      ecdhGl: conEcdh ? emisor.ecdhPublica : null,
      esDebug: esDebug ?? true,
    );
