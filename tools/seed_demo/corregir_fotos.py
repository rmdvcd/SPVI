"""Reintenta las fotos cuyo resultado no correspondía, con consultas puntuales y filtro por título."""
import json
import os
import time
import urllib.parse
import urllib.request
from io import BytesIO

from PIL import Image, ImageOps

UA = "ShalevSeed/1.0 (datos de demostracion; contacto local)"
COMMONS = "https://commons.wikimedia.org/w/api.php"
AQUI = os.path.dirname(__file__)
SALIDA = os.path.join(AQUI, "seed_fotos")

# archivo -> [(consulta, palabras que debe contener el título), ...]
OBJETIVOS = {
    "prod14": [("sardine can", ("sardine",)), ("canned sardines", ("sardine",)), ("sardines", ("sardine",))],
    "prod29": [("peanuts bowl", ("peanut",)), ("roasted peanuts", ("peanut",))],
    "serv01": [("smartphone", ("smartphone", "phone")), ("mobile phone", ("phone", "mobile"))],
    "serv03": [("color laser printer", ("printer",)), ("colour printer", ("printer",))],
    "serv06": [("laminating machine", ("laminat",)), ("laminated document", ("laminat", "laminated"))],
    "serv08": [("photo prints", ("print",)), ("photographic paper", ("photo", "print"))],
    "serv10": [("haircut man", ("haircut", "hair", "barber")), ("barber shop", ("barber", "hair"))],
    "serv12": [("manicure", ("manicure", "nail")), ("manicured nails", ("nail", "manicure"))],
    "serv18": [("smartphone charger", ("charger", "charging")), ("battery charger phone", ("charger",))],
}

_ULTIMA = [0.0]


def api(params):
    desde = time.time() - _ULTIMA[0]
    if desde < 2.0:
        time.sleep(2.0 - desde)
    req = urllib.request.Request(COMMONS + "?" + urllib.parse.urlencode(params), headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=40) as r:
        _ULTIMA[0] = time.time()
        return json.load(r)


def candidatos(consulta):
    d = api({
        "action": "query", "generator": "search", "gsrsearch": f"filetype:bitmap {consulta}",
        "gsrnamespace": 6, "gsrlimit": 10, "prop": "imageinfo",
        "iiprop": "url|extmetadata", "iiurlwidth": 900, "format": "json",
    })
    salida = []
    for p in ((d.get("query") or {}).get("pages") or {}).values():
        info = (p.get("imageinfo") or [{}])[0]
        if not info.get("thumburl"):
            continue
        meta = info.get("extmetadata") or {}
        salida.append({
            "titulo": p.get("title", "")[5:],
            "url": info["thumburl"],
            "descripcion": info.get("descriptionurl", ""),
            "licencia": (meta.get("LicenseShortName") or {}).get("value", "?"),
        })
    return salida


def cuadrada(datos, destino):
    img = Image.open(BytesIO(datos))
    img = ImageOps.exif_transpose(img).convert("RGB")
    lado = min(img.size)
    x, y = (img.width - lado) // 2, (img.height - lado) // 2
    img.crop((x, y, x + lado, y + lado)).resize((512, 512), Image.LANCZOS).save(destino, "JPEG", quality=88, optimize=True)


def main():
    cache_ruta = os.path.join(SALIDA, "creditos.json")
    cache = json.load(open(cache_ruta, encoding="utf-8")) if os.path.exists(cache_ruta) else {}
    for nombre, intentos in OBJETIVOS.items():
        hecho = False
        for consulta, claves in intentos:
            if hecho:
                break
            try:
                lista = candidatos(consulta)
            except Exception as e:  # noqa: BLE001
                print(f"  ! {nombre} {consulta}: {e}")
                continue
            buenos = [c for c in lista if any(k in c["titulo"].lower() for k in claves)]
            for c in buenos[:2]:
                try:
                    req = urllib.request.Request(c["url"], headers={"User-Agent": UA})
                    with urllib.request.urlopen(req, timeout=60) as r:
                        cuadrada(r.read(), os.path.join(SALIDA, f"{nombre}.jpg"))
                    cache[nombre] = c
                    print(f"{nombre}: {c['titulo'][:60]} ({c['licencia']})")
                    hecho = True
                    break
                except Exception as e:  # noqa: BLE001
                    print(f"  ! descarga {nombre}: {e}")
        if not hecho:
            print(f"{nombre}: SIN SUSTITUTO")
    json.dump(cache, open(cache_ruta, "w", encoding="utf-8"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()

