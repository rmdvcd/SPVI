import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:spvi_ios/src/domain/models/models.dart';
import 'package:spvi_ios/src/presentation/providers/spvi_providers.dart';

void main() {
  group('SPVI Pruebas Unitarias de Lógica y Paridad', () {
    test('Cálculos monetarios con precisión exacta', () {
      const m1 = Money(35000); // 350 CUP
      const m2 = Money(15050); // 150.50 CUP
      final suma = m1 + m2;
      expect(suma.centavos, 50050);
      expect(suma.formatted, '500.50 CUP');
    });

    test('Flujo de apertura, venta y cierre de turno en TurnoNotifier', () {
      final container = ProviderContainer();
      final notifier = container.read(turnoProvider.notifier);

      // 1. Abrir turno con fondo inicial de 500 CUP
      notifier.abrirTurno(const Money(50000));
      expect(container.read(turnoProvider)?.estado, EstadoTurno.abierto);
      expect(container.read(turnoProvider)?.fondoInicial.centavos, 50000);
      expect(container.read(turnoProvider)?.efectivoEsperado.centavos, 50000);

      // 2. Registrar venta en efectivo de 350 CUP
      notifier.registrarVenta(const Money(35000), MetodoPago.efectivo);
      expect(container.read(turnoProvider)?.efectivoEsperado.centavos, 85000);
      expect(container.read(turnoProvider)?.totalVentasEfectivo.centavos, 35000);

      // 3. Registrar venta por transferencia de 200 CUP (no suma a efectivo esperado)
      notifier.registrarVenta(const Money(20000), MetodoPago.transferencia);
      expect(container.read(turnoProvider)?.efectivoEsperado.centavos, 85000);
      expect(container.read(turnoProvider)?.totalVentasTransferencia.centavos, 20000);

      // 4. Registrar salida de caja de 100 CUP
      notifier.registrarMovimiento(TipoMovimientoCaja.salida, const Money(10000));
      expect(container.read(turnoProvider)?.efectivoEsperado.centavos, 75000);

      // 5. Cierre de turno
      notifier.cerrarTurno(efectivoReal: const Money(75000), nota: "Cuadre exacto");
      expect(container.read(turnoProvider)?.estado, EstadoTurno.cerrado);
      expect(container.read(turnoProvider)?.efectivoReal.centavos, 75000);
    });

    test('Descuento de stock en InventarioNotifier', () {
      final container = ProviderContainer();
      final inventarioNotifier = container.read(inventarioProvider.notifier);

      // I-03: la app ya no arranca con datos de demostración; el test siembra el suyo.
      expect(container.read(inventarioProvider), isEmpty);
      inventarioNotifier.agregarOEditarProducto(kDemoInventario.first);
      final p = container.read(inventarioProvider).first;
      final stockInicial = p.stock;

      inventarioNotifier.descontarStock(p.id, 2.0);
      final pActualizado = container.read(inventarioProvider).firstWhere((item) => item.id == p.id);

      expect(pActualizado.stock, stockInicial - 2.0);
    });
  });
}
