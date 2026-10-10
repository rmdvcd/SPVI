"""Port del formato de CodigoQr/CriptoSync/CanalCifrado de Android; sin servidor de negocio.

Nunca utilizar un canal autenticado como sustituto de las validaciones de licencia,
permisos, vencimiento del QR y persistencia idempotente que necesita ServidorSync.
"""
import base64
import gzip
import hashlib
import hmac
import json
import os
import struct
from io import BytesIO
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

MAX_FRAME=16*1024*1024
MAX_PLAIN=4*MAX_FRAME
QR_PREFIX='SPVI-VINC1:'


class ProtocolError(ValueError):
    """No contiene mensajes, claves ni datos del negocio."""


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode('ascii')


def unb64url(text):
    if not isinstance(text,str) or len(text)>2000: raise ProtocolError('Código no válido.')
    try: return base64.b64decode(text+'='*(-len(text)%4),altchars=b'-_',validate=True)
    except (ValueError,UnicodeError): raise ProtocolError('Código no válido.') from None


def qr_encode(business,name,hosts,port,employee,employee_name,token,expires_ms):
    if not isinstance(token,bytes) or len(token)!=16: raise ProtocolError('Código no válido.')
    document=dict(n=business,nn=name,h=hosts,p=port,e=employee,en=employee_name,t=b64url(token),x=expires_ms)
    result=QR_PREFIX+b64url(json.dumps(document,ensure_ascii=False,separators=(',',':')).encode('utf-8'))
    qr_decode(result)
    return result


def qr_decode(text):
    if not isinstance(text,str) or len(text)>2000 or not text.startswith(QR_PREFIX):
        raise ProtocolError('Código no válido.')
    try:
        d=json.loads(unb64url(text[len(QR_PREFIX):]))
        if not isinstance(d,dict) or set(d)!=set(('n','nn','h','p','e','en','t','x')): raise ValueError()
        if any(not isinstance(d[k],str) for k in ('n','nn','en')) or not d['n'].strip(): raise ValueError()
        if type(d['p']) is not int or not 1<=d['p']<=65535 or type(d['e']) is not int or d['e']<=0: raise ValueError()
        if type(d['x']) is not int or not 0<d['x']<2**63: raise ValueError()
        if not isinstance(d['h'],list) or not d['h'] or any(not isinstance(h,str) or not h.strip() for h in d['h']): raise ValueError()
        token=unb64url(d['t'])
        if len(token)!=16: raise ValueError()
        return d,token
    except (ValueError,TypeError,KeyError,UnicodeError,RecursionError):
        raise ProtocolError('Código no válido.') from None


def hkdf(key,salt,info):
    return HKDF(algorithm=hashes.SHA256(),length=32,salt=salt,info=info).derive(key)


def new_key(): return ec.generate_private_key(ec.SECP256R1())


def public_spki(key):
    return key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)


def employee_key(private,peer_spki,token):
    if len(token)!=16 or len(peer_spki)>1024: raise ProtocolError('Vinculación no válida.')
    try:
        peer=serialization.load_der_public_key(peer_spki)
        if not isinstance(peer,ec.EllipticCurvePublicKey) or not isinstance(peer.curve,ec.SECP256R1): raise ValueError()
        return hkdf(private.exchange(ec.ECDH(),peer),token,b'spvi-clave-empleado-v1')
    except (ValueError,TypeError): raise ProtocolError('Vinculación no válida.') from None


def link_mac(token,client_spki,server_spki=None):
    data=b'spvi-vinc-c'+client_spki if server_spki is None else b'spvi-vinc-s'+server_spki+client_spki
    return hmac.new(token,data,hashlib.sha256).digest()


def verify_mac(expected,received):
    if not hmac.compare_digest(expected,received): raise ProtocolError('Autenticación no válida.')


def session_keys(key,client_nonce,server_nonce):
    if len(key)!=32 or len(client_nonce)!=16 or len(server_nonce)!=16: raise ProtocolError('Sesión no válida.')
    return hkdf(key,client_nonce+server_nonce,b'spvi-c2s-v1'),hkdf(key,client_nonce+server_nonce,b'spvi-s2c-v1')


def rejection_mac(key,nonce,reason):
    return hmac.new(key,b'spvi-rechazo'+nonce+reason.encode('utf-8'),hashlib.sha256).digest()


def read_exact(stream,size):
    chunks=[]
    while size:
        part=stream.read(size)
        if not part: raise ProtocolError('Conexión interrumpida.')
        chunks.append(part);size-=len(part)
    return b''.join(chunks)


def read_frame(stream,limit=MAX_FRAME):
    size=struct.unpack('>i',read_exact(stream,4))[0]
    if not 0<=size<=limit: raise ProtocolError('Trama no válida.')
    return read_exact(stream,size)


def frame(content):
    if len(content)>MAX_FRAME: raise ProtocolError('Trama demasiado grande.')
    return struct.pack('>i',len(content))+content


def decode_json(data):
    try:
        message=json.loads(data)
        if not isinstance(message,dict) or not isinstance(message.get('t'),str): raise ValueError()
        return message
    except (ValueError,UnicodeError,RecursionError): raise ProtocolError('Mensaje no válido.') from None


def read_greeting(stream): return decode_json(read_frame(stream,64*1024))


def greeting(message):
    data=json.dumps(message,ensure_ascii=False,separators=(',',':')).encode('utf-8')
    if len(data)>64*1024: raise ProtocolError('Saludo demasiado grande.')
    return frame(data)


class Channel:
    """Uso serializado por conexión. Tras cualquier fallo de autenticación, se descarta."""
    def __init__(self,keys,principal):
        self.send_key,self.receive_key=(keys[1],keys[0]) if principal else keys
        self.send_tag,self.receive_tag=(2,1) if principal else (1,2)
        self.sent=self.received=0
        self.failed=False

    @staticmethod
    def aad(tag,sequence): return b'SPVI-S1\0'+struct.pack('>Bq',tag,sequence)

    def encode(self,message):
        if self.failed: raise ProtocolError('Sesión cerrada.')
        plain=json.dumps(message,ensure_ascii=False,separators=(',',':')).encode('utf-8')
        if len(plain)>MAX_PLAIN: raise ProtocolError('Mensaje demasiado grande.')
        iv=os.urandom(12)
        cipher=AESGCM(self.send_key).encrypt(iv,gzip.compress(plain),self.aad(self.send_tag,self.sent))
        packet=frame(iv+cipher)
        self.sent+=1
        return packet

    def receive(self,stream):
        if self.failed: raise ProtocolError('Sesión cerrada.')
        try:
            data=read_frame(stream)
            if len(data)<28: raise ValueError()
            zipped=AESGCM(self.receive_key).decrypt(data[:12],data[12:],self.aad(self.receive_tag,self.received))
            with gzip.GzipFile(fileobj=BytesIO(zipped)) as unzipped: plain=unzipped.read(MAX_PLAIN+1)
            if len(plain)>MAX_PLAIN: raise ValueError()
            message=decode_json(plain)
            self.received+=1
            return message
        except Exception:
            self.failed=True
            raise ProtocolError('Mensaje cifrado no válido.') from None
