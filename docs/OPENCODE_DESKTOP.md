# OpenCode CLI en el PC del propietario

Guía del flujo local acordado para SPVI. Esta sesión de Arena analiza y modifica el repositorio, pero no compila, ejecuta la app ni corre pruebas. El propietario ejecuta esos pasos con OpenCode CLI en su PC y comparte los resultados necesarios para actualizar [docs/VERIFICACION.md](VERIFICACION.md).

## 1. Preparar el entorno

| Requisito | Valor |
|---|---|
| JDK | 17 |
| Android SDK | Platform 35 y Build Tools 35.0.0 |
| Android mínimo de pruebas | API 26 o posterior |
| Teléfono | Opciones de desarrollador y depuración USB para instrumentados o prueba manual |
| Gradle | Usar `./gradlew`; la versión está fijada en `gradle/wrapper/gradle-wrapper.properties` |

Configura `ANDROID_HOME` o `sdk.dir` en `local.properties`. No versionar ese archivo. La primera ejecución puede necesitar conexión para descargar Gradle y dependencias.

## 2. Abrir SPVI con OpenCode CLI

1. Abre la raíz del clon de SPVI.
2. Pide a OpenCode que lea `AGENTS.md`, `Pendiente.md`, `PRUEBAS_DISPOSITIVO.md` y `docs/VERIFICACION.md`.
3. Indica el commit exacto que se quiere comprobar.
4. Mantén datos de prueba ficticios; no compartas contraseñas, claves, licencias ni datos de clientes.

Prompt sugerido:

> Lee AGENTS.md y Pendiente.md. Ejecuta únicamente el comando de validación que te indique, desde la raíz del proyecto. No cambies código ni borres cachés si aparece un error: resume el comando, el código de salida y las primeras causas concretas. No declares aprobadas las tareas que no ejecutó.

## 3. Orden recomendado de validación

Ejecuta las tareas necesarias desde la raíz:

```bash
./gradlew --version
./gradlew spviCheck
./gradlew :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin
./gradlew spviInstrumentedTests
./gradlew :app:recordRoborazziDebug
python3 tools/verificacion/api_minima.py
python3 tools/verificacion/documentacion.py
```

- `spviCheck` reúne la suite JVM, lint, APK debug y control de permisos.
- La compilación de `androidTest` confirma que esos tests se construyen; no los ejecuta.
- `spviInstrumentedTests` requiere teléfono o emulador conectado.
- Roborazzi necesita generar y luego revisar visualmente las capturas; revisa `CAPTURAS.md`.
- Para una release, sigue `RELEASE.md`; no crees ni envíes credenciales al agente.

No repitas un comando destructivo ni borres cachés por rutina. Conserva el primer error completo y pide análisis antes de cambiar archivos.

## 4. Pruebas manuales

Sigue el checklist de [PRUEBAS_DISPOSITIVO.md](../PRUEBAS_DISPOSITIVO.md). Prioriza, según el estado de [Pendiente.md](../Pendiente.md):

1. flujos de turno, venta, caja y respaldo con datos de prueba;
2. captura y revisión de los casos Roborazzi pendientes;
3. vinculación y sincronización con varios teléfonos;
4. medición de Inicio para T1.5, si se dispone de un teléfono representativo.

Una prueba manual debe incluir teléfono, versión de Android, commit, pasos observados y resultado. Un test unitario no reemplaza la prueba real de varios teléfonos; una instalación no reemplaza la validación de licencia.

## 5. Enviar resultados

Comparte un resumen sin datos sensibles:

- commit y rama;
- comando exacto y código de salida;
- tests JVM: ejecutados, fallidos, errores y omitidos;
- lint y R8, si se ejecutaron;
- tests instrumentados: ejecutados (no solo compilados), dispositivo y API;
- capturas generadas y revisión visual;
- pruebas manuales con el número de teléfono/dispositivos y resultado, sin datos personales.

El agente actualizará el registro solo con estos resultados o con una CI cuyo estado final haya sido observado. Si falta la evidencia, se conserva **pendiente / no verificado**.
