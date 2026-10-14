import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:pointycastle/export.dart';

import 'crypto.dart';

/// Claves públicas fijadas: espejo de `LicenseTrust.kt` (Android), `licensing/trust.py` (web) y del repositorio GL.
///
/// Son las claves de **una instalación concreta de GL** (el teléfono del emisor). Si el emisor las regenera hay que
/// volver a fijarlas aquí y en las otras dos apps: en los tests se comprueba que coinciden con sus huellas
/// (`comprobarFijadas`). Rotación vigente: 2026-10-05 (se conserva la anterior para re-verificar lo ya emitido).
class Trust {
  Trust._();

  /// Clave ECDH de GL: con ella se cifran las solicitudes `SPVIR1`.
  static const String ecdhSpki =
      'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+bzI3jdfnqVmHOj3+O0dksDlQTJtdGLA1O0KP+dctJW3urECwUDhLEnS1DDu7cVeaVZZkdZCU8PuxGCcPw/hJA==';
  static const String ecdhHuella =
      'sha256:2a3f:9fce:b105:7ee8:563c:04cd:e4d1:00e7:058b:c7b8:0410:024b:e658:efe1:5905:666d';

  /// Claves de firma de GL (`gl-sign-v1`): la vigente y la anterior, para lo emitido con la rotación previa.
  static const List<String> signSpkis = [
    'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1LFKFD7Af1xhbza5CrD7ArwOwq5h4qG70edEp54zozXoUa/r7P7xRQwwV82rXcy0mPznM3TNMk8wGxYCwR7ubg==',
    'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+UpBZNnTcsbL5yR5LQRD1qATSub6tV7dgaVKNgXjFUbJFbxWmUQWOQvqsOBZmt7bKDdu3iLW02Z/RtiPfa3OWQ==',
  ];
  static const List<String> signHuellas = [
    'sha256:58d4:3aac:6f1b:099a:e86c:4375:b7c7:ffb5:8631:2399:246c:1f26:f861:e302:9c83:8c4e',
    'sha256:8a91:bcfb:19dc:af1b:af6e:0bb7:c82a:9410:e9b5:aa1d:fea0:cab8:241c:52d1:bcbd:6888',
  ];

  /// Clave maestra de desarrollo de GL (mitigación 1, Opción A): **solo** vale en builds de depuración.
  static const String maestra = 'GLMK-PERM-MAXS-9999';

  /// `sha256:xxxx:…` (el formato de la pantalla «Claves» de GL).
  static String huella(String spkiBase64) {
    try {
      final der = base64.decode(spkiBase64.replaceAll(RegExp(r'\s'), ''));
      final hex = _hex(P256.sha256(der));
      final partes = <String>[];
      for (var i = 0; i < hex.length; i += 4) {
        partes.add(hex.substring(i, i + 4));
      }
      return 'sha256:${partes.join(':')}';
    } catch (_) {
      return 'sha256:?';
    }
  }

  /// ¿Las claves del build coinciden con las huellas publicadas por GL?
  static bool comprobarFijadas() {
    if (huella(ecdhSpki) != ecdhHuella) return false;
    for (var i = 0; i < signSpkis.length; i++) {
      if (huella(signSpkis[i]) != signHuellas[i]) return false;
    }
    return true;
  }

  /// Claves de firma ya decodificadas.
  static List<ECPublicKey> get clavesDeFirma =>
      _firmas ??= signSpkis.map(P256.claveDeSpki).toList(growable: false);
  static List<ECPublicKey>? _firmas;

  /// Clave ECDH de GL ya decodificada.
  static ECPublicKey get claveEcdh => _ecdh ??= P256.claveDeSpki(ecdhSpki);
  static ECPublicKey? _ecdh;

  static String _hex(List<int> datos) {
    final salida = StringBuffer();
    for (final b in datos) {
      salida.write(b.toRadixString(16).padLeft(2, '0'));
    }
    return salida.toString();
  }
}

/// ¿Estamos en una compilación de depuración? Es lo que decide si la clave maestra de GL vale algo.
///
/// Fail-closed: en release siempre `false`, así que la app publicada no activa nada con la maestra aunque el código
/// fuente esté a la vista (mitigación 1, Opción A). [forzar] existe solo para los tests.
class ModoDesarrollo {
  ModoDesarrollo._();

  static bool? _forzado;

  static bool get esDebug => _forzado ?? kDebugMode;

  @visibleForTesting
  static void forzar(bool? valor) => _forzado = valor;
}
