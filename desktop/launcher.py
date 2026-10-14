"""Lanzador portable de SPVI para Windows: icono en el área de notificación, sin consola.

Al abrirse inicia el servidor local (https://spvi.io) y la pantalla principal en el navegador. El icono de la
barra de tareas (clic: Pantalla principal; clic derecho: menú) permite abrir Apps conectadas, Tiempo en línea y
Ajustes, reiniciar o salir. No modifica DNS, certificados ni firewall de Windows.
"""
import argparse
import json
import os
import subprocess
import sys
import threading
import time
import webbrowser
from pathlib import Path

ROUTES = (('Pantalla principal', 'inicio'), ('Apps conectadas', 'vinculadas'), ('Tiempo en línea', 'tiempo'), ('Ajustes', 'ajustes'))


def base_dir():
    return Path(sys.executable).parent if getattr(sys, 'frozen', False) else Path(__file__).parent


def resource(*parts):
    root = Path(getattr(sys, '_MEIPASS', Path(__file__).parent))
    return root.joinpath('spvi_web', *parts)


EXTRACTION_FILES = ('_sqlite3.pyd', 'sqlite3.dll')
EXTRACTION_TIMEOUT = 30


def extraction_ready(base, needed=EXTRACTION_FILES, timeout=EXTRACTION_TIMEOUT):
    """Espera a que el ejecutable termine de extraer sus archivos temporales.

    Al reiniciar en Windows el proceso nuevo puede arrancar antes de que la
    extracción termine; sin esta espera falla con «No module named '_sqlite3'».
    Devuelve True si están todos los archivos, False si se agota el tiempo.
    """
    end = time.monotonic() + timeout
    while True:
        if all((base / name).is_file() for name in needed):
            return True
        if time.monotonic() >= end:
            return False
        time.sleep(0.1)


def alert(message, title='SPVI'):
    """Aviso visible también sin consola (MessageBox con el icono de la app en Windows)."""
    if os.name == 'nt':
        try:
            import ctypes
            ctypes.windll.user32.MessageBoxW(None, message, title, 0x40 | 0x10000)  # MB_ICONINFORMATION | MB_SETFOREGROUND
            return
        except Exception:
            pass
    print(message, file=sys.stderr)


def parse(argv=None):
    parser = argparse.ArgumentParser(description='SPVI · app principal de escritorio')
    parser.add_argument('--self-test', action='store_true', help='Verificar el paquete sin abrir datos ni navegador')
    parser.add_argument('--reset-password', action='store_true', help='Quitar la clave de acceso (con SPVI cerrado)')
    parser.add_argument('--seed', action='store_true', help='Cargar los datos de prueba «Bodega La Esquina» (borra todo) y salir')
    parser.add_argument('--dev', action='store_true', help='Modo de desarrollo: muestra «Datos de prueba» en Ajustes')
    parser.add_argument('--autostart', action='store_true', help=argparse.SUPPRESS)
    parser.add_argument('--restart', action='store_true', help=argparse.SUPPRESS)
    parser.add_argument('--console', action='store_true', help='Sin icono de notificación (Ctrl+C para cerrar)')
    parser.add_argument('--lan-host', help='IPv4 privada de esta PC para atender apps secundarias')
    parser.add_argument('--lan-port', type=int, default=47811, help='Puerto TCP de sincronización')
    parser.add_argument('--dominio', default='spvi.io', help='Dominio local servido por HTTPS (spvi.io por defecto)')
    parser.add_argument('--local', action='store_true', help='Sin HTTPS: servir solo en http://127.0.0.1:8765')
    parser.add_argument('--https', action='store_true', help=argparse.SUPPRESS)  # compatibilidad: HTTPS ya es el modo por defecto
    parser.add_argument('--port', type=int, help='Puerto web; 443 por defecto (8765 con --local)')
    parser.add_argument('--browser', help='Ruta del navegador preferido')
    parser.add_argument('--data-dir', type=Path)
    parser.add_argument('--cert', type=Path, help='Certificado PEM del dominio local')
    parser.add_argument('--key', type=Path, help='Clave privada PEM')
    return parser, parser.parse_args(argv)


def open_existing(data):
    """Si ya hay una instancia, abre su pantalla principal en lugar de mostrar un error."""
    try:
        url = json.loads((data / 'instancia.json').read_text(encoding='utf-8'))['url']
        webbrowser.open(url + '#/inicio')
        return True
    except (OSError, ValueError, KeyError):
        return False


def build_tray(url, open_route, restart, quit_app):
    """Icono de notificación con el icono de SPVI. Devuelve ``None`` si el sistema no lo admite."""
    try:
        import pystray
        from PIL import Image
        image = Image.open(resource('static', 'icons', 'spvi-64.png'))
        items = [pystray.MenuItem(title, (lambda route: lambda icon, item: open_route(route))(route), default=(route == 'inicio'))
                 for title, route in ROUTES]
        items += [pystray.Menu.SEPARATOR, pystray.MenuItem('Reiniciar', lambda icon, item: restart()),
                  pystray.MenuItem('Salir', lambda icon, item: quit_app())]
        return pystray.Icon('SPVI', image, 'SPVI · ' + url.replace('https://', '').replace('http://', ''), pystray.Menu(*items))
    except Exception:
        return None


def main(argv=None):
    if sys.stdout is None or sys.stderr is None:
        # Ejecutable sin consola: los mensajes van a un registro (o se descartan) en lugar de fallar al escribir.
        sink = open(os.devnull, 'w', encoding='utf-8')
        sys.stdout = sys.stdout or sink; sys.stderr = sys.stderr or sink
    parser, args = parse(argv)
    base = getattr(sys, '_MEIPASS', None)
    if base is not None and not extraction_ready(Path(base)):
        alert('SPVI no terminó de preparar sus archivos temporales. Ciérralo si sigue abierto y ábrelo de nuevo.')
        return 1
    if args.self_test:
        from spvi_web.selftest import run
        try: run()
        except Exception as error:   # sin diálogos: el script de compilación solo mira el código de salida
            print('SPVI: prueba fallida: %s' % error, file=sys.stderr)
            return 1
        return 0
    if args.port is not None and not 1 <= args.port <= 65535:
        parser.error('Puerto fuera de rango.')
    data = args.data_dir or base_dir() / 'datos'
    data.mkdir(parents=True, exist_ok=True)
    from spvi_web.configuration import Config, acquire_lock
    if args.reset_password:
        if not (data / 'config.json').is_file(): parser.error('No existe una configuración en esa carpeta.')
        try:
            with acquire_lock(data):
                config = Config(data / 'config.json')
                config.data['password_hash'] = None
                from spvi_web.configuration import _write
                _write(config.path, config.data)
        except (ValueError, OSError) as error: parser.error(str(error))
        alert('Se quitó la clave de acceso. Puedes crear otra en Ajustes › Clave de acceso.\n'
              'La licencia y los datos no se modificaron.')
        return
    if not args.local:
        from spvi_web.tls import validate_certificate
        import socket
        if not args.cert or not args.key or not args.cert.is_file() or not args.key.is_file():
            parser.error('HTTPS requiere --cert y --key. Consulta el README (URL https://%s).' % args.dominio)
        try:
            validate_certificate(args.cert, args.key, args.dominio)
            if socket.gethostbyname(args.dominio) != '127.0.0.1':
                parser.error('Configura %s → 127.0.0.1 en esta PC (archivo hosts) antes de iniciar.' % args.dominio)
        except (ValueError, OSError) as error:
            parser.error(str(error))

    # Una sola instancia por carpeta de datos. Al reiniciar se espera a que la anterior termine.
    deadline = time.time() + (20 if args.restart else 0)
    while True:
        try:
            instance_lock = acquire_lock(data); break
        except ValueError as error:
            if time.time() < deadline: time.sleep(.5); continue
            if open_existing(data): return
            alert(str(error)); return

    config = Config(data / 'config.json')
    preferences = config.preferences()
    dev = args.dev or os.environ.get('SPVI_DEV') == '1'
    stop, restart_requested = threading.Event(), threading.Event()
    port = args.port or (8765 if args.local else 443)
    url = f'http://127.0.0.1:{port}' if args.local else 'https://' + args.dominio + (f':{port}' if port != 443 else '')

    if args.browser:
        if not Path(args.browser).is_file(): parser.error('No se encontró el navegador indicado.')
        browser = webbrowser.BackgroundBrowser(args.browser)
    else:
        browser = webbrowser

    def open_route(route='inicio'):
        threading.Thread(target=browser.open, args=(f'{url}/#/{route}',), daemon=True).start()

    def quit_app(): stop.set()

    def restart(): restart_requested.set(); stop.set()

    controls = dict(restart=lambda: threading.Timer(.5, restart).start(), quit=lambda: threading.Timer(.5, quit_app).start())
    tray = None if args.console or args.seed else build_tray(url, open_route, restart, quit_app)
    if tray is not None:
        try: tray.run_detached()
        except Exception: tray = None

    if args.autostart and preferences['retrasoSegundos']:
        if tray is not None: tray.title = 'SPVI · iniciando…'
        if stop.wait(preferences['retrasoSegundos']):
            if tray is not None: tray.stop()
            instance_lock.close(); return

    from cheroot.wsgi import Server
    from spvi_web.app import create_app
    identity_dir = Path(os.environ.get('LOCALAPPDATA', str(data))) / 'SPVI-Web' / 'identidad' if os.name == 'nt' else data
    app = create_app(data, config=config, secure=not args.local, hosts=[args.dominio, '127.0.0.1', 'localhost'], identity_dir=identity_dir, dev=dev, controls=controls)
    sync = app.extensions['sync']
    if args.seed:
        from spvi_web import seed
        print(seed.load(app.extensions['store']))
        instance_lock.close(); return
    lan = config.preferences()['red']
    for host, lan_port in ([(args.lan_host, args.lan_port)] if args.lan_host else []) + ([(lan['host'], lan['port'])] if lan else []):
        try: sync.start(host, lan_port); break
        except ValueError as error:
            if args.lan_host: alert('No se pudo iniciar la red local: ' + str(error))
    server = Server(('127.0.0.1', port), app, numthreads=8)
    if not args.local:
        from cheroot.ssl.builtin import BuiltinSSLAdapter
        server.ssl_adapter = BuiltinSSLAdapter(str(args.cert), str(args.key))
    try:
        server.prepare()
    except OSError:
        if tray is not None: tray.stop()
        instance_lock.close()
        alert(f'El puerto {port} está ocupado por otro programa. Cierra ese programa o inicia SPVI con --port.')
        return
    (data / 'instancia.json').write_text(json.dumps(dict(url=url, pid=os.getpid())), encoding='utf-8')
    worker = threading.Thread(target=server.serve, name='SPVI web', daemon=True)
    worker.start()
    if tray is not None:
        tray.title = 'SPVI · ' + url.replace('http://', '').replace('https://', '')
    if (not args.autostart and not args.restart) or (args.autostart and preferences['abrirAlIniciar']):
        open_route('inicio')
    elif tray is not None and args.autostart:
        try: tray.notify('SPVI está listo. Haz clic en el icono para abrir la pantalla principal.', 'SPVI')
        except Exception: pass
    if tray is None:
        print(f'SPVI: {url}\nDatos: {data}\nCtrl+C para cerrar. Cerrar el navegador no detiene SPVI.')
    try:
        while not stop.wait(1):
            pass
    except KeyboardInterrupt:
        pass
    finally:
        if tray is not None:
            try: tray.stop()
            except Exception: pass
        sync.stop()
        server.stop()
        try: (data / 'instancia.json').unlink()
        except OSError: pass
        instance_lock.close()
    if restart_requested.is_set():
        relaunch()


def relaunch():
    """Vuelve a abrir SPVI con los mismos argumentos (sin ``--autostart``), ya sin el bloqueo de esta instancia."""
    args = [a for a in sys.argv[1:] if a not in ('--autostart', '--restart')] + ['--restart']
    command = [sys.executable] + ([] if getattr(sys, 'frozen', False) else [str(Path(__file__).resolve())]) + args
    flags = 0x00000008 | 0x00000200 if os.name == 'nt' else 0  # DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP
    subprocess.Popen(command, close_fds=True, creationflags=flags, cwd=str(base_dir()))
    if os.name == 'nt':
        os._exit(0)  # la limpieza ya terminó; evita que el proceso viejo quede colgado tras el reinicio


if __name__ == '__main__':
    sys.exit(main())
