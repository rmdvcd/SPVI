"""Comprobaciones locales antes de abrir el navegador HTTPS."""
from datetime import datetime, timezone
from cryptography import x509
from cryptography.hazmat.primitives import serialization


def validate_certificate(cert_path, key_path, hostname='spvi.minegocio.cu'):
    try:
        cert=x509.load_pem_x509_certificate(cert_path.read_bytes())
        key=serialization.load_pem_private_key(key_path.read_bytes(),password=None)
        names=cert.extensions.get_extension_for_class(x509.SubjectAlternativeName).value.get_values_for_type(x509.DNSName)
        now=datetime.now(timezone.utc)
        if hostname.casefold() not in {name.casefold() for name in names}:
            raise ValueError('El certificado no corresponde al dominio SPVI.')
        if not cert.not_valid_before_utc <= now < cert.not_valid_after_utc:
            raise ValueError('El certificado no está vigente.')
        public=lambda k:k.public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
        if public(cert.public_key()) != public(key.public_key()):
            raise ValueError('El certificado y la clave no coinciden.')
    except (OSError,TypeError,x509.ExtensionNotFound):
        raise ValueError('No se pudieron leer el certificado y su clave PEM.') from None
