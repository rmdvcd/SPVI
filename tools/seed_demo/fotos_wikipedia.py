"""Fotos reales para el sembrador: imagen principal del artículo de Wikipedia de cada concepto.

Usa `pageimages` (una imagen por artículo, la de la ficha) y, si el artículo no tiene, cae a
una búsqueda en Wikimedia Commons. Recorta en cuadrado, 512x512, JPEG. Deja los créditos.
"""
import json
import os
import time
import urllib.parse
import urllib.request
from io import BytesIO

from PIL import Image, ImageOps

UA = "ShalevSeed/1.0 (datos de demostracion; contacto local)"
WIKI = "https://en.wikipedia.org/w/api.php"
COMMONS = "https://commons.wikimedia.org/w/api.php"
AQUI = os.path.dirname(__file__)
SALIDA = os.path.join(AQUI, "seed_fotos")
CREDITOS = os.path.join(AQUI, "CREDITOS.md")

MAPA = [
    ("prod01", "Coffee"), ("prod02", "White rice"), ("prod03", "Black turtle bean"),
    ("prod04", "Sugar"), ("prod05", "Sunflower oil"), ("prod06", "Wheat flour"),
    ("prod07", "Powdered milk"), ("prod08", "Condensed milk"), ("prod09", "Yogurt"),
    ("prod10", "Gouda cheese"), ("prod11", "Ham"), ("prod12", "Sausage"),
    ("prod13", "Canned tuna"), ("prod14", "Sardine"), ("prod15", "Spaghetti"),
    ("prod16", "Tomato sauce"), ("prod17", "Mayonnaise"), ("prod18", "Mustard (condiment)"),
    ("prod19", "Cola"), ("prod20", "Bottled water"), ("prod21", "Juice"),
    ("prod22", "Beer"), ("prod23", "Rum"), ("prod24", "Red wine"),
    ("prod25", "Sliced bread"), ("prod26", "Biscuit"), ("prod27", "Chocolate bar"),
    ("prod28", "Potato chip"), ("prod29", "Peanut"), ("prod30", "Banana"),
    ("prod31", "Tomato"), ("prod32", "Onion"), ("prod33", "Laundry detergent"),
    ("prod34", "Soap"), ("prod35", "Toothpaste"), ("prod36", "Toilet paper"),
    ("prod37", "Café con leche"), ("prod38", "Cappuccino"), ("prod39", "Smoothie"),
    ("prod40", "Ham sandwich"),
    ("serv01", "Mobile phone"), ("serv02", "Printer (computing)"), ("serv03", "Color printing"),
    ("serv04", "Photocopier"), ("serv05", "Multifunction printer"), ("serv06", "Lamination"),
    ("serv07", "Coil binding"), ("serv08", "Photographic printing"), ("serv09", "Image scanner"),
    ("serv10", "Haircut"), ("serv11", "Beard"), ("serv12", "Manicure"),
    ("serv13", "Hair coloring"), ("serv14", "Nail polish"), ("serv15", "Laundry"),
    ("serv16", "Ironing"), ("serv17", "Delivery (commerce)"), ("serv18", "Battery charger"),
    ("serv19", "Mobile app"), ("serv20", "Gift wrapping"),
]

# Conceptos que el artículo no ilustra bien: se busca directamente en Commons.
COMONS = {
    "prod21": "glass of mango juice",
    "serv03": "colour printing press",
    "serv08": "photo prints",
    "serv17": "delivery van",
    "serv19": "smartphone home screen apps",
    "serv20": "gift wrapped present",
}

_ULTIMA = [0.0]


def get(url, params, intentos=5):
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
    return {}


def imagen_de_articulo(titulo):
    d = get(WIKI, {
        "action": "query", "titles": titulo, "prop": "pageimages|info",
        "piprop": "thumbnail|name", "pithumbsize": 900, "inprop": "url", "redirects": 1, "format": "json",
    })
    paginas = (d.get("query") or {}).get("pages") or {}
    for p in paginas.values():
        thumb = (p.get("thumbnail") or {}).get("source")
        if thumb:
            return {"url": thumb, "titulo": titulo, "descripcion": p.get("fullurl", ""), "licencia": "Wikipedia"}
    return None


def imagen_de_commons(consulta):
    d = get(COMMONS, {
        "action": "query", "generator": "search", "gsrsearch": f"filetype:bitmap {consulta}",
        "gsrnamespace": 6, "gsrlimit": 5, "prop": "imageinfo",
        "iiprop": "url|extmetadata", "iiurlwidth": 900, "format": "json",
    })
    paginas = (d.get("query") or {}).get("pages") or {}
    for p in paginas.values():
        info = (p.get("imageinfo") or [{}])[0]
        if info.get("thumburl"):
            meta = info.get("extmetadata") or {}
            return {
                "url": info["thumburl"], "titulo": p.get("title", "")[5:],
                "descripcion": info.get("descriptionurl", ""),
                "licencia": (meta.get("LicenseShortName") or {}).get("value", "?"),
            }
    return None


def cuadrada(datos, destino):
    img = Image.open(BytesIO(datos))
    img = ImageOps.exif_transpose(img).convert("RGB")
    lado = min(img.size)
    x, y = (img.width - lado) // 2, (img.height - lado) // 2
    img.crop((x, y, x + lado, y + lado)).resize((512, 512), Image.LANCZOS).save(
        destino, "JPEG", quality=88, optimize=True
    )


CACHE = os.path.join(AQUI, "seed_fotos", "creditos.json")


def titulos_en_lote(titulos):
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
        f.write("# Créditos de las fotos de demostración\n\n")
        f.write("Imágenes de Wikipedia/Wikimedia Commons para los datos de demostración del sembrador.\n")
        f.write("Antes de usar la app en material promocional público, revisa la licencia de cada obra.\n\n")
        f.write("| Archivo | Obra / artículo | Licencia | Enlace |\n|---|---|---|---|\n")
        for nombre, c in filas:
            f.write(f"| `{nombre}.jpg` | {c['titulo']} | {c['licencia']} | {c['descripcion']} |\n")
    print(f"\n{len(filas)}/60 con imagen. Fallos: {', '.join(fallos) if fallos else 'ninguno'}")


if __name__ == "__main__":
    main()
