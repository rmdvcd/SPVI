import 'package:flutter/cupertino.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'src/core/theme/spvi_colors.dart';
import 'src/core/utils/haptics.dart';
import 'src/domain/models/models.dart';
import 'src/presentation/providers/spvi_providers.dart';
import 'src/presentation/screens/inicio/inicio_screen.dart';
import 'src/presentation/screens/licencia/bloqueo_screen.dart';
import 'src/presentation/screens/inventario/inventario_screen.dart';
import 'src/presentation/screens/servicios/servicios_screen.dart';
import 'src/presentation/screens/registros/registros_screen.dart';
import 'src/presentation/screens/ajustes/ajustes_screen.dart';
import 'src/presentation/widgets/spvi_widgets.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ProviderScope(child: SpviApp()));
}

/// App Raíz optimizada para iOS con Cupertino y sin overscroll glow
class SpviApp extends StatelessWidget {
  const SpviApp({super.key});

  @override
  Widget build(BuildContext context) {
    return CupertinoApp(
      title: 'SPVI',
      debugShowCheckedModeBanner: false,
      theme: SpviColors.lightTheme,
      localizationsDelegates: const [
        GlobalCupertinoLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ],
      supportedLocales: const [
        Locale('es', 'ES'),
        Locale('es', 'CU'),
      ],
      locale: const Locale('es', 'CU'),
      home: const Arranque(),
      builder: (context, child) {
        // Elimina cualquier glow de overscroll para una sensación 100% nativa iOS (bouncing physics)
        return ScrollConfiguration(
          behavior: const _CupertinoNoGlowScrollBehavior(),
          child: child ?? const SizedBox.shrink(),
        );
      },
    );
  }
}

/// Scroll behavior sin overscroll glow, con rebote nativo de iOS (BouncingScrollPhysics)
class _CupertinoNoGlowScrollBehavior extends ScrollBehavior {
  const _CupertinoNoGlowScrollBehavior();

  @override
  Widget buildOverscrollIndicator(BuildContext context, Widget child, ScrollableDetails details) {
    return child;
  }

  @override
  ScrollPhysics getScrollPhysics(BuildContext context) {
    return const BouncingScrollPhysics();
  }
}

/// Arranque: espera a que se lea la licencia del equipo y, si está **bloqueada** (prueba terminada, vencida, revocada
/// o reloj manipulado), no deja entrar al negocio: solo permite activar o pedir la licencia. Es el equivalente iOS de
/// `bloqueo/BloqueoGate.kt` de Android.
///
/// La licencia se vuelve a evaluar cada vez que el proveedor la consulta, así que este bloqueo no se puede sortear
/// dejando la app abierta.
class Arranque extends ConsumerWidget {
  const Arranque({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final licencia = ref.watch(licenciaProvider);
    if (licencia.tipo == TipoLicencia.vencida) return const PantallaBloqueo();
    if (licencia.cargando) {
      return const CupertinoPageScaffold(
        child: Center(child: CupertinoActivityIndicator(radius: 14)),
      );
    }
    return const SpviMainNavigation();
  }
}

/// Contenedor de Navegación Principal.
///
/// Petición 10 (animaciones): al tocar la barra inferior el contenido entra y sale con un **fundido**
/// (`SpviAnimacion.fundido`), como en Android. Para conseguirlo se usa un `AnimatedSwitcher` propio en
/// lugar de `CupertinoTabScaffold`, que no deja elegir la transición entre pestañas. Cada pantalla
/// conserva su `Navigator` raíz, así que las ventanas que se abren desde ellas siguen entrando con el
/// **deslizamiento lateral** de iOS.
class SpviMainNavigation extends StatefulWidget {
  const SpviMainNavigation({super.key});

  @override
  State<SpviMainNavigation> createState() => _SpviMainNavigationState();
}

class _SpviMainNavigationState extends State<SpviMainNavigation> {
  static const List<String> _etiquetas = ['Inicio', 'Inventario', 'Servicios', 'Registros', 'Ajustes'];
  static const List<IconData> _iconos = [
    CupertinoIcons.house_fill,
    CupertinoIcons.cube_box_fill,
    CupertinoIcons.briefcase_fill,
    CupertinoIcons.doc_text_fill,
    CupertinoIcons.gear_alt_fill,
  ];

  int _pestana = 0;

  void _cambiar(int indice) {
    if (indice == _pestana) return;
    SpviHaptics.selectionClick();
    setState(() => _pestana = indice);
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      child: Column(
        children: [
          Expanded(
            child: AnimatedSwitcher(
              duration: SpviAnimacion.fundido,
              switchInCurve: Curves.easeOut,
              switchOutCurve: Curves.easeIn,
              transitionBuilder: (child, animacion) => FadeTransition(
                opacity: animacion,
                child: child,
              ),
              child: KeyedSubtree(
                key: ValueKey(_pestana),
                child: _pantalla(_pestana),
              ),
            ),
          ),
          SafeArea(
            top: false,
            child: CupertinoTabBar(
              currentIndex: _pestana,
              activeColor: SpviColors.navy,
              inactiveColor: CupertinoColors.systemGrey,
              onTap: _cambiar,
              items: [
                for (var i = 0; i < _etiquetas.length; i++)
                  BottomNavigationBarItem(
                    icon: Icon(_iconos[i]),
                    label: _etiquetas[i],
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _pantalla(int indice) {
    switch (indice) {
      case 1:
        return const InventarioScreen();
      case 2:
        return const ServiciosScreen();
      case 3:
        return const RegistrosScreen();
      case 4:
        return const AjustesScreen();
      default:
        return const InicioScreen();
    }
  }
}
