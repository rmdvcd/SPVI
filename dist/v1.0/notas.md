# Shalev v1.0

Punto de venta e inventario para pequeños negocios. Funciona sin internet.

Primera versión pública.

## Qué corrige

- **Activación de licencias arreglada.** El build fija las claves públicas del emisor GL vigente (ECDH `sha256:fb3a:…:078d` y firma `sha256:113f:…:4b1c`). Hasta ahora se cifraba la solicitud con una clave que el emisor ya no tenía y GL respondía «solicitud dañada o no es para GL», así que no se podía activar ninguna licencia. Las cuatro firmas históricas se conservan para seguir verificando las licencias ya emitidas.

## Instalación

1. Descarga `Shalev-1.0.apk` (universal: arm64-v8a, armeabi-v7a y x86_64).
2. Abre el archivo y acepta instalar desde esta fuente si el teléfono lo pide.
3. Al arrancar, la app pide cámara, aviso de turno y fotos; después empieza el periodo de prueba de 7 días.

## Datos del artefacto

- Paquete: `cu.shalev.app`
- Versión: `1.0` (`versionCode 53`)
- Requiere Android 8.0 (API 26) o superior
- Firma: SHA-256 del certificado `139d3da93bd3abb881a2c55503dae0b059ab4c36a68ad16394a8b526ffd41738` (la misma de los builds de release anteriores)
- SHA-256 del APK: `2ec85c932dbf5c3dd0c47c056242bdca276d1364c1f0890334ac4562eb3297cd` (también en `Shalev-1.0.apk.sha256`)
