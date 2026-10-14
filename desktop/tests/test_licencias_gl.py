"""Sistema de licencias GL en el escritorio: clave maestra, envelope v1, sub-tokens y marca de agua del reloj.

Espejo de las pruebas de `:licencia` (Android) y de `licensing/*_test.dart` (iOS). Usa claves efímeras de prueba: no
toca las claves reales del emisor.
"""
import base64
import json
import os
import tempfile
import unittest
import uuid
from datetime import datetime, timedelta, timezone

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from spvi_web.licensing import codigo, envelope, subtokens, trust
from spvi_web.licensing.client import LicenseClient, SECUNDARIAS_DEFECTO, TOLERANCIA_RELOJ
from spvi_web.secrets import SecretBox


def spki(clave):
    return base64.b64encode(clave.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)).decode('ascii')


class ModoDesarrolloTest(unittest.TestCase):
    """Clave maestra `GLMK-PERM-MAXS-9999`: solo con `SPVI_DESARROLLO=1` (mitigación 1, Opción A)."""

    def setUp(self):
        self.previo = os.environ.pop(trust.VARIABLE_DESARROLLO, None)

    def tearDown(self):
        os.environ.pop(trust.VARIABLE_DESARROLLO, None)
        if self.previo is not None:
            os.environ[trust.VARIABLE_DESARROLLO] = self.previo

    def test_codigo_de_16_caracteres(self):
        self.assertEqual('4K7N9X2MB8PQ3T5V', codigo.normalizar('4k7n-9x2m-b8pq-3t5v'))
        self.assertEqual('4K7N-9X2M-B8PQ-3T5V', codigo.formatear('4K7N9X2MB8PQ3T5V'))
        self.assertIsNone(codigo.normalizar('4K7N-9X2M-B8PQ-3T5'))
        self.assertIsNone(codigo.normalizar('4K7N-9X2M-B8PQ-3T5!'))

    def test_la_maestra_no_vale_sin_modo_desarrollo(self):
        self.assertFalse(trust.es_debug())
        self.assertFalse(codigo.es_maestra(codigo.MAESTRA))
        with tempfile.TemporaryDirectory() as carpeta:
            client = LicenseClient(carpeta, SecretBox('pruebas'))
            with self.assertRaises(ValueError):
                client.activate(codigo.MAESTRA)
            self.assertEqual('PRUEBA', client.status()['clase'])

    def test_en_modo_desarrollo_activa_perpetua_con_diez_secundarias(self):
        os.environ[trust.VARIABLE_DESARROLLO] = '1'
        with tempfile.TemporaryDirectory() as carpeta:
            client = LicenseClient(carpeta, SecretBox('pruebas'))
            estado = client.activate('glmk permmaxs 9999')  # se admite con espacios y en minúsculas
            self.assertEqual('PERPETUA', estado['clase'])
            self.assertEqual(10, estado['secundarias'])
            self.assertIsNone(estado['venceEn'])
            self.assertEqual(subtokens.MAESTRA_ID, estado['id'])
            # Se re-verifica al volver a abrir la instalación.
            self.assertEqual('PERPETUA', LicenseClient(carpeta, SecretBox('pruebas')).status()['clase'])
            # Y se pueden emitir sub-tokens hasta la cuota máxima.
            self.assertTrue(client.emitir_subtoken('SPVI:secundaria0001', 1))
            self.assertIsNone(client.emitir_subtoken('SPVI:secundaria0001', 11))

    def test_una_licencia_maestra_instalada_se_ignora_en_produccion(self):
        os.environ[trust.VARIABLE_DESARROLLO] = '1'
        with tempfile.TemporaryDirectory() as carpeta:
            client = LicenseClient(carpeta, SecretBox('pruebas'))
            client.activate(codigo.MAESTRA)
            os.environ.pop(trust.VARIABLE_DESARROLLO, None)
            # El mismo almacenamiento, pero sin modo desarrollo: la licencia maestra no vale.
            with self.assertRaises(ValueError):
                LicenseClient(carpeta, SecretBox('pruebas')).require_active()


class MarcaDeAguaTest(unittest.TestCase):
    """Mitigación 3: el reloj no puede retroceder más que la tolerancia respecto a la última hora vista."""

    def test_reloj_atrasado_bloquea(self):
        with tempfile.TemporaryDirectory() as carpeta:
            instante = [1700000000]
            client = LicenseClient(carpeta, SecretBox('pruebas'), lambda: instante[0])
            self.assertEqual('PRUEBA', client.status()['clase'])
            instante[0] -= TOLERANCIA_RELOJ + 60
            estado = LicenseClient(carpeta, SecretBox('pruebas'), lambda: instante[0]).status()
            self.assertEqual('BLOQUEADA', estado['clase'])
            self.assertIn('fecha', estado['motivo'])

    def test_un_ajuste_pequeno_de_hora_no_bloquea(self):
        with tempfile.TemporaryDirectory() as carpeta:
            instante = [1700000000]
            LicenseClient(carpeta, SecretBox('pruebas'), lambda: instante[0]).status()
            instante[0] -= TOLERANCIA_RELOJ - 30
            self.assertEqual('PRUEBA', LicenseClient(carpeta, SecretBox('pruebas'), lambda: instante[0]).status()['clase'])


class EnvelopeV1Test(unittest.TestCase):
    """Flujo v1 (envelope JSON): AAD canonicalizada, firma de GL y activación de la licencia larga."""

    def setUp(self):
        self.gl_ecdh = ec.generate_private_key(ec.SECP256R1())
        self.gl_firma = ec.generate_private_key(ec.SECP256R1())
        self.carpeta = tempfile.mkdtemp()
        self.box = SecretBox('pruebas')
        self.instante = [1700000000]
        self.client = self._cliente()

    def _cliente(self):
        """Cliente con las claves de firma de PRUEBA (nunca las reales de GL)."""
        return LicenseClient(self.carpeta, self.box, lambda: self.instante[0], firma_keys=(spki(self.gl_firma),))

    def _licencia(self, tipo='MENSUAL', secundarias=2, device_id=None, app='SPVI', codigo16=None, estado='ACTIVA',
                  vence=None, aad=None):
        letra_id = str(uuid.uuid4())
        emision = datetime.fromtimestamp(self.instante[0], timezone.utc)
        plano = dict(
            v=1, id=letra_id, deviceId=device_id or self.client.data['deviceId'], appName=app, dispositivo='PC de prueba',
            nombre='Carlos', apellidos='Fernández', ci='89051212345', via='WHATSAPP', telefono='+5352345678',
            usuario=dict(nombre='Carlos', apellidos='Fernández', ci='89051212345', telefono='+5352345678', via='WHATSAPP'),
            tipo=tipo, licenciaObtenida=codigo16, secundarias=secundarias,
            solicitadaEn=emision.isoformat().replace('+00:00', 'Z'), emitidaEn=emision.isoformat().replace('+00:00', 'Z'),
            venceEn=None if vence is None else vence.isoformat().replace('+00:00', 'Z'), estado=estado,
            nonce=base64.urlsafe_b64encode(os.urandom(16)).rstrip(b'=').decode(), codigoCorto=codigo16,
        )
        sobre = envelope.sellar(
            json.dumps(plano).encode(), self.client.private.public_key(), self.gl_firma,
            envelope.INFO_LICENCIA, aad or (lambda epk: envelope.aad_licencia(letra_id)),
        )
        return letra_id, f'Licencia SPVI {tipo} · {secundarias} secundarias\nID: {letra_id}\n{json.dumps(sobre)}'

    def test_aad_canonical_con_longitud_prefijada(self):
        partes = ['1', envelope.ALG, 'abc', envelope.KID]
        esperado = b''.join(len(p.encode()).to_bytes(4, 'big') + p.encode() for p in partes)
        self.assertEqual(esperado, envelope.canonical(partes))
        # El reparto de campos no se puede confundir (mitigación 8).
        self.assertNotEqual(envelope.canonical(['1', 'a', 'bc']), envelope.canonical(['1', 'ab', 'c']))
        self.assertNotEqual(envelope.aad_licencia('x'), envelope.aad_licencia_heredada('x'))

    def test_ida_y_vuelta_de_la_solicitud(self):
        device_pub = self.client.private.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        plano = envelope.solicitud_v1('Carlos', 'Fernández', '89051212345', '+5352345678', 'MENSUAL', 2,
                                      self.client.data['deviceId'], device_pub)
        sobre = envelope.sellar(
            json.dumps(plano).encode(), self.gl_ecdh.public_key(), None, envelope.INFO_SOLICITUD,
            lambda epk: envelope.aad_solicitud(base64.b64encode(epk).decode()),
        )
        datos = json.loads(envelope.abrir(sobre, self.gl_ecdh, envelope.aad_solicitud(sobre['epk']), envelope.INFO_SOLICITUD))
        self.assertEqual('SPVI', datos['appName'])
        self.assertEqual(2, datos['secundarias'])
        self.assertEqual(self.client.data['deviceId'], datos['deviceId'])
        self.assertEqual(base64.b64encode(device_pub).decode(), datos['devicePub'])  # mitigación 5: obligatorio
        self.assertIn('usuario', datos)
        self.assertTrue(envelope.firma_de_la_efimera(sobre))
        # La solicitud NO se abre con la AAD heredada: se sella con la canonicalizada (mitigación 8).
        with self.assertRaises(ValueError):
            envelope.abrir(sobre, self.gl_ecdh, envelope.aad_solicitud_heredada(sobre['epk']), envelope.INFO_SOLICITUD)

    def test_activar_licencia_larga(self):
        codigo16 = codigo.formatear(uuid.uuid4().hex[:16].upper())
        letra_id, mensaje = self._licencia(codigo16=codigo16, vence=datetime.fromtimestamp(self.instante[0] + 30 * 86400, timezone.utc))
        estado = self.client.activate(mensaje)
        self.assertEqual('ACTIVA', estado['clase'])
        self.assertEqual(letra_id, estado['id'])
        self.assertEqual(2, estado['secundarias'])
        # Se guarda el envelope (no el mensaje entero) y se re-verifica al reabrir.
        self.assertTrue(self.client.data['code'].lstrip().startswith('{'))
        self.assertEqual(letra_id, self._cliente().status()['id'])

    def test_la_aad_heredada_de_las_licencias_antiguas_se_acepta(self):
        """GL cambió la AAD a la canonicalizada (mitigación 8); lo emitido antes se sigue pudiendo abrir."""
        letra_id = str(uuid.uuid4())
        emision = datetime(2026, 1, 1, tzinfo=timezone.utc)
        plano = dict(v=1, id=letra_id, deviceId=self.client.data['deviceId'], appName='SPVI', dispositivo='PC',
                     nombre='Carlos', apellidos='Fernández', ci='89051212345', via='WHATSAPP', telefono='+5352345678',
                     usuario=dict(nombre='Carlos', apellidos='Fernández', ci='89051212345', telefono='+5352345678', via='WHATSAPP'),
                     tipo='MENSUAL', licenciaObtenida='4K7N-9X2M-B8PQ-3T5V', secundarias=1,
                     solicitadaEn=emision.isoformat().replace('+00:00', 'Z'),
                     emitidaEn=emision.isoformat().replace('+00:00', 'Z'),
                     venceEn='2026-12-31T00:00:00Z', estado='ACTIVA', nonce='x', codigoCorto='4K7N-9X2M-B8PQ-3T5V')
        sobre = envelope.sellar(json.dumps(plano).encode(), self.client.private.public_key(), self.gl_firma,
                                envelope.INFO_LICENCIA, lambda epk: envelope.aad_licencia_heredada(letra_id))
        mensaje = f'Licencia SPVI MENSUAL\nID: {letra_id}\n{json.dumps(sobre)}'
        self.instante[0] = int(datetime(2026, 6, 1, tzinfo=timezone.utc).timestamp())
        self.assertEqual('ACTIVA', self.client.activate(mensaje)['clase'])
        self.assertEqual(letra_id, self.client.status()['id'])

    def test_rechazos(self):
        bueno = self._licencia(codigo16='4K7N-9X2M-B8PQ-3T5V', vence=datetime.fromtimestamp(self.instante[0] + 86400, timezone.utc))[1]
        casos = {
            'otra app': self._licencia(app='GestorPro')[1],
            'otro equipo': self._licencia(device_id='SPVI:otro000000000001')[1],
            'código mal formado': self._licencia(codigo16='4K7N-9X2M-B8PQ-3T5')[1],
            'alterado': bueno.replace('"tag":"', '"tag":"A'),
            'sin ID': bueno.split('\n')[-1],
            'vencida': self._licencia(vence=datetime.fromtimestamp(self.instante[0] - 86400, timezone.utc))[1],
            'revocada': self._licencia(estado='REVOCADA')[1],
        }
        for nombre, mensaje in casos.items():
            with self.subTest(nombre):
                with self.assertRaises(ValueError):
                    LicenseClient(tempfile.mkdtemp(), self.box, lambda: self.instante[0],
                                  firma_keys=(spki(self.gl_firma),)).activate(mensaje)

    def test_extractores(self):
        letra_id, mensaje = self._licencia()
        self.assertTrue(envelope.es_envelope(mensaje))
        self.assertEqual(letra_id, envelope.extraer_id(mensaje))
        self.assertEqual(json.loads(mensaje.split('\n')[-1]), json.loads(envelope.extraer_envelope(mensaje)))
        self.assertIsNone(envelope.extraer_envelope('SPVIR1:AAAA'))
        self.assertIsNone(envelope.extraer_id('sin id por aquí'))
        self.assertFalse(envelope.es_envelope('{"v":2,"alg":"otra"}'))


class SubTokenTest(unittest.TestCase):
    """Mitigación 7: la cuota de secundarias es un permiso firmado por la principal."""

    def setUp(self):
        self.principal = ec.generate_private_key(ec.SECP256R1())
        self.ajena = ec.generate_private_key(ec.SECP256R1())
        self.ahora = datetime(2026, 10, 13, 12, 0, tzinfo=timezone.utc)
        self.licencia = '7c9e6679-7425-40de-944b-e07fc1f90ae7'
        self.secundaria = 'SPVI:01a2b3c4d5e6f708'
        self.vence = self.ahora + timedelta(days=30)

    def test_ida_y_vuelta(self):
        token = subtokens.emitir(self.principal, self.licencia, 3, self.vence, self.secundaria, 2)
        datos = subtokens.verificar(token, spki(self.principal), self.secundaria, self.ahora, cuota_maxima=3)
        self.assertEqual(dict(licenciaId=self.licencia, dispositivo=self.secundaria, ranura=2, permitidas=3, venceEn=self.vence), datos)

    def test_rechazos(self):
        token = subtokens.emitir(self.principal, self.licencia, 3, self.vence, self.secundaria, 1)
        casos = {
            'firma ajena': (token, spki(self.ajena), self.secundaria, self.ahora, 3),
            'otro dispositivo': (token, spki(self.principal), 'SPVI:otro000000000001', self.ahora, 3),
            'cuota menor': (token, spki(self.principal), self.secundaria, self.ahora, 0),
            'vencido': (token, spki(self.principal), self.secundaria, self.vence + timedelta(seconds=1), 3),
            'firma rota': (token[:-1] + 'A', spki(self.principal), self.secundaria, self.ahora, 3),
            'basura': ('', spki(self.principal), self.secundaria, self.ahora, 3),
            'sin firma': ('a|b|c|d|e', spki(self.principal), self.secundaria, self.ahora, 3),
        }
        for nombre, (token, clave, dispositivo, ahora, cuota) in casos.items():
            with self.subTest(nombre):
                self.assertIsNone(subtokens.verificar(token, clave, dispositivo, ahora, cuota_maxima=cuota))

    def test_la_ranura_y_la_cuota_se_comprueban_al_emitir_y_al_verificar(self):
        with self.assertRaises(ValueError):
            subtokens.emitir(self.principal, self.licencia, 3, self.vence, self.secundaria, 0)
        with self.assertRaises(ValueError):
            subtokens.emitir(self.principal, self.licencia, 3, self.vence, self.secundaria, 4)
        with self.assertRaises(ValueError):
            subtokens.emitir(self.principal, self.licencia, 11, self.vence, self.secundaria, 1)
        # Una ranura fuera de rango, aunque venga firmada, no autoriza.
        fuera = subtokens.texto(self.licencia, self.secundaria, 7, 3, self.vence) + '.' + base64.b64encode(
            self.principal.sign(subtokens.texto(self.licencia, self.secundaria, 7, 3, self.vence).encode(),
                                 ec.ECDSA(hashes.SHA256()))).decode()
        self.assertIsNone(subtokens.verificar(fuera, spki(self.principal), self.secundaria, self.ahora, cuota_maxima=10))

    def test_los_separadores_no_se_pueden_colar(self):
        with self.assertRaises(ValueError):
            subtokens.emitir(self.principal, self.licencia, 3, self.vence, 'SPVI:a|b', 1)
        with self.assertRaises(ValueError):
            subtokens.emitir(self.principal, 'id|falso', 3, self.vence, self.secundaria, 1)

    def test_la_perpetua_no_caduca(self):
        token = subtokens.emitir(self.principal, self.licencia, 1, None, self.secundaria, 1)
        self.assertIn('|' + subtokens.PERPETUA + '.', token)
        self.assertIsNotNone(subtokens.verificar(token, spki(self.principal), self.secundaria,
                                                 self.ahora + timedelta(days=3650), cuota_maxima=1))

    def test_el_cliente_emite_y_verifica_con_su_licencia(self):
        with tempfile.TemporaryDirectory() as carpeta:
            principal = LicenseClient(carpeta, SecretBox('pruebas'))  # en periodo de prueba: cuota por defecto
            estado = principal.status()
            self.assertEqual(SECUNDARIAS_DEFECTO, estado['secundarias'])
            token = principal.emitir_subtoken(self.secundaria, 1)
            self.assertIsNotNone(token)
            self.assertIsNone(principal.emitir_subtoken(self.secundaria, SECUNDARIAS_DEFECTO + 1))
            datos = principal.verificar_subtoken(token, principal.firma_publica(), self.secundaria)
            self.assertEqual(datos['ranura'], 1)
            self.assertIsNone(principal.verificar_subtoken(token, principal.firma_publica(), 'SPVI:otro00000000001'))
