import json
import tempfile
import unittest
from pathlib import Path
from werkzeug.security import check_password_hash
from spvi_web.configuration import acquire_lock,reset_password


class ConfigurationTest(unittest.TestCase):
    def test_recuperar_clave_conserva_identidad(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'config.json'
            path.write_text(json.dumps(dict(secret='secreto-existente',password_hash='anterior')),encoding='utf-8')
            with acquire_lock(folder): reset_password(path,'nueva-clave-de-prueba')
            config=json.loads(path.read_text(encoding='utf-8'))
            self.assertEqual('secreto-existente',config['secret'])
            self.assertTrue(check_password_hash(config['password_hash'],'nueva-clave-de-prueba'))
            with self.assertRaises(ValueError): reset_password(path,'corta')

    def test_bloquea_segunda_instancia(self):
        with tempfile.TemporaryDirectory() as folder:
            with acquire_lock(folder):
                with self.assertRaises(ValueError): acquire_lock(folder)
            with acquire_lock(folder): pass
