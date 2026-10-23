# Dos variantes del reel con soundtrack

Se reutilizan las siete escenas de capturas reales y los mensajes de la campaña. No se ha modificado la app. El reel original sin música se conserva.

## Archivos

| Variante | Video | Fragmento de la canción original |
|---|---|---|
| Smokestack Lightning — Howlin’ Wolf | `reel_smokestack_lightning_1080x1920.mp4` | 00:15,106 → 00:45,106 |
| Meth Lab Zoso Sticker — 7Horse | `reel_meth_lab_zoso_sticker_1080x1920.mp4` | 01:53,043 → 02:23,043 |

Ambos: **30 segundos, 1080×1920, 24 fps, H.264/yuv420p, audio AAC estéreo a 48 kHz y 192 kb/s**, preparados para carga web con `faststart`.

Cada video tiene su propio `.srt` con tiempos adaptados a sus cortes. Los mensajes promocionales están integrados en las imágenes; el SRT es opcional y no es una transcripción de las canciones ni implica una locución.

## Selección de los highlights

Se analizaron únicamente los MP3 aportados por el usuario en `promocion/audio/`. Los metadatos de los archivos identifican las fuentes solicitadas:

- Smokestack Lightning: https://youtu.be/VMUt8KdDtTY
- Meth Lab Zoso Sticker: https://youtu.be/B45gd2AwOHw

La selección identifica una **ventana continua de alta energía y presencia de ataques musicales**, combinando RMS y flujo espectral positivo. Se excluyen los primeros 15 segundos y la cola. La entrada se ajusta a un ataque próximo. No se identifica automáticamente la letra ni se afirma que el fragmento sea un estribillo: el criterio es acústico, no una valoración basada en escucha humana.

Los seis cambios de escena se ajustan a ataques musicales detectados en un margen de ±0,30 segundos respecto al guion original. Se cuantizan a fotogramas de 24 fps. Los fragmentos musicales no se aceleran ni se reordenan.

- **Smokestack Lightning:** transiciones más suaves, fundidos visuales de 0,16 s; 18 s con pantallas claras.
- **Meth Lab Zoso Sticker:** transiciones más rápidas, fundidos visuales de 0,08 s; aproximadamente 17,7 s con pantallas claras.
- Ambas: entrada de audio de 0,08 s y salida de 0,9 s para evitar un corte brusco.
- Normalización en dos pasadas, objetivo −16 LUFS, límite −2 dBTP antes de AAC. La medición del archivo final, incluida la comprobación de picos, está en `VARIANTES_MUSICA.json`.

No se añadieron clics falsos, una voz sintética ni simulaciones de interacción. Son montajes animados de capturas reales.

## Descripciones

Se pueden reutilizar la descripción del reel y la versión corta para WhatsApp de `TEXTOS_PARA_PUBLICAR.md`, porque las funciones y las escenas no cambian. Si quieres identificar la música en el texto, puedes añadir:

- Variante 1: «Música: Smokestack Lightning — Howlin’ Wolf».
- Variante 2: «Música: Meth Lab Zoso Sticker — 7Horse».

**Dar crédito no sustituye una licencia.** Estos montajes no conceden derechos sobre las canciones. Antes de publicar, especialmente en anuncios o promociones comerciales, comprueba que tienes autorización para sincronizar y distribuir la música en Facebook, Instagram y WhatsApp. Las plataformas pueden silenciar o bloquear el audio aunque el archivo se reproduzca correctamente. Se conserva la versión sin música como alternativa.

Sigue pendiente también la revisión de derechos de las miniaturas fotográficas del catálogo documentada en el README de la campaña.

## Regenerar y validar

Con Python 3.11+ y el entorno de la campaña:

```bash
.venv/bin/python -m pip install -r tools/seed_demo/requirements_promocion.txt
.venv/bin/python tools/seed_demo/reels_con_musica.py --solo-analizar
.venv/bin/python tools/seed_demo/reels_con_musica.py
```

La última orden renderiza y valida los dos MP4, genera sus SRT y escribe `VARIANTES_MUSICA.json` con intervalos, cortes, normalización, hashes de fuentes y resultados de decodificación. Los originales se recuperaron de `origin/main`, commit `aa108c58a8eb8a3f6d6e1ffcd2ecbb8db32ac899`, sin cambiar la rama de trabajo.
