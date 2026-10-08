# Pendiente — estado de esta revisión

Última actualización: **2026-10-08**. Versión del código: 0.30.0 (`versionCode 51`), Room v11, sin cambio de esquema.

## 1. Estado de verificación

La última CI completamente confirmada es la del commit `3bf4fc1`, run [37834591679](https://github.com/rmdvcd/SPVI/actions/runs/37834591679): 5/5 trabajos verdes y 836 tests JVM sin fallos, errores ni omisiones. Los tests instrumentados solo se compilaron; no se ejecutaron.

El commit `43cc5df` incorporó T2.4 y T2.5. La corrida [37836886846](https://github.com/rmdvcd/SPVI/actions/runs/37836886846) se observó de forma parcial: lint, tests JVM + compilación de instrumentados, permisos y API mínima figuraban aprobados; Release/R8 seguía en curso. **El resultado final es desconocido y no se debe contar como CI verde.** No se retomó su seguimiento.

El propietario pidió que OpenCode CLI en su PC haga las compilaciones, ejecuciones y pruebas. El agente no ejecuta Gradle, la app, adb, emuladores, tests ni Roborazzi. Todo cambio posterior a la última evidencia está sin validar hasta recibir el informe local. Ver comandos en [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md) y estado medido en [docs/VERIFICACION.md](docs/VERIFICACION.md).

## 2. Próximas comprobaciones

### OpenCode CLI en el PC

- [ ] `./gradlew spviCheck` — tests JVM, lint, APK debug y permisos.
- [ ] `./gradlew :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin` — confirma compilación de instrumentados, no su ejecución.
- [ ] `./gradlew :app:assembleRelease :app:assembleDebug` — confirmar R8 y salidas de APK.
- [ ] `./gradlew spviInstrumentedTests` con teléfono o emulador API 26+ — ejecución real de instrumentados.
- [ ] `./gradlew :app:recordRoborazziDebug` — generar capturas claro/oscuro; revisar visualmente cada imagen nueva.
- [ ] `python3 tools/verificacion/api_minima.py` y `python3 tools/verificacion/documentacion.py`.
- [ ] Compartir rama, commit, comandos, salida, cantidades de tests y dispositivo; actualizar [docs/VERIFICACION.md](docs/VERIFICACION.md) solo con lo observado.

La estimación previa de **844 tests JVM** (836 de la última CI completa + 8 añadidos en `43cc5df`) ya no es el recuento actual: se retiró un test del antiguo flujo de escaneo. No se afirma una cifra nueva hasta que OpenCode CLI ejecute la suite.

### Comprobaciones que requieren teléfonos

- [ ] **T1.5:** ejecutar `RendimientoInicioTest` en dispositivo, medir Inicio con el seed de 18 meses y guardar el resultado. El test crea Room en memoria (`EntornoIntegracion`) y la cierra; no requiere borrar la base de datos instalada. No se ejecuta desde el agente; el resultado local y la medida/traza del teléfono siguen pendientes.
- [ ] **T2.1:** generar y revisar la captura de respaldo sin contraseña; no declarar validada solo por tener el código/test.
- **T2.2:** prueba manual de tres teléfonos dispensada por solicitud del propietario. La implementación/tests permanecen, pero la función no cuenta con esa validación física.
- [ ] **T2.5:** generar y revisar la captura del aviso de actualización atrasada; los tests también deben ejecutarse localmente.
- [ ] Instalar la app y comprobar flujos de uso real en un teléfono con datos ficticios.
- [ ] Preparar release firmado/AAB solo con el keystore privado del propietario; no se guarda en Git ni se comparte por chat.

## 3. Capturas

- El árbol tiene **142 PNG por tema** (284 en total).
- Hay **147 casos `@Test` de captura**; faltan cinco PNG por tema: dos casos recientes de Ajustes y tres capturas tablet. No se han generado ni revisado desde el agente.
- Casos pendientes: `07t_ajustes_actualizaciones_atrasadas`, `09m_respaldo_sin_contrasena`, `02q_inventario_tableta_detalle`, `04u_registros_tableta_detalle` y `09f_servicios_tableta_detalle`.
- Revisar el inventario y los límites de captura en [CAPTURAS.md](CAPTURAS.md).

## 4. Progreso documental y de higiene

- La tabla Room del README se actualiza contra las 18 entidades de `Entities.kt`; el script `tools/verificacion/documentacion.py` y el sexto job de documentación están añadidos al workflow. **No se han ejecutado**.
- Se añadieron/revisaron el manual del usuario y la guía de pruebas local, y se eliminan enlaces a archivos inexistentes de los índices vigentes.
- `docs/VINCULACION.md` ya no lista la preferencia de consultas de producto retirada y aclara que el botón Escanear de productos se eliminó en 0.30.0; el QR de vinculación sigue vigente.
- `docs/DECISIONES.md` reúne P37, P68b, P74 y P70 §5.4; las maquetas obsoletas de búsqueda de productos se retiraron y se conservaron los QR de vinculación y licencia.
- El archivo `docs/HISTORIAL_DESARROLLO.md` se comprobó estáticamente como UTF-8 válido y sin carácter U+FFFD ni secuencias habituales de mojibake; no se reescribió la bitácora histórica.
- `.kotlin/` debe permanecer ignorada y fuera del control de versiones.
- Limpieza estática realizada: retirados el escáner de productos y sus herramientas. La cobertura general del antiguo `EscanerInventarioTest` se conserva en `InventarioTest`; se eliminaron solo las comprobaciones del campo de código ya retirado. Se conservan los QR de vinculación y licencias, Transfermóvil y la cámara de QR. También se quitaron el FAB expandible sin usos y el bypass obsoleto `exigirDiferenciar`.
- Se retiraron las aserciones `!!` del Kotlin de producción en `core`, `domain`, `data`, `designsystem` y `app`; reemplazadas por accesos con invariante explícita o descarte seguro.
- `git diff --check` y `git diff --cached --check` pasaron sin salida. No se ejecutaron compilaciones ni pruebas; validar con OpenCode CLI.
- T4.4 implementada en el árbol: Inventario, Servicios y Registros mantienen la vista previa durante la consulta y exponen el estado `buscando` con barra lineal etiquetada para accesibilidad. Se añadieron pruebas unitarias y de UI; **no se ejecutaron** y requieren OpenCode CLI.
- T4.3: pasada estática de referencias en los 27 objetos `Textos*`; se retiraron 8 constantes sin uso, se corrigieron el placeholder de Inventario y el comentario de exportación de Servicios, y se quitaron menciones al escáner de productos de comentarios del formulario. También se corrigió el comentario de dependencias de CameraX/ML Kit: ahora atribuye la cámara al QR de vinculación, no a Inventario. La Ayuda de Registros ahora usa «Exportar» y explica PDF/Excel o guardar en el teléfono, con una aserción añadida. Falta la revisión visual/semántica completa de cada texto. «Nuevo código» de Vinculación es válido.
- T4.2 implementada en el árbol: lista-detalle adaptativo desde `WindowSizeClass.Medium` para Inventario/Registros/Servicios; fichas en panel persistente en ventanas anchas y modales con tamaño intrínseco acotado por el viewport. Añadida dependencia WindowSizeClass y tres capturas `w800dp`. **Pendiente compilar y revisar las capturas con OpenCode CLI; no validado por el agente.**
- No se cambió Room v11, el DTO de respaldo v4 ni el archivo `.spvi` v4.

## 5. Decisiones de producto confirmadas

- **T2.1:** mantener la exportación sin contraseña como opción explícita con advertencia; no eliminarla. La protección por contraseña sigue activada por defecto y los respaldos antiguos se pueden importar.
- **T4.1:** mantener los cinco iconos sin etiquetas en la barra inferior.
- **F5:** el propietario autorizó implementar las cuatro áreas por fases: multi-moneda, descuentos/impuestos/devoluciones parciales, usuarios locales en el mismo teléfono y textos externalizados. Antes de afectar dinero, impuestos, permisos o acceso de usuarios hay que fijar sus reglas funcionales.
- No modificar protocolo de sincronización ni lista de permisos sin una solicitud explícita. Un cambio de esquema sigue el procedimiento v12 con migración y esquema exportado.
