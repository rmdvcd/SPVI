'use strict';
// Se reutilizan api(), download(), refresh() y mensajes del módulo de pantalla principal.
let businessData=null, qrUrl=null, cart=[],managementGeneration=0;
const permissionLabels={VENDER_PRODUCTOS:'Vender productos',VENDER_SERVICIOS:'Vender servicios',EDITAR_INVENTARIO:'Editar inventario',CAMBIAR_PRECIOS:'Cambiar precios',EXPORTAR:'Exportar'};
const node=(tag,text)=>{const element=document.createElement(tag);if(text!==undefined)element.textContent=text;return element;};
function actionButton(label,action){const button=node('button',label);button.type='button';button.addEventListener('click',async()=>{button.disabled=true;try{await action();}catch(e){message(e.message);}finally{button.disabled=false;}});return button;}
function minor(value,digits=2){const text=String(value).trim();if(!new RegExp('^-?\\d+(\\.\\d{1,'+digits+'})?$').test(text))throw new Error('Número no válido.');const negative=text.startsWith('-');const [a,b='']=text.replace('-','').split('.');const result=Number(a)*10**digits+Number(b.padEnd(digits,'0'));if(!Number.isSafeInteger(result))throw new Error('Número demasiado grande.');return negative?-result:result;}
function bind(id,callback){$(id)?.addEventListener('submit',async e=>{e.preventDefault();const button=e.target.querySelector('button:not([type]),button[type=submit]');button.disabled=true;try{await callback(Object.fromEntries(new FormData(e.target)),e.target);}catch(error){const dialog=e.target.closest('dialog');if(dialog){let problem=dialog.querySelector('.dialog-error');if(!problem){problem=node('p');problem.className='dialog-error';problem.setAttribute('role','alert');dialog.append(problem);}problem.textContent=error.message;}else message(error.message);}finally{button.disabled=false;}});}
async function managementRefresh(){
  const generation=++managementGeneration;
  const [data,license,network,employees]=await Promise.all([api('/api/records'),api('/api/license'),api('/api/network'),api('/api/employees')]);
  if(generation!==managementGeneration)return;
  if(businessData && businessData.business_id!==data.business_id){delete $('#profile-form').dataset.loaded;delete $('#preferences-form').dataset.loaded;}
  businessData=data;
  $('#license-status').textContent=license.clase+' · '+license.secundarias+' secundarias'+(license.venceEn?' · Hasta '+new Date(license.venceEn).toLocaleDateString():'')+(license.motivo?' · '+license.motivo:'');
  $('#network-status').textContent=network.running?'Principal activa en '+network.address.join(':')+' · '+network.connected.length+' conectadas':'Red detenida';
  if(network.last_error)$('#network-status').textContent+=' · '+network.errors+' intentos rechazados; último: '+network.last_error.category+' ('+new Date(network.last_error.at).toLocaleString()+'). Los lotes no confirmados permanecen en el móvil.';
  if(document.activeElement!==$('#business-name [name=name]'))$('#business-name [name=name]').value=data.name;
  renderEmployees(employees,network.connected);renderPayments();renderSupplies();renderPrices();renderHistory();renderArchived();renderSettings();renderLedger();
  renderClients();
  rows('#transfers-history',data.ventas.filter(s=>s.transaccion).map(s=>[new Date(s.fecha).toLocaleString(),s.transaccion.numero,cup(s.transaccion.importeCent),s.anuladaEn?'Anulada':'Válida']));
}

async function changed(){await refresh();}
bind('#business-name',async data=>{await api('/api/settings',data);message('Nombre guardado.');});
bind('#license-request',async data=>{data.secundarias=Number(data.secundarias);if(!data.recupera)delete data.recupera;$('#license-code').value=(await api('/api/license/request',data)).code;message('Solicitud preparada. Envíala al desarrollador.');});
$('#copy-license')?.addEventListener('click',async()=>{try{await navigator.clipboard.writeText($('#license-code').value);message('Solicitud copiada.');}catch{message('Selecciona el texto y cópialo manualmente.');}});
bind('#license-activate',async(data,form)=>{await api('/api/license/activate',data);form.reset();await changed();message('Licencia revisada.');});
bind('#revocations',async(_,form)=>{const file=form.elements.file.files[0];if(file.size>524288)throw new Error('Archivo demasiado grande.');await api('/api/license/revocations',JSON.parse(await file.text()));await changed();});
bind('#network-start',async data=>{await api('/api/network',{action:'start',host:data.host,port:Number(data.port)});await managementRefresh();});
$('#network-stop')?.addEventListener('click',async()=>{try{await api('/api/network',{action:'stop'});await managementRefresh();}catch(e){message(e.message);}});
bind('#employee-create',async(data,form)=>{await api('/api/employees',{action:'create',name:data.name});form.reset();await managementRefresh();});
function renderEmployees(employees,connected){
  const container=$('#employees');container.replaceChildren();
  for(const employee of employees){
    const section=node('section');section.append(node('h3',employee.name),node('p',!employee.active?'Desvinculado':connected.includes(employee.id)?'Conectado':employee.linked?'Sin conexión':'Pendiente de vincular'));
    if(employee.phone)section.append(node('p','Teléfono declarado por la secundaria: '+employee.phone+' (prioridad para recibir el aviso de cobro)'));
    if(employee.version)section.append(node('p','Versión de la secundaria: '+employee.version));
    if(employee.last_sync)section.append(node('p','Última sincronización: '+new Date(employee.last_sync).toLocaleString()));
    if(employee.active){
      const permissionGroup=node('div');permissionGroup.className='permission-group';
      for(const [value,label] of Object.entries(permissionLabels)){const wrapper=node('label',label);const input=node('input');input.type='checkbox';input.value=value;input.checked=employee.permissions.includes(value);wrapper.prepend(input);permissionGroup.append(wrapper);}
      section.append(permissionGroup,actionButton('Guardar permisos',async()=>{await api('/api/employees',{action:'permissions',id:employee.id,permissions:[...permissionGroup.querySelectorAll('input:checked')].map(x=>x.value)});message('Permisos guardados.');}),
        actionButton('Mostrar QR',async()=>{
          const response=await fetch('/api/employees/qr',{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-Token':$('meta[name=csrf]').content},body:JSON.stringify({id:employee.id})});
          if(!response.ok)throw new Error((await response.json()).error || 'No se pudo generar el QR.');
          if(qrUrl)URL.revokeObjectURL(qrUrl);qrUrl=URL.createObjectURL(await response.blob());$('#employee-qr').src=qrUrl;$('#qr-dialog').showModal();
        }));
      const accounts=businessData.perfil || {};const assignments={};
      for(const [kind,key,title] of [['tarjetas','card_id','Tarjeta'],['telefonos','phone_id','Teléfono']]){
        const label=node('label',title+' asignado');const select=node('select');select.append(new Option('Usar selección general',''));
        for(const account of accounts[kind] || [])select.append(new Option(kind==='tarjetas'?'•••• '+account.numero.slice(-4):account.numero,account.id));
        select.value=employee[key] ?? '';assignments[key]=select;label.append(select);section.append(label);
      }
      section.append(actionButton('Guardar cuentas de cobro',async()=>{await api('/api/employees',{action:'payment',id:employee.id,card_id:assignments.card_id.value?Number(assignments.card_id.value):null,phone_id:assignments.phone_id.value?Number(assignments.phone_id.value):null});message('Cuentas asignadas.');}));
      const amount=node('input');amount.type='number';amount.min='0';amount.step='0.01';amount.setAttribute('aria-label','Fondo para '+employee.name);amount.value=String((employee.fund||0)/100);
      section.append(node('p',employee.requests_fund?'Solicita fondo para abrir turno':'Fondo para el próximo turno'),amount,
        actionButton('Asignar fondo',async()=>{await api('/api/employees',{action:'fund',id:employee.id,amount:amount.value});await managementRefresh();}),
        actionButton('Pedir cierre de turno',async()=>{await api('/api/employees',{action:'close',id:employee.id});await managementRefresh();}));
      if(employee.requests_close)section.append(node('p','Solicita cerrar el turno'),actionButton('Aprobar cierre',async()=>{await api('/api/employees',{action:'close',id:employee.id});await managementRefresh();}),actionButton('Rechazar cierre',async()=>{await api('/api/employees',{action:'reject-close',id:employee.id});await managementRefresh();}));
      section.append(actionButton('Desvincular',async()=>{if(!confirm('¿Desvincular a '+employee.name+'? Sincroniza primero sus ventas pendientes.'))return;await api('/api/employees',{action:'revoke',id:employee.id});await managementRefresh();}));
    }
    container.append(section);
  }
}
$('#qr-close')?.addEventListener('click',()=>$('#qr-dialog').close());
$('#qr-dialog')?.addEventListener('close',()=>{if(qrUrl){URL.revokeObjectURL(qrUrl);qrUrl=null;}$('#employee-qr').removeAttribute('src');});
function profile(){return structuredClone(businessData.perfil || {nombre:'',apellidos:'',ci:'',telefonos:[],tarjetas:[]});}
function renderPayments(){
  const container=$('#payment-accounts');container.replaceChildren();const p=profile();
  for(const [kind,title,key] of [['telefonos','Teléfonos','pagoTelefonoId'],['tarjetas','Tarjetas','pagoTarjetaId']]){
    container.append(node('h3',title));
    for(const item of p[kind] || []){
      const row=node('div');row.className='account-row';const label=node('label');const input=node('input');input.type='checkbox';input.dataset.accountKind=kind;input.checked=p[key]===item.id;
      label.append(input,paymentIcon(kind),node('span',(kind==='tarjetas'?'•••• '+item.numero.slice(-4):item.numero)+(item.alias?' · '+item.alias:'')));
      input.addEventListener('change',async()=>{const peers=[...container.querySelectorAll('input[type=checkbox]')].filter(x=>x.dataset.accountKind===kind);if(input.checked)for(const other of peers)if(other!==input)other.checked=false;for(const other of peers)other.disabled=true;try{const next=profile();next[key]=input.checked?item.id:null;await api('/api/settings',{perfil:next});await managementRefresh();}catch(e){message(e.message);renderPayments();}});
      row.append(label,actionButton('Editar',async()=>{const number=prompt('Número',item.numero);if(number===null)return;const next=profile();next[kind]=next[kind].map(x=>x.id===item.id?{...x,numero:number}:x);await api('/api/settings',{perfil:next});await managementRefresh();}),actionButton('Eliminar',async()=>{if(!confirm('¿Eliminar esta cuenta de cobro?'))return;const next=profile();next[kind]=next[kind].filter(x=>x.id!==item.id);if(next[key]===item.id)next[key]=null;await api('/api/settings',{perfil:next});await managementRefresh();}));container.append(row);
    }
  }
}
bind('#payment-add',async(data,form)=>{const p=profile();const list=p[data.kind] || [];list.push({id:Math.max(0,...list.map(x=>x.id))+1,numero:data.number,alias:data.alias || null});p[data.kind]=list;await api('/api/settings',{perfil:p});form.reset();await managementRefresh();});
function renderSupplies(){
  const list=$('#supplies');list.replaceChildren();for(const item of businessData.insumos){const row=node('p',item.nombre+' · '+item.cantidadMil/1000+' '+item.unidad+' ');row.append(actionButton('Editar',async()=>{openMetadata('insumos',item.id);}),actionButton('Eliminar',async()=>{if(confirm('¿Eliminar el insumo?')){await api('/api/catalog',{t:'insumo_eliminar',id:item.id});await changed();}}));list.append(row);}
  const select=$('#recipe-item'),previous=select.value;select.replaceChildren();for(const [kind,items] of [['productos',businessData.productos],['servicios',businessData.servicios]])for(const item of items.filter(x=>!x.eliminado))select.append(new Option(item.nombre,kind+':'+item.id));if([...select.options].some(x=>x.value===previous))select.value=previous;
  if(select.value!==previous || !select.dataset.loaded){select.dataset.loaded='true';select.dispatchEvent(new Event('change'));}
}
bind('#supply-form',async(data,form)=>{const old=businessData.insumos.find(x=>x.id===Number(data.id));const now=Date.now();await api('/api/catalog',{t:'insumo_guardar',insumo:{...(old||{}),id:Number(data.id),nombre:data.name,unidad:data.unit,precioCent:minor(data.cost),cantidadMil:minor(data.quantity,3),creadoEn:old?.creadoEn || now,actualizadoEn:now},cantidadLeidaMil:old?.cantidadMil ?? null});form.reset();form.elements.id.value='0';await changed();});
function recipeLine(value){const row=node('div');row.className='pair';const select=node('select');select.setAttribute('aria-label','Insumo');for(const x of businessData.insumos)select.append(new Option(x.nombre,x.id));if(value)select.value=value.insumoId;const input=node('input');input.type='number';input.min='0.001';input.step='0.001';input.required=true;input.value=value?value.cantidadMil/1000:1;input.setAttribute('aria-label','Cantidad por unidad');row.append(select,input,actionButton('Quitar',async()=>row.remove()));$('#recipe-lines').append(row);}
$('#recipe-add')?.addEventListener('click',()=>recipeLine());
$('#recipe-item')?.addEventListener('change',()=>{const [kind,id]=$('#recipe-item').value.split(':');const lines=kind==='productos'?(businessData.recetas.find(x=>x.productoId===Number(id))?.lineas || []):(businessData.servicios.find(x=>x.id===Number(id))?.insumos || []);$('#recipe-lines').replaceChildren();lines.forEach(recipeLine);});
bind('#recipe-form',async data=>{const [kind,raw]=data.item.split(':'),id=Number(raw);const lines=[...$('#recipe-lines').children].map(row=>({insumoId:Number(row.querySelector('select').value),cantidadMil:minor(row.querySelector('input').value,3)}));const item=structuredClone(businessData[kind].find(x=>x.id===id));if(kind==='productos'){item.categoria='Elaborado';await api('/api/catalog',{t:'producto_guardar',producto:item,receta:{productoId:id,lineas:lines},cantidadLeida:item.cantidad});}else{item.insumos=lines;await api('/api/catalog',{t:'servicio_guardar',servicio:item});}await changed();message('Receta guardada.');});
function renderPrices(){const select=$('#price-products');const chosen=[...select.selectedOptions].map(x=>x.value);select.replaceChildren();for(const product of businessData.productos.filter(x=>!x.eliminado)){const option=new Option(product.nombre,product.id);option.selected=chosen.includes(option.value);select.append(option);}const list=$('#price-rules');list.replaceChildren();for(const item of businessData.preajustes){const row=node('p',item.nombre+' · '+item.puntosBasicos/100+'% ');row.append(actionButton('Editar',async()=>{const form=$('#price-rule');form.elements.id.value=item.id;form.elements.name.value=item.nombre;form.elements.percent.value=item.puntosBasicos/100;form.elements.method.value=item.metodoPago || '';form.elements.minimum.value=(item.importeMinimoCent || 0)/100;for(const option of $('#price-products').options)option.selected=item.productoIds.includes(Number(option.value));form.scrollIntoView({block:'center'});}),actionButton(item.activo===false?'Activar':'Desactivar',async()=>{await api('/api/catalog',{t:'preajuste_guardar',preajuste:{...item,activo:item.activo===false}});await changed();}),actionButton('Eliminar',async()=>{await api('/api/catalog',{t:'preajuste_eliminar',id:item.id});await changed();}));list.append(row);}}
bind('#price-rule',async(data,form)=>{await api('/api/catalog',{t:'preajuste_guardar',preajuste:{id:Number(data.id),nombre:data.name,puntosBasicos:minor(data.percent),productoIds:[...$('#price-products').selectedOptions].map(x=>Number(x.value)),metodoPago:data.method || null,importeMinimoCent:minor(data.minimum),activo:true}});form.reset();await changed();});
function renderHistory(){const list=$('#sales-history');list.replaceChildren();for(const sale of [...businessData.ventas].sort((a,b)=>b.fecha-a.fecha).filter(s=>s.detalles.some(d=>d.nombre.toLocaleLowerCase().includes($('#history-search').value.toLocaleLowerCase()))).slice(0,historyLimit)){const row=node('p',new Date(sale.fecha).toLocaleString()+' · '+sale.detalles.map(x=>x.nombre+' ×'+x.cantidad).join(', ')+' · '+(sale.anuladaEn?'Anulada':cup(sale.detalles.reduce((a,d)=>a+d.precioUnitarioCent*d.cantidad,0))));const turn=businessData.turnos.find(x=>x.id===sale.turnoId);if(!sale.anuladaEn && turn && turn.cerradoEn==null)row.append(actionButton('Corregir',async()=>startCorrection(sale)),actionButton('Anular',async()=>{const reason=prompt('Motivo de la anulación');if(!reason)return;await api('/api/void',{id:sale.id,reason});await changed();}));list.append(row);}}
window.addEventListener('spvi-catalog',()=>{
  if(!snapshot)return;const visible=snapshot.items.filter(x=>x.name.toLocaleLowerCase().includes($('#search').value.toLocaleLowerCase()));
  [...$('#items').children].forEach((row,index)=>{const item=visible[index];const cell=node('td');cell.append(actionButton('Editar',async()=>{openMetadata(item.kind==='producto'?'productos':item.kind==='insumo'?'insumos':'servicios',item.domain_id);}),actionButton('Eliminar',async()=>{if(confirm('¿Eliminar '+item.name+' del catálogo?')){if(item.kind==='insumo')await api('/api/catalog',{t:'insumo_eliminar',id:item.domain_id});else await api('/api/item-delete',{item_id:item.id});await changed();}}));row.append(cell);});
});
$('#item-new')?.addEventListener('click',()=>{const form=$('#item-form');form.reset();form.dataset.action='item';for(const key of ['cost','stock','minimum'])form.elements[key].readOnly=false;});
function renderCart(){const list=$('#cart-lines');list.replaceChildren();cart.forEach((line,index)=>{const row=node('p',line.name+' ×'+line.quantity);row.append(actionButton('Quitar',async()=>{cart.splice(index,1);renderCart();}));list.append(row);});quoteCart();}
$('#cart-add')?.addEventListener('click',()=>{const f=$('#sale-form');const id=Number(f.elements.item_id.value),quantity=Number(f.elements.quantity.value);const item=snapshot.items.find(x=>x.id===id);if(!item || !Number.isSafeInteger(quantity) || quantity<1){message('Selecciona artículo y cantidad.');return;}cart.push({item_id:id,quantity,name:item.name});renderCart();});
bind('#sale-form',async(data,form)=>{
  const lines=cart.length?cart.map(({item_id,quantity})=>({item_id,quantity})):[{item_id:Number(data.item_id),quantity:Number(data.quantity)}];
  const payload={lines,method:data.method};
  if(data.method==='transferencia')payload.transaction={numero:data.numero,clienteNombre:data.clienteNombre,clienteCi:data.clienteCi,clienteTelefono:data.clienteTelefono,clienteFijo:form.elements.clienteFijo.checked};
  if(data.correction){payload.id=Number(data.correction);payload.reason=data.reason;}
  await api(data.correction?'/api/correct':'/api/cart',payload);
  cart=[];renderCart();form.reset();saleFields();$('#sale-dialog').close();await changed();message('Venta registrada.');
});

bind('#android-export',async(data,form)=>{await download('/api/android/export',data);form.reset();});
bind('#android-import',async(_,form)=>{const response=await fetch('/api/android/import',{method:'POST',headers:{'X-CSRF-Token':$('meta[name=csrf]').content},body:new FormData(form)});const result=await response.json().catch(()=>({}));if(!response.ok)throw new Error(result.error || 'No se pudo restaurar.');form.reset();await changed();message('Datos restaurados. Vuelve a vincular las secundarias.');});


// Edición por DTO: mantiene recetas y demás campos no presentes en este formulario.
let metadataTarget=null;
const metadataFields={
 productos:[['nombre','Nombre'],['categoria','Categoría'],['descripcion','Descripción'],['fechaCaducidad','Caducidad','date'],['precioCostoCent','Costo CUP','money'],['precioVentaCent','Venta CUP','money'],['cantidad','Existencias','integer'],['nivelBajo','Umbral bajo','integer'],['nivelCritico','Umbral crítico','integer']],
 servicios:[['nombre','Nombre'],['tipo','Tipo de servicio'],['descripcion','Descripción'],['importeCent','Importe CUP','money']],
 insumos:[['nombre','Nombre'],['unidad','Unidad'],['precioCent','Costo CUP','money'],['precioVentaCent','Venta CUP (vacío: no vender)','money'],['cantidadMil','Existencias','milli'],['nivelBajoMil','Umbral bajo','milli'],['nivelCriticoMil','Umbral crítico','milli']]
};
function openMetadata(kind,id){
 const item=structuredClone(businessData[kind].find(x=>x.id===id));if(!item)throw new Error('Actualiza el catálogo e inténtalo de nuevo.');
 metadataTarget={kind,id,item};const form=$('#metadata-form');form.replaceChildren();
 for(const [key,title,type] of metadataFields[kind]){
  const label=node('label',title),input=key==='unidad'?node('select'):node('input');input.name=key;
  if(key==='unidad')for(const unit of ['UNIDAD','KILOGRAMO','GRAMO','LITRO','MILILITRO'])input.append(new Option(unit,unit));
  else {input.type=type==='date'?'date':['money','milli','integer'].includes(type)?'number':'text';input.maxLength=key==='descripcion'?4096:120;if(input.type==='number'){input.min='0';input.step=type==='money'?'0.01':type==='milli'?'0.001':'1';}}
  input.value=item[key]==null?'':type==='money'?item[key]/100:type==='milli'?item[key]/1000:item[key];
  input.required=['nombre','categoria','tipo','unidad','precioCostoCent','importeCent','precioCent','cantidad','cantidadMil'].includes(key) || kind==='productos' && key==='precioVentaCent';label.append(input);form.append(label);
 }
 const button=node('button','Guardar artículo');form.append(button);$('#photo-form').hidden=kind==='insumos';$('#photo-preview').hidden=true;
 if(kind!=='insumos'){const image=$('#photo-preview');image.onload=()=>image.hidden=false;image.onerror=()=>image.hidden=true;image.src='/api/photo/'+kind+'/'+id+'?v='+Date.now();}
 $('#metadata-dialog').querySelector('.dialog-error')?.remove();$('#metadata-dialog').showModal();
}
bind('#metadata-form',async(data)=>{
 const {kind,id,item}=metadataTarget;const next=structuredClone(item);
 for(const [key,,type] of metadataFields[kind])next[key]=data[key]===''?null:type==='money'?minor(data[key]):type==='milli'?minor(data[key],3):type==='integer'?Number(data[key]):data[key];
 const singular={productos:'producto',servicios:'servicio',insumos:'insumo'}[kind];
 const payload={t:singular+'_guardar',[singular]:next};if(kind==='productos')payload.cantidadLeida=item.cantidad;if(kind==='insumos')payload.cantidadLeidaMil=item.cantidadMil;
 await api('/api/catalog',payload);$('#metadata-dialog').close();await changed();message('Artículo actualizado.');
});
bind('#photo-form',async(_,form)=>{
 const file=form.elements.photo.files[0];if(file.size>5*1024*1024)throw new Error('La foto supera 5 MB.');
 const content=await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result.split(',')[1]);reader.onerror=()=>reject(new Error('No se pudo leer la foto.'));reader.readAsDataURL(file);});
 await api('/api/photo',{kind:metadataTarget.kind,id:metadataTarget.id,content});form.reset();$('#photo-preview').src='/api/photo/'+metadataTarget.kind+'/'+metadataTarget.id+'?v='+Date.now();message('Foto guardada.');
});
$('#photo-remove').addEventListener('click',async()=>{try{await api('/api/photo',{kind:metadataTarget.kind,id:metadataTarget.id,content:null});$('#photo-preview').hidden=true;message('Foto eliminada.');}catch(e){message(e.message);}});
function renderClients(){
 rows('#fixed-clients',businessData.clientesFijos.map(c=>[c.nombreApellidos,'••••••••'+c.ci.slice(-3),c.telefono]));
 [...$('#fixed-clients').children].forEach((row,index)=>{const client=businessData.clientesFijos[index],cell=node('td');cell.append(actionButton('Editar',async()=>{const f=$('#client-form');f.elements.ci.value=client.ci;f.elements.name.value=client.nombreApellidos;f.elements.phone.value=client.telefono;f.scrollIntoView({block:'center'});}),actionButton('Eliminar',async()=>{if(confirm('¿Eliminar cliente fijo? No se borrarán sus ventas.')){await api('/api/client',{ci:client.ci,delete:true});await changed();}}));row.append(cell);});
}
bind('#client-form',async(data,form)=>{await api('/api/client',data);form.reset();await changed();message('Cliente guardado.');});
function saleFields(){const f=$('#sale-form'),transfer=f.elements.method.value==='transferencia';$('#transfer-fields').hidden=!transfer;f.elements.numero.required=transfer;$('#correction-reason').hidden=!f.elements.correction.value;f.elements.reason.required=!!f.elements.correction.value;}
$('#sale-form [name=method]').addEventListener('change',()=>{saleFields();quoteCart();});
$('#sale-open').addEventListener('click',()=>{const f=$('#sale-form');f.reset();cart=[];renderCart();saleFields();});
function startCorrection(sale){
 const f=$('#sale-form');f.reset();f.elements.correction.value=sale.id;f.elements.method.value=sale.metodoPago.toLowerCase();
 cart=sale.detalles.map(line=>{const kind={PRODUCTO:'producto',SERVICIO:'servicio',INSUMO:'insumo'}[line.clase || 'PRODUCTO'];const item=snapshot.items.find(x=>x.kind===kind && x.domain_id===line.productoId);if(!item)throw new Error('Un artículo fue eliminado. Restáuralo en el catálogo antes de corregir esta venta.');return {item_id:item.id,name:line.nombre,quantity:line.cantidad};});
 if(sale.transaccion){for(const key of ['numero','clienteNombre','clienteCi','clienteTelefono'])f.elements[key].value=sale.transaccion[key] || '';f.elements.clienteFijo.checked=sale.transaccion.clienteFijo;}
 renderCart();saleFields();$('#sale-dialog').showModal();
}
$('#sale-form [name=clienteNombre]').addEventListener('input',e=>{
 const options=$('#client-suggestions');options.replaceChildren();const value=e.target.value.toLocaleLowerCase();
 const matches=businessData.clientesFijos.filter(c=>c.nombreApellidos.toLocaleLowerCase().startsWith(value)).slice(0,3);
 for(const c of matches)options.append(new Option('CI ••••••••'+c.ci.slice(-3),c.nombreApellidos));
 const exact=businessData.clientesFijos.filter(c=>c.nombreApellidos===e.target.value);
 if(exact.length===1){const f=$('#sale-form');f.elements.clienteCi.value=exact[0].ci;f.elements.clienteTelefono.value=exact[0].telefono;f.elements.clienteFijo.checked=true;}
});

function renderArchived(){const container=$('#archived-items');container.replaceChildren();for(const kind of ['productos','servicios'])for(const item of businessData[kind].filter(x=>x.eliminado)){const row=node('p',item.nombre+' ');row.append(actionButton('Restaurar artículo',async()=>{const singular=kind==='productos'?'producto':'servicio';await api('/api/catalog',{t:singular+'_guardar',[singular]:{...item,eliminado:false}});await changed();}));container.append(row);}}

let historyLimit=100,ledgerLimit=100;
$('#history-more').addEventListener('click',()=>{historyLimit+=100;renderHistory();});
$('#history-search').addEventListener('input',()=>{historyLimit=100;renderHistory();});
$('#ledger-more').addEventListener('click',()=>{ledgerLimit+=100;renderLedger();});
$('#ledger-kind').addEventListener('change',()=>{ledgerLimit=100;renderLedger();});
function renderLedger(){
 const kind=$('#ledger-kind').value,head=$('#ledger-head');head.replaceChildren();const header=node('tr');
 const columns=kind==='turnos'?['Turno','Apertura','Cierre','Fondo','Contado']:kind==='caja'?['Fecha','Tipo','Importe','Motivo','Turno']:['Fecha','Tipo','Artículo','Variación','Existencia resultante','Turno','Nota'];
 for(const label of columns)header.append(node('th',label));head.append(header);
 const entries=[...businessData[kind]].sort((a,b)=>(b.fecha || b.abiertoEn)-(a.fecha || a.abiertoEn)).slice(0,ledgerLimit);
 rows('#ledger-body',entries.map(x=>kind==='turnos'?[x.id,new Date(x.abiertoEn).toLocaleString(),x.cerradoEn?new Date(x.cerradoEn).toLocaleString():'Abierto',cup(x.fondoCent || 0),x.contadoCent==null?'Sin arqueo':cup(x.contadoCent)]:kind==='caja'?[new Date(x.fecha).toLocaleString(),x.tipo,cup(x.importeCent),x.motivo,x.turnoId]:[new Date(x.fecha).toLocaleString(),x.tipo,x.nombre,x.entidad==='INSUMO'?x.delta/1000:x.delta,x.entidad==='INSUMO'?x.existencia/1000:x.existencia,x.turnoId || '—',x.nota || '']));
 $('#ledger-more').hidden=entries.length>=businessData[kind].length;
}
function renderSettings(){
 const form=$('#profile-form');if(!form.dataset.loaded){const value=profile();for(const key of ['nombre','apellidos','ci'])form.elements[key].value=value[key] || '';form.dataset.loaded='true';}
 const preferences=$('#preferences-form');if(!preferences.dataset.loaded){const value=businessData.preferencias || {};for(const key of ['productoBajo','productoCritico'])preferences.elements[key].value=value[key] ?? (key==='productoBajo'?5:1);preferences.elements.insumoBajo.value=(value.insumoBajoMil ?? 5000)/1000;preferences.elements.insumoCritico.value=(value.insumoCriticoMil ?? 1000)/1000;preferences.dataset.loaded='true';}
}
bind('#profile-form',async data=>{await api('/api/settings',{perfil:{...profile(),...data}});await changed();message('Perfil guardado.');});
bind('#preferences-form',async data=>{await api('/api/settings',{preferencias:{...(businessData.preferencias || {}),productoBajo:Number(data.productoBajo),productoCritico:Number(data.productoCritico),insumoBajoMil:minor(data.insumoBajo,3),insumoCriticoMil:minor(data.insumoCritico,3)}});await changed();message('Avisos guardados.');});

let quoteGeneration=0;
async function quoteCart(){
 if(!snapshot || !$('#sale-dialog'))return;const generation=++quoteGeneration,f=$('#sale-form');
 const lines=cart.length?cart.map(({item_id,quantity})=>({item_id,quantity})):[{item_id:Number(f.elements.item_id.value),quantity:Number(f.elements.quantity.value)}];
 const payload={lines,method:f.elements.method.value};if(f.elements.correction.value)payload.id=Number(f.elements.correction.value);
 try{const quote=await api('/api/quote',payload);if(generation===quoteGeneration)$('#cart-total').textContent='Total con ajustes: '+cup(quote.total);}
 catch(e){if(generation===quoteGeneration)$('#cart-total').textContent=e.message;}
}
for(const name of ['quantity','item_id'])$('#sale-form').elements[name].addEventListener('change',quoteCart);
managementRefresh().catch(e=>message(e.message));

function paymentIcon(kind){const ns='http://www.w3.org/2000/svg',svg=document.createElementNS(ns,'svg');svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('width','24');svg.setAttribute('height','24');svg.setAttribute('fill','none');svg.setAttribute('stroke','currentColor');svg.setAttribute('stroke-width','1.8');svg.setAttribute('aria-hidden','true');const frame=document.createElementNS(ns,'rect');for(const [key,value] of Object.entries(kind==='tarjetas'?{x:2,y:5,width:20,height:14,rx:3}:{x:6,y:2,width:12,height:20,rx:3}))frame.setAttribute(key,String(value));const line=document.createElementNS(ns,'path');line.setAttribute('d',kind==='tarjetas'?'M2 10h20M6 15h4':'M10 18h4');svg.append(frame,line);return svg;}
