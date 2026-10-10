# SPVI — Guía de los cambios en desarrollo

## Pago electrónico

En **Ajustes → Pago electrónico**, marca el teléfono y la tarjeta que usarás al cobrar.
Solo puede haber uno de cada tipo. Marcar otro sustituye al anterior; tocar el ya marcado lo desmarca.
El icono de tarjeta es igual para todos los bancos. Los botones de editar y eliminar están uno junto al otro.
Antes de cobrar por transferencia, comprueba que tienes ambos seleccionados.

## Inicio

La marca, el estado del turno y el selector de período permanecen arriba al desplazarte.
Los avisos, accesos y gráficos están debajo. Con turno abierto y permiso para vender aparecen
**Nueva venta**, abajo a la izquierda, y **Movimiento de caja**, abajo a la derecha.
Cuando el turno está cerrado no aparecen.

## Buscar y consultar transferencias

Los buscadores conservan su etiqueta, pero no tienen texto de ejemplo dentro.
Las transferencias no muestran el vendedor en la lista/tabla. Esto no borra quién registró la operación.

## Exportar

- **PDF y Excel:** informes de inventario, servicios y turnos.
- **Imagen:** lista de precios de productos o servicios.
- **Tarjetas promocionales:** productos o servicios con foto, nombre y precio. Puedes escribir un
  encabezado opcional de hasta 160 caracteres antes de compartir. Aparece encima de cada imagen.

Las promociones no incluyen insumos. Las imágenes para clientes no llevan costos ni existencias.
Los informes internos pueden incluir costos: revisa el destinatario antes de compartirlos.
La ficha individual de producto se comparte como imagen o tarjeta promocional, no PDF.

## Notificación

Expande la notificación para consultar el estado del turno, los turnos abiertos en el negocio y la
sincronización local. Con turno propio abierto también muestra su duración.
Tocarla vuelve a la aplicación. No muestra importes, clientes ni números de tarjeta.

## Escritorio Windows

La primera versión Python se encuentra en `desktop/`. Su instalación, límites y configuración del
navegador y dominio se explican en [la guía de escritorio](desktop/README.md).
Incluye el servidor principal para secundarias Android, pendiente de prueba real con móviles.
Es independiente y solo principal; no sustituye la aplicación móvil.

### Exportar y respaldar en escritorio

En **Exportar**, elige informe de inventario, servicios, turnos o productos para clientes.
Los formatos disponibles cambian con esa elección. Escribe el encabezado si eliges tarjetas promocionales.
Con varios PNG recibirás un ZIP. Los informes internos incluyen costos; las imágenes no.

En **Respaldo de escritorio**, crea una copia cifrada con contraseña. Para restaurar, cierra el turno,
selecciona el archivo `.spvidesk`, escribe su contraseña y confirma con `RESTAURAR`. Esta acción reemplaza
los datos; se conserva una copia cifrada previa. La sección **Intercambio con Android** importa `.spvi` v3/v4 y exporta v4; esa interoperabilidad requiere verificación en PC y móviles.

## Principal web independiente: ampliación pendiente de verificación

En **Apps secundarias**, crea el empleado, asigna permisos y cuentas de cobro y muestra el QR
con la red LAN iniciada. La web solo puede ser principal; el móvil conserva su aplicación propia.
Los fondos y solicitudes de cierre se administran por empleado. El QR es temporal y de un uso.

El carrito admite varias líneas. Los elaborados requieren receta y consumen sus insumos;
los preajustes aplicables se suman, con redondeo al centavo. La anulación está limitada al turno
abierto. Al crear o editar una regla, selecciona los productos afectados;
no incluye automáticamente productos creados después.

La restauración reemplaza el negocio: cierra los turnos, conserva la copia previa y vuelve a
vincular las secundarias. No activa en esta instalación la licencia del respaldo.
**Esta ampliación aún requiere pruebas en PC y móviles.** Consulta `desktop/VERIFICACION_PC.md`
y el alcance de `desktop/README.md`; implementación no equivale a verificación.

### Correcciones, transferencias e insumos

En **Historial de ventas → Corregir**, modifica el carrito y escribe el motivo. Puedes cambiar
cantidades, artículos y método de pago. Solo se admite con el turno original abierto, también si
la venta procede de una secundaria. El original se conserva anulado y se crea un reemplazo; si
hay un error no se guarda ninguna parte de la corrección. La secundaria recibe ambos cambios.

El total del diálogo es una cotización sin consumo ni registro. Al confirmar se recalculan precios
y existencias. Un insumo solo se vende si le has asignado precio de venta en **Editar artículo**;
se venden unidades enteras y se descuentan milésimas del stock.

Para transferencias, escribe el número de operación. **Cliente fijo** guarda el documento y teléfono
válidos; puedes editar o eliminar la ficha en Clientes sin modificar ventas anteriores. La tabla de
transferencias no muestra al vendedor. Los teléfonos se normalizan a E.164; se admiten cuentas de
12–20 cifras, de acuerdo con Android.

### Fotos y metadatos

**Editar** abre nombre, categoría/tipo, descripción, caducidad, precios, cantidades y umbrales según
el artículo. Las recetas se editan en su sección. Puedes restaurar productos/servicios archivados.
Las fotos se normalizan, sin metadatos EXIF, y aparecen en las tarjetas promocionales. `.spvidesk`
las conserva; Android no transporta los archivos de las fotos ni importa la extensión `webFotos`.

### Respuestas perdidas y acceso

Si una respuesta se pierde, reintenta **sin cambiar los datos**: la interfaz conserva la clave de
esa operación y el servidor no vuelve a aplicarla. No borres el almacenamiento de la pestaña mientras
resuelves una operación incierta. Al restaurar otro negocio se invalidan las solicitudes antiguas.

Para recuperar o cambiar la clave local, cierra SPVI y usa `SPVI.exe --reset-password` en la PC del
dueño, indicando `--data-dir` si corresponde. No borra negocio, licencia ni emparejamientos. Nunca
compartas esa clave en el chat. La recuperación requiere acceso al directorio desde la cuenta Windows.
