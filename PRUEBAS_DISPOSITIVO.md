# SPVI — pruebas en dispositivo

Lista de humo manual para complementar CI y tests automáticos. Usar teléfonos/emuladores de prueba y datos ficticios: importar un respaldo reemplaza los datos locales. No marcar una fila como aprobada si no se ejecutó realmente.

## Preparación

- [ ] Instala el APK **debug** para la mayoría de los flujos o el APK **release firmado** para verificar R8, firma, biometría y actualización. No uses el APK release sin firmar para instalar.
- [ ] Registra modelo, versión de Android, `versionName`, `versionCode`, variante y firma usada.
- [ ] Configura Perfil, licencia de prueba, forma de pago y un turno de prueba. Para sincronización, conecta una app principal y una secundaria a la misma red local.
- [ ] Conserva una copia de respaldo de prueba anterior si vas a comprobar una migración.

## Datos, costos y precios

- [ ] Crea un producto normal con costo `100.00` y venta `150.00`: debe guardarse.
- [ ] Intenta guardar ese producto con venta `100.00` y luego `99.99`: ambos deben rechazarse y señalar el precio de venta.
- [ ] Crea un Elaborado con receta conocida; comprueba el costo calculado y que no se pueda guardar con venta igual o inferior al costo de la receta.
- [ ] Crea un Insumo vendible: el precio de venta puede omitirse; si se introduce, debe ser mayor que el costo. Prueba valor igual y menor.
- [ ] Crea un Servicio con insumos, calcula su costo total y comprueba que el importe igual o inferior se rechace; un importe un centavo mayor debe aceptarse.
- [ ] Edita un Servicio cambiando sus insumos para elevar el costo por encima del importe; el guardado debe rechazarse.
- [ ] Eleva el costo de un insumo usado por un Elaborado o Servicio de modo que alcance/supere su venta/importe; el cambio no debe guardarse hasta que primero se ajuste el precio correspondiente.
- [ ] Crea un preajuste que deje el precio base por encima del costo pero el efectivo igual al costo; la cotización/venta debe rechazarse.
- [ ] Prueba dos preajustes combinados: la suma efectiva tampoco puede dejar el precio en o por debajo del costo.
- [ ] Comprueba que descuentos que mantienen precio efectivo por encima del costo se conservan y se registran con precio base, ajuste y costo correctos.

## Entradas y validación al escribir

- [ ] En cada formulario, intenta escribir y pegar letras en importes, porcentajes, cantidades, teléfonos, carnés y tarjetas: no deben entrar caracteres ajenos al tipo de dato.
- [ ] Pega `1,450.00 CUP` en un importe; el valor interpretado debe ser `1450.00`, no `1.45`.
- [ ] Prueba coma decimal como `1,45`; debe convertirse en `1.45`.
- [ ] Prueba agrupaciones inválidas como `12,5.3`, `1.2,3` y `1234,567.89`; no deben convertirse silenciosamente en otro importe.
- [ ] Prueba más de dos decimales en moneda y más de tres en cantidad de insumo: el campo debe limitarlo y el formulario no debe guardar un formato inválido.
- [ ] Escribe porcentaje `100.01` (si el control lo permite); el formulario debe señalar el rango al validar. Verifica también límites y valores vacíos.
- [ ] Revisa filtros/buscadores y nombres: el filtrado no debe insertar saltos de línea ni dejar controles invisibles.

## Respaldo

- [ ] Con contraseña, exporta, confirma el archivo `.spvi`, impórtalo en una instalación de prueba y verifica que los datos se restauren.
- [ ] Importa ese mismo archivo con una contraseña incorrecta; debe permitir corregirla y no tocar los datos existentes.
- [ ] Sin contraseña, pulsa **Guardar en el teléfono**: antes del selector debe aparecer la advertencia explícita de que quien obtenga el archivo puede leerlo.
- [ ] Repite con **Enviar a otra app**: la misma advertencia debe aparecer antes de compartir.
- [ ] Con biometría inscrita, confirma el aviso y comprueba que se exige biometría, sin opción de PIN/patrón. Solo el éxito debe abrir el selector/flujo de exportación.
- [ ] Cancela o falla la biometría: debe indicarse que no se exportó el respaldo.
- [ ] En un dispositivo sin biometría compatible, confirma que el aviso aún es obligatorio antes de continuar.
- [ ] Cancela el aviso: no debe abrirse el selector ni crearse/compartirse un archivo.
- [ ] Confirma que todos los textos expliquen que la biometría autoriza la acción, pero no añade una contraseña ni protege el archivo.
- [ ] Comprueba que importar un archivo sin contraseña no solicita una contraseña y que importar siempre avisa que reemplazará los datos.

## Venta, caja y sincronización

- [ ] Abre turno con fondo `0`; registra venta en efectivo y transferencia. Comprueba caja, existencias, cliente y Registros.
- [ ] Cierra turno con importe esperado, contado menor y contado igual; comprueba diferencias y el botón **Cuadra**.
- [ ] Registra entrada y salida de caja; motivo inválido o vacío no debe guardarse.
- [ ] Modifica/anula una venta de prueba y comprueba la devolución de existencias y el estado en Registros.
- [ ] Vincula una secundaria con QR de un solo uso. Sincroniza venta y cambios de inventario en ambos sentidos.
- [ ] Cierra la red durante una sincronización y vuelve a conectarla; comprueba reintentos sin duplicar ventas.
- [ ] Verifica que una conexión nueva no expulse una sesión autenticada si el saludo está malformado o no completa el primer mensaje cifrado.
- [ ] Si hay una actualización publicada, comprueba hash, firma y confirmación de Android antes de instalar.

## Compatibilidad, accesibilidad y migraciones

- [ ] Repite flujos principales en la API mínima admitida (26) y en una versión Android reciente.
- [ ] Prueba tema claro y oscuro, escala de letra grande, TalkBack, teclado abierto, navegación Atrás y orientación compatible.
- [ ] Abre Inicio, Inventario, Servicios, Registros y Ajustes con la app en segundo plano/primer plano; verifica que el estado no desaparezca.
- [ ] Actualiza desde una base de datos 10→11 con productos, imágenes, fechas, precios, niveles y transacciones: todo excepto el campo de código retirado debe conservarse.
- [ ] Desinstala/reinstala con datos ficticios y verifica el aviso/permiso del registro de prueba, sin confundirlo con una licencia o un respaldo.
- [ ] En Recientes, comprueba que las pantallas que muestran datos sensibles sigan protegidas con `FLAG_SECURE`.

## Registro del resultado

Anota por caso: **aprobado / fallido / no ejecutado**, dispositivo, versión Android, APK/huella, pasos, resultado esperado/real y captura/log sin datos personales. Informa cualquier fallo antes de publicar; no sustituyas los casos no ejecutados por el número de tests JVM.
