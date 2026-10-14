import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:pointycastle/export.dart';
import 'package:spvi_ios/src/licensing/crypto.dart';
import 'package:spvi_ios/src/licensing/envelope.dart';
import 'package:spvi_ios/src/licensing/licencia_corta.dart';
import 'package:spvi_ios/src/licensing/trust.dart';

import 'licencia_ayudas.dart';

/// Compatibilidad **real** con GL: los vectores de `tools/licencia/` y la licencia larga que GL emitió de verdad el
/// 2026-10-03 para el dispositivo de prueba (la misma que usa `GoldenVectorTest` en Android). Todo corre sin red: si
/// nuestra criptografía se separa un byte de la de GL, estos tests fallan.
void main() {
  // Vector 0.23: dispositivo de prueba, efímera de prueba y la licencia `SPVI2:` que GL emitió para él.
  final devicePub = P256.base64UrlDe('AiuZGHe7sUgbFLfqufeF1BMCvKuYZQuJKYSxnIoc0zMK');
  const deviceId = 'SPVI:golden0000000001';
  const huellaHex = 'fb445359b06179d282c7970ea4adab32';
  const efimeraHex = '0a0b0c0d0e0f10111213141516171819a0b0c0d0e0f1f2f3f4f5f6f7f8f9fafb';
  const epkHex = '03c36dbaadc01f9f92d0554cd88b70a31e4494cf092e4747d6ff74fd226c17a2b3';
  const cifradoHex =
      '0adcdd15829c196616a17a75442d35f75cec1626c4263329266096c095a390b9d8eca0d37cf0c5c3896bc13af9545a3f75be5ff3834ff507d2b42512';
  const cuerpoHex = '027c9e6679742540de944be07fc1f90ae7fb445359b06179d282c7970ea4adab320000026ac15ec06ae8ebc0';
  const firmaSpkiVector =
      'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEvXxzuIsum0ztpiAistqL4TGTpbVu3Cbn33hC4kzQtesGBa2nvag6xqK4DX4xQED6R/8WuDushc7bAURRu3znGg==';
  const codigoVector =
      'SPVI2:A8Ntuq3AH5-S0FVM2Itwox5ElM8JLkdH1v90_SJsF6KzCtzdFYKcGWYWoXp1RC0191zsFibEJjMpJmCWwJWjkLnY7KDTfPDFw4lrwTr5VFo_db5f84NP9QfStCUSFOSb9HjaQkeWuTWUZGftUUhs0d0y5eIav9Jn59WIyb9WHNln6Ui7Y_Ce6wOVPP9Do3JcgQ-0a--x8-TLCh1g0A';
  // Clave privada del dispositivo de prueba (PKCS#8; es pública, solo existe para los vectores).
  const pkcs8Vector = 'MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCA3KZoM7faOt6GlH6Bv4WpLS+4FC4Xovdfd2ugoNaBjOg==';
  // Clave privada de firma del vector.
  const firmaPrivadaHex = '1f2e3d4c5b6a79881f2e3d4c5b6a79881f2e3d4c5b6a79881f2e3d4c5b6a7988';

  /// El escalar `d` de la PKCS#8 del vector (los 32 bytes finales del `ECPrivateKey` interno).
  BigInt escalarDelVector() =>
      P256.escalarDe(Uint8List.fromList(base64.decode(pkcs8Vector).sublist(base64.decode(pkcs8Vector).length - 32)));

  CamposCorta camposDelVector() => CamposCorta(
        id: '7c9e6679-7425-40de-944b-e07fc1f90ae7',
        huella: hex(huellaHex),
        tipo: TipoLicenciaGL.mensual,
        estado: EstadoLicenciaGL.activa,
        secundarias: 2,
        emitidaEn: DateTime.fromMillisecondsSinceEpoch(0x6ac15ec0 * 1000, isUtc: true),
        venceEn: DateTime.fromMillisecondsSinceEpoch(0x6ae8ebc0 * 1000, isUtc: true),
      );

  group('Claves de GL fijadas', () {
    test('las claves del build coinciden con las huellas publicadas por GL', () {
      expect(Trust.comprobarFijadas(), isTrue);
      expect(Trust.huella(Trust.ecdhSpki), Trust.ecdhHuella);
      expect(Trust.huella(Trust.signSpkis.first), Trust.signHuellas.first);
      expect(Trust.huella(Trust.signSpkis.last), Trust.signHuellas.last);
    });

    test('la clave ECDH de GL y las de firma se decodifican como P-256', () {
      expect(Trust.claveEcdh.Q, isNotNull);
      expect(Trust.clavesDeFirma, hasLength(2));
    });
  });

  group('Vector 0.23 (licencia corta SPVI2)', () {
    test('el punto comprimido del dispositivo se reproduce desde su clave privada', () {
      final (privada, publica) = P256.parDesde(escalarDelVector());
      expect(P256.base64Url(P256.comprimir(publica)), 'AiuZGHe7sUgbFLfqufeF1BMCvKuYZQuJKYSxnIoc0zMK');
      expect(aHex(Uint8List.fromList(P256.bytesDe32(privada.d!))), aHex(Uint8List.fromList(base64.decode(pkcs8Vector).sublist(35))));
    });

    test('la huella del dispositivo es la del vector', () {
      expect(aHex(LicenciaCorta.huella(deviceId, devicePub)), huellaHex);
    });

    test('el cuerpo en claro de 44 bytes se codifica byte a byte como en GL', () {
      expect(aHex(LicenciaCorta.cuerpo(camposDelVector())), cuerpoHex);
    });

    test('el cifrado con la efímera del vector es idéntico al de GL (AES-GCM determinista)', () {
      final efimera = P256.privadaDesde(hex(efimeraHex));
      expect(aHex(P256.comprimir(P256.publicaDe(efimera))), epkHex);
      final emitida = LicenciaCorta.emitir(
        camposDelVector(),
        devicePub,
        (datos) => P256.firmarDer(P256.privadaDesde(hex(firmaPrivadaHex)), datos),
        efimera: efimera,
        publicaEfimera: P256.publicaDe(efimera),
      );
      final codigo = LicenciaCorta.extraer(emitida)!;
      expect(aHex(codigo.epk), epkHex);
      expect(aHex(codigo.cifrado), cifradoHex);
      expect(LicenciaCorta.firmaValida(codigo, [P256.claveDeSpki(firmaSpkiVector)]), isTrue);
      // Y la firma cruda r‖s de 64 B verifica con la misma clave que la DER.
      final der = P256.firmarDer(P256.privadaDesde(hex(firmaPrivadaHex)), codigo.mensajeFirmado);
      final (r, s) = P256.leerDer(der);
      final crudo = Uint8List.fromList([...P256.bytesDe32(r), ...P256.bytesDe32(s)]);
      expect(P256.verificarCrudo(P256.claveDeSpki(firmaSpkiVector), codigo.mensajeFirmado, crudo), isTrue);
    });

    test('la licencia del vector se verifica con su clave de firma', () {
      final codigo = LicenciaCorta.extraer(codigoVector);
      expect(codigo, isNotNull);
      expect(codigo!.texto, codigoVector);
      expect(aHex(codigo.epk), epkHex);
      expect(aHex(codigo.cifrado), cifradoHex);
      expect(LicenciaCorta.firmaValida(codigo, [P256.claveDeSpki(firmaSpkiVector)]), isTrue);
      // Con otra clave cualquiera, no.
      expect(LicenciaCorta.firmaValida(codigo, [P256.publicaDe(P256.privadaDesde(BigInt.from(7)))]), isFalse);
    });

    test('el dispositivo del vector descifra su propia licencia y lee los campos', () {
      final codigo = LicenciaCorta.extraer(codigoVector)!;
      final campos = LicenciaCorta.leer(LicenciaCorta.descifrar(codigo, P256.privadaDesde(escalarDelVector())));
      expect(campos, isNotNull);
      expect(aHex(LicenciaCorta.cuerpo(campos!)), cuerpoHex);
      expect(campos.id, '7c9e6679-7425-40de-944b-e07fc1f90ae7');
      expect(campos.tipo, TipoLicenciaGL.mensual);
      expect(campos.estado, EstadoLicenciaGL.activa);
      expect(campos.secundarias, 2);
      expect(campos.emitidaEn.millisecondsSinceEpoch ~/ 1000, 0x6ac15ec0);
      expect(campos.venceEn!.millisecondsSinceEpoch ~/ 1000, 0x6ae8ebc0);
      expect(LicenciaCorta.esDeEsteDispositivo(campos, deviceId, devicePub), isTrue);
      expect(LicenciaCorta.esDeEsteDispositivo(campos, 'SPVI:otroequipo0001', devicePub), isFalse);
    });

    test('la licencia del vector se extrae de un mensaje con espacios y saltos de línea', () {
      final conFormato = 'Licencia SPVI\nTipo: Mensual\n\n${codigoVector.substring(0, 100)}\n ${codigoVector.substring(100)}';
      final codigo = LicenciaCorta.extraer(conFormato);
      expect(codigo, isNotNull);
      expect(codigo!.texto, codigoVector);
      expect(LicenciaCorta.extraer('hola, no llevo licencia'), isNull);
      expect(LicenciaCorta.extraer(null), isNull);
    });
  });

  group('Licencia larga REAL de GL (envelope v1)', () {
    // Copia exacta de `tools/licencia/respuestas/licencia_gl_mensual_2.txt`.
    const mensaje = '''
Licencia SPVI MENSUAL · 2 secundarias · 8 000 CUP
ID: d53a020b-4062-4825-8219-024f997173f8
{"v":1,"alg":"ECIES-P256-AES256GCM-v1","epk":"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfAqgiTaL3VIaZNtkTMTsEEwCvrkebVhnJDNO93ScBAZMeSn88gK09EO94/cKUiA5OqeWXJfpZhRNBT4FVvzvLw==","iv":"CgTpyZMuI59ZvP2e","ct":"6A5QCaufa9NfFmrHIW/3TjZ4pnzd2fmgGnl3VRU36B0VxKtQ4rG9ksZR3rXTv/fAqp50UdrMAw0KyUXSsY9ZsB/33jX0fQjVDWfu6ImNGk/Zpr/dCpJfVUg8hX2n/9FfLKY4fOGHEu0iznC0ZQsxkJyyDe5alG63Ga42+V4wLpVM2cAIMDzatop9q6Psvj1Rq8ewMbsy/xHkngcbt/qLQ6KZugeBQtSGiHMtr+hWxJDXbckKWnppd0qhyXjcOMqdHrsxumrS/8pwtWx2P4287Lm2mvhyhOfIk2ayH4KgDtX4OMe/OWxGVae4OWIQMRdumzY0f/kYZW5QPqHzMbFlCDT9oxwGqYnf+4TX6tE8UnxpabrJn6/h9pn1wAbBOeeowv1CwXfjKWXakUc7jUEKosDiDkcv6N/dYUceOLerOghZnZD3wHMrdmjcyol8azDAM0kHBvyzgccDjeEIUSor+PkwEwidKb3LNevo+P8MgVpGLhW4wchDH2Rs+eEqzPc1YCFqW/4PTIXX5sLX1XhoVXRc/kMreWpB+RokjPact6I=","tag":"l7T2Iz+bnGBqkp8zEVEj4g==","sig":"MEYCIQC3w8nzKzxbnHsIm4t2ZMxUo4dY1rKAYE8/NMR7M3engQIhAI4XcfzkeZI6JO1C/Xw32gCmYeiUbAF9DT84q2Cyk3CU","kid":"gl-sign-v1"}
''';
    const licenciaId = 'd53a020b-4062-4825-8219-024f997173f8';

    test('el `ID:` se extrae del mensaje (la AAD lo necesita antes de descifrar)', () {
      expect(Envelope.extraerId(mensaje), licenciaId);
      expect(Envelope.esEnvelope(mensaje), isTrue);
      expect(Envelope.esEnvelope('{"v":1,"alg":"otra-cosa"}'), isFalse);
      expect(Envelope.esEnvelope('sin sobre'), isFalse);
    });

    test('la firma del emisor se verifica con las claves fijadas (ECDSA DER)', () {
      final envelope = Envelope.extraerEnvelope(mensaje)!;
      expect(Envelope.verificarFirma(envelope, Trust.clavesDeFirma), isTrue);
      // La firmó GL con su clave anterior a la rotación del 2026-10-05 (por eso se fijan las dos).
      expect(Trust.clavesDeFirma, hasLength(2));
      // No es la firma de la efímera del sobre: eso solo lo usan las solicitudes.
      expect(Envelope.firmaDeLaEfimera(envelope), isFalse);
    });

    test('la licencia se emitió con la AAD heredada (anterior a la mitigación 8)', () {
      final envelope = Envelope.extraerEnvelope(mensaje)!;
      // La canónica no abre esta licencia…
      expect(
        () => Envelope.abrir(
          envelope,
          P256.privadaDesde(escalarDelVector()),
          aad: Envelope.aadLicencia(licenciaId),
        ),
        throwsA(isA<Exception>()),
      );
      // …y la heredada sí: por eso el cliente la sigue aceptando al re-verificar lo ya emitido.
      final plano = Envelope.abrir(
        envelope,
        P256.privadaDesde(escalarDelVector()),
        aad: Envelope.aadLicenciaHeredada(licenciaId),
      );
      final info = jsonDecode(utf8.decode(plano)) as Map<String, dynamic>;
      expect(info['appName'], 'SPVI');
      expect(info['deviceId'], deviceId);
      expect(info['id'], licenciaId);
      expect(info['tipo'], 'MENSUAL');
      expect(info['estado'], 'ACTIVA');
      expect(info['secundarias'], 2);
      expect(info['emitidaEn'], '2026-10-03T20:03:02.754Z');
      expect(info['venceEn'], '2026-11-02T20:03:02.754Z');
    });

    test('otra clave de dispositivo no la puede abrir', () {
      final envelope = Envelope.extraerEnvelope(mensaje)!;
      final ajena = P256.generar().privateKey as ECPrivateKey;
      expect(
        () => Envelope.abrir(envelope, ajena, aad: Envelope.aadLicenciaHeredada(licenciaId)),
        throwsA(isA<Exception>()),
      );
    });
  });

  group('Envelope v1 propio (ida y vuelta)', () {
    test('lo que sella el cliente lo abre el otro lado con la misma AAD y la firma de la efímera', () {
      final par = P256.generar();
      final publica = par.publicKey as ECPublicKey;
      final privada = par.privateKey as ECPrivateKey;
      final plano = Uint8List.fromList(utf8.encode('{"hola":"mundo"}'));
      final sobre = Envelope.sellar(
        plano,
        publica,
        info: Envelope.infoSolicitud,
        aad: Envelope.aadSolicitud('EPK'),
      );
      expect(sobre['v'], 1);
      expect(sobre['alg'], 'ECIES-P256-AES256GCM-v1');
      expect(sobre['kid'], 'gl-sign-v1');
      // La firma es de la efímera del propio sobre: es el caso que GL acepta en las solicitudes.
      expect(Envelope.firmaDeLaEfimera(sobre), isTrue);
      final abierto = Envelope.abrir(sobre, privada, aad: Envelope.aadSolicitud('EPK'), info: Envelope.infoSolicitud);
      expect(utf8.decode(abierto), '{"hola":"mundo"}');
      // Con el dominio equivocado (info de licencia) no abre.
      expect(
        () => Envelope.abrir(sobre, privada, aad: Envelope.aadSolicitud('EPK'), info: Envelope.infoLicencia),
        throwsA(isA<Exception>()),
      );
      // Un byte cambiado tampoco.
      final roto = Map<String, dynamic>.from(sobre);
      final ct = base64.decode(roto['ct'] as String);
      ct[0] = ct[0] ^ 0x01;
      roto['ct'] = base64.encode(ct);
      expect(() => Envelope.abrir(roto, privada, aad: Envelope.aadSolicitud('EPK'), info: Envelope.infoSolicitud),
          throwsA(isA<Exception>()));
    });

    test('la AAD con longitud prefijada separa bien los campos', () {
      expect(aHex(Envelope.aadLicencia('uno')), aHex(P256.canonico([1, Envelope.alg, 'uno'])));
      // `a|1` y `a` dan AAD distintas (con barras se podrían confundir: con longitud prefijada, no).
      expect(aHex(Envelope.aadLicencia('a|1')), isNot(aHex(Envelope.aadLicencia('a'))));
      // La heredada es exactamente el texto con barras (para poder abrir lo emitido antes).
      expect(utf8.decode(Envelope.aadLicenciaHeredada('x')), '1|${Envelope.alg}|x');
      expect(utf8.decode(Envelope.aadSolicitudHeredada('EPK')), '1|${Envelope.alg}|EPK|gl-sign-v1');
      // Y las dos formas nunca coinciden.
      expect(aHex(Envelope.aadLicencia('x')), isNot(aHex(Envelope.aadLicenciaHeredada('x'))));
    });
  });

  group('Primitivas (HKDF, AES-GCM, ECDSA)', () {
    test('HKDF-SHA256 con el vector de la RFC 5869', () {
      final ikm = Uint8List(22)..fillRange(0, 22, 0x0b);
      final sal = hex('000102030405060708090a0b0c');
      final info = hex('f0f1f2f3f4f5f6f7f8f9');
      expect(aHex(P256.hkdfSha256(ikm, sal, info, 42)),
          '3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865');
    });

    test('AES-256-GCM va y vuelve, y detecta cualquier cambio', () {
      final clave = P256.aleatorio(32);
      final iv = P256.aleatorio(12);
      final aad = Uint8List.fromList(utf8.encode('SPVI-L2|prueba'));
      final plano = Uint8List.fromList(utf8.encode('licencia de prueba'));
      final cifrado = P256.aesGcmCifrar(clave, iv, aad, plano);
      expect(cifrado.length, plano.length + 16);
      expect(utf8.decode(P256.aesGcmDescifrar(clave, iv, aad, cifrado)), 'licencia de prueba');
      expect(() => P256.aesGcmDescifrar(clave, iv, Uint8List.fromList(utf8.encode('otra cosa')), cifrado),
          throwsA(isA<Exception>()));
      final roto = Uint8List.fromList(cifrado);
      roto[0] = roto[0] ^ 0xff;
      expect(() => P256.aesGcmDescifrar(clave, iv, aad, roto), throwsA(isA<Exception>()));
    });

    test('ECDSA: DER y crudo verifican lo mismo, y el DER se lee como lo escribe GL', () {
      final par = P256.generar();
      final privada = par.privateKey as ECPrivateKey;
      final publica = par.publicKey as ECPublicKey;
      final mensaje = Uint8List.fromList(utf8.encode('SPVI-L2|prueba'));
      final der = P256.firmarDer(privada, mensaje);
      expect(der[0], 0x30);
      expect(P256.verificarDer(publica, mensaje, der), isTrue);
      final (r, s) = P256.leerDer(der);
      final crudo = Uint8List.fromList([...P256.bytesDe32(r), ...P256.bytesDe32(s)]);
      expect(crudo.length, 64);
      expect(aHex(P256.firmarCrudo(privada, mensaje)).length, 128);
      expect(P256.verificarCrudo(publica, mensaje, crudo), isTrue);
      expect(P256.verificarCrudo(publica, Uint8List.fromList(utf8.encode('otro')), crudo), isFalse);
      expect(P256.verificarCrudo(publica, mensaje, Uint8List(63)), isFalse);
      expect(P256.verificarDer(publica, mensaje, Uint8List.fromList([1, 2, 3])), isFalse);
      expect(P256.verificarDer(publica, mensaje, Uint8List.fromList([...der, 0])), isFalse);
    });

    test('las claves SPKI de P-256 se escriben y se leen (Base64 de GL incluido)', () {
      final par = P256.generar();
      final publica = par.publicKey as ECPublicKey;
      final spki = base64.encode(P256.spkiDe(publica));
      expect(spki, startsWith('MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE'));
      expect(aHex(P256.comprimir(P256.claveDeSpki(spki))), aHex(P256.comprimir(publica)));
      expect(P256.claveDeSpki(Trust.ecdhSpki).Q, isNotNull);
      expect(() => P256.claveDeSpki('no es una clave'), throwsA(isA<Exception>()));
    });
  });
}
