# SPVI — Verificación real

> **Fuente única de verdad** del estado de verificación: solo se considera medido lo anotado aquí. Última actualización: **2026-10-18**. El repositorio mantiene la app Android nativa; Flutter/iOS y la web de escritorio se retiraron.

## Cambio en curso: cinco correcciones de la revisión del dueño (2026-10-18, sin commitear)

Alerta de Inicio → Inventario filtrado, botón «Ver la venta» centrado en la ficha de una transferencia, nombres del catálogo sin unidad pegada, auditoría de precios congelados y conteo de billetes visible con «Omitir» al cerrar turno.

- `./gradlew spviTests` (2026-10-18): **896 tests JVM en verde**, sin red ni dispositivo (891 antes de este lote + 5 nuevos).
- `./gradlew spviCheck` (2026-10-18): **OK** (tests JVM + `lintDebug` + `assembleDebug` + `spviPermisos`).
- `./gradlew verifyRoborazziDebug` (2026-10-18): **527 capturas en verde**. Cambiaban dos doradas y se regeneraron por decisión del dueño: `04p_registros_ficha_transferencia` (el botón ya sale centrado) y `01f_inicio_dialogo_cerrar_turno` (billetes a la vista y «Omitir» en la esquina), en claro y oscuro; ningún otro PNG cambia.
- `:data:connectedDebugAndroidTest` en el teléfono 4bc9f8f3 (Android 15, API 35), 2026-10-18: **60/60 en verde**. Incluye el caso nuevo `RepositoriosRoomTest.cambiarElPrecioNoAlteraElImporteDeLoVendido`, comprobado **en rojo** (esperado 30.00 CUP, obtenido 99.00 CUP al reescribir el detalle a propósito) y arregla los tres tests de migración de `EsquemaTest`, que fallaban con «duplicate column name: clienteFijo» por un recorte incompleto del fixture (fallo del test, no de las migraciones).
- Prueba de extremo a extremo en el teléfono 4bc9f8f3 (2026-10-18), APK debug con estos cambios:
  - **Alerta de Inicio → Inventario**: pendiente de repetir con este APK (el código es el de este lote; la prueba anterior del síntoma se hizo sobre el comportamiento viejo).
  - **Ficha de transferencia**: con el turno cerrado, «Ver la venta» centrado (x 515–568 de 1080; antes 156–209). Con el turno abierto y una transferencia del turno, la fila queda [Modificar venta][Anular venta][Ver la venta] (x 374–709, centro 541).
  - **Cierre de turno**: los campos por denominación se ven al abrir el diálogo; «Omitir el conteo de billetes» está en la esquina inferior derecha; 23 × 100 suma 2,300.00 CUP y muestra «Cuadra»; «Omitir el conteo de inventario» también en la esquina del paso de inventario y la revisión avisa «Conteo de inventario omitido: se cierra sin ajustar existencias.»; el turno **cerró** de verdad con ese conteo (sin error de inventario cambiado).
  - **Datos del teléfono**: la prueba abrió un turno (fondo 2,300.00), registró una venta por transferencia de prueba («Prueba Verificacion», 190.00 CUP, VERIF00001) y lo cerró con el conteo omitido; el teléfono queda como estaba, con el turno cerrado.
- **Pendiente antes de publicar**: regenerar los datos de ejemplo del teléfono para ver los nombres del catálogo sin unidad (el dato semilla solo se aplica al sembrar), correr CI y decidir si estos cambios se commitean con un mensaje descriptivo (el lote anterior quedó como `54c85f9 "j"`).

## Cambio en curso: editar importe, cantidad, método y nº en Registros (2026-10-18, `main`, `54c85f9`)

- `./gradlew spviTests` (2026-10-17): **891 tests JVM en verde**, sin red ni dispositivo.
- `./gradlew spviCheck`: **OK** (tests JVM + `lintDebug` + `assembleDebug` + `spviPermisos`).
- `./gradlew verifyRoborazziDebug`: 525 capturas; **1 fallo de comparación**, `01f_inicio_dialogo_cerrar_turno.png`, que corresponde al cambio de cierre con conteo ya en `main` y no se regenera aquí. Ninguna captura de Registros cambia (las doradas montan `AccionesRegistros()` sin acciones).
- `:data:TurnoRepositoryInstrumentedTest` en el teléfono 4bc9f8f3 (Android 15, API 35), 2026-10-17: **8/8 en verde**, incluidos los 2 casos nuevos de corrección de un ajuste (stock, movimiento y existencias posteriores). Los rojos se comprobaron antes de implementar.
- Prueba de extremo a extremo en el teléfono 4bc9f8f3 (2026-10-18), APK debug con estos cambios:
  - **Movimientos (turno abierto)**: un ajuste de +2 creado al editar la cantidad de un producto en Inventario se corrigió a +5 con motivo; el movimiento quedó en +5, «Existencia después» 123 y nota «Edición · Corregido: …», y el producto pasó a 123 u en Inventario.
  - **Movimientos (turno cerrado)**: ese mismo movimiento, ya con el turno cerrado, **no ofrece** «Editar ajuste» en la ficha.
  - **Transferencias (turno cerrado)**: la ficha solo ofrece «Ver la venta»; no aparecen las acciones de la venta.
  - **Transferencias (turno abierto)**: la ficha ofrece «Modificar venta», «Anular venta» y «Ver la venta»; desde «Modificar venta» se cambiaron la cantidad (190.00 → 380.00 CUP) y el nº de transacción (TRANS12345 → TRANS99999) con motivo obligatorio, y Registros muestra la transferencia nueva con el importe y el nº corregidos. El diálogo «Anular venta» avisa de que era una transferencia (se canceló, no se anuló).
- Versión rotulada **0.31.0**; el cambio quedó commiteado como `54c85f9`, que además versiona `data/schemas/cu.spvi.data.db.SpviDatabase/13.json` (pieza que faltaba del cierre con conteo). La corrida de CI de la rama sigue pendiente.

## Cambio en curso: cierre con conteo (2026-10-17, `arena/5c955caa-spvi`)

- `git diff --check`: pasó.
- Análisis sintáctico Tree-sitter de los 42 archivos Kotlin modificados/nuevos: sin errores de sintaxis; **no comprueba tipos, Room ni Hilt y no sustituye compilación**.
- Prueba Python/SQLite de `SQL_12_13` sobre `12.json`: pasó; conserva caja y añade `conteoCierreJson` nullable.
- `./gradlew spviTests`: **no pudo iniciar**, no hay Java ni JAVA_HOME.
- `python3 tools/verificacion/api_minima.py`: **no pudo ejecutarse**, falta ANDROID_HOME.
- Nuevos tests JVM/instrumentados y de sincronización: añadidos, **no ejecutados**.
- **Bloqueante antes de publicar:** ejecutar `spviCheck`, las migraciones instrumentadas, capturas y cierre/aprobación en dos teléfonos actualizados. En este entorno el código del cierre sí compiló y sus tests JVM corrieron dentro de `spviTests` (891 en verde, 2026-10-17) tras el merge `a293bad`; `13.json` está versionado desde `54c85f9`. Los resultados históricos de abajo no validan este cambio.

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

En esta rama, `app/build.gradle.kts` prepara el SDK instrumentado de Robolectric mediante Gradle y lo pasa en modo offline a **todos los tests JVM de `:app`**, no solo a las capturas Roborazzi. Esta corrección está **pendiente de CI**; localmente `spviTests` pasa en verde (2026-10-17) con ese SDK preparado por Gradle.

## 3. Verificaciones pendientes

| Pendiente | Estado |
|---|---|
| Confirmar la corrección de Robolectric | Esperar una corrida de CI de esta rama; localmente `spviTests` ya pasa (2026-10-17) con el SDK preparado por Gradle |
| Capturas Roborazzi | Ejecutadas el 2026-10-17: 525 capturas, 1 fallo de comparación en `01f_inicio_dialogo_cerrar_turno.png` (del cierre con conteo, no regenerada); ninguna captura de Registros cambia |
| Tests instrumentados de Room/UI | `:data:RepositoriosRoomTest`: 9/9 en verde el 2026-10-17 en el teléfono 4bc9f8f3 (Android 15, API 35). `:data:TurnoRepositoryInstrumentedTest`: 8/8 en verde el 2026-10-17, con los 2 casos nuevos de corrección de un ajuste. Los instrumentados de UI de `:app` siguen sin ejecutarse en esta revisión |
| Prueba de extremo a extremo en teléfono | Hecha para editar importe/cantidad/método/nº el 2026-10-18 en el teléfono 4bc9f8f3 (ver arriba); pendiente para el cierre con conteo |
| Sincronización principal/secundaria con dos teléfonos Android | Pendiente |
| Release firmado/AAB | No generado ni probado aquí; requiere el keystore fuera del repositorio |
| `tools/verificacion/verificar.sh` | No ejecutado en esta revisión; es una herramienta de verificación independiente que puede descargar compiladores/dependencias |

En esta rama (2026-10-17) sí hay JDK y Android SDK locales: `./gradlew spviTests` (869 tests JVM) y `./gradlew spviCheck` (tests + lint + `assembleDebug` + permisos) terminan en verde, y `:data:RepositoriosRoomTest` corre en el teléfono 4bc9f8f3. Lo pendiente para declarar la rama verificada sigue siendo la corrida de CI (job de tests JVM) y la prueba de la interfaz en el teléfono.

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
