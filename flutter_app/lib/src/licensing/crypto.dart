import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:pointycastle/asn1.dart' as asn1;
import 'package:pointycastle/export.dart';

import 'errores.dart';

/// Primitivas P-256 (secp256r1) con **pointycastle**: Dart puro, sin plugins nativos, así que se comporta igual en el
/// simulador, en el iPhone y en `flutter test`.
///
/// Contrato GL (`SISTEMA_LICENCIAS_CLIENTES.md` §6): ECDH P-256 → HKDF-SHA256 → AES-256-GCM (IV 12, tag 16) y
/// ECDSA P-256/SHA-256. La AAD del envelope v1 se canonicaliza con longitud prefijada (mitigación 8, Opción A).
class P256 {
  P256._();

  static final ECDomainParameters dominio = ECCurve_secp256r1();

  /// Cabecera DER de una SubjectPublicKeyInfo EC P-256 (`id-ecPublicKey` + `prime256v1`), hasta el BIT STRING.
  static const List<int> _cabeceraSpki = [
    0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02, 0x01,
    0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
  ];

  // ---------------------------------------------------------------- azar y claves

  /// `Random.secure()` del sistema: en iOS usa el generador del sistema operativo.
  static SecureRandom azar() {
    final semilla = Uint8List(32);
    final seguro = Random.secure();
    for (var i = 0; i < semilla.length; i++) {
      semilla[i] = seguro.nextInt(256);
    }
    return FortunaRandom()..seed(KeyParameter(semilla));
  }

  static Uint8List aleatorio(int largo) {
    final datos = Uint8List(largo);
    final rnd = azar();
    for (var i = 0; i < largo; i++) {
      datos[i] = rnd.nextUint8();
    }
    return datos;
  }

  static AsymmetricKeyPair<PublicKey, PrivateKey> generar() {
    final generador = ECKeyGenerator()..init(ParametersWithRandom(ECKeyGeneratorParameters(dominio), azar()));
    return generador.generateKeyPair();
  }

  /// Par persistente creado desde el escalar guardado (`d`, 32 B) y su punto.
  static ECPrivateKey privadaDesde(BigInt d) => ECPrivateKey(d, dominio);

  /// Clave pública de una privada: `Q = d·G` (lo mismo que hace el generador de pointycastle).
  static ECPublicKey publicaDe(ECPrivateKey privada) => ECPublicKey(dominio.G * privada.d!, dominio);

  /// Devuelve el par a partir de un escalar guardado (identidad persistente del equipo).
  static (ECPrivateKey, ECPublicKey) parDesde(BigInt d) {
    final privada = ECPrivateKey(d, dominio);
    return (privada, publicaDe(privada));
  }

  static BigInt escalarDe(Uint8List d32) => _enteroDe(d32);

  static Uint8List bytesDe32(BigInt valor) => _entero32(valor);

  static Uint8List escalaDe(ECPrivateKey privada) => _entero32(privada.d!);

  /// Punto comprimido SEC1 (33 B): el que viaja en `devicePub` (solicitud `SPVIR1`) y en `epk` de cada licencia.
  static Uint8List comprimir(ECPublicKey publica) => publica.Q!.getEncoded(true);

  static ECPublicKey descomprimir(Uint8List punto) {
    if (punto.length != 33 || (punto[0] != 0x02 && punto[0] != 0x03)) falla();
    try {
      return ECPublicKey(dominio.curve.decodePoint(punto), dominio);
    } catch (_) {
      falla();
    }
  }

  /// SPKI DER de una clave P-256 (formato del contrato para `devicePub` del flujo v1 y para las claves de GL).
  static Uint8List spkiDe(ECPublicKey publica) =>
      Uint8List.fromList([..._cabeceraSpki, ...publica.Q!.getEncoded(false)]);

  static String spkiBase64(ECPublicKey publica) => base64.encode(spkiDe(publica));

  /// SPKI DER en Base64 → clave pública P-256 (formato del contrato para las claves de GL).
  static ECPublicKey claveDeSpki(String textoBase64) {
    try {
      return claveDeDer(Uint8List.fromList(base64.decode(textoBase64.replaceAll(RegExp(r'\s'), ''))));
    } catch (_) {
      falla();
    }
  }

  /// SPKI DER → clave pública P-256. Se lee el punto del BIT STRING y, si el ASN.1 fuera raro, de la cola fija de
  /// 65 bytes de la SPKI de P-256 (91 bytes en total, la que emite el contrato).
  static ECPublicKey claveDeDer(Uint8List der) {
    final punto = _puntoDeSpki(der);
    try {
      return ECPublicKey(dominio.curve.decodePoint(punto), dominio);
    } catch (_) {
      falla();
    }
  }

  static Uint8List _puntoDeSpki(Uint8List der) {
    try {
      final secuencia = asn1.ASN1Parser(der).nextObject() as asn1.ASN1Sequence;
      final bit = secuencia.elements![1] as asn1.ASN1BitString;
      final valores = bit.stringValues;
      if (valores != null && valores.length >= 65) return Uint8List.fromList(valores);
    } catch (_) {
      // Se intenta por la vía fija.
    }
    if (der.length == 91 && _empiezaCon(der, _cabeceraSpki)) return Uint8List.sublistView(der, 26);
    falla();
  }

  static bool _empiezaCon(Uint8List datos, List<int> prefijo) {
    if (datos.length < prefijo.length) return false;
    for (var i = 0; i < prefijo.length; i++) {
      if (datos[i] != prefijo[i]) return false;
    }
    return true;
  }

  // ---------------------------------------------------------------- ECDH / HKDF

  static Uint8List compartir(ECPrivateKey privada, ECPublicKey otra) {
    final acuerdo = ECDHBasicAgreement()..init(privada);
    return _entero32(acuerdo.calculateAgreement(otra));
  }

  static Uint8List _entero32(BigInt valor) {
    final salida = Uint8List(32);
    var v = valor;
    for (var i = 31; i >= 0; i--) {
      salida[i] = (v & BigInt.from(0xff)).toInt();
      v = v >> 8;
    }
    if (v != BigInt.zero) falla();
    return salida;
  }

  static BigInt _enteroDe(Uint8List bytes) {
    var valor = BigInt.zero;
    for (final b in bytes) {
      valor = (valor << 8) | BigInt.from(b);
    }
    return valor;
  }

  static Uint8List hmacSha256(Uint8List clave, Uint8List datos) {
    final mac = HMac(SHA256Digest(), 64)..init(KeyParameter(clave));
    return mac.process(datos);
  }

  /// HKDF-SHA256 (RFC 5869). Cada bloque `T(i)` usa una instancia nueva de HMAC (sin estado compartido).
  static Uint8List hkdfSha256(Uint8List ikm, Uint8List sal, Uint8List info, int largo) {
    final salEfectiva = sal.isEmpty ? Uint8List(32) : sal;
    final prk = hmacSha256(salEfectiva, ikm);
    final salida = Uint8List(largo);
    var previo = Uint8List(0);
    var posicion = 0;
    var contador = 1;
    while (posicion < largo) {
      final datos = Uint8List(previo.length + info.length + 1)
        ..setRange(0, previo.length, previo)
        ..setRange(previo.length, previo.length + info.length, info);
      datos[datos.length - 1] = contador;
      previo = hmacSha256(prk, datos);
      final copiar = min(previo.length, largo - posicion);
      salida.setRange(posicion, posicion + copiar, previo);
      posicion += copiar;
      contador++;
    }
    return salida;
  }

  static Uint8List sha256(Uint8List datos) => SHA256Digest().process(datos);

  /// AES-256-GCM. Devuelve `ct ‖ tag` (igual que la salida de Java en el resto del proyecto).
  static Uint8List aesGcmCifrar(Uint8List clave, Uint8List iv, Uint8List aad, Uint8List plano) {
    final cifrador = GCMBlockCipher(AESEngine())..init(true, AEADParameters(KeyParameter(clave), 128, iv, aad));
    return cifrador.process(plano);
  }

  static Uint8List aesGcmDescifrar(Uint8List clave, Uint8List iv, Uint8List aad, Uint8List cifradoConTag) {
    try {
      final cifrador = GCMBlockCipher(AESEngine())..init(false, AEADParameters(KeyParameter(clave), 128, iv, aad));
      return cifrador.process(cifradoConTag);
    } catch (_) {
      falla();
    }
  }

  // ---------------------------------------------------------------- ECDSA

  static ECSignature _firmaCon(ECPrivateKey privada, Uint8List datos) {
    final firmador = ECDSASigner(SHA256Digest(), HMac(SHA256Digest(), 64))
      ..init(true, PrivateKeyParameter<ECPrivateKey>(privada));
    return firmador.generateSignature(datos) as ECSignature;
  }

  /// Firma **cruda** `r‖s` (64 B): la de la licencia corta `SPVI2:`.
  static Uint8List firmarCrudo(ECPrivateKey privada, Uint8List datos) {
    final firma = _firmaCon(privada, datos);
    return Uint8List.fromList([..._entero32(firma.r), ..._entero32(firma.s)]);
  }

  /// Firma en **DER**: la del envelope v1 (`sig`) y la de los sub-tokens del contrato.
  static Uint8List firmarDer(ECPrivateKey privada, Uint8List datos) {
    final firma = _firmaCon(privada, datos);
    return _der(firma.r, firma.s);
  }

  static bool verificarCrudo(ECPublicKey publica, Uint8List datos, Uint8List firmaCruda) {
    if (firmaCruda.length != 64) return false;
    return _verificar(publica, datos, _enteroDe(firmaCruda.sublist(0, 32)), _enteroDe(firmaCruda.sublist(32, 64)));
  }

  static bool verificarDer(ECPublicKey publica, Uint8List datos, Uint8List firmaDer) {
    try {
      final (r, s) = leerDer(firmaDer);
      return _verificar(publica, datos, r, s);
    } catch (_) {
      return false;
    }
  }

  static bool _verificar(ECPublicKey publica, Uint8List datos, BigInt r, BigInt s) {
    try {
      final verificador = ECDSASigner(SHA256Digest(), HMac(SHA256Digest(), 64))
        ..init(false, PublicKeyParameter<ECPublicKey>(publica));
      return verificador.verifySignature(datos, ECSignature(r, s));
    } catch (_) {
      return false;
    }
  }

  /// DER `SEQUENCE(INTEGER r, INTEGER s)` con la codificación mínima (la que produce `SHA256withECDSA`).
  static Uint8List _der(BigInt r, BigInt s) {
    Uint8List entero(BigInt valor) {
      var bytes = Uint8List.fromList(_bigIntBytes(valor));
      if (bytes.isEmpty) bytes = Uint8List.fromList([0]);
      if ((bytes[0] & 0x80) != 0) bytes = Uint8List.fromList([0, ...bytes]);
      return Uint8List.fromList([0x02, bytes.length, ...bytes]);
    }

    final cuerpo = Uint8List.fromList([...entero(r), ...entero(s)]);
    if (cuerpo.length >= 0x80) falla(); // P-256: la firma nunca pasa de 72 B
    return Uint8List.fromList([0x30, cuerpo.length, ...cuerpo]);
  }

  /// DER `SEQUENCE(INTEGER r, INTEGER s)` → (r, s). Falla si no es una firma del contrato.
  static (BigInt, BigInt) leerDer(Uint8List der) {
    if (der.length < 8 || der[0] != 0x30) falla();
    if (der[1] != der.length - 2) falla();
    if (der[2] != 0x02) falla();
    final largoR = der[3];
    if (4 + largoR + 2 > der.length) falla();
    final r = _enteroDe(der.sublist(4, 4 + largoR));
    final posicion = 4 + largoR;
    if (der[posicion] != 0x02) falla();
    final largoS = der[posicion + 1];
    if (posicion + 2 + largoS != der.length) falla();
    final s = _enteroDe(der.sublist(posicion + 2, posicion + 2 + largoS));
    return (r, s);
  }

  static List<int> _bigIntBytes(BigInt valor) {
    if (valor == BigInt.zero) return const [];
    final bytes = <int>[];
    var v = valor;
    while (v > BigInt.zero) {
      bytes.insert(0, (v & BigInt.from(0xff)).toInt());
      v = v >> 8;
    }
    return bytes;
  }

  /// AAD con **longitud prefijada** (4 bytes big-endian por campo), como `CryptoEngine.canonical` de GL.
  /// Mitigación 8 (Opción A): así no se pueden inyectar delimitadores.
  static Uint8List canonico(List<String> partes) {
    final salida = BytesBuilder(copy: false);
    for (final parte in partes) {
      final datos = Uint8List.fromList(utf8.encode(parte));
      final largo = datos.length;
      salida.add(Uint8List.fromList([(largo >> 24) & 0xff, (largo >> 16) & 0xff, (largo >> 8) & 0xff, largo & 0xff]));
      salida.add(datos);
    }
    return salida.toBytes();
  }

  /// Base64 **url sin relleno**: el alfabeto de `SPVIR1:` y `SPVI2:` (aguanta el transporte por SMS y WhatsApp).
  static String base64Url(Uint8List datos) => base64UrlEncode(datos).replaceAll('=', '');

  static Uint8List base64UrlDe(String texto) {
    try {
      final limpio = texto.replaceAll(RegExp(r'\s'), '');
      return base64Url.decode(limpio + '=' * ((4 - limpio.length % 4) % 4));
    } catch (_) {
      falla();
    }
  }

  /// Base64 **estándar**: el alfabeto del envelope v1.
  static String base64(Uint8List datos) => base64Encode(datos);

  static Uint8List base64De(String texto) {
    try {
      return base64Decode(texto.replaceAll(RegExp(r'\s'), ''));
    } catch (_) {
      falla();
    }
  }
}
