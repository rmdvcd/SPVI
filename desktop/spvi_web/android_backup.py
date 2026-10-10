"""Contenedor Android SPVIBAK v3/v4; mismo AAD y PBKDF2 que BackupCipher.kt."""
import gzip
import hashlib
import hmac
import json
import os
import struct
import time
from io import BytesIO
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAX_BYTES = 128 * 1024 * 1024
MAGIC = b'SPVIBAK'
MASK = (0x5A,0x13,0x7C,0x29,0x44,0x61,0x0E,0x38)
OBFUSCATED = (0x09,0x43,0x2A,0x60,0x69,0x23,0x4F,0x7B,0x11,0x3E,0x13,0x47,0x23,0x0E,0x6A,0x5D,
              0x74,0x21,0x4C,0x1B,0x73,0x4C,0x3C,0x0F,0x6B,0x5F,0x3D,0x7D,0x1D,0x24,0x3F,0x7E)


def password_value(password, protected):
    if not isinstance(password,str) or len(password)>256: raise ValueError('Contraseña no válida.')
    if protected:
        if not password: raise ValueError('Este respaldo requiere contraseña.')
        return password.encode('utf-8')
    return bytes(x ^ MASK[i % len(MASK)] for i,x in enumerate(OBFUSCATED))


def encode(document, password):
    raw = json.dumps(document,ensure_ascii=False,separators=(',',':')).encode()
    if len(raw)>MAX_BYTES: raise ValueError('Respaldo demasiado grande.')
    protected = bool(password)
    iterations = 310000 if protected else 10000
    salt, iv = os.urandom(16), os.urandom(12)
    compressed = gzip.compress(raw)
    aad = MAGIC + bytes((4,int(protected))) + struct.pack('>qI',int(time.time()*1000),iterations) + salt + iv + struct.pack('>q',len(compressed)+16)
    key = hashlib.pbkdf2_hmac('sha256',password_value(password,protected),salt,iterations,32)
    cipher = AESGCM(key).encrypt(iv,compressed,aad)
    if len(cipher)+89>MAX_BYTES: raise ValueError('Respaldo demasiado grande.')
    return aad + hashlib.sha256(cipher).digest() + cipher


def decode(data,password):
    if not isinstance(data,bytes) or len(data)>MAX_BYTES or len(data)<88 or data[:7]!=MAGIC:
        raise ValueError('No es un respaldo Android de SPVI válido.')
    version = data[7]
    if version not in (3,4): raise ValueError('Versión de respaldo no compatible.')
    offset = 9 if version==4 else 8
    if version==4 and data[8] not in (0,1): raise ValueError('Cabecera no válida.')
    protected = version==3 or data[8]==1
    aad_length = offset+48
    if len(data)<aad_length+32: raise ValueError('Respaldo incompleto.')
    _, iterations = struct.unpack('>qI',data[offset:offset+12])
    if not 10000<=iterations<=5000000: raise ValueError('Parámetros de cifrado no válidos.')
    salt,iv = data[offset+12:offset+28],data[offset+28:offset+40]
    length = struct.unpack('>q',data[offset+40:offset+48])[0]
    cipher = data[aad_length+32:]
    if length!=len(cipher) or length<16 or not hmac.compare_digest(hashlib.sha256(cipher).digest(),data[aad_length:aad_length+32]):
        raise ValueError('El respaldo está incompleto o dañado.')
    key = hashlib.pbkdf2_hmac('sha256',password_value(password,protected),salt,iterations,32)
    try:
        zipped = AESGCM(key).decrypt(iv,cipher,data[:aad_length])
        with gzip.GzipFile(fileobj=BytesIO(zipped)) as source:
            raw = source.read(MAX_BYTES+1)
        if len(raw)>MAX_BYTES: raise ValueError()
        document = json.loads(raw)
        if not isinstance(document,dict) or document.get('formato')!='spvi-respaldo' or document.get('version') not in (3,4):
            raise ValueError()
        return document
    except Exception as error:
        raise ValueError('Contraseña incorrecta o contenido de respaldo no válido.') from error
