'use strict';
/* Inventario: productos, elaborados (con receta) e insumos; existencias, umbrales, fotos y archivados. */

/** Editor de líneas de insumos (recetas de elaborados y consumos de servicios). */
SPVI.lineasInsumos = function (catalogo, lineas = []) {
  const insumos = catalogo.insumos.filter(x => !x.eliminado);
  const contenedor = h('div', {class: 'form'});
  const costo = h('p', {class: 'secundario'});
  const fila = (linea = null) => {
    const select = h('select', {'aria-label': 'Insumo'}, insumos.map(x => h('option', {value: x.id, text: x.nombre + ' (' + UNIDAD_CORTA[x.unidad] + ')'})));
    if (linea) select.value = linea.insumoId;
    const qty = h('input', {type: 'number', min: '0.001', step: '0.001', required: true, 'aria-label': 'Cantidad por unidad', value: linea ? linea.cantidadMil / 1000 : 1, inputmode: 'decimal'});
    const row = h('div', {class: 'fila linea-insumo'}, h('label', {class: 'campo', style: 'flex:2'}, h('span', {text: 'Insumo'}), select),
      h('label', {class: 'campo', style: 'flex:1'}, h('span', {text: 'Cantidad'}), qty), iconButton('Eliminar', 'Quitar insumo', () => { row.remove(); recalcular(); }, 'peligro'));
    select.addEventListener('change', recalcular); qty.addEventListener('input', recalcular);
    contenedor.insertBefore(row, agregar); recalcular();
  };
  const leer = () => [...contenedor.querySelectorAll('.linea-insumo')].map(row => ({insumoId: Number(row.querySelector('select').value), cantidadMil: minor(row.querySelector('input').value, 3)}));
  function recalcular() {
    try { costo.textContent = 'Costo de insumos: ' + cup(Math.round(leer().reduce((a, l) => a + (insumos.find(x => x.id === l.insumoId)?.precioCent || 0) * l.cantidadMil / 1000, 0))); }
    catch { costo.textContent = ''; }
  }
  const agregar = button('Agregar insumo', () => fila(), {kind: 'texto', iconName: 'Agregar', disabled: !insumos.length});
  contenedor.append(agregar);
  lineas.forEach(fila);
  if (!insumos.length) contenedor.prepend(h('p', {class: 'secundario', text: 'Primero registra insumos en Inventario > Insumos.'}));
  return {element: h('div', {class: 'form'}, contenedor, costo), leer, costo: () => { try { return Math.round(leer().reduce((a, l) => a + (insumos.find(x => x.id === l.insumoId)?.precioCent || 0) * l.cantidadMil / 1000, 0)); } catch { return 0; } }};
};

/** Bloque de foto dentro de un diálogo (solo artículos ya guardados). */
SPVI.bloqueFoto = function (kind, id) {
  const img = h('img', {class: 'foto-grande', alt: 'Foto del artículo', hidden: true});
  img.onload = () => { img.hidden = false; quitar.hidden = false; }; img.onerror = () => { img.hidden = true; quitar.hidden = true; };
  img.src = '/api/photo/' + kind + '/' + id + '?v=' + Date.now();
  const archivo = h('input', {type: 'file', accept: 'image/jpeg,image/png,image/webp', hidden: true, onchange: async () => {
    const file = archivo.files[0]; if (!file) return;
    await busy(null, async () => { await api('/api/photo', {kind, id, content: await leerFoto(file)}); img.src = '/api/photo/' + kind + '/' + id + '?v=' + Date.now(); snack('Foto guardada.'); });
    archivo.value = '';
  }});
  const quitar = button('Quitar foto', () => busy(null, async () => { await api('/api/photo', {kind, id, content: null}); img.hidden = true; quitar.hidden = true; snack('Foto eliminada.'); }), {kind: 'texto', iconName: 'Eliminar'});
  quitar.hidden = true;
  return h('div', {class: 'form'}, img, h('div', {class: 'fila'}, button('Elegir foto', () => archivo.click(), {kind: 'tonal', iconName: 'Imagen'}), quitar), archivo);
};

/** Costo y precio de venta a la derecha de la fila, diferenciados (el costo en segundo plano). */
function precios(costoCent, ventaCent) {
  return h('span', {class: 'valores'}, h('span', {class: 'secundario', text: 'Costo ' + cup(costoCent)}),
    ventaCent === null || ventaCent === undefined ? null : h('span', {class: 'dato', text: cup(ventaCent)}));
}

SPVI.views.inventario = async function (root, sub, params) {
  const catalogo = await SPVI.load.catalogo();
  const prefs = catalogo.preferencias || {};
  const tab = ['productos', 'elaborados', 'insumos', 'archivados'].includes(sub) ? sub : 'productos';
  const filtroAviso = params.get('filtro');
  let texto = '';
  root.append(h('div', {class: 'encabezado'}, h('h1', {text: 'Inventario'})),
    pestanas([['productos', 'Productos'], ['elaborados', 'Elaborados'], ['insumos', 'Insumos'], ['archivados', 'Archivados']], tab, id => SPVI.go('inventario/' + id)));
  SPVI.actions(iconButton('Exportar', 'Exportar inventario', () => SPVI.exportar('inventario')));
  if (filtroAviso) root.append(h('div', {class: 'banner'}, icon('Filtrar'),
    h('span', {class: 'texto', text: {critico: 'Mostrando existencias críticas', bajo: 'Mostrando existencias bajas', caduca: 'Mostrando productos que caducan en 7 días'}[filtroAviso] || 'Filtro'}),
    button('Quitar filtro', () => SPVI.go('inventario/' + tab), {kind: 'texto'})));

  const lista = h('div', {class: 'lista'});
  const elaborado = p => (p.categoria || '').toLowerCase() === 'elaborado';
  const fuente = () => {
    if (tab === 'insumos') return catalogo.insumos.filter(x => !x.eliminado);
    if (tab === 'archivados') return [...catalogo.productos, ...catalogo.servicios].filter(x => x.eliminado);
    return catalogo.productos.filter(p => !p.eliminado && (tab === 'elaborados') === elaborado(p));
  };
  const pintar = () => {
    const q = texto.trim().toLocaleLowerCase('es');
    let items = fuente().filter(x => !q || (x.nombre + ' ' + (x.categoria || x.tipo || '')).toLocaleLowerCase('es').includes(q));
    if (filtroAviso === 'caduca') items = items.filter(x => SPVI.caduca(x));
    else if (filtroAviso) items = items.filter(x => SPVI.nivel(x, tab === 'insumos' ? 'insumos' : 'productos', prefs) === filtroAviso);
    items.sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
    lista.replaceChildren(...(items.length ? items.map(x => fila(x)) : [vacio(tab === 'archivados' ? 'Restaurar' : 'Inventario',
      q || filtroAviso ? 'Ningún artículo coincide.' : {productos: 'Aún no hay productos. Usa «Nuevo producto».', elaborados: 'Los elaborados se preparan con insumos según su receta.', insumos: 'Aún no hay insumos.', archivados: 'No hay artículos archivados.'}[tab])]));
  };
  const fila = x => {
    if (tab === 'archivados') {
      const servicio = 'importeCent' in x;
      return itemLista({title: x.nombre, subtitle: servicio ? 'Servicio' : 'Producto', lead: leadIcon(servicio ? 'Servicios' : 'Inventario'),
        trailing: button('Restaurar', () => busy(null, async () => {
          const singular = servicio ? 'servicio' : 'producto';
          await api('/api/catalog', {t: singular + '_guardar', [singular]: {...x, eliminado: false}}); snack('Artículo restaurado.'); await SPVI.changed();
        }), {kind: 'texto', iconName: 'Restaurar'})});
    }
    if (tab === 'insumos') {
      const nivel = SPVI.nivel(x, 'insumos', prefs);
      return itemLista({title: x.nombre, indicator: SPVI.colorNivel(nivel), lead: leadIcon('Inventario'),
        subtitle: 'Existencias: ' + cantidad(x.cantidadMil / 1000) + ' ' + UNIDAD_CORTA[x.unidad] + (nivel !== 'ok' ? (nivel === 'critico' ? ' · Crítico' : ' · Bajo') : ''),
        trailing: precios(x.precioCent, x.precioVentaCent), onclick: () => editarInsumo(x)});
    }
    const nivel = SPVI.nivel(x, 'productos', prefs);
    const foto = h('img', {class: 'foto', alt: '', loading: 'lazy', src: '/api/photo/productos/' + x.id, onerror: e => e.target.replaceWith(leadIcon('Inventario'))});
    // Elaborados: cuántos se pueden hacer con las existencias actuales de sus insumos.
    const posibles = p => {
      const receta = catalogo.recetas.find(r => r.productoId === p.id);
      if (!receta || !receta.lineas.length) return null;
      let n = Infinity;
      for (const linea of receta.lineas) {
        const insumo = catalogo.insumos.find(i => i.id === linea.insumoId);
        if (!insumo) return 0;
        n = Math.min(n, Math.floor(insumo.cantidadMil / linea.cantidadMil));
      }
      return n === Infinity ? null : n;
    };
    const sub = elaborado(x)
      ? ['Categoría ' + (x.categoria || 'Elaborado'), 'Se pueden hacer ' + cantidad(posibles(x) ?? 0) + ' con las existencias']
      : [(x.categoria || 'Sin categoría'), 'Existencias: ' + cantidad(x.cantidad) + (nivel !== 'ok' ? (nivel === 'critico' ? ' · Crítico' : ' · Bajo') : ''),
        x.fechaCaducidad ? 'Caduca el ' + x.fechaCaducidad.split('-').reverse().join('/') : null];
    return itemLista({title: x.nombre, subtitle: sub, indicator: SPVI.caduca(x) ? 'var(--alert-caducidad)' : SPVI.colorNivel(nivel), lead: foto,
      trailing: precios(x.precioCostoCent, x.precioVentaCent), onclick: () => elaborado(x) ? editarElaborado(x) : editarProducto(x)});
  };

  const categorias = [...new Set(catalogo.productos.filter(p => !elaborado(p)).map(p => p.categoria).filter(Boolean))].sort();
  const guardarProducto = async (anterior, campos, receta = null) => {
    const ahora = Date.now();
    const producto = {...(anterior || {}), ...campos, id: anterior?.id || 0, creadoEn: anterior?.creadoEn || ahora, actualizadoEn: ahora};
    const payload = {t: 'producto_guardar', producto, cantidadLeida: anterior ? anterior.cantidad : null};
    if (receta) payload.receta = {productoId: anterior?.id || 0, lineas: receta};
    await api('/api/catalog', payload);
  };
  const accionesComunes = (kind, x, singular) => x ? [
    {label: 'Eliminar', kind: 'texto', icon: 'Eliminar', onClick: async ({close}) => {
      close();
      if (!await confirmar({title: 'Eliminar', text: kind === 'insumos' ? '¿Eliminar «' + x.nombre + '»?' : '¿Archivar «' + x.nombre + '»? Se conserva en ventas anteriores y puedes restaurarlo en Archivados.', confirm: kind === 'insumos' ? 'Eliminar' : 'Archivar', danger: true})) return false;
      await busy(null, async () => { await api('/api/catalog', {t: singular + '_eliminar', id: x.id}); snack('Listo.'); await SPVI.changed(); });
      return false;
    }}] : [];

  function editarProducto(x = null) {
    const body = [
      campo({label: 'Nombre', name: 'nombre', required: true, maxlength: 120, value: x?.nombre || ''}),
      h('datalist', {id: 'categorias'}, categorias.map(c => h('option', {value: c}))),
      h('div', {class: 'form'}, h('div', {class: 'par'},
        campo({label: 'Categoría', name: 'categoria', required: true, maxlength: 120, list: 'categorias', value: x?.categoria || ''}),
        campo({label: 'Caducidad (opcional)', name: 'fechaCaducidad', type: 'date', value: x?.fechaCaducidad || ''}))),
      h('div', {class: 'form'}, h('div', {class: 'par'},
        campo({label: 'Costo (CUP)', name: 'costo', type: 'number', min: 0, step: '0.01', required: true, inputmode: 'decimal', value: x ? pesos(x.precioCostoCent) : ''}),
        campo({label: 'Precio de venta (CUP)', name: 'precio', type: 'number', min: 0, step: '0.01', required: true, inputmode: 'decimal', value: x ? pesos(x.precioVentaCent) : ''}))),
      h('div', {class: 'form'}, h('div', {class: 'par'},
        campo({label: 'Existencias', name: 'cantidad', type: 'number', min: 0, step: 1, required: true, value: x ? x.cantidad : 0}),
        campo({label: 'Aviso de existencias bajas', name: 'nivelBajo', type: 'number', min: 0, step: 1, value: x?.nivelBajo ?? '', placeholder: String(prefs.productoBajo ?? 5)}),
        campo({label: 'Aviso de existencias críticas', name: 'nivelCritico', type: 'number', min: 0, step: 1, value: x?.nivelCritico ?? '', placeholder: String(prefs.productoCritico ?? 1)}))),
      campo({label: 'Descripción (opcional)', name: 'descripcion', rows: 3, maxlength: 4096, value: x?.descripcion || ''}),
    ];
    if (x) body.push(h('h3', {text: 'Foto'}), SPVI.bloqueFoto('productos', x.id));
    dialog({title: x ? 'Editar producto' : 'Nuevo producto', iconName: 'Inventario', wide: true, body, actions: [...accionesComunes('productos', x, 'producto'), {label: 'Cancelar'},
      {label: 'Guardar', primary: true, icon: 'Guardar', onClick: async ({data}) => {
        const entero = v => { if (v === '') return null; const n = Number(v); if (!Number.isSafeInteger(n) || n < 0) throw new Error('Usa números enteros en existencias y avisos.'); return n; };
        await guardarProducto(x, {nombre: data.nombre.trim(), categoria: data.categoria.trim(), fechaCaducidad: data.fechaCaducidad || null, descripcion: data.descripcion.trim() || null,
          precioCostoCent: minor(data.costo), precioVentaCent: minor(data.precio), cantidad: entero(data.cantidad) ?? 0, nivelBajo: entero(data.nivelBajo), nivelCritico: entero(data.nivelCritico)});
        snack('Producto guardado.'); await SPVI.changed();
      }}]});
  }

  function editarElaborado(x = null) {
    const receta = catalogo.recetas.find(r => x && r.productoId === x.id)?.lineas || [];
    const editor = SPVI.lineasInsumos(catalogo, receta);
    const body = [
      campo({label: 'Nombre', name: 'nombre', required: true, maxlength: 120, value: x?.nombre || ''}),
      campo({label: 'Precio de venta (CUP)', name: 'precio', type: 'number', min: 0, step: '0.01', required: true, inputmode: 'decimal', value: x ? pesos(x.precioVentaCent) : ''}),
      h('h3', {text: 'Receta (por unidad)'}), editor.element,
      campo({label: 'Descripción (opcional)', name: 'descripcion', rows: 3, maxlength: 4096, value: x?.descripcion || ''}),
    ];
    if (x) body.push(h('h3', {text: 'Foto'}), SPVI.bloqueFoto('productos', x.id));
    dialog({title: x ? 'Editar elaborado' : 'Nuevo elaborado', iconName: 'Especialidad', wide: true, body, actions: [...accionesComunes('productos', x, 'producto'), {label: 'Cancelar'},
      {label: 'Guardar', primary: true, icon: 'Guardar', onClick: async ({data}) => {
        const lineas = editor.leer();
        if (!lineas.length) throw new Error('Agrega al menos un insumo a la receta.');
        await guardarProducto(x, {nombre: data.nombre.trim(), categoria: 'Elaborado', descripcion: data.descripcion.trim() || null, precioVentaCent: minor(data.precio),
          precioCostoCent: editor.costo(), cantidad: x ? x.cantidad : 0}, lineas);
        snack('Elaborado guardado.'); await SPVI.changed();
      }}]});
  }

  function editarInsumo(x = null) {
    const u = x ? UNIDAD_CORTA[x.unidad] : 'unidad';
    const body = [
      campo({label: 'Nombre', name: 'nombre', required: true, maxlength: 120, value: x?.nombre || ''}),
      seleccion({label: 'Unidad de medida', name: 'unidad', options: Object.entries(UNIDADES), value: x?.unidad || 'UNIDAD'}),
      h('div', {class: 'form'}, h('div', {class: 'par'},
        campo({label: 'Costo por ' + u + ' (CUP)', name: 'costo', type: 'number', min: 0, step: '0.01', required: true, inputmode: 'decimal', value: x ? pesos(x.precioCent) : ''}),
        campo({label: 'Precio de venta (opcional)', name: 'venta', type: 'number', min: 0, step: '0.01', inputmode: 'decimal', value: x ? pesos(x.precioVentaCent) : '', help: 'Déjalo vacío si no se vende suelto.'}))),
      h('div', {class: 'form'}, h('div', {class: 'par'},
        campo({label: 'Existencias', name: 'cantidad', type: 'number', min: 0, step: '0.001', required: true, inputmode: 'decimal', value: x ? x.cantidadMil / 1000 : 0}),
        campo({label: 'Aviso de existencias bajas', name: 'bajo', type: 'number', min: 0, step: '0.001', inputmode: 'decimal', value: x?.nivelBajoMil != null ? x.nivelBajoMil / 1000 : '', placeholder: String((prefs.insumoBajoMil ?? 5000) / 1000)}),
        campo({label: 'Aviso de existencias críticas', name: 'critico', type: 'number', min: 0, step: '0.001', inputmode: 'decimal', value: x?.nivelCriticoMil != null ? x.nivelCriticoMil / 1000 : '', placeholder: String((prefs.insumoCriticoMil ?? 1000) / 1000)}))),
    ];
    dialog({title: x ? 'Editar insumo' : 'Nuevo insumo', iconName: 'Inventario', wide: true, body, actions: [...accionesComunes('insumos', x, 'insumo'), {label: 'Cancelar'},
      {label: 'Guardar', primary: true, icon: 'Guardar', onClick: async ({data}) => {
        const ahora = Date.now(), opcional = (v, d) => v === '' ? null : minor(v, d);
        await api('/api/catalog', {t: 'insumo_guardar', cantidadLeidaMil: x ? x.cantidadMil : null, insumo: {...(x || {}), id: x?.id || 0, nombre: data.nombre.trim(), unidad: data.unidad,
          precioCent: minor(data.costo), precioVentaCent: opcional(data.venta, 2), cantidadMil: minor(data.cantidad, 3), nivelBajoMil: opcional(data.bajo, 3), nivelCriticoMil: opcional(data.critico, 3),
          creadoEn: x?.creadoEn || ahora, actualizadoEn: ahora}});
        snack('Insumo guardado.'); await SPVI.changed();
      }}]});
  }

  if (tab !== 'archivados') {
    const nuevo = {productos: ['Nuevo producto', () => editarProducto()], elaborados: ['Nuevo elaborado', () => editarElaborado()], insumos: ['Nuevo insumo', () => editarInsumo()]}[tab];
    root.append(h('button', {type: 'button', class: 'fab extendida', onclick: nuevo[1]}, icon('Agregar'), nuevo[0]));
  }
  root.append(h('div', {class: 'fila'}, buscador('Buscar en ' + {productos: 'productos', elaborados: 'elaborados', insumos: 'insumos', archivados: 'archivados'}[tab], event => { texto = event.target.value; pintar(); })), lista);
  pintar();
};
