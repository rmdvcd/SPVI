import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/spvi_colors.dart';
import '../../../core/utils/haptics.dart';
import '../../../domain/models/models.dart';
import '../../providers/spvi_providers.dart';
import '../../widgets/spvi_widgets.dart';

class RegistrosScreen extends ConsumerStatefulWidget {
  const RegistrosScreen({super.key});

  @override
  ConsumerState<RegistrosScreen> createState() => _RegistrosScreenState();
}

class _RegistrosScreenState extends ConsumerState<RegistrosScreen> {
  int _tabSeleccionada = 0; // 0: Ventas, 1: Clientes Fijos, 2: Movimientos

  void _anularVentaDialog(VentaModel venta) {
    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text("¿Anular Venta?"),
        content: Text("Esta acción revertirá el importe (${venta.total.formatted}) y restaurará el stock en el inventario."),
        actions: [
          CupertinoDialogAction(
            child: const Text("Cancelar"),
            onPressed: () => Navigator.pop(ctx),
          ),
          CupertinoDialogAction(
            isDestructiveAction: true,
            child: const Text("Anular"),
            onPressed: () {
              ref.read(ventasHistorialProvider.notifier).anularVenta(venta.id);
              SpviHaptics.heavyImpact();
              Navigator.pop(ctx);
            },
          ),
        ],
      ),
    );
  }

  void _agregarClienteDialog() {
    final carnetCtrl = TextEditingController();
    final nombreCtrl = TextEditingController();
    final telCtrl = TextEditingController();
    final dirCtrl = TextEditingController();

    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => Container(
        height: MediaQuery.of(context).size.height * 0.75,
        color: CupertinoColors.systemBackground.resolveFrom(context),
        child: SafeArea(
          top: false,
          child: Column(
            children: [
              CupertinoNavigationBar(
                middle: const Text("Nuevo Cliente Fijo"),
                leading: CupertinoButton(
                  padding: EdgeInsets.zero,
                  child: const Text("Cancelar"),
                  onPressed: () => Navigator.pop(ctx),
                ),
                trailing: CupertinoButton(
                  padding: EdgeInsets.zero,
                  child: const Text("Guardar", style: TextStyle(fontWeight: FontWeight.bold)),
                  onPressed: () {
                    if (carnetCtrl.text.trim().isEmpty || nombreCtrl.text.trim().isEmpty) return;
                    ref.read(clientesProvider.notifier).agregarOActualizar(
                          ClienteFijoModel(
                            carnet: carnetCtrl.text.trim(),
                            nombre: nombreCtrl.text.trim(),
                            telefono: telCtrl.text.trim().isEmpty ? null : telCtrl.text.trim(),
                            direccion: dirCtrl.text.trim().isEmpty ? null : dirCtrl.text.trim(),
                          ),
                        );
                    SpviHaptics.mediumImpact();
                    Navigator.pop(ctx);
                  },
                ),
              ),
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.all(16.0),
                  children: [
                    const Text("Carné de Identidad (11 dígitos)", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6.0),
                    CupertinoTextField(
                      controller: carnetCtrl,
                      keyboardType: TextInputType.number,
                      placeholder: "Ej. 90010112345",
                    ),
                    const SizedBox(height: 16.0),
                    const Text("Nombre Completo", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6.0),
                    CupertinoTextField(
                      controller: nombreCtrl,
                      placeholder: "Ej. Juan Pérez",
                    ),
                    const SizedBox(height: 16.0),
                    const Text("Teléfono de Contacto", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6.0),
                    CupertinoTextField(
                      controller: telCtrl,
                      keyboardType: TextInputType.phone,
                      placeholder: "53...",
                    ),
                    const SizedBox(height: 16.0),
                    const Text("Dirección", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6.0),
                    CupertinoTextField(
                      controller: dirCtrl,
                      placeholder: "Calle, número y entrecalles",
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final ventas = ref.watch(ventasHistorialProvider);
    final clientes = ref.watch(clientesProvider);
    final movimientos = ref.watch(movimientosCajaProvider);

    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        largeTitle: const Text("Registros"),
        trailing: _tabSeleccionada == 1
            ? CupertinoButton(
                padding: EdgeInsets.zero,
                onPressed: _agregarClienteDialog,
                child: const Icon(CupertinoIcons.person_badge_plus, size: 26),
              )
            : null,
      ),
      child: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: SizedBox(
                width: double.infinity,
                child: CupertinoSlidingSegmentedControl<int>(
                  groupValue: _tabSeleccionada,
                  children: const {
                    0: Text("Ventas"),
                    1: Text("Clientes"),
                    2: Text("Caja"),
                  },
                  onValueChanged: (val) {
                    if (val != null) {
                      SpviHaptics.selectionClick();
                      setState(() => _tabSeleccionada = val);
                    }
                  },
                ),
              ),
            ),

            Expanded(
              child: Builder(
                builder: (context) {
                  if (_tabSeleccionada == 0) {
                    // TAB VENTAS
                    if (ventas.isEmpty) {
                      return const Center(
                        child: Text("No hay ventas registradas aún", style: TextStyle(color: CupertinoColors.secondaryLabel)),
                      );
                    }
                    return ListView.builder(
                      padding: const EdgeInsets.all(16.0),
                      itemCount: ventas.length,
                      itemBuilder: (context, index) {
                        final v = ventas[index];
                        return Padding(
                          padding: const EdgeInsets.only(bottom: 8.0),
                          child: SpviCard(
                            padding: const EdgeInsets.all(12.0),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Row(
                                      children: [
                                        Icon(
                                          v.metodoPago == MetodoPago.efectivo
                                              ? CupertinoIcons.money_dollar_circle_fill
                                              : CupertinoIcons.creditcard_fill,
                                          color: v.anulada ? CupertinoColors.systemGrey : SpviColors.navy,
                                        ),
                                        const SizedBox(width: 8),
                                        Text(
                                          v.metodoPago == MetodoPago.efectivo ? "Efectivo" : "Transferencia QR",
                                          style: TextStyle(
                                            fontWeight: FontWeight.bold,
                                            decoration: v.anulada ? TextDecoration.lineThrough : null,
                                          ),
                                        ),
                                      ],
                                    ),
                                    Text(
                                      v.total.formatted,
                                      style: TextStyle(
                                        fontWeight: FontWeight.bold,
                                        fontSize: 16.0,
                                        color: v.anulada ? SpviColors.error : SpviColors.exito,
                                        decoration: v.anulada ? TextDecoration.lineThrough : null,
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 6.0),
                                Text(
                                  "${v.lineas.length} artículos  •  ${v.fecha.hour.toString().padLeft(2, '0')}:${v.fecha.minute.toString().padLeft(2, '0')}" +
                                      (v.cliente != null ? "  •  Cliente: ${v.cliente!.nombre}" : ""),
                                  style: const TextStyle(fontSize: 13.0, color: CupertinoColors.secondaryLabel),
                                ),
                                if (v.numeroTransaccionTransferencia != null)
                                  Text(
                                    "Transacción: ${v.numeroTransaccionTransferencia}",
                                    style: const TextStyle(fontSize: 12.0, color: CupertinoColors.secondaryLabel),
                                  ),
                                if (!v.anulada) ...[
                                  const Divider(height: 16),
                                  Align(
                                    alignment: Alignment.centerRight,
                                    child: CupertinoButton(
                                      padding: EdgeInsets.zero,
                                      minSize: 0,
                                      onPressed: () => _anularVentaDialog(v),
                                      child: const Text("Anular venta", style: TextStyle(color: SpviColors.error, fontSize: 13)),
                                    ),
                                  ),
                                ] else
                                  const Padding(
                                    padding: EdgeInsets.only(top: 4.0),
                                    child: Text("ANULADA", style: TextStyle(color: SpviColors.error, fontWeight: FontWeight.bold, fontSize: 11)),
                                  ),
                              ],
                            ),
                          ),
                        );
                      },
                    );
                  } else if (_tabSeleccionada == 1) {
                    // TAB CLIENTES FIJOS
                    if (clientes.isEmpty) {
                      return const Center(
                        child: Text("No hay clientes guardados", style: TextStyle(color: CupertinoColors.secondaryLabel)),
                      );
                    }
                    return ListView.builder(
                      padding: const EdgeInsets.all(16.0),
                      itemCount: clientes.length,
                      itemBuilder: (context, index) {
                        final c = clientes[index];
                        return Padding(
                          padding: const EdgeInsets.only(bottom: 8.0),
                          child: SpviCard(
                            padding: const EdgeInsets.all(12.0),
                            child: Row(
                              children: [
                                const Icon(CupertinoIcons.person_crop_circle_fill, color: SpviColors.navy, size: 36),
                                const SizedBox(width: 12.0),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(c.nombre, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16.0)),
                                      Text("CI: ${c.carnet}  •  Tel: ${c.telefono ?? 'No registrado'}",
                                          style: const TextStyle(fontSize: 13.0, color: CupertinoColors.secondaryLabel)),
                                      if (c.direccion != null)
                                        Text(c.direccion!, style: const TextStyle(fontSize: 12.0, color: CupertinoColors.secondaryLabel)),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    );
                  } else {
                    // TAB CAJA MOVIMIENTOS
                    if (movimientos.isEmpty) {
                      return const Center(
                        child: Text("Sin movimientos de caja en el período", style: TextStyle(color: CupertinoColors.secondaryLabel)),
                      );
                    }
                    return ListView.builder(
                      padding: const EdgeInsets.all(16.0),
                      itemCount: movimientos.length,
                      itemBuilder: (context, index) {
                        final m = movimientos[index];
                        return Padding(
                          padding: const EdgeInsets.only(bottom: 8.0),
                          child: SpviCard(
                            padding: const EdgeInsets.all(12.0),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(m.motivo, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15.0)),
                                    Text(
                                      "${m.fecha.day}/${m.fecha.month} - ${m.fecha.hour.toString().padLeft(2, '0')}:${m.fecha.minute.toString().padLeft(2, '0')}",
                                      style: const TextStyle(fontSize: 12.0, color: CupertinoColors.secondaryLabel),
                                    ),
                                  ],
                                ),
                                Text(
                                  "${m.tipo == TipoMovimientoCaja.entrada ? '+' : '-'}${m.monto.formatted}",
                                  style: TextStyle(
                                    fontWeight: FontWeight.bold,
                                    fontSize: 16.0,
                                    color: m.tipo == TipoMovimientoCaja.entrada ? SpviColors.exito : SpviColors.orange,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    );
                  }
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
