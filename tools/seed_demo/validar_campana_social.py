"""Valida los archivos promocionales sin ejecutar ni modificar la app Android."""
import hashlib
import json
import re
import subprocess
from pathlib import Path

import imageio_ffmpeg
from PIL import Image

from campana_social import CAPTURAS, DURACIONES, SALIDA


def validar():
    imagenes = {}
    esperadas = {
        'collage_feed_1080x1350.png': (1080, 1350),
        'collage_feed_whatsapp.jpg': (1080, 1350),
        'super_collage_1080x1920.png': (1080, 1920),
        'super_collage_whatsapp.jpg': (1080, 1920),
        **{f'escenas_reel/{i:02d}.png': (1080, 1920) for i in range(1, 8)},
    }
    for nombre, medidas in esperadas.items():
        ruta = SALIDA / nombre
        with Image.open(ruta) as im:
            assert im.size == medidas, nombre
            assert im.mode == 'RGB', nombre
            im.verify()
        imagenes[nombre] = {'dimensiones': medidas, 'bytes': ruta.stat().st_size}

    video = SALIDA / 'reel_shalev_1080x1920.mp4'
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    # Decodifica todos los fotogramas y ambas pistas; -xerror convierte errores en fallos.
    proceso = subprocess.run([ffmpeg, '-hide_banner', '-xerror', '-i', str(video),
                              '-f', 'null', '-'], capture_output=True, text=True, check=True)
    info = proceso.stderr
    assert 'h264' in info and '1080x1920' in info and 'yuv420p' in info
    assert '24 fps' in info and 'Audio: aac' in info and 'stereo' in info
    frames, segundos = imageio_ffmpeg.count_frames_and_secs(str(video))
    assert frames == sum(DURACIONES)*24 == 720, frames
    assert abs(segundos - 30) < .05, segundos
    textos = (SALIDA/'reel_textos.srt').read_text()
    assert len(re.findall(r' --> ', textos)) == 7
    assert '00:00:30,000' in textos

    fuentes = {}
    for ruta in sorted(CAPTURAS.glob('shot_*.png')):
        fuentes[ruta.name] = hashlib.sha256(ruta.read_bytes()).hexdigest()
    informe = {
        'resultado': 'correcto',
        'imagenes': imagenes,
        'video': {'archivo': video.name, 'dimensiones': [1080, 1920], 'segundos': segundos,
                  'fps': 24, 'fotogramas_decodificados': frames, 'codec': 'H.264',
                  'formato_pixel': 'yuv420p', 'audio': 'AAC estéreo silencioso',
                  'decodificacion_completa_video_y_audio': True, 'bytes': video.stat().st_size},
        'srt': '7 bloques, 0–30 s',
        'sha256_capturas_originales': fuentes,
        'revision_visual': 'Collages y escenas 1, 5 y 7 revisados; texto sin desbordamientos.',
        'no_ejecutado': 'Tests Android ni pruebas en dispositivo; no se modificó la app.',
        'derechos_fotos': 'Pendiente de revisión por el titular antes de publicación pública.',
    }
    (SALIDA/'VALIDACION.json').write_text(json.dumps(informe, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps(informe['video'], ensure_ascii=False, indent=2))
    print('Imágenes y SRT: correctos. Capturas originales: SHA-256 registrado.')


if __name__ == '__main__':
    validar()
