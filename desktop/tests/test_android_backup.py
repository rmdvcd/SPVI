"""Contrato de contenedor Android: lecturas positivas y negativas sin red."""
import gzip
import hashlib
import json
import struct
import unittest
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from spvi_web.android_backup import encode,decode,MAGIC


class AndroidBackupTest(unittest.TestCase):
    def test_v4_con_y_sin_clave(self):
        doc=dict(formato='spvi-respaldo',version=4,productos=[],nombre='Café')
        for password in ('','contraseña de prueba'):
            blob=encode(doc,password)
            self.assertEqual(doc,decode(blob,password))
            with self.assertRaises(ValueError): decode(blob[:-1],password)
            damaged=bytearray(blob);damaged[-1]^=1
            with self.assertRaises(ValueError): decode(bytes(damaged),password)
        with self.assertRaises(ValueError): decode(encode(doc,'contraseña de prueba'),'incorrecta')

    def test_v3_construido_independientemente(self):
        doc=dict(formato='spvi-respaldo',version=3,productos=[])
        salt=bytes(range(16));iv=bytes(range(12));iterations=310000
        zipped=gzip.compress(json.dumps(doc).encode())
        aad=MAGIC+b'\x03'+struct.pack('>qI',1700000000000,iterations)+salt+iv+struct.pack('>q',len(zipped)+16)
        key=hashlib.pbkdf2_hmac('sha256',b'prueba',salt,iterations,32)
        cipher=AESGCM(key).encrypt(iv,zipped,aad)
        blob=aad+hashlib.sha256(cipher).digest()+cipher
        self.assertEqual(doc,decode(blob,'prueba'))

    def test_no_admite_version_futura(self):
        blob=bytearray(encode(dict(formato='spvi-respaldo',version=4),''));blob[7]=5
        with self.assertRaises(ValueError): decode(bytes(blob),'')
