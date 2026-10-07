# Prompt para OpenCode: SPVI 0.27.0 (retoques tras la prueba en el teléfono)

> Copia todo lo que hay debajo de la línea y pégalo en OpenCode (modo Build). Está pensado para hacerse por fases. Al terminar cada fase, OpenCode debe pasar `./gradlew spviTests`.

---

Eres el desarrollador de **SPVI**. La 0.26.0 ya se compiló, se instaló y se probó en un teléfono, y casi todo funciona. Ahora toca la **0.27.0 (versionCode 49)**: una serie de retoques de interfaz y tres funciones pequeñas.

## 0. Antes de tocar nada

1. Lee `AGENTS.md`, `Contexto.md`, `Pendiente.md` y `DESIGN_SYSTEM.md`. Las reglas de `AGENTS.md` siguen vigentes, salvo las excepciones de la sección 2 de este prompt.
2. Revisa el código **tal como está ahora**: puede haber cambiado al compilarlo con Gradle. No supongas nombres; búscalos. Puntos de partida:
   - `designsystem/.../component/{Navigation.kt, Overlays.kt, TextFields.kt, Surfaces.kt, Banner.kt}` y `designsystem/.../token/{Tokens.kt, ColorTokens.kt}`;
   - `app/.../navigation/{MainScaffold.kt, Deslizar.kt, Routes.kt}`;
   - `app/.../inicio/{InicioScreen.kt, InicioLogic.kt}`;
   - `app/.../respaldo/*`, `app/.../migrar/*`, `data/.../respaldo/{BackupCipher.kt, RespaldoRepositoryImpl.kt}`;
   - `app/.../onboarding/{OnboardingLogic.kt, OnboardingScreen.kt}`;
   - `app/.../soporte/SoporteScreen.kt`;
   - `app/.../producto/*`, `app/.../servicios/*`;
   - `app/.../vinculacion/*`;
   - `app/.../MainActivity.kt`.
3. Antes de escribir código, **dame un plan corto**: archivos que vas a tocar en cada tarea y riesgos. Espera mi «sí».

## 1. Cómo trabajar (no negociable)

- **Mantén el estilo que ya existe:**
  - componentes `Spvi*`, tokens (`SpviSpacing` xs=8/md=16/lg=24/xl=32, **no existe `sm`**), `SpviTextos`, `SpviMotion` y `SpviIcons`;
  - nada de colores, tamaños ni `fontWeight` sueltos en las pantallas;
  - Material 3 claro y oscuro, con contraste WCAG AA (si añades colores, amplía `ContrastTest`);
  - botones de solo icono con descripción y tooltip; Guardar = ✓; diálogos centrados; el «+» abajo a la derecha;
  - todo el texto en español.
- **Arreglos en la base.** Cuando sea posible, arregla en el componente del `:designsystem` y no pantalla por pantalla. Así queda uniforme en toda la app.
- **Clean Architecture:** la lógica va en ViewModels, casos de uso o funciones puras testeables, nunca en las pantallas.
- **Cambios mínimos, sin refactorizar** lo que no forma parte de la tarea.
- **Tests:** cada regla nueva lleva su test JVM. `./gradlew spviTests` siempre en verde; al final, `./gradlew spviCheck`.
- **Ambigüedades:** elige la opción más segura y simple, y anótala como «Suposición» en el informe final.

## 2. Decisiones del dueño para esta versión (ya confirmadas)

| Tema | Decisión |
|---|---|
| Contraseña del respaldo | **Opcional.** Por defecto, sin contraseña |
| Acceso con clave | **Opcional:** biometría o PIN/patrón del teléfono. Se pide **al abrir la app y tras 10 minutos en segundo plano**. Se ofrece en el recorrido inicial y se cambia en Ajustes |
| Permisos nuevos | Se autorizan **`USE_BIOMETRIC`** y **`USE_FINGERPRINT`** (los añade `androidx.biometric`; son permisos normales). Ningún otro |
| Principal → secundaria | **Nunca desde la app.** Una app principal no puede convertirse en secundaria. Para cambiar, hay que borrar los datos o reinstalar |

## 3. Tareas

### T1 · Títulos de las ventanas centrados arriba, sin icono

- **Diálogos y hojas** (`SpviDialog`, `SpviBottomSheet` y su base común en `Overlays.kt`):
  - quita el logo de la cabecera;
  - el título va arriba del todo, centrado, con `titleLarge` o el estilo que ya usen.
- **Barra superior:** `SpviTopBar` pasa a `CenterAlignedTopAppBar`. El título queda centrado y sin icono, y desaparece `showLogo`.
  - En Inicio el título es «SPVI» con la fuente de marca (`SpviMarca.fuente`), centrado y sin el logo.
  - Atrás y las acciones siguen en sus lados.
  - Un título largo baja a 2 líneas como mucho y nunca tapa las acciones.
- **Criterio:** ningún diálogo, hoja ni barra superior muestra el logo.

### T2 · Textos que no se truncan, no se parten y no se apilan

- **Busca** `maxLines = 1`, `TextOverflow.Ellipsis`, anchos o altos fijos (`width(`, `height(` con dp en contenedores de texto) y `Row` con varios `Text` sin `weight`.
- **Corrige así:**
  - el texto se ajusta a su contenedor: puede pasar a 2 líneas o más, y el contenedor crece (`heightIn(min = …)` en vez de `height`);
  - en una `Row`, `Modifier.weight(1f, fill = false)` en el texto y el resto a su tamaño;
  - las filas de botones o chips que no caben pasan a `FlowRow`;
  - ninguna palabra se parte letra a letra: da un ancho mínimo razonable o reorganiza la fila en vertical.
- **Excepción que se mantiene:** la **Descripción** de un artículo es de una línea (≤ 40 caracteres).
- **Revisa en especial** las tarjetas de Inicio (turno, alertas, accesos, Top 3, gráficos y leyendas), las filas de las listas, las fichas, Licencia, Caja (arqueo), las pestañas de Registros, los diálogos y el Stepper del recorrido inicial.
- **Criterio:** con letra al **200 %** y en una pantalla de 360 dp de ancho no hay textos cortados ni palabras partidas. Añade casos Roborazzi con `fontScale = 2f` para Inicio, la ficha de producto, Licencia y Caja.

### T3 · Top 3 de Inicio con medallas

- El número de cada puesto (1, 2, 3) va dentro de un **círculo**: oro, plata y bronce.
- **Colores:** añádelos como tokens en `ColorTokens.kt` (por ejemplo `medallaOro`, `medallaPlata`, `medallaBronce`) y dibuja el número con un color que cumpla AA sobre cada uno, en claro y en oscuro. Amplía `ContrastTest`.
- **Componente:** reutilizable, `SpviMedalla(puesto)` en el designsystem. Tamaño de 28–32 dp; el número usa el peso de dato (`SpviTextos.PESO_DATO`).
- **Accesibilidad:** «Primer puesto», «Segundo puesto», «Tercer puesto».
- Nada de líneas de rejilla ni de decoración extra.

### T4 · Animaciones sutiles, fluidas y a la frecuencia de la pantalla

- **Todas** las animaciones salen de `SpviMotion`:
  - duraciones de 150–250 ms;
  - para lo que cambia de tamaño o posición, `spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)` (añádelo a `SpviMotion`);
  - desplazamientos cortos (como mucho ¼ del contenedor) y fundidos.
- **Busca y elimina:**
  - bucles con `delay(16)` o similares que simulen frames (usa `Animatable`, `animate*AsState` o `withFrameNanos`, que siguen el refresco real de la pantalla: 60, 90 o 120 Hz);
  - animaciones que se reinician en cada recomposición;
  - `animateContentSize` dentro de las celdas de listas largas.
- **Rendimiento:** listas con `key` estable y `Modifier.animateItem()` (o `spviAnimateItem()`) solo donde aporte. Los gráficos siguen con `drawWithCache`.
- **No fuerces** la frecuencia de refresco por código (Android la elige; forzarla gasta batería). Respeta «Quitar animaciones» del sistema.
- **Fluidez en release:** añade `androidx.profileinstaller` (sin permisos) para que las Baseline Profiles de Compose se apliquen.
- **Criterio:** sin tirones al cambiar de sección, abrir diálogos, desplegar el «+» ni desplazar listas, en un teléfono de gama baja.

### T5 · Deslizar solo entre las 5 pantallas principales

- **Dónde funciona:** el gesto de `deslizarEntreSecciones` (`MainScaffold.kt`, `Deslizar.kt`) solo actúa cuando el destino actual es **exactamente** una de las rutas raíz: Inicio, Inventario, Servicios, Registros o Ajustes. Comprueba la ruta con `hasRoute(...)` sobre esas 5, **no** con la sección «seleccionada» de la barra.
- **Queda desactivado:**
  - en cualquier subpantalla (Perfil, Licencia, Respaldo, Venta, fichas, formularios, Caja, Apps vinculadas…);
  - con un diálogo, una hoja o el menú del «+» abierto;
  - en modo selección y en la selección para vender;
  - con el buscador activo o un campo de texto con foco.
- **Registros:** deslizar dentro de sus pestañas sigue funcionando como ahora.
- **Test** de la regla pura (ruta + estado → activo / no activo).

### T6 · Placeholders centrados en todos los campos de texto

- En `SpviTextField` (y en cualquier otro campo propio del designsystem), el **placeholder** va centrado: `Text(…, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())`.
- **No cambies** la alineación del texto escrito ni la de la etiqueta (anótalo como suposición).
- Comprueba que ninguna pantalla use `OutlinedTextField` o `TextField` directamente. Si alguna lo hace, pásala a `SpviTextField`.

### T7 · PDF y Excel con su icono

- Añade a `SpviIcons`:
  - `Pdf` = `Icons.Filled.PictureAsPdf`;
  - `Excel` = un icono de tabla de Material (`TableChart` o `GridOn`). **Sin logotipos de marcas.**
- Úsalos en todas las hojas y opciones de exportar: Registros, Turno, Inventario, Servicios y la ficha.
- Imagen y Tarjetas conservan sus iconos.

### T8 · Nada de textos técnicos o reveladores para el usuario

- **Recorre** todos los textos visibles: pantallas, diálogos, snackbars, notificaciones, Ayuda, Soporte, errores y los mensajes que se envían.
- **Quita o reescribe** en lenguaje llano:
  - versiones internas («P73», «0.26.0:», versionCode);
  - nombres técnicos (SHA-256, ECIES, PBKDF2, SQLCipher, token, deviceId, JSON, MediaStore, nombres de clases o archivos como `sys_…`);
  - mensajes de excepciones (`e.message`, `toString()` de errores);
  - IDs internos;
  - textos de depuración;
  - **cualquier explicación de cómo funcionan la licencia o el registro de la prueba**.
- **Aviso del permiso de fotos** (`TextosPrueba`): debe decir solo lo necesario, sin explicar el mecanismo. Por ejemplo, «Permite el acceso a fotos para que SPVI pueda conservar los datos de tu periodo de prueba». Ajusta `MANUAL_USUARIO.md` en la misma línea.
- **Errores:** siempre un texto amable y una acción («No se pudo leer el archivo. Comprueba que es un respaldo de SPVI.»). El detalle técnico no se muestra ni va a los logs con datos sensibles.
- **Se mantienen**, porque el usuario los necesita: el ID de la licencia, el vencimiento y el nº de transacción bancario.
- **Entrega:** una tabla «antes → después» en el informe.

### T9 · Una app principal nunca es secundaria

- **Interfaz:** en una app principal no aparece «Usar esta app como secundaria» ni ningún acceso a `VincularSecundaria`, tenga o no empleados.
- **Datos:** el caso de uso o repositorio de vincular como secundaria devuelve `SinPermiso` si la app es principal.
- **El tipo de app** se elige solo en el recorrido inicial. Ayuda y Manual explican que, para cambiarlo, hay que borrar los datos de la app (antes, exportar el respaldo).
- **Las secundarias** siguen sin poder crear secundarias (regla C5).
- **Tests** de las dos restricciones.

### T10 · Contraseña del respaldo opcional

- **Exportar:**
  - un interruptor «Proteger con contraseña», **apagado** por defecto;
  - apagado: no se piden contraseñas, y una línea avisa: «Sin contraseña, cualquiera con SPVI podrá abrir este archivo.»;
  - encendido: los dos campos actuales, con un mínimo de 8 caracteres.
- **Cifrado** (`BackupCipher`): sube el formato del archivo a **v4**, con un byte de indicador en la cabecera: con o sin contraseña.
  - Sin contraseña: la clave se deriva de un secreto interno ofuscado de la app (mismo AES-GCM; puedes bajar las iteraciones de PBKDF2).
  - Sigue leyendo los archivos **v3** (con contraseña).
  - `RespaldoDto` no cambia (sigue en v4).
- **Importar:** si el archivo no tiene contraseña, se importa tras la confirmación, sin pedirla. Si la tiene, se pide como ahora.
- **Migrar a otro teléfono:** mantén los **4 pasos**. El paso de la contraseña pasa a «Proteger (opcional)».
- **Tests:** ida y vuelta con y sin contraseña; un v3 sigue importándose; contraseña incorrecta = error claro; un archivo sin contraseña no la pide.
- **Documentación:** actualiza `FORMATOS.md` (cabecera v4) y `SECURITY.md` (riesgo aceptado: sin contraseña, cualquiera con SPVI puede abrir el archivo).

### T11 · Acceso con biometría o PIN del teléfono (opcional)

- **Dependencia:** `androidx.biometric:biometric` (versión estable en `libs.versions.toml`). `MainActivity` pasa a `FragmentActivity` (no hace falta AppCompat).
- **Permisos:** añade `USE_BIOMETRIC` y `USE_FINGERPRINT` a la lista de `spviPermisos` (`build.gradle.kts` raíz), al paso 9 de `tools/verificacion/verificar.sh` y a las listas de `AGENTS.md`, `README.md`, `SECURITY.md`, `RELEASE.md` y `Contexto.md`.
- **Cómo se pide:** `BiometricPrompt` con `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` en API 30+. En API 26–29, la combinación que admita la librería, con el PIN/patrón del teléfono como alternativa.
  - Si el teléfono no tiene bloqueo de pantalla, la opción aparece desactivada, con «Configura un bloqueo de pantalla en Ajustes del teléfono».
- **Cuándo se pide:** al abrir la app desde cero y al volver tras **≥ 10 minutos** en segundo plano.
  - Usa `ProcessLifecycleOwner` y `SystemClock.elapsedRealtime()`; nunca la hora de pared.
  - Al volver antes de 10 minutos (compartir un PDF, la cámara, WhatsApp) no se pide.
- **Pantalla de bloqueo:** un overlay sobre toda la app, como `BloqueoActualizacion` (Box + Surface, sin early return).
  - Lleva el botón «Desbloquear» (icono `Fingerprint` o `Lock`) y `FLAG_SECURE`.
  - **No** destruye el estado: una venta a medias sigue ahí al desbloquear.
  - Cancelar el diálogo deja la pantalla de bloqueo; no cierra la app ni la saltea.
- **Preferencia:** `accesoConClave: Boolean` en las preferencias de DataStore. **No** va en el respaldo (cada teléfono decide).
- **Dónde se activa:**
  - un paso opcional nuevo del recorrido inicial, «Acceso con clave» (en `PASOS_TOUR`, después de `TIPO_APP`; se puede saltar como los demás);
  - Ajustes → «Acceso con clave», con un interruptor que pide autenticarse antes de activarlo o desactivarlo.
- **Tests:** la regla de tiempo como función pura (arranque en frío → pide; < 10 min → no; ≥ 10 min → sí; desactivado → nunca) y el ViewModel con fakes.
- **Documentación:** README («Qué no hace» ya no dice «no tiene PIN»), MANUAL, SECURITY y Ayuda (respetando `AyudaSoporteTest`: ≤ 5 pasos de ≤ 110 caracteres, 6–12 temas).

### T12 · Iconos en los datos de Soporte

- La ficha de `SoporteScreen` (`SoporteInfo.filas`: Desarrollador, CI, Teléfono, Especialidad) pasa de etiqueta + valor sueltos a filas con **icono a la izquierda**, con el patrón de `SpviListItem`:
  - Desarrollador → `Person`;
  - CI → `Badge`;
  - Teléfono → `Phone`;
  - Especialidad → `Work`.
- **Lo que se mantiene:** el valor sigue siendo seleccionable (`SelectionContainer`) y la fuente única sigue siendo `DeveloperContact`.
- **Iconos:** añádelos a `SpviIcons` si faltan (también a `iconos.json` de `tools/capturas`).
- **Texto del pie:** ajusta «Nunca te pediremos contraseñas de respaldo…» para que siga siendo correcto con la contraseña opcional (por ejemplo, «Nunca te pediremos tus contraseñas ni datos de tu tarjeta.»).

### T13 · Foto primero en Nuevo producto y Nuevo servicio

- **Diseño:** la foto es lo **primero** del formulario, centrada arriba (miniatura de 96–120 dp con esquinas del tema y un marcador cuando no hay foto). Debajo, centrados, **dos botones de solo icono**:
  - **Cámara** (`PhotoCamera`);
  - **Galería** (`PhotoLibrary`).
  
  Con foto, hay un tercer botón «Quitar foto». Lo mismo al editar.
- **Cámara:** `ActivityResultContracts.TakePicture` con un URI de `FileProvider` en `cache/fotos/`.
  - Añade esa ruta a `file_paths.xml` y explícalo en `SECURITY.md`.
  - Como el manifiesto declara `CAMERA`, pide el permiso **justo antes** con el patrón de `app/.../common/Permisos.kt`.
- **Galería:** `ActivityResultContracts.PickVisualMedia(ImageOnly)` (Photo Picker, **sin permisos**; en Android viejos usa el respaldo que trae la librería). **No uses** `READ_MEDIA_IMAGES` para esto: ese permiso es solo para el registro de la prueba.
- **Guardado:** la imagen elegida se copia, reducida y comprimida (lado mayor ≤ 1080 px, JPEG ~85), al almacenamiento interno donde ya se guardan las fotos. Los temporales se borran.
- **Se aplica a** Producto, Elaborado, Insumo (si tiene foto) y Servicio.

### T14 · Alertas de inventario en Inicio: centradas y bien repartidas

- **Contenido:** el de cada alerta (icono, contador y texto) va **centrado**. La tarjeta mide lo que su contenido (sin alturas fijas) y el texto puede ocupar 2 líneas (ver T2).
- **Disposición equitativa:** una regla pura, testeable, que decida cuántas van por fila según cuántas haya:
  - 1 → 1;
  - 2 → 2;
  - 3 → 3;
  - 4 → 2 + 2;
  - 5 → 3 + 2;
  - 6 → 3 + 3.

  Dentro de una fila, todas tienen el **mismo ancho** (`weight(1f)`) y la **misma altura** (`IntrinsicSize.Min` en la fila). Una fila incompleta queda centrada.
- **Se mantiene:** un contador en 0 oculta su alerta.
- **Test** de la regla de reparto.

## 4. Cierre

1. Versión **0.27.0 (49)** en `app/build.gradle.kts`.
2. Documentación:
   - `README.md` (novedades de la 0.27.0, permisos y «Qué no hace»), `MANUAL_USUARIO.md`, `FORMATOS.md`, `SECURITY.md`, `DESIGN_SYSTEM.md` (medallas, motion, placeholder centrado, barra centrada, diálogos sin logo) y `Contexto.md`;
   - `Pendiente.md` (marca lo resuelto y añade lo que quede);
   - `Pruebas.md`: añade las pruebas de la 0.27.0 (bloqueo con clave, respaldo con y sin contraseña, foto con cámara y galería, deslizar solo en las 5 principales, letra al 200 %);
   - una entrada «0.27.0» en `docs/HISTORIAL_DESARROLLO.md`.
3. `./gradlew spviCheck` en verde, `./gradlew :app:recordRoborazziDebug` y luego la instalación en el teléfono.
4. **Informe final** en español:
   - tabla tarea → archivos → tests añadidos;
   - las cifras de tests por módulo;
   - la tabla de textos «antes → después» (T8);
   - las suposiciones;
   - lo que no se pudo probar.
