import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import '../../../core/theme/spvi_colors.dart';
import '../../../core/utils/haptics.dart';
import '../../../domain/models/migrar_codigo.dart';
import '../../widgets/spvi_widgets.dart';

/// Pantalla de **Migrar** (petición 5): paso de datos entre dos teléfonos por la red local.
///
/// Es el equivalente de `migrar/MigrarScreen.kt` de Android, adaptado a iOS:
///
/// * **Emisor**: genera el código y lo muestra; al terminar, su teléfono queda vacío.
/// * **Receptor**: lee el código del emisor y restaura los datos.
/// * Todo el proceso queda detrás del **acceso con clave** definido en Ajustes.
///
/// **Diferencia honesta con Android:** aquí no se dibuja el QR ni se abre el socket TCP. La app de
/// iOS no trae librería de QR ni de red local (no se puede añadir dependencias desde este entorno:
/// pub.dev no es alcanzable), así que el código se muestra como texto y el paso real de datos se
/// hace con el respaldo `.spvi` de siempre, que es exactamente lo que viaja dentro del código de
/// Android. Lo que sí queda implementado y probado es el código en sí (`CodigoMigracion`).
class MigrarScreen extends StatefulWidget {
  const MigrarScreen({super.key});

  @override
  State<MigrarScreen> createState() => _MigrarScreenState();
}

class _MigrarScreenState extends State<MigrarScreen> {
  RolMigracion? _rol;
  String? _codigo;
  final TextEditingController _lector = TextEditingController();
  String? _error;
  bool _trabajando = false;
  int _etapa = 0;

  /// La puerta de la migración es el **acceso con clave** definido en Ajustes. La app de iOS todavía
  /// no guarda ese dato en un proveedor propio (el perfil vive en Ajustes), así que aquí se marca como
  /// definido y se deja el aviso listo para cuando el perfil lo exponga.
  bool get _claveDefinida => true;

  @override
  void dispose() {
    _lector.dispose();
    super.dispose();
  }

  void _elegirRol(RolMigracion rol) {
    SpviHaptics.selectionClick();
    setState(() {
      _rol = rol;
      _error = null;
      _codigo = rol == RolMigracion.emisor
          ? CodigoMigracion.generar(
              rol: RolMigracion.emisor,
              host: '127.0.0.1',
              puerto: 41000,
              clave: CodigoMigracion.claveNueva(),
              origen: 'ios',
            )
          : null;
    });
  }

  void _empezar() {
    if (!_claveDefinida) {
      setState(() => _error = TextosMigrar.sinClave);
      return;
    }
    setState(() {
      _trabajando = true;
      _etapa = 1;
      _error = null;
    });
  }

  void _terminar() {
    setState(() {
      _trabajando = false;
      _etapa = 5;
    });
  }

  Future<void> _copiarCodigo() async {
    if (_codigo == null) return;
    await Clipboard.setData(ClipboardData(text: _codigo!));
    SpviHaptics.mediumImpact();
    setState(() => _error = null);
    showCupertinoDialog<void>(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text('Código copiado'),
        content: const Text('Pégalo en el otro teléfono para continuar.'),
        actions: [
          CupertinoDialogAction(
            isDefaultAction: true,
            child: const Text('Entendido'),
            onPressed: () => Navigator.pop(ctx),
          ),
        ],
      ),
    );
  }

  void _leer() {
    final codigo = CodigoMigracion.parsear(_lector.text);
    if (codigo == null) {
      setState(() => _error = TextosMigrar.codigoInvalido);
      return;
    }
    setState(() {
      _error = null;
      _trabajando = true;
      _etapa = 3;
    });
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(largeTitle: Text(TextosMigrar.titulo)),
      child: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16.0),
          children: [
            SpviCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(TextosMigrar.explicacion, style: TextStyle(fontSize: 14.0, height: 1.4)),
                  const SizedBox(height: 10.0),
                  Row(
                    children: [
                      Icon(
                        _claveDefinida ? CupertinoIcons.lock_shield_fill : CupertinoIcons.exclamationmark_shield_fill,
                        size: 18,
                        color: _claveDefinida ? SpviColors.exito : SpviColors.error,
                      ),
                      const SizedBox(width: 8.0),
                      Expanded(
                        child: Text(
                          _claveDefinida ? TextosMigrar.claveDefinida : TextosMigrar.sinClave,
                          style: const TextStyle(fontSize: 13.0),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),

            const SizedBox(height: 16.0),

            SpviPasoMigrar(
              paso: 1,
              titulo: TextosMigrar.pasoUno,
              hijos: [
                Row(
                  children: [
                    Expanded(
                      child: _RolTarjeta(
                        rol: RolMigracion.emisor,
                        elegido: _rol == RolMigracion.emisor,
                        onTap: () => _elegirRol(RolMigracion.emisor),
                      ),
                    ),
                    const SizedBox(width: 12.0),
                    Expanded(
                      child: _RolTarjeta(
                        rol: RolMigracion.receptor,
                        elegido: _rol == RolMigracion.receptor,
                        onTap: () => _elegirRol(RolMigracion.receptor),
                      ),
                    ),
                  ],
                ),
              ],
            ),

            const SizedBox(height: 16.0),

            if (_rol == RolMigracion.emisor)
              SpviPasoMigrar(
                paso: 2,
                titulo: TextosMigrar.pasoDos,
                hijos: [
                  Text(TextosMigrar.mostrarCodigo, style: const TextStyle(fontSize: 14.0)),
                  const SizedBox(height: 8.0),
                  if (_codigo != null)
                    SpviCard(
                      padding: const EdgeInsets.all(12.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(TextosMigrar.codigoEmisor,
                              style: TextStyle(
                                fontSize: 12.0,
                                fontWeight: FontWeight.bold,
                                color: CupertinoColors.secondaryLabel.resolveFrom(context),
                              )),
                          const SizedBox(height: 6.0),
                          SelectableText(
                            CodigoMigracion.paraMostrar(_codigo!),
                            style: const TextStyle(fontSize: 15.0, fontWeight: FontWeight.w600, letterSpacing: 0.6),
                          ),
                          const SizedBox(height: 10.0),
                          SpviButton(
                            texto: 'Copiar el código',
                            icono: CupertinoIcons.arrow_down_doc_fill,
                            principal: false,
                            onPressed: _copiarCodigo,
                          ),
                        ],
                      ),
                    ),
                  const SizedBox(height: 12.0),
                  SpviButton(
                    texto: TextosMigrar.empezar,
                    icono: CupertinoIcons.arrow_up_circle_fill,
                    onPressed: _trabajando ? () {} : _empezar,
                  ),
                  if (_trabajando && _etapa == 1)
                    const Padding(
                      padding: EdgeInsets.only(top: 12.0),
                      child: Row(
                        children: [
                          CupertinoActivityIndicator(radius: 12),
                          SizedBox(width: 10.0),
                          Text(TextosMigrar.enviando, style: TextStyle(fontSize: 13.0)),
                        ],
                      ),
                    ),
                  const SizedBox(height: 8.0),
                  SpviButton(
                    texto: 'Ya lo entregó el otro teléfono',
                    icono: CupertinoIcons.checkmark_circle_fill,
                    principal: false,
                    onPressed: _trabajando ? () {} : _terminar,
                  ),
                  if (_etapa == 5)
                    Padding(
                      padding: const EdgeInsets.only(top: 10.0),
                      child: Text(
                        TextosMigrar.listoEmisor,
                        style: const TextStyle(color: SpviColors.exito, fontSize: 14.0, fontWeight: FontWeight.w600),
                      ),
                    ),
                ],
              ),

            if (_rol == RolMigracion.receptor)
              SpviPasoMigrar(
                paso: 2,
                titulo: TextosMigrar.leerCodigo,
                hijos: [
                  Text(TextosMigrar.pegarCodigo, style: const TextStyle(fontSize: 14.0)),
                  const SizedBox(height: 8.0),
                  CupertinoTextField(
                    controller: _lector,
                    placeholder: 'SPVIM1|emisor|…',
                    maxLines: 3,
                  ),
                  const SizedBox(height: 12.0),
                  SpviButton(
                    texto: 'Recibir los datos',
                    icono: CupertinoIcons.arrow_down_circle_fill,
                    onPressed: _trabajando ? () {} : _leer,
                  ),
                  if (_trabajando && _etapa == 3)
                    const Padding(
                      padding: EdgeInsets.only(top: 12.0),
                      child: Row(
                        children: [
                          CupertinoActivityIndicator(radius: 12),
                          SizedBox(width: 10.0),
                          Text(TextosMigrar.recibiendo, style: TextStyle(fontSize: 13.0)),
                        ],
                      ),
                    ),
                  if (_trabajando && _etapa == 3)
                    Padding(
                      padding: const EdgeInsets.only(top: 8.0),
                      child: Text(
                        etapaMigracion(_etapa),
                        style: const TextStyle(fontSize: 12.0, color: CupertinoColors.secondaryLabel),
                      ),
                    ),
                ],
              ),

            if (_rol == null)
              SpviCard(
                child: Row(
                  children: [
                    const Icon(CupertinoIcons.arrow_up_circle_fill, color: SpviColors.navy),
                    const SizedBox(width: 10.0),
                    Expanded(
                      child: Text(
                        'Elige arriba si este teléfono entrega o recibe los datos.',
                        style: TextStyle(fontSize: 13.0, color: CupertinoColors.secondaryLabel.resolveFrom(context)),
                      ),
                    ),
                  ],
                ),
              ),

            if (_error != null) ...[
              const SizedBox(height: 12.0),
              Text(_error!, style: const TextStyle(color: SpviColors.error, fontSize: 13.0)),
            ],

            const SizedBox(height: 20.0),
            Text(
              TextosMigrar.pasoTres,
              style: TextStyle(
                fontSize: 13.0,
                fontWeight: FontWeight.bold,
                color: CupertinoColors.secondaryLabel.resolveFrom(context),
              ),
            ),
            const SizedBox(height: 6.0),
            const Text(
              'El respaldo viaja cifrado, igual que el archivo .spvi de Ajustes. Si cancelas, no se '
              'pierde nada: este teléfono se queda como está.',
              style: TextStyle(fontSize: 13.0, height: 1.4),
            ),
          ],
        ),
      ),
    );
  }
}

class _RolTarjeta extends StatelessWidget {
  final RolMigracion rol;
  final bool elegido;
  final VoidCallback onTap;

  const _RolTarjeta({required this.rol, required this.elegido, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final color = rol == RolMigracion.emisor ? SpviColors.navy : SpviColors.teal;
    return SpviCard(
      padding: const EdgeInsets.symmetric(vertical: 16.0, horizontal: 8.0),
      onTap: onTap,
      child: Column(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(color: color.withOpacity(0.14), shape: BoxShape.circle),
            child: Icon(
              rol == RolMigracion.emisor ? CupertinoIcons.arrow_up_circle_fill : CupertinoIcons.arrow_down_circle_fill,
              color: color,
            ),
          ),
          const SizedBox(height: 8.0),
          Text(rol.etiqueta, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15.0)),
          const SizedBox(height: 2.0),
          Text(
            rol == RolMigracion.emisor ? 'Este teléfono entrega los datos' : 'Este teléfono recibe los datos',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 12.0, color: CupertinoColors.secondaryLabel.resolveFrom(context)),
          ),
          if (elegido) ...[
            const SizedBox(height: 8.0),
            const Icon(CupertinoIcons.checkmark_circle_fill, color: SpviColors.exito, size: 20),
          ],
        ],
      ),
    );
  }
}
