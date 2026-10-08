# SPVI — Seguridad

Cómo protege SPVI los datos del negocio, la licencia y los respaldos. Describe el código actual **0.30.0**; las advertencias sobre respaldos sin contraseña se actualizaron para reflejar que el cifrado interno no aporta confidencialidad frente a quien obtiene el archivo. Cada afirmación debe corresponder al código y al modelo de amenazas.

## 1. Principios

1. **Datos locales.** No hay servidor remoto, cuentas, nube propia, Firebase, analítica, telemetría ni anuncios. La principal sí abre un servidor TCP temporal en la red local para sincronizar hasta cinco secundarias; ese tráfico no sale a internet. Las actualizaciones y la lista de licencias revocadas consultan GitHub por HTTPS; la licencia se solicita a GL y, tras activarse, se verifica offline.
2. **Cifrado en reposo de todo dato persistente** (base de datos, preferencias, licencia), con claves atadas al Android Keystore.
3. **Permisos mínimos:** solo `CAMERA`, `INTERNET` y `POST_NOTIFICATIONS` (desde la 0.18.0, autorizado por el usuario), más `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` y `CHANGE_NETWORK_STATE` (desde la 0.19.2, autorizados por el usuario, solo para el servicio de la app principal con secundarias), y desde la 0.25.0 `REQUEST_INSTALL_PACKAGES` (instalar la actualización descargada) y `REQUEST_DELETE_PACKAGES` (pedir la desinstalación en el teléfono cuya licencia se recuperó en otro), los dos autorizados por el usuario, y desde la 0.26.0 `READ_MEDIA_IMAGES` (Android 13+), `READ_EXTERNAL_STORAGE` (hasta Android 12) y `WRITE_EXTERNAL_STORAGE` (hasta Android 9), autorizados por el usuario solo para el registro de la prueba (encontrar tras reinstalar la imagen cifrada de Imágenes/SPVI), y desde la 0.27.0 `USE_BIOMETRIC` y `USE_FINGERPRINT` (permisos normales, sin diálogo, añadidos por `androidx.biometric`), usados para el acceso con clave opcional y para autorizar la exportación de respaldos sin contraseña. Cada uno está limitado a esas funciones.
4. **Mensajes genéricos:** los errores de licencia y de cifrado no dicen qué comprobación falló.
5. **Fallo seguro:** un valor ilegible equivale a «ausente». Para la licencia eso significa prueba o bloqueo, nunca desbloqueo.

## 2. Gestión de claves

| Clave | Tipo | Dónde vive | Para qué | Código |
|---|---|---|---|---|
| `spvi_aead_v1` | AES-256-GCM | Android Keystore (StrongBox si el equipo lo tiene; si no, TEE). No es extraíble | Cifrar cada valor de DataStore y envolver los demás secretos | `data/.../security/KeystoreAead.kt` |
| Passphrase de SQLCipher | 32 bytes aleatorios | `noBackupFilesDir`, envuelta con `spvi_aead_v1` | Abrir `spvi.db` | `data/.../security/DatabasePassphrase.kt` |
| Clave del dispositivo | Par EC P-256 persistente | **API ≥ 31:** Keystore con `PURPOSE_AGREE_KEY` (alias `spvi_device_ecdh_v1`, StrongBox si es posible), la privada no sale. **API 26–30:** clave por software en `noBackupFilesDir`, envuelta con `spvi_aead_v1` (AAD = clave pública) | ECDH para descifrar la licencia; su SPKI viaja como `devicePub` | `data/.../security/AndroidDeviceKey.kt` |
| Clave de firma de GL | ECDSA P-256, pública | **Fijada en el build** (`LicenseTrust.SIGN_KEYS`, lista) con su huella SHA-256 | Verificar que la licencia la emitió GL. Es el ancla de confianza | `licencia/.../LicenseTrust.kt` |
| Clave ECDH de GL | P-256, pública | **Fijada en el build** (`LicenseTrust.ECDH_KEY`) con su huella. No se puede cambiar desde la app: desde la 0.13.0 ya no existe la pantalla *Clave del emisor* | Cifrar la solicitud de licencia hacia GL | `licencia/.../LicenseTrust.kt` |
| Claves efímeras | P-256 | Solo en memoria, una por solicitud | ECIES de la solicitud y su firma | `licencia/.../crypto/` |
| Clave del respaldo | AES-256 derivada | Con contraseña, PBKDF2 de la contraseña y salt aleatorio. Sin contraseña, PBKDF2 de un secreto ofuscado incluido en la app (10 000 iteraciones); no es secreto frente a quien obtenga el código/APK | Cifrar el archivo `.spvi` | `data/.../respaldo/BackupCipher.kt` |

**Reglas:**
- **Ninguna clave de GL se puede configurar desde la app.** Si se pudiera cambiar la de firma, cualquiera podría firmarse licencias. Un build sin la ECDH fijada no puede pedir licencias, y `LicenseTrustTest` comprueba que la clave está fijada y coincide con su huella.
- **Rotación de GL:** se añade la clave nueva a `SIGN_KEYS` en un build nuevo y se conserva la antigua, para que las licencias ya activadas sigan verificando.
- **Pérdida de claves del dispositivo** (borrar datos, reinstalar): la base de datos y la licencia instalada dejan de poder abrirse. El único camino para recuperar datos es un respaldo `.spvi`.

## 3. Cifrado

| Qué | Cómo | Código |
|---|---|---|
| Base de datos (inventario, ventas, clientes, perfil, precios) | **SQLCipher 4.6.1** vía `SupportOpenHelperFactory`, passphrase de 32 B | `data/.../db/SpviDatabase.kt` |
| Preferencias, progreso del asistente, licencia | Preferences DataStore; **cada valor** cifrado con `KeystoreAead` (AES-256-GCM, IV de 12 B, AAD = nombre de la clave, así un valor no se puede mover a otra clave) | `data/.../local/SecureDataStore.kt` |
| Respaldo `.spvi` | Con contraseña: PBKDF2-HMAC-SHA256 (**310 000** iteraciones); sin contraseña: **10 000** iteraciones sobre el secreto incluido en la app, sin confidencialidad. Salt de 16 B por archivo → AES-256-GCM sobre el JSON comprimido con gzip; cabecera autenticada como AAD. Formato en [FORMATOS.md](FORMATOS.md) | `data/.../respaldo/BackupCipher.kt` |
| Solicitud de licencia | ECIES-P256-AES256GCM-v1: ECDH efímera ↔ ECDH de GL, HKDF-SHA256 (`gl-req-v1`), AES-256-GCM con AAD canónica, firma ECDSA con la efímera | `licencia/.../crypto/GlCodec.kt` |
| Licencia recibida | Verificación ECDSA con la clave de firma de GL sobre `epk‖iv‖ct‖tag`, ECDH con la clave del dispositivo, HKDF (`gl-lic-v1`), AES-256-GCM con AAD `1|alg|<licenseId>` | `licencia/.../crypto/GlCodec.kt`, `LicenseValidator.kt` |
| Red | Solo HTTPS (`usesCleartextTraffic=false` y un interceptor que rechaza cualquier petición no HTTPS, también tras redirecciones) | `data/.../network/Red.kt` (OkHttp, sin Retrofit desde la 0.13.0) |

**Contraseña del respaldo:** es **opcional** («Proteger con contraseña», apagado por defecto); si se activa, exige 8 caracteres como mínimo y repetición. Con contraseña, PBKDF2-HMAC-SHA256 (310 000 iteraciones y salt aleatorio) deriva la clave. Sin contraseña se usa un secreto ofuscado incluido en la app: aunque el formato almacena AES-GCM, ese secreto no es confidencial y no protege el archivo de quien lo obtiene. La contraseña del usuario llega al caso de uso como `CharArray` y se borra al terminar; el JSON en claro se sobrescribe tras cifrarlo.

## 4. Superficie expuesta

| Superficie | Medida |
|---|---|
| **Permisos** | `CAMERA`: solo mientras la cámara está encendida (QR de vinculación o foto del producto), pedido con explicación previa. `INTERNET`: solo para la consulta semanal de actualizaciones y revocaciones en GitHub, y la sincronización en la red local. `POST_NOTIFICATIONS` (Android 13+): solo para el aviso fijo «Turno abierto desde las HH:MM», sin importes ni datos del cliente, y se pide al abrir un turno. Si se niega, no pasa nada. No hay `RECEIVE_BOOT_COMPLETED`: el aviso no se restaura tras reiniciar. `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` y `CHANGE_NETWORK_STATE` (0.19.2, normales, sin diálogo): solo para el servicio en primer plano `connectedDevice` de la app principal con secundarias, mientras está a la vista o hay algún turno abierto. `CHANGE_NETWORK_STATE` es requisito de Android 14 para ese tipo; SPVI no cambia ninguna red. `ACCESS_NETWORK_STATE` y los servicios de telemetría `datatransport` que añade ML Kit se **eliminan** del manifiesto |
| **Qué sale a internet** | Solo consultas HTTPS de actualizaciones y revocaciones en GitHub. No se envían datos del negocio, del usuario, del dispositivo ni códigos de productos. User-Agent `SPVI/0.27 (Android)`; GitHub puede recibir la IP y los metadatos habituales de la conexión. Sin caché, cookies, reintentos silenciosos ni redirecciones HTTPS→HTTP |
| **Copia automática de Android** | `allowBackup=false`, `fullBackupContent=false` y `data_extraction_rules` que excluyen todo de la nube y de la transferencia entre dispositivos |
| **Componentes exportados** | Solo `MainActivity`: launcher, `SEND text/plain` (SMS compartido) y `SEND`/`VIEW` `application/octet-stream` (respaldo, solo `content://`) |
| **Archivos recibidos** | Se copian a `cacheDir/compartir` con un tope de 128 MB y se borran a las 24 h. Nunca se importan solos: siempre piden confirmación (y la contraseña, si el archivo la tiene) |
| **FileProvider** | No exportado; solo expone `cache/compartir/` (permiso temporal de lectura para la app elegida) y, desde la 0.27.0, `cache/fotos/`: el archivo temporal donde la app de cámara escribe la foto de Nuevo producto/servicio (`TakePicture`, permiso temporal de escritura solo para esa URI). La foto se copia reducida (lado ≤ 1024 px, JPEG 85) a `filesDir/fotos` y la carpeta temporal se vacía. Nada de base de datos, preferencias ni las fotos guardadas |
| **Almacenamiento externo** | Guardar y abrir pasa por el selector del sistema (SAF). Única excepción (0.26.0): el registro cifrado de la prueba (`Pictures/SPVI/sys_<huella>.png` y `.sys_<huella>.bin` en Download y Documents, ver FORMATOS.md §2.7). El permiso de fotos se pide una sola vez, recién instalada; si se niega, la prueba sigue funcionando (solo no se detecta una instalación anterior). La app no lee ninguna otra imagen |
| **Visibilidad de paquetes** | Sin `<queries>`: SPVI no consulta ni abre otras apps de pago (Enzona se quitó en la 0.13.0 y el botón «Abrir Transfermóvil» en la 0.17.0) |
| **Portapapeles** | Solo se lee al tocar «Pegar» / «Pegar SMS», nunca en segundo plano |
| **Capturas y Recientes** | `FLAG_SECURE` en Perfil, Licencia, Pago electrónico, Respaldo, Migrar, el paso «Tus datos» del asistente, los pasos QR (tarjeta del vendedor, desde la 0.14.0) y de datos del cliente en Venta y Registros mientras se ven transferencias |
| **Red local (0.19.0)** | Solo si hay apps secundarias: servidor TCP de la app principal en la red local (puertos 47811–47820) y anuncio NSD `_spvi._tcp`, mientras la app está abierta o (desde 0.19.2) mientras el servicio en primer plano la mantiene, solo con turnos abiertos. Vinculación por QR de un solo uso (10 min) + ECDH P-256 autenticado con HMAC del token; sesiones AES-256-GCM con clave por sentido y nº de secuencia (sin repetición ni reflexión). Nada sale a internet. Detalle: [docs/VINCULACION.md](docs/VINCULACION.md) |
| **Logs** | La app no registra datos personales (la única llamada es un aviso genérico de la cámara). R8 elimina en release todas las llamadas a `android.util.Log` |
| **Fotos** | Se re-codifican al guardarlas (lado máx. 1024 px), lo que elimina EXIF y GPS. Coil no tiene módulo de red: solo muestra archivos locales |

## 4 bis. Novedades de la 0.25.0

| Superficie | Medida |
|---|---|
| **GitHub (actualizaciones y revocadas)** | Solo si el build trae un repositorio (`-PspviGithubRepo=usuario/repo`; vacío = no se consulta nada). Una vez por semana al abrir la app, nunca en segundo plano. Peticiones HTTPS **idénticas para todos los teléfonos**: `api.github.com/repos/<repo>/releases/latest` y `github.com/<repo>/releases/download/revocaciones/revocadas.json`, sin datos del negocio, del usuario ni del dispositivo. Respuestas de texto limitadas a 1 MB. `data/.../network/ActualizacionesRepositoryImpl.kt` |
| **APK descargado** | Solo se instala si su SHA-256 coincide con la huella publicada (`digest` de la API o recurso `.sha256`); sin huella no se ofrece instalar. Máx. 200 MB, en `cacheDir/actualizacion`. Instalación con `PackageInstaller` (sin FileProvider); Android exige confirmar y que el APK esté firmado con la **misma clave** que la app instalada. `app/.../actualizacion/` |
| **APK a las secundarias (red local)** | Por el canal cifrado de sincronización ya existente, por bloques de 256 KB; la secundaria comprueba el SHA-256 anunciado por la principal antes de instalar. `data/.../sync/ApkLocal.kt` |
| **Lista de revocadas** | Firmada por GL (ECDSA P-256, clave `gl-sign-v1` fijada en el build) sobre `"SPVI-REV1|" + datos`. Solo contiene huellas truncadas `SHA-256("SPVI-REV|" + licenseId)`: ni nombres, ni carnés, ni teléfonos. Un archivo sin firma válida, con otro formato o corrupto se ignora. `licencia/.../contract/ListaRevocaciones.kt` |
| **Teléfono con la licencia revocada** | Se marca la revocación, se borran los datos del negocio (base de datos, preferencias y estado) y queda bloqueado en «Licencia transferida», que pide desinstalar (`ACTION_DELETE`; Android siempre pide confirmación). No se puede desactivar mientras haya licencia instalada |
| **Respaldo v4** | Lleva el ID, tipo, secundarias, vencimiento y CI de la licencia (datos que ya figuran en el mensaje de licencia), no la licencia ni ninguna clave |
| **Licencia compartida a SPVI** | `SEND text/plain` con `SPVI2:` abre Licencia y la activa; la verificación criptográfica es la de siempre |

## 4 ter. Novedades de la 0.27.0

| Área | Medida |
|---|---|
| **Respaldo sin contraseña** | Formato `.spvi` v4 con un byte indicador autenticado. El archivo sigue usando AES-GCM, pero la clave procede de un secreto ofuscado que está en la app/código público: no ofrece confidencialidad y quien consigue el archivo puede leerlo sin conocer una contraseña. Antes de guardar o compartir, la app muestra una advertencia que debe confirmarse; si hay biometría inscrita, pide `BIOMETRIC_WEAK` sin alternativa `DEVICE_CREDENTIAL`. Sin biometría, la advertencia explícita es obligatoria. La autenticación biométrica autoriza la acción, pero no cifra ni protege el archivo. Con contraseña se usa PBKDF2 (310 000 iteraciones) y una contraseña del usuario |
| **Acceso con clave** | Opcional. `BiometricPrompt` con biometría o PIN/patrón **del teléfono** (`BIOMETRIC_WEAK` + `DEVICE_CREDENTIAL` en API 30+). SPVI no guarda ninguna clave. Se pide al abrir en frío y tras ≥ 10 min en segundo plano (`elapsedRealtime`, nunca la hora). Pantalla de bloqueo superpuesta con `FLAG_SECURE`; no destruye el estado. La preferencia es del teléfono y no va en el respaldo. No es un cifrado adicional: protege de un curioso con el teléfono desbloqueado, no de quien tenga acceso root |
| **Foto con la cámara** | `CAMERA` se pide justo antes de abrir la cámara; la galería usa el selector de fotos del sistema, sin permisos. `READ_MEDIA_IMAGES` sigue siendo solo del registro de la prueba |
| **Principal → secundaria** | Bloqueado en la interfaz y en `VinculacionViewModel` (`PRINCIPAL_NO_SECUNDARIA`) |
| **Clientes fijos** | Nombre, carné y teléfono en la base cifrada y en el respaldo; la pestaña Clientes lleva `FLAG_SECURE` y no se exporta |
| **Claves de GL** | Par del 05/10/2026 fijado; la firma anterior se conserva solo para verificar licencias ya emitidas |

**Riesgos residuales:** la revocación solo llega al teléfono antiguo si se conecta a internet. Si alguien controla el repositorio de GitHub puede publicar un APK, pero Android no lo instala como actualización si no lleva la firma del desarrollador. No puede falsificar la lista de revocadas sin la clave de GL.

## 5. Modelo de amenazas

| Amenaza | Mitigación | Riesgo residual |
|---|---|---|
| **Robo o pérdida del teléfono bloqueado** | Base de datos y preferencias cifradas con claves del Keystore; acceso con clave opcional si se activa | Si el acceso con clave está desactivado y el teléfono queda desbloqueado, SPVI no añade otra barrera |
| **Extracción de archivos** (copia de `/data`, ADB backup, copia en la nube) | Cifrado en reposo, claves no extraíbles, copia del sistema desactivada | Un equipo con root y la app en ejecución puede leer la memoria |
| **Licencia falsificada** | Firma ECDSA de GL verificada con una clave fijada en el APK; envelope estricto; `deviceId` y `devicePub` deben coincidir | — |
| **Licencia de otro teléfono o de otra app** | Se descifra solo con la clave del dispositivo; `deviceId` debe ser `SPVI:` + el propio | — |
| **Atrasar el reloj** | Se guarda la última hora vista; si el reloj retrocede más de 2 h → `ClockTampered` (bloqueo hasta corregirlo). Re-evaluación al volver a primer plano y cada ≤ 15 min | Adelantar y luego no tocar el reloj no da ventaja; no hay hora de red de confianza |
| **Licencia instalada manipulada** | Se guarda el envelope completo y se **re-verifica criptográficamente en cada evaluación**; no existe un indicador «licenciado» | — |
| **Reutilizar la licencia tras recuperarla en otro teléfono** (0.25.0) | GL revoca la anterior y la publica en la lista firmada; el teléfono antiguo la aplica en su consulta semanal, borra los datos y se bloquea | Si el teléfono antiguo nunca se conecta a internet, sigue funcionando hasta que venza su licencia |
| **Reutilizar la licencia tras migrar** | Se registra `lic.migrated_at` y se rechaza toda licencia emitida antes | Sin conexión, nadie obliga a ejecutar el borrado en el teléfono viejo |
| **APK modificado** (parche que salta la comprobación) | R8 sube el coste | **No se puede impedir** con una licencia offline |
| **Respaldo robado** | Con contraseña: AES-256-GCM, PBKDF2 (310 000 iteraciones) y salt aleatorio. Sin contraseña: la clave deriva de un secreto incluido en la app, sin confidencialidad real | Una contraseña débil se puede adivinar por fuerza bruta. Sin contraseña, cualquiera que obtenga el archivo puede leerlo; la confirmación y la biometría solo autorizan exportarlo, no lo protegen |
| **Respaldo alterado o cortado** | Cabecera autenticada, largo y SHA-256 del cifrado; todo se valida antes de escribir y la importación es una sola transacción | — |
| **Archivo malicioso recibido** («zip bomb», archivo enorme) | Tope de 128 MB al copiar y al descomprimir; solo `content://` | — |
| **Interceptación de red** | Solo HTTPS, sin redirecciones a HTTP; no se envían datos personales ni códigos de productos | GitHub puede ver la IP y los metadatos habituales de las solicitudes de actualización/revocación |
| **Canal de licencia** (WhatsApp/SMS) | La solicitud va cifrada hacia GL; la licencia solo la descifra el teléfono destino | Los metadatos del mensaje (quién escribe a quién) son visibles para el operador |
| **Datos de clientes compartidos** | El texto compartido de una venta no lleva costo ni ganancia; el de transferencias muestra solo las 3 últimas cifras del carné | Los PDF/Excel de Registros llevan el carné completo y no tienen contraseña |
| **Mirada por encima del hombro / Recientes** | `FLAG_SECURE` en pantallas con datos personales; tarjetas enmascaradas en Pago electrónico | — |

## 6. Buenas prácticas

**Para el usuario**
- Usa un bloqueo de pantalla en el teléfono: es lo que protege las claves del Keystore.
- Si el respaldo sale del teléfono (Drive, WhatsApp), es preferible activarle una contraseña larga y guardarla aparte. Si la contraseña se pierde, SPVI no puede recuperarla. Si la omites, cualquiera que obtenga el archivo puede leer los datos; la advertencia y la biometría, si está disponible, solo autorizan la exportación y no lo protegen.
- Guarda los respaldos en más de un sitio (teléfono y Drive, por ejemplo).
- Comparte los PDF y Excel de Registros solo con quien corresponda: no llevan contraseña.
- Pega en Licencia solo mensajes que vengan del desarrollador. Para cambiar de teléfono usa **Ajustes → Migrar**, no borres los datos a mano.

**Para el desarrollador**
- Firma siempre con el mismo keystore de release y guárdalo fuera del repositorio.
- **Clave de firma del APK** (no confundir con la de GL): guárdala fuera del repositorio (`keystore.properties` y `*.jks` están en `.gitignore`) y con copia de seguridad. Si se pierde, no se puede actualizar la app y cambian todos los `deviceId`. Ver [RELEASE.md](RELEASE.md).
- No hagas configurable la clave de firma de GL. Para rotarla, **añade** la nueva a `SIGN_KEYS` sin quitar la anterior y publica un build nuevo.
- Ejecuta `LicenseTrustTest` y `GoldenVectorTest` antes de cada versión; repite la prueba de extremo a extremo con GL (ver `LICENSE_CLIENT.md`) si GL cambia.
- Ejecuta `./gradlew spviCheck` antes de entregar: incluye `spviPermisos`, que falla si el manifiesto fusionado pide algo fuera de la lista autorizada: `CAMERA`, `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `CHANGE_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`, `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `USE_BIOMETRIC` y `USE_FINGERPRINT`.
- No añadas logs con datos de usuario ni dependencias con telemetría.
- La base vigente es Room v11 (`data/schemas/cu.spvi.data.db.SpviDatabase/11.json`). Cada cambio futuro de entidad debe subir la versión, añadir la migración explícita desde la versión anterior, exportar el esquema nuevo y cubrirla con `EsquemaTest`; la siguiente sería v12 (`Migration(11, 12)`, `12.json`). La recreación vacía solo se permite desde las versiones de desarrollo 1 y 2.
- Si cambia el formato del respaldo, sube la versión y mantén la lectura de las anteriores. Hay una excepción, y es única: en la 0.13.0 se dejaron de leer v1 y v2 porque no había clientes.

## 7. Comunicar un problema de seguridad

Escribe al desarrollador por WhatsApp o SMS al **+53 51815604** (Ajustes → Soporte). No publiques el detalle hasta que haya una versión corregida.
