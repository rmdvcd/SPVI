'use strict';
/* Ajustes: negocio, licencia, pago electrónico, precios, avisos, respaldo, clave de acceso, inicio con Windows y tema. */

const TIPOS_LICENCIA = {MENSUAL: ['Mensual', 600000, 100000], SEMESTRAL: ['Semestral', 3000000, 500000], ANUAL: ['Anual', 5000000, 900000], PERPETUA: ['Perpetua', 9000000, 1700000]};
const RETRASOS = [[0, 'Sin retraso'], [15, '15 segundos'], [30, '30 segundos'], [60, '1 minuto'], [120, '2 minutos'], [300, '5 minutos']];

SPVI.views.ajustes = async function (root, sub) {
  const [catalogo, licencia, app] = await Promise.all([SPVI.load.catalogo(), SPVI.load.licencia(), SPVI.load.app()]);
  const secciones = [
    {id: 'negocio', titulo: 'Negocio y propietario', detalle: catalogo.name, icono: 'Perfil'},
    {id: 'licencia', titulo: 'Licencia', detalle: {PRUEBA: 'Periodo de prueba', ACTIVA: 'Activa', PERPETUA: 'Perpetua', BLOQUEADA: 'Sin licencia vigente'}[licencia.clase], icono: 'Licencia'},
    {id: 'pago', titulo: 'Pago electrónico', detalle: 'Tarjetas y teléfonos para cobrar por transferencia', icono: 'PagoElectronico'},
    {id: 'precios', titulo: 'Ajustes de precio', detalle: 'Descuentos y recargos automáticos', icono: 'Precios'},
    {id: 'avisos', titulo: 'Avisos de inventario', detalle: 'Umbrales de existencias bajas y críticas', icono: 'Alerta'},
    {id: 'respaldo', titulo: 'Respaldo', detalle: 'Copias de seguridad y archivos de la app móvil', icono: 'Respaldo'},
    {id: 'acceso', titulo: 'Clave de acceso', detalle: app.protegida ? 'Activada' : 'Sin clave', icono: 'Bloqueo'},
    {id: 'inicio', titulo: 'Inicio de SPVI', detalle: app.preferencias.inicioConWindows ? 'Se inicia con Windows' : 'Inicio manual', icono: 'Reloj'},
    {id: 'tema', titulo: 'Tema', detalle: {claro: 'Claro', oscuro: 'Oscuro', sistema: 'Según el sistema'}[app.preferencias.tema], icono: 'TemaOscuro'},
  ];
  if (app.dev) secciones.push({id: 'prueba', titulo: 'Datos de prueba', detalle: 'Solo en modo desarrollo', icono: 'Desarrollador'});
  const seccion = secciones.find(s => s.id === sub);
  if (!seccion) {
    root.append(h('div', {class: 'encabezado'}, h('h1', {text: 'Ajustes'})),
      h('div', {class: 'lista'}, secciones.map(s => itemLista({title: s.titulo, subtitle: s.detalle, lead: leadIcon(s.icono), onclick: () => SPVI.go('ajustes/' + s.id), trailing: icon('Siguiente')}))));
    if (app.controles.length) root.append(h('div', {class: 'fila'},
      app.controles.includes('restart') ? button('Reiniciar SPVI', reiniciar, {kind: 'borde', iconName: 'Reiniciar'}) : null,
      app.controles.includes('quit') ? button('Salir de SPVI', salir, {kind: 'texto', iconName: 'Apagar'}) : null));
    return;
  }
  root.append(h('div', {class: 'encabezado'}, iconButton('AtrasPagina', 'Volver a Ajustes', () => SPVI.go('ajustes')), h('h1', {text: seccion.titulo})));
  const perfil = () => structuredClone(catalogo.perfil || {nombre: '', apellidos: '', ci: '', telefonos: [], tarjetas: []});
  const guardarPerfil = async p => { await api('/api/settings', {perfil: p}); };

  if (sub === 'negocio') {
    const p = perfil();
    const form = h('form', {class: 'card form'},
      campo({label: 'Nombre del negocio', name: 'negocio', required: true, maxlength: 120, value: catalogo.name || ''}),
      h('div', {class: 'par'}, campo({label: 'Nombre del propietario', name: 'nombre', maxlength: 120, value: p.nombre || ''}), campo({label: 'Apellidos', name: 'apellidos', maxlength: 120, value: p.apellidos || ''})),
      campo({label: 'Carné de identidad', name: 'ci', maxlength: 20, inputmode: 'numeric', value: p.ci || ''}),
      h('div', {class: 'fila fin'}, button('Guardar', null, {type: 'submit', iconName: 'Guardar'})));
    form.addEventListener('submit', event => { event.preventDefault(); busy(form.querySelector('button[type=submit]'), async () => {
      const data = Object.fromEntries(new FormData(form));
      await api('/api/settings', {name: data.negocio}); await guardarPerfil({...perfil(), nombre: data.nombre.trim(), apellidos: data.apellidos.trim(), ci: data.ci.trim()});
      snack('Datos guardados.'); await SPVI.changed();
    }); });
    root.append(form);
  }

  if (sub === 'licencia') vistaLicencia(root, catalogo, licencia);

  if (sub === 'pago') {
    const p = perfil();
    for (const [clave, titulo, seleccionada, iconName] of [['tarjetas', 'Tarjetas', 'pagoTarjetaId', 'Transferencia'], ['telefonos', 'Teléfonos', 'pagoTelefonoId', 'Telefono']]) {
      const lista = p[clave] || [];
      root.append(h('div', {class: 'encabezado'}, h('h3', {text: titulo}), button('Agregar', () => editarCuenta(clave), {kind: 'texto', iconName: 'Agregar'})),
        lista.length ? h('div', {class: 'lista'}, lista.map(c => itemLista({title: clave === 'tarjetas' ? '•••• •••• •••• ' + c.numero.slice(-4) : c.numero, subtitle: [c.alias, p[seleccionada] === c.id ? 'Predeterminada' : null].filter(Boolean),
          lead: leadIcon(iconName), onclick: () => editarCuenta(clave, c)}))) : h('div', {class: 'card'}, vacio(iconName, 'Sin ' + titulo.toLowerCase() + ' registrados.')));
    }
    function editarCuenta(clave, c = null) {
      const seleccionada = clave === 'tarjetas' ? 'pagoTarjetaId' : 'pagoTelefonoId', p = perfil();
      dialog({title: c ? 'Editar cuenta' : (clave === 'tarjetas' ? 'Nueva tarjeta' : 'Nuevo teléfono'), iconName: 'PagoElectronico', body: [
        campo({label: clave === 'tarjetas' ? 'Número de tarjeta (16 dígitos)' : 'Número de teléfono', name: 'numero', required: true, maxlength: 32, inputmode: 'numeric', value: c?.numero || ''}),
        campo({label: 'Alias (opcional)', name: 'alias', maxlength: 60, value: c?.alias || ''}),
        h('label', {class: 'casilla'}, h('input', {type: 'checkbox', name: 'predeterminada', checked: c ? p[seleccionada] === c.id : !(p[clave] || []).length}), 'Usar por defecto al cobrar'),
      ], actions: [
        ...(c ? [{label: 'Eliminar', icon: 'Eliminar', onClick: async () => {
          const next = perfil(); next[clave] = next[clave].filter(x => x.id !== c.id); if (next[seleccionada] === c.id) next[seleccionada] = null;
          await guardarPerfil(next); snack('Cuenta eliminada.'); await SPVI.changed();
        }}] : []),
        {label: 'Cancelar'},
        {label: 'Guardar', primary: true, icon: 'Guardar', onClick: async ({data, form}) => {
          const next = perfil(); next[clave] = next[clave] || [];
          const id = c?.id || Math.max(0, ...next[clave].map(x => x.id)) + 1;
          const cuenta = {...(c || {}), id, numero: data.numero.replace(/\s|-/g, ''), alias: data.alias.trim() || null};
          next[clave] = c ? next[clave].map(x => x.id === id ? cuenta : x) : [...next[clave], cuenta];
          if (form.elements.predeterminada.checked) next[seleccionada] = id; else if (next[seleccionada] === id) next[seleccionada] = null;
          await guardarPerfil(next); snack('Cuenta guardada.'); await SPVI.changed();
        }},
      ]});
    }
  }

  if (sub === 'precios') {
    const productos = catalogo.productos.filter(x => !x.eliminado).sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
    root.append(h('p', {class: 'secundario', text: 'Cambian el precio al vender según la forma de pago o el importe. Un porcentaje negativo es un descuento.'}),
      catalogo.preajustes.length ? h('div', {class: 'lista'}, catalogo.preajustes.map(r => itemLista({title: r.nombre, lead: leadIcon('Porcentaje'),
        subtitle: [(r.puntosBasicos > 0 ? '+' : '') + r.puntosBasicos / 100 + ' %' + (r.metodoPago ? ' · Solo ' + (r.metodoPago === 'EFECTIVO' ? 'efectivo' : 'transferencia') : '') + (r.importeMinimoCent ? ' · Desde ' + cup(r.importeMinimoCent) : ''),
          r.productoIds.length ? r.productoIds.length + (r.productoIds.length === 1 ? ' producto' : ' productos') : 'Todos los productos'],
        value: r.activo === false ? 'Inactivo' : null, onclick: () => editarRegla(r)}))) : h('div', {class: 'card'}, vacio('Precios', 'Sin ajustes de precio.')),
      h('button', {type: 'button', class: 'fab extendida', onclick: () => editarRegla()}, icon('Agregar'), 'Nuevo ajuste'));
    function editarRegla(r = null) {
      const casillas = h('div', {class: 'lista plana', style: 'max-height:240px;overflow:auto'}, productos.map(p => h('label', {class: 'casilla'}, h('input', {type: 'checkbox', value: p.id, checked: r?.productoIds.includes(p.id)}), p.nombre)));
      dialog({title: r ? 'Editar ajuste' : 'Nuevo ajuste de precio', iconName: 'Precios', wide: true, body: [
        campo({label: 'Nombre', name: 'nombre', required: true, maxlength: 120, value: r?.nombre || ''}),
        h('div', {class: 'form'}, h('div', {class: 'par'},
          campo({label: 'Porcentaje (negativo = descuento)', name: 'porcentaje', type: 'number', step: '0.01', min: -100, required: true, value: r ? r.puntosBasicos / 100 : ''}),
          seleccion({label: 'Forma de pago', name: 'metodo', value: r?.metodoPago || '', options: [['', 'Cualquiera'], ['EFECTIVO', 'Solo efectivo'], ['TRANSFERENCIA', 'Solo transferencia']]}),
          campo({label: 'Importe mínimo (CUP)', name: 'minimo', type: 'number', min: 0, step: '0.01', value: r ? pesos(r.importeMinimoCent || 0) : '0'}))),
        interruptor({label: 'Activo', name: 'activo', checked: r ? r.activo !== false : true}),
        h('span', {class: 'secundario', text: 'Productos (ninguno marcado = todos)'}), casillas,
      ], actions: [
        ...(r ? [{label: 'Eliminar', icon: 'Eliminar', onClick: async () => { await api('/api/catalog', {t: 'preajuste_eliminar', id: r.id}); snack('Ajuste eliminado.'); await SPVI.changed(); }}] : []),
        {label: 'Cancelar'},
        {label: 'Guardar', primary: true, icon: 'Guardar', onClick: async ({data, form}) => {
          await api('/api/catalog', {t: 'preajuste_guardar', preajuste: {...(r || {}), id: r?.id || 0, nombre: data.nombre.trim(), puntosBasicos: minor(data.porcentaje), metodoPago: data.metodo || null,
            importeMinimoCent: minor(data.minimo || '0'), activo: form.elements.activo.checked, productoIds: [...casillas.querySelectorAll('input:checked')].map(x => Number(x.value))}});
          snack('Ajuste guardado.'); await SPVI.changed();
        }},
      ]});
    }
  }

  if (sub === 'avisos') {
    const v = catalogo.preferencias || {};
    const form = h('form', {class: 'card form'},
      h('p', {class: 'secundario', text: 'Se usan cuando el artículo no tiene umbrales propios.'}),
      h('h3', {text: 'Productos (unidades)'}),
      h('div', {class: 'par'}, campo({label: 'Existencias bajas', name: 'productoBajo', type: 'number', min: 0, step: 1, required: true, value: v.productoBajo ?? 5}),
        campo({label: 'Existencias críticas', name: 'productoCritico', type: 'number', min: 0, step: 1, required: true, value: v.productoCritico ?? 1})),
      h('h3', {text: 'Insumos'}),
      h('div', {class: 'par'}, campo({label: 'Existencias bajas', name: 'insumoBajo', type: 'number', min: 0, step: '0.001', required: true, value: (v.insumoBajoMil ?? 5000) / 1000}),
        campo({label: 'Existencias críticas', name: 'insumoCritico', type: 'number', min: 0, step: '0.001', required: true, value: (v.insumoCriticoMil ?? 1000) / 1000})),
      h('div', {class: 'fila fin'}, button('Guardar', null, {type: 'submit', iconName: 'Guardar'})));
    form.addEventListener('submit', event => { event.preventDefault(); busy(form.querySelector('button[type=submit]'), async () => {
      const d = Object.fromEntries(new FormData(form));
      const entero = x => { const n = Number(x); if (!Number.isSafeInteger(n) || n < 0) throw new Error('Usa números enteros para productos.'); return n; };
      await api('/api/settings', {preferencias: {...v, productoBajo: entero(d.productoBajo), productoCritico: entero(d.productoCritico), insumoBajoMil: minor(d.insumoBajo, 3), insumoCriticoMil: minor(d.insumoCritico, 3)}});
      snack('Avisos guardados.'); await SPVI.changed();
    }); });
    root.append(form);
  }

  if (sub === 'respaldo') {
    const archivo = (accept) => h('label', {class: 'campo'}, h('span', {text: 'Archivo'}), h('input', {type: 'file', name: 'file', accept, required: true}));
    const subir = async (path, form) => {
      const response = await fetch(path, {method: 'POST', headers: {'X-CSRF-Token': csrf()}, body: new FormData(form)});
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(result.error || 'No se pudo restaurar.');
    };
    root.append(h('div', {class: 'grid'},
      h('div', {class: 'card'}, h('div', {class: 'cabecera'}, icon('Respaldo'), h('h3', {text: 'Copia de seguridad de esta PC'})),
        h('p', {class: 'secundario', text: 'Archivo .spvidesk cifrado con una contraseña. Guárdalo fuera de esta PC.'}),
        h('div', {class: 'fila'},
          button('Crear copia', () => dialog({title: 'Crear copia de seguridad', iconName: 'Respaldo', body: [
            campo({label: 'Contraseña de la copia (mínimo 12 caracteres)', name: 'password', type: 'password', minlength: 12, maxlength: 256, required: true, autocomplete: 'new-password'}),
            h('p', {class: 'secundario', text: 'Sin esta contraseña no se puede restaurar la copia.'})],
          actions: [{label: 'Cancelar'}, {label: 'Descargar', primary: true, icon: 'Exportar', onClick: async ({data}) => { await download('/api/backup', {password: data.password}); snack('Copia descargada.'); }}]}), {iconName: 'Exportar'}),
          button('Restaurar', () => dialog({title: 'Restaurar copia', iconName: 'Restaurar', body: [
            h('div', {class: 'banner mal'}, icon('Alerta'), h('span', {class: 'texto', text: 'Reemplaza todos los datos actuales de esta PC.'})),
            archivo('.spvidesk'), campo({label: 'Contraseña de la copia', name: 'password', type: 'password', minlength: 12, maxlength: 256, required: true, autocomplete: 'off'}),
            campo({label: 'Escribe RESTAURAR para confirmar', name: 'confirm', required: true, pattern: 'RESTAURAR', autocomplete: 'off'})],
          actions: [{label: 'Cancelar'}, {label: 'Restaurar', primary: true, kind: 'peligro', icon: 'Restaurar', onClick: async ({form}) => { await subir('/api/restore', form); snack('Datos restaurados.'); await SPVI.changed(); }}]}), {kind: 'borde', iconName: 'Restaurar'}))),
      h('div', {class: 'card'}, h('div', {class: 'cabecera'}, icon('Movil'), h('h3', {text: 'Archivo de la app móvil'})),
        h('p', {class: 'secundario', text: 'Archivo .spvi para pasar el negocio entre esta PC y la app móvil principal.'}),
        h('div', {class: 'fila'},
          button('Exportar .spvi', () => dialog({title: 'Exportar a la app móvil', iconName: 'Exportar', body: [
            campo({label: 'Contraseña del archivo (opcional)', name: 'password', type: 'password', maxlength: 256, autocomplete: 'new-password'})],
          actions: [{label: 'Cancelar'}, {label: 'Descargar', primary: true, icon: 'Exportar', onClick: async ({data}) => { await download('/api/android/export', {password: data.password}); snack('Archivo descargado.'); }}]}), {iconName: 'Exportar'}),
          button('Importar .spvi', () => dialog({title: 'Importar de la app móvil', iconName: 'Importar', body: [
            h('div', {class: 'banner mal'}, icon('Alerta'), h('span', {class: 'texto', text: 'Reemplaza el negocio de esta PC. Después vuelve a vincular las apps secundarias.'})),
            archivo('.spvi'), campo({label: 'Contraseña del archivo (si la tiene)', name: 'password', type: 'password', maxlength: 256, autocomplete: 'off'}),
            campo({label: 'Contraseña para guardar una copia del estado actual (mínimo 12)', name: 'safety_password', type: 'password', minlength: 12, maxlength: 256, required: true, autocomplete: 'new-password'}),
            campo({label: 'Escribe RESTAURAR para confirmar', name: 'confirm', required: true, pattern: 'RESTAURAR', autocomplete: 'off'})],
          actions: [{label: 'Cancelar'}, {label: 'Importar', primary: true, kind: 'peligro', icon: 'Importar', onClick: async ({form}) => { await subir('/api/android/import', form); snack('Negocio importado.'); await SPVI.changed(); }}]}), {kind: 'borde', iconName: 'Importar'})))));
  }

  if (sub === 'acceso') {
    const form = h('form', {class: 'card form'},
      h('p', {class: 'secundario', text: app.protegida ? 'SPVI pide esta clave al abrir la pantalla principal.' : 'Sin clave, cualquiera que use esta PC puede abrir SPVI. Puedes crear una cuando quieras.'}),
      app.protegida ? campo({label: 'Clave actual', name: 'actual', type: 'password', required: true, maxlength: 256, autocomplete: 'current-password'}) : null,
      h('div', {class: 'par'},
        campo({label: app.protegida ? 'Nueva clave' : 'Clave', name: 'nueva', type: 'password', minlength: 8, maxlength: 256, autocomplete: 'new-password', required: !app.protegida}),
        campo({label: 'Repite la clave', name: 'repite', type: 'password', minlength: 8, maxlength: 256, autocomplete: 'new-password', required: !app.protegida})),
      h('div', {class: 'fila fin'},
        app.protegida ? button('Quitar clave', () => busy(null, async () => {
          if (!form.elements.actual.reportValidity()) return;
          if (!await confirmar({title: 'Quitar clave', text: 'SPVI se abrirá sin pedir clave.', confirm: 'Quitar clave', danger: true})) return;
          await api('/api/app/access', {actual: form.elements.actual.value, nueva: null}); terminar(false, 'Clave quitada.');
        }), {kind: 'texto', iconName: 'Eliminar'}) : null,
        button(app.protegida ? 'Cambiar clave' : 'Crear clave', null, {type: 'submit', iconName: 'Guardar'})));
    form.addEventListener('submit', event => { event.preventDefault(); busy(form.querySelector('button[type=submit]'), async () => {
      const d = Object.fromEntries(new FormData(form));
      if (!d.nueva) throw new Error('Escribe la nueva clave.');
      if (d.nueva !== d.repite) throw new Error('Las claves no coinciden.');
      await api('/api/app/access', {actual: d.actual || '', nueva: d.nueva, repite: d.repite}); terminar(true, 'Clave guardada.');
    }); });
    const terminar = async (protegida, mensaje) => { document.body.dataset.protegida = protegida ? 'si' : 'no'; snack(mensaje); await SPVI.changed(); };
    root.append(form);
  }

  if (sub === 'inicio') {
    const pref = app.preferencias;
    const guardar = async cambios => { await busy(null, async () => { SPVI.cache.app = await api('/api/app/preferences', cambios); snack('Guardado.'); await SPVI.render(); }); };
    const retraso = seleccion({label: 'Esperar antes de iniciar', name: 'retraso', value: String(pref.retrasoSegundos), options: RETRASOS.map(([v, t]) => [String(v), t]),
      help: 'Útil si la PC tarda en conectarse a la red al encender.', onchange: event => guardar({retrasoSegundos: Number(event.target.value)})});
    const conWindows = interruptor({label: 'Iniciar con Windows', help: app.inicioDisponible ? 'SPVI se abre en segundo plano al iniciar sesión y queda junto al reloj.' : 'Disponible solo en Windows.',
      checked: pref.inicioConWindows, onchange: event => guardar({inicioConWindows: event.target.checked})});
    const abrir = interruptor({label: 'Abrir la pantalla principal al iniciar con Windows', checked: pref.abrirAlIniciar, onchange: event => guardar({abrirAlIniciar: event.target.checked})});
    if (!app.inicioDisponible) conWindows.querySelector('input').disabled = true;
    if (!pref.inicioConWindows) { abrir.querySelector('input').disabled = true; retraso.querySelector('select').disabled = true; }
    root.append(h('div', {class: 'card form'}, conWindows, retraso, abrir),
      h('p', {class: 'secundario', text: 'Si no se inicia con Windows, abre SPVI desde su acceso directo. El icono junto al reloj da acceso a la pantalla principal, las apps conectadas y el tiempo en línea.'}));
    if (app.controles.length) root.append(h('div', {class: 'fila'},
      app.controles.includes('restart') ? button('Reiniciar SPVI', reiniciar, {kind: 'borde', iconName: 'Reiniciar'}) : null,
      app.controles.includes('quit') ? button('Salir de SPVI', salir, {kind: 'texto', iconName: 'Apagar'}) : null));
  }

  if (sub === 'tema') {
    const actual = app.preferencias.tema;
    root.append(h('div', {class: 'lista'}, [['claro', 'Claro', 'Inicio'], ['oscuro', 'Oscuro', 'TemaOscuro'], ['sistema', 'Según el sistema', 'Configurar']].map(([id, label, ic]) =>
      h('label', {class: 'item-lista'}, leadIcon(ic), h('span', {class: 'texto'}, h('span', {class: 'titulo', text: label})),
        h('input', {type: 'radio', name: 'tema', value: id, checked: id === actual, onchange: () => busy(null, async () => {
          document.documentElement.dataset.theme = id; SPVI.cache.app = await api('/api/app/preferences', {tema: id}); snack('Tema aplicado.');
        })})))));
  }

  if (sub === 'prueba') {
    root.append(h('div', {class: 'card'}, h('div', {class: 'banner mal'}, icon('Alerta'), h('span', {class: 'texto', text: 'Borra todos los datos del negocio y carga un negocio de ejemplo con varios meses de ventas.'})),
      h('div', {class: 'fila'}, button('Cargar datos de prueba', async () => {
        if (!await confirmar({title: 'Datos de prueba', text: 'Se borrarán todos los datos actuales.', confirm: 'Borrar y cargar', danger: true, palabra: 'BORRAR'})) return;
        await busy(null, async () => { snack('Cargando datos de prueba…'); await api('/api/seed', {confirm: 'BORRAR'}); snack('Datos de prueba cargados.'); SPVI.state.carrito = {lineas: [], metodo: 'efectivo', correccion: null}; await SPVI.changed(); });
      }, {kind: 'peligro', iconName: 'Desarrollador'}))));
  }
};

/* ---------- Licencia: solicitud por QR de WhatsApp y activación con código de 16 dígitos. ---------- */
function vistaLicencia(root, catalogo, licencia) {
  const p = catalogo.perfil || {};
  const nombres = {MENSUAL: 'Mensual', SEMESTRAL: 'Semestral', ANUAL: 'Anual', PERPETUA: 'Perpetua'};
  const estado = h('div', {class: 'card ' + (licencia.clase === 'BLOQUEADA' ? 'error' : 'primario')}, h('div', {class: 'cabecera'}, icon(licencia.clase === 'BLOQUEADA' ? 'Bloqueo' : 'Verificado'),
    h('h3', {text: {PRUEBA: 'Periodo de prueba', ACTIVA: 'Licencia ' + (nombres[licencia.tipo] || '').toLowerCase() + ' activa', PERPETUA: 'Licencia perpetua', BLOQUEADA: 'Sin licencia vigente'}[licencia.clase]})),
    h('span', {text: licencia.clase === 'BLOQUEADA' ? (licencia.motivo || '') : licencia.venceEn ? 'Válida hasta el ' + fecha(licencia.venceEn - 1) : 'No vence.'}),
    h('span', {text: 'Apps secundarias incluidas: ' + licencia.secundarias}));
  root.append(estado);

  // Paso 1: solicitud
  let tipo = 'ANUAL', secundarias = 0;
  const total = h('p', {class: 'dato'});
  const tipos = h('div', {class: 'chips', role: 'radiogroup', 'aria-label': 'Tipo de licencia'});
  const pintarTipos = () => {
    tipos.replaceChildren(...Object.entries(TIPOS_LICENCIA).map(([id, [label, precio]]) => h('button', {type: 'button', class: 'chip', 'aria-pressed': String(id === tipo), onclick: () => { tipo = id; pintarTipos(); }},
      label, ' ', h('small', {text: cup(precio)}))));
    const [, base, extra] = TIPOS_LICENCIA[tipo];
    total.textContent = secundarias ? 'Total: ' + cup(base + secundarias * extra) + ' (' + cup(base) + ' + ' + secundarias + ' × ' + cup(extra) + ')' : 'Total: ' + cup(base);
  };
  const selectSec = seleccion({label: 'Apps secundarias (empleados)', name: 'secundarias', value: '0', options: Array.from({length: 11}, (_, n) => [String(n), n === 0 ? 'Sin apps secundarias' : n === 1 ? '1 app secundaria' : n + ' apps secundarias'])});
  selectSec.querySelector('select').addEventListener('change', event => { secundarias = Number(event.target.value); pintarTipos(); });
  const recuperar = h('details', null, h('summary', {text: '¿Reinstalaste SPVI o cambiaste de PC?'}),
    campo({label: 'ID de la licencia anterior (opcional)', name: 'recupera', maxlength: 36, autocomplete: 'off', help: 'Si lo tienes, el desarrollador puede reemitirla sin un nuevo pago.'}));
  const form = h('form', {class: 'card form'},
    h('div', {class: 'cabecera'}, h('span', {class: 'lead paso', text: '1'}), h('h3', {text: licencia.clase === 'PERPETUA' ? 'Solicitar otra licencia' : 'Solicitar licencia'})),
    h('p', {class: 'secundario', text: 'Paga el total al desarrollador y escribe el número de transacción del pago. Se generará un código QR para enviar la solicitud por WhatsApp desde tu teléfono.'}),
    h('span', {class: 'secundario', text: 'Tipo de licencia'}), tipos, selectSec, total,
    h('div', {class: 'par'}, campo({label: 'Nombre', name: 'nombre', required: true, maxlength: 80, value: p.nombre || ''}), campo({label: 'Apellidos', name: 'apellidos', required: true, maxlength: 80, value: p.apellidos || ''})),
    h('div', {class: 'par'}, campo({label: 'Carné de identidad', name: 'ci', required: true, maxlength: 20, inputmode: 'numeric', value: p.ci || ''}), campo({label: 'Teléfono', name: 'telefono', required: true, maxlength: 20, inputmode: 'tel'})),
    campo({label: 'Número de transacción del pago', name: 'transaccion', maxlength: 40, autocomplete: 'off', help: 'De 4 a 40 letras, números o guiones.'}),
    recuperar,
    h('div', {class: 'fila fin'}, button('Generar QR de solicitud', null, {type: 'submit', iconName: 'WhatsApp', kind: 'whatsapp'})));
  pintarTipos();
  form.addEventListener('submit', event => { event.preventDefault(); busy(form.querySelector('button[type=submit]'), async () => {
    if (!form.reportValidity()) return;
    const d = Object.fromEntries(new FormData(form));
    if (!d.transaccion.trim() && !d.recupera?.trim()) throw new Error('Escribe el número de transacción del pago.');
    const solicitud = await api('/api/license/request', {nombre: d.nombre, apellidos: d.apellidos, ci: d.ci, telefono: d.telefono, tipo, secundarias, transaccion: d.transaccion, recupera: d.recupera || ''});
    const png = await apiBlob('/api/license/qr', {url: solicitud.url});
    const url = URL.createObjectURL(await png.blob());
    dialog({title: 'Enviar solicitud', iconName: 'WhatsApp', body: [
      h('div', {class: 'centro form'}, h('img', {class: 'qr', src: url, alt: 'Código QR de la solicitud'}),
        h('ol', {class: 'pasos'}, h('li', {text: 'Abre la cámara del teléfono (o el lector de QR) y apunta a este código.'}),
          h('li', {text: 'Toca el enlace: se abrirá WhatsApp con la solicitud ya escrita.'}), h('li', {text: 'Envía el mensaje sin modificarlo.'}),
          h('li', {text: 'Cuando el desarrollador verifique el pago, te enviará un código de 16 dígitos. Escríbelo en el paso 2.'}))),
      h('details', null, h('summary', {text: 'Ver el mensaje'}), h('pre', {class: 'mensaje', text: solicitud.message})),
    ], actions: [
      {label: 'Copiar mensaje', icon: 'Copiar', onClick: async () => { await navigator.clipboard.writeText(solicitud.message); snack('Mensaje copiado.'); return false; }},
      {label: 'Listo', primary: true},
    ], onClose: () => URL.revokeObjectURL(url)});
  }); });

  // Paso 2: activación (código de 16 caracteres o clave maestra de desarrollo)
  const codigo = h('input', {class: 'codigo', name: 'codigo', inputmode: 'text', autocomplete: 'off', placeholder: '0000-0000-0000-0000', 'aria-label': 'Código de licencia', maxlength: 19, required: true, spellcheck: false});
  codigo.addEventListener('input', () => {
    const texto = codigo.value.toUpperCase().slice(0, 19);
    if (/[A-Z]/.test(texto)) {
      codigo.value = texto.replace(/[^0-9A-Z\s-]/g, '');  // maestra: letras, números y guiones
    } else {
      const digitos = texto.replace(/\D/g, '').slice(0, 16);
      codigo.value = digitos.replace(/(\d{4})(?=\d)/g, '$1-');
    }
  });
  const activar = h('form', {class: 'card form'},
    h('div', {class: 'cabecera'}, h('span', {class: 'lead paso', text: '2'}), h('h3', {text: 'Activar licencia'})),
    h('p', {class: 'secundario', text: 'Escribe el código de 16 dígitos que te envió el desarrollador.'}),
    h('label', {class: 'campo'}, h('span', {text: 'Código de licencia'}), codigo),
    h('div', {class: 'fila fin'}, button('Activar', null, {type: 'submit', iconName: 'Licencia'})));
  activar.addEventListener('submit', event => { event.preventDefault(); busy(activar.querySelector('button[type=submit]'), async () => {
    if (!/^[0-9A-Z]{16}$/.test(codigo.value.replace(/[\s-]/g, '').toUpperCase())) throw new Error('El código tiene 16 caracteres.');
    await api('/api/license/activate', {code: codigo.value}); snack('Licencia activada.'); await SPVI.changed();
  }); });
  root.append(h('div', {class: 'dos'}, form, activar));

  // Lista de revocaciones (la entrega el desarrollador si hace falta)
  const archivo = h('input', {type: 'file', accept: '.json,application/json', hidden: true, onchange: () => busy(null, async () => {
    const file = archivo.files[0]; if (!file) return;
    let envelope; try { envelope = JSON.parse(await file.text()); } catch { throw new Error('Archivo no válido.'); }
    await api('/api/license/revocations', envelope); snack('Lista actualizada.'); await SPVI.changed();
  })});
  root.append(h('div', {class: 'fila'}, button('Importar lista de revocaciones', () => archivo.click(), {kind: 'texto', iconName: 'Importar'}), archivo));
}

/* ---------- Reiniciar / salir (también desde el icono junto al reloj). ---------- */
async function reiniciar() {
  if (!await confirmar({title: 'Reiniciar SPVI', text: 'SPVI se cerrará y volverá a abrirse. La red local se reanuda sola.', confirm: 'Reiniciar', iconName: 'Reiniciar'})) return;
  await busy(null, async () => {
    await api('/api/app/restart', {});
    $('#vista').replaceChildren(h('div', {class: 'vista'}, h('div', {class: 'card centro'}, icon('Reiniciar', 'lg'), h('h2', {text: 'Reiniciando SPVI…'}), h('p', {class: 'secundario', text: 'La página se recargará sola.'}))));
    const responde = async () => { try { return (await fetch('/favicon.ico', {cache: 'no-store'})).ok; } catch { return false; } };
    const espera = ms => new Promise(r => setTimeout(r, ms));
    const inicio = Date.now();
    while (Date.now() - inicio < 10000 && await responde()) await espera(500);   // primero se cierra
    while (Date.now() - inicio < 60000 && !await responde()) await espera(1000); // y luego vuelve
    location.reload();
  });
}
async function salir() {
  if (!await confirmar({title: 'Salir de SPVI', text: 'Se cerrará SPVI en esta PC y las apps secundarias no podrán sincronizar hasta que lo vuelvas a abrir.', confirm: 'Salir', danger: true, iconName: 'Apagar'})) return;
  await busy(null, async () => {
    await api('/api/app/quit', {});
    document.body.replaceChildren(h('main', {class: 'acceso'}, h('div', {class: 'card'}, h('img', {src: '/static/icons/spvi-192.png', alt: ''}), h('h1', {class: 'marca', text: 'SPVI'}),
      h('p', {text: 'SPVI se cerró. Ya puedes cerrar esta pestaña.'}))));
  });
}
