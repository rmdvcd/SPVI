import 'dart:convert';
import 'dart:typed_data';

import 'package:pointycastle/export.dart';

import 'crypto.dart';
import 'errores.dart';

/// Tipo de licencia, en el orden fijo del contrato (no depende del orden de los `enum`).
///
/// Los precios son los del contrato (`SISTEMA_LICENCIAS_CLIENTES.md` §7): base en CUP + un importe fijo por cada app
/// secundaria.
enum TipoLicenciaGL {
  mensual('Mensual', 'MENSUAL', 6000, 1000),
  semestral('Semestral', 'SEMESTRAL', 30000, 5000),
  anual('Anual', 'ANUAL', 50000, 9000),
  perpetua('Perpetua', 'PERPETUA', 90000, 17000);

  const TipoLicenciaGL(this.etiqueta, this.nombre, this.precioCup, this.precioSecundariaCup);

  /// Como se llama en la interfaz.
  final String etiqueta;

  /// Nombre en el contrato (lo que viaja en la solicitud y lo que devuelve la licencia).
  final String nombre;

  /// Precio base en CUP (sin secundarias).
  final int precioCup;

  /// Importe en CUP por cada app secundaria.
  final int precioSecundariaCup;
}

/// Estado de la licencia, en el orden fijo del contrato.
enum EstadoLicenciaGL { activa, vencida, revocada, perpetua }

/// Campos del cuerpo en claro de una licencia corta.
class CamposCorta {
  const CamposCorta({
    required this.id,
    required this.huella,
    required this.tipo,
    required this.estado,
    required this.secundarias,
    required this.emitidaEn,
    required this.venceEn,
  });

  final String id;
  final Uint8List huella;
  final TipoLicenciaGL tipo;
  final EstadoLicenciaGL estado;
  final int secundarias;
  final DateTime emitidaEn;
  final DateTime? venceEn;

  /// Vigente según [ahora]: no revocada ni vencida, y el vencimiento no llegó.
  bool vigente(DateTime ahora) =>
      estado != EstadoLicenciaGL.revocada &&
      estado != EstadoLicenciaGL.vencida &&
      (venceEn == null || ahora.isBefore(venceEn!));
}

/// Código `SPVI2:` descompuesto: `epk (33) ‖ ct (44) ‖ tag (16) ‖ firma (64)`.
class CodigoCorta {
  const CodigoCorta(this.epk, this.cifrado, this.firma);

  final Uint8List epk;
  final Uint8List cifrado; // ct ‖ tag
  final Uint8List firma;

  /// Forma canónica (sin espacios) que se guarda.
  String get texto => LicenciaCorta.prefijo + P256.base64Url(Uint8List.fromList([...epk, ...cifrado, ...firma]));

  /// Lo que firma GL: dominio ‖ epk ‖ ct ‖ tag.
  Uint8List get mensajeFirmado => Uint8List.fromList([...utf8.encode(LicenciaCorta.dominio), ...epk, ...cifrado]);
}

/// **Licencia corta cifrada** `SPVI2:`, la ÚNICA forma que emite GL para el teléfono y la única activable en iOS.
///
/// Espejo de `contract/LicenciaCorta.kt` (Android) y de `licensing/client.py` (web). Texto:
/// `SPVI2:` + Base64url sin relleno (210 caracteres) de `epk (33 B) ‖ ct (44 B) ‖ tag (16 B) ‖ firma (64 B)`.
///
/// Cuerpo en claro (44 B, big-endian):
/// | byte | campo |
/// |---|---|
/// | 0 | formato = 2 |
/// | 1–16 | licenseId (UUID, 16 B) |
/// | 17–32 | huella = SHA-256(`"SPVI-L2|" + deviceId + "|"` UTF-8 ‖ devicePub comprimida 33 B)[0..16] |
/// | 33 | tipo: 0 MENSUAL, 1 SEMESTRAL, 2 ANUAL, 3 PERPETUA |
/// | 34 | estado: 0 ACTIVA, 1 VENCIDA, 2 REVOCADA, 3 PERPETUA |
/// | 35 | secundarias (0..10) |
/// | 36–39 | emitidaEn, segundos Unix (uint32) |
/// | 40–43 | venceEn, segundos Unix (uint32); 0 = sin vencimiento |
///
/// Cifrado (GL → este dispositivo): `okm = HKDF-SHA256(ECDH(efímera, devicePub), sal = epk, info = "SPVI-L2", 44 B)`
/// → clave AES-256 ‖ IV (12 B), con AAD `"SPVI-L2|"` ‖ epk.
///
/// Firma: ECDSA P-256/SHA-256 de la clave de firma de GL (`gl-sign-v1`) sobre `"SPVI-L2|"` ‖ epk ‖ ct ‖ tag, en crudo
/// `r‖s`. Así otro teléfono puede comprobar que es de GL **sin** descifrarla y el dominio impide reutilizar firmas de
/// otros formatos.
class LicenciaCorta {
  LicenciaCorta._();

  static const String prefijo = 'SPVI2:';
  static const String dominio = 'SPVI-L2|';
  static const String info = 'SPVI-L2';
  static const int formato = 2;
  static const int largoCuerpo = 44;
  static const int largoEpk = 33;
  static const int largoTag = 16;
  static const int largoCifrado = largoCuerpo + largoTag; // ct ‖ tag
  static const int largoFirma = 64;
  static const int largoHuella = 16;
  static const int largoBinario = largoEpk + largoCifrado + largoFirma; // 157 B
  static const int largoBase64 = 210; // ⌈157 × 4 / 3⌉, sin relleno
  static const int largoTexto = 216; // con el prefijo
  static const List<TipoLicenciaGL> tipos = TipoLicenciaGL.values;
  static const List<EstadoLicenciaGL> estados = EstadoLicenciaGL.values;
  static final RegExp _alfabetoBase64Url = RegExp(r'[A-Za-z0-9\-_]');

  static Uint8List aad(Uint8List epk) => Uint8List.fromList([...utf8.encode(dominio), ...epk]);

  /// Primeros 16 B de SHA-256(`"SPVI-L2|" + deviceId + "|"` ‖ devicePub comprimida).
  static Uint8List huella(String deviceId, Uint8List devicePubComprimida) {
    if (devicePubComprimida.length != largoEpk) falla();
    final datos = Uint8List.fromList([...utf8.encode('$dominio$deviceId|'), ...devicePubComprimida]);
    return Uint8List.sublistView(P256.sha256(datos), 0, largoHuella);
  }

  /// Cuerpo de 44 B (lo usa el emisor: GL; aquí también los tests y la referencia de la app).
  static Uint8List cuerpo(CamposCorta campos) {
    if (campos.huella.length != largoHuella || campos.secundarias < 0 || campos.secundarias > 10) falla();
    if ((campos.tipo == TipoLicenciaGL.perpetua) != (campos.venceEn == null)) falla();
    final salida = Uint8List(largoCuerpo);
    final vista = ByteData.view(salida.buffer);
    salida[0] = formato;
    salida.setRange(1, 17, _uuidBytes(campos.id));
    salida.setRange(17, 33, campos.huella);
    salida[33] = tipos.indexOf(campos.tipo);
    salida[34] = estados.indexOf(campos.estado);
    salida[35] = campos.secundarias;
    vista.setUint32(36, _segundos(campos.emitidaEn), Endian.big);
    vista.setUint32(40, campos.venceEn == null ? 0 : _segundos(campos.venceEn!), Endian.big);
    return salida;
  }

  /// `null` = cuerpo mal formado (formato, tipo o estado desconocidos, tamaño incorrecto o incoherencias).
  static CamposCorta? leer(Uint8List? cuerpoLeido) {
    try {
      final cuerpo = cuerpoLeido!;
      if (cuerpo.length != largoCuerpo || cuerpo[0] != formato || cuerpo[35] > 10) return null;
      final vista = ByteData.view(cuerpo.buffer, cuerpo.offsetInBytes, cuerpo.length);
      final tipo = _enumDe(tipos, cuerpo[33]);
      final estado = _enumDe(estados, cuerpo[34]);
      if (tipo == null || estado == null) return null;
      final emitida = DateTime.fromMillisecondsSinceEpoch(vista.getUint32(36, Endian.big) * 1000, isUtc: true);
      final venceCrudo = vista.getUint32(40, Endian.big);
      final vence = venceCrudo == 0 ? null : DateTime.fromMillisecondsSinceEpoch(venceCrudo * 1000, isUtc: true);
      if ((tipo == TipoLicenciaGL.perpetua) != (vence == null)) return null;
      if (vence != null && !vence.isAfter(emitida)) return null;
      return CamposCorta(
        id: _uuidTexto(cuerpo, 1),
        huella: Uint8List.fromList(cuerpo.sublist(17, 33)),
        tipo: tipo,
        estado: estado,
        secundarias: cuerpo[35],
        emitidaEn: emitida,
        venceEn: vence,
      );
    } catch (_) {
      return null;
    }
  }

  /// Emisor de referencia (lo que hace GL): cifra [campos] hacia el dispositivo y firma con [firmarDer] (ECDSA DER de
  /// la clave de firma de GL). [efimera] y [publicaEfimera] solo se pasan en los vectores de prueba.
  static String emitir(
    CamposCorta campos,
    Uint8List devicePubComprimida,
    Uint8List Function(Uint8List) firmarDer, {
    ECPrivateKey? efimera,
    ECPublicKey? publicaEfimera,
  }) {
    final par = P256.generar();
    final privadaEfimera = efimera ?? par.privateKey as ECPrivateKey;
    final puntoEfimero = publicaEfimera ?? (efimera == null ? par.publicKey as ECPublicKey : P256.publicaDe(privadaEfimera));
    final epk = P256.comprimir(puntoEfimero);
    final (clave, iv) = _claveIv(P256.compartir(privadaEfimera, P256.descomprimir(devicePubComprimida)), epk);
    final cifrado = P256.aesGcmCifrar(clave, iv, aad(epk), cuerpo(campos));
    final sinFirma = CodigoCorta(epk, cifrado, Uint8List(largoFirma));
    final firma = _derARaw(firmarDer(sinFirma.mensajeFirmado));
    return CodigoCorta(epk, cifrado, firma).texto;
  }

  /// Descifra con la clave privada del dispositivo. Falla (genérico) si no es para esta clave.
  static Uint8List descifrar(CodigoCorta codigo, ECPrivateKey privadaDispositivo) {
    final (clave, iv) = _claveIv(P256.compartir(privadaDispositivo, P256.descomprimir(codigo.epk)), codigo.epk);
    return P256.aesGcmDescifrar(clave, iv, aad(codigo.epk), codigo.cifrado);
  }

  /// Verifica la firma de GL sobre el código (sin descifrar). Nunca lanza.
  static bool firmaValida(CodigoCorta codigo, List<ECPublicKey> clavesDeFirma) {
    for (final clave in clavesDeFirma) {
      if (P256.verificarCrudo(clave, codigo.mensajeFirmado, codigo.firma)) return true;
    }
    return false;
  }

  /// Busca `SPVI2:` en un mensaje (WhatsApp, SMS o un QR) y toma los 210 caracteres Base64url siguientes, ignorando
  /// espacios y saltos de línea que algunas apps insertan al partir líneas. `null` = no hay código completo.
  static CodigoCorta? extraer(String? texto) {
    if (texto == null) return null;
    var desde = texto.indexOf(prefijo);
    while (desde >= 0) {
      final buffer = StringBuffer();
      var i = desde + prefijo.length;
      while (i < texto.length && buffer.length < largoBase64) {
        final c = texto[i];
        if (_alfabetoBase64Url.hasMatch(c)) {
          buffer.write(c);
        } else if (c.trim().isNotEmpty) {
          break;
        }
        i++;
      }
      if (buffer.length == largoBase64) {
        try {
          final datos = P256.base64UrlDe(buffer.toString());
          if (datos.length == largoBinario) {
            return CodigoCorta(
              Uint8List.sublistView(datos, 0, largoEpk),
              Uint8List.sublistView(datos, largoEpk, largoEpk + largoCifrado),
              Uint8List.sublistView(datos, largoEpk + largoCifrado),
            );
          }
        } catch (_) {
          // Se sigue buscando otro prefijo: puede haber varios códigos en el mensaje.
        }
      }
      desde = texto.indexOf(prefijo, desde + 1);
    }
    return null;
  }

  /// ¿El cuerpo corresponde a este dispositivo (su deviceId y su clave)?
  static bool esDeEsteDispositivo(CamposCorta campos, String deviceId, Uint8List devicePubComprimida) {
    final propia = huella(deviceId, devicePubComprimida);
    return _iguales(campos.huella, propia);
  }

  /// Comparación en tiempo constante (las huellas no son secreto, pero no cuesta nada).
  static bool _iguales(Uint8List a, Uint8List b) {
    if (a.length != b.length) return false;
    var diferencia = 0;
    for (var i = 0; i < a.length; i++) {
      diferencia |= a[i] ^ b[i];
    }
    return diferencia == 0;
  }

  // ---------------------------------------------------------------- interno

  static (Uint8List, Uint8List) _claveIv(Uint8List compartido, Uint8List epk) {
    final okm = P256.hkdfSha256(compartido, epk, Uint8List.fromList(utf8.encode(info)), 44);
    return (Uint8List.sublistView(okm, 0, 32), Uint8List.sublistView(okm, 32, 44));
  }

  static int _segundos(DateTime momento) {
    final segundos = momento.toUtc().millisecondsSinceEpoch ~/ 1000;
    if (segundos < 1 || segundos > 0xFFFFFFFF) falla();
    return segundos;
  }

  static Uint8List _uuidBytes(String id) {
    final hex = id.replaceAll('-', '');
    if (hex.length != 32) falla();
    final salida = Uint8List(16);
    for (var i = 0; i < 16; i++) {
      salida[i] = int.parse(hex.substring(i * 2, i * 2 + 2), radix: 16);
    }
    return salida;
  }

  static String _uuidTexto(Uint8List cuerpo, int desde) {
    final buffer = StringBuffer();
    for (var i = 0; i < 16; i++) {
      buffer.write(cuerpo[desde + i].toRadixString(16).padLeft(2, '0'));
      if (i == 3 || i == 5 || i == 7 || i == 9) buffer.write('-');
    }
    return buffer.toString();
  }

  static T? _enumDe<T>(List<T> valores, int indice) => indice >= 0 && indice < valores.length ? valores[indice] : null;

  /// DER → crudo `r‖s` de 64 B (la firma del contrato viaja en crudo).
  static Uint8List _derARaw(Uint8List der) {
    final (r, s) = P256.leerDer(der);
    return Uint8List.fromList([...P256.bytesDe32(r), ...P256.bytesDe32(s)]);
  }
}
