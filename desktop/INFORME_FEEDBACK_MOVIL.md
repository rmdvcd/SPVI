# Informe de feedback: lo que tienen las apps móviles y no la de escritorio

**Fecha:** 14 de octubre de 2026
**Alcance:** los diez cambios pedidos para el teléfono (Android 0.31.0 + Flutter/iOS) y lo que ya tenía
la web de escritorio (`desktop/`, versión 0.31.0).
**Método:** se revisó el código de `desktop/spvi_web/` (Python + `static/js/*.js`) y se comparó,
funcionalidad por funcionalidad, con `app/` (Kotlin) y `flutter_app/` (Flutter). Nada de este informe
está verificado en ejecución: la web se recorre con pruebas Python y navegador sin pantalla en Linux,
pero **no se ha probado en Windows ni con teléfonos reales** ([VERIFICACION_PC.md](VERIFICACION_PC.md)).

La regla que se aplicó para filtrar: **¿tiene sentido en una PC con navegador, usada como principal?**
Si la función existe por algo del teléfono (hardware, permisos del sistema, pantalla chica, toque),
se descarta con el motivo. Si es de negocio o de claridad, se propone.

---

## 1. Lo que el móvil tiene y el escritorio no

| # | Función del móvil (0.31.0) | ¿Está en la web? | Veredicto |
|---|---|---|---|
| 1 | Inicio: «Turno cerrado» + selector de período en la misma fila, primer contenedor fijo al desplazar | Parcial: el turno está en Inicio y los gráficos son propios, pero **no hay selector de período** en Inicio (sí chips de período en Registros) y el contenido se desplaza entero | **Sí conviene** (ver 1.1) |
| 2 | Inventario: fila reordenada (cantidad que se puede hacer + unidad, categoría debajo, costo y venta a la derecha diferenciados) | No: la fila muestra nombre, categoría, precio de venta y stock; el costo no se ve en la lista | **Sí conviene** (ver 1.2) |
| 3 | Registros: todas las listas con casilla de selección | No: Registros tiene período, búsqueda y paginación, pero **sin marcar elementos**; para elegir qué exportar hay un desplegable de alcance (inventario/servicios/productos/turnos) | **Sí conviene** (ver 1.3) |
| 4 | Licencia: fuera el botón de llave del final | No aplica: ese asistente paso a paso es del móvil; la web ya tiene su propia pantalla de Licencia con QR | **No aplica** |
| 5 | Migrar: paso de datos entre dos teléfonos por QR (Emisor/Receptor) | No, y **no debe estarlo**: la web es solo principal y ya hace ese papel con el respaldo `.spvi` y las apps vinculadas | **No tiene sentido** (ver 2.1) |
| 6 | Actualizar: diálogo con barra de progreso circular y tres resultados | No: la web no se actualiza sola (la actualiza el `.exe`/el dueño) | **No tiene sentido** (ver 2.2) |
| 7 | Ayuda: manual por secciones con icono por tema y esquemas | No: no hay pantalla de Ayuda ni de Soporte en la web | **Sí conviene, mínimo** (ver 1.4) |
| 8 | Soporte: filas centradas Icono → Descripción → Texto | No | **Sí conviene, mínimo** (ver 1.4) |
| 9 | Primera apertura: todos los permisos de golpe y sin textos | No: la web no pide permisos del sistema | **No tiene sentido** (ver 2.3) |
| 10 | Animaciones: deslizar lateral, fundido al cambiar de pestaña, ventanas desde el centro-abajo | Parcial: la web tiene transición de entrada entre secciones y micro-interacciones, con `prefers-reduced-motion`, pero **sin fundido al cambiar de sección** ni ventanas desde abajo | **Sí conviene, ligero** (ver 1.5) |

Además, cosas del móvil que la web no tiene y que vale la pena anotar aunque no formsen parte del
encargo: **empleados con QR y permisos por empleado** (la web tiene administración de empleados, pero
no el QR de acceso ni los permisos granulares del móvil), **notificaciones** (la web tiene icono de
bandeja, que para eso está) y **cámara** (la web sube fotos desde archivo, que es lo natural en PC).

---

## 2. Lo que **no** tiene sentido implementar en web-escritorio (filtrado)

### 2.1 Migrar por QR entre dos teléfonos (petición 5)
En la PC no hay segundo teléfono que sincronizar por punto de acceso: la web **ya es** la principal y
recibe a las secundarias por la red local (`sync/`), además de importar y exportar respaldos `.spvi`.
Copiar el asistente Emisor/Receptor sería inventar un paso que no existe. **Descartado.**
Lo único razonable, y ya existente: que el respaldo de la web siga siendo compatible con el del móvil.

### 2.2 Actualizar con diálogo de progreso (petición 6)
La web se actualiza cuando el dueño instala la versión nueva (`build_windows.ps1`, `.exe`); no hay
canal de descarga en caliente ni instalador de paquetes desde el navegador. Un diálogo de «estás al
día» no diría nada que el número de versión de Ajustes no diga ya. **Descartado.**
Si en el futuro se añade actualización en caliente, el diálogo tendría sentido; hoy no.

### 2.3 Pedir todos los permisos al abrir por primera vez (petición 9)
Los permisos de Android (cámara, notificaciones, fotos, servicios en primer plano) no existen en el
navegador. Lo más parecido —permisos de notificación del navegador o de cámara— se pide cuando la
función lo necesita, no todos de golpe al entrar. **Descartado.**

### 2.4 Acceso con huella o biometría (petición 5, la puerta)
En la PC la puerta es la **clave de acceso** de Ajustes, que ya existe y funciona en `127.0.0.1`.
La biometría del teléfono no está disponible desde el navegador. **Descartado** (la clave ya cubre el
mismo riesgo).

### 2.5 Pantalla completa táctil, barra inferior y gestos
La web usa menú lateral y ratón/teclado. La barra de navegación inferior, el deslizamiento entre
secciones y los botones flotantes del móvil son decisiones de pantalla chica. **Descartado** salvo el
fundido de cambio de sección (1.5), que es de claridad, no de dedo.

---

## 3. Lo que **sí** conviene implementar en la web

### 1.1 Inicio: selector de período junto al turno
Hoy Inicio muestra los gráficos del día implícitamente y el período solo se puede cambiar en
Registros. Poner un selector de período (Turno / Hoy / 7 días / 30 días / Todo) junto a la tarjeta del
turno, **reutilizando los chips que ya existen en `static/js/registros.js`**, haría que los KPIs y los
dos gráficos (dona de formas de cobro y área de ganancia) se lean para el período que se elige.
Es el cambio con mejor relación esfuerzo/beneficio de la lista: son dos gráficos SVG propios ya
hechos (`static/js/inicio.js`) y un estado que ya se guarda en `SPVI.state`.
*Esfuerzo: bajo. No hace falta tocar el backend: el tablero ya llega con las series por día.*

### 1.2 Inventario: la fila como en el móvil
Añadir a cada fila **la cantidad que se puede hacer con su unidad** (para elaborados, la que permiten
las recetas; para insumos, la cantidad con `u`/`kg`/`L`) y **el precio de costo junto al de venta**,
diferenciados por color. En la web el costo ya está en el modelo (`business.py` guarda costos), así
que es solo presentación. Ayuda a no vender por debajo del costo, que es justo lo que la fila nueva
del móvil resuelve.
*Esfuerzo: bajo-medio (hay que calcular la cantidad posible de los elaborados; el móvil ya lo hace en
`InventarioLogic.kt` y se puede portar).*

### 1.3 Registros: marcar elementos con casilla
La web tiene búsqueda, período y paginación; falta poder **marcar filas** para exportar o anular
varias. Con la paginación que ya existe, la casilla debería respetar la página visible y ofrecer
«marcar todo lo filtrado» (no solo la página). El desplegable de alcance de exportación podría
quedarse como está y sumar «lo seleccionado».
*Esfuerzo: medio. Cuidado: la paginación hace que «seleccionar todo» sea ambiguo —hay que decidirlo
antes de programar.*

### 1.4 Ayuda y Soporte, versión mínima
No hace falta el manual completo del móvil, pero sí dos pantallas cortas:
- **Ayuda**: cómo se hace cada cosa en la web (turno, venta, inventario, respaldo, apps vinculadas),
  con el mismo estilo de secciones con icono. La web ya tiene el juego de iconos (`Ayuda`, `Soporte`,
  `Respaldo`, `Vinculacion`, `Licencia`…).
- **Soporte**: los datos del desarrollador (teléfono/WhatsApp), centrados, en el orden
  Icono → Descripción → Texto, igual que en el móvil.
*Esfuerzo: bajo. Es contenido estático; se puede copiar la estructura de `ayuda/` del móvil.*

### 1.5 Fundido al cambiar de sección y ventanas desde el centro-abajo
La web ya tiene transición de entrada entre secciones y micro-interacciones con
`prefers-reduced-motion`. Falta el **fundido de salida y entrada** al cambiar de sección del menú
lateral y que los diálogos entren **desde el centro-abajo** en lugar de aparecer de golpe. Es el mismo
criterio que en el móvil (barra de navegación = fundido, ventanas = desde abajo) y mantiene la app
coherente entre dispositivos.
*Esfuerzo: bajo. Todo está en `static/app.css` y `static/js/core.js`.*

---

## 4. Orden sugerido

1. **1.1 selector de período en Inicio** y **1.5 animaciones**: poco código, se notan todos los días.
2. **1.2 fila de inventario** y **1.4 Ayuda/Soporte**: claridad para el dueño.
3. **1.3 casillas en Registros**: el más laborioso por la paginación; conviene decidir primero el
   comportamiento de «seleccionar todo».

Nada de lo anterior cambia el modelo de datos ni el protocolo de sincronización, así que **no obliga a
tocar las apps móviles** ni a migrar respaldos.

---

## 5. Lo que queda igual en las tres apps

- **Respaldo**: `.spvi` del móvil y `.spvidesk` de la web siguen siendo compatibles; la migración entre
  teléfonos es cosa del móvil y la web no interviene.
- **Licencia**: mismo contrato GL en las tres (ver [LICENSE_CLIENT.md](../LICENSE_CLIENT.md)).
- **Sincronización**: la web manda la instantánea con el sub-token firmado de cada secundaria; los
  diez cambios del móvil no tocan ese protocolo.

---

## 6. Estado de la implementación (14/10/2026)

Las cinco mejoras de la sección 3 **están implementadas** en `desktop/spvi_web/`, en el orden sugerido.
Ninguna cambia el modelo de datos ni el protocolo de sincronización, así que las apps móviles no se tocan.

| Mejora | Dónde | Qué se hizo |
|---|---|---|
| **1.1** Período en Inicio | `core.js`, `inicio.js`, `app.css` | Barra fija (`.barra-turno` + `.pegajoso`) con el estado del turno y los chips de período en la misma fila: Turno / Hoy / 7 / 30 / 90 días / Todo. `Business.dashboard()` acepta `1, 7, 30, 90`, `0` (todo) y `'turno'` (desde la apertura del turno; sin turno abierto, desde el inicio del día). La serie diaria del gráfico se deriva del período (`SPVI.diasSerie`). Registros usa ahora los mismos chips (`chipsPeriodo`). |
| **1.5** Animaciones | `app.css`, `core.js` | `SPVI.fundir()` desvanece la sección anterior 120 ms antes de pintar la nueva y los diálogos entran desde el centro-abajo (`@keyframes ventana-entra`). Se respeta `prefers-reduced-motion`. |
| **1.2** Fila de inventario | `inventario.js`, `app.css` | Nombre, categoría y existencias a la izquierda; **costo y venta a la derecha, diferenciados** (`.valores`). En elaborados se añade «Se pueden hacer N con las existencias», calculado de la receta y de las existencias de insumos. |
| **1.4** Ayuda y Soporte | `ayuda.js` (nuevo), `core.js`, `index.html`, `app.css` | Dos secciones nuevas en el menú lateral. **Ayuda**: nueve secciones desplegables con icono (turno y caja, venta, inventario, servicios, registros y exportar, respaldo, apps vinculadas, licencia y ajustes). **Soporte**: datos del desarrollador en filas centradas, en el orden Icono → Descripción → Texto, con WhatsApp y copiar teléfono. |
| **1.3** Casillas en Registros | `registros.js`, `business.py`, `exports.py`, `app.css` | Casilla en todas las listas y barra de selección con **«Marcar todo lo filtrado»** (exacto, no solo la página visible: `ledger(..., ids=True)` devuelve los identificadores de todo el filtro), «Quitar selección» y «Exportar selección». Alcance de exportación nuevo **ventas** (PDF/Excel) y filtrado por selección también en **turnos**. |

**Decisión sobre «seleccionar todo» con paginación** (la que el informe pedía tomar antes de programar): la
casilla marca la fila visible, pero **«Marcar todo lo filtrado» marca todo lo que devuelve el filtro activo**
(búsqueda + período), no solo la página de 50. Para eso el backend envía, solo cuando se piden, los
identificadores de todo el filtro. La selección sobrevive al cambio de página y de filtro, y se limpia al
cambiar de pestaña de Registros.

**Límites conocidos:** en Caja, Movimientos y Clientes fijos la casilla aparece (todas las listas la tienen,
como en el móvil) pero la barra no ofrece exportar, porque no existe informe para ese contenido. La
exportación por selección admite como máximo 1000 elementos, igual que antes.

**Verificado:** 129 pruebas Python (`desktop/tests/`) y una prueba de humo temporal de la interfaz con jsdom
que recorre Inicio, Registros, Inventario, Ayuda y Soporte y comprueba los 31 comportamientos anteriores.
**Sin probar en Windows ni con el `.exe`**: sigue pendiente [VERIFICACION_PC.md](VERIFICACION_PC.md).
