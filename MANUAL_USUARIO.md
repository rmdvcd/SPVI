# Shalev — Guía de cambios Android en desarrollo

## Mejoras Android en curso (versión aún no actualizada en el código)

- **Inicio:** «Turno cerrado» (o «Turno abierto») y, debajo, el selector de período. Con el aviso de
  licencia forman el bloque de arriba y son **lo único que no se desplaza**: quedan fijos mientras miras
  los gráficos y las alertas.
- **Inventario:** cada fila muestra, debajo del nombre, **cuánto puedes hacer** con lo que tienes y su
  unidad (u, L, g, kg, cajas), y debajo la **categoría**. A la derecha están el precio de **costo** y el
  de **venta**, con colores distintos para no confundirlos. Los insumos sin precio de venta no lo muestran.
- **Registros:** todas las listas (ventas, transferencias, movimientos, turnos y clientes fijos) se
  pueden **marcar con la casilla redonda**, igual que en Inventario. Con algo marcado aparece la barra de
  selección: cuántos elementos son, seleccionar todo y quitar la selección. Exportar usa solo lo marcado.
- **Filtro de Registros:** en **Movimientos** la ventana solo pide el **período**: no hay campos de
  importe ni aviso, porque un movimiento no tiene importe. En ventas, transferencias y servicios siguen
  el mínimo, el máximo y el vendedor.
- **Filtro de Inventario:** el combo **Estado** ofrece **Todos, Stock bajo, Stock crítico, Próximos a
  caducar y Sin existencia**. Después van **Tipo** (artículos, elaborados, insumos) y **Categoría**.
- **Clientes fijos:** al abrir la ficha de un cliente, **Editar** y **Quitar de clientes fijos** están
  centrados en el pie de la ventana. **Editar** abre una ventana para corregir el **nombre**, el **carné**
  y el **teléfono**; si el carné ya es de otro cliente no se guarda y te lo avisa, y sus compras anteriores
  quedan con los datos corregidos.
- **Editar lo registrado:** con el turno **abierto** (y en la app principal), la ficha de una venta o un
  servicio deja **Modificar** (cantidades, método de pago y nº de transacción) y **Anular**; la ficha de una
  **transferencia** ofrece lo mismo sobre la venta que la pagó —su importe y su nº salen de ahí— y la de un
  **movimiento** deja **corregir la cantidad** de un *Ajuste*: se aplica al stock, la «existencia después»
  de los movimientos siguientes se corrige igual y el **motivo** queda anotado en el movimiento.
- **Licencia:** se quitó el botón de llave del final; el asistente avanza con *Paso siguiente*.
- **Actualizar** (antes «Actualizaciones»): al tocarlo sale un cuadro con una **barra de progreso
  circular** y al terminar dice *Actualizado*, *Estas al día* o *Sin conexión*.
- **Ayuda:** el manual está organizado por **secciones** (Empezar, Vender y cobrar, Inventario, Tus datos
  y tu licencia, Empleados y Ayuda), cada tema con su **icono** y, donde hace falta, un **esquema** dibujado.
- **Soporte:** las filas están centradas, en el orden **Icono, Descripción, Texto**.
- **Primera apertura:** la app muestra una ventana con el **checklist de permisos** (cámara,
  aviso de turno y fotos, cada uno con su para qué); *Continuar* abre el cuadro del sistema y
  *Ahora no* no insiste. Si los negaste, cada función los vuelve a pedir donde hacen falta.
- **Animaciones:** al tocar la barra de abajo el contenido entra y sale con un **fundido**; al
  **deslizar** el dedo, la pantalla se mueve **hacia los lados** con la dirección del gesto; las ventanas y
  los diálogos **suben desde el centro de abajo**.

## Pago electrónico

En **Ajustes → Pago electrónico**, marca el teléfono y la tarjeta que usarás al cobrar.
Solo puede haber uno de cada tipo. Marcar otro sustituye al anterior; tocar el ya marcado lo desmarca.
El icono de tarjeta es igual para todos los bancos. Los botones de editar y eliminar están uno junto al otro.
Antes de cobrar por transferencia, comprueba que tienes ambos seleccionados.

## Inicio

El interruptor del turno (tocarlo abre o cierra el turno), la lista de período y el aviso de licencia forman
el bloque de arriba de Inicio y **no se desplazan**: quedan fijos. Al desplazarte aparecen las alertas y, en la
principal, los gráficos y resúmenes de ventas, inventario,
métodos de pago y ganancia. Los gráficos y resúmenes se pliegan; empiezan cerrados y puedes abrir solo los que quieras consultar.
Inicio se abre siempre por arriba: no recuerda por dónde ibas la última vez.

## Cerrar turno: efectivo e inventario

1. En Inicio toca cerrar el turno. Cuenta los **billetes por denominación**: 1, 3, 5, 10, 20, 50, 100, 200, 500, 1000, **5000 y 20000 CUP**. Escribe cuántos tienes de cada uno; un campo vacío equivale a cero. Añade las monedas u otro efectivo aparte. Shalev suma el contado y muestra si sobra o falta respecto al esperado. Con los billetes a la vista hay que contar **al menos uno**; si prefieres escribir solo el total (o cero), pulsa **Omitir el conteo de billetes** en la esquina inferior derecha y desde ahí puedes volver a contar con **Contar billetes por denominación**.
2. Continúa al **conteo de inventario**. Ahora solo se cuenta **lo que cambió durante el turno** (los artículos que moviste): lo demás se da por bueno. Los artículos van separados por **pestañas de categoría** (Alimentos, Bebidas, Insumos…), así que vas contando por donde te toca. Escribe **0** cuando no quede nada. Los productos usan unidades enteras y los insumos admiten hasta tres decimales (punto o coma). Los elaborados no se cuentan como stock: se cuentan sus insumos. Si en el turno no cambió nada, no hay nada que contar. Si no vas a contar el inventario ahora, pulsa **Omitir el conteo de inventario** (esquina inferior derecha): el turno se cierra **sin ajustar existencias** y el conteo queda anotado con el motivo «Conteo de inventario omitido».
3. Revisa esperado, contado y diferencia. Si hay diferencias, explica el **motivo** (3–160 caracteres) y confirma. En la principal, se registran los ajustes y se cierra el turno juntos; si falla una comprobación, no se aplican ajustes parciales.
4. En una **app de empleado**, confirmar **solo envía el conteo**. El encargado lo revisa en **Apps vinculadas → empleado** y aprueba o rechaza. Solo la principal modifica el inventario del negocio. Incluso si el encargado pidió el cierre, un conteo nuevo de inventario requiere su aprobación.

Si el inventario cambió mientras contabas, no se sobrescribe: vuelve a cargar y contar. En la principal, si un artículo se movió después de que el empleado contara, la diferencia se aplica sobre el valor actual (no se rechaza el conteo). El encargado puede rechazar la solicitud para pedir un nuevo conteo. Registros → Turnos → Caja muestra el desglose y la conciliación; también van en el PDF/Excel del turno y en el respaldo.

**Compatibilidad:** para usar este flujo, actualiza la principal y las apps de empleados. Una principal antigua no puede aprobar el nuevo conteo; no se debe combinar con secundarias que ya lo requieran. Estos cambios están en desarrollo, pendientes de verificación en dispositivos.

## Apps vinculadas (empleados)

- **La sincronización es manual.** Ya no hay sincronización automática: el empleado toca **Sincronizar ahora** en «Apps vinculadas» cuando quiere enviar lo que ha vendido o traer el catálogo. Dos cosas siguen siendo automáticas: al **abrir turno** se sincroniza (no se empieza un turno con el catálogo viejo) y al **pedir el cierre** se envía la solicitud. Lo que queda pendiente de enviar se ve en «N pendientes de enviar».
- **La conexión se mantiene abierta.** La app del empleado se queda conectada a la principal mientras esté en la misma red (y se reconecta sola si se cae), así el encargado ve si está o no.
- **Aviso de cambios.** Si el encargado cambia el catálogo, la app del empleado lo dice («Sincroniza para recibirlo»).
- **El fondo de caja lo escribe quien abre el turno**, también en la app de un empleado: el encargado ya no lo asigna ni lo pide.
- **Barra de avance.** Mientras se sincroniza, hay una barra de avance en la notificación y, en la principal, una barra por cada app conectada en «Apps vinculadas».
- **Sonidos.** La principal avisa con sonido cuando la app de un empleado **se conecta**, **se desconecta** y **termina de sincronizar**; la app del empleado avisa cuando **termina su sincronización**. Los sonidos se pueden cambiar en los ajustes de notificaciones de Android (canales «App conectada», «App desconectada» y «Sincronización terminada»).
- **Fotos.** La app del empleado recibe las **fotos de los productos y servicios** del catálogo; las descarga la primera vez y las guarda en el teléfono.

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
Mientras una app vinculada sincroniza hay otra notificación con la **barra de avance**.
