import 'package:flutter_test/flutter_test.dart';
import 'package:spvi_ios/src/presentation/screens/inicio/inicio_screen.dart';
import 'package:spvi_ios/src/domain/models/models.dart';

/// Pruebas del selector de período de Inicio (petición 1): los cálculos son puros, como en Android.
void main() {
  group('Períodos de Inicio', () {
    final ahora = DateTime(2026, 10, 14, 12, 0); // miércoles 14 de octubre de 2026

    test('Las etiquetas están en español', () {
      expect(etiquetaPeriodo(PeriodoInicio.turno), 'Turno');
      expect(etiquetaPeriodo(PeriodoInicio.hoy), 'Hoy');
      expect(etiquetaPeriodo(PeriodoInicio.semana), 'Semana');
      expect(etiquetaPeriodo(PeriodoInicio.mes), 'Mes');
      expect(etiquetaPeriodo(PeriodoInicio.anio), 'Año');
    });

    test('El primer día de la semana es el lunes', () {
      expect(desdePeriodo(PeriodoInicio.semana, ahora), DateTime(2026, 10, 12));
      expect(desdePeriodo(PeriodoInicio.mes, ahora), DateTime(2026, 10, 1));
      expect(desdePeriodo(PeriodoInicio.anio, ahora), DateTime(2026, 1, 1));
      expect(desdePeriodo(PeriodoInicio.hoy, ahora), DateTime(2026, 10, 14));
    });

    VentaModel venta(String id, DateTime fecha, int centavos, {bool anulada = false}) {
      return VentaModel(
        id: id,
        turnoId: 't1',
        fecha: fecha,
        lineas: const [],
        total: Money(centavos),
        metodoPago: MetodoPago.efectivo,
        anulada: anulada,
      );
    }

    final ventas = [
      venta('hoy', DateTime(2026, 10, 14, 9), 10000),
      venta('ayer', DateTime(2026, 10, 13, 23), 20000),
      venta('mesPasado', DateTime(2026, 9, 30, 10), 40000),
      venta('anulada', DateTime(2026, 10, 14, 11), 50000, anulada: true),
    ];

    test('Hoy solo suma las ventas de hoy y sin anular', () {
      expect(totalPeriodo(PeriodoInicio.hoy, ventas, null, ahora).centavos, 10000);
    });

    test('La semana suma desde el lunes', () {
      expect(totalPeriodo(PeriodoInicio.semana, ventas, null, ahora).centavos, 30000);
    });

    test('El mes no arrastra el mes pasado', () {
      expect(totalPeriodo(PeriodoInicio.mes, ventas, null, ahora).centavos, 30000);
    });

    test('El año suma todo el año', () {
      expect(totalPeriodo(PeriodoInicio.anio, ventas, null, ahora).centavos, 70000);
    });

    test('El turno usa los totales del turno abierto', () {
      final turno = TurnoModel(
        id: 't1',
        fechaApertura: DateTime(2026, 10, 14, 8),
        fondoInicial: const Money(50000),
        totalVentasEfectivo: const Money(15000),
        totalVentasTransferencia: const Money(25000),
        estado: EstadoTurno.abierto,
      );
      expect(totalPeriodo(PeriodoInicio.turno, ventas, turno, ahora).centavos, 40000);
    });

    test('Sin turno abierto el período Turno queda en cero', () {
      expect(totalPeriodo(PeriodoInicio.turno, ventas, null, ahora).centavos, 0);
    });
  });
}
