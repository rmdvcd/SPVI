# Pendiente de SPVI

## Alcance vigente

El producto mantenido en este repositorio es la app Android nativa (`:app`, `:designsystem`, `:data`, `:domain`, `:licencia`, `:core`). La app Flutter/iOS y la aplicación web de escritorio se retiraron el 2026-10-15; sus pruebas y pipelines ya no forman parte del proceso. La sincronización local entre teléfonos Android, las licencias GL y el formato `.spvi` siguen activos.

La versión del código es **0.30.0 (`versionCode 51`)**. Los cambios Android en curso aún no son un release.

## Verificación pendiente

- El último CI observado antes de esta limpieza, run [38802995010](https://github.com/rmdvcd/SPVI/actions/runs/38802995010) sobre `main` (`84afc87`), falló en dos pruebas de `ProcesoActualizarTest` al no poder descargar el SDK de Robolectric desde el proceso de tests. Lint, permisos y API mínima sí pasaron. Consulta el detalle y el estado de la corrección en [docs/VERIFICACION.md](docs/VERIFICACION.md).
- En esta rama se amplió la preparación del SDK Robolectric y el modo offline a todos los tests de `:app`. La corrección aún necesita una corrida de CI: no hay JDK/Android SDK local en este entorno para ejecutar Gradle.
- Ejecutar `./gradlew spviCheck` en un entorno con JDK 17 y Android SDK 35, y confirmar que las pruebas JVM, lint, compilación y permisos terminan correctamente.
- Ejecutar las capturas Roborazzi de `:app` y revisar los cambios de imágenes de referencia.

## Validación en dispositivos antes de publicar

- Correr los tests instrumentados de Room en teléfono o emulador API 26+.
- Comprobar en dos teléfonos Android la vinculación principal/secundaria, permisos, sincronización y desconexión/reconexión por la red local.
- Hacer una migración real con QR entre teléfonos y confirmar datos, licencia y vuelta a la app.
- Probar activación y revalidación con GL real, incluida una licencia envelope v1 heredada si está disponible.
- Crear e importar un respaldo `.spvi` con y sin contraseña; probar también lectura de un respaldo compatible anterior.
- Firmar el APK/AAB con el keystore de release, verificar firma y ejecutar la prueba de humo descrita en [RELEASE.md](RELEASE.md).

## Al cerrar

Actualizar [docs/VERIFICACION.md](docs/VERIFICACION.md) con el SHA, el resultado de CI, las pruebas de dispositivo realmente ejecutadas y cualquier limitación conocida. No declarar verde una comprobación que no se haya ejecutado.
