"""Respaldos de escritorio cifrados; nunca se ejecuta SQL procedente del archivo."""
import gzip
import hashlib
import json
import os
import sqlite3
from datetime import datetime
from io import BytesIO
from pathlib import Path
from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from .store import SCHEMA

MAGIC = b'SPVIDSK1'
MAX_BYTES = 128 * 1024 * 1024
TABLES = {
    'items': ('id','name','kind','price','cost','stock','minimum'),
    'shifts': ('id','opened','closed','fund','counted','expected'),
    'sales': ('id','shift_id','item_id','name','quantity','price','cost','method','created'),
    'movements': ('id','shift_id','amount','reason','created'),
}
TEXT = {'name','kind','opened','closed','method','created','reason'}
NULLABLE = {'closed','counted','expected'}
DATES = {'opened','closed','created'}


def password_bytes(password):
    if not isinstance(password,str) or not 12 <= len(password) <= 256:
        raise ValueError('La contraseña del respaldo debe tener entre 12 y 256 caracteres.')
    return password.encode('utf-8')


def bounded_gunzip(content):
    try:
        with gzip.GzipFile(fileobj=BytesIO(content)) as stream:
            data = stream.read(MAX_BYTES + 1)
            if len(data) > MAX_BYTES:
                raise ValueError('El respaldo supera el tamaño permitido.')
            return data
    except (OSError, EOFError):
        raise ValueError('Contenido del respaldo no válido.') from None


def seal(document, password):
    password = password_bytes(password)
    raw = json.dumps(document,ensure_ascii=False,separators=(',',':')).encode('utf-8')
    if len(raw) > MAX_BYTES: raise ValueError('El respaldo supera el tamaño permitido.')
    salt, nonce = os.urandom(16), os.urandom(12)
    header = MAGIC + salt + nonce
    key = hashlib.pbkdf2_hmac('sha256',password,salt,310000,32)
    ciphertext = AESGCM(key).encrypt(nonce,gzip.compress(raw),header)
    if len(header) + len(ciphertext) > MAX_BYTES: raise ValueError('El respaldo supera el tamaño permitido.')
    return header + ciphertext


def unseal(content, password):
    password = password_bytes(password)
    if not isinstance(content,bytes) or not 52 <= len(content) <= MAX_BYTES or not content.startswith(MAGIC):
        raise ValueError('No es un respaldo de SPVI de escritorio compatible.')
    salt, nonce, header = content[8:24],content[24:36],content[:36]
    key = hashlib.pbkdf2_hmac('sha256',password,salt,310000,32)
    try:
        raw = AESGCM(key).decrypt(nonce,content[36:],header)
        return json.loads(bounded_gunzip(raw))
    except (InvalidTag, UnicodeError, json.JSONDecodeError, RecursionError):
        raise ValueError('Contraseña incorrecta o respaldo dañado.') from None


def snapshot(db):
    return {'format':'spvi-desktop','version':1,
            'tables':{name:[dict(row) for row in db.execute('SELECT '+','.join(cols)+' FROM '+name+' ORDER BY id')]
                      for name,cols in TABLES.items()}}


def validate(document):
    if not isinstance(document,dict) or document.get('format') != 'spvi-desktop' or document.get('version') != 1:
        raise ValueError('Versión de respaldo no compatible.')
    tables = document.get('tables')
    if not isinstance(tables,dict) or set(tables) != set(TABLES): raise ValueError('Tablas de respaldo no válidas.')
    count = 0
    for name, columns in TABLES.items():
        rows = tables[name]
        if not isinstance(rows,list): raise ValueError('Tabla no válida.')
        count += len(rows)
        if count > 100000: raise ValueError('Demasiados registros en el respaldo.')
        for row in rows:
            if not isinstance(row,dict) or set(row) != set(columns): raise ValueError('Registro no válido.')
            for column,value in row.items():
                if value is None and column in NULLABLE: continue
                if column in TEXT:
                    if not isinstance(value,str) or not value.strip() or len(value) > 120:
                        raise ValueError('Texto no válido en el respaldo.')
                    if column in DATES:
                        try: datetime.strptime(value,'%Y-%m-%dT%H:%M:%SZ')
                        except ValueError: raise ValueError('Fecha no válida en el respaldo.') from None
                elif type(value) is not int or abs(value) > 9_000_000_000_000_000:
                    raise ValueError('Número no válido en el respaldo.')
                if column in ('id','shift_id','item_id') and value <= 0: raise ValueError('Identificador no válido.')
                if column in ('price','cost','counted','fund') and value is not None and value < 0:
                    raise ValueError('Importe negativo no válido.')
            if name == 'shifts' and ((row['closed'] is None) != (row['counted'] is None and row['expected'] is None)):
                raise ValueError('Arqueo incompleto.')
            if name == 'shifts' and row['closed'] is not None and (row['counted'] is None or row['expected'] is None):
                raise ValueError('Arqueo incompleto.')
    # Validar relaciones y restricciones en una BD aislada antes de tocar la real.
    db = sqlite3.connect(':memory:')
    try:
        db.execute('PRAGMA foreign_keys=ON'); db.executescript(SCHEMA)
        insert(db,tables)
    except sqlite3.IntegrityError:
        raise ValueError('El respaldo contiene relaciones o valores inconsistentes.') from None
    finally: db.close()
    return tables


def insert(db,tables):
    for name,columns in TABLES.items():
        db.executemany('INSERT INTO '+name+'('+','.join(columns)+') VALUES('+','.join('?' for _ in columns)+')',
                       [tuple(row[c] for c in columns) for row in tables[name]])


def backup(store,password):
    with store.connect() as db:
        db.execute('BEGIN')
        document = snapshot(db)
    return seal(document,password)


def restore(store,content,password):
    tables = validate(unseal(content,password))
    if any(s['closed'] is None for s in tables['shifts']):
        raise ValueError('Cierra los turnos antes de crear la copia que vas a restaurar.')
    with store.connect() as db:
        db.execute('BEGIN IMMEDIATE')
        if db.execute('SELECT 1 FROM shifts WHERE closed IS NULL').fetchone():
            raise ValueError('Cierra el turno actual antes de restaurar.')
        # Copia de seguridad previa cifrada con la misma contraseña del respaldo restaurado.
        previous = seal(snapshot(db),password)
        target = Path(store.path).parent / 'antes_de_restaurar.spvidesk'
        temporary = target.with_suffix('.tmp')
        try:
            with open(temporary,'wb') as file:
                os.chmod(temporary,0o600)
                file.write(previous); file.flush(); os.fsync(file.fileno())
            os.replace(temporary,target)
            for table_name in reversed(TABLES): db.execute('DELETE FROM '+table_name)
            insert(db,tables)
        finally:
            temporary.unlink(missing_ok=True)
