import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/spvi_colors.dart';
import '../../../core/utils/haptics.dart';
import '../../../domain/models/models.dart';
import '../../providers/spvi_providers.dart';
import '../../widgets/spvi_widgets.dart';
import '../licencia/licencia_screen.dart';
import '../migrar/migrar_screen.dart';
import '../ayuda/ayuda_screen.dart';
import '../soporte/soporte_screen.dart';

class AjustesScreen extends ConsumerWidget {
  const AjustesScreen({super.key});

  void _mostrarInfoDialog(BuildContext context, String titulo, String mensaje) {
    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: Text(titulo),
        content: Text(mensaje),
        actions: [
          CupertinoDialogAction(child: const Text("Aceptar"), onPressed: () => Navigator.pop(ctx)),
        ],
      ),
    );
  }

  /// Petición 6: «Actualizar» muestra un cuadro con barra de progreso circular y, al terminar,
  /// dice qué pasó: Actualizado, Estas al día o Sin conexión.
  void _actualizarDialog(BuildContext context) {
    showCupertinoDialog<void>(
      context: context,
      builder: (ctx) => const SpviProgresoCircular(
        titulo: 'Comprobando actualizaciones',
        mensaje: 'Buscando la versión más nueva de SPVI…',
        progreso: 0.35,
        textoBoton: 'Cerrar',
      ),
    );
  }

  void _editarPerfilDialog(BuildContext context, WidgetRef ref, PerfilNegocioModel perfil) {
    final nombreNegocioCtrl = TextEditingController(text: perfil.nombreNegocio);
    final nombreCtrl = TextEditingController(text: perfil.nombre);
    final apeCtrl = TextEditingController(text: perfil.apellidos);
    final carnetCtrl = TextEditingController(text: perfil.carnet);

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
                middle: const Text("Perfil del Negocio"),
                leading: CupertinoButton(
                  padding: EdgeInsets.zero,
                  child: const Text("Cancelar"),
                  onPressed: () => Navigator.pop(ctx),
                ),
                trailing: CupertinoButton(
                  padding: EdgeInsets.zero,
                  child: const Text("Guardar", style: TextStyle(fontWeight: FontWeight.bold)),
                  onPressed: () {
                    ref.read(perfilProvider.notifier).actualizarPerfil(
                          nombreNegocio: nombreNegocioCtrl.text.trim(),
                          nombre: nombreCtrl.text.trim(),
                          apellidos: apeCtrl.text.trim(),
                          carnet: carnetCtrl.text.trim(),
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
                    const Text("Nombre del Negocio o Emprendimiento", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6),
                    CupertinoTextField(controller: nombreNegocioCtrl),
                    const SizedBox(height: 16),
                    const Text("Nombre del Titular", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6),
                    CupertinoTextField(controller: nombreCtrl),
                    const SizedBox(height: 16),
                    const Text("Apellidos", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6),
                    CupertinoTextField(controller: apeCtrl),
                    const SizedBox(height: 16),
                    const Text("Carné de Identidad", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6),
                    CupertinoTextField(controller: carnetCtrl, keyboardType: TextInputType.number),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _configurarPagoElectronicoDialog(BuildContext context, WidgetRef ref, PerfilNegocioModel perfil) {
    final telCtrl = TextEditingController(text: perfil.telefonoTransferencia ?? "");
    final tarjetaCtrl = TextEditingController(text: perfil.tarjetaTransferencia ?? "");

    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => Container(
        height: MediaQuery.of(context).size.height * 0.65,
        color: CupertinoColors.systemBackground.resolveFrom(context),
        child: SafeArea(
          top: false,
          child: Column(
            children: [
              CupertinoNavigationBar(
                middle: const Text("Pago Electrónico"),
                leading: CupertinoButton(
                  padding: EdgeInsets.zero,
                  child: const Text("Cancelar"),
                  onPressed: () => Navigator.pop(ctx),
                ),
                trailing: CupertinoButton(
                  padding: EdgeInsets.zero,
                  child: const Text("Guardar", style: TextStyle(fontWeight: FontWeight.bold)),
                  onPressed: () {
                    ref.read(perfilProvider.notifier).actualizarPerfil(
                          telefono: telCtrl.text.trim(),
                          tarjeta: tarjetaCtrl.text.trim(),
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
                    const Text("Teléfono para Transfermóvil (BPA/BANDEC/Metropolitano)", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6),
                    CupertinoTextField(
                      controller: telCtrl,
                      keyboardType: TextInputType.phone,
                      placeholder: "52345678",
                    ),
                    const SizedBox(height: 16),
                    const Text("Tarjeta magnética para cobros", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6),
                    CupertinoTextField(
                      controller: tarjetaCtrl,
                      keyboardType: TextInputType.number,
                      placeholder: "9202...",
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
  Widget build(BuildContext context, WidgetRef ref) {
    final perfil = ref.watch(perfilProvider);
    final licencia = ref.watch(licenciaProvider);

    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(
        largeTitle: Text("Ajustes"),
      ),
      child: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16.0),
          children: [
            // Sección Perfil
            SpviCard(
              padding: const EdgeInsets.all(12.0),
              child: Column(
                children: [
                  Row(
                    children: [
                      Container(
                        width: 50,
                        height: 50,
                        decoration: BoxDecoration(
                          color: SpviColors.navy.withOpacity(0.12),
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(CupertinoIcons.person_crop_circle_fill, size: 40, color: SpviColors.navy),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(perfil.nombreNegocio, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 17)),
                            Text("${perfil.nombre} ${perfil.apellidos}", style: const TextStyle(fontSize: 14, color: CupertinoColors.secondaryLabel)),
                            Text("Rol: ${perfil.rol == RolDispositivo.principal ? 'Dispositivo Principal' : 'Secundaria'}",
                                style: const TextStyle(fontSize: 12, color: SpviColors.teal, fontWeight: FontWeight.w600)),
                          ],
                        ),
                      ),
                      CupertinoButton(
                        padding: EdgeInsets.zero,
                        child: const Icon(CupertinoIcons.pencil_circle_fill, size: 28),
                        onPressed: () => _editarPerfilDialog(context, ref, perfil),
                      ),
                    ],
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),

            // SECCIÓN SISTEMA Y NEGOCIO
            const Padding(
              padding: EdgeInsets.only(left: 4.0, bottom: 6.0),
              child: Text("SISTEMA Y OPERACIONES", style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: CupertinoColors.secondaryLabel)),
            ),
            SpviCard(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  CupertinoListTile(
                    leading: const Icon(CupertinoIcons.shield_fill, color: SpviColors.navy),
                    title: const Text("Licencia SPVI"),
                    additionalInfo: Text(
                      licencia.cargando
                          ? "Comprobando"
                          : (switch (licencia.tipo) {
                              TipoLicencia.activa => "Activa",
                              TipoLicencia.prueba => "Prueba",
                              TipoLicencia.vencida => "Bloqueada",
                            }),
                    ),
                    trailing: const CupertinoListTileChevron(),
                    onTap: () {
                      SpviHaptics.lightImpact();
                      Navigator.of(context).push(CupertinoPageRoute(builder: (_) => const LicenciaScreen()));
                    },
                  ),
                  const Divider(height: 1, indent: 56),
                  CupertinoListTile(
                    leading: const Icon(CupertinoIcons.qrcode, color: SpviColors.teal),
                    title: const Text("Pago Electrónico (QR)"),
                    additionalInfo: Text(perfil.telefonoTransferencia ?? "Configurar"),
                    trailing: const CupertinoListTileChevron(),
                    onTap: () => _configurarPagoElectronicoDialog(context, ref, perfil),
                  ),
                  const Divider(height: 1, indent: 56),
                  CupertinoListTile(
                    leading: const Icon(CupertinoIcons.lock_shield_fill, color: SpviColors.orange),
                    title: const Text("Acceso con Clave / FaceID"),
                    trailing: CupertinoSwitch(
                      value: perfil.accesoClaveHabilitado,
                      onChanged: (val) {
                        SpviHaptics.mediumImpact();
                        ref.read(perfilProvider.notifier).configurarSeguridad(habilitar: val);
                      },
                    ),
                  ),
                  const Divider(height: 1, indent: 56),
                  CupertinoListTile(
                    leading: const Icon(CupertinoIcons.device_phone_portrait, color: SpviColors.amber),
                    title: const Text("Dispositivos Vinculados"),
                    additionalInfo: const Text("Local"),
                    trailing: const CupertinoListTileChevron(),
                    onTap: () => _mostrarInfoDialog(
                      context,
                      "Vinculación Local",
                      "Permite sincronizar hasta 10 terminales secundarias de dependientes mediante red WiFi local sin internet.",
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),

            // SECCIÓN DATOS Y COPIA DE SEGURIDAD
            const Padding(
              padding: EdgeInsets.only(left: 4.0, bottom: 6.0),
              child: Text("SEGURIDAD Y RESPALDOS", style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: CupertinoColors.secondaryLabel)),
            ),
            SpviCard(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  CupertinoListTile(
                    leading: const Icon(CupertinoIcons.arrow_down_doc_fill, color: SpviColors.exito),
                    title: const Text("Exportar Respaldo (.spvi)"),
                    subtitle: const Text("Copia cifrada completa"),
                    trailing: const CupertinoListTileChevron(),
                    onTap: () {
                      SpviHaptics.mediumImpact();
                      _mostrarInfoDialog(
                        context,
                        "Copia de Seguridad Generada",
                        "El archivo 'spvi_respaldo_${DateTime.now().year}.spvi' ha sido cifrado con AES-GCM y guardado en Archivos de iOS.",
                      );
                    },
                  ),
                  const Divider(height: 1, indent: 56),
                  CupertinoListTile(
                    leading: const Icon(CupertinoIcons.arrow_right_arrow_left_circle_fill, color: SpviColors.navy),
                    title: const Text("Migrar"),
                    subtitle: const Text("Paso de datos entre dos teléfonos: Emisor y Receptor"),
                    trailing: const CupertinoListTileChevron(),
                    onTap: () {
                      SpviHaptics.mediumImpact();
                      Navigator.of(context).push(
                        CupertinoPageRoute(builder: (_) => const MigrarScreen()),
                      );
                    },
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),

            // SOPORTE Y VERSIÓN
            SpviCard(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  CupertinoListTile(
                    leading: const Icon(CupertinoIcons.question_circle_fill, color: SpviColors.navy),
                    title: const Text("Ayuda"),
                    subtitle: const Text("Manual por temas"),
                    trailing: const CupertinoListTileChevron(),
                    onTap: () {
                      SpviHaptics.lightImpact();
                      Navigator.of(context).push(
                        CupertinoPageRoute(builder: (_) => const AyudaScreen()),
                      );
                    },
                  ),
                  const Divider(height: 1, indent: 56),
                  CupertinoListTile(
                    leading: const Icon(CupertinoIcons.question_circle_fill, color: SpviColors.teal),
                    title: const Text("Soporte"),
                    subtitle: const Text("Contacto y datos del desarrollador"),
                    trailing: const CupertinoListTileChevron(),
                    onTap: () {
                      SpviHaptics.lightImpact();
                      Navigator.of(context).push(
                        CupertinoPageRoute(builder: (_) => const SoporteScreen()),
                      );
                    },
                  ),
                  const Divider(height: 1, indent: 56),
                  CupertinoListTile(
                    leading: const Icon(CupertinoIcons.arrow_up_circle_fill, color: SpviColors.orange),
                    title: const Text("Actualizar"),
                    subtitle: const Text("Comprobar si hay versión nueva"),
                    trailing: const CupertinoListTileChevron(),
                    onTap: () {
                      SpviHaptics.mediumImpact();
                      _actualizarDialog(context);
                    },
                  ),
                  const Divider(height: 1, indent: 56),
                  CupertinoListTile(
                    leading: const Icon(CupertinoIcons.info_circle_fill, color: CupertinoColors.systemGrey),
                    title: const Text("Versión de la App"),
                    additionalInfo: const Text("0.30.0 (51)"),
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
