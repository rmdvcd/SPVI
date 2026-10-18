# Escaneo OCR local

Para retomar esta implementación con OpenCode CLI, sigue la guía de contexto [`OPENCODE_ESCANEO_OCR.md`](OPENCODE_ESCANEO_OCR.md).

El OCR usa el artefacto empaquetado `com.google.mlkit:text-recognition` (Latin) y **no** el modelo descargable de Play Services: funciona en modo avión y no sube imágenes. Los URI se leen localmente (`InputImage.fromFilePath`, que ya aplica la orientación EXIF) y el reconocimiento corre fuera del hilo principal.

## Cómo funciona el flujo (0.31.0)

1. **Asistente (3 pasos):** tipo de registro (productos o insumos), campos a extraer y foto. Nombre, categoría, cantidad, costo y venta (productos); nombre, cantidad, costo y unidad (insumos) son obligatorios y no se pueden desmarcar. La cámara solo se usa mientras se toma la foto; también se puede elegir una imagen de la galería.
2. **Columnas detectadas:** si la geometría de la imagen permite confirmar una tabla, se muestran las columnas con su encabezado (o la primera celda como muestra) y se asigna **columna → campo**; lo que quede en «Ignorar» no se importa. Si no hay estructura confirmada, se pasa directo a la revisión con el aviso `scan_structure_fallback`.
3. **Revisión obligatoria:** todos los campos elegidos son editables en cada fila, se pueden añadir y eliminar filas, y los errores se señalan en la fila y el campo concretos. Nada se guarda sin pulsar **Confirmar e importar**.

La fila es del tipo elegido en el paso 1 (producto o insumo), así que solo se usan los campos aplicables.

## Extracción

- La tabla se reconstruye **por geometría** (`boundingBox` de cada línea y de cada palabra): las líneas se agrupan en filas por solape vertical y en columnas por su **borde izquierdo** (todas las celdas de una columna empiezan a la misma x, aunque una desborde su columna). No se usan tabuladores ni barras verticales.
- Cuando ML Kit pega dos columnas en una sola línea, se cortan donde el hueco entre palabras **duplica el alto del texto**; un hueco de palabra corriente no corta la celda (medido en el teléfono: 26 px de hueco con 20 px de alto era un espacio, no una columna).
- El encabezado es la primera fila con dos o más celdas reconocidas como nombre de campo y no tiene por qué ser la primera fila: lo de encima (título, fecha) no entra como registro. El mapeo automático usa sinónimos («precio costo», «pvp», «cant.», «u. m.», …).
- Si no se confirma la estructura, el fallback es honesto: **una línea reconocida = un registro editable** y un aviso visible. Un documento con columnas no delimitadas o manuscrito requerirá corrección manual; ML Kit no garantiza lectura manuscrita fiable.
- Lectura de números: `1,5` y `85,50` se interpretan como decimales; `1,450.00` y `1,200` con la convención de la app (el punto siempre es decimal). El importe tolera el símbolo y la unidad pegada («1,450.00 CUP», «$ 150»).
- Unidad: se busca el símbolo o el nombre contra `UnidadMedida` (u, kg, g, L, ml); si no se reconoce **no se inventa una unidad** y la fila se señala para que se elija en la revisión.
- Categoría: la del texto si es válida; si viene vacía se muestra a la vista como `Categorias.OTROS` («Otros»), siempre editable. «Insumos» y «Elaborado» no se importan desde el escáner (se crean en sus propias pantallas): la fila se señala con el motivo.
- Cantidad de producto: solo unidades enteras (igual que el formulario); cantidad de insumo: hasta tres decimales.

## Guardado por lotes

- Antes de insertar nada, **todas** las filas se revisan con los validadores del dominio (`Validadores.producto` / `Validadores.insumo`). Si alguna falla, no se guarda ninguna y se señala la fila y el campo.
- El guardado usa los casos de uso existentes (`GuardarProducto` / `GuardarInsumo`) fila por fila; no hay operación atómica de lote. Cada fila se quita de la revisión al guardarse, de modo que un reintento tras un fallo parcial **no duplica** lo ya insertado y se informa cuántos registros entraron y cuál quedó pendiente.
- Si el dominio rechaza un nombre repetido (sin descripción que lo distinga), se explica en español en vez de mostrar el error interno.

## Archivos y pruebas

| Archivo | Contenido |
|---|---|
| `app/.../inventario/EscanerInventarioLogic.kt` | Lógica pura: reconstrucción de la tabla, mapeo, filas, lecturas numéricas y revisión. |
| `app/.../inventario/EscanerInventarioViewModel.kt` | Lo que depende de Android: imagen + ML Kit, estado del asistente y guardado por lotes. |
| `app/.../inventario/EscanerInventarioScreen.kt` | Asistente Compose de 3 pasos y revisión. |
| `app/src/test/.../EscanerInventarioLogicTest.kt` | 19 pruebas JVM de la lógica (sin dispositivo ni red), varias nacidas de lo medido en el teléfono. |
| `app/src/test/.../EscanerInventarioViewModelTest.kt` | 5 pruebas JVM del ViewModel con repositorios dobles. |

## PP-Layout / modelo ONNX: retirado (bloqueado)

La detección de disposición con un modelo PP-Layout **no está implementada**. En esta versión se retiró la dependencia `com.microsoft.onnxruntime:onnxruntime-android` del catálogo y del módulo `:app` porque no había ningún artefacto con licencia de redistribución, procedencia y contrato de salida verificables; declarar el runtime sin modelo ni código de inferencia solo añadía tamaño al APK.

El repositorio **no contiene** ningún modelo (ni `pp_doclayout_l.onnx` ni un Paddle renombrado) y no se debe añadir uno sin comprobar antes fuente, licencia, versión, tamaño, arquitectura de entrada/salida y hash SHA-256.

Para reincorporarlo hace falta, en este orden:

1. Elegir un modelo ONNX redistribuible y documentar licencia, origen, SHA-256 y dimensiones de entrada/salida.
2. Volver a declararlo: `onnxRuntime = "x.y.z"` en `[versions]` y `onnxruntime-android = { module = "com.microsoft.onnxruntime:onnxruntime-android", version.ref = "onnxRuntime" }` en `[libraries]` de `gradle/libs.versions.toml`, más `implementation(libs.onnxruntime.android)` en `app/build.gradle.kts`.
3. Colocar el peso en `app/src/main/assets/` (o acordar un asset pack/descarga diferida; el APK crece entre 100 y 130 MB).
4. Implementar carga de sesión única, preprocesamiento, inferencia y decodificación **conforme a esa exportación** (normalización, etiquetas, umbrales y NMS), más pruebas de preprocesamiento/decodificación con entradas y salidas de referencia, y validar la inferencia en el dispositivo.
5. Mantener el fallback actual de ML Kit si el modelo falta, falla o no detecta estructura.

## Limitaciones

Tablas densas, columnas pegadas, imágenes borrosas o giradas y escritura manuscrita exigen corrección manual: la revisión existe justo para eso. Validar siempre las cantidades, costos y precios antes de confirmar. Prueba en teléfono: ver `PRUEBAS_DISPOSITIVO.md`.
