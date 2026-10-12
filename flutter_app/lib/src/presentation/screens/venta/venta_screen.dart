import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/spvi_colors.dart';
import '../../../core/utils/haptics.dart';
import '../../../domain/models/models.dart';
import '../../providers/spvi_providers.dart';
import '../../widgets/spvi_widgets.dart';

class VentaScreen extends ConsumerStatefulWidget {
  const VentaScreen({super.key});

  @override
  ConsumerState<VentaScreen> createState() => _VentaScreenState();
}

class _VentaScreenState extends ConsumerState<VentaScreen> {
  final Map<String, int> _carrito = {};
  MetodoPago _metodoPago = MetodoPago.efectivo;
  final TextEditingController _transaccionController = TextEditingController();
  final TextEditingController _clienteController = TextEditingController();
  ClienteFijoModel? _clienteSeleccionado;

  Money _calcularTotal(List<ProductoModel> productos) {
    int total = 0;
    _carrito.forEach((id, qty) {
      final prod = productos.firstWhere((p) => p.id == id);
      total += prod.precioVenta.centavos * qty;
    });
    return Money(total);
  }

  void _confirmarCobro(Money total, List<ProductoModel> productos) {
    if (_carrito.isEmpty) return;

    final turno = ref.read(turnoProvider);
    if (turno == null || turno.estado != EstadoTurno.abierto) {
      showCupertinoDialog(
        context: context,
        builder: (ctx) => CupertinoAlertDialog(
          title: const Text("Turno cerrado"),
          content: const Text("Debes abrir un turno para poder registrar ventas."),
          actions: [
            CupertinoDialogAction(child: const Text("Aceptar"), onPressed: () => Navigator.pop(ctx)),
          ],
        ),
      );
      return;
    }

    final perfil = ref.read(perfilProvider);

    // Diálogo nativo iOS de confirmación / cobro
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => CupertinoActionSheet(
        title: Text("Total a Cobrar: ${total.formatted}"),
        message: _metodoPago == MetodoPago.transferencia
            ? Text("Transfermóvil / EnZona\nCuenta: ${perfil.tarjetaTransferencia ?? '9202...'}\nMóvil: ${perfil.telefonoTransferencia ?? '52...'}")
            : const Text("Cobro en Efectivo"),
        actions: [
          CupertinoActionSheetAction(
            isDefaultAction: true,
            onPressed: () {
              Navigator.pop(ctx);
              _finalizarVenta(total, productos);
            },
            child: const Text("Confirmar Pago y Registrar"),
          ),
        ],
        cancelButton: CupertinoActionSheetAction(
          isDestructiveAction: true,
          onPressed: () => Navigator.pop(ctx),
          child: const Text("Cancelar"),
        ),
      ),
    );
  }

  void _finalizarVenta(Money total, List<ProductoModel> productos) {
    final turno = ref.read(turnoProvider)!;

    final lineas = _carrito.entries.map((entry) {
      final p = productos.firstWhere((prod) => prod.id == entry.key);
      // Descontar inventario
      ref.read(inventarioProvider.notifier).descontarStock(p.id, entry.value.toDouble());
      return LineaVentaModel(
        itemId: p.id,
        nombre: p.nombre,
        cantidad: entry.value.toDouble(),
        precioUnitario: p.precioVenta,
        tipo: TipoVenta.venta,
      );
    }).toList();

    final venta = VentaModel(
      id: "v-${DateTime.now().millisecondsSinceEpoch}",
      turnoId: turno.id,
      fecha: DateTime.now(),
      lineas: lineas,
      total: total,
      metodoPago: _metodoPago,
      numeroTransaccionTransferencia: _metodoPago == MetodoPago.transferencia ? _transaccionController.text.trim() : null,
      cliente: _clienteSeleccionado,
    );

    ref.read(ventasHistorialProvider.notifier).registrarVenta(venta);
    ref.read(turnoProvider.notifier).registrarVenta(total, _metodoPago);

    SpviHaptics.heavyImpact();

    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text("¡Venta Registrada!"),
        content: Text("Total: ${total.formatted}\nOperación guardada correctamente."),
        actions: [
          CupertinoDialogAction(
            isDefaultAction: true,
            child: const Text("Listo"),
            onPressed: () {
              Navigator.pop(ctx);
              Navigator.pop(context); // Regresar
            },
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final productos = ref.watch(inventarioProvider);
    final clientes = ref.watch(clientesProvider);
    final total = _calcularTotal(productos);

    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(
        middle: Text("Nueva Venta"),
        previousPageTitle: "Inicio",
      ),
      child: SafeArea(
        child: Column(
          children: [
            // Selección de Cliente Fijo y Pago
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: SpviCard(
                padding: const EdgeInsets.all(12.0),
                child: Column(
                  children: [
                    // Segmented Control de Método de Pago
                    SizedBox(
                      width: double.infinity,
                      child: CupertinoSlidingSegmentedControl<MetodoPago>(
                        groupValue: _metodoPago,
                        children: const {
                          MetodoPago.efectivo: Padding(
                            padding: EdgeInsets.symmetric(horizontal: 16.0),
                            child: Text("Efectivo"),
                          ),
                          MetodoPago.transferencia: Padding(
                            padding: EdgeInsets.symmetric(horizontal: 16.0),
                            child: Text("Transferencia QR"),
                          ),
                        },
                        onValueChanged: (val) {
                          if (val != null) {
                            SpviHaptics.selectionClick();
                            setState(() => _metodoPago = val);
                          }
                        },
                      ),
                    ),
                    if (_metodoPago == MetodoPago.transferencia) ...[
                      const SizedBox(height: 8.0),
                      CupertinoTextField(
                        controller: _transaccionController,
                        placeholder: "Nº de transacción / SMS Transfermóvil",
                        prefix: const Padding(
                          padding: EdgeInsets.only(left: 8.0),
                          child: Icon(CupertinoIcons.qrcode_viewfinder, size: 20),
                        ),
                      ),
                    ],
                    const SizedBox(height: 8.0),
                    // Autocompletar Cliente Fijo (Prompt 13 y 0.27.0)
                    CupertinoTextField(
                      controller: _clienteController,
                      placeholder: "Cliente Fijo (opcional - buscar o carné)",
                      prefix: const Padding(
                        padding: EdgeInsets.only(left: 8.0),
                        child: Icon(CupertinoIcons.person_crop_circle, size: 20),
                      ),
                      onChanged: (text) {
                        setState(() {
                          if (text.isEmpty) {
                            _clienteSeleccionado = null;
                          } else {
                            final match = clientes.where((c) =>
                                c.nombre.toLowerCase().contains(text.toLowerCase()) ||
                                c.carnet.contains(text)).toList();
                            _clienteSeleccionado = match.isNotEmpty ? match.first : null;
                          }
                        });
                      },
                    ),
                    if (_clienteSeleccionado != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 4.0),
                        child: Align(
                          alignment: Alignment.centerLeft,
                          child: Text(
                            "✓ Cliente asignado: ${_clienteSeleccionado!.nombre} (${_clienteSeleccionado!.carnet})",
                            style: const TextStyle(fontSize: 12.0, color: SpviColors.exito, fontWeight: FontWeight.bold),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),

            // Catálogo disponible para añadir al carrito
            Expanded(
              child: ListView.separated(
                padding: const EdgeInsets.symmetric(horizontal: 16.0),
                itemCount: productos.length,
                separatorBuilder: (_, __) => const Divider(height: 1),
                itemBuilder: (context, index) {
                  final p = productos[index];
                  final qty = _carrito[p.id] ?? 0;

                  return Container(
                    color: CupertinoColors.white,
                    padding: const EdgeInsets.symmetric(vertical: 10.0, horizontal: 12.0),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(p.nombre, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
                              const SizedBox(height: 2),
                              Text(
                                "${p.precioVenta.formatted}  •  Stock: ${p.stock.toInt()}",
                                style: const TextStyle(color: CupertinoColors.secondaryLabel, fontSize: 13),
                              ),
                            ],
                          ),
                        ),
                        // Controles + / - estilo Cupertino
                        Row(
                          children: [
                            if (qty > 0) ...[
                              CupertinoButton(
                                padding: EdgeInsets.zero,
                                onPressed: () {
                                  SpviHaptics.selectionClick();
                                  setState(() {
                                    if (qty > 1) {
                                      _carrito[p.id] = qty - 1;
                                    } else {
                                      _carrito.remove(p.id);
                                    }
                                  });
                                },
                                child: const Icon(CupertinoIcons.minus_circle, size: 28, color: SpviColors.navy),
                              ),
                              Padding(
                                padding: const EdgeInsets.symmetric(horizontal: 8.0),
                                child: Text("$qty", style: const TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
                              ),
                            ],
                            CupertinoButton(
                              padding: EdgeInsets.zero,
                              onPressed: () {
                                if (qty < p.stock) {
                                  SpviHaptics.selectionClick();
                                  setState(() {
                                    _carrito[p.id] = qty + 1;
                                  });
                                }
                              },
                              child: const Icon(CupertinoIcons.plus_circle_fill, size: 28, color: SpviColors.navy),
                            ),
                          ],
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),

            // Barra Inferior de Total y Cobro
            Container(
              padding: const EdgeInsets.all(16.0),
              decoration: BoxDecoration(
                color: CupertinoColors.white,
                boxShadow: [
                  BoxShadow(color: CupertinoColors.systemGrey4.withOpacity(0.5), blurRadius: 4, offset: const Offset(0, -2)),
                ],
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text("Total a cobrar", style: TextStyle(color: CupertinoColors.secondaryLabel, fontSize: 13)),
                        Text(
                          total.formatted,
                          style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: SpviColors.navy),
                        ),
                      ],
                    ),
                  ),
                  CupertinoButton.filled(
                    onPressed: _carrito.isNotEmpty ? () => _confirmarCobro(total, productos) : null,
                    child: const Text("Cobrar", style: TextStyle(fontWeight: FontWeight.bold)),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
