# Licencia 0.23.x: solicitud y licencia cifradas, mensaje «datos + código», solo texto

> **0.23.1 (44):** se quitó todo lo de QR (Prompt 65): la solicitud y la licencia viajan **solo como texto** por WhatsApp o SMS, y en Activar solo queda Pegar + Activar. Motivo: manejar una imagen de QR en el mismo teléfono es complicado para usuarios no avanzados.

Prompt 64. Sustituye el formato de la 0.22.0 (documento ya eliminado; ver HISTORIAL_DESARROLLO.md). Prompt para GL: `docs/GL_PROMPT_0.23.md`.

## 1. Qué cambia para el usuario

| Antes (0.22.0) | Ahora (0.23.0) |
|---|---|
| Solicitud = envelope JSON (~1 000 caracteres) | Solicitud = **datos legibles + renglón en blanco + `SPVIR1:…`** (unos 780 caracteres) |
| Por WhatsApp o SMS, el envelope | Por WhatsApp (`wa.me`) o SMS: **solo texto** |
| GL respondía la licencia larga (y la corta `SPVI1:` si se pedía) | GL responde **siempre** la licencia corta **cifrada** `SPVI2:` (216 caracteres) con las características arriba |
| QR de la licencia, con cámara o imagen | **Sin QR**: se copia el mensaje y se pega |
| Se activaban la larga y la corta | **Solo `SPVI2:`**. La larga ya instalada sigue valiendo hasta que venza |

## 2. Mensajes

Solicitud (SPVI → GL), `MensajesLicencia.solicitud`:

```
Solicitud de licencia SPVI
Nombre: María Pérez González
Carné de identidad: 85010112345
Teléfono: +5352345678
Tipo: Mensual
Apps secundarias: 2
Precio: 8,000.00 CUP
Renueva: d53a020b-4062-4825-8219-024f997173f8

SPVIR1:Az6UNtjK…
```

Licencia (GL → SPVI), `MensajesLicencia.licencia` (referencia para GL):

```
Licencia SPVI
Tipo: Mensual
Apps secundarias: 2
Emitida: 03/10/2026
Vence: 02/11/2026
ID: 7c9e6679-7425-40de-944b-e07fc1f90ae7

SPVI2:A8Ntuq3A…(216 caracteres)
```

Las líneas de arriba son **solo para leer**. SPVI y GL usan únicamente el código: si alguien edita el texto, no cambia nada.

## 3. Criptografía

| | Solicitud `SPVIR1` | Licencia `SPVI2` |
|---|---|---|
| Binario | `epk 33 ‖ ct ‖ tag 16` | `epk 33 ‖ ct 44 ‖ tag 16 ‖ firma 64` = 157 B |
| Clave | ECDH(efímera de SPVI, ECDH de GL) | ECDH(efímera de GL, clave del teléfono) |
| HKDF-SHA256 | salt = epk, info `SPVI-R1`, 44 B → clave ‖ IV | salt = epk, info `SPVI-L2`, 44 B → clave ‖ IV |
| AES-256-GCM, AAD | `"SPVI-R1|"` ‖ epk | `"SPVI-L2|"` ‖ epk |
| Contenido | JSON `RequestPayload` v2 (`devicePub` = punto comprimido) | cuerpo 44 B: formato 2, id, huella, tipo, estado, secundarias, fechas |
| Firma | — (GCM autentica; la firma de la efímera v1 no autenticaba a nadie) | ECDSA `gl-sign-v1` sobre `"SPVI-L2|"` ‖ epk ‖ ct ‖ tag, r‖s |

Huella = SHA-256(`"SPVI-L2|" + deviceId + "|"` ‖ devicePub comprimida)[0..16].

Por qué es seguro:

- **Solo GL la emite:** firma con la clave fijada en el build. Un byte cambiado → rechazo.
- **Solo este teléfono la lee:** la clave privada está en el Keystore. Copiar el `deviceId` y la clave pública a otro teléfono no basta (`copiarElDeviceIdYLaClavePublicaAOtroTelefonoNoBasta`).
- **Atada a este dispositivo:** la huella cifrada (deviceId + clave).
- **Nadie la lee por el camino:** tipo, fechas e id van cifrados. Lo de arriba lo escribe GL en claro solo para el cliente.
- **Migrar:** la firma se comprueba SIN descifrar, así que el teléfono viejo reconoce la licencia del nuevo como autorización.

## 4. Envío

WhatsApp: `wa.me/5351815604?text=…` con el mensaje. SMS: `smsto:+5351815604` con el mismo texto. El usuario solo pulsa Enviar. **Sin permisos ni archivos.** (La 0.23.0 enviaba además una imagen QR por WhatsApp; se quitó en la 0.23.1, igual que la lectura del QR con la cámara o desde una imagen.)

## 5. Compatibilidad

- Una licencia **larga** ya instalada se sigue re-verificando con su criptografía (firma de GL + descifrado) hasta que venza (`laLicenciaLargaYaNoSeActivaPeroLaInstaladaSigue`, `GoldenVectorTest.verifyRealLicense`).
- `SPVI1:` nunca la emitió GL. Si alguien la tuviera instalada, pasaría a prueba o a bloqueo, según los días de prueba que le queden.
- SPVI < 0.23.0 no activa `SPVI2:`. GL debe avisar al operador si llega una solicitud antigua (envelope JSON).

## 6. Pruebas

- `:licencia` (77 tests):
  - `LicenciaCortaTest` (13): formato, extracción, punto comprimido, activación y reinicio, rechazos, clon, Migrar, renovación y vector de Python;
  - `LicenseFlowTest`;
  - `RuntimeVerificationTest` (cambiar un byte de epk, ct, tag o firma);
  - `CasosBordeLicenciaTest`;
  - `GoldenVectorTest`.
- `:app`:
  - `LicenciaViewModelTest`: evento `Enviar(via, texto, codigo)`.
- `tools/licencia/probar_gl.py`:
  - otra implementación (Python) de los dos formatos;
  - `autoprueba` da CORRECTA;
  - `vector` coincide con `LicenciaCortaTest`;
  - `verificar` comprueba la respuesta real de GL.
- `docs/GL_SOLICITUD_PRUEBA.txt` contiene una solicitud real hacia la clave ECDH de GL (renovación de d53a020b…, que debe vencer el 2026-12-02).

## 7. Suposiciones

- **S1.** Por SMS, la respuesta completa ocupa unos 340 caracteres GSM-7 (3 SMS concatenados). Si GL envía solo la línea `SPVI2:`, son 216 (2 SMS). Las dos formas se activan.
- **S2.** (0.23.1) Sin QR: si la app de mensajes parte el código en líneas, SPVI lo une al pegar.
- **S3.** Fechas del mensaje de la licencia: hora de Cuba (America/Havana). El vencimiento real es el instante cifrado.
