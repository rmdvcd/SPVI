# Prompt para OpenCode: cerrar lo pendiente de SPVI

> **Cómo usarlo:** abre OpenCode (modo Build) en la carpeta del repositorio, en tu PC. Copia todo lo que hay debajo de la línea y pégalo. Trabaja **por fases**: al final de cada una se detiene y te informa.
>
> **Antes de empezar:** responde a las decisiones de la Fase 1 (o deja que OpenCode te las pregunte). Esta tarea sale de la comprobación de [ESTADO_INSTRUCCIONES.md](ESTADO_INSTRUCCIONES.md).

---

Eres el desarrollador de **SPVI**, una app Android de punto de venta en Kotlin (paquete `cu.spvi.app`). Trabajas en mi PC con Windows. Responde siempre en español.

## 0. Reglas no negociables

1. Lee `AGENTS.md` antes de tocar nada. Si este prompt contradice `AGENTS.md`, para y pregúntame.
2. Trabaja **solo** en la rama `arena/cb554258-spvi`. No crees ni cambies a otras ramas, no hagas `push --force`, no reescribas historia, no abras PR y no toques `main`.
3. No cambies, sin mi confirmación: la lista de permisos, las claves de GL, el protocolo de sincronización v1, el DTO de respaldo v4, la lectura de `.spvi` v3 y v4, la firma del release ni la versión de la app (0.30.0, versionCode 51).
4. No añadas Firebase, analítica, telemetría, anuncios ni servidores propios. No añadas `READ_SMS`. Nada de leer SMS ni el portapapeles en segundo plano.
5. **Nunca des una tarea por hecha sin evidencia.** Para cada comprobación anota el comando, el código de salida, los recuentos reales y el SHA del commit. Lo que no puedas ejecutar, márcalo `NO VERIFICADO`.
6. Edita los documentos con cuidado: cambia solo lo necesario. No borres reglas ni secciones de `AGENTS.md`, `Contexto.md`, `README.md` ni `RELEASE.md`.
7. Un commit por tarea, con mensaje en español que empiece por el ID (por ejemplo `T2.2: ...`). Al final de cada fase, `git push origin arena/cb554258-spvi`.
8. En PowerShell, escribe entre comillas las propiedades `-P`. Comandos de referencia:
   - `.\gradlew.bat --no-configuration-cache "-Pkotlin.compiler.execution-strategy=in-process" spviTests`
   - `.\gradlew.bat spviCheck`

## Fase 0 · Línea base (sin cambios de código)

1. `git fetch --all --prune`
2. `git checkout arena/cb554258-spvi` y después `git pull --ff-only origin arena/cb554258-spvi`
3. Comprueba que `main` está contenida en tu rama: `git merge-base --is-ancestor origin/main HEAD`
4. Anota `git rev-parse HEAD`, `java -version` y la versión del SDK de Android.
5. Ejecuta `spviCheck` sobre esta base. Si falla, para y dame el primer error útil (archivo y línea).
6. Informa del resultado y espera.

## Fase 1 · Decisiones (no toques las tareas afectadas hasta que responda)

Presenta cada decisión con su recomendación y espera mi respuesta:

- **D1 · Respaldo protegido por defecto.** `main` (0.27.0) lo deja apagado. El plan (T2.1) y `arena/c64c3dc9-spvi` lo dejan encendido, con «sin contraseña» como opción explícita y con advertencia. Recomendación: encendido.
- **D2 · Captura de SMS por notificaciones** (`arena/1c545bdc-spvi`, `CapturaSmsPagoService`). Contradice la regla 3 de `AGENTS.md`. Recomendación: no integrarla.
- **D3 · `Pendiente.md` y `Pruebas.md`.** Recomendación: reponer `Pendiente.md` (existe en `arena/c64c3dc9-spvi`) y quitar de README, AGENTS y Contexto la referencia a `Pruebas.md`, que no existe en ninguna rama.
- **D4 · Versión de salida.** Recomendación: mantener 0.30.0 (51) hasta que F1 y F2 estén en verde.
- **D5 · F5** (multimoneda, descuentos y devoluciones, usuarios locales, `strings.xml`). Recomendación: no empezar.
- **D6 · Dispositivo de pruebas.** ¿Emulador o teléfono dedicado? Nunca el de uso diario con datos reales. Recomendación: emulador con API 35.
- **D7 · Actualizaciones por defecto** (`arena/1c545bdc-spvi`, `spviGithubRepo=rmdvcd/SPVI`). Cambia la regla «vacío = no consulta nada». Recomendación: decidirlo antes de integrar esa rama.

## Fase 2 · Integrar el trabajo que ya existe en otras ramas

Solo después de tu respuesta a la Fase 1. **En esta ronda no integres** `arena/1c545bdc-spvi` ni `arena/d7f67f1f-spvi`: tienen la CI en rojo y decisiones pendientes.

1. **Seed.** `git cherry-pick c2d3cf6` (de `origin/arena/3c116bea-spvi`, CI verde). Si hay conflicto en `docs/HISTORIAL_DESARROLLO.md`, conserva las dos entradas. Ejecuta `spviTests`.

2. **Código de `origin/arena/c64c3dc9-spvi`, por commits y en este orden:**
   `dda0408` (T1.4) · `bacd303` · `cc30f77` · `71e5875` · `0b5b765` (T2.1, solo si D1 = encendido) · `e687bbb` · `3bf4fc1` (T2.2 y T2.3) · `43cc5df` (T2.4 y T2.5) · `9aabb7b` (T3, T4.2–T4.4; CI roja).
   Ejecuta `spviTests` tras cada commit o grupo pequeño. Si uno falla, corrígelo con el cambio mínimo. Si no lo consigues, sepáralo y repórtalo: no lo arregles tapando el test.

3. **Documentos.** No integres las reescrituras de `AGENTS.md`, `Contexto.md`, `README.md` ni `RELEASE.md` de `c64c3dc9` ni de `d7f67f1f`. Si un cherry-pick las trae, quédate con la versión de esta rama (`git checkout --ours -- <archivo>`) y aplica a mano solo los cambios concretos que falten: versión de la base de datos v11, enlaces a documentos existentes, escáner retirado y `docs/VERIFICACION.md`.

4. **Reglas.** Comprueba que `AGENTS.md` conserve estas reglas, y si no, restáuralas desde `origin/main`:
   - «nunca leer SMS ni el portapapeles en segundo plano»;
   - «SPVI2:» como única licencia activable;
   - «la firma del release debe ser siempre la misma (deviceId)»;
   - la sección «Trampas conocidas».

5. **CI roja de `9aabb7b`.** Reproduce en local, en este orden: `spviTests`, `:data:compileDebugAndroidTestKotlin`, `:app:compileDebugAndroidTestKotlin`, `:app:lintDebug`, `:app:assembleRelease`, `spviPermisos`, `python tools/verificacion/api_minima.py` y `python tools/verificacion/documentacion.py`. Corrige la causa. No amplíes `lint-baseline.xml` para tapar errores: solo se basilinan avisos de estilo, y revisa el diff.

## Fase 3 · Lo que falta y no existe en ninguna rama

1. **T3.2.** Compara la tabla de esquema del README con `Entities.kt` (Room v11), campo por campo. Corrige «v10» en README, `AGENTS.md` y `Contexto.md`.
2. **T3.3.** Quita el carácter U+FFFD de `ANALISIS_SPVI.md` (búscalo con `\uFFFD`).
3. **T3.5.** Pon 0.30.0 en la cabecera de `UI_UX_IX.md` y de `SECURITY.md`, si la integración no lo dejó ya.
4. **T3.1.** Quita las menciones al escáner de códigos de barras que queden: «escáner (CameraX + ML Kit)» en `Contexto.md`, «ML Kit (escáner)» en `AGENTS.md` y el comentario de escáner de `app/build.gradle.kts`. Si `tools/escaner/` sigue existiendo, elimínala: no compila.
5. **T3.4 y D3.** Según tu decisión.
6. **T2, criterio visual.** Tras `:app:recordRoborazziDebug`, revisa las capturas `*letra_200*` y las de textos largos. Si algo se corta o se parte, corrígelo con `heightIn`, `FlowRow` o `weight(1f, fill = false)`. Describe lo que has visto; no lo des por bueno sin mirarlo.
7. **T8.** Prepara la tabla «antes → después» de los textos técnicos que hayas cambiado. Va en el informe final.
8. **T1.5 (solo con D6 resuelto).** Comprueba el dispositivo con `adb devices -l` y ejecuta `.\gradlew.bat --no-configuration-cache "-Pkotlin.compiler.execution-strategy=in-process" :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=cu.spvi.app.integracion.RendimientoInicioTest"`. Anota modelo, versión de Android, RAM, tiempos y resultado de las aserciones. Sin dispositivo autorizado, marca `NO VERIFICADO`. Nunca uses el teléfono de uso diario.
9. **F5.** No la toques.

## Fase 4 · Verificación final

- `.\gradlew.bat spviCheck` debe terminar con código 0. Informa de los recuentos por módulo, de los fallos y del lint (errores y avisos).
- `.\gradlew.bat :app:recordRoborazziDebug` solo si cambiaste la interfaz. Revisa las imágenes que cambian.
- `.\gradlew.bat spviInstrumentedTests` solo con un dispositivo autorizado (D6).
- Actualiza `docs/VERIFICACION.md` con resultados reales: SHA, fecha, comando, código de salida y recuentos. No copies cifras de documentos antiguos.
- Añade una entrada en `docs/HISTORIAL_DESARROLLO.md` por cada cambio de comportamiento (regla 7 de `AGENTS.md`).

## Fase 5 · Entrega

- `git push origin arena/cb554258-spvi` tras cada fase.
- Si tienes `gh`, comprueba la CI de la rama con `gh run list --branch arena/cb554258-spvi --limit 3`. Si falla, corrige hasta dejarla en verde o repórtame el primer error.
- No abras PR. Pregúntame antes.

## Informe final (en español)

1. Tabla: tarea, estado (✅ verificado, 🧪 NO VERIFICADO, ⚠️ decisión pendiente, ❌ bloqueado), commit, archivos y tests añadidos.
2. Salida literal de `spviCheck`: código de salida y recuentos.
3. Lo que queda sin hacer y por qué.
4. Suposiciones que hayas tomado.
5. Las decisiones D1–D7 que sigan abiertas, con tu recomendación.
