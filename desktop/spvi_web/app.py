"""Interfaz HTTP local autenticada. No expone el protocolo de sincronización Android."""
import secrets
import hashlib
from pathlib import Path
from flask import Flask, abort, jsonify, render_template, request, session, send_file
from io import BytesIO
from .exports import export, MIMES
from .backups import backup, restore, MAX_BYTES
from werkzeug.security import check_password_hash
from .store import Store
from .secrets import SecretBox
from .licensing.client import LicenseClient
from .business import Business
from .sync.server import SyncManager
from . import android_backup


def create_app(data_dir, password_hash, secret, secure=False, hosts=None, identity_dir=None):
    app = Flask(__name__)
    access_version=hashlib.sha256(password_hash.encode()).hexdigest()
    app.config['ACCESS_VERSION']=access_version
    app.config.update(SECRET_KEY=secret, MAX_CONTENT_LENGTH=android_backup.MAX_BYTES + 65536, SESSION_COOKIE_HTTPONLY=True,
                      SESSION_COOKIE_SAMESITE='Strict', SESSION_COOKIE_SECURE=secure,
                      TRUSTED_HOSTS=hosts or ['127.0.0.1', 'localhost', 'spvi.minegocio.cu'])
    Path(data_dir).mkdir(parents=True, exist_ok=True)
    box = SecretBox(secret)
    identity = Path(identity_dir) if identity_dir else Path(data_dir)
    identity.mkdir(parents=True, exist_ok=True)
    licensing = LicenseClient(identity, box)
    store = Business(Store(Path(data_dir) / 'spvi.sqlite3'), licensing, box)
    sync = SyncManager(store)
    app.extensions.update(store=store, licensing=licensing, sync=sync)

    @app.before_request
    def protect():
        if session.get('authenticated') and session.get('credential_version')!=access_version: session.clear()
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
        response.headers['Content-Security-Policy'] = "default-src 'self'; img-src 'self' blob:; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
        return response

    @app.get('/')
    def index():
        session.setdefault('csrf', secrets.token_urlsafe(32))
        return render_template('index.html', csrf=session['csrf'], authenticated=session.get('authenticated', False))

    @app.post('/login')
    def login():
        data = request.get_json(silent=True)
        if not isinstance(data, dict):
            return jsonify(error='Datos no válidos.'), 400
        password = data.get('password', '')
        if not isinstance(password, str) or len(password) > 256 or not check_password_hash(password_hash, password):
            return jsonify(error='Clave incorrecta.'), 401
        session.clear()
        session.update(authenticated=True, csrf=secrets.token_urlsafe(32),credential_version=access_version)
        return jsonify(ok=True)

    @app.post('/api/logout')
    def logout():
        session.clear()
        return jsonify(ok=True)

    @app.get('/api/dashboard')
    def dashboard():
        try:
            return jsonify(store.dashboard(int(request.args.get('days', '30'))))
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
            if action == 'request': return jsonify(code=licensing.request(data))
            if action == 'activate': return jsonify(licensing.activate(data.get('code')))
            if action == 'revocations': return jsonify(licensing.import_revocations(data))
            raise ValueError('Acción no válida.')
        except (ValueError, KeyError, TypeError) as error:
            return jsonify(error=str(error) if isinstance(error, ValueError) else 'Datos no válidos.'), 400

    @app.get('/api/network')
    def network_status(): return jsonify(sync.status())

    @app.post('/api/network')
    def network_action():
        data = request.get_json(silent=True)
        if not isinstance(data, dict): return jsonify(error='Datos no válidos.'), 400
        try:
            if data.get('action') == 'start': return jsonify(sync.start(data.get('host'), data.get('port', 47811)))
            if data.get('action') == 'stop': sync.stop(); return jsonify(sync.status())
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
