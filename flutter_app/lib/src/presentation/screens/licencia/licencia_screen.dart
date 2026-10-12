import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/spvi_colors.dart';
import '../../../core/utils/haptics.dart';
import '../../../domain/models/models.dart';
import '../../providers/spvi_providers.dart';
import '../../widgets/spvi_widgets.dart';

class LicenciaScreen extends ConsumerStatefulWidget {
  const LicenciaScreen({super.key});

  @override
  ConsumerState<LicenciaScreen> createState() => _LicenciaScreenState();
}

class _LicenciaScreenState extends ConsumerState<LicenciaScreen> {
  final TextEditingController _claveController = TextEditingController();

  void _activarLicencia() {
    final clave = _claveController.text.trim();
    if (clave.length < 8) {
      showCupertinoDialog(
        context: context,
        builder: (ctx) => CupertinoAlertDialog(
          title: const Text("Clave Inválida"),
          content: const Text("La clave de activación debe contener al menos 8 caracteres emitidos por el desarrollador GL."),
          actions: [
            CupertinoDialogAction(child: const Text("Entendido"), onPressed: () => Navigator.pop(ctx)),
          ],
        ),
      );
      return;
    }

    ref.read(licenciaProvider.notifier).activarConClave(clave);
    SpviHaptics.heavyImpact();

    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text("¡Licencia Activada!"),
        content: const Text("Tu copia de SPVI para iOS ha sido activada de forma permanente y offline."),
        actions: [
          CupertinoDialogAction(
            isDefaultAction: true,
            child: const Text("Aceptar"),
            onPressed: () {
              Navigator.pop(ctx);
              Navigator.pop(context);
            },
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final licencia = ref.watch(licenciaProvider);

    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(
        middle: Text("Licencia SPVI"),
        previousPageTitle: "Ajustes",
      ),
      child: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16.0),
          children: [
            SpviCard(
              child: Column(
                children: [
                  Icon(
                    licencia.tipo == TipoLicencia.activa
                        ? CupertinoIcons.checkmark_seal_fill
                        : CupertinoIcons.shield_lefthalf_fill,
                    size: 60,
                    color: licencia.tipo == TipoLicencia.activa ? SpviColors.exito : SpviColors.navy,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    licencia.tipo == TipoLicencia.activa ? "Licencia Activa y Válida" : "Período de Prueba",
                    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    licencia.tipo == TipoLicencia.activa
                        ? "Activado para: ${licencia.titular ?? 'Negocio'}"
                        : "Te restan ${licencia.diasRestantesPrueba} días para probar todas las funciones sin restricciones.",
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: CupertinoColors.secondaryLabel, fontSize: 14),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),
            if (licencia.tipo != TipoLicencia.activa) ...[
              const Text(
                "Activar Licencia Offline",
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              CupertinoTextField(
                controller: _claveController,
                placeholder: "Pega aquí la clave de licencia GL",
                padding: const EdgeInsets.all(14.0),
                clearButtonMode: OverlayVisibilityMode.editing,
              ),
              const SizedBox(height: 12),
              CupertinoButton.filled(
                onPressed: _activarLicencia,
                child: const Text("Validar y Activar"),
              ),
            ],
            const SizedBox(height: 24),
            const SpviCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text("Información del Contrato", style: TextStyle(fontWeight: FontWeight.bold)),
                  SizedBox(height: 8),
                  Text(
                    "SPVI no requiere conexión a internet para validar la licencia ni recopila datos personales. La clave vincula tu instalación de forma matemática y permanente.",
                    style: TextStyle(fontSize: 13, color: CupertinoColors.secondaryLabel),
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
