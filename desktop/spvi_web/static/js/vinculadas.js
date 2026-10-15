'use strict';
/* Apps vinculadas: red local de sincronización y apps móviles secundarias (vinculación por QR, permisos, fondo y cierre). */

const PERMISOS = {VENDER_PRODUCTOS: 'Vender productos', VENDER_SERVICIOS: 'Vender servicios', EDITAR_INVENTARIO: 'Editar inventario', CAMBIAR_PRECIOS: 'Cambiar precios', EXPORTAR: 'Exportar'};

async function mostrarQr(path, payload, titulo, texto) {
  const respuesta = await apiBlob(path, payload);
  const url = URL.createObjectURL(await respuesta.blob());
  dialog({title: titulo, iconName: 'Escanear', body: [h('div', {class: 'centro form'}, h('img', {class: 'qr', src: url, alt: 'Código QR'}), h('p', {text: texto}))],
    actions: [{label: 'Listo', primary: true}], onClose: () => URL.revokeObjectURL(url)});
}

SPVI.views.vinculadas = async function (root) {
  const [catalogo, licencia, red, empleados] = await Promise.all([SPVI.load.catalogo(), SPVI.load.licencia(), api('/api/network'), api('/api/employees')]);
  const activos = empleados.filter(e => e.active);
  root.append(h('div', {class: 'encabezado'}, h('h1', {text: 'Apps vinculadas'})));
  SPVI.actions(iconButton('Sincronizar', 'Actualizar', () => SPVI.render()));

  // Red local
  const tarjetaRed = h('div', {class: 'card'}, h('div', {class: 'cabecera'}, icon('RedLocal'), h('h3', {text: 'Red local'}),
    h('span', {class: 'etiqueta ' + (red.running ? 'ok' : ''), text: red.running ? 'Activa' : 'Detenida'})));
  if (red.running) {
    tarjetaRed.append(h('p', {text: 'Las apps secundarias sincronizan con esta PC en ' + red.address.join(':') + '.'}),
      h('p', {class: 'secundario', text: red.connected.length ? (red.connected.length === 1 ? '1 app conectada ahora.' : red.connected.length + ' apps conectadas ahora.') : 'Ninguna app conectada en este momento.'}),
      h('div', {class: 'fila'}, button('Detener', () => busy(null, async () => { await api('/api/network', {action: 'stop'}); snack('Red local detenida.'); await SPVI.render(); }), {kind: 'borde', iconName: 'Apagar'})));
  } else {
    const opciones = red.candidatas.map(ip => [ip, ip]);
    if (red.guardada && !red.candidatas.includes(red.guardada.host)) opciones.unshift([red.guardada.host, red.guardada.host]);
    const formulario = h('form', {class: 'form', onsubmit: event => event.preventDefault()},
      h('p', {class: 'secundario', text: 'Elige la dirección de esta PC en la red Wi-Fi del negocio. Las apps secundarias deben estar en la misma red.'}),
      h('div', {class: 'par'},
        opciones.length ? seleccion({label: 'Dirección de esta PC', name: 'host', options: opciones, value: red.guardada?.host || opciones[0][0]})
          : campo({label: 'Dirección de esta PC', name: 'host', required: true, maxlength: 45, placeholder: '192.168.1.10', help: 'No se detectó una red local; escríbela.'}),
        campo({label: 'Puerto', name: 'port', type: 'number', min: 1024, max: 65535, step: 1, required: true, value: red.guardada?.port || 47811})));
    const iniciar = button('Iniciar red local', () => busy(iniciar, async () => {
      if (!formulario.reportValidity()) return;
      const data = Object.fromEntries(new FormData(formulario));
      await api('/api/network', {action: 'start', host: data.host, port: Number(data.port)}); snack('Red local activa.'); await SPVI.render();
    }), {iconName: 'RedLocal', disabled: licencia.clase === 'BLOQUEADA'});
    tarjetaRed.append(formulario, h('div', {class: 'fila'}, iniciar));
  }
  if (red.last_error) tarjetaRed.append(h('p', {class: 'secundario', text: 'Último intento rechazado: ' + fechaHora(red.last_error.at) + '. Las ventas no confirmadas siguen guardadas en el móvil.'}));
  root.append(tarjetaRed);

  // Apps secundarias
  const plazas = licencia.secundarias || 0;
  root.append(h('div', {class: 'encabezado'}, h('h3', {text: 'Apps secundarias'}), h('span', {class: 'etiqueta', text: activos.length + ' de ' + plazas + ' plazas'})));
  const perfil = catalogo.perfil || {};
  if (!activos.length) root.append(h('div', {class: 'card'}, vacio('Vinculacion', plazas ? 'Agrega una app secundaria y escanea su código desde el móvil.' : 'Tu licencia no incluye apps secundarias.')));
  const grid = h('div', {class: 'grid'});
  for (const e of activos) {
    const conectada = red.connected.includes(e.id);
    const avisos = [e.requests_fund ? h('span', {class: 'etiqueta mal', text: 'Pide fondo'}) : null, e.requests_close ? h('span', {class: 'etiqueta mal', text: 'Pide cerrar turno'}) : null].filter(Boolean);
    const card = h('div', {class: 'card'},
      h('div', {class: 'cabecera'}, h('span', {class: 'punto' + (conectada ? ' on' : ''), title: conectada ? 'Conectada' : 'Sin conexión'}), h('h3', {text: e.name}),
        iconButton('Escanear', 'Código de vinculación', () => busy(null, () => mostrarQr('/api/employees/qr', {id: e.id}, 'Vincular ' + e.name, 'En el móvil secundario, abre Vinculación y escanea este código. Caduca pronto: genera otro si hace falta.')))),
      h('p', {class: 'secundario', text: [e.linked ? 'Vinculada' : 'Pendiente de vincular', 'Última sincronización: ' + hace(e.last_sync), e.version ? 'Versión ' + e.version : null].filter(Boolean).join(' · ')}),
      avisos.length ? h('div', {class: 'fila'}, avisos) : null,
      h('div', {class: 'fila'},
        button('Permisos', () => permisos(e), {kind: 'tonal', iconName: 'Configurar'}),
        button('Fondo', () => fondo(e), {kind: 'tonal', iconName: 'Efectivo'}),
        e.requests_close ? button('Aprobar cierre', () => accion(e, 'close', 'Cierre aprobado.'), {iconName: 'Confirmar'}) : button('Pedir cierre', () => accion(e, 'close', 'Cierre solicitado.'), {kind: 'texto', iconName: 'Turno'}),
        e.requests_close ? button('Rechazar cierre', () => accion(e, 'reject-close', 'Cierre rechazado.'), {kind: 'texto', iconName: 'Cancelar'}) : null,
        (perfil.tarjetas?.length || perfil.telefonos?.length) ? button('Cobros', () => cobros(e), {kind: 'texto', iconName: 'PagoElectronico'}) : null,
        button('Desvincular', () => desvincular(e), {kind: 'texto', iconName: 'Desvincular'})));
    grid.append(card);
  }
  root.append(grid);
  if (activos.length < plazas) root.append(h('button', {type: 'button', class: 'fab extendida', onclick: nueva, disabled: licencia.clase === 'BLOQUEADA'}, icon('AgregarEmpleado'), 'Agregar app'));

  async function accion(e, action, mensaje, extra = {}) { await busy(null, async () => { await api('/api/employees', {action, id: e.id, ...extra}); snack(mensaje); await SPVI.render(); }); }
  function nueva() {
    dialog({title: 'Agregar app secundaria', iconName: 'AgregarEmpleado', body: [campo({label: 'Nombre del empleado', name: 'name', maxlength: 120, required: true}),
      h('p', {class: 'secundario', text: 'Después, inicia la red local y escanea el código desde el móvil del empleado.'})],
    actions: [{label: 'Cancelar'}, {label: 'Agregar', primary: true, icon: 'Confirmar', onClick: async ({data}) => { await api('/api/employees', {action: 'create', name: data.name}); snack('App agregada.'); await SPVI.render(); }}]});
  }
  function permisos(e) {
    dialog({title: 'Permisos de ' + e.name, iconName: 'Configurar', body: Object.entries(PERMISOS).map(([id, label]) =>
      h('label', {class: 'casilla'}, h('input', {type: 'checkbox', name: id, checked: e.permissions.includes(id)}), label)),
    actions: [{label: 'Cancelar'}, {label: 'Guardar', primary: true, icon: 'Guardar', onClick: async ({form}) => {
      await api('/api/employees', {action: 'permissions', id: e.id, permissions: Object.keys(PERMISOS).filter(id => form.elements[id].checked)}); snack('Permisos guardados.'); await SPVI.render();
    }}]});
  }
  function fondo(e) {
    dialog({title: 'Fondo para ' + e.name, iconName: 'Efectivo', body: [h('p', {class: 'secundario', text: 'Efectivo inicial del próximo turno de esta app. Se envía en la próxima sincronización.'}),
      campo({label: 'Fondo (CUP)', name: 'amount', type: 'number', min: 0, step: '0.01', required: true, inputmode: 'decimal', value: e.fund !== null && e.fund !== undefined ? pesos(e.fund) : ''})],
    actions: [{label: 'Cancelar'}, {label: 'Asignar', primary: true, icon: 'Confirmar', onClick: async ({data}) => { minor(data.amount); await api('/api/employees', {action: 'fund', id: e.id, amount: data.amount}); snack('Fondo asignado.'); await SPVI.render(); }}]});
  }
  function cobros(e) {
    const tarjetas = [['', 'La del negocio'], ...(perfil.tarjetas || []).map(t => [String(t.id), '•••• ' + t.numero.slice(-4) + (t.alias ? ' · ' + t.alias : '')])];
    const telefonos = [['', 'El del negocio'], ...(perfil.telefonos || []).map(t => [String(t.id), t.numero + (t.alias ? ' · ' + t.alias : '')])];
    dialog({title: 'Cuentas de cobro', iconName: 'PagoElectronico', body: [h('p', {class: 'secundario', text: 'Cuenta que verá ' + e.name + ' al cobrar por transferencia.'}),
      seleccion({label: 'Tarjeta', name: 'card', options: tarjetas, value: e.card_id ? String(e.card_id) : ''}),
      seleccion({label: 'Teléfono', name: 'phone', options: telefonos, value: e.phone_id ? String(e.phone_id) : ''})],
    actions: [{label: 'Cancelar'}, {label: 'Guardar', primary: true, icon: 'Guardar', onClick: async ({data}) => {
      await api('/api/employees', {action: 'payment', id: e.id, card_id: data.card ? Number(data.card) : null, phone_id: data.phone ? Number(data.phone) : null}); snack('Cuentas guardadas.'); await SPVI.render();
    }}]});
  }
  async function desvincular(e) {
    if (await confirmar({title: 'Desvincular', text: '¿Desvincular la app de ' + e.name + '? Sincroniza antes sus ventas pendientes: después no podrá enviarlas.', confirm: 'Desvincular', danger: true})) {
      await accion(e, 'revoke', 'App desvinculada.');
    }
  }
};
