# Manual de uso de SPVI

SPVI ayuda a organizar las ventas y el inventario del negocio. Las pantallas pueden cambiar un poco según los permisos de la licencia y la versión instalada. En la app también hay una guía breve en **Ajustes → Ayuda**.

## 1. Antes de empezar

- SPVI funciona en Android 8.0 o posterior.
- La información del negocio se guarda cifrada en el teléfono. No hace falta crear una cuenta.
- La prueba inicial dura 7 días. Después se necesita una licencia.
- Puedes completar el asistente inicial ahora o continuar y terminarlo luego en **Ajustes → Completar configuración**.
- Para pedir la licencia, añade tus datos si quieres y sigue los pasos de **Ajustes → Licencia**. La respuesta se pega en SPVI; no se leen los SMS en segundo plano.
- En un teléfono principal puedes activar un bloqueo opcional con la huella, el rostro o el PIN del dispositivo: **Ajustes → Acceso con clave**.

## 2. Elegir el tipo de app

Cada negocio tiene un teléfono **principal**, que administra los datos y la licencia. Si la licencia lo permite, los empleados pueden usar apps **secundarias** vinculadas a la principal.

- La principal crea la vinculación y muestra un QR de un solo uso en **Ajustes → Apps vinculadas**.
- En el teléfono del empleado, elige **Usar esta app como secundaria**, escribe su número y lee el QR de la principal. Ambos teléfonos deben estar conectados a la misma red local.
- La principal asigna los permisos y el medio de cobro de cada empleado. El empleado no puede cambiar lo que administra la principal.
- Si se pierde la conexión, una secundaria puede continuar trabajando con los datos que tiene. Para abrir otro turno puede necesitar sincronizar primero.
- El empleado solicita el cierre de su turno; la persona dueña lo aprueba o lo rechaza.

La cámara se usa para esta vinculación y para tomar fotos de productos o servicios. Si no quieres usarla, también puedes escribir los datos manualmente y omitir las fotos.

## 3. Completar la configuración

En **Ajustes → Completar configuración** puedes volver a los datos del negocio y los avisos de inventario. Los datos personales no son obligatorios para empezar. Los niveles bajo y crítico determinan cuándo se muestran avisos; el crítico debe ser igual o menor que el nivel bajo.

Para cobrar por transferencia, configura la tarjeta y el teléfono en **Ajustes → Pago electrónico**. Elige cuál queda **En uso**. Si una secundaria cobra con sus propios datos, la persona dueña los configura desde **Apps vinculadas**.

## 4. Productos, insumos y servicios

### Productos

1. Abre **Inventario** y toca **+**.
2. Escribe el nombre, la categoría y los precios de costo y venta.
3. Añade la cantidad disponible. Puedes indicar niveles de stock, una fecha de caducidad, una descripción y una foto.
4. Toca **✓ Guardar**.

Si ya existe un producto con el mismo nombre, usa una descripción corta para distinguirlo, por ejemplo «Lata 350 ml». Para cambiar una foto, abre el formulario del producto y elige la opción de cámara o imagen disponible.

### Insumos y productos elaborados

- En **Inventario**, selecciona la categoría **Insumos** para registrar materias primas, unidades de medida, costo y cantidad.
- Un insumo puede tener también precio de venta si se vende por separado.
- Para un producto elaborado, agrega una receta con los insumos y cantidades que consume cada unidad.
- SPVI calcula cuántas unidades se pueden preparar según los insumos disponibles y descuenta esos insumos al vender.

### Servicios

1. Abre **Servicios** y toca **+**.
2. Escribe nombre, tipo e importe.
3. Si el servicio consume materiales, añádelos como insumos.
4. Guarda con **✓**.

Los insumos vinculados a un servicio se descuentan cuando se registra una prestación.

## 5. Abrir turno y vender

No se puede registrar una venta sin un turno abierto.

1. En **Inicio**, abre el turno. Si el negocio maneja efectivo, registra el fondo inicial cuando corresponda.
2. Toca **Nueva venta** y elige **Productos** o **Servicios**.
3. Selecciona artículos y cantidades. SPVI muestra el total antes de confirmar.
4. Elige **Efectivo** o **Transferencia** y revisa la información.
5. Confirma la venta. Si sales antes de confirmar, la venta no queda registrada.

### Efectivo

La venta se registra en el turno y actualiza el inventario. Los movimientos de caja (por ejemplo, entradas o salidas) también se anotan dentro del turno y deben llevar un motivo.

### Transferencia

Configura primero el medio de cobro en **Ajustes → Pago electrónico**. En la venta, el QR muestra la cuenta configurada; el cliente debe enviar el importe que SPVI indica. Luego registra el número de transacción y los datos del cliente que correspondan.

SPVI **no envía ni confirma el pago bancario**. Comprueba el pago en la aplicación bancaria antes de entregar el producto o prestar el servicio. Si se marca **Cliente fijo**, el nombre, el carné y el teléfono se pueden sugerir en ventas futuras.

### Cerrar turno y revisar caja

Al terminar, cierra el turno y completa el conteo de efectivo si se solicita. El resumen del turno queda en **Registros → Turnos**; desde su detalle puedes revisar las pestañas de resumen, ventas, movimientos y caja, según el tipo de turno y permisos.

Una app secundaria puede pedir el cierre, pero lo autoriza la principal. Si una secundaria necesita efectivo inicial, la persona dueña puede asignar un fondo desde **Apps vinculadas**.

## 6. Consultar y compartir registros

En **Registros** puedes revisar ventas, servicios, transferencias, movimientos, turnos y clientes fijos. Usa **Filtrar** para ajustar fechas, importes o vendedor cuando esté disponible. Toca una fila para ver su detalle.

Según el registro y los permisos, puedes compartir o guardar un resumen en PDF o Excel. Las exportaciones no llevan contraseña; revisa los datos personales que contienen antes de enviarlas.

En **Inventario** también puedes filtrar, seleccionar varios artículos y exportar la lista o una ficha. Los informes reflejan los filtros que están activos.

## 7. Respaldo y cambio de teléfono

### Exportar una copia

1. En la app principal abre **Ajustes → Respaldo → Exportar**.
2. **Proteger con contraseña** viene activado. Escribe una contraseña de al menos 8 caracteres dos veces y guárdala en un lugar seguro.
3. Elige dónde guardar el archivo `.spvi` o con qué app compartirlo.

La contraseña no se puede recuperar. Si desactivas la protección, cualquiera que consiga el archivo podrá leer la información del negocio. El respaldo no contiene las fotos.

### Importar una copia

1. Abre **Ajustes → Respaldo → Importar** y selecciona el archivo.
2. Comprueba que SPVI lo reconozca; escribe la contraseña si la pide.
3. Confirma solo después de revisar el aviso.

**Importar reemplaza los datos que ya están en el teléfono.** Guarda una copia reciente antes de continuar y no uses el único respaldo en una prueba.

### Migrar a otro teléfono

Usa **Ajustes → Migrar a otro teléfono** y sigue los pasos en orden. No borres ni restablezcas el teléfono anterior hasta confirmar que los datos del nuevo están completos y que su licencia funciona. Si solo vas a trasladar los datos, también puedes exportar e importar un respaldo; la licencia instalada no se copia como licencia utilizable en otro teléfono.

## 8. Licencia, actualizaciones y ayuda

- **Ajustes → Licencia** muestra el estado y los pasos para solicitar, pegar y activar la respuesta. Si la solicitud es por SMS, puede llegar en varios mensajes: copia la respuesta completa.
- **Ajustes → Actualizaciones** permite buscar una versión cuando el build tiene configurado el repositorio. El teléfono muestra la confirmación de Android antes de instalar.
- En **Ajustes → Actualizaciones** también puedes ver cuándo SPVI recibió su última respuesta correcta. Si pasan 14 días sin confirmación, si nunca se logró consultar o si el reloj parece haber retrocedido, aparece un aviso cuando el repositorio está configurado. El aviso no sustituye a una conexión ni confirma que el teléfono esté actualizado.
- Abre **Ajustes → Ayuda** para los pasos breves, o **Ajustes → Soporte** para contactar al desarrollador.

## 9. Cuida tus datos

- No compartas la contraseña del respaldo. SPVI no puede recuperarla.
- Guarda una copia fuera del teléfono y comprueba que recuerdas su contraseña.
- Trata con cuidado los PDF y Excel: pueden mostrar datos de clientes y no están cifrados.
- Antes de borrar datos, desinstalar o cambiar de teléfono, exporta un respaldo y asegúrate de que se puede abrir.
- Si algo no coincide, revisa el turno y el registro antes de modificar datos. SPVI registra las ventas y los movimientos; no sustituye la comprobación de un pago real.
