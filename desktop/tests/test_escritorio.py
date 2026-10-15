"""Pruebas sin red de la app de escritorio 0.31: código de licencia de 16 dígitos, preferencias, inicio automático,
datos de prueba, Registros, Tiempo en línea, rutas nuevas e iconos de la interfaz."""
import base64
import os
import re
import tempfile
import unittest
from pathlib import Path
from unittest import mock
from urllib.parse import unquote
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from werkzeug.security import generate_password_hash
from spvi_web import autostart, seed
from spvi_web.app import create_app
from spvi_web.configuration import Config
from spvi_web.licensing import pc_code, trust
from spvi_web.licensing.client import LicenseClient, compressed
from spvi_web.secrets import SecretBox

STATIC = Path(__file__).resolve().parent.parent / 'spvi_web' / 'static'

# Vectores fijos que GL (SpviPc16Test) debe reproducir: si cambian, los códigos ya emitidos dejarían de valer.
DEVICE_SCALAR = int('11' * 32, 16)
GL_SCALAR = int('22' * 32, 16)
DEVICE_ID = 'SPVI:vector-pc'
VECTOR_K = 'f8172d0216b94696475578e34812e7b9dad6d169b47a9d3ef8d72d29a4916791'
K0 = bytes(range(32))
VECTORS = [((0, 0, 30), '7291084215650869', 'f5c544bf-43b9-442f-81bc-f305e79b29c7'),
           ((1, 2, 210), '4352223125748983', 'fae3f2ea-69c6-47e7-82a7-146037a95f27'),
           ((2, 3, 400), '2442853808274450', '7f27dc10-b1d8-4052-9bfd-ce3895370026'),
           ((3, 10, 0), '2898511421339540', 'f011f57b-d990-4f03-b13b-c5e5d96c23e9'),
           ((2, 0, 9999), '3760330209272777', '10af3fee-076c-4a0c-ac3d-1f81d856f0a0')]


def gl_public_b64(private):
    return base64.b64encode(private.public_key().public_bytes(serialization.Encoding.DER,
                                                              serialization.PublicFormat.SubjectPublicKeyInfo)).decode()


class CodigoPcTest(unittest.TestCase):
    def test_vectores_fijos(self):
        device = ec.derive_private_key(DEVICE_SCALAR, ec.SECP256R1())
        gl = ec.derive_private_key(GL_SCALAR, ec.SECP256R1())
        shared = gl.exchange(ec.ECDH(), device.public_key())
        self.assertEqual(VECTOR_K, pc_code.key(shared, compressed(device.public_key()), DEVICE_ID).hex())
        for args, code, license in VECTORS:
            self.assertEqual(code, pc_code.issue(K0, *args))
            self.assertEqual(license, pc_code.license_id(DEVICE_ID, code))
            tipo, secundarias, dia = args
            self.assertEqual(dict(tipo=pc_code.TYPES[tipo], secundarias=secundarias, dia=dia), pc_code.verify(K0, code))

    def test_rechaza_otra_clave_y_alteraciones(self):
        code = pc_code.issue(K0, 2, 3, 400)
        with self.assertRaises(ValueError): pc_code.verify(bytes(32), code)
        for i in range(16):
            altered = code[:i] + str((int(code[i]) + 1) % 10) + code[i + 1:]
            with self.assertRaises(ValueError): pc_code.verify(K0, altered)
        with self.assertRaises(ValueError): pc_code.issue(K0, 3, 0, 5)     # perpetua con vencimiento
        with self.assertRaises(ValueError): pc_code.issue(K0, 0, 0, 0)     # temporal sin vencimiento
        with self.assertRaises(ValueError): pc_code.issue(K0, 0, 11, 5)

    def test_formato_y_vencimiento(self):
        self.assertEqual('1234-5678-9012-3456', pc_code.grouped('1234567890123456'))
        self.assertEqual('1234567890123456', pc_code.extract('Tu código: 1234 5678-9012 3456. Gracias'))
        self.assertIsNone(pc_code.extract('123456789012345'))
        self.assertEqual(pc_code.EPOCH + 31 * 86400, pc_code.expiry_seconds(30))
        self.assertIsNone(pc_code.expiry_seconds(0))
        self.assertEqual(30, pc_code.day_of(pc_code.EPOCH + 30 * 86400 + 5))


class ActivacionPcTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.gl = ec.generate_private_key(ec.SECP256R1())
        patcher = mock.patch.object(trust, 'ECDH', gl_public_b64(self.gl)); patcher.start(); self.addCleanup(patcher.stop)
        self.clock = [pc_code.EPOCH + 10 * 86400]
        self.client = LicenseClient(self.temp.name, SecretBox('pruebas'), lambda: self.clock[0])

    def emit(self, tipo, secundarias, dia):
        """Lo que hace GL: ECDH con la clave pública del equipo que viene en la solicitud."""
        public = self.client.private.public_key()
        k = pc_code.key(self.gl.exchange(ec.ECDH(), public), compressed(public), self.client.data['deviceId'])
        return pc_code.grouped(pc_code.issue(k, tipo, secundarias, dia))

    def test_activa_vence_y_rechaza_codigos_ajenos(self):
        self.assertEqual('PRUEBA', self.client.status()['clase'])
        with self.assertRaises(ValueError): self.client.activate('0000-0000-0000-0000')
        status = self.client.activate(self.emit(2, 3, 40))
        self.assertEqual(('ACTIVA', 'ANUAL', 3), (status['clase'], status['tipo'], status['secundarias']))
        self.assertEqual(pc_code.expiry_seconds(40) * 1000, status['venceEn'])
        self.clock[0] = pc_code.expiry_seconds(40)
        self.assertEqual('BLOQUEADA', self.client.status()['clase'])

    def test_perpetua_y_solicitud_por_whatsapp(self):
        self.assertEqual('PERPETUA', self.client.activate(self.emit(3, 1, 0))['clase'])
        message = self.client.request_message(dict(nombre='Ana', apellidos='Pérez', ci='85010112345', telefono='52123456',
                                                   tipo='ANUAL', secundarias=2, transaccion='TMS-12345'))
        self.assertTrue(message['url'].startswith('https://wa.me/5351815604?text='))
        text = unquote(message['url'].split('text=', 1)[1])
        for line in ('Solicitud de licencia SPVI', 'Plataforma: PC', 'Tipo: Anual', 'Apps secundarias: 2', 'Transacción: TMS-12345'):
            self.assertIn(line, text)
        self.assertIn('SPVIR1:', text)
        # GL (PR #3, 2026-10-14) solo emite solicitudes frescas: el mensaje avisa de las 72 h.
        self.assertIn('Vale 72 horas', text)
        with self.assertRaises(ValueError):
            self.client.request_message(dict(nombre='Ana', apellidos='Pérez', ci='85010112345', telefono='52123456', tipo='ANUAL'))

    def test_nombre_como_el_que_exige_gl(self):
        # Lo que GL rechazaría con InvalidPayload debe fallar aquí con mensaje claro, no en el emisor.
        for malo in ('A', 'Juan123', 'Ana_', 'x' * 81):
            with self.assertRaises(ValueError, msg=malo):
                self.client.request(dict(nombre=malo, apellidos='Pérez', ci='85010112345', telefono='52123456',
                                         tipo='ANUAL', secundarias=0, transaccion='TMS-12345'))
        for bueno in ('Ana', 'María José', "D'Angelo", 'García-López', 'J. Pérez'):
            self.client.request(dict(nombre=bueno, apellidos='Pérez', ci='85010112345', telefono='52123456',
                                     tipo='ANUAL', secundarias=0, transaccion='TMS-12345'))


class PreferenciasTest(unittest.TestCase):
    def test_valores_validos_y_persistencia(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'config.json'
            config = Config(path)
            self.assertIsNone(config.password_hash)
            self.assertTrue(config.check('cualquiera'))
            self.assertEqual('sistema', config.preferences()['tema'])
            config.update_preferences(dict(tema='oscuro', retrasoSegundos=30, inicioConWindows=True, red=dict(host='192.168.1.5', port=47811)))
            again = Config(path).preferences()
            self.assertEqual(('oscuro', 30, True), (again['tema'], again['retrasoSegundos'], again['inicioConWindows']))
            self.assertEqual(dict(host='192.168.1.5', port=47811), again['red'])
            for bad in (dict(tema='azul'), dict(retrasoSegundos=7), dict(abrirAlIniciar='si'), dict(red=dict(host='x')),
                        dict(red=dict(host='x', port='1')), dict(desconocida=1)):
                with self.assertRaises(ValueError): config.update_preferences(bad)

    def test_clave_opcional(self):
        config = Config(password_hash=None, secret='s')
        before = config.access_version()
        with self.assertRaises(ValueError): config.set_password('', 'corta')
        config.set_password('', 'clave-larga-1')
        self.assertNotEqual(before, config.access_version())
        self.assertFalse(config.check('otra-clave'))
        with self.assertRaises(ValueError): config.set_password('mala', None)
        config.set_password('clave-larga-1', None)
        self.assertTrue(config.check(''))


@unittest.skipUnless(os.name != 'nt', 'en Windows usaría el registro real')
class InicioAutomaticoTest(unittest.TestCase):
    def test_crea_y_quita_entrada_xdg(self):
        with tempfile.TemporaryDirectory() as folder, mock.patch.dict(os.environ, {'XDG_CONFIG_HOME': folder}), \
                mock.patch.object(autostart.sys, 'platform', 'linux'):
            autostart.apply(True)
            self.assertTrue(autostart.enabled())
            self.assertIn('--autostart', (Path(folder) / 'autostart' / 'spvi.desktop').read_text(encoding='utf-8'))
            autostart.apply(False)
            self.assertFalse(autostart.enabled())


class EscritorioHttpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.app = create_app(cls.temp.name, None, 'solo-pruebas', dev=True, controls=dict(restart=lambda: None))
        cls.app.testing = True
        cls.store = cls.app.extensions['store']
        cls.counts = seed.load(cls.store, days=40)

    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()

    def setUp(self):
        self.client = self.app.test_client()
        page = self.client.get('/')
        self.token = re.search(r'name="csrf" content="([^"]+)"', page.get_data(as_text=True)).group(1)

    def post(self, path, data):
        return self.client.post(path, json=data, headers={'X-CSRF-Token': self.token})

    def test_sin_clave_abre_la_pantalla_principal(self):
        html = self.client.get('/').get_data(as_text=True)
        self.assertIn('id="drawer"', html)
        self.assertIn('data-protegida="no"', html)
        self.assertNotIn('id="login"', html)

    def test_datos_de_prueba(self):
        self.assertGreater(self.counts['ventas'], 50)
        catalog = self.client.get('/api/catalogo').get_json()
        self.assertEqual('Bodega La Esquina', catalog['name'])
        self.assertTrue(all(p['localId'] for p in catalog['productos']))
        self.assertTrue(catalog['clientesFijos'])

    def test_registros_paginados_y_filtrados(self):
        page = self.client.get('/api/registros/ventas?page=1&size=10').get_json()
        self.assertEqual(10, len(page['items']))
        self.assertGreaterEqual(page['pages'], 2)
        dates = [x['fecha'] for x in page['items']]
        self.assertEqual(sorted(dates, reverse=True), dates)
        transfers = self.client.get('/api/registros/transferencias?size=200').get_json()
        self.assertTrue(all(x['transaccion'] for x in transfers['items']))
        since = dates[0] - 86400000
        recent = self.client.get('/api/registros/ventas?desde=%d' % since).get_json()
        self.assertTrue(all(x['fecha'] >= since for x in recent['items']))
        self.assertEqual(400, self.client.get('/api/registros/otra').status_code)
        self.assertEqual(400, self.client.get('/api/registros/ventas?size=500').status_code)

    def test_tiempo_en_linea_y_red(self):
        if not self.client.get('/api/employees').get_json():
            self.assertEqual(200, self.post('/api/employees', dict(action='create', name='Caja 2')).status_code)
        uptime = self.client.get('/api/uptime').get_json()
        self.assertLessEqual(uptime['app']['since'], uptime['ahora'])
        self.assertFalse(uptime['lan']['running'])
        self.assertTrue(uptime['apps'])
        for a in uptime['apps']:
            self.assertLessEqual({'nombre', 'connected', 'since', 'current', 'total', 'last_seen', 'ultimaSincronizacion', 'activa'}, set(a))
        network = self.client.get('/api/network').get_json()
        self.assertIn('candidatas', network)
        self.assertIsNone(network['guardada'])

    def test_preferencias_y_controles(self):
        info = self.client.get('/api/app').get_json()
        self.assertEqual(['restart'], info['controles'])
        self.assertTrue(info['dev'])
        self.assertEqual('claro', self.post('/api/app/preferences', dict(tema='claro')).get_json()['preferencias']['tema'])
        self.assertIn('data-theme="claro"', self.client.get('/').get_data(as_text=True))
        self.assertEqual(400, self.post('/api/app/preferences', dict(tema='rosa')).status_code)
        self.assertEqual(200, self.post('/api/app/restart', {}).status_code)
        self.assertEqual(400, self.post('/api/app/quit', {}).status_code)

    def test_qr_de_licencia_solo_para_whatsapp_del_desarrollador(self):
        self.assertEqual(400, self.post('/api/license/qr', dict(url='https://ejemplo.com')).status_code)
        response = self.post('/api/license/qr', dict(url='https://wa.me/5351815604?text=hola'))
        self.assertEqual(200, response.status_code)
        self.assertTrue(response.data.startswith(b'\x89PNG'))

    def test_clave_creada_desde_ajustes(self):
        temp = tempfile.TemporaryDirectory(); self.addCleanup(temp.cleanup)
        app = create_app(temp.name, None, 'otra'); app.testing = True
        client = app.test_client()
        token = re.search(r'name="csrf" content="([^"]+)"', client.get('/').get_data(as_text=True)).group(1)
        headers = {'X-CSRF-Token': token}
        self.assertEqual(400, client.post('/api/app/access', json=dict(nueva='clave-larga-1', repite='otra'), headers=headers).status_code)
        self.assertTrue(client.post('/api/app/access', json=dict(nueva='clave-larga-1', repite='clave-larga-1'), headers=headers).get_json()['protegida'])
        fresh = app.test_client()
        self.assertIn('id="login"', fresh.get('/').get_data(as_text=True))
        self.assertEqual(401, fresh.get('/api/catalogo').status_code)

    def test_semilla_solo_en_desarrollo(self):
        temp = tempfile.TemporaryDirectory(); self.addCleanup(temp.cleanup)
        app = create_app(temp.name, generate_password_hash('clave-de-pruebas'), 'x'); app.testing = True
        client = app.test_client()
        with client.session_transaction() as session:
            session.update(authenticated=True, credential_version=app.config['ACCESS_VERSION'], csrf='t')
        self.assertEqual(404, client.post('/api/seed', json=dict(confirm='BORRAR'), headers={'X-CSRF-Token': 't'}).status_code)
        self.assertEqual(400, self.post('/api/seed', dict(confirm='no')).status_code)


class InterfazTest(unittest.TestCase):
    def test_sprite_al_dia_con_spviicons(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location('generar_iconos', STATIC.parents[1] / 'tools' / 'generar_iconos.py')
        module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        if not module.SOURCE.is_file(): self.skipTest('Sin el código de la app móvil')
        content, count = module.build()
        self.assertGreaterEqual(count, 90)
        self.assertEqual(content, (STATIC / 'icons.svg').read_text(encoding='utf-8'), 'Ejecuta tools/generar_iconos.py')

    def test_iconos_usados_existen_en_el_sprite(self):
        sprite = set(re.findall(r'<symbol id="([A-Za-z]+)"', (STATIC / 'icons.svg').read_text(encoding='utf-8')))
        used = set()
        for script in (STATIC / 'js').glob('*.js'):
            text = script.read_text(encoding='utf-8')
            used |= set(re.findall(r"(?:icon|iconButton|leadIcon|vacio)\('([A-Za-z]+)'", text))
            used |= set(re.findall(r"(?:iconName|icon|icono): '([A-Za-z]+)'", text))
        self.assertTrue(used)
        self.assertEqual(set(), used - sprite)

    def test_sin_estilos_ni_scripts_en_linea(self):
        template = (STATIC.parent / 'templates' / 'index.html').read_text(encoding='utf-8')
        self.assertNotRegex(template, r'<script(?![^>]*\bsrc=)')
        self.assertNotIn('style=', template)
        for script in (STATIC / 'js').glob('*.js'):
            self.assertNotIn("setAttribute('style'", script.read_text(encoding='utf-8'))
            self.assertNotIn('innerHTML', script.read_text(encoding='utf-8'))
