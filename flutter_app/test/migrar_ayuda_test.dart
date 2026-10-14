import 'package:flutter_test/flutter_test.dart';
import 'package:spvi_ios/src/domain/models/migrar_codigo.dart';
import 'package:spvi_ios/src/presentation/screens/ayuda/ayuda_screen.dart';
import 'package:spvi_ios/src/presentation/screens/soporte/soporte_screen.dart';

/// Pruebas de los cambios del 14 de octubre de 2026 en la app de iOS: Migrar (petición 5), el
/// manual de Ayuda (petición 7) y Soporte (petición 8).
///
/// Son equivalentes a `MigrarTest.kt` y `AyudaSoporteTest.kt` de Android: prueban el contenido
/// puro, sin montar la interfaz, porque aquí no se puede compilar.
void main() {
  group('Código de migración', () {
    test('El emisor genera un código que el receptor entiende', () {
      final codigo = CodigoMigracion.generar(
        rol: RolMigracion.emisor,
        host: '192.168.0.15',
        puerto: 41234,
        clave: 'clave-secreta',
        origen: 'android-123',
      );

      expect(codigo.startsWith('SPVIM1|'), isTrue);

      final leido = CodigoMigracion.parsear(codigo);
      expect(leido, isNotNull);
      expect(leido!.rol, RolMigracion.emisor);
      expect(leido.host, '192.168.0.15');
      expect(leido.puerto, 41234);
      expect(leido.clave, 'clave-secreta');
      expect(leido.origen, 'android-123');
    });

    test('El código del receptor se lee igual', () {
      final codigo = CodigoMigracion.generar(
        rol: RolMigracion.receptor,
        host: '192.168.0.20',
        puerto: 50000,
        clave: 'otra-clave',
      );
      final leido = CodigoMigracion.parsear(codigo);
      expect(leido!.rol, RolMigracion.receptor);
      expect(leido.host, '192.168.0.20');
      expect(leido.puerto, 50000);
    });

    test('Un código sin el campo del origen sigue valiendo', () {
      final codigo = CodigoMigracion.generar(
        rol: RolMigracion.emisor,
        host: '10.0.0.2',
        puerto: 60000,
        clave: 'k',
      );
      final sinOrigen = codigo.split('|').take(5).join('|');
      final leido = CodigoMigracion.parsear(sinOrigen);
      expect(leido, isNotNull);
      expect(leido!.rol, RolMigracion.emisor);
      expect(leido.origen, '');
    });

    test('Un código de otra app o incompleto se rechaza', () {
      expect(CodigoMigracion.parsear('SPVI-VINC1:algo'), isNull);
      expect(CodigoMigracion.parsear('SPVIM1|emisor|host'), isNull);
      expect(CodigoMigracion.parsear('SPVIM1|ninguno|host|80|clave|origen'), isNull);
      expect(CodigoMigracion.parsear('SPVIM1|emisor|host|cero|clave|origen'), isNull);
      expect(CodigoMigracion.parsear('SPVIM1|emisor|host|80||origen'), isNull);
    });

    test('Un texto con el código dentro se lee (lo que pega el escáner)', () {
      final codigo = CodigoMigracion.generar(
        rol: RolMigracion.emisor,
        host: '192.168.1.5',
        puerto: 4321,
        clave: 'abc',
      );
      final leido = CodigoMigracion.parsear('texto previo $codigo texto final');
      expect(leido, isNotNull);
      expect(leido!.puerto, 4321);
    });

    test('El código para mostrar se agrupa de 4 en 4', () {
      expect(CodigoMigracion.paraMostrar('SPVIM1'), 'SPVI M1');
      expect(CodigoMigracion.paraMostrar('12345678'), '1234 5678');
    });

    test('Las etapas y el porcentaje se leen en español', () {
      expect(etapaMigracion(5), 'Terminado');
      expect(etapaMigracion(99), 'Preparando');
      expect(porcentaje(0.0), '0 %');
      expect(porcentaje(0.256), '26 %');
      expect(porcentaje(2.0), '100 %');
    });
  });

  group('Manual de Ayuda', () {
    test('Tiene secciones con icono, título y detalle', () {
      expect(AyudaContenido.secciones.length, 6);
      for (final seccion in AyudaContenido.secciones) {
        expect(seccion.titulo.isNotEmpty, isTrue);
        expect(seccion.detalle.isNotEmpty, isTrue);
        expect(seccion.temas.isNotEmpty, isTrue);
      }
    });

    test('Cada tema tiene su icono y su texto', () {
      for (final seccion in AyudaContenido.secciones) {
        for (final tema in seccion.temas) {
          expect(tema.titulo.isNotEmpty, isTrue);
          expect(tema.parrafos.isNotEmpty, isTrue);
          for (final parrafo in tema.parrafos) {
            expect(parrafo.isNotEmpty, isTrue);
          }
        }
      }
    });

    test('La migración se explica en el manual, con su esquema', () {
      final temas = AyudaContenido.temasPara('Empezar');
      final migrar = temas.firstWhere((t) => t.titulo.contains('Migrar'));
      expect(migrar.esquema, isNotNull);
      expect(migrar.esquema!.cajas.length, 3);
      expect(migrar.esquema!.flechas.length, 2);
      expect(migrar.parrafos.join(' ').contains('emisor'), isTrue);
    });

    test('Buscar una sección que no existe no rompe', () {
      expect(AyudaContenido.temasPara('No existe'), isEmpty);
      expect(AyudaContenido.iconoSeccion('No existe').codePoint > 0, isTrue);
    });

    test('La selección de elementos se explica en Registros', () {
      final temas = AyudaContenido.temasPara('Registros');
      expect(temas.any((t) => t.titulo.contains('Seleccionar')), isTrue);
    });
  });

  group('Soporte', () {
    test('Las cuatro fichas están, con texto', () {
      expect(SoporteInfo.filas.length, 4);
      final etiquetas = SoporteInfo.filas.map((f) => f.etiqueta).toList();
      expect(etiquetas, ['Desarrollador', 'C.I.', 'Teléfono', 'Especialidad']);
      for (final fila in SoporteInfo.filas) {
        expect(fila.texto.isNotEmpty, isTrue);
      }
    });

    test('El teléfono del desarrollador es el mismo de las otras apps', () {
      final telefono = SoporteInfo.filas.firstWhere((f) => f.etiqueta == 'Teléfono');
      expect(telefono.texto, '+53 5181 5604');
      expect(SoporteInfo.whatsappUrl, 'https://wa.me/5351815604');
    });

    test('Cada ficha tiene su color', () {
      for (final fila in SoporteInfo.filas) {
        expect(SoporteInfo.color(fila.etiqueta).value > 0, isTrue);
      }
    });
  });

  group('Textos de Migrar', () {
    test('Los mensajes clave existen y están en español', () {
      expect(TextosMigrar.titulo, 'Migrar');
      expect(TextosMigrar.emisor, 'Emisor');
      expect(TextosMigrar.receptor, 'Receptor');
      expect(TextosMigrar.sinClave.contains('acceso con clave'), isTrue);
      expect(TextosMigrar.listoEmisor.contains('vacío'), isTrue);
    });
  });
}
