import 'package:flutter/cupertino.dart';
import '../../../core/theme/spvi_colors.dart';
import '../../widgets/spvi_widgets.dart';

/// Contenido del manual (petición 7).
///
/// Es el equivalente de `ayuda/AyudaContenido.kt` de Android: el texto vive **fuera** de la pantalla,
/// en español y junto a los iconos, para poder comprobar el manual sin montar la interfaz.
/// **Sin capturas**: los diagramas son esquemas dibujados con cajas, como en Android, para que no se
/// desactualicen.
class AyudaContenido {
  const AyudaContenido._();

  static const List<SeccionAyuda> secciones = [
    SeccionAyuda(
      icono: CupertinoIcons.gear_alt_fill,
      titulo: 'Empezar',
      detalle: 'Perfil, licencia y acceso con clave',
      temas: [
        TemaAyuda(
          icono: CupertinoIcons.person_crop_circle_fill,
          titulo: 'Perfil del negocio',
          parrafos: [
            'En Ajustes → Perfil pones el nombre del negocio, tu nombre, apellidos y carné. '
                'Con eso SPVI sabe a quién pertenece la licencia y quién responde por la caja.',
            'El rol del teléfono se ve en la misma tarjeta: Principal o Secundaria. Una secundaria '
                'sincroniza con la principal por la red local y no puede cambiar la licencia.',
          ],
        ),
        TemaAyuda(
          icono: CupertinoIcons.lock_shield_fill,
          titulo: 'Licencia y acceso con clave',
          parrafos: [
            'Ajustes → Licencia SPVI muestra el estado real: activa, en prueba o bloqueada. Desde '
                'ahí se activa el código que envía el desarrollador o se genera la solicitud nueva.',
            'Ajustes → Acceso con Clave pide la huella o el PIN del teléfono al abrir la app y '
                'después de 10 minutos sin usarla. Es la misma puerta que protege Migrar.',
          ],
        ),
        TemaAyuda(
          icono: CupertinoIcons.arrow_right_arrow_left_circle_fill,
          titulo: 'Migrar a otro teléfono',
          parrafos: [
            'Ajustes → Migrar pasa los datos de un teléfono a otro. Un teléfono es el emisor '
                '(entrega la copia) y el otro el receptor (la recibe).',
            'El emisor muestra un código; el receptor lo lee. Todo el camino queda protegido por el '
                'acceso con clave. Al terminar, el teléfono emisor se queda vacío.',
          ],
          esquema: EsquemaAyuda(
            cajas: ['Teléfono emisor: muestra el código', 'Código de migración', 'Teléfono receptor: lee el código y restaura'],
            flechas: ['↓', '↓'],
          ),
        ),
        TemaAyuda(
          icono: CupertinoIcons.question_circle_fill,
          titulo: 'Primera apertura: permisos',
          parrafos: [
            'La primera vez que abres SPVI el sistema pregunta por los permisos que necesita: '
                'cámara para los códigos, notificaciones para los avisos y fotos para las imágenes '
                'de productos. SPVI no explica nada más: solo el cuadro del sistema.',
            'Si los negaste, puedes darlos en Ajustes → Permisos.',
          ],
        ),
      ],
    ),
    SeccionAyuda(
      icono: CupertinoIcons.cart_fill_badge_plus,
      titulo: 'Vender y cobrar',
      detalle: 'Turno, venta y caja',
      temas: [
        TemaAyuda(
          icono: CupertinoIcons.timer,
          titulo: 'El turno',
          parrafos: [
            'Abrir turno pide el fondo de cambio en efectivo. Con el turno abierto se vende y se '
                'registran movimientos de caja; cerrarlo hace el arqueo y compara el efectivo '
                'contado con el esperado.',
          ],
          esquema: EsquemaAyuda(
            cajas: ['Abrir turno (fondo de cambio)', 'Vender y mover caja', 'Cerrar turno y arqueo'],
            flechas: ['↓', '↓'],
          ),
        ),
        TemaAyuda(
          icono: CupertinoIcons.cart_fill_badge_plus,
          titulo: 'Registrar una venta',
          parrafos: [
            'Inicio → Nueva Venta. Eliges los productos o insumos, la cantidad y el método de cobro: '
                'efectivo o transferencia. Con transferencia se anota el número de la transacción.',
            'El stock se descuenta solo. Los productos elaborados descuentan los insumos que llevan.',
          ],
          esquema: EsquemaAyuda(
            cajas: ['Elegir productos', 'Cantidad y método de cobro', 'Venta registrada y stock descontado'],
            flechas: ['↓', '↓'],
          ),
        ),
        TemaAyuda(
          icono: CupertinoIcons.creditcard_fill,
          titulo: 'Cobros por transferencia',
          parrafos: [
            'Ajustes → Pago Electrónico guarda el teléfono y la tarjeta que se muestran al cobrar. '
                'El código de pago se genera en la ventana de cobro y se comparte por WhatsApp.',
          ],
        ),
        TemaAyuda(
          icono: CupertinoIcons.money_dollar_circle_fill,
          titulo: 'Movimientos de caja',
          parrafos: [
            'Desde el turno abierto puedes registrar entradas y salidas de efectivo con su motivo. '
                'Quedan en Registros → Caja con la hora y el importe.',
          ],
        ),
      ],
    ),
    SeccionAyuda(
      icono: CupertinoIcons.cube_box_fill,
      titulo: 'Inventario',
      detalle: 'Productos, insumos y precios',
      temas: [
        TemaAyuda(
          icono: CupertinoIcons.cube_box_fill,
          titulo: 'Cada fila del inventario',
          parrafos: [
            'El nombre, debajo cuánto puedes hacer con lo que tienes y su unidad (u, L, g, kg, '
                'cajas), y debajo la categoría. A la derecha, el precio de costo y el de venta: '
                'el de venta va en el color de la marca para no confundirlos.',
          ],
        ),
        TemaAyuda(
          icono: CupertinoIcons.exclamationmark_triangle_fill,
          titulo: 'Avisos de stock',
          parrafos: [
            'Cada producto o insumo tiene un mínimo. Cuando baja de ese mínimo se marca y aparece '
                'en las alertas de Inicio, para reponerlo antes de quedarte sin nada.',
          ],
        ),
      ],
    ),
    SeccionAyuda(
      icono: CupertinoIcons.doc_text_fill,
      titulo: 'Registros',
      detalle: 'Ventas, clientes y caja',
      temas: [
        TemaAyuda(
          icono: CupertinoIcons.checkmark_circle_fill,
          titulo: 'Seleccionar elementos',
          parrafos: [
            'En Registros todas las listas se pueden marcar con la casilla redonda: ventas, clientes '
                'fijos y movimientos de caja. Con algo marcado aparece la barra de selección para '
                'elegir todo o quitarla.',
          ],
        ),
        TemaAyuda(
          icono: CupertinoIcons.minus_circle_fill,
          titulo: 'Anular una venta',
          parrafos: [
            'Una venta se puede anular desde Registros → Ventas. Al anularla se devuelve el importe '
                'al total y el stock al inventario; la venta queda marcada como ANULADA, no se borra.',
          ],
        ),
      ],
    ),
    SeccionAyuda(
      icono: CupertinoIcons.person_badge_plus,
      titulo: 'Empleados',
      detalle: 'Las apps de tus empleados',
      temas: [
        TemaAyuda(
          icono: CupertinoIcons.person_badge_plus,
          titulo: 'Vincular la app de un empleado',
          parrafos: [
            'Cada empleado tiene su propia app vinculada a la tuya por la red local. Se vinculan con un '
                'código QR que se muestra una sola vez.',
            'Las ventas de las apps vinculadas llegan a la tuya y se suman a los registros. Si un empleado '
                'cambia de teléfono, hay que volver a vincularlo.',
          ],
        ),
        TemaAyuda(
          icono: CupertinoIcons.person_crop_circle,
          titulo: 'Quitar a un empleado',
          parrafos: [
            'Al desvincular una app, esa app deja de enviar datos. Lo que ya recibido se queda en tus '
                'registros; no se borra.',
          ],
        ),
      ],
    ),
    SeccionAyuda(
      icono: CupertinoIcons.gear_alt_fill,
      titulo: 'Tus datos y tu licencia',
      detalle: 'Respaldo, actualizar y soporte',
      temas: [
        TemaAyuda(
          icono: CupertinoIcons.arrow_down_doc_fill,
          titulo: 'Respaldo .spvi',
          parrafos: [
            'Ajustes → Exportar respaldo genera un archivo .spvi cifrado con toda la información. '
                'Guárdalo en un sitio seguro: es la única copia que puedes llevar a otro teléfono.',
          ],
          esquema: EsquemaAyuda(
            cajas: ['Datos de SPVI', 'Archivo .spvi cifrado', 'Otro teléfono con SPVI'],
            flechas: ['exportar ↓', 'importar ↓'],
          ),
        ),
        TemaAyuda(
          icono: CupertinoIcons.arrow_up_circle_fill,
          titulo: 'Actualizar',
          parrafos: [
            'Ajustes → Actualizar comprueba si hay una versión nueva. Mientras trabaja se ve una '
                'barra de progreso circular y al terminar dice: Actualizado, Estas al día o Sin '
                'conexión.',
          ],
        ),
        TemaAyuda(
          icono: CupertinoIcons.question_circle_fill,
          titulo: 'Ayuda y soporte',
          parrafos: [
            'Este manual está en Ajustes → Ayuda. En Ajustes → Soporte están los datos del '
                'desarrollador, el contacto y a qué se dedica SPVI.',
          ],
        ),
      ],
    ),
  ];

  /// Icono de una sección, para no repetir los `switch` por la interfaz.
  static IconData iconoSeccion(String titulo) {
    for (final seccion in secciones) {
      if (seccion.titulo == titulo) return seccion.icono;
    }
    return CupertinoIcons.question_circle_fill;
  }

  /// Temas de una sección.
  static List<TemaAyuda> temasPara(String titulo) {
    for (final seccion in secciones) {
      if (seccion.titulo == titulo) return seccion.temas;
    }
    return const [];
  }
}

class SeccionAyuda {
  final IconData icono;
  final String titulo;
  final String detalle;
  final List<TemaAyuda> temas;

  const SeccionAyuda({
    required this.icono,
    required this.titulo,
    required this.detalle,
    required this.temas,
  });
}

class TemaAyuda {
  final IconData icono;
  final String titulo;
  final List<String> parrafos;
  final EsquemaAyuda? esquema;

  const TemaAyuda({
    required this.icono,
    required this.titulo,
    required this.parrafos,
    this.esquema,
  });
}

class EsquemaAyuda {
  final List<String> cajas;
  final List<String> flechas;

  const EsquemaAyuda({required this.cajas, required this.flechas});
}

/// Pantalla del manual por secciones (petición 7).
class AyudaScreen extends StatelessWidget {
  const AyudaScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(largeTitle: Text('Ayuda')),
      child: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16.0),
          children: [
            SpviCard(
              child: Row(
                children: [
                  const Icon(CupertinoIcons.doc_text_fill, color: SpviColors.navy),
                  const SizedBox(width: 10.0),
                  Expanded(
                    child: Text(
                      'Manual de SPVI, por temas. Elige una sección y entra en el tema que necesites.',
                      style: TextStyle(fontSize: 13.0, color: CupertinoColors.secondaryLabel.resolveFrom(context)),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12.0),
            for (final seccion in AyudaContenido.secciones)
              Padding(
                padding: const EdgeInsets.only(bottom: 8.0),
                child: SpviSeccionAyuda(
                  icono: seccion.icono,
                  titulo: seccion.titulo,
                  detalle: seccion.detalle,
                  onTap: () {
                    Navigator.of(context).push(
                      CupertinoPageRoute(
                        builder: (_) => AyudaSeccionScreen(seccion: seccion),
                      ),
                    );
                  },
                ),
              ),
            const SizedBox(height: 16.0),
            Text(
              'SPVI funciona sin conexión. Los datos se quedan en el teléfono.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12.0, color: CupertinoColors.secondaryLabel.resolveFrom(context)),
            ),
          ],
        ),
      ),
    );
  }
}

/// Temas de una sección del manual.
class AyudaSeccionScreen extends StatelessWidget {
  final SeccionAyuda seccion;

  const AyudaSeccionScreen({super.key, required this.seccion});

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        largeTitle: null,
        middle: Text(seccion.titulo),
        previousPageTitle: 'Ayuda',
      ),
      child: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16.0),
          children: [
            for (final tema in seccion.temas) ...[
              SpviTemaAyuda(icono: tema.icono, titulo: tema.titulo, parrafos: tema.parrafos),
              if (tema.esquema != null)
                Padding(
                  padding: const EdgeInsets.only(top: 6.0),
                  child: SpviEsquema(cajas: tema.esquema!.cajas, flechas: tema.esquema!.flechas),
                ),
              const SizedBox(height: 10.0),
            ],
          ],
        ),
      ),
    );
  }
}
