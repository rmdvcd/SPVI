p = "docs/HISTORIAL_DESARROLLO.md"
s = open(p, encoding="utf-8").read()
ancla = "El detalle está en `promocion/README.md`."
extra = ancla + """

También se abrió turno en el teléfono y se registró una venta de cinco artículos para capturar la pantalla **Nueva venta** (carrito con cantidades y total) y el **Comprobante de pago**; la lista de más vendidos y los totales del Inicio se actualizaron con esa venta.

Con las capturas definitivas se preparó el paquete de **Google Play** en `play_store/`: icono 512×512, gráfico destacado 1024×500, ocho capturas 1080×1920 (9:16, recortadas de las de 2400) y `ficha.md` con nombre, descripción corta y completa y novedades. Lo genera `tools/seed_demo/play_store.py`. `play_store/README.md` anota lo que falta para publicar: el AAB firmado con la misma clave (`app/build/outputs/bundle/release/app-release.aab`), la política de privacidad y los formularios de la consola."""
s = s.replace(ancla, extra, 1)
open(p, "w", encoding="utf-8").write(s)
print("ok")
