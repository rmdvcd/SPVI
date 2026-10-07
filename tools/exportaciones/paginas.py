#!/usr/bin/env python3
"""Páginas A4 apaisadas (1124x795 px, como tools/capturas) que muestran cada exportación generada por Muestras.kt +
render.py: el PDF (página 1), el Excel (rejilla leída del .xlsx real), y las imágenes PNG (0.26.0: sin textos).
Uso: python3 paginas.py <dir-muestras> <salida-html> [n-inicial]   ->  eNN.html (luego: node ../capturas/render.js)
"""
import base64, html, io, json, os, re, sys, zipfile
import pymupdf

D, SAL = sys.argv[1], sys.argv[2]
N0 = int(sys.argv[3]) if len(sys.argv) > 3 else 1
os.makedirs(SAL, exist_ok=True)
datos = json.load(open(f"{D}/tablas.json", encoding="utf-8"))
OBS = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "observaciones.json"), encoding="utf-8"))
e = html.escape


def uri_png(b): return "data:image/png;base64," + base64.b64encode(b).decode()


CSS = """@page{size:842.88pt 595.92pt;margin:0}*{margin:0;padding:0;box-sizing:border-box}
html,body{width:1123.84px;height:794.56px;font-family:Roboto,'DejaVu Sans',sans-serif;color:#1A1C1E;background:#fff}
.w{height:100%;display:flex;flex-direction:column;padding:22px 34px 18px}
h1{font-size:17px;font-weight:600;color:#2B4FA3;margin-bottom:12px}
.c{flex:1;display:flex;gap:22px;min-height:0}
.izq{flex:1;display:flex;gap:14px;align-items:flex-start;justify-content:center;min-width:0}
.der{width:300px;display:flex;flex-direction:column;gap:10px;font-size:12px;line-height:17px}
.der h2{font-size:12.5px;font-weight:700;margin-bottom:2px}
.cajita{border:1px solid #E0E3E7;border-radius:10px;padding:9px 12px;background:#F8F9FA}
.mal{border-color:#C62828;background:#FDE7E7}.mal h2{color:#8E1B1B}
.bien{border-color:#2F6F80;background:#E3EEF1}
.pdf{height:100%;max-height:700px;box-shadow:0 1px 4px rgba(0,0,0,.25);border:1px solid #ccc}
.png{max-height:700px;max-width:100%;min-width:0;object-fit:contain;box-shadow:0 1px 4px rgba(0,0,0,.25)}
.xl{font-family:'DejaVu Sans',Arial,sans-serif;font-size:11px;border:1px solid #c8c8c8;background:#fff;width:100%;max-height:680px;overflow:hidden;display:flex;flex-direction:column}
.xl .barra{background:#217346;color:#fff;font-size:12px;padding:5px 10px}
.xl .fx{border-bottom:1px solid #d4d4d4;padding:3px 8px;color:#555;font-size:11px}
.xl table{border-collapse:collapse;table-layout:fixed}
.xl td{border:1px solid #e1e1e1;height:19px;padding:0 4px;white-space:nowrap;overflow:hidden;text-overflow:clip}
.xl td.h{background:#f3f3f3;color:#555;text-align:center;font-size:10px}
.xl tr.frz td{border-bottom:2px solid #9a9a9a}
.xl .tabs{margin-top:auto;border-top:1px solid #d4d4d4;display:flex;gap:2px;padding:0 8px;background:#f3f3f3}
.xl .tabs span{padding:4px 12px;font-size:11px;color:#444}.xl .tabs span.sel{background:#fff;color:#217346;font-weight:700;border-bottom:2px solid #217346}
.chat{width:360px;height:680px;border-radius:22px;background:#ECE5DD;box-shadow:0 1px 4px rgba(0,0,0,.25);overflow:hidden;display:flex;flex-direction:column}
.chat .top{background:#075E54;color:#fff;padding:12px 16px;font-size:14px}
.chat .bub{margin:12px 12px 0 auto;max-width:300px;background:#DCF8C6;border-radius:10px 2px 10px 10px;padding:8px 10px;font-size:11.5px;line-height:15.5px;white-space:pre-wrap;word-break:break-word;overflow:hidden}
.chat .meta{margin:4px 14px 0 auto;font-size:10px;color:#555}
.pie{font-size:10px;color:#5F6368;margin-top:8px}
"""


def pagina(n, titulo, izq, clave):
    o = OBS.get(clave, {})
    der = f'<div class="cajita"><h2>Dónde</h2>{e(o.get("donde", ""))}</div>'
    if o.get("incluye"): der += f'<div class="cajita"><h2>Qué incluye</h2>{e(o["incluye"])}</div>'
    for m in o.get("problemas", []): der += f'<div class="cajita mal"><h2>Problema detectado</h2>{e(m)}</div>'
    for m in o.get("bien", []): der += f'<div class="cajita bien"><h2>Correcto</h2>{e(m)}</div>'
    pie = ('Generado con el código real (TablasExport y XlsxWriter); el PDF y las imágenes se dibujan con las mismas medidas, '
           'colores y reglas de PdfWriter.kt, ImagenTabla.kt y Tarjetas.kt (tools/exportaciones). Datos de ejemplo.')
    h = (f'<!doctype html><html lang="es"><head><meta charset="utf-8"><style>{CSS}</style></head><body><div class="w">'
         f'<h1>E{n} · {e(titulo)}</h1><div class="c"><div class="izq">{izq}</div><div class="der">{der}</div></div>'
         f'<div class="pie">{pie}</div></div></body></html>')
    open(f"{SAL}/e{n:02d}.html", "w", encoding="utf-8").write(h)
    return n + 1


def _recorte(pg):
    """Página recortada hasta donde acaba el contenido (sin el pie «SPVI · página N»), a 170 ppp."""
    w, h = pg.rect.width, pg.rect.height
    fondo = max((b[3] for b in pg.get_text("blocks") if b[3] < h - 60), default=300)
    alto = min(h - 50, fondo + 24)
    return uri_png(pg.get_pixmap(dpi=170, clip=pymupdf.Rect(0, 0, w, alto)).tobytes("png")), w > h


def img_pdf(nombre, pags=(0,)):
    """Una o varias páginas (lado a lado). 0.25.1: las tablas de más de 5 columnas van en hoja horizontal."""
    d = pymupdf.open(f"{D}/{nombre}.pdf")
    imgs, orient = [], []
    for p in pags:
        uri, apaisada = _recorte(d[p]); orient.append("horizontal (842 × 595 pt)" if apaisada else "vertical (595 × 842 pt)")
        imgs.append(f'<img class="pdf" style="width:{100 // len(pags) - (2 if len(pags) > 1 else 0)}%;height:auto;max-height:640px;object-fit:contain;object-position:top" src="{uri}">')
    hojas = " + ".join(f"pág. {p + 1}: A4 {o}" for p, o in zip(pags, orient)) if len(pags) > 1 else f"Hoja A4 {orient[0]}"
    return (f'<div style="display:flex;flex-direction:column;gap:6px;width:100%"><div style="display:flex;gap:10px;align-items:flex-start">{"".join(imgs)}</div>'
            f'<div class="pie">{hojas}, recortada bajo la última fila · {d.page_count} página(s) · pie «SPVI · página N»</div></div>')


def img_png(ruta):
    return f'<img class="png" src="{uri_png(open(ruta, "rb").read())}">'


# ---------------------------------------------------------------------------------------- Excel (lectura del .xlsx)
NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}


def leer_xlsx(ruta):
    from lxml import etree
    z = zipfile.ZipFile(ruta)
    sst = []
    if "xl/sharedStrings.xml" in z.namelist():
        for si in etree.fromstring(z.read("xl/sharedStrings.xml")).findall("m:si", NS):
            sst.append("".join(si.itertext()))
    wb = etree.fromstring(z.read("xl/workbook.xml"))
    nombres = [s.get("name") for s in wb.find("m:sheets", NS)]
    styles_raw = z.read("xl/styles.xml").decode()
    valido = True
    try: st = etree.fromstring(styles_raw.encode())
    except Exception: valido = False; st = None
    # 0.26.0 (§8): estilo real de cada celda (negrita, ajuste de texto, formato CUP) leído de styles.xml.
    global ESTILOS
    ESTILOS = {}
    if st is not None:
        fmts = {f.get("numFmtId"): f.get("formatCode", "") for f in st.iter("{%s}numFmt" % NS["m"])}
        fuentes = [f.find("m:b", NS) is not None for f in st.find("m:fonts", NS)]
        for k, xf in enumerate(st.find("m:cellXfs", NS)):
            al = xf.find("m:alignment", NS)
            ESTILOS[str(k)] = {"b": fuentes[int(xf.get("fontId", 0))] if fuentes else False,
                               "wrap": al is not None and al.get("wrapText") in ("1", "true"),
                               "cup": "CUP" in fmts.get(xf.get("numFmtId"), "").replace("\\", "")}
    hojas = []
    for i, nom in enumerate(nombres, 1):
        x = etree.fromstring(z.read(f"xl/worksheets/sheet{i}.xml"))
        cols = {int(c.get("min")): float(c.get("width")) for c in x.iter("{%s}col" % NS["m"])}
        filas = {}
        for c in x.iter("{%s}c" % NS["m"]):
            ref = c.get("r"); col = re.match(r"[A-Z]+", ref).group(); fila = int(ref[len(col):])
            v = c.find("m:v", NS); isv = c.find("m:is", NS)
            if c.get("t") == "s": val, num = sst[int(v.text)], False
            elif c.get("t") == "inlineStr": val, num = "".join(isv.itertext()), False
            elif v is not None: val, num = v.text, True
            else: val, num = "", False
            ci = 0
            for ch in col: ci = ci * 26 + ord(ch) - 64
            filas.setdefault(fila, {})[ci] = (val, num, c.get("s"))
        hojas.append((nom, cols, filas))
    return hojas, valido


def excel(ruta, hoja=0, alto=680, filas_min=22):
    hojas, valido = leer_xlsx(ruta)
    nom, cols, filas = hojas[hoja]
    ncol = max(max(f.keys()) for f in filas.values())
    nfil = max(filas)
    ancho = lambda c: int(cols.get(c, 8.43) * 7 + 5)
    letras = lambda c: chr(64 + c)
    filas_html = '<tr><td class="h" style="width:34px"></td>' + "".join(f'<td class="h" style="width:{ancho(c)}px">{letras(c)}</td>' for c in range(1, ncol + 1)) + "</tr>"
    for r in range(1, max(nfil, filas_min) + 1):
        tr = '<tr class="frz">' if r == 1 else "<tr>"
        tr += f'<td class="h">{r}</td>'
        for c in range(1, ncol + 1):
            val, num, s = filas.get(r, {}).get(c, ("", False, None))
            es = ESTILOS.get(s or "0", {})
            est = "background:#DDE3F0;font-weight:700;" if r == 1 else ("font-weight:700;" if es.get("b") else "")
            if es.get("wrap"): est += "white-space:normal;"
            if num and val:
                v = float(val); txt = f"{v:,.2f} CUP" if es.get("cup") else (str(int(v)) if v == int(v) else str(v))
                est += "text-align:right;"
            else: txt = val
            tr += f'<td style="{est}">{e(txt)}</td>'
        filas_html += tr + "</tr>"
    tabs = "".join(f'<span class="{"sel" if i == hoja else ""}">{e(h[0])}</span>' for i, h in enumerate(hojas))
    # 0.26.0: se ve la hoja entera (todas las columnas con su ancho real), reducida si no cabe.
    zoom = min(1.0, 728 / (34 + sum(ancho(c) for c in range(1, ncol + 1))))
    return (f'<div class="xl" style="max-height:{alto}px"><div class="barra">{e(os.path.basename(ruta))} · Excel</div><div class="fx">A1 &nbsp;|&nbsp; '
            f'{e(filas.get(1, {}).get(1, ("", 0, 0))[0])}</div><table style="zoom:{zoom:.3f}">{filas_html}</table><div class="tabs">{tabs}</div></div>'), valido


def chat(texto, titulo="WhatsApp"):
    return (f'<div class="chat"><div class="top">{e(titulo)}</div><div class="bub">{e(texto)}</div>'
            f'<div class="meta">{len(texto)} caracteres</div></div>')


def generar(n=N0):
    for nombre, tit in (("registros_ventas", "Registros → Ventas → Compartir → PDF"),
                        ("registros_transferencias", "Registros → Transferencias → Compartir → PDF"),
                        ("registros_movimientos", "Registros → Movimientos → Compartir → PDF"),
                        ("inventario", "Inventario → Exportar → PDF"),
                        ("ficha_producto", "Inventario → ficha de un producto → Compartir → PDF"),
                        ("servicios", "Servicios → Exportar → PDF")):
        n = pagina(n, tit, img_pdf(nombre, (0,)), "pdf_" + nombre)
    # 0.25.1 (A): el turno completo; con hojas vertical (resumen, arqueo, caja) y horizontal (ventas, inventario).
    n = pagina(n, "Registros → Turnos → turno → Compartir → PDF (pág. 1: resumen, arqueo y caja)", img_pdf("turno", (0,)), "pdf_turno")
    if pymupdf.open(f"{D}/turno.pdf").page_count > 1:
        n = pagina(n, "Registros → Turnos → turno → Compartir → PDF (pág. 2: ventas y movimientos, en horizontal)", img_pdf("turno", (1,)), "pdf_turno")
    for nombre, tit in (("registros_ventas", "Registros → Ventas → Compartir → Excel"),
                        ("registros_transferencias", "Registros → Transferencias → Compartir → Excel"),
                        ("registros_movimientos", "Registros → Movimientos → Compartir → Excel"),
                        ("inventario", "Inventario → Exportar → Excel"),
                        ("servicios", "Servicios → Exportar → Excel")):
        h, _ = excel(f"{D}/{nombre}.xlsx")
        n = pagina(n, tit, h, "xlsx_" + nombre)
    a, _ = excel(f"{D}/turno.xlsx", 0, 330, 12); b, _ = excel(f"{D}/turno.xlsx", 1, 330, 10)
    n = pagina(n, "Registros → Turnos → turno → Compartir → Excel (hojas «Turno…» y «Arqueo de caja»)",
               f'<div style="display:flex;flex-direction:column;gap:12px;width:100%">{a}{b}</div>', "xlsx_turno")
    n = pagina(n, "Inventario → Exportar → Imagen (lista de precios para clientes)", img_png(f"{D}/SPVI_precios_1.png"), "png_precios")
    n = pagina(n, "Inventario → Exportar → Tarjetas (hasta 6 por imagen) · ficha → Imagen (1 tarjeta)",
               '<img class="png" style="max-width:48%" src="' + uri_png(open(f"{D}/SPVI_productos_1.png","rb").read()) + '"><img class="png" style="max-width:48%" src="' + uri_png(open(f"{D}/SPVI_producto_ficha.png","rb").read()) + '">', "png_tarjetas")
    return n


if __name__ == "__main__":
    print("hasta", generar() - 1)
