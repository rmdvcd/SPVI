# SPVI — Interfaz, experiencia e interacción (UI · UX · IX)

Decisiones de diseño de la versión **0.19.3**: sistema de diseño, criterios de experiencia e interacción, cambios respecto a versiones anteriores y lo que queda pendiente. La referencia de tokens y componentes está en [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md).

**Usuario objetivo:** dueño o empleado de un pequeño negocio en Cuba, sin conocimientos técnicos, con un teléfono Android de gama media o baja y conexión cara o inestable.

## 1. Design system

Módulo `:designsystem`. **Regla única: ninguna pantalla define colores, tamaños, radios, duraciones ni estilos de texto propios.** Si hace falta algo nuevo, se añade primero al design system.

| Pieza | Resumen |
|---|---|
| Tema | Material 3, claro y oscuro según el sistema. **Sin color dinámico** (Material You), para mantener la marca y el contraste verificado |
| Color | primary `#2B4FA3` / `#66D1EF` (oscuro); secondary `#B4541A`; tertiary `#2F6F80`. El naranja `#EE7F3B` y el teal `#478EA1` del icono solo decoran (no alcanzan AA con texto blanco) |
| Colores de alerta | Uno por tipo: stock bajo, stock crítico, insumo bajo, insumo crítico, caducidad (`AlertTone`) |
| Elevación | Claro: sombras sutiles (cards 1dp). Oscuro: elevación tonal, sin sombras |
| Tipografía | Escala compacta para una interfaz de trabajo (cuerpo 14sp, secundario 13sp, títulos 15–20sp), toda en `sp`. Con letra del sistema ≥ 130 % (`letraGrande()`), los botones crecen en alto y las filas apilan el importe bajo el nombre |
| Espaciado | Rejilla de 8dp: 8 / 16 / 24 / 32, sin valores intermedios |
| Radios | 8 (chips, campos) · 12 (botones) · 16 (cards) · 24 (hojas y diálogos) |
| Tamaños | Botones 40–48dp de alto (sin máximo con letra grande), campos 56dp, área táctil ≥ 48dp, iconos 24/18dp, QR 240dp, miniatura 88dp, hueco del FAB 88dp, ancho máximo de formularios 600dp |
| Movimiento | 200 / 250 / 300 ms, `FastOutSlowInEasing`. Fundido entre las 5 pestañas; al abrir un detalle la pantalla entra desde la derecha (1/10 + fundido) y al volver, al revés. Filas de listas con `spviAnimateItem()`; gráfico de área que crece desde abajo sin recalcular el trazo (`drawWithCache`) |
| Iconos | Siempre rellenos y con nombre de negocio (`SpviIcons.Venta`…). Las pantallas no importan `Icons.*` |
| Componentes | Botones (primario, secundario, de texto, solo icono), campo de texto con borrado y acción del teclado (`imeAction`), card, fila de lista con indicador de color, chip, **pestañas** (`SpviTabs`), **cabecera de asistente** (`SpviStepper`), **banner de estado** (`SpviStatusBanner`: Info / Aviso / Crítico), contador de alerta, snackbar con vibración breve, diálogo con logo, hoja inferior con pie fijo y protección de cambios, FAB simple y expandible, progreso, barra inferior, barra superior, estado vacío con Ayuda y gráficos de barras, dona y área. Vibración: `SpviHaptics` (sin permiso VIBRATE) |
| Catálogo | `SpviCatalog.kt` (Previews claro y oscuro) y, en debug, **Ajustes → Design system** |
| Verificación | `ContrastTest` falla el build si un par de colores baja del mínimo AA |

## 2. Decisiones

### 2.1 Experiencia (UX)

| Decisión | Motivo |
|---|---|
| **5 destinos en la barra inferior**, solo iconos con resaltado invertido | Requisito de SPVI.txt. El nombre sale en el tooltip y en TalkBack |
| **Nada bloquea salvo la licencia** | El asistente se puede omitir paso a paso o entero; lo pendiente se retoma en *Ajustes → Completar configuración* |
| **Valores recomendados** si se omiten los avisos: 5 (bajo) / 1 (crítico) | El usuario no tiene que entender los umbrales para empezar |
| **Permisos en contexto:** cámara al tocar Escanear, con explicación previa; internet con consentimiento propio (*Preguntar / Permitidas / Nunca*) | Pedir solo cuando se entiende para qué; siempre hay alternativa manual |
| **Sin turno no se vende**, con bloqueo explicado y botón «Abrir turno» | Las cuentas del turno cuadran; abrir siempre es una acción explícita |
| **Venta en pasos cortos:** carrito → comprobante (efectivo) o QR → datos del cliente (transferencia). La venta se registra solo al confirmar | Se puede cancelar sin dejar rastro |
| **Un Elaborado se vende directamente desde sus insumos** (0.16.0): sin paso de producción ni existencias propias; se muestra «Alcanza para N» | El vendedor no prepara nada antes en la app; ve de un vistazo cuántos puede vender |
| **Lenguaje llano:** errores con qué pasó y qué hacer, sin jerga técnica | Usuario sin conocimientos previos |
| **Exportar «lo que se ve»** (búsqueda y filtro aplicados, indicados en el título) | Lo que el usuario ve es lo que recibe |
| **Datos para clientes vs. datos internos:** imagen, tarjetas y texto de una venta sin costo ni existencias; carné enmascarado en el texto | Se comparte sin revelar márgenes ni datos personales completos |
| **Respaldo explicado antes de pedir la contraseña:** qué contiene, si llegó completo, qué se va a reemplazar | Evita confundir un archivo cortado con una contraseña incorrecta |
| **Bloqueo por licencia abre el panel de Licencia directamente** | Una pantalla menos; la solución está a la vista |
| **Licencia en 3 pasos** (Revisa tus datos → Pide la licencia → Activa la licencia) con atajo «Ya tengo el mensaje de licencia» | Un objetivo por pantalla; quien ya recibió la respuesta va directo a Activar |
| **Respaldo en pasos:** exportar = contraseña → dónde guardarlo; importar = archivo (se comprueba) → contraseña → confirmar | La contraseña se valida antes de elegir destino; el diálogo de importación dice en qué paso está |
| **El banner de licencia avisa con tiempo:** tono Info (> 7 días), Aviso (≤ 7) y Crítico el último día, con qué hacer | El bloqueo deja de llegar por sorpresa |
| **Registros recuerda la pestaña, la búsqueda y los filtros** aunque el sistema cierre la app | Volver a la app no obliga a filtrar otra vez |
| **Desde un estado vacío se llega a la Ayuda** (Inventario, Elaboración, Registros) | Quien no sabe empezar tiene la explicación a un toque |

### 2.2 Interacción (IX)

| Patrón | Dónde |
|---|---|
| **Seleccionar ≠ abrir:** casilla para seleccionar, toque en la fila para ver la ficha | Inventario, Insumos, selección de venta |
| **Barra de selección** «N seleccionados» con Marcar todo, Exportar, Eliminar y Quitar selección; la selección sobrevive a búsquedas y filtros | Inventario, Insumos |
| **Buscador sin tildes ni mayúsculas** («cafe» encuentra «Café») | Inventario, Insumos, Registros |
| **Filtro en hoja inferior** con distintivo del número de filtros y chip «Quitar filtro» | Inventario, Insumos, Registros |
| **Estados completos:** Cargando, Vacío (con acción), Sin resultados (con Quitar filtros), Error (con Reintentar) | Todas las listas |
| **Confirmación solo en lo destructivo o irreversible:** cerrar turno, eliminar, descartar venta, importar, borrar teléfono (escribiendo BORRAR) | — |
| **Preguntar antes de salir** con cambios sin guardar (flecha, botón atrás del sistema; en hojas, también deslizar o tocar fuera). Cancelar en el pie de una hoja es explícito y no pregunta | Perfil, Producto, Insumo, hojas de Precios y de Pago electrónico; Venta («¿Descartar la venta?») |
| **Validación con una sola regla (A16):** el error de un campo aparece cuando lo has **editado y sales de él**, o al pulsar Guardar/Siguiente; desde ese momento se actualiza en tiempo real y desaparece en cuanto el valor es válido. Mientras escribes no hay rojo; un campo vacío solo se marca al guardar. Los botones − / + cuentan como edición terminada. Implementada en `SpviTextField` (`validarAlSalir`, `forzarError`) | Producto, Insumo, Perfil, Pago electrónico, Precios, Venta→Cliente, Onboarding, Licencia, Respaldo, Escáner |
| **Teclado que avanza:** «Siguiente» pasa al campo siguiente y «Listo» en el último guarda (o confirma) | Producto, Insumo, Perfil, datos de Licencia, contraseñas del Respaldo |
| **Vibración breve** al leer un código, al terminar una venta y con cada snackbar; respeta el ajuste del sistema | Escáner, Venta, todos los mensajes |
| **Asistentes en la misma pantalla** con `SpviStepper` («Paso N de M: título» + barra): Licencia 3 pasos; Respaldo Exportar 3 (Contraseña → Destino → Respaldo listo) e Importar 4 (Elegir → Comprobar → Contraseña → Confirmar); Onboarding; Migrar 4 (la cabecera indica el paso en curso; las 4 tarjetas siguen visibles) | Licencia, Respaldo, Onboarding, Migrar |
| **Progreso de la operación por etapas** («Etapa 1 de 3 · Reuniendo los datos…»), con las demás acciones deshabilitadas. «Etapa», no «Paso», para no confundirlo con el asistente | Respaldo |
| **Pegar solo al tocar «Pegar»**; compartir a SPVI desde otras apps (SMS, `.spvi`) | Venta, Licencia, Respaldo |
| **Eventos de un solo uso** (snackbar, abrir otra app) separados del estado | Todos los ViewModels |
| **Estado que sobrevive** a girar la pantalla y a la muerte del proceso (`SavedStateHandle`) | Carrito y paso de la venta, período de Inicio, pestaña/búsqueda/filtros de Registros |

### 2.3 Accesibilidad

- Contraste **WCAG 2.1 AA** verificado por test (texto ≥ 4.5:1, elementos de UI ≥ 3:1, gráficos ≥ 3:1).
- Área táctil ≥ 48dp; todos los botones de solo icono tienen descripción y tooltip.
- Texto en `sp`: respeta el tamaño de letra del sistema.
- Indicadores de color siempre acompañados de texto o descripción (estado de stock en TalkBack, contadores con número).
- Interruptor de turno con rol `Switch` y región viva; temas de Ayuda con estado «Abierto/Cerrado»; onboarding anuncia «Página X de N»; gráficos con descripción textual.
- Iconos decorativos con `contentDescription = null`.
- **Letra grande** (probado con escala 2.0): etiquetas de botón en hasta 2 líneas sin altura máxima; filas con el importe debajo del nombre; pestañas en 2 líneas.
- **Pestañas reales** en Elaboración y Registros: TalkBack dice «pestaña, seleccionada, 2 de 4».
- **Asistentes** con región viva: al cambiar de paso se anuncia «Paso 2 de 3: Pide la licencia».
- **Banner de licencia:** cada tono lleva icono y texto propios (nunca solo color); Aviso y Crítico se anuncian.
- **Datos sensibles:** el paso QR de la venta (tarjeta del vendedor) también usa FLAG_SECURE.
- Edge-to-edge estándar: no se ocultan ni se alteran las barras del sistema ni el notch.

## 3. Antes / después

| Tema | Antes | Después (0.14.0) | Fase |
|---|---|---|---|
| Gráficos | Librería Vico prevista (sin gráfico de dona) | Gráficos propios en Canvas: sin cuadrícula, con descripción accesible, sin dependencia extra | Prompt 6 |
| Verde de WhatsApp | `#128C7E` oficial (4.14:1, no pasa AA) | `#0B7A5E` (5.30:1) | Prompts 1–2 |
| Nueva venta con turno cerrado | Abría el turno de forma implícita | Bloqueo explicado y botón «Abrir turno» | Prompt 7 |
| Alertas de insumos | Lista provisional `AlertaListaScreen` | Elaboración con el filtro ya aplicado | Prompt 10 |
| Etiquetas de movimiento | «Produccion» sin tilde en PDF/Excel | «Producción» en un solo sitio del dominio | Prompt 11 |
| Vencimiento de licencia | Pantalla intermedia de bloqueo | Se abre directamente el panel de Licencia | Prompt 12 |
| Perfil | Marcador de posición | Nombre, apellidos, CI, teléfonos y tarjetas con validación; tarjetas enmascaradas | Prompt 12 |
| Exportar como imagen | Imagen = tarjetas de 6 productos | Imagen = lista de precios (25 filas por imagen); Tarjetas aparte | Prompt 14 |
| Registros | Solo compartir texto | Además, Exportar PDF y Excel de lo que se ve | Prompt 14 |
| Importar respaldo | Solo desde el selector de archivos | También con Compartir y Abrir con → SPVI | Prompt 14 |
| Respaldo dañado | «Contraseña incorrecta o archivo alterado» | Se distingue archivo incompleto, dañado o de otra app **antes** de pedir la contraseña; progreso por pasos | Prompt 14 |
| Clave del emisor, tasas de cambio, botón Enzona, caducidad por código | Pantallas o datos sin uso real | Eliminados; respaldo siempre completo | Prompt 17 |
| Iconos | `material-icons-extended` (≈ 2 300 iconos) | `SpviIcons` propios, solo los usados | Prompt 17 |
| Pestañas | Chips que TalkBack leía como «botón» | `SpviTabs` con rol de pestaña y posición | Prompt 18 |
| Licencia | Todo en una pantalla larga | Asistente de 3 pasos con atajo a Activar | Prompt 18 |
| Respaldo | Contraseña y botones a la vez | Exportar en 3 pasos (con «Respaldo listo») e importar en 4, en la misma pantalla | Prompt 18 (cierre) |
| Validación | Unos formularios al escribir, otros solo al guardar, Pago con heurística de longitud | Una regla para todos: al salir del campo editado o al guardar | Prompt 18 (cierre, A16) |
| Onboarding y Migrar | Texto «Paso N de M» propio / tarjetas numeradas sin cabecera | `SpviStepper` como Licencia y Respaldo | Prompt 18 (cierre, L9) |
| Animación de filas | Sin animación en el carrito ni en las listas de Pago electrónico | `spviAnimateItem()` también ahí | Prompt 18 (cierre, A18) |
| Ancho máximo | Solo Producto, Insumo, Licencia y Respaldo | También Onboarding, Perfil, Pago electrónico, Escáner, Venta, Migrar y Soporte | Prompt 18 (cierre, A22) |
| Banner de licencia | Mismo aspecto con 6 meses o 1 día | Tonos Info / Aviso / Crítico con icono y qué hacer | Prompt 18 |
| Salir sin guardar | Solo en Perfil | Formularios de Producto e Insumo y hojas de Precios y Pago | Prompt 18 |
| Letra grande | Botones de 48dp máx. que cortaban el texto | Botones y filas que crecen; probado con escala 2.0 | Prompt 18 |
| Medidas sueltas | 14 valores `dp` en pantallas | 0: todo con tokens (`SpviSize`, `SpviRadius`, `SpviElevation`) | Prompt 18 |
| Ayuda | 6 textos con controles que no existían | Textos con las etiquetas reales | Prompt 18 |

## 4. Pendientes

Los textos que no coincidían con la interfaz (Ayuda y aviso de turno cerrado) se corrigieron en el Prompt 18. Las «funciones sin pantalla» se resolvieron en el Prompt 17: el tema sigue siempre al sistema, los días de aviso de caducidad (7) y el tiempo de espera de red (5 s) son constantes, y las tasas de cambio y `TextoWriter` se eliminaron.

- **Fotos fuera del respaldo:** en otro teléfono los productos importados aparecen sin foto.
- **Contenido de la Ayuda en la app:** 10 temas fijos en `AyudaContenido`; no hay búsqueda ni imágenes.
- **Deshacer:** las acciones destructivas usan confirmación, no «Deshacer» en el snackbar. Decisión confirmada al cerrar el Prompt 18 (pregunta 4, opción 2): más simple y sin estados intermedios en la base de datos.
- **Tests de interfaz e integración:** compilados, pero no ejecutados en dispositivo dentro de este entorno. El esquema de Room se comprueba con `EsquemaTest` (Prompt 17); las bases v1/v2 se recrean (`fallbackToDestructiveMigrationFrom(1, 2)`).
- **Medir recomposiciones:** no hay un test automático que cuente recomposiciones; el banner y los gráficos usan claves estables y `drawWithCache`, revisado a mano.

## 5. Cierre del plan del Prompt 18 (respuestas a las preguntas de la auditoría)

| Pregunta | Respuesta | Resultado |
|---|---|---|
| 1. Orden con el Prompt 17 | **A:** primero las fases A–B del Prompt 17 | Ya aplicado así (0.13.0 → 0.14.0); A05 resuelto |
| 2. Tipografía (A24) | **Opción 2:** `bodyMedium` 14sp y `bodySmall` 13sp | Aplicado; comprobado por `P18ComponentesTest.tipografiaDeLecturaSubida1sp` |
| 3. Asistentes de Licencia y Respaldo | **Opción 1:** 3 o 4 pasos en la misma pantalla, sin rutas nuevas | Licencia 3; Respaldo Exportar 3 e Importar 4; también Onboarding y Migrar con `SpviStepper` |
| 4. Deshacer | **Opción 2:** solo confirmación | Sin «Deshacer»; 0 cambios en la base de datos |

Hallazgos A01–A24: todos cerrados. A21 queda resuelto por decisión (pregunta 4) y A05 por el orden de fases (pregunta 1).


## Prompt 24 (0.15.0)

Botones solo icono (en 0.15.1 también «Nueva venta»), todas las ventanas centradas con acciones centradas, pestañas en el detalle del turno, ilustraciones en los estados vacíos y de error, filas con un solo dato, campos redondeados y formato de fecha del dispositivo. La aplicación de Fitts, Hick, Jakob, Proximidad y Von Restorff está en [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md#prompt-24--botones-solo-icono-ventanas-centradas-y-leyes-de-ux--0150) y el detalle pedido por pedido, en [docs/HISTORIAL_DESARROLLO.md](docs/HISTORIAL_DESARROLLO.md).

## Ajustes 0.15.1

- **Nueva venta:** solo icono (carrito relleno), centrada junto a Escanear (tonal), con 24dp entre ellas. Ya no hay ningún botón con texto.
- **Período:** `SpviComboBox` con aspecto de tarjeta tonal, igual que los accesos de Inicio. El icono va en un círculo y encima se ve la etiqueta con el valor en negrita; la flecha gira al abrir. El menú tiene radio 16dp y la opción elegida resaltada con ✓.
- **Guardar = Confirmar (✓):** un solo icono para «aceptar» en toda la app (Jakob).
- **Registros identificados por fecha:** Ventas «01/10/2026 14:32», Transferencias fecha + cliente, Movimientos fecha + artículo, Turnos «30/09/2026» + horas · persona. Los títulos pasan a «Venta del …», «Transferencia del …», «Movimiento del …» y «Turno del …». Ningún id interno se muestra, se comparte ni se exporta. El Nº de transacción bancario sí se mantiene: es un dato del banco.

## Cambios 0.16.0 — Elaborados sin producción

- **Sin paso intermedio:** desaparece Producir. Un Elaborado se vende mientras alcancen sus insumos, y al registrar la venta se descuentan en la misma transacción.
- **«Alcanza para N»** es el dato principal de un Elaborado en Elaboración, Inventario (fila y ficha) y el carrito, con el mismo texto en todas partes (`textoAlcance`). Con 0 se lee «Sin insumos suficientes» y el tono es de peligro (el texto acompaña al color).
- **Avisos solo de insumos:** un Elaborado nunca cuenta como stock bajo ni crítico.
- **Formulario más corto (Hick):** un Elaborado ya no pide Cantidad ni niveles; Precio de venta cierra el teclado con ✓.
- **Venta:** el «+» del carrito se desactiva al llegar a lo que alcanza. En modo selección, un Elaborado sin insumos no se puede marcar y se explica por qué.
