import 'dart:convert';
import 'dart:typed_data';

import 'package:pointycastle/export.dart';

import 'crypto.dart';
import 'errores.dart';

/// Sub-token de app secundaria (mitigación 7 del sistema de licencias de GL, Opción A):
/// la cuota de apps secundarias deja de ser una auto-declaración y pasa a ser **un permiso firmado por la app
/// principal**.
///
/// Formato (`SISTEMA_LICENCIAS_CLIENTES.md` §8.4), idéntico en las tres apps:
/// ```
/// <licenseId>|<deviceId secundaria>|<ranura>|<permitidas>|<venceEn ISO-8601 | PERPETUA>.<firma Base64 DER>
/// ```
/// * `ranura`: 1..`permitidas` (la cuota que trae la licencia de la principal).
/// * `venceEn`: el de la licencia principal; `PERPETUA` = sin vencimiento.
/// * La firma es ECDSA P-256/SHA-256 (DER) de la principal sobre el texto de la izquierda, tal cual.
class SubToken {
  SubToken._();

  static const String perpetua = 'PERPETUA';
  static const String separadorCampos = '|';
  static const String separadorFirma = '.';
  static const int largoMaximo = 4096;
  static const String idMaestra = 'master-license-local';
  static const String idPrueba = 'prueba';

  /// Texto firmable de un sub-token.
  static String texto(String licenciaId, String dispositivo, int ranura, int permitidas, DateTime? venceEn) => [
    licenciaId,
    dispositivo,
    '$ranura',
    '$permitidas',
    venceEn == null ? perpetua : venceEn.toUtc().toIso8601String(),
  ].join(separadorCampos);

  /// Emite el sub-token de la ranura [ranura] para [dispositivo]. Falla si los datos no encajan.
  static String emitir(
    ECPrivateKey firmante,
    String licenciaId,
    int permitidas,
    DateTime? venceEn,
    String dispositivo,
    int ranura,
  ) {
    if (permitidas < 0 || permitidas > 10) falla();
    if (ranura < 1 || ranura > permitidas) falla();
    if (licenciaId.isEmpty || licenciaId.length > 64) falla();
    if (dispositivo.isEmpty || dispositivo.length > 128) falla();
    if (_tieneSeparadores(licenciaId) || _tieneSeparadores(dispositivo)) falla();
    final contenido = texto(licenciaId, dispositivo, ranura, permitidas, venceEn);
    final firma = P256.firmarDer(firmante, Uint8List.fromList(utf8.encode(contenido)));
    return '$contenido$separadorFirma${base64.encode(firma)}';
  }

  /// Verifica un sub-token con la clave pública de la principal. Devuelve sus datos **solo** si todo cuadra: firma,
  /// dispositivo, ranura dentro de `1..permitidas`, `permitidas` dentro de la cuota de la licencia ([cuotaMaxima]),
  /// formato correcto y no vencido. Cualquier otra cosa devuelve `null` (nunca lanza).
  static DatosSubToken? verificar(
    String token,
    Uint8List firmaSpki,
    String dispositivoEsperado,
    DateTime ahora, {
    int cuotaMaxima = 10,
  }) {
    try {
      if (token.isEmpty || token.length > largoMaximo) return null;
      final corte = token.lastIndexOf(separadorFirma);
      if (corte <= 0 || corte == token.length - 1) return null;
      final contenido = token.substring(0, corte);
      final firma = base64.decode(token.substring(corte + 1));
      final campos = contenido.split(separadorCampos);
      if (campos.length != 5) return null;
      if (!P256.verificarDer(P256.claveDeDer(firmaSpki), Uint8List.fromList(utf8.encode(contenido)), firma)) return null;
      final ranura = int.tryParse(campos[2]);
      final permitidas = int.tryParse(campos[3]);
      if (ranura == null || permitidas == null) return null;
      if (permitidas < 0 || permitidas > 10 || permitidas > cuotaMaxima) return null;
      if (ranura < 1 || ranura > permitidas) return null;
      if (campos[1] != dispositivoEsperado) return null;
      final vence = campos[4] == perpetua ? null : DateTime.parse(campos[4]).toUtc();
      final datos = DatosSubToken(
        licenciaId: campos[0],
        dispositivo: campos[1],
        ranura: ranura,
        permitidas: permitidas,
        venceEn: vence,
      );
      return datos.vigente(ahora) ? datos : null;
    } catch (_) {
      return null;
    }
  }

  /// Verificación contra una clave pública en SPKI Base64 (es como viaja el sub-token de la principal).
  static DatosSubToken? verificarBase64(
    String token,
    String firmaSpkiBase64,
    String dispositivoEsperado,
    DateTime ahora, {
    int cuotaMaxima = 10,
  }) {
    try {
      return verificar(token, P256.base64De(firmaSpkiBase64), dispositivoEsperado, ahora, cuotaMaxima: cuotaMaxima);
    } catch (_) {
      return null;
    }
  }

  /// ¿Es el `licenciaId` de una clave maestra de desarrollo? (nunca cuenta como licencia real).
  static bool esMaestra(String licenciaId) => licenciaId == idMaestra;

  static bool _tieneSeparadores(String texto) =>
      texto.contains(separadorCampos) || texto.contains(separadorFirma);
}

/// Datos que autoriza un sub-token.
class DatosSubToken {
  const DatosSubToken({
    required this.licenciaId,
    required this.dispositivo,
    required this.ranura,
    required this.permitidas,
    required this.venceEn,
  });

  final String licenciaId;
  final String dispositivo;
  final int ranura;
  final int permitidas;
  final DateTime? venceEn;

  /// Un sub-token perpetuo o con vencimiento posterior a [ahora] sigue autorizando.
  bool vigente(DateTime ahora) => venceEn == null || ahora.isBefore(venceEn!);
}
