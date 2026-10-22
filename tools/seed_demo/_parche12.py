p = "tools/seed_demo/collage.py"
s = open(p, encoding="utf-8").read()
s = s.replace('nombres = ["shot_inicio.png", "shot_inventario.png", "shot_servicios.png"]',
              'nombres = ["shot_inicio.png", "shot_carrito.png", "shot_inventario.png"]')
s = s.replace('fila1 = [("shot_inicio.png", 196), ("shot_inventario.png", 540), ("shot_registros.png", 884)]',
              'fila1 = [("shot_inicio.png", 196), ("shot_carrito.png", 540), ("shot_inventario.png", 884)]')
s = s.replace('fila2 = [("shot_vinculadas.png", 196), ("shot_servicios.png", 540), ("shot_inventario_claro.png", 884)]',
              'fila2 = [("shot_servicios.png", 196), ("shot_registros.png", 540), ("shot_vinculadas.png", 884)]')
s = s.replace('''"shot_vinculadas.png", "shot_inicio_claro.png", "shot_inventario_claro.png",
                   "shot_servicios_claro.png"):''','''"shot_vinculadas.png", "shot_inicio_claro.png", "shot_inventario_claro.png",
                   "shot_servicios_claro.png", "shot_carrito.png", "shot_pago.png"):''')
open(p, "w", encoding="utf-8").write(s)
print("ok")
