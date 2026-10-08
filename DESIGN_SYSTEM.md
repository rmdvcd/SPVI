# SPVI — Design System

Módulo `:designsystem` (`cu.spvi.designsystem`). **Regla única: ninguna pantalla define colores, tamaños, radios, duraciones ni estilos de texto propios.** Todo sale de los tokens y los componentes descritos aquí. Si hace falta algo nuevo, se añade primero al design system.

```
designsystem/src/main/kotlin/cu/spvi/designsystem/
├── token/ColorTokens.kt   ← valores ARGB crudos (única fuente; ContrastTest los verifica)
├── token/Tokens.kt        ← SpviSpacing, SpviRadius, SpviElevation, SpviOpacity, SpviSize, SpviMotion
├── theme/Color.kt         ← LightColorScheme, DarkColorScheme, SpviExtendedColors, AlertTone
├── theme/Theme.kt         ← SpviTheme, SpviTypography, SpviShapes
├── icon/SpviIcons.kt      ← iconos semánticos rellenos (+ WhatsApp vectorial)
├── component/             ← Buttons, TextFields, Surfaces, Indicators, Overlays, Navigation, Charts,
│                             Tabs, Stepper, Banner, Haptics (P18)
└── preview/SpviCatalog.kt ← catálogo vivo (Previews claro/oscuro + pantalla debug en Ajustes)
```

## 1. Tema

`SpviTheme { … }` sigue al sistema con `isSystemInDarkTheme()`. **Sin dynamic color (Material You)**: la paleta de Android 12+ cambiaría los colores de marca y el contraste AA verificado dejaría de estar garantizado.

| Rol | Claro | Oscuro |
|---|---|---|
| background | `#F8F9FA` | `#0F1318` (0.21.3; antes `#121212`) |
| surface | `#FFFFFF` | `#1A1F26` (0.21.3; antes `#1E1E1E`) |
| onSurface | `#1A1C1E` (casi negro) | `#E6E6E6` (blanco suave) |
| onSurfaceVariant | `#5F6368` (gris oscuro) | `#B3B9C2` (gris azulado claro) |
| primary / onPrimary | `#2B4FA3` / blanco | `#66D1EF` / `#00212C` |
| secondary | `#B4541A` | `#F6A15C` |
| tertiary | `#2F6F80` | `#7FC4D4` |
| error | `#C62828` | `#EF9A9A` |
| outline | `#767B82` (0.21.1; antes `#8A8F96`) | `#8C939E` (0.21.3) |

**Elevación:**
- En modo **claro** la elevación se expresa con **sombras sutiles** (`SpviElevation.low = 1dp` en las cards) y `surfaceTint = Transparent`.
- En modo **oscuro** la elevación es **tonal**: cuanto más alto el elemento, más clara su superficie, y no se usan sombras.
  - 0.21.3: todos los grises llevan un toque del azul de marca (antes neutros); la escala de luminancia es la misma.
  - `surfaceContainerLowest`: `#14181E`
  - `surfaceContainerLow`: `#1F252D`
  - `surfaceContainer`: `#242A33`
  - `surfaceContainerHigh`: `#2A313B`
  - `surfaceContainerHighest`: `#313944`
  - `outlineVariant`: `#363E4A`; ventana (`values-night/colors.xml`): `#0F1318`.

**Colores de marca:**
- El naranja `#EE7F3B` y el teal `#478EA1` de SVIPicono **no alcanzan AA con texto blanco** (2.72 y 3.72).
- Por eso solo se usan como decoración: el icono y el fondo del splash.
- Para texto y botones se usan sus variantes oscurecidas: secondary `#B4541A` y tertiary `#2F6F80`.

**Un significado por color (0.21.1):**
- `secondaryContainer` (naranja claro) = **solo avisos** (`BannerTone.Aviso`).
- `tertiaryContainer` (petróleo claro) = acciones secundarias: `IconActionStyle.Tonal` (✕ Cancelar, leer QR de vinculación, − / +) y los mini botones del menú +.
- `primary` = acción principal, chip elegido e indicador de la barra inferior. `error` = destructivo y «Sin existencia».

**Colores extendidos** (`SpviTheme.colors`):
- `alert*`: un color para cada `AlertTone` (StockBajo, StockCritico, InsumoBajo, InsumoCritico, Caducidad). `SinExistencia` (0.21.1) usa el rojo de StockCritico, pero `SpviAlertCounter` lo pinta **relleno** (`CardTone.Error`: fondo `error`, texto `onError`). `Revisar` (0.24.0, «Nombre repetido: añade descripción») no es de existencias: `SpviAlertCounter` pinta el número en `colorScheme.primary` (AA ya verificado en `ContrastTest`); no añade colores a la paleta.
- Insumo bajo: claro `#2F6F80`, oscuro `#7FC4D4` (= tertiary; 0.21.2, antes verde `#3D7A1F` / `#AED581`, que se confundía con `success`).
- Insumo crítico claro: `#9A3412` (0.21.1; antes `#B45309`, indistinguible del naranja de marca `#B4541A`).
- `chart`: claro `#2B4FA3 #B4541A #8B5E34 #A23B72 #6B6B00 #80868D`, oscuro `#66D1EF #F6A15C #D4A373 #F48FB1 #C5C56A #9E9E9E` (0.21.2: caramelo en vez de petróleo). 0.21.1: sin los tonos de las alertas (morado, verde, amarillo), comprobado en `ContrastTest.alertasDistintasDeMarcaYGraficos`.
- `success`.
- `whatsapp` (`#0B7A5E`) y `onWhatsapp`.

Se accede con `SpviTheme.colors.of(AlertTone.StockCritico)`.

### Contraste (WCAG 2.1 AA)

Mínimos: 4.5:1 para texto normal y 3:1 para elementos de UI. `ContrastTest` falla el build si algún par baja de su mínimo.

| Par | Ratio |
|---|---|
| primary / blanco (claro) | 7.64 |
| secondary / blanco | 4.97 |
| tertiary / blanco | 5.67 |
| onSurfaceVariant claro | 6.05 |
| texto secundario 65 % claro | 5.33 |
| outline (UI) sobre blanco / diálogo (`surfaceContainerHigh`) / `Highest` | 4.26 / 3.63 / 3.47 |
| error claro | 5.62 |
| primary oscuro / onPrimary | 9.52 |
| onSurface oscuro sobre `#1A1F26` | 13.27 |
| onSurfaceVariant oscuro | 8.39 |
| texto secundario 65 % oscuro | 6.33 |
| error oscuro | 7.70 |
| tonal: onTertiaryContainer / tertiaryContainer (claro / oscuro) | 10.62 / 8.64 |
| «Sin existencia» onError / error (claro / oscuro) | 5.62 / 8.23 |
| alertas claro | ≥ 5.0 |
| alertas oscuro | ≥ 6.9 |
| WhatsApp `#0B7A5E` / blanco | 5.30 |

> El verde oficial de WhatsApp `#128C7E` da 4.14 con blanco y **no pasa**. Por eso se usa `#0B7A5E`.

## 2. Tipografía (`SpviTypography`)

La escala es más pequeña que la de M3 por defecto: la interfaz es densa y de trabajo.

| Estilo | Tamaño / interlínea | Peso | Uso |
|---|---|---|---|
| headlineSmall | 20 / 26 | SemiBold | Títulos de bloqueo y onboarding |
| titleLarge | 18 / 24 | SemiBold | TopBar |
| titleMedium | 15 / 20 | SemiBold | Título de Card y diálogo |
| titleSmall | 13 / 18 | Medium | Banners |
| bodyLarge | 15 / 21 | Normal | Texto destacado |
| bodyMedium | 14 / 20 | Normal | Texto normal (P18: +1sp) |
| bodySmall | 13 / 18 | Normal | Texto secundario (P18: +1sp) |
| labelLarge | 13 / 18 | Medium | **Botones** |
| labelMedium / labelSmall | 12 / 11 | Medium | Chips y badges |
| **`SpviTextos.dato`** (0.26.0) | 13 / 18 | **SemiBold (600)** | **Datos**: precios, importes, fechas, cantidades, contadores, vencimiento e ID de la licencia |

- **Seminegrita de datos (0.26.0, P73 §5):** `SpviTextos.dato` (titleSmall en 600) es el valor de `SpviListItem` (listas, fichas, licencia), los totales parciales, el «Esperado» del arqueo, la vista previa de precios y los datos de Inicio. `SpviTextos.datoEn(estilo)` aplica el mismo peso a otro tamaño de la escala; lo usan la fecha de las filas de Registros y Turnos (`SpviListItem(tituloEsDato = true)`) y los valores de la tarjeta de licencia. Los contadores (`SpviBadge`) usan `SpviTextos.PESO_DATO`. Mismo color que el texto normal: el contraste no cambia. No se escriben `fontWeight` sueltos. En el **PDF**, las columnas de importes/números/fechas, la columna Valor de las tablas Dato/Valor y los totales van en seminegrita (Typeface 600 en Android 9+, negrita en 8.x); en **Excel**, en negrita (sigue siendo número).

- **Texto secundario:** se escribe con `SpviSecondaryText(...)`, que aplica bodySmall y `onSurface` al **65 %** de opacidad (`SpviOpacity.secondary`, dentro del rango 60–70 %).
- **Placeholder:** usa 60 % de opacidad.
- **Deshabilitado:** usa 38 %.
- Todos los tamaños están en `sp`, así que respetan el tamaño de fuente que el usuario elija en el sistema.
- **Letra grande:** `letraGrande()` es true cuando la escala de fuente del sistema es ≥ `SpviFontScale.GRANDE` (1.3). Entonces los botones no tienen alto máximo y su etiqueta puede ocupar 2 líneas, y `SpviListItem` pone el valor debajo del título.

## 3. Espaciado: rejilla de 8dp

| Token | Valor | Uso |
|---|---|---|
| `SpviSpacing.xs` | 8dp | Separación interna (icono–texto, entre chips) |
| `SpviSpacing.md` | 16dp | Padding de pantalla y de card, separación entre bloques |
| `SpviSpacing.lg` | 24dp | Pantallas completas (bloqueo, onboarding) |
| `SpviSpacing.xl` | 32dp | Solo para separaciones excepcionales |

No hay valores intermedios (4, 12, 20…).

## 4. Radios, elevación, tamaños y motion

- **Radios:**
  - `sm` = 8dp: chips y campos.
  - `md` = 12dp: botones y snackbar.
  - `lg` = 16dp: **cards**.
  - `xl` = 24dp: sheets y diálogos.
- **Elevación:**
  - `none` = 0.
  - `low` = 1dp: cards.
  - `mid` = 3dp: FAB.
  - `high` = 6dp: FAB presionado.
  - `tonalBar` = 3dp: barras inferiores fijas de Inventario y Venta.
- **Tamaños:**
  - Botón: entre 40 y 48dp de alto (`buttonMin`/`buttonMax`).
  - Campo de texto: 56dp.
  - Área táctil: 48dp como mínimo.
  - Iconos: 24dp, o 18dp en la versión pequeña.
  - Logo: 72dp en bloqueo y onboarding. **0.27.0:** ya no aparece en la TopBar ni en diálogos u hojas.
  - `fotoFormulario` = 112dp (0.27.0): foto centrada al principio de Nuevo producto/servicio (`SelectorFoto`).
  - Medalla del Top 3: 28–32dp (`SpviMedalla`).
  - Trazos internos (P19): `strokeHairline` = 1dp (línea base de gráficos), `strokeRegular` = 2dp (área y spinner), `chartBarMax` = 28dp.
  - `fabClearance` = 88dp (hueco al final de las listas con FAB), `qr` = 240dp, `fotoMiniatura` = 88dp, `campoCantidad` = 112dp, `contentMaxWidth` = 600dp (formularios y ventanas), `dialogMinWidth` = 280dp.
- **Motion** (`SpviMotion`). **0.27.0 (T4/N4):** todo sale de aquí; nada de bucles con `delay(16)`, la animación sigue el refresco real de la pantalla (60/90/120 Hz) y respeta «Quitar animaciones» del sistema.
  - Duraciones: `FAST` 150, `SHORT` 200, `MEDIUM` 250 y `LONG` 250 ms (antes 300).
  - `muelle()`: `spring(DampingRatioNoBouncy, StiffnessMediumLow)` para lo que cambia de tamaño o posición (botones con `loading`, barra inferior).
  - `ventanaEntra`/`ventanaSale` (N4): diálogos y hojas suben desde abajo hasta el centro con fundido (muelle) y, al cerrarse, bajan con fundido (200 ms); el velo aparece detrás.
  - Curva: `FastOutSlowInEasing`.
  - Entre las 5 pestañas: `screenEnter`/`screenExit` (fundido + zoom sutil).
  - Hacia un detalle y de vuelta: `screenForwardEnter/Exit` y `screenBackEnter/Exit` (deslizamiento de 1/4 + fundido).
  - Filas de listas: `listFade` y `listPlacement`, aplicados con `spviAnimateItem()`.
  - Formularios, modales y FAB: `enterVertical`/`exitVertical` (deslizamiento de ¼ + fundido).

## 5. Componentes

| Componente | Descripción |
|---|---|
| `SpviPrimaryButton` | **P24: solo icono** (`icon` obligatorio), circular de 48dp; `text` pasa a ser la descripción de TalkBack y el tooltip al mantener pulsado. **P25: sin excepciones** (también «Nueva venta»; se quitó `mostrarTexto`). Admite `loading` (spinner). |
| `SpviSecondaryButton` | Igual, con contorno y sin relleno. |
| `SpviTextButton` | Acción terciaria (p. ej. «No, nunca»): solo icono, sin fondo. |
| `SpviIconAction` | **Solo icono** en un área de 48dp. `contentDescription` es obligatorio: lo lee TalkBack y se muestra como tooltip al mantener pulsado. Estilos Standard, Tonal y Filled, o `containerColor` (WhatsApp). Con `selected` se invierte el resaltado. |
| `SpviButtonRow` | **P24:** fila de acciones **centrada** con separación media (16dp), orden Cancelar → Confirmar. Úsese también para un botón suelto: así no queda pegado a la izquierda. |
| `SpviBarraAcciones` | **P24:** barra inferior fija de los formularios (Insumo, Producto, Onboarding): una sola acción principal, centrada y al alcance del pulgar. Sustituye al «Guardar» repetido arriba y abajo. |
| `SpviTextField` | Con contorno y 56dp de alto; **P24: esquinas redondeadas** (12dp una línea, 16dp multilínea). **0.27.0 (T6):** el placeholder va **centrado** (el texto escrito y la etiqueta no cambian). Placeholder claro y **X pequeña** para borrar cuando hay texto. Muestra el error debajo, con `isError` y `errorText`. `imeAction` + `onImeAction`: «Siguiente» en los campos intermedios y «Listo» (guardar) en el último. **Regla común de validación (A16):** con `validarAlSalir = true` el error aparece cuando el campo se ha editado y pierde el foco, o con `forzarError = true` (tras Guardar/Siguiente); un campo vacío y sin foco solo se marca al forzar. Las decisiones están en `SpviValidacion` (puras, con test JVM). Sin `validarAlSalir`, `isError` se muestra tal cual (filtros, errores del servidor). |
| `SpviCard` | Radio de 16dp, padding de 16dp y elevación baja. Surface tonal Default, Tonal, Highlight o Error (0.21.1). Título opcional y `onClick`. |
| `SpviListItem` | **Indicador de color a la izquierda** (4×32dp), nombre (hasta 3 líneas desde la 0.27.0) y subtítulo, **valor alineado a la derecha** (máx. 45 % del ancho; debajo del nombre con letra grande; **P24:** si no cabe, la letra se reduce con `SpviTextoAjustable` en vez de cortarse). Admite leading, trailing y `selected`. **Regla P24 de filas:** nombre + dato principal; el subtítulo solo aparece como aviso (crítico, bajo, vence…) o, en los registros, con la fecha. El resto vive en la ficha. |
| `SpviBadge` / `SpviAlertCounter` | Contador numérico, y contador de alerta con su tono. **0.27.0 (T14):** en Inicio las alertas van centradas (icono, contador y texto) y repartidas por filas con una regla pura: 1, 2, 3 → una fila; 4 → 2 + 2; 5 → 3 + 2; 6 → 3 + 3; mismo ancho y alto dentro de la fila, fila incompleta centrada. |
| `SpviMedalla` | **0.27.0 (T3):** número del puesto (1, 2, 3) dentro de un círculo oro, plata o bronce (`MEDALLA_ORO/PLATA/BRONCE`, iguales en claro y oscuro) con el número en el tono oscuro de su familia (≥ 4.5:1, `ContrastTest`). Peso de dato. TalkBack: «Primer/Segundo/Tercer puesto». |
| `SpviChip` | FilterChip con `supportingLabel` (por ejemplo, el precio). |
| `SpviSnackbarHost` | Snackbar sobre `inverseSurface`, con vibración breve al aparecer. |
| `SpviDialog` | **Centrado**; **0.27.0 (T1):** sin logo, título centrado arriba del todo; Cancelar y Confirmar (iconos) **centrados con 16dp entre ellos**; la confirmación va rellena (Von Restorff). Con `destructive` se pinta en el color de error. **P24:** `confirmEnabled`, `confirmLoading` (spinner), `confirmTag` y `confirmIcon` evitan meter botones extra dentro del diálogo. El ancho y alto se ajustan al contenido (`IntrinsicSize`) con límites mínimos/máximos del viewport. |
| `SpviBottomSheet` | **P24: ya no es una hoja inferior sino una ventana centrada**, con contenido desplazable y **pie fijo** centrado. Su tamaño es intrínseco al contenido (`IntrinsicSize`), limitado al viewport y al teclado. Tocar fuera cierra. Con `sinGuardar = true`, tocar fuera o atrás preguntan «¿Salir sin guardar?». `enPanel = true` presenta una ficha como panel persistente junto a la lista en ventanas medianas/expandidas. Se mantiene el nombre para no tocar las llamadas. |
| `SpviComboBox` | **P24:** lista desplegable de solo lectura para elegir UNA opción (Período en Inicio). **P25:** aspecto de tarjeta tonal (radio 16dp, icono en círculo `primaryContainer`, etiqueta + valor en negrita, flecha que gira); menú con radio 16dp y la opción elegida resaltada con ✓. |
| `SpviTextoAjustable` | **P24:** texto que no se parte ni se corta: si no cabe, reduce la letra hasta 11sp; solo por debajo aparece «…». Importes de filas y celdas de Inicio. |
| `SpviIlustracionImagen` + `SpviIlustracion` | **P24:** 9 ilustraciones vectoriales (Error, SinResultados, Inventario, Elaboracion, Carrito, Registros, Precios, SinTurno, NoExiste) de **unDraw** (paquete npm `undraw-svg` 2.0.0, MIT), dentro del APK. El acento toma el `primary` del tema; en oscuro los grises se invierten. Decorativas (TalkBack no las lee). |
| `SpviFab` | FAB de una sola acción (p. ej. «Agregar servicio»), con colores y elevación del tema. **0.26.0:** abajo a la derecha en Inventario, Servicios y Precios (antes, centrado). |
| `SpviLoading` / `SpviLinearProgress` | Progreso circular (con descripción accesible) y lineal, determinado o indeterminado. |
| `SpviNavigationBar` + `SpviNavItem` | Barra inferior de 5 ítems, **solo iconos rellenos** y **resaltado invertido**: el ítem seleccionado tiene fondo primary e icono onPrimary. El nombre sale como tooltip y en TalkBack. |
| `SpviTopBar` | **0.27.0 (T1):** `CenterAlignedTopAppBar`: título centrado (máx. 2 líneas, nunca tapa las acciones), flecha atrás y acciones a los lados; sin logo (`showLogo` eliminado). Con `marca = true` (Inicio) el título «SPVI» usa la fuente de marca. |
| `SpviLogo` | SVIPicono (`R.drawable.spvi_logo`). |
| `SpviEmptyState` | Ilustración (P24, `ilustracion`) o logo, título (encabezado), detalle opcional, botón de Ayuda opcional (`ayuda`) y acción opcional. **No repite acciones que ya están en pantalla** (p. ej. el botón flotante «+»). |
| `SpviTabs` + `SpviTab` | Pestañas con rol Tab y posición («2 de 4»), resaltado invertido, número opcional («Insumos (2)») y `testTag` por pestaña. |
| `SpviStepper` | Cabecera de asistente: «Paso N de M», título, barra de progreso y flechas Atrás/Siguiente de 48dp; anuncia el paso con región viva. Sin flechas (Onboarding, Migrar, Importar) el título usa todo el ancho; con una sola, se reserva el hueco de la otra. |
| `SpviStatusBanner` + `BannerTone` | Banner Info / Aviso / Crítico con icono propio por tono (Licencia, Reloj, Alerta), texto, detalle y acción u `onClick`. Colores del tema por pares contenedor/contenido. |
| `SpviHaptics` / `rememberSpviHaptics()` | `exito()`, `error()`, `seleccion()` con `View.performHapticFeedback` (CONFIRM/REJECT en Android 11+). Sin permiso VIBRATE. |
| `spviAnimateItem()` / `Modifier.spviContentWidth()` | Animación común de filas en listas perezosas; ancho máximo centrado para formularios. |
| `SpviSecondaryText` | Texto secundario al 65 %. |
| `SpviBarChart` / `SpviDonutChart` / `SpviAreaChart` | Gráficos propios en Canvas (`component/Charts.kt`): sin líneas de cuadrícula, animación corta (el área usa `drawWithCache`: el trazo se calcula una vez y la animación solo escala) y descripción textual para TalkBack. Paleta `SpviTheme.colors.chart` (≥ 3:1 sobre la superficie, verificada en `ContrastTest`). **P24:** en las barras, la intensidad del color depende del valor respecto al máximo de la escala Y (`ChartMath.intensidad`: opacidad de 0,30 a 1, lineal). Alto `SpviSize.chartHeight` (160dp); dona de 136dp con trazo de 24dp. |

## 6. Iconografía

- **0.27.0:** `Pdf` y `Excel` (tabla, sin logotipos de marca) en todas las hojas de exportar (`iconoFormato`); `Huella`/`Bloqueo` (acceso con clave), `Desarrollador`, `Identidad`, `Telefono`, `Especialidad` (Soporte), `Camara`/`Galeria` (foto) y `ClientesFijos`.
- Todos los iconos son **rellenos** (trazados propios de Material Symbols, Apache 2.0, desde el Prompt 17) y se exponen con nombres de negocio (`SpviIcons.Inventario`, `SpviIcons.Venta`, …). Las pantallas nunca importan `Icons.*` directamente.
- **Estado seleccionado = resaltado invertido.** Vale para la barra inferior, `SpviIconAction(selected = true)` y la selección WhatsApp/SMS.
- **WhatsApp:** es un vector propio con los arcos SVG expandidos, verificado contra el SVG de origen.

## 7. SVIPicono

- **Launcher:** icono adaptativo con fondo **transparente** (P29) y capa **monochrome** para los iconos temáticos de Android 13+.
- **Splash (0.27.0, N3):** fondo del color de ventana del tema (`spvi_window_background`: claro `#F8F9FA`, oscuro `#0F1318`) con el icono; se quitó `ic_launcher_background`.
- **En la app:** `SpviLogo` (`drawable-nodpi/spvi_logo.png`, **fondo transparente** desde la 0.27.0, sin recorte circular) en bloqueo, onboarding y estados vacíos. Ya no en barras ni diálogos.

## 8. Accesibilidad

- Contraste AA verificado por test.
- Área táctil de 48dp.
- Todos los botones de solo icono llevan descripción.
- Los iconos decorativos van con `contentDescription = null`.
- Los tamaños en `sp` escalan con la fuente del sistema.
- El indicador de páginas del onboarding anuncia "Página X de N".
- `SpviLoading` anuncia "Cargando".
- Pestañas con rol y posición; asistentes y banners de Aviso/Crítico con región viva.
- Probado con escala de fuente 2.0 (`DisenoP18UiTest`).

## 9. Cómo usar

```kotlin
SpviCard(title = "Existencias") {
    SpviListItem(
        title = "Harina",
        value = "12 kg",
        indicatorColor = SpviTheme.colors.of(AlertTone.InsumoBajo),
    )
    SpviSecondaryText("Actualizado hoy")
    SpviButtonRow {
        SpviIconAction(SpviIcons.Editar, "Editar", onClick = {})
        SpviPrimaryButton("Reponer", onClick = {})
    }
}
```

Para ver el catálogo completo hay dos opciones:
- Abrir `SpviCatalog.kt` en Android Studio (Previews claro y oscuro).
- En un build debug, ir a **Ajustes → Design system**.

## Prompt 24 — botones solo icono, ventanas centradas y leyes de UX — 0.15.0

| Ley | Cómo se aplica |
|---|---|
| **Fitts** | Acciones principales abajo y grandes: botón flotante para crear, abajo a la derecha (Inventario, Servicios, **Precios**; 0.26.0), `SpviBarraAcciones` fija en los formularios, botones de 48dp. «Nueva venta» centrada en Inicio. |
| **Hick** | Menos opciones a la vista: un solo botón flotante por pestaña, sin acciones repetidas en los estados vacíos, filas con un solo dato, Período en combobox. |
| **Jakob** | Patrones de Android: crear = botón flotante, orden Cancelar → Confirmar, pestañas para varias tablas, selector de fecha del sistema. |
| **Proximidad** | Cada botón junto a lo que afecta (pegar SMS con su explicación, leer el QR dentro del flujo de vinculación); acciones del diálogo agrupadas y centradas. |
| **Von Restorff** | «Nueva venta» es el único botón relleno de la tarjeta del turno (P25: también solo icono); la confirmación de cada diálogo va rellena y el resto sin fondo. |

- **Botones:** solo icono, sin excepciones desde P25. El texto queda como descripción para TalkBack y tooltip.
- **Ventanas:** todas centradas, tocar fuera cierra, acciones centradas con 16dp de separación. El selector de fecha (`CampoFecha`) también centra Cancelar/Aceptar.
- **Pestañas:** Elaboración (Insumos/Elaborados), Registros y, nuevo, el **detalle del turno** (Resumen · Ventas · Inventario).
- **Fechas y horas:** formato del dispositivo (`Dates.formato`, `FormatoFechaDispositivo`); la guía del campo de fecha se deriva de él (`FechasUi.guia()`, p. ej. «m/d/aa»).
- **Textos:** descripciones de tarjetas y estados vacíos acortadas a una frase; los textos ya no nombran botones por su etiqueta («toca el botón de pegar»).


## Ajustes 0.15.1

- `SpviPrimaryButton`: se elimina `mostrarTexto`; todos los botones son solo icono.
- `SpviComboBox`: superficie tonal con radio 16dp (`surfaceContainer` en claro, `surfaceContainerHigh` en oscuro) y borde `primary` de 2dp mientras está abierto. El icono va en un círculo `primaryContainer` de 40dp; la etiqueta es `labelMedium` y el valor `titleMedium`. La flecha `SpviIcons.Desplegar` gira 180°. El menú tiene radio 16dp y la opción elegida lleva fondo `primaryContainer` y ✓.
- `SpviIcons.Guardar` = `SpviIcons.Confirmar` (✓); se elimina el trazado `Save`. (En la 0.18.3 pasó a ser un disquete; la 0.18.4 lo devuelve a ✓ por decisión del usuario.)

## Ajustes 0.16.0

- Sin componentes nuevos. La fila de Elaborado (`SpviListItem`) usa `value = textoAlcance(n)` y `indicatorColor = error` cuando no alcanza. La fila del Inventario aplica `TonoFila.PELIGRO` con `alcanza == 0`.
- Se retira el diálogo Producir (era un `SpviDialog` con campo de unidades); no queda ningún uso de `SpviIcons.Agregar` como acción de fila en Elaboración.

## Ajustes 0.16.1

- `SpviListItem(valueMaxLines = 1, subtitleMaxLines = 1)`: si un texto debe leerse entero, pasa a varias líneas en vez de cortarse. Se usan 2 líneas en las filas de Elaborados y en el carrito, y 4 en las fichas. Los importes cortos siguen en 1 línea y reducen la letra (`SpviTextoAjustable`).

## Ajustes 0.17.0 — centrado y totales

- **`LocalSpviCentrado`** (`Surfaces.kt`): `SpviCard` y el cuerpo de `SpviVentana` lo ponen a `true` y centran su columna (`CenterHorizontally`). Con él, `SpviSecondaryText` sin `textAlign` explícito se centra. `SpviListItem` lo vuelve a poner a `false` dentro de su fila, así que subtítulos y valores de filas no cambian. Si un texto debe ir a la izquierda dentro de una tarjeta (p. ej. junto a un icono), se pasa `textAlign = TextAlign.Start`.
- **`SpviCard(titleCentered = true)`** es ahora el valor por defecto.
- **Párrafos sueltos** (`bodyMedium` fuera de tarjetas) y **títulos de sección**: `textAlign = Center` + `fillMaxWidth()`.
- **Contadores con acción** («N elegidos» + icono, chips de filtro): una fila `fillMaxWidth` con `spacedBy(md, CenterHorizontally)`, sin `weight`, para que vayan centrados como grupo.
- **Total único**: en cada pantalla o ventana con un importe total, se muestra una sola vez, centrado arriba: etiqueta `SpviSecondaryText` y valor `SpviTextoAjustable` en `headlineSmall` negrita, agrupados para TalkBack (`mergeDescendants`). Lo usan Venta (`TotalVenta`, etiqueta «Total estimado», «Total» o «Importe a transferir» según el paso), la ficha de Registros y el detalle de turno («Total vendido»). No se repite en tablas ni en títulos.
- **Barra de Venta**: Cancelar (✕, tonal) y Confirmar (✓, relleno), solo iconos, centrados con `SpviBarraAcciones` (16dp entre ellos). El nombre de la acción va en la descripción de TalkBack y en el tooltip.
- **`Equitativo`** (`OnboardingScreen.kt`): `Arrangement.Vertical` que reparte el mismo espacio entre los elementos y en los bordes; si no caben, deja 16dp entre ellos y la columna se desplaza.
- **Una alerta sola** en Inicio va centrada con el mismo ancho que cuando son dos.
- La lista de Pago electrónico ya no usa la barra lateral (`indicatorColor`) para «En uso»: lo indican el fondo seleccionado y la etiqueta.

## Ajustes 0.27.0

- **T1** títulos centrados sin logo (TopBar, diálogos, hojas). **T2** textos que crecen en lugar de cortarse; capturas con letra al 200 % (`LetraGrande` en las pruebas de capturas). **T3** `SpviMedalla`. **T4/N4** motion (arriba). **T6** placeholder centrado. **T7** iconos PDF/Excel. **T14** reparto de alertas. **N3** logo transparente.
- **Clientes fijos (N2):** casilla «Cliente fijo» como `SpviListItem` con `Checkbox` a la izquierda; sugerencias (hasta 3) como filas con icono de persona justo debajo del nombre; pestaña «Clientes» en Registros con ficha en `SpviBottomSheet` y «Quitar» con `SpviDialog` destructivo.
