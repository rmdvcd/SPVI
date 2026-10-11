"""Datos de prueba «Bodega cubana» (solo modo de desarrollo): espejo de ``domain/.../seed`` de la app móvil.

Mismo catálogo (11 insumos, 32 productos con 2 elaborados y sus recetas, 8 servicios, 12 clientes fijos, 2 tarjetas
y 2 teléfonos) y mismas reglas del historial: 548 días, descanso cada séptimo día, 6–14 ventas por día, 22 % de
servicios, 30 % por transferencia (60 % a clientes fijos), alguna venta anulada, movimientos de caja semanales,
descuadres ocasionales y dos vendedores en rachas de 14 días, de 08:00 a 20:00 (hora de Cuba).

El generador aleatorio de Python no es el de Kotlin: el historial es **similar** al del móvil, no idéntico.
Borra todos los datos del negocio de esta PC (no toca la licencia ni la clave de acceso).
"""
import random
import uuid
from datetime import datetime, time, timedelta
from zoneinfo import ZoneInfo

from . import dto

ZONE = ZoneInfo('America/Havana')
DAYS = 548
SEED = 2907
INITIAL_FUND = 2000
WORKDAY = 12 * 3600
STREAK = 14
SELLERS = (('María', 'Fernández'), ('Jorge', 'Pérez'))
EVENTUAL = 'EVT:'

INSUMOS = (
    ('harina-trigo', 'Harina de trigo', 'KILOGRAMO', 180), ('azucar-blanca', 'Azúcar blanca', 'KILOGRAMO', 200),
    ('aceite-vegetal', 'Aceite vegetal', 'LITRO', 800), ('sal-fina', 'Sal fina', 'KILOGRAMO', 120),
    ('levadura', 'Levadura', 'GRAMO', 2), ('carne-cerdo', 'Carne de cerdo', 'KILOGRAMO', 900),
    ('coco-rallado', 'Coco rallado', 'KILOGRAMO', 500), ('leche-polvo', 'Leche en polvo', 'KILOGRAMO', 1200),
    ('hilo-bobina', 'Hilo (bobina)', 'UNIDAD', 150), ('gasolina', 'Gasolina', 'LITRO', 300),
    ('detergente-liquido', 'Detergente líquido', 'LITRO', 400),
)
ELABORADO = 'Elaborado'
PRODUCTOS = (
    ('arroz', 'Granos y cereales', 'Arroz (lb)', 150, 180, 50, 10),
    ('frijoles-negros', 'Granos y cereales', 'Frijoles negros (lb)', 180, 220, None, None),
    ('azucar-refino', 'Granos y cereales', 'Azúcar refino (lb)', 160, 200, None, None),
    ('aceite-bote', 'Aceites y grasas', 'Aceite (botella 1 L)', 700, 850, 20, 5),
    ('cafe-mezcla', 'Bebidas', 'Café mezcla (paquete)', 250, 320, None, None),
    ('sal-paquete', 'Condimentos y especias', 'Sal (paquete)', 100, 130, None, None),
    ('pan-suave', 'Panadería y dulcería', 'Pan suave (unidad)', 25, 35, None, None),
    ('galletas-dulces', 'Panadería y dulcería', 'Galletas dulces (paquete)', 120, 150, None, None),
    ('cake', 'Panadería y dulcería', 'Cake (unidad)', 200, 260, None, None),
    ('refresco-lata', 'Bebidas', 'Refresco (lata)', 110, 150, None, None),
    ('jugo-mango', 'Bebidas', 'Jugo de mango (caja)', 90, 120, None, None),
    ('malta', 'Bebidas', 'Malta (botella)', 130, 170, None, None),
    ('leche-entera', 'Lácteos', 'Leche entera (litro)', 220, 280, None, None),
    ('queso-blanco', 'Lácteos', 'Queso blanco (lb)', 550, 680, None, None),
    ('yogurt-natural', 'Lácteos', 'Yogurt natural (vaso)', 80, 110, None, None),
    ('picadillo', 'Cárnicos y embutidos', 'Picadillo (lb)', 380, 460, None, None),
    ('pollo-congelado', 'Cárnicos y embutidos', 'Pollo congelado (lb)', 320, 400, None, None),
    ('salchichas', 'Cárnicos y embutidos', 'Salchichas (paquete)', 280, 350, None, None),
    ('pure-tomate', 'Conservas', 'Puré de tomate (caja)', 140, 180, None, None),
    ('sardinas', 'Conservas', 'Sardinas (lata)', 160, 200, None, None),
    ('comino', 'Condimentos y especias', 'Comino (sobre)', 60, 90, None, None),
    ('vinagre', 'Condimentos y especias', 'Vinagre (botella)', 110, 150, None, None),
    ('caramelos', 'Confituras y snacks', 'Caramelos (paquete)', 70, 100, None, None),
    ('jabon-bano', 'Aseo personal', 'Jabón de baño (unidad)', 90, 130, 30, 8),
    ('champu', 'Aseo personal', 'Champú (pomo)', 320, 420, None, None),
    ('pasta-dental', 'Aseo personal', 'Pasta dental (tubo)', 210, 280, None, None),
    ('desodorante', 'Aseo personal', 'Desodorante (unidad)', 260, 340, None, None),
    ('detergente-polvo', 'Limpieza del hogar', 'Detergente en polvo (paquete)', 240, 310, None, None),
    ('cloro', 'Limpieza del hogar', 'Cloro (litro)', 130, 170, None, None),
    ('cigarros-fuertes', 'Cigarros y tabacos', 'Cigarros fuertes (caja)', 150, 190, None, None),
    ('pan-lechon', ELABORADO, 'Pan con lechón (unidad)', 180, 280, None, None),
    ('dulce-coco', ELABORADO, 'Dulce de coco (unidad)', 90, 150, None, None),
)
RECETAS = {
    'pan-lechon': (('harina-trigo', 200), ('levadura', 10_000), ('carne-cerdo', 150), ('aceite-vegetal', 20),
                   ('sal-fina', 5)),
    'dulce-coco': (('coco-rallado', 60), ('azucar-blanca', 50), ('leche-polvo', 20)),
}
SERVICIOS = (
    ('corte-pelo', 'Corte de pelo', 'Belleza', 300, ()),
    ('arreglo-ropa', 'Arreglo de ropa', 'Costura', 250, (('hilo-bobina', 1_000),)),
    ('entrega-domicilio', 'Entrega a domicilio', 'Mensajería', 150, (('gasolina', 300),)),
    ('lavado-ropa', 'Lavado de ropa (lb)', 'Lavandería', 120, (('detergente-liquido', 100),)),
    ('afilado', 'Afilado de cuchillos', 'Reparación', 100, ()),
    ('manicura', 'Manicura', 'Belleza', 350, ()),
    ('planchado', 'Planchado (pieza)', 'Lavandería', 80, ()),
    ('reparacion-calzado', 'Reparación de calzado', 'Reparación', 400, ()),
)
CLIENTES = (
    ('María Pérez', '90051512345', '52123456'), ('Jorge Hernández', '85082323456', '53123456'),
    ('Ana García', '92010134567', '54123456'), ('Luis Fernández', '78123045678', '55123456'),
    ('Carmen Rodríguez', '95070256789', '56123456'), ('Pedro Martínez', '80091567890', '57123456'),
    ('Rosa López', '88110478901', '58123456'), ('Miguel Sánchez', '76032989012', '59123456'),
    ('Elena Díaz', '93121790123', '50123456'), ('Carlos Ruiz', '84060801234', '51123456'),
    ('Lucía Torres', '90112512345', '52123457'), ('Rafael Moreno', '79091823456', '53123457'),
)
TARJETAS = (('9225123456789012', 'Tarjeta MLC María'), ('9226987654321098', 'Tarjeta CUP Jorge'))
TELEFONOS = (('52123456', 'Pago María'), ('53123456', 'Pago Jorge'))


def _base36(n):
    digits = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'
    out = ''
    while True:
        n, r = divmod(n, 36)
        out = digits[r] + out
        if not n: return out


def eventual_name(k):
    letters = ''
    while True:
        letters += chr(ord('A') + k % 26)
        k = k // 26 - 1
        if k < 0: break
    return 'Eventual ' + letters[::-1]


def _java_hash(text):
    h = 0
    for ch in text:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h


def _client(key):
    name = key[len(EVENTUAL):] if key.startswith(EVENTUAL) else key
    fixed = next((c for c in CLIENTES if c[0] == name), None)
    if fixed: return fixed
    h = _java_hash(name)
    return name, '62%09d' % (h % 1_000_000_000), '5%07d' % ((h >> 16 & 0xFFFFFF) % 10_000_000)


def _products(r, n):
    order = list(range(len(PRODUCTOS))); r.shuffle(order)
    chosen = order[:n]
    elaborated = [i for i in chosen if PRODUCTOS[i][0] in RECETAS]
    if len(elaborated) > 1:
        substitute = next(i for i in order if i not in chosen and PRODUCTOS[i][0] not in RECETAS)
        chosen[chosen.index(elaborated[1])] = substitute
    return [(PRODUCTOS[i][0], 'PRODUCTO') for i in chosen]


def _services(r, n):
    order = list(range(len(SERVICIOS))); r.shuffle(order)
    return [(SERVICIOS[i][0], 'SERVICIO') for i in order[:n]]


def plan_day(index, seed=SEED):
    """Plan determinista de un día: ``None`` si es de descanso."""
    r = random.Random(seed ^ (index * 0x9E3779B9))
    if index % 7 == 6: return None
    sales = []
    for n in range(6 + r.randrange(9)):
        services = r.randrange(100) < 22
        lines = (_services if services else _products)(r, 1 + r.randrange(4))
        lines = [(key, kind, 1 + r.randrange(3)) for key, kind in lines]
        transfer = r.randrange(100) < 30
        client = None
        if transfer:
            client = CLIENTES[r.randrange(len(CLIENTES))][0] if r.randrange(100) < 60 else EVENTUAL + eventual_name(r.randrange(500))
        sales.append(dict(lines=lines, method='TRANSFERENCIA' if transfer else 'EFECTIVO', client=client, void=False,
                          number='SD' + _base36(index * 100 + n).rjust(4, '0') + 'X7'))
    if len(sales) > 1 and r.randrange(100) < 15:
        sales[r.randrange(len(sales))]['void'] = True
    cash = []
    if index % 7 == 3:
        entry = index % 14 == 3
        cash.append(('ENTRADA' if entry else 'SALIDA', 500 + r.randrange(16) * 100,
                     'Aporte para sencillo' if entry else 'Pago a proveedor'))
    mismatch = (1 if r.random() < .5 else -1) * (50 + r.randrange(8) * 50) if index % 28 in (10, 24) else 0
    return dict(sales=sales, cash=cash, mismatch=mismatch)


def _ms(moment): return int(moment.timestamp() * 1000)


def build(days=DAYS, today=None):
    """Documento ``spvi-respaldo`` v4 con el catálogo y el historial de prueba."""
    today = today or datetime.now(ZONE).date()
    plans = [plan_day(i) for i in range(days)]
    base = today - timedelta(days=days)
    created = _ms(datetime.combine(base, time(8), ZONE))
    demand_products, demand_supplies = {}, {}
    recipes = dict(RECETAS); recipes.update({s[0]: s[4] for s in SERVICIOS})
    for plan in filter(None, plans):
        for sale in plan['sales']:
            for key, kind, quantity in sale['lines']:
                if kind == 'PRODUCTO' and key not in RECETAS:
                    demand_products[key] = demand_products.get(key, 0) + quantity
                for supply, milli in recipes.get(key, ()):
                    demand_supplies[supply] = demand_supplies.get(supply, 0) + milli * quantity
    supplies, supply_ids = [], {}
    for i, (key, name, unit, price) in enumerate(INSUMOS, 1):
        supply_ids[key] = i
        supplies.append(dict(id=i, nombre=name, unidad=unit, precioCent=price * 100, precioVentaCent=None,
                             cantidadMil=max(1000, demand_supplies.get(key, 0) * 120 // 100),
                             creadoEn=created, actualizadoEn=created))
    by_supply = {x['id']: x for x in supplies}

    def recipe_cost(lines):
        return sum((by_supply[supply_ids[k]]['precioCent'] * m + 500) // 1000 for k, m in lines)

    products, product_ids, recetas = [], {}, []
    for i, (key, category, name, cost, price, low, critical) in enumerate(PRODUCTOS, 1):
        product_ids[key] = i
        elaborated = key in RECETAS
        products.append(dict(id=i, categoria=category, nombre=name, precioCostoCent=cost * 100, precioVentaCent=price * 100,
                             cantidad=0 if elaborated else max(50, demand_products.get(key, 0) * 120 // 100),
                             nivelBajo=None if elaborated else low, nivelCritico=None if elaborated else critical,
                             creadoEn=created, actualizadoEn=created, eliminado=False))
        if elaborated:
            recetas.append(dict(productoId=i, lineas=[dict(insumoId=supply_ids[k], cantidadMil=m) for k, m in RECETAS[key]]))
    services, service_ids = [], {}
    for i, (key, name, kind, amount, lines) in enumerate(SERVICIOS, 1):
        service_ids[key] = i
        services.append(dict(id=i, nombre=name, tipo=kind, importeCent=amount * 100, creadoEn=created, actualizadoEn=created,
                             eliminado=False, insumos=[dict(insumoId=supply_ids[k], cantidadMil=m) for k, m in lines]))
    by_product = {x['id']: x for x in products}
    movements, sales, cash, turns, clients = [], [], [], [], {}

    def movement(when, kind, entity, item, delta, turn=None, sale=None, who='Bodega La Esquina'):
        field = 'cantidad' if entity == 'PRODUCTO' else 'cantidadMil'
        item[field] += delta
        movements.append(dict(id=len(movements) + 1, fecha=when, tipo=kind, entidad=entity, entidadId=item['id'],
                              nombre=item['nombre'], delta=delta, existencia=item[field], turnoId=turn, ventaId=sale,
                              hechoPor=who))

    for item in products:
        if item['cantidad']:
            amount, item['cantidad'] = item['cantidad'], 0
            movement(created, 'ALTA', 'PRODUCTO', item, amount)
    for item in supplies:
        amount, item['cantidadMil'] = item['cantidadMil'], 0
        movement(created, 'ALTA', 'INSUMO', item, amount)

    fund = INITIAL_FUND * 100
    card, phone = TARJETAS[0][0], '+53' + TELEFONOS[0][0]
    seller = SELLERS[0]
    for index, plan in enumerate(plans):
        day = base + timedelta(days=index)
        seller = SELLERS[(index // STREAK) % len(SELLERS)]
        if plan is None: continue
        who = '%s %s' % seller
        opened = datetime.combine(day, time(8), ZONE)
        closing = _ms(datetime.combine(day, time(20), ZONE))
        turn = dict(id=len(turns) + 1, uuid=str(uuid.UUID(int=random.Random(index).getrandbits(128), version=4)),
                    abiertoEn=_ms(opened), fondoCent=fund, abiertoPor=who)
        cash_sales = 0
        for n, planned in enumerate(plan['sales']):
            when = _ms(opened + timedelta(seconds=(n + 1) * WORKDAY // (len(plan['sales']) + 1)))
            sale = dict(id=len(sales) + 1, uuid=str(uuid.UUID(int=random.Random(index * 1000 + n).getrandbits(128), version=4)),
                        turnoId=turn['id'], fecha=when, metodoPago=planned['method'], detalles=[])
            for key, kind, quantity in planned['lines']:
                if kind == 'SERVICIO':
                    item = services[service_ids[key] - 1]
                    lines, price, category = SERVICIOS[service_ids[key] - 1][4], item['importeCent'], item['tipo']
                    cost = recipe_cost(lines)
                else:
                    item = by_product[product_ids[key]]
                    lines, price, category = RECETAS.get(key, ()), item['precioVentaCent'], item['categoria']
                    cost = recipe_cost(lines) if lines else item['precioCostoCent']
                sale['detalles'].append(dict(id=len(sale['detalles']) + 1, productoId=item['id'], nombre=item['nombre'],
                                             categoria=category, cantidad=quantity, precioBaseCent=price,
                                             precioUnitarioCent=price, costoUnitarioCent=cost, clase=kind))
                if kind == 'PRODUCTO' and key not in RECETAS:
                    movement(when, 'VENTA', 'PRODUCTO', item, -quantity, turn['id'], sale['id'], who)
                for supply, milli in lines:
                    movement(when, 'CONSUMO', 'INSUMO', by_supply[supply_ids[supply]], -milli * quantity, turn['id'], sale['id'], who)
            amount = sum(d['precioUnitarioCent'] * d['cantidad'] for d in sale['detalles'])
            if planned['client']:
                name, ci, telephone = _client(planned['client'])
                fixed = not planned['client'].startswith(EVENTUAL)
                sale['transaccion'] = dict(id=sale['id'], fecha=when, importeCent=amount, numero=planned['number'],
                                           clienteNombre=name, clienteCi=ci, clienteTelefono='+53' + telephone,
                                           clienteFijo=fixed, tarjetaCobro=card, telefonoCobro=phone)
                if fixed:
                    old = clients.get(ci)
                    clients[ci] = dict(nombreApellidos=name, ci=ci, telefono='+53' + telephone,
                                       creadoEn=old['creadoEn'] if old else when, actualizadoEn=when)
            if planned['void']:
                sale.update(anuladaEn=when + 60_000, motivoAnulacion='Cliente desistió', anuladaPor=who)
                for original in [m for m in movements if m.get('ventaId') == sale['id']]:
                    target = by_product[original['entidadId']] if original['entidad'] == 'PRODUCTO' else by_supply[original['entidadId']]
                    movement(when + 60_000, 'ANULACION', original['entidad'], target, -original['delta'], turn['id'], sale['id'], who)
            elif planned['method'] == 'EFECTIVO':
                cash_sales += amount
            sales.append(sale)
        balance = 0
        for kind, pesos, reason in plan['cash']:
            cash.append(dict(id=len(cash) + 1, uuid=str(uuid.UUID(int=random.Random(-index - 1).getrandbits(128), version=4)),
                             turnoId=turn['id'], fecha=closing, tipo=kind, importeCent=pesos * 100, motivo=reason, hechoPor=who))
            balance += pesos * 100 if kind == 'ENTRADA' else -pesos * 100
        counted = fund + cash_sales + balance + plan['mismatch'] * 100
        turn.update(cerradoEn=closing, contadoCent=counted, cerradoPor=who)
        turns.append(turn)
        fund = counted
    for ci, (name, _, telephone) in ((c[1], c) for c in CLIENTES):
        clients.setdefault(ci, dict(nombreApellidos=name, ci=ci, telefono='+53' + telephone, creadoEn=created, actualizadoEn=created))
    preset = dict(id=1, nombre='Fines de semana +5%', puntosBasicos=500, productoIds=list(range(1, 6)), activo=True,
                  metodoPago=None, importeMinimoCent=0)
    profile = dict(nombre=seller[0], apellidos=seller[1], ci='62010112345',
                   tarjetas=[dict(id=i, numero=n, alias=a) for i, (n, a) in enumerate(TARJETAS, 1)],
                   telefonos=[dict(id=i, numero='+53' + n, alias=a) for i, (n, a) in enumerate(TELEFONOS, 1)],
                   pagoTarjetaId=1, pagoTelefonoId=1)
    return dict(formato='spvi-respaldo', version=4, creadoEn=dto.now_ms(), appVersion='SPVI Web principal (datos de prueba)',
                productos=products, servicios=services, insumos=supplies, recetas=recetas, preajustes=[preset],
                turnos=turns, ventas=sales, movimientos=movements, caja=cash, clientesFijos=list(clients.values()),
                perfil=profile, preferencias=dict(productoBajo=5, productoCritico=1, insumoBajoMil=5000, insumoCriticoMil=1000),
                empleados=[])


def load(business, days=DAYS, today=None):
    """Reemplaza el negocio de esta PC por los datos de prueba. Devuelve un resumen de lo creado."""
    document = dto.validate_document(build(days, today))
    from .business import objects
    with business.connect() as db:
        business.begin(db)
        if any(t.get('cerradoEn') is None for t in objects(db, 'turnos')):
            raise ValueError('Cierra el turno abierto antes de cargar los datos de prueba.')
        business.replace_all(db, document, [], [], 'Bodega La Esquina')
        index = business.turn_index(db)
        for turn in objects(db, 'turnos'): business.summarize(db, turn, index)
    return dict(dias=days, turnos=len(document['turnos']), ventas=len(document['ventas']),
                productos=len(document['productos']), servicios=len(document['servicios']), insumos=len(document['insumos']))
