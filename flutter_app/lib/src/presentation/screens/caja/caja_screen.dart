import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/spvi_colors.dart';
import '../../../core/utils/haptics.dart';
import '../../../domain/models/models.dart';
import '../../providers/spvi_providers.dart';
import '../../widgets/spvi_widgets.dart';

class CajaScreen extends ConsumerStatefulWidget {
  const CajaScreen({super.key});

  @override
  ConsumerState<CajaScreen> createState() => _CajaScreenState();
}

class _CajaScreenState extends ConsumerState<CajaScreen> {
  final TextEditingController _montoController = TextEditingController();
  final TextEditingController _motivoController = TextEditingController();

  void _mostrarDialogoMovimiento(TipoMovimientoCaja tipo) {
    _montoController.clear();
    _motivoController.clear();

    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: Text(tipo == TipoMovimientoCaja.entrada ? "Registrar Entrada" : "Registrar Salida"),
        content: Padding(
          padding: const EdgeInsets.only(top: 12.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              CupertinoTextField(
                controller: _montoController,
                placeholder: "Monto (CUP)",
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                prefix: const Padding(
                  padding: EdgeInsets.only(left: 8.0),
                  child: Text("\$", style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              ),
              const SizedBox(height: 8.0),
              CupertinoTextField(
                controller: _motivoController,
                placeholder: "Motivo (ej. Cambio, Pago proveedor)",
              ),
            ],
          ),
        ),
        actions: [
          CupertinoDialogAction(
            child: const Text("Cancelar"),
            onPressed: () => Navigator.pop(ctx),
          ),
          CupertinoDialogAction(
            isDefaultAction: true,
            child: const Text("Guardar"),
            onPressed: () {
              final montoDouble = double.tryParse(_montoController.text) ?? 0.0;
              if (montoDouble <= 0 || _motivoController.text.trim().isEmpty) return;

              final turno = ref.read(turnoProvider);
              if (turno == null) return;

              final monto = Money.fromDouble(montoDouble);
              ref.read(turnoProvider.notifier).registrarMovimiento(tipo, monto);
              ref.read(movimientosCajaProvider.notifier).registrarMovimiento(
                MovimientoCajaModel(
                  id: "mov-${DateTime.now().millisecondsSinceEpoch}",
                  turnoId: turno.id,
                  fecha: DateTime.now(),
                  tipo: tipo,
                  monto: monto,
                  motivo: _motivoController.text.trim(),
                ),
              );

              SpviHaptics.mediumImpact();
              Navigator.pop(ctx);
            },
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final turno = ref.watch(turnoProvider);
    final movimientos = ref.watch(movimientosCajaProvider);

    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(
        middle: Text("Caja y Arqueo"),
        previousPageTitle: "Inicio",
      ),
      child: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16.0),
          children: [
            if (turno != null && turno.estado == EstadoTurno.abierto) ...[
              SpviCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      "Estado de Caja (Turno Abierto)",
                      style: TextStyle(fontSize: 16.0, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 12.0),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text("Fondo inicial:"),
                        Text(turno.fondoInicial.formatted, style: const TextStyle(fontWeight: FontWeight.w600)),
                      ],
                    ),
                    const SizedBox(height: 6.0),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text("Ventas en Efectivo:"),
                        Text(turno.totalVentasEfectivo.formatted, style: const TextStyle(fontWeight: FontWeight.w600, color: SpviColors.exito)),
                      ],
                    ),
                    const Divider(height: 20),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text("Efectivo Esperado:", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16.0)),
                        Text(
                          turno.efectivoEsperado.formatted,
                          style: const TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 18.0,
                            color: SpviColors.navy,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16.0),
              Row(
                children: [
                  Expanded(
                    child: CupertinoButton.filled(
                      padding: const EdgeInsets.symmetric(vertical: 12.0),
                      onPressed: () => _mostrarDialogoMovimiento(TipoMovimientoCaja.entrada),
                      child: const Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(CupertinoIcons.arrow_down_circle_fill, size: 18),
                          SizedBox(width: 6),
                          Text("Entrada"),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 12.0),
                  Expanded(
                    child: CupertinoButton(
                      color: SpviColors.orange,
                      padding: const EdgeInsets.symmetric(vertical: 12.0),
                      onPressed: () => _mostrarDialogoMovimiento(TipoMovimientoCaja.salida),
                      child: const Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(CupertinoIcons.arrow_up_circle_fill, size: 18),
                          SizedBox(width: 6),
                          Text("Salida"),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ] else ...[
              const SpviCard(
                child: Center(
                  child: Padding(
                    padding: EdgeInsets.all(24.0),
                    child: Text(
                      "No hay un turno abierto actualmente.\nAbre un turno desde la pantalla de Inicio para gestionar la caja.",
                      textAlign: TextAlign.center,
                      style: TextStyle(color: CupertinoColors.secondaryLabel),
                    ),
                  ),
                ),
              ),
            ],
            const SizedBox(height: 24.0),
            const Text(
              "Movimientos de Efectivo",
              style: TextStyle(fontSize: 18.0, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8.0),
            if (movimientos.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 32.0),
                child: Center(
                  child: Text(
                    "Sin entradas ni salidas registradas",
                    style: TextStyle(color: CupertinoColors.secondaryLabel),
                  ),
                ),
              )
            else
              ...movimientos.map(
                (m) => Padding(
                  padding: const EdgeInsets.only(bottom: 8.0),
                  child: SpviCard(
                    padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            Icon(
                              m.tipo == TipoMovimientoCaja.entrada
                                  ? CupertinoIcons.add_circled_solid
                                  : CupertinoIcons.minus_circle_fill,
                              color: m.tipo == TipoMovimientoCaja.entrada ? SpviColors.exito : SpviColors.orange,
                              size: 24.0,
                            ),
                            const SizedBox(width: 12.0),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  m.motivo,
                                  style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15.0),
                                ),
                                Text(
                                  "${m.fecha.hour.toString().padLeft(2, '0')}:${m.fecha.minute.toString().padLeft(2, '0')}",
                                  style: const TextStyle(fontSize: 12.0, color: CupertinoColors.secondaryLabel),
                                ),
                              ],
                            ),
                          ],
                        ),
                        Text(
                          "${m.tipo == TipoMovimientoCaja.entrada ? '+' : '-'}${m.monto.formatted}",
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 15.0,
                            color: m.tipo == TipoMovimientoCaja.entrada ? SpviColors.exito : SpviColors.orange,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
