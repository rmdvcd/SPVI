# Guía de contexto para OpenCode CLI: terminar el escáner OCR de inventario

**Estado:** implementado, con pruebas JVM en verde (`spviTests`/`spviCheck`) y **probado en el teléfono el 2026-10-18** (productos, insumos y el caso sin tabla; ver `docs/VERIFICACION.md`). Responde y documenta en español.

## Instrucción inicial para OpenCode

> Lee primero `AGENTS.md`, `Contexto.md`, `Pendiente.md` y `docs/ESCANEO_OCR.md`; inspecciona el estado de Git y no descartes cambios existentes. Continúa el escáner OCR de inventario descrito en `docs/OPENCODE_ESCANEO_OCR.md`, respetando la arquitectura Kotlin/Compose/Hilt/Room y las reglas del repositorio. No inventes un contrato de modelo ONNX. Trabaja por fases: en cada una compila y ejecuta las pruebas pertinentes antes de avanzar; si falta un artefacto externo o una decisión necesaria, detente e informa con precisión. No insertes registros sin revisión y confirmación. Todo el OCR y layout deben funcionar sin red.

## Decisiones confirmadas por el dueño

- La app es Android nativa en Kotlin, no KMP.
- El escáner incluye **productos e insumos**.
- OCR local gratuito y sin nube. Motor: ML Kit Text Recognition v2 para alfabeto latino, artefacto empaquetado (`com.google.mlkit:text-recognition`), sin descargas dinámicas.
- El usuario debe elegir los campos antes de procesar, corregir los resultados en una revisión obligatoria, y confirmar antes de persistir.
- Usar los modelos y casos de uso existentes; no modificar el esquema ni cambiar de arquitectura sin necesidad demostrada.

## Estado al cerrar esta fase

| Archivo | Contenido |
|---|---|
| `app/.../inventario/EscanerInventarioLogic.kt` | Lógica pura: reconstrucción de la tabla por geometría, mapeo columna → campo, filas, lecturas numéricas y revisión. API pública sin `TODO()`. |
| `app/.../inventario/EscanerInventarioViewModel.kt` | Lo que depende de Android: `InputImage`, ML Kit, estado del asistente y guardado por lotes. Sin textos (errores tipados). |
| `app/.../inventario/EscanerInventarioScreen.kt` | Asistente Compose de 3 pasos (tipo/campos/imagen → columnas → revisión) con componentes `Spvi*`. |
| `app/src/test/.../EscanerInventarioLogicTest.kt` | 19 pruebas JVM de la lógica. |
| `app/src/test/.../EscanerInventarioViewModelTest.kt` | 5 pruebas JVM del ViewModel con repositorios dobles (`FakesApp`). |
| `app/src/main/res/values/strings.xml` | Textos `scan_*` en español (sin textos incrustados en Kotlin). |
| `domain/.../model/Producto.kt` | `Categorias.OTROS` («Otros») como categoría de reserva visible y editable. |
| `docs/ESCANEO_OCR.md` | Cómo funciona el flujo, limitaciones y cómo reincorporar PP-Layout/ONNX. |

Detalle de lo implementado, con las convenciones de números y unidades, en `docs/ESCANEO_OCR.md`.

## Puntos de la guía original y cómo quedaron

### 1. Modelo PP-Layout / ONNX — BLOQUEADO, dependencia retirada

No hay modelo con procedencia, licencia de redistribución y contrato de salida verificables, así que **no se inventó un decodificador**. Se retiró `com.microsoft.onnxruntime:onnxruntime-android` del catálogo (`gradle/libs.versions.toml`) y del módulo `:app`: declarar el runtime sin modelo ni código de inferencia solo añadía tamaño al APK. Los pasos exactos para reincorporarlo (licencia/hash/contrato, entradas del catálogo, peso en `assets/`, sesión única, pruebas de preprocesamiento y decodificación) están en `docs/ESCANEO_OCR.md` §«PP-Layout / modelo ONNX: retirado (bloqueado)». El fallback de ML Kit se conserva y sigue avisando en español cuando no se confirma la estructura.

### 2. Extracción estructurada — HECHO

- Tabla reconstruida por `boundingBox`: filas por solape vertical, columnas por **borde izquierdo** (una celda que desborda su columna no crea otra) y corte de la línea donde el hueco entre palabras **duplica el alto del texto**; sin tabuladores ni barras verticales. Estas tres reglas salen de lo medido en el teléfono (ver `docs/VERIFICACION.md`).
- Encabezado reconocido por sinónimos —no tiene por qué estar en la primera fila: el título de encima no entra como registro— y **mapeo manual columna → campo** en el paso 2 (lo que quede en «Ignorar» no se importa). El ajuste vuelve a armar las filas sin perder los campos elegidos.
- Orientación EXIF aplicada por `InputImage.fromFilePath`; procesamiento en `Dispatchers.Default`; el cliente de ML Kit se cierra en `finally` y el OCR se puede cancelar.
- La lectura respeta ñ, tildes, decimales y separadores de miles en español (`1,5` decimal; el punto siempre decimal, como en la app).
- Unidad contra `UnidadMedida` (u, kg, g, L, ml): si no se reconoce, la fila se señala — **no se inventa una unidad**.
- Categoría de producto: si la imagen no la trae, se muestra a la vista como «Otros», editable. «Insumos» y «Elaborado» se rechazan con mensaje propio (se crean en sus pantallas).
- Tipo de registro explícito (producto o insumo) y solo campos aplicables: cantidad de producto en unidades enteras; cantidad de insumo en milésimas.

### 3. Revisión y guardado — HECHO

- Todos los campos elegidos son editables, incluidos los vacíos; se pueden añadir y eliminar filas y volver atrás sin perder la imagen ni el resultado.
- Antes de insertar nada se revisan **todas** las filas con `Validadores.producto` / `Validadores.insumo`; si alguna falla, no se guarda ninguna y se señala la fila y el campo (sin defaults silenciosos).
- El guardado usa `GuardarProducto` / `GuardarInsumo` fila por fila (no hay operación atómica de lote) y **quita cada fila al guardarse**: un reintento tras un fallo parcial no duplica lo insertado y se informa cuántos entraron y cuál quedó pendiente.
- No se escribe directamente en Room desde la interfaz.
- Se reutilizan `SelectorFoto`, el selector de fotos y los permisos ya declarados; no se añadió ningún permiso.

### 4. Calidad, textos y recursos — HECHO

- Textos nuevos en `strings.xml` (se eliminaron los que quedaron muertos); errores tipados en el ViewModel y traducidos en la pantalla.
- Componentes `Spvi*` y `SpviSpacing`; botones de solo icono con descripción; errores visibles por campo y aviso de fallo parcial.
- `spviPermisos` pasa: ML Kit no añade permisos ni telemetría y el OCR funciona en modo avión.

## Prueba manual requerida cuando haya dispositivo

Hecho el 2026-10-18 en el teléfono 4bc9f8f3 (Android 15) con las tres imágenes de ejemplo del generador `generar-tablas-ocr.ps1`: tabla de productos (4 filas, 5 columnas y encabezado mapeado solo), tabla de insumos (3 filas con unidad `kg` y cantidad `12.5`) y un párrafo sin tabla (aviso de estructura no confirmada, error «No se pudo leer “Cantidad” en la fila 1» y **nada guardado**). Queda pendiente de repetir, sin prisa:

- Foto impresa real (no PNG digital) de una tabla con encabezados, cantidades fraccionarias y precios; revisar que se detecten filas/columnas y corregir el mapeo.
- Una lista manuscrita en español, comprobando explícitamente que la revisión deja corregir tildes, ñ y números; no asumir OCR manuscrito fiable.
- Probar cámara con permiso concedido/denegado, galería, cancelación durante el reconocimiento, imagen borrosa/rotada, cero texto, fila añadida/eliminada y cierre/reentrada.
- Confirmar que cancelar no persiste nada, que solo confirmar guarda, que la lista se actualiza y que un fallo parcial no duplica filas.
- Activar modo avión y repetir el flujo completo.
