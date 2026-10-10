"""Lanzador portable. No modifica DNS, certificados ni firewall de Windows."""
import argparse
import os
import getpass
import json
import secrets
import socket
import sys
import threading
import webbrowser
from pathlib import Path
from cheroot.wsgi import Server
from cheroot.ssl.builtin import BuiltinSSLAdapter
from werkzeug.security import generate_password_hash
from spvi_web.app import create_app
from spvi_web.tls import validate_certificate


def main():
    parser = argparse.ArgumentParser(description='SPVI · principal de escritorio (versión inicial)')
    parser.add_argument('--reset-password', action='store_true', help='Cambiar la clave desde esta consola; requiere cerrar SPVI')
    parser.add_argument('--self-test', action='store_true', help='Verificar el paquete sin abrir datos ni navegador')
    parser.add_argument('--lan-host', help='IPv4 privada de esta PC para atender secundarias Android')
    parser.add_argument('--lan-port', type=int, default=47811, help='Puerto TCP de sincronización, distinto del puerto web')
    parser.add_argument('--local', action='store_true', help='Usar HTTP local en vez del dominio HTTPS')
    parser.add_argument('--port', type=int, help='Puerto; 443 para HTTPS o 8765 en modo local')
    parser.add_argument('--browser', help='Ruta del navegador preferido')
    parser.add_argument('--data-dir', type=Path)
    parser.add_argument('--cert', type=Path, help='Certificado PEM de spvi.minegocio.cu')
    parser.add_argument('--key', type=Path, help='Clave privada PEM')
    args = parser.parse_args()
    if args.self_test:
        from spvi_web.selftest import run
        run()
        return
    if args.port is not None and not 1 <= args.port <= 65535:
        parser.error('Puerto fuera de rango.')
    base = Path(sys.executable).parent if getattr(sys, 'frozen', False) else Path(__file__).parent
    data = args.data_dir or base / 'datos'
    if args.reset_password:
        from spvi_web.configuration import acquire_lock, reset_password
        if not (data/'config.json').is_file(): parser.error('No existe una configuración en esa carpeta.')
        try:
            with acquire_lock(data):
                password=getpass.getpass('Nueva clave (mínimo 12 caracteres): ')
                if password!=getpass.getpass('Repite la clave: '): parser.error('Las claves no coinciden.')
                reset_password(data/'config.json',password)
        except (ValueError,OSError) as error: parser.error(str(error))
        print('Clave actualizada. La licencia, datos y secretos de vinculación no se modificaron.')
        return
    if not args.local:
        if not args.cert or not args.key or not args.cert.is_file() or not args.key.is_file():
            parser.error('HTTPS requiere --cert y --key. Consulta README o usa --local para evaluación.')
        try:
            validate_certificate(args.cert, args.key)
        except ValueError as error:
            parser.error(str(error))
        try:
            if socket.gethostbyname('spvi.minegocio.cu') != '127.0.0.1':
                parser.error('Configura spvi.minegocio.cu → 127.0.0.1 en esta PC antes de iniciar.')
        except OSError:
            parser.error('El dominio local no se puede resolver. Consulta README.')
    data.mkdir(parents=True, exist_ok=True)
    from spvi_web.configuration import acquire_lock
    try: instance_lock=acquire_lock(data)
    except ValueError as error: parser.error(str(error))
    config = data / 'config.json'
    if not config.exists():
        password = getpass.getpass('Crea una clave de acceso (mínimo 12 caracteres): ')
        if len(password) < 12 or len(password) > 256 or password != getpass.getpass('Repite la clave: '):
            parser.error('Las claves no coinciden o no tienen la longitud requerida.')
        with config.open('x', encoding='utf-8') as file:
            json.dump({'password_hash': generate_password_hash(password), 'secret': secrets.token_hex(32)}, file)
        config.chmod(0o600)
    settings = json.loads(config.read_text(encoding='utf-8'))
    # La identidad Windows sobrevive a mover o volver a descargar el .exe.
    identity_dir = Path(os.environ.get('LOCALAPPDATA', str(data))) / 'SPVI-Web' / 'identidad' if os.name == 'nt' else data
    app = create_app(data, settings['password_hash'], settings['secret'], secure=not args.local, identity_dir=identity_dir)
    sync = app.extensions['sync']
    if args.lan_host:
        try: sync.start(args.lan_host, args.lan_port)
        except ValueError as error: parser.error(str(error))
    port = args.port or (8765 if args.local else 443)
    url = f'http://127.0.0.1:{port}' if args.local else 'https://SPVI.minegocio.cu' + (f':{port}' if port != 443 else '')
    server = Server(('127.0.0.1', port), app, numthreads=4)
    if not args.local:
        server.ssl_adapter = BuiltinSSLAdapter(str(args.cert), str(args.key))
    if args.browser:
        browser_path = Path(args.browser)
        if not browser_path.is_file():
            parser.error('No se encontró el navegador indicado.')
        browser = webbrowser.BackgroundBrowser(str(browser_path))
    else:
        browser = webbrowser.get()
    try:
        server.prepare()
        threading.Thread(target=browser.open, args=(url,), daemon=True).start()
        print(f'SPVI: {url}\nDatos: {data}\nCtrl+C para cerrar. Cerrar el navegador no detiene SPVI.')
        server.serve()
    except KeyboardInterrupt:
        pass
    finally:
        sync.stop()
        server.stop()
        instance_lock.close()


if __name__ == '__main__':
    main()
