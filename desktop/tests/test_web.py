"""Pruebas sin red de caja, persistencia y controles HTTP."""
import re
from io import BytesIO
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from werkzeug.security import generate_password_hash
from spvi_web.app import create_app
from spvi_web.store import Store, money


class StoreTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'test.sqlite3'
        self.store = Store(self.path)
        self.store.operate('item', dict(name='Café', kind='producto', price='15.25', cost='5.10', stock=3, minimum=1))

    def sale(self, **extra):
        self.store.operate('sale', dict(item_id=1, quantity=1, method='efectivo', **extra))

    def test_closed_shift_blocks_sale_and_movement(self):
        with self.assertRaises(ValueError): self.sale()
        with self.assertRaises(ValueError): self.store.operate('movement', dict(amount=2, direction='entrada', reason='Fondo'))
        self.assertEqual(3, self.store.dashboard()['items'][0]['stock'])

    def test_arqueo_and_margin(self):
        self.store.operate('open', dict(fund=100))
        self.sale()
        self.store.operate('sale', dict(item_id=1, quantity=1, method='transferencia'))
        self.store.operate('movement', dict(amount=10, direction='salida', reason='Compra'))
        d = self.store.dashboard()
        self.assertEqual(10525, d['expected'])
        self.assertEqual(3050, d['revenue'])
        self.assertEqual(2030, d['margin'])
        self.assertEqual(1, len(d['low_stock']))
        self.store.operate('close', dict(counted=105))
        shift = self.store.dashboard()['shifts'][0]
        self.assertEqual(-25, shift['counted'] - shift['expected'])
        with self.assertRaises(ValueError): self.sale()

    def test_one_open_shift(self):
        self.store.operate('open', dict(fund=0))
        with self.assertRaises(ValueError): self.store.operate('open', dict(fund=5))
        self.assertEqual(1, len(self.store.dashboard()['shifts']))

    def test_failed_sale_rolls_back_stock(self):
        self.store.operate('open', dict(fund=0))
        for quantity, method in [(4,'efectivo'), (1,'otro'), (0,'efectivo'), (-1,'efectivo')]:
            with self.assertRaises(ValueError): self.store.operate('sale', dict(item_id=1, quantity=quantity, method=method))
        self.assertEqual(3, self.store.dashboard()['items'][0]['stock'])
        self.assertEqual(0, self.store.dashboard()['operations'])

    def test_concurrent_sales_do_not_oversell(self):
        self.store.operate('open', dict(fund=0))
        def sell(_):
            try: self.sale(); return True
            except ValueError: return False
        with ThreadPoolExecutor(max_workers=6) as pool:
            self.assertEqual(3, sum(pool.map(sell, range(10))))
        self.assertEqual(0, self.store.dashboard()['items'][0]['stock'])

    def test_service_has_no_stock_limit(self):
        self.store.operate('item', dict(name='Corte', kind='servicio', price=30, cost=2))
        self.store.operate('open', dict(fund=0))
        self.store.operate('sale', dict(item_id=2, quantity=10, method='efectivo'))
        self.assertEqual(30000, self.store.dashboard()['revenue'])

    def test_invalid_amounts(self):
        for value in ['NaN','Infinity','-1','0.001',None,'',True,'1000000000']:
            with self.subTest(value=value), self.assertRaises(ValueError): money(value)
        self.assertEqual(29, money('0.29'))

    def test_no_negative_cash(self):
        self.store.operate('open', dict(fund=0))
        with self.assertRaises(ValueError): self.store.operate('movement', dict(amount=1, direction='salida', reason='Compra'))
        self.assertEqual(0, self.store.dashboard()['expected'])

    def test_persistence(self):
        self.store.operate('open', dict(fund=10)); self.sale()
        self.assertEqual(1525, Store(self.path).dashboard()['revenue'])

    def test_dashboard_comparison_daily_and_below_cost(self):
        self.store.operate('open',dict(fund=0)); self.sale()
        with self.store.connect() as db:
            db.execute("UPDATE sales SET created=strftime('%Y-%m-%dT%H:%M:%SZ','now','-40 days')")
            db.execute('UPDATE items SET price=400')
        self.sale()
        dashboard=self.store.dashboard(30)
        self.assertEqual(1525,dashboard['previous_revenue'])
        self.assertEqual(400,dashboard['daily'][0]['revenue'])
        self.assertLess(dashboard['revenue_change_percent'],0)
        self.assertEqual(1,len(dashboard['below_cost']))

    def test_period_validation(self):
        with self.assertRaises(ValueError): self.store.dashboard(0)


class HttpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.password_hash = generate_password_hash('clave-de-pruebas')

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.app = create_app(self.temp.name, self.password_hash, 'solo-pruebas')
        self.app.testing = True
        self.client = self.app.test_client()
        self.csrf()

    def csrf(self):
        html = self.client.get('/').get_data(as_text=True)
        self.token = re.search(r'name="csrf" content="([^"]+)"', html).group(1)
        return html

    def post(self, path, data):
        return self.client.post(path, json=data, headers={'X-CSRF-Token':self.token})

    def login(self):
        self.assertEqual(200, self.post('/login', {'password':'clave-de-pruebas'}).status_code)
        self.csrf()

    def test_auth_required(self):
        self.assertEqual(401, self.client.get('/api/dashboard').status_code)
        self.assertEqual(401, self.post('/login', {'password':'incorrecta'}).status_code)

    def test_csrf_required_even_login(self):
        self.assertEqual(403, self.client.post('/login', json={'password':'clave-de-pruebas'}).status_code)
        self.login()
        self.assertEqual(403, self.client.post('/api/open', json={'fund':0}).status_code)

    def test_operations_and_logout(self):
        self.login()
        self.assertEqual(200, self.post('/api/open', {'fund':'0.29'}).status_code)
        self.assertEqual(29, self.client.get('/api/dashboard').json['expected'])
        self.assertEqual(400, self.post('/api/open', {'fund':0}).status_code)
        self.assertEqual(200, self.post('/api/logout', {}).status_code)
        self.assertEqual(401, self.client.get('/api/dashboard').status_code)

    def test_untrusted_host(self):
        self.assertEqual(400, self.client.get('/', headers={'Host':'attacker.example'}).status_code)

    def test_invalid_input_and_period(self):
        self.login()
        for data in [[], None, {'price':'NaN'}, {'kind':'otro'}]:
            self.assertEqual(400, self.post('/api/item', data).status_code)
        self.assertEqual(400, self.client.get('/api/dashboard?days=abc').status_code)
        self.assertEqual(400, self.post('/api/desconocido', {}).status_code)

    def test_download_requires_auth_and_csrf(self):
        self.assertEqual(401,self.post('/api/export',{'scope':'inventario','format':'pdf'}).status_code)
        self.login()
        self.assertEqual(403,self.client.post('/api/backup',json={'password':'clave-larga-prueba'}).status_code)
        self.assertEqual(403,self.client.post('/api/restore').status_code)

    def test_downloads_and_restore_over_http(self):
        self.login()
        self.post('/api/item',dict(name='Café',kind='producto',price=20,cost=1,stock=2))
        with self.post('/api/export',{'scope':'inventario','format':'pdf'}) as response:
            self.assertEqual(200,response.status_code)
            self.assertTrue(response.data.startswith(b'%PDF-'))
            self.assertIn('attachment',response.headers['Content-Disposition'])
        self.assertEqual(400,self.post('/api/export',{'scope':'turnos','format':'imagen'}).status_code)
        self.assertEqual(400,self.post('/api/export',{'scope':[], 'format':'pdf'}).status_code)
        with self.post('/api/backup',{'password':'clave-larga-prueba'}) as response:
            self.assertEqual(200,response.status_code)
            copy=response.data
        response=self.client.post('/api/restore',data={'file':(BytesIO(copy),'copy.spvidesk'),
            'password':'clave-larga-prueba','confirm':'RESTAURAR'}, headers={'X-CSRF-Token':self.token})
        self.assertEqual(200,response.status_code)
        self.assertEqual(1,len(self.client.get('/api/dashboard').json['items']))

    def test_restore_needs_confirmation_and_valid_file(self):
        self.login()
        self.assertEqual(400,self.post('/api/restore',{}).status_code)
        response=self.client.post('/api/restore',data={'file':(BytesIO(b'bad'),'copy.spvidesk'),
            'password':'clave-larga-prueba','confirm':'RESTAURAR'},headers={'X-CSRF-Token':self.token})
        self.assertEqual(400,response.status_code)
        self.assertEqual(400,self.post('/api/backup',{'password':'corta'}).status_code)

    def test_regular_posts_keep_small_size_limit(self):
        self.login()
        self.assertEqual(413,self.post('/api/item',{'name':'x'*17000}).status_code)

    def test_headers_and_assets(self):
        response = self.app.test_client().get('/')
        self.assertEqual('DENY', response.headers['X-Frame-Options'])
        self.assertEqual('no-store', response.headers['Cache-Control'])
        with self.client.get('/static/app.js') as asset:
            self.assertEqual(200, asset.status_code)
        self.assertIn('HttpOnly', response.headers.get('Set-Cookie', ''))

    def test_old_csrf_rejected_after_login(self):
        old = self.token; self.login()
        self.assertNotEqual(old, self.token)
        self.assertEqual(403, self.client.post('/api/open', json={'fund':0}, headers={'X-CSRF-Token':old}).status_code)


if __name__ == '__main__': unittest.main()
