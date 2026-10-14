'use strict';
/* Inicio: estado de licencia, turno, ventas del período y avisos de inventario (como en la app móvil). */

/** Nivel de existencias según los umbrales del artículo o los de Ajustes > Avisos. */
SPVI.nivel = function (item, kind, prefs = {}) {
  if (kind === 'insumos') {
    const low = item.nivelBajoMil ?? prefs.insumoBajoMil ?? 5000, critical = item.nivelCriticoMil ?? prefs.insumoCriticoMil ?? 1000;
    return item.cantidadMil <= critical ? 'critico' : item.cantidadMil <= low ? 'bajo' : 'ok';
  }
  if ((item.categoria || '').toLowerCase() === 'elaborado') return 'ok';
  const low = item.nivelBajo ?? prefs.productoBajo ?? 5, critical = item.nivelCritico ?? prefs.productoCritico ?? 1;
  return item.cantidad <= critical ? 'critico' : item.cantidad <= low ? 'bajo' : 'ok';
};
SPVI.caduca = item => item.fechaCaducidad && new Date(item.fechaCaducidad + 'T23:59:59') - Date.now() < 7 * 86400000;
SPVI.colorNivel = nivel => nivel === 'critico' ? 'var(--alert-critico)' : nivel === 'bajo' ? 'var(--alert-bajo)' : null;

/* ---------- Gráficos SVG propios (sin librerías ni líneas de rejilla). ---------- */
/** Dona de reparto. segmentos: [{label, value, color}]. */
function donaSvg(segmentos) {
  const total = segmentos.reduce((suma, x) => suma + (x.value || 0), 0);
  const radio = 40, perimetro = 2 * Math.PI * radio;
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 100 100'); svg.setAttribute('role', 'img');
  svg.setAttribute('aria-label', segmentos.map(x => x.label + ': ' + (total ? Math.round(x.value * 100 / total) : 0) + ' %').join('. '));
  let offset = 0;
  for (const segmento of segmentos) {
    const parte = total ? (segmento.value || 0) / total : 0;
    if (!parte) continue;
    const circulo = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    circulo.setAttribute('cx', '50'); circulo.setAttribute('cy', '50'); circulo.setAttribute('r', String(radio));
    circulo.setAttribute('fill', 'none'); circulo.setAttribute('stroke', segmento.color); circulo.setAttribute('stroke-width', '16');
    circulo.setAttribute('stroke-dasharray', (perimetro * parte).toFixed(2) + ' ' + (perimetro * (1 - parte)).toFixed(2));
    circulo.setAttribute('stroke-dashoffset', (-offset).toFixed(2));
    circulo.setAttribute('transform', 'rotate(-90 50 50)');
    svg.append(circulo); offset += perimetro * parte;
  }
  return svg;
}
/** Área de una serie numérica; los valores negativos se recortan a cero. */
function areaSvg(valores) {
  const ancho = 100, alto = 40, maximo = Math.max(1, ...valores.map(v => Math.max(0, v)));
  const puntos = valores.map((v, i) => [(valores.length < 2 ? 0 : i * ancho / (valores.length - 1)).toFixed(2), (alto - Math.max(0, v) * alto / maximo).toFixed(2)]);
  const linea = puntos.map((p, i) => (i ? 'L' : 'M') + p[0] + ' ' + p[1]).join(' ');
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 100 40'); svg.setAttribute('preserveAspectRatio', 'none'); svg.setAttribute('role', 'img');
  svg.setAttribute('aria-label', 'Ganancia por día');
  const relleno = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  relleno.setAttribute('d', linea + ' L ' + ancho + ' ' + alto + ' L 0 ' + alto + ' Z'); relleno.setAttribute('fill', 'var(--primary)'); relleno.setAttribute('opacity', '.18');
  const trazo = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  trazo.setAttribute('d', linea); trazo.setAttribute('fill', 'none'); trazo.setAttribute('stroke', 'var(--primary)');
  trazo.setAttribute('stroke-width', '1.5'); trazo.setAttribute('stroke-linejoin', 'round'); trazo.setAttribute('vector-effect', 'non-scaling-stroke');
  svg.append(relleno, trazo);
  return svg;
}

/* ---------- Turno y caja: ventanas compartidas por Inicio y Venta. ---------- */
SPVI.turno = {
  abrir() {
    dialog({title: 'Abrir turno', iconName: 'Turno', body: [
      h('p', {class: 'secundario', text: 'Cuenta el efectivo con el que empiezas el día.'}),
      campo({label: 'Fondo inicial (CUP)', name: 'fund', type: 'number', min: 0, step: '0.01', required: true, inputmode: 'decimal', value: '0'}),
    ], actions: [{label: 'Cancelar'}, {label: 'Abrir turno', primary: true, icon: 'Confirmar', onClick: async ({data}) => {
      minor(data.fund); await api('/api/open', {fund: data.fund}); snack('Turno abierto.'); await SPVI.changed();
    }}]});
  },
  async cerrar() {
    const tablero = await SPVI.load.dashboard(7);
    const esperado = tablero.expected || 0;
    const diferencia = h('p', {class: 'dato'});
    const input = campo({label: 'Efectivo contado (CUP)', name: 'counted', type: 'number', min: 0, step: '0.01', required: true, inputmode: 'decimal',
      oninput: event => { try { const d = minor(event.target.value) - esperado; diferencia.textContent = d === 0 ? 'Cuadra con lo esperado.' : (d > 0 ? 'Sobran ' : 'Faltan ') + cup(Math.abs(d)); } catch { diferencia.textContent = ''; } }});
    dialog({title: 'Cerrar turno', iconName: 'Turno', body: [
      itemLista({title: 'Efectivo esperado', value: cup(esperado), subtitle: 'Fondo + ventas en efectivo ± movimientos de caja'}),
      input, diferencia,
    ], actions: [{label: 'Cancelar'}, {label: 'Cerrar turno', primary: true, icon: 'Confirmar', onClick: async ({data}) => {
      minor(data.counted); await api('/api/close', {counted: data.counted}); snack('Turno cerrado.'); await SPVI.changed();
    }}]});
  },
  caja() {
    dialog({title: 'Movimiento de caja', iconName: 'Efectivo', body: [
      seleccion({label: 'Tipo', name: 'direction', options: [['entrada', 'Entrada de efectivo'], ['salida', 'Salida de efectivo']]}),
      campo({label: 'Importe (CUP)', name: 'amount', type: 'number', min: '0.01', step: '0.01', required: true, inputmode: 'decimal'}),
      campo({label: 'Motivo', name: 'reason', maxlength: 120, required: true}),
    ], actions: [{label: 'Cancelar'}, {label: 'Registrar', primary: true, icon: 'Guardar', onClick: async ({data}) => {
      minor(data.amount); await api('/api/movement', data); snack('Movimiento registrado.'); await SPVI.changed();
    }}]});
  },
};

SPVI.bannerLicencia = function (licencia) {
  if (licencia.clase === 'PRUEBA') {
    const dias = Math.max(0, Math.ceil((licencia.venceEn - Date.now()) / 86400000));
    return h('div', {class: 'banner'}, icon('Prueba'), h('span', {class: 'texto', text: 'Periodo de prueba: ' + (dias === 1 ? 'queda 1 día.' : 'quedan ' + dias + ' días.')}),
      button('Licencia', () => SPVI.go('ajustes/licencia'), {kind: 'texto'}));
  }
  if (licencia.clase === 'BLOQUEADA') {
    return h('div', {class: 'banner mal'}, icon('Bloqueo'), h('span', {class: 'texto', text: licencia.motivo || 'Activa una licencia para seguir vendiendo.'}),
      button('Activar', () => SPVI.go('ajustes/licencia'), {kind: 'texto'}));
  }
  if (licencia.venceEn && licencia.venceEn - Date.now() < 7 * 86400000) {
    return h('div', {class: 'banner'}, icon('Vencido'), h('span', {class: 'texto', text: 'Tu licencia vence el ' + fecha(licencia.venceEn - 1) + '.'}),
      button('Renovar', () => SPVI.go('ajustes/licencia'), {kind: 'texto'}));
  }
  return null;
};

SPVI.views.inicio = async function (root) {
  const dias = Number(sessionStorage.getItem('spvi-periodo') || 7);
  const [catalogo, licencia, tablero] = await Promise.all([SPVI.load.catalogo(), SPVI.load.licencia(), SPVI.load.dashboard(dias)]);
  const prefs = catalogo.preferencias || {};
  root.append(h('div', {class: 'encabezado'}, h('h1', {text: catalogo.name || 'Mi negocio'})));
  const banner = SPVI.bannerLicencia(licencia); if (banner) root.append(banner);

  // Turno
  const turno = tablero.shift;
  root.append(turno
    ? h('div', {class: 'card primario'}, h('div', {class: 'cabecera'}, icon('Turno'), h('h3', {text: 'Turno abierto'}), h('span', {class: 'etiqueta ok', text: 'Desde ' + fechaHora(Date.parse(turno.opened))})),
      h('div', {class: 'grid kpi'},
        h('div', null, h('span', {class: 'secundario', text: 'Fondo inicial'}), h('div', {class: 'valor dato', text: cup(turno.fund)})),
        h('div', null, h('span', {class: 'secundario', text: 'Efectivo esperado'}), h('div', {class: 'valor dato', text: cup(tablero.expected)}))),
      h('div', {class: 'fila'}, button('Vender', () => SPVI.go('venta'), {iconName: 'Venta'}), button('Caja', SPVI.turno.caja, {kind: 'tonal', iconName: 'Efectivo'}),
        button('Cerrar turno', SPVI.turno.cerrar, {kind: 'borde', iconName: 'Turno'})))
    : h('div', {class: 'card tonal'}, h('div', {class: 'cabecera'}, icon('Turno'), h('h3', {text: 'Sin turno abierto'})),
      h('p', {class: 'secundario', text: 'Abre un turno para empezar a vender.'}),
      h('div', {class: 'fila'}, button('Abrir turno', SPVI.turno.abrir, {iconName: 'Turno', disabled: licencia.clase === 'BLOQUEADA'}))));

  // Avisos de inventario
  const productos = catalogo.productos.filter(x => !x.eliminado), insumos = catalogo.insumos.filter(x => !x.eliminado);
  const niveles = [...productos.map(x => SPVI.nivel(x, 'productos', prefs)), ...insumos.map(x => SPVI.nivel(x, 'insumos', prefs))];
  const caducan = productos.filter(SPVI.caduca).length;
  const aviso = (titulo, n, color, iconName, filtro) => h('button', {type: 'button', class: 'card centro', onclick: () => SPVI.go('inventario?filtro=' + filtro)},
    h('span', {class: 'alerta-num', style: n ? 'color:' + color : '', text: String(n)}), h('span', {class: 'fila centro'}, icon(iconName, 'sm'), titulo));
  root.append(h('h3', {text: 'Avisos de inventario'}), h('div', {class: 'grid kpi'},
    aviso('Existencias críticas', niveles.filter(x => x === 'critico').length, 'var(--alert-critico)', 'Alerta', 'critico'),
    aviso('Existencias bajas', niveles.filter(x => x === 'bajo').length, 'var(--alert-bajo)', 'Inventario', 'bajo'),
    aviso('Caducan en 7 días', caducan, 'var(--alert-caducidad)', 'Fecha', 'caduca')));

  // Período
  const periodo = h('div', {class: 'chips', role: 'group', 'aria-label': 'Período'}, [[7, '7 días'], [30, '30 días'], [90, '90 días']].map(([d, label]) =>
    h('button', {type: 'button', class: 'chip', 'aria-pressed': String(d === dias), onclick: () => { sessionStorage.setItem('spvi-periodo', d); SPVI.render(); }}, label)));
  const cambio = tablero.revenue_change_percent;
  const kpi = (titulo, valor, extra = null) => h('div', {class: 'card'}, h('span', {class: 'secundario', text: titulo}), h('span', {class: 'valor', text: valor}), extra);
  root.append(h('div', {class: 'encabezado'}, h('h3', {text: 'Resumen de ventas'}), periodo), h('div', {class: 'grid kpi seis'},
    kpi('Ingresos', cup(tablero.revenue), cambio === null ? null : h('span', {class: 'etiqueta ' + (cambio >= 0 ? 'ok' : 'mal'), text: (cambio >= 0 ? '+' : '') + cambio + ' % vs. período anterior'})),
    kpi('Ganancia', cup(tablero.margin)), kpi('Operaciones', String(tablero.operations)),
    kpi('Efectivo', cup(tablero.cash)), kpi('Transferencias', cup(tablero.transfers)), kpi('Valor del inventario', cup(tablero.inventory_cost))));

  // Gráfico diario
  const serie = [];
  for (let i = dias - 1; i >= 0; i--) { const d = new Date(Date.now() - i * 86400000); serie.push(d.toISOString().slice(0, 10)); }
  const porDia = Object.fromEntries(tablero.daily.map(x => [x.day, x.revenue]));
  const maximo = Math.max(1, ...serie.map(d => porDia[d] || 0));
  root.append(h('div', {class: 'dos'},
    h('div', {class: 'card'}, h('div', {class: 'cabecera'}, icon('Registros'), h('h3', {text: 'Ingresos por día'})),
      h('div', {class: 'barras', role: 'img', 'aria-label': 'Ingresos por día'}, serie.map((d, i) => h('div', {class: 'barra', style: 'height:' + Math.round((porDia[d] || 0) * 100 / maximo) + '%;animation-delay:' + (i * 12) + 'ms', title: fecha(Date.parse(d + 'T12:00:00')) + ': ' + cup(porDia[d] || 0)}))),
      h('div', {class: 'ejes'}, h('span', {text: fecha(Date.parse(serie[0] + 'T12:00:00'))}), h('span', {text: 'Hoy'}))),
    h('div', {class: 'card'}, h('div', {class: 'cabecera'}, icon('Recomendados'), h('h3', {text: 'Más vendidos'})),
      tablero.top.length ? h('div', {class: 'lista plana'}, tablero.top.map((x, i) => itemLista({title: x.name, value: cup(x.total), lead: h('span', {class: 'lead', text: String(i + 1)})})))
        : vacio('Venta', 'Aún no hay ventas en este período.'))));

  // Formas de cobro y ganancia por día.
  const margenPorDia = Object.fromEntries(tablero.daily.map(x => [x.day, x.margin]));
  const metodos = [{label: 'Efectivo', value: tablero.cash, color: 'var(--chart-1)'}, {label: 'Transferencias', value: tablero.transfers, color: 'var(--chart-2)'}];
  const totalMetodos = tablero.cash + tablero.transfers;
  root.append(h('div', {class: 'dos'},
    h('div', {class: 'card'}, h('div', {class: 'cabecera'}, icon('Efectivo'), h('h3', {text: 'Formas de cobro'})),
      totalMetodos ? h('div', {class: 'dona'}, donaSvg(metodos),
        h('div', {class: 'leyenda'}, metodos.map(x => h('div', {class: 'fila'},
          h('span', {class: 'punto', style: 'background:' + x.color}),
          h('span', {text: x.label + ': ' + Math.round(x.value * 100 / totalMetodos) + ' %'}),
          h('span', {class: 'dato', style: 'margin-left:auto', text: cup(x.value)})))))
        : vacio('Efectivo', 'Sin ventas en este período.')),
    h('div', {class: 'card'}, h('div', {class: 'cabecera'}, icon('Registros'), h('h3', {text: 'Ganancia por día'})),
      h('div', {class: 'area'}, areaSvg(serie.map(d => margenPorDia[d] || 0))),
      h('div', {class: 'ejes'}, h('span', {text: fecha(Date.parse(serie[0] + 'T12:00:00'))}), h('span', {text: 'Hoy'})))));

  // Bajo costo y movimientos recientes
  if (tablero.below_cost.length) {
    root.append(h('div', {class: 'card error'}, h('div', {class: 'cabecera'}, icon('Alerta'), h('h3', {text: 'Precio igual o menor que el costo'})),
      h('div', {class: 'lista plana'}, tablero.below_cost.slice(0, 8).map(x => itemLista({title: x.name, subtitle: 'Costo ' + cup(x.cost), value: cup(x.price)})))));
  }
  root.append(h('div', {class: 'dos'},
    h('div', {class: 'card'}, h('div', {class: 'cabecera'}, icon('Venta'), h('h3', {text: 'Ventas recientes'}), button('Ver todo', () => SPVI.go('registros'), {kind: 'texto'})),
      tablero.recent.length ? h('div', {class: 'lista plana'}, tablero.recent.slice(0, 8).map(x => itemLista({title: x.name, subtitle: fechaHora(Date.parse(x.created)) + ' · ' + (x.method === 'transferencia' ? 'Transferencia' : 'Efectivo'), value: cup(x.total)})))
        : vacio('Venta', 'Sin ventas todavía.')),
    h('div', {class: 'card'}, h('div', {class: 'cabecera'}, icon('Turno'), h('h3', {text: 'Últimos turnos'}), button('Ver todo', () => SPVI.go('registros?tipo=turnos'), {kind: 'texto'})),
      tablero.shifts.length ? h('div', {class: 'lista plana'}, tablero.shifts.map(t => {
        const diff = t.counted === null || t.counted === undefined ? null : t.counted - t.expected;
        return itemLista({title: fechaHora(Date.parse(t.opened)), subtitle: t.closed ? 'Cerrado ' + fechaHora(Date.parse(t.closed)) : 'Abierto',
          value: diff === null ? cup(t.expected) : diff === 0 ? 'Cuadra' : (diff > 0 ? '+' : '−') + cup(Math.abs(diff))});
      })) : vacio('Turno', 'Sin turnos todavía.'))));
};
