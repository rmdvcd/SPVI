"""Interfaz HTTP local (clave de acceso opcional). No expone el protocolo de sincronización Android."""
import secrets
import time
from pathlib import Path
from flask import Flask, abort, jsonify, render_template, request, session, send_file
from io import BytesIO
from .exports import export, MIMES
from .backups import backup, restore, MAX_BYTES
from .store import Store
from .secrets import SecretBox
from .licensing.client import LicenseClient
from .business import Business
from .sync.server import SyncManager
from . import android_backup, VERSION
from .configuration import Config


def create_app(data_dir, password_hash=None, secret=None, secure=False, hosts=None, identity_dir=None, config=None,
               dev=False, controls=None):
    """``config``: :class:`Config` de la carpeta de datos (clave y preferencias). Sin ella se usa una en memoria con
    ``password_hash`` (``None`` = sin clave). ``controls``: acciones del icono de notificación (reiniciar, salir)."""
    config = config or Config(password_hash=password_hash, secret=secret)
    secret = config.secret
    app = Flask(__name__)
    app.config['ACCESS_VERSION']=config.access_version()
    app.config.update(DEV=bool(dev), STARTED_AT=int(time.time()*1000))
    app.config.update(SECRET_KEY=secret, MAX_CONTENT_LENGTH=android_backup.MAX_BYTES + 65536, SESSION_COOKIE_HTTPONLY=True,
                      SESSION_COOKIE_SAMESITE='Strict', SESSION_COOKIE_SECURE=secure,
                      TRUSTED_HOSTS=hosts or ['127.0.0.1', 'localhost', 'spvi.io'])
    Path(data_dir).mkdir(parents=True, exist_ok=True)
    box = SecretBox(secret)
    identity = Path(identity_dir) if identity_dir else Path(data_dir)
    identity.mkdir(parents=True, exist_ok=True)
    licensing = LicenseClient(identity, box)
    store = Business(Store(Path(data_dir) / 'spvi.sqlite3'), licensing, box)
    sync = SyncManager(store)
    app.extensions.update(store=store, licensing=licensing, sync=sync, config=config, controls=controls or {})

    def grant():
        session.update(authenticated=True, credential_version=config.access_version())

    @app.before_request
    def protect():
        access_version = config.access_version()
        app.config['ACCESS_VERSION'] = access_version
        if session.get('authenticated') and session.get('credential_version')!=access_version: session.clear()
        if not config.password_hash and not session.get('authenticated'):
            grant()
        if request.method == 'POST':
            if request.path not in ('/api/restore', '/api/android/import', '/api/license/revocations', '/api/catalog', '/api/photo') and (request.content_length or 0) > 16384:
                abort(413)
            token = request.headers.get('X-CSRF-Token', '')
            if not token or not secrets.compare_digest(token, session.get('csrf', '')):
                abort(403)
        if request.path.startswith('/api/') and not session.get('authenticated'):
            abort(401)

    @app.after_request
    def headers(response):
        response.headers['Cache-Control'] = 'no-store'
        response.headers['X-Content-Type-Options'] = 'nosniff'
        response.headers['X-Frame-Options'] = 'DENY'
        response.headers['Content-Security-Policy'] = "default-src 'self'; img-src 'self' blob:; script-src 'self'; style-src 'self'; font-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
        return response

    @app.get('/')
    def index():
        session.setdefault('csrf', secrets.token_urlsafe(32))
        return render_template('index.html', csrf=session['csrf'], authenticated=session.get('authenticated', False),
                               theme=config.preferences()['tema'], protected=bool(config.password_hash), dev=app.config['DEV'],
                               version=VERSION)

    @app.get('/favicon.ico')
    def favicon():
        return app.send_static_file('icons/spvi.ico')

    @app.post('/login')
    def login():
        data = request.get_json(silent=True)
        if not isinstance(data, dict):
            return jsonify(error='Datos no válidos.'), 400
        password = data.get('password', '')
        if not isinstance(password, str) or len(password) > 256 or not config.check(password):
            return jsonify(error='Clave incorrecta.'), 401
        session.clear()
        session.update(csrf=secrets.token_urlsafe(32)); grant()
        return jsonify(ok=True)

    @app.post('/api/logout')
    def logout():
        session.clear()
        return jsonify(ok=True)

    from . import autostart

    def app_state():
        return dict(version=VERSION, dev=app.config['DEV'], protegida=bool(config.password_hash),
                    preferencias=config.preferences(), inicioDisponible=autostart.supported(),
                    controles=sorted((controls or {}).keys()))

    @app.get('/api/app')
    def app_info(): return jsonify(app_state())

    @app.post('/api/app/preferences')
    def app_preferences():
        data = request.get_json(silent=True)
        try:
            if not isinstance(data, dict): raise ValueError('Datos no válidos.')
            before = config.preferences()
            after = dict(before, **data)
            config.update_preferences(data)
            if after.get('inicioConWindows') != before['inicioConWindows']:
                try: autostart.apply(after['inicioConWindows'])
                except ValueError:
                    config.update_preferences(dict(inicioConWindows=before['inicioConWindows'])); raise
            return jsonify(app_state())
        except (ValueError, TypeError) as error: return jsonify(error=str(error)), 400

    @app.post('/api/app/access')
    def app_access():
        data = request.get_json(silent=True)
        try:
            if not isinstance(data, dict): raise ValueError('Datos no válidos.')
            new = data.get('nueva') or None
            if new is not None and new != data.get('repite'): raise ValueError('Las claves no coinciden.')
            config.set_password(data.get('actual', ''), new)
            grant()
            return jsonify(app_state())
        except (ValueError, TypeError) as error: return jsonify(error=str(error)), 400

    @app.post('/api/app/<action>')
    def app_control(action):
        if action not in (controls or {}): return jsonify(error='Acción no disponible.'), 400
        controls[action]()
        return jsonify(ok=True)

    @app.get('/api/uptime')
    def uptime():
        state = sync.uptime()
        names = {e['id']: e for e in store.employees()}
        apps = []
        for id, e in names.items():
            if not e['active'] and id not in state['apps']: continue
            item = dict(id=id, nombre=e['name'], activa=e['active'], ultimaSincronizacion=e['last_sync'],
                        version=e['version'], **state['apps'].get(id, dict(connected=False, since=None, current=0, total=0,
                                                                           last_seen=None, last_sync=None)))
            apps.append(item)
        return jsonify(ahora=int(time.time()*1000), app=dict(since=app.config['STARTED_AT']), lan=state['lan'], apps=apps)

    @app.get('/api/catalogo')
    def catalog_snapshot(): return jsonify(store.catalog())

    @app.get('/api/registros/<kind>')
    def ledger(kind):
        try:
            def optional(name):
                value = request.args.get(name)
                return int(value) if value not in (None, '') else None
            return jsonify(store.ledger(kind, request.args.get('q', ''), int(request.args.get('page', '1')),
                                        int(request.args.get('size', '50')), optional('desde'), optional('hasta'),
                                        request.args.get('ids') == '1'))
        except ValueError as error: return jsonify(error=str(error) if 'válid' in str(error) else 'Consulta no válida.'), 400

    @app.post('/api/seed')
    def load_seed():
        if not app.config['DEV']: abort(404)
        data = request.get_json(silent=True)
        if not isinstance(data, dict) or data.get('confirm') != 'BORRAR': return jsonify(error='Confirma que quieres borrar todo.'), 400
        from . import seed
        try:
            sync.stop()
            return jsonify(seed.load(store))
        except ValueError as error: return jsonify(error=str(error)), 400

    @app.get('/api/dashboard')
    def dashboard():
        try:
            return jsonify(store.dashboard(request.args.get('days', '30')))
        except ValueError as error:
            return jsonify(error=str(error)), 400

    @app.post('/api/export')
    def download_export():
        data = request.get_json(silent=True)
        if not isinstance(data, dict):
            return jsonify(error='Datos no válidos.'), 400
        try:
            scope, fmt = data.get('scope'), data.get('format')
            if not isinstance(scope, str) or not isinstance(fmt, str):
                raise ValueError('Formato no válido.')
            content, extension = export(store, scope, fmt, data.get('comment', ''), data.get('query', ''), data.get('ids'))
        except ValueError as error:
            return jsonify(error=str(error)), 400
        return send_file(BytesIO(content), mimetype=MIMES[extension], as_attachment=True,
                         download_name=f'SPVI_{scope}.{extension}')

    @app.post('/api/backup')
    def download_backup():
        data = request.get_json(silent=True)
        if not isinstance(data, dict): return jsonify(error='Datos no válidos.'), 400
        try:
            content = store.export_backup(data.get('password'))
        except ValueError as error:
            return jsonify(error=str(error)), 400
        return send_file(BytesIO(content), mimetype='application/octet-stream', as_attachment=True,
                         download_name='SPVI_respaldo.spvidesk')

    @app.post('/api/restore')
    def restore_backup():
        if request.form.get('confirm') != 'RESTAURAR':
            return jsonify(error='Confirma que quieres reemplazar los datos.'), 400
        file = request.files.get('file')
        if file is None: return jsonify(error='Selecciona un respaldo.'), 400
        try:
            sync.stop()
            store.import_backup(file.read(MAX_BYTES + 1), request.form.get('password'))
        except (ValueError,KeyError,TypeError) as error:
            return jsonify(error=str(error) if isinstance(error,ValueError) else 'Datos no válidos.'), 400
        return jsonify(ok=True)

    @app.get('/api/license')
    def license_status(): return jsonify(licensing.status())

    @app.post('/api/license/<action>')
    def license_action(action):
        data = request.get_json(silent=True)
        if not isinstance(data, dict): return jsonify(error='Datos no válidos.'), 400
        try:
            if action == 'request': return jsonify(licensing.request_message(data))
            if action == 'activate': return jsonify(licensing.activate(data.get('code')))
            if action == 'revocations': return jsonify(licensing.import_revocations(data))
            raise ValueError('Acción no válida.')
        except (ValueError, KeyError, TypeError) as error:
            return jsonify(error=str(error) if isinstance(error, ValueError) else 'Datos no válidos.'), 400

    @app.post('/api/license/qr')
    def license_qr():
        """QR del enlace de WhatsApp con la solicitud: se escanea con la cámara del teléfono."""
        from .licensing.client import DEVELOPER
        data = request.get_json(silent=True)
        url = data.get('url') if isinstance(data, dict) else None
        if not isinstance(url, str) or len(url) > 6000 or not url.startswith('https://wa.me/%s?text=' % DEVELOPER):
            return jsonify(error='Prepara primero la solicitud.'), 400
        import qrcode
        image = qrcode.make(url, error_correction=qrcode.constants.ERROR_CORRECT_L, box_size=6, border=2)
        output = BytesIO(); image.save(output, format='PNG'); output.seek(0)
        return send_file(output, mimetype='image/png')

    def candidates():
        """IPv4 privadas de esta PC, para elegir la de la red local sin escribirla."""
        import ipaddress, socket
        found = []
        try:
            probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            try:
                probe.connect(('10.255.255.255', 1)); found.append(probe.getsockname()[0])
            finally: probe.close()
        except OSError: pass
        try: found += socket.gethostbyname_ex(socket.gethostname())[2]
        except OSError: pass
        result = []
        for value in found:
            try: address = ipaddress.IPv4Address(value)
            except ValueError: continue
            if address.is_private and not address.is_loopback and not address.is_link_local and value not in result: result.append(value)
        return result

    @app.get('/api/network')
    def network_status():
        return jsonify(dict(sync.status(), candidatas=candidates(), guardada=config.preferences()['red']))

    @app.post('/api/network')
    def network_action():
        data = request.get_json(silent=True)
        if not isinstance(data, dict): return jsonify(error='Datos no válidos.'), 400
        try:
            if data.get('action') == 'start':
                state = sync.start(data.get('host'), data.get('port', 47811))
                host, port = state['address']
                config.update_preferences(dict(red=dict(host=host, port=port)))
                return jsonify(state)
            if data.get('action') == 'stop':
                sync.stop(); config.update_preferences(dict(red=None)); return jsonify(sync.status())
            raise ValueError('La web solo puede operar como principal.')
        except (ValueError, TypeError) as error: return jsonify(error=str(error)), 400

    @app.get('/api/employees')
    def employees(): return jsonify(store.employees())

    @app.post('/api/employees')
    def employee_action():
        data = request.get_json(silent=True)
        if not isinstance(data, dict): return jsonify(error='Datos no válidos.'), 400
        try:
            id = store.edit_employee(data)
            if data.get('action') == 'revoke': sync.disconnect(id)
            return jsonify(id=id)
        except (ValueError, TypeError) as error: return jsonify(error=str(error)), 400

    @app.post('/api/employees/qr')
    def employee_qr():
        data = request.get_json(silent=True)
        if not isinstance(data, dict): return jsonify(error='Datos no válidos.'), 400
        try:
            from .store import integer
            import qrcode
            code = sync.qr(integer(data.get('id'), 1))
            image = qrcode.make(code)
            output = BytesIO(); image.save(output, format='PNG'); output.seek(0)
            return send_file(output, mimetype='image/png')
        except (ValueError, TypeError) as error: return jsonify(error=str(error)), 400

    @app.get('/api/records')
    def records(): return jsonify(store.records())

    @app.post('/api/settings')
    def settings():
        data = request.get_json(silent=True)
        if not isinstance(data, dict): return jsonify(error='Datos no válidos.'), 400
        try: store.settings(data); return jsonify(ok=True)
        except (ValueError, TypeError, KeyError) as error: return jsonify(error=str(error)), 400

    @app.post('/api/catalog')
    def catalog_action():
        data = request.get_json(silent=True)
        if not isinstance(data, dict): return jsonify(error='Datos no válidos.'), 400
        try:
            licensing.require_active()
            with store.connect() as db:
                store.begin(db)
                value = store.catalog_command(db, data)
            return jsonify(value=value)
        except (ValueError, KeyError, TypeError) as error: return jsonify(error=str(error)), 400

    @app.post('/api/android/export')
    def export_android():
        data = request.get_json(silent=True)
        if not isinstance(data, dict): return jsonify(error='Datos no válidos.'), 400
        try: content = store.export_backup(data.get('password', ''), android=True)
        except (ValueError, TypeError) as error: return jsonify(error=str(error)), 400
        return send_file(BytesIO(content), mimetype='application/octet-stream', as_attachment=True, download_name='SPVI_negocio.spvi')

    @app.post('/api/android/import')
    def import_android():
        if request.form.get('confirm') != 'RESTAURAR': return jsonify(error='Confirma el reemplazo de datos.'), 400
        file = request.files.get('file')
        if file is None: return jsonify(error='Selecciona el archivo .spvi.'), 400
        try:
            sync.stop()
            store.import_backup(file.read(android_backup.MAX_BYTES + 1), request.form.get('password', ''), android=True, safety_password=request.form.get('safety_password'))
            return jsonify(ok=True)
        except (ValueError, KeyError, TypeError) as error: return jsonify(error=str(error)), 400

    @app.get('/api/photo/<kind>/<int:id>')
    def photo_read(kind,id):
        if kind not in ('productos','servicios'): abort(404)
        with store.connect() as db:
            row=db.execute('SELECT content FROM photos WHERE kind=? AND id=?',(kind,id)).fetchone()
        if not row: abort(404)
        return send_file(BytesIO(row[0]),mimetype='image/jpeg')

    @app.post('/api/photo')
    def photo_action():
        from .business import get
        from . import photos, dto
        data=request.get_json(silent=True)
        try:
            if not isinstance(data,dict) or data.get('kind') not in ('productos','servicios'): raise ValueError('Artículo no válido.')
            licensing.require_active()
            id=dto.number(data.get('id'),1); kind=data['kind']
            content=photos.decode(data['content']) if data.get('content') is not None else None
            with store.connect() as db:
                store.begin(db)
                if not get(db,kind,id): raise ValueError('Artículo inexistente.')
                if content:
                    size=db.execute('SELECT COALESCE(SUM(length(content)),0) FROM photos WHERE NOT(kind=? AND id=?)',(kind,id)).fetchone()[0]
                    if size+len(content)>64*1024*1024: raise ValueError('Las fotos del negocio exceden 64 MB.')
                    db.execute('INSERT OR REPLACE INTO photos VALUES(?,?,?)',(kind,id,content))
                else: db.execute('DELETE FROM photos WHERE kind=? AND id=?',(kind,id))
            return jsonify(ok=True)
        except (ValueError,KeyError,TypeError) as error: return jsonify(error=str(error)),400

    @app.post('/api/quote')
    def quote():
        data=request.get_json(silent=True)
        try:
            if not isinstance(data,dict): raise ValueError('Carrito no válido.')
            return jsonify(store.quote(data))
        except (ValueError,KeyError,TypeError) as error: return jsonify(error=str(error)),400

    @app.post('/api/<action>')
    def operate(action):
        data = request.get_json(silent=True)
        if not isinstance(data, dict):
            return jsonify(error='Datos no válidos.'), 400
        try:
            store.operate(action, data)
        except (ValueError,KeyError,TypeError) as error:
            return jsonify(error=str(error) if isinstance(error,ValueError) else 'Datos no válidos.'), 400
        return jsonify(ok=True)

    from .idempotency import protect as idempotent
    for name in ('operate','catalog_action','settings','employee_action','photo_action'):
        if name in app.view_functions:
            app.view_functions[name]=idempotent(app,store,app.view_functions[name])
    return app
