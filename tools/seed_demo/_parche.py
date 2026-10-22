import re, io
p = "tools/seed_demo/descargar_fotos.py"
s = open(p, encoding="utf-8").read()

# 1) Esperas y reintentos ante 429
s = s.replace('''def api(params):
    url = API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=40) as r:
        return json.load(r)''', '''ULTIMA_LLAMADA = [0.0]


def api(params, intentos=4):
    espera = 3.0
    for intento in range(intentos):
        desde = time.time() - ULTIMA_LLAMADA[0]
        if desde < espera:
            time.sleep(espera - desde)
        url = API + "?" + urllib.parse.urlencode(params)
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=40) as r:
                ULTIMA_LLAMADA[0] = time.time()
                return json.load(r)
        except urllib.error.HTTPError as e:
            ULTIMA_LLAMADA[0] = time.time()
            if e.code == 429 and intento < intentos - 1:
                pausa = 30 * (intento + 1)
                print(f"  ... 429, espero {pausa}s")
                time.sleep(pausa)
                espera = 6.0
                continue
            raise
    return {}''')

# 2) Reanudar: no volver a bajar lo que ya está
s = s.replace('''    for i, (nombre, termino, alterno) in enumerate(total, start=1):
        candidatos = buscar(termino) or buscar(alterno)''', '''    for i, (nombre, termino, alterno) in enumerate(total, start=1):
        destino = os.path.join(SALIDA, f"{nombre}.jpg")
        if os.path.exists(destino) and os.path.getsize(destino) > 8000:
            print(f"[{i}/{len(total)}] {nombre}: ya estaba")
            continue
        candidatos = buscar(termino) or buscar(alterno)''')

# 3) Filtro de relevancia: alguna palabra de la consulta en el título
s = s.replace('''    salida.sort(key=lambda x: 0 if any(l in x["licencia"].lower() for l in LIBRES) else 1)
    return salida''', '''    claves = [w for w in termino.lower().split() if len(w) > 3]
    relevantes = [c for c in salida if any(k in c["titulo"].lower() for k in claves)]
    if relevantes:
        salida = relevantes
    salida.sort(key=lambda x: 0 if any(l in x["licencia"].lower() for l in LIBRES) else 1)
    return salida''')

# 4) Consultas más precisas para servicios
for viejo, nuevo in [
    ('("serv01", "mobile phone top up", "smartphone voucher")', '("serv01", "mobile phone", "smartphone hand")'),
    ('("serv02", "black and white printing", "printer printing paper")', '("serv02", "laser printer", "office printer")'),
    ('("serv03", "color printing", "color printer")', '("serv03", "inkjet printer", "color printer")'),
    ('("serv05", "double sided copy", "photocopier paper")', '("serv05", "office copier", "copier machine")'),
    ('("serv06", "laminating card", "laminator machine")', '("serv06", "laminating machine", "laminator")'),
    ('("serv08", "photo printing", "printed photographs")', '("serv08", "photographic print", "photo album")'),
    ('("serv09", "document scanner", "flatbed scanner")', '("serv09", "image scanner", "flatbed scanner")'),
    ('("serv11", "beard trim", "bearded man barber")', '("serv11", "beard", "bearded man")'),
    ('("serv14", "nail repair", "nail polish hands")', '("serv14", "nail polish", "fingernails")'),
    ('("serv15", "laundry service", "laundry washing machine")', '("serv15", "laundry room", "washing machine")'),
    ('("serv16", "ironing clothes", "electric iron shirt")', '("serv16", "ironing", "clothes iron")'),
    ('("serv17", "delivery courier", "courier package delivery")', '("serv17", "delivery van", "courier")'),
    ('("serv18", "phone charging", "smartphone charger")', '("serv18", "battery charger", "phone charger")'),
    ('("serv19", "phone app installation", "smartphone apps screen")', '("serv19", "smartphone apps", "mobile app")'),
    ('("serv20", "gift wrapping", "gift box ribbon")', '("serv20", "gift wrapping", "gift box")'),
]:
    s = s.replace(viejo, nuevo)

open(p, "w", encoding="utf-8").write(s)
print("parcheado")
