# SPVI — Sistema de Punto de Venta e Inventario

App Android nativa para pequeños negocios: vender en turnos, controlar inventario e insumos, cobrar por transferencia, ver estadísticas y exportar registros. Funciona **sin conexión** y guarda todos los datos **cifrados en el teléfono**. El uso se controla con licencias offline emitidas por la app GL del desarrollador (contrato v1).

| | |
|---|---|
| Versión | **0.30.0** (`versionCode 51`) |
| Paquete | `cu.spvi.app` (debug: `cu.spvi.app.debug`) |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| Base de datos | Room **v10** cifrada con SQLCipher (respaldo `RespaldoDto` v4; archivo `.spvi` v4) |
| Contrato de licencias | GL v1 (`ECIES-P256-AES256GCM-v1`) |
| Permisos | Solo `CAMERA`, `INTERNET` y `POST_NOTIFICATIONS` (aviso «Turno abierto», Android 13+), más `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` y `CHANGE_NETWORK_STATE` (servicio de la app principal con secundarias, 0.19.2), `REQUEST_INSTALL_PACKAGES` / `REQUEST_DELETE_PACKAGES` (actualizaciones y licencia transferida, 0.25.0) y `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` (hasta Android 12) / `WRITE_EXTERNAL_STORAGE` (hasta Android 9) para el registro de la prueba (0.26.0), y `USE_BIOMETRIC` / `USE_FINGERPRINT` (acceso con clave opcional, 0.27.0; permisos normales que añade `androidx.biometric`). La captura SMS opcional requiere habilitar manualmente el Acceso especial a notificaciones; no solicita `READ_SMS`. |

**Documentación**

| Documento | Contenido |
|---|---|
| [SECURITY.md](SECURITY.md) | Gestión de claves, cifrado, modelo de amenazas y buenas prácticas |
| [LICENSE_CLIENT.md](LICENSE_CLIENT.md) | Cómo aplica SPVI el contrato de licencias GL v1 |
| [FORMATOS.md](FORMATOS.md) | Formato del respaldo `.spvi` y de las exportaciones (PDF, Excel, imágenes; registro de la prueba) |
| [UI_UX_IX.md](UI_UX_IX.md) | Design system, decisiones de interfaz, antes/después y pendientes |
| [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) | Referencia de tokens y componentes del módulo `:designsystem` |
| [MANUAL_USUARIO.md](MANUAL_USUARIO.md) | Manual breve para el usuario final, sin tecnicismos |
| [RELEASE.md](RELEASE.md) | Keystore, firma, `bundleRelease`/`assembleRelease`, verificación del APK y prueba de humo |
| [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md) | Pruebas en teléfono por USB: adb, tests instrumentados, pruebas manuales guiadas, checklist y solución de problemas |
| [Contexto.md](Contexto.md) · [Pruebas.md](Pruebas.md) · [Pendiente.md](Pendiente.md) | Resumen del proyecto, orden de pruebas y lo que falta (para OpenCode) |
| [docs/HISTORIAL_DESARROLLO.md](docs/HISTORIAL_DESARROLLO.md) | Qué se hizo en cada fase (Prompts 1–19) y las suposiciones de cada momento |

Los documentos de trabajo que estaban fuera del proyecto (`DECISIONES_LICENCIA_SPVI.md`, `PRUEBA_COMPATIBILIDAD_GL.md`, `ARQUITECTURA_SPVI.md`, planes y auditorías de los Prompts 17 y 18, entregas `ENTREGA_*`) se retiraron en la 0.18.0. Lo vigente está en `LICENSE_CLIENT.md` (contrato GL y decisiones de licencia), `SECURITY.md`, `docs/HISTORIAL_DESARROLLO.md` y el propio código.

## Descripción

### Qué hace

| Área | Funciones |
|---|---|
| **Inicio** | Banner de licencia, **Nueva venta** (izquierda) y turno con su interruptor abierto/cerrado (derecha), alertas de inventario (stock bajo/crítico, insumo bajo/crítico, próximo a caducar), accesos a Pago electrónico (teléfono y tarjeta/cuenta resaltados) y Precios, selector de período y, debajo, acordeones: 4 gráficos (Ventas, Inventario, Métodos de pago, Ganancia neta) y Top 3 (más vendido, lento movimiento, rentabilidad). Los acordeones empiezan cerrados y se cierran de nuevo al cambiar de ventana |
| **Venta** | Solo con turno abierto. Selección desde el Inventario, carrito con cantidades, **Efectivo** (comprobante) o **Transferencia** (QR de Transfermóvil con tarjeta y móvil; el total aparece debajo porque el QR oficial no lo admite). El nº de transacción se puede capturar opcionalmente desde una notificación autorizada o pegar/compartir desde el SMS de PAGOxMOVIL. Un Elaborado se vende mientras alcancen sus insumos, que se descuentan en la misma transacción |
| **Inventario** | Lista con buscador, filtros, selección múltiple, ficha, alta manual, exportación (PDF, Excel, Imagen, Tarjetas) |
| **QR de vinculación** | CameraX + ML Kit (modelo empaquetado). Solo lee el QR para vincular la principal con la secundaria |
| **Servicios** | (0.18.0, sustituye a Elaboración.) Nombre, Tipo, Importe y, opcionales, foto y descripción. Pueden gastar insumos. Se venden aparte de los productos, tienen su pestaña en Registros y sus indicadores en Inicio. Los **insumos** son ahora la categoría «Insumos» del Inventario (precio de venta opcional, en unidades enteras). Los Elaborados muestran «Alcanza para N» |
| **Registros** | Ventas, Transferencias, Movimientos, **Clientes** (clientes fijos, 0.27.0) y Turnos, con buscador y filtros de fecha e importe. Exportar a PDF/Excel (0.26.0: ya no se comparte como texto) |
| **Ajustes** | Completar configuración, Perfil, Licencia, Pago electrónico, Precios (preajustes), Avisos de inventario, Permisos, Respaldo, Migrar a otro teléfono, Ayuda, Soporte |

### Qué no hace

- No tiene servidor, cuentas de usuario, sincronización en la nube, Firebase, analítica, telemetría ni anuncios.
- No pide `READ_SMS` ni consulta el buzón. El SMS se puede pegar o compartir; también hay captura opcional de notificaciones nuevas mientras una venta por transferencia espera el pago (requiere habilitar manualmente el Acceso especial a notificaciones y solo usa el número e importe reconocidos, no guarda el texto completo).
- No usa el almacenamiento compartido para tus datos: guardar y abrir archivos pasa por el selector del sistema. La única excepción (0.26.0) es el registro cifrado de la prueba en Imágenes/SPVI y Download/Documents.
- No tiene usuarios ni contraseñas propias. Desde la 0.27.0 hay un **acceso con clave opcional** que usa la huella o el PIN/patrón **del teléfono** (SPVI no guarda ninguna clave).
- No modifica el launcher, la barra de estado, la de navegación ni el notch (edge-to-edge estándar).

## Requisitos

- **Android Studio** Ladybug (2024.2) o superior, con **JDK 17** (el JBR incluido sirve).
- **Android SDK 35**.
- **Gradle 8.11.1**: lo descarga el wrapper (`gradlew`).
- Teléfono o emulador con **Android 8.0 (API 26)** o superior. La cámara es opcional (`android.hardware.camera.any`, `required=false`).

Versiones principales (`gradle/libs.versions.toml`): Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01, Hilt 2.52, Room 2.6.1, SQLCipher 4.6.1, DataStore 1.1.1, Navigation 2.8.5, CameraX 1.4.1, ML Kit barcode 17.3.0, OkHttp 4.12.0, Coil 3.0.4, fastexcel 0.18.4, ZXing core 3.5.3, kotlinx.serialization 1.7.3, coroutines 1.9.0.

## Configuración

1. **Claves públicas de GL.** Ya están fijadas en `licencia/src/main/kotlin/cu/spvi/licencia/LicenseTrust.kt` (par vigente de GL del 05/10/2026: clave ECDH `sha256:2a3f:9fce:…:666d` y clave de firma `sha256:58d4:3aac:…:8c4e`; se conserva la firma anterior `sha256:8a91:bcfb:…:6888` para las licencias ya emitidas). `LicenseTrustTest` falla si alguna no se puede leer o no coincide con su huella. Si GL cambia de par de claves (borrado de datos, reinstalación, otro teléfono), hay que publicar un build nuevo con la clave añadida a `SIGN_KEYS` (ver [LICENSE_CLIENT.md](LICENSE_CLIENT.md)).
2. **Firma de release.** El repositorio no contiene ningún keystore ni contraseña. `app/build.gradle.kts` los lee de `keystore.properties`, que git ignora, o de las variables `SPVI_KEYSTORE`, `SPVI_KEYSTORE_PASSWORD`, `SPVI_KEY_ALIAS` y `SPVI_KEY_PASSWORD`. Los pasos completos están en [RELEASE.md](RELEASE.md). **Firma siempre con la misma clave**, porque `ANDROID_ID` (y con él el `deviceId` de la licencia) depende de la firma del APK.
3. **Contacto del desarrollador.** Teléfono `+5351815604` en una sola constante (`core/.../contact/DeveloperContact.kt`), que usan Licencia, Migrar y Soporte.
4. **Esquema Room.** El primer build genera `data/schemas/cu.spvi.data.db.SpviDatabase/3.json` (`room.schemaLocation`). Versiónalo en git: es la base de las migraciones futuras y lo usa `EsquemaTest`.
5. **Baseline de lint.** Se entrega vacío. La primera vez ejecuta `./gradlew :app:updateLintBaseline` y versiona `app/lint-baseline.xml`; a partir de ahí, cualquier error nuevo de lint rompe `spviCheck`.
6. **Actualizaciones:** el build apunta por defecto a las Releases públicas de [`rmdvcd/SPVI`](https://github.com/rmdvcd/SPVI/releases) (`spviGithubRepo` en `gradle.properties`). Para compilar un fork, se puede sobrescribir con `-PspviGithubRepo=usuario/repositorio`; vacío desactiva las consultas.
7. **Nada más que configurar:** no hay claves de API, `google-services.json` ni variables de entorno.

## Compilación

```bash
./gradlew assembleDebug           # APK debug (cu.spvi.app.debug), incluye Ajustes → Design system
./gradlew assembleRelease         # APK release: R8 activo (ofusca y elimina android.util.Log); firmado si hay keystore
./gradlew bundleRelease           # AAB release (solo para Google Play)
./gradlew spviRelease             # spviCheck + AAB + APK de release (ver RELEASE.md)
./gradlew spviTests               # todos los tests JVM (sin dispositivo y sin red)
./gradlew spviInstrumentedTests   # tests instrumentados (emulador o teléfono API 26+)
./gradlew spviCheck               # antes de entregar: spviTests + lintDebug + assembleDebug + spviPermisos
```

`spviPermisos` lee el manifiesto **fusionado** de debug y de release, y falla si aparece cualquier permiso fuera de la lista autorizada (`CAMERA`, `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `CHANGE_NETWORK_STATE`, desde la 0.25.0 `REQUEST_INSTALL_PACKAGES` y `REQUEST_DELETE_PACKAGES`, desde la 0.26.0 `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` y `WRITE_EXTERNAL_STORAGE`, estos dos últimos con `maxSdkVersion`, y desde la 0.27.0 `USE_BIOMETRIC` y `USE_FINGERPRINT`). `tools/verificacion/verificar.sh` hace la misma comprobación sobre el manifiesto de `:app`. La única excepción es el permiso propio de nivel *signature* `cu.spvi.app….DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, que declara androidx.core: no lo concede el usuario. La CI de GitHub Actions (`.github/workflows/ci.yml`) se ejecuta en cada push a `main` o a una rama `arena/**` y en cada pull request; sus trabajos son los mismos comandos de arriba: `spviTests` + compilación de los instrumentados, `:app:lintDebug`, `:app:assembleRelease` (con `missing_rules.txt` vacío), `spviPermisos` y `tools/verificacion/api_minima.py`.

### Arquitectura

```
:app ──────────► :designsystem   (Compose, tokens, componentes)
  │  └────────► :data ─────────┐   (incluye Keystore: data/security)
  └──────────► :domain ◄───────┘
                 └──► :licencia
                         └──► :core
```

| Módulo | Tipo | Contenido |
|---|---|---|
| `:core` | JVM | `AppResult`/`AppError`, `Cup` (centavos) y `Money.format` («1,450.00 CUP»), `Percent`, `Cantidad` (milésimas), `Clock`, validadores, contacto del desarrollador |
| `:licencia` | JVM | Contrato GL v1, criptografía (ECDH, HKDF, AES-GCM, ECDSA), `LicenseManager`, estados y banner |
| `:domain` | JVM | Modelos, interfaces de repositorio, servicios puros (stock, planificador de venta, recetas, estadísticas, tablas de exportación) y casos de uso |
| `:data` | Android | Room + SQLCipher, DAOs, repositorios, DTO de respaldo, `BackupCipher`, exportadores PDF/XLSX, DataStore cifrado, red del escáner (OkHttp), fotos y, en `security/`, `KeystoreAead`, `AndroidDeviceKey` y `DatabasePassphrase` (antes módulo `:security`, fusionado en P17) |
| `:designsystem` | Android | Tema, tokens, iconos, componentes y gráficos (ver [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)) |
| `:app` | Android | Una Activity, NavHost y las pantallas como paquetes (MVVM + UDF: `StateFlow` de estado y `Channel` de eventos). Los ViewModels usan casos de uso cuando hay lógica, y la interfaz de repositorio de `:domain` cuando solo se lee o se borra (P17) |

- **DI:** Hilt. **Navegación:** Navigation Compose 2.8 con rutas `@Serializable`.
- **Arranque:** splash del sistema → `RootViewModel` → *Bloqueo* (si la licencia no permite el uso) → *Onboarding* (primera vez) → pantalla principal. Bloqueo y Onboarding están fuera del NavHost: ni Atrás ni un enlace pueden saltárselas.
- **Barra inferior:** Inicio, Inventario, Servicios, Registros, Ajustes (solo iconos, resaltado invertido). Se pasa de una sección a otra deslizando el dedo hacia los lados.

### Pruebas

| Qué | Comando |
|---|---|
| Todos los JVM (core, licencia, domain, data, designsystem, app) | `./gradlew spviTests` |
| Un módulo | `./gradlew :domain:test` · `./gradlew :app:testDebugUnitTest` |
| Todos los instrumentados | `./gradlew spviInstrumentedTests` |
| Solo los flujos de integración | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=cu.spvi.app.integracion` |
| Capturas de pantalla claro/oscuro (Robolectric + Roborazzi, sin emulador) | `./gradlew :app:recordRoborazziDebug` → `app/capturas/` · ver [CAPTURAS.md](CAPTURAS.md) |
| **Sin Gradle ni Android SDK** (Linux): compila todo, Room y Hilt por KSP, migración, tests JVM y los tests de Room de `:data` con SQLite por JDBC | `bash tools/verificacion/verificar.sh` (descarga ~1,5 GB la primera vez en `~/.cache/spvi-tc`) |

- Los tests JVM **no pueden salir a internet** (proxy inexistente salvo el bucle local, que usa `MockWebServer`) y se ejecutan con `user.timezone=UTC`.
- Los instrumentados usan Room **en memoria** y fakes en los bordes (cámara, selector de archivos, bases públicas). Los fakes de `:app` viven en `app/src/sharedTest` y los comparten los tests JVM y los instrumentados.
- Informes: `<módulo>/build/reports/tests/` y `<módulo>/build/reports/androidTests/connected/`.
- **Verificación actual: [docs/VERIFICACION.md](docs/VERIFICACION.md)** (única fuente de verdad; este README ya no lleva historial). Corrida del 2026-10-07, todo en verde: 825 tests JVM, lint 0 errores, R8 sin clases ausentes, permisos del manifiesto dentro de la lista autorizada y API mínima 26 sin llamadas prohibidas. El detalle por capa y los casos borde están en [docs/HISTORIAL_DESARROLLO.md](docs/HISTORIAL_DESARROLLO.md#tests-prompt-15).

## Contrato GL aplicado

SPVI es **cliente** del contrato v1 de GL, sin cambiarlo. Detalle completo en [LICENSE_CLIENT.md](LICENSE_CLIENT.md).

| Punto | Aplicación en SPVI |
|---|---|
| Solicitud | Payload v1 completo (`v`, `nombre`, `apellidos`, `ci`, `via`, `telefono`, `deviceId`, `tipo`, `solicitadaEn`, `nonce`, `devicePub`) + `appName: "SPVI"`, cifrado con ECIES-P256-AES256GCM-v1 hacia la clave ECDH de GL y firmado con la clave efímera |
| `deviceId` | `"SPVI:" + ANDROID_ID` |
| `devicePub` | Clave P-256 persistente del teléfono. **Nunca se envía `null`** |
| Envío | Solo texto: WhatsApp (`wa.me/5351815604`) o SMS (`smsto:+5351815604`) con los datos legibles + la solicitud cifrada `SPVIR1:`. Respuesta de GL: licencia corta cifrada `SPVI2:` (216 caracteres) con las características arriba ([docs/LICENCIA_0.23.md](docs/LICENCIA_0.23.md)) |
| Activación | Se pega el mensaje de GL: firma ECDSA de GL, descifrado con la clave del teléfono, `deviceId`, `devicePub`, `estado` y `venceEn` |
| Envelope | Estricto: solo las 8 claves de v1 |
| Claves de GL | Firma y ECDH: solo fijadas en el build |
| Prueba | 7 días |
| Tipos y precios | Mensual 6,000 · Semestral 30,000 · Anual 50,000 · Perpetua 90,000 CUP |
| Reloj | Si retrocede más de 2 h respecto a la última hora vista → bloqueo hasta corregirlo |
| Migrar | La autorización es la licencia emitida al teléfono nuevo; el viejo cede su licencia y se borra |

## Cambios de la 0.30.1 en curso (sin compilar ni probar en teléfono)

- **Pago electrónico:** los logos de BPA, BANDEC y BANMET se muestran con esquinas redondeadas y el mismo tamaño (96 × 48 dp). El logo se elige por los 4 primeros dígitos de la tarjeta; es una estimación (ver [docs/LOGOS_BANCOS.md](docs/LOGOS_BANCOS.md): `9225`/`9235` los comparten varios bancos y `9226` tiene fuentes contradictorias).
- **Inicio:** «Nueva venta» a la izquierda y, a la derecha, el turno con su interruptor de dos estados en un contenedor que mide lo que mide su contenido (con letra muy grande se apilan). En la tarjeta de Pago electrónico, el teléfono y la tarjeta/cuenta van en seminegrita. Los gráficos y listas que siguen al selector de período son acordeones: empiezan cerrados y se vuelven a cerrar al cambiar de ventana.
- **Licencia:** una tarjeta «Precios de la licencia» lista el precio de cada tipo y lo que suma cada app secundaria.
- **Actualizaciones:** antes de abrir el instalador SPVI guarda un respaldo completo (sin contraseña, en la carpeta privada de la app; no se exporta ni cuenta para el recordatorio mensual). Si no se puede guardar, no se actualiza. Al abrir la versión nueva se restaura y se borra; si la instalación se cancela, se borra sin restaurar. Las ventas hechas entre el respaldo y la instalación no entran en él. El texto de la actualización ya no menciona el repositorio ni su alojamiento.

## Novedades de la 0.27.1 (correcciones, [historial](docs/HISTORIAL_DESARROLLO.md))

- La app ya no se cierra en Android 8–13 al abrir Inicio, ni en Android 8–11 al convertir importes y cantidades: usaba APIs de Android 14 y 12.
- Con letra muy grande ya no se parten palabras ni importes: alertas y accesos de Inicio, arqueo de caja, subtítulos de Ajustes y Exportar.
- Textos de Ajustes al día: Respaldo y Permisos del teléfono.

## Novedades de la 0.27.0 ([docs/PROMPT_0.27.0.md](docs/PROMPT_0.27.0.md))

| Función | Resumen |
|---|---|
| **Títulos centrados** | Barra superior, diálogos y hojas con el título centrado arriba y sin logo. En Inicio, «SPVI» con la fuente de marca |
| **Textos completos** | Los textos pasan a varias líneas en lugar de cortarse (también con letra al 200 % en 360 dp); capturas con letra grande de Inicio, ficha, Licencia y arqueo |
| **Top 3 con medallas** | Oro, plata y bronce (`SpviMedalla`), con contraste AA en claro y oscuro |
| **Animaciones** | 150–250 ms y muelle sin rebote (`SpviMotion`); las ventanas emergentes suben desde abajo al centro y bajan al cerrar; `profileinstaller` para fluidez en release |
| **Deslizar** | Solo entre las 5 pantallas principales, nunca en subpantallas, con un diálogo abierto, en selección o escribiendo |
| **Campos** | El texto de ejemplo (placeholder) va centrado |
| **PDF y Excel** | Con su icono en todas las hojas de exportar |
| **Textos llanos** | Sin versiones, nombres técnicos ni explicaciones de la licencia o del registro de la prueba |
| **Principal ≠ secundaria** | Una app principal nunca puede pasar a secundaria; para cambiar el tipo hay que exportar el respaldo y borrar los datos de SPVI |
| **Respaldo sin contraseña** | «Proteger con contraseña» es opcional y viene apagado; el archivo `.spvi` v4 indica si la lleva. Se siguen abriendo los v3 |
| **Acceso con clave** | Opcional (recorrido inicial y Ajustes): huella o PIN/patrón del teléfono al abrir la app y tras 10 minutos o más en segundo plano. No va en el respaldo |
| **Soporte** | Datos del desarrollador con icono |
| **Foto primero** | Nuevo producto y Nuevo servicio empiezan por la foto, con botones Cámara y Galería (selector de fotos del sistema, sin permisos) |
| **Alertas de Inicio** | Centradas y repartidas por filas (4 → 2 + 2, 5 → 3 + 2, 6 → 3 + 3) |
| **Gráfico de inventario** | Los insumos cuentan en su propia medida (sin «u») |
| **Icono** | Logo con fondo transparente |
| **Clientes fijos** | En una venta por transferencia, «Cliente fijo» guarda nombre, carné y teléfono (por carné). Al escribir el nombre se sugieren hasta 3 clientes y tocar uno rellena sus datos. Registros → Clientes muestra sus compras y permite quitarlo. Viajan en el respaldo y desde las secundarias al sincronizar |
| **Claves de GL** | Par nuevo de GL del 05/10/2026 fijado en `LicenseTrust` (se conserva la firma anterior) |

Base de datos v10 (`MIGRACION_9_10`): tabla `cliente_fijo` y `transaccion.clienteFijo`.

## Novedades de la 0.26.0 ([docs/PLAN_0.26.md](docs/PLAN_0.26.md))

| Función | Resumen |
|---|---|
| **Fondo de los empleados** | El encargado asigna en Apps vinculadas el fondo de caja de cada turno de una secundaria («Fondo del próximo turno» → Asignar fondo; puede ser 0). Sin él la secundaria no abre turno: toca «Pedir fondo» y lo recibe al sincronizar, sin poder cambiarlo. Sirve para un solo turno. Inicio de la principal avisa «Luis pide abrir turno» |
| **Apps de empleados antiguas** | Una secundaria 0.25.x sigue abriendo turno con su propio fondo; la principal muestra «Actualiza la app de X» |
| **Actualizaciones obligatorias** | Si hay una versión nueva, «Más tarde» la aplaza (hasta 30 días, con la fecha límite visible). Vencido el plazo, la app se bloquea con «Actualiza SPVI para seguir» y solo deja Actualizar, Exportar respaldo y cerrar el turno abierto. La búsqueda semanal ya no se desactiva (Ajustes → Actualizaciones → Buscar ahora) |
| **Respaldo solo cifrado** | Respaldo guarda solo el archivo `.spvi` con contraseña; se quitó el «Documento PDF» |
| **Sin exportaciones de texto** | Registros, Inventario, Servicios y las fichas exportan PDF y Excel (e Imagen/Tarjetas en Inventario); solo la licencia se envía como texto |
| **«+» en la esquina** | El botón «+» va abajo a la derecha en Inventario, Servicios, Precios y Apps vinculadas |
| **La prueba no se reinicia al reinstalar** | La fecha de inicio de la prueba se guarda cifrada (AES-GCM, clave derivada de ANDROID_ID) en una imagen pequeña en Imágenes/SPVI y en copias en Download y Documents, que quedan al desinstalar. Recién instalada, SPVI pide **una vez** el acceso a fotos para encontrar la de una instalación anterior; gana la fecha más antigua. Una copia ilegible se ignora (no bloquea). La fecha atrasada se detecta además con el reloj monótono del teléfono ([docs/PLAN_ANTIREINSTALACION.md](docs/PLAN_ANTIREINSTALACION.md)) |
| **Seminegrita** | Importes, precios, cantidades, fechas y totales en seminegrita en la app y en el PDF; en Excel en negrita, con columnas al ancho de su contenido y texto largo en varias líneas |

Base de datos v9 (`MIGRACION_8_9`): empleado con `fondoAsignadoCent`, `fondoAsignadoEn`, `aperturaSolicitadaEn` y `versionCode`. Protocolo v1 ampliado con campos opcionales (compatible con 0.25.x).

## Novedades de la 0.25.1

| Función | Resumen |
|---|---|
| **Compartir el turno** | Registros → Turnos → detalle → Compartir: PDF o Excel del turno completo (resumen, arqueo, caja, ventas con estado, movimientos); enviar a otra app o guardar en el teléfono |
| **Modificar venta completa** | Pantalla completa: cantidades, quitar, **añadir artículos** (precio actual) y **cambiar el método de pago** (transferencia con nº obligatorio) |
| **Recuperar con licencia vencida** | El campo «ID de la licencia anterior» aparece también si la licencia instalada venció |
| **Conteo con «Ahora no»** | En la secundaria, el conteo pedido por el encargado se puede posponer; el aviso ofrece «Contar» y nada se cierra sin conteo |
| **Exportaciones corregidas** | Excel válido (formato CUP escapado); PDF en horizontal con más de 5 columnas, anchos por contenido e importes a la derecha; «Servicios» sin «SPVI ·» duplicado; Inventario → Exportar marca «Uso interno (incluye costos)» / «Para clientes (sin costos)»; nombres de hoja con fecha legible |

Con esto desaparecen las desviaciones de la 0.25.0 sobre Modificar, el conteo no descartable, la recuperación solo sin licencia y la falta de PDF/Excel del turno.

## Novedades de la 0.25.0

| Función | Resumen |
|---|---|
| **Arqueo de caja** | Fondo obligatorio al abrir (puede ser 0), entradas/salidas de efectivo con motivo, efectivo contado al cerrar (botón «Cuadra»), esperado y diferencia en la pestaña **Caja** del turno. En la principal y en las secundarias (su conteo viaja a la principal antes de aprobar el cierre) |
| **Anular / modificar ventas** | Solo la principal, solo ventas de turnos abiertos, con motivo. La venta queda ANULADA (fuera de totales y caja; existencias devueltas). Modificar = anular + venta corregida enlazada («Corrige #N») con los precios de entonces |
| **Recordatorio de respaldo** | Aviso en Inicio de la principal tras 30 días sin exportar; abre Respaldo |
| **Actualizaciones** | Consulta semanal a la última Release pública de GitHub (`-PspviGithubRepo`), descarga con SHA-256 e instalación con `PackageInstaller`; la principal reparte el APK a las secundarias por la red local |
| **Recuperar licencia** | El respaldo v4 trae el ID de la licencia; la solicitud `SPVIR1` lleva `recupera`; GL revoca la anterior y publica `revocadas.json` firmado; el teléfono antiguo borra sus datos, se bloquea («Licencia transferida») y pide desinstalarse. Compartir el mensaje `SPVI2:` a SPVI la activa sola |

Desviaciones documentadas: instalación con `PackageInstaller` (sin FileProvider ni configuración de red propia; OkHttp solo HTTPS); Modificar no cambia el método de pago ni añade artículos (para eso, anular y vender de nuevo); el diálogo de conteo de un cierre pedido por la principal no se puede descartar; la tarjeta de actualización solo aparece en Inicio y Ajustes y nunca interrumpe una venta; la descarga no sigue en segundo plano; el campo de recuperación solo aparece sin licencia instalada; el arqueo se ve en la pestaña Caja (no hay PDF/Excel del turno) y el Excel de ventas añade la columna Estado; una venta de servicios anulada llega a la secundaria en su siguiente sincronización. Protocolo de sincronización: sigue en v1 con campos opcionales (las secundarias 0.24 siguen funcionando).

Para GL: [docs/GL_PROMPT_0.25.md](docs/GL_PROMPT_0.25.md) (recuperación automática y lista de revocadas; pruebas con `tools/licencia/probar_gl.py revocadas …` y `vector_0.25.txt`).

## Apps Principal y Secundaria (0.19.0)

Una app **principal** (dueño: base de datos general y licencia) y hasta **5 secundarias** (empleados), vinculadas con un QR cifrado y sincronizadas por la **red local** (wifi del local o zona wifi del teléfono principal), al momento o cuando el empleado decida. Ajustes → Apps vinculadas. Detalle técnico y de seguridad: [docs/VINCULACION.md](docs/VINCULACION.md).

## Esquema de base de datos

Archivo `spvi.db`, Room **versión 10** (v4: servicios; v5: Principal/Secundaria; historial abajo, ver [docs/VINCULACION.md](docs/VINCULACION.md)), cifrado con SQLCipher. Definición en `data/src/main/kotlin/cu/spvi/data/db/entity/Entities.kt`.

**Convenciones:** importes en centavos (`…Cent`, `Long`); cantidades de insumo en milésimas (`…Mil`); instantes en epoch ms UTC; fechas de calendario en `epochDay`; enums como `TEXT` con su nombre; sin `TypeConverters`.

| Tabla | Clave | Columnas principales | Relaciones e índices |
|---|---|---|---|
| `producto` | `id` auto | `categoria`, `nombre`, `descripcion`, `fotoUri`, `fechaCaducidad`, `precioCostoCent`, `precioVentaCent`, `cantidad`, `nivelBajo`, `nivelCritico`, `codigo`, `creadoEn`, `actualizadoEn`, `eliminado` | Índices: `categoria`, `codigo`, (`eliminado`, `creadoEn`), `fechaCaducidad`. Borrado lógico (`eliminado`) |
| `insumo` | `id` auto | `nombre`, `unidad`, `precioCent`, `cantidadMil`, `nivelBajoMil`, `nivelCriticoMil`, `creadoEn`, `actualizadoEn` | Índices: `creadoEn`, `nombre` |
| `receta_linea` | (`productoId`, `insumoId`) | `cantidadMil` | FK producto (CASCADE), FK insumo (**RESTRICT**: un insumo en uso no se borra) |
| `turno` | `id` auto | `abiertoEn`, `cerradoEn`, `numVentas`, `unidades`, `totalCent`, `efectivoCent`, `transferenciaCent`, `costoCent`, `numMovimientos`, `abiertoPor`, `cerradoPor`, `ventasEfectivo`, `ventasTransferencia`, `movimientosProducto`, `movimientosInsumo` | Resumen nulo mientras está abierto |
| `venta` | `id` auto | `turnoId`, `fecha`, `metodoPago`, `totalCent`, `costoCent`, `unidades` | FK turno (RESTRICT). Índices: `fecha`, `turnoId`, `totalCent` |
| `detalle_venta` | `id` auto | `ventaId`, `productoId`, `nombre`, `categoria`, `cantidad`, `precioBaseCent`, `precioUnitarioCent`, `costoUnitarioCent` | FK venta (CASCADE). Nombre y precios copiados al vender |
| `transaccion` | `id` auto | `ventaId`, `fecha`, `importeCent`, `numero`, `clienteNombre`, `clienteCi`, `clienteTelefono`, `tarjetaCobro`, `telefonoCobro`, `clienteFijo` (v10) | FK venta (CASCADE), `ventaId` único. Índices: `fecha`, `numero`, `importeCent` |
| `movimiento` | `id` auto | `fecha`, `tipo`, `entidad` (PRODUCTO/INSUMO), `entidadId`, `nombre`, `delta`, `existencia`, `turnoId`, `ventaId`, `nota` | Índices: `fecha`, (`entidad`, `entidadId`), `turnoId` |
| `perfil` | `id` = 1 | `nombre`, `apellidos`, `ci`, `pagoTarjetaId`, `pagoTelefonoId` | Fila única |
| `tarjeta` | `id` auto | `numero`, `alias` | `numero` único |
| `telefono` | `id` auto | `numero`, `alias` | `numero` único |
| `preajuste` | `id` auto | `nombre`, `puntosBasicos`, `metodoPago`, `importeMinimoCent`, `activo` | — |
| `preajuste_producto` | (`preajusteId`, `productoId`) | — | FK a ambos (CASCADE) |
| `cliente_fijo` (v10) | `id` auto | `nombreApellidos`, `ci`, `telefono`, `creadoEn`, `actualizadoEn` | `ci` único. Las compras se calculan de `transaccion` por carné |

- **Versión 3 consolidada (P17):** sin `tasa_cambio` ni `caducidad_codigo`. Como no había clientes, las bases de desarrollo v1 y v2 se recrean vacías (`fallbackToDestructiveMigrationFrom(1, 2)`) y no hay clases de migración. Cualquier versión futura necesita una `Migration(3, 4)`… explícita: una migración que falte rompe en desarrollo en lugar de borrar datos de usuarios.
- **v5 (0.19.0):** `turno` y `venta` con `uuid` único, `empleadoId` y `sincronizado`; tabla `empleado` (apps secundarias de la principal). `MIGRACION_4_5`.
- **v10 (0.27.0):** tabla `cliente_fijo` y `transaccion.clienteFijo` (`MIGRACION_9_10`, esquema `10.json`). Solo añade.
- **v10 (0.27.0):** tabla `cliente_fijo` (carné único) y `transaccion.clienteFijo` (`MIGRACION_9_10`, esquema `10.json`).
- **v6–v9:** v6/v7 (0.20–0.21) empleados y permisos; **v9 (0.26.0)**: `empleado.fondoAsignadoCent`/`fondoAsignadoEn`/`aperturaSolicitadaEn`/`versionCode` (`MIGRACION_8_9`, esquema `9.json`); **v8 (0.25.0)**: tabla `movimiento_caja`, `turno.fondoCent`/`contadoCent` y totales de caja, `venta.anuladaEn`/`motivoAnulacion`/`anuladaPor`/`corrigeVentaId`. `MIGRACION_7_8`, esquema `8.json`.
- **Stock nunca negativo:** las actualizaciones usan `WHERE cantidad + :delta >= 0`.
- **Atomicidad:** venta, producción, cierre de turno e importación de respaldo van cada uno en una sola transacción (`db.tx { }`).
- **Fuera de la base de datos:** preferencias (`pref.v1`), progreso del asistente (`setup.v1`) y licencia (`lic.*`) van en DataStore con cada valor cifrado; las fotos, en `filesDir/fotos`.

## Manual de uso

El manual para el usuario final, sin tecnicismos, está en [MANUAL_USUARIO.md](MANUAL_USUARIO.md). La app incluye una versión breve en **Ajustes → Ayuda**. Resumen del flujo diario:

1. **Primera vez:** el asistente (Bienvenida → Tus datos → Avisos de inventario → Periodo de prueba). Todo se puede omitir y completar después en **Ajustes → Completar configuración**.
2. **Cargar productos:** Inventario → botón **+** → formulario (con el filtro *Insumos* puesto, **+** abre directamente su formulario). Las recetas se escriben en el formulario del Elaborado.
3. **Configurar el cobro por transferencia:** Pago electrónico (tarjeta de Inicio o Ajustes): tarjeta y teléfono.
4. **Vender:** en Inicio, abrir el turno (interruptor de la tarjeta de turno) → **Nueva venta** → elegir productos → Efectivo o Transferencia → Confirmar.
5. **Cerrar el turno** al terminar: queda su resumen en Registros → Turnos.
6. **Revisar y exportar:** Registros (texto, PDF o Excel) e Inventario (PDF, Excel, imagen, tarjetas, texto).
7. **Copia de seguridad:** Ajustes → Respaldo, con contraseña. El respaldo siempre es completo.
8. **Licencia:** Ajustes → Licencia → elegir tipo → *Enviar por WhatsApp* o *SMS* → pegar la respuesta → *Activar*.

## Desviaciones de SPVI.txt (decididas en P17)

| SPVI.txt | Pedía | Estado en 0.14.0 | Motivo |
|---|---|---|---|
| línea 6 | Transfermóvil **y Enzona** para confirmar el pago y el QR | **Solo Transfermóvil.** Se quitaron el botón «Abrir Enzona» y su `<queries>` | Enzona no publica el formato de su QR: el botón solo abría la app |
| línea 11 | Exportar en PDF el perfil, los preajustes **y las tasas de cambio** | **Sin tasas** (tabla, DTO y sección del PDF eliminadas) | Nunca tuvieron pantalla de edición y ninguna operación las usaba |
| línea 87 | Escáner: buscar la fecha de caducidad en Room por código y guardarla | **Sin escáner ni campo código.** La fecha va en cada producto | Se eliminó el subsistema de código de barras; decisión del propietario |
| línea 132 | Respaldo de «Configuración» o «Todo» | **Solo «Todo»** | Un único tipo evita importar a medias y simplifica el formato (v3) |

## Advertencias

- **La contraseña del respaldo no se puede recuperar.** Sin ella el archivo `.spvi` no se abre.
- **Importar reemplaza TODOS los datos:** inventario, insumos, turnos, ventas y configuración. Los respaldos de versiones anteriores a la 0.13.0 (formato v1/v2) ya no se abren.
- **Las fotos de los productos no van en el respaldo.** El respaldo guarda la ruta de la foto, pero no la imagen; en otro teléfono los productos aparecen sin foto.
- **La licencia no va en el respaldo.** Cada teléfono necesita la suya (`deviceId` distinto). Desde la 0.25.0 el respaldo lleva su ID para **recuperarla** gratis en otro teléfono (MANUAL §9).
- **Borrar los datos de la app o reinstalarla** destruye la base de datos (su clave vive en el Keystore del teléfono) y la licencia instalada. Haz un respaldo antes. El `deviceId` suele mantenerse si el APK está firmado con la misma clave, así que el desarrollador puede volver a emitir la licencia.
- **El PDF de configuración y las exportaciones de Registros no llevan contraseña.** Las de Transferencias incluyen nombre, carné y teléfono de clientes.
- **Licencia offline:** la única revocación es la lista de GitHub (0.25.0), que el teléfono antiguo solo aplica si se conecta a internet; un APK modificado puede saltarse la comprobación. **Reinstalar** SPVI reinicia los 7 días de prueba (aceptado y documentado). Ver [SECURITY.md](SECURITY.md#modelo-de-amenazas).
- **QR de Transfermóvil sin importe:** el formato oficial no lo admite; el cliente escribe el total que SPVI muestra bajo el QR.
- **SMS de licencia:** la solicitud ocupa unos 780 caracteres (varios SMS) y la licencia, unos 340 (3 SMS) o 216 si GL manda solo el código. WhatsApp es el canal recomendado.
- **Valores fijos:** el tema sigue al sistema, el aviso de caducidad salta 7 días antes y la red espera 5 s. Son constantes, no preferencias (P17).
- **Pruebas en este entorno:** la suite se compiló y los tests JVM pasaron fuera de Gradle. Gradle, lint, R8, Room KSP y los tests instrumentados no se han ejecutado nunca. Confirma con `./gradlew spviCheck spviInstrumentedTests` y la prueba de humo de [RELEASE.md](RELEASE.md) antes de distribuir.
- **Debug y release no comparten licencia:** cada uno tiene su propio paquete (`cu.spvi.app.debug` frente a `cu.spvi.app`) y su propia firma, así que su `deviceId` es distinto. Lo mismo pasa entre el APK distribuido a mano y uno publicado en Google Play con *Play App Signing*.

## Licencia del código

El repositorio no incluye un archivo de licencia del código fuente; los derechos corresponden al desarrollador (ver Ajustes → Soporte). El documento [LICENSE_CLIENT.md](LICENSE_CLIENT.md) describe el sistema de **licencias de uso** de la app, no la licencia del código fuente.

**Recursos de terceros incluidos en el APK:** iconos con trazados de Material Symbols (Apache 2.0) y, desde la 0.15.0, 9 ilustraciones de [unDraw](https://undraw.co) (licencia unDraw: uso libre, también comercial, sin atribución obligatoria) tomadas del paquete npm `undraw-svg` 2.0.0 (MIT) y convertidas a vectores Compose (`designsystem/.../ilustracion/SpviIlustraciones.kt`).

### Revisión de entradas y listas (09/10/2026, pendiente de verificar)

Los datos secundarios de las listas se muestran por renglones. Los campos numéricos rechazan formatos ambiguos en lugar de convertirlos silenciosamente; las cuentas bancarias admiten hasta 20 cifras. El precio de venta debe superar el costo: se valida al guardar productos/insumos y al cotizar, también después de aplicar descuentos. Los precios históricos no se modifican. La compilación y las pruebas quedan para opencode CLI en el PC del usuario: [guía de revisión](docs/REVISION_2026-10-09.md).
