# Auditoría crítica — SPVI 0.30.0

**Fecha:** 2026-10-09 · **Base:** `main` @ `331a9cde` (merge de la PR #7) · **App:** 0.30.0, `versionCode 51`
**Método:** lectura del árbol completo, conteos medidos con `grep`/`find` sobre los `src/main` versionados, y consulta de la CI real de GitHub (runs + check-run annotations). Una prueba de concepto criptográfica ejecutada en Python.
**Límite declarado:** en este entorno no hay JDK ni Android SDK ni acceso a Maven/Google. **No se compiló ni se ejecutó nada de la app.** Todo lo relativo a builds, tests y tamaños de APK viene de la API de GitHub o de `docs/VERIFICACION.md` (se indica cuál). Nada de esto es medición propia con Gradle.

> Nota de honestidad: este informe cita rutas y líneas concretas. Cada una se verificó contra el árbol actual. Donde no pude verificar, lo marco 🧪.
>
> Los recuentos se midieron dos veces: la primera pasada dio cifras infladas o mal atribuidas (número de tests, avisos de Lint, índices de Room, líneas de ficheros, algún archivo citado que no existe). Los de aquí son los de la segunda pasada, medidos con `grep -o | wc -l` y `find` sobre el árbol del commit indicado, con cada ruta comprobada una a una. Aun así: si una cifra tuya no coincide, la fuente de verdad es el código, no este archivo.

---

## 0. Veredicto en cinco líneas

1. **La ingeniería del núcleo es de nivel alto**: dominio puro, dinero en centavos con desbordamiento detectado, transacciones atómicas, criptografía bien armada, 996 tests JVM.
2. **`main` está en rojo**: 6 tests fallando en el commit actual, y se fusionaron dos PRs sabiendo que la verificación pendiente no se había ejecutado.
3. **Se añadió una regla de negocio dura (precio > costo) que puede parar una caja registradora**: rompió el generador de datos de prueba del proyecto (arreglado en `06ac298` mientras se escribía esto, §1.1) y sigue bloqueando en la cotización sin prueba que lo cubra.
4. **La seguridad tiene un agujero estructural y documentado como "aceptado"**: el respaldo "sin contraseña" es legible por cualquiera; lo demostré descifrando un archivo con datos publicados en el repositorio.
5. **Nunca se instaló en un teléfono y no hay ni un release publicado**: la app está, literalmente, sin validar en el único entorno donde importa, y su mecanismo de actualización apunta a un sitio vacío.

Es un proyecto con muy buena cabeza y malos cierres. Lo peor que tiene no es un bug: es la distancia entre lo que sus documentos afirman y lo que el repositorio sostiene.

---

## 1. Números medidos hoy

| Métrica | Valor |
|---|---|
| Módulos | 6: `:app`, `:designsystem`, `:data`, `:domain`, `:licencia`, `:core` |
| Archivos `.kt` versionados | 445 (272 en `src/main`) |
| Líneas Kotlin | 59,009 totales · 39,789 en `src/main` |
| `@Test` en `src/test` | **996** en el commit auditado (app 519 · domain 240 · licencia 99 · data 86 · designsystem 33 · core 19), de los que **143 son capturas** → 853 corren en CI. En `06ac298` son **999 − 143 = 856**, que es exactamente la cifra que reporta el `spviTests` del dueño: el conteo cuadra con el runner |
| `@Test` en `androidTest` | 144 — **el CI solo los compila, nunca los ejecuta** |
| Literales de cadena en UI | 2,865 en `app`+`designsystem` (`src/main`) · `stringResource`: **0** · `strings.xml`: **4 cadenas** |
| `runCatching` / `catch (e: Exception)` / `getOrNull()` / `!!` / `lateinit` en `src/main` | 113 / 40 / 51 / 33 / 8 |
| `@Preview` | 2 en todo el proyecto |
| Room | `SpviDatabase.kt:73` `VERSION = 11`, esquemas 5.json–11.json, 17 `Index(` en `Entities.kt`, 9 `@Transaction`, 135 `@Query`, 0 `@RawQuery`, 10 `LIMIT` (todos `LIMIT 1` de lookups, ninguno en las consultas de período) |
| Capa de UI más grande | `InicioScreen.kt` 870 l · `VentaScreen.kt` 602 · `InventarioScreen.kt` 572 · `RegistrosScreen.kt` 545 · `VinculacionScreen.kt` 528 · `OnboardingScreen.kt` 494 · `LicenciaScreen.kt` 489 · `AjustesScreen.kt` 267 |
| En GitHub | 0 releases · 0 tags · licencia `NOASSERTION` · 1 autor · CI **failure** en `331a9cd` y **success** en `06ac298` (1,5 h después) |
| Peso versionado innecesario | `app/capturas/` 21 MB (284 PNG) · `docs/HISTORIAL_DESARROLLO.md` 168 KB · `.kotlin/sessions/…salive` (0 B) |
| APK | debug 58,8 MB y release 45,4 MB (`docs/HISTORIAL_DESARROLLO.md`, verificación del 2026-10-09 en el PC del dueño; la release firmada sigue sin comprobarse) |
| Herramientas de calidad | `lint` con baseline vacío deliberado: 0 errores y **95 avisos** en `331a9cd`, **127 avisos** en `06ac298` — el baseline se mantiene vacío a propósito, pero el ruido sube sin que nadie lo triaje · **sin Kover/JaCoCo: nadie sabe la cobertura real** · sin `ktlint`/`spotless` · sin macrobenchmark · sin baseline profile |

### 1.1 Y mientras se escribía esto, `main` se movió

Este informe se midió sobre `331a9cd`. A las 04:40 de hoy el dueño empujó `06ac298` —**con mensaje de commit «1»**— y cerró varios de los huecos de abajo. Conviene decirlo aquí, porque cambia el estado de tres hallazgos y confirma otros dos:

- **El seed ×1000 está arreglado** (venía de `c2d3cf6`, rama `arena/3c116bea-spvi`, es decir: llevaba días esperando fusionarse). Las recetas pasan a gramos/mL (`harina-trigo 200_000 → 200`, `gasolina 500_000 → 300`) y, sobre todo, aparecen dos invariantes nuevas en `CatalogoBodegaTest`: `productosConCostoMenorQueVenta` y `recetasConCostoMenorQueVenta`. **Esa es la forma correcta del arreglo**: no tocar los números y ya, sino dejar un test que no vuelve a dejar pasar la incoherencia.
- **La CI se pone verde parcheando los fixtures, no la regla.** El propio `HISTORIAL_DESARROLLO.md` lo dice literal: «Siguiendo la revisión, se actualizaron los fixtures para esperar rechazo, no la regla». `PlanificadorVenta.kt:87` y `Validadores.preajuste` **no se tocaron** (verificado con `git diff --name-only 331a9cd..origin/main`). Los casos nuevos `VentaFlujoTest`/`VentaViewModelTest`/`ModificarVentaTest` ahora pasan un `costo = 20` a los productos de prueba para esquivar el bloqueo; `ValidadoresTest.no se vende al costo ni por debajo` cubre `producto` e `insumo`, pero **nada prueba el bloqueo a nivel de cotización ni el preajuste sin control de costo**.
- **La app sí se instaló en un teléfono.** En un Redmi 23021RAAEG con Android 15: compila, abre Inicio/Inventario/Pago electrónico, fuente al 200 % legible, y `FLAG_SECURE` deja en negro la captura de Pago electrónico (correcto). Eso retira la parte dura de B8. Lo que sigue sin ejecutarse son los 144 instrumentados, las 143 capturas y `spviDistCheck` (APK firmado): la verificación en dispositivo fue **manual y sin automatizar**, y `docs/VERIFICACION.md` —el sitio donde vive su propia regla de «una fila por release»— **no se tocó en ese commit**.
---

## 2. Bloqueantes

| # | Hallazgo | Evidencia |
|---|---|---|
| **B1** | **CI roja en el commit auditado.** 6 tests fallando en `331a9cd`: `OrquestadorSeedTest.rotacionDeVendedoresPorRachas`, `VentaFlujoTest.insumoCompartidoQueNoAlcanzaParaAmbosNoRegistraNada`, `VentaFlujoTest.ventaMixtaArticuloYElaborado`, `VentaViewModelTest.flujoEfectivoCompleto`, `ModificarVentaTest.anadirLineaConPrecioActualYConservarElDeEntonces`, `ProductoFormLogicTest.erroresClarosPorCampo`. Se fusionaron dos PRs con eso sin verificar. **Estado actual:** verde en `06ac298`, por lo de §1.1 (fixtures, no regla). | run `37877669746` (failure) y `37885008821` (success), anotaciones de la API de GitHub; `git diff --name-only` |
| **B2** | **Regla dura que bloquea la caja.** `if (detalles.any { it.precioUnitario <= it.costoUnitario }) return Err(Validacion("precioVenta", RANGO))` en la *cotización*, más `precioVenta <= precioCosto → RANGO` al dar de alta. | `domain/…/service/PlanificadorVenta.kt:87`, `domain/…/validation/Validadores.kt:41` (producto) y `:56` (insumo) |
| **B3** | **El bloqueo tapa el diagnóstico real.** Dentro de `planificar` el control de existencias va antes (`:66`) y el de precio después (`:87`), pero la resolución de recetas y el descuento de insumos compartidos ocurren **después** (`cot.elaborados()` y `ventas.registrar`): un faltante que solo se descubre al expandir la receta nunca llega a contarse, porque el precio lo aborta antes. | fallo esperado `StockInsuficiente([Harina])` → recibido `Validacion(precioVenta,RANGO)` |
| **B4** | **El seed de demostración abortaba por diseño.** `OrquestadorSeed` hace `return r` al primer `Err` de `cotizar.planificar` y tira toda la generación: un solo artículo con precio ≤ costo anula el demo entero. ~~Bloqueante~~ **resuelto en `06ac298`** ajustando el catálogo (§1.1), pero el patrón sigue: un generador de datos de prueba que aborta en vez de reportar cuál línea es inválida. | `domain/…/seed/OrquestadorSeed.kt:129-131` |
| **B5** | **Los números del catálogo de ejemplo eran imposibles (×1000).** `LineaRecetaSeed("harina-trigo", 200_000)` en «milésimas» = **200 kg de harina por un pan**: costo 187,620 CUP contra venta 280; `dulce-coco` 320,000 vs 150; «entrega a domicilio» con 500 L de gasolina → 150,000 CUP por entrega. Nadie lo había visto: en el commit auditado había 15 tests en `domain/…/seed/` y ninguno comparaba el costo de la receta con el precio de venta. | `CatalogoBodega.kt:96,102-104` en `331a9cd` + aritmética de `core/…/quantity/Cantidad.kt`. **Arreglado en `06ac298`** con invariantes nuevas que impiden que vuelva (§1.1) |
| **B6** | **Respaldo "sin contraseña" = sin cifrado** (PoC ejecutada, §10.1). Es el modo por defecto: `conContrasena: Boolean = false`. | `app/…/respaldo/RespaldoLogic.kt:46-49`, `data/…/respaldo/BackupCipher.kt:210-225` |
| **B7** | **Publicación inexistente.** `GITHUB_REPO=rmdvcd/SPVI` alimenta `releases/latest` y `revocadas.json`; la API responde 0 releases, 0 tags. Toda la actualización obligatoria, el bloqueo por versión antigua y la revocación de licencias son hoy **código muerto que falla abierto**. | `docs/REVISION_2026-10-09.md` pide publicar la release en su punto 9; `SECURITY.md` dice que el fallo de `revocadas.json` es tolerado |
| **B8** | **El dispositivo real sigue sin estar automatizado.** Se instaló a mano en un Redmi con Android 15 (§1.1), pero los 144 tests instrumentados (incluido `EsquemaTest`, única comprobación de migraciones de Room) y las 143 capturas Roborazzi **nunca se han ejecutado**, `spviDistCheck` (APK firmado) tampoco, y lo no verificable se quedó en la lista del propio `HISTORIAL_DESARROLLO.md`: dinero con coma en el teléfono, descuentos acumulados, QR de Transfermóvil, SMS bancario real y la release firmada. | `docs/VERIFICACION.md:73`, `docs/ESTADO_INSTRUCCIONES.md:80` (T0.6), `docs/HISTORIAL_DESARROLLO.md` (verificación 2026-10-09) |
| **B9** | **La documentación de entrada manda a leer archivos que no existen.** `AGENTS.md:5` (el punto de entrada de cualquier agente o colaborador) arranca con `Pendiente.md` y `Pruebas.md`: ninguno existe. `README.md` enlaza `MANUAL_USUARIO.md` y `PRUEBAS_DISPOSITIVO.md` (no existen); `RELEASE.md` y `SECURITY.md`, tampoco los tienen. En total, **9 documentos enlazados que no existen en ninguna parte del repositorio**: `Pendiente.md`, `Pruebas.md`, `MANUAL_USUARIO.md`, `PRUEBAS_DISPOSITIVO.md`, `ARQUITECTURA_SPVI.md`, `DECISIONES_LICENCIA_SPVI.md`, `PRUEBA_COMPATIBILIDAD_GL.md`, `CONTEXTO_LICENCIAS.md` (el archivo real es `docs/GL_CONTEXTO_LICENCIAS.md`: enlace roto por un prefijo), `SpviSpacing` y `docs/DECISIONES.md` —y este último es el índice que los demás invocan como autoridad. Peor aún: `docs/ESTADO_INSTRUCCIONES.md:108` describe un módulo `:basepublica` que **no existe** en `settings.gradle.kts` (6 módulos, sin ese). Y `README.md:10,228` dicen "Room **v10**" cuando `SpviDatabase.VERSION = 11` con esquemas hasta `11.json`. | enlaces verificados contra el árbol (`find` por nombre: 9 inexistentes); `settings.gradle.kts:29-34`; `data/…/db/SpviDatabase.kt:73` `VERSION = 11` |

Sobre B9, el matiz importante: `docs/VERIFICACION.md` **no miente**, está **helada**. Su última fila es del 2026-10-07 (`531cab3`, `f480e50`, 5/5 verde) y su regla (`docs/VERIFICACION.md:6`, F6 del plan de correcciones) dice «cada release añade o actualiza una fila»; la última fila la añadieron las PRs de **ayer**, que dejaron `main` rojo sin tocar una línea de ese documento. El dato duro también se oxidó: VERIFICACION cuadra "967 `@Test` − 142 capturas = 825"; hoy son 996 − 143 = 853. Un número que solo sirve si es comprobable, y este ya no lo es (el desfase de v10/v11 en README sí lo reconoce `docs/ESTADO_INSTRUCCIONES.md` en su propio inventario de deuda).

---

## 3. Lo que está bien hecho (y es mucho)

1. **Dominio puro de verdad.** `:domain` no ve Android: 0 imports `android.*`, 0 Room/Coil/DataStore. Todas las reglas (venta, recetas, stock, estadísticas, filtros, exportación de tablas) son funciones puras testables en JVM.
2. **Dinero serio.** `Cup` en centavos `Long` con `Math.addExact/multiplyExact`, `porMilesimas` redondeando al centavo más cercano, división truncada hacia cero documentada, y una coma de formato que **es contrato de parseo** (acepta `1,450.00` y lo mismo que `Money.parse`).
3. **`AppResult` sin excepciones de negocio cruzando capas**, y `runCatchingCancelable` re-lanza `CancellationException` en vez de tragarla — detalle que el 90 % de los proyectos se salta.
4. **Atomicidad real.** Venta + líneas + `StockDelta` + kardex en la misma transacción (`VentaDaos`, 5 `@Transaction`). El secundario recalcula el cierre en lugar de aceptarlo en claro (`data/…/sync/AlmacenSync.kt`).
5. **Sin fugas de segundo plano.** `shareIn/stateIn(WhileSubscribed(5_000))`, `ProcessLifecycleOwner` para re-evaluar al volver, y la política explícita de "nada corre mientras la app está cerrada". Ni un `GlobalScope` en `src/main` (0 usos) y cero `TODO`/`FIXME`/`println`.
6. **Higiene de permisos de otro nivel:** 3 permisos; `signature` para biometría; `allowBackup=false` + `dataExtractionRules` + `autoBackupRules` sin dominios; `usesCleartextTraffic=false`; y un `ExportedComponentVerifierTest` que compara el **manifiesto fusionado**, no el declarado. Eso es pensar en amenazas reales, no en checkboxes.
7. **Canal LAN bien diseñado:** ECDH P-256, HKDF por sentido, AES-256-GCM con AAD `ETIQUETA:secuencia` anti-replay y anti reflejo, `soTimeout` 90 s, tramas topadas a 16 MiB, `borrarClaves()` al cerrar, y un token de vinculación **de verdad de un solo uso** (`ServidorSync.kt:208` lo anula al aceptar).
8. **El APK no se instala a ciegas:** `InstaladorApk` verifica paquete, `versionCode` mayor **y mismo certificado de firma** antes de abrir el instalador; el binario viaja en bloques por el canal cifrado (`ApkLocal`, `filesDir` privado), no por un servidor HTTP abierto. Corregí en este informe la idea de que "se sirve por HTTP en claro": es falso.
9. **Licencia offline con criterio:** firma ECDSA verificada con `MessageDigest.isEqual`, clave de dispositivo envuelta en Keystore, `plain.fill(0)` tras usarla, y una tolerancia de reloj asimétrica justificada por escrito.
10. **Design system con cabeza:** tokens con el razonamiento de contraste escrito en el código, `ContrastTest` que audita las 31 combinaciones de las tablas, tema oscuro, 4 escalas de letra, campos que validan **al salir**, `contentMaxWidth = 600.dp` para que una tablet no sea un móvil estirado, y un buscador con debounce + normalización de tildes + LIKE escapado.
11. **El ticket no miente sobre su propia incertidumbre:** "no cuadra" es un estado legítimo del cierre (`Arqueo.estado`), no un error rojo. Para un comercio eso vale más que diez gráficos.
12. **Comentarios que explican *por qué*.** `Estadisticas.rango` explicando el horario de verano cubano que empieza a las 00:00, o `PlanificadorVenta` justificando el `coerceAtLeast` de descuentos. Eso es mantenimiento futuro.

---

## 4. Código — 7.5/10

**Lo bueno:** se ve el diseño (casos de uso por módulo, repos en `:domain`, implementaciones en `:data`, `:core` sin Android). `IdArticulo` (producto positivo / insumo negativo) es sucio pero está documentado y permite compartir alertas, ajustes y recetas sin claves compuestas. Cero `TODO`, cero `FIXME`, cero `println`, cero `catch {}` vacíos.

**Lo malo, medido:**

1. **113 `runCatching` + 40 `catch (e: Exception)`** que silencian. Casos con consecuencias:
   - `VentaViewModel.kt:208` → `productos.observarTodos().catch { emit(emptyMap()) }`: un fallo de la base de datos se muestra como "no hay productos". La pantalla de venta no distingue catálogo vacío de catálogo roto, y el usuario cobra sobre lo que ve.
   - `LicenseManager.kt:168` → `runCatching { reg.sincronizar(...) }.getOrNull()`: si el registro anti-reinstalación falla, nadie se entera, y el control que justifica tres permisos y 4 copias se apaga en silencio.
   - `RootViewModel` usa `seguro { … }` que produce `Desconocido()` **sin la causa**. En el respaldo sí se encadenan las causas (`mensajeRespaldo`), o sea: el criterio existe pero no es homogéneo.
2. **33 líneas con `!!` en los `src/main` de los 6 módulos:** `OrquestadorSeed.kt` (5), `data/export/PdfWriter.kt` (5), `AlmacenSync.kt` (3), `ProductoFormLogic.kt` (3), `core/money/Money.kt` (2), `TurnoDetalleScreen.kt` (2), `OnboardingScreen.kt` (2) y 9 ficheros más con 1-2. Más 8 `lateinit` en `MainActivity`, `SpviApplication`, `ServicioSync` y `CapturaSmsPagoService`. Lo relevante no es el número: es que **el proyecto ya lo sabe** — `docs/ESTADO_INSTRUCCIONES.md:134` marca F6.2 ❌ con exactamente «33 líneas con `!!` en `src/main`», existe la instrucción escrita para eliminarlos (`docs/PROMPT_OPENCODE_PENDIENTE.md:125`) y la corrección vive en la rama `c64c3dc9` sin fusionar.
3. **`getOrNull()` ×51**: en `CapturaSmsPagoService`, un `meta-data` mal escrito apaga la captura de notificaciones sin señal alguna. Si el error es del desarrollador, hay que gritarlo en debug.
4. **La capa de presentación decide demasiado.** `InicioScreen.kt` (870 líneas) reconstruye filtros y etiquetas dentro del composable; el ViewModel expone datos crudos (`FiltroTurno` sin etiqueta, `OpcionPeriodo` sin rango) y los testes tienen que buscar `testTag` en el árbol en vez de preguntar al estado. Con 2 `@Preview` en el proyecto entero, el bucle de revisión visual es: capturas Roborazzi que nadie ejecuta.
5. **La capa de red está casada con el dispositivo.** `RedLocal` (NSD + `NetworkInterface`) se inyecta directamente en `ServidorSync`/`ClienteSync`: no hay una interfaz `Transporte` que permita un relé en la nube. Cuando quieras sincronizar entre ciudades, tocarás el protocolo.
6. **Frontera `:core`/`:domain` sin resolver.** `:core` es JVM puro pero aporta el `platform("android")` a todas las configuraciones de `:app` porque define una excepción de negocio (`NoConversationSelected`) por su `message`. `docs/ANALISIS_SPVI.md` ya propone sacarla a `:domain`; sigue igual.
7. **`data/…/network/Red.kt:10` — `USER_AGENT = "SPVI/0.27 (Android)"`** con la app en 0.30.0: el identificador que ve tu licenciatario está obsoleto y nada (ni test, ni constante generada desde `versionName`) evita que vuelva a pasar.
8. **Ruido versionado:** `app/capturas/` con 21 MB de PNG que ya no compara nadie, `.kotlin/sessions/…salive` (un archivo vacío del daemon) y `app/lint-baseline.xml` entregado vacío.
9. **Sin historial utilizable:** 445 archivos, 59 mil líneas, cero tags. El `HISTORIAL_DESARROLLO.md` de 168 KB es tu `git log` y a la vez no es navegable.

---

## 5. UI — 7/10

**Fuerte:** paleta y tokens centralizados, tema oscuro, iconos vectoriales propios, un set de ilustraciones propias (`designsystem/ilustracion/SpviIlustraciones.kt`) para los estados vacíos, medallas del Top 3, tipos de letra escalables con `SpviTextoAjustable`, y un sistema de "pasos" coherente. Se nota que alguien decidió en vez de acumular Material.

**Débil, con nombres y apellidos:**
1. **La exportación a imagen ignora el tema.** `ImagenTabla.kt:67-72` fija `Color.WHITE`, `#E3EEF1`, `#F5F7F8`, `#1B1B1B`, `#5F6368` en claro fijo. Con el móvil en modo oscuro, las tablas PNG que compartes con clientes salen en claro. Es deliberado (se ve en el código), pero significa que **el design system no llega al 30 % de lo que produces**: PDF, XLSX y PNG tienen su propia paleta paralela.
2. **El gráfico del ticket no es un gráfico.** `SpviDonutChart` con `legend = false` se renderiza como texto. En el comprobante impreso eso es una decisión razonable; en la *pantalla* de "Ver venta" es una oportunidad perdida.
3. **`AjustesScreen` (267 líneas de archivo) mete en la misma lista** "Datos de prueba", "Revocación de licencia", "Borrar todo" y el interruptor de captura de SMS. La zona de riesgo no está aislada visualmente; se protege con confirmaciones, no con jerarquía.
4. **Rotación = amnesia.** Sin `configChanges` y con un solo Activity, al girar se pierden los acordeones de Inicio (`InicioScreen.kt:121-125` lo documenta y lo asume: "todo vuelve a estar cerrado"). Sobrevive lo crítico (carrito, formularios) vía `SavedStateHandle`; eso está bien elegido. Lo mal elegido es que un scroll largo en Registros se pierda: no hay `rememberLazyListState` en las listas principales. 🧪
5. **Sin layout para pantallas grandes:** `contentMaxWidth = 600.dp` centra una franja en una tablet de 11". No hay `WindowSizeClass`, ni `values-sw600dp`, ni dos columnas. Para el escenario "tablet en el mostrador" estás desperdiciando la mitad del ancho.
6. **Ni un solo `DynamicColors`:** coherente con el control del contraste, pero significa que cada color nuevo se decide a mano y que Android 12+ verá la app "de otra época".
7. **`contentDescription = null` ×42 vs descriptiva ×9.** La mayoría son iconos dentro de botones con etiqueta (correcto), pero la asimetría revela que no hay una revisión de accesibilidad sistemática: la tienes resuelta en los gráficos (`semantics(mergeDescendants)`) y ausente en las listas.

---

## 6. IX (arquitectura de la información) — 6/10

**Fuerte:** 5 destinos + Ajustes arriba, sin drawer. Pasos numerados con progreso en venta/respaldo/vinculación/licencia. Filtro persistente en Inicio. Búsqueda con debounce y orden "fijados → alfabético → cronológico". Borradores automáticos al salir de la venta.

**Débil:**
1. **La venta tiene 4 pasos y el caso dominante es de 1.** Añadir un producto **obliga** a ir al paso 2 (`onAnyarProducto → if (paso == 1) irAPaso(2)`), aunque cobres un solo artículo en efectivo. Existe modo rápido (un toque en el `+`), pero la ruta más frecuente sigue siendo la más larga.
2. **Tres sitios para lo mismo sin criterio de uso:** Inicio (gráficos por turno/período), Registros (ventas/turnos/movimientos/ajustes con otros filtros) y Caja (movimientos de efectivo). Un dueño no sabe dónde mirar "cuánto vendí hoy"; un empleado nuevo, menos.
3. **"Generar datos de prueba" esconde el "Borra todo" en el subtítulo** (`AjustesScreen.kt:210`): es la única entrada de Ajustes cuyo nombre invita a tocar y cuya advertencia vive en la línea de abajo. Y la pantalla (267 líneas, 14 entradas: Perfil, Licencia, Pago electrónico, Precios, Avisos, Permisos, Respaldo, Migrar, Actualizaciones, Ayuda, Soporte, catálogo de componentes y seed) mezcla lo destructivo con lo inofensivo: la zona de riesgo se protege solo con un diálogo de confirmación, no con estructura ni con rol.
4. **El bloqueo por actualización oculta el árbol a TalkBack** (`MainScaffold.kt:207` `clearAndSetSemantics {}`). La justificación es buena ("nada de lo de abajo es operable"), pero el resultado es que **la persona que necesita leer el ID de soporte para desbloquear no puede leerlo**. El botón de cerrar turno se salvó dibujándolo fuera del `Box`; los datos, no.
5. **Sin atajo a lo repetido** (llegar al carrito desde fuera de Venta, reponer un insumo desde la alerta con un toque, reordenar filas).
6. **Dos vocabularios para la misma cosa:** "Paso 2 de 4" en la venta y "Detalle" en Registros; "Ajuste" (movimiento de inventario) y "Ajustes" (preferencias de la app) en la misma app. Eso último es un error de IX de libro: `AjustesScreen` y `ajustes/` en código, pero el usuario entra ahí para cambiar *niveles de alerta*, no para ajustar stock.

---

## 7. UX — 6.5/10

**Fuerte:** validación al salir (no mientras tecleas), teclados numéricos con separadores, háptica en confirmaciones, mensajes propios por código de error, onboarding sin cuenta ni correo, y el detalle fino de que un SMS pegado "de una venta distinta" pida confirmación explícita en lugar de aceptar ciegamente.

**Débil:**
1. **La regla de precio/costo atrapa al cajero con el cliente delante.** El mensaje (`VentaLogic.kt:240`) dice "Revisa los precios en Inventario y los descuentos: el precio de venta debe superar el costo". Correcto, útil... y **sin acción**: no hay botón a Inventario, no hay "vender así de todas formas", no hay forma de registrar una pérdida. Con descuentos acumulables hasta −90 % (`Validadores.RANGO_AJUSTE = -9_000..10_000`, `:33`) y preajustes múltiples, bajar del costo es *fácil*: `Validadores.preajuste` (`:110-115`) solo exige que `puntosBasicos != 0` y esté en rango —**no contrasta el costo del artículo afectado**—, así que un preajuste perfectamente legal puede dejar todo su catálogo marcado como no vendible sin que la pantalla del preajuste diga nada. Y como el costo de un Elaborado sale de la receta (`PlanificadorVenta.kt:83`), **subir el precio de la harina vuelve invendible el pan** sin que nadie haya tocado el pan.
2. **Los textos de validación están desincronizados por diseño.** El dominio dice "debe ser mayor que 0" (`Validadores.mensaje`) y el formulario "debe ser mayor que el costo y que 0" (`ProductoFormLogic.kt:189`); en insumos, el formulario sí dice lo del costo (`InsumoFormLogic.kt:96`). Que un test (`ProductoFormLogicTest.erroresClarosPorCampo`) haya quedado contando la diferencia es la prueba: la cadena de la UI no está ligada a la regla del dominio.
3. **Cero "Deshacer".** Se confirma todo (borrar, anular, ajustar) y no se puede revertir desde la app; la única red es el respaldo previo. Para un POS con un dedo en la pantalla y otro en el dinero, el undo de un borrado vale más que cualquier animación.
4. **El demo está apagado y roto.** `mostrarSeed = BuildConfig.DEBUG` (`AjustesScreen.kt:124`): un cliente que quiera probar SPVI no puede generar catálogo de ejemplo; y si eres tú en debug, solo verás "Datos de prueba inválidos." (`SeedViewModel.kt:48` mapea *cualquier* `Validacion` a esa frase sin la causa). La función que habría hecho visible todo este bloque —un demo en la build de release, con el error real en pantalla en lugar de "inválidos"— sigue oculta detrás de un `BuildConfig` y sin mensaje útil.
5. **Cero internacionalización, y el formato va en tu contra.** `strings.xml` con 4 cadenas para una app con 2,865 literales. Y mientras el idioma es español, `Money.format` imprime `1,450.00 CUP` (Locale.US) y `Money.parse` **rechaza la coma decimal**: "14,50" → `NoValido`. El documento `REVISION_2026-10-09.md` pide probar a propósito "escribir dinero con coma"; es decir: **sabes que el usuario lo va a intentar y la respuesta del sistema es "formato inválido"**.
6. **Los errores no dicen a qué artículo se refieren.** En el carrito, la validación devuelve el campo (`precioVenta`) pero no el producto. Con 4 líneas en el carrito, el usuario tiene que adivinar cuál está mal. `PlanificadorVenta` podría devolver el `productoId` en la validación; no lo hace.
7. **Notificaciones como mecanismo de cobro.** Pedir "Acceso a notificaciones" (un permiso que Android marca en rojo) para leer el SMS de Transfermóvil es técnicamente limpio —no se pide `READ_SMS`— pero la percepción del usuario es "esta app lee mis mensajes". No hay una pantalla que explique el modelo de threat con la calma con que lo explica `SECURITY.md`.

---

## 8. Funcionalidad — 6.5/10

**Cobertura (impresionante para un proyecto de un autor):** catálogo con productos/insumos/elaborados con recetas/servicios/combos; venta en 4 pasos con QR y captura opcional del SMS de cobro; turnos con apertura, cierre, faltantes, arqueo y fondo por empleado; caja con entradas/salidas y "no cuadra"; gastos; preajustes de precio; alertas con niveles mínimos y caducidades; estadísticas con Top 3 (producto, servicio, empleado, cliente) y gráfico de ganancia neta; exportación PDF/XLSX/tablas PNG/tarjetas PNG; respaldo `.spvi` v4; multi-dispositivo con vinculación QR; licencia offline con prueba y aviso de expiración; y comprobación de versión.

**Huecos, en orden de dolor:**
1. **No se puede imprimir.** Cero `PrintManager`/`android.print`/ESC-POS en `app/src/main`. Para un comercio cubano con impresora térmica Bluetooth, esto es probablemente la función nº1 ausente, y no es un extra: el ticket es el comprobante legal de la venta. Tampoco hay cajón portamonedas ni lectura de códigos de barras de producto (el subsistema existió y se eliminó: `README.md:277`).
2. **Dos únicos métodos de pago:** `enum class MetodoPago { EFECTIVO, TRANSFERENCIA }` (`Venta.kt:8`). Sin "otra tarjeta", "depósito en efectivo", "saldo/consigna" ni pago mixto. Y el cierre solo reconcilia efectivo: `esperado = fondo + ventasEfectivo + entradas − salidas` (`Turno.kt:163`) — está bien que ignore las transferencias, pero **nada compara el importe del SMS con el total de la venta**: si el cliente transfiere 200 en una venta de 280, la app lo registra igual (el importe del SMS se usa para pre-rellenar, no para cuadrar). 🧪
3. **No hay catálogo de proveedores ni órdenes de compra.** La reposición es un `MOVIMIENTO` con tipo `ENTRADA`. Se puede vivir así, pero no da para saber "a quién le debo" ni el precio real al que compraste cada lote (el `precioCosto` es un único valor por artículo, no por entrada).
4. **Sin control por lote/vencimiento en la salida.** Hay `fechaCaducidad` por producto y alertas, pero ningún `Ajuste` por vencimiento automático ni FIFO: la merma se registra a mano.
5. **El modo secundaria no impide crear catálogo.** `esSecundaria` apaga Inicio/Alertas, pero no hay nada que avise en Productos de que lo que crees ahí **se perderá en el próximo `reemplazarCatalogo`** (`AlmacenSync.kt:264,268`).
6. **Las fotos nunca viajan a la secundaria**: `copy(fotoUri = null)` con el comentario "Las fotos son archivos del teléfono principal: aquí no existen". Es coherente (son URIs locales) pero el resultado es un catálogo con dos apariencias según el dispositivo. Falta copiar el archivo al vincular (o decirlo en la UI).
7. **Sin CSV ni salida de datos crudos.** `FormatoExport` = {PDF, XLSX} + PNG. El respaldo JSON no es un formato analizable (está cifrado, y con razón). Un negocio que quiera montar su hoja de cálculo con las ventas tiene que abrir el XLSX.
8. **Sin nada fiscal.** El `Cierre` no conoce libro de compras/ventas, IVA ni formatos de la ONAT. Si tu mercado es Cuba, esto no es un "nice to have": es el motivo por el que un contador deja de usar tu app.
9. **La licencia puede parar una caja por motivos administrativos.** `BloqueoLicencia` con `FechaExcedida` fuerza a cerrar el turno para salir, y la renovación es **manual** (el ID de dispositivo por chat, contrato GL). Con 0 releases publicados y `revocadas.json` inexistente, el canal automatizado no está: el riesgo operativo de "me quedé sin caja porque no localicé al desarrollador" es real y no está dicho en la UI.
10. **Los preajustes no tienen vigencia temporal** (on/off, no "del 1 al 15"), no hay precios por cliente ni lista de mayoreo — y `PrecioCliente` es un número único `precioCliente` por producto.

---

## 9. Flujo de datos y procesos — 6/10

**Flujo de datos, bueno:** UI → `StateFlow` del ViewModel → caso de uso → interfaz de repositorio → implementación en `:data`. Nunca se expone un DAO. `Clock` único y `ZoneId` de Cuba centralizado. Escrituras en transacción Room con su kardex. Lecturas reactivas compartidas. Un solo lugar para el dinero.

**Flujo de datos, malo:**
1. **Todo el filtrado y agregación ocurre en memoria sobre tablas completas.** `InventarioFiltro.aplicar` recibe `List<Producto>` + `List<Insumo>` y hace varias pasadas `.filter` en Kotlin; `RegistrosFiltro` idem sobre `ventas.observarEnPeriodo()`. Ninguna consulta de período lleva `LIMIT`. Con el techo declarado de ~1,313 artículos y ~30 k ventas (los que aguantaba la última batería de rendimiento) y un año como ventana por defecto, el coste de cada recomposición es O(tabla). `InventarioUseCases.kt:19-21` ni siquiera pide `flowOn`: combina los flujos crudos. `flowOn(io)` (6 usos) y `withContext(io)` en los casos de uso pesados lo sacan del hilo principal —eso sí está hecho, y es la corrección F1 de 0.30.0— pero no reduce el trabajo. El plan de llevarlo a SQL (T1.4) está escrito en una rama `arena/*` **sin fusionar**.
2. **E/S en el camino de arranque.** `LicenseManager.evaluate()` invoca `registroPrueba.sincronizar()` (línea 168), que hace 1 lectura de archivo + 3 consultas `ContentResolver` + hasta 3 escrituras, y se ejecuta en cada arranque, en cada vuelta a primer plano y en cada tick de `VigilarLicencia` (≤15 min, `ProgramaLicencia.MAXIMO`). Con MediaStore saturado, el arranque espera. No hay marca "ya sincronizado en esta sesión".
3. **`RepositoriosSegunTipo` es una caja negra de confianza:** la secundaria aplica los `movimientos` del primario sin re-validar precio ni regla de costo, y con deltas que pueden dejar stock negativo a propósito. Es la política correcta para un Hub-and-Spoke, pero convive con una validación dura en el alta local: **el mismo dato es ilegal si lo creas tú y legal si te lo sincronizan.**
4. **Sin trazabilidad operativa.** No hay un `audit` de "quién cambió este precio y cuándo" más allá del kardex de existencias; los cambios de `precioVenta` no dejan rastro. Para un negocio con dos empleados, eso es un conflicto sin resolver.

**Procesos, que es donde más duele:**
1. **CI roja en `main` tras dos fusiones** (PR #6 y #7). `docs/PLAN_CORRECCIONES.md:68` — "Nada avanza sin CI verde. Si un paso no se puede verificar, se marca NO VERIFICADO y no se cierra" — se incumplió justo en la última línea del historial, con el fallo de `VentaFlujoTest` gritando que el cambio de comportamiento rompió un caso de negocio (venta con falta de stock ahora reporta un error de precios). **Estado a fecha de hoy:** `main` está verde desde `06ac298`, pero por parchear los fixtures, no la regla (§1.1): la lección de proceso sigue siendo la misma dos veces —se fusiona sin verificar y se repara sin escribir dónde toca.**
2. **El documento de entrega de ese mismo PR (`docs/REVISION_2026-10-09.md`) decía textualmente que ningún comando se ejecutó** y enumeraba 9 comprobaciones manuales. No era negligencia: era un cuello de botella deliberado en la máquina del dueño. `06ac298` lo deshace en parte (se compiló, se instaló en un Redmi con Android 15 y se corrió `spviCheck` en verde), pero **la verificación se escribió en `docs/HISTORIAL_DESARROLLO.md` y no en `docs/VERIFICACION.md`**, que es el documento donde vive la regla «una fila por release»: la tabla sigue parada en el 07-10 con 825 tests cuando el runner ya reporta 856. El conocimiento existe y está escrito; lo que no existe es el hábito de llevarlo al único sitio que se consulta.
3. **Sin gate local:** no hay `pre-commit`, ni `ktlint`/`spotless`, ni hook que impida fusionar con CI roja. Lint con baseline vacío es una política legible (mejor que basilinar 95 avisos), pero no cubre formato ni estilo.
4. **Números que se citan en varios sitios con valores distintos**: `@Test` 825 / 967 / 996 / **999**, capturas 142 / 143, instrumentados 136 / **144**, avisos de Lint 95 / **127**, «Room v10» vs `VERSION = 11`. Cuando la misma cifra aparece en README, `VERIFICACION.md` y `PLAN_CORRECCIONES.md` sin fuente única, deja de ser un dato: `06ac298` añadió 3 tests y actualizó el historial, y ni una línea de `VERIFICACION.md` se movió.
5. **Sin LICENSE de código** en un repo público (`NOASSERTION`); solo `LICENSE_CLIENT.md`, que es un contrato unilateral para el comprador. Y `SECURITY.md` + `docs/*` publican la cadena de confianza en el mismo repositorio donde está el `SECRETO`/`MASCARA` del respaldo — coherente con un threat model que asume código público, pero conviene decirlo de una frase en la portada.
6. **Casi todo lo que aquí se critica está ya reconocido y aparcado en la rama equivocada.** `docs/ESTADO_INSTRUCCIONES.md` §F6 lista F6.1 ❌ (`.kotlin/` sigue versionado: `git ls-files .kotlin` devuelve `…salive`, y `.gitignore` no lo cubre), F6.2 ❌ (los `!!`), F6.3 ❌ (`docs/DECISIONES.md`, el índice de las decisiones P37/P68b/P74 que citan los demás documentos, no existe) y F6.4 ⏳ («ejecutar los **136** tests instrumentados»: son 144 —ni su propio conteo está al día—). El problema de proceso no es la ceguera: el diagnóstico existe y está bien escrito; lo que falta es el Merge, y hay 8 ramas `arena/*` diciéndolo.
7. **El verde del PR no habla de tu rama.** En este propio PR: el run del push (`37968553343`, cabeza `e491ee0`) **falló** y el del pull request (`37968604912`) pasó en sus 5 trabajos, porque GitHub corre el `pull_request` sobre el *merge ref*, que ya incluye el arreglo de `main`. Añadido: los cinco trabajos duraron 28-71 s, lo que apunta a tareas resueltas desde la caché de Gradle de la corrida verde anterior (no se pudo leer el log del runner para confirmarlo). `ci.yml` dispara en `push` (branches: `main`, `arena/**`) y en `pull_request`: la única señal honesta de lo que contiene una rama es la del push, y debería ser la obligatoria.

---

## 10. Seguridad — 7/10

### 10.1 Prueba de concepto: el respaldo "sin contraseña" no está cifrado contra nadie

Modo por defecto: `conContrasena = false` (`RespaldoLogic.kt:46-49`). La clave de ese modo se deriva con PBKDF2-SHA256/10,000 de un secreto reconstruido como `SECRETO[i] xor MASCARA[i%8]`, y **ambas matrices están en el código público** (`BackupCipher.kt:210-225`).

Reproducido durante esta auditoría con un script de ~40 líneas fuera del repositorio (no se añade aquí para no dejar en el repo material que facilite abrir los respaldos de un cliente): escribir un `.spvi` v4 con el formato de la app y volver a leerlo usando **solo material publicado**.

```
archivo 'robado' : 193 bytes
descifrado       : {"version":4,"negocio":{"nombre":"Bodega La Amistad"},"perfil":{"nombre":"Yoelin"},
                  "productos":[{"nombre":"Refresco","precioVenta":350}]}
```

Lo que se demuestra: **cualquiera con el archivo puede abrirlo**, sin el teléfono, sin Keystore, sin tu ayuda. Y el archivo acaba en `Download/` o en un chat de WhatsApp (el botón "Compartir" está para eso). `SECURITY.md` lo documenta como riesgo aceptado; la UI no. El riesgo lo está corriendo el cliente, que marca la casilla por defecto.

Tres arreglos en orden de coste: (a) invertir el default y que "sin contraseña" sea una elección informada con frase explícita; (b) si quieres conservar «sin contraseña» para recuperar sin depender de una frase, deriva una clave aleatoria por respaldo y escríbela junto al `.spvi` en un `.spvi.key` corto e imprimible; (c) añade un `MAC` con clave de publicación para que nadie pueda *fabricar* un respaldo e inyectarlo (hoy `ImportarRespaldo` acepta el que se le pase, y el único control es el CRC de la cabecera).

### 10.2 Lo demás que está mal, por orden
1. **DoS LAN sin autenticar, y secuestro de sesión.** `ServidorSync` lee un `Hola` en claro, resuelve el empleado y ejecuta `sesiones.put(e.id, s)?.cerrar()` (`:234`) **antes** de probar posesión de la clave. Cualquiera en la Wi-Fi con `negocioId` + `empleadoId` (entero pequeño) expulsa a la secundaria legítima en bucle. Sin límite de conexiones: `accept()` lanza una coroutina por socket con `soTimeout` de 90 s (`:173`, `:340`). `SECURITY.md` lo acepta; un POS que no puede cobrar porque un vecino te vacía las sesiones es un POS caído, y "requiere estar en tu Wi-Fi" es una premisa más frágil de lo que parece en una ciudad con redes repetidas.
2. **El `Hola` en claro** lleva `negocioId` y `empleadoId`: un espía pasivo conoce el identificador del negocio y el roster. Contradice la regla 2 de `AGENTS.md` ("cero datos de negocio viajan sin cifrar") y el propio `REVISION_2026-10-09` no lo lista como excepción. Un `Hola` con el hash del negocio + un challenge bastaría.
3. **Enumeración de empleados** por mensajes de rechazo distintos (no existe vs ya conectado).
4. **Los registros de prueba viven en el almacenamiento compartido.** `sys_*.png` en `Pictures/SPVI/`, `.sys_*.bin` en `Download/` y `Documents/` (`RegistroPruebaAndroid.kt:104-115, 200-208`), con clave derivada de `ANDROID_ID` + constantes del binario. Dos consecuencias: (a) es ** forense predecible** —cualquier app con `READ_MEDIA_IMAGES` ve un archivo basura en tu galería y puede borrarlo o llenarte el índice— y (b) un `adb`/restauración mal hecha puede dejar copias desincronizadas que la app intenta reconciliar en cada arranque. La tolerancia a que falten 2 de 4 copias (`LicenseManager`) reduce el ruido, no la superficie. `docs/PLAN_ANTIREINSTALACION` explica el porqué comercial; el costo lo paga el teléfono del cliente.
5. **La revocación falla abierto por diseño:** sin `revocadas.json` (no hay release) el cliente loguea un aviso y sigue. Combinado con "0 releases publicados", hoy **no existe ningún mecanismo remoto real** ni para revocar ni para obligar a actualizar. Es un subsistema de dos patas sobre una silla de una.
6. **`SecureCounter` global** (`app/…/common/SecureWindow.kt:24-32`): un contador de proceso, no por `Activity`. Con dos composiciones adquiriendo el flag en activities distintas (hoy no ocurre: es single-Activity) o con una excepción entre acquire/release, `FLAG_SECURE` se queda puesto o se quita pronto. Menor y teórico, pero es un `object mutable` sin sincronizar en el borde de una política de privacidad.
7. **El PIN protege el acceso, no los datos.** El contrato de `domain/…/repository/ConfiguracionRepositories.kt:122` dice lo que hay: PBKDF2-HMAC-SHA256 + AES-256-GCM para lo que se derive de frase, y avisa de que «la BD SQLCipher está atada al Keystore y no es portable por sí misma». Traducción operativa: si al cliente se le corrompe el Keystore (o restaura sin él), **el respaldo es la única salida**, y el respaldo por defecto no tiene contraseña (§10.1). Las dos mitades del plan de recuperación están descompensadas.
8. **`AndroidDeviceKey.loadSoftware()`** regenera la clave por software si el envoltorio de Keystore se corrompe: es la decisión correcta para no perder datos, y el efecto secundario es que una reinstalación limpia borra la posibilidad de descifrar respaldos viejos atados a esa clave. Está documentado en `SECURITY.md`, no en la pantalla de Licencia.

### 10.3 Lo que está bien (y no es habitual)
Keystore + `EncryptedSharedPreferences` con clave de 32 B y AAD etiquetado; HKDF por sentido; `MessageDigest.isEqual` en firma y en verificación; `plain.fill(0)` tras usar la clave; `Red.cliente()` con `followSslRedirects(false)`, `retryOnConnectionFailure(false)` y un interceptor `SoloHttps` montado en las dos capas (petición y redirecciones); SHA-256 obligatorio en todo archivo recibido (`.pdf/.xlsx/.png/.jpg/.spvi/.txt`); `file://` restringido a `cacheDir/compartir` en `ArchivosApp.abrirOrigen` (`app/…/common/Archivos.kt:64-75`, con `canonicalFile` y comprobación del padre: sin traversal); `allowBackup=false` y extracción de datos vacía; verificación de `exported` contra el manifiesto final; y un `SecureWindow` que oculta el contenido en el switcher de apps mientras se opera. Si a esto le sumas la PoC del §10.1 como *único* punto de verdad crítico, la seguridad de SPVI está en el 5 % superior de las apps de este tamaño.

---

## 11. Versatilidad — 4/10

SPVI es un monolito **excelentemente afinado para un solo escenario**: un negocio pequeño en Cuba, un teléfono Android, sin internet, en español, en pesos cubanos, operado por una persona.

**Soldado al escenario:**
- **Moneda:** `Cup` es un `value class` con el símbolo y el `Locale.US` dentro del tipo de dominio. No existe `Moneda` como concepto. Meter USD/MLC con tasa (que en Cuba no es exótico: es el caso normal de la venta en efectivo y la transferencia en MLC) exige tocar `Producto.precioVenta`, `Cierre`, `Gasto`, `Arqueo`, las tablas de exportación y el parseo. 320 usos del tipo `Cup` en los `src/main`.
- **Idioma:** 2,865 literales, 0 `stringResource`. Cualquier segundo idioma es una reescritura de `app/src/main` (205 archivos).
- **Fiscalidad y mercado:** ni IVA, ni libro de compras/ventas, ni ticket fiscal, ni multi-sucursal (`Negocio` es modelo único, `Almacen` no existe).
- **Plataforma:** Android 8+ y nada más. Sin web para el dueño (que suele querer ver las ventas desde una laptop), sin desktop, sin iOS. La "secundaria" cubre el caso de caja, no el de consulta remota.
- **Hardware:** sin impresión, sin lector de códigos de barras de producto, sin báscula, sin cajón. El lector QR existe solo para vincular, y la app **requiere ML Kit empaquetado** para eso (`CamaraEscaner.kt` es el único consumidor), lo que pesa en el APK.
- **Distribución:** APK directo por WhatsApp/Telegram. Sin tienda, sin updates incrementales, 45.4 MB por versión.
- **Y sin embargo, hay versatilidad real bien sembrada:** `:licencia` es un módulo JVM con contrato de cable byte-a-byte y verificador independiente (GL podría emitir desde cualquier stack); el protocolo está versionado (`data/…/sync/Protocolo.kt:36` `VERSION_PROTOCOLO = 1`) con rechazos explícitos `version`/`limite`/`codigo_vencido`/`otro_negocio`, y `ConfigSync` deja convivir a un cliente viejo; `Tramas` está pensado para streaming. Con dos decisiones (`Moneda` como concepto y `strings.xml` de verdad) este proyecto pasa de "app de un negocio cubano" a "POS exportable".

---

## 12. Rendimiento y uso de recursos — 7/10 (sin una sola medición propia)

**Bien hecho por diseño:** paginación de exportaciones (25 filas por PNG en `DisenoTabla.POR_IMAGEN`, pero `debounce(250)` en búsqueda, `flatMapLatest` al cambiar de período, `shareIn(WhileSubscribed(5_000))`, `LazyColumn` con `key` en las 17 listas de `app/src/main` (todas con key, verificado), compresión y topes anti bombas en red y respaldo (16 MiB por trama, 128 MiB de plano), `Bitmap.recycle()` explícito, R8 con `-assumenosideeffects` para borrar `Log` en release y `shrinkResources`.

**Lo que preocupa:**
1. **Nada está medido.** 0 benchmarks, 0 perfilado, 0 números de arranque en frío, 0 FPS. Hay `profileinstaller` en las dependencias (`app/build.gradle.kts:140`) **pero no hay baseline profile en el árbol ni módulo `:baselineprofile`**: la dependencia no hace nada. En `docs/ESTADO_INSTRUCCIONES.md:91` figura como T1.6 ⏳. Es peso muerto + una sensación falsa de "ya optimizamos el arranque".
2. **Sin `splits.abi`:** `app/build.gradle.kts` no declara `abiFilters` ni `splits`, así que el APK lleva los `.so` de SQLCipher y ML Kit para las 4 ABIs. En un teléfono real solo se usa una. Es el recorte de tamaño más barato que existe y no está hecho.
3. **Agregación O(tabla) en cada tick** (§9.1). Con 18 meses de datos esto se nota en Inicio y Registros, y `flowOn(io)` lo esconde pero no lo resuelve: consume CPU y memoria que en un gama baja de 3 GB se paga en jank.
4. **E/S de MediaStore en arranque y reanudación** (§9.2), y 3 escrituras atómicas (`atomico()` + `.tmp` + rename) por copia desincronizada.
5. **Sin tuning de SQLite:** la única `openHelperFactory` es `SupportOpenHelperFactory(passphrase)` y no hay un `PRAGMA` propio (`journal_mode`, `synchronous`, `busy_timeout`, `cache_size`). Para una BD que se abre en cada arranque y escribe en cada venta, `WAL` + `synchronous=NORMAL` + `busy_timeout` son la diferencia entre "escribe" y "escribe y espera". (SQLCipher ya trae WAL por defecto en la factory de SQLCipher; lo que no hay es `busy_timeout`, y eso sí importa con dos coroutines escribiendo a la vez.) 🧪
6. **Sin caché de resultados pesados:** el resumen de Inicio reconstruye las 6 agregaciones desde cero al volver a la pantalla; `shareIn` cachea el flujo, no el resultado calculado.
7. **`VinculacionScreen`/`QrVinculacionScreen`:** el `ImageBitmap` del QR y el `AndroidView` de CameraX se montan en cada entrada a la pantalla (correcto: solo se necesita al vincular), pero no hay `remember` sobre el `Bitmap` de la imagen del QR. Con QRs de ~1 KB es irrelevante; lo menciono porque es el mismo patrón que en `ImagenTabla` sí cuidan. 🧪

**Consumo de recursos del proyecto (no de la app):** 21 MB de PNG y 168 KB de changelog en git; 45 MB de APK para reinstalar en cada versión en un mercado donde el dato cuesta. En Cuba, "pesan" el doble.

---

## 13. Matriz

| Dimensión | Nota | Resumen de una línea |
|---|---|---|
| **Código** | 7.5 | Dominio ejemplar, dinero y transacciones correctas; capa de presentación con silencio operativo, 33 `!!`, sin `@Preview`, `:core` con fuga de Android. |
| **UI** | 7 | Sistema propio, contraste auditado, tema oscuro; pero exportaciones con paleta paralela fija, sin pantallas grandes, `Ajustes` saturado. |
| **IX** | 6 | Pasos con progreso brillantes; tres sitios para la misma pregunta, 4 pasos obligatorios para lo trivial, bloqueo inaccesible para TalkBack. |
| **UX** | 6.5 | Validación al salir y mensajes propios por código; regla dura que atrapa en la caja sin acción, sin deshacer, demo apagado, numérico contra el idioma de la pantalla. |
| **Funcionalidad** | 6.5 | Cobertura amplia y madura; sin impresión, sin proveedores/compras, sin fiscal, 2 métodos de pago, sync que pisa el catálogo. |
| **Flujo de datos y procesos** | 6 | Dentro de la app, impecable y atómico; fuera, CI roja, cero gate local, cifras contradictorias y verificación delegada en un humano con un teléfono. |
| **Seguridad** | 7 | Cripto de nivel profesional y permisos ejemplares; respaldo sin contraseña legible por cualquiera (PoC), DoS LAN no autenticado, revocación fallando abierto, archivos ocultos en la galería del cliente. |
| **Versatilidad** | 4 | Monolita perfecto para un caso; moneda, idioma, fisco, hardware y plataforma están soldados al tipo de dato. |
| **Rendimiento y recursos** | 7 | Paginación, debounce, shareIn, R8 y topes anti bombas; sin una sola medición, sin baseline profile, sin splits de ABI y con agregación O(tabla). |

---

## 14. Si fueras a hacerme caso, en este orden

1. **(SIGUE PENDIENTE — `06ac298` la dejó intacta) Decide la regla de precio y líbrala de la cotización.** `precio ≤ costo` debe ser **advertencia** en `PlanificadorVenta` (mostrar, no bloquear) y validación dura solo en `Validadores.producto/insumo` al dar de alta. Devuelve el `productoId` en la validación para que la UI diga *qué* artículo está en rojo. Predicción razonable, no certeza (aquí no se puede ejecutar Gradle): con ese cambio 5 de los 6 tests vuelven a describir el comportamiento esperado; el sexto (`OrquestadorSeedTest`) exige además el punto 2. Y `ProductoFormLogicTest` es solo alinear el texto del formulario con `Validadores.mensaje`.
2. ~~Arreglar el ×1000 de `CatalogoBodega.RECETAS`~~ **hecho en `06ac298`**, y bien: valores en g/mL más dos invariantes en `CatalogoBodegaTest` (costo declarado y costo de receta por debajo de la venta). Lo que queda pendiente de ese mismo hilo es el punto 1: el bloqueo en la cotización.
3. **Saca el seed del `BuildConfig.DEBUG`** y haz que el error muestre la causa. Es tu mejor argumentador de ventas y hoy no existe para un usuario normal.
4. **Cambia el default del respaldo a "con contraseña"** y añade una frase explícita cuando se desmarque ("este archivo se puede abrir sin clave"). Es ~15 líneas y cierra el agujero §10.1.
5. **Publica `v0.30.0`** con el APK firmado y un `revocadas.json` vacío: activa de golpe actualización, revocación y distribución, y te obliga a pasar `spviDistCheck` (que nunca se ejecutó).
6. **Automatiza lo que ya hiciste a mano en el teléfono.** La pasada manual del 09-10 demostró que el dispositivo no es el problema: ejecutar los 144 instrumentados (`EsquemaTest` incluido) y las 143 capturas en un workflow `self-hosted` o en `androidx.test` sobre emulator en CI, y añadir una impresora a la lista. Un resultado manual no es re-ejecutable cuando cambie el tema o la densidad de fuente.
7. **Fusión de T1.4 (agregación en SQL) y `splits.abi`.** El primero es tu problema de escalado real; el segundo son ~10 MB gratis.
8. **Genera el baseline profile** (módulo `:baselineprofile` con macrobenchmark) o quita `profileinstaller`: tener la dependencia sin el perfil es peor que no tenerla, porque se lee como "ya lo hicimos".
9. **Sana el silencio operativo:** que los 40 `catch (e: Exception)` logueuen con causa; que `VentaViewModel:208` propague el fallo al estado (una fila roja "no se pudo leer el catálogo" en vez de "no hay productos"); y que `LicenseManager:168` no envuelva `sincronizar` en `runCatching` sin log.
10. **Un único sitio para las cifras:** versión, `versionCode`, Room, número de tests, generados desde `gradle.properties`/`build.gradle` y leídos por un `tools/verificacion/docs.sh` que falle si un `.md` enlaza a un archivo inexistente. Con 9 documentos enlazados que no existen (B9) y 3 valores distintos de «@Test», esto ya te ha mordido dos veces.
11. **Decide el futuro del `Ajuste`/`ajustes` homónimo y de los tres sitios de reportes** (Inicio/Registros/Caja) en una pantalla "Hoy" por rol. La función ya está; lo que falta es el mapa.
