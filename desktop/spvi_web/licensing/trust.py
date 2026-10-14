"""Claves públicas fijadas: espejo de LicenseTrust.kt, rotación 2026-10-05.

Las claves son de una instalación concreta de GL (Android Keystore del teléfono del emisor). Si el emisor las
regenera, hay que volver a fijarlas aquí y en las otras dos apps. La huella SHA-256 se comprueba con `huella()`.
"""
import base64
import hashlib
import os

ECDH = 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+bzI3jdfnqVmHOj3+O0dksDlQTJtdGLA1O0KP+dctJW3urECwUDhLEnS1DDu7cVeaVZZkdZCU8PuxGCcPw/hJA=='
ECDH_HUELLA = 'sha256:2a3f:9fce:b105:7ee8:563c:04cd:e4d1:00e7:058b:c7b8:0410:024b:e658:efe1:5905:666d'
SIGN = (
    'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1LFKFD7Af1xhbza5CrD7ArwOwq5h4qG70edEp54zozXoUa/r7P7xRQwwV82rXcy0mPznM3TNMk8wGxYCwR7ubg==',
    'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+UpBZNnTcsbL5yR5LQRD1qATSub6tV7dgaVKNgXjFUbJFbxWmUQWOQvqsOBZmt7bKDdu3iLW02Z/RtiPfa3OWQ==',
)
SIGN_HUELLAS = (
    'sha256:58d4:3aac:6f1b:099a:e86c:4375:b7c7:ffb5:8631:2399:246c:1f26:f861:e302:9c83:8c4e',
    'sha256:8a91:bcfb:19dc:af1b:af6e:0bb7:c82a:9410:e9b5:aa1d:fea0:cab8:241c:52d1:bcbd:6888',
)

# Clave maestra de desarrollo (GL). Solo se acepta en «modo desarrollo» (mitigación 1, Opción A).
MAESTRA = 'GLMK-PERM-MAXS-9999'
VARIABLE_DESARROLLO = 'SPVI_DESARROLLO'


def huella(spki_b64):
    """`sha256:xxxx:…` (formato de la pantalla Claves de GL) de una clave pública SPKI en Base64."""
    try:
        der = base64.b64decode(spki_b64, validate=True)
    except Exception:
        raise ValueError('Clave pública no válida.') from None
    raw = hashlib.sha256(der).hexdigest()
    return 'sha256:' + ':'.join(raw[i:i + 4] for i in range(0, 64, 4))


def es_debug(environ=os.environ):
    """¿Estamos en un build/ejecución de desarrollo? La variable se lee en cada llamada (los tests la cambian).

    Fail-closed: sin `SPVI_DESARROLLO=1` la clave maestra NO vale, ni siquiera con el código fuente a la vista.
    """
    return str(environ.get(VARIABLE_DESARROLLO, '')).strip() in ('1', 'true', 'si', 'sí', 'on')


def comprobar_fijadas():
    """Las claves del build coinciden con las huellas publicadas por GL (si no, se levanta el error)."""
    if huella(ECDH) != ECDH_HUELLA:
        raise ValueError('La clave ECDH fijada no coincide con su huella.')
    for clave, esperada in zip(SIGN, SIGN_HUELLAS):
        if huella(clave) != esperada:
            raise ValueError('La clave de firma fijada no coincide con su huella.')
    return True
