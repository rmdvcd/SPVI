# Escaneo OCR local

Para retomar y completar esta implementación con OpenCode CLI, sigue la guía de contexto [`OPENCODE_ESCANEO_OCR.md`](OPENCODE_ESCANEO_OCR.md).

El OCR usa el artefacto empaquetado `com.google.mlkit:text-recognition` (Latin) y no el modelo descargable de Play Services. El procesamiento se inicia desde `Dispatchers.Default`; los URI se leen localmente. No se suben imágenes.

## PP-Layout / modelo ONNX

La app incluye ONNX Runtime Android. Para activar detección de disposición debe añadirse el modelo compatible `pp_doclayout_l.onnx` en `app/src/main/assets/` y definirse su preprocesamiento y decodificación de salidas conforme a la exportación concreta del modelo (dimensiones, normalización, etiquetas y NMS). **El repositorio no contiene ese modelo ni una exportación ONNX verificable**, por lo que esta versión no inventa un decodificador incompatible: usa el fallback completo de ML Kit, agrupado por líneas, e informa que la estructura requiere revisión.

La descarga/copia del modelo no se incluye en Git para no añadir ~100–130 MB de pesos sin confirmar licencia, procedencia y hash. El activo empaquetado aumentará el tamaño del APK de forma significativa; a futuro conviene distribuirlo como asset pack/descarga diferida si el modelo elegido y la licencia lo permiten. Aunque se haya aceptado el tamaño, la integración de inferencia PP-Layout requiere incorporar los pesos y validar su contrato de salida en dispositivo antes de habilitarla.

## Limitaciones

El fallback trata cada línea reconocida como registro editable. Documentos con columnas no delimitadas, tablas densas y escritura manuscrita requerirán corrección manual; ML Kit no garantiza lectura manuscrita fiable. Validar cantidades, costos y precios en la revisión antes de confirmar.
