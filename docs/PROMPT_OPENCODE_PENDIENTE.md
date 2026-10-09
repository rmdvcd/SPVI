# Prompt para OpenCode: cerrar lo pendiente de SPVI (versión 2)

> **Versión 2 · 2026-10-08.** Sustituye a la versión anterior, que tenía una fase de preguntas. Las decisiones D1–D7 ya están tomadas (ver `docs/ESTADO_INSTRUCCIONES.md`, §7).
>
> **Cómo usarlo.** Abre OpenCode (modo Build) en la carpeta raíz del repositorio, en tu PC. Copia todo lo que hay debajo de la línea y pégalo. El trabajo va por fases. Al final de cada una, OpenCode se detiene e informa; continúa cuando escribas «sigue».
>
> **Base.** Rama `arena/cb554258-spvi`, creada desde `main` en `82672fd`. `main` avanzó a `8ef4842` cuando fusionaste la PR #4 (ver «Pendiente del dueño» más abajo).

---

Eres el desarrollador de **SPVI**, una app Android de punto de venta en Kotlin (paquete `cu.spvi.app`). Trabajas en el PC del dueño, con Windows. Responde siempre en español.

## 0. Reglas no negociables

1. Lee `AGENTS.md` antes de tocar nada. Si este prompt contradice `AGENTS.md`, para y pregunta.
2. Trabaja **solo** en la rama `arena/cb554258-spvi`. No cambies a otras ramas, no hagas `push --force`, no reescribas historia, no abras PR y **no toques `main`**.
3. Protegidos sin confirmación del dueño: lista de permisos, claves de GL, protocolo de sincronización v1, DTO de respaldo v4, lectura de `.spvi` v3 y v4, firma del release y versión (0.30.0, `versionCode` 51).
4. Nada de Firebase, analítica, telemetría, anuncios ni servidores propios. Nada de `READ_SMS`. Nunca leer SMS ni el portapapeles en segundo plano.
5. Nunca des una tarea por hecha sin evidencia. Para cada comprobación anota el comando, el código de salida, los recuentos reales y el SHA del commit. Lo que no ejecutes queda como `NO VERIFICADO`.
6. Un commit por tarea, en español, con el ID al principio (`T2.1: ...`). Al final de cada fase, `git push origin arena/cb554258-spvi`.
7. En PowerShell, escribe entre comillas todo argumento que empiece por `-P`.

## 1. Decisiones tomadas (no las vuelvas a preguntar)

- **D1 · Respaldo protegido por defecto.** Al exportar, «Proteger con contraseña» viene activado. «Sin contraseña» sigue como acción explícita, con advertencia clara. Esto revierte el valor por defecto de la 0.27.0 (T10): anótalo en `docs/HISTORIAL_DESARROLLO.md`.
- **D2 · Sin captura de SMS en esta rama.** No integres `CapturaSmsPagoService` ni nada de `arena/1c545bdc-spvi`.
- **D3 · `Pendiente.md` sí, `Pruebas.md` no.** Repón `Pendiente.md` (de `c64c3dc9`, puesto al día). No crees `Pruebas.md`. Las pruebas de la 0.27.0 que pedía su cierre van a `PRUEBAS_DISPOSITIVO.md`.
- **D4 · Versión.** Sigue en 0.30.0 (`versionCode` 51). No la cambies. No la subas hasta que F1 y F2 estén en verde.
- **D5 · F5 fuera.** No empieces ninguna tarea de F5: T5.1 multimoneda, T5.2 descuentos, impuestos y devoluciones, T5.3 usuarios locales, T5.4 `strings.xml`.
- **D6 · Dispositivo.** Las mediciones se hacen en un emulador API 35 y valen solo como referencia. T1.5 no se cierra hasta medir en un teléfono de gama baja, y eso lo hace el dueño. Nunca uses el teléfono de uso diario.
- **D7 · Sin consulta a GitHub por defecto.** `spviGithubRepo` no se define en `gradle.properties` de esta rama, así que queda vacío. La regla es «vacío = no consulta nada».

Ya decidido, no preguntes:
- **T4.1:** barra inferior solo con iconos (requisito de `SPVI.txt`, citado en `UI_UX_IX.md`).
- **T2.2:** la prueba manual con tres teléfonos la dispensó el dueño. En el informe va como `NO VERIFICADO`.

**Pendiente del dueño (no lo toques).** `main` cambió a `8ef4842` cuando el dueño fusionó la PR #4 (`arena/1c545bdc-spvi`). Esa fusión trae `CapturaSmsPagoService` y `spviGithubRepo=rmdvcd/SPVI`, que chocan con D2, D7 y con la regla de SMS de `AGENTS.md`. Esta ronda no sincroniza con `main` ni cambia nada de la PR #4. Si el dueño decide otra cosa, lo dirá en un prompt nuevo.

## Fase 0 · Línea base (sin cambios de código)

1. `git fetch --all --prune`.
2. `git checkout arena/cb554258-spvi` y después `git pull --ff-only origin arena/cb554258-spvi`.
3. Anota `git rev-parse HEAD`, `git rev-parse origin/main` y `java -version`. No hagas merge ni rebase de `main` en esta rama.
4. Ejecuta `.\gradlew.bat spviCheck`. Si falla, para y dame el primer error (archivo y línea).
5. Informa y espera mi «sigue».

## Fase 1 · Integrar lo que ya existe en otras ramas

**1.1 Seed.** `git cherry-pick c2d3cf6` (de `origin/arena/3c116bea-spvi`: recetas en g/mL, CI verde). **No** traigas `4c2f1cc` (es `AGENT_BUS.md`). Si hay conflicto en `docs/HISTORIAL_DESARROLLO.md`, conserva las dos entradas. Después, `.\gradlew.bat spviTests`.

**1.2 Código de `origin/arena/c64c3dc9-spvi`,** en este orden:
`dda0408` (T1.4) · `bacd303` · `cc30f77` · `71e5875` · `0b5b765` (T2.1) · `e687bbb` · `3bf4fc1` (T2.2, T2.3) · `43cc5df` (T2.4, T2.5) · `9aabb7b` (T3, T4.2–T4.4; su CI está en rojo, run `37856092133`).

Para cada commit:
- Después, `.\gradlew.bat spviTests` (o cada dos o tres commits, si todo va bien). Si falla, corrige con el cambio mínimo. No tapes un test para que pase.
- **Documentos protegidos.** Si el commit toca `AGENTS.md`, `Contexto.md`, `README.md` o `RELEASE.md`, restaura esos archivos a la versión anterior (`git checkout HEAD~1 -- <archivo>`) y haz un commit «docs: conservar las reglas vigentes». Si hay conflicto en esos archivos, resuélvelo con `git checkout --ours -- <archivo>`. Excepción: en `0b5b765`, aplica a mano a `README.md` solo los cambios sobre el respaldo, si son correctos.
- **Pérdida de contenido.** Si un documento pierde más del 20 % de sus líneas sin que lo justifique el cambio, restáuralo y repórtalo.
- **Conflictos en `docs/HISTORIAL_DESARROLLO.md` o `docs/VERIFICACION.md`.** Conserva las dos versiones de cada entrada y ordénalas por fecha.
- **Entran tal cual:** `Pendiente.md`, `MANUAL_USUARIO.md`, `PRUEBAS_DISPOSITIVO.md`, `docs/DECISIONES.md`, `tools/verificacion/documentacion.py` y el job `docs` de `.github/workflows/ci.yml`.

**1.3 CI roja de `9aabb7b`.** Reproduce en local los comandos de la CI (`.github/workflows/ci.yml`):
- `.\gradlew.bat spviTests :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin --continue --stacktrace --console=plain --no-configuration-cache`
- `.\gradlew.bat :app:lintDebug --no-configuration-cache --stacktrace --console=plain`
- `.\gradlew.bat :app:assembleRelease :app:assembleDebug --continue --stacktrace --console=plain --no-configuration-cache`
- `.\gradlew.bat spviPermisos --no-configuration-cache --stacktrace --console=plain`
- `python tools/verificacion/api_minima.py`
- `python tools/verificacion/documentacion.py`

Corrige la causa de cada fallo. No amplíes `app/lint-baseline.xml` para tapar errores; revisa cualquier línea nueva que añadas. Si el demonio de Kotlin se cae al arrancar, añade `"-Pkotlin.compiler.execution.strategy=in-process"` (así lo usa la CI).

**1.4 Comprobaciones.**
- `git grep -n CapturaSmsPagoService` no devuelve nada en esta rama.
- `AGENTS.md` conserva estas reglas (si no, restáuralas desde `82672fd`): «nunca leer SMS ni el portapapeles en segundo plano», «SPVI2:» como única licencia activable, «la firma del release debe ser siempre la misma (deviceId)» y la sección «Trampas conocidas».

Para aquí e informa. Espera mi «sigue».

## Fase 2 · Lo que falta

**2.1 T3.1 · Escáner de productos.** El escáner de códigos de productos ya no existe (v11, 0.30.0). **CameraX y ML Kit NO se quitan**: los usa la vinculación por QR (`app/src/main/kotlin/cu/spvi/app/vinculacion/qr/CamaraEscaner.kt`).
- En `Contexto.md`, quita «escáner (CameraX + ML Kit)» de la fila de Inventario.
- En `AGENTS.md` (línea 17), cambia «ML Kit (escáner)» por «ML Kit (lee el QR de vinculación)».
- En `app/build.gradle.kts` (línea 143), reescribe el comentario «Inventario: escáner…» para que describa la cámara del QR de vinculación. El plan dice `data/build.gradle.kts`, pero el comentario está en `app`.
- Elimina `tools/escaner/` con `git rm -r tools/escaner`. `docs/VERIFICACION.md` lo marca como arnés retirado.
- Revisa `AyudaContenido.kt`: no debe hablar del escáner de productos. Lo del QR de vinculación sí puede quedarse.

**2.2 T3.2 · Esquema.** Compara la tabla de esquema de `README.md`, campo por campo, con `data/src/main/kotlin/cu/spvi/data/db/entity/Entities.kt` (Room v11). Corrige las menciones de versión actual: «v10» en `README.md` (línea 10), `AGENTS.md` (línea 63) y `Contexto.md` (línea 18) pasan a «v11 (`MIGRACION_10_11`, esquema `11.json`)». Deja las notas históricas, como «v10 (0.27.0)».

**2.3 T3.3 · Codificación.** `docs/HISTORIAL_DESARROLLO.md` ya está reparado (`fb82943`): compruébalo. Busca el carácter U+FFFD (�) en archivos de texto, sin PNG ni binarios. Hoy aparece en `ANALISIS_SPVI.md`: léelo en contexto y ponle el carácter correcto, o reescribe la frase.

**2.4 T3.4 · Documentos que faltaban (D3).**
- Si no llegaron con los cherry-picks, repón `MANUAL_USUARIO.md`, `PRUEBAS_DISPOSITIVO.md` y `Pendiente.md` desde `origin/arena/c64c3dc9-spvi`.
- Revisa `MANUAL_USUARIO.md` contra la app real (pantallas, textos de `AyudaContenido.kt`, respaldo con contraseña activada por defecto, sin escáner de productos) y corrige lo que no coincida.
- Añade a `PRUEBAS_DISPOSITIVO.md` las pruebas que pedía el cierre de la 0.27.0: bloqueo con clave, respaldo con y sin contraseña, foto con cámara y con galería, deslizar solo en las 5 principales y letra al 200 %.
- `Pendiente.md`: reescribe su estado con lo que verifiques en la Fase 3. No dejes afirmaciones de `c64c3dc9` que no hayas comprobado.
- Quita las menciones a `Pruebas.md` de `README.md` (línea 26), `AGENTS.md` (línea 5) y `Contexto.md`. Cambia la frase, no borres la regla que la acompaña.
- Menciones a `opencode.json` y `.opencode/commands/` (en `AGENTS.md`, `Contexto.md` y `docs/OPENCODE_DESKTOP.md`): estos archivos nunca han estado en git. **Compruébalos en la carpeta de este PC.** Si existen, no toques esas menciones. Si no existen, cambia la frase (no la regla) y dilo en el informe. **No crees esos archivos.**
- Comprueba que los enlaces del README a `MANUAL_USUARIO.md`, `PRUEBAS_DISPOSITIVO.md` y `Pendiente.md` funcionan.

**2.5 T2 · Textos que no se cortan (criterio de la 0.27.0).** Busca `maxLines = 1`, `TextOverflow.Ellipsis` y anchos o altos fijos en contenedores de texto (`git grep -n -E "maxLines = 1|TextOverflow.Ellipsis"`). Quedan unos 10. Corrígelos así: `heightIn(min = …)` en vez de altura fija; `Modifier.weight(1f, fill = false)` en el texto dentro de una `Row`; `FlowRow` para las filas de botones o chips que no caben. La única excepción es la Descripción de un artículo (≤ 40 caracteres). Criterio: con letra al 200 % y 360 dp de ancho, ni textos cortados ni palabras partidas.
Después de `.\gradlew.bat :app:recordRoborazziDebug`, revisa las capturas `*letra_200*` y las de Inicio, fichas, Licencia y Caja. Describe lo que ves; no des el criterio por bueno sin mirarlo.

**2.6 T2.1 · Respaldo protegido (D1).** Comprueba y anota:
- La pantalla de respaldo y `RespaldoLogic` arrancan con «Proteger con contraseña» activado.
- Pasan `data/src/test/kotlin/cu/spvi/data/BackupCipherTest.kt`, `app/src/test/kotlin/cu/spvi/app/respaldo/RespaldoTest.kt` y `RespaldoPasosTest.kt`.
- Hay una captura Roborazzi nueva de la pantalla de respaldo.
- El modo sin contraseña muestra una advertencia que no sea tibia, del tipo «cualquiera que consiga este archivo puede leer tus ventas y los datos de tus clientes».
- `FORMATOS.md` y `SECURITY.md` describen el valor nuevo.

**2.7 T2.2 · Prueba de tres teléfonos.** La dispensó el dueño. En el informe: `NO VERIFICADO`.

**2.8 F4 · Adaptatividad y Ayuda.**
- T4.2: en ≥ 600 dp, Inventario, Registros y Servicios usan lista-detalle, y los diálogos se dimensionan por contenido (`IntrinsicSize`), no por fracción de pantalla (`Overlays.kt`). Debe haber capturas con cualificador de tablet.
- T4.3: revisa los `Textos*` con los tests que ya existen. `AyudaContenido.kt` solo describe lo que la app hace hoy.
- T4.4: la búsqueda muestra que está trabajando mientras busca.
Verifica cada punto con un test o una captura. Si no hay, `NO VERIFICADO`.

**2.9 T8 · Textos técnicos.** Recorre los textos visibles (pantallas, diálogos, snackbars, errores, Ayuda, Soporte) y reescribe en lenguaje llano lo que todavía tenga términos técnicos o reveladores: SHA-256, ECIES, PBKDF2, SQLCipher, token, deviceId, JSON, MediaStore, nombres de archivo como `sys_…`, `e.message` o versiones internas. Guarda la tabla «antes → después» para el informe.

**2.10 T1.5 · Rendimiento, referencia en emulador (D6).**
1. En Android Studio (Device Manager), arranca un AVD con API 35. Comprueba con `adb devices -l` que aparece como `device`.
2. Mide `ObtenerResumenGeneral` con el `GeneradorSeed` de 18 meses, que ya usa `app/src/androidTest/kotlin/cu/spvi/app/integracion/RendimientoInicioTest.kt`: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=cu.spvi.app.integracion.RendimientoInicioTest" --no-configuration-cache`.
3. Anota el tiempo con el código actual (después de T1.4). Si puedes, repite la medición con `origin/main` en el mismo emulador («antes»); si no, escribe «antes: NO VERIFICADO».
4. En `docs/VERIFICACION.md` anota el modelo del emulador, la API, la RAM, los tiempos, el umbral del test y esta frase: «referencia en emulador: T1.5 sigue abierto hasta medir en un teléfono de gama baja».

**2.11 F6 · Higiene.** Comprueba que `git ls-files .kotlin` está vacío, que `.kotlin/` está en `.gitignore` y que `docs/DECISIONES.md` existe y es el índice de las referencias «P37», «P68b», «P74» y «§5.4». Si tras la integración quedan `!!` en UI o ViewModels (`git grep -n "!!" -- app/src/main data/src/main`), sustitúyelos por comprobaciones que muestren un mensaje, sin cambiar el comportamiento. Anota el número antes y después.

**2.12 F5.** No la toques (D5).

Para aquí e informa. Espera mi «sigue».

## Fase 3 · Verificación final

1. `.\gradlew.bat spviCheck` debe terminar con código 0. Informa de los recuentos por módulo, de los fallos y del lint (errores y avisos).
2. `python tools/verificacion/documentacion.py` debe terminar con código 0.
3. Si cambiaste la interfaz: `.\gradlew.bat :app:recordRoborazziDebug` y revisa qué imágenes cambiaron.
4. Con el emulador arrancado: `.\gradlew.bat spviInstrumentedTests --no-configuration-cache` (136 tests instrumentados; F6). Si no puedes, `NO VERIFICADO`.
5. Actualiza `docs/VERIFICACION.md` con resultados reales: fecha, SHA, comando, código de salida y recuentos. No copies cifras de `c64c3dc9` ni de documentos antiguos que no hayas reproducido.
6. `PLAN_CORRECCIONES.md`: actualiza solo la tabla de estado de las fases. No cambies tareas ni criterios.
7. `docs/HISTORIAL_DESARROLLO.md`: añade una entrada por cada cambio de comportamiento. La de T2.1 dice que revierte el valor por defecto de la 0.27.0.
8. Autocomprobación. Cada comando debe dar lo que se indica:
   - `git grep -n "Pruebas.md"`: solo aparece en `docs/HISTORIAL_DESARROLLO.md`, `ANALISIS_SPVI.md`, `PLAN_CORRECCIONES.md`, `docs/PROMPT_0.27.0.md`, `docs/ESTADO_INSTRUCCIONES.md` y `docs/PROMPT_OPENCODE_PENDIENTE.md`.
   - `git grep -n CapturaSmsPagoService`: vacío.
   - `git ls-files .kotlin`: vacío.

Para aquí e informa. Espera mi «sigue».

## Fase 4 · Entrega

1. `git push origin arena/cb554258-spvi`, sin `--force`.
2. CI: `gh run list --branch arena/cb554258-spvi --limit 3`. Si no aparece ninguna ejecución, lánzala con `gh workflow run ci.yml --ref arena/cb554258-spvi`. Si falla, corrige hasta dejarla en verde o repórtame el primer error.
3. No abras PR. Pregúntame antes.

## Informe final (en español)

1. Tabla por tarea: estado (✅ verificado · 🧪 NO VERIFICADO · ⚠️ decisión pendiente · ❌ bloqueado), SHA del commit, archivos y tests añadidos.
2. Salida de `spviCheck`: código de salida y recuentos.
3. Tabla «antes → después» de textos (T8).
4. Lo que queda sin hacer y por qué. Incluye T1.5 (solo cierra con teléfono de gama baja) y T2.2 (prueba de tres teléfonos dispensada).
5. Resultado de la comprobación de `opencode.json` y `.opencode/commands/` en este PC.
6. Suposiciones que hayas tomado.
