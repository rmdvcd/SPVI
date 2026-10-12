import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/spvi_colors.dart';
import '../../../core/utils/haptics.dart';
import '../../../domain/models/models.dart';
import '../../providers/spvi_providers.dart';
import '../../widgets/spvi_widgets.dart';
import '../caja/caja_screen.dart';
import '../venta/venta_screen.dart';
import '../licencia/licencia_screen.dart';

class InicioScreen extends ConsumerWidget {
  const InicioScreen({super.key});

  void _abrirTurnoDialog(BuildContext context, WidgetRef ref) {
    final controller = TextEditingController(text: "500");
    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text("Abrir Turno"),
        content: Padding(
          padding: const EdgeInsets.only(top: 12.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text("Ingresa el fondo inicial obligatorio en efectivo:"),
              const SizedBox(height: 12.0),
              CupertinoTextField(
                controller: controller,
                placeholder: "Fondo inicial (CUP)",
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                prefix: const Padding(
                  padding: EdgeInsets.only(left: 8.0),
                  child: Text("\$", style: TextStyle(fontWeight: FontWeight.bold)),
                ),
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
            child: const Text("Abrir Turno"),
            onPressed: () {
              final monto = double.tryParse(controller.text) ?? 0.0;
              ref.read(turnoProvider.notifier).abrirTurno(Money.fromDouble(monto));
              SpviHaptics.mediumImpact();
              Navigator.pop(ctx);
            },
          ),
        ],
      ),
    );
  }

  void _cerrarTurnoDialog(BuildContext context, WidgetRef ref, TurnoModel turno) {
    final controller = TextEditingController(text: turno.efectivoEsperado.toDouble.toStringAsFixed(0));
    final notaController = TextEditingController();

    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text("Cerrar Turno"),
        content: Padding(
          padding: const EdgeInsets.only(top: 12.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text("Efectivo esperado: ${turno.efectivoEsperado.formatted}"),
              const SizedBox(height: 12.0),
              CupertinoTextField(
                controller: controller,
                placeholder: "Efectivo real contado",
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                prefix: const Padding(
                  padding: EdgeInsets.only(left: 8.0),
                  child: Text("\$", style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              ),
              const SizedBox(height: 8.0),
              CupertinoButton(
                padding: EdgeInsets.zero,
                onPressed: () {
                  controller.text = turno.efectivoEsperado.toDouble.toStringAsFixed(0);
                  SpviHaptics.lightImpact();
                },
                child: const Text("Cuadra exacto", style: TextStyle(fontSize: 14)),
              ),
              const SizedBox(height: 8.0),
              CupertinoTextField(
                controller: notaController,
                placeholder: "Nota de cierre (opcional)",
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
            isDestructiveAction: true,
            child: const Text("Confirmar Cierre"),
            onPressed: () {
              final real = double.tryParse(controller.text) ?? 0.0;
              ref.read(turnoProvider.notifier).cerrarTurno(
                    efectivoReal: Money.fromDouble(real),
                    nota: notaController.text.trim().isEmpty ? null : notaController.text.trim(),
                  );
              SpviHaptics.heavyImpact();
              Navigator.pop(ctx);
            },
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final licencia = ref.watch(licenciaProvider);
    final turno = ref.watch(turnoProvider);
    final productos = ref.watch(inventarioProvider);
    final insumos = ref.watch(insumosProvider);

    // Contadores de alertas
    final alertasProd = productos.where((p) => p.stockCritico).toList();
    final alertasIns = insumos.where((i) => i.stockCritico).toList();

    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(
        largeTitle: Text("SPVI"),
      ),
      child: SafeArea(
        child: ListView(
          padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
          children: [
            // Banner de Licencia (Offline/Prueba)
            if (licencia.tipo == TipoLicencia.prueba) ...[
              SpviBanner(
                titulo: "Período de prueba activo",
                mensaje: "Te quedan ${licencia.diasRestantesPrueba} días de prueba gratuita completa.",
                icono: CupertinoIcons.timer,
                colorFondo: SpviColors.lightPrimaryContainer,
                colorTexto: SpviColors.navy,
                textoAccion: "Activar Licencia Offline",
                onAccion: () {
                  Navigator.of(context).push(
                    CupertinoPageRoute(builder: (_) => const LicenciaScreen()),
                  );
                },
              ),
              const SizedBox(height: 16.0),
            ],

            // Tarjeta de Turno
            SpviCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          Icon(
                            CupertinoIcons.circle_fill,
                            size: 12.0,
                            color: turno?.estado == EstadoTurno.abierto ? SpviColors.exito : CupertinoColors.systemGrey,
                          ),
                          const SizedBox(width: 8.0),
                          Text(
                            turno?.estado == EstadoTurno.abierto ? "Turno en curso" : "Turno cerrado",
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16.0),
                          ),
                        ],
                      ),
                      if (turno?.estado == EstadoTurno.abierto)
                        CupertinoButton(
                          padding: EdgeInsets.zero,
                          child: const Text("Caja", style: TextStyle(fontWeight: FontWeight.bold)),
                          onPressed: () {
                            SpviHaptics.lightImpact();
                            Navigator.of(context).push(
                              CupertinoPageRoute(builder: (_) => const CajaScreen()),
                            );
                          },
                        ),
                    ],
                  ),
                  const SizedBox(height: 12.0),
                  if (turno?.estado == EstadoTurno.abierto) ...[
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text("Fondo inicial:", style: TextStyle(color: CupertinoColors.secondaryLabel)),
                        Text(turno!.fondoInicial.formatted, style: const TextStyle(fontWeight: FontWeight.w600)),
                      ],
                    ),
                    const SizedBox(height: 4.0),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text("Total ventas efectivo:", style: TextStyle(color: CupertinoColors.secondaryLabel)),
                        Text(turno.totalVentasEfectivo.formatted, style: const TextStyle(fontWeight: FontWeight.w600)),
                      ],
                    ),
                    const SizedBox(height: 4.0),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text("Transferencias QR:", style: TextStyle(color: CupertinoColors.secondaryLabel)),
                        Text(turno.totalVentasTransferencia.formatted, style: const TextStyle(fontWeight: FontWeight.w600)),
                      ],
                    ),
                    const SizedBox(height: 16.0),
                    SizedBox(
                      width: double.infinity,
                      child: CupertinoButton(
                        color: CupertinoColors.systemGrey5,
                        onPressed: () => _cerrarTurnoDialog(context, ref, turno),
                        child: const Text("Cerrar Turno y Arqueo", style: TextStyle(color: SpviColors.error, fontWeight: FontWeight.bold)),
                      ),
                    ),
                  ] else ...[
                    const Text(
                      "Para comenzar a vender y gestionar la caja, abre un turno con el fondo de cambio.",
                      style: TextStyle(color: CupertinoColors.secondaryLabel, fontSize: 14.0),
                    ),
                    const SizedBox(height: 16.0),
                    SizedBox(
                      width: double.infinity,
                      child: CupertinoButton.filled(
                        onPressed: () => _abrirTurnoDialog(context, ref),
                        child: const Text("Abrir Turno", style: TextStyle(fontWeight: FontWeight.bold)),
                      ),
                    ),
                  ],
                ],
              ),
            ),

            const SizedBox(height: 20.0),

            // Botones de Acción Rápida: Nueva Venta y Escanear (Centrados, estilo HIG)
            Row(
              children: [
                Expanded(
                  child: CupertinoButton.filled(
                    padding: const EdgeInsets.symmetric(vertical: 18.0),
                    onPressed: turno?.estado == EstadoTurno.abierto
                        ? () {
                            SpviHaptics.mediumImpact();
                            Navigator.of(context).push(
                              CupertinoPageRoute(builder: (_) => const VentaScreen()),
                            );
                          }
                        : null,
                    child: const Column(
                      children: [
                        Icon(CupertinoIcons.cart_fill_badge_plus, size: 28),
                        SizedBox(height: 6),
                        Text("Nueva Venta", style: TextStyle(fontWeight: FontWeight.bold)),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 12.0),
                Expanded(
                  child: CupertinoButton(
                    color: SpviColors.navy,
                    padding: const EdgeInsets.symmetric(vertical: 18.0),
                    onPressed: () {
                      SpviHaptics.lightImpact();
                      showCupertinoDialog(
                        context: context,
                        builder: (ctx) => CupertinoAlertDialog(
                          title: const Text("Escanear Código"),
                          content: const Text("Cámara lista con Vision framework para códigos de barras de productos y QR."),
                          actions: [
                            CupertinoDialogAction(child: const Text("OK"), onPressed: () => Navigator.pop(ctx)),
                          ],
                        ),
                      );
                    },
                    child: const Column(
                      children: [
                        Icon(CupertinoIcons.qrcode_viewfinder, size: 28, color: CupertinoColors.white),
                        SizedBox(height: 6),
                        Text("Escanear", style: TextStyle(fontWeight: FontWeight.bold, color: CupertinoColors.white)),
                      ],
                    ),
                  ),
                ),
              ],
            ),

            const SizedBox(height: 24.0),

            // Alertas Críticas (Stock bajo o insumos por agotarse)
            const Text(
              "Alertas de Inventario",
              style: TextStyle(fontSize: 18.0, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 10.0),

            if (alertasProd.isEmpty && alertasIns.isEmpty)
              const SpviCard(
                child: Row(
                  children: [
                    Icon(CupertinoIcons.checkmark_seal_fill, color: SpviColors.exito, size: 24),
                    SizedBox(width: 12),
                    Expanded(
                      child: Text("Todos los productos e insumos tienen niveles óptimos de stock.",
                          style: TextStyle(fontSize: 14)),
                    ),
                  ],
                ),
              )
            else ...[
              for (final prod in alertasProd)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8.0),
                  child: SpviCard(
                    padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
                    child: Row(
                      children: [
                        const Icon(CupertinoIcons.exclamationmark_triangle_fill, color: SpviColors.alerta, size: 24),
                        const SizedBox(width: 12.0),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(prod.nombre, style: const TextStyle(fontWeight: FontWeight.bold)),
                              Text("Stock bajo: ${prod.stock.toInt()} unidades (mínimo: ${prod.stockMinimo.toInt()})",
                                  style: const TextStyle(fontSize: 13, color: CupertinoColors.secondaryLabel)),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              for (final ins in alertasIns)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8.0),
                  child: SpviCard(
                    padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
                    child: Row(
                      children: [
                        const Icon(CupertinoIcons.exclamationmark_triangle_fill, color: SpviColors.orange, size: 24),
                        const SizedBox(width: 12.0),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text("Insumo: ${ins.nombre}", style: const TextStyle(fontWeight: FontWeight.bold)),
                              Text("Quedan ${ins.cantidad} ${ins.unidadMedida} (mínimo: ${ins.minimo} ${ins.unidadMedida})",
                                  style: const TextStyle(fontSize: 13, color: CupertinoColors.secondaryLabel)),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ],
        ),
      ),
    );
  }
}
