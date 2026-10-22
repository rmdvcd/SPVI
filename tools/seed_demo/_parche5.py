p = "data/src/debug/kotlin/cu/shalev/data/seed/Sembrador.kt"
s = open(p, encoding="utf-8").read()
s = s.replace("""            val idsInsumo = insumos.mapIndexed { i, ins ->
                val resto = (8 + azar.nextInt(25)) * 1000L""","""            val idsInsumo = insumos.mapIndexed { i, ins ->
                // Existencias finales suficientes para que los elaborados se puedan seguir sirviendo.
                val unidades = if (ins.unidad == "u") 120L + azar.nextInt(180) else 800L + azar.nextInt(2200)
                val resto = unidades * 1000L""")
s = s.replace("""                        nivelBajoMil = 3_000, nivelCriticoMil = 1_000,""","""                        nivelBajoMil = resto / 4, nivelCriticoMil = resto / 10,""")
open(p, "w", encoding="utf-8").write(s)
print("ok")
