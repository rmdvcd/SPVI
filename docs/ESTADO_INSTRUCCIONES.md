# SPVI — Estado de las instrucciones de sesiones anteriores

> **Comprobación:** 2026-10-09 · **Base:** `main` @ `82672fd` · **Rama de la sesión:** `arena/cb554258-spvi`
>
> **Método.** Lectura del código y de la documentación de `main` y de las ramas remotas `arena/*`, búsquedas en el árbol y consulta de la CI con `gh`. **No se ha compilado ni ejecutado nada**: la sesión no tiene Android SDK ni acceso a Maven o Google (solo GitHub, npm y PyPI).
>
> **Leyenda.** ✅ está en el código (no significa que funcione) · 🟡 parcial · ❌ no está · 🔀 está solo en otra rama, no en `main` · ⚠️ conflicto que necesita tu decisión · ⏳ sin empezar · 🧪 **NO VERIFICADO**: requiere ejecutar (Gradle, Roborazzi, dispositivo) o mirar la pantalla.

## 1. Fuentes que tomo como «instrucciones de sesiones anteriores»

| Fuente | Qué pide | Situación |
|---|---|---|
| [PROMPT_0.27.0.md](PROMPT_0.27.0.md) | Tareas T1–T14 y cierre (documentación, `Pruebas.md`, `Pendiente.md`, `MANUAL_USUARIO.md`) | Prompt para OpenCode de la 0.27.0. La app va ya por la 0.30.0 |
| [PLAN_CORRECCIONES.md](../PLAN_CORRECCIONES.md) (commit `fa91473`, «aprobado por el autor») | Fases F0–F6 | Plan vigente |
| `AGENT_BUS.md` (turnos `804c960` y `4c2f1cc`) | Validación local, medición de T1.5 y diseño de T1.4 | Retirado de `main` en `82672fd` |

No tengo acceso al chat de esas sesiones. Si hubo instrucciones que no quedaron escritas en el repositorio, no aparecen aquí.

## 2. Resumen

- **En `main`:** F0 en código y CI, salvo tu prueba en el teléfono (T0.6). F1 en T1.1–T1.3. Y 12 de las 14 tareas del PROMPT 0.27.0 en código; T2 y T8 quedan a medias.
- **Solo en otras ramas, sin fusionar:** T1.4 (agregación SQL), T2.2–T2.5 (seguridad), T3.4–T3.6, la mayor parte de F6 y T4.2–T4.4. Casi todo está en `arena/c64c3dc9-spvi`.
- **Sin hacer:** T1.5 (medición en dispositivo), T1.6 (opcional), F5, y parte de F3 (T3.2 y T3.5).
- **Riesgos que hay que resolver antes de fusionar:**
  1. `arena/c64c3dc9-spvi` tiene la CI en rojo en su último commit (`9aabb7b`, cuatro trabajos fallan).
  2. Su reescritura de `AGENTS.md`, `Contexto.md` y `README.md` elimina reglas que decidiste tú (por ejemplo, «nunca leer SMS ni el portapapeles en segundo plano»).
  3. `arena/1c545bdc-spvi` añade captura automática de notificaciones de SMS, que contradice la regla 3 de `AGENTS.md`.

## 3. Ramas remotas y CI

| Rama | Último commit | Qué contiene (frente a `main`) | CI del último commit |
|---|---|---|---|
| `main` | `82672fd` | — | ✅ `success` ([37715675514](https://github.com/rmdvcd/SPVI/actions/runs/37715675514)) |
| `arena/c64c3dc9-spvi` | `9aabb7b` | 9 commits: T1.4, T2.1–T2.5, T3.1, T3.4–T3.6, T4.1–T4.4 y F6 | ❌ fallan Lint, Tests JVM, R8/APK y API mínima ([37856092133](https://github.com/rmdvcd/SPVI/actions/runs/37856092133)). En `43cc5df` la CI era ✅ ([37836886846](https://github.com/rmdvcd/SPVI/actions/runs/37836886846)) |
| `arena/3c116bea-spvi` | `c2d3cf6` | Corrección del seed (recetas en g/mL, costo menor que venta) | ✅ `success` ([37857577330](https://github.com/rmdvcd/SPVI/actions/runs/37857577330)) |
| `arena/1c545bdc-spvi` | `f7ab5aa` | Tablas, pagos (logos de bancos y captura de SMS), actualizaciones | ❌ en `b9559b2`; `f7ab5aa` estaba en curso al comprobarlo |
| `arena/d7f67f1f-spvi` | `6fe6321` | Validaciones, respaldos, documentación reescrita, guía de dispositivo | ❌ falla «Tests JVM y compilación de instrumentados» ([37723487443](https://github.com/rmdvcd/SPVI/actions/runs/37723487443)) |

No he podido descargar los registros de los fallos: el servicio de GitHub responde con error EOF. Por eso el prompt pide reproducirlos en local.

## 4. PROMPT_0.27.0 (T1–T14 y cierre)

| ID | Tarea | `main` | Otras ramas | Evidencia y notas |
|---|---|---|---|---|
| T1 | Títulos centrados, sin logo | ✅ 🧪 | — | `Navigation.kt`: `CenterAlignedTopAppBar` y sin `showLogo`. `Overlays.kt`: diálogos y hojas sin logo. Falta revisión visual. |
| T2 | Textos que no se cortan ni se parten | 🟡 | — | Hay casos Roborazzi «letra 200 %» para Inicio, ficha, Licencia y Caja (`InicioCapturas.kt`, `InventarioCapturas.kt`, `AjustesCapturas.kt`). Quedan unos 10 usos de `maxLines`/`Ellipsis` (`Surfaces.kt`, `Selectores.kt`, `Tabs.kt`, `Navigation.kt`, `CajaUi.kt`). 🧪 |
| T3 | Medallas del Top 3 | ✅ | — | `SpviMedalla` y `SpviMedallaColores` en `Indicators.kt`; tokens `MEDALLA_*` en `ColorTokens.kt`; `ContrastTest.medallasLegibles`. |
| T4 | Animaciones con `SpviMotion` | ✅ 🧪 | — | `muelle()` = `spring(NoBouncy, MediumLow)` en `Tokens.kt`. No quedan `delay(16)` ni `withFrameNanos`. `profileinstaller` añadido. La fluidez en gama baja es 🧪. |
| T5 | Deslizar solo entre las cinco raíces | ✅ | — | `deslizarPermitido` en `MainScaffold.kt`; `DeslizarTest`. |
| T6 | Placeholder centrado | ✅ | — | `TextFields.kt` (`textAlign = Center`). Ningún `TextField` directo en `app`. |
| T7 | Iconos PDF y Excel | ✅ | — | `SpviIcons.Pdf` (PictureAsPdf) y `SpviIcons.Excel` (TableView), usados en `IconosFormato.kt`. |
| T8 | Sin textos técnicos | 🟡 | — | En los literales de UI no quedan términos técnicos (solo un `SHA-256` interno). Falta la tabla «antes → después» y revisar los textos compuestos. 🧪 |
| T9 | Una principal nunca pasa a secundaria | ✅ | — | `Vinculacion.puedeVincularseComoSecundaria` (dominio), comprobación en `VinculacionViewModel`, botón oculto y tests en `VinculacionViewModelTest`. Diferencia menor: devuelve un mensaje propio en vez de `SinPermiso`. |
| T10 | Contraseña del respaldo opcional (v4) | ✅ ⚠️ | 🔀 `c64c3dc9`: encendida por defecto | `BackupCipher` (cabecera v4 con indicador), `RespaldoLogic.conContrasena = false`, `BackupCipherTest`, `FORMATOS.md` en v4. Ver D1. |
| T11 | Acceso con biometría o PIN | ✅ 🧪 | — | `AccesoClave.kt` (`BiometricPrompt`); `MainActivity : FragmentActivity` con `ProcessLifecycleOwner`; paso `ACCESO_CLAVE` en el recorrido; fila en Ajustes; `AccesoClaveTest`. `BiometricPrompt` no se ha probado nunca en un teléfono. |
| T12 | Iconos en los datos de Soporte | ✅ | — | `SoporteInfo.icono(...)` en `SoporteScreen.kt`; pie actualizado. |
| T13 | Foto primero (cámara y galería) | ✅ | — | `SelectorFoto` en `ProductoFormScreen` y `ServicioFormScreen`; ruta `fotos/` en `archivos_compartidos.xml`; `PickVisualMedia` sin permisos. Queda `onElegirFoto` en `AccionesForm`, probablemente código muerto. |
| T14 | Reparto de alertas en Inicio | ✅ | — | `repartoAlertas` en `InicioLogic.kt`; `InicioLogicTest.repartoDeAlertasPorFilas`. |
| Cierre | Documentación 0.27.0 | 🟡 | — | README con novedades, `FORMATOS.md` en v4 e historial de 0.27.0: ✅. ❌ `MANUAL_USUARIO.md`, ❌ `Pendiente.md` y ❌ `Pruebas.md`: los enlazan README, AGENTS y Contexto, y no existen. |

## 5. PLAN_CORRECCIONES.md (F0–F6)

### F0 · Verdad y CI

| ID | Tarea | `main` | Otras ramas | Evidencia y notas |
|---|---|---|---|---|
| T0.1 | CI con trabajos separados | ✅ | 🔀 `c64c3dc9` añade `docs` | `ci.yml`: tests, lint, release, permisos y api-min. |
| T0.2 | Tests instrumentados que no compilaban | ✅ | — | No quedan `porCodigo`, `ConsentimientoRed` ni `consultasEnLinea` en `src/`. Sí quedan en `tools/escaner/`, que no se compila. |
| T0.3 | Arreglos de la primera corrida | ✅ | — | La última CI de `main` es `success`. |
| T0.4 | Congelar la versión 0.30.0 (51) | ✅ | — | `app/build.gradle.kts` (líneas 49–50) y README. |
| T0.5 | `docs/VERIFICACION.md` como fuente única | 🟡 | — | Existe; última actualización el 2026-10-07 (`f480e50`). Sigue la contradicción de versión de la base de datos (T3.2). |
| T0.6 | Probar el APK en tu teléfono (tú) | 🧪 | — | Sin evidencia en el repositorio. |

### F1 · Rendimiento

| ID | Tarea | `main` | Otras ramas | Evidencia y notas |
|---|---|---|---|---|
| T1.1 | `withContext(io)` en casos de uso de Inicio | ✅ | — | `IoDispatcher` en `InicioUseCases.kt`. |
| T1.2 | `flowOn(io)` en cadenas reactivas | ✅ | — | `flowOn` en los casos de uso de `domain` (Elaboración, Inicio, Inventario, Registro y Servicio). |
| T1.3 | `debounce` en buscadores | 🟡 | — | Está en `InventarioViewModel`, `RegistrosViewModel` y `ServiciosViewModel`. No encuentro el de Precios ni el de la ficha: revisar. |
| T1.4 | Agregación en SQL | ❌ | 🔀 `c64c3dc9` (`dda0408`) | `GROUP BY` por ventanas, con tests de paridad SQL/memoria (`VentaAgregadosRoomTest`). CI ✅ en `cc30f77` (run [37766967443](https://github.com/rmdvcd/SPVI/actions/runs/37766967443)), según su propio `docs/VERIFICACION.md`. Sin fusionar. |
| T1.5 | Medición en dispositivo | 🧪 NO VERIFICADO | — | `RendimientoInicioTest` compila; no se ha ejecutado en un dispositivo autorizado. |
| T1.6 | Baseline profile (opcional) | ⏳ | — | No existe el módulo `:baselineprofile`. |

### F2 · Seguridad

| ID | Tarea | `main` | Otras ramas | Evidencia y notas |
|---|---|---|---|---|
| T2.1 | Respaldo protegido por defecto | ⚠️ | 🔀 `c64c3dc9` (`0b5b765`) | `main` lo deja apagado (decisión de la 0.27.0). Ver D1. |
| T2.2 | Sesiones LAN que no se sustituyen sin autenticar | ❌ | 🔀 `c64c3dc9` (`3bf4fc1`) | `ControlConexiones.kt`, `SesionesActivas.kt`, `ControlConexionesTest` y `sesionNoSeCierraSinPruebaDeClave`. CI ✅ en `3bf4fc1` (run [37834591679](https://github.com/rmdvcd/SPVI/actions/runs/37834591679)). |
| T2.3 | «Hola» con respuesta uniforme | ❌ | 🔀 `c64c3dc9` (`3bf4fc1`) | `AutenticacionHola.kt` (`rechazoIdentidadHola`). |
| T2.4 | Caché del registro de prueba | ❌ | 🔀 `c64c3dc9` (`43cc5df`) | `CacheLecturas.kt` y `CacheLecturasTest`. CI ✅ en `43cc5df` (run [37836886846](https://github.com/rmdvcd/SPVI/actions/runs/37836886846)). |
| T2.5 | Aviso si GitHub no responde | ❌ | 🔀 `c64c3dc9` (`43cc5df`) | `diasSinComprobar` y aviso en Ajustes (14 días). |

### F3 · Coherencia documental

| ID | Tarea | `main` | Otras ramas | Evidencia y notas |
|---|---|---|---|---|
| T3.1 | Retirar el escáner de códigos de barras | 🟡 | 🔀 `c64c3dc9` | El código ya no tiene escáner de productos (v11, 0.30.0). Quedan «escáner (CameraX + ML Kit)» en `Contexto.md`, «ML Kit (escáner)» en `AGENTS.md`, un comentario en `app/build.gradle.kts` y la carpeta `tools/escaner/`. |
| T3.2 | Esquema Room v11 en la documentación | ❌ | 🔀 README reescrito en `c64c3dc9` | La base de datos es v11 (`SpviDatabase.VERSION = 11`, `MIGRACION_10_11`, `11.json`). README, AGENTS.md y Contexto.md dicen v10. |
| T3.3 | Codificación del historial | 🟡 | — | `HISTORIAL_DESARROLLO.md` sin mojibake (`fb82943`). Queda un carácter U+FFFD en `ANALISIS_SPVI.md`. |
| T3.4 | Manual, guía de dispositivo y pendientes | ❌ | 🔀 `c64c3dc9` (3 archivos) · 🔀 `d7f67f1f` (2) | `main` enlaza cuatro archivos que no existen. Ver D3. |
| T3.5 | Cabeceras de versión | ❌ | 🔀 `c64c3dc9` (UI/UX a 0.30.0) | `UI_UX_IX.md` dice 0.19.3 y `SECURITY.md` dice 0.26.0 en `main`. |
| T3.6 | Chequeo automático de documentación | ❌ | 🔀 `c64c3dc9` (`tools/verificacion/documentacion.py` y job `docs`) | Añadido, pero no ejecutado. |

### F4 · Interfaz y adaptatividad

| ID | Tarea | `main` | Otras ramas | Evidencia y notas |
|---|---|---|---|---|
| T4.1 | Barra inferior con o sin etiquetas | ✅ decidido | — | `c64c3dc9` registra la decisión: solo iconos. `main` ya lo cumple (`alwaysShowLabel = false`). |
| T4.2 | Lista-detalle desde 600 dp | ❌ | 🔀 `c64c3dc9` (`WindowSize.kt`) | Sin validar; la CI de su último commit está en rojo. 🧪 |
| T4.3 | Auditoría de `Textos*` y Ayuda | ❌ | 🔀 `c64c3dc9` (parcial) | Auditoría estática parcial. 🧪 |
| T4.4 | Señal de progreso en la búsqueda | ❌ | 🔀 `c64c3dc9` | Implementada en esa rama, sin validar. 🧪 |

### F5 · Producto

| ID | Tarea | `main` | Otras ramas | Evidencia y notas |
|---|---|---|---|---|
| T5.1–T5.4 | Multimoneda, descuentos y devoluciones, usuarios locales, `strings.xml` | ⏳ | — | Sin empezar. `c64c3dc9` dice que F5 está autorizada por fases, pero sin reglas detalladas. Ver D5. |

### F6 · Higiene

| ID | Tarea | `main` | Otras ramas | Evidencia y notas |
|---|---|---|---|---|
| F6.1 | `.kotlin/` fuera de Git | ❌ | 🔀 `c64c3dc9` ✅ | Sigue versionado (`.kotlin/sessions/…salive`). |
| F6.2 | Quitar `!!` de la producción | ❌ | 🔀 `c64c3dc9` (0) | `main` tiene 33 líneas con `!!` en `src/main`. |
| F6.3 | `docs/DECISIONES.md` | ❌ | 🔀 `c64c3dc9` ✅ | — |
| F6.4 | Ejecutar los 136 tests instrumentados | ⏳ | — | Nunca se han ejecutado. Requiere D6. |

## 6. Turnos del bus (`AGENT_BUS.md`, 7–8 de octubre)

| Turno | Resultado | Estado |
|---|---|---|
| Validación local | 826 tests, 0 fallos; lint con 0 errores y 127 avisos; `spviPermisos` correcto. Lo informó OpenCode en Windows | ✅ informado; no lo he reproducido |
| Medición de T1.5 | Sin dispositivo de pruebas autorizado | 🧪 NO VERIFICADO |
| Diseño de T1.4 | Pedido en el turno; implementado en `c64c3dc9` (`dda0408`) | 🔀 sin fusionar |
| Decisiones pedidas al dueño (T1.4 y dispositivo) | Sin respuesta registrada en el repositorio | ⏳ |

## 7. Decisiones que necesito de ti

- **D1 · Respaldo protegido por defecto.** `main` (0.27.0) lo deja apagado. El plan (T2.1) y `c64c3dc9` lo dejan encendido, con «sin contraseña» como opción explícita y con advertencia. *Recomendación:* encendido.
- **D2 · Captura de SMS por notificaciones (`1c545bdc`).** Contradice la regla 3 de AGENTS.md. *Recomendación:* no integrarla, o cambiar esa regla de forma explícita.
- **D3 · `Pendiente.md` y `Pruebas.md`.** `c64c3dc9` repone `Pendiente.md`; `d7f67f1f` dice que ya no forman parte del proyecto; `Pruebas.md` no existe en ninguna rama, pero AGENTS y Contexto lo exigen. *Recomendación:* reponer `Pendiente.md` y quitar la referencia a `Pruebas.md`.
- **D4 · Versión de salida.** ¿0.30.0 (51) o 0.31.0 (52)? *Recomendación:* no subir la versión hasta que F1 y F2 estén en verde.
- **D5 · F5** (multimoneda, descuentos, usuarios locales, `strings.xml`). *Recomendación:* no empezar sin reglas de negocio.
- **D6 · Dispositivo de pruebas.** ¿Emulador o teléfono dedicado? Nunca el de uso diario con datos reales. Hace falta para T1.5 y para las 136 pruebas instrumentadas.
- **D7 · Actualizaciones por defecto (`1c545bdc`).** Su `gradle.properties` fija `spviGithubRepo=rmdvcd/SPVI`, así que la app consultaría GitHub sin configurar nada. Hoy la regla es «vacío = no consulta nada» (README y Contexto). *Recomendación:* decidirlo antes de integrar esa rama.

## 8. Riesgos de integración

- **Reescrituras de documentos.**
  - `c64c3dc9`: `AGENTS.md` pierde «nunca leer SMS ni el portapapeles en segundo plano», «SPVI2: es la única licencia activable», «la firma del release debe ser siempre la misma (deviceId)» y la sección «Trampas conocidas». `Contexto.md` pasa de 243 a 53 líneas y `README.md` de 291 a 125.
  - `d7f67f1f`: `Contexto.md` pasa de 243 a 75 líneas y `RELEASE.md` de 184 a 133.
  - **No integrar esos documentos tal cual.** Se aplican solo los cambios concretos.
- **Fallos de la CI en `c64c3dc9` y `d7f67f1f`.** Hay que reproducirlos en local; no se pueden leer desde aquí.

## 9. Lo que esta comprobación no cubre

- Compilación, tests, Roborazzi, tests instrumentados y pruebas en teléfono.
- Revisión visual de T1, T2 y T4.
- La causa exacta de los fallos de la CI de `c64c3dc9` y `d7f67f1f`.

## 10. Siguiente paso

Responde a D1–D7 y ejecuta [PROMPT_OPENCODE_PENDIENTE.md](PROMPT_OPENCODE_PENDIENTE.md) en OpenCode, en tu PC, desde la carpeta del repositorio.
