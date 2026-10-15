"""Solicitud SPVIR1, activación con código de 16 dígitos (PC) y lista firmada de revocaciones de GL.

Las licencias ``SPVI2`` ya guardadas por versiones anteriores siguen siendo válidas."""
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
from urllib.parse import quote
from . import codigo, envelope, pc_code, subtokens, trust

TYPES = ('MENSUAL', 'SEMESTRAL', 'ANUAL', 'PERPETUA')
TYPE_LABELS = dict(MENSUAL='Mensual', SEMESTRAL='Semestral', ANUAL='Anual', PERPETUA='Perpetua')
DEVELOPER = '5351815604'
TRANSACTION = re.compile(r'[A-Za-z0-9\-]{4,40}')
MAX_FAILURES, FAILURE_WINDOW = 5, 600
# Marca de agua del reloj (mitigación 3): si la hora del equipo retrocede más que esta tolerancia respecto a la
# última vista, la licencia queda bloqueada (se avisa de revisar la fecha). Se persiste en `last`.
TOLERANCIA_RELOJ = 300
# Cuota de apps secundarias si la licencia no trae el campo (licencias anteriores y periodo de prueba).
SECUNDARIAS_DEFECTO = 5


def enc(data): return base64.urlsafe_b64encode(data).rstrip(b'=').decode('ascii')


def dec(text): return base64.b64decode(text + '=' * (-len(text) % 4), altchars=b'-_', validate=True)


def compressed(key):
    return key.public_bytes(serialization.Encoding.X962, serialization.PublicFormat.CompressedPoint)


def _instante(valor):
    """ISO-8601 → epoch en segundos. `None`/vacío → `None`; texto inválido → `ValueError`."""
    if valor in (None, ''):
        return None
    try:
        return int(datetime.fromisoformat(str(valor).replace('Z', '+00:00')).timestamp())
    except ValueError:
        raise ValueError('Fecha de licencia no válida.') from None


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


# Regla de nombre de GL al validar la solicitud (PR #3 de GL, 2026-10-14): letras Unicode, espacio, punto,
# apóstrofo o guion, 2-80. Se comprueba aquí para que el error se vea en SPVI y no como rechazo mudo en GL.
NAME_RE = re.compile(r"(?:[^\W\d_]|[ .'\-]){2,80}")

# GL exige la solicitud fresca al emitir (≤72 h, ≤2 h de adelanto): se avisa en el propio mensaje.
AVISO_FRESCURA = 'Vale 72 horas: envíala y paga cuanto antes; si pasa ese tiempo, genera otra solicitud.'


class LicenseClient:
    def __init__(self, directory, box, clock=time.time, firma_keys=trust.SIGN):
        self.path = Path(directory) / 'licencia.bin'
        # Claves de firma de GL fijadas en el build (los tests usan las suyas: nunca se tocan las reales).
        # Auditoría 2026-10-14 (D-02): la verificación huella↔clave se hace al arrancar la app (create_app),
        # no aquí: los tests sustituyen las claves por las suyas y eso no es un error de fijación.
        self.firma_keys = tuple(firma_keys)
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
            # Mitigación 3 (marca de agua del reloj): la hora del equipo no puede retroceder respecto a la última
            # vista (`last`). Con tolerancia pequeña por los ajustes de hora y las zonas horarias.
            if now + TOLERANCIA_RELOJ < self.data['last']:
                return dict(base, motivo='Revisa la fecha y hora del equipo.')
            if now > self.data['last']:
                self.data['last'] = now; self._save()
            if not self.data['code']:
                expires = self.data['started'] + 7 * 86400
                if now < expires:
                    return dict(base, clase='PRUEBA', secundarias=5, venceEn=expires * 1000)
                return dict(base, motivo='La prueba terminó. Activa una licencia.')
            info = self._installed()
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

    def _key(self):
        public = serialization.load_der_public_key(base64.b64decode(trust.ECDH))
        shared = self.private.exchange(ec.ECDH(), public)
        return pc_code.key(shared, compressed(self.private.public_key()), self.data['deviceId'])

    def _installed(self):
        code = self.data['code']
        if code == 'MASTER':
            # Clave maestra de desarrollo: perpetua y con el máximo de secundarias, SOLO con SPVI_DESARROLLO=1.
            if not trust.es_debug(): raise ValueError('Licencia no válida para esta instalación.')
            return dict(id=subtokens.MAESTRA_ID, tipo='PERPETUA', estado=3, secundarias=10,
                        emitidaEn=self.data.get('activated', 0), venceEn=None, code=codigo.MAESTRA)
        if code.startswith('PC1:'):
            digits = code[4:]
            info = pc_code.verify(self._key(), digits)
            return dict(id=pc_code.license_id(self.data['deviceId'], digits), tipo=info['tipo'], estado=0,
                        secundarias=info['secundarias'], emitidaEn=self.data.get('activated', 0),
                        venceEn=pc_code.expiry_seconds(info['dia']), code=pc_code.grouped(digits))
        if code.lstrip().startswith('{'):
            info = self._abrir_larga(code, self.data.get('envelopeId'), int(self.clock()))
            tipo = info.get('tipo')
            secundarias = info.get('secundarias')
            return dict(id=info.get('id'), tipo=tipo, estado=3 if tipo == 'PERPETUA' else 0,
                        secundarias=secundarias if isinstance(secundarias, int) else SECUNDARIAS_DEFECTO,
                        emitidaEn=_instante(info.get('emitidaEn')) or self.data.get('activated', 0),
                        venceEn=_instante(info.get('venceEn')), code=info.get('licenciaObtenida') or info.get('codigoCorto'))
        return decode_license(code, self.private, self.data['deviceId'], self.firma_keys)

    def activate(self, code):
        """Activa lo que envíe el desarrollador, en este orden:

        1. la **clave maestra de desarrollo** `GLMK-PERM-MAXS-9999`, solo con `SPVI_DESARROLLO=1` (mitigación 1);
        2. el **código de 16 dígitos** de PC (`XXXX-XXXX-XXXX-XXXX`) emitido por GL;
        3. la **licencia corta** `SPVI2:…` (el formato del teléfono);
        4. la **licencia larga** del contrato v1 (envelope JSON + línea `ID:`).
        """
        if not isinstance(code, str) or len(code) > 8192: raise ValueError('Código no válido.')
        with self.lock:
            now = self.clock()
            failures = [t for t in getattr(self, 'failures', []) if now - t < FAILURE_WINDOW]
            if len(failures) >= MAX_FAILURES:
                raise ValueError('Demasiados intentos. Espera unos minutos y vuelve a intentarlo.')
            digits = pc_code.extract(code)
            envelope_id = envelope.extraer_id(code) if envelope.extraer_envelope(code) else None
            try:
                if codigo.es_maestra(code):
                    stored, ident = 'MASTER', None
                elif digits:
                    info = pc_code.verify(self._key(), digits)
                    expires = pc_code.expiry_seconds(info['dia'])
                    if expires and now >= expires: raise ValueError('La licencia no está vigente.')
                    stored, ident = 'PC1:' + digits, None
                elif 'SPVI2:' in code:
                    info = decode_license(code, self.private, self.data['deviceId'], self.firma_keys)
                    if info['estado'] in (1, 2): raise ValueError('Licencia vencida o revocada.')
                    stored, ident = info['code'], None
                elif envelope_id:
                    self._abrir_larga(code, envelope_id, now)  # valida antes de guardar
                    stored, ident = envelope.extraer_envelope(code), envelope_id
                else:
                    raise ValueError('Escribe el código de tu licencia (16 caracteres o SPVI2:…).')
            except ValueError:
                failures.append(now); self.failures = failures
                raise
            self.failures = []
            self.data['code'] = stored; self.data['activated'] = int(now)
            self.data['envelopeId'] = ident
            self._save()
            return self.status()

    def _abrir_larga(self, mensaje, letra_id, ahora):
        """Licencia **larga** del contrato v1: firma de GL, descifrado con la clave del equipo y validaciones.

        El `ID:` viaja fuera del sobre porque la AAD del GCM lo necesita antes de descifrar.
        """
        try:
            documento = json.loads(envelope.extraer_envelope(mensaje))
        except ValueError:
            raise ValueError('La licencia no es válida para esta instalación.') from None
        if not envelope.verificar_firma(documento, self.firma_keys):
            raise ValueError('La firma no corresponde al emisor de SPVI.')
        try:
            plano = envelope.abrir(documento, self.private, envelope.aad_licencia(letra_id))
        except ValueError:
            try:
                plano = envelope.abrir(documento, self.private, envelope.aad_licencia_heredada(letra_id))
            except ValueError:
                raise ValueError('La licencia no es válida para esta instalación.') from None
        try:
            info = json.loads(plano)
        except ValueError:
            raise ValueError('La licencia no es válida para esta instalación.') from None
        if str(info.get('appName', '')).upper() != envelope.APP:
            raise ValueError('La licencia es de otra aplicación.')
        if info.get('deviceId') != self.data['deviceId']:
            raise ValueError('La licencia corresponde a otro equipo.')
        if str(info.get('id', '')).lower() != str(letra_id).lower():
            raise ValueError('La licencia no es válida para esta instalación.')
        codigo16 = info.get('licenciaObtenida') or info.get('codigoCorto')
        if codigo16 is not None and not codigo.valido(codigo16):
            raise ValueError('La licencia no es válida para esta instalación.')
        if info.get('licenciaObtenida') and info.get('codigoCorto') and info['licenciaObtenida'] != info['codigoCorto']:
            raise ValueError('La licencia no es válida para esta instalación.')
        if info.get('estado') not in ('ACTIVA', 'PERPETUA'):
            raise ValueError('Licencia vencida o revocada.')
        perpetua = info.get('tipo') == 'PERPETUA'
        if perpetua != (info.get('venceEn') in (None, '')) and info.get('estado') == 'PERPETUA':
            raise ValueError('La licencia no es válida para esta instalación.')
        vence = _instante(info.get('venceEn'))
        if vence is None and not perpetua:
            raise ValueError('La licencia no es válida para esta instalación.')
        if vence is not None and ahora >= vence:
            raise ValueError('La licencia no está vigente.')
        return info

    def emitir_subtoken(self, dispositivo, ranura):
        """Firma (con la clave del equipo) el sub-token de la ranura `ranura` para [dispositivo].

        Mitigación 7, Opción A: así una app secundaria no se auto-declara autorizada. Devuelve `None` si la licencia
        está bloqueada, si la cuota es 0 o si la ranura se sale de la cuota.
        """
        with self.lock:
            state = self.status()
            if state['clase'] == 'BLOQUEADA':
                return None
            cuota = int(state.get('secundarias') or 0)
            if cuota <= 0 or not isinstance(ranura, int) or not 1 <= ranura <= cuota:
                return None
            vence = None if not state.get('venceEn') else datetime.fromtimestamp(state['venceEn'] / 1000, timezone.utc)
            try:
                return subtokens.emitir(self.private, state.get('id') or subtokens.PRUEBA_ID, cuota, vence,
                                        str(dispositivo), ranura)
            except ValueError:
                return None

    def firma_publica(self):
        """SPKI Base64 de la clave con la que este equipo firma los sub-tokens (viaja con ellos)."""
        return base64.b64encode(self.private.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)).decode('ascii')

    def verificar_subtoken(self, token, firma_publica, dispositivo):
        """Datos del sub-token que entregó la app principal, o `None` si no autoriza a [dispositivo].

        La cuota con la que se comprueba es la de la licencia propia (una app sin licencia no acepta sub-tokens).
        """
        state = self.status()
        cuota = int(state.get('secundarias') or 0)
        if cuota <= 0:
            return None
        ahora = datetime.fromtimestamp(self.clock(), timezone.utc)
        return subtokens.verificar(token, firma_publica, str(dispositivo), ahora, cuota_maxima=cuota)

    def request(self, data):
        """Solicitud cifrada ``SPVIR1`` de la plataforma PC con el número de transacción del pago."""
        fields = {k: str(data.get(k, '')).strip() for k in ('nombre', 'apellidos', 'ci', 'telefono', 'tipo')}
        if not NAME_RE.fullmatch(fields['nombre']) or not NAME_RE.fullmatch(fields['apellidos']):
            raise ValueError('Escribe nombre y apellidos válidos (solo letras, entre 2 y 80 caracteres).')
        from ..dto import identity_number, telephone
        fields['ci']=identity_number(fields['ci']);fields['telefono']=telephone(fields['telefono'])
        if fields['tipo'] not in TYPES: raise ValueError('Elige el tipo de licencia.')
        n = data.get('secundarias', 0)
        if type(n) is not int or not 0 <= n <= 10: raise ValueError('Elige entre 0 y 10 secundarias.')
        transaction = str(data.get('transaccion', '') or '').strip()
        recover = str(data.get('recupera', '') or '').strip()
        if transaction and not TRANSACTION.fullmatch(transaction):
            raise ValueError('El número de transacción tiene de 4 a 40 letras, números o guiones.')
        if not transaction and not recover:
            raise ValueError('Escribe el número de transacción del pago.')
        payload = dict(fields, v=2, via='WHATSAPP', plataforma='PC', deviceId=self.data['deviceId'], secundarias=n,
                       solicitadaEn=datetime.fromtimestamp(self.clock(),timezone.utc).isoformat().replace('+00:00','Z'),
                       nonce=enc(os.urandom(16)), devicePub=enc(compressed(self.private.public_key())))
        if transaction: payload['transaccion'] = transaction
        if recover:
            try: payload['recupera'] = str(uuid.UUID(recover))
            except ValueError as error: raise ValueError('El ID de la licencia anterior no es válido.') from error
        elif self.data['code']:
            info = self._installed()
            if info['tipo'] != 'PERPETUA': payload['renueva'] = info['id']
        ephemeral = ec.generate_private_key(ec.SECP256R1())
        epk = compressed(ephemeral.public_key())
        public = serialization.load_der_public_key(base64.b64decode(trust.ECDH))
        key = derive(ephemeral.exchange(ec.ECDH(), public), epk, b'SPVI-R1')
        raw = json.dumps(payload, ensure_ascii=False,separators=(',',':')).encode()
        cipher = AESGCM(key[:32]).encrypt(key[32:], raw, b'SPVI-R1|' + epk)
        return 'SPVIR1:' + enc(epk + cipher)

    def request_message(self, data):
        """Texto para WhatsApp (datos legibles + solicitud cifrada) y su enlace ``wa.me``, que se muestra como QR."""
        code = self.request(data)
        lines = ['Solicitud de licencia SPVI', 'Plataforma: PC',
                 'Nombre: %s %s' % (str(data.get('nombre', '')).strip(), str(data.get('apellidos', '')).strip())]
        for label, field in (('CI', 'ci'), ('Teléfono', 'telefono')):
            if str(data.get(field, '') or '').strip(): lines.append('%s: %s' % (label, str(data[field]).strip()))
        lines += ['Tipo: ' + TYPE_LABELS[data['tipo']], 'Apps secundarias: %d' % data.get('secundarias', 0)]
        if str(data.get('transaccion', '') or '').strip(): lines.append('Transacción: ' + str(data['transaccion']).strip())
        if str(data.get('recupera', '') or '').strip(): lines.append('Recuperar licencia: ' + str(data['recupera']).strip())
        lines.append(AVISO_FRESCURA)
        message = '\n'.join(lines) + '\n\n' + code
        return dict(code=code, message=message, url='https://wa.me/%s?text=%s' % (DEVELOPER, quote(message, safe='')))

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
