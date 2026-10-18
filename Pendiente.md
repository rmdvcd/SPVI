# Pendiente de SPVI

## Alcance vigente

El producto mantenido en este repositorio es la app Android nativa (`:app`, `:designsystem`, `:data`, `:domain`, `:licencia`, `:core`). La app Flutter/iOS y la aplicación web de escritorio se retiraron el 2026-10-15; sus pruebas y pipelines ya no forman parte del proceso. La sincronización local entre teléfonos Android, las licencias GL y el formato `.spvi` siguen activos.

La versión del código es **0.30.0 (`versionCode 51`)**. Los cambios Android en curso aún no son un release: el de editar importe, cantidad, método y nº en Registros se rotula **0.31.0** (commiteado como `54c85f9`).

## Verificación pendiente

- Cierre con billetes/conciliación (rama `arena/5c955caa-spvi`, ya en `main`): `13.json` está versionado desde `54c85f9`; en este entorno compila y sus tests JVM pasaron dentro de `spviTests` (891 en verde, 2026-10-17). Faltan por confirmar los instrumentados de migración 12→13 y probar en dos teléfonos actualizados la aprobación/rechazo, el conteo desfasado, la desconexión y el reenvío.

- El último CI observado antes de esta limpieza, run [38802995010](https://github.com/rmdvcd/SPVI/actions/runs/38802995010) sobre `main` (`84afc87`), falló en dos pruebas de `ProcesoActualizarTest` al no poder descargar el SDK de Robolectric desde el proceso de tests. Lint, permisos y API mínima sí pasaron. `app/build.gradle.kts` prepara ese SDK con Gradle y los tests JVM de `:app` corren en modo offline; el CI volvió a verde el 2026-10-18: run [39176254439](https://github.com/rmdvcd/SPVI/actions/runs/39176254439) sobre `6959eb1` (permisos, API mínima, lint, tests JVM y compilación de instrumentados, release R8 y APK debug). Los instrumentados en emulador no corren de forma ordinaria.
- `./gradlew spviCheck` ejecutado el 2026-10-17 con JDK 17 y Android SDK 35: en verde (tests JVM, lint, compilación y permisos). `./gradlew spviTests`: 891 tests JVM en verde. La corrida de CI de la rama ya se hizo (ver arriba) y `spviCheck` volvió a pasar el 2026-10-18, con **1057** tests JVM.
- Capturas Roborazzi ejecutadas el 2026-10-17: 525 capturas, **1 fallo de comparación** en `01f_inicio_dialogo_cerrar_turno.png`, que es del cambio de cierre con conteo ya en `main` (no regenerada aquí); ninguna captura de Registros cambia. Falta decidir si esa captura se regenera con el cambio de cierre. El 2026-10-18 `verifyRoborazziDebug` quedó en **527 capturas en verde** (incluye las 2 de Inventario del escáner).
- Escáner OCR de inventario (0.31.0, sin commitear): lógica pura, ViewModel, pantalla y documentación terminados; probado en el teléfono 4bc9f8f3 con tres PNG de tabla (productos 43 → 47, insumos → 50, y un párrafo sin tabla que quedó bloqueado sin guardar nada). Faltan las comprobaciones manuales con **foto impresa real**, lista manuscrita, permiso de cámara concedido/denegado, cancelación durante el reconocimiento y **modo avión** (detalle en [docs/OPENCODE_ESCANEO_OCR.md](docs/OPENCODE_ESCANEO_OCR.md)).
- Publicar los cambios en curso (editar Registros y escáner OCR, 0.31.0): correr CI, subir y regenerar capturas tras decidir lo anterior.

## Validación en dispositivos antes de publicar

- Instrumentados de Room: `:data:RepositoriosRoomTest` 9/9 y `:data:TurnoRepositoryInstrumentedTest` 8/8 en verde el 2026-10-17 en el teléfono 4bc9f8f3 (Android 15, API 35). Faltan los instrumentados de UI de `:app`.
- Comprobar en dos teléfonos Android la vinculación principal/secundaria, permisos, sincronización y desconexión/reconexión por la red local.
- Probar activación y revalidación con GL real, incluida una licencia envelope v1 heredada si está disponible.
- Crear e importar un respaldo `.spvi` con y sin contraseña; probar también lectura de un respaldo compatible anterior.
- Firmar el APK/AAB con el keystore de release, verificar firma y ejecutar la prueba de humo descrita en [RELEASE.md](RELEASE.md).

## Al cerrar

Actualizar [docs/VERIFICACION.md](docs/VERIFICACION.md) con el SHA, el resultado de CI, las pruebas de dispositivo realmente ejecutadas y cualquier limitación conocida. No declarar verde una comprobación que no se haya ejecutado.
