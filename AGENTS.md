# SPVI — reglas para agentes (OpenCode)

App Android de punto de venta e inventario para pequeños negocios en Cuba. **Versión 0.27.1 (`versionCode 50`)**. Paquete `cu.spvi.app`. Todo el texto de la interfaz, los comentarios y la documentación están en **español**; responde en español.

**Empieza por `Contexto.md`** (resumen completo del proyecto), `Pendiente.md` (lo que falta) y `Pruebas.md` (qué probar y en qué orden); `opencode.json` carga los dos primeros en cada sesión. La referencia visual es `SPVI_0.26.0_capturas_y_exportaciones.pdf` (raíz; no se sube a git). Guía paso a paso para compilar y retocar con OpenCode Desktop: `docs/OPENCODE_DESKTOP.md`. Lee los demás documentos solo cuando la tarea los necesite:
- arquitectura, compilación y novedades: `README.md`;
- release firmado y pruebas en el teléfono: `RELEASE.md`;
- archivos que escribe la app (respaldo, PDF, Excel, PNG): `FORMATOS.md`;
- seguridad y permisos: `SECURITY.md`;
- componentes, colores y tipografía: `DESIGN_SYSTEM.md`, `UI_UX_IX.md`;
- capturas Roborazzi: `CAPTURAS.md`;
- licencias (cliente GL): `LICENSE_CLIENT.md`, `docs/GL_ESPECIFICACION.md`;
- registro de decisiones de cada versión: `docs/HISTORIAL_DESARROLLO.md`.

## Pila

Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.11.1 (wrapper) · JDK 17 · compileSdk/targetSdk 35 · minSdk 26 · Compose BOM 2024.12.01 (Material 3) · Hilt 2.52 (KSP) · Room 2.6.1 + SQLCipher 4.6.1 · OkHttp + kotlinx.serialization · fastexcel · ML Kit (escáner) · Robolectric 4.14.1 + Roborazzi 1.38.0. Versiones en `gradle/libs.versions.toml`.

## Módulos (Clean Architecture)

| Módulo | Contenido | Depende de |
|---|---|---|
| `:core` | Utilidades puras (dinero `Cup`, fechas, `Resultado`) | — |
| `:licencia` | Cliente de licencias GL (formatos `SPVIR1:` / `SPVI2:`, revocaciones) | core |
| `:domain` | Modelos, repositorios (interfaces), casos de uso, `TablasExport` | core, licencia |
| `:data` | Room, SQLCipher, respaldo, sincronización LAN, exportadores (PdfWriter, XlsxWriter, `DisenoPdf`) | domain |
| `:designsystem` | Tema, `Spvi*` componentes, `SpviIcons`, `SpviSpacing` | — |
| `:app` | Pantallas Compose, ViewModels Hilt, navegación | todos |

Reutiliza repositorios y casos de uso existentes; no metas lógica de negocio en las pantallas.

## Comandos

```bash
./gradlew spviTests                    # todos los tests JVM (sin dispositivo ni red)
./gradlew :app:assembleDebug           # APK debug
./gradlew spviCheck                    # antes de entregar: tests + lintDebug + assembleDebug + spviPermisos
./gradlew :app:recordRoborazziDebug    # capturas reales (claro y oscuro)
./gradlew spviInstrumentedTests        # emulador o teléfono API 26+
./gradlew spviRelease                  # AAB + APK firmados (requiere keystore.properties, ver RELEASE.md)
```

Un solo test: `./gradlew :domain:test --tests "cu.spvi.domain.Version025Test"`. Sin Android SDK queda `bash tools/verificacion/verificar.sh` (descarga kotlinc y compila sin Gradle; ~15 min).

## Reglas que no se negocian (decididas por el dueño del proyecto)

1. **Permisos:** solo `CAMERA`, `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `CHANGE_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES` y, solo para el registro de la prueba (0.26.0), `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` (`maxSdkVersion` 32) y `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion` 28) y, solo para el acceso con clave opcional (0.27.0), `USE_BIOMETRIC` y `USE_FINGERPRINT` (los añade `androidx.biometric`). `spviPermisos` falla con cualquier otro. Nada de WAKE_LOCK, arranque, batería ni otro uso del almacenamiento.
2. **Sin** Firebase, analítica, telemetría, anuncios ni servidores propios. INTERNET solo para: sincronización en la red local, consultas de códigos de barras, y GitHub (actualizaciones y lista de revocadas).
3. **No se vende sin turno abierto.** La cámara solo mientras se usa. SMS solo pegando o compartiendo (nunca leer SMS ni el portapapeles en segundo plano).
4. **UI:** Material 3 claro/oscuro, contraste WCAG AA (`ContrastTest`), sin estilos sueltos: usa `Spvi*` del `:designsystem`. `SpviSpacing` tiene xs=8, md=16, lg=24, xl=32 (**no existe `sm`**). Botones de solo icono con descripción. Diálogos centrados. Guardar = ✓. Gráficos sin líneas de rejilla. Asistentes de 3–4 pasos.
5. **Datos sensibles:** nada en logs ni en Recientes (`FLAG_SECURE` en Licencia, Perfil, QR y datos del cliente). El CI sale enmascarado (`••••••••345`) en los textos compartidos.
6. **Licencia:** no copies el almacenamiento de GL ni expongas detalles internos. Solo la licencia corta `SPVI2:` es activable.
7. **Documentación sin funciones inventadas.** Si cambias comportamiento, actualiza README/MANUAL_USUARIO/FORMATOS y añade una entrada en `docs/HISTORIAL_DESARROLLO.md`.
8. Tests sin red. Todo lo que añadas debe compilar y pasar `spviTests`.

## Trampas conocidas

- Hilt: los constructores `@Inject` **no** admiten parámetros por defecto. Si cambias el constructor de un ViewModel, actualiza `TurnoViewModelsTest`, `PerfilTest` y `EntornoIntegracion.kt`.
- Kotlin: una entrada de `enum` no puede usar una `const` de su propio companion; `CompositionLocal.current` no se puede leer dentro del DSL de `LazyColumn`; `spviAnimateItem()` solo dentro de `LazyItemScope`.
- KSP: si falla Room/Hilt de forma rara, borra `build/generated/ksp` del módulo y repite.
- Categoría de elaborados: `Categorias.ELABORADO = "Elaborado"` (singular).
- Excel: el formato de importe es `#,##0.00 \C\U\P` (con comillas fastexcel genera un `styles.xml` inválido).
- Base de datos v10 (`MIGRACION_9_10`, esquema `data/schemas/…/10.json`); respaldo DTO v4 (importa v3); archivo `.spvi` v4 (lee v3). Un cambio de esquema exige migración + esquema exportado + test.
- La firma de release debe ser **siempre la misma** (el `deviceId` de la licencia depende de ella).

## Lo que nunca se ha ejecutado (verifícalo tú)

Gradle real, lint, R8/ProGuard en release, Roborazzi, el APK en un teléfono y la prueba con dos teléfonos (principal + secundaria). Sigue `docs/OPENCODE_DESKTOP.md` §3–§6.
