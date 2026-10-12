import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/spvi_colors.dart';
import '../../../core/utils/haptics.dart';
import '../../../domain/models/models.dart';
import '../../providers/spvi_providers.dart';
import '../../widgets/spvi_widgets.dart';

class ServiciosScreen extends ConsumerStatefulWidget {
  const ServiciosScreen({super.key});

  @override
  ConsumerState<ServiciosScreen> createState() => _ServiciosScreenState();
}

class _ServiciosScreenState extends ConsumerState<ServiciosScreen> {
  void _abrirFormularioServicio([ServicioModel? servicio]) {
    final nombreCtrl = TextEditingController(text: servicio?.nombre ?? "");
    final tipoCtrl = TextEditingController(text: servicio?.tipo ?? "General");
    final importeCtrl = TextEditingController(text: servicio != null ? servicio.importe.toDouble.toStringAsFixed(0) : "");
    final descCtrl = TextEditingController(text: servicio?.descripcion ?? "");

    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => Container(
        height: MediaQuery.of(context).size.height * 0.8,
        color: CupertinoColors.systemBackground.resolveFrom(context),
        child: SafeArea(
          top: false,
          child: Column(
            children: [
              CupertinoNavigationBar(
                middle: Text(servicio == null ? "Nuevo Servicio" : "Editar Servicio"),
                leading: CupertinoButton(
                  padding: EdgeInsets.zero,
                  child: const Text("Cancelar"),
                  onPressed: () => Navigator.pop(ctx),
                ),
                trailing: CupertinoButton(
                  padding: EdgeInsets.zero,
                  child: const Text("Guardar", style: TextStyle(fontWeight: FontWeight.bold)),
                  onPressed: () {
                    final nom = nombreCtrl.text.trim();
                    final imp = double.tryParse(importeCtrl.text) ?? 0.0;
                    if (nom.isEmpty || imp <= 0) return;

                    final nuevo = ServicioModel(
                      id: servicio?.id ?? "srv-${DateTime.now().millisecondsSinceEpoch}",
                      nombre: nom,
                      tipo: tipoCtrl.text.trim().isEmpty ? "General" : tipoCtrl.text.trim(),
                      importe: Money.fromDouble(imp),
                      descripcion: descCtrl.text.trim().isEmpty ? null : descCtrl.text.trim(),
                    );

                    ref.read(serviciosProvider.notifier).agregarOEditar(nuevo);
                    SpviHaptics.mediumImpact();
                    Navigator.pop(ctx);
                  },
                ),
              ),
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.all(16.0),
                  children: [
                    const Text("Nombre del Servicio", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6.0),
                    CupertinoTextField(
                      controller: nombreCtrl,
                      placeholder: "Ej. Reparación de Calzado, Asesoría",
                    ),
                    const SizedBox(height: 16.0),
                    const Text("Rubro / Tipo", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6.0),
                    CupertinoTextField(
                      controller: tipoCtrl,
                      placeholder: "Ej. Técnico, Personal, Digital",
                    ),
                    const SizedBox(height: 16.0),
                    const Text("Importe o Tarifa Base (CUP)", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6.0),
                    CupertinoTextField(
                      controller: importeCtrl,
                      placeholder: "0.00",
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    ),
                    const SizedBox(height: 16.0),
                    const Text("Descripción (opcional)", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6.0),
                    CupertinoTextField(
                      controller: descCtrl,
                      placeholder: "Detalles del servicio brindado",
                      maxLines: 3,
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
    final servicios = ref.watch(serviciosProvider);

    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        largeTitle: const Text("Servicios"),
        trailing: CupertinoButton(
          padding: EdgeInsets.zero,
          child: const Icon(CupertinoIcons.add_circled_solid, size: 28),
          onPressed: () {
            SpviHaptics.lightImpact();
            _abrirFormularioServicio();
          },
        ),
      ),
      child: SafeArea(
        child: servicios.isEmpty
            ? const Center(
                child: Text(
                  "No hay servicios configurados.\nPulsa el botón + para añadir tu primer servicio.",
                  textAlign: TextAlign.center,
                  style: TextStyle(color: CupertinoColors.secondaryLabel),
                ),
              )
            : ListView.builder(
                padding: const EdgeInsets.all(16.0),
                itemCount: servicios.length,
                itemBuilder: (context, index) {
                  final s = servicios[index];
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 8.0),
                    child: SpviCard(
                      padding: const EdgeInsets.all(12.0),
                      onTap: () => _abrirFormularioServicio(s),
                      child: Row(
                        children: [
                          Container(
                            width: 44,
                            height: 44,
                            decoration: BoxDecoration(
                              color: SpviColors.teal.withOpacity(0.15),
                              borderRadius: BorderRadius.circular(8.0),
                            ),
                            child: const Icon(CupertinoIcons.briefcase_fill, color: SpviColors.teal),
                          ),
                          const SizedBox(width: 12.0),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(s.nombre, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16.0)),
                                const SizedBox(height: 2.0),
                                Text(
                                  s.tipo + (s.descripcion != null ? " • ${s.descripcion}" : ""),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(fontSize: 13.0, color: CupertinoColors.secondaryLabel),
                                ),
                              ],
                            ),
                          ),
                          Text(
                            s.importe.formatted,
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16.0, color: SpviColors.navy),
                          ),
                          const SizedBox(width: 8.0),
                          const Icon(CupertinoIcons.chevron_right, size: 16.0, color: CupertinoColors.systemGrey3),
                        ],
                      ),
                    ),
                  );
                },
              ),
      ),
    );
  }
}
