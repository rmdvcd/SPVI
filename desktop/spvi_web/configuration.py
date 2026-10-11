"""Configuración local del portable: una sola instancia, clave de acceso opcional y preferencias de la PC."""
import json
import os
from pathlib import Path
from werkzeug.security import generate_password_hash


def acquire_lock(directory):
    path=Path(directory)/'spvi.lock'
    file=open(path,'a+b')
    try:
        if file.seek(0,2)==0: file.write(b'0');file.flush()
        file.seek(0)
        if os.name=='nt':
            import msvcrt
            msvcrt.locking(file.fileno(),msvcrt.LK_NBLCK,1)
        else:
            import fcntl
            fcntl.flock(file.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
        return file
    except OSError:
        file.close()
        raise ValueError('Ya hay una instancia de SPVI usando esta carpeta. Ciérrala primero.') from None


def reset_password(path,password):
    if not isinstance(password,str) or not 12<=len(password)<=256: raise ValueError('La clave requiere entre 12 y 256 caracteres.')
    path=Path(path)
    settings=json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(settings.get('secret'),str) or not settings['secret']: raise ValueError('Configuración incompleta. No se modificó.')
    settings['password_hash']=generate_password_hash(password)
    temporary=path.with_suffix('.tmp')
    descriptor=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(descriptor,'w',encoding='utf-8') as file:
        json.dump(settings,file);file.flush();os.fsync(file.fileno())
    os.replace(temporary,path)


MIN_PASSWORD = 8
THEMES = ('claro', 'oscuro', 'sistema')
DELAYS = (0, 15, 30, 60, 120, 300)
# abrirAlIniciar: al iniciar con Windows, además del icono, abrir la pantalla principal.
# red: última dirección de la red local en uso; se reanuda al abrir SPVI (None = detenida).
DEFAULTS = dict(tema='sistema', inicioConWindows=False, retrasoSegundos=0, abrirAlIniciar=False, red=None)


def _write(path, settings):
    if path is None: return
    temporary = path.with_suffix('.tmp')
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, 'w', encoding='utf-8') as file:
        json.dump(settings, file); file.flush(); os.fsync(file.fileno())
    os.replace(temporary, path)


class Config:
    """``config.json`` de la carpeta de datos: secreto de la instalación, clave opcional y preferencias de la PC.

    La clave de acceso es opcional desde 0.31: sin ella, la pantalla principal abre directamente (el servidor solo
    escucha en 127.0.0.1). Se crea, cambia o quita desde Ajustes › Clave de acceso.
    """

    def __init__(self, path=None, password_hash=None, secret=None):
        import secrets as _secrets
        import threading
        self.path, self.lock = Path(path) if path else None, threading.RLock()
        if self.path is None:
            # En memoria (pruebas y autoprueba del paquete): nada se escribe en disco.
            self.data = dict(secret=secret or _secrets.token_hex(32), password_hash=password_hash)
        elif self.path.exists():
            self.data = json.loads(self.path.read_text(encoding='utf-8'))
            if not isinstance(self.data.get('secret'), str) or not self.data['secret']:
                raise ValueError('Configuración incompleta.')
        else:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            self.data = dict(secret=_secrets.token_hex(32), password_hash=None)
            _write(self.path, self.data)
        self.data.setdefault('password_hash', None)

    @property
    def secret(self): return self.data['secret']

    @property
    def password_hash(self): return self.data.get('password_hash') or None

    def access_version(self):
        import hashlib
        return hashlib.sha256(('SPVI-acceso|' + (self.password_hash or 'sin-clave')).encode()).hexdigest()

    def check(self, password):
        from werkzeug.security import check_password_hash
        if not self.password_hash: return True
        return isinstance(password, str) and len(password) <= 256 and check_password_hash(self.password_hash, password)

    def set_password(self, current, new):
        """Crea o cambia la clave; ``new`` vacío o ``None`` la quita. Exige la clave actual si existe."""
        with self.lock:
            if self.password_hash and not self.check(current): raise ValueError('La clave actual no es correcta.')
            if new in (None, ''):
                self.data['password_hash'] = None
            else:
                if not isinstance(new, str) or not MIN_PASSWORD <= len(new) <= 256:
                    raise ValueError('La clave requiere entre %d y 256 caracteres.' % MIN_PASSWORD)
                self.data['password_hash'] = generate_password_hash(new)
            _write(self.path, self.data)

    def preferences(self):
        result = dict(DEFAULTS)
        result.update({k: v for k, v in (self.data.get('preferencias') or {}).items() if k in DEFAULTS})
        return result

    def update_preferences(self, values):
        if not isinstance(values, dict): raise ValueError('Preferencias no válidas.')
        with self.lock:
            current = self.preferences()
            for key, value in values.items():
                if key not in DEFAULTS: raise ValueError('Preferencia desconocida.')
                if key == 'tema' and value not in THEMES: raise ValueError('Tema no válido.')
                if key == 'retrasoSegundos' and value not in DELAYS: raise ValueError('Retraso no válido.')
                if key in ('inicioConWindows', 'abrirAlIniciar') and type(value) is not bool: raise ValueError('Valor no válido.')
                if key == 'red' and value is not None and (not isinstance(value, dict) or set(value) != {'host', 'port'}
                                                           or not isinstance(value['host'], str) or type(value['port']) is not int):
                    raise ValueError('Red no válida.')
                current[key] = value
            self.data['preferencias'] = current
            _write(self.path, self.data)
            return current
