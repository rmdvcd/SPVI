# Capturas de pantalla (claro y oscuro)

Capturas **reales** de la interfaz de SPVI generadas en la JVM con **Robolectric 4.14.1 + Roborazzi 1.38.0**: sin emulador, sin teléfono y sin red. Cada caso pinta un `*Content` sin estado (los mismos que usan los tests instrumentados) con datos de ejemplo y guarda dos imágenes con el mismo nombre, una en tema claro y otra en oscuro.

| | |
|---|---|
| Código | `app/src/test/kotlin/cu/spvi/app/capturas/` (7 clases + `Capturas.kt`) |
| Casos | **147** → 294 PNG esperados (claro/oscuro). Hay 142 PNG por tema (284); faltan 5 casos por tema: dos nuevos de Ajustes y tres tablet. |
| Salida | `app/capturas/claro/ID.png` y `app/capturas/oscuro/ID.png` |
| Pantalla de referencia | Teléfono: 360 × 800 dp a xhdpi (720 × 1600 px). Largas: 360 × 1600 dp. Tableta: 800 × 1280 dp |
| Tema | `SpviTheme(darkTheme = false/true)`; se cambia sin recrear la actividad |

## 1. Generarlas

Desde la raíz del proyecto:

```bash
./gradlew :app:recordRoborazziDebug
```

- Android Studio: panel **Gradle → SPVI → app → Tasks → roborazzi → recordRoborazziDebug**.
- Las imágenes quedan en `app/capturas/{claro,oscuro}/`. Informe HTML: `app/build/reports/roborazzi/index.html`.
- Solo una clase: `./gradlew :app:recordRoborazziDebug --tests "cu.spvi.app.capturas.VentaCapturas"`.
- Solo un caso: `--tests "cu.spvi.app.capturas.VentaCapturas.qr"`.

La **primera** ejecución descarga con Gradle las dependencias de test (Robolectric, Roborazzi y el Android 15 preinstrumentado de Robolectric, ~150 MB). Después todo funciona sin conexión.

### Comparar contra las guardadas

Con las imágenes ya grabadas, para detectar cambios visuales:

```bash
./gradlew :app:compareRoborazziDebug   # genera *_compare.png con las diferencias marcadas
./gradlew :app:verifyRoborazziDebug    # falla si alguna pantalla cambió
```

## 2. Cómo está montado

| Pieza | Dónde | Por qué |
|---|---|---|
| Plugin `io.github.takahirom.roborazzi` 1.38.0 | `build.gradle.kts` (apply false) y `app/build.gradle.kts` | Añade las tareas `record/compare/verifyRoborazziDebug`. No toca el APK. |
| `testImplementation`: robolectric, roborazzi, roborazzi-compose, ui-test-junit4, androidx.test.ext.junit | `app/build.gradle.kts` | Solo tests: **no entran en el APK** (restricción P18 «sin dependencias nuevas sin justificar»: son de test y la justificación es esta tarea). |
| `unitTests.isIncludeAndroidResources = true` | `app/build.gradle.kts` | Robolectric necesita los recursos (logo, iconos vectoriales). No afecta a los tests JVM existentes. |
| Configuración `robolectricSdk` + tarea `prepararRobolectricSdk` | `app/build.gradle.kts` | Gradle descarga `android-all-instrumented:15-robolectric-12650502-i7` (SDK 35) y Robolectric se ejecuta con `robolectric.offline=true`. Así se mantiene la regla del Prompt 15: **los tests no salen a internet** (el proxy inexistente de los tests sigue activo). |
| Exclusión `**/capturas/**` | `app/build.gradle.kts` | En `test`, `testDebugUnitTest`, `spviTests` y `spviCheck` las capturas **no se ejecutan** (son lentas y no validan lógica). Solo corren cuando el nombre de la tarea pedida contiene «Roborazzi». |
| `@Config(application = Application::class)` | cada clase | No arranca `SpviApplication` (Hilt, SQLCipher, Keystore): las pantallas se pintan sin datos reales ni claves. |
| `captureScreenRoboImage` | `Capturas.kt` | Captura toda la pantalla, así los diálogos y hojas (que viven en su propia ventana) también salen. |

## 3. Qué se captura

Los ID coinciden con los de las maquetas HTML del Prompt 23 cuando representan la misma pantalla y el mismo estado (133 de 155 en la 0.16.1; las maquetas HTML son anteriores al Prompt 24 y no reflejan sus cambios visuales; `03l` y `03m` de las maquetas muestran Producir, retirado en 0.16.0), para poder ponerlas lado a lado.

| Clase | Casos | Contenido |
|---|---|---|
| `InicioCapturas` | 13 | Inicio con datos (pantalla y captura larga), vacío, cargando y errores con banners, diálogos y snackbar; gráficos de barras/área/donut y Top 3. |
| `InventarioCapturas` | 24 | Lista con niveles, estados vacíos/error, filtro, selección, eliminar, fichas normal/Elaborado y **detalle lateral en tableta**; hojas Exportar/Compartir, modo venta y snackbar. Formularios de producto, insumos y descarte. |
| `ServiciosCapturas` | 6 | Servicios: lista, vacío, modo venta, ficha con insumos, **detalle lateral en tableta** y formulario. |
| `RegistrosCapturas` | 26 | Tablas de Ventas, Transferencias, Movimientos y Turnos; estados vacíos/error; fichas, incluyendo **detalle lateral en tableta**; filtros, exportación y detalle de turno. |
| `VentaCapturas` | 24 | Venta sin turno, carritos, errores de stock, comprobantes/QR, datos del cliente; hojas de pago electrónico y Precios. |
| `AjustesCapturas` | 50 | Ajustes, Perfil, Licencia, Respaldo (incluida exportación explícita sin contraseña con advertencia), Migrar, Ayuda, Soporte y asistente de configuración. |
| `VinculacionCapturas` | 4 | Vinculación y pantallas de QR; la cámara real se valida en dispositivo, no en Robolectric. |

### Tablet (T4.2)

`Captura.TABLETA` usa 800 × 1280 dp; con `WindowSizeClass.Medium` debe mantener la lista junto al detalle seleccionado en:

- `02q_inventario_tableta_detalle`
- `04u_registros_tableta_detalle`
- `09f_servicios_tableta_detalle`

Las tres capturas claro/oscuro están pendientes de generar y revisar con OpenCode CLI en el PC del propietario.

### Letra grande (0.27.0, T2)

Cuatro casos con `LetraGrande` (`Capturas.kt`: `fontScale = 2`, el máximo de Android 14+) para comprobar que nada se corta con «…» ni parte letras:

| ID | Clase | Pantalla |
|---|---|---|
| `01z_inicio_letra_200` | `InicioCapturas` | Inicio con datos (captura larga) |
| `02z_inventario_ficha_letra_200` | `InventarioCapturas` | Ficha de producto |
| `07z_licencia_letra_200` | `AjustesCapturas` | Licencia (prueba) |
| `07z2_caja_arqueo_letra_200` | `AjustesCapturas` | Caja / arqueo al cerrar |

Se usa un `CompositionLocalProvider(LocalDensity)` en vez de `@Config(fontScale)` porque no está claro que Robolectric aplique este último a Compose.

**Generadas de verdad en la 0.27.1 (P79):** `./gradlew :app:recordRoborazziDebug` escribe 154 PNG por tema en `app/capturas/{claro,oscuro}`. Al revisarlas se corrigieron cortes reales con letra al 200 %:
- alertas de Inicio: el número por fila ahora se adapta a la letra;
- tarjetas Pago/Precios: se apilan cuando no caben;
- importes del arqueo: el importe baja de línea en vez de partirse;
- subtítulos de Ajustes y Exportar: hasta 3 líneas.

Límites del arnés:
- **Ventanas `Dialog`:** `LetraGrande` no llega a ellas, porque el `Dialog` crea su propia ventana con la densidad del sistema. Por eso `02z_inventario_ficha_letra_200` sale al 100 %. Los diálogos con letra grande solo se pueden comprobar en un teléfono (Ajustes → Pantalla → Tamaño de fuente al máximo).
- **Barra superior de la actividad de prueba:** la actividad de prueba hereda `Theme.SPVI.Splash`, que en Robolectric muestra una barra. `capturar()` la oculta para que no tape la parte de arriba de las capturas. En la app no aparece, porque `installSplashScreen()` pasa a `Theme.SPVI`.

## 4. Límites conocidos

- **Vista de cámara para leer un QR**: CameraX no funciona en Robolectric; se comprueba en un teléfono real siguiendo [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md).
- **Barra de navegación inferior**: la pinta `MainScaffold`, no los `*Content`; en las capturas reales de Inicio, Inventario, Servicios, Registros y Ajustes no aparece.
- **Estados que dependen de escribir o de un selector del sistema** (filtro con importes inválidos, selector de fecha abierto, activación rechazada, menú desplegado del FAB): solo como maqueta.
- **Fuentes**: Robolectric usa las fuentes de su Android 15; puede haber diferencias mínimas de interletra respecto a un teléfono.
- Para esta revisión hay 142 PNG por tema; faltan cinco baselines por tema (dos casos de Ajustes y las tres capturas tablet). El agente no ejecuta Gradle ni Roborazzi: el propietario los generará con OpenCode CLI en su PC y revisará las imágenes. Ver [Pendiente.md](Pendiente.md).

## Maquetas PDF (P44)

`tools/capturas/` genera las maquetas HTML de Apps vinculadas (0.19.x) con los iconos de `SpviIcons.kt` (`iconos.json`), los colores de `ColorTokens.kt`, el logo y la fuente de marca del módulo `designsystem`, y los textos de `VinculacionLogic`, `BloqueoSecundaria`, `AjustesScreen` y `PlanAviso`. Son maquetas, no capturas: si cambian esos textos o la disposición, hay que actualizar `generar.py` a mano.

```bash
pip install qrcode pymupdf
npm i playwright && npx playwright install chromium   # y fonts-roboto en el sistema
python3 tools/capturas/generar.py /tmp/caps 25         # 80 páginas, numeradas desde el 25
node tools/capturas/render.js /tmp/caps                # pNN.pdf (842.88 × 595.92 pt)
```

La portada está en `tools/capturas/portada.html`. El PDF archivado local `SPVI_0.26.0_capturas_y_exportaciones.pdf` (127 páginas, excluido de Git) contiene pantallas nº 1–108 y exportaciones E1–E16; es histórico, no una guía del comportamiento actual. Nº 1–24 son de la 0.18.4 (paleta anterior); nº 25–67, de la 0.19 a la 0.23.1 (apps vinculadas, cierre pedido, recorrido inicial y licencia por texto); nº 68–69, Descripción (0.24.0); nº 70–88, 0.25.0 (arqueo, anular, respaldo, actualizaciones, recuperación); nº 89–96, 0.25.1 (compartir turno, modificar venta completa, recuperación con licencia vencida, «Ahora no» en el conteo, destinatario de cada exportación). Nº 97–108, 0.26.0 (respaldo solo .spvi, «+» en la esquina, seminegrita, fondo asignado por el encargado, actualización obligatoria y permiso de fotos para que la prueba no se reinicie al reinstalar). Las exportaciones salen de `tools/exportaciones/generar.sh` (TablasExport y XlsxWriter reales; el PDF y los PNG replican PdfWriter/DisenoPdf, ImagenTabla y Tarjetas) y `paginas.py`; el PDF se arma con `tools/capturas/ensamblar.py`. El QR de la página de vinculación lleva un texto de ejemplo y no vincula nada.
