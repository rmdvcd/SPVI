import 'package:flutter/cupertino.dart';
import '../../core/theme/spvi_colors.dart';
import '../../core/utils/haptics.dart';

/// Tarjeta de sección estilo iOS agrupada (Cupertino Grouped Inset Card)
class SpviCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final VoidCallback? onTap;

  const SpviCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16.0),
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = CupertinoTheme.brightnessOf(context) == Brightness.dark;
    final cardContent = Container(
      decoration: BoxDecoration(
        color: isDark ? SpviColors.darkSurface : CupertinoColors.white,
        borderRadius: BorderRadius.circular(12.0),
        border: Border.all(
          color: isDark ? SpviColors.darkBorder : const Color(0xFFE5E5EA),
          width: 0.8,
        ),
      ),
      padding: padding,
      child: child,
    );

    if (onTap != null) {
      return CupertinoButton(
        padding: EdgeInsets.zero,
        onPressed: () {
          SpviHaptics.lightImpact();
          onTap!();
        },
        child: cardContent,
      );
    }

    return cardContent;
  }
}

/// Banner de Licencia o Alerta destacado
class SpviBanner extends StatelessWidget {
  final String titulo;
  final String mensaje;
  final IconData icono;
  final Color colorFondo;
  final Color colorTexto;
  final VoidCallback? onAccion;
  final String? textoAccion;

  const SpviBanner({
    super.key,
    required this.titulo,
    required this.mensaje,
    this.icono = CupertinoIcons.info_circle_fill,
    this.colorFondo = SpviColors.lightPrimaryContainer,
    this.colorTexto = SpviColors.navy,
    this.onAccion,
    this.textoAccion,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
      decoration: BoxDecoration(
        color: colorFondo,
        borderRadius: BorderRadius.circular(12.0),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icono, color: colorTexto, size: 22.0),
          const SizedBox(width: 12.0),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  titulo,
                  style: TextStyle(
                    fontSize: 15.0,
                    fontWeight: FontWeight.bold,
                    color: colorTexto,
                  ),
                ),
                const SizedBox(height: 2.0),
                Text(
                  mensaje,
                  style: TextStyle(
                    fontSize: 13.0,
                    color: colorTexto.withOpacity(0.9),
                  ),
                ),
                if (onAccion != null && textoAccion != null) ...[
                  const SizedBox(height: 6.0),
                  CupertinoButton(
                    padding: EdgeInsets.zero,
                    minSize: 0,
                    onPressed: () {
                      SpviHaptics.lightImpact();
                      onAccion!();
                    },
                    child: Text(
                      textoAccion!,
                      style: TextStyle(
                        fontSize: 14.0,
                        fontWeight: FontWeight.w600,
                        color: colorTexto,
                        decoration: TextDecoration.underline,
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Botón con estilo iOS y feedback háptico
class SpviButton extends StatelessWidget {
  final String texto;
  final IconData? icono;
  final VoidCallback onPressed;
  final bool principal;
  final bool destructivo;

  const SpviButton({
    super.key,
    required this.texto,
    this.icono,
    required this.onPressed,
    this.principal = true,
    this.destructivo = false,
  });

  @override
  Widget build(BuildContext context) {
    final color = destructivo
        ? SpviColors.error
        : (principal ? SpviColors.navy : CupertinoColors.systemGrey5);
    final textoColor = destructivo
        ? CupertinoColors.white
        : (principal ? CupertinoColors.white : CupertinoColors.label);

    return CupertinoButton(
      color: color,
      padding: const EdgeInsets.symmetric(vertical: 14.0, horizontal: 20.0),
      borderRadius: BorderRadius.circular(10.0),
      onPressed: () {
        if (destructivo) {
          SpviHaptics.heavyImpact();
        } else {
          SpviHaptics.lightImpact();
        }
        onPressed();
      },
      child: Row(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          if (icono != null) ...[
            Icon(icono, color: textoColor, size: 20.0),
            const SizedBox(width: 8.0),
          ],
          Text(
            texto,
            style: TextStyle(
              color: textoColor,
              fontWeight: FontWeight.w600,
              fontSize: 16.0,
            ),
          ),
        ],
      ),
    );
  }
}
