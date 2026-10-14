import 'dart:convert';
import 'dart:typed_data';

import 'package:pointycastle/export.dart';

import 'crypto.dart';
import 'errores.dart';
import 'licencia_corta.dart';

/// **Solicitud cifrada** compacta (`SPVIR1:`, contrato SPVI 0.23.1): lo que la app manda a GL para pedir su licencia.
///
/// Texto: `SPVIR1:` + Base64url sin relleno de `epk (33 B) ‖ ct ‖ tag (16 B)`.
/// * `epk`: clave pública EFÍMERA P-256 de la app, punto comprimido SEC1 (nueva en cada solicitud).
/// * `okm = HKDF-SHA256(ECDH(efímera, clave ECDH de GL), sal = epk, info = "SPVI-R1", 44 B)` → clave AES-256 ‖ IV.
/// * AES-256-GCM con AAD `"SPVI-R1|"` ‖ epk. Texto plano = JSON v2 con los datos del solicitante.
///
/// Sin firma de la efímera: la confidencialidad y la integridad las da GCM con la clave ECDH de GL fijada en el build
/// (la firma de la efímera no autenticaba a nadie). Ocupa unos 500 caracteres, así que el mensaje cabe como pie de la
/// imagen del QR de WhatsApp.
class Solicitud {
  Solicitud._();

  static const String prefijo = 'SPVIR1:';
  static const String info = 'SPVI-R1';
  static const int largoEpk = 33;
  static const int largoTag = 16;
  static const RegExp _alfabetoBase64Url = RegExp(r'[A-Za-z0-9\-_]');

  /// Datos del solicitante (lo que se ve en el mensaje; el resto va cifrado).
  static Map<String, dynamic> payload({
    required String nombre,
    required String apellidos,
    required String ci,
    required String telefono,
    required String tipo,
    required int secundarias,
    required String deviceId,
    required Uint8List devicePubComprimida,
    String via = 'WHATSAPP',
    String? renueva,
    String? recupera,
    DateTime? solicitadaEn,
    Uint8List? nonce,
  }) {
    if (devicePubComprimida.length != largoEpk) falla();
    final datos = <String, dynamic>{
      'v': 2,
      'nombre': nombre,
      'apellidos': apellidos,
      'ci': ci,
      'via': via,
      'telefono': telefono,
      'deviceId': deviceId,
      'tipo': tipo,
      'solicitadaEn': (solicitadaEn ?? DateTime.now()).toUtc().toIso8601String(),
      'nonce': P256.base64Url(nonce ?? P256.aleatorio(16)),
      'devicePub': P256.base64Url(devicePubComprimida),
      'secundarias': secundarias,
    };
    if (renueva != null && renueva.isNotEmpty) datos['renueva'] = renueva;
    if (recupera != null && recupera.isNotEmpty) datos['recupera'] = recupera;
    return datos;
  }

  static Uint8List aad(Uint8List epk) => Uint8List.fromList([...utf8.encode('$info|'), ...epk]);

  /// Cifra [plano] hacia la clave ECDH de GL.
  static String sellar(Uint8List plano, ECPublicKey glEcdh, {ECPrivateKey? efimera, ECPublicKey? publicaEfimera}) {
    final par = P256.generar();
    final privada = efimera ?? par.privateKey as ECPrivateKey;
    final punto = publicaEfimera ?? (efimera == null ? par.publicKey as ECPublicKey : P256.publicaDe(privada));
    final epk = P256.comprimir(punto);
    final okm = P256.hkdfSha256(P256.compartir(privada, glEcdh), epk, Uint8List.fromList(utf8.encode(info)), 44);
    final cifrado = P256.aesGcmCifrar(Uint8List.sublistView(okm, 0, 32), Uint8List.sublistView(okm, 32, 44), aad(epk), plano);
    return prefijo + P256.base64Url(Uint8List.fromList([...epk, ...cifrado]));
  }

  /// Lo que hace GL: descifra con su clave privada ECDH. Falla si la solicitud no es auténtica.
  static Uint8List abrir(String texto, ECPrivateKey glEcdhPrivada) {
    final datos = extraer(texto);
    if (datos == null) falla();
    final epk = Uint8List.sublistView(datos, 0, largoEpk);
    final okm = P256.hkdfSha256(P256.compartir(glEcdhPrivada, P256.descomprimir(epk)), epk, Uint8List.fromList(utf8.encode(info)), 44);
    return P256.aesGcmDescifrar(
      Uint8List.sublistView(okm, 0, 32),
      Uint8List.sublistView(okm, 32, 44),
      aad(epk),
      Uint8List.sublistView(datos, largoEpk),
    );
  }

  /// Busca `SPVIR1:` en un mensaje y toma los caracteres Base64url que le siguen, ignorando espacios y saltos de
  /// línea (la solicitud va al final). `null` = no hay solicitud.
  static Uint8List? extraer(String? texto) {
    if (texto == null) return null;
    final inicio = texto.indexOf(prefijo);
    if (inicio < 0) return null;
    final buffer = StringBuffer();
    for (var i = inicio + prefijo.length; i < texto.length; i++) {
      final c = texto[i];
      if (_alfabetoBase64Url.hasMatch(c)) {
        buffer.write(c);
      } else if (c.trim().isNotEmpty) {
        break;
      }
    }
    if (buffer.isEmpty) return null;
    try {
      final datos = P256.base64UrlDe(buffer.toString());
      return datos.length > largoEpk + largoTag ? datos : null;
    } catch (_) {
      return null;
    }
  }

  /// Texto del mensaje para WhatsApp: lo legible arriba y la solicitud cifrada al final (§4.1 del contrato).
  static String mensaje({
    required String nombre,
    required String apellidos,
    required String ci,
    required String telefono,
    required TipoLicenciaGL tipo,
    required int secundarias,
    required String codigo,
    String? transaccion,
    String? recupera,
  }) {
    final lineas = <String>[
      'Solicitud de licencia SPVI',
      'Nombre: $nombre $apellidos',
      'Carné de identidad: $ci',
      'Teléfono: $telefono',
      'Tipo: ${tipo.etiqueta}',
      'Apps secundarias: $secundarias',
      'Precio: ${_cup(precio(tipo, secundarias))} CUP',
    ];
    if (transaccion != null && transaccion.trim().isNotEmpty) lineas.add('Transacción: ${transaccion.trim()}');
    if (recupera != null && recupera.trim().isNotEmpty) lineas.add('Licencia anterior: ${recupera.trim()}');
    return '${lineas.join('\n')}\n\n$codigo';
  }

  /// Precio en CUP del contrato (base + importe por cada app secundaria).
  static int precio(TipoLicenciaGL tipo, int secundarias) =>
      tipo.precioCup + (secundarias < 0 ? 0 : secundarias) * tipo.precioSecundariaCup;

  /// Cifra con separador de miles por espacios (`50 000`), como en el resto del proyecto.
  static String _cup(int pesos) {
    final texto = pesos.toString();
    final salida = StringBuffer();
    for (var i = 0; i < texto.length; i++) {
      if (i > 0 && (texto.length - i) % 3 == 0) salida.write(' ');
      salida.write(texto[i]);
    }
    return salida.toString();
  }
}
