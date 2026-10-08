# Pruebas en PC y teléfonos

Esta guía separa compilación, tests automáticos, capturas y comprobaciones manuales. **En el flujo actual, OpenCode CLI en el PC del propietario ejecuta Gradle, la app y las pruebas.** El agente de Arena solo analiza y modifica archivos; no debe declarar un cambio probado hasta recibir el resultado local correspondiente.

## 1. Preparar el PC

- JDK 17.
- Android Studio o Android SDK Platform 35 y Build Tools 35.0.0.
- `ANDROID_HOME` o `sdk.dir` en `local.properties` (ese archivo no se sube a Git).
- OpenCode CLI con la carpeta raíz del repositorio abierta.
- Para pruebas de dispositivo: teléfono Android 8.0+ con depuración USB, o emulador API 26+.

Antes de probar, pide a OpenCode que lea `AGENTS.md`, `README.md`, `Pendiente.md` y esta guía. No añadas contraseñas, claves de firma, licencias reales ni datos de clientes al chat o al repositorio.

## 2. Validación automática desde OpenCode CLI

Ejecuta desde la raíz del clon local:

```bash
./gradlew --version
./gradlew spviCheck
./gradlew :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin
./gradlew :app:assembleRelease :app:assembleDebug
./gradlew spviPermisos
python3 tools/verificacion/api_minima.py
python3 tools/verificacion/documentacion.py
```

`spviCheck` cubre tests JVM, lint, APK debug y permisos. La compilación de `androidTest` **solo demuestra que los instrumentados compilan**; no los ejecuta. Para ejecutarlos con un teléfono conectado o un emulador:

```bash
./gradlew spviInstrumentedTests
```

Las capturas se generan por separado:

```bash
./gradlew :app:recordRoborazziDebug
./gradlew :app:compareRoborazziDebug
./gradlew :app:verifyRoborazziDebug
```

Compara las imágenes con las referencias de `app/capturas/{claro,oscuro}/` y revisa cualquier PNG nuevo o modificado. Consulta [CAPTURAS.md](CAPTURAS.md) para el inventario de casos. Ejecutar o generar no equivale a revisar visualmente el resultado.

### Qué devolver como resultado

Copia un resumen con:

1. Rama y commit probados.
2. Comando ejecutado y resultado final (código de salida).
3. Tests JVM: cantidad, fallos, errores y omitidos.
4. Lint: errores y avisos; R8: resultado y clases ausentes, si aplica.
5. Instrumentados: cantidad ejecutada y dispositivo/API; no reportes como ejecutados los que solo compilaron.
6. Capturas: cuáles se generaron y cuáles se revisaron.
7. En pruebas manuales: modelo del teléfono, versión Android, pasos realizados y resultado. No adjuntes datos personales.

No pegues passwords, tokens, claves privadas ni datos reales. Si algo falla, conserva el fragmento de error necesario y el comando exacto.

## 3. Instalar una compilación de prueba

Con el teléfono conectado y la depuración USB autorizada:

```bash
adb devices
./gradlew :app:installDebug
adb logcat -c
adb logcat | grep -i spvi
```

La compilación debug usa el paquete `cu.spvi.app.debug` y no comparte su licencia con la versión release. Para instalar un release firmado, sigue [RELEASE.md](RELEASE.md) y usa un teléfono de prueba o un respaldo verificado. No instales una compilación de prueba encima de la instalación de un cliente.

## 4. Checklist funcional de un teléfono

Usa datos ficticios y un turno de prueba. Marca un punto solo después de observar el resultado en pantalla y en Registros.

### Inicio y configuración

- [ ] Abrir SPVI y recorrer el asistente; comprobar que se puede omitir y retomar desde **Ajustes → Completar configuración**.
- [ ] Guardar datos de ejemplo, niveles de inventario y medio de cobro; volver a abrir Ajustes y comprobar que persisten.
- [ ] Negar un permiso opcional y comprobar que SPVI explica la limitación sin bloquear las demás funciones.
- [ ] Activar el acceso con clave si el dispositivo lo admite; cerrar y volver a abrir la app.

### Inventario y servicios

- [ ] Crear, editar y consultar un producto con nombre, descripción, precios y cantidad.
- [ ] Crear un insumo, editar sus niveles y comprobar el filtro de categoría.
- [ ] Crear un elaborado con receta; registrar una venta de prueba y comprobar que se descuentan sus insumos.
- [ ] Crear un servicio con insumos y comprobar el descuento al venderlo.
- [ ] Comprobar la ficha, las alertas, la caducidad y la exportación de una lista filtrada.

### Turno, ventas y caja

- [ ] Intentar vender sin turno: SPVI debe ofrecer abrirlo y no guardar la venta.
- [ ] Abrir un turno, registrar una venta en efectivo y comprobar total e inventario.
- [ ] Registrar una venta por transferencia con datos ficticios; comprobar que el registro muestra el importe y los datos introducidos.
- [ ] Registrar una entrada o salida de caja con motivo; comprobar que aparece en el detalle del turno.
- [ ] Cerrar el turno, hacer el conteo si se solicita y revisar el arqueo en **Registros → Turnos**.
- [ ] Comprobar filtros, detalle y exportación PDF/Excel. Las exportaciones de prueba pueden contener información sensible: no las compartas.

### Respaldo

- [ ] Exportar un respaldo de prueba con contraseña; confirmar que la opción de proteger aparece activada.
- [ ] Importar la copia en una instalación de prueba y comprobar que los datos quedan restaurados.
- [ ] Probar una contraseña incorrecta y verificar que no se reemplazan los datos.
- [ ] Probar un archivo antiguo sin contraseña únicamente con datos ficticios y confirmar que aparece la advertencia correspondiente.
- [ ] No uses la única copia del negocio ni importes sobre un teléfono de producción durante esta comprobación.

## 5. Pruebas que requieren varios teléfonos

### Vinculación y sincronización

- [ ] Conectar una principal y una secundaria a la misma red local.
- [ ] Vincular la secundaria con el QR de un solo uso; confirmar en la principal qué empleado y permisos se asignaron.
- [ ] Abrir turno, realizar una venta en la secundaria y sincronizarla; comprobar que aparece una sola vez en la principal.
- [ ] Desconectar temporalmente la red local, hacer una operación de prueba y reconectar; comprobar la sincronización y que no se dupliquen turnos o ventas.
- [ ] Pedir cierre desde la secundaria y aprobarlo desde la principal.
- [ ] Asignar fondo a una secundaria, abrir su turno y comprobar que el fondo coincide.

### T2.2 — prueba manual dispensada

Por solicitud del propietario, no se realizará la comprobación manual con tres teléfonos. La implementación y sus tests se conservan; esta prueba física no se considera ejecutada ni aprobada.

## 6. Casos pendientes de esta revisión

- **T1.5 (aclarado como la petición «T4.5»):** ejecutar `RendimientoInicioTest` en teléfono/emulador y medir Inicio con el seed de 18 meses. El test usa Room en memoria (`EntornoIntegracion`) y la cierra: borrar la base instalada no es necesario y arriesga datos del negocio. No se ejecuta desde el agente; con OpenCode CLI, si el propietario autoriza la medición en teléfono real, conservar también Perfetto y anotar modelo, Android, fecha y pasos. Compilar los instrumentados no satisface esta tarea.
- **T2.1:** generar y revisar las capturas nuevas de respaldo sin contraseña, además de ejecutar los tests JVM.
- **T2.5:** generar y revisar la captura de aviso de actualización atrasada y ejecutar los tests de texto/estado. No adelantes el reloj de un teléfono con datos reales para forzar el aviso.
- **Roborazzi:** el árbol contiene 142 PNG por tema; hay 147 casos y faltan cinco baselines por tema (dos de Ajustes y tres tablet). Generar y revisar los nuevos/modificados según [CAPTURAS.md](CAPTURAS.md).
- **Release real:** la CI produce APK sin firmar. Una release firmada, AAB y humo de instalación requieren el keystore del propietario; no se sube al repositorio.

## 7. Actualizaciones y recuperación de licencia

Prueba las actualizaciones solo con un build de prueba configurado para un repositorio controlado y con una versión de prueba disponible. Verifica que Android solicite confirmación antes de instalar. No uses una actualización de prueba sobre el teléfono principal del negocio.

Las licencias reales están ligadas al dispositivo y sus mensajes contienen información sensible. Para una recuperación o transferencia, usa [LICENSE_CLIENT.md](LICENSE_CLIENT.md), [RELEASE.md](RELEASE.md) y el flujo guiado de la app; no pegues la licencia ni la solicitud cifrada en los reportes de prueba.
