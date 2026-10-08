# Trabajar con SPVI desde OpenCode Desktop

Guía vigente para orientar las tareas de compilación, verificación y prueba manual. El proyecto declara SPVI **0.30.0** (`versionCode 51`), Room **v11** y respaldo `.spvi` v4. El estado medido y las comprobaciones pendientes están en [docs/VERIFICACION.md](VERIFICACION.md); no se deben trasladar a los cambios actuales resultados de una corrida anterior.

## 1. Abrir y entender el proyecto

1. Abre la carpeta raíz `SPVI`.
2. Pide al agente que lea `AGENTS.md`, `README.md`, `docs/VERIFICACION.md` y el documento específico de la tarea. Para pruebas en teléfono usa [PRUEBAS_DISPOSITIVO.md](../PRUEBAS_DISPOSITIVO.md); para firma y publicación, [RELEASE.md](../RELEASE.md); para capturas, [CAPTURAS.md](../CAPTURAS.md).
3. Conserva los cambios existentes: antes de editar, revisa `git status` y el diff. No ejecutes una inicialización que reemplace `AGENTS.md` ni dependas de `Pendiente.md`, `Pruebas.md`, `opencode.json` o comandos `/...` que no forman parte de este repositorio.
4. Mantén los textos y la documentación en español. Si cambias comportamiento, actualiza también el manual/documentación pertinente y añade una entrada a [docs/HISTORIAL_DESARROLLO.md](HISTORIAL_DESARROLLO.md).

## 2. Preparar el entorno

| Requisito | Versión | Nota |
|---|---|---|
| JDK | 17 | Comprueba con `java -version`. El JBR de Android Studio sirve. |
| Android SDK | Platform 35 | El CI requiere también Build-Tools 35.0.0. |
| Gradle | 8.11.1 | Lo instala el wrapper del repositorio (`./gradlew`). |
| Dispositivo | Android 8.0 / API 26 o superior | Necesario para instrumentados y pruebas manuales; puede usarse un emulador. |

Configura `ANDROID_HOME` o crea `local.properties` local con `sdk.dir=...` (no se versiona). La primera compilación necesita internet para descargar dependencias. Mantén desactivado el configuration cache, tal como está configurado en `gradle.properties` y en CI.

## 3. Verificación local

Primero comprueba el entorno:

```bash
java -version
./gradlew --version
```

Después puedes reproducir por separado las comprobaciones del CI:

```bash
# Tests JVM y compilación de los tests instrumentados (estos no se ejecutan aquí)
./gradlew spviTests :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin

# Lint
./gradlew :app:lintDebug

# APK debug y release con R8. Release exige el repositorio público de actualizaciones.
./gradlew :app:assembleDebug :app:assembleRelease -PspviGithubRepo=rmdvcd/SPVI

# Manifiestos fusionados
./gradlew spviPermisos

# Ejecutar después de compilar el bytecode debug
python3 tools/verificacion/api_minima.py
```

`./gradlew spviCheck` agrupa tests JVM, lint, APK debug y permisos; no compila los tests instrumentados. `./gradlew spviRelease -PspviGithubRepo=rmdvcd/SPVI` añade los AAB y APK de release a esas comprobaciones. La firma requiere el keystore real; el CI genera el APK de release sin firmar.

**Estado de este árbol:** Java no está instalado en el entorno de desarrollo actual. No se ejecutaron Gradle, tests, lint, R8 ni compilaciones instrumentadas sobre los cambios locales; la última corrida remota está registrada por separado en [docs/VERIFICACION.md](VERIFICACION.md). No afirmes que el árbol actual pasó hasta que se verifique.

## 4. Pruebas con dispositivo

Los tests instrumentados necesitan un emulador o teléfono conectado con API 26 o superior:

```bash
./gradlew spviInstrumentedTests
```

El CI compila los instrumentados, pero no los ejecuta. Para migraciones de Room, permisos en Android, biometría, selector de archivos, QR/cámara, actualización y sincronización de dos teléfonos, sigue [PRUEBAS_DISPOSITIVO.md](../PRUEBAS_DISPOSITIVO.md). Antes de instalar un release, revisa [RELEASE.md](../RELEASE.md). Usa datos ficticios: importar un respaldo reemplaza los datos locales.

## 5. Capturas de interfaz

Las capturas Roborazzi se generan aparte y no se incluyen en `spviTests` ni `spviCheck`:

```bash
./gradlew :app:recordRoborazziDebug
```

Para revisar diferencias o detalles del arnés, consulta [CAPTURAS.md](../CAPTURAS.md). Robolectric no sustituye una prueba visual en un teléfono; en particular, no ejecuta la vista real de CameraX ni los diálogos del sistema.

## 6. Firma y publicación

`assembleRelease`, `bundleRelease` y `spviRelease` requieren `spviGithubRepo` con formato `propietario/repositorio`, porque la app consulta actualizaciones y revocaciones en ese destino. Usa `-PspviGithubRepo=rmdvcd/SPVI` o una configuración local no versionada. No guardes secretos en `gradle.properties` del repositorio.

Configura el keystore de release según [RELEASE.md](../RELEASE.md); no lo añadas al repositorio y conserva siempre la misma clave. No publiques hasta completar la verificación de código, la prueba de humo y la lista de dispositivo.
