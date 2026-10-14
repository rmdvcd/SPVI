# Auditoría integral de SPVI — 2026-10-14

**Base auditada:** `main` @ `29f909fead49bd3cd77aa4d680bc659b7e7011db` (rama de trabajo `arena/48e006de-spvi`, sin cambios de código: esta auditoría es solo lectura + este informe).
**Alcance:** app Android (Kotlin/Compose, 6 módulos Gradle), principal de escritorio (`desktop/`, Python/Flask + JS), app iOS (`flutter_app/`, Flutter/Dart), herramientas (`tools/`), CI (`.github/workflows/`), dependencias y documentación.
**Método:** lectura del árbol completo, conteos medidos con scripts sobre el código versionado, revisión manual de criptografía/red/BD, contraste con las auditorías previas (`ANALISIS_SPVI.md`, `docs/AUDITORIA_CRITICA_2026-10-09.md`, `PLAN_CORRECCIONES.md`) verificando qué sigue vigente, y ejecución real de lo ejecutable en este entorno.

## Qué se ejecutó y qué no (límites)

| Comprobación | Resultado |
|---|---|
| Tests de escritorio (Python `unittest`, venv limpio con `requirements` del proyecto) | ✅ **129 tests, OK** (9,6 s) |
| Estado del CI de GitHub (`gh run list`) | ✅ Últimas corridas de `main` y de la rama del PR #14 en **success** (tests JVM, lint, release R8, permisos, API mínima, empaquetado Windows) |
| Derivación del secreto del respaldo «sin contraseña» solo desde el código público | ✅ **Reproducida** (ver S-01) |
| Consulta a la API de GitHub: releases y tags de `rmdvcd/SPVI` | ✅ **0 releases, 0 tags** (ver I-05) |
| Compilación Android / `spviTests` / lint / R8 en este entorno | ❌ Sin JDK ni Android SDK; se apoya en el CI (verde) |
| Tests Flutter (`flutter test`) | ❌ Sin Dart/Flutter; Codemagic no se verificó desde aquí |
| Tests instrumentados (145) y capturas Roborazzi (146) | ❌ Ningún CI los ejecuta (solo los compila); ver I-02 |

> Toda afirmación que depende de tiempo de ejecución y no pudo medirse está marcada 🧪 (inferencia). Las rutas citan `archivo:línea` del commit auditado.

---

## 0. Veredicto en una página

SPVI sigue siendo un proyecto con un núcleo de ingeniería notablemente sólido — dominio JVM puro, dinero en centavos sin `Double`, SQL parametrizado, criptografía de licencias y de sincronización bien construida, permisos mínimos con control automático — y con **los mismos tres problemas de cierre** que ya señalaban las auditorías anteriores, más dos hallazgos nuevos en la superficie LAN:

1. **El respaldo «sin contraseña» sigue siendo el modo por defecto y su «secreto» está publicado** en este repositorio público (S-01). Lo volví a derivar en segundos, solo con el código. La migración entre teléfonos reutiliza ese modo, así que hereda el problema (S-02).
2. **El servidor LAN de Android cierra la sesión legítima antes de autenticar a la nueva** y acepta un `deviceId` sin autenticar (S-03, S-04). Un atacante pasivo en la wifi (o activo con lo que el propio protocolo emite en claro) puede dejar sin sincronización a una secundaria o envenenar el destino de su sub-token de licencia. La versión de escritorio del mismo protocolo hace esto último igual, pero el reemplazo de sesión lo hace bien: después de descifrar el primer mensaje.
3. **Hay tres productos, no uno:** Android es el producto real; el escritorio ya es funcional como principal (129 tests en verde) pero guarda la base de datos sin cifrar (S-06); **la app iOS es un prototipo con datos de demostración en memoria** — sin persistencia del negocio, sin red local, sin QR — aunque su `Info.plist` ya promete cámara, fotos, Face ID y Bonjour (I-03). Publicarla tal cual sería vender una maqueta.
4. **La verificación sigue coja donde más duele:** falta `12.json` del esquema Room v12, los 145 tests instrumentados y las 146 capturas no se ejecutan nunca, y no hay releases publicadas, así que actualización y revocación son hoy **código que falla abierto sin haber funcionado nunca en producción** (I-01, I-02, I-05).
5. **El código muerto es poco pero concreto** (funciones de respaldo legacy nunca llamadas en escritorio, `comprobar_fijadas()` nunca invocada en escritorio, 42 imports sin uso en Kotlin, `profileinstaller` sin perfil), y **la optimización pendiente importante es una sola**: la agregación de Inicio/Registros sigue cargando históricos completos en memoria en vez de agregar en SQL (O-01, O-02).

| Eje | Nota | Una línea |
|---|---|---|
| Arquitectura y código Android | 8/10 | Capas estrictas, 0 `GlobalScope`/`runBlocking` en producción, 903 tests JVM ejecutables en CI |
| Seguridad Android | 6,5/10 | Cripto fuerte, pero S-01/S-02/S-03/S-04 son reales y explotables en su contexto |
| Seguridad escritorio | 6/10 | Web local cuidada (CSRF, CSP, PBKDF2 310k); BD en claro y acceso sin clave por defecto |
| App iOS (Flutter) | 3/10 | Licencias reales; negocio = datos demo volátiles. Incompleta, no publicable |
| Código muerto / higiene | 7/10 | Poco, localizado y barato de eliminar (sección 3) |
| Rendimiento | 6/10 | Hilo principal ya saneado (F1 parcial); falta agregación SQL y paginación |
| Verificación | 5/10 | CI Android+Windows verde; instrumentados, capturas e iOS fuera del circuito |
| Documentación | 7/10 | Exhaustiva, pero con deriva de versión y 2 enlaces rotos verificados |

---

## 1. Inventario medido hoy

| Métrica | Valor medido |
|---|---|
| Módulos Gradle | 6: `:core` `:licencia` `:domain` `:data` `:designsystem` `:app` |
| Kotlin `src/main` | 282 archivos, **42 087 LOC** (app 21 545 · data 8 464 · domain 5 902 · designsystem 3 783 · licencia 2 055 · core 338) |
| Tests Kotlin `@Test` | **1 049** en `src/test` (de ellos **146 capturas** Roborazzi que `spviTests` excluye → **903** ejecutables en CI) + **145** instrumentados en `src/androidTest` |
| Escritorio | 46 archivos Python (app+tests), 129 tests ✅ · 11 archivos JS (sin `innerHTML`: todo `textContent`/`createElement`, ver §4) |
| Flutter/iOS | 35 archivos Dart: 7 925 LOC `lib/` + 1 379 de tests (sin ejecutar aquí) |
| Versión declarada | `versionName 0.30.0`, `versionCode 51` (`app/build.gradle.kts:49-50`; también en `pubspec.yaml`) — pero el código contiene trabajo etiquetado 0.31.0 (migración QR, sub-tokens, BD v12): ver I-04 |
| Base de datos | Room **v12** (`SpviDatabase.kt:73`), esquemas exportados solo hasta `11.json`: ver I-01 |
| Permisos Android | 11 (+2 de biometría por fusión), con tarea `spviPermisos` que falla si aparece otro ✅ |
| Peso versionado | `app/capturas/` 286 PNG ≈ 22 MB · `.playwright-mcp/` 30 archivos de sesión de navegador versionados por error |
| En GitHub | CI verde; **0 releases, 0 tags**; 1 040 archivos rastreados; sin keystores/`.env`/`.pem` rastreados ✅ (verificado) |

**Deuda heredada de auditorías anteriores — estado verificado hoy:**

| Hallazgo anterior | Estado |
|---|---|
| CI inexistente / roja | ✅ **Resuelto.** CI con 5 trabajos + workflow Windows, verde en `main` |
| F1: trabajo pesado en hilo principal | 🟡 **Parcial.** `flowOn(io)`/`withContext(io)` en Inicio/Inventario/Registros/Servicios/Elaboración y `debounce(250)` en buscadores existen; falta la agregación en SQL (T1.4) → O-01 |
| Respaldo sin contraseña por defecto (B6) | ❌ **Sigue igual** → S-01 |
| Regla dura `precio ≤ costo` bloquea la cotización (B2) | ❌ **Sigue igual** en `PlanificadorVenta.kt:87` → §6.1 |
| `12.json` ausente | ❌ **Sigue** → I-01 |
| `profileinstaller` sin baseline profile | ❌ **Sigue** → D-04 |
| Sin splits ABI para el APK distribuido por GitHub/WhatsApp | ❌ **Sigue** → O-05 |
| Seed ×1000 de `CatalogoBodega` | ✅ Resuelto (con invariantes en test) |
| Instrumentados que no compilaban | ✅ Compilan en CI (pero no se ejecutan) → I-02 |

---

## 2. Vulnerabilidades y debilidades de seguridad

Severidad pensada para el contexto real: negocios pequeños, wifi compartida, respaldos que viajan por WhatsApp y un repositorio **público**.

### S-01 · CRÍTICA — El respaldo «sin contraseña» se abre con un secreto publicado, y es el modo por defecto

- **Evidencia:** `BackupCipher.kt:196-202` guarda `MASCARA`/`SECRETO` (XOR) en el código; `derivarInterna()` (`:158`) reconstruye la clave con PBKDF2 a solo **10 000** iteraciones. `RespaldoLogic.kt:46`: `conContrasena: Boolean = false` — el interruptor «Proteger con contraseña» nace **apagado**.
- **Prueba:** derivé el secreto en este entorno, sin APK ni ingeniería inversa, uniendo `SECRETO[i] xor MASCARA[i % 8]`: resulta una cadena legible de 32 caracteres (`SPVI-BACK-••••.2027-271••••`, truncada aquí a propósito). Con ella, cualquier `.spvi` sin contraseña se descifra con ~20 líneas de Python: ventas, clientes con CI y teléfono, perfil e ID de licencia.
- **Por qué es crítica y no «riesgo aceptado»:** `SECURITY.md` lo documenta como «cualquiera con SPVI puede abrirlo». Ya no: **cualquiera con acceso a este repositorio público** (o al APK, que es lo mismo) puede. El archivo típicamente viaja por WhatsApp/Drive, fuera del control del dueño.
- **Corrección:** (a) invertir el defecto a `true`; (b) texto explícito al desactivarlo («quien consiga este archivo podrá leer tus ventas y los datos de tus clientes sin clave»); (c) a medio plazo, eliminar el modo sin contraseña al **exportar** y mantenerlo solo al **importar** (compatibilidad con copias viejas). Actualizar `SECURITY.md` §3, que hoy describe el riesgo por debajo de lo real.

### S-02 · ALTA — La migración entre teléfonos viaja descifrable por cualquier oyente de la wifi

- **Evidencia:** `CanalMigracion.kt:53,87` exporta con `SIN_CONTRASENA = charArrayOf()`; el saludo con la clave va **en claro** (`:83`, `:113`: `SPVIM1 <clave>` por TCP sin cifrar); la clave del QR son 8 bytes aleatorios (64 bits, `MigrarQr.kt:49`).
- **Impacto:** la confidencialidad no depende de la clave del QR sino del secreto de S-01: un oyente **pasivo** en la misma wifi (o en la zona wifi del propio emisor, donde «la red» es el atacante potencial) captura el flujo TCP y descifra el negocio entero a posteriori. El diseño cree que «el archivo viaja cifrado» (comentario de la clase), pero viaja cifrado con una clave pública.
- **Corrección:** derivar una clave de transporte de la clave del QR (p. ej. HKDF(clave QR) → AES-GCM sobre el flujo, o exportar el respaldo con la clave del QR como contraseña). Es un cambio local al canal, sin tocar el formato `.spvi`.

### S-03 · ALTA — `ServidorSync` (Android): denegación de servicio pre-autenticación y superficie LAN sin límites

- **Evidencia:** en `ServidorSync.kt:239`, `sesiones.put(e.id, s)?.cerrar()` cierra la sesión legítima **al crear la nueva, antes de recibir el primer mensaje cifrado** (`canal.recibir()` llega después, en el bucle). El saludo `Hola` es JSON en claro (`Protocolo.kt:59`): expone `negocio`, `empleado` (ids secuenciales) y `device`. El nombre NSD publicado es `SPVI-` + los 12 primeros caracteres del ID de negocio (`RedLocal.kt:104`). `abrirSocket()` (`:142-150`) escucha en **todas las interfaces** y el bucle `accept()` lanza una corrutina por conexión **sin semáforo, sin límite de intentos y sin espera creciente** (el `soTimeout` de 90 s retiene cada intento).
- **Impacto:** quien observe una sola sincronización legítima (wifi compartida) aprende `negocio`+`empleado` y puede, en bucle, (1) tirar la sesión de esa secundaria indefinidamente — seguirá vendiendo, pero dejará de sincronizar — y (2) abrir conexiones a medio autenticar sin coste. No puede leer datos (el canal va cifrado con claves por empleado), ni provocar el borrado `QUITADA` (autenticado con MAC ✅).
- **Corrección (sin cambiar el protocolo v1, como exige el plan):** mover `sesiones.put(...)` a **después** del primer `canal.recibir()` válido — exactamente lo que ya hace el servidor de escritorio (`desktop/spvi_web/sync/server.py:74-78`: `register()` tras el primer `receive()`) —; añadir un semáforo de conexiones concurrentes (el de escritorio usa 24) y retardo creciente tras saludos que no culminan. Esto era la tarea T2.2/T2.3 del `PLAN_CORRECCIONES.md`: sigue pendiente.

### S-04 · MEDIA-ALTA — Envenenamiento del `deviceId` sin autenticar (Android y escritorio)

- **Evidencia:** `ServidorSync.kt:233`: `if (h.device != null) anotarDevice(e.id, h.device)` se ejecuta al leer el saludo, **antes** de que el canal cifrado demuestre posesión de la clave del empleado. `anotarDevice` persiste el valor si tiene formato válido. En escritorio, lo mismo: `sync/server.py` rama `hola` llama `business.set_device(...)` (`business.py:438`) antes de cualquier prueba criptográfica (la sesión se autentica después).
- **Impacto:** con `negocio`+`empleado` (ver S-03) un atacante puede sobrescribir el `deviceId` vinculado a un empleado. La principal firmará el sub-token de licencia (mitigación 7) para **otro** dispositivo: la secundaria legítima dejará de validar su permiso, o un clon con ese `deviceId` pasará a ser el destinatario. Es sabotaje de disponibilidad de la licencia y un ladrillo hacia suplantación.
- **Corrección:** anotar el `deviceId` solo tras el primer mensaje cifrado válido de la sesión (o solo en `Vincular`, donde ya hay MAC con el token del QR), y no sobrescribir un `deviceId` ya conocido sin esa prueba.

### S-05 · MEDIA — Escritorio: acceso web sin clave por defecto, sin límite de intentos

- **Evidencia:** `app.py:47-48`: si no hay `password_hash`, `protect()` **auto-concede** la sesión (`grant()`) a cualquier petición. El servidor escucha solo en `127.0.0.1` (`launcher.py`: `Server(('127.0.0.1', port))`) y hay CSRF + cookies `SameSite=Strict` + CSP estricta ✅, lo que bloquea ataques desde páginas web maliciosas. Pero cualquier **proceso local, otro usuario del PC o una extensión** tiene acceso total al punto de venta sin credencial. `/login` (`app.py`) no tiene retardo ni bloqueo tras fallos; la clave mínima es de 8 caracteres (`configuration.py: MIN_PASSWORD = 8`), con hash Werkzeug (scrypt) ✅.
- **Corrección:** asistente de primera ejecución que pida crear la clave (permitiendo «ahora no» con aviso), retardo exponencial en `/login`, y subir el mínimo a 12 (el propio módulo de respaldo ya exige 12–256).

### S-06 · MEDIA — Escritorio: base de datos del negocio sin cifrar en reposo

- **Evidencia:** `app.py:35` abre `spvi.sqlite3` SQLite estándar (sin SQLCipher); `business.py` guarda el negocio como JSON en la tabla `objects`, fotos como BLOB y clientes con CI/teléfono dentro del payload — todo en claro. `SecretBox` (DPAPI en Windows) solo protege el documento de licencia y los secretos de sincronización de empleados. `config.json` contiene el `secret` de la instalación en texto plano (en Windows lo mitigan las ACL del perfil de usuario; el modo `0o600` solo aplica a POSIX).
- **Contraste:** Android cifra BD (SQLCipher), preferencias (AES-GCM/Keystore) y licencia. En el PC, robar la carpeta `datos/` = robar el negocio entero, aunque la licencia siga protegida.
- **Corrección:** como mínimo, documentarlo en el README de escritorio como decisión consciente; mejor, cifrar los payloads sensibles (CI/teléfono) con `SecretBox`, que ya existe.

### S-07 · MEDIA — iOS: clave privada de licencia fuera del Keychain y puertas de seguridad simuladas

- **Evidencia:** `flutter_app/lib/src/licensing/almacen.dart:20-29` guarda **todo el documento de licencia — incluida la clave privada P-256 del equipo — en `shared_preferences`** (plist en el sandbox; el comentario lo reconoce: no se quiso añadir el plugin de Keychain). `migrar_screen.dart:39`: `bool get _claveDefinida => true;` — la «puerta» de acceso con clave de la migración **está cableada a verdadero**. El PIN de Ajustes se guarda como `clavePin` en claro en el modelo en memoria (`models.dart:307`, `spvi_providers.dart:109`) y no se verifica en ningún arranque: no hay pantalla de bloqueo por PIN/biometría en Flutter pese a `NSFaceIDUsageDescription`.
- **Impacto:** una copia de seguridad de iOS no cifrada o un equipo con jailbreak exponen la identidad criptográfica del dispositivo; y cualquier persona con el teléfono en la mano entra a Migrar sin clave, contra lo que promete la pantalla.
- **Corrección:** `flutter_secure_storage` (Keychain) para el documento de licencia; implementar el bloqueo o **retirar** el interruptor de Ajustes y el texto de Face ID hasta tenerlo.

### S-08 · MEDIA — Actualizaciones y revocaciones: bien firmadas, pero el canal no existe todavía

- **Verificado:** la API de GitHub devuelve **0 releases y 0 tags**. `GITHUB_REPO=rmdvcd/SPVI` alimenta `releases/latest` y `revocadas.json` (`ActualizacionesRepositoryImpl.kt`): hoy esas consultas devuelven 404 y el código **falla abierto** (sin actualización, sin revocación) — comportamiento seguro para vender, pero el mecanismo de seguridad remoto nunca ha operado.
- **Lo que está bien:** sin huella SHA-256 no se ofrece instalar; el APK descargado se verifica por hash y `InstaladorApk.comprobar()` exige mismo paquete, `versionCode` mayor y **mismo certificado de firma** antes de abrir el instalador del sistema ✅. La lista de revocadas solo se acepta con firma ECDSA de GL válida ✅.
- **Lo que falta:** no hay *certificate pinning* en `Red.kt` (0 `CertificatePinner`): un MITM con una CA comprometida podría servir una release antigua (rollback de la *oferta*; la instalación seguiría bloqueada por firma/`versionCode` mayor, y la revocación por firma ECDSA, así que el impacto real es bajo-medio: denegación y congelamiento de revocaciones). `USER_AGENT = "SPVI/0.27 (Android)"` (`Red.kt:10`) quedó desactualizado dos versiones — cosmético, pero delata deriva.
- **Corrección:** publicar por fin `v0.30.x` firmada + `revocadas.json` vacío (desbloquea todo el flujo y obliga a probar `spviRelease`), y valorar pinning del `digest` de la release — la firma Android ya es el ancla fuerte; el pinning es defensa en profundidad, no urgente.

### S-09 · BAJA — Antirreinicio de la prueba: débil por diseño (declarado)

`CifradoPrueba` (`RegistroPrueba.kt:43`) deriva su clave de `ANDROID_ID` (identificador no secreto, estable por firma+usuario) con una sal ofuscada en el código y 10 000 iteraciones. Quien lea este repositorio puede descifrar y **reescribir** las copias `sys_*.png/.bin` para reiniciar su prueba. El propio código lo declara «protección débil a propósito». Se acepta si el modelo de negocio lo tolera; no debe presentarse como barrera. La licencia real de GL no depende de esto ✅.

### S-10 · BAJA — Cadena de suministro y claves de desarrollo

- La **clave maestra** `GLMK-PERM-MAXS-9999` está en el código público, pero su aceptación está cerrada por defecto y atada a build de depuración en las tres apps (`ModoDesarrollo.esDebug = BuildConfig.DEBUG`, fail-closed ✅; en escritorio, variable `SPVI_DESARROLLO=1` ✅; en Flutter, `kDebugMode` ✅). El riesgo residual es operativo: **nunca distribuir APKs debug** (llevan además `applicationIdSuffix = ".debug"`, catálogo del design system y generador seed activos).
- Acciones de GitHub referenciadas por etiqueta mutable (`actions/checkout@v4`, sin *pin* por SHA); `gradle-wrapper.properties` sin `distributionSha256Sum`; sin Dependabot/Renovate; Codemagic usa `flutter: stable` y `xcode: latest` flotantes y conserva el correo de ejemplo `user@example.com`. Endurecer: pin por SHA, suma del wrapper, Dependabot mensual para Gradle/PyPI/pub.

### Lo que está bien (verificado, no tocar)

- **SQL:** cero concatenación con datos de usuario. Room usa parámetros en todas las `@Query`; las migraciones son SQL estático. En escritorio, las únicas concatenaciones SQL usan claves de diccionarios constantes (`backups.py:71`, `business.py:744` con columna de una tupla fija) y valores siempre parametrizados ✅.
- **Cripto de licencias y sync:** ECDSA/ECDH P-256 vía JCA/`cryptography`, HKDF, AES-256-GCM con nº de secuencia en el AAD, token de vinculación de un solo uso con MAC, rechazo `QUITADA` autenticado, comparaciones en tiempo constante (`MessageDigest.isEqual`), `SecureRandom` en todo lo sensible (el único `kotlin.random.Random` es el seed determinista de datos de prueba ✅). Sin `TrustManager` permisivos, sin `http://`, sin WebView, `usesCleartextTraffic=false` + interceptor solo-HTTPS ✅.
- **Android en reposo:** SQLCipher con passphrase envuelta por Keystore (StrongBox→TEE→software, degradación controlada), DataStore cifrado valor a valor con AAD = nombre de clave, `allowBackup=false`, telemetría de ML Kit eliminada del manifiesto, `FLAG_SECURE` en pantallas sensibles, FileProvider no exportado ✅.
- **Web de escritorio:** CSRF por token con `compare_digest`, CSP `default-src 'self'`, `X-Frame-Options: DENY`, cookies HttpOnly/SameSite, límite de cuerpo de 16 KB en POST operativos, validación DTO exhaustiva antes de tocar la BD y respaldo con PBKDF2 **310 000** iteraciones con contraseña obligatoria de 12–256 ✅. El JS no usa `innerHTML` en ningún archivo ✅.
- **Sin secretos en el repositorio:** ningún `*.jks`, `keystore.properties`, `.env` ni `.pem` rastreado ✅.

---

## 3. Código muerto y lastre

| ID | Hallazgo | Evidencia | Acción |
|---|---|---|---|
| D-01 | `backups.backup()` y `backups.restore()` (legacy v1) **nunca se llaman**; la app usa `Business.export_backup/import_backup` (`seal/unseal/snapshot/validate/insert` sí viven) | `backups.py:122,129`; `app.py:8` importa `backup, restore` sin usarlos | Borrar ambas funciones y el import; conservar `seal/unseal/validate` (ruta de migración v1→v2) |
| D-02 | `trust.comprobar_fijadas()` de escritorio **nunca se invoca** (ni en arranque ni en tests): las claves GL de escritorio no se autocomprueban contra su huella, a diferencia de Kotlin (`LicenseTrust.validated()` en cada uso) y Flutter (test) | `desktop/spvi_web/licensing/trust.py:44`; 0 llamadas en todo `desktop/` | Llamarla al construir `LicenseClient` o eliminarla y fiarlo a un test |
| D-03 | **42 imports sin uso** en Kotlin `src/main` (medidos excluyendo delegados `getValue/setValue`): p. ej. `AsyncImage`, `RoundedCornerShape`, `SpviRadius`, `clip` en `ProductoFormScreen.kt` y `ServicioFormScreen.kt` (la foto pasó a `Miniatura`/`SelectorFoto`), `LocalDate` en tres ViewModels, `ActivationResult` en `LicenciaViewModel.kt`, `EstadoPermiso/Explicacion/explicacionCamara` en `LicenciaForm.kt` | Conteo automático sobre los 282 archivos | Pasar el *organize imports* de Android Studio; son warnings, pero ensucian cada revisión |
| D-04 | `androidx.profileinstaller` declarado y en el APK **sin ningún baseline profile** en el árbol (no hay `*.prof`/`*.profm` ni módulo `:baselineprofile`) | `app/build.gradle.kts:140`; búsqueda de perfiles = 0 | Generar el perfil con macrobenchmark o retirar la dependencia: hoy es peso y una falsa sensación de optimización |
| D-05 | Ruta `Route.Catalogo` y `CatalogoScreen` (design system) registradas en el grafo de navegación también en release; solo se enlazan desde Ajustes con `BuildConfig.DEBUG` (`AjustesScreen.kt:126`). Lo mismo, el diálogo/generador **seed** (`domain/seed/` completo: `CatalogoBodega`, `GeneradorSeed`, `OrquestadorSeed`) viaja en el APK de release aunque su única puerta es debug | `MainScaffold.kt` (`composable<Route.Catalogo>`), `AjustesScreen.kt:126-127` | Aceptable si se quiere el catálogo en release interna; si no, mover ambos a `src/debug` |
| D-06 | `.playwright-mcp/` (30 archivos de una sesión de navegador, con PDF/XLSX de inventario generados al probarla) versionado por accidente; `app/capturas/` = 286 PNG (22 MB) en git | `git ls-files` | Añadir `.playwright-mcp/` a `.gitignore` y `git rm --cached`; las capturas son la referencia Roborazzi: se quedan, pero valorar Git LFS |
| D-07 | Enlaces rotos verificados: `README.md` y `RELEASE.md` enlazan `PRUEBAS_DISPOSITIVO.md`, que no existe. `Red.kt:10` aún anuncia `SPVI/0.27` | Escaneo de enlaces de los 36 `.md` | Crear el documento o quitar el enlace; generar el UA desde `BuildConfig.VERSION_NAME` |
| D-08 | `docs/HISTORIAL_DESARROLLO.md` (200 KB) y la propia acumulación documental (36 `.md`, con cifras que se contradicen entre versiones: los tests JVM son «825» en `AGENTS.md` y 903 medidos hoy) | Conteo §1 | Una sola fuente para cifras repetidas (versión, nº de tests, esquema) o un chequeo `docs.sh` en CI, como ya proponía el plan (T3.6) |

No se encontró código muerto estructural en Android: el análisis de referencias sobre las 282 clases/objetos de `src/main` no arroja módulos huérfanos (los aparentes «sin referencias» son objetos `*Tags` de test, módulos Hilt descubiertos por KSP y tipos usados dentro de su propio archivo). Tampoco hay `TODO()`/`NotImplementedError` ni `TODO/FIXME` reales en producción (las coincidencias son el enum `PeriodoRegistro.TODO` y la palabra «TODO» en comentarios en español).

---

## 4. Optimizaciones posibles

### Android

| ID | Optimización | Evidencia / impacto |
|---|---|---|
| O-01 | **Agregar en SQL en vez de en memoria (la importante).** `ObtenerGraficosPeriodo`/`ObtenerResumenGeneral` cargan `ventas.entre(...)`/`deTurno(...)` como `List<VentaCompleta>` (venta + detalles) y agrupan con `Estadisticas.serie/top3` en memoria. El F1 lo sacó del hilo principal (`withContext(io)`, `flowOn`) ✅, pero con el historial de 18 meses del propio seed el coste CPU/memoria crece linealmente con **todo** el histórico en cada visita a Inicio | Añadir `@Query` con `GROUP BY` por cubo/rango en `VentaDaos` conservando `Estadisticas` como oráculo de tests que comparen SQL vs memoria (era T1.4). 🧪 Sin medición en dispositivo: es la primera candidata a perfilar |
| O-02 | **Listas sin paginar ni `LIMIT`.** `VentaDao.filtrar` devuelve `Flow<List<VentaCompleta>>` completo; `observarTodos()` de productos/insumos/servicios carga catálogos enteros. Correcto para un catálogo pequeño; Registros con años de ventas cargará miles de filas con sus detalles en cada cambio de filtro | Proyecciones ligeras para listas (sin detalles hasta abrir la ficha) y/o Paging 3 en Registros |
| O-03 | **Búsqueda por nombre con `LIKE '%…%'` sin índice posible.** `detalle_venta` indexa `ventaId` y `productoId`, no `nombre` (`Entities.kt:167`); el filtro de Registros hace subconsulta con `LIKE '%texto%'` → escaneo completo. Igual en `transaccion` | Índice compuesto no resuelve el comodín inicial: alternativa, índice FTS o limitar la búsqueda a prefijo. Impacto real solo con históricos grandes 🧪 |
| O-04 | `Tarjetas.kt:131`: `BitmapFactory.decodeFile()` **sin `inSampleSize`** al renderizar tarjetas para compartir. Las fotos importadas ya se reducen a ≤1024 px ✅, por lo que el riesgo es acotado, pero una URI de foto anterior/externa decodifica a resolución completa dentro de un `Bitmap` de tarjeta ya grande | Decodificar con cota (`inJustDecodeBounds` + muestra), como ya hace `FotoRepositoryImpl.kt:124` |
| O-05 | **APK universal para distribución directa.** Sin `splits.abi`, el APK de GitHub/WhatsApp lleva los `.so` de SQLCipher+ML Kit de 4 ABIs; el AAB los separa, pero el canal de distribución no es Play | Publicar también APKs por ABI (o solo `arm64-v8a` + `armeabi-v7a`, que cubren el parque cubano) junto al universal |
| O-06 | Sin mediciones: 0 benchmarks, 0 baseline profiles (D-04), sin macrobenchmark de arranque. Todo lo anterior es prioridad *a ciegas* hasta medir en un gama baja | Un `BaselineProfile` + una pasada de Perfetto con el seed de 18 meses antes/después de O-01 |
| — | Ya bien resuelto (no tocar): `debounce(250)` + `flatMapLatest` + `distinctUntilChanged` en buscadores, `shareIn/stateIn(WhileSubscribed(5_000))`, `collectAsStateWithLifecycle` en las 77 recogidas de estado, exportaciones con `withContext(io)`, topes anti-bomba (trama 16 MB, respaldo 128 MB, gunzip acotado) | |

### Escritorio

| ID | Optimización | Evidencia |
|---|---|---|
| O-07 | `business.objects(db, kind)` **carga y parsea en JSON la colección entera** en cada `catalog()`, `records()`, `dashboard()` y en cada sincronización (`instantanea`). Con años de ventas, cada petición web re-lee el histórico completo | Paginación en SQL para `ledger` (ya pagina en Python tras cargar) y proyecciones por período; caché de `instantanea` por hash |
| O-08 | Doble modelo persistente: `store.py` mantiene el esquema legacy (`items/shifts/sales/movements`) como archivo base + proyección `capture_local`, mientras el negocio real vive en `objects`. Conviven dos verdades y código como D-01 | Plan de retirada del esquema legacy tras la migración v1 (ya existe la conversión de respaldos) |
| O-09 | Sin hashes en `requirements.txt` (`pip install --require-hashes`) pese a estar todo pineado a versión exacta ✅; `tzdata==2026.5` y `cryptography==46.0.2` exigen revisión de compatibilidad en Windows | Añadir hashes al generar el portable |

### Proceso

| ID | Optimización |
|---|---|
| O-10 | CI sin caché de cobertura (no hay Kover/JaCoCo: nadie sabe la cobertura real de los 903 tests), sin `ktlint/detekt`, y con `--no-configuration-cache` forzado por un error de serialización histórico: cada corrida tarda ~10 min. Subir la configuración-caché tras corregir la causa y añadir un job de **emulador** semanal para instrumentados (I-02) ahorraría más que cualquier micro-optimización de código |

---

## 5. Módulos incompletos

| ID | Módulo | Qué falta exactamente |
|---|---|---|
| I-01 | **Esquema Room v12** | `SpviDatabase.VERSION = 12` y `MIGRACION_11_12` existen (`SpviDatabase.kt:73,243`), pero `data/schemas/.../` solo llega a `11.json`. Sin el JSON que genera Room al compilar, `EsquemaTest.laBdCreadaDesdeElEsquemaSeAbreConLasEntidadesActuales` no puede crear la BD v12 y la cadena de migraciones queda sin su ancla. **No debe escribirse a mano** (el `identityHash` lo calcula Room): compilar una vez en la PC del dueño y commitear el archivo |
| I-02 | **Verificación en dispositivo** | 145 tests instrumentados y 146 capturas Roborazzi **compilan en CI pero no se ejecutan en ninguna parte**. Además, las 3 capturas nuevas de Migrar (`09t_migrar_emisor_receptor`, `09u_migrar_emisor_qr`, `09v_migrar_sin_clave`) están **declaradas sin PNG de referencia**, y los cambios 0.31 de Inicio/Inventario/Registros/Licencia/Ajustes invalidan las referencias actuales: hay que regrabar (`:app:recordRoborazziDebug`) y revisar el diff visual completo |
| I-03 | **App iOS (Flutter) — el módulo incompleto mayor** | El negocio vive en `StateNotifier`s **en memoria con datos de demostración precargados** (`spvi_providers.dart:173-345`: «Café Expreso Doble», clientes «Carlos Martínez» CI `88051212345`, «Ana Rodríguez»…): al cerrar la app, ventas, turnos e inventario **desaparecen**; al abrirla, aparecen datos que no son del negocio. No hay BD (solo `shared_preferences` para la licencia), ni sincronización LAN, ni cámara/QR, ni respaldo, ni exportaciones — pese a que `Info.plist` declara `NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription`, `NSFaceIDUsageDescription`, `NSLocalNetworkUsageDescription` y el servicio Bonjour `_spvi-sync._tcp`, ninguno implementado (0 referencias a socket/cámara en `lib/`, sin dependencias para ello). `MigrarScreen` lo admite con honestidad: no hay socket ni QR, genera un código con `host: '127.0.0.1'` decorativo y su puerta de clave es `=> true` (S-07). **Lo único completo y probado es el cliente de licencias GL.** Estado correcto: prototipo de licencias + maqueta navegable; no debe distribuirse como POS |
| I-04 | **Congelación de versión** | El árbol declara 0.30.0/51 en Android y Flutter, pero contiene la BD v12, los sub-tokens y la migración QR etiquetados «0.31.0» en comentarios y en `Pendiente.md` («Android 0.31.0, diez cambios… sin poder compilar en este entorno» — hoy el CI ya los verifica en verde ✅, pero el `versionCode` no se movió). Decidir: publicar como 0.31.0 (subir `versionCode` a 52 en ambos `build.gradle.kts`/`pubspec.yaml`) o documentar que 0.30.0 nunca salió. `docs/VERIFICACION.md` debería zanjarlo |
| I-05 | **Publicación y canal de actualización** | 0 releases / 0 tags (S-08): sin APK firmado publicado ni `revocadas.json`, la consulta semanal, el bloqueo por versión y la revocación remota son código inerte. Es el desbloqueo con más efecto por línea cambiada (ninguna) |
| I-06 | **Lado GL del código de PC** | Según `Pendiente.md`, GL aún no tiene `SpviPc16.kt`, los campos `plataforma/transaccion/recupera` ni `emitirCodigoPc`: la licencia de PC de 16 dígitos del escritorio 0.31.0 **no puede funcionar de punta a punta** hasta publicar eso en el repo de GL. Fuera de este árbol, pero bloquea una función anunciada |
| I-07 | **Escritorio en Windows real** | El workflow de Windows empaqueta el `.exe` y pasa la autoprueba ✅, pero quedan sin verificar en un Windows real (lo dice `desktop/VERIFICACION_PC.md`): icono de notificación, registro `Run` de inicio con Windows, `.exe` sin consola y la licencia de PC contra la app GL real. En Linux todo pasa (129 tests + humo jsdom) |
| I-08 | **Decisiones de producto abiertas** (no son defectos) | Sin multi-moneda (CUP soldado al tipo `Cup`), sin impuestos/descuentos/devoluciones parciales, textos de UI como literales Kotlin (0 `stringResource`, `strings.xml` con 3 cadenas): cualquier i18n o segundo mercado es una reescritura de textos. Están documentados como límites intencionales; solo se listan para que conste el coste si se decide lo contrario |

### 6.1 Defecto funcional (no de seguridad) que sigue abierto

`PlanificadorVenta.kt:87`: `if (detalles.any { it.precioUnitario <= it.costoUnitario }) return Err(...)` — una cotización **ya en el comprobante** se rechaza si cualquier línea queda a precio ≤ costo (p. ej. tras un preajuste de descuento acumulado o una receta que encareció). Es una regla de alta de producto aplicada en el peor momento posible: con el cliente delante. La auditoría de octubre ya lo marcó (B2) y la CI se puso verde ajustando *fixtures*, no la regla. Recomendación sin cambios: convertirlo en **advertencia visible** en la cotización (qué artículo pierde dinero) y mantener el bloqueo duro solo en `Validadores` al dar de alta/editar. Hasta entonces, una subida de coste de un insumo puede parar una caja que vende elaborados.

---

## 6. Plan de corrección priorizado

**Inmediato (antes de la próxima release)**
1. S-01: defecto «con contraseña» = sí + aviso sin ambages (y decidir eliminar el modo al exportar). Actualizar `SECURITY.md`.
2. I-01: generar y commitear `12.json` compilando en la PC del dueño; verificar `EsquemaTest`.
3. I-04/I-05: cerrar la versión (0.31.0, `versionCode` 52) y publicar la primera release firmada con `revocadas.json`.
4. S-03/S-04 (Android): mover el reemplazo de sesión y la anotación de `deviceId` a después del primer mensaje cifrado; semáforo de conexiones. Sin tocar el protocolo v1.
5. S-02: clave de transporte derivada de la clave del QR en la migración.

**Corto plazo (siguiente ciclo)**
6. S-04 en escritorio (`set_device` tras autenticar), S-05 (clave en primera ejecución + retardo en `/login`), D-01/D-02/D-03/D-06/D-07 (limpieza verificable en una tarde).
7. I-02: job de CI (emulador) para instrumentados en cada release y regrabado de capturas 0.31 con revisión del diff.
8. O-01/O-02 con medición previa (O-06) en un gama baja con el seed de 18 meses.
9. §6.1: la regla de precio≤costo pasa a advertencia en cotización, con test a nivel de cotización y de preajuste.

**Medio plazo / decisiones del dueño**
10. I-03: decidir el futuro de iOS — completar persistencia (Drift/SQLite cifrado) + Keychain antes de cualquier distribución, o declararla oficialmente «solo licencias» y retirar permisos prometidos del `Info.plist`. No dejarla en el estado actual si va a verla un cliente.
11. S-06: cifrar al menos CI/teléfonos en el escritorio con el `SecretBox` existente; documentar el modelo de datos en claro mientras tanto.
12. D-04/O-05: baseline profile real o fuera; APKs por ABI para el canal directo. S-10: pin de acciones por SHA y Dependabot.

---

## Apéndice A — Reproducción de S-01 (extracto)

Con solo el código versionado (`BackupCipher.kt:196-202`): `secreto[i] = SECRETO[i] XOR MASCARA[i mod 8]` produce la contraseña interna de 32 caracteres con la que, junto al `salt` e iteraciones que el propio archivo `.spvi` declara en su cabecera, PBKDF2-HMAC-SHA256 deriva la clave AES-256-GCM de cualquier respaldo con indicador «sin contraseña». La cadena se omite aquí; cualquiera puede regenerarla con tres líneas desde el repositorio público, que es exactamente el problema.

## Apéndice B — Comandos ejecutados para esta auditoría

```bash
git log --oneline -5; git ls-files | wc -l                      # 1 040 archivos, HEAD 29f909f
/tmp/spvi-venv/bin/python -m unittest discover -s desktop/tests -t desktop   # 129 tests OK
gh run list --limit 8                                           # CI main/PR en success
gh api repos/rmdvcd/SPVI/releases --jq length                   # 0
gh api repos/rmdvcd/SPVI/tags     --jq length                   # 0
# + escaneos de patrones (TODO/!!/catch/SQL/red/cripto), conteo de @Test/LOC/imports,
#   análisis de referencias para código muerto y escaneo de enlaces Markdown.
```

*Sin cambios de código en esta sesión: solo se añadió este informe y el venv temporal fuera del repositorio (`/tmp/spvi-venv`, no versionado).*

---

## 10. Correcciones aplicadas (misma fecha, rama `arena/48e006de-spvi`)

Tras entregar este informe, el dueño pidió «aplica todas las correcciones posibles». Estado:

### Aplicadas y verificadas aquí (tests de escritorio: 130/130 en verde)

| Hallazgo | Corrección |
|---|---|
| S-01 | «Proteger con contraseña» viene ENCENDIDO por defecto al exportar (`ExportForm`), con aviso reforzado del modo sin contraseña en pantalla, `BackupCipher` y `SECURITY.md`. Tests `RespaldoTest`/`RespaldoPasosTest` actualizados. |
| S-02 | `CanalMigracion` reescrito: apretón de manos HMAC-SHA256 con nonces (la clave del QR nunca viaja), clave de sesión por conexión y marcos AES-256-GCM; clave del QR de 64 → 128 bits; lectura de líneas byte a byte (corrige además un bug latente de búfer que podía comerse el inicio del flujo). Nuevo `CanalMigracionTest` (punta a punta, clave mala, nada en claro por el cable). |
| S-03 | `ServidorSync` (Android) no registra la sesión ni anota el `deviceId` hasta el primer mensaje cifrado. |
| S-04 | Tope de 24 conexiones en el servidor Android; el escritorio anota el `device` del saludo solo tras autenticar (`note_device`), manteniendo `hello()` compatible con sus tests. |
| S-05 | `/login` del escritorio con freno: 5 fallos → 60 s (429), con test nuevo. Clave nueva mínima: 12 caracteres (antes 8). |
| §6.1 | `PlanificadorVenta` ya no bloquea vender al costo o por debajo: cotiza y devuelve `Cotizacion.advertencias`; la app lo muestra en comprobante y QR (`AvisoBajoCosto`). El bloqueo duro queda en `Validadores` al crear/editar productos. Test del dominio actualizado. |
| D-01 | 43 imports Kotlin sin uso eliminados (detección conservadora: nombre sin ninguna aparición en el archivo; los delegados `getValue/setValue` y extensiones `Modifier.*` no se tocaron). |
| D-02 | `trust.comprobar_fijadas()` se ejecuta al arrancar la app de escritorio (`create_app`). En `LicenseClient` no: los tests sustituyen las claves y eso no es un error de fijación (comprobado: hacerlo ahí rompe `ActivacionPcTest`). |
| D-04 | Fuera `androidx-profileinstaller` (dependencia y catálogo): sin baseline profile no aportaba nada. |
| D-05 | `app.py` ya no importa `backup`/`restore` sin usar; en `backups.py` queda documentado por qué se conservan (formato v1 y sus tests). |
| D-06 | `.playwright-mcp/` sale del repositorio y entra en `.gitignore`. |
| D-07 | Creado `PRUEBAS_DISPOSITIVO.md` (el documento que README/RELEASE enlazaban): adb, instrumentados, flujos manuales, checklist y problemas frecuentes. |
| O-04 | `Tarjetas.kt` decodifica las fotos acotadas a su zona (`inSampleSize`), no a resolución completa. |
| O-05 | Splits por ABI en el release (arm64-v8a, armeabi-v7a, x86_64 + universal). |
| S-08 | Acciones de CI fijadas por SHA en `ci.yml`/`desktop.yml` y `.github/dependabot.yml` (acciones, Gradle, pip). |
| S-10 (parcial) | `USER_AGENT` sin versión («SPVI (Android)») en vez del «0.27» congelado. |
| I-02 | Trabajo `instrumentados` en CI: emulador Android 35, semanal y a mano (no en cada push). |
| I-03 (parcial) | Flutter: los datos de demostración ya NO se cargan al abrir la app; solo con `--dart-define=SPVI_DEMO=true` o si un test los siembra. Test actualizado. |
| I-08 (parcial) | Un solo `codemagic.yaml` (raíz; el de `flutter_app/` era una copia idéntica) y fuera el correo de ejemplo `user@example.com` (queda explicado cómo configurar notificaciones). |
| D-08 (parcial) | `AGENTS.md`: cifra de tests corregida (906 JVM sin capturas, conteo 2026-10-14). |

### No aplicadas aquí (y por qué)

| Hallazgo | Motivo |
|---|---|
| I-01 (`12.json`) | Lo genera Room al compilar; este entorno no tiene JDK/Gradle ni acceso a Maven. Saldrá de la primera compilación real y hay que versionarlo entonces. |
| S-07 (Flutter/Keychain) | Requiere dependencia nueva (`flutter_secure_storage`) y SDK de Flutter para verificar; sin eso sería código a ciegas sobre la identidad del dispositivo. |
| I-03 completo (persistencia Flutter, sync, cámara, backup) | Es un módulo de producto entero, no una corrección; queda en `Pendiente.md`. |
| S-06 (BD de escritorio sin cifrar) | Exige migrar el formato de datos de los usuarios actuales; decisión y plan de migración pendientes. |
| S-09 (`CifradoPrueba`/`deviceId`) | Debilidad reconocida por diseño (mitigación 2 de GL); cambiarla invalida registros emitidos. |
| O-01/O-02 (paginación y agregados SQL) | Cambios de DAO con riesgo de regresión sin poder ejecutar los tests de Room aquí; mejor con la suite JVM/instrumentada delante. |
| `distributionSha256Sum` del wrapper | No se puede obtener la suma oficial sin acceso a `services.gradle.org`; un valor inventado rompería todos los builds. |
| Releases firmadas / OTA (S-08 resto) | Requieren la clave de firma del dueño y publicar en GitHub Releases: fuera del alcance de una corrección de código. |
| Capturas 09t–09v de Migrar (I-02 resto) | Se generan con Roborazzi en un equipo con SDK. |

### Verificación

- Escritorio: `python -m unittest discover` → **130 tests OK** (incluye el nuevo de throttling).
- Kotlin/Dart: **sin compilación posible en este entorno** (no hay JDK, Gradle, ni SDK de Flutter). Los cambios
  JVM quedan verificados por el CI de la rama (`spviTests` + compilación de instrumentados); hasta verlo en
  verde, las correcciones de Android deben tratarse como «aplicadas, pendientes de CI».
- Nota de proceso: durante la aplicación se detectaron y repararon dos corrupciones por ediciones en
  paralelo al mismo archivo (`app.py`, `VentaScreen.kt`): colas duplicadas, ya eliminadas y re-verificadas.
