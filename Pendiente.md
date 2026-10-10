# Pendiente de SPVI

## Principal web independiente

Los pendientes funcionales enumerados en la revisión anterior están implementados en `desktop/`:
correcciones, transferencias/clientes, insumos sueltos, metadatos/fotos, idempotencia y validación
anidada, junto con servidor principal LAN, GL y respaldo compartido. No confundir código escrito
con funcionamiento verificado. La web solo opera como principal y no sustituye el móvil.

**Pendiente:** ejecutar en la PC del propietario las pruebas y compilaciones, corregir los fallos
que aparezcan y verificar interacción Windows/navegador/Android/GL. No se han ejecutado en esta fase,
por instrucción del propietario. Seguir `desktop/VERIFICACION_PC.md`; no reutilizar el resultado
histórico de 45 pruebas como evidencia del código actual.

Límites intencionales: sin API nativa Android; administración web solo en loopback; DNS/certificados
configurados explícitamente; fotos web no importadas por Android; ningún respaldo transfiere
activación ni secretos de emparejamiento; no se inventan consumos ausentes en respaldos históricos.
No incorpora impuestos ni contabilidad de partida doble.

## Android

Conservar los cambios existentes de pago electrónico, exportaciones, Inicio y notificaciones.
Ejecutar pruebas/compilación y revisar visualmente en dispositivos. Ver `Pruebas.md` y
`docs/OPENCODE_DESKTOP.md`. No declarar una APK verificada sin ejecutar el proceso correspondiente.
