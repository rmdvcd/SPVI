import 'package:flutter/cupertino.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'src/core/theme/spvi_colors.dart';
import 'src/core/utils/haptics.dart';
import 'src/presentation/screens/inicio/inicio_screen.dart';
import 'src/presentation/screens/inventario/inventario_screen.dart';
import 'src/presentation/screens/servicios/servicios_screen.dart';
import 'src/presentation/screens/registros/registros_screen.dart';
import 'src/presentation/screens/ajustes/ajustes_screen.dart';

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
      home: const SpviMainNavigation(),
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

/// Contenedor de Navegación Principal con CupertinoTabScaffold
class SpviMainNavigation extends StatefulWidget {
  const SpviMainNavigation({super.key});

  @override
  State<SpviMainNavigation> createState() => _SpviMainNavigationState();
}

class _SpviMainNavigationState extends State<SpviMainNavigation> {
  final CupertinoTabController _tabController = CupertinoTabController();

  @override
  Widget build(BuildContext context) {
    return CupertinoTabScaffold(
      controller: _tabController,
      tabBar: CupertinoTabBar(
        activeColor: SpviColors.navy,
        inactiveColor: CupertinoColors.systemGrey,
        onTap: (index) {
          SpviHaptics.lightImpact();
        },
        items: const [
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.house_fill),
            label: 'Inicio',
          ),
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.cube_box_fill),
            label: 'Inventario',
          ),
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.briefcase_fill),
            label: 'Servicios',
          ),
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.doc_text_fill),
            label: 'Registros',
          ),
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.gear_alt_fill),
            label: 'Ajustes',
          ),
        ],
      ),
      tabBuilder: (BuildContext context, int index) {
        switch (index) {
          case 0:
            return CupertinoTabView(
              builder: (context) => const InicioScreen(),
            );
          case 1:
            return CupertinoTabView(
              builder: (context) => const InventarioScreen(),
            );
          case 2:
            return CupertinoTabView(
              builder: (context) => const ServiciosScreen(),
            );
          case 3:
            return CupertinoTabView(
              builder: (context) => const RegistrosScreen(),
            );
          case 4:
            return CupertinoTabView(
              builder: (context) => const AjustesScreen(),
            );
          default:
            return CupertinoTabView(
              builder: (context) => const InicioScreen(),
            );
        }
      },
    );
  }
}
