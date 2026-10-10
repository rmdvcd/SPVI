"""Servidor principal sin sockets: QR, claves, deduplicación y cambios de venta."""
import copy
import unittest
import uuid
from types import SimpleNamespace
from tests import test_business
from spvi_web import dto
from spvi_web.business import objects,get
from spvi_web.sync.server import SyncManager,enc,dec,local_address
from spvi_web.sync.protocol import qr_decode,new_key,public_spki,link_mac,employee_key


class SyncServerTest(unittest.TestCase):
    setUp=test_business.BusinessTest.setUp

    def employee(self):
        return self.business.edit_employee(dict(action='create',name='Empleado de prueba'))

    def test_qr_un_uso_y_clave_compartida(self):
        employee=self.employee();manager=SyncManager(self.business)
        manager.running=True;manager.server=SimpleNamespace(server_address=('192.168.1.10',47811))
        fields,token=qr_decode(manager.qr(employee));private=new_key();peer=public_spki(private)
        hello=dict(t='vincular',v=1,negocio=fields['n'],empleado=employee,pub=enc(peer),mac=enc(link_mac(token,peer)))
        response,key,linked=manager.hello(hello)
        self.assertEqual('vincular_ok',response['t']);self.assertEqual(employee,linked)
        self.assertEqual(key,employee_key(private,dec(response['pub']),token))
        self.assertEqual('codigo_invalido',manager.hello(hello)[0]['motivo'])
        self.business.edit_employee(dict(action='revoke',id=employee))
        denied=manager.hello(dict(t='hola',v=1,negocio=fields['n'],empleado=employee,nonce=enc(bytes(16))))[0]
        self.assertEqual('quitada',denied['motivo']);self.assertIn('mac',denied)

    def batch(self):
        turn=str(uuid.uuid4());sale=str(uuid.uuid4());now=dto.now_ms()
        detail=dict(id=1,productoId=1,nombre='Café',categoria='Otros',cantidad=2,precioBaseCent=1000,precioUnitarioCent=1000,costoUnitarioCent=200)
        movement=dict(id=1,fecha=now,tipo='VENTA',entidad='PRODUCTO',entidadId=1,nombre='Café',delta=-2,existencia=18)
        return dict(id=1,lote=dict(turnos=[dict(uuid=turn,turno=dict(id=1,abiertoEn=now,fondoCent=0))],ventas=[dict(uuid=sale,turnoUuid=turn,venta=dict(id=1,turnoId=1,fecha=now,metodoPago='EFECTIVO',detalles=[detail]),movimientos=[movement])],caja=[]))

    def test_lote_repetido_no_descontado_dos_veces(self):
        employee=self.employee();message=self.batch()
        first=self.business.synchronize(employee,message)
        again=self.business.synchronize(employee,message)
        self.assertEqual(first['recibidos'],again['recibidos'])
        with self.business.connect() as db:
            self.assertEqual(18,get(db,'productos',1)['cantidad']);self.assertEqual(1,len(objects(db,'ventas')))
        changed=copy.deepcopy(message);changed['lote']['ventas'][0]['venta']['detalles'][0]['precioUnitarioCent']=1100
        with self.assertRaises(ValueError): self.business.synchronize(employee,changed)

    def test_correccion_secundaria_envia_reemplazo_y_no_cambia_caja_local(self):
        employee=self.employee();message=self.batch();self.business.synchronize(employee,message)
        self.business.operate('correct',dict(id=1,reason='Cantidad',lines=[dict(item_id=1,quantity=3)],method='efectivo'))
        changes=self.business.synchronize(employee,dict(id=2,lote={}))['cambiosVentas']
        self.assertEqual(2,len(changes));replacement=next(c for c in changes if c.get('nueva'))
        self.assertEqual(message['lote']['ventas'][0]['uuid'],replacement['corrigeUuid'])
        self.assertEqual(0,replacement['nueva']['id']);self.assertEqual(3,replacement['nueva']['detalles'][0]['cantidad'])
        self.assertEqual(10000,self.business.dashboard()['expected'])
        ack=self.business.synchronize(employee,dict(id=3,lote={},cambiosAplicados=[c['uuid'] for c in changes]))
        self.assertEqual([],ack['cambiosVentas'])

    def test_lan_no_acepta_direcciones_publicas(self):
        for host in ('0.0.0.0','127.0.0.1','8.8.8.8','::1','ejemplo.com'):
            with self.assertRaises(ValueError): local_address(host)
        self.assertEqual('192.168.1.2',local_address('192.168.1.2'))

    def test_cambio_permisos_no_pierde_ventas_offline(self):
        employee=self.employee();self.business.edit_employee(dict(action='permissions',id=employee,permissions=[]))
        self.assertEqual(1,len(self.business.synchronize(employee,self.batch())['recibidos']['ventas']))

    def test_instantanea_solo_expone_cuentas_asignadas(self):
        employee=self.employee()
        self.business.settings(dict(perfil=dict(nombre='Dueño',apellidos='Privado',ci='90010112345',tarjetas=[dict(id=1,numero='123456789012'),dict(id=2,numero='12345678901234567890')],telefonos=[dict(id=1,numero='51234567'),dict(id=2,numero='+5357654321')],pagoTarjetaId=1,pagoTelefonoId=1)))
        self.business.edit_employee(dict(action='payment',id=employee,card_id=2,phone_id=2))
        with self.business.connect() as db:
            profile=self.business.instantanea(db,self.business.employee(db,employee))['perfil']
        self.assertEqual('',profile['ci']);self.assertEqual('',profile['nombre'])
        self.assertEqual([2],[x['id'] for x in profile['tarjetas']])
        self.assertEqual([2],[x['id'] for x in profile['telefonos']])
