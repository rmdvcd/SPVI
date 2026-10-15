'use strict';
/* Venta: catálogo a la izquierda y carrito a la derecha; cobro en efectivo o por transferencia; corrección de ventas. */

SPVI.state.carrito = SPVI.state.carrito || {lineas: [], metodo: 'efectivo', correccion: null};

/** Artículos vendibles con su id local (el que usa /api/cart). */
function articulosVenta(catalogo) {
  const lista = [];
  for (const p of catalogo.productos) if (!p.eliminado && p.localId) {
    const elaborado = (p.categoria || '').toLowerCase() === 'elaborado';
    lista.push({id: p.localId, dominio: p.id, clase: elaborado ? 'elaborado' : 'producto', foto: 'productos', nombre: p.nombre, precio: p.precioVentaCent || 0,
      existencias: elaborado ? null : p.cantidad, detalle: elaborado ? 'Elaborado' : p.categoria, nivel: SPVI.nivel(p, 'productos', catalogo.preferencias || {})});
  }
  for (const s of catalogo.servicios) if (!s.eliminado && s.localId) lista.push({id: s.localId, dominio: s.id, clase: 'servicio', foto: 'servicios', nombre: s.nombre, precio: s.importeCent, existencias: null, detalle: s.tipo || 'Servicio'});
  for (const i of catalogo.insumos) if (!i.eliminado && i.localId && i.precioVentaCent !== null && i.precioVentaCent !== undefined) {
    lista.push({id: i.localId, dominio: i.id, clase: 'insumo', foto: null, nombre: i.nombre, precio: i.precioVentaCent, existencias: i.cantidadMil / 1000, unidad: UNIDAD_CORTA[i.unidad], detalle: 'Insumo', nivel: SPVI.nivel(i, 'insumos', catalogo.preferencias || {})});
  }
  return lista.sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
}

/** Llamado desde Registros: carga la venta en el carrito para corregirla. */
SPVI.corregir = async function (venta) {
  const catalogo = await SPVI.load.catalogo();
  const lineas = venta.detalles.map(d => {
    const clase = {PRODUCTO: 'productos', SERVICIO: 'servicios', INSUMO: 'insumos'}[d.clase || 'PRODUCTO'];
    const item = catalogo[clase].find(x => x.id === d.productoId);
    if (!item || !item.localId || item.eliminado) throw new Error('«' + d.nombre + '» ya no está en el catálogo. Restáuralo antes de corregir esta venta.');
    return {item_id: item.localId, nombre: d.nombre, cantidad: d.cantidad, precio: d.precioUnitarioCent};
  });
  SPVI.state.carrito = {lineas, metodo: venta.metodoPago.toLowerCase(), correccion: {id: venta.id, transaccion: venta.transaccion || null}};
  SPVI.go('venta');
};

SPVI.views.venta = async function (root) {
  const [catalogo, licencia] = await Promise.all([SPVI.load.catalogo(), SPVI.load.licencia()]);
  const carrito = SPVI.state.carrito;
  root.append(h('div', {class: 'encabezado'}, h('h1', {text: carrito.correccion ? 'Corregir venta' : 'Venta'})));
  if (!catalogo.turnoAbierto) {
    root.append(h('div', {class: 'card centro'}, vacio('Turno', 'Para vender, primero abre un turno.'),
      button('Abrir turno', SPVI.turno.abrir, {iconName: 'Turno', disabled: licencia.clase === 'BLOQUEADA'})));
    return;
  }
  SPVI.actions(iconButton('Efectivo', 'Movimiento de caja', SPVI.turno.caja), iconButton('Turno', 'Cerrar turno', SPVI.turno.cerrar));

  const articulos = articulosVenta(catalogo);
  let filtro = 'todo', texto = '';
  const rejilla = h('div', {class: 'catalogo'});
  const pintarCatalogo = () => {
    const q = texto.trim().toLocaleLowerCase('es');
    const visibles = articulos.filter(a => (filtro === 'todo' || a.clase === filtro) && (!q || a.nombre.toLocaleLowerCase('es').includes(q) || (a.detalle || '').toLocaleLowerCase('es').includes(q)));
    rejilla.replaceChildren(...(visibles.length ? visibles.map(a => {
      const agotado = a.existencias !== null && a.existencias <= 0;
      const img = a.foto ? h('img', {src: '/api/photo/' + a.foto + '/' + a.dominio, alt: '', loading: 'lazy', onerror: e => e.target.remove()}) : null;
      return h('button', {type: 'button', class: 'articulo', disabled: agotado, title: agotado ? 'Sin existencias' : 'Agregar al carrito', onclick: () => agregar(a)},
        img, h('span', {class: 'nombre', text: a.nombre}), h('span', {class: 'secundario', text: a.detalle || ''}),
        h('span', {class: 'dato', text: cup(a.precio)}),
        a.existencias !== null ? h('span', {class: 'secundario', style: SPVI.colorNivel(a.nivel) ? 'color:' + SPVI.colorNivel(a.nivel) + ';opacity:1' : '',
          text: agotado ? 'Agotado' : 'Existencias: ' + cantidad(a.existencias) + (a.unidad ? ' ' + a.unidad : '')}) : null);
    }) : [vacio('Buscar', articulos.length ? 'Ningún artículo coincide con la búsqueda.' : 'Agrega productos o servicios en Inventario.')]));
  };
  const filtros = h('div', {class: 'chips', role: 'group', 'aria-label': 'Filtrar'}, [['todo', 'Todo'], ['producto', 'Productos'], ['elaborado', 'Elaborados'], ['servicio', 'Servicios'], ['insumo', 'Insumos']]
    .filter(([id]) => id === 'todo' || articulos.some(a => a.clase === id))
    .map(([id, label]) => h('button', {type: 'button', class: 'chip', 'aria-pressed': String(id === filtro), onclick: event => {
      filtro = id; for (const chip of filtros.children) chip.setAttribute('aria-pressed', String(chip === event.currentTarget)); pintarCatalogo();
    }}, label)));

  // Carrito
  const lineasEl = h('div', {class: 'lineas'}), totalEl = h('div', {class: 'total', text: cup(0)}), notaTotal = h('p', {class: 'secundario'});
  const cobrar = button(carrito.correccion ? 'Guardar corrección' : 'Cobrar', () => cobrarVenta(), {iconName: carrito.correccion ? 'Guardar' : 'Cobrar'});
  const metodos = h('div', {class: 'chips', role: 'group', 'aria-label': 'Forma de pago'}, [['efectivo', 'Efectivo', 'Efectivo'], ['transferencia', 'Transferencia', 'Transferencia']].map(([id, label, ic]) =>
    h('button', {type: 'button', class: 'chip', 'aria-pressed': String(carrito.metodo === id), onclick: () => { carrito.metodo = id; for (const chip of metodos.children) chip.setAttribute('aria-pressed', String(chip.dataset.id === id)); cotizar(); }, dataset: {id}}, icon(ic, 'sm'), ' ', label)));
  let generacion = 0, temporizador = null, totalCotizado = 0;
  function cotizar() {
    clearTimeout(temporizador);
    const base = carrito.lineas.reduce((a, l) => a + l.precio * l.cantidad, 0);
    totalCotizado = base; totalEl.textContent = cup(base); notaTotal.textContent = '';
    cobrar.disabled = !carrito.lineas.length;
    if (!carrito.lineas.length) return;
    const g = ++generacion;
    temporizador = setTimeout(async () => {
      const payload = {lines: carrito.lineas.map(l => ({item_id: l.item_id, quantity: l.cantidad})), method: carrito.metodo};
      if (carrito.correccion) payload.id = carrito.correccion.id;
      try {
        const q = await api('/api/quote', payload); if (g !== generacion) return;
        totalCotizado = q.total; totalEl.textContent = cup(q.total);
        notaTotal.textContent = q.total !== base ? 'Incluye ajustes de precio (' + (q.total < base ? '−' : '+') + cup(Math.abs(q.total - base)) + ').' : '';
      } catch (error) { if (g === generacion) notaTotal.textContent = error.message; }
    }, 250);
  }
  // En pantallas estrechas el carrito queda debajo del catálogo: un botón flotante lo resume y lleva a él.
  const irCarrito = h('button', {type: 'button', class: 'fab extendida solo-estrecho', hidden: true, onclick: () => tarjetaCarrito.scrollIntoView({behavior: 'smooth', block: 'start'})});
  function pintarCarrito() {
    const unidades = carrito.lineas.reduce((a, l) => a + l.cantidad, 0);
    irCarrito.hidden = !unidades;
    irCarrito.replaceChildren(icon('AgregarCarrito'), unidades + (unidades === 1 ? ' artículo · ' : ' artículos · ') + cup(carrito.lineas.reduce((a, l) => a + l.precio * l.cantidad, 0)));
    lineasEl.replaceChildren(...(carrito.lineas.length ? carrito.lineas.map((l, i) => h('div', {class: 'linea'},
      h('div', {class: 'texto'}, h('div', {text: l.nombre}), h('div', {class: 'secundario', text: cup(l.precio) + ' c/u'})),
      h('div', {class: 'cantidad'},
        iconButton('Quitar', 'Restar uno', () => { if (l.cantidad > 1) l.cantidad--; else carrito.lineas.splice(i, 1); pintarCarrito(); }),
        h('span', {text: String(l.cantidad)}),
        iconButton('Agregar', 'Sumar uno', () => { l.cantidad++; pintarCarrito(); })),
      h('span', {class: 'dato', text: cup(l.precio * l.cantidad)}),
      iconButton('Eliminar', 'Quitar del carrito', () => { carrito.lineas.splice(i, 1); pintarCarrito(); }, 'peligro'))) : [vacio('AgregarCarrito', 'Toca un artículo para agregarlo.')]));
    cotizar();
  }
  function agregar(a) {
    const linea = carrito.lineas.find(l => l.item_id === a.id);
    if (linea) linea.cantidad++; else carrito.lineas.push({item_id: a.id, nombre: a.nombre, cantidad: 1, precio: a.precio});
    pintarCarrito();
  }

  function cobrarVenta() {
    const transferencia = carrito.metodo === 'transferencia';
    const prev = carrito.correccion?.transaccion || {};
    const body = [itemLista({title: 'Total a cobrar', value: cup(totalCotizado), lead: leadIcon(transferencia ? 'Transferencia' : 'Efectivo')})];
    if (carrito.correccion) body.push(campo({label: 'Motivo de la corrección', name: 'reason', maxlength: 120, required: true}));
    if (transferencia) {
      const lista = h('datalist', {id: 'clientes-fijos'}, catalogo.clientesFijos.map(c => h('option', {value: c.nombreApellidos})));
      const nombre = campo({label: 'Cliente (nombre y apellidos)', name: 'clienteNombre', maxlength: 120, list: 'clientes-fijos', autocomplete: 'off', value: prev.clienteNombre || ''});
      const ci = campo({label: 'Carné de identidad', name: 'clienteCi', maxlength: 11, inputmode: 'numeric', autocomplete: 'off', value: prev.clienteCi || ''});
      const tel = campo({label: 'Teléfono', name: 'clienteTelefono', maxlength: 20, inputmode: 'tel', autocomplete: 'off', value: prev.clienteTelefono || ''});
      const fijo = h('label', {class: 'casilla'}, h('input', {type: 'checkbox', name: 'clienteFijo', checked: !!prev.clienteFijo}), 'Guardar como cliente fijo');
      nombre.querySelector('input').addEventListener('input', event => {
        const c = catalogo.clientesFijos.find(x => x.nombreApellidos === event.target.value);
        if (c) { ci.querySelector('input').value = c.ci; tel.querySelector('input').value = c.telefono; fijo.querySelector('input').checked = true; }
      });
      body.push(campo({label: 'Número de transacción', name: 'numero', maxlength: 120, required: true, autocomplete: 'off', value: prev.numero || ''}), lista, nombre,
        h('div', {class: 'form'}, h('div', {class: 'par'}, ci, tel)), fijo);
    } else if (!carrito.correccion) {
      const cambio = h('p', {class: 'dato'});
      body.push(campo({label: 'Efectivo recibido (opcional)', name: 'recibido', type: 'number', min: 0, step: '0.01', inputmode: 'decimal', oninput: event => {
        try { const d = minor(event.target.value) - totalCotizado; cambio.textContent = d >= 0 ? 'Cambio: ' + cup(d) : 'Faltan ' + cup(-d); } catch { cambio.textContent = ''; }
      }}), cambio);
    }
    dialog({title: carrito.correccion ? 'Guardar corrección' : 'Cobrar', iconName: 'Cobrar', body, actions: [{label: 'Cancelar'},
      {label: 'Confirmar', primary: true, icon: 'Confirmar', onClick: async ({data, form}) => {
        const payload = {lines: carrito.lineas.map(l => ({item_id: l.item_id, quantity: l.cantidad})), method: carrito.metodo};
        if (transferencia) payload.transaction = {numero: data.numero, clienteNombre: data.clienteNombre || '', clienteCi: data.clienteCi || '', clienteTelefono: data.clienteTelefono || '', clienteFijo: form.elements.clienteFijo.checked};
        if (carrito.correccion) { payload.id = carrito.correccion.id; payload.reason = data.reason; }
        await api(carrito.correccion ? '/api/correct' : '/api/cart', payload);
        snack(carrito.correccion ? 'Venta corregida.' : 'Venta registrada: ' + cup(totalCotizado));
        SPVI.state.carrito = {lineas: [], metodo: 'efectivo', correccion: null};
        await SPVI.changed();
      }}]});
  }

  const tarjetaCarrito = h('div', {class: 'card carrito pegajoso'});
  const cabeceraCarrito = h('div', {class: 'cabecera'}, icon('AgregarCarrito'), h('h3', {text: 'Carrito'}),
    carrito.correccion ? button('Cancelar corrección', () => { SPVI.state.carrito = {lineas: [], metodo: 'efectivo', correccion: null}; SPVI.render(); }, {kind: 'texto'})
      : iconButton('BorrarTodo', 'Vaciar carrito', () => { carrito.lineas = []; pintarCarrito(); }));
  root.append(h('div', {class: 'dos'},
    h('div', {class: 'vista'}, h('div', {class: 'fila'}, buscador('Buscar artículo', event => { texto = event.target.value; pintarCatalogo(); })), filtros, rejilla),
    tarjetaCarrito), irCarrito);
  tarjetaCarrito.append(cabeceraCarrito, lineasEl, h('span', {class: 'secundario', text: 'Forma de pago'}), metodos,
    h('div', null, h('span', {class: 'secundario', text: 'Total'}), totalEl, notaTotal), cobrar);
  pintarCatalogo(); pintarCarrito();
  return () => clearTimeout(temporizador);
};
