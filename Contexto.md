# Contexto de SPVI para la IA de OpenCode

Este documento resume todo lo que necesitas para trabajar en SPVI sin leer el historial. Las reglas obligatorias están en `AGENTS.md`. Lo que falta por hacer está en `Pendiente.md` y cómo probarlo, en `Pruebas.md`. El PDF `SPVI_0.26.0_capturas_y_exportaciones.pdf` muestra cómo debe verse cada pantalla y cada exportación de la 0.26.0; lo nuevo de la 0.27.0 está en `README.md` (Novedades) y `docs/PROMPT_0.27.0.md`.

> **Idioma:** responde siempre en español. Todo el texto de la interfaz, los comentarios y la documentación también están en español.

---

## 1. Qué es SPVI

SPVI (Sistema de Punto de Venta e Inventario) es una app Android nativa para pequeños negocios de Cuba.

| | |
|---|---|
| Versión | **0.30.0** (`versionCode 51`) |
| Paquete | `cu.spvi.app` (debug: `cu.spvi.app.debug`) |
| SDK | minSdk 26 · targetSdk 35 · compileSdk 35 |
| Base de datos | Room **v10** cifrada con SQLCipher |
| Respaldo | `.spvi` v4 cifrado, contraseña opcional (lee también v3); `RespaldoDto` v4 (importa también v3) |
| Protocolo de la red local | `VERSION_PROTOCOLO = 1` (los campos nuevos son opcionales) |
| Licencias | Offline, emitidas por la app GL del desarrollador (contrato GL v1) |

- **Funciona sin conexión.** Todos los datos van cifrados en el teléfono.
- **Sin servidor propio, sin cuentas, sin nube.** Sin Firebase, analítica, telemetría ni anuncios.
- **Prueba gratis de 7 días.** Después hace falta una licencia que vende el desarrollador.

### Funciones

| Área | Qué hace |
|---|---|
| **Inicio** | Banner de licencia, turno (abrir/cerrar), **Nueva venta** y **Escanear** centrados (solo icono, con tooltip), alertas (stock e insumos bajos o críticos, caducidad), gráficos sin rejilla y top 3. En la principal: avisos de solicitudes de cierre, de fondo pedido y de «Actualiza la app de X» |
| **Venta** | Solo con turno abierto. **Efectivo**, o **Transferencia** con QR de Transfermóvil (el total se muestra debajo) y nº de transacción; el SMS de PAGOxMOVIL se puede pegar/compartir o capturar opcionalmente desde una notificación con permiso manual. **Cliente fijo** (0.27.0): se guarda por carné y se sugiere al escribir el nombre (hasta 3). Un Elaborado descuenta sus insumos y muestra «Alcanza para N» |
| **Caja (arqueo)** | Fondo obligatorio al abrir, entradas y salidas con motivo, esperado = fondo + efectivo + entradas − salidas. Al cerrar se cuenta, con «Cuadra» en un toque |
| **Inventario** | Productos, Elaborados e Insumos. Precio opcional en los insumos, en unidades enteras. Buscador, filtros, escáner (CameraX + ML Kit) y exportar a PDF, Excel, Imagen y Tarjetas |
| **Servicios** | Tipo libre, importe, foto y descripción. Pueden gastar insumos y tienen su pestaña en Registros |
| **Registros** | Ventas, Servicios, Transferencias, Movimientos, **Clientes** (0.27.0) y Turnos, ordenados por fecha, con filtros. **Anular o modificar** una venta: solo la principal y solo con el turno abierto |
| **Ajustes** | Perfil (nombre, apellidos y carné), Licencia, **Acceso con clave** (0.27.0, opcional: huella o PIN del teléfono al abrir y tras ≥ 10 min fuera), Pago electrónico (teléfonos y tarjetas), Precios, Avisos, Permisos, Respaldo, Migrar a otro teléfono (4 pasos), Actualizaciones, Ayuda y Soporte |
| **Apps vinculadas** | Una app **principal** (dueño) y hasta 10 **secundarias** (empleados) en la misma wifi, sin internet ni servidor. Cada empleado tiene sus permisos. La secundaria no crea secundarias y **una principal nunca pasa a secundaria** (0.27.0): el tipo se elige en el recorrido inicial; para cambiarlo hay que exportar el respaldo y borrar los datos de SPVI. Una secundaria activa el acceso con clave solo desde Ajustes |

### Exportaciones (desde la 0.26.0, sin texto)

- **PDF y Excel** de Registros, Inventario, Servicios y Turno.
- **Imagen de precios** y **Tarjetas** de productos.
- **Formato:** importes, cantidades y fechas van en **seminegrita** en el PDF y en **negrita** en Excel. Los códigos de barras y teléfonos (8 o más cifras) no se destacan.
- **PDF:** en horizontal cuando la tabla tiene más de 5 columnas.
- **Excel:** cada columna mide lo que su contenido (4–80 unidades) y los importes llevan el formato `#,##0.00 \C\U\P`.
- **Única salida como mensaje de texto:** la solicitud de licencia (y el contacto de Soporte).

Formato exacto de cada archivo: `FORMATOS.md`.

---

## 2. Arquitectura

**Pila:** Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.11.1 (wrapper) · JDK 17 · Compose BOM 2024.12.01 (Material 3) · Hilt 2.52 (KSP) · Room 2.6.1 + SQLCipher 4.6.1 · DataStore · OkHttp 4.12 + kotlinx.serialization · fastexcel · CameraX + ML Kit · ZXing · Coil · Robolectric 4.14.1 + Roborazzi 1.38.0.

| Módulo | Contenido | Depende de |
|---|---|---|
| `:core` | Utilidades puras: dinero `Cup` en centavos, fechas, resultados | — |
| `:licencia` | Cliente de licencias GL (`LicenseManager`, formatos `SPVIR1:`/`SPVI2:`, revocaciones) y `prueba/RegistroPrueba.kt` | core |
| `:domain` | Modelos, interfaces de repositorio, casos de uso, `TablasExport` | core, licencia |
| `:data` | Room, SQLCipher, DataStore cifrado, respaldo, sincronización LAN (`ServidorSync`, `ClienteSync`), exportadores (`PdfWriter`, `DisenoPdf`, `EscritoresJvm`/XlsxWriter), implementaciones de repositorios | domain |
| `:designsystem` | Tema, componentes `Spvi*`, `SpviIcons`, `SpviSpacing`, `SpviTextos` | — |
| `:app` | Pantallas Compose, ViewModels Hilt, navegación type-safe y `root/` (puertas de licencia, bloqueo y onboarding) | todos |

**Reglas de diseño:**
- Clean Architecture: nada de lógica de negocio en las pantallas. Reutiliza los repositorios y casos de uso que ya existen.
- UI solo con componentes `Spvi*` y tokens, sin estilos sueltos ni `fontWeight` a mano.
- `SpviSpacing`: xs=8, md=16, lg=24, xl=32. **No existe `sm`**.

---

## 3. Piezas clave y dónde están

### Licencia (`:licencia`)

- **`LicenseManager.evaluate()`** decide el estado: `Trial(n)`, `TrialExpired`, `Active`, `Perpetual`, `Expired`, `Revoked` o `ClockTampered`.
  - Si el reloj retrocede más de 2 h respecto a la última fecha vista (`lastSeen`), el estado es `ClockTampered`.
- **Solicitud:** cifrada, en formato `SPVIR1:` (ECIES, HKDF «SPVI-R1»). Se envía por WhatsApp (`wa.me/5351815604`) o por SMS: un texto de introducción, una línea en blanco y la parte cifrada.
- **Licencia:** el formato corto `SPVI2:` (210 caracteres, firmado). Es el único activable. En la app, «Activar» = **Pegar + Activar**. **Sin QR.**
- **Recuperación:** en un teléfono nuevo, con el «ID de la licencia anterior». El teléfono viejo queda en la lista de revocadas y se desinstala solo.
  - La lista de revocadas es `revocadas.json`, firmada, en la Release `revocaciones` de GitHub.
- **Precios (CUP):**
  - base 6000 / 30000 / 50000 / 90000 (mensual, semestral, anual, perpetua);
  - más 1000 / 5000 / 9000 / 17000 por cada secundaria, de 0 a 10;
  - la prueba cubre 5 secundarias.
- **Claves de GL (0.27.0):** par del 05/10/2026 en `LicenseTrust` (se conserva la firma anterior); contexto actual de GL en `docs/GL_CONTEXTO_LICENCIAS.md`.
- **Contrato:** `LICENSE_CLIENT.md`, `docs/GL_ESPECIFICACION.md` y `docs/GL_PROMPT_0.25.md`. **No copies el almacenamiento de GL ni expongas detalles internos.**

### Prueba que no se reinicia al reinstalar (0.26.0, P74)

- **Dónde se guarda:** la fecha de inicio de la prueba va cifrada (AES-GCM, clave PBKDF2 a partir de ANDROID_ID) en 4 copias:
  - `filesDir`;
  - `Pictures/SPVI/sys_<huella>.png`: la vía principal en Android 10+, se lee tras reinstalar con el permiso de fotos;
  - `.sys_<huella>.bin` en Download y en Documents: tras reinstalar solo se leen en Android 8–9.
- **Qué fecha vale:** gana el inicio más antiguo y la última fecha vista (`lastSeen`) más reciente. Una copia ilegible se ignora (**nunca bloquea**).
- **Reloj:** `DetectorRelojAndroid` usa `elapsedRealtime` dentro del mismo arranque, además de la comprobación de `lastSeen`.
- **Permiso de fotos:** `PermisoRegistroPrueba` pide el acceso una sola vez, recién instalada. Si se niega, la app sigue funcionando.
- **Código:**
  - `licencia/.../prueba/RegistroPrueba.kt`;
  - `data/.../licencia/prueba/RegistroPruebaAndroid.kt`;
  - `domain/.../model/EstadoPrueba.kt`;
  - `app/.../licencia/prueba/TrialViewModel.kt`.
- **Diseño y decisiones del dueño:** `docs/PLAN_ANTIREINSTALACION.md`.

### Apps vinculadas (red local)

- **Vinculación:** con un QR. La principal ofrece un servicio en primer plano (`connectedDevice`) que va en la misma notificación del turno abierto. La notificación no muestra importes y no se restaura tras reiniciar el teléfono.
- **Licencia:** la de la principal cubre a las secundarias. Si no está activa, la secundaria muestra `BloqueoSecundaria`.
- **Cierre de turno:** el empleado solo **solicita** el cierre y el dueño lo aprueba. Nunca se cierra solo ni durante una venta.
- **Fondo de caja (0.26.0):** lo **asigna la principal** para cada turno de una secundaria. Sin fondo asignado, la secundaria no puede abrir turno: toca «Pedir fondo».
  - Una secundaria 0.25.x abre turno como antes, y la principal muestra «Actualiza la app de X».
- **Sin conexión:** la secundaria sigue vendiendo, pero necesita sincronizar para abrir un turno nuevo.
- **Actualizaciones:** la principal reparte el APK a las secundarias por la red local.
- **Detalle:** `docs/VINCULACION.md`.

### Actualizaciones (GitHub)

- **Consulta:** una vez por semana, al abrir la app, la principal mira la última Release pública de `GITHUB_REPO` (`-PspviGithubRepo=usuario/repo`; vacío = no consulta nada).
- **Descarga:** se comprueba el SHA-256 y se instala con `REQUEST_INSTALL_PACKAGES`.
- **Obligatorias (0.26.0):** toda versión nueva es obligatoria 30 días después de que el teléfono la detecte.
  - Al vencer, `BloqueoActualizacion` bloquea toda la app. Solo deja Actualizar, Exportar respaldo y Cerrar turno.
  - Solo bloquea si de verdad existe una actualización.
- **Código:** `app/.../actualizacion/GestorActualizacion.kt`, `InstaladorApk.kt`, `domain/.../model/EstadoApp.kt`.

### Respaldo y migración

- **Respaldo:** solo el archivo `.spvi`, siempre cifrado; desde la 0.27.0 la contraseña es opcional y viene apagada (sin ella, cualquiera con SPVI lo abre). Lleva los clientes fijos (sin PDF). Un recordatorio mensual, solo en la principal.
- **Migrar a otro teléfono:** asistente de 4 pasos.

---

## 4. Reglas del dueño que no se negocian (resumen; la lista completa está en `AGENTS.md`)

1. **Permisos.** Solo estos; `spviPermisos` falla con cualquier otro:
   - `CAMERA`, `INTERNET` y `POST_NOTIFICATIONS`;
   - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` y `CHANGE_NETWORK_STATE`;
   - `REQUEST_INSTALL_PACKAGES` y `REQUEST_DELETE_PACKAGES`;
   - `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` (maxSdk 32) y `WRITE_EXTERNAL_STORAGE` (maxSdk 28), solo para el registro de la prueba;
   - `USE_BIOMETRIC` y `USE_FINGERPRINT` (0.27.0), solo para el acceso con clave.
2. **Internet** solo para la red local y GitHub Releases (`rmdvcd/SPVI`, actualizaciones y revocaciones); no se requieren claves de API.
3. **Cámara y SMS.** No se vende sin turno abierto. La cámara solo mientras se usa. No se pide `READ_SMS` ni se consulta el buzón; la captura opcional usa Acceso a notificaciones, que el usuario habilita manualmente, y solo procesa un SMS nuevo reconocible mientras una transferencia espera el pago. El portapapeles solo se lee al tocar «Pegar SMS»; compartir el mensaje sigue disponible.
4. **UI:**
   - Material 3 claro y oscuro, con contraste WCAG AA (`ContrastTest`).
   - `FLAG_SECURE` en las pantallas sensibles.
   - Botones de solo icono con descripción y tooltip; Guardar = ✓; diálogos centrados; pestañas como viñetas.
   - El botón «+» va **abajo a la derecha**.
   - Tipografía: bodyMedium 14 y bodySmall 13. Sin «Deshacer».
5. **Datos sensibles:** nada en los logs ni en la vista de Recientes.
6. **Documentación sin funciones inventadas.** Si cambias el comportamiento, actualiza README, MANUAL_USUARIO y FORMATOS, y añade una entrada en `docs/HISTORIAL_DESARROLLO.md`.
7. **Tests:** sin red. Todo lo que añadas debe compilar y pasar `./gradlew spviTests`.
8. **Firma:** siempre el mismo keystore. El `deviceId` de la licencia depende de la firma.
9. **Cambios mínimos.** No refactorices lo que funciona. Ante una ambigüedad, elige la opción más segura y simple, y documéntala.

---

## 5. Estado de la verificación

- **`tools/verificacion/verificar.sh`** (sin Gradle ni Android SDK; compila con kotlinc, KSP de Room y Hilt, y Robolectric): **«Todo correcto»**.
  - **850 tests JVM** (0.27.1, Gradle real): core 19, licencia 99, domain 242, data 96, designsystem 27, app 367.
  - **41 tests de Room** en la JVM, con SQLite real.
  - El esquema `10.json` coincide con `MIGRACION_9_10`.
  - Los permisos del manifiesto de `:app` están dentro de la lista autorizada (`USE_BIOMETRIC`/`USE_FINGERPRINT` llegan al fusionar `androidx.biometric`: solo los ve `spviPermisos` con Gradle).
- **Lo que nunca se ha ejecutado:**
  - Gradle real, lint y R8 (release);
  - las capturas Roborazzi;
  - el APK en un teléfono y la prueba con dos teléfonos;
  - `BiometricPrompt`, la cámara con `FileProvider` y las animaciones de ventanas (0.27.0);
  - MediaStore (registro de la prueba), la instalación de actualizaciones y la descarga desde GitHub.

  Eso es lo que toca ahora; ver `Pruebas.md`.

---

## 6. Comandos

```bash
./gradlew spviTests                 # todos los tests JVM (sin dispositivo ni red)
./gradlew :app:assembleDebug        # APK debug → app/build/outputs/apk/debug/
./gradlew spviCheck                 # spviTests + lintDebug + assembleDebug + spviPermisos
./gradlew :app:installDebug         # instalar en el teléfono conectado
./gradlew spviInstrumentedTests     # tests de Room en el teléfono o emulador
./gradlew :app:recordRoborazziDebug # capturas reales claro/oscuro
./gradlew spviRelease               # AAB + APK firmados (requiere keystore.properties)
./gradlew :domain:test --tests "cu.spvi.domain.EstadoPruebaTest"   # un solo test
```

Comandos de OpenCode ya preparados en `.opencode/commands/`: `/compilar`, `/probar`, `/capturas`, `/release` y `/retoque`.

---

## 7. Trampas conocidas (te ahorrarán tiempo)

**Hilt**
- Los constructores `@Inject` no admiten parámetros por defecto.
- Si cambias el constructor de un ViewModel, actualiza `TurnoViewModelsTest`, `PerfilTest` y `app/src/sharedTest/.../EntornoIntegracion.kt`.

**Kotlin**
- Una entrada de `enum` no puede usar una `const` de su propio companion.
- No se puede leer `CompositionLocal.current` dentro del DSL de `LazyColumn`.
- `spviAnimateItem()` solo funciona en `LazyItemScope`.
- No hay smart cast de propiedades de otro módulo: copia el valor a un `val` local.
- No uses `fun interface` con métodos `suspend`: usa una interface normal.

**KSP**
- Si Room o Hilt fallan de forma rara, borra `build/generated/ksp` y repite.

**Datos**
- La categoría de los elaborados es `Categorias.ELABORADO = "Elaborado"`, en singular.
- Un cambio de esquema de Room exige migración, esquema exportado y test.

**fastexcel**
- Todo el estilo de una celda va en un solo setter.
- El formato CUP va escapado (`\C\U\P`): con comillas, el `styles.xml` queda inválido.

**R8**
- Los `@Serializable` del contrato GL, de las rutas y de `licencia.prueba.RegistroPrueba` tienen reglas en `app/proguard-rules.pro`.
- Si algo falla solo en release, revisa `missing_rules.txt`.

**Fakes de test**
- Los fakes viven en `domain/src/test/.../Fakes.kt`, `app/src/sharedTest/.../FakesApp.kt` y `licencia/src/test/.../FakeGl.kt`.
- Una interface de repositorio nueva necesita su fake.

---

## 8. Mapa de documentos (léelos solo si la tarea lo pide)

| Documento | Para qué |
|---|---|
| `AGENTS.md` | Reglas para agentes (OpenCode lo lee siempre) |
| `Pruebas.md` / `Pendiente.md` | Qué probar y qué falta |
| `README.md` | Arquitectura y novedades por versión |
| `RELEASE.md` | Keystore, firma, prueba de humo y publicación en GitHub |
| `PRUEBAS_DISPOSITIVO.md` | Guía larga por USB: adb y pruebas manuales por flujo |
| `FORMATOS.md` | Respaldo, PDF, Excel, PNG y registro de la prueba |
| `SECURITY.md` / `LICENSE_CLIENT.md` | Cifrado, claves y licencias |
| `DESIGN_SYSTEM.md` / `UI_UX_IX.md` | Componentes, colores y tipografía |
| `MANUAL_USUARIO.md` | Manual del usuario final |
| `docs/VINCULACION.md` | Protocolo principal ⇄ secundaria |
| `docs/PROMPT_0.27.0.md` | Especificación de la versión actual (T1–T14); el resto, en la entrada 0.27.0 del historial |
| `docs/PLAN_0.26.md` / `docs/PLAN_ANTIREINSTALACION.md` | Decisiones de la 0.26.0 |
| `docs/HISTORIAL_DESARROLLO.md` | Registro de decisiones de cada versión |
| `docs/OPENCODE_DESKTOP.md` | Guía paso a paso con OpenCode Desktop |
