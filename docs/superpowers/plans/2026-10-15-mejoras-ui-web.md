# Mejoras UI web-desktop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corregir los 7 defectos de UI detectados en las capturas de la app web-desktop (FAB que tapa precios, banner de prueba inconsistente, barra de Registros saturada, costos siempre visibles, pestañas inactivas tenues, Salir con igual peso que Reiniciar, cronómetros sin formato humano).

**Architecture:** Cambios solo en `desktop/spvi_web/static` (CSS en `app.css`, vistas en `js/`). Sin cambios de API ni de modelo. El flag de costos persiste en `localStorage`.

**Tech Stack:** JS vainilla + CSS con variables, Python unittest para regresión, capturas Playwright para verificación visual.

**Spec:** Revisión de UI del 2026-10-15 (capturas en `C:\Users\666\AppData\Local\Temp\opencode\shots\web_*.png`): bug FAB en Servicios, banner en 3 de 10 secciones, etc.

## Global Constraints

- Todo el texto de interfaz y comentarios en español.
- No tocar API Python ni modelo de datos; solo `static/`.
- `localStorage` solo para preferencias (`spvi-menu`, `spvi-pestana` existen); nada sensible.
- Cada tarea termina con `unittest discover -s tests` en verde (desde `desktop/` con `$env:PYTHONPATH="."`) y captura Playwright verificada.
- Si cambia comportamiento visible, entrada en `docs/HISTORIAL_DESARROLLO.md`.

## Review Focus

- Pantalla estrecha (≤600px): la barra de Registros y las pestañas no deben romper layout; verificar con captura a 480px.
- `Archivados` (sin FAB) no debe ganar margen inferior fantasma con la regla del FAB.
- Con costos ocultos, la fila debe mantener alineación vertical (no colapsar altura de forma rara).
- Banner global no duplicado en Inicio (quitar la llamada por vista al centralizar).
- `prefers-reduced-motion` sigue respetado (no se añaden animaciones).

---

### Task 1: Padding inferior anti-FAB

**Files:**
- Modify: `desktop/spvi_web/static/app.css` (≈línea 210, junto a `.fab`)

**Interfaces:**
- Consumes: clases existentes `.fab`, `.fab.extendida`, `.lista`, `.vista`
- Produces: regla CSS que da aire inferior a la lista cuando la vista tiene FAB

- [ ] **Step 1: Añadir regla** en `app.css` junto a `.fab`: `.vista:has(.fab) .lista:last-of-type{margin-bottom:80px}` (si `:has` no se quiere usar, alternativa: clase `con-fab` en las 5 vistas con FAB: `servicios.js:48`, `inventario.js:202-206`, `registros.js:192`, `ajustes.js:90`, `venta.js:92`).
- [ ] **Step 2: Verificar visualmente** con el script `C:\Users\666\AppData\Local\Temp\opencode\pw\shots.js` (rutas `servicios`, `inventario`): el precio de la última fila («Reparación de calzado … 300.00 CUP») ya no queda tapado; `archivados` sin margen extra.
- [ ] **Step 3: Correr regresión**: `$env:PYTHONPATH="."; .\.venv\Scripts\python.exe -m unittest discover -s tests` desde `desktop/`. Expected: OK.

### Task 2: Banner de licencia global

**Files:**
- Modify: `desktop/spvi_web/static/js/core.js` (en `SPVI.render`, tras pintar `SPVI.views[id]`)
- Modify: `desktop/spvi_web/static/js/inicio.js:112` (quitar llamada local)
- Check: `venta.js`, `vinculadas.js` (las capturas muestran banner ahí; si lo pintan por su cuenta, quitar esas llamadas también)

**Interfaces:**
- Consumes: `SPVI.bannerLicencia(licencia)` definido en `js/inicio.js:90-105` (devuelve `div.banner` o null)
- Produces: banner pintado una sola vez por sección, idéntico en las 10 rutas

- [ ] **Step 1: Centralizar**: en `SPVI.render` (`core.js`, después de `SPVI.views[id](...)`), anteponer `SPVI.bannerLicencia(app.licencia)` al `root` si no es null.
- [ ] **Step 2: Eliminar llamadas por vista** en `inicio.js:112` (y `venta.js`/`vinculadas.js` si existen).
- [ ] **Step 3: Verificar** capturando las 10 rutas: banner visible en todas cuando hay prueba activa, y ninguno duplicado en Inicio.
- [ ] **Step 4: Correr regresión** unittest. Expected: OK.

### Task 3: Chips de período a segunda fila en Registros

**Files:**
- Modify: `desktop/spvi_web/static/js/registros.js:189-191`

**Interfaces:**
- Consumes: helpers `pestanas()` (`core.js:266-269`), `chipsPeriodo()` (`core.js:273-277`), estado `SPVI.state.registros`
- Produces: misma conducta de filtrado, layout en dos filas (pestañas / buscador+chips)

- [ ] **Step 1: Separar filas**: cambiar `h('div',{class:'fila'},buscador(...),periodos)` por bloque de pestañas, luego `div.fila` solo con buscador, luego `div.fila.chips-fila` (o clase equivalente) solo con `periodos`.
- [ ] **Step 2: Verificar** captura de `registros` a 1366px y a 480px: sin solapes, chips legibles.
- [ ] **Step 3: Correr regresión** unittest. Expected: OK.

### Task 4: Toggle «Ver costos» en Inventario

**Files:**
- Modify: `desktop/spvi_web/static/js/inventario.js:45-49` (`precios(costo,venta)`), cabecera de la vista (≈línea 60-64) para el interruptor

**Interfaces:**
- Consumes: `localStorage` clave `spvi-ver-costos` (`'1'` visible / `'0'` oculto, por defecto visible)
- Produces: `precios()` omite `span.secundario('Costo …')` cuando el flag es `'0'`; chip/interruptor «Ver costos» que alterna y repinta la vista

- [ ] **Step 1: Condicionar** el `span.secundario` de `precios()` al flag de `localStorage`.
- [ ] **Step 2: Añadir interruptor** «Ver costos» (chip con `aria-pressed`, mismo patrón que `chipsPeriodo`) en la cabecera de Inventario; al pulsar guarda el flag y repinta.
- [ ] **Step 3: Verificar** capturas: con costos ocultos la fila mantiene alineación y el precio sigue en negrita; el flag persiste al recargar.
- [ ] **Step 4: Añadir prueba** en `desktop/tests/` que cargue la vista o el helper y afirme que el costo no se pinta con flag `'0'` (sigue el patrón de las pruebas JS existentes si hay; si no, prueba Python del endpoint no aplica — en ese caso verificación por captura + nota en el commit).
- [ ] **Step 5: Correr regresión** unittest. Expected: OK.

### Task 5: Contraste de pestañas inactivas

**Files:**
- Modify: `desktop/spvi_web/static/app.css:243-245` (`.pestanas button` sin `aria-selected`)

**Interfaces:**
- Consumes: variables `--on-surface-variant`, `--surface`
- Produces: texto inactivo con más peso/contraste sin tocar el seleccionado

- [ ] **Step 1: Subir** a `font-weight:600` y color a un tono más oscuro que `--on-surface-variant` (p. ej. `--on-surface` al 80% o variable existente más oscura; no inventar hex fuera de la paleta de `app.css:12,29`).
- [ ] **Step 2: Verificar** capturas de `inventario` y `registros`: inactivas legibles, seleccionada intacta.
- [ ] **Step 3: Correr regresión** unittest. Expected: OK.

### Task 6: «Salir de SPVI» como acción secundaria

**Files:**
- Modify: `desktop/spvi_web/static/js/ajustes.js:25-27` y `203-205`

**Interfaces:**
- Consumes: helper `button()` (`core.js:43-45`); revisar qué `kind` existen (`borde` usado; buscar `peligro`/`texto` en `core.js`/`app.css`)
- Produces: «Reiniciar SPVI» mantiene `kind:'borde'`; «Salir de SPVI» pasa al `kind` secundario/de texto existente (misma confirmación y handler `salir()` intactos)

- [ ] **Step 1: Cambiar** el `kind` del botón Salir en ambos bloques; si no existe `kind` de texto, añadir clase mínima `.btn.texto` en `app.css:193-199` (transparente, sin borde).
- [ ] **Step 2: Verificar** captura de `ajustes`: Salir visualmente secundario, misma posición.
- [ ] **Step 3: Correr regresión** unittest. Expected: OK.

### Task 7: Formato humano junto a los cronómetros

**Files:**
- Modify: `desktop/spvi_web/static/js/core.js:56-61` (nuevo helper junto a `duracion(ms)`)
- Modify: `desktop/spvi_web/static/js/tiempo.js:7,27-42` (mostrarlo)

**Interfaces:**
- Consumes: `duracion(ms)` existente (`HH:MM:SS`, `>24h→"Xd Xh XXmin"`)
- Produces: `duracionCorta(ms)->string` (p. ej. `6 min`, `2 h 5 min`, `3 días`) mostrado bajo/c Junto a cada `.reloj` de Tiempo en línea sin alterar el `tic()` de 1s

- [ ] **Step 1: Añadir** `duracionCorta(ms)` en `core.js` junto a `duracion` (minutos/horas/días, sin segundos).
- [ ] **Step 2: Mostrarla** en `tiempo.js` junto a `relojApp`/`relojRed` (elemento `.secundario` que `tic()` actualiza).
- [ ] **Step 3: Verificar** captura de `tiempo`: se lee «6 min» junto a `00:06:28`.
- [ ] **Step 4: Correr regresión** unittest. Expected: OK.

### Task 8: Cierre (docs + verificación final)

**Files:**
- Modify: `docs/HISTORIAL_DESARROLLO.md` (nueva entrada arriba)

- [ ] **Step 1: Entrada** resumiendo las 7 tareas (qué cambió y por qué), fecha 2026-10-15.
- [ ] **Step 2: Suite completa** unittest + las 10 capturas Playwright revisadas (incluida `registros` a 480px).
- [ ] **Step 3: Commit** (`git add desktop/spvi_web docs/HISTORIAL_DESARROLLO.md`, mensaje `feat(web): mejoras UI de la revisión visual`).
