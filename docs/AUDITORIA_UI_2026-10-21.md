# Auditoría de interfaz — 2026-10-21 (0.32.0)

Auditoría de la interfaz completa de la app Android con la base de reglas de `ui-ux-pro-max` (toque, iconos, contraste, estados, insets, movimiento, formularios). No se propone nada sin evidencia: cada hallazgo apunta a `archivo:línea` y, cuando se pudo medir, se midió (tamaño real del nodo de semántica, píxeles de las capturas).

- **Alcance:** `:designsystem` (21 ficheros: 15 componentes, tokens, color, tema, iconos) + `:app` (20 pantallas y sus piezas) + las 274 capturas de referencia (`app/capturas/claro`, `app/capturas/oscuro`).
- **Método:** reglas de UX del skill como lista de comprobación, contrastadas con el código; las capturas se miraron y, cuando hacía falta, se midieron con un script sobre los PNG; la semántica se comprobó con `createComposeRule` (Robolectric) sobre el árbol de semántica real.
- **Lo que no se comprobó:** TalkBack en un teléfono real (la instalación por USB está bloqueada por el HyperOS del 4bc9f8f3), capturas en horizontal y en tablet (hoy las 274 son de 360×800 dp en vertical) y el teclado abierto en un dispositivo.

## Resumen

La app sale bien parada: sin colores ni tamaños sueltos, una sola familia de iconos, estados de carga/vacío/error en todas las listas, contraste verificado por test en claro y oscuro, diálogos centrados, gráficos sin rejilla y validaciones en línea. De los 7 hallazgos, **4 se corrigieron en este lote** y 3 quedan anotados (uno a decisión del dueño, dos como huecos de cobertura).

| # | Severidad | Hallazgo | Estado |
|---|---|---|---|
| 1 | Alto | La casilla de selección mide **24×24 dp** (mínimo táctil 48 dp) | **Pendiente** (a decisión del dueño) |
| 2 | Alto | **El teclado tapa la barra de acción** de Venta y Modificar venta | **Corregido** (0.32.0) |
| 3 | Medio | «Continuar» usaba el icono del método de pago (se leía como una cámara) | **Corregido** (0.32.0) |
| 4 | Medio | Las ventanas emergentes no respetan barras del sistema ni notch | **Corregido** (0.32.0) |
| 5 | Bajo | Comentario desactualizado en `Charts.kt` («300 ms») | **Corregido** (0.32.0) |
| 6 | Cobertura | No hay capturas ni tests en horizontal ni tablet | Anotado |
| 7 | Cobertura | No hay test del tamaño táctil mínimo | Anotado (depende del #1) |

## 1 · La casilla de selección mide 24×24 dp (pendiente)

`designsystem/.../component/Casillas.kt:39-51`: el lienzo es `ShalevSize.casilla` (24 dp, `token/Tokens.kt:75`) y el `toggleable` va sobre ese mismo lienzo. **Medido** con `createComposeRule`: el nodo de semántica ocupa 48×48 px a xhdpi = **24×24 dp**.

Usos donde la casilla es la que responde (`onCheckedChange` con valor): `app/inventario/InventarioScreen.kt:377`, `app/servicios/ServiciosScreen.kt:337`, `app/registros/RegistrosScreen.kt:394` y `:476`, `app/registros/ClientesFijos.kt:236`, `app/pagos/PagoElectronicoScreen.kt:209`.

Por qué importa: es el **único** punto de entrada a la selección múltiple (exportar o eliminar en lote) y está junto a una fila que abre la ficha (`InventarioScreen.kt:360`), así que fallar un dedo abre el producto en vez de marcarlo. Contradice el propio `ShalevSize.touchTarget = 48.dp`, que sí respetan los botones de icono, las filas, las pestañas y los desplegables.

Arreglo cuando se haga: `size(ShalevSize.touchTarget)` conservando el dibujo de 16 dp (el lienzo ya dibuja el cuadro centrado) y **regrabar** las capturas de Inventario, Servicios, Registros, Clientes y Pago electrónico.

**Comprobado que NO es un problema:** los controles anidados dentro de una fila pulsable **sí** son accesibles. Con una prueba temporal sobre `ShalevListItem`: el nodo «Editar Producto» ejecuta el botón del `trailing` (no la fila) y el nodo «Seleccionar Producto» ejecuta la casilla; cada uno es un nodo propio. El tamaño, eso sí, es de 24 dp.

## 2 · El teclado tapaba la barra de acción (corregido)

`MainActivity.kt:65` usa `enableEdgeToEdge`: en API 30+ la ventana no se redimensiona al abrir el teclado, así que hay que aplicar el inset del IME a mano (los formularios ya lo hacían: `ProductoFormScreen.kt:194`, `LicenciaScreen.kt:192`, `RespaldoScreen.kt:171`, `ServicioFormScreen.kt:161`, `OnboardingScreen.kt:186`, `PerfilScreen.kt:97`, `InsumoFormScreen.kt:141`).

- `app/venta/VentaScreen.kt`: sin `imePadding`; `Scaffold` 221 con `bottomBar = BarraAcciones` 224 y `PasoCliente` con 4 campos (`LazyColumn` 506). La barra «Cancelar / Confirmar» quedaba bajo el teclado.
- `app/registros/ModificarVenta.kt`: `Scaffold` 359 con los campos del cliente (421) y el motivo (424), sin `imePadding`.
- `designsystem/.../component/Buttons.kt:229`: `ShalevBarraAcciones` solo aplica `navigationBarsPadding()`.

**Arreglo:** `modifier = Modifier.imePadding()` en el `Scaffold` de las dos pantallas (sube la barra con el teclado y encoge el contenido).

## 3 · «Continuar» con el icono del método de pago (corregido)

`app/venta/VentaScreen.kt:598-603`: el botón principal del carrito era `ShalevPrimaryButton("Continuar", icon = Efectivo|Transferencia)`. `ShalevIcons.Efectivo` = `MaterialSolido.Payments` (`icon/ShalevIcons.kt:25`), que a 24 dp **se lee como una cámara** (ver `app/capturas/claro/05a_venta_carrito.png`, botón inferior). El nombre accesible sí era «Continuar», pero el icono no comunicaba la acción. En el resto de la app «Continuar» sí usa flecha (`app/common/ListasComunes.kt:121`).

**Arreglo:** `icon = ShalevIcons.Siguiente`. El método de pago elegido se sigue viendo en los chips de arriba (`SelectorMetodo`, `VentaScreen.kt:447-455`).

## 4 · Las ventanas emergentes no respetaban las barras del sistema (corregido)

`designsystem/.../component/Overlays.kt:238`: el `Dialog` se abre con `decorFitsSystemWindows = false` y el `Box` a pantalla completa solo apartaba el teclado (`imePadding`), con la tarjeta centrada y `heightIn(max = alto * 0.9)`. En **horizontal** (altura ≈ 360 dp) la hoja deja ~18 dp arriba, menos que la barra de estado (~24 dp) o el notch; la Activity compensa el recorte (`MainActivity.kt:73-76`), pero el diálogo es otra ventana.

**Arreglo:** `safeDrawingPadding()` (barras del sistema, recorte y teclado) en el `Box`. El velo oscuro sigue pintando toda la pantalla. En Robolectric los insets son 0, así que **ninguna captura cambia** por este arreglo: se nota en un teléfono real.

## 5 · Comentario desactualizado (corregido)

`designsystem/.../component/Charts.kt:45` decía «animación de entrada de 300 ms»; la entrada es un muelle sin rebote (`ShalevMotion.muelle()`, línea 106) y el tope del proyecto es 250 ms (`token/Tokens.kt:121`).

## Lo que se verificó y está correcto

- **Tipografía:** ni un `fontSize` ni un `.sp` fuera de `:app`; todo sale de `MaterialTheme.typography` y `ShalevTextos`.
- **Color:** ni un color hex en la UI. Los únicos `Color.White/Black` están en el dibujo de imágenes y QR (`app/common/ImagenTabla.kt:63`, `app/inventario/Tarjetas.kt:101,157`, `app/venta/QrCodigo.kt:54-62`).
- **Espaciado:** ni un `dp` literal en `:app` salvo el umbral del gesto (`app/navigation/Deslizar.kt:58`); la rejilla es de 8 dp (`ShalevSpacing`).
- **Iconos:** 77 iconos, todos de la misma familia (`MaterialSolido`, `designsystem/icon/ShalevIcons.kt`), sin emoji; los decorativos con `contentDescription = null` y los de acción con descripción obligatoria y tooltip (`Buttons.kt:166-202`).
- **Áreas táctiles:** botones de icono 48 dp, filas/pestañas/desplegables/acordeones ≥ 48 dp. Los de texto miden 40-48 dp (el mínimo de Material 3).
- **Contraste:** verificado por test en claro y oscuro, incluido el secundario al 65 % (`designsystem/src/test/.../ContrastTest.kt:43-101`). Medido sobre el PNG: los títulos de las dos filas de Precios son idénticos (`rgb(26,28,30)`), no hay filas «apagadas» por error.
- **Estados:** carga, vacío y error en todas las listas (`InventarioScreen.kt:233-252`, `RegistrosScreen.kt:322-331,443-447`, `ServiciosScreen.kt:212-228`, `PreciosScreen.kt:141-160`, `InicioScreen.kt:732-736,805-813`, `TurnoDetalleScreen.kt:159-160`).
- **Formularios:** etiqueta siempre visible, error en línea y al salir del campo (`TextFields.kt:41-46,85-86`), acción del teclado `Next`/`Done`, botón de limpiar con descripción.
- **Ventanas:** centradas, con pie fijo y «¿Salir sin guardar?» (`Overlays.kt:180-200`).
- **Gráficos:** sin rejilla, solo línea base, con descripción para TalkBack y leyenda con nombre y porcentaje (`Charts.kt:44-49,128`).
- **Color nunca es el único indicador:** tonos de banner con icono y texto (`Banner.kt:31-35`), alertas de stock con texto («Crítico · Vence …»), ajuste pausado con el interruptor además del gris.
- **Insets:** barras de acción con `navigationBarsPadding` (`Buttons.kt:229`, `ListasComunes.kt:114`), listas con holgura para el FAB (`ShalevSize.fabClearance`), notch en horizontal (`MainActivity.kt:73-76`).
- **Movimiento:** 150-250 ms y muelles sin rebote, todo desde `ShalevMotion` (`Tokens.kt:116-179`).
- **Ancho de lectura:** 600 dp en formularios y asistentes (`Surfaces.kt:367-368`).

## Huecos de cobertura

1. **No hay capturas ni tests en horizontal ni tablet** (las 274 son de 360×800 dp en vertical) y la app no fija orientación. Es el sitio donde se ve el arreglo #4.
2. **No hay test del área táctil mínima.** `AuditoriaUiTest` comprueba semántica (encabezado, `selected`, descripción del filtro), no dimensiones. El que faltaría depende del arreglo #1.
