import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/spvi_colors.dart';
import '../../../core/utils/haptics.dart';
import '../../providers/spvi_providers.dart';
import '../../widgets/spvi_widgets.dart';
import 'licencia_screen.dart';

/// Bloqueo de arranque: con la licencia bloqueada (prueba terminada, vencida, revocada o reloj manipulado) la app
/// **no se puede usar**; solo se puede activar o pedir la licencia.
///
/// Es el equivalente iOS de `bloqueo/BloqueoGate.kt` de Android: ni Atrás ni un enlace saltan esta pantalla. El
/// detalle de qué comprobación falló no se muestra nunca: el motivo es siempre un texto genérico.
class PantallaBloqueo extends ConsumerStatefulWidget {
  const PantallaBloqueo({super.key});

  @override
  ConsumerState<PantallaBloqueo> createState() => _PantallaBloqueoState();
}

class _PantallaBloqueoState extends ConsumerState<PantallaBloqueo> {
  final TextEditingController _claveController = TextEditingController();
  bool _activando = false;

  @override
  void dispose() {
    _claveController.dispose();
    super.dispose();
  }

  Future<void> _activar() async {
    final clave = _claveController.text.trim();
    if (clave.isEmpty) {
      await _aviso('Escribe el código', 'Pega el mensaje completo que te envió el desarrollador.');
      return;
    }
    setState(() => _activando = true);
    final resultado = await ref.read(licenciaProvider.notifier).activar(clave);
    if (!mounted) return;
    setState(() => _activando = false);
    SpviHaptics.heavyImpact();
    if (resultado.aceptada) {
      _claveController.clear();
      await _aviso('Licencia activada', 'Tu copia de SPVI quedó activada.');
    } else {
      await _aviso('No se pudo activar', resultado.motivo ?? 'La licencia no es válida para esta instalación.');
    }
  }

  Future<void> _copiar(String texto) async {
    await Clipboard.setData(ClipboardData(text: texto));
    SpviHaptics.lightImpact();
    if (!mounted) return;
    await _aviso('Copiado', 'Identificador de este equipo copiado. Envíalo si te lo pide el desarrollador.');
  }

  Future<void> _aviso(String titulo, String mensaje) => showCupertinoDialog<void>(
        context: context,
        builder: (ctx) => CupertinoAlertDialog(
          title: Text(titulo),
          content: Text(mensaje),
          actions: [
            CupertinoDialogAction(
              isDefaultAction: true,
              child: const Text('Entendido'),
              onPressed: () => Navigator.pop(ctx),
            ),
          ],
        ),
      );

  @override
  Widget build(BuildContext context) {
    final licencia = ref.watch(licenciaProvider);
    return CupertinoPageScaffold(
      child: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20.0),
          children: [
            const SizedBox(height: 12),
            const Icon(CupertinoIcons.lock_shield_fill, size: 70, color: SpviColors.error),
            const SizedBox(height: 12),
            const Text(
              'SPVI bloqueado',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Text(
              licencia.motivo ?? 'Activa una licencia para seguir trabajando.',
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 15, color: CupertinoColors.secondaryLabel),
            ),
            const SizedBox(height: 20),
            SpviCard(
              child: Column(
                children: [
                  const Text('Activar licencia', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 4),
                  const Text(
                    'Pega el mensaje completo del desarrollador (contiene el código SPVI2:).',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 13, color: CupertinoColors.secondaryLabel),
                  ),
                  const SizedBox(height: 10),
                  CupertinoTextField(
                    controller: _claveController,
                    placeholder: 'Código SPVI2:…',
                    padding: const EdgeInsets.all(14.0),
                    maxLines: 3,
                    minLines: 2,
                    clearButtonMode: OverlayVisibilityMode.editing,
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: CupertinoButton.filled(
                      onPressed: _activando ? null : _activar,
                      child: _activando
                          ? const CupertinoActivityIndicator(color: CupertinoColors.white)
                          : const Text('Validar y activar'),
                    ),
                  ),
                  const SizedBox(height: 8),
                  CupertinoButton(
                    padding: EdgeInsets.zero,
                    onPressed: () => Navigator.of(context).push(
                      CupertinoPageRoute(builder: (_) => const LicenciaScreen()),
                    ),
                    child: const Text('Solicitar una licencia nueva', style: TextStyle(fontSize: 13)),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            CupertinoButton(
              padding: EdgeInsets.zero,
              onPressed: licencia.dispositivo.isEmpty ? null : () => _copiar(licencia.dispositivo),
              child: Text(
                licencia.dispositivo.isEmpty ? 'Equipo sin registrar' : 'Equipo: ${licencia.dispositivo}',
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 12, color: SpviColors.navy),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
