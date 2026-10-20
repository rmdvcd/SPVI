# Mejoras UI móvil Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aplicar las mejoras de UI detectadas en las capturas del móvil: resúmenes en los acordeones de Inicio, selección bajo demanda en listas, y secciones agrupadas en Ajustes.

**Architecture:** Cambios solo en pantallas Compose (`:app`) y lógica de presentación existente; cero cambios en casos de uso, repositorios o base de datos. Reutilizar componentes `Shalev*` y datos que los ViewModels ya exponen.

**Tech Stack:** Kotlin, Compose Material 3, Hilt, Room. Tests JVM con `./gradlew shalevTests`.

**Spec:** Revisión de UI del 2026-10-15 (capturas en `C:\Users\666\AppData\Local\Temp\opencode\shots\and_*.png`).

## Global Constraints

- UI solo con componentes `Shalev*` de `:designsystem`; `ShalevSpacing` solo `xs/md/lg/xl` (NO existe `sm`).
- Nada de lógica de negocio en pantallas; reutilizar casos de uso/repos.
- Constructores `@Inject` sin parámetros por defecto; si cambia un constructor de ViewModel, actualizar `TurnoViewModelsTest`, `PerfilTest`, `EntornoIntegracion.kt`.
- No inventar funciones: si cambia comportamiento, actualizar `MANUAL_USUARIO.md` si aplica + entrada en `docs/HISTORIAL_DESARROLLO.md`.
- Tests sin red. Todo debe compilar y pasar `shalevTests`.
- Compilar con JDK 17 (`C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1`) y `ANDROID_HOME=$env:LOCALAPPDATA\Android\Sdk`. Versiones fijadas en `gradle/libs.versions.toml` (kotlin 2.0.21, hilt 2.52, navigation 2.8.5, coroutines 1.9.0): NO subir dependencias.
- No tocar permisos, `FLAG_SECURE`, ni decisiones del dueño (pestañas desplazables de Registros con >4 tabs: se queda como está).

## Review Focus

- Secundaria (sin `vender`/sin gráficos): los resúmenes de Inicio no deben mostrar datos que no ve; respetar el `if (!permisos.esSecundaria)` existente.
- Selección bajo demanda: la exportación («solo marcadas») y «Eliminar selección» deben seguir funcionando igual cuando hay selección activa.
- `ListaTurnos` de Registros usa selección local sin acción conjunta: no mezclarla con el nuevo modo.
- Rotación/proceso muerto: el modo selección puede perderse (como la selección actual); documentarlo, no persistirlo.
- Capturas Roborazzi existentes (`*Capturas.kt`) pueden cambiar de imagen: regenerar solo si el cambio es el esperado.

---

### Task 1: Resumen de una línea en acordeones de Inicio

**Files:**
- Modify: `app/src/main/kotlin/cu/shalev/app/inicio/InicioScreen.kt` (cabecera de `CardGrafico` ≈L835, `top/topPersonas` ≈L788/L811)
- Test: `app/src/test/kotlin/cu/shalev/app/inicio/InicioLogicTest.kt` (formato del resumen, si la lógica vive ahí; si el resumen se compone en la pantalla con `Cup.*`, el test es de `InicioLogic` donde aplique)

**Interfaces:**
- Consumes: `state.graficos: EstadoCarga<GraficosPeriodo>` (`totalVentas, ganancia`), `state.resumen: EstadoCarga<ResumenGeneral>` (`top3: List<TopItem(nombre,unidades,ingresos,ganancia)>`); formato con `Cup.enPesos()` (`InicioLogic.kt:232`); `ShalevAccordionCard(title, ...)` (mirar su firma: añadir `subtitle` o componer cabecera existente)
- Produces: cada acordeón muestra bajo el título una línea (Ventas→total, Ganancia→ganancia+margen, Inventario→nº alertas o existencias, Métodos→principal, Más vendido→`valorTop()`/`subtituloTop()` existentes `InicioLogic.kt:319-331`); secundaria sin cambios (respeta el gate de L415)

- [ ] **Step 1: Test de formato**: añadir casos en `InicioLogicTest.kt` para la función que componga el resumen (o la existente `valorTop/subtituloTop` si se reutiliza) con valores del seed (p. ej. total `51,770.00 CUP`).
- [ ] **Step 2: Correrlo y verlo fallar**: `./gradlew :app:testDebugUnitTest --tests "cu.shalev.app.inicio.InicioLogicTest"`. Expected: FAIL.
- [ ] **Step 3: Implementar** el `subtitle` en `CardGrafico`/`top` usando `state.graficos/resumen` (solo lectura, sin tocar VM).
- [ ] **Step 4: Correr tests** del paquete inicio. Expected: PASS.
- [ ] **Step 5: Commit** (`feat(movil): resumen en acordeones de Inicio`).

### Task 2: Selección bajo demanda en Inventario, Servicios y Registros

**Files:**
- Modify: `app/.../inventario/InventarioScreen.kt` (`Fila` L391-447, `Checkbox` L417-422, `BarraSeleccion` L324-343, FAB L203-211), `app/.../servicios/ServiciosScreen.kt` (L364-386, L376-380, L297-316, FAB L184-189), `app/.../registros/RegistrosScreen.kt` (`CasillaRegistro` L363-368, `BarraSeleccion` L408-429)
- Modify: `InventarioViewModel.kt` (L73/L97/L202-217), `ServiciosViewModel` equivalente, `RegistrosViewModel.kt` (L68-69/L172-183): añadir `seleccionActiva: Boolean` al estado (empieza false; true al entrar en modo; false al `limpiarSeleccion`)
- Test: `InventarioViewModelTest.kt`, `ServiciosViewModelTest.kt` (o `ServiciosAppTest.kt` según qué cubra el VM), `RegistrosViewModelTest.kt`

**Interfaces:**
- Consumes: acciones VM existentes `alternar/seleccionarTodo/limpiarSeleccion` (Registros L172-183); entran en modo: pulsación larga en fila o botón «Seleccionar» en la barra de la pantalla; salir: `limpiarSeleccion` (la `BarraSeleccion` ya la llama)
- Produces: `Checkbox`/`CasillaRegistro` visible solo si `seleccionActiva || item en seleccion`; FAB se sigue ocultando con selección (regla L205 intacta); exportar/eliminar sin cambios

- [ ] **Step 1: Tests de VM**: `seleccionActiva` empieza false; tras `alternar(id)` pasa a true; tras `limpiarSeleccion` vuelve a false (uno por VM).
- [ ] **Step 2: Correrlos y verlos fallar**. Expected: FAIL (no existe el campo).
- [ ] **Step 3: Implementar** campo + transiciones en los 3 VMs (sin cambiar constructores si es posible: si el estado es data class con defaults está permitido — la trampa Hilt es sobre constructores `@Inject`, no sobre el estado).
- [ ] **Step 4: Implementar** visibilidad condicional del checkbox + entrada en modo (long-press en `Fila` y/o botón «Seleccionar»; elegir UNO por pantalla, el mismo en las tres) en las 3 pantallas.
- [ ] **Step 5: Correr tests** de los 3 paquetes + `TurnoViewModelsTest` si se tocó un constructor. Expected: PASS.
- [ ] **Step 6: Commit** (`feat(movil): selección bajo demanda en listas`).

### Task 3: Agrupar Ajustes con cabeceras

**Files:**
- Modify: `app/src/main/kotlin/cu/shalev/app/ajustes/AjustesScreen.kt` (`AjustesContent` L150-226, `Entry`/`ShalevListItem` L267-289)
- Test: `app/src/test/kotlin/cu/shalev/app/ajustes/AjustesTextosTest.kt` (verificar que existan los títulos de sección si el test cubre textos)

**Interfaces:**
- Consumes: `ShalevListItem` existente para filas; secciones: Cuenta (Perfil, Acceso con clave), Negocio (Licencia, Apps vinculadas, Pago electrónico, Precios, Avisos), Datos (Respaldo, Migrar), Sistema (Permisos, Actualizar, Completar configuración), Ayuda (Ayuda, Soporte) + Debug (las 2 debug al final). Orden actual de filas NO cambia, solo se intercalan cabeceras (usar `stickyHeader` o item de cabecera del designsystem si existe; si no, `Text` con estilo de sección existente).
- Produces: misma lista con cabeceras de sección; secundaria sigue ocultando lo principal-only

- [ ] **Step 1: Implementar** cabeceras intercalando items en el `LazyColumn` (sin reordenar filas ni cambiar condiciones `if (principal)`/`DEBUG`).
- [ ] **Step 2: Correr tests** de ajustes. Expected: PASS.
- [ ] **Step 3: Commit** (`feat(movil): secciones en Ajustes`).

### Task 4: Verificar sin cambios (FAB, contraste, héroes)

**Files:** ninguno (solo verificación)

- [ ] **Step 1: FAB al final de lista**: con la app instalada, desplazar Inventario hasta el final y capturar: la última fila queda sobre `fabClearance=88.dp` (`InventarioScreen.kt:375`, `ServiciosScreen.kt:351`). Si se confirma, sin cambios; si tapa, subir `bottom` usando `ShalevSpacing`/`ShalevSize` existentes.
- [ ] **Step 2: Contraste**: correr `./gradlew :designsystem:testDebugUnitTest --tests "*ContrastTest*"` (los tokens ya verifican AA: `ShalevSecondaryText` al 65%, banner Aviso `D_ON_SECONDARY_CONTAINER 0xFFFFDCC2`). Expected: PASS, sin cambios.
- [ ] **Step 3: Héroes con turno abierto**: inspección en `InicioScreen.kt:421-438` confirma «Nueva venta»+«Movimiento» solo con turno abierto y `permisos.vender`; no existe «Escanear» en Inicio (cámara solo en uso). Sin cambios.

### Task 5: Cierre (docs + verificación final)

**Files:**
- Modify: `docs/HISTORIAL_DESARROLLO.md` (nueva entrada arriba); `MANUAL_USUARIO.md` solo si el modo selección cambia pasos descritos

- [ ] **Step 1: Entrada** resumiendo Tasks 1-3, fecha 2026-10-15.
- [ ] **Step 2: Suite completa**: `./gradlew shalevTests` (JDK 17, con `ANDROID_HOME` fijado). Expected: PASS.
- [ ] **Step 3: APK debug + captura** de Inicio/Inventario/Ajustes para confirmar visual.
- [ ] **Step 4: Commit** final.
