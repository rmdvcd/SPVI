p = "tools/seed_demo/collage.py"
s = open(p, encoding="utf-8").read()
s = s.replace('''def centrado(d, texto, y, font, relleno):
    caja = d.textbbox((0, 0), texto, font=font)
    d.text(((d.im.width - (caja[2] - caja[0])) // 2, y), texto, font=font, fill=relleno)''','''def centrado(d, texto, y, font, relleno, ancho):
    caja = d.textbbox((0, 0), texto, font=font)
    d.text(((ancho - (caja[2] - caja[0])) // 2, y), texto, font=font, fill=relleno)''')
s = s.replace('centrado(d, "SHALEV", 64, f_titulo, (255, 255, 255))', 'centrado(d, "SHALEV", 64, f_titulo, (255, 255, 255), hoja.width)')
s = s.replace('centrado(d, "Punto de venta e inventario", 176, f_sub, AZUL)', 'centrado(d, "Punto de venta e inventario", 176, f_sub, AZUL, hoja.width)')
s = s.replace('centrado(d, "✓  " + linea, y + i * 50, f, TEXTO_SUAVE)', 'centrado(d, "✓  " + linea, y + i * 50, f, TEXTO_SUAVE, hoja.width)')
s = s.replace('centrado(d, "cu.shalev.app   ·   Android 8.0+", y, fuente(28), (140, 158, 182))', 'centrado(d, "cu.shalev.app   ·   Android 8.0+", y, fuente(28), (140, 158, 182), hoja.width)')
open(p, "w", encoding="utf-8").write(s)
print("ok")
