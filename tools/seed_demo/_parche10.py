p = "data/src/debug/kotlin/cu/shalev/data/seed/Sembrador.kt"
s = open(p, encoding="utf-8").read()
s = s.replace("""            val idsProducto = productos.mapIndexed { i, p ->
                val resto = if (p.elaborado) 0L else (12 + azar.nextInt(45)).toLong()""","""            val idsProducto = productos.mapIndexed { i, p ->
                val resto = if (p.elaborado) 0L else (12 + azar.nextInt(45)).toLong()
                // El costo del elaborado es el de su receta (lo que gasta una unidad).
                val costoElaborado = recetas[i].orEmpty().sumOf { (j, mil) -> insumos[j].precioCent * mil / 1000 }""")
s = s.replace("""                        precioCostoCent = p.costo, precioVentaCent = p.venta,""","""                        precioCostoCent = if (p.elaborado) costoElaborado else p.costo, precioVentaCent = p.venta,""")
open(p, "w", encoding="utf-8").write(s)
print("ok")
