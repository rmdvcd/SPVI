"""Principal web: DTO Android persistentes y proyección del puesto de venta local.

Las transacciones SQLite abarcan el registro, sus movimientos y el acuse por UUID.
Los IDs de una secundaria nunca sustituyen los IDs de la principal.
"""
import copy
import hashlib
import json
import os
import sqlite3
import uuid
import threading
from contextlib import contextmanager
from collections import defaultdict
from decimal import Decimal
from pathlib import Path
from . import dto, android_backup, backups
from .store import Store, money, integer, text

PERMISSIONS = {'VENDER_PRODUCTOS','VENDER_SERVICIOS','EDITAR_INVENTARIO','CAMBIAR_PRECIOS','EXPORTAR'}
SCHEMA = '''
CREATE TABLE IF NOT EXISTS object_counters(kind TEXT PRIMARY KEY,last_id INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS remote_receipts(employee INTEGER NOT NULL,kind TEXT NOT NULL,uuid TEXT NOT NULL,digest TEXT NOT NULL,PRIMARY KEY(employee,kind,uuid));
CREATE TABLE IF NOT EXISTS photos(kind TEXT NOT NULL,id INTEGER NOT NULL,content BLOB NOT NULL,PRIMARY KEY(kind,id));
CREATE TABLE IF NOT EXISTS http_requests(key TEXT PRIMARY KEY,digest TEXT NOT NULL,result TEXT NOT NULL,status INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS business_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS objects(kind TEXT NOT NULL,id INTEGER NOT NULL,payload TEXT NOT NULL,
 uuid TEXT,employee INTEGER,PRIMARY KEY(kind,id),UNIQUE(kind,uuid));
CREATE TABLE IF NOT EXISTS local_refs(kind TEXT NOT NULL,local_id INTEGER NOT NULL,object_id INTEGER NOT NULL,
 PRIMARY KEY(kind,local_id),UNIQUE(kind,object_id));
CREATE TABLE IF NOT EXISTS employees(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,permissions TEXT NOT NULL,
 active INTEGER NOT NULL DEFAULT 1,secret BLOB,token BLOB,expires INTEGER,linked INTEGER,last_sync INTEGER,
 fund INTEGER,fund_token INTEGER,requests_fund INTEGER NOT NULL DEFAULT 0,requests_close INTEGER NOT NULL DEFAULT 0,
 close_requested INTEGER NOT NULL DEFAULT 0,close_rejected INTEGER NOT NULL DEFAULT 0,
 phone TEXT,version INTEGER,card_id INTEGER,phone_id INTEGER);
CREATE TABLE IF NOT EXISTS commands(employee INTEGER NOT NULL,key TEXT NOT NULL,request TEXT NOT NULL,result TEXT NOT NULL,
 PRIMARY KEY(employee,key));
CREATE TABLE IF NOT EXISTS grouped_sales(local_id INTEGER PRIMARY KEY,sale_id INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS sale_changes(employee INTEGER NOT NULL,uuid TEXT NOT NULL,payload TEXT NOT NULL,
 PRIMARY KEY(employee,uuid));
'''


def meta(db,key,default=None):
    row=db.execute('SELECT value FROM business_meta WHERE key=?',(key,)).fetchone()
    return json.loads(row[0]) if row else default


def set_meta(db,key,value): db.execute('INSERT OR REPLACE INTO business_meta VALUES(?,?)',(key,dto.dump(value)))


def objects(db,kind): return [json.loads(r[0]) for r in db.execute('SELECT payload FROM objects WHERE kind=? ORDER BY id',(kind,))]


def get(db,kind,id):
    row=db.execute('SELECT payload FROM objects WHERE kind=? AND id=?',(kind,id)).fetchone()
    return json.loads(row[0]) if row else None


def by_uuid(db,kind,code):
    row=db.execute('SELECT payload FROM objects WHERE kind=? AND uuid=?',(kind,code)).fetchone()
    return json.loads(row[0]) if row else None


def next_id(db,kind):
    current=db.execute('SELECT COALESCE(MAX(id),0) FROM objects WHERE kind=?',(kind,)).fetchone()[0]
    row=db.execute('SELECT last_id FROM object_counters WHERE kind=?',(kind,)).fetchone()
    return max(current,row[0] if row else 0)+1


def put(db,kind,value,id=None):
    item=copy.deepcopy(value)
    id=id if id is not None else item.get('id',item.get('productoId'))
    if not id: id=next_id(db,kind)
    db.execute('INSERT INTO object_counters VALUES(?,?) ON CONFLICT(kind) DO UPDATE SET last_id=MAX(last_id,excluded.last_id)',(kind,id))
    if kind not in ('recetas','clientesFijos'): item['id']=id
    db.execute('INSERT INTO objects(kind,id,payload,uuid,employee) VALUES(?,?,?,?,?) ON CONFLICT(kind,id) DO UPDATE SET payload=excluded.payload,uuid=excluded.uuid,employee=excluded.employee',
               (kind,id,dto.dump(item),item.get('uuid'),item.get('empleadoId')))
    return item


def linked_id(db,kind,local_id):
    row=db.execute('SELECT object_id FROM local_refs WHERE kind=? AND local_id=?',(kind,local_id)).fetchone()
    if row: return row[0]
    value=next_id(db,kind)
    db.execute('INSERT INTO local_refs VALUES(?,?,?)',(kind,local_id,value))
    return value


def total(sale): return sum(x['precioUnitarioCent']*x['cantidad'] for x in sale['detalles'])


def cost(sale): return sum(x['costoUnitarioCent']*x['cantidad'] for x in sale['detalles'])


class Business:
    ROLE='PRINCIPAL'

    def __init__(self,store,license_client,box):
        self.store,self.license,self.box=store,license_client,box
        self._context=threading.local()
        self.path=store.path
        with store.connect() as db:
            db.executescript(SCHEMA)
            self.begin(db)
            if meta(db,'schema') not in (None,1): raise ValueError('Versión de negocio no compatible.')
            set_meta(db,'schema',1)
            if not meta(db,'business_id'): set_meta(db,'business_id',str(uuid.uuid4()))
            if not meta(db,'name'): set_meta(db,'name','Mi negocio')
            self.capture_local(db)

    @contextmanager
    def connect(self):
        current=getattr(self._context,'db',None)
        if current is not None: yield current
        else:
            with self.store.connect() as db: yield db

    @staticmethod
    def begin(db):
        if not db.in_transaction: db.execute('BEGIN IMMEDIATE')

    @contextmanager
    def request_transaction(self):
        with self.store.connect() as db:
            self.begin(db)
            self._context.db=db
            try: yield db
            finally: del self._context.db


    def capture_local(self,db,update_items=False):
        """Incorpora datos previos de escritorio sin perder los campos adicionales del DTO."""
        now=dto.now_ms()
        for row in db.execute('SELECT * FROM items'):
            if db.execute("SELECT 1 FROM local_refs WHERE kind='insumos' AND local_id=?",(row['id'],)).fetchone(): continue
            kind='productos' if row['kind']=='producto' else 'servicios'
            id=linked_id(db,kind,row['id']); item=get(db,kind,id)
            if item and row['id']!=update_items: continue
            item=item or dict(id=id,creadoEn=now,actualizadoEn=now)
            item.update(nombre=row['name'])
            if kind=='productos':
                item.update(precioVentaCent=row['price'],precioCostoCent=row['cost'])
                # El espejo local no representa stock negativo de ventas offline: conservar el valor exacto.
                if item.get('cantidad',0)>=0 or row['stock']>0: item['cantidad']=row['stock']
                item.setdefault('categoria','Otros'); item['nivelBajo']=row['minimum']
            else:
                item.update(importeCent=row['price']); item.setdefault('tipo','Servicio'); item.setdefault('insumos',[])
            put(db,kind,item)
        for row in db.execute('SELECT * FROM shifts'):
            id=linked_id(db,'turnos',row['id']); item=get(db,'turnos',id) or dict(id=id,uuid=str(uuid.uuid4()))
            item.update(abiertoEn=dto.millis(row['opened']),cerradoEn=dto.millis(row['closed']) if row['closed'] else None,
                        fondoCent=row['fund'],contadoCent=row['counted'],abiertoPor=meta(db,'name'))
            put(db,'turnos',item)
        for row in db.execute('SELECT * FROM sales'):
            if db.execute('SELECT 1 FROM grouped_sales WHERE local_id=?',(row['id'],)).fetchone(): continue
            id=linked_id(db,'ventas',row['id'])
            if get(db,'ventas',id): continue
            local=db.execute('SELECT kind FROM items WHERE id=?',(row['item_id'],)).fetchone()
            kind='productos' if local['kind']=='producto' else 'servicios'
            pid=linked_id(db,kind,row['item_id']); product=get(db,kind,pid)
            self.consume_recipe(db,kind,product,row['quantity'],allow_negative=False)
            detail=dict(id=0,productoId=pid,nombre=row['name'],categoria=product.get('categoria',product.get('tipo','Servicio')),
                        cantidad=row['quantity'],precioBaseCent=row['price'],precioUnitarioCent=row['price'],costoUnitarioCent=row['cost'],
                        clase='PRODUCTO' if kind=='productos' else 'SERVICIO')
            put(db,'ventas',dict(id=id,uuid=str(uuid.uuid4()),turnoId=linked_id(db,'turnos',row['shift_id']),fecha=dto.millis(row['created']),
                                metodoPago=row['method'].upper(),detalles=[detail]))
        for row in db.execute('SELECT * FROM movements'):
            id=linked_id(db,'caja',row['id'])
            if get(db,'caja',id): continue
            put(db,'caja',dict(id=id,uuid=str(uuid.uuid4()),turnoId=linked_id(db,'turnos',row['shift_id']),fecha=dto.millis(row['created']),
                              tipo='ENTRADA' if row['amount']>0 else 'SALIDA',importeCent=abs(row['amount']),motivo=row['reason']))
        for turn in objects(db,'turnos'):
            if turn.get('cerradoEn') is not None: self.summarize(db,turn)

    def project_catalog(self,db):
        for kind in ('productos','servicios','insumos'):
            for item in objects(db,kind):
                row=db.execute('SELECT local_id FROM local_refs WHERE kind=? AND object_id=?',(kind,item['id'])).fetchone()
                local=row[0] if row else None
                price=(item.get('precioVentaCent') or 0) if kind!='servicios' else item['importeCent']
                cost_value=item['precioCostoCent'] if kind=='productos' else item['precioCent'] if kind=='insumos' else self.recipe_cost(db,item.get('insumos',[]))
                values=(item['nombre'],'producto' if kind=='productos' else 'servicio',price,cost_value,
                        max(0,item.get('cantidad',0)),max(0,item.get('nivelBajo') or 0))
                if local is None:
                    local=db.execute('INSERT INTO items(name,kind,price,cost,stock,minimum) VALUES(?,?,?,?,?,?)',values).lastrowid
                    db.execute('INSERT INTO local_refs VALUES(?,?,?)',(kind,local,item['id']))
                else:
                    db.execute('UPDATE items SET name=?,kind=?,price=?,cost=?,stock=?,minimum=? WHERE id=?',values+(local,))

    @staticmethod
    def recipe_cost(db,lines):
        return sum(((get(db,'insumos',x['insumoId']) or {}).get('precioCent',0)*x['cantidadMil']+500)//1000 for x in lines)

    def consume_recipe(self,db,kind,item,quantity,allow_negative=False):
        if kind=='servicios': lines=item.get('insumos',[])
        else: lines=(get(db,'recetas',item['id']) or {}).get('lineas',[]) if item.get('categoria','').casefold()=='elaborado' else []
        for line in lines:
            supply=get(db,'insumos',line['insumoId'])
            if not supply: raise ValueError('Falta un insumo de la receta.')
            supply['cantidadMil']-=line['cantidadMil']*quantity
            if supply['cantidadMil']<0 and not allow_negative: raise ValueError('Insumos insuficientes.')
            put(db,'insumos',supply)

    def operate(self,action,data):
        self.license.require_active()
        with self.connect() as db:
            self.begin(db)
            if action=='client':
                ci=dto.identity_number(data.get('ci'))
                row=db.execute("SELECT id FROM objects WHERE kind='clientesFijos' AND json_extract(payload,'$.ci')=?",(ci,)).fetchone()
                if data.get('delete'):
                    if row: db.execute("DELETE FROM objects WHERE kind='clientesFijos' AND id=?",(row[0],))
                else:
                    old=get(db,'clientesFijos',row[0]) if row else {}
                    client=dto.validate('clientesFijos',dict(ci=ci,nombreApellidos=text(data.get('name')),telefono=data.get('phone'),creadoEn=old.get('creadoEn',dto.now_ms()),actualizadoEn=dto.now_ms()))
                    put(db,'clientesFijos',client,id=row[0] if row else next_id(db,'clientesFijos'))
                return
            if action in ('sale','item-edit','item-delete'):
                local=integer(data.get('item_id'),1)
                ref=db.execute("SELECT kind,object_id FROM local_refs WHERE local_id=? AND kind IN ('productos','servicios')",(local,)).fetchone()
                if not ref: raise ValueError('Artículo no encontrado.')
                item=get(db,ref['kind'],ref['object_id'])
                if item.get('eliminado'): raise ValueError('Artículo eliminado.')
                if action=='item-delete':
                    item['eliminado']=True; item['actualizadoEn']=dto.now_ms(); put(db,ref['kind'],item); return
                if action=='item-edit':
                    db.execute('UPDATE items SET name=?,price=?,cost=?,stock=?,minimum=? WHERE id=?',
                               (text(data.get('name')),money(data.get('price')),money(data.get('cost')),integer(data.get('stock',0)),integer(data.get('minimum',0)),local))
                    if ref['kind']=='productos': item['cantidad']=integer(data.get('stock',0)); put(db,ref['kind'],item)
                    self.capture_local(db,update_items=local)
                    item=get(db,ref['kind'],ref['object_id'])
                    for key in ('descripcion','categoria','tipo','fechaCaducidad','nivelCritico'):
                        if key in data: item[key]=data[key] if data[key]!='' else None
                    if item.get('nivelCritico') is not None: item['nivelCritico']=integer(item['nivelCritico'])
                    dto.validate(ref['kind'],item); put(db,ref['kind'],item)
                    return
            if action in ('cart','correct','sale'):
                from .checkout import checkout
                if action=='sale': data=dict(data,lines=[dict(item_id=data.get('item_id'),quantity=data.get('quantity'))])
                return checkout(self,db,data,integer(data.get('id'),1) if action=='correct' else None)
            if action=='void':
                self.void_sale(db,integer(data.get('id'),1),text(data.get('reason')))
                return
            self.store.apply(db,action,data)
            self.capture_local(db)

    def quote(self,data):
        from .checkout import checkout
        self.license.require_active()
        preview=copy.deepcopy(data)
        if preview.get('method')=='transferencia':
            preview['transaction']=dict(numero='COTIZACION',clienteNombre='',clienteCi='',clienteTelefono='',clienteFijo=False)
        if preview.get('id'): preview['reason']='Cotización de corrección'
        with self.connect() as db:
            self.begin(db);db.execute('SAVEPOINT quotation')
            try:
                id=checkout(self,db,preview,integer(preview['id'],1) if preview.get('id') else None)
                sale=get(db,'ventas',id)
                return dict(total=total(sale),details=sale['detalles'])
            finally:
                db.execute('ROLLBACK TO quotation');db.execute('RELEASE quotation')

    def summarize(self,db,turn):
        sales=[s for s in objects(db,'ventas') if s['turnoId']==turn['id']]
        valid=[s for s in sales if s.get('anuladaEn') is None]
        moves=[m for m in objects(db,'caja') if m['turnoId']==turn['id']]
        stock=[m for m in objects(db,'movimientos') if m.get('turnoId')==turn['id']]
        turn['resumen']=dict(numVentas=len(valid),unidades=sum(d['cantidad'] for s in valid for d in s['detalles']),
            totalCent=sum(total(s) for s in valid),efectivoCent=sum(total(s) for s in valid if s['metodoPago']=='EFECTIVO'),
            transferenciaCent=sum(total(s) for s in valid if s['metodoPago']=='TRANSFERENCIA'),costoCent=sum(cost(s) for s in valid),
            numMovimientos=len(stock),entradasCent=sum(m['importeCent'] for m in moves if m['tipo']=='ENTRADA'),
            salidasCent=sum(m['importeCent'] for m in moves if m['tipo']=='SALIDA'),numAnuladas=len(sales)-len(valid),
            ventasEfectivo=sum(s['metodoPago']=='EFECTIVO' for s in valid),ventasTransferencia=sum(s['metodoPago']=='TRANSFERENCIA' for s in valid),
            movimientosProducto=sum(m['entidad']=='PRODUCTO' for m in stock),movimientosInsumo=sum(m['entidad']=='INSUMO' for m in stock))
        put(db,'turnos',turn)
        return turn['resumen']

    def void_sale(self,db,id,reason):
        sale=get(db,'ventas',id)
        if not sale: raise ValueError('Venta no encontrada.')
        turn=get(db,'turnos',sale['turnoId'])
        if not turn or turn.get('cerradoEn') is not None: raise ValueError('Solo se anula con turno abierto.')
        if sale.get('anuladaEn') is not None: return
        sale.update(anuladaEn=dto.now_ms(),motivoAnulacion=reason,anuladaPor=meta(db,'name'))
        motions=[m for m in objects(db,'movimientos') if m.get('ventaId')==id]
        if motions:
            for movement in motions:
                balance=self.stock_delta(db,movement['entidad'],movement['entidadId'],-movement['delta'])
                current=get(db,'productos' if movement['entidad']=='PRODUCTO' else 'insumos',movement['entidadId'])
                reversal=dict(movement,id=next_id(db,'movimientos'),fecha=sale['anuladaEn'],tipo='ANULACION',
                              delta=-movement['delta'],existencia=balance)
                if current is None: reversal['nota']='Artículo ausente del catálogo: conciliar existencias manualmente'
                reversal.pop('uuid',None)
                put(db,'movimientos',reversal)
        else:
            for line in sale['detalles']:
                kind={'PRODUCTO':'productos','SERVICIO':'servicios','INSUMO':'insumos'}[line.get('clase','PRODUCTO')]
                item=get(db,kind,line['productoId'])
                if item:
                    if kind=='productos' and line.get('categoria','').casefold()!='elaborado': item['cantidad']+=line['cantidad']; put(db,kind,item)
                    elif kind=='insumos': item['cantidadMil']+=line['cantidad']*1000; put(db,kind,item)
                    # Sin movimientos históricos no se puede deducir una receta pasada de la receta actual.
        put(db,'ventas',sale)
        if sale.get('empleadoId'):
            change=dict(uuid=sale['uuid'],turnoUuid=turn['uuid'],anuladaEn=sale['anuladaEn'],motivo=reason,anuladaPor=meta(db,'name'))
            db.execute('INSERT OR REPLACE INTO sale_changes VALUES(?,?,?)',(sale['empleadoId'],sale['uuid'],dto.dump(change)))
        else:
            ref=db.execute("SELECT local_id FROM local_refs WHERE kind='ventas' AND object_id=?",(id,)).fetchone()
            if ref: db.execute('UPDATE sales SET price=0,cost=0 WHERE id=?',(ref[0],))
            db.execute('UPDATE sales SET price=0,cost=0 WHERE id IN (SELECT local_id FROM grouped_sales WHERE sale_id=?)',(id,))
        self.project_catalog(db)

    @staticmethod
    def stock_delta(db,entity,id,delta):
        kind='productos' if entity=='PRODUCTO' else 'insumos'; key='cantidad' if entity=='PRODUCTO' else 'cantidadMil'
        item=get(db,kind,id)
        if item:
            item[key]+=delta; item['actualizadoEn']=dto.now_ms(); put(db,kind,item)
            return item[key]
        return 0

    def inventory_record(self,db,kind,item,delta,category='AJUSTE'):
        if kind not in ('productos','insumos') or not delta: return
        put(db,'movimientos',dict(id=next_id(db,'movimientos'),fecha=dto.now_ms(),tipo=category,
            entidad='PRODUCTO' if kind=='productos' else 'INSUMO',entidadId=item['id'],nombre=item['nombre'],
            delta=delta,existencia=item['cantidad' if kind=='productos' else 'cantidadMil'],hechoPor=meta(db,'name')))

    def catalog_command(self,db,action):
        tag=action.get('t')
        mapping={'producto':'productos','servicio':'servicios','insumo':'insumos','preajuste':'preajustes'}
        if tag=='producto_precios':
            prices=action.get('precios')
            if not isinstance(prices,dict) or len(prices)>10000: raise ValueError('Precios no válidos.')
            for id,amount in prices.items():
                item=get(db,'productos',int(id))
                if not item: raise ValueError('Producto inexistente.')
                item['precioVentaCent']=dto.number(amount); put(db,'productos',item)
            self.project_catalog(db); return None
        parts=str(tag).split('_')
        if len(parts)!=2 or parts[0] not in mapping: raise ValueError('Acción no válida.')
        singular,verb=parts; kind=mapping[singular]
        if verb=='guardar':
            item=dto.validate(kind,action.get(singular),allow_zero=True)
            old=get(db,kind,item['id']) if item['id'] else None
            if item['id'] and not old: raise ValueError('Registro inexistente.')
            if old:
                item['fotoUri']=old.get('fotoUri')
                if kind=='productos' and action.get('cantidadLeida') is not None:
                    item['cantidad']=old['cantidad']+item['cantidad']-dto.number(action['cantidadLeida'],-9_000_000_000_000_000)
                if kind=='insumos' and action.get('cantidadLeidaMil') is not None:
                    item['cantidadMil']=old['cantidadMil']+item['cantidadMil']-dto.number(action['cantidadLeidaMil'],-9_000_000_000_000_000)
            item['actualizadoEn']=dto.now_ms()
            if kind=='servicios':
                for line in item.get('insumos',[]):
                    if not get(db,'insumos',line['insumoId']): raise ValueError('Insumo inexistente.')
            item=put(db,kind,item)
            if kind in ('productos','insumos'):
                field='cantidad' if kind=='productos' else 'cantidadMil'
                self.inventory_record(db,kind,item,item[field]-(old or {}).get(field,0),'AJUSTE' if old else 'ALTA')
            if kind=='productos' and action.get('receta') is not None:
                recipe=dto.validate('recetas',action['receta']);recipe['productoId']=item['id']
                for line in recipe['lineas']:
                    if not get(db,'insumos',line['insumoId']): raise ValueError('Insumo inexistente.')
                put(db,'recetas',recipe,id=item['id'])
            result=item['id']
        elif verb=='eliminar':
            id=dto.number(action.get('id'),1); item=get(db,kind,id)
            if not item: raise ValueError('Registro inexistente.')
            if kind=='insumos' and any(any(x['insumoId']==id for x in obj.get(field,[])) for k,field in [('recetas','lineas'),('servicios','insumos')] for obj in objects(db,k)):
                raise ValueError('El insumo está en uso.')
            if kind=='insumos' and any(any(line.get('clase')=='INSUMO' and line['productoId']==id for line in sale['detalles']) or any(m.get('ventaId')==sale['id'] and m['entidad']=='INSUMO' and m['entidadId']==id for m in objects(db,'movimientos')) for sale in objects(db,'ventas')):
                raise ValueError('Conserva el insumo utilizado en ventas para mantener su trazabilidad. Puedes quitarle el precio de venta.')
            if kind in ('preajustes','insumos'): db.execute('DELETE FROM objects WHERE kind=? AND id=?',(kind,id))
            else: item.update(eliminado=True,actualizadoEn=dto.now_ms()); put(db,kind,item)
            result=id
        elif verb=='stock' and kind in ('productos','insumos'):
            id=dto.number(action.get('id'),1)
            delta=dto.number(action.get('delta' if kind=='productos' else 'deltaMil'),-9_000_000_000_000_000)
            item=get(db,kind,id)
            if not item: raise ValueError('Artículo inexistente.')
            field='cantidad' if kind=='productos' else 'cantidadMil'
            if item[field]+delta<0: raise ValueError('Existencias insuficientes.')
            result=self.stock_delta(db,'PRODUCTO' if kind=='productos' else 'INSUMO',id,delta)
            self.inventory_record(db,kind,get(db,kind,id),delta)
        else: raise ValueError('Acción no válida.')
        self.project_catalog(db)
        return result

    def employee(self,db,id):
        row=db.execute('SELECT * FROM employees WHERE id=?',(id,)).fetchone()
        if not row: raise ValueError('Empleado no encontrado.')
        return dict(row)

    def seat_allowed(self,db,id):
        state=self.license.status()
        ids=[x[0] for x in db.execute('SELECT id FROM employees WHERE active=1 ORDER BY id')]
        return id in ids[:state['secundarias']] and state['clase']!='BLOQUEADA'

    def employees(self):
        with self.connect() as db:
            return [dict(id=e['id'],name=e['name'],permissions=json.loads(e['permissions']),active=bool(e['active']),
                         linked=e['linked'],last_sync=e['last_sync'],phone=e['phone'],version=e['version'],requests_fund=bool(e['requests_fund']),
                         requests_close=bool(e['requests_close']),fund=e['fund'],card_id=e['card_id'],phone_id=e['phone_id'])
                    for e in db.execute('SELECT * FROM employees ORDER BY id')]

    def edit_employee(self,data):
        state=self.license.require_active()
        with self.connect() as db:
            self.begin(db)
            if data.get('action')=='create':
                if db.execute('SELECT COUNT(*) FROM employees WHERE active=1').fetchone()[0]>=state['secundarias']:
                    raise ValueError('Se alcanzó el límite de secundarias de la licencia.')
                permissions=data.get('permissions',['VENDER_PRODUCTOS','VENDER_SERVICIOS','EXPORTAR'])
                if not isinstance(permissions,list) or any(x not in PERMISSIONS for x in permissions): raise ValueError('Permisos no válidos.')
                return db.execute('INSERT INTO employees(name,permissions) VALUES(?,?)',(text(data.get('name')),dto.dump(permissions))).lastrowid
            id=integer(data.get('id'),1); e=self.employee(db,id)
            action=data.get('action')
            if action=='revoke':
                db.execute('UPDATE employees SET active=0,token=NULL,expires=NULL WHERE id=?',(id,))
            elif action=='permissions':
                permissions=data.get('permissions')
                if not isinstance(permissions,list) or any(x not in PERMISSIONS for x in permissions): raise ValueError('Permisos no válidos.')
                db.execute('UPDATE employees SET permissions=? WHERE id=?',(dto.dump(permissions),id))
            elif action=='fund':
                if any(t.get('empleadoId')==id and t.get('cerradoEn') is None for t in objects(db,'turnos')): raise ValueError('Ya tiene un turno abierto.')
                db.execute('UPDATE employees SET fund=?,fund_token=?,requests_fund=0 WHERE id=?',
                           (money(data.get('amount')),max(dto.now_ms(),(e['fund_token'] or 0)+1),id))
            elif action=='close':
                db.execute('UPDATE employees SET close_requested=1,requests_close=0,close_rejected=0 WHERE id=?',(id,))
            elif action=='reject-close':
                db.execute('UPDATE employees SET requests_close=0,close_rejected=1 WHERE id=?',(id,))
            elif action=='payment':
                profile=meta(db,'perfil',{}) or {}
                card=data.get('card_id'); phone=data.get('phone_id')
                if card is not None and card not in [x['id'] for x in profile.get('tarjetas',[])]: raise ValueError('Tarjeta no válida.')
                if phone is not None and phone not in [x['id'] for x in profile.get('telefonos',[])]: raise ValueError('Teléfono no válido.')
                db.execute('UPDATE employees SET card_id=?,phone_id=? WHERE id=?',(card,phone,id))
            else: raise ValueError('Acción no válida.')
            return id

    def instantanea(self,db,e):
        state=self.license.status()
        lic={key:state[key] for key in ('clase','tipo','venceEn') if state.get(key) is not None}
        if not self.seat_allowed(db,e['id']): lic={'clase':'BLOQUEADA'}
        result=dict(nombreNegocio=meta(db,'name'),empleado=dict(nombre=e['name'],permisos=json.loads(e['permissions'])),licencia=lic)
        for kind in ('productos','servicios','insumos','recetas','preajustes'):
            result[kind]=[dict(x,fotoUri=None) if kind in ('productos','servicios') else x for x in objects(db,kind) if not x.get('eliminado')]
        profile=meta(db,'perfil')
        if profile:
            card=next((x for x in profile.get('tarjetas',[]) if x['id']==e['card_id']),None) or next((x for x in profile.get('tarjetas',[]) if x['id']==profile.get('pagoTarjetaId')),None)
            phone=next((x for x in profile.get('telefonos',[]) if x['id']==e['phone_id']),None) or next((x for x in profile.get('telefonos',[]) if x['id']==profile.get('pagoTelefonoId')),None)
            if e.get('phone'): phone=dict(id=-1,numero=dto.telephone(e['phone']))
            result['perfil']=dict(nombre='',apellidos='',ci='',tarjetas=[card] if card else [],telefonos=[phone] if phone else [],pagoTarjetaId=card['id'] if card else None,pagoTelefonoId=phone['id'] if phone else None)
        prefs=meta(db,'preferencias')
        if prefs: result['preferencias']=prefs
        return result

    @staticmethod
    def receipt(db,employee,kind,code,entry):
        digest=hashlib.sha256(dto.dump(entry).encode()).hexdigest()
        row=db.execute('SELECT digest FROM remote_receipts WHERE employee=? AND kind=? AND uuid=?',(employee,kind,code)).fetchone()
        if row and row[0]!=digest: raise ValueError('UUID recibido con contenido diferente.')
        db.execute('INSERT OR IGNORE INTO remote_receipts VALUES(?,?,?,?)',(employee,kind,code,digest))

    def apply_batch(self,db,e,message):
        batch=message.get('lote')
        if not isinstance(batch,dict): raise ValueError('Lote no válido.')
        for kind in ('turnos','ventas','caja'):
            if not isinstance(batch.get(kind,[]),list) or len(batch.get(kind,[]))>10000: raise ValueError('Lote demasiado grande.')
        if any(not isinstance(entry,dict) for kind in ('turnos','ventas','caja') for entry in batch.get(kind,[])): raise ValueError('Registro de lote no válido.')
        received=dict(turnos=[],ventas=[],caja=[])
        for entry in batch.get('turnos',[]):
            code=text(entry.get('uuid')); incoming=dto.validate('turnos',entry.get('turno'))
            old=by_uuid(db,'turnos',code)
            if old and (old['abiertoEn']!=incoming['abiertoEn'] or old.get('fondoCent')!=incoming.get('fondoCent')):
                raise ValueError('El origen cambió la apertura o el fondo de un turno recibido.')
            if old and old.get('empleadoId')!=e['id']: raise ValueError('Turno de otra app.')
            if old and old.get('cerradoEn') is not None:
                received['turnos'].append(dict(uuid=code,cerrado=True)); continue
            if not old:
                if incoming.get('cerradoEn') is None and any(t.get('empleadoId')==e['id'] and t.get('cerradoEn') is None for t in objects(db,'turnos')):
                    raise ValueError('Esta app ya tiene un turno abierto.')
                incoming['id']=next_id(db,'turnos')
            else: incoming['id']=old['id']
            incoming.update(uuid=code,empleadoId=e['id'],sincronizado=True,abiertoPor=e['name'])
            put(db,'turnos',incoming)
            received['turnos'].append(dict(uuid=code,cerrado=incoming.get('cerradoEn') is not None))
        for entry in batch.get('ventas',[]):
            code=text(entry.get('uuid')); self.receipt(db,e['id'],'ventas',code,entry); old=by_uuid(db,'ventas',code)
            if old:
                if old.get('empleadoId')!=e['id']: raise ValueError('Venta de otra app.')
                received['ventas'].append(code); continue
            sale=dto.validate('ventas',entry.get('venta')); turn=by_uuid(db,'turnos',text(entry.get('turnoUuid')))
            if not turn or turn.get('empleadoId')!=e['id']: raise ValueError('Falta el turno de origen del registro.')
            # Aceptar ventas offline previas aunque la licencia haya vencido; la instantánea bloquea nuevas ventas.
            # Los permisos actuales se aplican a comandos, no invalidan ventas offline ya realizadas.
            movements=entry.get('movimientos',[])
            if not isinstance(movements,list) or len(movements)>10000: raise ValueError('Movimientos no válidos.')
            parsed=[dto.validate('movimientos',x) for x in movements]
            required=defaultdict(int)
            for line in sale['detalles']:
                if line.get('clase','PRODUCTO')=='PRODUCTO':
                    item=get(db,'productos',line['productoId'])
                    if item and item.get('categoria','').casefold()!='elaborado': required[('PRODUCTO',line['productoId'])]+=line['cantidad']
                elif line.get('clase')=='INSUMO': required[('INSUMO',line['productoId'])]+=line['cantidad']*1000
            for (entity,item_id),quantity in required.items():
                if -sum(m['delta'] for m in parsed if m['entidad']==entity and m['entidadId']==item_id)!=quantity:
                    raise ValueError('Los movimientos no corresponden a la venta.')
            sale.update(id=next_id(db,'ventas'),turnoId=turn['id'],uuid=code,empleadoId=e['id'],sincronizado=True)
            # Solo la principal puede anular/corregir una venta recibida.
            sale.update(anuladaEn=None,motivoAnulacion=None,anuladaPor=None,corrigeVentaId=None)
            put(db,'ventas',sale)
            for movement in parsed:
                if movement['delta']>0: raise ValueError('Una venta no puede aumentar existencias.')
                balance=self.stock_delta(db,movement['entidad'],movement['entidadId'],movement['delta'])
                movement.update(id=next_id(db,'movimientos'),turnoId=turn['id'],ventaId=sale['id'],existencia=balance,hechoPor=e['name'])
                if balance<0: movement['nota']='Venta offline con existencias insuficientes: revisa el conteo'
                put(db,'movimientos',movement)
            tr=sale.get('transaccion')
            if tr and tr.get('clienteFijo'):
                existing=next((x for x in objects(db,'clientesFijos') if x['ci']==tr['clienteCi']),None)
                client=dict(nombreApellidos=tr['clienteNombre'],ci=tr['clienteCi'],telefono=tr['clienteTelefono'],
                            creadoEn=(existing or {}).get('creadoEn',sale['fecha']),actualizadoEn=sale['fecha'])
                row=db.execute("SELECT id FROM objects WHERE kind='clientesFijos' AND json_extract(payload,'$.ci')=?",(client['ci'],)).fetchone()
                put(db,'clientesFijos',client,id=row[0] if row else next_id(db,'clientesFijos'))
            received['ventas'].append(code)
        for entry in batch.get('caja',[]):
            code=text(entry.get('uuid')); self.receipt(db,e['id'],'caja',code,entry); old=by_uuid(db,'caja',code)
            if old:
                if old.get('empleadoId')!=e['id']: raise ValueError('Caja de otra app.')
                received['caja'].append(code); continue
            movement=dto.validate('caja',entry.get('mov')); turn=by_uuid(db,'turnos',text(entry.get('turnoUuid')))
            if not turn or turn.get('empleadoId')!=e['id']: raise ValueError('Falta el turno de origen del registro.')
            movement.update(id=next_id(db,'caja'),turnoId=turn['id'],uuid=code,empleadoId=e['id'],sincronizado=True,hechoPor=e['name'])
            put(db,'caja',movement); received['caja'].append(code)
        for turn in objects(db,'turnos'):
            if turn.get('empleadoId')==e['id'] and turn.get('cerradoEn') is not None: self.summarize(db,turn)
        self.project_catalog(db)
        return received

    def synchronize(self,id,message):
        with self.connect() as db:
            self.begin(db)
            e=self.employee(db,id)
            if not e['active']: return {'t':'quitada'}
            received=self.apply_batch(db,e,message)
            confirmed=message.get('cambiosAplicados',[])
            if not isinstance(confirmed,list) or len(confirmed)>10000 or any(not isinstance(x,str) for x in confirmed): raise ValueError('Confirmaciones no válidas.')
            db.executemany('DELETE FROM sale_changes WHERE employee=? AND uuid=?',[(id,x) for x in confirmed])
            active=any(t.get('empleadoId')==id and t.get('cerradoEn') is None for t in objects(db,'turnos'))
            if message.get('fondoUsado') is not None and message['fondoUsado']==e['fund_token']:
                db.execute('UPDATE employees SET fund=NULL,fund_token=NULL WHERE id=?',(id,))
            e=self.employee(db,id)
            requested=bool(message.get('pideFondo')) and not active and e['fund'] is None
            close_requested=bool(message.get('solicitaCierre')) and active and not e['close_requested']
            phone=message.get('telefono')
            if phone is not None and (not isinstance(phone,str) or len(phone)>32): raise ValueError('Teléfono no válido.')
            if phone: phone=dto.telephone(phone)
            version=message.get('versionCode')
            if version is not None: dto.number(version)
            db.execute('UPDATE employees SET requests_fund=?,requests_close=?,last_sync=?,phone=?,version=?,close_requested=? WHERE id=?',
                       (int(requested),int(close_requested),dto.now_ms(),phone,version,int(active and e['close_requested']),id))
            snapshot=self.instantanea(db,e); digest=hashlib.sha256(dto.dump(snapshot).encode()).hexdigest()
            result=dict(t='sync_ok',id=dto.number(message.get('id')),recibidos=received,hash=digest,
                        cerrarTurno=bool(active and e['close_requested']),cierreRechazado=bool(e['close_rejected']),asignaFondos=True,
                        cambiosVentas=[json.loads(r[0]) for r in db.execute('SELECT payload FROM sale_changes WHERE employee=?',(id,))])
            db.execute('UPDATE employees SET close_rejected=0 WHERE id=?',(id,))
            if digest!=message.get('hash'): result['instantanea']=snapshot
            if e['fund'] is not None: result['fondo']=dict(cent=e['fund'],token=e['fund_token'])
            return result

    def command(self,id,message):
        sequence=dto.number(message.get('id')); key=message.get('clave'); action=message.get('accion')
        if not isinstance(action,dict): raise ValueError('Comando no válido.')
        if key is not None: key=text(key)
        with self.connect() as db:
            self.begin(db)
            e=self.employee(db,id)
            encoded=dto.dump(action)
            old=db.execute('SELECT request,result FROM commands WHERE employee=? AND key=?',(id,key)).fetchone() if key else None
            if old:
                if old['request']!=encoded: raise ValueError('El identificador ya se utilizó para otra operación.')
                return dict(json.loads(old['result']),id=sequence)
            needed='CAMBIAR_PRECIOS' if action.get('t') in ('producto_precios','preajuste_guardar','preajuste_eliminar') else 'EDITAR_INVENTARIO'
            result=dict(t='cmd_ok',id=sequence)
            if not e['active'] or needed not in json.loads(e['permissions']) or not self.seat_allowed(db,id):
                result['error']='permiso'
            else:
                db.execute('SAVEPOINT command_data')
                try: result['valor']=self.catalog_command(db,action)
                except (ValueError,KeyError,TypeError,sqlite3.IntegrityError):
                    db.execute('ROLLBACK TO command_data'); result['error']='validacion:datos:FORMATO'
                finally: db.execute('RELEASE command_data')
            if key: db.execute('INSERT INTO commands VALUES(?,?,?,?)',(id,key,encoded,dto.dump(result)))
            return result

    def document(self,db):
        result=meta(db,'extra',{}) or {}
        result.update(formato='spvi-respaldo',version=4,creadoEn=dto.now_ms(),appVersion='SPVI Web principal')
        for kind in dto.COLLECTIONS: result[kind]=objects(db,kind)
        for key in ('perfil','preferencias'): result[key]=meta(db,key)
        result['empleados']=[dict(id=e['id'],nombre=e['name'],permisos=json.loads(e['permissions']),creadoEn=e['linked'] or dto.now_ms(),
                                  tarjetaId=e['card_id'],telefonoId=e['phone_id']) for e in db.execute('SELECT * FROM employees WHERE active=1')]
        from .photos import document as photo_document
        result['webFotos']=photo_document(db)
        state=self.license.status()
        if state.get('id'): result['licencia']=dict(id=state['id'],tipo=state.get('tipo') or 'MENSUAL',secundarias=state['secundarias'],venceEn=state['venceEn'])
        return result

    def export_backup(self,password,android=False):
        with self.connect() as db:
            db.execute('BEGIN')
            document=self.document(db)
            if not android:
                value=backups.snapshot(db)
                value.update(version=2,business=document,name=meta(db,'name'))
        return android_backup.encode(document,password) if android else backups.seal(value,password)

    def import_backup(self,content,password,android=False,safety_password=None):
        value=android_backup.decode(content,password) if android else backups.unseal(content,password)
        if not android and (not isinstance(value,dict) or value.get('format')!='spvi-desktop' or value.get('version') not in (1,2)):
            raise ValueError('Versión de respaldo de escritorio no compatible.')
        if not android and value.get('version')==1:
            import tempfile
            tables=backups.validate(value)
            with tempfile.TemporaryDirectory(prefix='spvi-migracion-') as folder:
                temporary_store=Store(Path(folder)/'legacy.sqlite3')
                with temporary_store.connect() as db: backups.insert(db,tables)
                legacy=Business(temporary_store,self.license,self.box)
                with legacy.connect() as db: value=dict(version=2,business=legacy.document(db),name=meta(db,'name'))
        document=dto.validate_document(value if android else value.get('business'))
        from .photos import validate as validate_photos
        photos=validate_photos(document.get('webFotos',[]))
        for kind,id,_ in photos:
            if not any(x['id']==id for x in document[kind]): raise ValueError('Foto de un artículo inexistente.')
        if any(t.get('cerradoEn') is None for t in document['turnos']): raise ValueError('El respaldo tiene turnos abiertos. Ciérralos en el origen.')
        employees=document.get('empleados',[])
        if not isinstance(employees,list) or len(employees)>1000: raise ValueError('Empleados no válidos.')
        for e in employees:
            dto.number(e.get('id'),1); text(e.get('nombre'))
            if not isinstance(e.get('permisos',[]),list) or any(p not in PERMISSIONS for p in e.get('permisos',[])): raise ValueError('Permisos no válidos.')
        if len({e['id'] for e in employees})!=len(employees): raise ValueError('Empleados repetidos.')
        with self.connect() as db:
            self.begin(db)
            if any(t.get('cerradoEn') is None for t in objects(db,'turnos')): raise ValueError('Cierra todos los turnos antes de restaurar.')
            previous=backups.snapshot(db); previous.update(version=2,business=self.document(db),name=meta(db,'name'))
            safety_password=safety_password if safety_password is not None else password
            if not isinstance(safety_password,str) or len(safety_password)<12: raise ValueError('Para restaurar utiliza una copia protegida con contraseña de al menos 12 caracteres.')
            target=Path(self.path).parent/'antes_de_restaurar.spvidesk'
            temporary=target.with_suffix('.tmp')
            encrypted=backups.seal(previous,safety_password)
            with open(temporary,'wb') as file:
                os.chmod(temporary,0o600);file.write(encrypted);file.flush();os.fsync(file.fileno())
            os.replace(temporary,target)
            for table in ('sales','movements','shifts','items','local_refs','objects','commands','sale_changes','grouped_sales','employees','http_requests','photos','remote_receipts'): db.execute('DELETE FROM '+table)
            for kind in dto.COLLECTIONS:
                for index,obj in enumerate(document[kind],1):
                    put(db,kind,obj,id=obj.get('id',obj.get('productoId',index)))
            db.executemany('INSERT INTO photos VALUES(?,?,?)',photos)
            for key in ('perfil','preferencias'): set_meta(db,key,document.get(key))
            extra={key:val for key,val in document.items() if key not in dto.COLLECTIONS and key not in ('empleados','perfil','preferencias','licencia','webFotos')}
            set_meta(db,'extra',extra)
            for e in employees:
                db.execute('INSERT INTO employees(id,name,permissions,active,card_id,phone_id) VALUES(?,?,?,?,?,?)',
                    (e['id'],e['nombre'],dto.dump(e.get('permisos',[])),0,e.get('tarjetaId'),e.get('telefonoId')))
            set_meta(db,'business_id',str(uuid.uuid4()))
            if not android: set_meta(db,'name',text(value.get('name','Mi negocio')))
            self.project_catalog(db)

    def settings(self,data):
        self.license.require_active()
        with self.connect() as db:
            self.begin(db)
            if 'name' in data: set_meta(db,'name',text(data['name']))
            if 'perfil' in data:
                profile=dto.profile(data['perfil'])
                if not isinstance(profile,dict): raise ValueError('Perfil no válido.')
                for key in ('nombre','apellidos','ci'):
                    if not isinstance(profile.get(key,''),str) or len(profile.get(key,''))>120: raise ValueError('Perfil no válido.')
                for kind,key in [('tarjetas','pagoTarjetaId'),('telefonos','pagoTelefonoId')]:
                    rows=profile.get(kind,[])
                    if not isinstance(rows,list) or len(rows)>100: raise ValueError('Demasiadas cuentas.')
                    seen=set()
                    for row in rows:
                        if not isinstance(row,dict): raise ValueError('Cuenta no válida.')
                        dto.number(row.get('id'),-1 if kind=='telefonos' else 1); text(row.get('numero'),32)
                        if row['id'] in seen: raise ValueError('Identificador repetido.')
                        seen.add(row['id'])
                    if profile.get(key) is not None and profile[key] not in seen: raise ValueError('Selecciona una cuenta de la lista.')
                    column='card_id' if kind=='tarjetas' else 'phone_id'
                    for employee in db.execute('SELECT id,'+column+' FROM employees').fetchall():
                        if employee[column] is not None and employee[column] not in seen:
                            db.execute('UPDATE employees SET '+column+'=NULL WHERE id=?',(employee['id'],))
                for field in ('nombre','apellidos','ci'): profile.setdefault(field,'')
                set_meta(db,'perfil',profile)
            if 'preferencias' in data:
                if not isinstance(data['preferencias'],dict): raise ValueError('Preferencias no válidas.')
                set_meta(db,'preferencias',dto.preferences(data['preferencias']))

    def records(self):
        with self.connect() as db:
            db.execute('BEGIN')
            return dict(role=self.ROLE,name=meta(db,'name'),business_id=meta(db,'business_id'),
                        perfil=meta(db,'perfil'),preferencias=meta(db,'preferencias'),
                        **{kind:objects(db,kind) for kind in dto.COLLECTIONS})

    def dashboard(self,days=30):
        if days not in (7,30,90): raise ValueError('Período no válido.')
        with self.connect() as db:
            db.execute('BEGIN')
            now=dto.now_ms(); start=now-days*86400000
            sales=[x for x in objects(db,'ventas') if x.get('anuladaEn') is None]
            current=[s for s in sales if start<=s['fecha']<=now]
            previous=sum(total(s) for s in sales if start-days*86400000<=s['fecha']<start)
            items=[];preferences=meta(db,'preferencias',{}) or {}
            for kind in ('productos','servicios','insumos'):
                for item in objects(db,kind):
                    if item.get('eliminado'): continue
                    row=db.execute('SELECT local_id FROM local_refs WHERE kind=? AND object_id=?',(kind,item['id'])).fetchone()
                    if not row: continue
                    elaborated=kind=='productos' and item.get('categoria','').casefold()=='elaborado'
                    items.append(dict(elaborated=elaborated,id=row[0],domain_id=item['id'],name=item['nombre'],kind={'productos':'producto','servicios':'servicio','insumos':'insumo'}[kind],sellable=kind!='insumos' or item.get('precioVentaCent') is not None,
                        price=(item.get('precioVentaCent') or 0) if kind!='servicios' else item['importeCent'],
                        cost=item['precioCostoCent'] if kind=='productos' else item['precioCent'] if kind=='insumos' else self.recipe_cost(db,item.get('insumos',[])),
                        stock=item.get('cantidadMil',0)/1000 if kind=='insumos' else item.get('cantidad',0),minimum=(item.get('nivelBajoMil') if item.get('nivelBajoMil') is not None else preferences.get('insumoBajoMil',5000))/1000 if kind=='insumos' else item.get('nivelBajo') if item.get('nivelBajo') is not None else preferences.get('productoBajo',5),expires=item.get('fechaCaducidad')))
            revenue=sum(total(s) for s in current); daily=defaultdict(lambda:dict(revenue=0,margin=0)); tops=defaultdict(int)
            for sale in current:
                day=dto.iso(sale['fecha'])[:10]; daily[day]['revenue']+=total(sale); daily[day]['margin']+=total(sale)-cost(sale)
                for line in sale['detalles']: tops[line['nombre']]+=line['precioUnitarioCent']*line['cantidad']
            local=db.execute('SELECT * FROM shifts WHERE closed IS NULL').fetchone()
            shifts=[]
            for t in reversed(objects(db,'turnos')):
                summary=t.get('resumen') or {}
                moves=[m for m in objects(db,'caja') if m['turnoId']==t['id']]
                expected=(t.get('fondoCent') or 0)+sum(total(s) for s in sales if s['turnoId']==t['id'] and s['metodoPago']=='EFECTIVO')+sum(m['importeCent']*(1 if m['tipo']=='ENTRADA' else -1) for m in moves)
                shifts.append(dict(id=t['id'],opened=dto.iso(t['abiertoEn']),closed=dto.iso(t['cerradoEn']) if t.get('cerradoEn') is not None else None,
                    employee=t.get('empleadoId'),fund=t.get('fondoCent') or 0,counted=t.get('contadoCent'),expected=expected))
            recent=[]
            for sale in sorted(sales,key=lambda s:s['fecha'],reverse=True)[:20]:
                recent.append(dict(id=sale['id'],name=', '.join(d['nombre'] for d in sale['detalles']),quantity=sum(d['cantidad'] for d in sale['detalles']),
                    method=sale['metodoPago'].lower(),created=dto.iso(sale['fecha']),total=total(sale),price=0,employee=sale.get('empleadoId')))
            return dict(role=self.ROLE,business_id=meta(db,'business_id'),revenue=revenue,margin=sum(total(s)-cost(s) for s in current),operations=len(current),
                cash=sum(total(s) for s in current if s['metodoPago']=='EFECTIVO'),transfers=sum(total(s) for s in current if s['metodoPago']=='TRANSFERENCIA'),
                previous_revenue=previous,revenue_change_percent=round((revenue-previous)*100/previous,1) if previous else None,
                daily=[dict(day=k,**daily[k]) for k in sorted(daily)],below_cost=[x for x in items if x['sellable'] and x['price']<=x['cost']],
                items=items,low_stock=[x for x in items if x['kind']!='servicio' and not x['elaborated'] and x['stock']<=x['minimum']],
                inventory_cost=round(sum(x['stock']*x['cost'] for x in items if x['kind']!='servicio' and not x['elaborated'])),
                shift=dict(local) if local else None,expected=self.store.expected(db,local) if local else None,
                shifts=shifts[:10],recent=recent,top=[dict(name=k,total=v) for k,v in sorted(tops.items(),key=lambda x:-x[1])[:5]])

    def report(self,scope,query='',ids=None):
        with self.connect() as db:
            db.execute('BEGIN')
            if scope=='turnos':
                rows=[]; shifts=objects(db,'turnos')
                for turn in shifts:
                    # Recalcular sin mutar la BD de lectura.
                    sales=[s for s in objects(db,'ventas') if s['turnoId']==turn['id'] and s.get('anuladaEn') is None]
                    moves=[m for m in objects(db,'caja') if m['turnoId']==turn['id']]
                    expected=(turn.get('fondoCent') or 0)+sum(total(s) for s in sales if s['metodoPago']=='EFECTIVO')+sum(m['importeCent']*(1 if m['tipo']=='ENTRADA' else -1) for m in moves)
                    counted=turn.get('contadoCent')
                    rows.append([turn['id'],dto.iso(turn['abiertoEn']),dto.iso(turn['cerradoEn']) if turn.get('cerradoEn') is not None else 'Abierto',
                                 Decimal(turn.get('fondoCent') or 0)/100,Decimal(expected)/100,Decimal(counted)/100 if counted is not None else '',
                                 Decimal(counted-expected)/100 if counted is not None else ''])
                return ['Turno','Apertura UTC','Cierre UTC','Fondo CUP','Esperado CUP','Contado CUP','Diferencia CUP'],rows,shifts
            kinds=('productos','insumos') if scope=='inventario' else ('servicios',) if scope=='servicios' else ('productos',)
            result=[]
            for kind in kinds:
                for item in objects(db,kind):
                    if item.get('eliminado') or query.casefold() not in item['nombre'].casefold(): continue
                    ref=db.execute('SELECT local_id FROM local_refs WHERE kind=? AND object_id=?',(kind,item['id'])).fetchone()
                    if ids is not None and (not ref or ref[0] not in ids): continue
                    photo=db.execute('SELECT content FROM photos WHERE kind=? AND id=?',(kind,item['id'])).fetchone()
                    result.append(dict(id=ref[0] if ref else item['id'],name=item['nombre'],
                        price=(item.get('precioVentaCent') or 0) if kind!='servicios' else item['importeCent'],
                        cost=item['precioCostoCent'] if kind=='productos' else item['precioCent'] if kind=='insumos' else self.recipe_cost(db,item.get('insumos',[])),
                        stock=Decimal(item['cantidadMil'])/1000 if kind=='insumos' else item.get('cantidad',0),
                        unit=item.get('unidad','u'),photo=bytes(photo[0]) if photo else None))
            columns=['Nombre','Precio CUP','Costo CUP']+(['Existencias','Unidad'] if scope!='servicios' else [])
            rows=[[x['name'],Decimal(x['price'])/100,Decimal(x['cost'])/100]+([x['stock'],x['unit']] if scope!='servicios' else []) for x in result]
            return columns,rows,result
