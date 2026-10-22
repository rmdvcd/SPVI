p = "data/src/debug/kotlin/cu/shalev/data/seed/Sembrador.kt"
s = open(p, encoding="utf-8").read()
s = s.replace("""            db.mantenimientoDao().borrarOperacion()
            db.mantenimientoDao().borrarConfiguracion()""","""            db.mantenimientoDao().borrarOperacion()
            db.mantenimientoDao().borrarConfiguracion()
            // Las apps vinculadas no las borra el vaciado de respaldo (no viajan en él): se limpian aquí.
            db.openHelper.writableDatabase.execSQL("DELETE FROM empleado")""")
open(p, "w", encoding="utf-8").write(s)
print("ok")
