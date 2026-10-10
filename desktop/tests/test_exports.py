import tempfile
import unittest
from decimal import Decimal
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
from openpyxl import load_workbook
from spvi_web.store import Store
from spvi_web.exports import export, table


class ExportsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.store = Store(Path(self.temp.name) / 'test.sqlite3')
        for name, kind in [('Café','producto'), ('=HYPERLINK("x")','producto'), ('Corte','servicio')]:
            self.store.operate('item', dict(name=name, kind=kind, price='25.10', cost='12.30', stock=8))

    def test_policy_rejects_wrong_combinations(self):
        for scope, fmt in [('turnos','imagen'), ('inventario','tarjetas'), ('ventas','pdf'), ('productos','xlsx')]:
            with self.subTest(scope=scope, fmt=fmt), self.assertRaises(ValueError): export(self.store,scope,fmt)

    def test_excel_real_numeric_cells_and_no_formulas(self):
        data, ext = export(self.store,'inventario','xlsx')
        self.assertEqual('xlsx',ext)
        book = load_workbook(BytesIO(data)); self.addCleanup(book.close)
        self.assertEqual('s',book.active['A2'].data_type)
        self.assertEqual('=HYPERLINK("x")',book.active['A2'].value)
        self.assertEqual(25.1,book.active['B2'].value)
        self.assertEqual('A2',book.active.freeze_panes)

    def test_pdf_file(self):
        data, ext = export(self.store,'servicios','pdf')
        self.assertTrue(data.startswith(b'%PDF-')); self.assertEqual('pdf',ext)

    def test_images_paginated_and_comments_change_image(self):
        data, ext = export(self.store,'productos','tarjetas', 'Oferta de hoy')
        self.assertEqual('zip',ext)
        with ZipFile(BytesIO(data)) as archive:
            self.assertEqual(2,len(archive.namelist()))
            with Image.open(BytesIO(archive.read('SPVI_1.png'))) as image: self.assertEqual(1080,image.width)
        a, _ = export(self.store,'servicios','tarjetas')
        b, _ = export(self.store,'servicios','tarjetas','Promoción')
        self.assertNotEqual(a,b)

    def test_commercial_images_do_not_depend_on_private_cost_or_stock(self):
        a, _ = export(self.store,'productos','imagen')
        with self.store.connect() as db: db.execute('UPDATE items SET cost=999999,stock=3456')
        b, _ = export(self.store,'productos','imagen')
        self.assertEqual(a,b)

    def test_filter_and_selection(self):
        cols, rows, items = table(self.store,'inventario',query='CAFÉ',ids=[1])
        self.assertEqual(1,len(items)); self.assertEqual(Decimal('25.10'),rows[0][1])
        with self.assertRaises(ValueError): export(self.store,'productos','imagen',ids=[])
        with self.assertRaises(ValueError): export(self.store,'productos','tarjetas','x'*161)

    def test_turnos_include_arqueo(self):
        self.store.operate('open',dict(fund=5)); self.store.operate('close',dict(counted=4))
        cols, rows, _ = table(self.store,'turnos')
        self.assertEqual(Decimal('-1'),rows[0][-1])
