#!/usr/bin/env python3
"""PDF final: portada + índice enlazado (y marcadores) + pantallas nº 1–24 (PDF anterior) + nº 25… (generar.py) + E1… (paginas.py).
Uso: python3 ensamblar.py PORTADA.pdf ANTERIOR.pdf (p. ej. ~/SPVI_0.25.1_capturas_y_exportaciones.pdf) DIR_PANTALLAS DIR_EXPORT SALIDA.pdf
"""
import glob, os, sys
import pymupdf

portada, anterior, dcap, dexp, salida = sys.argv[1:6]
# DejaVu: tiene →, ✓ y ✕ (Roboto no).
FUENTE = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
NEGRITA = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
MEDIDA = pymupdf.Font(fontfile=FUENTE)

out = pymupdf.open()
out.insert_pdf(pymupdf.open(portada))
ant = pymupdf.open(anterior)
paginas = []  # (sección, título, doc, índice)
# nº 1–24 (0.18.4) solo existen como PDF: se toman del PDF entregado anterior buscando «N. …» en la primera línea.
import re
vistos = {}
for i in range(ant.page_count):
    linea = ant[i].get_text().strip().split("\n")[0]
    m = re.match(r"^(\d+)\. ", linea)
    if m and 1 <= int(m.group(1)) <= 24 and int(m.group(1)) not in vistos: vistos[int(m.group(1))] = (linea, i)
assert len(vistos) == 24, f"faltan pantallas 1–24 en {anterior}"
for k in range(1, 25):
    paginas.append(("Pantallas", vistos[k][0], ant, vistos[k][1]))
for f in sorted(glob.glob(f"{dcap}/p*.pdf")):
    d = pymupdf.open(f); paginas.append(("Pantallas", d[0].get_text().strip().split("\n")[0], d, 0))
for f in sorted(glob.glob(f"{dexp}/e*.pdf")):
    d = pymupdf.open(f); paginas.append(("Exportaciones", d[0].get_text().strip().split("\n")[0], d, 0))

W, H = 842.88, 595.92
POR_COL, COLS = 34, 2
por_pag = POR_COL * COLS
filas = []
seccion = None
for s, t, *_ in paginas:
    if s != seccion:
        filas.append(("titulo", s)); seccion = s
    filas.append(("item", t))
n_indice = -(-len(filas) // por_pag)
primera = 1 + n_indice  # índice 0-based de la primera página de contenido
indices = []
for k in range(n_indice):
    pg = out.new_page(width=W, height=H)
    indices.append(pg)
    pg.insert_font(fontname="R", fontfile=FUENTE); pg.insert_font(fontname="B", fontfile=NEGRITA)
    pg.insert_text((40, 40), f"Índice ({k + 1}/{n_indice})", fontname="B", fontsize=16, color=(0.169, 0.31, 0.639))
for sec, t, d, i in paginas:
    out.insert_pdf(d, from_page=i, to_page=i)

toc = [[1, "Portada", 1]] + [[1, f"Índice {k + 1}", 2 + k] for k in range(n_indice)]
destino = primera
item = 0
seccion = None
for j, (tipo, texto) in enumerate(filas):
    pg = out[1 + j // por_pag]; r = j % por_pag; col, fila = divmod(r, POR_COL)
    x = 40 + col * (W - 80) / COLS; y = 66 + fila * 15
    ancho = (W - 80) / COLS - 34
    if tipo == "titulo":
        pg.insert_text((x, y), texto, fontname="B", fontsize=10.5, color=(0.169, 0.31, 0.639))
        seccion = texto; toc.append([1, texto, destino + 1])
        continue
    etiqueta = texto
    while MEDIDA.text_length(etiqueta, fontsize=8.2) > ancho - 6 and len(etiqueta) > 10:
        etiqueta = etiqueta[:-2]
    if etiqueta != texto: etiqueta = etiqueta.rstrip() + "…"
    pg.insert_text((x, y), etiqueta, fontname="R", fontsize=8.2, color=(0.1, 0.11, 0.12))
    pg.insert_text((x + ancho + 2, y), str(destino + 1), fontname="R", fontsize=8, color=(0.37, 0.39, 0.41))
    pg.insert_link({"kind": pymupdf.LINK_GOTO, "page": destino, "from": pymupdf.Rect(x, y - 10, x + ancho + 14, y + 3)})
    toc.append([2, texto[:90], destino + 1])
    destino += 1
    item += 1
out.set_toc(toc)
out.set_metadata({"title": "SPVI 0.25.1 — pantallas y exportaciones", "author": "SPVI"})
out.save(salida, garbage=3, deflate=True)
print(salida, out.page_count, "páginas;", n_indice, "de índice")
