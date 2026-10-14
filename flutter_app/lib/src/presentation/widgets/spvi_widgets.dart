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

// ---------------------------------------------------------------------------
// Piezas compartidas de los cambios del 14 de octubre de 2026 (peticiones 1 a 10).
// Todo en español y con los tokens de `spvi_colors.dart`, sin estilos improvisados.
// ---------------------------------------------------------------------------

/// Duraciones de las animaciones pedidas (petición 10): barra de navegación = fundido,
/// ventanas emergentes = suben desde el centro de abajo.
class SpviAnimacion {
  SpviAnimacion._();

  /// Fundido de entrada y salida al cambiar de pestaña en la barra inferior.
  static const Duration fundido = Duration(milliseconds: 220);

  /// Ventanas y diálogos: suben desde el centro de abajo.
  static const Duration ventana = Duration(milliseconds: 260);
}

/// Casilla de selección redonda, igual a la de Inventario (petición 3).
class SpviCasilla extends StatelessWidget {
  final bool marcada;
  final ValueChanged<bool> onChanged;
  final Color? color;

  const SpviCasilla({
    super.key,
    required this.marcada,
    required this.onChanged,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    final activo = color ?? SpviColors.navy;
    return CupertinoButton(
      padding: EdgeInsets.zero,
      minSize: 0,
      onPressed: () {
        SpviHaptics.selectionClick();
        onChanged(!marcada);
      },
      child: Container(
        width: 26,
        height: 26,
        decoration: BoxDecoration(
          color: marcada ? activo : CupertinoColors.transparent,
          shape: BoxShape.circle,
          border: Border.all(
            color: marcada ? activo : CupertinoColors.systemGrey3.resolveFrom(context),
            width: 1.6,
          ),
        ),
        child: marcada
            ? const Icon(CupertinoIcons.checkmark, size: 16, color: CupertinoColors.white)
            : null,
      ),
    );
  }
}

/// Barra que aparece cuando hay elementos marcados: cuántos son y cómo quitar la selección.
class SpviBarraSeleccion extends StatelessWidget {
  final int cantidad;
  final VoidCallback onSeleccionarTodo;
  final VoidCallback onQuitar;

  const SpviBarraSeleccion({
    super.key,
    required this.cantidad,
    required this.onSeleccionarTodo,
    required this.onQuitar,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      color: SpviColors.navy.withOpacity(0.12),
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
      child: Row(
        children: [
          Icon(CupertinoIcons.checkmark_circle_fill, size: 20, color: SpviColors.navy),
          const SizedBox(width: 8.0),
          Expanded(
            child: Text(
              cantidad == 1 ? "1 elemento seleccionado" : "$cantidad elementos seleccionados",
              style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14.0),
            ),
          ),
          CupertinoButton(
            padding: EdgeInsets.zero,
            minSize: 0,
            onPressed: () {
              SpviHaptics.lightImpact();
              onSeleccionarTodo();
            },
          child: Text(
            "Seleccionar todo",
            style: const TextStyle(fontSize: 13.0, fontWeight: FontWeight.w600),
          ),
          ),
          const SizedBox(width: 12.0),
          CupertinoButton(
            padding: EdgeInsets.zero,
            minSize: 0,
            onPressed: () {
              SpviHaptics.lightImpact();
              onQuitar();
            },
            child: const Text("Quitar", style: TextStyle(fontSize: 13.0, color: SpviColors.error, fontWeight: FontWeight.w600)),
          ),
        ],
      ),
    );
  }
}

/// Ficha de dos columnas (etiqueta a la izquierda, valor a la derecha) usada en Soporte y Migrar.
class SpviFicha extends StatelessWidget {
  final List<(String, String)> filas;

  const SpviFicha({super.key, required this.filas});

  @override
  Widget build(BuildContext context) {
    final etiqueta = TextStyle(fontSize: 13.0, color: CupertinoColors.secondaryLabel.resolveFrom(context));
    final valor = const TextStyle(fontSize: 14.0, fontWeight: FontWeight.w600);
    return Column(
      children: [
        for (var i = 0; i < filas.length; i++) ...[
          if (i > 0) const Divider(height: 10, indent: 8, endIndent: 8),
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 5.0),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(child: Text(filas[i].$1, style: etiqueta)),
                const SizedBox(width: 12.0),
                Expanded(child: Text(filas[i].$2, style: valor)),
              ],
            ),
          ),
        ],
      ],
    );
  }
}

/// Fila de Soporte: icono, descripción y texto, todo centrado (petición 8).
class SpviFilaSoporte extends StatelessWidget {
  final IconData icono;
  final Color color;
  final String descripcion;
  final String texto;
  final VoidCallback? onTap;

  const SpviFilaSoporte({
    super.key,
    required this.icono,
    required this.color,
    required this.descripcion,
    required this.texto,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final secundario = TextStyle(
      fontSize: 13.0,
      color: CupertinoColors.secondaryLabel.resolveFrom(context),
      height: 1.35,
    );
    return CupertinoButton(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 14.0),
      minSize: 0,
      onPressed: onTap == null
          ? null
          : () {
              SpviHaptics.lightImpact();
              onTap!();
            },
      child: Column(
        children: [
          Container(
            width: 46,
            height: 46,
            decoration: BoxDecoration(color: color.withOpacity(0.14), shape: BoxShape.circle),
            child: Icon(icono, color: color, size: 26),
          ),
          const SizedBox(height: 8.0),
          Text(
            descripcion,
            textAlign: TextAlign.center,
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15.0),
          ),
          const SizedBox(height: 4.0),
          Text(texto, textAlign: TextAlign.center, style: secundario),
        ],
      ),
    );
  }
}

/// Sección del manual de Ayuda: icono, título y detalle (petición 7).
class SpviSeccionAyuda extends StatelessWidget {
  final IconData icono;
  final String titulo;
  final String detalle;
  final VoidCallback onTap;

  const SpviSeccionAyuda({
    super.key,
    required this.icono,
    required this.titulo,
    required this.detalle,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return SpviCard(
      padding: const EdgeInsets.all(12.0),
      onTap: onTap,
      child: Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: SpviColors.navy.withOpacity(0.12),
              borderRadius: BorderRadius.circular(8.0),
            ),
            child: Icon(icono, color: SpviColors.navy),
          ),
          const SizedBox(width: 12.0),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(titulo, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16.0)),
                const SizedBox(height: 2.0),
                Text(
                  detalle,
                  style: TextStyle(fontSize: 13.0, color: CupertinoColors.secondaryLabel.resolveFrom(context)),
                ),
              ],
            ),
          ),
          const Icon(CupertinoIcons.chevron_right, size: 16, color: CupertinoColors.systemGrey3),
        ],
      ),
    );
  }
}

/// Tema del manual: icono a la izquierda y el texto a la derecha.
class SpviTemaAyuda extends StatelessWidget {
  final IconData icono;
  final String titulo;
  final List<String> parrafos;

  const SpviTemaAyuda({
    super.key,
    required this.icono,
    required this.titulo,
    required this.parrafos,
  });

  @override
  Widget build(BuildContext context) {
    return SpviCard(
      padding: const EdgeInsets.all(14.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icono, color: SpviColors.navy, size: 22),
              const SizedBox(width: 8.0),
              Expanded(
                child: Text(titulo, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16.0)),
              ),
            ],
          ),
          const SizedBox(height: 8.0),
          for (final parrafo in parrafos)
            Padding(
              padding: const EdgeInsets.only(bottom: 6.0),
              child: Text(
                parrafo,
                style: const TextStyle(fontSize: 14.0, height: 1.4),
              ),
            ),
        ],
      ),
    );
  }
}

/// Diagrama del manual: filas de cajas con etiquetas, como los esquemas de Android.
class SpviEsquema extends StatelessWidget {
  final List<String> cajas;
  final List<String> flechas;

  const SpviEsquema({super.key, required this.cajas, required this.flechas});

  @override
  Widget build(BuildContext context) {
    final etiqueta = TextStyle(
      fontSize: 12.0,
      fontWeight: FontWeight.w600,
      color: CupertinoColors.secondaryLabel.resolveFrom(context),
    );
    return Padding(
      padding: const EdgeInsets.only(top: 4.0, bottom: 8.0),
      child: Column(
        children: [
          for (var i = 0; i < cajas.length; i++) ...[
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(vertical: 10.0, horizontal: 12.0),
              decoration: BoxDecoration(
                color: CupertinoColors.systemGrey5.resolveFrom(context),
                borderRadius: BorderRadius.circular(8.0),
                border: Border.all(color: CupertinoColors.separator.resolveFrom(context)),
              ),
              child: Text(
                cajas[i],
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 13.0, fontWeight: FontWeight.w600),
              ),
            ),
            if (i < flechas.length)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 4.0),
                child: Text(flechas[i], style: etiqueta),
              ),
          ],
        ],
      ),
    );
  }
}

/// Cuadro con barra de progreso circular, para Actualizar (petición 6).
class SpviProgresoCircular extends StatelessWidget {
  final String titulo;
  final String? mensaje;
  final double? progreso;
  final String? textoBoton;
  final VoidCallback? onBoton;

  const SpviProgresoCircular({
    super.key,
    required this.titulo,
    this.mensaje,
    this.progreso,
    this.textoBoton,
    this.onBoton,
  });

  @override
  Widget build(BuildContext context) {
    final secundario = TextStyle(
      fontSize: 13.0,
      color: CupertinoColors.secondaryLabel.resolveFrom(context),
    );
    return CupertinoAlertDialog(
      title: Text(titulo),
      content: Padding(
        padding: const EdgeInsets.only(top: 12.0),
        child: Column(
          children: [
            SizedBox(
              height: 56,
              width: 56,
              child: Stack(
                alignment: Alignment.center,
                children: [
                  CupertinoActivityIndicator(radius: 24),
                  if (progreso != null)
                    Text("${(progreso! * 100).round()}%", style: const TextStyle(fontSize: 12.0, fontWeight: FontWeight.bold)),
                ],
              ),
            ),
            if (mensaje != null) ...[
              const SizedBox(height: 12.0),
              Text(mensaje!, textAlign: TextAlign.center, style: secundario),
            ],
          ],
        ),
      ),
      actions: [
        CupertinoDialogAction(
          isDefaultAction: textoBoton != null,
          onPressed: () {
            SpviHaptics.lightImpact();
            if (onBoton != null) {
              onBoton!();
            } else {
              Navigator.pop(context);
            }
          },
          child: Text(textoBoton ?? "Cerrar"),
        ),
      ],
    );
  }
}

/// Paso del asistente de Migrar: número, título y contenido (petición 5).
class SpviPasoMigrar extends StatelessWidget {
  final int paso;
  final String titulo;
  final List<Widget> hijos;

  const SpviPasoMigrar({
    super.key,
    required this.paso,
    required this.titulo,
    required this.hijos,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Container(
              width: 26,
              height: 26,
              decoration: const BoxDecoration(color: SpviColors.navy, shape: BoxShape.circle),
              alignment: Alignment.center,
              child: Text(
                "$paso",
                style: const TextStyle(color: CupertinoColors.white, fontWeight: FontWeight.bold, fontSize: 13.0),
              ),
            ),
            const SizedBox(width: 8.0),
            Expanded(child: Text(titulo, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16.0))),
          ],
        ),
        const SizedBox(height: 10.0),
        ...hijos,
      ],
    );
  }
}
