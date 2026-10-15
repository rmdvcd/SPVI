'use strict';
/* Núcleo de la interfaz: utilidades, API, ventanas emergentes, mensajes y navegación por secciones (#/seccion). */
const SPVI = window.SPVI = {views: {}, cache: {}, state: {}};
const ICONS = '/static/icons.svg';
const LOGO = '/static/icons/spvi-64.png';

function $(selector, root = document) { return root.querySelector(selector); }

/** Crea un elemento: h('button', {class: 'btn', onclick}, 'Texto', icon('Agregar')). */
function h(tag, attrs, ...children) {
  const element = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value === null || value === undefined || value === false) continue;
    if (key === 'class') element.className = value;
    else if (key === 'text') element.textContent = value;
    else if (key === 'dataset') Object.assign(element.dataset, value);
    else if (key === 'style') element.style.cssText = value;   // CSSOM: la CSP no permite atributos style
    else if (key.startsWith('on') && typeof value === 'function') element.addEventListener(key.slice(2), value);
    else if (['disabled', 'checked', 'hidden', 'required', 'selected', 'readOnly', 'multiple', 'autofocus'].includes(key)) element[key] = !!value;
    else if (key === 'value') element.value = value;
    else element.setAttribute(key, value === true ? '' : value);
  }
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    element.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return element;
}

function icon(name, extra = '') {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', ('icon ' + extra).trim()); svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
  use.setAttribute('href', ICONS + '#' + name); svg.append(use);
  return svg;
}

/** Botón solo icono: siempre con descripción accesible (title + aria-label). */
function iconButton(name, label, onclick, extra = '') {
  return h('button', {type: 'button', class: ('icon-btn ' + extra).trim(), 'aria-label': label, title: label, onclick}, icon(name));
}

function button(label, onclick, {kind = '', iconName = null, type = 'button', disabled = false} = {}) {
  return h('button', {type, class: ('btn ' + kind).trim(), onclick, disabled}, iconName ? icon(iconName) : null, label);
}

/* ---------- Formatos (como la app móvil: «1,100.00 CUP», «01/10/2026 12:20»). ---------- */
const numberFormat = new Intl.NumberFormat('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2});
function cup(cents) { return numberFormat.format((cents || 0) / 100) + ' CUP'; }
function pesos(cents) { return cents === null || cents === undefined ? '' : String(cents / 100); }
function cantidad(value) { return new Intl.NumberFormat('en-US', {maximumFractionDigits: 3}).format(value); }
const pad = n => String(n).padStart(2, '0');
function fecha(ms) { if (!ms) return ''; const d = new Date(ms); return pad(d.getDate()) + '/' + pad(d.getMonth() + 1) + '/' + d.getFullYear(); }
function hora(ms) { const d = new Date(ms); return pad(d.getHours()) + ':' + pad(d.getMinutes()); }
function fechaHora(ms) { return ms ? fecha(ms) + ' ' + hora(ms) : ''; }
function duracion(ms) {
  ms = Math.max(0, ms || 0);
  const s = Math.floor(ms / 1000), d = Math.floor(s / 86400), hh = Math.floor(s % 86400 / 3600), mm = Math.floor(s % 3600 / 60), ss = s % 60;
  if (d) return d + ' d ' + hh + ' h ' + pad(mm) + ' min';
  return pad(hh) + ':' + pad(mm) + ':' + pad(ss);
}
function hace(ms) {
  if (!ms) return 'Nunca';
  const s = Math.max(0, Math.round((Date.now() - ms) / 1000));
  if (s < 60) return 'Hace un momento';
  if (s < 3600) return 'Hace ' + Math.floor(s / 60) + ' min';
  if (s < 86400) return 'Hace ' + Math.floor(s / 3600) + ' h';
  return fechaHora(ms);
}
/** Texto decimal → entero en centésimas (o milésimas): sin errores de coma flotante. */
function minor(value, digits = 2) {
  const text = String(value ?? '').trim().replace(',', '.');
  if (!new RegExp('^-?\\d+(\\.\\d{1,' + digits + '})?$').test(text)) throw new Error('Escribe un número válido.');
  const negative = text.startsWith('-'); const [a, b = ''] = text.replace('-', '').split('.');
  const result = Number(a) * 10 ** digits + Number(b.padEnd(digits, '0'));
  if (!Number.isSafeInteger(result)) throw new Error('Número demasiado grande.');
  return negative ? -result : result;
}
const UNIDADES = {UNIDAD: 'Unidad', KILOGRAMO: 'Kilogramo', GRAMO: 'Gramo', LITRO: 'Litro', MILILITRO: 'Mililitro'};
const UNIDAD_CORTA = {UNIDAD: 'u', KILOGRAMO: 'kg', GRAMO: 'g', LITRO: 'L', MILILITRO: 'mL'};

/* ---------- API local (CSRF, idempotencia y errores legibles). ---------- */
const csrf = () => $('meta[name=csrf]').content;
const TRANSACCIONAL = /^\/api\/(cart|correct|sale|void|item|item-edit|item-delete|open|close|movement|catalog|settings|employees|photo|client)$/;
async function api(path, data) {
  const headers = data !== undefined ? {'Content-Type': 'application/json', 'X-CSRF-Token': csrf()} : {};
  let pending;
  if (data !== undefined && TRANSACCIONAL.test(path)) {
    const business = SPVI.cache.catalogo?.business_id || '';
    if (business) headers['X-SPVI-Business'] = business;
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(business + '\n' + path + '\n' + JSON.stringify(data)));
    pending = 'spvi-operation-' + [...new Uint8Array(digest)].map(x => x.toString(16).padStart(2, '0')).join('');
    const key = sessionStorage.getItem(pending) || crypto.randomUUID(); sessionStorage.setItem(pending, key); headers['Idempotency-Key'] = key;
  }
  let response;
  try { response = await fetch(path, {method: data !== undefined ? 'POST' : 'GET', headers, body: data !== undefined ? JSON.stringify(data) : undefined}); }
  catch { throw new Error('SPVI no responde. Comprueba que sigue abierto (icono junto al reloj).'); }
  if (response.status === 401) { location.reload(); throw new Error('Inicia sesión de nuevo.'); }
  let value;
  try { value = await response.json(); }
  catch {
    if (response.ok) throw new Error('No se pudo confirmar la respuesta. Reintenta sin cambiar los datos.');
    value = {error: response.status === 403 ? 'La sesión cambió. Recarga la página.' : response.status === 413 ? 'La operación supera el tamaño permitido.' : 'No se pudo completar la operación.'};
  }
  if (!response.ok) { if (pending && response.status < 500) sessionStorage.removeItem(pending); throw new Error(value.error || 'No se pudo completar la operación.'); }
  if (pending) sessionStorage.removeItem(pending);
  return value;
}
async function apiBlob(path, data, form) {
  const options = {method: 'POST', headers: {'X-CSRF-Token': csrf()}};
  if (form) options.body = form; else { options.headers['Content-Type'] = 'application/json'; options.body = JSON.stringify(data); }
  const response = await fetch(path, options);
  if (!response.ok) { const error = await response.json().catch(() => ({})); throw new Error(error.error || 'No se pudo completar la operación.'); }
  return response;
}
async function download(path, data) {
  const response = await apiBlob(path, data);
  const name = /filename="?([^";]+)/.exec(response.headers.get('Content-Disposition') || '')?.[1] || 'SPVI';
  const url = URL.createObjectURL(await response.blob());
  h('a', {href: url, download: name}).click();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}

/* ---------- Datos compartidos entre secciones. ---------- */
SPVI.load = {
  async catalogo(force) { if (force || !SPVI.cache.catalogo) SPVI.cache.catalogo = await api('/api/catalogo'); return SPVI.cache.catalogo; },
  async licencia(force) { if (force || !SPVI.cache.licencia) SPVI.cache.licencia = await api('/api/license'); return SPVI.cache.licencia; },
  async app(force) { if (force || !SPVI.cache.app) SPVI.cache.app = await api('/api/app'); return SPVI.cache.app; },
  async dashboard(days = 30) { return api('/api/dashboard?days=' + days); },
};
/** Tras guardar algo: se descarta lo leído y se vuelve a pintar la sección actual. */
SPVI.changed = async function () { SPVI.cache = {}; await SPVI.render(); };

/* ---------- Mensajes breves. ---------- */
let snackTimer;
function snack(message, error = false) {
  const bar = $('#snackbar'); bar.textContent = message; bar.className = 'snackbar' + (error ? ' error' : ''); bar.hidden = false;
  clearTimeout(snackTimer); snackTimer = setTimeout(() => { bar.hidden = true; }, error ? 6000 : 3500);
}
/** Ejecuta una acción con el botón desactivado y muestra el error si falla. */
async function busy(element, action) {
  if (element) element.disabled = true;
  try { return await action(); } catch (error) { snack(error.message, true); } finally { if (element) element.disabled = false; }
}

/* ---------- Una sola pestaña: las demás muestran un aviso y no pintan nada. ---------- */
const CLAVE_PESTANA = 'spvi-pestana';
/* El identificador sobrevive a la recarga (sessionStorage es por pestaña); si no, «Usar aquí» se bloquearía a sí mismo. */
let ID_PESTANA = null;
try { ID_PESTANA = sessionStorage.getItem('spvi-id') || ''; } catch { ID_PESTANA = ''; }
if (!ID_PESTANA) {
  ID_PESTANA = crypto.randomUUID();
  try { sessionStorage.setItem('spvi-id', ID_PESTANA); } catch { /* sin acción */ }
}
const LATIDO_MS = 3000, CADUCIDAD_MS = 9000;
let pestanaPropia = false, latidoPestana = null;
function leerPestana() {
  try { return JSON.parse(localStorage.getItem(CLAVE_PESTANA) || 'null'); }
  catch { return null; }
}
function escribirPestana() {
  localStorage.setItem(CLAVE_PESTANA, JSON.stringify({id: ID_PESTANA, ts: Date.now()}));
}
/** Reclama esta pestaña como la activa, salvo que otra siga viva. Sin almacenamiento no se controla. */
function pestanaActiva() {
  try {
    const actual = leerPestana();
    if (actual && actual.id !== ID_PESTANA && Date.now() - actual.ts < CADUCIDAD_MS) return false;
    escribirPestana(); pestanaPropia = true;
  } catch { return true; }
  latidoPestana = setInterval(() => { if (pestanaPropia) { try { escribirPestana(); } catch { /* sin acción */ } } }, LATIDO_MS);
  window.addEventListener('storage', evento => {
    if (evento.key === CLAVE_PESTANA && pestanaPropia) {
      const otra = leerPestana();
      if (otra && otra.id !== ID_PESTANA) { pestanaPropia = false; clearInterval(latidoPestana); mostrarPestanaBloqueada(); }
    }
  });
  window.addEventListener('beforeunload', () => {
    if (!pestanaPropia) return;
    try { const actual = leerPestana(); if (actual && actual.id === ID_PESTANA) localStorage.removeItem(CLAVE_PESTANA); }
    catch { /* sin acción */ }
  });
  return true;
}
/** Aviso a pantalla completa cuando SPVI ya está abierto en otra pestaña. */
function mostrarPestanaBloqueada() {
  if (cleanup) { try { cleanup(); } catch { /* sin acción */ } cleanup = null; }
  for (const open of document.querySelectorAll('dialog[open]')) open.close('');
  buildMenu('inicio'); SPVI.actions();
  $('#seccion').textContent = ''; document.title = 'SPVI';
  $('#vista').replaceChildren(h('div', {class: 'vista'},
    h('div', {class: 'card centro'},
      vacio('Alerta', 'SPVI ya está abierto en otra pestaña de este navegador. Usa esa pestaña para seguir trabajando: solo puede haber una a la vez.'),
      button('Usar aquí', () => { try { escribirPestana(); } catch { /* sin acción */ } location.reload(); }, {iconName: 'Confirmar'}))));
}

/* ---------- Ventanas emergentes centradas con el icono de SPVI. ---------- */
function dialog({title, body = [], actions = [], wide = false, iconName = null, onClose = null}) {
  const error = h('p', {class: 'dlg-error', role: 'alert'});
  const form = h('form', {class: 'dlg', method: 'dialog', novalidate: true});
  const element = h('dialog', {class: wide ? 'ancho' : '', 'aria-label': title}, form);
  const close = value => { if (element.open) element.close(value); };
  const header = h('div', {class: 'dlg-cab'},
    iconName ? h('span', {class: 'lead'}, icon(iconName, 'lg')) : h('img', {class: 'logo', src: LOGO, alt: ''}),
    h('h2', {text: title}),
    iconButton('Cancelar', 'Cerrar', () => close('')));
  const footer = h('div', {class: 'dlg-pie'});
  let primary = null;
  for (const action of actions) {
    const btn = button(action.label, null, {kind: action.kind ?? (action.primary ? '' : 'texto'), iconName: action.icon, type: action.primary ? 'submit' : 'button'});
    btn.addEventListener('click', async event => {
      event.preventDefault();
      if (action.primary && !form.reportValidity()) return;
      if (!action.onClick) { close(action.value ?? ''); return; }
      error.textContent = ''; btn.disabled = true;
      try { const keep = await action.onClick({form, close, error, data: Object.fromEntries(new FormData(form))}); if (keep !== false) close(action.value ?? 'ok'); }
      catch (problem) { error.textContent = problem.message; }
      finally { btn.disabled = false; }
    });
    if (action.primary) primary = btn;
    footer.append(btn);
  }
  form.append(header, h('div', {class: 'dlg-cuerpo'}, body, error), footer);
  form.addEventListener('submit', event => { event.preventDefault(); primary?.click(); });
  element.addEventListener('close', () => { onClose?.(element.returnValue); setTimeout(() => element.remove(), 0); });
  document.body.append(element); element.showModal();
  const first = form.querySelector('.dlg-cuerpo input:not([type=hidden]):not([readonly]), .dlg-cuerpo select, .dlg-cuerpo textarea');
  (first || primary)?.focus();
  return {element, form, close, error};
}
/** Confirmación propia (no la del navegador). Con «palabra», hay que escribirla para continuar. */
function confirmar({title, text, confirm = 'Aceptar', danger = false, iconName = null, palabra = null}) {
  return new Promise(resolve => {
    let accepted = false;
    const body = [h('p', {text})];
    if (palabra) body.push(campo({label: 'Escribe ' + palabra + ' para confirmar', name: 'palabra', required: true, autocomplete: 'off'}));
    dialog({title, iconName, body, onClose: () => resolve(accepted), actions: [
      {label: 'Cancelar'},
      {label: confirm, primary: true, kind: danger ? 'peligro' : '', icon: danger ? 'Eliminar' : 'Confirmar', onClick: ({data}) => {
        if (palabra && data.palabra.trim().toUpperCase() !== palabra) throw new Error('Escribe ' + palabra + ' exactamente.');
        accepted = true;
      }},
    ]});
  });
}

/* ---------- Campos de formulario. ---------- */
function campo({label, name, type = 'text', value = '', help = null, rows = null, ...rest}) {
  const input = rows ? h('textarea', {name, rows, ...rest}) : h('input', {name, type, ...rest});
  if (value !== null && value !== undefined) input.value = value;
  if (type === 'number' && !rest.step) input.step = 'any';
  return h('label', {class: 'campo'}, h('span', {text: label}), input, help ? h('span', {class: 'ayuda', text: help}) : null);
}
function seleccion({label, name, options, value = '', help = null, ...rest}) {
  const select = h('select', {name, ...rest}, options.map(([v, text]) => h('option', {value: v, text})));
  select.value = value ?? '';
  return h('label', {class: 'campo'}, h('span', {text: label}), select, help ? h('span', {class: 'ayuda', text: help}) : null);
}
function interruptor({label, help = null, checked = false, name = null, onchange = null}) {
  return h('label', {class: 'interruptor'}, h('span', {class: 'texto'}, h('span', {text: label}), help ? h('span', {class: 'secundario', text: help}) : null),
    h('input', {type: 'checkbox', role: 'switch', name, checked, onchange}));
}
function buscador(placeholderLabel, oninput, value = '') {
  return h('label', {class: 'buscar'}, icon('Buscar'), h('input', {type: 'search', 'aria-label': placeholderLabel, placeholder: placeholderLabel, value, oninput}));
}
function pestanas(items, selected, onselect) {
  return h('div', {class: 'pestanas', role: 'tablist'}, items.map(([id, label]) =>
    h('button', {type: 'button', role: 'tab', 'aria-selected': String(id === selected), onclick: () => onselect(id)}, label)));
}
function vacio(iconName, text) { return h('div', {class: 'vacio'}, icon(iconName), h('p', {text})); }

/* ---------- Período: los mismos chips en Inicio y en Registros (se guarda en la sesión). ---------- */
const PERIODOS = [['turno', 'Turno'], ['hoy', 'Hoy'], ['7', '7 días'], ['30', '30 días'], ['90', '90 días'], ['todo', 'Todo']];
function chipsPeriodo(actual, onchange, lista = PERIODOS) {
  return h('div', {class: 'chips', role: 'group', 'aria-label': 'Período'}, lista.map(([id, label]) =>
    h('button', {type: 'button', class: 'chip', 'aria-pressed': String(actual === id), onclick: () => onchange(id)}, label)));
}
SPVI.periodo = () => {
  const guardado = sessionStorage.getItem('spvi-periodo');
  return PERIODOS.some(([id]) => id === guardado) ? guardado : '7';
};
/** Instante desde el que cuenta el período (``null`` = todo el historial). */
SPVI.desdePeriodo = function (periodo, aperturaTurno) {
  if (periodo === 'todo') return null;
  const inicio = new Date(); inicio.setHours(0, 0, 0, 0);
  if (periodo === 'turno') return aperturaTurno ? Date.parse(aperturaTurno) : inicio.getTime();
  if (periodo === 'hoy') return inicio.getTime();
  inicio.setDate(inicio.getDate() - Number(periodo) + 1);
  return inicio.getTime();
};
/** Días que debe cubrir la serie del gráfico según el período elegido. */
SPVI.diasSerie = function (periodo, tablero) {
  if (periodo === 'todo') {
    const dias = [...new Set((tablero.daily || []).map(x => x.day))].sort();
    return (dias.length ? dias : [new Date().toISOString().slice(0, 10)]).slice(-30);
  }
  const desde = periodo === 'turno' && tablero.shift ? Date.parse(tablero.shift.opened) : Date.now();
  const cuantos = periodo === 'hoy' ? 1 : Math.max(1, Math.ceil((Date.now() - desde) / 86400000) + 1);
  const serie = [];
  for (let i = cuantos - 1; i >= 0; i--) serie.push(new Date(Date.now() - i * 86400000).toISOString().slice(0, 10));
  return serie;
};
function itemLista({title, subtitle = null, value = null, lead = null, onclick = null, trailing = null, cls = '', indicator = null}) {
  const tag = onclick ? 'button' : 'div';
  return h(tag, {class: ('item-lista ' + cls).trim(), type: onclick ? 'button' : null, onclick},
    indicator ? h('span', {class: 'indicador', style: 'background:' + indicator}) : null,
    lead, h('span', {class: 'texto'}, h('span', {class: 'titulo', text: title}),
      [].concat(subtitle || []).filter(Boolean).map(line => h('span', {class: 'secundario', text: line}))),
    value !== null ? h('span', {class: 'valor', text: value}) : null, trailing);
}
function leadIcon(name) { return h('span', {class: 'lead'}, icon(name)); }
/** Lee una foto como base64 (sin prefijo) para /api/photo. */
function leerFoto(file) {
  return new Promise((resolve, reject) => {
    if (file.size > 5 * 1024 * 1024) { reject(new Error('La foto supera 5 MB.')); return; }
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(',')[1]);
    reader.onerror = () => reject(new Error('No se pudo leer la foto.'));
    reader.readAsDataURL(file);
  });
}

/* ---------- Navegación: menú lateral (hamburguesa) y secciones. ---------- */
SPVI.routes = [
  {id: 'inicio', title: 'Inicio', icon: 'Inicio'},
  {id: 'venta', title: 'Venta', icon: 'Venta'},
  {id: 'inventario', title: 'Inventario', icon: 'Inventario'},
  {id: 'servicios', title: 'Servicios', icon: 'Servicios'},
  {id: 'registros', title: 'Registros', icon: 'Registros'},
  null,
  {id: 'vinculadas', title: 'Apps vinculadas', icon: 'Vinculacion'},
  {id: 'tiempo', title: 'Tiempo en línea', icon: 'Cronometro'},
  null,
  {id: 'ajustes', title: 'Ajustes', icon: 'Ajustes'},
  null,
  {id: 'ayuda', title: 'Ayuda', icon: 'Ayuda'},
  {id: 'soporte', title: 'Soporte', icon: 'Soporte'},
];
let cleanup = null;
function parseHash() {
  const [path, query = ''] = location.hash.replace(/^#\/?/, '').split('?');
  const [id = 'inicio', ...rest] = path.split('/');
  return {id: SPVI.views[id] ? id : 'inicio', sub: rest.join('/'), params: new URLSearchParams(query)};
}
function drawer(open) {
  document.body.classList.toggle('drawer-open', open);
  $('#scrim').hidden = !open; $('#menu-btn').setAttribute('aria-expanded', String(open));
}
function buildMenu(current) {
  const menu = $('#menu'); menu.replaceChildren();
  for (const route of SPVI.routes) {
    if (!route) { menu.append(h('hr')); continue; }
    menu.append(h('a', {class: 'item', href: '#/' + route.id, 'aria-current': route.id === current ? 'page' : null}, icon(route.icon), h('span', {text: route.title})));
  }
  if (document.body.dataset.protegida === 'si') {
    menu.append(h('hr'), h('a', {class: 'item', href: '#', onclick: async event => {
      event.preventDefault(); await busy(null, async () => { await api('/api/logout', {}); location.reload(); });
    }}, icon('Salir'), h('span', {text: 'Cerrar sesión'})));
  }
}
SPVI.actions = (...nodes) => $('#acciones-barra').replaceChildren(...nodes);
SPVI.render = async function () {
  if (!pestanaPropia) { mostrarPestanaBloqueada(); return; }
  const {id, sub, params} = parseHash();
  if (cleanup) { try { cleanup(); } catch { /* sin acción */ } cleanup = null; }
  for (const open of document.querySelectorAll('dialog[open]')) open.close('');   // las ventanas pertenecen a la sección anterior
  buildMenu(id); SPVI.actions();
  const route = SPVI.routes.find(r => r && r.id === id);
  $('#seccion').textContent = route.title; document.title = 'SPVI · ' + route.title;
  const view = $('#vista');
  try {
    const [catalogo, licencia] = await Promise.all([SPVI.load.catalogo(), SPVI.load.licencia()]);
    $('#nombre-negocio').textContent = catalogo.name;
    $('#estado-licencia').textContent = {PRUEBA: 'Periodo de prueba', ACTIVA: 'Licencia activa', PERPETUA: 'Licencia perpetua', BLOQUEADA: 'Sin licencia vigente'}[licencia.clase] || '';
    const container = h('div', {class: 'vista'});
    const result = await SPVI.views[id](container, sub, params);
    if (typeof result === 'function') cleanup = result;
    const banner = SPVI.bannerLicencia(licencia); if (banner) container.prepend(banner);
    await SPVI.fundir(view.firstElementChild);
    view.replaceChildren(container);
  } catch (error) {
    view.replaceChildren(h('div', {class: 'vista'}, h('div', {class: 'card error centro'}, icon('Alerta', 'lg'), h('p', {text: error.message}),
      button('Reintentar', () => SPVI.changed(), {iconName: 'Reintentar'}))));
  }
};
/** Fundido de salida de la sección anterior antes de pintar la nueva (respeta «reducir movimiento»). */
SPVI.fundir = function (anterior) {
  return new Promise(resolve => {
    if (!anterior || !anterior.classList.contains('vista') || window.matchMedia('(prefers-reduced-motion: reduce)').matches) { resolve(); return; }
    anterior.classList.add('saliendo');
    setTimeout(resolve, 120);
  });
};
SPVI.go = route => { if (location.hash === '#/' + route) SPVI.render(); else location.hash = '#/' + route; };

window.addEventListener('hashchange', () => { if (window.innerWidth < 1200) drawer(false); window.scrollTo(0, 0); SPVI.render(); });
document.addEventListener('DOMContentLoaded', () => {
  // En pantallas anchas el menú queda fijo y la hamburguesa lo oculta o muestra; en estrechas se abre encima.
  if (localStorage.getItem('spvi-menu') === 'cerrado') document.body.classList.add('drawer-cerrado');
  $('#menu-btn').addEventListener('click', () => {
    if (window.innerWidth >= 1200) {
      const cerrado = document.body.classList.toggle('drawer-cerrado');
      localStorage.setItem('spvi-menu', cerrado ? 'cerrado' : 'abierto'); $('#menu-btn').setAttribute('aria-expanded', String(!cerrado));
    } else drawer(!document.body.classList.contains('drawer-open'));
  });
  $('#scrim').addEventListener('click', () => drawer(false));
  document.addEventListener('keydown', event => { if (event.key === 'Escape' && document.body.classList.contains('drawer-open')) drawer(false); });
  if (!location.hash) history.replaceState(null, '', '#/inicio');
  if (!pestanaActiva()) { mostrarPestanaBloqueada(); return; }
  SPVI.render();
});
