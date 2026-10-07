# SPVI — Verificación real

> **Única fuente de verdad** del estado de verificación del proyecto: lo que diga este archivo es lo que se ha
> medido; lo que no esté aquí, no está verificado. Última actualización: **2026-10-07**, commit `f480e50`.
>
> Regla permanente (F6 del plan de correcciones): cada release añade o actualiza una fila de la tabla de
> corridas. El README ya no lleva historial de verificaciones: apunta aquí.

---

## 1. Cómo se verifica hoy

GitHub Actions, en cada push a `main` o a una rama `arena/**`, en cada *pull request* y a mano
(`workflow_dispatch`): [`.github/workflows/ci.yml`](../.github/workflows/ci.yml). El repositorio es público, así
que los minutos son gratis e ilimitados.

Cinco trabajos independientes; cada uno es un comando del proyecto:

| Trabajo | Comando | Qué demuestra |
|---|---|---|
| Tests JVM y compilación de instrumentados | `./gradlew spviTests :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin` | Que los tests JVM pasan y que los instrumentados compilan |
| Lint (baseline) | `./gradlew :app:lintDebug` | Que `lint-baseline.xml` sigue vacío: cualquier aviso nuevo rompe la corrida |
| Release (R8) y APK debug | `./gradlew :app:assembleRelease :app:assembleDebug` | Que R8 minifica sin clases ausentes y que salen los APK |
| Permisos del manifiesto fusionado | `./gradlew spviPermisos` | Que ni debug ni release piden permisos fuera de la lista autorizada |
| API mínima 26 | `python3 tools/verificacion/api_minima.py` | Que ninguna clase compilada llama a una API posterior a Android 8 sin comprobar la versión |

Cuando un trabajo falla, `tools/verificacion/resumen_fallos.sh` publica lo esencial en las anotaciones del
*check run*; cuando pasa, `tools/verificacion/resumen_ok.py` publica las cifras como avisos. Así el resultado es
legible desde la API de GitHub y no solo desde la interfaz web.

## 2. Corridas

| Fecha | Commit | Corrida | Trabajos | Resultado |
|---|---|---|---|---|
| 2026-10-07 | `f480e50` | [37653496967](https://github.com/rmdvcd/SPVI/actions/runs/37653496967) | 5/5 | **Todo en verde** (~11 min) |

### Detalle de la corrida del 2026-10-07

| Comprobación | Resultado medido |
|---|---|
| Tests JVM | **825 ejecutados, 0 fallidos, 0 errores, 0 omitidos** |
| Compilación de los instrumentados | Compilan los de `:data` y `:app` (no se ejecutan: ver §3) |
| Lint | **0 errores, 95 avisos** con el baseline vacío |
| R8 (release) | **Sin clases ausentes**; `app-release-unsigned.apk` de 45,4 MB (sha256 `fba04320c6ae9b89…`) |
| APK debug | `app-debug.apk` de 58,7 MB (sha256 `e723b4f6a37e2a34…`) |
| Permisos | Manifiesto fusionado (debug y release) dentro de la lista autorizada |
| API mínima | **3423 clases revisadas, 0 llamadas no permitidas** |

Los artefactos se descargan desde la página de la corrida: `apk` (debug + release sin firmar, 30 días) e
`informe-lint` (14 días).

### Cuadre de los «825» (para que el número sea comprobable)

`src/test` tiene **967** anotaciones `@Test`; **142** pertenecen a `app/src/test/kotlin/cu/spvi/app/capturas/` y
quedan excluidas de `test`, `spviTests` y `spviCheck` (solo se ejecutan con las tareas de Roborazzi, ver
`app/build.gradle.kts`). 967 − 142 = **825**, exactamente lo que corrió el CI.

## 3. Lo que NO está verificado (y por tanto no se puede afirmar)

| Pendiente | Por qué | Cuándo |
|---|---|---|
| Los **136 tests instrumentados** | El CI no levanta un emulador y todavía no hay teléfono conectado | F0.6 / F6 (emulador en el CI o tu teléfono) |
| **Capturas de pantalla (Roborazzi)** | Hay 142 casos × 2 temas = 284 PNG en `app/capturas/`, pero el CI ni los regenera ni los compara: pueden quedar desfasados sin que nadie lo note | A mano con `recordRoborazziDebug`; evaluar compararlas en el CI |
| **APK release firmado y AAB** | El CI no tiene el keystore (nunca va al repositorio); la firma es local (RELEASE.md) y **nunca se ha hecho** | F0.6 |
| **La app en un teléfono real** | Nunca se ha instalado, en ninguna versión | F0.6 (T0.6) |
| **La prueba de dos teléfonos** (principal + secundaria vendiendo) | Requiere los dos aparatos | F0.6 / F6 |
| **`tools/escaner/*`** | Arranque del escáner retirado en la 0.28.0: no entra en ninguna tarea, no compila y nadie lo nota | F3 (sale del repositorio o se archiva) |
| **`tools/verificacion/verificar.sh`** | Compilación sin Gradle para entornos sin SDK; el CI no la usa y nadie la ha ejecutado desde que se escribió | Sin decisión; puede quedarse como herramienta de emergencia |

## 4. Cómo se reproduce en tu equipo

```bash
./gradlew spviCheck                     # tests JVM + lintDebug + assembleDebug + spviPermisos
./gradlew spviRelease                   # + AAB y APK firmados (requiere keystore.properties; ver RELEASE.md)
./gradlew :app:recordRoborazziDebug     # regenera app/capturas/{claro,oscuro}
./gradlew spviInstrumentedTests         # con un teléfono o emulador API 26+
python3 tools/verificacion/api_minima.py  # después de assembleDebug, con JDK 17 y ANDROID_HOME
```

## 5. Lo que la documentación decía y lo que es cierto

- **Sí hubo una compilación con Gradle real antes del CI**, pero de una versión anterior: `docs/HISTORIAL_DESARROLLO.md`
  (§0.27.1, P79) documenta la primera, en un equipo Windows, con 850 tests JVM, 154 capturas × 2, lint sin errores,
  R8 sin clases ausentes y `api_minima.py` en 0 llamadas. Ese historial es creíble (enumera errores reales de
  compatibilidad con Android 8 que encontró). Pero `AGENTS.md` decía que Gradle real, lint, R8 y Roborazzi «nunca» se
  habían ejecutado: dos relatos incompatibles en el mismo repositorio.
- **El código actual no estaba verificado por nadie.** La compilación de 0.27.1 es anterior a la retirada del escáner
  en línea (0.28.0) y a la versión 0.30.0; sus números ya no describen este código (hoy: **825 tests JVM** y **142
  capturas por tema**; la diferencia con los 850/154 de entonces no se ha medido, es coherente con el código
  retirado). La corrida del 2026-10-07 es la primera que compila y prueba **este** estado.
- **El repositorio no se podía compilar desde su propio clon en Linux**: `gradlew` estaba guardado sin permiso de
  ejecución (modo 644). Al primer intento el CI murió con `Permission denied` (exit 126). Corregido en el commit
  `dfcac65`. Es un síntoma de que todos los intentos anteriores fueron desde Windows o con herramientas externas.
- **No existía CI**: ningún push se había verificado nunca. El README además decía que el archivo de CI «está
  preparado pero inactivo» cuando el archivo no existía.
- Números a corregir en la fase documental (F3): el esquema de la base de datos es el **11**, no el 10, y
  `strings.xml` y el README todavía describen el escáner retirado.
