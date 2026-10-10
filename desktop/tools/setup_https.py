"""Genera una CA privada y un certificado local. No instala confianza ni modifica hosts."""
import argparse
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID, ExtendedKeyUsageOID


def generate(directory):
    directory=Path(directory)
    directory.mkdir(parents=True,exist_ok=True)
    paths=[directory/name for name in ('spvi-ca.crt','spvi.crt','spvi.key')]
    if any(path.exists() for path in paths):
        raise ValueError('Ya existen certificados. Usa una carpeta nueva para no sobrescribirlos.')
    now=datetime.now(timezone.utc)
    root_key=ec.generate_private_key(ec.SECP256R1()); server_key=ec.generate_private_key(ec.SECP256R1())
    root_name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'SPVI CA local')])
    ca=(x509.CertificateBuilder().subject_name(root_name).issuer_name(root_name).public_key(root_key.public_key())
        .serial_number(x509.random_serial_number()).not_valid_before(now-timedelta(minutes=5)).not_valid_after(now+timedelta(days=730))
        .add_extension(x509.BasicConstraints(ca=True,path_length=0),critical=True)
        .add_extension(x509.KeyUsage(False,False,False,False,False,True,True,False,False),critical=True)
        .sign(root_key,hashes.SHA256()))
    domain=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'spvi.minegocio.cu')])
    cert=(x509.CertificateBuilder().subject_name(domain).issuer_name(root_name).public_key(server_key.public_key())
          .serial_number(x509.random_serial_number()).not_valid_before(now-timedelta(minutes=5)).not_valid_after(now+timedelta(days=365))
          .add_extension(x509.BasicConstraints(ca=False,path_length=None),critical=True)
          .add_extension(x509.SubjectAlternativeName([x509.DNSName('spvi.minegocio.cu')]),critical=False)
          .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]),critical=False)
          .add_extension(x509.KeyUsage(True,False,False,False,False,False,False,False,False),critical=True)
          .sign(root_key,hashes.SHA256()))
    contents=[ca.public_bytes(serialization.Encoding.PEM),cert.public_bytes(serialization.Encoding.PEM),
              server_key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption())]
    for path,content in zip(paths,contents):
        descriptor=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(descriptor,'wb') as file: file.write(content)
    # La clave privada de la CA no se guarda: esta CA solo emitió el certificado local.
    return paths


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description='Preparar certificado HTTPS local de SPVI')
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    try: generate(args.output)
    except (ValueError,OSError) as error: parser.error(str(error))
    print('Certificados creados. Falta instalar explícitamente spvi-ca.crt como raíz confiable y configurar hosts.')
    print('No compartas spvi.key. Consulta README para configurar Windows y renovar el certificado.')
