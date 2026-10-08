# SPVI — manual breve de usuario

## Primeros pasos

1. Abre SPVI y completa el asistente o termina la configuración después en **Ajustes → Completar configuración**.
2. En **Perfil**, escribe los datos del negocio. En **Pago electrónico**, guarda la tarjeta y el teléfono que aparecerán en el QR de Transfermóvil.
3. Carga los productos en **Inventario → +**. Para un producto normal, escribe costo de compra y precio de venta. Para un Elaborado, añade la receta de insumos; SPVI calcula el costo.
4. Crea los **Servicios** que ofreces. Si consumen insumos, añade cuánto usa cada uno; SPVI calcula el costo de esos insumos.

## Precios y ventas

- El precio de venta debe ser **mayor** que el costo. Un precio igual al costo tampoco se permite.
- El precio final después de los preajustes/descuentos también debe superar el costo; si un descuento lo deja igual o por debajo, SPVI no permite completar la venta.
- En un Insumo vendible, el precio de venta (si se configura) debe superar su costo.
- Los campos numéricos filtran la entrada mientras escribes y muestran la validación antes de guardar. Los importes aceptan punto decimal y el formato agrupado de SPVI, por ejemplo `1,450.00 CUP`; si pegas separadores mezclados o una agrupación incorrecta, el valor se rechaza y debes corregirlo. No se convierte silenciosamente en otro importe.
- Abre un turno desde **Inicio** antes de vender. Elige artículos, cantidades y forma de pago; las existencias se descuentan al confirmar.
- Un Elaborado o Servicio con receta solo se ofrece cuando hay insumos suficientes.

## Caja y registros

- Al abrir un turno, registra el fondo de caja. Al cerrarlo, cuenta el efectivo; **Cuadra** copia el importe esperado.
- Registra entradas y salidas con un motivo. Los movimientos no se borran: se corrigen con el movimiento contrario.
- Consulta ventas, transferencias, movimientos, clientes y turnos en **Registros**. Puedes exportar los informes disponibles a PDF o Excel.

## Copias de seguridad

Ve a **Ajustes → Respaldo**. El respaldo incluye los datos del negocio, pero no incluye las fotos ni la licencia; importar un respaldo reemplaza los datos que ya hay en ese teléfono.

La contraseña es opcional:

- **Con contraseña:** el archivo `.spvi` queda protegido con la contraseña que escribas. Confírmala y guárdala en un lugar seguro; SPVI no puede recuperarla.
- **Sin contraseña:** cualquiera que obtenga el archivo puede leer los datos del negocio. Antes de guardar o compartir, SPVI presenta una advertencia que debes confirmar. Si hay biometría disponible, también pide huella o reconocimiento facial, sin ofrecer el PIN como sustituto. La biometría solo autoriza la exportación: **no añade una clave, no cifra ni protege el archivo**.

Si no quieres exportarlo, cancela el aviso o la solicitud biométrica; no se inicia la exportación. Guarda una copia protegida si el archivo va a salir del teléfono.

## Licencia, conexión y privacidad

- La licencia se activa con la respuesta de GL. La app puede funcionar sin conexión después, pero la activación, las actualizaciones y la consulta de revocaciones requieren internet.
- La sincronización entre la app principal y las secundarias ocurre por la red local del negocio; no se envían las ventas a una nube de SPVI.
- El acceso con huella o PIN/patrón del teléfono es opcional y se configura en **Ajustes → Acceso con clave**. Es independiente de la confirmación biométrica para exportar un respaldo.
- Para recuperar datos tras cambiar de teléfono, exporta el respaldo antes. La licencia se gestiona por separado.

## Ayuda

En la aplicación, abre **Ajustes → Ayuda**. Para soporte, usa el contacto que aparece en **Ajustes → Soporte**.
