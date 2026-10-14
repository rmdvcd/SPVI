'use strict';
/* Ayuda y Soporte: manual breve por secciones con icono y datos del desarrollador (como en la app móvil). */

const TEMAS_AYUDA = [
  {icono: 'Turno', titulo: 'Turno y caja', lineas: [
    'Abre el turno con el efectivo con el que empiezas. Sin turno abierto no se puede vender.',
    'Cerrar turno compara el efectivo contado con el esperado: fondo inicial + ventas en efectivo ± movimientos de caja.',
    'Las entradas y salidas de caja se registran con motivo y quedan en Registros > Caja.']},
  {icono: 'Venta', titulo: 'Venta', lineas: [
    'Elige los artículos del catálogo y pulsa Cobrar. El cobro puede ser en efectivo o por transferencia.',
    'En transferencia se guarda el número de la transacción y, si eliges cliente fijo, su nombre, carné y teléfono.',
    'Solo se pueden anular o corregir ventas del turno abierto. Al anular, las existencias vuelven al inventario.']},
  {icono: 'Inventario', titulo: 'Inventario', lineas: [
    'Productos: existencias, costo, precio de venta, categoría y fecha de caducidad.',
    'Elaborados: se preparan con insumos según su receta. La fila muestra cuántos se pueden hacer con lo que hay en existencia.',
    'Insumos: se miden en unidad, kilogramo, gramo, litro o mililitro, con avisos de existencias bajas y críticas.',
    'Archivados: lo que dejas de usar se archiva y se puede restaurar; las ventas anteriores no se borran.']},
  {icono: 'Servicios', titulo: 'Servicios', lineas: [
    'Cada servicio tiene su importe y, si consume insumos, la lista de lo que gasta cada vez que se cobra.',
    'Se exportan solos (con foto) desde Servicios o desde Registros > Servicios.']},
  {icono: 'Registros', titulo: 'Registros y exportar', lineas: [
    'Reúne ventas, servicios, transferencias, movimientos de inventario, caja, turnos y clientes fijos.',
    'Tiene búsqueda, selector de período y páginas de 50 registros.',
    'El botón Exportar de la barra superior prepara PDF, Excel, imágenes o tarjetas promocionales.']},
  {icono: 'Respaldo', titulo: 'Respaldo', lineas: [
    'Ajustes > Respaldo descarga un archivo .spvidesk con todos los datos del negocio.',
    'Desde el mismo sitio se importa un respaldo .spvi de la app móvil.',
    'Restaurar reemplaza todos los datos actuales: comprueba el respaldo antes de restaurar.']},
  {icono: 'Vinculacion', titulo: 'Apps vinculadas', lineas: [
    'Cada empleado tiene su código QR en Ajustes > Empleados; lo escanea la app móvil para conectarse como secundaria.',
    'La web manda los datos y recibe las ventas por la red local. El estado de cada app se ve en Apps vinculadas y en Tiempo en línea.',
    'A cada app se le dan permisos: vender productos, vender servicios, editar inventario, cambiar precios o exportar.']},
  {icono: 'Licencia', titulo: 'Licencia', lineas: [
    'El periodo de prueba funciona 5 días con hasta 5 apps secundarias.',
    'Para activar, Ajustes > Licencia prepara la solicitud y la envías por WhatsApp al desarrollador con el número de transacción del pago.',
    'El desarrollador responde con un código de 16 dígitos que se escribe en el paso 2 de esa misma pantalla.']},
  {icono: 'Ajustes', titulo: 'Ajustes', lineas: [
    'Negocio y propietario, pago electrónico, precios, avisos de inventario, respaldo, clave de acceso, inicio con Windows y tema.',
    'La clave de acceso protege SPVI cuando se abre desde otro equipo de la red.']},
];

const DATOS_SOPORTE = [
  {icono: 'Desarrollador', descripcion: 'Desarrollador', texto: 'Ing. Ronnie Montero Duarte'},
  {icono: 'Identidad', descripcion: 'Carné de identidad', texto: '91040922502'},
  {icono: 'Especialidad', descripcion: 'Especialidad', texto: 'Desarrollo de sistemas y aplicaciones multiplataforma'},
  {icono: 'Telefono', descripcion: 'Teléfono', texto: '+53 51815604'},
  {icono: 'WhatsApp', descripcion: 'WhatsApp', texto: 'wa.me/5351815604'},
];

SPVI.views.ayuda = async function (root) {
  root.append(h('div', {class: 'encabezado'}, h('h1', {text: 'Ayuda'})),
    h('p', {class: 'secundario', text: 'Cómo se hace cada cosa en SPVI para PC. Abre una sección para ver el detalle.'}),
    h('div', {class: 'lista'}, TEMAS_AYUDA.map(tema => h('details', {class: 'bloque'},
      h('summary', {class: 'item-lista'}, leadIcon(tema.icono),
        h('span', {class: 'texto'}, h('span', {class: 'titulo', text: tema.titulo})), icon('Desplegar', 'sm')),
      h('div', {class: 'detalle'}, tema.lineas.map(linea => h('p', {class: 'secundario', text: '· ' + linea})))))));
};

SPVI.views.soporte = async function (root) {
  root.append(h('div', {class: 'encabezado'}, h('h1', {text: 'Soporte'})),
    h('div', {class: 'card centro'}, icon('Soporte', 'lg'), h('h3', {text: 'Atención del desarrollador'}),
      h('p', {class: 'secundario', text: 'Escribe o llama para activar la licencia, resolver dudas o pedir una función nueva.'})),
    h('div', {class: 'lista'}, DATOS_SOPORTE.map(d => itemLista({lead: leadIcon(d.icono), title: d.descripcion, subtitle: d.texto, cls: 'centro'}))),
    h('div', {class: 'fila centro'}, button('Abrir WhatsApp', () => window.open('https://wa.me/5351815604', '_blank', 'noopener'), {kind: 'whatsapp', iconName: 'WhatsApp'}),
      button('Copiar teléfono', () => busy(null, async () => {
        if (!navigator.clipboard) throw new Error('Copia el número a mano: +53 51815604.');
        await navigator.clipboard.writeText('+5351815604'); snack('Teléfono copiado.');
      }), {kind: 'tonal', iconName: 'Copiar'})));
};
