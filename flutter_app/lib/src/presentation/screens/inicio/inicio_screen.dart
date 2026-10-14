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

/// Períodos del selector de Inicio (petición 1). Los mismos que en Android.
enum PeriodoInicio { turno, hoy, semana, mes, anio }

/// Etiqueta corta de cada período.
String etiquetaPeriodo(PeriodoInicio periodo) {
  switch (periodo) {
    case PeriodoInicio.turno:
      return 'Turno';
    case PeriodoInicio.hoy:
      return 'Hoy';
    case PeriodoInicio.semana:
      return 'Semana';
    case PeriodoInicio.mes:
      return 'Mes';
    case PeriodoInicio.anio:
      return 'Año';
  }
}

/// Primer día que cuenta para el período elegido, contando desde [ahora].
DateTime desdePeriodo(PeriodoInicio periodo, DateTime ahora) {
  switch (periodo) {
    case PeriodoInicio.turno:
      return ahora;
    case PeriodoInicio.hoy:
      return DateTime(ahora.year, ahora.month, ahora.day);
    case PeriodoInicio.semana:
      final hoy = DateTime(ahora.year, ahora.month, ahora.day);
      return hoy.subtract(Duration(days: hoy.weekday - 1));
    case PeriodoInicio.mes:
      return DateTime(ahora.year, ahora.month);
    case PeriodoInicio.anio:
      return DateTime(ahora.year);
  }
}

/// Total vendido en el período, sin las ventas anuladas. El turno abierto se calcula con sus propios
/// totales, como en Android.
Money totalPeriodo(PeriodoInicio periodo, List<VentaModel> ventas, TurnoModel? turno, DateTime ahora) {
  if (periodo == PeriodoInicio.turno) {
    final abierto = turno;
    if (abierto == null) return const Money(0);
    return abierto.totalVentasEfectivo + abierto.totalVentasTransferencia;
  }
  final desde = desdePeriodo(periodo, ahora);
  var total = const Money(0);
  for (final venta in ventas) {
    if (venta.anulada) continue;
    if (venta.fecha.isBefore(desde)) continue;
    total += venta.total;
  }
  return total;
}

class InicioScreen extends ConsumerStatefulWidget {
  const InicioScreen({super.key});

  @override
  ConsumerState<InicioScreen> createState() => _InicioScreenState();
}

class _InicioScreenState extends ConsumerState<InicioScreen> {
  PeriodoInicio _periodo = PeriodoInicio.hoy;

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
        child: Column(
          children: [
            // Petición 1: primer contenedor de Inicio y el único que no se desplaza. A la izquierda el
            // turno («Turno cerrado» o «Turno en curso») y a su lado el selector de período, los dos con
            // el mismo alto dentro del contenedor (reparto equitativo).
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: IntrinsicHeight(
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Expanded(child: _TarjetaTurno(periodo: _periodo)),
                    const SizedBox(width: 12.0),
                    Expanded(
                      child: _TarjetaPeriodo(
                        periodo: _periodo,
                        onPeriodo: (valor) {
                          SpviHaptics.selectionClick();
                          setState(() => _periodo = valor);
                        },
                      ),
                    ),
                  ],
                ),
              ),
            ),

            Expanded(
              child: ListView(
                padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                children: [
            // Banner de Licencia bloqueada (reloj manipulado, licencia vencida/revocada o prueba terminada)
            if (licencia.tipo == TipoLicencia.vencida) ...[
              SpviBanner(
                titulo: "Licencia bloqueada",
                mensaje: licencia.motivo ?? "Activa una licencia para seguir trabajando.",
                icono: CupertinoIcons.exclamationmark_shield_fill,
                colorFondo: SpviColors.error.withOpacity(0.12),
                colorTexto: SpviColors.error,
                textoAccion: "Ver licencia",
                onAccion: () {
                  Navigator.of(context).push(
                    CupertinoPageRoute(builder: (_) => const LicenciaScreen()),
                  );
                },
              ),
              const SizedBox(height: 16.0),
            ],

            // Banner de Licencia (Offline/Prueba)
            if (licencia.tipo == TipoLicencia.prueba && !licencia.cargando) ...[
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
          ],
        ),
      ),
    );
  }
}

/// Contenedor del turno, a la izquierda del contenedor fijo de Inicio.
class _TarjetaTurno extends ConsumerWidget {
  final PeriodoInicio periodo;

  const _TarjetaTurno({required this.periodo});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final turno = ref.watch(turnoProvider);
    final ventas = ref.watch(ventasHistorialProvider);
    final total = totalPeriodo(periodo, ventas, turno, DateTime.now());
    final abierto = turno?.estado == EstadoTurno.abierto;

    return SpviCard(
      padding: const EdgeInsets.all(12.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                CupertinoIcons.circle_fill,
                size: 10.0,
                color: abierto ? SpviColors.exito : CupertinoColors.systemGrey,
              ),
              const SizedBox(width: 6.0),
              Expanded(
                child: Text(
                  abierto ? "Turno en curso" : "Turno cerrado",
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15.0),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6.0),
          Text(
            "Ventas (${etiquetaPeriodo(periodo)})",
            style: TextStyle(fontSize: 12.0, color: CupertinoColors.secondaryLabel.resolveFrom(context)),
          ),
          Text(
            total.formatted,
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18.0, color: SpviColors.navy),
          ),
          const SizedBox(height: 10.0),
          if (abierto)
            Row(
              children: [
                Expanded(
                  child: CupertinoButton(
                    color: SpviColors.navy,
                    padding: const EdgeInsets.symmetric(vertical: 10.0),
                    onPressed: () {
                      SpviHaptics.lightImpact();
                      Navigator.of(context).push(
                        CupertinoPageRoute(builder: (_) => const CajaScreen()),
                      );
                    },
                    child: const Text("Caja", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14.0)),
                  ),
                ),
                const SizedBox(width: 8.0),
                Expanded(
                  child: CupertinoButton(
                    color: CupertinoColors.systemGrey5,
                    padding: const EdgeInsets.symmetric(vertical: 10.0),
                    onPressed: () => _cerrarTurno(context, ref, turno!),
                    child: const Text(
                      "Cerrar",
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14.0, color: SpviColors.error),
                    ),
                  ),
                ),
              ],
            )
          else
            SizedBox(
              width: double.infinity,
              child: CupertinoButton.filled(
                padding: const EdgeInsets.symmetric(vertical: 10.0),
                onPressed: () => _abrirTurno(context, ref),
                child: const Text("Abrir turno", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14.0)),
              ),
            ),
        ],
      ),
    );
  }

  void _cerrarTurno(BuildContext context, WidgetRef ref, TurnoModel turno) {
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

  void _abrirTurno(BuildContext context, WidgetRef ref) {
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
}

/// Selector de período, a la derecha del turno. Mismo alto que la tarjeta del turno.
class _TarjetaPeriodo extends StatelessWidget {
  final PeriodoInicio periodo;
  final ValueChanged<PeriodoInicio> onPeriodo;

  const _TarjetaPeriodo({required this.periodo, required this.onPeriodo});

  @override
  Widget build(BuildContext context) {
    return SpviCard(
      padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 12.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            "Período",
            style: TextStyle(
              fontSize: 12.0,
              fontWeight: FontWeight.bold,
              color: CupertinoColors.secondaryLabel.resolveFrom(context),
            ),
          ),
          const SizedBox(height: 8.0),
          for (final opcion in PeriodoInicio.values)
            CupertinoButton(
              padding: const EdgeInsets.symmetric(vertical: 4.0),
              minSize: 0,
              onPressed: () => onPeriodo(opcion),
              child: Row(
                children: [
                  Icon(
                    periodo == opcion ? CupertinoIcons.checkmark_circle_fill : CupertinoIcons.circle,
                    size: 14,
                    color: periodo == opcion ? SpviColors.navy : CupertinoColors.systemGrey,
                  ),
                  const SizedBox(width: 6.0),
                  Text(
                    etiquetaPeriodo(opcion),
                    style: TextStyle(
                      fontSize: 13.0,
                      fontWeight: periodo == opcion ? FontWeight.bold : FontWeight.normal,
                      color: periodo == opcion ? SpviColors.navy : CupertinoColors.label.resolveFrom(context),
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
