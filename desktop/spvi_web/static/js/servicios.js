'use strict';
/* Servicios: catálogo de servicios con importe, tipo, insumos que consumen y foto. */

SPVI.views.servicios = async function (root) {
  const catalogo = await SPVI.load.catalogo();
  let texto = '';
  root.append(h('div', {class: 'encabezado'}, h('h1', {text: 'Servicios'})));
  SPVI.actions(iconButton('Exportar', 'Exportar servicios', () => SPVI.exportar('servicios')));
  const tipos = [...new Set(catalogo.servicios.map(s => s.tipo).filter(Boolean))].sort();
  const lista = h('div', {class: 'lista'});
  const pintar = () => {
    const q = texto.trim().toLocaleLowerCase('es');
    const items = catalogo.servicios.filter(s => !s.eliminado && (!q || (s.nombre + ' ' + s.tipo).toLocaleLowerCase('es').includes(q))).sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
    lista.replaceChildren(...(items.length ? items.map(s => itemLista({title: s.nombre,
      subtitle: [s.tipo, s.insumos?.length ? 'Consume ' + s.insumos.length + (s.insumos.length === 1 ? ' insumo' : ' insumos') : null],
      value: cup(s.importeCent), onclick: () => editar(s),
      lead: h('img', {class: 'foto', alt: '', loading: 'lazy', src: '/api/photo/servicios/' + s.id, onerror: e => e.target.replaceWith(leadIcon('Servicios'))})}))
      : [vacio('Servicios', q ? 'Ningún servicio coincide.' : 'Aún no hay servicios. Usa «Nuevo servicio».')]));
  };
  function editar(x = null) {
    const editor = SPVI.lineasInsumos(catalogo, x?.insumos || []);
    const body = [
      campo({label: 'Nombre', name: 'nombre', required: true, maxlength: 120, value: x?.nombre || ''}),
      h('datalist', {id: 'tipos-servicio'}, tipos.map(t => h('option', {value: t}))),
      h('div', {class: 'form'}, h('div', {class: 'par'},
        campo({label: 'Tipo de servicio', name: 'tipo', required: true, maxlength: 120, list: 'tipos-servicio', value: x?.tipo || ''}),
        campo({label: 'Importe (CUP)', name: 'importe', type: 'number', min: 0, step: '0.01', required: true, inputmode: 'decimal', value: x ? pesos(x.importeCent) : ''}))),
      campo({label: 'Descripción (opcional)', name: 'descripcion', rows: 3, maxlength: 4096, value: x?.descripcion || ''}),
      h('h3', {text: 'Insumos que consume (opcional)'}), editor.element,
    ];
    if (x) body.push(h('h3', {text: 'Foto'}), SPVI.bloqueFoto('servicios', x.id));
    const acciones = x ? [{label: 'Eliminar', icon: 'Eliminar', onClick: async ({close}) => {
      close();
      if (await confirmar({title: 'Archivar servicio', text: '¿Archivar «' + x.nombre + '»? Se conserva en ventas anteriores y puedes restaurarlo en Inventario > Archivados.', confirm: 'Archivar', danger: true})) {
        await busy(null, async () => { await api('/api/catalog', {t: 'servicio_eliminar', id: x.id}); snack('Servicio archivado.'); await SPVI.changed(); });
      }
      return false;
    }}] : [];
    dialog({title: x ? 'Editar servicio' : 'Nuevo servicio', iconName: 'Servicios', wide: true, body, actions: [...acciones, {label: 'Cancelar'},
      {label: 'Guardar', primary: true, icon: 'Guardar', onClick: async ({data}) => {
        const ahora = Date.now();
        await api('/api/catalog', {t: 'servicio_guardar', servicio: {...(x || {}), id: x?.id || 0, nombre: data.nombre.trim(), tipo: data.tipo.trim(), importeCent: minor(data.importe),
          descripcion: data.descripcion.trim() || null, insumos: editor.leer(), creadoEn: x?.creadoEn || ahora, actualizadoEn: ahora}});
        snack('Servicio guardado.'); await SPVI.changed();
      }}]});
  }
  root.append(h('div', {class: 'fila'}, buscador('Buscar servicio', event => { texto = event.target.value; pintar(); })), lista,
    h('button', {type: 'button', class: 'fab extendida', onclick: () => editar()}, icon('Agregar'), 'Nuevo servicio'));
  pintar();
};
