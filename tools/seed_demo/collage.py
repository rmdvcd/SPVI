"""Collage de promoción de Shalev a partir de capturas reales del teléfono.

Salidas en `promocion/`:
  - collage_feed_1080x1350.png     (publicación 4:5)
  - collage_historia_1080x1920.png (historia 9:16)
  - capturas/ (pantallas originales usadas)
"""
import os
import shutil

from PIL import Image, ImageDraw, ImageFilter, ImageFont

AQUI = os.path.dirname(__file__)
RAIZ = os.path.dirname(os.path.dirname(AQUI))
ENTRADA = os.path.join(RAIZ, "app_usb_check")
SALIDA = os.path.join(RAIZ, "promocion")
CAPTURAS = os.path.join(SALIDA, "capturas")

FUENTES = [r"C:\Windows\Fonts\segoeuib.ttf", r"C:\Windows\Fonts\arialbd.ttf"]
AZUL = (96, 206, 244)
AZUL_LINEA = (34, 118, 176)
TEXTO_SUAVE = (176, 196, 220)
ARRIBA, ABAJO = (9, 15, 27), (18, 31, 50)


def fuente(tam):
    for ruta in FUENTES:
        if os.path.exists(ruta):
            return ImageFont.truetype(ruta, tam)
    return ImageFont.load_default()


def fondo(ancho, alto):
    img = Image.new("RGB", (ancho, alto), ARRIBA)
    d = ImageDraw.Draw(img)
    for y in range(alto):
        t = y / (alto - 1)
        d.line([(0, y), (ancho, y)], fill=tuple(int(ARRIBA[i] + (ABAJO[i] - ARRIBA[i]) * t) for i in range(3)))
    capa = Image.new("RGBA", (ancho, alto), (0, 0, 0, 0))
    dl = ImageDraw.Draw(capa)
    for x in range(0, ancho + 60, 60):
        dl.line([(x, 0), (x, alto)], fill=(255, 255, 255, 10), width=1)
    for y in range(0, alto + 60, 60):
        dl.line([(0, y), (ancho, y)], fill=(255, 255, 255, 10), width=1)
    return Image.alpha_composite(img.convert("RGBA"), capa)


def centrado(d, texto, y, font, relleno, ancho):
    caja = d.textbbox((0, 0), texto, font=font)
    d.text(((ancho - (caja[2] - caja[0])) // 2, y), texto, font=font, fill=relleno)


def encabezado(hoja):
    d = ImageDraw.Draw(hoja)
    f_titulo, f_sub = fuente(96), fuente(38)
    centrado(d, "SHALEV", 64, f_titulo, (255, 255, 255), hoja.width)
    centrado(d, "Punto de venta e inventario", 176, f_sub, AZUL, hoja.width)
    f_pill = fuente(28)
    texto = "Funciona sin internet"
    caja = d.textbbox((0, 0), texto, font=f_pill)
    ancho = (caja[2] - caja[0]) + 56
    x = (hoja.width - ancho) // 2
    d.rounded_rectangle((x, 234, x + ancho, 286), 26, fill=(23, 38, 58), outline=AZUL_LINEA, width=2)
    d.text((x + 28, 242), texto, font=f_pill, fill=AZUL)


def telefono(ruta, ancho):
    im = Image.open(ruta).convert("RGB")
    im = im.crop((0, 96, im.width, im.height - 96))
    alto = int(ancho * im.height / im.width)
    im = im.resize((ancho, alto), Image.LANCZOS)
    radio = max(14, ancho // 18)
    mascara = Image.new("L", im.size, 0)
    ImageDraw.Draw(mascara).rounded_rectangle((0, 0, ancho - 1, alto - 1), radio, fill=255)
    sombra = Image.new("RGBA", (ancho + 48, alto + 48), (0, 0, 0, 0))
    ImageDraw.Draw(sombra).rounded_rectangle((24, 28, ancho + 24, alto + 28), radio, fill=(0, 0, 0, 165))
    sombra = sombra.filter(ImageFilter.GaussianBlur(16))
    foto = Image.new("RGBA", (ancho, alto), (0, 0, 0, 0))
    foto.paste(im, (0, 0), mascara)
    return sombra, foto


def pegar(hoja, ruta, centro_x, arriba_y, ancho):
    sombra, foto = telefono(ruta, ancho)
    capa = Image.new("RGBA", (foto.width + 48, foto.height + 48), (0, 0, 0, 0))
    capa.alpha_composite(sombra, (0, 0))
    capa.alpha_composite(foto, (24, 24))
    hoja.alpha_composite(capa, (centro_x - capa.width // 2, arriba_y))


def bullets(hoja, lineas, y):
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
        d.text((x + 58, yy), linea, font=f, fill=TEXTO_SUAVE)


def pie(hoja, y):
    d = ImageDraw.Draw(hoja)
    centrado(d, "cu.shalev.app   ·   Android 8.0+", y, fuente(28), (140, 158, 182), hoja.width)


def collage_feed():
    hoja = fondo(1080, 1350)
    encabezado(hoja)
    ancho = 312
    xs = [192, 540, 888]
    nombres = ["shot_inicio.png", "shot_carrito.png", "shot_inventario.png"]
    for x, nombre in zip(xs, nombres):
        pegar(hoja, os.path.join(ENTRADA, nombre), x, 316, ancho)
    bullets(hoja, ["Ventas por efectivo y transferencia", "Inventario y servicios con fotos",
                   "Reportes y respaldos sin internet"], 1006)
    pie(hoja, 1282)
    return hoja.convert("RGB")


def collage_historia():
    hoja = fondo(1080, 1920)
    encabezado(hoja)
    ancho = 300
    fila1 = [("shot_inicio.png", 196), ("shot_carrito.png", 540), ("shot_inventario.png", 884)]
    fila2 = [("shot_servicios.png", 196), ("shot_registros.png", 540), ("shot_vinculadas.png", 884)]
    for nombre, x in fila1:
        pegar(hoja, os.path.join(ENTRADA, nombre), x, 306, ancho)
    for nombre, x in fila2:
        pegar(hoja, os.path.join(ENTRADA, nombre), x, 1002, ancho)
    bullets(hoja, ["8 apps secundarias para tus empleados", "Todo el negocio en un solo lugar"], 1756)
    pie(hoja, 1846)
    return hoja.convert("RGB")


def main():
    os.makedirs(CAPTURAS, exist_ok=True)
    for nombre in ("shot_inicio.png", "shot_inventario.png", "shot_servicios.png", "shot_registros.png",
                   "shot_vinculadas.png", "shot_inicio_claro.png", "shot_inventario_claro.png",
                   "shot_servicios_claro.png", "shot_carrito.png", "shot_pago.png"):
        origen = os.path.join(ENTRADA, nombre)
        if os.path.exists(origen):
            shutil.copy2(origen, os.path.join(CAPTURAS, nombre))
    collage_feed().save(os.path.join(SALIDA, "collage_feed_1080x1350.png"))
    collage_historia().save(os.path.join(SALIDA, "collage_historia_1080x1920.png"))
    print("collages en", SALIDA)


if __name__ == "__main__":
    main()
