# SPVI — Cliente de licencias (contrato GL v1 aplicado)

Cómo cumple SPVI el contrato v1 del emisor offline **GL** (`CONTEXTO_LICENCIAS.md`) en la versión **0.14.1**, actualizado al formato de la **0.23.0**. Las decisiones y su justificación estaban en `DECISIONES_LICENCIA_SPVI.md` (v1.5) (documento de trabajo retirado en la 0.18.0; lo esencial está en este archivo); aquí se resume lo que está implementado.

> **Este documento trata de la licencia de uso de la app**, no de la licencia del código fuente.

**Regla rectora:** SPVI se adapta al contrato v1 **sin cambiarlo** (`v = 1`, mismos algoritmos, AAD, info de HKDF y campos) y no reutiliza nada del almacenamiento de GL.

## 1. Piezas

| Pieza | Módulo / archivo | Función |
|---|---|---|
| Constantes del contrato | `:licencia` `contract/GlContract.kt` | `VERSION = 1`, `ALG = "ECIES-P256-AES256GCM-v1"`, `KID = "gl-sign-v1"`, `gl-req-v1`, `gl-lic-v1`, IV 12, tag 16, AES 32; `APP_NAME = "SPVI"`, `DEVICE_ID_PREFIX = "SPVI:"` |
| Modelos | `contract/GlModels.kt` | `Envelope` (8 campos), `RequestPayload`, `LicensePayload`, `TipoLicencia` (con precio), `EstadoLicencia`, `Via` |
| Criptografía | `crypto/Crypto.kt`, `crypto/GlCodec.kt`, `contract/SolicitudCifrada.kt`, `contract/LicenciaCorta.kt` | P-256 (punto comprimido), ECDH, HKDF-SHA256, AES-256-GCM, ECDSA; solicitud `SPVIR1`, licencia `SPVI2`, `GlLicenseOpener` (solo para la larga ya instalada) |
| Claves de GL | `LicenseTrust.kt` | Claves públicas (firma y ECDH) fijadas en el build con su huella |
| Reglas | `LicenseValidator.kt`, `LicenseState.kt` | Validación semántica, estados y texto del banner |
| Orquestación | `LicenseManager.kt` | Evaluar, construir la solicitud, activar, autorizar y ceder (Migrar) |
| Clave del dispositivo | `:data` `security/AndroidDeviceKey.kt` | Par P-256 persistente |
| Persistencia | `:data` `licencia/DataStoreLicenseStore.kt`, `DeviceIdProvider.kt` | DataStore cifrado propio de SPVI |
| UI | `:app` `licencia/`, `bloqueo/BloqueoGate.kt`, `root/RootViewModel.kt`, `migrar/` | Panel, bloqueo, re-evaluación, migración |

## 2. Solicitud (SPVI → GL) — 0.23.0

### Payload (`RequestPayload`, `v = 2`)

| Campo | Valor en SPVI |
|---|---|
| `v` | `2` (`GlContract.SOLICITUD_VERSION`) |
| `nombre`, `apellidos` | Del Perfil (dos campos separados, misma regla que GL: letras Unicode, 2–80) |
| `ci` | Del Perfil, en mayúsculas (CI cubano de 11 dígitos, dentro de 5–20 alfanumérico) |
| `via` | `WHATSAPP` o `SMS`, según el botón pulsado |
| `telefono` | Un teléfono del Perfil elegido por el usuario, normalizado a E.164 (`5XXXXXXX` → `+535XXXXXXX`). No se lee la SIM |
| `deviceId` | `"SPVI:" + ANDROID_ID`, solo `a-z0-9` ASCII, 128 caracteres como máximo. El prefijo identifica la app (ya no va `appName`) |
| `tipo` | `MENSUAL` · `SEMESTRAL` · `ANUAL` · `PERPETUA` |
| `solicitadaEn` | Instante actual en ISO-8601, truncado a segundos |
| `nonce` | 16 bytes de `SecureRandom` en Base64url sin relleno |
| `devicePub` | **Punto comprimido** P-256 (33 B) de la clave del dispositivo, Base64url sin relleno. Nunca `null` |
| `secundarias` | Apps secundarias, 0–10. Precio = base del tipo + `secundarias` × importe del tipo (1 000 / 5 000 / 9 000 / 17 000 CUP) |
| `renueva` | Solo al renovar: `id` de la licencia instalada, auténtica y con vencimiento (`LicenseManager.licenciaRenovable`). Ausente en una primera licencia |

Si el Perfil está vacío, el panel pide los datos y los guarda en el Perfil al solicitar.

### Cifrado (`SolicitudCifrada`) y mensaje (`MensajesLicencia.solicitud`)

1. Par efímero P-256; `epk` = su pública comprimida (33 B).
2. ECDH(efímera, ECDH pública de GL) → HKDF-SHA256 (salt = `epk`, info `SPVI-R1`, 44 B) → clave AES-256 ‖ IV.
3. AES-256-GCM con AAD `"SPVI-R1|"` ‖ `epk`. Código = `SPVIR1:` + Base64url(`epk` ‖ ct ‖ tag).
4. Mensaje = datos legibles (nombre, carné, teléfono, tipo, secundarias, precio y, si aplica, «Renueva») + renglón en blanco + código. `LicenseManager.buildRequest` devuelve `SolicitudGenerada(texto, codigo)`.
5. **Solo texto** (0.23.1): WhatsApp (`https://wa.me/5351815604?text=…`) o SMS (`smsto:+5351815604`, `sms_body`). El usuario solo pulsa Enviar. Sin permisos.

## 3. Activación (GL → SPVI) — licencia corta cifrada `SPVI2:`

El usuario pega el mensaje de GL (o solo el código) en *Activar licencia* (desde la 0.23.1 no hay QR). Especificación completa en `LicenciaCorta.kt` y `docs/GL_PROMPT_0.23.md`; resumen en `docs/LICENCIA_0.23.md`.

1. **Extracción:** `SPVI2:` + 210 caracteres Base64url, ignorando espacios y saltos de línea dentro del código. Las líneas de arriba no se leen. Sin código → `NotFound`, también si es una licencia larga.
2. **Firma** ECDSA `gl-sign-v1` (claves fijadas) sobre `"SPVI-L2|"` ‖ epk ‖ ct ‖ tag (r‖s).
3. **Descifrado:** ECDH(clave del dispositivo en el Keystore, `epk`) → HKDF (`SPVI-L2`) → AES-256-GCM.
4. **Huella** = SHA-256(`"SPVI-L2|" + deviceId + "|"` ‖ devicePub comprimida)[0..16], igual a la de este teléfono.
5. **Validación** (`LicenseValidator`): `PERPETUA` sin `venceEn`; el resto con `venceEn` posterior a `emitidaEn`; `secundarias` 0–10.
6. **Antigüedad:** una licencia emitida antes que la instalada, o antes de una migración, → `Outdated`.
7. **Guardado:** el código + `id`, `emitidaEn`, `tipo`, `venceEn`, `deviceId`. En cada arranque se repiten los pasos 2–5.

| Resultado | Mensaje al usuario |
|---|---|
| `Accepted` | «Licencia activada» |
| `NotFound` | «No se encontró una licencia en el texto pegado» |
| `Rejected` | «La licencia no es válida para este dispositivo» |
| `Outdated` | «Ya tienes instalada una licencia más reciente» |

Nunca se indica qué comprobación falló.

- **Licencia larga instalada** (envelope v1, antes de la 0.23.0): se sigue re-verificando (firma, descifrado con el AAD de su `licenseId`, `LicenseValidator`) hasta que venza. Ya no se puede activar una nueva.
- **Migrar:** la licencia `SPVI2:` de GL para otro teléfono (firma válida, no se descifra aquí) es una autorización; la propia → «mismo teléfono».

## 4. Estados, banner y bloqueo

| Estado | ¿Desbloquea? | Origen |
|---|---|---|
| `Trial(díasRestantes)` | Sí | Sin licencia, dentro de los **7 días** desde el primer arranque |
| `TrialExpired` | No | Prueba agotada, o teléfono que cedió su licencia al migrar |
| `Active(tipo, venceEn)` | Sí | `estado = ACTIVA` y `venceEn` en el futuro |
| `Perpetual` | Sí | `PERPETUA` |
| `Expired(tipo)` | No | `VENCIDA`, o `venceEn` ya pasado |
| `Revoked` | No | `estado = REVOCADA` en un envelope firmado (se muestra como «Licencia no válida») |
| `ClockTampered` | No | El reloj retrocedió más de **2 h** respecto a la última hora vista |

- **Vencimiento:** lo fija GL desde la emisión (+30 / +180 / +365 días).
- **Evaluación** (`LicenseManager.evaluate`), en este orden: reloj → licencia guardada, **re-verificada criptográficamente** → periodo de prueba. No existe un indicador «licenciado».
- **Re-evaluación:** al arrancar, al volver a primer plano y mientras la app está abierta (`VigilarLicencia`: justo al vencer o cada ≤ 15 min).
- **Banner de Inicio:** «Periodo de prueba restante: N días»; Mensual en días; Semestral y Anual en meses (en el último mes, en días); Perpetua sin banner.
- **Bloqueo:** si el estado no desbloquea, `RootViewModel` muestra el panel de Licencia **directamente**, también si vence con la app abierta. Desde ahí solo se llega a *Soporte*. Atrás sale de la app.
- **Precios mostrados:** Mensual 6,000 · Semestral 30,000 · Anual 50,000 · Perpetua 90,000 CUP. Perpetua no ofrece renovación; cualquier otra licencia instalada ofrece «Renovar o cambiar».

## 5. Claves de GL

| Clave | Política |
|---|---|
| Firma (`kid = gl-sign-v1`) | **Solo fijada en el build**, como lista (`LicenseTrust.SIGN_KEYS`) para poder rotarla. Vigente (05/10/2026) `sha256:58d4:3aac:…:8c4e`; se conserva la anterior `sha256:8a91:bcfb:…:6888` solo para que las licencias ya emitidas con ella sigan verificando |
| ECDH | **Solo fijada en el build** (`LicenseTrust.ECDH_KEY`). Vigente (05/10/2026) `sha256:2a3f:9fce:…:666d`; sustituye a `sha256:7b51:9e55:…:538c`. Desde la 0.13.0 no se puede pegar desde la app: la pantalla *Clave del emisor* se eliminó en P17. Rotarla exige un build nuevo |

Origen: pestaña **Claves** de GL → «Copiar ambas claves» ([docs/GL_CONTEXTO_LICENCIAS.md](docs/GL_CONTEXTO_LICENCIAS.md)). GL regeneró el par al desinstalarse y reinstalarse el 05/10/2026.

`LicenseTrustTest` comprueba que cada clave fijada se lee como P-256 y coincide con su huella, y que la rotación conserva la firma anterior y sustituye la ECDH. Cambiar la ECDH no afecta a la licencia ya instalada. Pero una solicitud hecha con un SPVI anterior a la 0.27.0 va cifrada hacia la ECDH vieja y **GL ya no puede abrirla**: el cliente tiene que actualizar SPVI y volver a pedirla.

## 6. Almacenamiento

DataStore cifrado propio (`SecureDataStore`: cada valor con AES-256-GCM del Keystore y AAD = nombre de la clave). Excluido de toda copia del sistema.

| Clave | Contenido |
|---|---|
| `lic.envelope` | Desde 0.23.0, la licencia `SPVI2:…` (reconocida por el prefijo); antes, el envelope completo de la larga + `id`, `emitidaEn`, `tipo`, `venceEn`, `deviceId` (los metadatos se contrastan con lo verificado en cada evaluación) |
| `lic.trial_start` | Inicio del periodo de prueba |
| `lic.last_seen` | Última hora vista (antirretroceso del reloj) |
| `lic.migrated_at` | Instante en que el teléfono cedió su licencia |

Un valor ilegible (Keystore borrado) cuenta como ausente: el teléfono queda en prueba o bloqueado, nunca desbloqueado. **La licencia no entra en los respaldos.**

## 7. Migrar a otro teléfono (O5)

El contrato v1 no tiene mensaje de migración. SPVI lo resuelve **sin cambiar el contrato**:

1. **Solicitud** al desarrollador (WhatsApp/SMS, texto plano): IDs de los dos teléfonos, ID de la licencia y nombre, **sin CI**.
2. **Respaldo completo** del teléfono viejo.
3. **Autorización** = la licencia que GL emite al teléfono **nuevo**. El viejo verifica la **firma de GL sin descifrarla** (la firma cubre el cifrado) y comprueba que **no** es suya.
4. **Borrar este teléfono:** solo con una autorización válida y escribiendo `BORRAR`. Orden fijo, sin cancelación a medias: ceder la licencia (`lic.migrated_at` + borrar la licencia) → borrar base de datos, preferencias, fotos y temporales → re-evaluar.

Después, el teléfono viejo queda bloqueado sin periodo de prueba, rechaza cualquier licencia emitida antes de la migración (también el mensaje viejo que siga en WhatsApp) y acepta una emitida después.

**Límite:** sin conexión, nada obliga a ejecutar el paso 4 en el teléfono viejo. Lo controla el desarrollador decidiendo cuándo emite.

## 8. Impacto en el contrato

| Decisión | ¿Cambia el contrato? | ¿Hay que tocar GL? |
|---|---|---|
| Payload v1 completo + `appName`, `deviceId` con prefijo `SPVI:` (D2) | No | No |
| `devicePub` siempre presente (D2.5) | No | No |
| Claves fijadas; ECDH configurable solo si falta (D3, D3.1) | No | Recomendado: pantalla «Exportar claves públicas» en GL |
| Destino +5351815604 (D4) | No | No |
| Envelope estricto (D2.6) | No (alineación) | No |
| Migrar (O5) | No | No |
| Campo opcional `secundarias` en solicitud y licencia (0.21.0, C4) | No (campo extra al final; el payload de la licencia se lee con `ignoreUnknownKeys`) | **Sí:** GL debe copiarlo de la solicitud a la licencia y cobrar por él (hoy en `docs/GL_PROMPT_0.25.md`) |
| Licencia corta + QR, `licenciaCorta` (0.22.0, L-b/L-c) | No (forma adicional; la larga sigue igual) | **Sí:** GL la emite si la solicitud trae `licenciaCorta` (sustituida en la 0.23; ver `docs/GL_PROMPT_0.25.md`) |
| Renovación sin perder días, `renueva` (0.22.0, L-e) | No (campo extra al final) | **Sí:** GL calcula `venceEn = max(ahora, venceEn anterior) + duración` con SUS registros |
| `licenseId` fuera del envelope (N1) | No | Resuelto: GL lo pone en la línea «ID:» (solo afecta a la larga ya instalada) |
| **Solicitud `SPVIR1` + licencia corta cifrada `SPVI2` únicas, mensaje «datos + renglón + código», solo texto (0.23.0; QR quitado en la 0.23.1)** | **Sí:** solicitud `v = 2` y la larga deja de activarse; las claves de GL no cambian | **Sí:** `docs/GL_PROMPT_0.23.md` |

## 9. Pruebas

| Test | Qué cubre |
|---|---|
| `LicenseTrustTest` | Claves fijadas: P-256, huellas, firma ≠ cifrado |
| `GoldenVectorTest` | `printRequest` genera una solicitud real para GL; `verifyRealLicense`: la larga real de GL ya no se activa, pero instalada sigue valiendo |
| `LicenciaCortaTest` | Licencia `SPVI2`: formato, extracción, punto comprimido, rechazos, clon, Migrar, renovación, vector de Python |
| `LicenseFlowTest`, `RuntimeVerificationTest`, `BannerTest`, `CasosBordeLicenciaTest` | Flujo completo con un GL simulado (`FakeGl`), formato de la solicitud, estados, reloj, bytes alterados, perpetua, migración |
| `LicenciaInstrumentedTest` (`:data`) | Keystore real y DataStore cifrado |
| `LicenciaUiTest`, `FlujoLicenciaTest` (`:app`) | Panel y bloqueo → activar → Inicio |

Prueba de extremo a extremo con la app GL real: generar una solicitud en SPVI, emitir la respuesta en GL, pegarla en Licencia → Activar y comprobar el estado. El documento detallado `PRUEBA_COMPATIBILIDAD_GL.md` se retiró en la 0.18.0.
