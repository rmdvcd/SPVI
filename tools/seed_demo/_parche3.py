p = "tools/seed_demo/fotos_wikipedia.py"
s = open(p, encoding="utf-8").read()
s = s.replace('''def main():
    os.makedirs(SALIDA, exist_ok=True)
    filas, fallos = [], []''', '''CACHE = os.path.join(AQUI, "seed_fotos", "creditos.json")


def main():
    os.makedirs(SALIDA, exist_ok=True)
    filas, fallos = [], []
    previos = {}
    if os.path.exists(CACHE):
        previos = json.load(open(CACHE, encoding="utf-8"))
    filas = list(previos.items()) if previos else []''')

s = s.replace('''    filas = filas or []
    with open(CREDITOS, "w", encoding="utf-8") as f:''','''    with open(CACHE, "w", encoding="utf-8") as fc:
        json.dump(dict(filas), fc, ensure_ascii=False, indent=1)
    filas = sorted(dict(filas).items(), key=lambda x: x[0])
    with open(CREDITOS, "w", encoding="utf-8") as f:''')
open(p, "w", encoding="utf-8").write(s)
print("ok")
