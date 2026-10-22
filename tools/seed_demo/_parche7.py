p = "data/src/debug/kotlin/cu/shalev/data/seed/Sembrador.kt"
s = open(p, encoding="utf-8").read()
s = s.replace('''        Insumo("Café en grano", "g", 9),
        Insumo("Leche entera", "ml", 2),
        Insumo("Azúcar", "g", 1),
        Insumo("Harina", "g", 1),
        Insumo("Huevos", "u", 1200),
        Insumo("Mantequilla", "g", 12),
        Insumo("Queso crema", "g", 14),
        Insumo("Jamón en lonjas", "g", 18),
        Insumo("Pulpa de mango", "ml", 6),
        Insumo("Chocolate en polvo", "g", 15),
        Insumo("Sirope de vainilla", "ml", 8),
        Insumo("Pan de sándwich", "u", 900),
        Insumo("Vasos de 12 oz", "u", 700),
        Insumo("Servilletas", "u", 200),''','''        Insumo("Café en grano", "GRAMO", 9),
        Insumo("Leche entera", "MILILITRO", 2),
        Insumo("Azúcar", "GRAMO", 1),
        Insumo("Harina", "GRAMO", 1),
        Insumo("Huevos", "UNIDAD", 1200),
        Insumo("Mantequilla", "GRAMO", 12),
        Insumo("Queso crema", "GRAMO", 14),
        Insumo("Jamón en lonjas", "GRAMO", 18),
        Insumo("Pulpa de mango", "MILILITRO", 6),
        Insumo("Chocolate en polvo", "GRAMO", 15),
        Insumo("Sirope de vainilla", "MILILITRO", 8),
        Insumo("Pan de sándwich", "UNIDAD", 900),
        Insumo("Vasos de 12 oz", "UNIDAD", 700),
        Insumo("Servilletas", "UNIDAD", 200),''')
s = s.replace('val unidades = if (ins.unidad == "u")', 'val unidades = if (ins.unidad == "UNIDAD")')
open(p, "w", encoding="utf-8").write(s)
print("ok")
