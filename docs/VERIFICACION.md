# SPVI — estado de verificación

> Fuente de verdad para distinguir resultados medidos de tareas pendientes. Actualizado al revisar este árbol el **2026-10-07** (hora local). Los cambios actuales **no están verificados**: Java no está instalado en este entorno y no se ejecutaron Gradle, tests, lint ni compilación.

## 1. CI configurado

GitHub Actions ejecuta cinco trabajos en cada push a `main` o `arena/**`, en cada pull request y en `workflow_dispatch`: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml).

| Trabajo | Comprobación | Alcance |
|---|---|---|
| Tests JVM y compilación de instrumentados | `spviTests :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin` | Ejecuta los tests JVM y compila, pero no ejecuta, tests Android instrumentados |
| Lint | `:app:lintDebug` | Analiza la app con el baseline configurado |
| Release y debug | `:app:assembleRelease :app:assembleDebug` | R8, APK y chequeo de clases ausentes. CI pasa `GITHUB_REPOSITORY` como `spviGithubRepo`; release sin firmar por no disponer del keystore |
| Permisos | `spviPermisos` | Verifica manifiestos fusionados de debug y release |
| API mínima | `tools/verificacion/api_minima.py` | Busca llamadas a API posteriores a Android 26 sin protección de versión |

Gradle local/CI usa `--no-configuration-cache`; `gradle.properties` también lo deja desactivado para mantener el comportamiento reproducible hasta resolver un error de serialización de tareas/plugins.

## 2. Corridas remotas consultadas

| Fecha mostrada por GitHub | Commit | Corrida | Resultado medido |
|---|---|---|---|
| 2026-10-08 | `82672fdf` | [37715675514](https://github.com/rmdvcd/SPVI/actions/runs/37715675514) | **5/5 trabajos exitosos.** No se recuperaron los logs detallados; el endpoint de resultados respondió `EOF`, así que no se afirma un conteo de tests para esta corrida. |
| 2026-10-07 | `531cab3` | [37656256703](https://github.com/rmdvcd/SPVI/actions/runs/37656256703) | 5/5 trabajos; detalle histórico abajo. |
| 2026-10-07 | `f480e50` | [37653496967](https://github.com/rmdvcd/SPVI/actions/runs/37653496967) | 5/5 trabajos exitosos. |

La corrida más reciente consultada es anterior a los cambios sin commit que están en el árbol actual. **No demuestra que el estado actual compile ni que sus pruebas pasen.**

### Detalle disponible de `37656256703` (`531cab3`)

| Comprobación | Resultado histórico |
|---|---|
| Tests JVM | 825 ejecutados; 0 fallidos, 0 errores, 0 omitidos |
| Tests instrumentados | Compilados, no ejecutados (CI no levanta emulador) |
| Lint | 0 errores y 95 avisos con baseline vacío |
| R8 | Sin clases ausentes; APK release sin firmar generado |
| Permisos | Manifiestos dentro de la lista autorizada |
| API mínima | 3423 clases revisadas; 0 llamadas no permitidas |

Estas cifras corresponden a `531cab3`, no deben atribuirse a `82672fdf` ni a los cambios actuales.

## 3. Cambios actuales pendientes de ejecutar

- [ ] `./gradlew spviTests`
- [ ] `./gradlew :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin`
- [ ] `./gradlew :app:lintDebug`
- [ ] `./gradlew :app:assembleRelease :app:assembleDebug -PspviGithubRepo=rmdvcd/SPVI`
- [ ] `./gradlew spviPermisos`
- [ ] `python3 tools/verificacion/api_minima.py` después de compilar bytecode debug
- [ ] Tests instrumentados en emulador/teléfono y checklist de [PRUEBAS_DISPOSITIVO.md](../PRUEBAS_DISPOSITIVO.md)
- [ ] Regenerar/verificar las capturas Roborazzi: cambiaron textos del respaldo y se añadieron estados de advertencia/confirmación sin contraseña (con y sin biometría)
- [ ] Prueba de humo de APK release firmado con el keystore real

En este contenedor `java -version` terminó en `java: command not found`; por esa razón no se intentó ninguna compilación local. El último CI verde no sustituye las comprobaciones pendientes de esta rama.

## 4. Reproducir en un equipo con JDK 17 y SDK 35

```bash
./gradlew spviTests :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin
./gradlew :app:lintDebug
./gradlew :app:assembleRelease :app:assembleDebug -PspviGithubRepo=rmdvcd/SPVI
./gradlew spviPermisos
python3 tools/verificacion/api_minima.py
./gradlew spviInstrumentedTests  # con teléfono o emulador API 26+
```

Para generar/comparar capturas Robolectric, ver [CAPTURAS.md](../CAPTURAS.md). Para compilar y publicar, seguir [RELEASE.md](../RELEASE.md).
