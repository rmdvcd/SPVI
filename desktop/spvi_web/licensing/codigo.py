"""Código de licencia del contrato GL: 16 caracteres `[0-9A-Z]` en 4 grupos de 4 (`XXXX-XXXX-XXXX-XXXX`).

Espejo de `CodigoLicencia.kt` y de `CodigoLicencia` de la app iOS (`licensing/codigo.dart`). Referencia:
`SISTEMA_LICENCIAS_CLIENTES.md` §2.1–§2.2 del repositorio GL.
"""
import re

from . import trust

LARGO = 16
MAESTRA = trust.MAESTRA
_ALFABETO = re.compile(r'[0-9A-Z]{16}')
_SEPARADORES = re.compile(r'[\s\-]')
MAESTRA_NORMALIZADA = _SEPARADORES.sub('', MAESTRA)


def normalizar(texto):
    """Sin guiones ni espacios y en mayúsculas; `None` si no son 16 caracteres de `[0-9A-Z]`."""
    if not isinstance(texto, str):
        return None
    limpio = _SEPARADORES.sub('', texto.strip().upper())
    return limpio if _ALFABETO.fullmatch(limpio) else None


def valido(texto):
    return normalizar(texto) is not None


def formatear(texto):
    """`XXXX-XXXX-XXXX-XXXX`; `None` si el texto no es un código del contrato."""
    limpio = normalizar(texto)
    return '-'.join(limpio[i:i + 4] for i in range(0, LARGO, 4)) if limpio else None


def es_maestra(texto, debug=None):
    """Clave maestra de desarrollo: **solo** en modo desarrollo (mitigación 1, Opción A)."""
    if debug is None:
        debug = trust.es_debug()
    return bool(debug) and normalizar(texto) == MAESTRA_NORMALIZADA
