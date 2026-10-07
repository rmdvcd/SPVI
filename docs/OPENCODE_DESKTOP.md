# Compilar y dar los retoques finales con OpenCode Desktop

Guía para llevar SPVI **0.26.0** desde esta carpeta hasta un APK probado en el teléfono, usando OpenCode Desktop como asistente. El código ya pasa la verificación sin Gradle (`tools/verificacion/verificar.sh`: 823 tests JVM + 38 de Room). Lo que **nunca** se ha hecho es lo de esta guía: Gradle real, lint, R8, Roborazzi y el teléfono.

OpenCode lee `AGENTS.md` (raíz) en cada sesión y, por `opencode.json`, también `Contexto.md` y `Pendiente.md`. `Pruebas.md` tiene el orden de pruebas. El PDF de capturas está en la raíz (excluido de git). Los comandos `/compilar`, `/probar`, `/capturas`, `/release` y `/retoque` están en `.opencode/commands/`.

## 1. Preparar el ordenador (una vez)

| Qué | Versión | Nota |
|---|---|---|
| JDK | **17** | Android Studio trae uno («JetBrains Runtime 17»). Comprueba con `java -version`. |
| Android Studio | Ladybug (2024.2) o posterior | Instala con él **SDK Platform 35** y **Build-Tools 35**. |
| `ANDROID_HOME` | ruta del SDK | O crea `local.properties` en la raíz con `sdk.dir=/ruta/al/Android/Sdk` (no se sube a git). |
| OpenCode Desktop | la última | Abre la carpeta `SPVI` como proyecto. Elige un modelo con buena capacidad para Kotlin. |
| Teléfono | Android 8.0+ (API 26) | Activa **Opciones de desarrollador → Depuración USB**. |

La primera compilación descarga Gradle 8.11.1 y las dependencias (~1 GB). Necesita internet una vez; después funciona sin conexión.

## 2. Abrir el proyecto en OpenCode

1. **File → Open folder →** `SPVI`.
2. En la primera sesión escribe: `Lee AGENTS.md y docs/OPENCODE_DESKTOP.md y resume en 5 líneas qué vas a respetar.` Así compruebas que cargó las reglas.
3. **No** ejecutes `/init`: reescribiría `AGENTS.md`. Si lo haces, revisa que no se pierdan las reglas.

## 3. Primera compilación — `/compilar`

```bash
./gradlew --version            # Gradle 8.11.1, JVM 17
./gradlew :app:assembleDebug
```

Errores esperables en la primera vez y qué hacer:

| Síntoma | Causa probable | Arreglo |
|---|---|---|
| `SDK location not found` | falta `local.properties` o `ANDROID_HOME` | ver §1 |
| `Unsupported class file major version` | JDK distinto de 17 | `JAVA_HOME` al JDK 17 o *Gradle JDK* en Android Studio |
| Error de KSP en Room o Hilt | caché de una compilación anterior | `./gradlew clean` y repetir |
| Lint: `MissingTranslation`, `UnusedResources`… | lint nunca se ha ejecutado | arreglar o, solo si es un falso positivo, anotarlo en `app/lint.xml` explicando el motivo |
| Resolución de dependencias | red o repositorio caído | repetir; las versiones están fijas en `gradle/libs.versions.toml` |

Pide a OpenCode que **corrija solo lo que falla**, sin refactorizar, y que repita hasta que compile. El APK queda en `app/build/outputs/apk/debug/app-debug.apk` (paquete `cu.spvi.app.debug`).

## 4. Tests y comprobación completa — `/probar`

```bash
./gradlew spviTests     # deben salir 823 tests JVM (core 16, licencia 98, domain 238, data 92, designsystem 26, app 353)
./gradlew spviCheck     # tests + lintDebug + assembleDebug + spviPermisos
```

Si una cifra no coincide, que OpenCode compare con `README.md` («Última verificación») antes de tocar nada: un test que Gradle ejecuta y `verificar.sh` no (o al revés) se explica, no se borra.

Los 37 tests de Room de `data/src/androidTest` corren de verdad en un emulador o teléfono con `./gradlew spviInstrumentedTests`.

## 5. Capturas reales — `/capturas`

```bash
./gradlew :app:recordRoborazziDebug
```

Genera las capturas claro/oscuro en `app/build/outputs/roborazzi/` (ver `CAPTURAS.md`). Compáralas con el PDF entregado `SPVI_0.26.0_capturas_y_exportaciones.pdf`: las maquetas de ese PDF son HTML hechas a mano, así que la **referencia es la captura real**. Si ves cortes de texto, contraste bajo o diferencias de diseño, pide el arreglo con `/retoque`.

## 6. En el teléfono

1. `./gradlew :app:installDebug` (teléfono conectado) o copia el APK.
2. Sigue la tabla de **RELEASE.md §6** (pruebas de humo 1–20). Las filas 13–16 son de la 0.25.1 y las 17–20, de la 0.26.0 (fondo asignado, reinstalar sin reiniciar la prueba, con y sin permiso de fotos, actualización obligatoria):
   - compartir un turno en PDF y Excel;
   - modificar una venta añadiendo un artículo y cambiando a Transferencia;
   - en una secundaria, «Ahora no» y después «Contar»;
   - licencia vencida → campo de recuperación.
3. **Dos teléfonos** (principal + secundaria en la misma wifi):
   - vincular con el QR;
   - vender en la secundaria y ver la venta en la principal;
   - pedir el cierre desde la principal y contar la caja en la secundaria;
   - aprobar el cierre;
   - repartir una actualización por la red local.
4. Abre los Excel exportados con Excel o LibreOffice: **no** debe salir «encontró un problema con el contenido».

Anota lo que falle (pantalla, pasos, qué esperabas) y pásaselo a OpenCode con `/retoque`.

## 7. Release firmado — `/release`

Sigue **RELEASE.md** desde §1:

1. Crea el keystore **una sola vez** y guárdalo en dos sitios.
2. Crea `keystore.properties`.
3. Si ya tienes el repositorio de GitHub para las actualizaciones, añade `spviGithubRepo=usuario/repositorio` en `gradle.properties`. Sin él, la app no consulta GitHub: ni actualizaciones ni revocaciones.
4. Ejecuta `./gradlew spviRelease`.
5. Prueba el APK **de release** en el teléfono (R8 puede romper la serialización: revisa `missing_rules.txt` y añade reglas en `app/proguard-rules.pro`).

## 8. Retoques pendientes conocidos

| # | Pendiente | Dónde | Cómo |
|---|---|---|---|
| 1 | Repositorio GitHub de actualizaciones sin configurar | `gradle.properties` → `spviGithubRepo` | Crear el repo público y publicar la release (RELEASE.md §8) |
| 2 | Lint nunca ejecutado | `./gradlew :app:lintDebug` | Corregir avisos reales; no desactivar reglas en bloque |
| 3 | R8 nunca probado | `assembleRelease` | Probar compra, respaldo, licencia y sincronización con el APK de release |
| 4 | Capturas Roborazzi nunca generadas | §5 | Revisar textos cortados con letra al 200 % |
| 5 | Prueba con dos teléfonos | §6.3 | Vinculación, sincronización, cierre pedido, APK por LAN |
| 6 | PDF real en el teléfono | Registros/Turnos → Compartir → PDF | Confirmar que coincide con las muestras del PDF entregado (hojas horizontales con más de 5 columnas) |
| 7 | GL (generador de licencias) | `docs/GL_PROMPT_0.25.md` | Recuperación automática y `revocadas.json` firmado |

## 9. Cómo pedir cambios a OpenCode (plantilla)

```
/retoque <pantalla o archivo>: <qué pasa> → <qué quieres>.
Ejemplo: /retoque Registros → Turnos → Compartir: con letra al 200 % el texto de la hoja se corta → que ocupe 3 líneas.
```

El comando recuerda las reglas: cambio mínimo, español, design system, sin permisos nuevos, test si cambia la lógica, `spviTests` en verde y una entrada en `docs/HISTORIAL_DESARROLLO.md`. Si el cambio altera lo que ve el usuario, también actualiza `MANUAL_USUARIO.md`.
