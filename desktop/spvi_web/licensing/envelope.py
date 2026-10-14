"""Envelope JSON v1 del contrato GL (solicitud y licencia larga).

Espejo de `contract/EnvelopeV1.kt` (Android) y de `licensing/envelope.dart` (iOS). Referencia:
`SISTEMA_LICENCIAS_CLIENTES.md` §4–§6 y `SECURITY.md` (mitigaciones 5, 6 y 8) del repositorio GL.

* `alg` = `ECIES-P256-AES256GCM-v1`; ECDH P-256 → HKDF-SHA256 (sal = IV, info `gl-req-v1` / `gl-lic-v1`) →
  AES-256-GCM; firma ECDSA P-256/SHA-256 (DER) sobre `epk ‖ iv ‖ ct ‖ tag`.
* La AAD va **canonicalizada con longitud prefijada** (4 bytes big-endian por campo), como `CryptoEngine.canonical`
  de GL: mitigación 8 (inyección de delimitadores). La AAD heredada (`1|alg|id`) solo se acepta al descifrar.
"""
import base64
import json
import os
import re
from datetime import datetime, timezone

from cryptography.exceptions import InvalidSignature, InvalidTag
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, utils
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

VERSION = 1
ALG = 'ECIES-P256-AES256GCM-v1'
KID = 'gl-sign-v1'
INFO_SOLICITUD = 'gl-req-v1'
INFO_LICENCIA = 'gl-lic-v1'
IV_LARGO = 12
APP = 'SPVI'


def b64(data):
    return base64.b64encode(data).decode('ascii')


def deb64(texto):
    return base64.b64decode(texto, validate=True)


def canonical(partes):
    """AAD con longitud prefijada (4 bytes big-endian), igual que GL."""
    salida = bytearray()
    for parte in partes:
        datos = str(parte).encode('utf-8')
        salida += len(datos).to_bytes(4, 'big') + datos
    return bytes(salida)


def aad_solicitud(epk_b64):
    return canonical([VERSION, ALG, epk_b64, KID])


def aad_licencia(license_id):
    return canonical([VERSION, ALG, license_id])


def aad_solicitud_heredada(epk_b64):
    """AAD anterior a la mitigación 8 (solo para abrir lo ya emitido con ella)."""
    return f'{VERSION}|{ALG}|{epk_b64}|{KID}'.encode()


def aad_licencia_heredada(license_id):
    return f'{VERSION}|{ALG}|{license_id}'.encode()


def derivar(compartido, sal, info):
    """HKDF-SHA256 (sal = IV, 32 B) → clave AES-256. El IV del GCM es el mismo que la sal, como en GL."""
    etiqueta = info.encode('utf-8') if isinstance(info, str) else info
    return HKDF(algorithm=hashes.SHA256(), length=32, salt=sal, info=etiqueta).derive(compartido)


def _firma(privada, datos):
    return privada.sign(datos, ec.ECDSA(hashes.SHA256()))


def sellar(plano, peer_public, firmante, info, aad, iv=None):
    """Cifra [plano] hacia [peer_public] y firma el transcript. Devuelve el envelope como `dict`.

    [firmante] `None` = firma la **efímera** del propio envelope (lo que hace el cliente en una solicitud: GL acepta
    la firma de la efímera). Para una licencia, GL pasa su clave de firma (`gl-sign-v1`).
    """
    efimera = ec.generate_private_key(ec.SECP256R1())
    iv = iv or os.urandom(IV_LARGO)
    epk_der = efimera.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    clave = derivar(efimera.exchange(ec.ECDH(), peer_public), iv, info)
    cifrado = AESGCM(clave).encrypt(iv, plano, aad(epk_der))
    ct, tag = cifrado[:-16], cifrado[-16:]
    return dict(
        v=VERSION, alg=ALG, epk=b64(epk_der), iv=b64(iv), ct=b64(ct), tag=b64(tag),
        sig=b64(_firma(firmante or efimera, epk_der + iv + ct + tag)), kid=KID,
    )


def abrir(envelope, privada, aad, info=INFO_LICENCIA):
    """Descifra un envelope con la clave privada ECDH propia ([info] = `gl-req-v1` o `gl-lic-v1`).

    Levanta ValueError si algo no cuadra.
    """
    if envelope.get('v') != VERSION or envelope.get('alg') != ALG:
        raise ValueError('Envelope no válido.')
    epk_der = deb64(envelope['epk'])
    iv, ct, tag = deb64(envelope['iv']), deb64(envelope['ct']), deb64(envelope['tag'])
    if len(iv) != IV_LARGO or len(tag) != 16:
        raise ValueError('Envelope no válido.')
    peer = serialization.load_der_public_key(epk_der)
    if not isinstance(peer, ec.EllipticCurvePublicKey) or not isinstance(peer.curve, ec.SECP256R1):
        raise ValueError('Envelope no válido.')
    clave = derivar(privada.exchange(ec.ECDH(), peer), iv, info)
    try:
        return AESGCM(clave).decrypt(iv, ct + tag, aad)
    except InvalidTag:
        raise ValueError('No se pudo descifrar el envelope.') from None


def verificar_firma(envelope, publicas):
    """Comprueba la firma del emisor sobre `epk ‖ iv ‖ ct ‖ tag` (sin descifrar). No lanza."""
    try:
        transcript = deb64(envelope['epk']) + deb64(envelope['iv']) + deb64(envelope['ct']) + deb64(envelope['tag'])
        firma = deb64(envelope['sig'])
        for spki in publicas:
            publica = serialization.load_der_public_key(base64.b64decode(spki))
            try:
                publica.verify(firma, transcript, ec.ECDSA(hashes.SHA256()))
                return True
            except InvalidSignature:
                continue
    except Exception:
        return False
    return False


def firma_de_la_efimera(envelope):
    """Firma del cliente sobre el transcript: la comprueba la efímera del propio envelope."""
    if envelope.get('v') != VERSION or envelope.get('alg') != ALG:
        return False
    try:
        transcript = deb64(envelope['epk']) + deb64(envelope['iv']) + deb64(envelope['ct']) + deb64(envelope['tag'])
        epk = serialization.load_der_public_key(deb64(envelope['epk']))
        epk.verify(deb64(envelope['sig']), transcript, ec.ECDSA(hashes.SHA256()))
        return True
    except Exception:
        return False


_RX_INICIO = re.compile(r'\{\s*"v"\s*:\s*1\s*,')
_RX_ID = re.compile(r'(?m)^\s*ID\s*:\s*([A-Za-z0-9\-]{8,64})\s*$')


def extraer_envelope(texto):
    """Saca el envelope JSON de un mensaje (cobro + `ID:` + envelope). `None` si no hay."""
    if not isinstance(texto, str):
        return None
    inicio = _RX_INICIO.search(texto)
    if not inicio:
        return None
    fin = texto.rfind('}')
    if fin <= inicio.start():
        return None
    return texto[inicio.start():fin + 1]


def extraer_id(texto):
    """`ID:` del mensaje de GL (la licencia larga lo lleva FUERA del sobre: la AAD lo necesita antes de descifrar)."""
    if not isinstance(texto, str):
        return None
    encontrado = _RX_ID.search(texto)
    return encontrado.group(1) if encontrado else None


def es_envelope(texto):
    datos = extraer_envelope(texto)
    if not datos:
        return False
    try:
        envelope = json.loads(datos)
    except ValueError:
        return False
    return envelope.get('v') == VERSION and envelope.get('alg') == ALG and envelope.get('kid') == KID


def solicitud_v1(nombre, apellidos, ci, telefono, tipo, secundarias, device_id, device_pub_spki,
                 plataforma='PC', nonce=None):
    """Payload plano de la solicitud v1 (§4.2).

    `devicePub` es la clave PERSISTENTE del equipo en SPKI Base64 (el contrato la exige: mitigación 5) y el bloque
    `usuario` duplica los datos del solicitante, como en el esquema.
    """
    usuario = dict(nombre=nombre, apellidos=apellidos, ci=ci, telefono=telefono, via='WHATSAPP')
    instante = datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')
    return dict(
        v=VERSION, deviceId=device_id, appName=APP, dispositivo=plataforma, nombre=nombre, apellidos=apellidos,
        ci=ci, via='WHATSAPP', telefono=telefono, usuario=usuario, tipo=tipo, secundarias=secundarias,
        solicitadaEn=instante,
        nonce=base64.urlsafe_b64encode(nonce or os.urandom(16)).rstrip(b'=').decode('ascii'),
        devicePub=b64(device_pub_spki),
    )
