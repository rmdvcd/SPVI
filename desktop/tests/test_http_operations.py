"""Mutaciones HTTP atómicas, claves de reintento y fotos; no ejecutadas aquí."""
import base64
from io import BytesIO
import unittest
from PIL import Image
from tests import test_web
from spvi_web.business import objects


class HttpOperationsTest(unittest.TestCase):
    setUpClass=classmethod(test_web.HttpTest.setUpClass.__func__)
    setUp=test_web.HttpTest.setUp
    csrf=test_web.HttpTest.csrf
    post=test_web.HttpTest.post
    login=test_web.HttpTest.login

    def prepare(self):
        self.login()
        self.assertEqual(200,self.post('/api/item',dict(name='Café',kind='producto',price=10,cost=2,stock=10,minimum=0)).status_code)
        self.assertEqual(200,self.post('/api/open',dict(fund=100)).status_code)

    def keyed(self,data,key='operacion-prueba-12345',path='/api/cart'):
        return self.client.post(path,json=data,headers={'X-CSRF-Token':self.token,'Idempotency-Key':key})

    def test_reintento_persistido_y_conflicto(self):
        self.prepare();payload=dict(lines=[dict(item_id=1,quantity=2)],method='efectivo')
        self.assertEqual(200,self.keyed(payload).status_code)
        self.assertEqual(200,self.keyed(payload).status_code)
        self.assertEqual(1,self.client.get('/api/dashboard').json['operations'])
        payload['lines'][0]['quantity']=3
        self.assertEqual(409,self.keyed(payload).status_code)
        self.assertEqual(12000,self.client.get('/api/dashboard').json['expected'])

    def test_error_no_reserva_clave_ni_stock(self):
        self.prepare();payload=dict(lines=[dict(item_id=1,quantity=200)],method='efectivo')
        self.assertEqual(400,self.keyed(payload).status_code)
        payload['lines'][0]['quantity']=1
        self.assertEqual(200,self.keyed(payload).status_code)
        self.assertEqual(11000,self.client.get('/api/dashboard').json['expected'])

    def test_alta_catalogo_idempotente(self):
        self.login();now=1700000000000
        payload=dict(t='insumo_guardar',insumo=dict(id=0,nombre='Azúcar',unidad='KILOGRAMO',precioCent=100,cantidadMil=2000,creadoEn=now,actualizadoEn=now))
        first=self.keyed(payload,path='/api/catalog');second=self.keyed(payload,path='/api/catalog')
        self.assertEqual(200,first.status_code);self.assertEqual(first.json,second.json)
        self.assertEqual(1,len(self.client.get('/api/records').json['insumos']))

    def test_foto_normalizada_y_sin_rutas(self):
        self.prepare();image=Image.new('RGB',(32,24),'white');buffer=BytesIO();image.save(buffer,'PNG');image.close()
        payload=dict(kind='productos',id=1,content=base64.b64encode(buffer.getvalue()).decode())
        self.assertEqual(200,self.keyed(payload,path='/api/photo').status_code)
        response=self.client.get('/api/photo/productos/1')
        self.assertEqual('image/jpeg',response.mimetype);self.assertTrue(response.data.startswith(b'\xff\xd8'))
        self.assertEqual(400,self.keyed(dict(payload,content='file:///etc/passwd'),key='otra-operacion-12345',path='/api/photo').status_code)
        with self.app.extensions['store'].connect() as db:
            self.assertEqual(1,len(objects(db,'productos')))

    def test_negocio_anterior_no_puede_mutar_el_actual(self):
        self.prepare()
        response=self.client.post('/api/cart',json=dict(lines=[dict(item_id=1,quantity=1)],method='efectivo'),headers={'X-CSRF-Token':self.token,'Idempotency-Key':'peticion-anterior-12345','X-SPVI-Business':'negocio-anterior'})
        self.assertEqual(409,response.status_code)
        self.assertEqual(0,self.client.get('/api/dashboard').json['operations'])
