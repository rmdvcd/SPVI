import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import '../../../core/theme/spvi_colors.dart';
import '../../../core/utils/haptics.dart';
import '../../widgets/spvi_widgets.dart';

/// Pantalla de Soporte (petición 8): textos e iconos centrados, en el orden
/// **Icono, Descripción, Texto**.
///
/// Es el equivalente de `soporte/SoporteScreen.kt` de Android. Los datos viven aquí, en español y
/// fuera de la interfaz, para poder comprobarlos sin montar la pantalla.
class SoporteInfo {
  const SoporteInfo._();

  /// WhatsApp del desarrollador (misma constante que las otras dos apps).
  static const String telefonoDesarrollador = '+53 5181 5604';
  static const String whatsappUrl = 'https://wa.me/5351815604';

  static const List<FilaSoporte> filas = [
    FilaSoporte(
      icono: CupertinoIcons.wrench_fill,
      etiqueta: 'Desarrollador',
      texto: 'SPVI lo hace y lo mantiene una sola persona. Los cambios se pedir por aquí.',
    ),
    FilaSoporte(
      icono: CupertinoIcons.info_circle_fill,
      etiqueta: 'C.I.',
      texto: 'El desarrollador responde por la app con su carné de identidad.',
    ),
    FilaSoporte(
      icono: CupertinoIcons.phone_fill,
      etiqueta: 'Teléfono',
      texto: telefonoDesarrollador,
    ),
    FilaSoporte(
      icono: CupertinoIcons.briefcase_fill,
      etiqueta: 'Especialidad',
      texto: 'Punto de venta e inventario para negocios pequeños.',
    ),
  ];

  /// Color de cada fila, igual que en Android.
  static Color color(String etiqueta) {
    switch (etiqueta) {
      case 'Desarrollador':
        return SpviColors.navy;
      case 'C.I.':
        return SpviColors.orange;
      case 'Teléfono':
        return SpviColors.exito;
      default:
        return SpviColors.teal;
    }
  }
}

class FilaSoporte {
  final IconData icono;
  final String etiqueta;
  final String texto;

  const FilaSoporte({required this.icono, required this.etiqueta, required this.texto});
}

class SoporteScreen extends StatelessWidget {
  const SoporteScreen({super.key});

  Future<void> _abrirWhatsApp(BuildContext context) async {
    SpviHaptics.lightImpact();
    showCupertinoDialog<void>(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text('Contacto'),
        content: Text(SoporteInfo.whatsappUrl),
        actions: [
          CupertinoDialogAction(
            child: const Text('Copiar enlace'),
            onPressed: () async {
              await Clipboard.setData(ClipboardData(text: SoporteInfo.whatsappUrl));
              if (ctx.mounted) Navigator.pop(ctx);
            },
          ),
          CupertinoDialogAction(
            isDefaultAction: true,
            child: const Text('Cerrar'),
            onPressed: () => Navigator.pop(ctx),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(largeTitle: Text('Soporte')),
      child: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16.0),
          children: [
            const SpviCard(
              child: Column(
                children: [
                  Text(
                    'SPVI — Sistema de Punto de Venta e Inventario',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16.0),
                  ),
                  SizedBox(height: 6.0),
                  Text(
                    'Pensado para negocios pequeños y trabajadores por cuenta propia en Cuba. '
                    'Funciona sin conexión.',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 13.0, height: 1.4),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16.0),
            SpviCard(
              padding: const EdgeInsets.symmetric(vertical: 6.0),
              child: Column(
                children: [
                  for (final fila in SoporteInfo.filas)
                    SpviFilaSoporte(
                      icono: fila.icono,
                      color: SoporteInfo.color(fila.etiqueta),
                      descripcion: fila.etiqueta,
                      texto: fila.texto,
                      onTap: fila.etiqueta == 'Teléfono' ? () => _abrirWhatsApp(context) : null,
                    ),
                ],
              ),
            ),
            const SizedBox(height: 16.0),
            SpviButton(
              texto: 'Escribir al desarrollador',
              icono: CupertinoIcons.person_crop_circle_fill,
              onPressed: () => _abrirWhatsApp(context),
            ),
            const SizedBox(height: 12.0),
            Text(
              'Versión de la app 0.30.0 (51)',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12.0, color: CupertinoColors.secondaryLabel.resolveFrom(context)),
            ),
          ],
        ),
      ),
    );
  }
}
