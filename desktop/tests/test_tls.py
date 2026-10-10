import tempfile
import unittest
from pathlib import Path
from spvi_web.tls import validate_certificate
from tools.setup_https import generate


class TlsTest(unittest.TestCase):
    def test_generate_validate_and_refuse_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            ca,cert,key=generate(directory)
            validate_certificate(cert,key)
            self.assertTrue(ca.exists())
            self.assertFalse((Path(directory)/'ca.key').exists())
            with self.assertRaises(ValueError): generate(directory)
            with self.assertRaises(ValueError): validate_certificate(cert,key,'otro.dominio')

    def test_mismatched_key(self):
        with tempfile.TemporaryDirectory() as directory:
            _,cert,_=generate(Path(directory)/'uno')
            _,_,key=generate(Path(directory)/'dos')
            with self.assertRaises(ValueError): validate_certificate(cert,key)
