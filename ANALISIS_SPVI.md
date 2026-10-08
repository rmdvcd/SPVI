# SPVI — Auditoría técnica sin anestesia

> **Aviso (2026-10-08): informe histórico, no estado actual.** El análisis siguiente describe únicamente el commit `2e5a149`; desde entonces cambiaron la versión, el esquema, los tests, la CI y los documentos. La última evidencia de CI y los pendientes actuales están en [docs/VERIFICACION.md](docs/VERIFICACION.md) y [Pendiente.md](Pendiente.md). Por ejemplo, los tests instrumentados que este informe encontró rotos ya compilaron después, aunque aún no se han ejecutado en dispositivo.

**Alcance histórico:** commit `2e5a149` («Primer commit: Proyecto inicial»), rama de trabajo `arena/3c116bea-spvi`.
**Método:** lectura completa de los 6 módulos (38 903 líneas Kotlin en `src/main`, 434 archivos `.kt`),
manifiesto, Gradle, documentación (4 673 líneas Markdown), capturas y herramientas. Análisis **estático**.

**Límite honesto y no negociable:** este entorno **no tiene JDK ni Android SDK** (no hay `javac`, ni `libjvm.so`,
ni `ANDROID_HOME`), y la salida a internet está limitada a GitHub, Maven Central, PyPI y codeload. Por eso:
**no he compilado, no he ejecutado un solo test, no he abierto un APK.** Todo lo que sigue son afirmaciones
derivables del código y de los números medidos, y está marcado como *inferencia* cuando depende del tiempo de
ejecución. La ironía es la primera conclusión del informe: **nadie con acceso a este repositorio puede demostrar
que SPVI compila**, y las tres fuentes internas se contradicen sobre si alguien lo hizo.

---

## 0. Veredicto en una página

**SPVI es, con diferencia, el mejor proyecto Android "de un solo autor" que se puede auditar a este nivel de
detalle: arquitectura limpia, criptografía bien aplicada, dinero sin `Double`, migraciones explícitas y una
documentación de 4 673 líneas.** Y también es un proyecto que **está a una tarde de fallar el primer build** por
deuda de verificación: tests instrumentados que no compilan, `lint-baseline.xml` vacío que su propio autor
reconoce no haber ejecutado, y un README que afirma haber pasado `spviCheck`, `assembleRelease` y R8 «todo
correcto» mientras `AGENTS.md` —el mismo repositorio— dice «lo que nunca se ha ejecutado: Gradle real, lint,
R8/ProGuard, Roborazzi, el APK en un teléfono». Una de las dos frases es falsa; el proyecto no sabe cuál.

| Eje | Nota | Una línea |
|---|---|---|
| **Arquitectura y código** | 8.5/10 | Capas estrictas, dominio JVM puro, cero `GlobalScope`, cero `runBlocking` en producción. Ejemplar. |
| **Seguridad** | 8/10 | SQLCipher + Keystore + claves fijadas + protocolo LAN con AEAD por sentido y nº de secuencia. Un DoS local sin autenticar y un default inseguro en el respaldo. |
| **UI / design system** | 8/10 | Tokens centralizados, contraste AA verificado por test, `contentDescription` obligatorio por tipo, estados completos. |
| **UX / IX** | 7/10 | Lenguaje llano, "no se vende sin turno", exportar "lo que se ve". Fricción: 5 iconos sin etiqueta, cero «Deshacer», ayuda que describe funciones ya eliminadas. |
| **Funcionalidad** | 7/10 | POS + inventario + insumos + caja + servicios + LAN + licencias offline: enorme. Falta multi-moneda (MLC/USD), impuestos, descuentos, devoluciones. |
| **Flujo de datos** | 7.5/10 | Reactivo con Room/DataStore, transacciones atómicas, DTO de respaldo versionado. Pero **no hay un solo `flowOn`**: toda la agregación corre en el hilo principal. |
| **Rendimiento y recursos** | 5.5/10 | Gráficos, Top 3 y filtros de inventario **en el hilo de UI**; `MediaStore` consultado en cada resume; exportaciones y listas sin `debounce`. Nada medido. |
| **Versatilidad** | 4.5/10 | Un idioma, una moneda, una orientación, un `applicationId`, sin `WindowSizeClass` ni recursos por tamaño. En tablet/POS se estira una columna de teléfono. |
| **Verificación / confianza** | 3/10 | 1 113 `@Test` escritos, ~136 instrumentados que **no compilan**, y ninguna ejecución demostrable. |

---

## 1. Qué es, con números

| Métrica | Valor |
|---|---|
| Módulos Gradle | 6 (`core`, `licencia`, `domain`, `data`, `designsystem`, `app`) |
| Líneas `src/main` | **38 903** (`app` 19 410 · `data` 8 156 · `domain` 5 786 · `designsystem` 3 655 · `licencia` 1 561 · `core` 335) |
| Archivos `.kt` | 434 (main 267, test 135, androidTest 25) |
| Tests declarados | **1 113** `@Test` (967 JVM, 136 instrumentados) |
| Líneas de test JVM | 13 830 (35 % del código de producción: ratio excelente) |
| Documentación | 4 673 líneas en 19 documentos + 284 capturas (21 MB) |
| Versión declarada | `versionName 0.27.1`, `versionCode 50` — **mientras el código contiene trabajo de 0.28.x, 0.29.x y 0.30.0** |
| Base de datos | Room **v11** (esquemas `5.json`–`11.json`), SQLCipher 4.6.1 |
| Permisos declarados | **11** (`CAMERA`, `INTERNET`, `POST_NOTIFICATIONS`, 3 de servicio en primer plano, `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`, 3 de almacenamiento con `maxSdkVersion`) + 2 de biometría fusionados |
| Stack | Kotlin 2.0.21 · AGP 8.7.3 · Compose BOM 2024.12.01 · Hilt 2.52 · Room 2.6.1 · OkHttp 4.12 · ML Kit barcode · Coil 3 · fastexcel · ZXing |

---

## 2. Puntos fuertes (lo que no hay que tocar)

### 2.1 Arquitectura y código
1. **Dependencias en un solo sentido y verificables:** `app → designsystem, data, domain`; `data → domain, licencia`;
   `domain → licencia → core`. `core`, `licencia` y `domain` son **JVM puro**: 7 682 líneas de lógica de negocio
   testeable sin emulador. Eso es lo que hace posible el 35 % de código en tests.
2. **Disciplina de corrutinas:** cero `GlobalScope`, cero `runBlocking` y cero `Thread.sleep` en código de
   producción (solo en `tools/`). 77 `viewModelScope.launch`, dispatchers inyectados (`@IoDispatcher`).
   `SesionVenta`, `GuardiaCambios`, `Cancelable` (`runCatchingCancelable`) demuestran que el autor conoce las
   trampas reales de Kotlin/Coroutines.
3. **Dinero correcto por construcción:** `Cup` en centavos `Long` con `Math.addExact`/`multiplyExact`
   (desbordamiento detectado, no silencioso), `BigDecimal` con `HALF_UP`, milésimas enteras para insumos,
   `Money.parse` con regex estricta. **Nunca `Double` para dinero.** Es el error nº 1 de los POS caseros y aquí
   está resuelto a nivel de tipo.
4. **Integridad del stock en SQL:** `UPDATE producto SET cantidad = cantidad + :delta WHERE ... AND (:delta >= 0 OR cantidad + :delta >= 0)`
   → el stock no puede quedar negativo aunque haya carreras. La venta, la producción, el cierre de turno y la
   importación de respaldo van cada una en **una sola transacción** (`db.tx { }`) con `abortar()` y comprobación
   de faltantes.
5. **Migraciones explícitas 3→4→5→…→11** con su `Migration`, con los esquemas JSON versionados en git y tests de
   migración que reconstruyen la BD desde el JSON. `fallbackToDestructiveMigrationFrom(1, 2)` limitado a dos
   versiones de desarrollo. Es la práctica correcta y casi nadie la cumple.
6. **Idempotencia y tolerancia de protocolo bien pensadas:** `ignoreUnknownKeys`, `explicitNulls = false`,
   comandos deduplicados por `clave` (`MemoriaComandos`), lotes de sincronización marcados por `uuid`. La
   convivencia de versiones mezcladas (0.24 vendiendo contra 0.26) está diseñada, no improvisada.
7. `!!` en 299 sitios, pero el patrón dominante en las capas bajas es `AppResult`/`AppError` con mensajes de
   usuario final (`errorIdentificacion`, `ErroresRemotos`). El estilo está mezclado, no abandonado.

### 2.2 Seguridad (aquí está lo mejor del proyecto)
8. **Cifrado en reposo completo y coherente:** SQLCipher con passphrase de 32 bytes envuelta por una clave
   AES-256-GCM del Android Keystore (StrongBox si existe, con degradación a TEE y luego a software *controlada*);
   DataStore cifrado **valor por valor con AAD = nombre de la clave** (así un valor no se puede trasplantar de
   clave); clave de dispositivo ECDH P-256 en Keystore con `PURPOSE_AGREE_KEY` en API ≥ 31 (la privada no sale)
   y por software envuelta en API 26–30 conservando la licencia al actualizar de 30 a 31.
9. **Anclas de confianza no configurables:** las claves de GL están fijadas en el binario con huella SHA-256 y
   `LicenseTrustTest` falla si no coinciden; la pantalla «Clave del emisor» se eliminó. La licencia se
   **re-verifica criptográficamente en cada evaluación**; no existe un flag «licenciado» que alguien pueda
   voltear. Un valor ilegible equivale a «ausente» (fallo seguro).
10. **Protocolo LAN propio, y bien hecho:** ECDH P-256 autenticado con HMAC del token del QR (16 B, un solo uso,
    10 min), HKDF con clave distinta por sentido, AES-256-GCM con el **número de secuencia en el AAD** (no se
    puede reordenar ni repetir), tope de trama de 16 MB, gunzip acotado a 4×, y el `Rechazo.QUITADA` que hace
    borrar los datos va **autenticado con MAC** (nadie en la red puede provocarlo). El permiso de cada comando se
    comprueba **en el servidor con los datos de la principal** (`EjecutorComandos`), no en el cliente.
11. **Respaldos:** formato propio versionado (`SPVIBAK` v4) con cabecera autenticada como AAD, PBKDF2 310 000
    iteraciones (10 000 sin contraseña), lectura de la versión anterior, límite anti zip-bomb de 128 MB,
    validación **antes** de tocar la base y la importación en una sola transacción.
12. **Superficie externa mínima:** `allowBackup=false` + `dataExtractionRules` que excluyen todo, cero
    telemetría (se **eliminan** del manifiesto los servicios `datatransport` de ML Kit y el `ACCESS_NETWORK_STATE`),
    `FileProvider` no exportado limitado a `cache/compartir` y `cache/fotos`, cero `<queries>`, cero `READ_SMS`,
    `FLAG_SECURE` en las pantallas con datos personales, y **la app no registra datos personales** (R8 elimina
    `android.util.Log` en release).
13. **Antirreinicio de la prueba:** la fecha de inicio se guarda cifrada **fuera de la app** (PNG en
    `Pictures/SPVI` + `.bin` en Download y Documents) y se combina con reloj monótono `elapsedRealtime` para
    detectar un reloj atrasado. Es un nivel de ingenio que no se ve ni en productos comerciales. Y está hecho
    con el respeto de pedir el permiso **una sola vez** y seguir funcionando si se niega.
14. **Verificación de confianza en actualizaciones:** el APK solo se ofrece si su SHA-256 coincide, y Android
    exige la misma firma para instalarlo; la lista de revocadas se verifica con ECDSA contra la clave fijada de
    GL, y si no verifica **se ignora** (nunca revoca por un archivo corrupto).

### 2.3 UI, UX e IX
15. **Design system con reglas ejecutables:** ninguna pantalla define colores/tamaños; `ContrastTest` **falla el
    build** si un par baja de WCAG AA; `SpviIconButton(contentDescription: String)` hace **imposible** compilar un
    botón de solo icono sin descripción para TalkBack; los gráficos llevan resumen para lector de pantalla;
    `SpviSpacing` es una rejilla de 8 sin valores sueltos; `LineBreak.Heading/Paragraph` evita viudas y huérfanas
    en los títulos; la letra al 200 % tiene capturas propias (`*_letra_200.png`) y comportamiento definido
    (botones que crecen, importe que se apila).
16. **Estados de pantalla completos:** hay capturas y código para *cargando*, *vacío*, *sin resultados*, *error
    con Reintentar* en Inicio, Inventario, Registros y Servicios. La mayoría de las apps profesionales no cubre
    los cuatro.
17. **Decisiones de producto argumentadas por escrito:** «no se vende sin turno», «exportar lo que se ve»,
    «vender un elaborado desde sus insumos con *Alcanza para N*», «el fondo de caja es obligatorio y la
    secundaria no cierra sin contar», «los datos para clientes no llevan costo ni margen». Hay diecinueve
    documentos que explican el *porqué*, no solo el *qué*.
18. **Capturas como documentación viva y automatizable:** 284 PNG claro/oscuro generados con **Robolectric +
    Roborazzi sin emulador ni red** (con el `android-all` resuelto por Gradle en modo offline). Esto es
    infraestructura de calidad de verdad.

### 2.4 Ingeniería de build
19. `spviPermisos` lee el **manifiesto fusionado** de debug y release y **falla el build** si aparece un permiso
    fuera de la lista autorizada (con la única excepción razonada del permiso de nivel *signature* de
    androidx.core). Es una defensa contra el *feature creep* de dependencias que casi nadie implementa.
20. Los tests JVM se ejecutan **sin red por diseño**: proxy `http(s)` a `127.0.0.1:9` con allowlist solo para
    loopback, y `user.timezone=UTC`. Un test que dependiera de internet **falla en vez de pasar por suerte**.

---

## 3. Puntos débiles y defectos

### 3.1 Lo grave (bloquea la entrega)

**D1 — 🔴 No hay ninguna evidencia de que el proyecto compile, y la documentación se contradice a sí misma.**
- README, línea 122: *«Última verificación (0.27.1, Gradle real con JDK 17 y SDK 35: `spviCheck` +
  `recordRoborazziDebug` + `assembleRelease` + `tools/verificacion/api_minima.py`, todo correcto; 850 tests JVM
  […]; lint sin errores; R8 sin clases ausentes; API mínima 26 sin llamadas no permitidas)»*.
- `AGENTS.md`: *«**Lo que nunca se ha ejecutado** (verifícalo tú): Gradle real, lint, R8/ProGuard en release,
  Roborazzi, el APK en un teléfono y la prueba con dos teléfonos.»*
- `app/lint-baseline.xml`: *«Se entrega **VACÍO** porque el entorno donde se escribió **no tiene el SDK de
  Android**. La primera vez […]: `./gradlew :app:updateLintBaseline`»*.

Las tres no pueden ser ciertas a la vez. Y el párrafo del README además tiene **markdown roto** (un `**` sin
cerrar que se traga el paréntesis y mezcla la lista de versiones con la verificación de la 0.22.0). Consecuencia
práctica: **no hay ninguna prueba de que R8 no destruya los serializadores de `kotlinx.serialization` usados
explícitamente (`Mensaje.serializer()`, `RespaldoDto.serializer()`, rutas `@Serializable` de Navigation), ni de
que Hilt genere el grafo, ni de que Room acepte las 135 `@Query`, ni de que el APK arranque.** Es la deuda más
cara del proyecto y la única que no se puede arreglar leyendo código.

**D2 — 🔴 Tres archivos de test no compilan; `spviInstrumentedTests` no puede pasar hoy.**
- `data/src/androidTest/.../RepositoriosRoomTest.kt` usa `Producto(..., codigo = "7501055363056")` y
  `productos.porCodigo(...)`: **los dos se eliminaron** con el código de barras (0.30.0). `Producto` no tiene
  campo `codigo` y `ProductoRepository` no tiene `porCodigo`.
- `data/src/androidTest/.../ConfiguracionInicialInstrumentedTest.kt` y `.../RespaldoRoomTest.kt` usan
  `ConsentimientoRed`, `guardarConsultasEnLinea(...)` y `consultasEnLinea`: **los tres se eliminaron** y solo
  sobreviven en esos tests (`Preferencias` hoy tiene exactamente 4 campos, ninguno de red).
- Además, `tools/escaner/*` (el arnés «en vivo» del escáner) importa 6 tipos eliminados
  (`FuenteProductos`, `ProductoEnLinea`, `NombreEnLinea`, `DescargarFoto`, `BuscarProductoEnLinea`,
  `ConsentimientoRed`, `EscanerViewModel`) y `data/build.gradle.kts` sigue comentando el escáner como
  «única red de la app». No rompe el build (está fuera de los *source sets*), pero es código muerto que engaña.

**D3 — 🟠 El código va tres versiones por delante de la versión y de la documentación.**
`versionName 0.27.1` / `versionCode 50`, y sin embargo el código de producción contiene comentarios y
comportamiento de **0.28.0** (seminegrita en importes y fechas), **0.29.0** (semilla «Bodega cubana» de 18 meses
en `:domain/seed` + `Migracion1011Test`), **0.29.1/0.29.2** (tops de empleado y cliente, `vendedoresDe(ids)`) y
**0.30.0** (eliminación total del código de barras, migración 10→11). Mientras:
- el README sigue anunciando *«Codigo de barras»*, la columna `codigo` en la tabla `producto` y
  «consultas de códigos de barras» como función de INTERNET;
- el README dice **«Room v10»** y el código es **v11** (`data/schemas/.../11.json`);
- El informe original marcó `docs/HISTORIAL_DESARROLLO.md` como corrupto en UTF-8. En la revisión estática del 2026-10-08 el archivo actual decodifica como UTF-8 y no contiene U+FFFD ni secuencias habituales de mojibake; se conserva la afirmación únicamente como hallazgo histórico de aquel commit.
- `UI_UX_IX.md` sigue anclado a «la versión **0.19.3**» y `SECURITY.md` a «la versión **0.26.0**» (mientras
  incluye contenido de la 0.27.0).

**D4 — 🟠 El repositorio referencia documentos que no existen.**
`MANUAL_USUARIO.md`, `PRUEBAS_DISPOSITIVO.md`, `Pruebas.md`, `Pendiente.md`, `opencode.json` y
`.github/workflows/ci.yml` **no están en el árbol**. El README los lista con enlaces, dice que la CI «está
preparada pero inactiva», y `AGENTS.md` —que es el contrato de trabajo para agentes— ordena empezar por
`Contexto.md`, `Pendiente.md` y `Pruebas.md` y afirma que `opencode.json` los carga en cada sesión. Un manual de
usuario final que no existe es un agujero de producto, no de documentación: es el material que el dueño del
negocio necesita.

### 3.2 Seguridad

**D5 — 🟠 Denegación de servicio de la sincronización sin autenticar (LAN).**
En `ServidorSync.sesion()`, tras derivar las claves de sesión y **sin ninguna prueba de posesión**, la conexión
se registra y **se cierra la anterior**:
```kotlin
val s = Sesion(e.id, socket, canal)
sesiones.put(e.id, s)?.cerrar() // «la misma app reconectando: se queda la conexión nueva»
```
Cualquier equipo en la misma wifi que conozca el `negocioId` —viaja **en claro** en cada `Hola` y está impreso en
el QR— y un `empleadoId` (entero pequeño, también en claro en el saludo) puede **expulsar la sesión legítima del
empleado en bucle**. No hay fuga de datos (no puede descifrar nada: sin la clave del empleado no pasa del primer
AES-GCM) pero rompe la sincronización del local, es trivial de programar y no hay límite de intentos ni de
conexiones. *Arreglo:* reemplazar la sesión anterior **solo después** de que la nueva descifre su primer mensaje,
o mantener ambas y enrutar por sesión en vez de por empleado.

**D6 — 🟡 Oráculo de enumeración y superficie innecesaria en el saludo.**
`Hola` responde `OTRO_NEGOCIO`, `DESCONOCIDA` o `HolaOk` sin autenticación previa: cualquiera en la red puede
saber si un negocio y un empleado existen. El identificador de negocio es de 12 bytes aleatorios (bien), pero
viaja en claro en cada conexión. Es superficie regalada en el escenario que el propio proyecto documenta
(«zona wifi del teléfono principal», es decir, una red compartida).

**D7 — 🟠 El respaldo «sin contraseña» está activado por defecto y contiene datos personales.**
«Proteger con contraseña» viene **apagado**; en ese modo la clave sale de un secreto ofuscado con XOR en el APK
(`SECRETO`/`MASCARA`) y 10 000 iteraciones, con la clave del archivo (que el usuario manda por WhatsApp o sube a
Drive) abrible por cualquiera que tenga SPVI o extraiga el secreto del APK. El respaldo incluye **ventas,
clientes con carné y teléfono, el ID y el CI de la licencia**. Está declarado como «riesgo aceptado» en
`SECURITY.md`, pero un riesgo aceptado en un *default* es una decisión distinta a un riesgo residual: aquí el
usuario que no lee la letra pequeña queda desprotegido. *Arreglo:* activarlo por defecto y exigir una acción
explícita («Exportar sin contraseña») para desactivarlo.

**D8 — 🟡 Fallo seguro con efecto colateral: «si no verifica, ignoro» es *fail-open* para el atacante.**
`ListaRevocaciones.verificar` devuelve `null` y el archivo se descarta; el APK solo se instala si el SHA-256
coincide con lo que publica la **misma API** (HTTPS sin *pinning*, sin verificación de firma del APK por la app).
Android exige la misma clave de firma para instalar, así que no se puede colar un APK ajeno; pero **un
intermediario con una CA propia puede retener la actualización, servir notas de versión falsas o borrar
`revocadas.json`** y el teléfono revocado sigue funcionando. Con una licencia offline no tiene arreglo completo,
pero sí detectarlo (recordar que la lista debía llegar, avisar tras N días sin poder consultarla).

**D9 — 🟡 Sin PIN propio, sin detección de root, sin ofuscación fuerte.** Todo está documentado y aceptado
(el modelo es «licencia offline, se puede parchear el APK»), pero conviene tenerlo presente antes de invertir
más en el canal de licencias: con root, `Frida` o un APK reempaquetado, la verificación ECDSA se salta.

### 3.3 Rendimiento y uso de recursos (lo más flojo, y lo peor medido)

**D10 — 🔴 Toda la agregación de Inicio corre en el hilo principal.** Hecho verificado, no sospecha:
- **no existe ni un solo `flowOn`** en `app`, `domain` ni `data` (0 ocurrencias);
- `InicioViewModel` llama a `obtenerGraficos(...)` y `obtenerResumen(...)` dentro de `viewModelScope.launch { }`
  (Main) **sin `withContext`**;
- `ObtenerGraficosPeriodo` hace `Estadisticas.serie(ventas.entre(desde, hasta).validas(), ...)`: miles de ventas
  mapeadas a dominio, agrupadas en cubos y ordenadas **en el hilo de UI**;
- `ObtenerResumenGeneral` recorre en el mismo hilo todas las ventas de 30 días para el Top 3, los métodos de
  pago, los servicios, los empleados y los clientes, más todo el inventario activo.

Con el propio generador de datos de prueba del proyecto (548 días, ~470 turnos, miles de ventas) y el período
«Año» esto no es teoría: es *jank* de cientos de milisegundos o segundos en un gama baja, que es exactamente el
público objetivo declarado. *Arreglo:* `flowOn(io)` en la cadena, `withContext(io)` alrededor de los casos de
uso, y agregación en SQL (`GROUP BY` por cubo) para los gráficos.

**D11 — 🟠 `MediaStore` consultado en cada vuelta a primer plano.**
`LicenseManager.evaluate()` llama a `registroExterno.sincronizar(...)` **en cada evaluación**, y `evaluate()`
corre al arrancar, en **cada `onResume`** (`RootViewModel.refrescar()`) y en el temporizador de vigilancia.
`RegistroPruebaAndroid.sincronizar` hace: lectura interna + **tres consultas a `MediaStore`**
(`Pictures`, `Download`, `Documents` filtrando por nombre) + parseo de chunk PNG + descifrado + comparación, y
en su caso escritura. La lógica de «no reescribir si está al día» (`alDia`) ahorra escrituras, **no lecturas**.
En un teléfono de gama baja son tres *binder calls* al proveedor de medios cada vez que el usuario vuelve a la
app, para nada. *Arreglo:* cachear el resultado en memoria/DataStore y repetir la pasada solo si cambió el día,
si la instalación es nueva o cada N horas.

**D12 — 🟠 Buscadores sin `debounce` y filtrado en memoria.**
`InventarioViewModel.buscar(texto)` actualiza el filtro y `ObservarInventario` vuelve a aplicar
`InventarioFiltro.aplicar(...)` (filtrado + ordenación + construcción de la vista, con `Money.format` por fila)
**en el contexto del colector, que es Main** porque el `stateIn` es `viewModelScope`. `RegistrosViewModel` usa
`flatMapLatest` con `distinctUntilChanged()` pero **sin `debounce`**: teclear «9» consultas nuevas en Room, cada
una una `LIKE` con subconsulta. En el `Inventario` todo el cálculo es en memoria sobre la lista completa, así que
crece linealmente con el catálogo. *Arreglo:* `debounce(200–300 ms)`, `flowOn(io)` y filtrar en SQL.

**D13 — 🟡 Historial sin agregación en SQL ni política de retención.** Los gráficos, las exportaciones y los
Top recorren el historial completo porque la agregación se hace en Kotlin (`Estadisticas.serie`). No hay
`GROUP BY` por cubo, no hay archivado/purga y no hay «solo los últimos N meses» más allá del filtro de consulta.
Con años de uso (y sin recortar), todo se vuelve progresivamente más lento y más grande en una sola base SQLite.

**D14 — 🟡 Coste de fondo aceptado y no medido.** La principal mantiene `ServerSocket` + anuncio NSD + sondeo de
direcciones **cada 10 segundos** mientras vive el proceso, y el **servicio en primer plano** (`connectedDevice`)
lo mantiene vivo con turnos abiertos: notificación permanente y proceso residente, que es lo que el dueño aceptó.
La secundaria sostiene un socket persistente con latido de 30 s. Nada de esto tiene una medición (ni
`Battery Historian`, ni macrobenchmark, ni *baseline profile* generado: se añadió `profileinstaller` y ahí se
quedó).

**D15 — 🟡 Cero medición de rendimiento y recomposición.** No hay `androidx.benchmark`, ni `Macrobenchmark`, ni
prueba que cuente recomposiciones (lo admite `UI_UX_IX.md`). Las decisiones de rendimiento se han tomado «a mano»
(`drawWithCache`, claves estables).

*Lo que **sí** está bien y me habían hecho sospechar: las fotos se decodifican con `inJustDecodeBounds` +
`inSampleSize` y se re-codifican a ≤ 1024 px/JPEG 85 (sin EXIF/GPS) — sin riesgo de OOM; y la exportación a PNG
recicla cada bitmap (`bmp.recycle()`) en el mismo bucle en que lo escribe — el pico es una página, no el
documento entero.*

### 3.4 Versatilidad

**D16 — 🟠 Una sola forma de pantalla.** Ni `WindowSizeClass`, ni `values-sw600dp`, ni recursos alternativos.
`Overlays.kt` dimensiona los diálogos con `LocalConfiguration.screenHeightDp.dp` (fracción de pantalla, no
contenido) y `InicioScreen` calcula el ancho del gráfico con `screenWidthDp`. `spviContentWidth()` (600 dp máx.)
amortigua el destrozo en tablet, pero no hay diseño adaptativo: en un tablet, un plegable, en multitarea o en un
terminal POS Android (el *hardware* natural para un POS cubano) esto es una columna de teléfono estirada.

**D17 — 🟠 Internacionalización inexistente.** `strings.xml` tiene **3 cadenas**; **toda** la interfaz está en
objetos Kotlin (653 `const val ... = "..."`, 27 objetos `Textos*`). Es centralizado y testeable, y para Cuba es
defendible, pero: (a) cambiar terminología para un cliente (p. ej. «turno» → «caja») exige recompilar;
(b) traducir a otro idioma es un refactor; (c) `supportsRtl="true"` está declarado mientras no hay un solo
recurso traducido y el diseño asume LTR. Prometer RTL sin probarlo es deuda silenciosa.

**D18 — 🟡 Un `applicationId`, cero *flavors*, cero configuración de build.** No hay forma de compilar una
variante demo/blanca, ni de desactivar la licencia, ni de cambiar el repositorio de actualizaciones sin recordar
`-PspviGithubRepo=usuario/repo`. Con `GITHUB_REPO` vacío por defecto, **el APK tal cual sale del repo no puede
actualizarse ni aplicar revocaciones**.

**D19 — 🟠 Modelo de negocio demasiado estrecho para el mercado que dice atender.** El propio README habla de
Cuba, pero: los importes son **CUP y nada más** (el sufijo « CUP» está incrustado en `Money.format` con
`DecimalFormat` en *locale* US); hay 2 métodos de pago (efectivo y transferencia); no hay **multi-moneda**
(MLC/USD, que es como opera medio comercio cubano), ni **tasa de cambio**, ni impuestos, ni descuentos por línea,
ni devoluciones parciales, ni tarifas por servicio configurables por cliente. Es la limitación funcional que más
dinero puede costar: obliga a operar «en CUP» o a hacer trampas con los precios.

**D20 — 🟡 Un usuario por teléfono.** El «empleado» solo existe como **app secundaria en otro teléfono**. No hay
usuarios/roles en el mismo equipo, ni PIN propio (depende del bloqueo del teléfono). Un negocio con un solo
teléfono de trabajo y dos vendedores, o con el dueño ocupando el equipo, no puede distinguir quién hizo qué.

### 3.5 UI, IX y UX

**D21 — 🟠 La Ayuda miente.** `AyudaContenido` (10 temas fijos) y algunas rutas de Ajustes siguen describiendo
el escáner de códigos de barras y las «consultas de códigos en internet», **eliminados en 0.30.0**. En una app
pensada para usuarios sin conocimientos técnicos, una ayuda que describe funciones que no existen es peor que no
tener ayuda: es la vía directa al «esto no funciona» del cliente.

**D22 — 🟠 Cinco iconos sin etiqueta en la barra inferior.** Es una decisión documentada (con *tooltip* y
TalkBack), pero choca con el propio usuario objetivo del proyecto. Un vendedor nuevo mirando la pantalla no sabe
qué es el icono de «ManoRecibe» (Servicios) ni por qué «History» es Registros. En una app de trabajo usada a
diario por turnos, la curva de aprendizaje se paga todas las semanas con personal nuevo.

**D23 — 🟡 Sin «Deshacer», solo confirmaciones.** Decisión explícita del dueño, y es defendible... hasta que se
junta con las confirmaciones sistemáticas: en un flujo de venta rápido, cada acción destructiva cuesta un toque
extra y una lectura. Con bases de datos transaccionales, un `Undo` de 5 segundos en el *snackbar* habría sido más
barato de construir que la batería de diálogos que lo sustituye.

**D24 — 🟡 Búsqueda sin señal de progreso.** El diseño evita el parpadeo conservando la lista anterior, pero no
hay indicador de «buscando» en el campo: en un gama baja, con el filtrado en el hilo principal (D12), el usuario
percibe que la app «se quedó».

**D25 — 🟡 Diálogos dimensionados por pantalla, no por contenido** (`Overlays.kt`, `screenHeightDp`). Con letra
al 200 % las capturas se ven correctas, pero es un cálculo frágil que no depende del texto que va dentro.

### 3.6 Código y proceso

**D26 — 🟡 299 `!!` conviviendo con `AppResult`.** Cada `!!` es un *crash* potencial en un código que **nadie ha
ejecutado**. No es un defecto de estilo: en un proyecto sin verificación de ejecución, `!!` es deuda sin red de
seguridad. Los *fakes* y los tests cubren bastante, pero los `!!` están sobre todo donde no hay tests: UI y
ViewModels.

**D27 — 🟡 Trazabilidad opaca para terceros.** Los comentarios son extraordinarios en detalle, pero están
plagados de referencias a planes que **no están en el repositorio** («P37», «P68b», «P74», «§5.4», «decisión
D2.5», «A21»). Para el propio autor en dos años, o para cualquiera que herede esto, la mitad del *porqué* vive en
documentos que no existen.

**D28 — 🟡 Sin CI ejecutable.** `spviCheck` existe y es bueno, pero el *workflow* no está y nunca se ha corrido.
`spviCheck` además depende de `assembleDebug` + `lintDebug` **con `checkDependencies = true`** y un baseline
**vacío**: el primer `spviCheck` real será un muro de errores de lint (probablemente cientos), lo cual garantiza
que la próxima vez tampoco se ejecute.

**D29 — 🟡 Higiene del repositorio.** Se ha versionado `.kotlin/sessions/kotlin-compiler-…salive` (archivo de
sesión del compilador) y `.kotlin/` no está en `.gitignore`; `data/build.gradle.kts` mantiene un comentario sobre
el escáner ya eliminado; `EsquemaTest` documenta `6.json` cuando ya hay `11.json`; el `README` arrastra un
párrafo con markdown roto (D1).

**D30 — 🟡 `Log` en producción (`CamaraEscaner.kt`).** Una sola llamada, aviso genérico, eliminada por R8 en
release: inocuo y coherente con lo documentado, pero es la excepción que demuestra que el «cero logs» se mantiene
por disciplina, no por una comprobación automática.

---

## 4. Tabla de acción priorizada

| # | Sev. | Qué | Dónde | Coste estimado |
|---|---|---|---|---|
| 1 | 🔴 | **Compilar y ejecutar `spviTests` + `spviCheck` en Gradle real y publicar la salida.** Si no pasa, arreglar y regenerar `lint-baseline.xml`; si pasa, corregir `AGENTS.md` o el README (uno de los dos miente). | todo el repo | 1–2 días (más el `git bisect` que salga) |
| 2 | 🔴 | Arreglar los 3 tests instrumentados que no compilan (quitar `codigo`/`porCodigo`, `ConsentimientoRed`/`guardarConsultasEnLinea`). | `data/src/androidTest` | 2 h |
| 3 | 🔴 | Sacar la agregación de Inicio del hilo principal: `withContext(io)` en los casos de uso + `flowOn` en las cadenas reactivas; después, `GROUP BY` en SQL. | `domain/usecase/InicioUseCases.kt`, `InicioViewModel`, DAOs | 1–3 días |
| 4 | 🟠 | Cerrar la sesión anterior **solo** tras el primer descifrado correcto (+ límite de conexiones/Intentos). | `data/sync/ServidorSync.kt` | 3 h |
| 5 | 🟠 | Activar «Proteger con contraseña» por defecto en el respaldo y exigir acción explícita para exportar sin ella. | `respaldo/` | 4 h |
| 6 | 🟠 | Cachear la lectura del registro de prueba (no 3 consultas a `MediaStore` en cada `onResume`). | `RegistroPruebaAndroid` + `LicenseManager.evaluate` | 3 h |
| 7 | 🟠 | Alinear la documentación con el binario: versión (0.27.1 vs 0.28–0.30), Room v11, quitar el escáner del README/SECURITY/Ayuda, arreglar el UTF-8 de `HISTORIAL_DESARROLLO.md`, reponer o desenlazar `MANUAL_USUARIO.md`, `PRUEBAS_DISPOSITIVO.md`, `Pendiente.md`, `opencode.json`, `ci.yml`. | docs + `AyudaContenido` | 1–2 días |
| 8 | 🟠 | `debounce` + `flowOn(io)` + filtrado en SQL en Inventario y Registros. | `InventarioViewModel`, `RegistrosViewModel`, DAOs | 1 día |
| 9 | 🟠 | Adaptatividad mínima real: `WindowSizeClass` (lista+detalle en ≥ 600 dp), recursos por tamaño, diálogos por contenido. | `:designsystem`, `:app` | 3–5 días |
| 10 | 🟠 | Decidir el mercado: si se quiere vender a MIPYMES, multi-moneda (CUP/MLC/USD + tasa) y descuentos/impuestos dejan de ser opcionales. | `core/money`, `domain`, esquema | 1–2 semanas |
| 11 | 🟡 | Extraer los textos a recursos (`strings.xml`) con `Textos*` como envoltorio: sin i18n, al menos sin recompilar por un texto. | `:app` (653 constantes) | 1 semana |
| 12 | 🟡 | Medir antes de optimizar más: macrobenchmark de arranque, batería con la principal sirviendo, y un mapa de calor de la pantalla de Inicio con el *seed* de 18 meses cargado. | nuevo módulo `:benchmark` | 3 días |

## 5. Cómo verificar todo esto en 30 minutos (para el dueño)

```bash
# 1) ¿Compila y pasan los tests JVM? (esto decide si el README o AGENTS.md tiene razón)
./gradlew clean spviTests --no-build-cache

# 2) ¿Compilan los instrumentados que hoy están rotos?
./gradlew :data:compileDebugAndroidTestKotlin

# 3) Lint + APK + permisos
./gradlew spviCheck          # ojo: el baseline está VACÍO, espere errores

# 4) Release real (R8, serialización, Hilt) con firma
./gradlew spviRelease        # y luego instalar el APK en un teléfono y abrirlo

# 5) Sin Android SDK (descarga JDK+kotlinc; requiere salida a api.adoptium.net y dl.google.com)
bash tools/verificacion/verificar.sh
```
Si (1) falla, **el proyecto no está donde dice la documentación** y todo lo demás es secundario.
Si (1) pasa, el informe cambia de orden pero no de contenido: la deuda de rendimiento (D10–D13) y la de
versatilidad (D16–D20) siguen ahí, y ahora con evidencia.

---

*Informe generado a partir del estado del repositorio en `arena/3c116bea-spvi`. Sin compilación ni ejecución:
ninguna afirmación de rendimiento se ha medido en dispositivo, y se señala como inferencia cuando corresponde.*
