"""Descarga fotos reales de Wikimedia Commons para los artículos y servicios del sembrador.

- Prefiere dominio público / CC0; si no hay, usa la primera imagen de la búsqueda (CC BY / CC BY-SA).
- Recorta al centro en cuadrado, redimensiona a 512x512 y guarda JPEG de calidad 88 con el nombre
  que espera el sembrador (`prodNN.jpg`, `servNN.jpg`).
- Deja el crédito de cada imagen en `CREDITOS.md`.
"""
import json
import os
import time
import urllib.parse
import urllib.request

from PIL import Image, ImageOps

UA = "ShalevSeed/1.0 (datos de demostracion; contacto local)"
API = "https://commons.wikimedia.org/w/api.php"
AQUI = os.path.dirname(__file__)
SALIDA = os.path.join(AQUI, "seed_fotos")
CREDITOS = os.path.join(AQUI, "CREDITOS.md")
LIBRES = ("cc0", "public domain", "pd-", "pd ")

PRODUCTOS = [
    ("prod01", "ground coffee", "coffee powder bowl"),
    ("prod02", "white rice", "rice grains"),
    ("prod03", "black beans", "black turtle beans"),
    ("prod04", "white sugar", "sugar crystals"),
    ("prod05", "sunflower oil bottle", "cooking oil bottle"),
    ("prod06", "wheat flour", "flour bag"),
    ("prod07", "powdered milk", "milk powder"),
    ("prod08", "condensed milk can", "condensed milk"),
    ("prod09", "yogurt cup", "plain yogurt"),
    ("prod10", "gouda cheese", "cheese wheel"),
    ("prod11", "ham slices", "sliced ham"),
    ("prod12", "sausages", "smoked sausage"),
    ("prod13", "canned tuna", "tuna can"),
    ("prod14", "canned sardines", "sardine can"),
    ("prod15", "spaghetti pasta", "dry pasta"),
    ("prod16", "tomato sauce jar", "tomato sauce"),
    ("prod17", "mayonnaise", "mayonnaise jar"),
    ("prod18", "mustard", "mustard jar"),
    ("prod19", "cola bottle", "soft drink bottle"),
    ("prod20", "mineral water bottle", "water bottle"),
    ("prod21", "mango juice", "mango drink"),
    ("prod22", "beer bottle", "lager beer"),
    ("prod23", "rum bottle", "rum"),
    ("prod24", "red wine bottle", "red wine"),
    ("prod25", "sliced bread", "white bread loaf"),
    ("prod26", "biscuits", "marie biscuits"),
    ("prod27", "chocolate bar", "chocolate tablet"),
    ("prod28", "potato chips", "crisps bag"),
    ("prod29", "peanuts", "roasted peanuts"),
    ("prod30", "bananas", "banana bunch"),
    ("prod31", "tomatoes", "ripe tomatoes"),
    ("prod32", "onions", "onion bulbs"),
    ("prod33", "laundry detergent", "washing powder"),
    ("prod34", "bar soap", "soap bar"),
    ("prod35", "toothpaste", "toothpaste tube"),
    ("prod36", "toilet paper", "toilet paper rolls"),
    ("prod37", "cafe con leche", "coffee with milk"),
    ("prod38", "cappuccino", "cappuccino cup"),
    ("prod39", "mango smoothie", "mango shake"),
    ("prod40", "ham sandwich", "sandwich"),
]

SERVICIOS = [
    ("serv01", "mobile phone", "smartphone hand"),
    ("serv02", "laser printer", "office printer"),
    ("serv03", "inkjet printer", "color printer"),
    ("serv04", "photocopy", "photocopier"),
    ("serv05", "office copier", "copier machine"),
    ("serv06", "laminating machine", "laminator"),
    ("serv07", "spiral binding", "coil binding"),
    ("serv08", "photographic print", "photo album"),
    ("serv09", "image scanner", "flatbed scanner"),
    ("serv10", "haircut", "barber haircut"),
    ("serv11", "beard", "bearded man"),
    ("serv12", "manicure", "manicure nails"),
    ("serv13", "hair coloring", "hair dye salon"),
    ("serv14", "nail polish", "fingernails"),
    ("serv15", "laundry room", "washing machine"),
    ("serv16", "ironing", "clothes iron"),
    ("serv17", "delivery van", "courier"),
    ("serv18", "battery charger", "phone charger"),
    ("serv19", "smartphone apps", "mobile app"),
    ("serv20", "gift wrapping", "gift box"),
]


ULTIMA_LLAMADA = [0.0]


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
    return {}


def buscar(termino):
    try:
        d = api({
            "action": "query", "generator": "search",
            "gsrsearch": f"filetype:bitmap {termino}", "gsrnamespace": 6, "gsrlimit": 8,
            "prop": "imageinfo", "iiprop": "url|extmetadata", "iiurlwidth": 800, "format": "json",
        })
    except Exception as e:  # noqa: BLE001
        print(f"  ! búsqueda fallida {termino}: {e}")
        return []
    paginas = (d.get("query") or {}).get("pages") or {}
    salida = []
    for p in paginas.values():
        info = (p.get("imageinfo") or [{}])[0]
        if not info.get("thumburl"):
            continue
        meta = info.get("extmetadata") or {}
        lic = (meta.get("LicenseShortName") or {}).get("value", "?")
        autor = (meta.get("Artist") or {}).get("value", "?")
        salida.append({
            "titulo": p.get("title", "?"),
            "url": info["thumburl"],
            "descripcion": info.get("descriptionurl", ""),
            "licencia": lic,
            "autor": autor,
        })
    claves = [w for w in termino.lower().split() if len(w) > 3]
    relevantes = [c for c in salida if any(k in c["titulo"].lower() for k in claves)]
    if relevantes:
        salida = relevantes
    salida.sort(key=lambda x: 0 if any(l in x["licencia"].lower() for l in LIBRES) else 1)
    return salida


def descargar(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def cuadrada(datos, destino):
    from io import BytesIO
    img = Image.open(BytesIO(datos))
    img = ImageOps.exif_transpose(img).convert("RGB")
    lado = min(img.size)
    izq = (img.width - lado) // 2
    arriba = (img.height - lado) // 2
    img = img.crop((izq, arriba, izq + lado, arriba + lado)).resize((512, 512), Image.LANCZOS)
    img.save(destino, "JPEG", quality=88, optimize=True)


def main():
    os.makedirs(SALIDA, exist_ok=True)
    filas = []
    total = PRODUCTOS + SERVICIOS
    for i, (nombre, termino, alterno) in enumerate(total, start=1):
        destino = os.path.join(SALIDA, f"{nombre}.jpg")
        if os.path.exists(destino) and os.path.getsize(destino) > 8000:
            print(f"[{i}/{len(total)}] {nombre}: ya estaba")
            continue
        candidatos = buscar(termino) or buscar(alterno)
        if not candidatos:
            print(f"[{i}/{len(total)}] {nombre}: SIN RESULTADOS ({termino})")
            continue
        elegido = None
        for c in candidatos:
            try:
                datos = descargar(c["url"])
                cuadrada(datos, os.path.join(SALIDA, f"{nombre}.jpg"))
                elegido = c
                break
            except Exception as e:  # noqa: BLE001
                print(f"  ! {nombre} no se pudo bajar {c['url'][:60]}: {e}")
        if not elegido:
            print(f"[{i}/{len(total)}] {nombre}: fallo de descarga")
            continue
        filas.append((nombre, elegido))
        print(f"[{i}/{len(total)}] {nombre}: {elegido['titulo'][5:60]} ({elegido['licencia']})")
        time.sleep(0.4)

    with open(CREDITOS, "w", encoding="utf-8") as f:
        f.write("# Créditos de las fotos de demostración\n\n")
        f.write("Imágenes descargadas de Wikimedia Commons (licencias libres). Se usan solo en los datos\n")
        f.write("de demostración del sembrador de depuración.\n\n")
        f.write("| Archivo | Obra | Licencia | Autor | Enlace |\n|---|---|---|---|---|\n")
        for nombre, c in filas:
            autor = c["autor"].replace("|", "/").replace("\n", " ")
            if len(autor) > 80:
                autor = autor[:77] + "…"
            f.write(f"| `{nombre}.jpg` | {c['titulo'][5:]} | {c['licencia']} | {autor} | [Commons]({c['descripcion']}) |\n")
    print(f"\n{len(filas)} de {len(total)} fotos. Créditos en {CREDITOS}")


if __name__ == "__main__":
    main()
