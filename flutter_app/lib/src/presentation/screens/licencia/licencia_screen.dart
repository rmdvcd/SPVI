import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/spvi_colors.dart';
import '../../../core/utils/haptics.dart';
import '../../../domain/models/models.dart';
import '../../../licensing/cliente.dart' as lic;
import '../../../licensing/licencia_corta.dart' as gl;
import '../../providers/spvi_providers.dart';
import '../../widgets/spvi_widgets.dart';

/// Pantalla de licencia: estado real (re-verificado en cada consulta), activación `SPVI2:` y solicitud `SPVIR1`.
///
/// Todo el trabajo criptográfico ocurre en `licensing/cliente.dart`; aquí solo hay interfaz. Nunca se le dice al
/// usuario qué comprobación falló: los mensajes de rechazo son los que devuelve el cliente (genéricos).
class LicenciaScreen extends ConsumerStatefulWidget {
  const LicenciaScreen({super.key});

  /// WhatsApp del desarrollador (misma constante que las otras dos apps).
  static const String telefonoDesarrollador = '+53 5181 5604';
  static const String whatsappUrl = 'https://wa.me/5351815604';

  @override
  ConsumerState<LicenciaScreen> createState() => _LicenciaScreenState();
}

class _LicenciaScreenState extends ConsumerState<LicenciaScreen> {
  final TextEditingController _claveController = TextEditingController();
  final TextEditingController _nombreController = TextEditingController();
  final TextEditingController _apellidosController = TextEditingController();
  final TextEditingController _ciController = TextEditingController();
  final TextEditingController _telefonoController = TextEditingController();
  final TextEditingController _transaccionController = TextEditingController();

  bool _activando = false;
  bool _solicitando = false;
  bool _mostrarSolicitud = false;
  gl.TipoLicenciaGL _tipo = gl.TipoLicenciaGL.mensual;
  int _secundarias = 0;
  lic.SolicitudGenerada? _generada;

  @override
  void dispose() {
    _claveController.dispose();
    _nombreController.dispose();
    _apellidosController.dispose();
    _ciController.dispose();
    _telefonoController.dispose();
    _transaccionController.dispose();
    super.dispose();
  }

  // ---------------------------------------------------------------- acciones

  Future<void> _activar() async {
    final clave = _claveController.text.trim();
    if (clave.isEmpty) {
      await _aviso('Escribe el código', 'Pega el mensaje que te envió el desarrollador (contiene el código SPVI2:).');
      return;
    }
    setState(() => _activando = true);
    final resultado = await ref.read(licenciaProvider.notifier).activar(clave);
    if (!mounted) return;
    setState(() => _activando = false);
    SpviHaptics.heavyImpact();
    if (resultado.aceptada) {
      _claveController.clear();
      final estado = resultado.estado;
      await _aviso(
        'Licencia activada',
        estado == null ? 'Tu copia de SPVI quedó activada.' : '${estado.etiqueta}. Gracias por confiar en SPVI.',
      );
    } else {
      await _aviso('No se pudo activar', resultado.motivo ?? 'La licencia no es válida para esta instalación.');
    }
  }

  Future<void> _generarSolicitud() async {
    final renovable = await ref.read(licenciaProvider.notifier).licenciaRenovable();
    if (!mounted) return;
    setState(() => _solicitando = true);
    try {
      final generada = await ref.read(licenciaProvider.notifier).solicitar(
            lic.DatosSolicitud(
              nombre: _nombreController.text,
              apellidos: _apellidosController.text,
              ci: _ciController.text,
              telefono: _telefonoController.text,
              tipo: _tipo,
              secundarias: _secundarias,
              transaccion: _transaccionController.text,
              renueva: renovable,
            ),
          );
      if (!mounted) return;
      setState(() {
        _solicitando = false;
        _generada = generada;
      });
      if (generada == null) {
        await _aviso('Sin claves de emisión', 'Este build no trae las claves de GL: no se pueden generar solicitudes.');
      } else {
        SpviHaptics.mediumImpact();
      }
    } catch (error) {
      if (!mounted) return;
      setState(() => _solicitando = false);
      await _aviso('Revisa los datos', error is lic.LicenciaInvalida ? error.detalle : 'No se pudo generar la solicitud.');
    }
  }

  Future<void> _copiar(String texto, String queja) async {
    await Clipboard.setData(ClipboardData(text: texto));
    SpviHaptics.lightImpact();
    if (!mounted) return;
    await _aviso('Copiado', queja);
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

  // ---------------------------------------------------------------- interfaz

  @override
  Widget build(BuildContext context) {
    final licencia = ref.watch(licenciaProvider);

    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(
        middle: Text('Licencia SPVI'),
        previousPageTitle: 'Ajustes',
      ),
      child: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16.0),
          children: [
            _estado(licencia),
            const SizedBox(height: 20),
            if (licencia.cargando)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 24.0),
                child: Center(child: CupertinoActivityIndicator()),
              )
            else ...[
              if (licencia.tipo != TipoLicencia.activa) ...[
                _activacion(),
                const SizedBox(height: 20),
              ],
              _solicitud(licencia),
            ],
            const SizedBox(height: 24),
            _contrato(licencia),
          ],
        ),
      ),
    );
  }

  Widget _estado(LicenciaInfo licencia) {
    final activa = licencia.tipo == TipoLicencia.activa;
    final bloqueada = licencia.tipo == TipoLicencia.vencida;
    return SpviCard(
      child: Column(
        children: [
          Icon(
            activa
                ? CupertinoIcons.checkmark_seal_fill
                : bloqueada
                    ? CupertinoIcons.exclamationmark_shield_fill
                    : CupertinoIcons.shield_lefthalf_fill,
            size: 60,
            color: activa ? SpviColors.exito : bloqueada ? SpviColors.error : SpviColors.navy,
          ),
          const SizedBox(height: 12),
          Text(
            activa
                ? 'Licencia Activa y Válida'
                : bloqueada
                    ? 'Licencia Bloqueada'
                    : 'Período de Prueba',
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 6),
          Text(
            _detalleEstado(licencia),
            textAlign: TextAlign.center,
            style: const TextStyle(color: CupertinoColors.secondaryLabel, fontSize: 14),
          ),
          const SizedBox(height: 12),
          CupertinoButton(
            padding: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 4.0),
            onPressed: licencia.dispositivo.isEmpty
                ? null
                : () => _copiar(licencia.dispositivo, 'Identificador de este equipo copiado. Envíalo si te lo pide el desarrollador.'),
            child: Text(
              licencia.dispositivo.isEmpty ? 'Equipo sin registrar' : 'Equipo: ${licencia.dispositivo}',
              style: const TextStyle(fontSize: 12, color: SpviColors.navy),
            ),
          ),
        ],
      ),
    );
  }

  String _detalleEstado(LicenciaInfo licencia) {
    if (licencia.motivo != null && licencia.tipo == TipoLicencia.vencida) return licencia.motivo!;
    if (licencia.tipo == TipoLicencia.activa) {
      final tipo = licencia.titular ?? 'Licencia';
      final vence = licencia.venceEn;
      final secundarias = licencia.secundarias;
      return '$tipo · $secundarias app${secundarias == 1 ? '' : 's'} secundaria${secundarias == 1 ? '' : 's'}'
          '${vence == null ? ' · sin vencimiento' : ' · vence el ${_fecha(vence)}'}';
    }
    final dias = licencia.diasRestantesPrueba;
    return 'Te restan $dias día${dias == 1 ? '' : 's'} para probar todas las funciones sin restricciones.';
  }

  Widget _activacion() => SpviCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Activar licencia', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            const Text(
              'Pega el mensaje completo del desarrollador (empieza por «Licencia SPVI» e incluye el código SPVI2:).',
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
          ],
        ),
      );

  Widget _solicitud(LicenciaInfo licencia) {
    final tipos = LicenciaNotifier.tipos;
    return SpviCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Expanded(
                child: Text('Solicitar licencia', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
              ),
              CupertinoButton(
                padding: EdgeInsets.zero,
                onPressed: () => setState(() => _mostrarSolicitud = !_mostrarSolicitud),
                child: Text(_mostrarSolicitud ? 'Ocultar' : 'Ver formulario', style: const TextStyle(fontSize: 13)),
              ),
            ],
          ),
          const Text(
            'La solicitud se cifra en el equipo con la clave de GL y viaja por WhatsApp. No lleva datos personales '
            'legibles más allá de los que escribas aquí.',
            style: TextStyle(fontSize: 13, color: CupertinoColors.secondaryLabel),
          ),
          if (_mostrarSolicitud) ...[
            const SizedBox(height: 12),
            CupertinoTextField(
              controller: _nombreController,
              placeholder: 'Nombre',
              padding: const EdgeInsets.all(12.0),
            ),
            const SizedBox(height: 8),
            CupertinoTextField(
              controller: _apellidosController,
              placeholder: 'Apellidos',
              padding: const EdgeInsets.all(12.0),
            ),
            const SizedBox(height: 8),
            CupertinoTextField(
              controller: _ciController,
              placeholder: 'Carné de identidad',
              padding: const EdgeInsets.all(12.0),
              keyboardType: TextInputType.number,
            ),
            const SizedBox(height: 8),
            CupertinoTextField(
              controller: _telefonoController,
              placeholder: 'Teléfono',
              padding: const EdgeInsets.all(12.0),
              keyboardType: TextInputType.phone,
            ),
            const SizedBox(height: 8),
            CupertinoTextField(
              controller: _transaccionController,
              placeholder: 'No. de transacción del pago (Transfermóvil/EnZona)',
              padding: const EdgeInsets.all(12.0),
            ),
            const SizedBox(height: 12),
            const Text('Tipo de licencia', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
            const SizedBox(height: 6),
            CupertinoSlidingSegmentedControl<gl.TipoLicenciaGL>(
              groupValue: _tipo,
              onValueChanged: (valor) => setState(() => _tipo = valor ?? _tipo),
              children: {
                for (final tipo in tipos) tipo: Padding(padding: const EdgeInsets.symmetric(vertical: 6.0), child: Text(tipo.etiqueta)),
              },
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                const Expanded(
                  child: Text('Apps secundarias', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                ),
                CupertinoButton(
                  padding: const EdgeInsets.symmetric(horizontal: 10.0),
                  onPressed: _secundarias == 0 ? null : () => setState(() => _secundarias--),
                  child: const Icon(CupertinoIcons.minus_circle_fill, size: 26),
                ),
                SizedBox(
                  width: 32,
                  child: Text('$_secundarias', textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.bold)),
                ),
                CupertinoButton(
                  padding: const EdgeInsets.symmetric(horizontal: 10.0),
                  onPressed: _secundarias >= lic.ClienteLicencia.secundariasMax
                      ? null
                      : () => setState(() => _secundarias++),
                  child: const Icon(CupertinoIcons.plus_circle_fill, size: 26),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              'Precio: ${lic.Solicitud.precio(_tipo, _secundarias)} CUP',
              style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: SpviColors.navy),
            ),
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              child: CupertinoButton.filled(
                onPressed: _solicitando ? null : _generarSolicitud,
                child: _solicitando
                    ? const CupertinoActivityIndicator(color: CupertinoColors.white)
                    : const Text('Generar solicitud cifrada'),
              ),
            ),
            if (_generada != null) ...[
              const SizedBox(height: 14),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12.0),
                decoration: BoxDecoration(
                  color: SpviColors.lightPrimaryContainer,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  _generada!.mensaje,
                  style: const TextStyle(fontSize: 12),
                ),
              ),
              const SizedBox(height: 10),
              SizedBox(
                width: double.infinity,
                child: CupertinoButton(
                  color: SpviColors.teal,
                  onPressed: () => _copiar(_generada!.mensaje, 'Abre WhatsApp, busca ${LicenciaScreen.telefonoDesarrollador} y pega el mensaje.'),
                  child: const Text('Copiar solicitud'),
                ),
              ),
              const SizedBox(height: 6),
              Text(
                'Envíala por WhatsApp al ${LicenciaScreen.telefonoDesarrollador} (${LicenciaScreen.whatsappUrl}). '
                'Recibirás de vuelta tu licencia SPVI2 para pegarla arriba.',
                style: const TextStyle(fontSize: 12, color: CupertinoColors.secondaryLabel),
              ),
            ],
          ],
          if (licencia.secundarias > 0 && licencia.tipo == TipoLicencia.activa)
            Padding(
              padding: const EdgeInsets.only(top: 8.0),
              child: Text(
                'Esta licencia cubre ${licencia.secundarias} app(s) secundaria(s).',
                style: const TextStyle(fontSize: 12, color: CupertinoColors.secondaryLabel),
              ),
            ),
        ],
      ),
    );
  }

  Widget _contrato(LicenciaInfo licencia) => SpviCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: const [
            Text('Información del Contrato', style: TextStyle(fontWeight: FontWeight.bold)),
            SizedBox(height: 8),
            Text(
              'SPVI no requiere conexión a internet para validar la licencia ni recopila datos personales. La clave '
              'vincula tu instalación de forma matemática y permanente: la licencia se verifica entera cada vez que la '
              'app la consulta, con las claves públicas del desarrollador fijadas en el propio build.',
              style: TextStyle(fontSize: 13, color: CupertinoColors.secondaryLabel),
            ),
            SizedBox(height: 8),
            Text(
              'Si un archivo del equipo se altera, la fecha del sistema retrocede o la licencia está en la lista de '
              'revocadas, la app queda bloqueada y te lo dirá aquí.',
              style: TextStyle(fontSize: 13, color: CupertinoColors.secondaryLabel),
            ),
          ],
        ),
      );

  static String _fecha(DateTime momento) {
    final local = momento.toLocal();
    final dia = local.day.toString().padLeft(2, '0');
    final mes = local.month.toString().padLeft(2, '0');
    return '$dia/$mes/${local.year}';
  }
}
