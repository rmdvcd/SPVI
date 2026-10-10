'use strict';
const $ = s => document.querySelector(s);
const cup = v => new Intl.NumberFormat('es-CU', {style:'currency', currency:'CUP'}).format(v / 100);
const message = text => { $('#message').textContent = text; };
let snapshot;
async function api(path, data) {
  const transactional=data && /^\/api\/(cart|correct|sale|void|item|item-edit|item-delete|open|close|movement|catalog|settings|employees|photo|client)$/.test(path);
  const headers=data?{'Content-Type':'application/json','X-CSRF-Token':$('meta[name=csrf]').content}:{};
  let pending;
  if(transactional){
    const business=snapshot?.business_id || '';if(business)headers['X-SPVI-Business']=business;
    const digest=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(business+'\n'+path+'\n'+JSON.stringify(data)));
    pending='spvi-operation-'+[...new Uint8Array(digest)].map(x=>x.toString(16).padStart(2,'0')).join('');
    const key=sessionStorage.getItem(pending) || crypto.randomUUID();sessionStorage.setItem(pending,key);headers['Idempotency-Key']=key;
  }
  const response = await fetch(path, {method:data?'POST':'GET',headers,body:data?JSON.stringify(data):undefined});
  if(response.status===401 && path!=='/login'){location.reload();throw new Error('Sesión cerrada.');}
  let value;try{value=await response.json();}catch{if(response.ok)throw new Error('No se pudo confirmar la respuesta. Reintenta sin cambiar los datos; se conservará la misma operación.');value={error:response.status===403?'La sesión cambió. Recarga la página e inicia sesión.':response.status===413?'La operación supera el tamaño permitido.':'El servidor no pudo completar la operación.'};}
  if(!response.ok){if(pending && response.status<500)sessionStorage.removeItem(pending);throw new Error(value.error || 'No se pudo completar la operación.');}
  if(pending)sessionStorage.removeItem(pending);
  return value;
}

function rows(target, values) {
  const body = $(target); body.replaceChildren();
  for (const valuesRow of values) {
    const row = document.createElement('tr');
    for (const text of valuesRow) { const cell = document.createElement('td'); cell.textContent = text; row.append(cell); }
    body.append(row);
  }
}
function list(target, values) {
  const container = $(target); container.replaceChildren();
  for (const text of values.length ? values : ['Sin registros']) { const p = document.createElement('p'); p.textContent = text; container.append(p); }
}
function catalog() {
  const search = $('#search').value.toLocaleLowerCase();
  rows('#items', snapshot.items.filter(x => x.name.toLocaleLowerCase().includes(search)).map(x => [x.name, x.kind, cup(x.price), x.kind !== 'servicio' ? x.stock : '—']));
  window.dispatchEvent(new Event('spvi-catalog'));
}
async function refresh() {
  snapshot = await api('/api/dashboard?days=' + $('#period').value);
  const d = snapshot;
  $('#metrics').replaceChildren();
  const metrics = [['Ventas',cup(d.revenue)],['Período anterior',cup(d.previous_revenue)],['Variación',d.revenue_change_percent === null ? 'Sin base comparable' : d.revenue_change_percent + '%'],['Margen bruto',cup(d.margin)],['Ticket medio',cup(d.operations ? d.revenue / d.operations : 0)],['Operaciones',d.operations],['Efectivo vendido',cup(d.cash)],['Transferencias',cup(d.transfers)],['Inventario a costo',cup(d.inventory_cost)]];
  for (const [name, value] of metrics) { const section=document.createElement('section'); const title=document.createElement('p');title.textContent=name;const amount=document.createElement('strong');amount.textContent=value;section.append(title,amount);$('#metrics').append(section); }
  $('#shift').textContent = d.shift ? 'Abierto · Efectivo esperado: ' + cup(d.expected) : 'Cerrado';
  $('#open-area').hidden = !!d.shift; $('#close-area').hidden = !d.shift; $('#floating').hidden = !d.shift;
  const select=$('#sale-items'); select.replaceChildren();
  for (const item of d.items.filter(x => x.sellable!==false && (x.kind === 'servicio' || x.elaborated || x.stock > 0))) { const option=document.createElement('option');option.value=item.id;option.textContent=item.name+' · '+cup(item.price);select.append(option); }
  catalog();
  list('#daily', d.daily.map(x=>x.day+' · '+cup(x.revenue)+' · Margen bruto: '+cup(x.margin)));
  list('#below-cost', d.below_cost.map(x=>x.name+' · Venta '+cup(x.price)+' · Costo '+cup(x.cost)));
  list('#top', d.top.map(x=>x.name+' · '+cup(x.total)));
  list('#alerts', [...d.low_stock.map(x=>x.name+' · '+x.stock+' unidades · umbral '+x.minimum),...d.items.filter(x=>x.expires && x.expires<=new Date(Date.now()+7*86400000).toISOString().slice(0,10)).map(x=>x.name+' · Caducidad: '+x.expires)]);
  list('#shifts', d.shifts.map(x=>'Turno '+x.id+' · '+(x.closed ? 'Cerrado · Diferencia: '+(x.counted === null ? 'Sin arqueo' : cup(x.counted-x.expected)) : 'Abierto')));
  if(typeof managementRefresh==='function')await managementRefresh();
  rows('#recent', d.recent.map(x=>[x.created, x.name, x.quantity, x.method, cup(x.total ?? x.price*x.quantity)]));
}
$('#login')?.addEventListener('submit', async e => {e.preventDefault();const button=e.target.querySelector('button');button.disabled=true;try {await api('/login', Object.fromEntries(new FormData(e.target)));location.reload();} catch(error){message(error.message);} finally{button.disabled=false;}});
$('#logout')?.addEventListener('click', async()=>{try{await api('/api/logout',{});location.reload();}catch(e){message(e.message);}});
for (const form of document.querySelectorAll('form[data-action]')) form.addEventListener('submit', async e=>{
  e.preventDefault(); const button=form.querySelector('button'); button.disabled=true;
  const dialog = form.closest('dialog');
  try {await api('/api/'+form.dataset.action,Object.fromEntries(new FormData(form)));form.reset();if(form.id==='item-form'){form.dataset.action='item';for(const key of ['cost','stock','minimum'])form.elements[key].readOnly=false;}dialog?.close();await refresh();message('Guardado.');}
  catch(error){if(dialog) dialog.querySelector('.dialog-error').textContent=error.message;else message(error.message);}
  finally {button.disabled=false;}
});
for(const kind of ['sale','movement']) $(`#${kind}-open`)?.addEventListener('click',()=>{$(`#${kind}-dialog .dialog-error`).textContent='';$(`#${kind}-dialog`).showModal();});
for(const button of document.querySelectorAll('[data-close]')) button.addEventListener('click',()=>button.closest('dialog').close());
$('#period')?.addEventListener('change',()=>refresh().catch(e=>message(e.message)));
$('#search')?.addEventListener('input',catalog);
if($('#metrics')) refresh().catch(e=>message(e.message));

// Descargas autenticadas: el nombre lo proporciona el servidor, nunca una ruta del usuario.
async function download(path, payload) {
  const response = await fetch(path, {method:'POST', headers:{'Content-Type':'application/json', 'X-CSRF-Token':$('meta[name=csrf]').content}, body:JSON.stringify(payload)});
  if (!response.ok) { const error = await response.json().catch(()=>({})); throw new Error(error.error || 'No se pudo descargar.'); }
  const blob = await response.blob(), url = URL.createObjectURL(blob);
  const a = document.createElement('a'); a.href=url;
  a.download=(response.headers.get('Content-Disposition') || '').match(/filename="?([^";]+)"?/)?.[1] || 'SPVI';
  document.body.append(a); a.click(); a.remove(); setTimeout(()=>URL.revokeObjectURL(url), 30000);
}
const formats = {inventario:['pdf','xlsx'],turnos:['pdf','xlsx'],servicios:['pdf','xlsx','imagen','tarjetas'],productos:['imagen','tarjetas']};
const formatNames = {pdf:'▤ PDF',xlsx:'▦ Excel',imagen:'▧ Imagen',tarjetas:'▣ Tarjetas promocionales'};
function exportFormats() {
  $('#export-format').replaceChildren(...formats[$('#export-scope').value].map(value=>new Option(formatNames[value],value)));
  promotionField();
}
function promotionField() { $('#promotion-comment').hidden=$('#export-format').value !== 'tarjetas'; }
$('#export-scope')?.addEventListener('change',exportFormats);
$('#export-format')?.addEventListener('change',promotionField);
$('#export-form')?.addEventListener('submit',async e=>{
  e.preventDefault(); const button=e.target.querySelector('button');button.disabled=true;
  try {await download('/api/export',{...Object.fromEntries(new FormData(e.target)),query:$('#search').value});message('Archivo preparado.');}
  catch(error){message(error.message);}finally{button.disabled=false;}
});
if($('#export-scope')) exportFormats();

$('#backup-form')?.addEventListener('submit',async e=>{
  e.preventDefault(); const button=e.target.querySelector('button');button.disabled=true;
  try {await download('/api/backup',Object.fromEntries(new FormData(e.target)));e.target.reset();message('Respaldo cifrado preparado.');}
  catch(error){message(error.message);}finally{button.disabled=false;}
});
$('#restore-form')?.addEventListener('submit',async e=>{
  e.preventDefault(); const button=e.target.querySelector('button');button.disabled=true;
  try {
    const response=await fetch('/api/restore',{method:'POST',headers:{'X-CSRF-Token':$('meta[name=csrf]').content},body:new FormData(e.target)});
    const data=await response.json().catch(()=>({}));if(!response.ok) throw new Error(data.error || 'No se pudo restaurar.');
    e.target.reset();await refresh();message('Respaldo restaurado.');
  }catch(error){message(error.message);}finally{button.disabled=false;}
});

if($('#metrics'))new ResizeObserver(entries=>document.documentElement.style.setProperty('--header-height',entries[0].target.getBoundingClientRect().height+'px')).observe($('header'));

$('#item-form [name=kind]')?.addEventListener('change',e=>{const service=e.target.value==='servicio';for(const key of ['cost','stock','minimum']){const input=$('#item-form').elements[key];input.readOnly=service;if(service)input.value='0';}});
