import 'dart:convert';
import 'dart:typed_data';

import 'package:pointycastle/export.dart';

import 'crypto.dart';
import 'errores.dart';

/// Envelope JSON v1 del contrato GL (solicitud y licencia larga).
///
/// Espejo de `contract/EnvelopeV1.kt` (Android) y de `licensing/envelope.py` (web). Referencia:
/// `SISTEMA_LICENCIAS_CLIENTES.md` §4–§6 y `SECURITY.md` (mitigaciones 5, 6 y 8) del repositorio GL.
///
/// * `alg` = `ECIES-P256-AES256GCM-v1`; ECDH P-256 → HKDF-SHA256 (sal = IV, info `gl-req-v1` / `gl-lic-v1`) →
///   AES-256-GCM; firma ECDSA P-256/SHA-256 (DER) sobre `epk ‖ iv ‖ ct ‖ tag`.
/// * La AAD va **canonicalizada con longitud prefijada** (4 bytes big-endian por campo), como `CryptoEngine.canonical`
///   de GL: mitigación 8 (inyección de delimitadores). La AAD heredada (`1|alg|id`) solo se acepta al descifrar, para
///   poder re-verificar lo emitido antes de la mitigación.
class Envelope {
  Envelope._();

  static const int version = 1;
  static const String alg = 'ECIES-P256-AES256GCM-v1';
  static const String kid = 'gl-sign-v1';
  static const String infoSolicitud = 'gl-req-v1';
  static const String infoLicencia = 'gl-lic-v1';
  static const int ivLargo = 12;
  static const int tagLargo = 16;
  static const String app = 'SPVI';

  static final RegExp _inicio = RegExp(r'\{\s*"v"\s*:\s*1\s*,');
  static final RegExp _id = RegExp(r'^\s*ID\s*:\s*([A-Za-z0-9\-]{8,64})\s*$', multiLine: true);

  /// AAD con longitud prefijada (4 bytes big-endian), igual que GL.
  static Uint8List canonico(List<Object?> partes) => P256.canonico([for (final p in partes) '$p']);

  static Uint8List aadSolicitud(String epkBase64) => canonico([version, alg, epkBase64, kid]);

  static Uint8List aadLicencia(String licenciaId) => canonico([version, alg, licenciaId]);

  /// AAD anterior a la mitigación 8: solo para **abrir** lo ya emitido con ella.
  static Uint8List aadSolicitudHeredada(String epkBase64) => Uint8List.fromList(utf8.encode('$version|$alg|$epkBase64|$kid'));

  static Uint8List aadLicenciaHeredada(String licenciaId) => Uint8List.fromList(utf8.encode('$version|$alg|$licenciaId'));

  /// HKDF-SHA256 (sal = IV, 32 B) → clave AES-256. El IV del GCM es el mismo que la sal, como en GL.
  static Uint8List derivar(Uint8List compartido, Uint8List sal, String info) =>
      P256.hkdfSha256(compartido, sal, Uint8List.fromList(utf8.encode(info)), 32);

  /// Envelope v1 como `Map` listo para `jsonEncode` (ct y tag van separados, como en GL).
  static Map<String, dynamic> documento({
    required Uint8List epk,
    required Uint8List iv,
    required Uint8List ct,
    required Uint8List tag,
    required Uint8List sig,
  }) => {
    'v': version,
    'alg': alg,
    'epk': base64.encode(epk),
    'iv': base64.encode(iv),
    'ct': base64.encode(ct),
    'tag': base64.encode(tag),
    'sig': base64.encode(sig),
    'kid': kid,
  };

  /// Transcript que se firma: `epk ‖ iv ‖ ct ‖ tag`.
  static Uint8List transcript(Map<String, dynamic> envelope) => Uint8List.fromList([
    ..._dato(envelope['epk']),
    ..._dato(envelope['iv']),
    ..._dato(envelope['ct']),
    ..._dato(envelope['tag']),
  ]);

  /// Cifra [plano] hacia [destino] y firma el transcript. [firmante] `null` = firma la **efímera** del propio
  /// envelope (es lo que hace un cliente al solicitar: GL acepta la firma de la efímera).
  static Map<String, dynamic> sellar(
    Uint8List plano,
    ECPublicKey destino, {
    ECPrivateKey? firmante,
    required String info,
    required Uint8List aad,
    Uint8List? iv,
  }) {
    final par = P256.generar();
    final efimera = par.privateKey as ECPrivateKey;
    final nonce = iv ?? P256.aleatorio(ivLargo);
    final epk = P256.spkiDe(par.publicKey as ECPublicKey);
    final clave = derivar(P256.compartir(efimera, destino), nonce, info);
    final cifrado = P256.aesGcmCifrar(clave, nonce, aad, plano);
    final ct = Uint8List.sublistView(cifrado, 0, cifrado.length - tagLargo);
    final tag = Uint8List.sublistView(cifrado, cifrado.length - tagLargo);
    final transcripto = Uint8List.fromList([...epk, ...nonce, ...ct, ...tag]);
    final firma = P256.firmarDer(firmante ?? efimera, transcripto);
    return documento(epk: epk, iv: nonce, ct: ct, tag: tag, sig: firma);
  }

  /// Descifra un envelope con la clave privada ECDH propia.
  static Uint8List abrir(
    Map<String, dynamic> envelope,
    ECPrivateKey privada, {
    required Uint8List aad,
    String info = infoLicencia,
  }) {
    _comprobarForma(envelope);
    final epk = _dato(envelope['epk']);
    final iv = _dato(envelope['iv']);
    final ct = _dato(envelope['ct']);
    final tag = _dato(envelope['tag']);
    final ajena = P256.claveDeDer(epk);
    final clave = derivar(P256.compartir(privada, ajena), iv, info);
    return P256.aesGcmDescifrar(clave, iv, aad, Uint8List.fromList([...ct, ...tag]));
  }

  /// Firma del emisor sobre el transcript (sin descifrar). No lanza.
  static bool verificarFirma(Map<String, dynamic> envelope, List<ECPublicKey> publicas) {
    try {
      final transcripto = transcript(envelope);
      final firma = _dato(envelope['sig']);
      for (final publica in publicas) {
        if (P256.verificarDer(publica, transcripto, firma)) return true;
      }
      return false;
    } catch (_) {
      return false;
    }
  }

  /// Firma del cliente: la comprueba la propia efímera del envelope.
  static bool firmaDeLaEfimera(Map<String, dynamic> envelope) {
    try {
      _comprobarForma(envelope);
      final epk = _dato(envelope['epk']);
      final efimera = P256.claveDeDer(epk);
      return P256.verificarDer(efimera, transcript(envelope), _dato(envelope['sig']));
    } catch (_) {
      return false;
    }
  }

  /// Saca el envelope JSON de un mensaje (texto + `ID:` + envelope). `null` si no hay.
  static Map<String, dynamic>? extraerEnvelope(String? texto) {
    if (texto == null) return null;
    final inicio = _inicio.firstMatch(texto);
    if (inicio == null) return null;
    final fin = texto.lastIndexOf('}');
    if (fin <= inicio.start) return null;
    try {
      final datos = jsonDecode(texto.substring(inicio.start, fin + 1));
      return datos is Map<String, dynamic> ? datos : null;
    } catch (_) {
      return null;
    }
  }

  /// `ID:` del mensaje de GL: la licencia larga lo lleva **fuera** del sobre porque la AAD del GCM lo necesita antes
  /// de descifrar.
  static String? extraerId(String? texto) {
    if (texto == null) return null;
    final encontrado = _id.firstMatch(texto);
    return encontrado?.group(1);
  }

  static bool esEnvelope(String? texto) {
    final envelope = extraerEnvelope(texto);
    if (envelope == null) return false;
    return '${envelope['v']}' == '$version' && envelope['alg'] == alg && envelope['kid'] == kid;
  }

  /// Cuerpo plano de la **solicitud v1** (§4.2), por si algún día hace falta el formato largo de solicitud.
  static Map<String, dynamic> solicitudV1({
    required String nombre,
    required String apellidos,
    required String ci,
    required String telefono,
    required String tipo,
    required int secundarias,
    required String deviceId,
    required String devicePubSpkiBase64,
    String plataforma = 'IOS',
    Uint8List? nonce,
  }) {
    final usuario = {
      'nombre': nombre,
      'apellidos': apellidos,
      'ci': ci,
      'telefono': telefono,
      'via': 'WHATSAPP',
    };
    return {
      'v': version,
      'deviceId': deviceId,
      'appName': app,
      'dispositivo': plataforma,
      'nombre': nombre,
      'apellidos': apellidos,
      'ci': ci,
      'via': 'WHATSAPP',
      'telefono': telefono,
      'usuario': usuario,
      'tipo': tipo,
      'secundarias': secundarias,
      'solicitadaEn': DateTime.now().toUtc().toIso8601String(),
      'nonce': P256.base64Url(nonce ?? P256.aleatorio(16)),
      'devicePub': devicePubSpkiBase64,
    };
  }

  static void _comprobarForma(Map<String, dynamic> envelope) {
    if ('${envelope['v']}' != '$version' || envelope['alg'] != alg) falla();
    final iv = _dato(envelope['iv']);
    final tag = _dato(envelope['tag']);
    if (iv.length != ivLargo || tag.length != tagLargo) falla();
  }

  static Uint8List _dato(Object? valor) {
    if (valor is! String || valor.isEmpty) falla();
    return P256.base64De(valor);
  }
}
