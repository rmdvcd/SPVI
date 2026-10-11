"""Licencia de PC: código de 16 dígitos (XXXX-XXXX-XXXX-XXXX) emitido por GL.

Contrato «SPVI-P1» (espejo de ``dev.gl.license.security.SpviPc16`` en GL):

* ``Z = ECDH(clave privada del equipo, clave ECDH pública de GL)``; GL obtiene el mismo valor con su clave privada y la
  ``devicePub`` (33 bytes comprimidos) que viaja en la solicitud ``SPVIR1``.
* ``K = HKDF-SHA256(Z, sal = devicePub, info = "SPVI-P1|" + deviceId, 32 bytes)``.
* ``D`` (6 dígitos) = ``(tipo * 11 + secundarias) * 10000 + dia``; ``tipo`` 0–3 (mensual, semestral, anual, perpetua),
  ``secundarias`` 0–10 y ``dia`` = día de vencimiento contado desde el 2026-01-01 UTC (0 en la perpetua).
* ``M`` (10 dígitos) = primeros 8 bytes de ``HMAC-SHA256(K, "SPVI-P1|code|" + D)`` como entero, módulo 10¹⁰.
* ``mask`` = primeros 8 bytes de ``HMAC-SHA256(K, "SPVI-P1|mask|" + M)`` módulo 10⁶; ``D' = (D + mask) mod 10⁶``.
* Código = ``D'`` (6) + ``M`` (10). El ID de la licencia es un UUID derivado de ``deviceId`` y el código.

16 dígitos no admiten una firma asimétrica: la autenticidad descansa en ``K``, que solo conocen GL y esta instalación
(la clave privada del equipo se guarda protegida por el sistema en ``licencia.bin``).
"""
import hashlib
import hmac
import re
import uuid

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

EPOCH = 1767225600  # 2026-01-01T00:00:00Z
DAY = 86400
TYPES = ('MENSUAL', 'SEMESTRAL', 'ANUAL', 'PERPETUA')
_PATTERN = re.compile(r'(?<!\d)(\d{4})[\s\-]?(\d{4})[\s\-]?(\d{4})[\s\-]?(\d{4})(?!\d)')


def key(shared, device_pub, device_id):
    """Clave de 32 bytes común a GL y al equipo."""
    return HKDF(algorithm=hashes.SHA256(), length=32, salt=bytes(device_pub),
                info=('SPVI-P1|' + device_id).encode()).derive(shared)


def _number(k, label, digits, modulo):
    mac = hmac.new(k, ('SPVI-P1|' + label + '|' + digits).encode('ascii'), hashlib.sha256).digest()
    return int.from_bytes(mac[:8], 'big') % modulo


def issue(k, tipo, secundarias, dia):
    """Código de 16 dígitos sin guiones. Lo usa GL (y las pruebas)."""
    if not 0 <= tipo <= 3 or not 0 <= secundarias <= 10 or not 0 <= dia <= 9999:
        raise ValueError('Datos de licencia fuera de rango.')
    if (tipo == 3) != (dia == 0):
        raise ValueError('Solo la perpetua no tiene vencimiento.')
    data = (tipo * 11 + secundarias) * 10000 + dia
    mac = _number(k, 'code', '%06d' % data, 10 ** 10)
    mask = _number(k, 'mask', '%010d' % mac, 10 ** 6)
    return '%06d%010d' % ((data + mask) % 10 ** 6, mac)


def verify(k, code):
    """Devuelve ``dict(tipo, secundarias, dia)`` o lanza ``ValueError`` si el código no es de esta instalación."""
    if not isinstance(code, str) or not re.fullmatch(r'\d{16}', code):
        raise ValueError('Código no válido.')
    masked, mac = int(code[:6]), int(code[6:])
    data = (masked - _number(k, 'mask', code[6:], 10 ** 6)) % 10 ** 6
    expected = _number(k, 'code', '%06d' % data, 10 ** 10)
    if not hmac.compare_digest('%010d' % expected, '%010d' % mac):
        raise ValueError('Código no válido para esta instalación.')
    group, dia = divmod(data, 10000)
    tipo, secundarias = divmod(group, 11)
    if tipo > 3 or (tipo == 3) != (dia == 0):
        raise ValueError('Código no válido para esta instalación.')
    return dict(tipo=TYPES[tipo], secundarias=secundarias, dia=dia)


def extract(text):
    """Los 16 dígitos de un texto pegado (con o sin guiones/espacios), o ``None``."""
    if not isinstance(text, str):
        return None
    found = _PATTERN.search(text)
    return ''.join(found.groups()) if found else None


def grouped(code):
    return '-'.join(code[i:i + 4] for i in range(0, 16, 4))


def license_id(device_id, code):
    raw = bytearray(hashlib.sha256(('SPVI-P1|id|' + device_id + '|' + code).encode()).digest()[:16])
    raw[6] = (raw[6] & 0x0F) | 0x40
    raw[8] = (raw[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(raw)))


def expiry_seconds(dia):
    """Fin (exclusivo) del día de vencimiento: la licencia vale todo ese día UTC."""
    return EPOCH + (dia + 1) * DAY if dia else None


def day_of(seconds):
    return max(0, (int(seconds) - EPOCH) // DAY)
