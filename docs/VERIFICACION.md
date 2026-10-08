# SPVI — Verificación

> Única fuente de verdad para los resultados observados. Última actualización documental: **2026-10-08**.
> No se considera aprobada una tarea sin un resultado para el commit exacto. La compilación de instrumentados no es su ejecución; generar capturas no es revisarlas.

## 1. Flujo acordado

El propietario indicó que OpenCode CLI en su PC ejecuta compilación, app y pruebas. El agente de Arena solo analiza y modifica archivos: no ejecuta Gradle, emuladores, `adb`, tests ni Roborazzi, y no continúa el seguimiento de una CI remota que el propietario haya dejado sin consultar.

Los workflows de GitHub siguen configurados para cada push a `main` o `arena/**`, pull request y ejecución manual: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml). La versión actual del workflow tiene seis trabajos:

| Trabajo | Comando principal | Alcance |
|---|---|---|
| JVM + compilación de instrumentados | `./gradlew spviTests :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin` | Ejecuta los tests JVM y compila los instrumentados; no los ejecuta |
| Lint | `./gradlew :app:lintDebug` | Revisa lint con el baseline del proyecto |
| Release y debug | `./gradlew :app:assembleRelease :app:assembleDebug` | Compila APK; el release de CI no lleva la firma privada |
| Permisos | `./gradlew spviPermisos` | Revisa el manifiesto fusionado frente a la lista autorizada |
| API mínima | `./gradlew :app:assembleDebug` y `python3 tools/verificacion/api_minima.py` | Busca llamadas incompatibles con Android 8/API 26 |
| Documentación | `python3 tools/verificacion/documentacion.py` | Revisa enlaces del README, coincidencia de versión y columnas de Room documentadas |

Los comandos Gradle del workflow usan `--no-configuration-cache` para mantener reproducibilidad. El trabajo de documentación es nuevo en los cambios de esta revisión y todavía no tiene un resultado observado.

## 2. Corridas observadas

| Fecha | Commit | Run | Resultado conocido |
|---|---|---|---|
| 2026-10-08 | `3bf4fc1` | [37834591679](https://github.com/rmdvcd/SPVI/actions/runs/37834591679) | **5/5 jobs verdes.** 836 tests JVM: 0 fallidos, 0 errores, 0 omitidos. Lint 0 errores; R8 sin clases ausentes; permisos autorizados; API mínima, 3440 clases revisadas y 0 llamadas no permitidas. Los instrumentados compilaron, no se ejecutaron. |
| 2026-10-08 | `43cc5df` | [37836886846](https://github.com/rmdvcd/SPVI/actions/runs/37836886846) | **Estado final desconocido.** En la última observación, lint, tests JVM + compilación de instrumentados, permisos y API mínima constaban aprobados; Release/R8 seguía en curso. El seguimiento se detuvo antes del resultado final. No contar como corrida verde ni inferir el resultado. |
| 2026-10-08 | `e687bbb` | [37832730353](https://github.com/rmdvcd/SPVI/actions/runs/37832730353) | 5/5 jobs verdes; 832 tests JVM sin fallos, errores u omisiones. Los instrumentados solo compilaron. |
| 2026-10-08 | `cc30f77` | [37766967443](https://github.com/rmdvcd/SPVI/actions/runs/37766967443) | 5/5 jobs verdes; 831 tests JVM. Incluye la paridad de la agregación SQL de T1.4. |

La última **CI completa confirmada** es `37834591679`, para `3bf4fc1`. No prueba cambios posteriores.

## 3. Estado del código posterior a la última CI completa

`43cc5df` añadió T2.4 y T2.5 y ocho tests JVM. Si todos pasan, el conteo esperado sube de 836 a **844**, pero no se ha ejecutado ni confirmado. Las ediciones de documentación, limpieza y workflow que aparecen en el árbol actual tampoco tienen compilación, ejecución ni CI observadas.

- **T2.4 — caché del registro de prueba:** implementación en memoria con TTL de seis horas, cambio de día local o contexto del permiso como causas de lectura nueva; escrituras propias actualizan la caché. Tests nuevos pendientes de OpenCode CLI.
- **T2.5 — estado de actualizaciones:** guarda la fecha de la última respuesta correcta y muestra aviso en Ajustes tras 14 días, si nunca respondió correctamente o si el reloj retrocedió, cuando el repositorio está configurado. Tests y captura nuevos pendientes.
- No se cambió el esquema Room, la versión de protocolo de sincronización ni los formatos de respaldo.

## 4. Lo que falta comprobar

| Pendiente | Estado |
|---|---|
| Tests JVM de T2.4/T2.5 y del árbol actual | No ejecutados por el agente; esperar el resultado de OpenCode CLI en el PC del propietario |
| Instrumentados | Las últimas CI los compilaron, no los ejecutaron. Ejecutar `./gradlew spviInstrumentedTests` en teléfono o emulador |
| **T1.5 — rendimiento y Perfetto** | No hay nueva medición de `RendimientoInicioTest` con 18 meses ni trazas antes/después. El test usa Room en memoria; no requiere borrar la base instalada |
| **Capturas Roborazzi** | El árbol contiene 142 PNG por tema; hay 147 casos. Faltan cinco baselines por tema (dos de Ajustes y tres tablet) y su revisión visual |
| **T2.1** | Falta generar y revisar la captura de la advertencia al exportar sin contraseña |
| **T2.2** | Prueba manual de tres teléfonos dispensada por solicitud del propietario. Implementación/tests se conservan; no se registra validación física |
| **T2.5** | Falta generar y revisar la captura del aviso de actualización atrasada |
| App en teléfono real | No hay evidencia para esta revisión |
| Release firmado / AAB | La firma requiere el keystore local del propietario; no se sube al repositorio |
| Script y job de documentación | Están escritos en el árbol, pero no ejecutados ni validados por CI |

## 5. Comandos para el PC del propietario

Ejecutar mediante OpenCode CLI desde la raíz del clon, con JDK 17 y Android SDK 35:

```bash
./gradlew spviCheck
./gradlew :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin
./gradlew spviInstrumentedTests
./gradlew :app:recordRoborazziDebug
python3 tools/verificacion/api_minima.py
python3 tools/verificacion/documentacion.py
```

`spviCheck` no ejecuta instrumentados ni actualiza las capturas. Tras una prueba, registrar el commit, comando, resultado final, conteos, teléfono/API, capturas generadas y cuáles se revisaron. La guía está en [PRUEBAS_DISPOSITIVO.md](../PRUEBAS_DISPOSITIVO.md).

## 6. Historial y cautela

Las cifras históricas describen solo el commit asociado a cada corrida. No trasladar el número de tests, resultado de R8, permisos o capturas de una versión anterior al árbol actual. Si no se observa el final de una corrida, registrar «desconocido» y esperar el informe local del propietario en vez de completar el dato por inferencia.
