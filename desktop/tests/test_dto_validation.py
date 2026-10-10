"""Validación negativa de campos anidados y referencias de respaldo."""
import unittest
from spvi_web import dto


class DtoTest(unittest.TestCase):
    def test_receta_con_insumo_repetido_o_nulo(self):
        for lines in (None,[dict(insumoId=1,cantidadMil=10)]*2,[dict(insumoId=True,cantidadMil=10)]):
            with self.assertRaises(ValueError): dto.validate('recetas',dict(productoId=1,lineas=lines))

    def test_perfil_seleccion_y_tipos(self):
        for profile in ({'tarjetas':[None]}, {'telefonos':[dict(id=1,numero='123')]}, {'pagoTarjetaId':True}, {'ci':'٩'*11}):
            with self.assertRaises(ValueError): dto.profile(profile)

    def test_movimiento_referencia_y_enum(self):
        movement=dict(id=1,fecha=1,tipo='INVENTADO',entidad='PRODUCTO',entidadId=1,nombre='P',delta=1,existencia=1)
        with self.assertRaises(ValueError): dto.validate('movimientos',movement)
        movement.update(tipo='AJUSTE',entidadId='1')
        with self.assertRaises(ValueError): dto.validate('movimientos',movement)

    def test_no_acepta_venta_con_turno_ajeno_al_respaldo(self):
        sale=dict(id=1,turnoId=9,fecha=1,metodoPago='EFECTIVO',detalles=[dict(id=1,productoId=1,nombre='P',categoria='Otros',cantidad=1,precioBaseCent=2,precioUnitarioCent=2,costoUnitarioCent=1)])
        with self.assertRaises(ValueError): dto.validate_document(dict(formato='spvi-respaldo',version=4,ventas=[sale]))

    def test_resumen_booleano_no_es_numero(self):
        with self.assertRaises(ValueError): dto.validate('turnos',dict(id=1,abiertoEn=1,resumen={'numVentas':True}))

    def test_cuentas_android_y_telefono_e164(self):
        value=dto.profile(dict(tarjetas=[dict(id=1,numero='1'*12),dict(id=2,numero='2'*20)],telefonos=[dict(id=1,numero='51234567')],pagoTarjetaId=2,pagoTelefonoId=1))
        self.assertEqual('+5351234567',value['telefonos'][0]['numero'])
        self.assertEqual(20,len(value['tarjetas'][1]['numero']))
        self.assertEqual('AB123',dto.identity_number('ab123'))
        synthetic=dto.profile(dict(telefonos=[dict(id=-1,numero='+5351234567')],pagoTelefonoId=-1))
        self.assertEqual(-1,synthetic['pagoTelefonoId'])
