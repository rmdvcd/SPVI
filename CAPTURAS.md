# Capturas de interfaz (claro y oscuro)

La suite de capturas pinta componentes `*Content` de la interfaz con **Robolectric 4.14.1 + Roborazzi 1.38.0**. Se ejecuta en la JVM, sin emulador, teléfono ni acceso de red durante la captura. Cada caso usa datos de ejemplo y produce una imagen en tema claro y otra en oscuro.

| Elemento | Estado en el código actual |
|---|---|
| Pruebas | `app/src/test/kotlin/cu/spvi/app/capturas/` — 7 clases de captura y el helper `Capturas.kt` |
| Casos | 145 métodos `@Test`; 290 PNG esperados (145 claro + 145 oscuro) |
| Salida | `app/capturas/claro/ID.png` y `app/capturas/oscuro/ID.png` |
| Pantalla de referencia | 360 × 800 dp, xhdpi (720 × 1600 px). Formularios largos: 360 × 1600 dp |
| Tema | `SpviTheme(darkTheme = false/true)`; se cambia sin recrear la actividad |

**Estado de las imágenes guardadas:** el árbol contiene 284 PNG versionados (142 por tema), pero no se compararon con el código actual ni se regeneraron durante esta revisión: Java no está instalado en el entorno. Por tanto, las tres capturas nuevas (incluidas las variantes de biometría) y cualquier diferencia visual de los cambios recientes siguen pendientes; no presentes las imágenes actuales como verificación del estado nuevo. El estado de compilación está en [docs/VERIFICACION.md](docs/VERIFICACION.md).

## 1. Generar y comparar

Desde la raíz del proyecto:

```bash
./gradlew :app:recordRoborazziDebug
```

Las imágenes se escriben en `app/capturas/{claro,oscuro}/`. La tarea genera también el informe en `app/build/reports/roborazzi/index.html`. La primera ejecución necesita que Gradle resuelva las dependencias de test y el Android 15 preinstrumentado de Robolectric; después el código de captura se ejecuta en modo offline.

Para grabar una clase concreta:

```bash
./gradlew :app:recordRoborazziDebug --tests "cu.spvi.app.capturas.VentaCapturas"
```

Para comparar con las imágenes versionadas o hacer que la tarea falle ante cambios:

```bash
./gradlew :app:compareRoborazziDebug
./gradlew :app:verifyRoborazziDebug
```

`spviTests` y `spviCheck` excluyen `**/capturas/**` porque estas pruebas son lentas y verifican presentación, no lógica. Hay que ejecutar la tarea Roborazzi explícitamente y revisar los cambios en claro y oscuro antes de actualizar las imágenes.

## 2. Cómo está montado

| Pieza | Ubicación / función |
|---|---|
| Plugin Roborazzi 1.38.0 | `build.gradle.kts` y `app/build.gradle.kts`; solo tareas de prueba, no entra en el APK |
| Dependencias Robolectric/Compose UI Test | `testImplementation` en `app/build.gradle.kts` |
| SDK de Robolectric | Configuración `robolectricSdk` y tarea `prepararRobolectricSdk`; usa Android 15 (API 35) empaquetado por Robolectric |
| Red | `robolectric.offline=true`; la captura no consulta servicios remotos |
| Exclusión de tests | En tareas JVM normales, incluidas `spviTests` y `spviCheck`, no se ejecutan capturas |
| Aplicación de prueba | `@Config(application = Application::class)` evita arrancar `SpviApplication`, Hilt, SQLCipher y Keystore |
| Captura | `captureScreenRoboImage` captura toda la pantalla, incluidos diálogos Compose y hojas |

## 3. Cobertura actual

| Clase | Casos | Contenido |
|---|---:|---|
| `InicioCapturas` | 13 | Inicio con datos, vacío, cargando, errores, alertas, turno y diálogos de venta/cierre; incluye letra grande |
| `InventarioCapturas` | 23 | Lista, filtros, selección, ficha de producto y Elaborado, exportar/compartir, modo venta y formulario/receta. **No hay capturas de escáner de productos:** se retiró en 0.30.0 |
| `RegistrosCapturas` | 25 | Tablas y fichas de ventas, transferencias, movimientos y turnos; filtros, exportación y variantes de detalle |
| `VentaCapturas` | 24 | Carrito, efectivo/transferencia, QR de pago, datos de cliente, Pago electrónico y Precios |
| `ServiciosCapturas` | 5 | Lista, vacío, modo venta, ficha y formulario de servicio |
| `VinculacionCapturas` | 4 | Principal con empleados, ficha y cierre pedido, y secundaria conectada |
| `AjustesCapturas` | 51 | Ajustes, Perfil, Licencia, Respaldo (incluye advertencia y confirmación sin contraseña, con/sin biometría), Migrar, Ayuda, Soporte y asistente inicial |

Cuatro casos verifican vistas persistentes con letra al 200 % (`fontScale = 2`): Inicio, ficha de Inventario, Licencia y arqueo. Se usa `CompositionLocalProvider(LocalDensity)` porque `@Config(fontScale)` no se aplica de forma fiable a Compose en Robolectric.

## 4. Límites conocidos

- **Cámara y selectores del sistema:** Robolectric no ejecuta la vista real de CameraX, la cámara para fotos, el lector de QR ni las ventanas de autenticación biométrica del sistema. Se comprueba su flujo en [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md).
- **Barra inferior:** la pinta `MainScaffold`, que no forma parte de los `*Content` capturados.
- **Diálogos del sistema:** los selectores de SAF, permisos y `BiometricPrompt` no son capturas Robolectric. Las ventanas `Dialog` tampoco reciben el `fontScale` de `LetraGrande`; hay que comprobar letra grande en un teléfono.
- **Interacción real:** la mayoría de los estados se entregan directamente como modelos UI de ejemplo. La suite no demuestra que la escritura, el teclado, el guardado o el hardware funcionen de extremo a extremo.
- **Barra superior de la actividad de prueba:** la actividad puede heredar `Theme.SPVI.Splash`; `capturar()` la oculta para evitar que tape la imagen. La actividad normal instala el tema de aplicación.

## 5. Maquetas HTML/PDF históricas

`tools/capturas/` contiene un generador de maquetas HTML y scripts para montarlas en PDF. Son ilustraciones históricas (varias describen versiones anteriores, incluido el escáner que se retiró); **no son capturas Robolectric ni una referencia fiable para el diseño actual**. El PDF `SPVI_0.26.0_capturas_y_exportaciones.pdf` no está en el árbol actual. No lo cites como entrega vigente.

Las maquetas todavía pueden regenerarse manualmente si se necesita conservar esa referencia histórica:

```bash
pip install qrcode pymupdf
npm i playwright && npx playwright install chromium
python3 tools/capturas/generar.py /tmp/caps 25
node tools/capturas/render.js /tmp/caps
```

Para actualizar la interfaz vigente, usa las capturas Roborazzi de la sección 1 y comprueba los cambios en dispositivo cuando afecten a cámara, accesibilidad o componentes del sistema.
