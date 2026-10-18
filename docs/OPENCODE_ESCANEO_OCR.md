# Guía de contexto para OpenCode CLI: terminar el escáner OCR de inventario

**Estado:** implementación parcial; no se debe presentar como terminada. Este documento sirve para continuar la tarea en OpenCode CLI desde la raíz del repositorio. Responde y documenta en español.

## Instrucción inicial para OpenCode

> Lee primero `AGENTS.md`, `Contexto.md`, `Pendiente.md` y `docs/ESCANEO_OCR.md`; inspecciona el estado de Git y no descartes cambios existentes. Continúa el escáner OCR de inventario descrito en `docs/OPENCODE_ESCANEO_OCR.md`, respetando la arquitectura Kotlin/Compose/Hilt/Room y las reglas del repositorio. No inventes un contrato de modelo ONNX. Trabaja por fases: en cada una compila y ejecuta las pruebas pertinentes antes de avanzar; si falta un artefacto externo o una decisión necesaria, detente e informa con precisión. No insertes registros sin revisión y confirmación. Todo el OCR y layout deben funcionar sin red.

## Decisiones confirmadas por el dueño

- La app es Android nativa en Kotlin, no KMP.
- El escáner incluye **productos e insumos**.
- OCR local gratuito y sin nube. Motor deseado: ML Kit Text Recognition v2 para alfabeto latino.
- Se acepta el impacto considerable del modelo ONNX en el tamaño de la aplicación.
- El usuario debe elegir los campos antes de procesar, corregir los resultados en una revisión obligatoria, y confirmar antes de persistir.
- Usar los modelos y casos de uso existentes; no modificar el esquema ni cambiar de arquitectura sin necesidad demostrada.

## Estado actual en el árbol de trabajo

Archivos incorporados en el avance parcial:

- `app/src/main/kotlin/cu/spvi/app/inventario/EscanerInventarioScreen.kt`: pantalla nueva para tipo de artículo, preselección de campos, selector/captura mediante `SelectorFoto`, OCR de ML Kit, filas editables, cancelación del OCR y guardado.
- `app/src/main/kotlin/cu/spvi/app/inventario/InventarioScreen.kt`: botón Escanear en inventario normal, condicionado por permiso de edición; no aparece en modo de selección de venta.
- `app/src/main/kotlin/cu/spvi/app/navigation/Routes.kt` y `MainScaffold.kt`: ruta del flujo y retorno a inventario con mensaje.
- `app/src/main/res/values/strings.xml`: recursos españoles añadidos.
- `gradle/libs.versions.toml`, `app/build.gradle.kts`: ML Kit Text Recognition, ONNX Runtime Android y `kotlinx-coroutines-play-services`.
- `docs/ESCANEO_OCR.md`: estado de modelo y limitaciones.

La entidad está separada: `Producto` y `Insumo` en `domain/src/main/kotlin/cu/spvi/domain/model/`; persistencia mediante `GuardarProducto` y `GuardarInsumo`. Productos requieren categoría, costo, precio de venta y cantidad; el validador también contempla identificación nombre/descripción. Insumos requieren nombre, precio y cantidad, admiten unidad y precio de venta opcional. Revisar siempre `Validadores.kt` y los formularios actuales antes de cambiar defaults.

**No verificado:** al iniciar el trabajo no había `java`/`JAVA_HOME` en el entorno de Arena; `./gradlew :app:assembleDebug` terminó antes de Gradle con “JAVA_HOME is not set and no 'java' command could be found”. En el entorno OpenCode del dueño comprueba `java -version` y el JDK 17 del proyecto. No afirmes que compila hasta ejecutar la compilación.

## Pendientes funcionales y técnicos

### 1. Obtener e integrar un modelo PP-Layout válido

- Identificar un **modelo ONNX real**, compatible con Android y el caso de uso (detección de tablas/regiones; el nombre `pp_doclayout_l.onnx` por sí solo no garantiza detección de celdas).
- Verificar fuente, licencia de redistribución, versión, tamaño, arquitectura de entrada/salida y hash SHA-256. No incluir un archivo desconocido, un puntero falso ni un modelo Paddle no convertido renombrado como ONNX.
- Conseguir aprobación/licencia compatible y colocar el artefacto en `app/src/main/assets/` (o asset pack si se acuerda una descarga diferida). El dueño aceptó el tamaño del APK; eso **no equivale** a aprobar una licencia de modelo desconocida.
- Implementar carga de sesión en singleton/proveedor, cierre de sesión y entorno, copia a fichero privado si ONNX Runtime lo exige, tensor de entrada, normalización/redimensionado, inferencia, decodificación de etiquetas/cajas, umbrales y NMS según el contrato de ESA exportación. Evitar inicializar una sesión por cada imagen.
- Crear pruebas de preprocesamiento/decodificación con entradas y salidas de referencia, y comprobar inferencia en dispositivo. Si solo hay cajas de regiones y no de celdas, reconstruir filas/columnas con las coordenadas o justificar una alternativa estructural real; no llamar “detección de celdas” a una detección de páginas/bloques.
- Mantener fallback ML Kit si el modelo falta, falla o no detecta estructura, y mostrar aviso localizado en español.
- Actualizar `docs/ESCANEO_OCR.md` con origen, licencia, hash, dimensiones de entrada/salida, instrucciones exactas de inclusión y tamaño medido del APK.

Actualmente ONNX Runtime está declarado como dependencia pero **no hay modelo ni código de inferencia PP-Layout**. El flujo actual únicamente ejecuta ML Kit. Resolver esto es el principal bloqueo para cumplir el objetivo original. Si OpenCode no puede verificar un modelo redistribuible, no descargues nada a ciegas: informa el bloqueo y conserva el fallback honesto.

### 2. Terminar la extracción estructurada

- El código actual usa textos `Line` y una división simple por tabuladores, punto y coma o barra vertical; no emplea `TextBlock → Line → Element` con `boundingBox` ni reconstruye tablas por coordenadas. Esa heurística no suele separar columnas en una fotografía de papel.
- Aplicar corrección de orientación y reducción razonable de bitmap antes de inferencias. Respetar EXIF; cerrar/cancelar tareas y liberar bitmaps/tensores/sesión según corresponda.
- Implementar agrupación geométrica por filas y columnas para el fallback; verificar lectura de ñ, tildes, decimales y separadores de miles en español.
- Añadir asignación manual **columna detectada → campo** si el mapeo no es inequívoco. La pantalla actual ofrece selección de campos, pero no una asignación independiente por columna.
- Hacer explícito si una fila es producto o insumo y usar únicamente campos aplicables. Unidad debe elegirse/validarse contra `UnidadMedida`; categoría de producto no debe quedar silenciosamente mal asignada.

### 3. Robustecer la revisión y guardado

- En la revisión deben ser editables todos los campos seleccionados, incluidos campos vacíos; añadir/eliminar filas y poder volver sin perder la imagen/resultado.
- Validar obligatorios, formato decimal/local, precio de venta mayor que costo para producto y restricciones de dominio antes de iniciar inserciones. No reemplazar errores de validación por defaults silenciosos salvo decisión explícita.
- El guardado actual invoca casos de uso por fila (no operación atómica de lote). Revisar y documentar comportamiento ante fallo parcial, impedir reintentos duplicados, conservar estado y comunicar cuántas filas sí se guardaron y cuáles quedan pendientes.
- Considerar un caso de uso de lote solo si puede preservar validaciones, transacciones y movimientos de inventario existentes; no escribir directamente en Room desde UI.
- Comprobar permisos reales de lectura/edición. Reutilizar `SelectorFoto`, `ActivityResultContracts.RequestPermission`, `TakePicture`, selector de fotos y `FileProvider` existentes; no añadir permisos de almacenamiento innecesarios.

### 4. Calidad, strings y recursos

- Revisar todos los textos nuevos: etiquetas y errores en `strings.xml`, sin hardcodear en Kotlin. La categoría fallback “Otros” debe provenir del recurso o modelo apropiado.
- Eliminar imports sin uso y aplicar convenciones locales; revisar accesibilidad (content descriptions, etiquetas, tamaños táctiles, progreso y acciones de cancelar).
- Comprobar el manifiesto fusionado y `spviPermisos`. ML Kit/ONNX no deben introducir telemetría, permisos nuevos ni solicitudes de red. Mantener el OCR offline; no activar descargas dinámicas.
- Revisar impacto del botón con permisos y anchura en la TopBar; respetar componentes `Spvi*` del design system.

## Plan incremental para OpenCode CLI

1. **Línea base:** revisar `git status --short --branch`, diff completo, `AGENTS.md`, `Contexto.md`, `Pendiente.md`, `Pruebas.md`; verificar JDK/SDK y ejecutar `./gradlew :app:assembleDebug` antes de tocar código. Separar fallos preexistentes de los nuevos.
2. **Compilación e integración actual:** corregir compile/lint de esta rama sin ampliar alcance; confirmar dependencias/aliases y recursos. Ejecutar assembleDebug y pruebas JVM pertinentes.
3. **Modelo/PP-Layout:** validar procedencia/licencia/contrato y conseguir peso; implementar loader, preprocesamiento e inferencia; agregar pruebas unitarias con fixtures pequeños (no versionar pesos pesados de prueba). Si no se puede garantizar el modelo, detenerse y documentar explícitamente qué queda bloqueado.
4. **Mapeo y revisión:** usar bounding boxes reales y mapeo manual, luego comprobar revisión, validación y guardado parcial/duplicados.
5. **Pruebas y documentación:** añadir pruebas para el mapeo y modelos, ejecutar al menos `./gradlew :app:assembleDebug`, `./gradlew spviTests` y `./gradlew spviPermisos` (o `spviCheck` según tiempo/ambiente). Luego prueba manual en dispositivo con imagen impresa, imagen digital/captura y manuscrito español; registra modelo/teléfono/resultado, no prometas precisión manuscrita.
6. **Informe final:** lista de archivos/dependencias, licencia/huella/tamaño de modelo y APK, comandos/códigos de salida, pruebas manuales realizadas, limitaciones y pendientes. Todo lo no ejecutado debe figurar como **NO VERIFICADO**.

## Prueba manual requerida cuando haya dispositivo

- Imagen digital y foto impresa de una tabla con encabezados, cantidades fraccionarias y precios; revisar que se detecten filas/columnas y corregir el mapeo.
- Una lista manuscrita en español, comprobando explícitamente que la revisión deja corregir tildes, ñ y números; no asumir OCR manuscrito fiable.
- Probar cámara con permiso concedido/denegado, galería, cancelación durante inferencia, imagen borrosa/rotada, ausencia de tabla, cero texto, fila añadida/eliminada y cierre/reentrada.
- Confirmar que cancelar no persiste nada, que solo confirmar guarda, que la lista se actualiza y que un fallo parcial no duplica filas.
- Activar modo avión y repetir OCR/flujo para verificar que el procesamiento no depende de red.
