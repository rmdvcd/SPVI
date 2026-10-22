p = "tools/seed_demo/collage.py"
s = open(p, encoding="utf-8").read()
s = s.replace('''def bullets(hoja, lineas, y):
    d = ImageDraw.Draw(hoja)
    f = fuente(34)
    for i, linea in enumerate(lineas):
        centrado(d, "✓  " + linea, y + i * 50, f, TEXTO_SUAVE, hoja.width)''','''def bullets(hoja, lineas, y):
    d = ImageDraw.Draw(hoja)
    f = fuente(34)
    for i, linea in enumerate(lineas):
        caja = d.textbbox((0, 0), linea, font=f)
        ancho_texto = caja[2] - caja[0]
        total = ancho_texto + 58
        x = (hoja.width - total) // 2
        yy = y + i * 54
        # Círculo con la marca (dibujada: el glifo «✓» no está en todas las fuentes)
        d.ellipse((x, yy + 8, x + 34, yy + 42), fill=(26, 44, 66), outline=AZUL, width=2)
        d.line([(x + 9, yy + 25), (x + 15, yy + 32), (x + 25, yy + 18)], fill=AZUL, width=4)
        d.text((x + 58, yy), linea, font=f, fill=TEXTO_SUAVE)''')
s = s.replace('ancho = 296\n    xs = [192, 540, 888]', 'ancho = 312\n    xs = [192, 540, 888]')
s = s.replace('bullets(hoja, ["Ventas por efectivo y transferencia", "Inventario y servicios con fotos",\n                   "Reportes y respaldos sin internet"], 958)\n    pie(hoja, 1258)', 'bullets(hoja, ["Ventas por efectivo y transferencia", "Inventario y servicios con fotos",\n                   "Reportes y respaldos sin internet"], 1006)\n    pie(hoja, 1282)')
open(p, "w", encoding="utf-8").write(s)
print("ok")
