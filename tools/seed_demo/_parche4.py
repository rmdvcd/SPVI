p = "tools/seed_demo/fotos_wikipedia.py"
s = open(p, encoding="utf-8").read()

# Reemplaza el bucle principal por: 1 consulta por lote + descargas
inicio = s.index("def main():")
nuevo = '''def titulos_en_lote(titulos):
    """pageimages de hasta 50 artículos en UNA consulta. Devuelve {título pedido: url}."""
    mapa = {}
    for i in range(0, len(titulos), 40):
        lote = titulos[i:i + 40]
        d = get(WIKI, {
            "action": "query", "titles": "|".join(lote), "prop": "pageimages",
            "piprop": "thumbnail", "pithumbsize": 900, "redirects": 1, "format": "json",
        })
        q = d.get("query") or {}
        equivalencias = {}
        for clave in ("normalized", "redirects"):
            for par in q.get(clave) or []:
                equivalencias[par["from"]] = par["to"]
        paginas = {p["title"]: p for p in (q.get("pages") or {}).values()}
        for titulo in lote:
            actual, visto = titulo, set()
            while actual in equivalencias and actual not in visto:
                visto.add(actual)
                actual = equivalencias[actual]
            pagina = paginas.get(actual)
            if pagina and (pagina.get("thumbnail") or {}).get("source"):
                mapa[titulo] = pagina["thumbnail"]["source"]
    return mapa


def main():
    os.makedirs(SALIDA, exist_ok=True)
    filas, fallos = [], []
    previos = {}
    if os.path.exists(CACHE):
        previos = json.load(open(CACHE, encoding="utf-8"))
    filas = list(previos.items()) if previos else []

    pendientes = [(n, c) for n, c in MAPA if not (os.path.exists(os.path.join(SALIDA, n + ".jpg")) and os.path.getsize(os.path.join(SALIDA, n + ".jpg")) > 8000)]
    print(f"{len(pendientes)} pendientes de {len(MAPA)}")
    try:
        urls = titulos_en_lote([c for _, c in pendientes])
    except Exception as e:  # noqa: BLE001
        print("lote fallido:", e)
        urls = {}
    print(f"{len(urls)} con imagen en Wikipedia")

    for i, (nombre, concepto) in enumerate(pendientes, start=1):
        destino = os.path.join(SALIDA, nombre + ".jpg")
        candidatos = []
        if concepto in urls:
            candidatos.append({"url": urls[concepto], "titulo": concepto, "descripcion": f"https://en.wikipedia.org/wiki/{concepto.replace(' ', '_')}", "licencia": "Wikipedia"})
        try:
            alt = imagen_de_commons(COMONS.get(nombre, concepto))
            if alt:
                candidatos.append(alt)
        except Exception as e:  # noqa: BLE001
            print(f"  ! commons {concepto}: {e}")
        elegido = None
        for c in candidatos:
            try:
                req = urllib.request.Request(c["url"], headers={"User-Agent": UA})
                with urllib.request.urlopen(req, timeout=60) as r:
                    cuadrada(r.read(), destino)
                elegido = c
                break
            except Exception as e:  # noqa: BLE001
                print(f"  ! {nombre}: {e}")
        if elegido:
            filas.append((nombre, elegido))
            print(f"[{i}/{len(pendientes)}] {nombre}: {elegido['titulo'][:50]}")
            time.sleep(0.5)
        else:
            fallos.append(nombre)
            print(f"[{i}/{len(pendientes)}] {nombre}: SIN IMAGEN")

    with open(CACHE, "w", encoding="utf-8") as fc:
        json.dump(dict(filas), fc, ensure_ascii=False, indent=1)
    filas = sorted(dict(filas).items(), key=lambda x: x[0])
    with open(CREDITOS, "w", encoding="utf-8") as f:
        f.write("# Créditos de las fotos de demostración\\n\\n")
        f.write("Imágenes de Wikipedia/Wikimedia Commons para los datos de demostración del sembrador.\\n")
        f.write("Antes de usar la app en material promocional público, revisa la licencia de cada obra.\\n\\n")
        f.write("| Archivo | Obra / artículo | Licencia | Enlace |\\n|---|---|---|---|\\n")
        for nombre, c in filas:
            f.write(f"| `{nombre}.jpg` | {c['titulo']} | {c['licencia']} | {c['descripcion']} |\\n")
    print(f"\\n{len(filas)}/60 con imagen. Fallos: {', '.join(fallos) if fallos else 'ninguno'}")


if __name__ == "__main__":
    main()
'''
s = s[:inicio] + nuevo
open(p, "w", encoding="utf-8").write(s)
print("reescrito")
