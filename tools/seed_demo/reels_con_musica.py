"""Dos variantes musicales del reel, con fragmentos destacados por energía y ataques.

Usa únicamente los audios aportados en promocion/audio; no descarga canciones.
Regenerar: python tools/seed_demo/reels_con_musica.py
Analizar sin renderizar: añadir --solo-analizar.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path

import imageio_ffmpeg
import numpy as np

from campana_social import RAIZ, SALIDA

FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
FPS = 24
DURACION = 30
SR = 11025
HOP = 220
PISTAS = [
    {'patron': 'Smokestack*.mp3', 'nombre': 'Smokestack Lightning',
     'artista': "Howlin’ Wolf", 'slug': 'smokestack_lightning',
     'url': 'https://youtu.be/VMUt8KdDtTY', 'fundido_visual': .16},
    {'patron': '7Horse*.mp3', 'nombre': 'Meth Lab Zoso Sticker',
     'artista': '7Horse', 'slug': 'meth_lab_zoso_sticker',
     'url': 'https://youtu.be/B45gd2AwOHw', 'fundido_visual': .08},
]


def ejecutar(argumentos, **opciones):
    return subprocess.run([FFMPEG, '-hide_banner', '-y', *argumentos], check=True, **opciones)


def analizar(ruta):
    resultado = ejecutar(['-v', 'error', '-i', str(ruta), '-ar', str(SR), '-ac', '1',
                          '-f', 'f32le', '-'], capture_output=True)
    audio = np.frombuffer(resultado.stdout, dtype='<f4')
    duracion = len(audio) / SR
    if duracion < 65:
        raise ValueError('Se necesita el audio completo, de al menos 65 segundos.')
    ventanas = np.lib.stride_tricks.sliding_window_view(audio, 1024)[::HOP]
    rms = np.sqrt(np.mean(ventanas**2, axis=1))
    espectro = np.abs(np.fft.rfft(ventanas, axis=1))
    flujo = np.maximum(np.diff(np.log1p(20*espectro), axis=0), 0).mean(axis=1)
    ataques = np.maximum(flujo - np.convolve(flujo, np.ones(51)/51, 'same'), 0)
    tasa = SR / HOP
    # Ventana continua de 30 s: evita la introducción y la cola. No se reordena la canción.
    puntuaciones = []
    for segundo in range(15, int(duracion) - DURACION - 12):
        a, b = int(segundo*tasa), int((segundo+DURACION)*tasa)
        puntuaciones.append((float(rms[a:b].mean()+.7*ataques[a:b].mean()), segundo))
    puntuacion, candidato = max(puntuaciones)
    tiempos = np.arange(len(ataques)) / tasa
    # Entra en un ataque próximo al comienzo de la ventana seleccionada.
    indices = np.where((tiempos >= candidato-.35) & (tiempos <= candidato+.35))[0]
    inicio = float(tiempos[indices[np.argmax(ataques[indices])]])
    cortes = [0]
    for objetivo in [4, 8, 12, 16, 20, 24]:
        relativos = tiempos - inicio
        indices = np.where(np.abs(relativos - objetivo) <= .30)[0]
        fuerza = ataques[indices] / (float(np.max(ataques[indices])) + 1e-9)
        ajuste = fuerza - .35 * np.abs(relativos[indices] - objetivo) / .30
        corte = round(float(relativos[indices[np.argmax(ajuste)]]) * FPS)
        cortes.append(corte)
    cortes.append(DURACION*FPS)
    assert all(b-a >= 3*FPS for a,b in zip(cortes, cortes[1:]))
    return {
        'inicio_audio_segundos': round(inicio, 6),
        'fin_audio_segundos': round(inicio+DURACION, 6),
        'duracion_original_segundos': round(duracion, 6),
        'cortes_fotogramas': cortes,
        'cortes_segundos': [round(c/FPS, 6) for c in cortes],
        'segundos_con_modo_claro': round((cortes[3]+720-cortes[6])/FPS, 3),
        'criterio': 'Ventana continua con mayor energía RMS y flujo espectral positivo, '
                    'sin introducción/cola; entrada en ataque cercano y cambios de escena '
                    'en ataques a ±0,30 s del guion. No identifica letras ni certifica un estribillo.',
        'puntuacion_energia': round(puntuacion, 6),
    }


def srt(tiempos, destino):
    original=(SALIDA/'reel_textos.srt').read_text()
    bloques=re.split(r'\n\s*\n', original.strip())
    def reloj(fotograma):
        ms=round(fotograma*1000/FPS)
        return f'{ms//3600000:02d}:{ms//60000%60:02d}:{ms//1000%60:02d},{ms%1000:03d}'
    nuevos=[]
    for i,bloque in enumerate(bloques):
        lineas=bloque.splitlines()
        nuevos.append(f'{i+1}\n{reloj(tiempos[i])} --> {reloj(tiempos[i+1])}\n'+ '\n'.join(lineas[2:]))
    destino.write_text('\n\n'.join(nuevos)+'\n')


def renderizar(pista, ruta, analisis):
    salida=SALIDA/f"reel_{pista['slug']}_1080x1920.mp4"
    with tempfile.TemporaryDirectory(prefix='shalev_musica_') as tmp:
        clips=[]
        for i,(a,b) in enumerate(zip(analisis['cortes_fotogramas'],analisis['cortes_fotogramas'][1:]),1):
            fotogramas=b-a
            segundos=fotogramas/FPS
            fundido=pista['fundido_visual']
            filtro=(f"scale=1124:1998,zoompan=z='1.01+0.025*on/{fotogramas}':"
                    f"x='iw/2-iw/zoom/2':y='ih/2-ih/zoom/2':d={fotogramas}:s=1080x1920:fps={FPS},"
                    f"fade=t=in:st=0:d={fundido},fade=t=out:st={segundos-fundido}:d={fundido},format=yuv420p")
            clip=Path(tmp)/f'{i}.mp4'
            ejecutar(['-v','error','-i',str(SALIDA/'escenas_reel'/f'{i:02d}.png'),
                      '-vf',filtro,'-frames:v',str(fotogramas),'-an','-c:v','libx264',
                      '-preset','fast','-crf','21','-threads','2',str(clip)])
            clips.append(clip)
            print(f"{pista['nombre']}: escena {i}/7",flush=True)
        lista=Path(tmp)/'clips.txt'
        lista.write_text(''.join(f"file '{p.as_posix()}'\n" for p in clips))
        # Normalización en dos pasadas: volumen comparable y margen para la codificación AAC.
        filtros='loudnorm=I=-16:TP=-2:LRA=9'
        medicion=ejecutar(['-ss',str(analisis['inicio_audio_segundos']),'-i',str(ruta),
                          '-t','30','-af',filtros+':print_format=json','-f','null','-'],
                          capture_output=True,text=True)
        medido=json.loads(re.findall(r'\{[^{}]+\}',medicion.stderr)[-1])
        normalizar=(f"{filtros}:measured_I={medido['input_i']}:measured_TP={medido['input_tp']}:"
                    f"measured_LRA={medido['input_lra']}:measured_thresh={medido['input_thresh']}:"
                    f"offset={medido['target_offset']}:linear=true,"
                    'afade=t=in:st=0:d=0.08,afade=t=out:st=29.1:d=0.9')
        ejecutar(['-v','error','-f','concat','-safe','0','-i',str(lista),
                  '-ss',str(analisis['inicio_audio_segundos']),'-i',str(ruta),
                  '-map','0:v:0','-map','1:a:0','-c:v','copy','-af',normalizar,
                  '-c:a','aac','-b:a','192k','-ar','48000','-ac','2','-t','30',
                  '-metadata',f"title=SHALEV — {pista['nombre']}",
                  '-metadata',f"comment=Montaje de capturas; audio aportado por el usuario: {pista['url']}",
                  '-movflags','+faststart',str(salida)])
        analisis['normalizacion']={'objetivo_LUFS':-16,'limite_dBTP':-2,
                                  'entrada_medida':medido,'fundido_entrada_s':.08,'fundido_salida_s':.9}
    srt(analisis['cortes_fotogramas'],salida.with_suffix('.srt'))
    return salida


def validar(ruta):
    proceso=ejecutar(['-xerror','-i',str(ruta),'-af','volumedetect','-f','null','-'],
                    capture_output=True,text=True)
    frames, segundos=imageio_ffmpeg.count_frames_and_secs(str(ruta))
    assert frames == 720 and abs(segundos-30)<.05
    for cadena in ['h264','1080x1920','24 fps','yuv420p','Audio: aac','48000 Hz','stereo']:
        assert cadena in proceso.stderr, cadena
    pico=float(re.findall(r'max_volume: ([\-\d.]+) dB',proceso.stderr)[-1])
    assert -30 < pico < -.2, pico
    return {'decodificacion_completa_audio_video':True,'fotogramas':frames,'duracion_s':segundos,
            'maximo_audio_decodificado_dBFS':pico,'bytes':ruta.stat().st_size,
            'sha256':hashlib.sha256(ruta.read_bytes()).hexdigest()}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--solo-analizar',action='store_true')
    args=parser.parse_args()
    informe=[]
    for pista in PISTAS:
        rutas=list((RAIZ/'promocion/audio').glob(pista['patron']))
        if len(rutas)!=1:
            raise ValueError(f"Se esperaba un audio para {pista['patron']}; encontrados: {len(rutas)}")
        ruta=rutas[0]
        analisis=analizar(ruta)
        print(pista['nombre'],json.dumps(analisis,ensure_ascii=False,indent=2),flush=True)
        detalle={'cancion':pista['nombre'],'artista':pista['artista'],'url':pista['url'],
                 'fuente':str(ruta.relative_to(RAIZ)),
                 'sha256_fuente':hashlib.sha256(ruta.read_bytes()).hexdigest(),**analisis}
        if not args.solo_analizar:
            salida=renderizar(pista,ruta,detalle)
            detalle['archivo']=salida.name
            detalle['validacion']=validar(salida)
        informe.append(detalle)
    if not args.solo_analizar:
        (SALIDA/'VARIANTES_MUSICA.json').write_text(json.dumps(informe,ensure_ascii=False,indent=2)+'\n')
        print('Ambos reels musicales validados.',flush=True)


if __name__=='__main__':
    main()
