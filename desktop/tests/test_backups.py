import tempfile
import unittest
from pathlib import Path
from spvi_web.store import Store
from spvi_web.backups import backup, restore, seal, unseal, validate

PASSWORD='respaldo-de-prueba'


class BackupTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.store=Store(Path(self.temp.name)/'test.sqlite3')
        self.store.operate('item',dict(name='Café',kind='producto',price=20,cost=3,stock=5))

    def test_roundtrip_and_safety_copy(self):
        self.store.operate('open',dict(fund=10))
        self.store.operate('sale',dict(item_id=1,quantity=1,method='efectivo'))
        self.store.operate('close',dict(counted=30))
        data=backup(self.store,PASSWORD)
        self.store.operate('item',dict(name='Otro',kind='servicio',price=5,cost=1))
        restore(self.store,data,PASSWORD)
        self.assertEqual(1,len(self.store.dashboard()['items']))
        self.assertEqual(2000,self.store.dashboard()['revenue'])
        previous=unseal((Path(self.temp.name)/'antes_de_restaurar.spvidesk').read_bytes(),PASSWORD)
        self.assertEqual(2,len(previous['tables']['items']))

    def test_wrong_password_and_tampering_leave_data_unchanged(self):
        data=backup(self.store,PASSWORD)
        for blob,pwd in [(data,'otra-clave-larga'),(data[:-1]+bytes([data[-1]^1]),PASSWORD),(data[:-5],PASSWORD)]:
            with self.assertRaises(ValueError): restore(self.store,blob,pwd)
        self.assertEqual(1,len(self.store.dashboard()['items']))

    def test_open_shifts_block_restore(self):
        data=backup(self.store,PASSWORD)
        self.store.operate('open',dict(fund=10))
        open_copy=backup(self.store,PASSWORD)
        with self.assertRaises(ValueError): restore(self.store,data,PASSWORD)
        self.store.operate('close',dict(counted=10))
        with self.assertRaises(ValueError): restore(self.store,open_copy,PASSWORD)

    def test_invalid_rows_do_not_delete_current_data(self):
        doc=unseal(backup(self.store,PASSWORD),PASSWORD)
        doc['tables']['items'][0]['price']=-1
        with self.assertRaises(ValueError): restore(self.store,seal(doc,PASSWORD),PASSWORD)
        self.assertEqual(2000,self.store.dashboard()['items'][0]['price'])

    def test_no_config_or_credentials_in_backup(self):
        doc=unseal(backup(self.store,PASSWORD),PASSWORD)
        self.assertEqual({'items','shifts','sales','movements'},set(doc['tables']))
        with self.assertRaises(ValueError): validate({'format':'spvi-respaldo','version':4})

    def test_unique_random_encryption(self):
        a=backup(self.store,PASSWORD);b=backup(self.store,PASSWORD)
        self.assertNotEqual(a,b);self.assertNotIn(b'Caf',a)
        self.assertEqual(unseal(a,PASSWORD),unseal(b,PASSWORD))
