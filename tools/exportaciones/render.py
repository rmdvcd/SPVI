#!/usr/bin/env python3
"""Dibuja los PDF y PNG de SPVI a partir de tablas.json (salida de Muestras.kt) con las MISMAS medidas, colores y
reglas que el código Android:
  * PDF  -> data/src/main/kotlin/cu/spvi/data/export/PdfWriter.kt   (A4 595x842 pt, horizontal con >5 columnas; DisenoPdf.kt)
  * PNG  -> app/src/main/kotlin/cu/spvi/app/common/ImagenTabla.kt    (1080 px, 25 filas por imagen)
  * PNG  -> app/src/main/kotlin/cu/spvi/app/inventario/Tarjetas.kt   (1080 px, 1 tarjeta 4:5 o rejilla 2x3)
Android usa Roboto: se descarga a ~/.cache/fonts si falta. Uso: python3 render.py <dir-de-Muestras>
"""
import json, os, sys, urllib.request
import pymupdf
from PIL import Image, ImageDraw, ImageFont

D = sys.argv[1]
FD = os.path.expanduser("~/.cache/fonts")
os.makedirs(FD, exist_ok=True)
for w in ("Regular", "Bold"):
    f = f"{FD}/Roboto-{w}.ttf"
    if not os.path.exists(f):
        urllib.request.urlretrieve(f"https://cdn.jsdelivr.net/gh/googlefonts/roboto@main/src/hinted/Roboto-{w}.ttf", f)
REG, BOLD = f"{FD}/Roboto-Regular.ttf", f"{FD}/Roboto-Bold.ttf"
# 0.26.0 (§5): Roboto 600 = Typeface.create(DEFAULT, 600) del PdfWriter (Android 9+).
SEMI = f"{FD}/Roboto-SemiBold.ttf"
if not os.path.exists(SEMI):
    urllib.request.urlretrieve("https://cdn.jsdelivr.net/fontsource/fonts/roboto@latest/latin-600-normal.ttf", SEMI)
datos = json.load(open(f"{D}/tablas.json", encoding="utf-8"))


def hexc(h):
    h = h.lstrip("#"); return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))


# ------------------------------------------------------------------------------------------------ PDF (PdfWriter)
A4_CORTO, A4_LARGO, M, FILA = 595, 842, 36.0, 16.0
FR, FB, FS = pymupdf.Font(fontfile=REG), pymupdf.Font(fontfile=BOLD), pymupdf.Font(fontfile=SEMI)


def ellipsize(s, font, size, ancho):
    if font.text_length(s, size) <= ancho: return s
    while s and font.text_length(s + "…", size) > ancho: s = s[:-1]
    return s + "…" if s else ""


# 0.25.1 (E2): réplica de data/src/main/kotlin/cu/spvi/data/export/DisenoPdf.kt
import re
MAX_COLUMNAS_VERTICAL, RELLENO, MIN_LIBRE, MAX_NATURAL = 5, 8.0, 48.0, 260.0
RE_NUMERO = re.compile(r"^[-+−]?[\d.,]+(\s?(CUP|%|[^\W\d_]{1,6}))?$")
RE_FECHA = re.compile(r"^\d{2}/\d{2}/\d{4}( \d{2}:\d{2})?$")
RE_CODIGO = re.compile(r"^[+•\dA-Z][\dA-Z•\-]{2,24}$")


def _valores(t, c): return [f[c].strip() for f in t["filas"][:200] if c < len(f) and f[c].strip()]
def horizontal(t): return len(t["columnas"]) > MAX_COLUMNAS_VERTICAL
def derecha(t, c): v = _valores(t, c); return bool(v) and all(RE_NUMERO.match(x) for x in v)
def destacada(t, c):
    if len(t["columnas"]) == 2 and c == 1 and t["columnas"][1] == "Valor": return True
    v = _valores(t, c); return bool(v) and all((RE_NUMERO.match(x) and not re.match(r"^\+?\d{8,}$", x)) or RE_FECHA.match(x) for x in v)
def compacta(t, c): v = _valores(t, c); return bool(v) and all(RE_NUMERO.match(x) or RE_FECHA.match(x) or RE_CODIGO.match(x) for x in v)


def anchos_pdf(t, util, medir):
    n = len(t["columnas"])
    if n == 0: return []
    nat = [min(max(medir(t["columnas"][c], True), max([medir(x, destacada(t, c)) for x in _valores(t, c)] or [0])) + RELLENO, MAX_NATURAL) for c in range(n)]
    fija = [compacta(t, c) for c in range(n)]
    total = sum(nat)
    if total <= util:
        libres = [c for c in range(n) if not fija[c]] or list(range(n))
        base = max(sum(nat[c] for c in libres), 1); extra = util - total
        return [nat[c] + (extra * nat[c] / base if c in libres else 0) for c in range(n)]
    usado = sum(nat[c] for c in range(n) if fija[c]); libres = [c for c in range(n) if not fija[c]]; resto = util - usado
    if libres and resto >= len(libres) * MIN_LIBRE:
        peso = max(sum(nat[c] for c in libres), 1); rep = resto - len(libres) * MIN_LIBRE
        a = [nat[c] if fija[c] else MIN_LIBRE + rep * nat[c] / peso for c in range(n)]
        sobra = 0.0
        for c in libres:
            if a[c] > nat[c]: sobra += a[c] - nat[c]; a[c] = nat[c]
        faltan = [c for c in libres if a[c] < nat[c]]
        if sobra > 0 and faltan:
            for c in faltan: a[c] += sobra / len(faltan)
        elif sobra > 0:
            for c in libres: a[c] += sobra / len(libres)
        return a
    return [util * nat[c] / total for c in range(n)]


def pdf(tablas, destino):
    doc = pymupdf.open()
    st = {"n": 0, "pag": None, "y": 0.0, "W": A4_CORTO, "H": A4_LARGO}

    def texto(x, y, s, font, size, color):
        nom, fic = {id(FR): ("R", REG), id(FS): ("S", SEMI)}.get(id(font), ("B", BOLD))
        st["pag"].insert_text((x, y), s, fontname=nom, fontfile=fic, fontsize=size, color=color)

    def nueva():
        cerrar_pag(); st["n"] += 1
        st["pag"] = doc.new_page(width=st["W"], height=st["H"]); st["y"] = M

    def cerrar_pag():
        if st["pag"] is not None:
            texto(M, st["H"] - M / 2, f"SPVI · página {st['n']}", FR, 8, hexc("5F6368"))
        st["pag"] = None

    def celdas(vals, anchos, der, font, color):
        x = M
        for i, (v, w) in enumerate(zip(vals, anchos)):
            f = font[i] if isinstance(font, list) else font
            s = ellipsize(v, f, 9, max(w - 6, 0))
            dx = w - 3 - f.text_length(s, 9) if der[i] else 3
            texto(x + dx, st["y"] + 11.5, s, f, 9, color); x += w
        st["y"] += FILA

    for t in tablas:
        w = A4_LARGO if horizontal(t) else A4_CORTO
        cambia = w != st["W"]
        if cambia: st["W"], st["H"] = w, (A4_CORTO if w == A4_LARGO else A4_LARGO)
        W, H = st["W"], st["H"]
        if st["pag"] is None or cambia or st["y"] > H - M - FILA * 4: nueva()
        else: st["y"] += FILA
        texto(M, st["y"] + 14, t["titulo"], FB, 15, (0, 0, 0)); st["y"] += 26
        anchos = anchos_pdf(t, W - 2 * M, lambda s, b: (FB if b else FR).text_length(s, 9))
        pinceles = [FS if destacada(t, c) else FR for c in range(len(t["columnas"]))]
        der = [derecha(t, c) for c in range(len(t["columnas"]))]

        def cab():
            st["pag"].draw_rect(pymupdf.Rect(M, st["y"], W - M, st["y"] + FILA), color=None, fill=hexc("E8EAED"))
            celdas(t["columnas"], anchos, der, FB, (0, 0, 0))
        cab()
        for f in t["filas"]:
            if st["y"] + FILA > H - M - FILA: nueva(); cab()
            celdas(f, anchos, der, pinceles, hexc("202124"))
            st["pag"].draw_line((M, st["y"]), (W - M, st["y"]), color=hexc("BDC1C6"), width=0.5)
        for p in t["pie"]:
            if st["y"] + FILA > H - M - FILA: nueva()
            texto(M, st["y"] + 12, p, FB, 9, (0, 0, 0)); st["y"] += FILA
    if st["pag"] is None: nueva()
    cerrar_pag()
    doc.save(destino)


# ------------------------------------------------------------------------------------------------ PNG (ImagenTabla)
def pil(path, size): return ImageFont.truetype(path, int(round(size)))


def recortar(s, f, ancho):
    if f.getlength(s) <= ancho: return s
    while s and f.getlength(s + "…") > ancho: s = s[:-1]
    return s + "…"


def imagen_tabla(t, base):
    ANCHO, MARGEN, TITULO, CAB, FIL, PIE, POR = 1080, 48, 120, 72, 64, 64, 25
    filas = t["filas"]; paginas = [filas[i:i + POR] for i in range(0, len(filas), POR)] or [[]]
    util = ANCHO - 2 * MARGEN
    pesos = [min(max(max(len(x) for x in [t["columnas"][i]] + [f[i] for f in filas]), 6), 28) for i in range(len(t["columnas"]))]
    b = [p * util // sum(pesos) for p in pesos]; b[-1] = util - sum(b[:-1])
    der = [bool(filas) and all((v == "" or v.endswith(" CUP") or all(ch.isdigit() or ch in ",.-" for ch in v)) for v in (f[i] for f in filas)) for i in range(len(t["columnas"]))]
    salida = []
    for k, pf in enumerate(paginas):
        alto = MARGEN * 2 + TITULO + CAB + len(pf) * FIL + PIE
        im = Image.new("RGB", (ANCHO, alto), "white"); d = ImageDraw.Draw(im)
        ft = pil(BOLD, 52); tit = t["titulo"] if len(paginas) <= 1 else f"{t['titulo']} · {k + 1}/{len(paginas)}"
        d.text((MARGEN, MARGEN + 64), recortar(tit, ft, ANCHO - 2 * MARGEN), font=ft, fill="#1F5F70", anchor="ls")
        y = MARGEN + TITULO
        d.rectangle([MARGEN, y, ANCHO - MARGEN, y + CAB], fill="#E3EEF1")

        def fila(vals, y, alto, f):
            x = MARGEN; asc, desc = f.getmetrics(); base = y + alto / 2 + (asc - desc) / 2
            for i, v in enumerate(vals):
                s = recortar(v, f, b[i] - 32); tx = x + b[i] - 16 - f.getlength(s) if der[i] else x + 16
                d.text((tx, base), s, font=f, fill="#1B1B1B", anchor="ls"); x += b[i]
        fila(t["columnas"], y, CAB, pil(BOLD, 32)); y += CAB
        for j, f in enumerate(pf):
            if j % 2 == 1: d.rectangle([MARGEN, y, ANCHO - MARGEN, y + FIL], fill="#F5F7F8")
            fila(f, y, FIL, pil(REG, 32)); y += FIL
        d.text((MARGEN, y + PIE - 20), "SPVI", font=pil(REG, 26), fill="#5F6368", anchor="ls")
        p = f"{base}_{k + 1}.png"; im.save(p); salida.append(p)
    return salida


# ------------------------------------------------------------------------------------------------ PNG (Tarjetas)
def envolver(s, f, ancho, maxl):
    palabras, lineas, act = s.split(), [], ""
    for w in palabras:
        prueba = (act + " " + w).strip()
        if f.getlength(prueba) <= ancho or not act: act = prueba
        else: lineas.append(act); act = w
    if act: lineas.append(act)
    if len(lineas) > maxl:
        lineas = lineas[:maxl]; lineas[-1] = recortar(lineas[-1] + "…", f, ancho) if f.getlength(lineas[-1] + "…") > ancho else lineas[-1] + "…"
    return [recortar(l, f, ancho) for l in lineas]


def texto_bloque(d, s, f, size, x, y, ancho, maxl, color):
    alto_l = round(size * 1.172); asc = f.getmetrics()[0]
    ls = envolver(s, f, ancho, maxl)
    for i, l in enumerate(ls): d.text((x, y + i * alto_l + asc), l, font=f, fill=color, anchor="ls")
    return len(ls) * alto_l


def tarjeta(im, p, r, grande):
    l, t, rr, b = r; radio = 32
    sombra = Image.new("RGBA", im.size, (0, 0, 0, 0)); ImageDraw.Draw(sombra).rounded_rectangle([l, t + 4, rr, b + 4], radio, fill=(0, 0, 0, 28))
    im.alpha_composite(sombra)
    d = ImageDraw.Draw(im); d.rounded_rectangle([l, t, rr, b], radio, fill="white")
    pad = 48 if grande else 28; alto_foto = (b - t) * (0.62 if grande else 0.56); zf = (l, t, rr, t + alto_foto)
    mask = Image.new("L", im.size, 0); md = ImageDraw.Draw(mask)
    md.rounded_rectangle([zf[0], zf[1], zf[2], zf[3] + radio], radio, fill=255); md.rectangle([zf[0], zf[3], zf[2], zf[3] + radio], fill=0)
    capa = Image.new("RGBA", im.size, "#478EA1"); im.paste(capa, (0, 0), mask)
    fi = pil(BOLD, alto_foto * 0.4); d = ImageDraw.Draw(im)
    d.text(((zf[0] + zf[2]) / 2, (zf[1] + zf[3]) / 2), p["inicial"], font=fi, fill="white", anchor="mm")
    y = zf[3] + pad * 0.8; ancho = (rr - l) - 2 * pad
    sz = 64 if grande else 38; y += texto_bloque(d, p["nombre"], pil(BOLD, sz), sz, l + pad, y, ancho, 2, "#1B1B1B")
    sz = 40 if grande else 26; y += 8 + texto_bloque(d, p["categoria"], pil(REG, sz), sz, l + pad, y + 8, ancho, 1, "#5F6368")
    sz = 76 if grande else 42; texto_bloque(d, p["precio"], pil(BOLD, sz), sz, l + pad, b - pad - sz * 1.2, ancho, 1, "#1F5F70")


def tarjetas(ps, nombre):
    n = len(ps); ANCHO, MARGEN, SEP, CELDA = 1080, 40, 24, 600
    filas = (n + (1 if n <= 1 else 2) - 1) // (1 if n <= 1 else 2)
    alto = 1350 if n <= 1 else MARGEN * 2 + filas * CELDA + (filas - 1) * SEP
    im = Image.new("RGBA", (ANCHO, alto), "#F8F9FA")
    if n == 1: tarjeta(im, ps[0], (48, 48, ANCHO - 48, alto - 48), True)
    else:
        w = (ANCHO - 2 * MARGEN - SEP) / 2
        for k, p in enumerate(ps):
            x = MARGEN + (k % 2) * (w + SEP); y = MARGEN + (k // 2) * (CELDA + SEP)
            tarjeta(im, p, (x, y, x + w, y + CELDA), False)
    im.convert("RGB").save(nombre); return nombre


if __name__ == "__main__":
    for n, ts in datos["docs"].items(): pdf(ts, f"{D}/{n}.pdf")
    imagen_tabla(datos["precios"], f"{D}/SPVI_precios")
    tarjetas(datos["tarjetas"], f"{D}/SPVI_productos_1.png")
    tarjetas(datos["tarjetas"][:1], f"{D}/SPVI_producto_ficha.png")
    print("PDF y PNG en", D)
