p = "tools/seed_demo/fotos_wikipedia.py"
s = open(p, encoding="utf-8").read()

s = s.replace('''def get(url, params):
    desde = time.time() - _ULTIMA[0]
    if desde < 1.2:
        time.sleep(1.2 - desde)
    req = urllib.request.Request(url + "?" + urllib.parse.urlencode(params), headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=40) as r:
        _ULTIMA[0] = time.time()
        return json.load(r)''', '''def get(url, params, intentos=5):
    espera = 45
    for intento in range(intentos):
        desde = time.time() - _ULTIMA[0]
        if desde < 3.0:
            time.sleep(3.0 - desde)
        req = urllib.request.Request(url + "?" + urllib.parse.urlencode(params), headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=40) as r:
                _ULTIMA[0] = time.time()
                return json.load(r)
        except urllib.error.HTTPError as e:
            _ULTIMA[0] = time.time()
            if e.code in (429, 500, 502, 503) and intento < intentos - 1:
                print(f"  ... {e.code}, espero {espera}s")
                time.sleep(espera)
                espera = min(espera * 2, 240)
                continue
            raise
    return {}''')

s = s.replace('''    for i, (nombre, concepto) in enumerate(MAPA, start=1):
        destino = os.path.join(SALIDA, f"{nombre}.jpg")''', '''    for i, (nombre, concepto) in enumerate(MAPA, start=1):
        destino = os.path.join(SALIDA, f"{nombre}.jpg")
        if os.path.exists(destino) and os.path.getsize(destino) > 8000:
            print(f"[{i}/60] {nombre}: ya estaba")
            continue''')

s = s.replace('''        for intento in (imagen_de_articulo(concepto), imagen_de_commons(COMONS.get(nombre, concepto))):''','''        candidatos = []
        try:
            candidatos.append(imagen_de_articulo(concepto))
        except Exception as e:  # noqa: BLE001
            print(f"  ! artículo {concepto}: {e}")
        try:
            candidatos.append(imagen_de_commons(COMONS.get(nombre, concepto)))
        except Exception as e:  # noqa: BLE001
            print(f"  ! commons {concepto}: {e}")
        for intento in candidatos:''')

s = s.replace('''    with open(CREDITOS, "w", encoding="utf-8") as f:''','''    filas = filas or []
    with open(CREDITOS, "w", encoding="utf-8") as f:''')

open(p, "w", encoding="utf-8").write(s)
print("parcheado")
