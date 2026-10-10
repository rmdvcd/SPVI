"""Venta y corrección atómicas; precios e insumos se congelan al confirmar."""
import uuid
from collections import defaultdict
from . import dto
from .store import integer, text


def checkout(business, db, data, correction=None):
    from .business import get, put, objects, next_id, meta, linked_id, total
    lines=data.get('lines')
    if not isinstance(lines,list) or not 1<=len(lines)<=100:
        raise ValueError('El carrito requiere entre 1 y 100 líneas.')
    method=data.get('method')
    if method not in ('efectivo','transferencia'): raise ValueError('Forma de pago no válida.')
    original=None
    if correction is not None:
        original=get(db,'ventas',integer(correction,1))
        if not original or original.get('anuladaEn') is not None: raise ValueError('La venta no se puede corregir.')
        turn=get(db,'turnos',original['turnoId'])
        business.void_sale(db,original['id'],text(data.get('reason')))
    else:
        local_turn=business.store.opened(db)
        turn=get(db,'turnos',linked_id(db,'turnos',local_turn['id']))
    if not turn or turn.get('cerradoEn') is not None: raise ValueError('Se requiere turno abierto.')
    grouped=defaultdict(int)
    for line in lines:
        if not isinstance(line,dict): raise ValueError('Línea no válida.')
        grouped[integer(line.get('item_id'),1)]+=integer(line.get('quantity'),1)
    selected=[]
    for local,quantity in grouped.items():
        integer(quantity,1)
        ref=db.execute("SELECT kind,object_id FROM local_refs WHERE local_id=? AND kind IN ('productos','servicios','insumos')",(local,)).fetchone()
        if not ref: raise ValueError('Artículo inexistente.')
        item=get(db,ref['kind'],ref['object_id'])
        if not item or item.get('eliminado'): raise ValueError('Artículo eliminado.')
        base=item.get('importeCent') if ref['kind']=='servicios' else item.get('precioVentaCent')
        if base is None: raise ValueError('El insumo no tiene precio de venta.')
        selected.append((local,ref['kind'],item,quantity,base))
    total_base=sum(base*q for _,kind,_,q,base in selected if kind=='productos')
    rules=[r for r in objects(db,'preajustes') if r.get('activo',True) and r.get('metodoPago') in (None,method.upper()) and total_base >= (r.get('importeMinimoCent') or 0)]
    sale=dict(id=next_id(db,'ventas'),uuid=str(uuid.uuid4()),turnoId=turn['id'],fecha=dto.now_ms(),metodoPago=method.upper(),detalles=[])
    if original: sale['corrigeVentaId']=original['id']
    if turn.get('empleadoId'): sale['empleadoId']=turn['empleadoId']
    stock=defaultdict(int)
    for local,kind,item,quantity,base in selected:
        elaborated=kind=='productos' and item['categoria'].casefold()=='elaborado'
        recipe=(get(db,'recetas',item['id']) or {}).get('lineas',[]) if elaborated else item.get('insumos',[])
        if elaborated and not recipe: raise ValueError('El elaborado necesita receta.')
        cost=business.recipe_cost(db,recipe) if kind=='servicios' or elaborated else item['precioCent' if kind=='insumos' else 'precioCostoCent']
        bp=max(-10000,sum(r['puntosBasicos'] for r in rules if item['id'] in r['productoIds'])) if kind=='productos' else 0
        price=(base*(10000+bp)+5000)//10000
        if price<=cost: raise ValueError('El precio ajustado debe superar el costo.')
        if kind=='productos' and not elaborated: stock[('PRODUCTO',item['id'],'VENTA')]-=quantity
        if kind=='insumos': stock[('INSUMO',item['id'],'VENTA')]-=quantity*1000
        for component in recipe: stock[('INSUMO',component['insumoId'],'CONSUMO')]-=component['cantidadMil']*quantity
        sale['detalles'].append(dict(id=len(sale['detalles'])+1,productoId=item['id'],nombre=item['nombre'],categoria=item.get('categoria',item.get('tipo',item.get('unidad','Insumo'))),cantidad=quantity,precioBaseCent=base,precioUnitarioCent=price,costoUnitarioCent=cost,clase={'productos':'PRODUCTO','servicios':'SERVICIO','insumos':'INSUMO'}[kind]))
    dto.number(total(sale)); dto.number(sum(d['costoUnitarioCent']*d['cantidad'] for d in sale['detalles']))
    for (entity,id,movement_type),delta in stock.items():
        item=get(db,'productos' if entity=='PRODUCTO' else 'insumos',id)
        field='cantidad' if entity=='PRODUCTO' else 'cantidadMil'
        if not item or item[field]+delta<0: raise ValueError('Existencias o insumos insuficientes.')
        balance=business.stock_delta(db,entity,id,delta)
        put(db,'movimientos',dict(id=next_id(db,'movimientos'),fecha=sale['fecha'],tipo=movement_type,entidad=entity,entidadId=id,nombre=item['nombre'],delta=delta,existencia=balance,turnoId=turn['id'],ventaId=sale['id'],hechoPor=meta(db,'name')))
    if method=='transferencia':
        source=data.get('transaction')
        if not isinstance(source,dict): raise ValueError('Completa los datos de transferencia.')
        for field in ('numero','clienteNombre','clienteCi','clienteTelefono'):
            if not isinstance(source.get(field,''),str): raise ValueError('Datos de transferencia no válidos.')
        profile=meta(db,'perfil',{}) or {}
        if turn.get('empleadoId'):
            employee=business.employee(db,turn['empleadoId'])
            profile=dict(profile,pagoTarjetaId=employee['card_id'] or profile.get('pagoTarjetaId'),pagoTelefonoId=employee['phone_id'] or profile.get('pagoTelefonoId'))
        tr=dict(id=sale['id'],fecha=sale['fecha'],importeCent=total(sale),numero=text(source.get('numero')),
                clienteNombre=source.get('clienteNombre','').strip(),clienteCi=source.get('clienteCi','').strip(),clienteTelefono=source.get('clienteTelefono','').strip(),clienteFijo=source.get('clienteFijo',False))
        for collection,selection,field in [('tarjetas','pagoTarjetaId','tarjetaCobro'),('telefonos','pagoTelefonoId','telefonoCobro')]:
            tr[field]=next((x['numero'] for x in profile.get(collection,[]) if x['id']==profile.get(selection)),None)
        sale['transaccion']=tr
    sale=dto.validate('ventas',sale)
    tr=sale.get('transaccion')
    if tr and tr['clienteFijo']:
        row=db.execute("SELECT id,payload FROM objects WHERE kind='clientesFijos' AND json_extract(payload,'$.ci')=?",(tr['clienteCi'],)).fetchone()
        previous=get(db,'clientesFijos',row['id']) if row else {}
        client=dto.validate('clientesFijos',dict(nombreApellidos=tr['clienteNombre'],ci=tr['clienteCi'],telefono=tr['clienteTelefono'],creadoEn=previous.get('creadoEn',sale['fecha']),actualizadoEn=sale['fecha']))
        put(db,'clientesFijos',client,id=row['id'] if row else next_id(db,'clientesFijos'))
    put(db,'ventas',sale)
    if not turn.get('empleadoId'):
        ref=db.execute("SELECT local_id FROM local_refs WHERE kind='turnos' AND object_id=?",(turn['id'],)).fetchone()
        if not ref: raise ValueError('Turno local sin correspondencia.')
        for index,((local,_,_,quantity,_),detail) in enumerate(zip(selected,sale['detalles'])):
            rowid=db.execute('INSERT INTO sales(shift_id,item_id,name,quantity,price,cost,method) VALUES(?,?,?,?,?,?,?)',(ref[0],local,detail['nombre'],quantity,detail['precioUnitarioCent'],detail['costoUnitarioCent'],method)).lastrowid
            if index==0: db.execute("INSERT INTO local_refs VALUES('ventas',?,?)",(rowid,sale['id']))
            else: db.execute('INSERT INTO grouped_sales VALUES(?,?)',(rowid,sale['id']))
    elif original:
        new=dict(sale,id=0,turnoId=0,corrigeVentaId=None)
        change=dict(uuid=sale['uuid'],turnoUuid=turn['uuid'],nueva=new,corrigeUuid=original['uuid'])
        db.execute('INSERT INTO sale_changes VALUES(?,?,?)',(turn['empleadoId'],sale['uuid'],dto.dump(change)))
    business.project_catalog(db)
    return sale['id']
