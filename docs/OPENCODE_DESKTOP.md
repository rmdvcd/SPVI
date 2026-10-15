# Compilar y verificar SPVI Android con OpenCode Desktop

Esta guía usa OpenCode Desktop como **herramienta de desarrollo**; no describe una aplicación de escritorio de SPVI. El producto de este repositorio es Android nativo. El estado medido de cada verificación está en [VERIFICACION.md](VERIFICACION.md); lo pendiente, en [../Pendiente.md](../Pendiente.md).

## 1. Preparar el entorno

| Requisito | Versión | Nota |
|---|---|---|
| JDK | 17 | Comprueba con `java -version`; el JBR de Android Studio sirve |
| Android Studio | Ladybug (2024.2) o posterior | Instala Android SDK Platform 35 y Build-Tools 35 |
| SDK | API 35 | Configura `ANDROID_HOME` o `local.properties` (`sdk.dir=...`, no se sube a Git) |
| Teléfono | Android 8.0+ (API 26) | Activa Opciones de desarrollador y Depuración USB para las pruebas de dispositivo |

El wrapper descarga Gradle 8.11.1. La primera compilación resuelve las dependencias del proyecto; no se debe habilitar acceso a red desde los tests JVM.

## 2. Cargar el proyecto

1. Abre la carpeta raíz `SPVI` en OpenCode Desktop.
2. Lee `AGENTS.md`, `Contexto.md`, `Pendiente.md` y `Pruebas.md` antes de cambiar código.
3. Usa `docs/VERIFICACION.md` como fuente del resultado de CI más reciente. No repitas cifras de una versión antigua como si fueran actuales.
4. No ejecutes una inicialización que reescriba `AGENTS.md`; conserva las reglas y la arquitectura Android.

## 3. Compilar y ejecutar tests JVM

```bash
./gradlew --version
./gradlew spviTests
./gradlew spviCheck
```

`spviTests` ejecuta las pruebas JVM; `spviCheck` añade lint, `assembleDebug` y comprobación de permisos. En `:app`, la configuración prepara el artefacto `android-all-instrumented` requerido por Robolectric mediante Gradle y ejecuta los tests offline. Si Robolectric intenta resolverlo por internet o no encuentra el SDK local, registra el error y revisa `app/build.gradle.kts` y `docs/VERIFICACION.md`; no desactives la restricción de red de los tests.

Para un test concreto:

```bash
./gradlew :domain:test --tests "cu.spvi.domain.EstadoPruebaTest"
```

## 4. Capturas e instrumentados

```bash
./gradlew :app:recordRoborazziDebug  # capturas de UI claro/oscuro; ver CAPTURAS.md
./gradlew spviInstrumentedTests      # requiere emulador o teléfono API 26+
```

Revisa las capturas antes de aceptar cualquier cambio visual. Los instrumentados requieren dispositivo; no los declares ejecutados si solo compilaron.

## 5. Prueba real de Android

Con el teléfono conectado:

```bash
./gradlew :app:installDebug
```

Sigue [../PRUEBAS_DISPOSITIVO.md](../PRUEBAS_DISPOSITIVO.md) para probar flujos y permisos. La sincronización y migración deben comprobarse con dos teléfonos Android; no basta con mocks ni con compilar.

## 6. Release

Sigue [../RELEASE.md](../RELEASE.md). El keystore, las contraseñas y los artefactos firmados deben permanecer fuera del repositorio. Antes de distribuir, verifica APK/AAB firmado, instala el APK de release y completa la prueba de humo.
