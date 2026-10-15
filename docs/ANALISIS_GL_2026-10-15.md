# Análisis de los últimos cambios de GL y su impacto en SPVI

> **Instantánea histórica (2026-10-15):** los comentarios sobre clientes de escritorio o Flutter describen el árbol existente al redactarse. Solo el cliente Android se mantiene ahora; conserva la compatibilidad con licencias GL heredadas ya instaladas.

- **Fecha:** 2026-10-15
- **GL revisado:** `rmdvcd/GL` en `59b73d8` (merge del PR #3, 2026-10-15 00:03 UTC), commit principal
  `005288d` «Harden licensing validation and archive handling». También revisado `ec5ecf1` (PR #2,
  2026-10-14 01:55 UTC: códigos de 16 caracteres, `txNumber`, rollover, mitigaciones «Opción A»).
- **SPVI revisado:** rama `arena/48e006de-spvi` en `5ac6bf0` (incluye la auditoría del 2026-10-14).
- **Veredicto:** el contrato v1/SPVI2 **no cambia de formato** (el propio PR #3 lo declara). Las claves
  fijadas no rotaron. Hay **una** novedad con impacto real para el usuario (frescura obligatoria en la
  emisión SPVI2) y endurecimientos de validación que las solicitudes de SPVI ya cumplían.

## Qué cambió en GL (solo lo que toca al cliente)

### PR #3 (`005288d`) — posterior a nuestro último trabajo en SPVI

| Cambio en GL | Impacto en SPVI |
|---|---|
| `RequestFreshness`: emisión v1 y SPVI2 con ≤ 72 h de antigüedad y ≤ 2 h de adelanto, comparación exacta (antes SPVI2 no comprobaba frescura y v1 truncaba a horas) | **Sí, de comportamiento:** una solicitud generada hace más de 3 días ahora se rechaza al emitir (`RequestExpired`). SPVI genera la solicitud en el momento de pulsar «Solicitar», pero el pago/confirmación puede tardar: había que avisar. |
| `Spvi23.validarSolicitud` más estricto: nonce `^[A-Za-z0-9+/=_-]{16,128}$`, `devicePub` de 44 caracteres/33 B exactos, `renueva` `^[A-Za-z0-9._:-]{1,64}$`, `solicitadaEn` ≤ 64 caracteres | Sin cambios necesarios: los tres clientes generan nonce Base64url de 16 B (22 caracteres), `devicePub` comprimida de 33 B y `renueva` = id de licencia (UUID). Verificado campo a campo. |
| Límites de tamaño SPVI (texto 16 512, código 16 384, crudo 12 288 B) y `.glreg` (32 MiB, 10 000 entradas) | Sin impacto: la solicitud de SPVI ronda 500 caracteres. `.glreg` es formato interno de GL. |
| `devicePub` obligatoria y EC P-256 validada también en la puerta criptográfica v1 (`cifrarLicencia` sin fallback a la clave de GL) | Sin impacto: SPVI dejó de enviar solicitudes v1 en la 0.23.0 y siempre manda `devicePub`. |
| Helper de cliente de GL corregido (GL-01: el IV de HKDF/GCM no coincidía) | Sin impacto: ese helper vive en el repo de GL; SPVI tiene su propia implementación (`SolicitudCifrada`), cuyo IV sale del HKDF con efímera nueva por solicitud. Revisado: no tiene el defecto. |
| `MASTER_KEY_CODE` solo en `BuildConfig` debug; `isMasterKey` sin defecto permisivo | Sin cambios: SPVI ya exigía el flag de debug explícito (`ModoDesarrollo`, `SPVI_DESARROLLO`, `kDebugMode`) y falla cerrado. |
| `.glreg` v2 (conserva `precioCobrado`, compatible con v1), carreras de UI, copia de claves etiquetada | Interno de GL. Sin impacto. |
| AAD v1: la documentación se alinea con el código (`canonical()` con prefijo de longitud) | Sin cambios: SPVI ya acepta la AAD canónica y la heredada `1|alg|…` al re-verificar licencias largas instaladas, y el contrato no se ha movido. |

### PR #2 (`ec5ecf1`) — anterior a SPVI `29f909f`, ya absorbido

Códigos de licencia de 16 caracteres (flujo v1 de GL), `txNumber` persistido y deduplicado al emitir,
rollover de días, `devicePub` obligatoria, ventana de 72 h en v1, AAD canónica y sub-tokens de
secundarias: es el paquete «Opción A» que SPVI ya implementó en su lado (ver `SECURITY.md` §4 quater y
`LICENSE_CLIENT.md`). Único matiz nuevo del PR #3: la deduplicación de `txNumber` también se aplica
ahora al emitir SPVI2 (el operador lo teclea en GL; el campo `transaccion` que el escritorio manda en
el payload lo sigue ignorando el DTO de GL, que usa `ignoreUnknownKeys = true`).

### Claves

`gl-public-keys.txt` de GL (snapshot 2026-10-05) coincide con lo fijado en SPVI: ECDH
`sha256:2a3f:9fce:…` y firma vigente `sha256:58d4:3aac:…` en Kotlin, Python y Dart. SPVI conserva
además la firma anterior (2026-09-30) solo para verificar licencias ya emitidas, como manda la regla
de rotación de `LicenseTrust.kt`. **No hay rotación que aplicar.**

### Lo que GL sigue sin hacer (sin acción en SPVI)

- El nonce de la solicitud no se consume: replay posible dentro de la ventana de 72 h si el operador
  vuelve a confirmar el pago. Es riesgo del emisor, no del cliente.
- Sin atestación ni prueba de posesión de `devicePub`: GL cifra hacia la clave suministrada. SPVI la
  guarda en Keystore, que es lo máximo que el cliente puede aportar sin cambiar el contrato.
- Compatibilidad ECDH/Keystore en Android 26–30 sin validar en GL (su `minSdk` sigue en 26). SPVI usa
  su propia clave de dispositivo ya probada en esos API; nada que cambiar aquí.
- El campo `recupera` de la solicitud SPVI lo ignora el DTO de GL (`ignoreUnknownKeys = true`): la
  recuperación la resuelve el operador con sus registros, como documenta SPVI desde la 0.25.0.

## Cambios aplicados en SPVI

1. **Aviso de caducidad en el mensaje de solicitud** (`Vale 72 horas: envíala y paga cuanto antes; si
   pasa ese tiempo, genera otra solicitud.`) en la parte legible, en los tres clientes:
   `MensajesLicencia.solicitud`/`recuperacion` (Kotlin), `LicenseClient.request_message` (Python) y
   `Solicitud.mensaje` (Dart). Test de flujo de licencia actualizado y aserción nueva en escritorio.
2. **Validación de nombre alineada con GL** en escritorio (`NAME_RE`) y Flutter: antes solo se exigía
   «no vacío»; un nombre que GL rechaza (1 carácter, dígitos, guion bajo) pasaba y moría como rechazo
   mudo en el emisor. En Android no hizo falta: `Validators.nombre` ya es un subconjunto estricto de la
   regla de GL. Test nuevo `test_nombre_como_el_que_exige_gl`.
3. **Documentación:** `LICENSE_CLIENT.md` §2 recoge frescura, límites y que el nonce no se consume.

## Verificación

- Escritorio: `python -m unittest discover` → **131 tests OK** (incluye el nuevo de nombre y el aviso).
- Kotlin: el cambio es una línea de texto en el mensaje + test actualizado; queda verificado por el CI
  de la rama (`spviTests`). Dart: sin SDK de Flutter en este entorno; cambio mínimo (una línea de
  mensaje y una regex de validación), pendiente de `flutter test` donde haya Flutter.
