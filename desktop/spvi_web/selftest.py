"""Prueba del paquete portable: se ejecuta sin navegador, red ni datos reales."""
import tempfile
from pathlib import Path
from werkzeug.security import generate_password_hash
from .app import create_app
from .exports import export


def run():
    with tempfile.TemporaryDirectory(prefix='spvi-prueba-') as directory:
        app=create_app(directory, generate_password_hash('prueba-local-empaquetado'), 'solo-prueba')
        app.testing=True
        with app.test_client() as client:
            with client.get('/') as response:
                if response.status_code != 200 or b'SPVI' not in response.data: raise RuntimeError('Falta la plantilla')
            for resource in ('app.js','management.js','app.css'):
                with client.get('/static/'+resource) as response:
                    if response.status_code != 200: raise RuntimeError('Faltan recursos')
            with client.session_transaction() as session:
                session['authenticated']=True;session['credential_version']=app.config['ACCESS_VERSION']
            with client.get('/') as response:
                if b'metadata-dialog' not in response.data: raise RuntimeError('Falta administración')
        store=app.extensions['store']
        store.operate('item',dict(name='Prueba',kind='producto',price=1,cost=0,stock=1))
        for scope,fmt in [('inventario','pdf'),('inventario','xlsx'),('productos','tarjetas')]:
            content,extension=export(store,scope,fmt,'Prueba')
            if not content: raise RuntimeError('Exportación vacía')
        import qrcode
        from io import BytesIO
        qr=BytesIO();qrcode.make('SPVI: prueba de recurso').save(qr,format='PNG')
        if not qr.getvalue().startswith(b'\x89PNG'): raise RuntimeError('No funciona el generador QR')
        store.operate('open',dict(fund=0))
        quote=store.quote(dict(lines=[dict(item_id=1,quantity=1)],method='efectivo'))
        if quote['total']!=100: raise RuntimeError('Cotización incorrecta')
        store.operate('cart',dict(lines=[dict(item_id=1,quantity=1)],method='efectivo'))
        store.operate('close',dict(counted=1))
        shared=store.export_backup('',android=True)
        from .android_backup import decode
        if len(decode(shared,'')['ventas'])!=1: raise RuntimeError('Respaldo Android incorrecto')
        encrypted=store.export_backup('clave-de-prueba-portable')
        store.import_backup(encrypted,'clave-de-prueba-portable')
        if not Path(directory,'spvi.sqlite3').exists(): raise RuntimeError('No se guardaron datos')
    print('SPVI: recursos, exportaciones y respaldo verificados.')
