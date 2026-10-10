"""Validación de DTO y utilidades compartidas por respaldo y sincronización."""
import copy
import json
import time
import re
from datetime import datetime, timezone, date

COLLECTIONS = ('productos','servicios','insumos','recetas','preajustes','turnos','ventas','movimientos','caja','clientesFijos')
REQUIRED = {
    'productos': ('id','categoria','nombre','precioCostoCent','precioVentaCent','cantidad','creadoEn','actualizadoEn'),
    'servicios': ('id','nombre','tipo','importeCent','creadoEn','actualizadoEn'),
    'insumos': ('id','nombre','unidad','precioCent','cantidadMil','creadoEn','actualizadoEn'),
    'preajustes': ('id','nombre','puntosBasicos','productoIds'),
    'recetas': ('productoId','lineas'),
    'turnos': ('id','abiertoEn'),
    'ventas': ('id','turnoId','fecha','metodoPago','detalles'),
    'movimientos': ('id','fecha','tipo','entidad','entidadId','nombre','delta','existencia'),
    'caja': ('id','turnoId','fecha','tipo','importeCent','motivo'),
    'clientesFijos': ('nombreApellidos','ci','telefono','creadoEn','actualizadoEn'),
}
TEXT_FIELDS = {'nombre','categoria','descripcion','tipo','unidad','metodoPago','motivo','entidad','nota','hechoPor',
               'nombreApellidos','ci','telefono','abiertoPor','cerradoPor','uuid','fotoUri','fechaCaducidad',
               'motivoAnulacion','anuladaPor','clienteNombre','clienteCi','clienteTelefono','numero','tarjetaCobro','telefonoCobro'}
BOOL_FIELDS = {'eliminado','activo','sincronizado','clienteFijo'}


def now_ms(): return int(time.time()*1000)


def iso(ms): return datetime.fromtimestamp(ms/1000,timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')


def millis(value): return int(datetime.fromisoformat(value.replace('Z','+00:00')).timestamp()*1000)


def dump(value): return json.dumps(value,ensure_ascii=False,separators=(',',':'),sort_keys=True)


def number(value,minimum=0,maximum=9_000_000_000_000_000):
    if type(value) is not int or not minimum<=value<=maximum: raise ValueError('Número fuera de rango.')
    return value


def short(value,limit=120):
    if not isinstance(value,str) or not value.strip() or len(value)>limit: raise ValueError('Texto no válido.')
    return value.strip()


def validate(kind, value, allow_zero=False):
    if kind not in REQUIRED or not isinstance(value,dict) or any(x not in value for x in REQUIRED[kind]):
        raise ValueError('Faltan campos del registro.')
    obj = copy.deepcopy(value)
    for key in REQUIRED[kind]:
        if obj[key] is None: raise ValueError('Campo requerido nulo.')
    for key in ('turnoId','productoId','entidadId','empleadoId','corrigeVentaId'):
        if obj.get(key) is not None: number(obj[key],1)
    for key in ('cantidad','cantidadMil','delta','existencia'):
        if key in obj: number(obj[key],-9000000000000000)
    for key in ('nivelBajo','nivelCritico','nivelBajoMil','nivelCriticoMil','importeMinimoCent'):
        if obj.get(key) is not None: number(obj[key])
    if obj.get('fechaCaducidad') is not None:
        try: date.fromisoformat(obj['fechaCaducidad'])
        except (TypeError,ValueError): raise ValueError('Fecha de caducidad no válida.') from None
    if kind=='clientesFijos':
        obj['ci']=identity_number(obj['ci']);obj['telefono']=telephone(obj['telefono'])
    if kind=='insumos' and obj['unidad'] not in ('UNIDAD','KILOGRAMO','GRAMO','LITRO','MILILITRO'): raise ValueError('Unidad no válida.')
    if kind=='movimientos' and obj['tipo'] not in ('ALTA','AJUSTE','VENTA','PRODUCCION','CONSUMO','BAJA','ANULACION'): raise ValueError('Tipo de movimiento no válido.')
    if kind=='turnos' and obj.get('resumen') is not None:
        if not isinstance(obj['resumen'],dict): raise ValueError('Resumen no válido.')
        for value in obj['resumen'].values():
            if value is not None: number(value)

    for key,val in obj.items():
        if val is None:
            if key in ('lineas','insumos','detalles','productoIds'): raise ValueError('Lista nula no válida.')
            continue
        if key in TEXT_FIELDS:
            if not isinstance(val,str) or len(val)>4096: raise ValueError('Texto demasiado largo.')
        elif key in BOOL_FIELDS:
            if type(val) is not bool: raise ValueError('Indicador no válido.')
        elif key in ('lineas','insumos','detalles','productoIds'):
            if not isinstance(val,list) or len(val)>10000: raise ValueError('Lista no válida.')
        elif isinstance(val,(int,float)):
            number(val,-9_000_000_000_000_000)
    if 'id' in REQUIRED[kind]: number(obj['id'],0 if allow_zero else 1)
    for key in ('fecha','creadoEn','actualizadoEn','abiertoEn','cerradoEn','anuladaEn'):
        if obj.get(key) is not None: number(obj[key],0,253402214400000)
    for key in ('precioCostoCent','precioVentaCent','importeCent','precioCent','fondoCent','contadoCent','precioVentaCent'):
        if obj.get(key) is not None: number(obj[key])
    for key in ('nombre','categoria','tipo','unidad','motivo','nombreApellidos'):
        if key in REQUIRED[kind]: short(obj[key])
    if kind=='turnos' and obj.get('cerradoEn') is not None and obj['cerradoEn']<obj['abiertoEn']:
        raise ValueError('Cierre anterior a apertura.')
    if kind=='ventas':
        if obj['metodoPago'] not in ('EFECTIVO','TRANSFERENCIA') or not obj['detalles']: raise ValueError('Venta no válida.')
        for line in obj['detalles']:
            if not isinstance(line,dict): raise ValueError('Detalle no válido.')
            for key in ('id','productoId','cantidad','precioBaseCent','precioUnitarioCent','costoUnitarioCent'):
                number(line.get(key),1 if key in ('productoId','cantidad') else 0)
            short(line.get('nombre')); short(line.get('categoria'))
            if line.get('clase','PRODUCTO') not in ('PRODUCTO','INSUMO','SERVICIO'): raise ValueError('Artículo no válido.')
        if obj.get('transaccion') is not None:
            tr=obj['transaccion']
            if not isinstance(tr,dict): raise ValueError('Transferencia no válida.')
            for key in ('id','fecha','importeCent'): number(tr.get(key))
            if type(tr.get('clienteFijo',False)) is not bool: raise ValueError('Cliente fijo no válido.')
            # Normalización compatible con Phone.normalize y el contrato GL.
            tr['clienteCi']=identity_number(tr.get('clienteCi'),not tr.get('clienteFijo',False))
            tr['clienteTelefono']=telephone(tr.get('clienteTelefono'),not tr.get('clienteFijo',False))
            if tr.get('tarjetaCobro'): tr['tarjetaCobro']=bank_account(tr['tarjetaCobro'])
            if tr.get('telefonoCobro'): tr['telefonoCobro']=telephone(tr['telefonoCobro'])
            short(tr.get('numero'))
            if tr.get('clienteFijo'): short(tr.get('clienteNombre'))
            if tr['importeCent']!=sum(d['cantidad']*d['precioUnitarioCent'] for d in obj['detalles']): raise ValueError('Importe de transferencia no coincide.')
            for key in ('numero','clienteNombre','clienteCi','clienteTelefono'):
                if not isinstance(tr.get(key),str) or len(tr[key])>120: raise ValueError('Transferencia no válida.')
    if kind=='ventas':
        number(sum(d['cantidad']*d['precioUnitarioCent'] for d in obj['detalles']))
        number(sum(d['cantidad']*d['costoUnitarioCent'] for d in obj['detalles']))
    if kind=='caja' and obj['tipo'] not in ('ENTRADA','SALIDA'): raise ValueError('Movimiento no válido.')
    if kind=='movimientos' and obj['entidad'] not in ('PRODUCTO','INSUMO'): raise ValueError('Entidad no válida.')
    if kind in ('servicios','recetas'):
        seen=set()
        for line in obj.get('insumos' if kind=='servicios' else 'lineas',[]):
            if not isinstance(line,dict): raise ValueError('Receta no válida.')
            number(line.get('insumoId'),1); number(line.get('cantidadMil'),1)
            if line['insumoId'] in seen: raise ValueError('Insumo repetido en receta.')
            seen.add(line['insumoId'])
    if kind=='preajustes':
        if obj.get('metodoPago') not in (None,'EFECTIVO','TRANSFERENCIA'): raise ValueError('Método no válido.')
        number(obj['puntosBasicos'],-10000,1000000)
        for value in obj['productoIds']: number(value,1)
    return obj


def validate_document(document):
    if not isinstance(document,dict) or document.get('formato')!='spvi-respaldo' or document.get('version') not in (3,4):
        raise ValueError('Respaldo no compatible.')
    result=copy.deepcopy(document)
    for kind in COLLECTIONS:
        rows=document.get(kind,[])
        if not isinstance(rows,list) or len(rows)>100000: raise ValueError('Demasiados registros.')
        result[kind]=[validate(kind,obj) for obj in rows]
        ids=[obj.get('id',obj.get('productoId',obj.get('ci'))) for obj in result[kind]]
        if len(set(ids))!=len(ids): raise ValueError('Identificadores repetidos en el respaldo.')
    products={x['id'] for x in result['productos']}; supplies={x['id'] for x in result['insumos']}
    shifts={x['id'] for x in result['turnos']}
    for recipe in result['recetas']:
        if recipe['productoId'] not in products or any(x['insumoId'] not in supplies for x in recipe['lineas']):
            raise ValueError('Receta con referencias inexistentes.')
    for service in result['servicios']:
        if any(x['insumoId'] not in supplies for x in service.get('insumos',[])): raise ValueError('Insumo de servicio inexistente.')
    for sale in result['ventas']:
        if sale['turnoId'] not in shifts: raise ValueError('Venta sin turno.')
    for cash in result['caja']:
        if cash['turnoId'] not in shifts: raise ValueError('Movimiento de caja sin turno.')
    for kind in ('turnos','ventas','caja'):
        codes=[x['uuid'] for x in result[kind] if x.get('uuid')]
        if len(codes)!=len(set(codes)): raise ValueError('UUID repetido.')
    if result.get('perfil') is not None: profile(result['perfil'])
    if result.get('preferencias') is not None: preferences(result['preferencias'])
    employees=result.get('empleados',[])
    if not isinstance(employees,list) or len(employees)>1000: raise ValueError('Empleados no válidos.')
    seen=set()
    for employee in employees:
        if not isinstance(employee,dict): raise ValueError('Empleado no válido.')
        number(employee.get('id'),1); short(employee.get('nombre')); number(employee.get('creadoEn'))
        if employee['id'] in seen: raise ValueError('Empleado repetido.')
        seen.add(employee['id'])
        permissions=employee.get('permisos',[])
        if not isinstance(permissions,list) or any(p not in ('VENDER_PRODUCTOS','VENDER_SERVICIOS','EDITAR_INVENTARIO','CAMBIAR_PRECIOS','EXPORTAR') for p in permissions): raise ValueError('Permisos no válidos.')
        for key in ('tarjetaId','telefonoId'):
            if employee.get(key) is not None: number(employee[key],1)
    sales={x['id']:x for x in result['ventas']}
    for sale in result['ventas']:
        parent=sale.get('corrigeVentaId')
        if parent is not None and (parent not in sales or parent==sale['id'] or sales[parent]['turnoId']!=sale['turnoId']): raise ValueError('Corrección sin venta original del mismo turno.')
    visited=set()
    for sale in result['ventas']:
        path=set();current=sale['id']
        while current is not None and current not in visited:
            if current in path: raise ValueError('Ciclo entre correcciones.')
            path.add(current);current=sales[current].get('corrigeVentaId')
        visited.update(path)
    for movement in result['movimientos']:
        if movement.get('turnoId') is not None and movement['turnoId'] not in shifts: raise ValueError('Movimiento sin turno.')
        if movement.get('ventaId') is not None and movement['ventaId'] not in sales: raise ValueError('Movimiento sin venta.')
    for field in ('perfil','preferencias'):
        if result.get(field) is not None and not isinstance(result[field],dict): raise ValueError('Configuración no válida.')
    return result


def digits(value, length, optional=False):
    if optional and value in ('',None): return
    if not isinstance(value,str) or len(value)!=length or not value.isascii() or not value.isdigit():
        raise ValueError('Número de documento, teléfono o tarjeta no válido.')


def profile(value):
    if not isinstance(value,dict): raise ValueError('Perfil no válido.')
    for key in ('nombre','apellidos','ci'):
        if not isinstance(value.get(key,''),str) or len(value.get(key,''))>120: raise ValueError('Perfil no válido.')
    value['ci']=identity_number(value.get('ci',''),True)
    for collection,selected in [('tarjetas','pagoTarjetaId'),('telefonos','pagoTelefonoId')]:
        rows=value.get(collection,[])
        if not isinstance(rows,list) or len(rows)>100: raise ValueError('Cuentas no válidas.')
        seen=set()
        for row in rows:
            if not isinstance(row,dict): raise ValueError('Cuenta no válida.')
            number(row.get('id'),-1 if collection=='telefonos' else 1)
            if row['id']==0: raise ValueError('Cuenta sin identificador.')
            row['numero']=bank_account(row.get('numero')) if collection=='tarjetas' else telephone(row.get('numero'))
            if row['id'] in seen: raise ValueError('Cuenta repetida.')
            seen.add(row['id'])
            if row.get('alias') is not None and (not isinstance(row['alias'],str) or len(row['alias'])>120): raise ValueError('Alias no válido.')
        if value.get(selected) is not None:
            number(value[selected],-1 if collection=='telefonos' else 1)
            if value[selected] not in seen: raise ValueError('Cuenta seleccionada inexistente.')
    return value


def preferences(value):
    if not isinstance(value,dict): raise ValueError('Preferencias no válidas.')
    for key in ('productoBajo','productoCritico','insumoBajoMil','insumoCriticoMil','empleadosPrevistos'):
        if key in value: number(value[key],0,10 if key=='empleadosPrevistos' else 9000000000000000)
    if value.get('modulos') is not None and (not isinstance(value['modulos'],list) or any(not isinstance(x,str) or len(x)>80 for x in value['modulos'])):
        raise ValueError('Módulos no válidos.')
    return value


def identity_number(value,optional=False):
    if optional and value in ('',None): return ''
    if not isinstance(value,str) or not re.fullmatch('[A-Za-z0-9]{5,20}',value.strip()): raise ValueError('Documento de identidad no válido.')
    return value.strip().upper()


def bank_account(value):
    if not isinstance(value,str): raise ValueError('Cuenta no válida.')
    normalized=value.replace(' ','').replace('-','')
    if not re.fullmatch('[0-9]{12,20}',normalized): raise ValueError('La tarjeta o cuenta requiere entre 12 y 20 cifras.')
    return normalized


def telephone(value,optional=False):
    if optional and value in ('',None): return ''
    if not isinstance(value,str): raise ValueError('Teléfono no válido.')
    value=value.strip()
    if not re.fullmatch('[+0-9 ()-]+',value): raise ValueError('Teléfono no válido.')
    digits=''.join(c for c in value if c in '0123456789')
    if value.startswith('+'): normalized='+'+digits
    elif value.startswith('00'): normalized='+'+digits[2:]
    elif len(digits)==8: normalized='+53'+digits
    elif len(digits)==10 and digits.startswith('53') or 11<=len(digits)<=15: normalized='+'+digits
    else: raise ValueError('Teléfono no válido.')
    if not re.fullmatch('[+][0-9]{8,15}',normalized): raise ValueError('Teléfono no válido.')
    return normalized
