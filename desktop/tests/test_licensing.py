"""Contrato GL con claves efímeras de prueba; nunca usa secretos del emisor real."""
import base64
import hashlib
import tempfile
import unittest
import uuid
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import ec,utils
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from spvi_web.licensing.client import LicenseClient,decode_license,compressed,derive,enc
from spvi_web.secrets import SecretBox


class LicensingTest(unittest.TestCase):
    def test_prueba_reinicio_expiracion_y_reloj(self):
        with tempfile.TemporaryDirectory() as folder:
            clock=[1700000000];box=SecretBox('pruebas')
            client=LicenseClient(folder,box,lambda:clock[0]);identity=client.data['deviceId']
            self.assertEqual('PRUEBA',client.status()['clase'])
            self.assertEqual(identity,LicenseClient(folder,box,lambda:clock[0]).data['deviceId'])
            clock[0]+=8*86400
            with self.assertRaises(ValueError): client.require_active()
            clock[0]-=9*86400
            self.assertIn('fecha',client.status()['motivo'])

    def test_licencia_firma_y_vinculo_a_instalacion(self):
        device=ec.generate_private_key(ec.SECP256R1());issuer=ec.generate_private_key(ec.SECP256R1());signer=ec.generate_private_key(ec.SECP256R1())
        epk=compressed(issuer.public_key());device_id='SPVI:prueba'
        fingerprint=hashlib.sha256(('SPVI-L2|'+device_id+'|').encode()+compressed(device.public_key())).digest()[:16]
        body=b'\x02'+uuid.uuid4().bytes+fingerprint+bytes((0,0,3))+(1700000000).to_bytes(4,'big')+(1703000000).to_bytes(4,'big')
        key=derive(issuer.exchange(ec.ECDH(),device.public_key()),epk,b'SPVI-L2')
        cipher=AESGCM(key[:32]).encrypt(key[32:],body,b'SPVI-L2|'+epk)
        r,s=utils.decode_dss_signature(signer.sign(b'SPVI-L2|'+epk+cipher,ec.ECDSA(hashes.SHA256())))
        raw=epk+cipher+r.to_bytes(32,'big')+s.to_bytes(32,'big')
        public=base64.b64encode(signer.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)).decode()
        code='SPVI2:'+enc(raw)
        self.assertEqual(3,decode_license(code,device,device_id,(public,))['secundarias'])
        with self.assertRaises(ValueError): decode_license(code,device,'otra instalación',(public,))
        with self.assertRaises(ValueError): decode_license(code,device,device_id)
        with self.assertRaises(ValueError): decode_license('SPVI2:'+enc(raw[:-1]+bytes([raw[-1]^1])),device,device_id,(public,))
