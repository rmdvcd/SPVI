# Material de promoción

Collages con capturas reales de la app en el teléfono `4bc9f8f3`, con los datos de demostración
del sembrador de depuración (18 meses de ventas, 54 artículos de inventario, 20 servicios y
8 apps secundarias).

| Archivo | Uso |
|---|---|
| `collage_feed_1080x1350.png` | Publicación de Facebook/Instagram (4:5) |
| `collage_historia_1080x1920.png` | Historia de Instagram/Facebook (9:16) |
| `capturas/` | Pantallas originales (1080×2400, tema claro y oscuro) |

Las capturas son de la versión de depuración (`cu.shalev.app.debug`); la app publicada es la misma
interfaz con el paquete `cu.shalev.app`.

## Cómo se regeneran

```powershell
# 1. Sembrar los datos en el teléfono (APK de depuración instalada)
adb shell am start -n cu.shalev.app.debug/cu.shalev.app.seed.SeedActivity

# 2. Capturas nuevas: guardarlas en app_usb_check/shot_*.png (1080x2400)
# 3. Montar los collages
python tools/seed_demo/collage.py
```

El sembrador vive en `data/src/debug` (no entra en el APK de release) y las fotos de los artículos
están en `tools/seed_demo/seed_fotos` (créditos en `tools/seed_demo/CREDITOS.md`).
