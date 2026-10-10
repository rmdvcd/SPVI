"""Acceso local al portable: una instancia y recuperación desde la consola del dueño."""
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
