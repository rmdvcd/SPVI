# SPVI — Verificación real

> **Fuente única de verdad** del estado de verificación: solo se considera medido lo anotado aquí. Última actualización: **2026-10-15**. El repositorio mantiene la app Android nativa; Flutter/iOS y la web de escritorio se retiraron.

## 1. CI actual

GitHub Actions ejecuta el workflow [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) en pushes a `main` y ramas `arena/**`, pull requests y ejecuciones manuales.

| Trabajo | Comando principal | Qué comprueba |
|---|---|---|
| Tests JVM y compilación de instrumentados | `./gradlew spviTests :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin` | Tests unitarios JVM y compilación de tests instrumentados |
| Lint | `./gradlew :app:lintDebug` | Lint Android con el baseline versionado |
| Release sin firma y APK debug | `./gradlew :app:assembleRelease :app:assembleDebug` | Compilación, R8 y generación de APKs |
| Permisos | `./gradlew spviPermisos` | Permisos de los manifiestos fusionados debug/release |
| API mínima 26 | `python3 tools/verificacion/api_minima.py` | Llamadas a APIs Android posteriores a API 26 |

El job de emulador instrumentado se ejecuta en la programación semanal y en disparos manuales (`workflow_dispatch`), no en cada push; no se considera validado por defecto. Los tests unitarios no deben salir a internet. En `:app`, Robolectric recibe el SDK instrumentado local resuelto por Gradle y se ejecuta en modo offline.

## 2. Última corrida observada

| Fecha | Commit | Corrida | Resultado |
|---|---|---|---|
| 2026-10-15 | `84afc87` | [38802995010](https://github.com/rmdvcd/SPVI/actions/runs/38802995010) | **Falló el job de tests JVM**; lint, permisos, API mínima y release/debug pasaron. El job de emulador quedó omitido. |

Detalle de esa corrida, anterior a la corrección en esta rama:

| Comprobación | Resultado medido |
|---|---|
| Tests JVM | Dos tests de `ProcesoActualizarTest` no pudieron obtener `org.robolectric:android-all-instrumented:15-robolectric-12650502-i7`; el proceso de tests no tenía acceso a red para resolverlo. No fue un fallo de aserción funcional. |
| Lint | **0 errores, 110 avisos** |
| Permisos | Pasaron |
| API mínima | **3.553 clases revisadas, 0 llamadas no permitidas** |
| Release/debug | Compilaron; R8 no reportó clases ausentes y se publicaron los APKs sin firma como artefactos de CI |
| Instrumentados en emulador | Omitidos en esa corrida |

En esta rama, `app/build.gradle.kts` prepara el SDK instrumentado de Robolectric mediante Gradle y lo pasa en modo offline a **todos los tests JVM de `:app`**, no solo a las capturas Roborazzi. Esta corrección está **pendiente de CI**: no hay JDK ni Android SDK instalados en este entorno y no se ha ejecutado localmente.

## 3. Verificaciones pendientes

| Pendiente | Estado |
|---|---|
| Confirmar la corrección de Robolectric | Esperar una corrida de CI de esta rama; no declarar resuelto hasta que `spviTests` pase |
| Capturas Roborazzi | No ejecutadas en esta revisión; regenerar y revisar claro/oscuro si cambia UI |
| Tests instrumentados de Room/UI | No ejecutados en dispositivo/emulador durante esta revisión |
| Prueba de extremo a extremo en teléfono | Pendiente |
| Sincronización principal/secundaria con dos teléfonos Android | Pendiente |
| Release firmado/AAB | No generado ni probado aquí; requiere el keystore fuera del repositorio |
| `tools/verificacion/verificar.sh` | No ejecutado en esta revisión; es una herramienta de verificación independiente que puede descargar compiladores/dependencias |

No se ejecutaron tests ni Gradle localmente: `java`/`JAVA_HOME` no están disponibles. No se debe describir la verificación de esta rama como verde hasta que pase CI.

## 4. Reproducir

Con JDK 17 y Android SDK 35 configurados:

```bash
./gradlew spviTests                     # todos los tests JVM
./gradlew spviCheck                     # tests JVM + lint + assembleDebug + permisos
./gradlew :app:recordRoborazziDebug     # capturas claro/oscuro
./gradlew spviInstrumentedTests         # requiere teléfono/emulador API 26+
./gradlew spviRelease                   # APK/AAB; firma si se configura el keystore
python3 tools/verificacion/api_minima.py # después de compilar bytecode debug
```

Añadir a esta tabla cada nueva corrida con fecha, SHA, resultado por job y cualquier límite que no haya sido comprobado. Mantener el historial anterior en `docs/HISTORIAL_DESARROLLO.md`; sus métricas pertenecen a las versiones y commits que menciona, no al estado actual.
