'use strict';
/* Tiempo en línea: cuánto lleva abierto SPVI en esta PC, la red local y cada app vinculada (conexión y última sincronización). */

SPVI.views.tiempo = async function (root) {
  let datos = await api('/api/uptime'), desfase = datos.ahora - Date.now();
  root.append(h('div', {class: 'encabezado'}, h('h1', {text: 'Tiempo en línea'})));
  const relojApp = h('div', {class: 'reloj'}), relojRed = h('div', {class: 'reloj'}), detalleApp = h('p', {class: 'secundario'}), detalleRed = h('p', {class: 'secundario'});
  const estadoRed = h('span', {class: 'etiqueta'});
  const lista = h('div', {class: 'lista'});
  root.append(h('div', {class: 'grid'},
    h('div', {class: 'card'}, h('div', {class: 'cabecera'}, icon('Cronometro'), h('h3', {text: 'SPVI en esta PC'}), h('span', {class: 'punto on', title: 'En línea'})), relojApp, detalleApp),
    h('div', {class: 'card'}, h('div', {class: 'cabecera'}, icon('RedLocal'), h('h3', {text: 'Red local'}), estadoRed), relojRed, detalleRed)),
    h('h3', {text: 'Apps vinculadas'}), lista);

  // «total» del servidor ya incluye la conexión en curso hasta «ahora» de la respuesta: se descuenta y se recalcula.
  const conectada = (a, ahora) => (a.total || 0) - (a.current || 0) + (a.connected && a.since ? ahora - a.since : 0);
  const pintarApps = () => {
    lista.replaceChildren(...(datos.apps.length ? datos.apps.map(a => {
      const ahora = Date.now() + desfase;
      const sincronizada = Math.max(a.last_sync || 0, a.ultimaSincronizacion || 0) || null;
      return itemLista({title: a.nombre, lead: h('span', {class: 'lead'}, h('span', {class: 'punto' + (a.connected ? ' on' : '')})),
        subtitle: [a.connected ? 'Conectada desde ' + hora(a.since) : (a.last_seen ? 'Vista por última vez: ' + hace(a.last_seen) : 'Sin conexión en esta sesión'),
          'Última sincronización: ' + hace(sincronizada) + (a.activa ? '' : ' · Desvinculada')],
        value: duracion(conectada(a, ahora)), trailing: h('span', {class: 'sr-only', text: 'tiempo conectada en esta sesión'})});
    }) : [vacio('Vinculacion', 'No hay apps vinculadas.')]));
  };
  const tic = () => {
    const ahora = Date.now() + desfase;
    relojApp.textContent = duracion(ahora - datos.app.since);
    detalleApp.textContent = 'Abierto desde ' + fechaHora(datos.app.since);
    if (datos.lan.running && datos.lan.since) {
      relojRed.textContent = duracion(ahora - datos.lan.since); estadoRed.textContent = 'Activa'; estadoRed.className = 'etiqueta ok';
      detalleRed.textContent = 'Activa desde ' + fechaHora(datos.lan.since) + (datos.lan.address ? ' en ' + datos.lan.address.join(':') : '');
    } else {
      relojRed.textContent = '--:--:--'; estadoRed.textContent = 'Detenida'; estadoRed.className = 'etiqueta';
      detalleRed.textContent = 'Inicia la red local en Apps vinculadas para que las secundarias sincronicen.';
    }
    for (const [i, a] of datos.apps.entries()) {
      const valor = lista.children[i]?.querySelector('.valor');
      if (valor) valor.textContent = duracion(conectada(a, ahora));
    }
  };
  pintarApps(); tic();
  const t1 = setInterval(tic, 1000);
  const t2 = setInterval(async () => {
    try { datos = await api('/api/uptime'); desfase = datos.ahora - Date.now(); pintarApps(); tic(); } catch { /* se reintenta en el siguiente ciclo */ }
  }, 5000);
  root.append(h('p', {class: 'secundario', text: 'Los tiempos de conexión cuentan desde que se abrió SPVI en esta PC.'}));
  return () => { clearInterval(t1); clearInterval(t2); };
};
