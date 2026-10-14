'use strict';
/* Registros: historial paginado de ventas, servicios, transferencias, inventario, caja, turnos y clientes fijos. */

const FORMATOS = {pdf: ['PDF', 'Pdf'], xlsx: ['Excel', 'Excel'], imagen: ['Imagen', 'Imagen'], tarjetas: ['Tarjetas promocionales', 'Promocion']};
const ALCANCES = {inventario: ['pdf', 'xlsx'], turnos: ['pdf', 'xlsx'], ventas: ['pdf', 'xlsx'], servicios: ['pdf', 'xlsx', 'imagen', 'tarjetas'], productos: ['imagen', 'tarjetas']};

/** Exportar a PDF, Excel, imagen o tarjetas promocionales. Con ``ids`` exporta solo lo seleccionado. */
SPVI.exportar = function (alcance = 'inventario', query = '', ids = null) {
  const formatos = h('div', {class: 'chips', role: 'radiogroup', 'aria-label': 'Formato'});
  const comentario = campo({label: 'Texto de la promoción (opcional)', name: 'comment', maxlength: 160});
  let formato = null, select = null;
  const alcanceActual = () => ids ? alcance : select.value;
  const pintarFormatos = () => {
    const lista = ALCANCES[alcanceActual()] || ['pdf']; if (!lista.includes(formato)) formato = lista[0];
    formatos.replaceChildren(...lista.map(f => h('button', {type: 'button', class: 'chip', 'aria-pressed': String(f === formato), onclick: () => { formato = f; pintarFormatos(); }}, icon(FORMATOS[f][1], 'sm'), ' ', FORMATOS[f][0])));
    comentario.hidden = formato !== 'tarjetas';
  };
  const bloque = ids
    ? h('div', {class: 'campo'}, h('span', {text: 'Qué exportar'}),
      h('p', {text: ids.length + (ids.length === 1 ? ' elemento seleccionado en Registros' : ' elementos seleccionados en Registros')}))
    : seleccion({label: 'Qué exportar', name: 'scope', value: alcance, options: [['inventario', 'Inventario'], ['servicios', 'Servicios'], ['productos', 'Productos (catálogo con fotos)'], ['turnos', 'Turnos'], ['ventas', 'Ventas']]});
  if (!ids) { select = bloque.querySelector('select'); select.addEventListener('change', pintarFormatos); }
  pintarFormatos();
  dialog({title: 'Exportar', iconName: 'Exportar', body: [bloque, h('span', {class: 'secundario', text: 'Formato'}), formatos, comentario], actions: [{label: 'Cancelar'},
    {label: 'Descargar', primary: true, icon: 'Exportar', onClick: async ({data}) => {
      await download('/api/export', {scope: alcanceActual(), format: formato, comment: data.comment || '', query, ids}); snack('Archivo preparado.');
    }}]});
};

let empleadosCache = null;
async function nombresEmpleados() {
  if (!empleadosCache) { try { empleadosCache = Object.fromEntries((await api('/api/employees')).map(e => [e.id, e.name])); } catch { empleadosCache = {}; } }
  return empleadosCache;
}
const totalVenta = v => v.detalles.reduce((a, d) => a + d.precioUnitarioCent * d.cantidad, 0);

SPVI.views.registros = async function (root, sub, params) {
  const catalogo = await SPVI.load.catalogo();
  empleadosCache = null;
  const empleados = await nombresEmpleados();
  const tipos = [['ventas', 'Ventas'], ['servicios', 'Servicios'], ['transferencias', 'Transferencias'], ['movimientos', 'Inventario'], ['caja', 'Caja'], ['turnos', 'Turnos'], ['clientes', 'Clientes fijos']];
  const tipo = tipos.some(([id]) => id === (sub || params.get('tipo'))) ? (sub || params.get('tipo')) : 'ventas';
  const estado = SPVI.state.registros = SPVI.state.registros?.tipo === tipo ? SPVI.state.registros : {tipo, q: '', periodo: 'todo', page: 1};
  estado.marcados = estado.marcados || new Set();
  // Solo ventas, servicios, transferencias y turnos tienen exportación por selección.
  const alcanceSel = {ventas: 'ventas', servicios: 'ventas', transferencias: 'ventas', turnos: 'turnos'}[tipo] || null;
  root.append(h('div', {class: 'encabezado'}, h('h1', {text: 'Registros'})), pestanas(tipos, tipo, id => SPVI.go('registros/' + id)));
  SPVI.actions(iconButton('Exportar', 'Exportar', () => SPVI.exportar(alcanceSel || 'inventario')));

  const resumen = h('p', {class: 'secundario'}), lista = h('div', {class: 'lista'}), paginacion = h('div', {class: 'paginacion'});
  const barra = h('div', {class: 'barra-sel', hidden: true, role: 'status', 'aria-live': 'polite'});
  const periodos = chipsPeriodo(estado.periodo, id => { estado.periodo = id; estado.page = 1; cargar(); });
  let temporizador = null, generacion = 0;
  async function cargar() {
    const g = ++generacion;
    const args = new URLSearchParams({q: estado.q, page: estado.page, size: 50});
    const desde = SPVI.desdePeriodo(estado.periodo, catalogo.turnoAbierto?.abiertoEn);
    if (desde !== null) args.set('desde', desde);
    let datos;
    try { datos = await api('/api/registros/' + tipo + '?' + args); } catch (error) { snack(error.message, true); return; }
    if (g !== generacion) return;
    const partes = [datos.count + (datos.count === 1 ? ' registro' : ' registros')];
    if (datos.total !== undefined) partes.push('Total válido: ' + cup(datos.total));
    if (datos.entradas !== undefined) partes.push('Entradas ' + cup(datos.entradas) + ' · Salidas ' + cup(datos.salidas));
    resumen.textContent = partes.join(' · ');
    lista.replaceChildren(...(datos.items.length ? datos.items.map(fila) : [vacio('Registros', estado.q || estado.periodo !== 'todo' ? 'No hay registros con estos filtros.' : 'Aún no hay registros.')]));
    paginacion.replaceChildren();
    if (datos.pages > 1) paginacion.append(
      iconButton('AtrasPagina', 'Página anterior', () => { estado.page--; cargar(); }), h('span', {text: 'Página ' + datos.page + ' de ' + datos.pages}),
      iconButton('Siguiente', 'Página siguiente', () => { estado.page++; cargar(); }));
    if (paginacion.children.length) { paginacion.children[0].disabled = datos.page <= 1; paginacion.children[2].disabled = datos.page >= datos.pages; }
    actualizarBarra();
  }
  const quien = id => id ? (empleados[id] || 'App secundaria') : null;
  /** Casilla de selección (botón con estado, para no anidar un campo dentro de la fila clicable). */
  function casilla(id) {
    const marcado = estado.marcados.has(id);
    const boton = h('button', {type: 'button', class: 'casilla', 'aria-pressed': String(marcado),
      'aria-label': marcado ? 'Quitar de la selección' : 'Seleccionar', title: marcado ? 'Quitar de la selección' : 'Seleccionar',
      onclick: event => {
        event.stopPropagation();
        if (estado.marcados.has(id)) estado.marcados.delete(id); else estado.marcados.add(id);
        const ahora = estado.marcados.has(id);
        const etiqueta = ahora ? 'Quitar de la selección' : 'Seleccionar';
        boton.setAttribute('aria-pressed', String(ahora));
        boton.setAttribute('aria-label', etiqueta); boton.title = etiqueta;
        actualizarBarra();
      }}, h('span', {class: 'caja'}));
    return boton;
  }
  const marcar = (id, icono) => h('span', {class: 'lead-casilla'}, casilla(id), leadIcon(icono));
  /** Barra que aparece al marcar elementos: marcar todo lo filtrado, quitar y exportar la selección. */
  function actualizarBarra() {
    const n = estado.marcados.size;
    barra.hidden = n === 0;
    if (!n) { barra.replaceChildren(); return; }
    barra.replaceChildren(...[h('strong', {text: n + (n === 1 ? ' elemento seleccionado' : ' elementos seleccionados')}),
      n > 1000 ? h('span', {class: 'secundario', text: 'Marca como máximo 1000 para exportar.'}) : null,
      button('Marcar todo lo filtrado', marcarTodo, {kind: 'texto', iconName: 'Lista'}),
      button('Quitar selección', () => { estado.marcados.clear(); cargar(); }, {kind: 'texto', iconName: 'Limpiar'}),
      alcanceSel ? button('Exportar selección', () => SPVI.exportar(alcanceSel, estado.q, [...estado.marcados]), {kind: 'tonal', iconName: 'Exportar', disabled: n > 1000}) : null].filter(Boolean));
  }
  /** Marca **todo el filtro**, no solo la página visible: el backend devuelve los identificadores. */
  async function marcarTodo() {
    const args = new URLSearchParams({q: estado.q, page: 1, size: 50, ids: '1'});
    const desde = SPVI.desdePeriodo(estado.periodo, catalogo.turnoAbierto?.abiertoEn);
    if (desde !== null) args.set('desde', desde);
    try {
      const datos = await api('/api/registros/' + tipo + '?' + args);
      for (const id of datos.ids || []) estado.marcados.add(id);
      snack('Se marcaron ' + estado.marcados.size + ' elementos del filtro.'); cargar();
    } catch (error) { snack(error.message, true); }
  }
  function fila(x) {
    if (['ventas', 'servicios', 'transferencias'].includes(tipo)) {
      const titulo = x.detalles.map(d => d.nombre + (d.cantidad > 1 ? ' ×' + d.cantidad : '')).join(', ');
      const subt = [fechaHora(x.fecha), x.metodoPago === 'TRANSFERENCIA' ? 'Transferencia' + (x.transaccion?.numero ? ' ' + x.transaccion.numero : '') : 'Efectivo', quien(x.empleadoId), x.anuladaEn ? 'Anulada' : null].filter(Boolean).join(' · ');
      return itemLista({title: titulo, subtitle: subt, value: cup(totalVenta(x)), cls: x.anuladaEn ? 'anulada' : '', lead: marcar(x.id, x.metodoPago === 'TRANSFERENCIA' ? 'Transferencia' : 'Efectivo'), onclick: () => detalleVenta(x)});
    }
    if (tipo === 'movimientos') {
      const u = x.entidad === 'INSUMO' ? 1000 : 1;
      const nombres = {ALTA: 'Alta', AJUSTE: 'Ajuste', VENTA: 'Venta', PRODUCCION: 'Producción', CONSUMO: 'Consumo', BAJA: 'Baja', ANULACION: 'Anulación'};
      return itemLista({title: x.nombre, subtitle: [fechaHora(x.fecha) + ' · ' + (nombres[x.tipo] || x.tipo), x.nota].filter(Boolean),
        value: (x.delta > 0 ? '+' : '') + cantidad(x.delta / u) + ' → ' + cantidad(x.existencia / u), lead: marcar(x.id, 'Inventario')});
    }
    if (tipo === 'caja') return itemLista({title: x.motivo, subtitle: fechaHora(x.fecha) + (x.hechoPor ? ' · ' + x.hechoPor : ''), lead: marcar(x.id, 'Efectivo'),
      value: (x.tipo === 'ENTRADA' ? '+' : '−') + cup(x.importeCent)});
    if (tipo === 'turnos') {
      const r = x.resumen || {};
      return itemLista({title: fechaHora(x.abiertoEn) + (quien(x.empleadoId) ? ' · ' + quien(x.empleadoId) : ''), lead: marcar(x.id, 'Turno'),
        subtitle: x.cerradoEn ? 'Cerrado ' + fechaHora(x.cerradoEn) : 'Abierto', value: r.totalCent !== undefined ? cup(r.totalCent) : null, onclick: () => detalleTurno(x)});
    }
    return itemLista({title: x.nombreApellidos, subtitle: 'CI ' + x.ci + ' · ' + x.telefono, lead: marcar(x.id, 'Perfil'), onclick: () => editarCliente(x)});
  }

  function detalleVenta(v) {
    const abierto = catalogo.turnoAbierto && v.turnoId === catalogo.turnoAbierto.id && !v.anuladaEn;
    const tr = v.transaccion;
    const body = [
      h('div', {class: 'lista plana'}, v.detalles.map(d => itemLista({title: d.nombre, subtitle: d.cantidad + ' × ' + cup(d.precioUnitarioCent), value: cup(d.cantidad * d.precioUnitarioCent)}))),
      h('div', {class: 'lista plana'}, itemLista({title: 'Total', value: cup(totalVenta(v))})),
      h('p', {class: 'secundario', text: [fechaHora(v.fecha), v.metodoPago === 'TRANSFERENCIA' ? 'Transferencia' : 'Efectivo', quien(v.empleadoId)].filter(Boolean).join(' · ')}),
    ];
    if (tr) body.push(h('div', {class: 'card tonal'}, h('strong', {text: 'Transferencia'}),
      h('span', {text: 'Número: ' + tr.numero}), tr.clienteNombre ? h('span', {text: 'Cliente: ' + tr.clienteNombre}) : null,
      tr.clienteCi ? h('span', {text: 'CI: ' + tr.clienteCi}) : null, tr.clienteTelefono ? h('span', {text: 'Teléfono: ' + tr.clienteTelefono}) : null));
    if (v.anuladaEn) body.push(h('div', {class: 'banner mal'}, icon('Cancelar'), h('span', {class: 'texto', text: 'Anulada el ' + fechaHora(v.anuladaEn) + (v.motivoAnulacion ? ': ' + v.motivoAnulacion : '')})));
    else if (!abierto) body.push(h('p', {class: 'secundario', text: 'Solo se pueden anular o corregir ventas del turno abierto.'}));
    const acciones = abierto ? [
      {label: 'Anular', icon: 'Cancelar', onClick: async ({close}) => { close(); anular(v); return false; }},
      {label: 'Corregir', icon: 'Editar', onClick: async () => { await SPVI.corregir(v); }},
    ] : [];
    dialog({title: 'Venta', iconName: 'Comprobante', body, actions: [...acciones, {label: 'Cerrar', primary: !abierto}]});
  }
  function anular(v) {
    dialog({title: 'Anular venta', iconName: 'Cancelar', body: [h('p', {text: 'Las existencias vuelven al inventario y la venta queda marcada como anulada.'}),
      campo({label: 'Motivo', name: 'reason', maxlength: 120, required: true})],
    actions: [{label: 'Cancelar'}, {label: 'Anular venta', primary: true, kind: 'peligro', icon: 'Confirmar', onClick: async ({data}) => {
      await api('/api/void', {id: v.id, reason: data.reason}); snack('Venta anulada.'); await SPVI.changed();
    }}]});
  }
  function detalleTurno(t) {
    const r = t.resumen || {};
    const filas = [['Fondo inicial', cup(t.fondoCent || 0)], ['Ventas', r.totalCent !== undefined ? cup(r.totalCent) : '—'], ['Efectivo', r.efectivoCent !== undefined ? cup(r.efectivoCent) + ' (' + r.ventasEfectivo + ')' : '—'],
      ['Transferencias', r.transferenciaCent !== undefined ? cup(r.transferenciaCent) + ' (' + r.ventasTransferencia + ')' : '—'], ['Ganancia', r.totalCent !== undefined ? cup(r.totalCent - r.costoCent) : '—'],
      ['Entradas de caja', r.entradasCent !== undefined ? cup(r.entradasCent) : '—'], ['Salidas de caja', r.salidasCent !== undefined ? cup(r.salidasCent) : '—'],
      ['Ventas anuladas', r.numAnuladas ?? '—'], ['Efectivo contado', t.contadoCent !== null && t.contadoCent !== undefined ? cup(t.contadoCent) : 'Sin arqueo']];
    dialog({title: 'Turno', iconName: 'Turno', body: [h('p', {class: 'secundario', text: 'Abierto ' + fechaHora(t.abiertoEn) + (t.cerradoEn ? ' · Cerrado ' + fechaHora(t.cerradoEn) : ' · Abierto')}),
      h('div', {class: 'lista plana'}, filas.map(([a, b]) => itemLista({title: a, value: String(b)})))], actions: [{label: 'Cerrar', primary: true}]});
  }
  function editarCliente(c = null) {
    dialog({title: c ? 'Cliente fijo' : 'Nuevo cliente fijo', iconName: 'ClientesFijos', body: [
      campo({label: 'Nombre y apellidos', name: 'name', maxlength: 120, required: true, value: c?.nombreApellidos || ''}),
      campo({label: 'Carné de identidad', name: 'ci', maxlength: 11, required: true, inputmode: 'numeric', value: c?.ci || '', readOnly: !!c}),
      campo({label: 'Teléfono', name: 'phone', maxlength: 20, required: true, inputmode: 'tel', value: c?.telefono || ''}),
    ], actions: [
      ...(c ? [{label: 'Eliminar', icon: 'Eliminar', onClick: async ({close}) => {
        close();
        if (await confirmar({title: 'Eliminar cliente fijo', text: '¿Eliminar a ' + c.nombreApellidos + '? Sus ventas no se borran.', confirm: 'Eliminar', danger: true})) {
          await busy(null, async () => { await api('/api/client', {ci: c.ci, delete: true}); snack('Cliente eliminado.'); await SPVI.changed(); });
        }
        return false;
      }}] : []),
      {label: 'Cancelar'},
      {label: 'Guardar', primary: true, icon: 'Guardar', onClick: async ({data}) => { await api('/api/client', {ci: data.ci, name: data.name, phone: data.phone}); snack('Cliente guardado.'); await SPVI.changed(); }},
    ]});
  }

  root.append(h('div', {class: 'fila'}, buscador('Buscar', event => {
    estado.q = event.target.value; estado.page = 1; clearTimeout(temporizador); temporizador = setTimeout(cargar, 300);
  }, estado.q), periodos), resumen, barra, lista, paginacion);
  if (tipo === 'clientes') root.append(h('button', {type: 'button', class: 'fab extendida', onclick: () => editarCliente()}, icon('Agregar'), 'Nuevo cliente'));
  await cargar();
  return () => clearTimeout(temporizador);
};
