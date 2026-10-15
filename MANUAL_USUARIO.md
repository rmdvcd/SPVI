# SPVI — Guía de los cambios en desarrollo

## Novedades del teléfono (0.31.0)

- **Inicio:** «Turno cerrado» (o «Turno en curso») y el selector de período están en la misma fila, con
  el mismo tamaño. Ese primer contenedor es el único que **no se desplaza**: queda fijo arriba mientras
  miras los gráficos y las alertas.
- **Inventario:** cada fila muestra, debajo del nombre, **cuánto puedes hacer** con lo que tienes y su
  unidad (u, L, g, kg, cajas), y debajo la **categoría**. A la derecha están el precio de **costo** y el
  de **venta**, con colores distintos para no confundirlos. Los insumos sin precio de venta no lo muestran.
- **Registros:** todas las listas (ventas, transferencias, movimientos, turnos y clientes fijos) se
  pueden **marcar con la casilla redonda**, igual que en Inventario. Con algo marcado aparece la barra de
  selección: cuántos elementos son, seleccionar todo y quitar la selección. Exportar usa solo lo marcado.
- **Licencia:** se quitó el botón de llave del final; el asistente avanza con *Paso siguiente*.
- **Migrar** (antes «Migrar a otro teléfono»): pasa los datos de un teléfono a otro. Elige el rol de cada
  uno: **Emisor** (entrega la copia y su teléfono se borra al terminar) o **Receptor** (la recibe). El emisor
  muestra un **código QR**; el receptor lo lee con la cámara. Todo el camino está protegido por el
  **acceso con clave** de Ajustes: si no lo has definido, la app te lo dice antes de empezar. También se
  explica en el **recorrido del primer arranque**.
- **Actualizar** (antes «Actualizaciones»): al tocarlo sale un cuadro con una **barra de progreso
  circular** y al terminar dice *Actualizado*, *Estas al día* o *Sin conexión*.
- **Ayuda:** el manual está organizado por **secciones** (Empezar, Vender y cobrar, Inventario, Registros,
  Tus datos y tu licencia), cada tema con su **icono** y, donde hace falta, un **esquema** dibujado.
- **Soporte:** las filas están centradas, en el orden **Icono, Descripción, Texto**.
- **Primera apertura:** la app pide **todos los permisos que necesita de una vez y sin textos** (cámara,
  notificaciones y fotos): solo sale el cuadro del sistema. Si los negaste, actívalos en Ajustes → Permisos.
- **Animaciones:** al tocar la barra de abajo el contenido entra y sale con un **fundido**; al
  **deslizar** el dedo, la pantalla se mueve **hacia los lados** con la dirección del gesto; las ventanas y
  los diálogos **suben desde el centro de abajo**.

> La app de iOS (`flutter_app/`) recibe los mismos cambios, con dos diferencias honestas: el código de
> migración se muestra como texto (no trae librería de QR) y los permisos los pide iOS cuando hace falta,
> no todos al abrir.

## Pago electrónico

En **Ajustes → Pago electrónico**, marca el teléfono y la tarjeta que usarás al cobrar.
Solo puede haber uno de cada tipo. Marcar otro sustituye al anterior; tocar el ya marcado lo desmarca.
El icono de tarjeta es igual para todos los bancos. Los botones de editar y eliminar están uno junto al otro.
Antes de cobrar por transferencia, comprueba que tienes ambos seleccionados.

## Inicio

El interruptor del turno (tocarlo abre o cierra el turno) y la lista de período forman el primer
contenedor de Inicio y **no se desplazan**: queda fijo arriba. Debajo hay dos zonas: la **principal**
(indicadores, alertas, las 7 listas del móvil —más vendido, lento movimiento, rentabilidad, servicios top/menos,
empleado y cliente, cada una oculta si no tiene datos— y listas recientes) y la **secundaria**, solo de datos (gráficos de
ingresos, cobro y ganancia, red local, conexiones y tiempo en línea, con enlaces a sus secciones).

## Buscar y consultar transferencias

Los buscadores de Registros conservan su texto de ejemplo. Las transferencias muestran el vendedor en la lista/tabla;
la ficha mantiene además los datos completos de la operación.

## Exportar

- **PDF y Excel:** informes de inventario, servicios, turnos y Registros (ventas, transferencias y movimientos).
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
Lo que el móvil tiene y la web no, con lo que **sí** y lo que **no** conviene llevar a la PC, está en
[el informe de feedback](desktop/INFORME_FEEDBACK_MOVIL.md).
Incluye el servidor principal para secundarias Android, pendiente de prueba real con móviles.
Es independiente y solo principal; no sustituye la aplicación móvil.

### SPVI en la PC (0.31.0)

- **Abrir y cerrar:** SPVI vive en el icono junto al reloj de Windows. Doble clic abre la pantalla principal;
  clic derecho muestra *Pantalla principal*, *Apps conectadas*, *Tiempo en línea*, *Ajustes*, *Reiniciar* y
  *Salir*. Cerrar la pestaña del navegador no cierra SPVI.
- **Menú:** el botón ☰ de arriba a la izquierda muestra u oculta el menú lateral con Inicio, Venta,
  Inventario, Servicios, Registros, Apps vinculadas, Tiempo en línea y Ajustes.
- **Clave de acceso (opcional):** en *Ajustes › Clave de acceso* se crea, cambia o quita. Si la olvidas,
  sal de SPVI y ejecuta `SPVI.exe --reset-password`: se quita y puedes crear otra.
- **Inicio con Windows:** en *Ajustes › Inicio de SPVI* elige si SPVI arranca con Windows, cuántos segundos espera
  y si abre la pantalla principal o solo el icono.
- **Tema:** *Ajustes › Tema*: Claro, Oscuro o el del sistema.
- **Tiempo en línea:** cuánto lleva abierto SPVI en esta PC y la red local, y por cada app vinculada si está
  conectada, el tiempo conectada y su última sincronización.
- **Licencia:** en *Ajustes › Licencia* envía tu solicitud al desarrollador por WhatsApp con tus datos,
  el tipo, las apps secundarias y el número de transacción del pago. El desarrollador responde con un código
  `XXXX-XXXX-XXXX-XXXX`: escríbelo en *Activar licencia* y pulsa *Activar*.

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
