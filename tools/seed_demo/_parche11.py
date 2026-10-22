p = "tools/seed_demo/play_store.py"
s = open(p, encoding="utf-8").read()
s = s.replace('''    for ruta, x, alto in ((os.path.join(SHOTS, "shot_carrito.png"), 700, 400),
                          (os.path.join(SHOTS, "shot_inicio.png"), 862, 400)):''','''    for ruta, x, alto in ((os.path.join(SHOTS, "shot_carrito.png"), 640, 420),
                          (os.path.join(SHOTS, "shot_inicio.png"), 820, 420)):''')
s = s.replace('hoja.alpha_composite(capa, (x - 40, 60))', 'hoja.alpha_composite(capa, (x - 40, 52))')
open(p, "w", encoding="utf-8").write(s)
print("ok")
