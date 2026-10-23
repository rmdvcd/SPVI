# SHALEV · Campaña social

Material elaborado el 23 de octubre de 2026 con **capturas reales ya aportadas**, conservadas en `promocion/capturas/`. No se modificó la app ni se generaron pantallas ficticias. No había teléfono, emulador ni ADB disponibles para hacer nuevas capturas o grabar una sesión interactiva.

## Variantes con música

Se añadieron **dos reels con soundtrack**, fragmentos de alta energía y cambios de escena
ajustados a ataques musicales. Véase [`MUSICA.md`](MUSICA.md) para intervalos, derechos y regeneración:

- [`Smokestack Lightning`](reel_smokestack_lightning_1080x1920.mp4), 30 s.
- [`Meth Lab Zoso Sticker — 7Horse`](reel_meth_lab_zoso_sticker_1080x1920.mp4), 30 s.

El reel original de la tabla siguiente permanece sin música. Los nuevos videos incluyen sus
propios SRT y el informe `VARIANTES_MUSICA.json`.

## Entregables originales

| Archivo | Formato y uso |
|---|---|
| `super_collage_1080x1920.png` | Pieza principal, vertical 9:16. Historias de Facebook/Instagram y estados de WhatsApp. Tres pantallas claras y dos detalles oscuros. |
| `collage_feed_1080x1350.png` | Collage 4:5 para publicaciones de Facebook e Instagram. Tres pantallas claras y dos detalles de gráficos/registros en oscuro. |
| `super_collage_whatsapp.jpg` | Copia JPG del supercollage, más ligera. |
| `collage_feed_whatsapp.jpg` | Copia JPG del collage de publicación, más ligera. |
| `reel_shalev_1080x1920.mp4` | Reel vertical, 30 segundos, 1080×1920, 24 fps, H.264/yuv420p y AAC estéreo silencioso. Compatible con Facebook, Instagram y WhatsApp. |
| `TEXTOS_PARA_PUBLICAR.md` | Descripciones para cada formato, versión corta para WhatsApp y texto alternativo. |
| `reel_textos.srt` | Texto temporizado opcional para accesibilidad o una edición posterior. No representa una voz grabada. |
| `escenas_reel/01.png` … `07.png` | Siete escenas del reel, reutilizables como tarjetas verticales. |
| `VALIDACION.json` | Dimensiones, hashes de fuentes y comprobaciones del render. |

Las escenas y los collages tienen textos integrados; no necesitan sonido para entenderse. El MP4 **original** (`reel_shalev_1080x1920.mp4`) incluye una pista de audio **silenciosa**, no música ni locución. Puedes añadir música desde la biblioteca autorizada de la plataforma al publicar. No se afirma que sea una grabación de uso: es un **montaje animado de capturas** con fundidos y acercamientos suaves. No hay clics o pulsaciones simulados.

## Selección de capturas

Los originales están un nivel arriba, en `../capturas/`:

| Captura | Tema | Motivo de selección |
|---|---|---|
| `shot_inicio_claro.png` | Claro | Vista general y gráfico de ventas. Collages y escenas 1/7. |
| `shot_inventario_claro.png` | Claro | Productos, fotos, búsqueda, precios y existencias. Collages y escenas 2/7. |
| `shot_servicios_claro.png` | Claro | Catálogo y categorías de servicios. Collages y escenas 3/7. |
| `shot_inicio.png` | Oscuro | Gráfico y ganancia neta. Detalle en collages y escena 5. |
| `shot_registros.png` | Oscuro | Historial tabular de ventas e importes. Detalle en collages y escena 5. |
| `shot_carrito.png` | Oscuro | Cantidades e importes de la nueva venta. Escena 4. |
| `shot_pago.png` | Oscuro | Comprobante y desglose de la venta. Escena 4. |
| `shot_vinculadas.png` | Oscuro | Equipo conectado por red local. Escena 6. |

Se conservaron también `shot_inventario.png` y `shot_servicios.png` como alternativas oscuras, pero no se usan para mantener el predominio claro.

Se recortan las barras de estado y navegación del sistema. Las capturas completas mantienen su proporción; las pequeñas vistas de análisis son **recortes de detalle**, no pantallas nuevas. No se generaron tablas de exportación inexistentes: se muestra la lista real de Registros. Los textos editoriales describen funciones documentadas; no todo lo mencionado aparece en estas capturas.

## Guion del reel

| Tiempo | Escena | Tema |
|---|---|---|
| 0–4 s | Inicio claro | Tu negocio, sin internet |
| 4–8 s | Inventario claro | Fotos, existencias, insumos y alertas |
| 8–12 s | Servicios claro | Catálogo de servicios |
| 12–16 s | Carrito y comprobante oscuros | Efectivo y transferencias |
| 16–20 s | Inicio y registros oscuros | Gráficos, historial y reportes |
| 20–24 s | Apps vinculadas oscuro | Empleados por Wi-Fi local |
| 24–30 s | Tres pantallas claras y contacto | Datos cifrados, sin anuncios y llamada a la acción |

Predomina el modo claro: 18 de 30 segundos. Las dos piezas estáticas muestran tres pantallas claras y dos detalles oscuros. El texto esencial del reel se mantiene fuera de los extremos superior e inferior; comprueba igualmente la previsualización de cada plataforma, ya que sus botones pueden variar.

## Publicar

1. Facebook/Instagram, publicación: usar el PNG 4:5 y la descripción 1.
2. Historias o estados: usar el supercollage vertical (JPG si se busca menos peso).
3. Reel: subir el MP4, elegir la escena 7 como portada y copiar la descripción 2.
4. WhatsApp: compartir el MP4 o JPG con la descripción 3. Enviar como documento si se necesita conservar la calidad original.
5. Si añades música, verifica sus permisos para cada plataforma; la licencia de una biblioteca de Instagram no implica permiso para distribuirla por WhatsApp.

El texto y las piezas usan el contacto público de soporte de `DeveloperContact.kt`, no el teléfono de ejemplo del pago electrónico. `cu.shalev.app` es el identificador de paquete, no una web de descarga verificada. La llamada a la acción dirige a WhatsApp.

## Antes de una campaña pública o pagada

**Revisar los derechos de las miniaturas fotográficas de los artículos y servicios.** Son parte de las capturas aportadas. El inventario de procedencia está en [`tools/seed_demo/CREDITOS.md`](../../tools/seed_demo/CREDITOS.md); varias entradas solo enlazan artículos de Wikipedia y no identifican la licencia/autor del archivo fotográfico. Este montaje no añade una licencia nueva ni resuelve esa verificación. Si no se puede confirmar una foto, reemplazarla por una propia en los datos de demostración y volver a capturar. Los importes, nombres, productos, ventas y empleados mostrados son de demostración, no resultados ni testimonios.

La sincronización entre teléfonos requiere estar en la misma red local y su cupo depende de la licencia. El uso diario no requiere internet, pero descargar/actualizar sí. Registrar una transferencia no verifica un pago con el banco. No se promete sincronización remota, nube ni conciliación bancaria automática.

## Regenerar

Desde la raíz, con Python 3.11+ y fuentes DejaVu Sans (Linux) o Arial (Windows/macOS):

```bash
python -m venv .venv
# Linux/macOS; en Windows usar .venv\Scripts\python.exe
.venv/bin/python -m pip install -r tools/seed_demo/requirements_promocion.txt
.venv/bin/python tools/seed_demo/campana_social.py --video
.venv/bin/python tools/seed_demo/validar_campana_social.py
```

Sin `--video`, genera solo imágenes. FFmpeg se obtiene mediante `imageio-ffmpeg`; no hace falta un ejecutable global. El script reutiliza las capturas existentes y obtiene el teléfono del archivo fuente de contacto. Los ficheros temporales del render se eliminan al terminar.

No se ejecutaron tests Android: no hay cambios de código de la app. Se validaron los formatos y dimensiones de las imágenes y la decodificación íntegra del MP4; el detalle está en `VALIDACION.json`.
