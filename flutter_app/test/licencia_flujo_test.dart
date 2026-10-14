import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:pointycastle/export.dart';
import 'package:spvi_ios/src/licensing/almacen.dart';
import 'package:spvi_ios/src/licensing/codigo.dart';
import 'package:spvi_ios/src/licensing/cliente.dart';
import 'package:spvi_ios/src/licensing/crypto.dart';
import 'package:spvi_ios/src/licensing/envelope.dart';
import 'package:spvi_ios/src/licensing/licencia_corta.dart';
import 'package:spvi_ios/src/licensing/solicitud.dart';
import 'package:spvi_ios/src/licensing/subtokens.dart';
import 'package:spvi_ios/src/licensing/trust.dart';

import 'licencia_ayudas.dart';

/// Flujo completo de la licencia en la app iOS, sin red y con un emisor de prueba (nunca las claves de verdad):
/// activación `SPVI2`, licencia larga del contrato v1, clave maestra de desarrollo, marca de agua del reloj,
/// sub-tokens de apps secundarias, lista de revocadas y solicitudes `SPVIR1`.
void main() {
  final t0 = DateTime.utc(2026, 10, 10, 12);

  group('Estado inicial y período de prueba', () {
    test('un equipo nuevo está en prueba 7 días y tiene identidad propia', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final estado = await cliente.estado();
      expect(cliente.deviceId, startsWith('SPVI:'));
      expect(cliente.deviceId.length, greaterThan(20));
      expect(estado.clase, ClaseLicencia.prueba);
      expect(estado.operativa, isTrue);
      expect(estado.secundarias, ClienteLicencia.secundariasDefecto);
      expect(estado.diasRestantes, 7);
      expect(await cliente.requerirActiva(), isA<EstadoLicencia>());
      // La identidad se guarda: al reabrir el mismo documento, el deviceId es el mismo.
      final otro = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      expect(otro.deviceId, cliente.deviceId);
    });

    test('la prueba termina a los 7 días y bloquea la app', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      expect((await cliente.estado()).clase, ClaseLicencia.prueba);
      final tarde = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0.add(const Duration(days: 8)));
      final estado = await tarde.estado();
      expect(estado.clase, ClaseLicencia.bloqueada);
      expect(estado.motivo, contains('prueba'));
      expect(() => tarde.requerirActiva(), throwsA(isA<LicenciaInvalida>()));
    });
  });

  group('Activación de la licencia corta (SPVI2)', () {
    test('una licencia emitida por GL para este equipo se activa y queda activa', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final codigo = emisor.licenciaCorta(
        deviceId: cliente.deviceId,
        devicePub: cliente.devicePubComprimida,
        emitidaEn: t0,
        venceEn: t0.add(const Duration(days: 30)),
        secundarias: 3,
      );
      final resultado = await cliente.activar(codigo);
      expect(resultado.aceptada, isTrue, reason: resultado.motivo);
      final estado = await cliente.estado();
      expect(estado.clase, ClaseLicencia.activa);
      expect(estado.tipo, TipoLicenciaGL.mensual);
      expect(estado.secundarias, 3);
      expect(estado.diasRestantes, 30);
      expect(estado.id, '7c9e6679-7425-40de-944b-e07fc1f90ae7');
      // Se re-verifica entera al reabrir el documento (nada de guardar un «ya está activada»).
      final tarde = await clienteDePrueba(
        emisor: emisor,
        almacen: almacen,
        reloj: () => t0.add(const Duration(days: 10)),
      );
      expect((await tarde.estado()).clase, ClaseLicencia.activa);
    });

    test('una licencia perpetua queda perpetua y sin días de cuenta atrás', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final codigo = emisor.licenciaCorta(
        deviceId: cliente.deviceId,
        devicePub: cliente.devicePubComprimida,
        emitidaEn: t0,
        tipo: TipoLicenciaGL.perpetua,
        estado: EstadoLicenciaGL.perpetua,
        secundarias: 10,
      );
      expect((await cliente.activar(codigo)).aceptada, isTrue);
      final estado = await cliente.estado();
      expect(estado.clase, ClaseLicencia.perpetua);
      expect(estado.secundarias, 10);
      expect(estado.venceEn, isNull);
      expect(estado.diasRestantes, 0);
      expect(await cliente.licenciaRenovable(), isNull);
    });

    test('un texto sin licencia no activa nada y se queda en prueba', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final resultado = await cliente.activar('hola, esto no es una licencia');
      expect(resultado.aceptada, isFalse);
      expect(resultado.motivo, isNotNull);
      expect((await cliente.estado()).clase, ClaseLicencia.prueba);
    });

    test('una licencia para OTRO equipo se rechaza (huella del dispositivo)', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final ajena = P256.generar();
      final codigo = emisor.licenciaCorta(
        deviceId: 'SPVI:otroequipo00000000000000000000',
        devicePub: P256.comprimir(ajena.publicKey as ECPublicKey),
        emitidaEn: t0,
        venceEn: t0.add(const Duration(days: 30)),
      );
      final resultado = await cliente.activar(codigo);
      expect(resultado.aceptada, isFalse);
      expect((await cliente.estado()).clase, ClaseLicencia.prueba);
    });

    test('una licencia firmada por otra clave se rechaza aunque esté bien cifrada', () async {
      final emisor = EmisorDePrueba();
      final otroEmisor = EmisorDePrueba();
      final cliente = await clienteDePrueba(emisor: emisor, reloj: () => t0);
      final codigo = otroEmisor.licenciaCorta(
        deviceId: cliente.deviceId,
        devicePub: cliente.devicePubComprimida,
        emitidaEn: t0,
        venceEn: t0.add(const Duration(days: 30)),
      );
      expect((await cliente.activar(codigo)).aceptada, isFalse);
    });

    test('un código alterado se rechaza (GCM y firma)', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final codigo = emisor.licenciaCorta(
        deviceId: cliente.deviceId,
        devicePub: cliente.devicePubComprimida,
        emitidaEn: t0,
        venceEn: t0.add(const Duration(days: 30)),
      );
      final roto = codigo.replaceRange(codigo.length - 5, codigo.length - 4, codigo.endsWith('A') ? 'B' : 'A');
      expect((await cliente.activar(roto)).aceptada, isFalse);
    });

    test('una licencia vencida o revocada se rechaza', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final vencida = emisor.licenciaCorta(
        deviceId: cliente.deviceId,
        devicePub: cliente.devicePubComprimida,
        emitidaEn: t0.subtract(const Duration(days: 60)),
        venceEn: t0.subtract(const Duration(days: 1)),
      );
      final resultado = await cliente.activar(vencida);
      expect(resultado.aceptada, isFalse);
      expect(resultado.motivo, contains('vigente'));
      final revocada = emisor.licenciaCorta(
        deviceId: cliente.deviceId,
        devicePub: cliente.devicePubComprimida,
        emitidaEn: t0,
        venceEn: t0.add(const Duration(days: 30)),
        estado: EstadoLicenciaGL.revocada,
      );
      expect((await cliente.activar(revocada)).aceptada, isFalse);
    });

    test('no se instala una licencia más antigua que la que ya está', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final nueva = emisor.licenciaCorta(
        deviceId: cliente.deviceId,
        devicePub: cliente.devicePubComprimida,
        emitidaEn: t0,
        venceEn: t0.add(const Duration(days: 30)),
      );
      expect((await cliente.activar(nueva)).aceptada, isTrue);
      final vieja = emisor.licenciaCorta(
        deviceId: cliente.deviceId,
        devicePub: cliente.devicePubComprimida,
        emitidaEn: t0.subtract(const Duration(days: 5)),
        venceEn: t0.add(const Duration(days: 25)),
        id: '11111111-2222-3333-4444-555555555555',
      );
      final resultado = await cliente.activar(vieja);
      expect(resultado.aceptada, isFalse);
      expect(resultado.motivo, contains('más reciente'));
      expect((await cliente.estado()).id, '7c9e6679-7425-40de-944b-e07fc1f90ae7');
    });

    test('la cuota de secundarias y el id quedan disponibles para renovar', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      expect(await cliente.licenciaRenovable(), isNull);
      final codigo = emisor.licenciaCorta(
        deviceId: cliente.deviceId,
        devicePub: cliente.devicePubComprimida,
        emitidaEn: t0,
        venceEn: t0.add(const Duration(days: 30)),
      );
      await cliente.activar(codigo);
      expect(await cliente.licenciaRenovable(), '7c9e6679-7425-40de-944b-e07fc1f90ae7');
    });
  });

  group('Licencia larga del contrato v1', () {
    String sobreLargo({
      required EmisorDePrueba emisor,
      required ClienteLicencia cliente,
      String appName = 'SPVI',
      String? deviceId,
      String id = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
      String tipo = 'MENSUAL',
      String estado = 'ACTIVA',
      int secundarias = 2,
      DateTime? emitidaEn,
      DateTime? venceEn,
      String? codigo16 = 'ABCD-EFGH-IJKL-MNOP',
    }) {
      final plano = utf8.encode(jsonEncode({
        'v': 1,
        'appName': appName,
        'deviceId': deviceId ?? cliente.deviceId,
        'id': id,
        'tipo': tipo,
        'estado': estado,
        'secundarias': secundarias,
        'emitidaEn': (emitidaEn ?? t0).toUtc().toIso8601String(),
        'venceEn': venceEn?.toUtc().toIso8601String(),
        if (codigo16 != null) 'licenciaObtenida': codigo16,
        if (codigo16 != null) 'codigoCorto': codigo16,
      }));
      final sobre = Envelope.sellar(
        Uint8List.fromList(plano),
        P256.claveDeSpki(cliente.firmaPublicaBase64),
        firmante: emisor.privateFirma,
        info: Envelope.infoLicencia,
        aad: Envelope.aadLicencia(id),
      );
      return 'Licencia SPVI\nID: $id\n${jsonEncode(sobre)}';
    }

    test('un mensaje del contrato v1 se activa (AAD canónica, firma de GL)', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final mensaje = sobreLargo(
        emisor: emisor,
        cliente: cliente,
        venceEn: t0.add(const Duration(days: 30)),
      );
      final resultado = await cliente.activar(mensaje);
      expect(resultado.aceptada, isTrue, reason: resultado.motivo);
      final estado = await cliente.estado();
      expect(estado.clase, ClaseLicencia.activa);
      expect(estado.id, 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee');
      expect(estado.secundarias, 2);
      // Y se vuelve a verificar al reabrir (la licencia larga guardada se descifra otra vez).
      final tarde = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0.add(const Duration(days: 3)));
      expect((await tarde.estado()).clase, ClaseLicencia.activa);
    });

    test('una licencia larga de otra app, de otro equipo o vencida se rechaza', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final otraApp = sobreLargo(emisor: emisor, cliente: cliente, appName: 'OTRA', venceEn: t0.add(const Duration(days: 30)));
      expect((await cliente.activar(otraApp)).aceptada, isFalse);
      final otroEquipo = sobreLargo(
        emisor: emisor,
        cliente: cliente,
        deviceId: 'SPVI:otroequipo00000000000000000000',
        venceEn: t0.add(const Duration(days: 30)),
      );
      expect((await cliente.activar(otroEquipo)).aceptada, isFalse);
      final vencida = sobreLargo(
        emisor: emisor,
        cliente: cliente,
        emitidaEn: t0.subtract(const Duration(days: 60)),
        venceEn: t0.subtract(const Duration(days: 1)),
      );
      expect((await cliente.activar(vencida)).aceptada, isFalse);
      expect((await cliente.estado()).clase, ClaseLicencia.prueba);
    });

    test('una licencia larga sin el ID en el mensaje no se puede ni descifrar', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final mensaje = sobreLargo(emisor: emisor, cliente: cliente, venceEn: t0.add(const Duration(days: 30)));
      final sinId = mensaje.replaceAll(RegExp(r'^ID:.*$', multiLine: true), '');
      expect((await cliente.activar(sinId)).aceptada, isFalse);
    });
  });

  group('Clave maestra de desarrollo (solo depuración)', () {
    test('en depuración la maestra deja la app perpetua con 10 secundarias', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0, esDebug: true);
      final resultado = await cliente.activar(Trust.maestra);
      expect(resultado.aceptada, isTrue, reason: resultado.motivo);
      final estado = await cliente.estado();
      expect(estado.clase, ClaseLicencia.perpetua);
      expect(estado.secundarias, ClienteLicencia.secundariasMax);
      expect(estado.id, SubToken.idMaestra);
    });

    test('en release la maestra no vale nada (mitigación 1)', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0, esDebug: false);
      final resultado = await cliente.activar('GLMK-PERM-MAXS-9999');
      expect(resultado.aceptada, isFalse);
      expect((await cliente.estado()).clase, ClaseLicencia.prueba);
      // Y si un equipo la tuviera instalada de una versión de depuración, en release se ignora.
      final depuracion = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0, esDebug: true);
      expect((await depuracion.activar(Trust.maestra)).aceptada, isTrue);
      final release = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0, esDebug: false);
      expect((await release.estado()).clase, ClaseLicencia.prueba);
    });
  });

  group('Marca de agua del reloj (mitigación 3)', () {
    test('atrasar la fecha del equipo bloquea la licencia', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final codigo = emisor.licenciaCorta(
        deviceId: cliente.deviceId,
        devicePub: cliente.devicePubComprimida,
        emitidaEn: t0,
        venceEn: t0.add(const Duration(days: 30)),
      );
      await cliente.activar(codigo);
      final atrasado = await clienteDePrueba(
        emisor: emisor,
        almacen: almacen,
        reloj: () => t0.subtract(const Duration(hours: 2)),
      );
      final estado = await atrasado.estado();
      expect(estado.clase, ClaseLicencia.bloqueada);
      expect(estado.motivo, contains('fecha y hora'));
    });

    test('un ajuste pequeño de la hora (dentro de la tolerancia) no bloquea', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      expect((await cliente.estado()).clase, ClaseLicencia.prueba);
      final ajustado = await clienteDePrueba(
        emisor: emisor,
        almacen: almacen,
        reloj: () => t0.subtract(const Duration(seconds: 60)),
      );
      expect((await ajustado.estado()).clase, ClaseLicencia.prueba);
    });

    test('el reloj solo avanza: la marca se actualiza y nunca se pierde', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      await cliente.estado();
      final despues = await clienteDePrueba(
        emisor: emisor,
        almacen: almacen,
        reloj: () => t0.add(const Duration(days: 2)),
      );
      expect((await despues.estado()).clase, ClaseLicencia.prueba);
      // Si después se atrasa respecto a ese día visto, se bloquea igual.
      final tramposo = await clienteDePrueba(
        emisor: emisor,
        almacen: almacen,
        reloj: () => t0.add(const Duration(hours: 1)),
      );
      expect((await tramposo.estado()).clase, ClaseLicencia.bloqueada);
    });
  });

  group('Sub-tokens de apps secundarias (mitigación 7)', () {
    test('la principal emite el sub-token de una ranura y la secundaria lo verifica', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final codigo = emisor.licenciaCorta(
        deviceId: cliente.deviceId,
        devicePub: cliente.devicePubComprimida,
        emitidaEn: t0,
        venceEn: t0.add(const Duration(days: 30)),
        secundarias: 3,
      );
      await cliente.activar(codigo);
      const secundaria = 'SPVI:secundaria00000000000000000000';
      final token = await cliente.emitirSubToken(secundaria, 2);
      expect(token, isNotNull);
      final datos = SubToken.verificarBase64(token!, cliente.firmaPublicaBase64, secundaria, t0, cuotaMaxima: 3);
      expect(datos, isNotNull);
      expect(datos!.licenciaId, '7c9e6679-7425-40de-944b-e07fc1f90ae7');
      expect(datos.dispositivo, secundaria);
      expect(datos.ranura, 2);
      expect(datos.permitidas, 3);
      expect(datos.venceEn, t0.add(const Duration(days: 30)));
      expect(datos.vigente(t0), isTrue);
      expect(datos.vigente(t0.add(const Duration(days: 31))), isFalse);
    });

    test('fuera de cuota, otro dispositivo o token alterado no autorizan', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final codigo = emisor.licenciaCorta(
        deviceId: cliente.deviceId,
        devicePub: cliente.devicePubComprimida,
        emitidaEn: t0,
        venceEn: t0.add(const Duration(days: 30)),
        secundarias: 2,
      );
      await cliente.activar(codigo);
      const secundaria = 'SPVI:secundaria00000000000000000000';
      expect(await cliente.emitirSubToken(secundaria, 3), isNull);
      expect(await cliente.emitirSubToken(secundaria, 0), isNull);
      final token = (await cliente.emitirSubToken(secundaria, 1))!;
      expect(SubToken.verificarBase64(token, cliente.firmaPublicaBase64, 'SPVI:otro00000000000000000000000000', t0), isNull);
      // Cuota de la licencia propia menor que la del sub-token → no vale.
      expect(SubToken.verificarBase64(token, cliente.firmaPublicaBase64, secundaria, t0, cuotaMaxima: 2), isNotNull);
      expect(SubToken.verificarBase64(token, cliente.firmaPublicaBase64, secundaria, t0, cuotaMaxima: 1), isNull);
      // Token alterado o con otra clave pública.
      final alterado = token.replaceRange(0, 1, 'X');
      expect(SubToken.verificarBase64(alterado, cliente.firmaPublicaBase64, secundaria, t0), isNull);
      final otroEmisor = EmisorDePrueba();
      expect(SubToken.verificarBase64(token, otroEmisor.firmaSpkiBase64, secundaria, t0), isNull);
    });

    test('sin licencia (período de prueba) el sub-token sale con la cuota por defecto', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      const secundaria = 'SPVI:secundaria00000000000000000000';
      final token = await cliente.emitirSubToken(secundaria, 1);
      expect(token, isNotNull);
      final datos = SubToken.verificarBase64(token!, cliente.firmaPublicaBase64, secundaria, t0);
      expect(datos, isNotNull);
      expect(datos!.licenciaId, SubToken.idPrueba);
      expect(datos.permitidas, ClienteLicencia.secundariasDefecto);
    });

    test('una app bloqueada no emite sub-tokens', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final tarde = await clienteDePrueba(
        emisor: emisor,
        almacen: almacen,
        reloj: () => t0.add(const Duration(days: 8)),
      );
      expect(await tarde.emitirSubToken('SPVI:secundaria00000000000000000000', 1), isNull);
      expect(await tarde.verificarSubToken('loquesea', cliente.firmaPublicaBase64, 'SPVI:x'), isNull);
    });

    test('el sub-token perpetuo y el vencido se comportan como manda el contrato', () {
      final emisor = EmisorDePrueba();
      const dispositivo = 'SPVI:secundaria00000000000000000000';
      final perpetuo = SubToken.emitir(emisor.privateFirma, 'lic-1', 2, null, dispositivo, 1);
      final datos = SubToken.verificarBase64(perpetuo, emisor.firmaSpkiBase64, dispositivo, t0)!;
      expect(datos.venceEn, isNull);
      expect(datos.vigente(t0.add(const Duration(days: 3650))), isTrue);
      final vencido = SubToken.emitir(emisor.privateFirma, 'lic-1', 2, t0.subtract(const Duration(days: 1)), dispositivo, 1);
      expect(SubToken.verificarBase64(vencido, emisor.firmaSpkiBase64, dispositivo, t0), isNull);
      // Y no se pueden inyectar separadores en los campos.
      expect(() => SubToken.emitir(emisor.privateFirma, 'lic|1', 2, null, dispositivo, 1), throwsA(isA<LicenciaInvalida>()));
    });
  });

  group('Lista de revocadas firmada por GL', () {
    test('si la licencia instalada aparece en la lista, la app queda bloqueada', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final codigo = emisor.licenciaCorta(
        deviceId: cliente.deviceId,
        devicePub: cliente.devicePubComprimida,
        emitidaEn: t0,
        venceEn: t0.add(const Duration(days: 30)),
      );
      await cliente.activar(codigo);
      final huella = _huellaRevocacion('7c9e6679-7425-40de-944b-e07fc1f90ae7');
      final estado = await cliente.importarRevocaciones(emisor.revocadas([huella]));
      expect(estado.clase, ClaseLicencia.bloqueada);
      expect(estado.motivo, contains('revocada'));
      // Otra lista que no incluye esta licencia no cambia nada.
      final otra = await cliente.importarRevocaciones(emisor.revocadas([_huellaRevocacion('otra-licencia')], emitidaEn: 1792000000));
      expect(otra.clase, ClaseLicencia.bloqueada, reason: 'la revocación ya instalada no se deshace');
    });

    test('una lista con firma ajena o manipulada se rechaza', () async {
      final emisor = EmisorDePrueba();
      final otroEmisor = EmisorDePrueba();
      final cliente = await clienteDePrueba(emisor: emisor, reloj: () => t0);
      await expectLater(
        cliente.importarRevocaciones(otroEmisor.revocadas([_huellaRevocacion('x')])),
        throwsA(isA<LicenciaInvalida>()),
      );
      await expectLater(cliente.importarRevocaciones('no es json'), throwsA(isA<LicenciaInvalida>()));
      await expectLater(cliente.importarRevocaciones('{"datos":"{}","firma":""}'), throwsA(isA<LicenciaInvalida>()));
    });
  });

  group('Solicitud SPVIR1', () {
    test('la solicitud se cifra hacia GL y lleva el devicePub de este equipo', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      expect(cliente.puedeSolicitar, isTrue);
      final generada = await cliente.solicitar(const DatosSolicitud(
        nombre: 'Prueba',
        apellidos: 'Compatibilidad',
        ci: '00000000000',
        telefono: '+5351815604',
        tipo: TipoLicenciaGL.mensual,
        secundarias: 2,
        transaccion: 'TX-1234',
      ));
      expect(generada.codigo, startsWith('SPVIR1:'));
      expect(generada.precioCup, 8000);
      expect(generada.mensaje, contains('Solicitud de licencia SPVI'));
      expect(generada.mensaje, contains(generada.codigo));
      expect(generada.mensaje, contains('8 000 CUP'));
      // GL la abre con su clave privada.
      final plano = jsonDecode(utf8.decode(emisor.abrirSolicitud(generada.codigo))) as Map<String, dynamic>;
      expect(plano['v'], 2);
      expect(plano['deviceId'], cliente.deviceId);
      expect(plano['devicePub'], cliente.devicePubBase64Url);
      expect(plano['tipo'], 'MENSUAL');
      expect(plano['secundarias'], 2);
      expect(plano['via'], 'WHATSAPP');
      expect(plano['nombre'], 'Prueba');
      expect(plano['nonce'], isNotEmpty);
      expect(DateTime.parse(plano['solicitadaEn'] as String).isUtc, isTrue);
      expect(plano.containsKey('renueva'), isFalse);
      expect(plano.containsKey('recupera'), isFalse);
    });

    test('la solicitud de renovación y la de recuperación llevan su campo', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final renovacion = await cliente.solicitar(const DatosSolicitud(
        nombre: 'Ana',
        apellidos: 'Pérez',
        ci: '85010112345',
        telefono: '51815604',
        tipo: TipoLicenciaGL.anual,
        secundarias: 0,
        renueva: '7c9e6679-7425-40de-944b-e07fc1f90ae7',
      ));
      final plano = jsonDecode(utf8.decode(emisor.abrirSolicitud(renovacion.codigo))) as Map<String, dynamic>;
      expect(plano['renueva'], '7c9e6679-7425-40de-944b-e07fc1f90ae7');
      expect(plano['tipo'], 'ANUAL');
      expect(renovacion.precioCup, 50000);
      final recuperacion = await cliente.solicitar(const DatosSolicitud(
        nombre: 'Ana',
        apellidos: 'Pérez',
        ci: '85010112345',
        telefono: '51815604',
        tipo: TipoLicenciaGL.mensual,
        recupera: '7c9e6679-7425-40de-944b-e07fc1f90ae7',
      ));
      final plano2 = jsonDecode(utf8.decode(emisor.abrirSolicitud(recuperacion.codigo))) as Map<String, dynamic>;
      expect(plano2['recupera'], '7c9e6679-7425-40de-944b-e07fc1f90ae7');
    });

    test('sin datos del solicitante no se genera nada', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      await expectLater(
        cliente.solicitar(const DatosSolicitud(
          nombre: '',
          apellidos: 'Pérez',
          ci: '85010112345',
          telefono: '51815604',
          tipo: TipoLicenciaGL.mensual,
        )),
        throwsA(isA<LicenciaInvalida>()),
      );
      await expectLater(
        cliente.solicitar(const DatosSolicitud(
          nombre: 'Ana',
          apellidos: 'Pérez',
          ci: '85010112345',
          telefono: '51815604',
          tipo: TipoLicenciaGL.mensual,
          secundarias: 11,
        )),
        throwsA(isA<LicenciaInvalida>()),
      );
    });

    test('sin claves de emisión en el build no se puede solicitar', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0, conEcdh: false);
      expect(cliente.puedeSolicitar, isFalse);
      await expectLater(
        cliente.solicitar(const DatosSolicitud(
          nombre: 'Ana',
          apellidos: 'Pérez',
          ci: '85010112345',
          telefono: '51815604',
          tipo: TipoLicenciaGL.mensual,
        )),
        throwsA(isA<LicenciaInvalida>()),
      );
    });

    test('la solicitud viaja tolerando los espacios que mete el transporte', () async {
      final emisor = EmisorDePrueba();
      final almacen = AlmacenEnMemoria();
      final cliente = await clienteDePrueba(emisor: emisor, almacen: almacen, reloj: () => t0);
      final generada = await cliente.solicitar(const DatosSolicitud(
        nombre: 'Ana',
        apellidos: 'Pérez',
        ci: '85010112345',
        telefono: '51815604',
        tipo: TipoLicenciaGL.semestral,
        secundarias: 1,
      ));
      final rota = '${generada.codigo.substring(0, 60)}\n ${generada.codigo.substring(60)}';
      final plano = jsonDecode(utf8.decode(emisor.abrirSolicitud(rota))) as Map<String, dynamic>;
      expect(plano['tipo'], 'SEMESTRAL');
      expect(generada.precioCup, 35000);
    });
  });

  group('Código de 16 caracteres y contrato', () {
    test('el código de 16 caracteres se normaliza como en el resto del proyecto', () {
      expect(CodigoLicencia.normalizar('abcd-efgh-ijkl-mnop'), 'ABCDEFGHIJKLMNOP');
      expect(CodigoLicencia.formatear('abcdefghijklmnop'), 'ABCD-EFGH-IJKL-MNOP');
      expect(CodigoLicencia.valido('ABCD-EFGH-IJKL-MNOP'), isTrue);
      expect(CodigoLicencia.valido('ABCD-EFGH-IJK'), isFalse);
      expect(CodigoLicencia.valido('ABCD-EFGH-IJKL-MNÑP'), isFalse);
      expect(CodigoLicencia.valido(null), isFalse);
      expect(CodigoLicencia.esMaestra(Trust.maestra, debug: true), isTrue);
      expect(CodigoLicencia.esMaestra(Trust.maestra, debug: false), isFalse);
    });

    test('los precios del contrato son los de las otras dos apps', () {
      expect(Solicitud.precio(TipoLicenciaGL.mensual, 0), 6000);
      expect(Solicitud.precio(TipoLicenciaGL.mensual, 2), 8000);
      expect(Solicitud.precio(TipoLicenciaGL.semestral, 1), 35000);
      expect(Solicitud.precio(TipoLicenciaGL.anual, 3), 77000);
      expect(Solicitud.precio(TipoLicenciaGL.perpetua, 10), 260000);
    });
  });
}

/// Huella con la que GL marca una licencia en la lista de revocadas.
String _huellaRevocacion(String id) =>
    aHex(Uint8List.sublistView(P256.sha256(Uint8List.fromList(utf8.encode('SPVI-REV|$id'))), 0, 16));
