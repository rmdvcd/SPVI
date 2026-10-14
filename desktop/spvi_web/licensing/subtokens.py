"""Sub-tokens de apps secundarias (mitigación 7, Opción A).

La cuota de apps secundarias deja de ser una auto-declaración y pasa a ser un permiso **firmado por la app
principal**. Formato (`SISTEMA_LICENCIAS_CLIENTES.md` §8.4), idéntico en las tres apps:

```
<licenseId>|<deviceId de la secundaria>|<ranura>|<permitidas>|<venceEn ISO-8601 | PERPETUA>.<firma Base64 DER>
```

* `ranura`: 1..`permitidas` (la cuota que trae la licencia de la principal).
* `venceEn`: el de la licencia principal; `PERPETUA` = sin vencimiento.
* La firma es ECDSA P-256/SHA-256 (DER) de la principal sobre el texto de la izquierda, tal cual.
"""
import base64
from datetime import datetime, timezone

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

PERPETUA = 'PERPETUA'
MAESTRA_ID = 'master-license-local'
PRUEBA_ID = 'prueba'
_SIN_VENCIMIENTO = (None, '')


def _spki(publica):
    if isinstance(publica, str):
        return base64.b64decode(publica)
    return publica.public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)


def texto(licencia_id, dispositivo, ranura, permitidas, vence_en=None):
    return '|'.join([str(licencia_id), str(dispositivo), str(int(ranura)), str(int(permitidas)),
                     PERPETUA if vence_en in _SIN_VENCIMIENTO else _iso(vence_en)])


def emitir(privada, licencia_id, permitidas, vence_en, dispositivo, ranura):
    """Firma el sub-token de la ranura `ranura` para `dispositivo`. Levanta ValueError si los datos no encajan."""
    if not isinstance(permitidas, int) or not 0 <= permitidas <= 10:
        raise ValueError('Cuota de secundarias no válida.')
    if not isinstance(ranura, int) or not 1 <= ranura <= permitidas:
        raise ValueError('Ranura fuera de la cuota de la licencia.')
    if not licencia_id or len(str(licencia_id)) > 64 or not dispositivo or len(str(dispositivo)) > 128:
        raise ValueError('Datos del sub-token no válidos.')
    if any(x in str(licencia_id) for x in '|.') or any(x in str(dispositivo) for x in '|.'):
        raise ValueError('Datos del sub-token no válidos.')
    contenido = texto(licencia_id, dispositivo, ranura, permitidas, vence_en)
    firma = privada.sign(contenido.encode(), ec.ECDSA(hashes.SHA256()))
    return contenido + '.' + base64.b64encode(firma).decode('ascii')


def verificar(token, publica, dispositivo_esperado, ahora, cuota_maxima=10):
    """Datos del sub-token o `None`. Comprueba firma, dispositivo, ranura, cuota, formato y vencimiento."""
    try:
        if not isinstance(token, str) or not token or len(token) > 4096:
            return None
        contenido, _, firma = token.rpartition('.')
        if not contenido or not firma:
            return None
        campos = contenido.split('|')
        if len(campos) != 5:
            return None
        clave = publica if hasattr(publica, 'verify') else serialization.load_der_public_key(_spki(publica))
        clave.verify(base64.b64decode(firma, validate=True), contenido.encode(), ec.ECDSA(hashes.SHA256()))
        ranura, permitidas = int(campos[2]), int(campos[3])
        if permitidas not in range(0, 11) or not 1 <= ranura <= permitidas or permitidas > cuota_maxima:
            return None
        if campos[1] != dispositivo_esperado:
            return None
        vence = None if campos[4] == PERPETUA else _parse(campos[4])
        if vence is not None and ahora >= vence:
            return None
        return dict(licenciaId=campos[0], dispositivo=campos[1], ranura=ranura, permitidas=permitidas, venceEn=vence)
    except (InvalidSignature, ValueError, TypeError, IndexError, KeyError):
        return None


def vigente(datos, ahora):
    return datos is not None and (datos['venceEn'] is None or ahora < datos['venceEn'])


def _iso(valor):
    if isinstance(valor, datetime):
        return valor.astimezone(timezone.utc).isoformat().replace('+00:00', 'Z')
    if isinstance(valor, (int, float)):
        return datetime.fromtimestamp(valor, timezone.utc).isoformat().replace('+00:00', 'Z')
    return str(valor)


def _parse(texto_iso):
    valor = str(texto_iso).replace('Z', '+00:00')
    momento = datetime.fromisoformat(valor)
    return momento if momento.tzinfo else momento.replace(tzinfo=timezone.utc)
