"""Solicitud SPVIR1, activación SPVI2 y lista firmada de revocaciones de GL."""
import base64
import hashlib
import hmac
import json
import os
import re
import threading
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, utils
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from . import trust

TYPES = ('MENSUAL', 'SEMESTRAL', 'ANUAL', 'PERPETUA')


def enc(data): return base64.urlsafe_b64encode(data).rstrip(b'=').decode('ascii')


def dec(text): return base64.b64decode(text + '=' * (-len(text) % 4), altchars=b'-_', validate=True)


def compressed(key):
    return key.public_bytes(serialization.Encoding.X962, serialization.PublicFormat.CompressedPoint)


def derive(shared, salt, info):
    return HKDF(algorithm=hashes.SHA256(), length=44, salt=salt, info=info).derive(shared)


def verify_signature(message, raw, keys=trust.SIGN):
    if len(raw) != 64: raise ValueError('Firma de licencia no válida.')
    signature = utils.encode_dss_signature(int.from_bytes(raw[:32], 'big'), int.from_bytes(raw[32:], 'big'))
    for public in keys:
        key = serialization.load_der_public_key(base64.b64decode(public))
        try:
            key.verify(signature, message, ec.ECDSA(hashes.SHA256()))
            return
        except InvalidSignature:
            continue
    raise ValueError('La firma no corresponde al emisor de SPVI.')


def decode_license(code, private, device_id, keys=trust.SIGN):
    try:
        match = re.search(r'SPVI2:([A-Za-z0-9_\-\s]+)', code)
        if not match: raise ValueError()
        encoded = re.sub(r'\s', '', match.group(1))[:210]
        raw = dec(encoded)
        if len(raw) != 157: raise ValueError()
        epk, cipher, signature = raw[:33], raw[33:93], raw[93:]
        verify_signature(b'SPVI-L2|' + epk + cipher, signature, keys)
        peer = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), epk)
        key = derive(private.exchange(ec.ECDH(), peer), epk, b'SPVI-L2')
        body = AESGCM(key[:32]).decrypt(key[32:], cipher, b'SPVI-L2|' + epk)
        fingerprint = hashlib.sha256(('SPVI-L2|' + device_id + '|').encode() + compressed(private.public_key())).digest()[:16]
        if len(body) != 44 or body[0] != 2 or not hmac.compare_digest(body[17:33], fingerprint): raise ValueError()
        if body[33] > 3 or body[34] > 3 or body[35] > 10: raise ValueError()
        issued, expires = int.from_bytes(body[36:40], 'big'), int.from_bytes(body[40:44], 'big')
        if (body[33] == 3) != (expires == 0) or (expires and expires <= issued): raise ValueError()
        return dict(id=str(uuid.UUID(bytes=body[1:17])), tipo=TYPES[body[33]], estado=body[34],
                    secundarias=body[35], emitidaEn=issued, venceEn=expires or None, code='SPVI2:' + enc(raw))
    except Exception as error:
        raise ValueError('Licencia no válida para esta instalación.') from error


class LicenseClient:
    def __init__(self, directory, box, clock=time.time):
        self.path = Path(directory) / 'licencia.bin'
        self.box, self.clock, self.lock = box, clock, threading.RLock()
        if self.path.exists():
            self.data = json.loads(box.open(self.path.read_bytes()))
        else:
            private = ec.generate_private_key(ec.SECP256R1())
            pem = private.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()).decode()
            now = int(clock())
            self.data = dict(deviceId='SPVI:' + uuid.uuid4().hex, private=pem, started=now, last=now,
                             code=None, revocations=None)
            self._save()
        self.private = serialization.load_pem_private_key(self.data['private'].encode(), password=None)

    def _save(self):
        data = self.box.seal(json.dumps(self.data, separators=(',', ':')).encode())
        temporary = self.path.with_suffix('.tmp')
        descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(descriptor, 'wb') as file:
            file.write(data); file.flush(); os.fsync(file.fileno())
        os.replace(temporary, self.path)

    def status(self):
        with self.lock:
            now = int(self.clock())
            base = dict(deviceId=self.data['deviceId'], clase='BLOQUEADA', secundarias=0, tipo=None, venceEn=None)
            if now + 300 < self.data['last']:
                return dict(base, motivo='Revisa la fecha y hora del equipo.')
            if now > self.data['last']:
                self.data['last'] = now; self._save()
            if not self.data['code']:
                expires = self.data['started'] + 7 * 86400
                if now < expires:
                    return dict(base, clase='PRUEBA', secundarias=5, venceEn=expires * 1000)
                return dict(base, motivo='La prueba terminó. Activa una licencia.')
            info = decode_license(self.data['code'], self.private, self.data['deviceId'])
            revoked = self.data.get('revocations')
            token = hashlib.sha256(('SPVI-REV|' + info['id']).encode()).digest()[:16].hex()
            if info['estado'] in (1, 2) or (revoked and token in revoked['revocadas']):
                return dict(base, id=info['id'], motivo='Licencia vencida o revocada.')
            if info['emitidaEn'] > now + 300 or (info['venceEn'] and now >= info['venceEn']):
                return dict(base, id=info['id'], motivo='La licencia no está vigente.')
            return dict(base, id=info['id'], clase='PERPETUA' if info['tipo'] == 'PERPETUA' else 'ACTIVA',
                        tipo=info['tipo'], secundarias=info['secundarias'],
                        venceEn=info['venceEn'] * 1000 if info['venceEn'] else None)

    def require_active(self):
        state = self.status()
        if state['clase'] == 'BLOQUEADA': raise ValueError(state['motivo'])
        return state

    def activate(self, code):
        if not isinstance(code, str) or len(code) > 4096: raise ValueError('Código no válido.')
        with self.lock:
            info = decode_license(code, self.private, self.data['deviceId'])
            if info['estado'] in (1, 2): raise ValueError('Licencia vencida o revocada.')
            self.data['code'] = info['code']; self._save()
            return self.status()

    def request(self, data):
        fields = {k: str(data.get(k, '')).strip() for k in ('nombre', 'apellidos', 'ci', 'telefono', 'tipo', 'via')}
        if not fields['nombre'] or not fields['apellidos'] or any(len(fields[k]) > 80 for k in ('nombre','apellidos')):
            raise ValueError('Completa nombre y apellidos.')
        from ..dto import identity_number, telephone
        fields['ci']=identity_number(fields['ci']);fields['telefono']=telephone(fields['telefono'])
        if fields['tipo'] not in TYPES or fields['via'] not in ('SMS','WHATSAPP'): raise ValueError('Solicitud no válida.')
        n = data.get('secundarias', 0)
        if type(n) is not int or not 0 <= n <= 10: raise ValueError('Elige entre 0 y 10 secundarias.')
        payload = dict(fields, v=2, deviceId=self.data['deviceId'], secundarias=n,
                       solicitadaEn=datetime.fromtimestamp(self.clock(),timezone.utc).isoformat().replace('+00:00','Z'),
                       nonce=enc(os.urandom(16)), devicePub=enc(compressed(self.private.public_key())))
        if data.get('recupera'):
            payload['recupera'] = str(uuid.UUID(data['recupera']))
        elif self.data['code']:
            info = decode_license(self.data['code'], self.private, self.data['deviceId'])
            if info['tipo'] != 'PERPETUA': payload['renueva'] = info['id']
        ephemeral = ec.generate_private_key(ec.SECP256R1())
        epk = compressed(ephemeral.public_key())
        public = serialization.load_der_public_key(base64.b64decode(trust.ECDH))
        key = derive(ephemeral.exchange(ec.ECDH(), public), epk, b'SPVI-R1')
        raw = json.dumps(payload, ensure_ascii=False,separators=(',',':')).encode()
        cipher = AESGCM(key[:32]).encrypt(key[32:], raw, b'SPVI-R1|' + epk)
        return 'SPVIR1:' + enc(epk + cipher)

    def import_revocations(self, envelope):
        if not isinstance(envelope,dict) or not isinstance(envelope.get('datos'), str) or len(envelope['datos']) > 512*1024:
            raise ValueError('Lista no válida.')
        try:
            verify_signature(b'SPVI-REV1|' + envelope['datos'].encode(), dec(envelope['firma']))
            doc = json.loads(envelope['datos'])
            if doc['v'] != 1 or type(doc['emitidaEn']) is not int or not isinstance(doc['revocadas'],list): raise ValueError()
            if any(not isinstance(x,str) or not re.fullmatch('[a-f0-9]{32}',x) for x in doc['revocadas']): raise ValueError()
        except Exception as error: raise ValueError('Lista de revocaciones no auténtica.') from error
        with self.lock:
            old = self.data.get('revocations')
            if old and doc['emitidaEn'] < old['emitidaEn']: raise ValueError('La lista es anterior a la ya instalada.')
            self.data['revocations'] = doc; self._save()
        return self.status()
