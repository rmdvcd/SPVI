"""Genera las fotos de demostración (productos y servicios) para el sembrador de debug.

Cada imagen es una tarjeta ilustrada: degradado del color de la categoría, un «paquete»
redondeado en blanco, una banda de color y el monograma del nombre. 512x512 JPEG.
"""
import os
import random
from PIL import Image, ImageDraw, ImageFont

W = H = 512
SALIDA = os.path.join(os.path.dirname(__file__), "seed_fotos")

COLORES = {
    "Alimentos": (239, 108, 42),
    "Granos y cereales": (198, 139, 42),
    "Bebidas": (18, 148, 141),
    "Bebidas alcohólicas": (124, 58, 148),
    "Lácteos": (41, 116, 196),
    "Cárnicos y embutidos": (198, 55, 55),
    "Conservas": (112, 130, 44),
    "Condimentos y especias": (150, 92, 40),
    "Frutas y vegetales": (56, 150, 64),
    "Limpieza del hogar": (22, 145, 175),
    "Aseo personal": (208, 70, 130),
    "Panadería y dulcería": (206, 152, 36),
    "Confituras y snacks": (196, 62, 116),
    "Aceites y grasas": (196, 170, 40),
    "Elaborado": (214, 84, 34),
    "Telefonía": (30, 120, 190),
    "Impresión": (70, 90, 175),
    "Copias": (92, 74, 160),
    "Encuadernación": (140, 80, 150),
    "Fotografía": (40, 140, 120),
    "Digitalización": (40, 130, 150),
    "Barbería": (60, 80, 110),
    "Belleza": (200, 70, 140),
    "Lavandería": (60, 140, 190),
    "Domicilio": (150, 110, 40),
    "Regalos": (190, 90, 70),
}

PRODUCTOS = [
    ("Café molido Serrano 250 g", "Alimentos"), ("Arroz grano largo 1 kg", "Granos y cereales"),
    ("Frijol negro 1 kg", "Granos y cereales"), ("Azúcar refino 1 kg", "Alimentos"),
    ("Aceite de girasol 1 L", "Aceites y grasas"), ("Harina de trigo 1 kg", "Granos y cereales"),
    ("Leche en polvo 400 g", "Lácteos"), ("Leche condensada 397 g", "Lácteos"),
    ("Yogur natural 200 ml", "Lácteos"), ("Queso Gouda 250 g", "Lácteos"),
    ("Jamón cocido 200 g", "Cárnicos y embutidos"), ("Salchicha ahumada 500 g", "Cárnicos y embutidos"),
    ("Atún en aceite 170 g", "Conservas"), ("Sardinas en tomate 155 g", "Conservas"),
    ("Pasta larga 500 g", "Alimentos"), ("Salsa de tomate 250 g", "Conservas"),
    ("Mayonesa 250 g", "Condimentos y especias"), ("Mostaza 200 g", "Condimentos y especias"),
    ("Refresco cola 1.5 L", "Bebidas"), ("Agua mineral 1.5 L", "Bebidas"),
    ("Jugo de mango 1 L", "Bebidas"), ("Cerveza rubia 330 ml", "Bebidas alcohólicas"),
    ("Ron añejo 750 ml", "Bebidas alcohólicas"), ("Vino tinto seco 750 ml", "Bebidas alcohólicas"),
    ("Pan de molde 400 g", "Panadería y dulcería"), ("Galletas María 200 g", "Panadería y dulcería"),
    ("Chocolate en barra 100 g", "Confituras y snacks"), ("Papas fritas 120 g", "Confituras y snacks"),
    ("Maní salado 150 g", "Confituras y snacks"), ("Plátano 1 kg", "Frutas y vegetales"),
    ("Tomate 1 kg", "Frutas y vegetales"), ("Cebolla 1 kg", "Frutas y vegetales"),
    ("Detergente 500 g", "Limpieza del hogar"), ("Jabón de baño 120 g", "Aseo personal"),
    ("Pasta dental 90 g", "Aseo personal"), ("Papel higiénico 4 rollos", "Aseo personal"),
    ("Café con leche", "Elaborado"), ("Capuchino", "Elaborado"),
    ("Refresco natural de mango", "Elaborado"), ("Sándwich de jamón", "Elaborado"),
]

SERVICIOS = [
    ("Recarga de saldo móvil", "Telefonía"), ("Impresión en blanco y negro", "Impresión"),
    ("Impresión a color", "Impresión"), ("Fotocopia a una cara", "Copias"),
    ("Fotocopia a doble cara", "Copias"), ("Plastificado de carné", "Encuadernación"),
    ("Encuadernación en espiral", "Encuadernación"), ("Revelado de fotos 4x6", "Fotografía"),
    ("Escaneo de documento", "Digitalización"), ("Corte de cabello", "Barbería"),
    ("Afelpado de barba", "Barbería"), ("Manicure", "Belleza"),
    ("Tinte de cabello", "Belleza"), ("Reparación de uña", "Belleza"),
    ("Lavado de ropa por libra", "Lavandería"), ("Planchado de prenda", "Lavandería"),
    ("Entrega a domicilio", "Domicilio"), ("Carga de batería de móvil", "Telefonía"),
    ("Instalación de aplicación", "Telefonía"), ("Funda de regalo", "Regalos"),
]

FUENTES = [
    r"C:\Windows\Fonts\segoeuib.ttf", r"C:\Windows\Fonts\arialbd.ttf",
    r"C:\Windows\Fonts\segoeui.ttf", r"C:\Windows\Fonts\arial.ttf",
]


def fuente(tam):
    for ruta in FUENTES:
        if os.path.exists(ruta):
            return ImageFont.truetype(ruta, tam)
    return ImageFont.load_default()


def monograma(nombre):
    palabras = [p for p in nombre.replace("-", " ").split() if len(p) > 1]
    if not palabras:
        return nombre[:2].upper()
    if len(palabras) == 1:
        return palabras[0][:2].upper()
    return (palabras[0][0] + palabras[1][0]).upper()


def aclarar(c, f):
    return tuple(min(255, int(v + (255 - v) * f)) for v in c)


def oscurecer(c, f):
    return tuple(max(0, int(v * (1 - f))) for v in c)


def degradado(base):
    img = Image.new("RGB", (W, H), base)
    d = ImageDraw.Draw(img)
    arriba, abajo = aclarar(base, 0.55), aclarar(base, 0.15)
    for y in range(H):
        t = y / (H - 1)
        d.line([(0, y), (W, y)], fill=tuple(int(arriba[i] + (abajo[i] - arriba[i]) * t) for i in range(3)))
    return img


def sombra(img, caja, radio, alfa=40, desplazamiento=10):
    capa = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(capa)
    x0, y0, x1, y1 = caja
    d.rounded_rectangle((x0, y0 + desplazamiento, x1, y1 + desplazamiento), radio, fill=(0, 0, 0, alfa))
    return Image.alpha_composite(img.convert("RGBA"), capa)


def tarjeta(nombre, categoria, indice, es_servicio):
    base = COLORES.get(categoria, (110, 110, 120))
    img = degradado(base)

    # Burbujas decorativas del fondo
    d = ImageDraw.Draw(img)
    for _ in range(6):
        x, y = random.randint(0, W), random.randint(0, H)
        r = random.randint(40, 120)
        d.ellipse((x - r, y - r, x + r, y + r), fill=aclarar(base, 0.25))

    # Cuerpo del «producto»
    caja = (86, 96, 426, 416)
    img = sombra(img, caja, 46)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle(caja, 46, fill=(250, 250, 252, 255))

    # Banda de color
    d.rounded_rectangle((86, 96, 426, 226), 46, fill=oscurecer(base, 0.05) + (255,))
    d.rectangle((86, 186, 426, 226), fill=oscurecer(base, 0.05) + (255,))

    # Monograma
    texto = monograma(nombre)
    f = fuente(96)
    caja_texto = d.textbbox((0, 0), texto, font=f)
    d.text(
        (256 - (caja_texto[2] - caja_texto[0]) / 2, 161 - (caja_texto[3] - caja_texto[1]) / 2),
        texto, font=f, fill=(255, 255, 255),
    )

    # Línea de «etiqueta»
    d.rounded_rectangle((128, 268, 384, 292), 12, fill=aclarar(base, 0.55))
    d.rounded_rectangle((128, 312, 320, 336), 12, fill=aclarar(base, 0.7))
    d.rounded_rectangle((128, 356, 352, 380), 12, fill=aclarar(base, 0.7))

    # Icono de etiqueta/precio (servicios) o de paquete (productos)
    if es_servicio:
        d.ellipse((330, 300, 386, 356), outline=oscurecer(base, 0.1), width=8)
        d.line((358, 310, 358, 346), fill=oscurecer(base, 0.1), width=8)
    else:
        d.rounded_rectangle((326, 306, 386, 366), 10, outline=oscurecer(base, 0.1), width=8)

    # Viñeta inferior
    return img.convert("RGB")


def main():
    os.makedirs(SALIDA, exist_ok=True)
    random.seed(7)
    for i, (nombre, categoria) in enumerate(PRODUCTOS, start=1):
        tarjeta(nombre, categoria, i, False).save(os.path.join(SALIDA, f"prod{i:02d}.jpg"), quality=88)
    for i, (nombre, categoria) in enumerate(SERVICIOS, start=1):
        tarjeta(nombre, categoria, i, True).save(os.path.join(SALIDA, f"serv{i:02d}.jpg"), quality=88)
    print(f"{len(PRODUCTOS)} productos y {len(SERVICIOS)} servicios en {SALIDA}")


if __name__ == "__main__":
    main()
