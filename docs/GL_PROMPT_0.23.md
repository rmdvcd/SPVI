# Prompt para GL — SPVI 0.23.1: solicitud y licencia cifradas, mensaje con datos + código (solo texto)

Pega el bloque siguiente en opencode (escritorio), dentro del proyecto **GL**. Es el único prompt que hay que aplicar:
sustituye a la versión de la 0.22 y a la versión anterior de este archivo (la de la 0.23.0, que pedía QR).

Qué cambia en el contrato de SPVI:
- la solicitud pasa a ser `SPVIR1:` (compacta, cifrada);
- la ÚNICA respuesta es la licencia corta **cifrada** `SPVI2:`;
- todo va **solo como texto**, sin QR ni imágenes en ningún sentido.

Las licencias largas ya instaladas siguen valiendo en los teléfonos hasta que venzan, pero SPVI 0.23.x ya no activa
licencias largas ni `SPVI1:`. Una SPVI anterior a 0.23.0 tampoco sabe activar `SPVI2:` (ver punto 7).

---

```text
Actualiza GL para SPVI 0.23.1. Las claves NO cambian: la misma clave ECDH de GL (la que descifra las solicitudes) y
la misma clave de firma gl-sign-v1. Todo lo demás de este punto en adelante sustituye al formato de SPVI anterior.

0) QUITA lo que haya de versiones anteriores para SPVI (solo si existe en GL):
   - la emisión de la licencia corta firmada "SPVI1:" y el campo "licenciaCorta";
   - la generación, muestra o envío de códigos QR / imágenes PNG de licencias o solicitudes de SPVI;
   - la emisión de la licencia LARGA (envelope JSON) para SPVI.
   No toques nada de otras apps que use GL.

1) MENSAJE DE SOLICITUD QUE LLEGA (SPVI → GL), por WhatsApp o SMS, SOLO TEXTO (sin imágenes ni QR):
      Solicitud de licencia SPVI
      Nombre: María Pérez González
      Carné de identidad: 85010112345
      Teléfono: +5352345678
      Tipo: Mensual
      Apps secundarias: 2
      Precio: 8,000.00 CUP
      Renueva: d53a020b-4062-4825-8219-024f997173f8      (solo si es una renovación)
      <renglón en blanco>
      SPVIR1:<Base64url sin relleno>
   Las líneas de arriba son SOLO para leer: GL usa exclusivamente lo que va cifrado en SPVIR1 (si no coinciden,
   manda el código). El operador pega el mensaje en GL (un cuadro de texto con botón «Pegar»): busca "SPVIR1:" en
   cualquier parte del texto, toma lo que sigue IGNORANDO espacios y saltos de línea (algunas apps parten líneas largas)
   y para en el primer carácter que no sea A-Z a-z 0-9 - _ . Si no hay "SPVIR1:" → «No es una solicitud de SPVI 0.23».

2) DESCIFRAR LA SOLICITUD SPVIR1:
      bytes = Base64url-decode(lo que sigue a "SPVIR1:")  =  epk (33 B) || ct || tag (16 B)
      epk  = clave pública efímera P-256 de SPVI, punto COMPRIMIDO SEC1 (0x02/0x03 || x de 32 B)
      ikm  = ECDH(clave privada ECDH de GL, epk)                    (32 B, coordenada x)
      okm  = HKDF-SHA256(ikm, salt = epk (33 B), info = UTF-8("SPVI-R1"), 44 B)
      clave AES-256 = okm[0..32), IV = okm[32..44)
      plano = AES-256-GCM-decrypt(clave, IV, AAD = UTF-8("SPVI-R1|") || epk, ct || tag)
   No hay firma de la efímera (ya no hace falta: GCM autentica). Si GCM falla → «solicitud dañada o no es para GL».
   plano = JSON UTF-8:
      {"v":2,"nombre":"…","apellidos":"…","ci":"…","via":"WHATSAPP"|"SMS","telefono":"+53…",
       "deviceId":"SPVI:…","tipo":"MENSUAL"|"SEMESTRAL"|"ANUAL"|"PERPETUA","solicitadaEn":"ISO-8601",
       "nonce":"…","devicePub":"<33 B comprimidos, Base64url sin relleno>","secundarias":0..10,
       "renueva":"<licenseId>"   (opcional)}
   Ya NO vienen "appName" ni "licenciaCorta": la app es el prefijo de deviceId ("SPVI:") y la corta es la única.
   Lee el JSON ignorando claves desconocidas. Valida v = 2, deviceId con prefijo "SPVI:", secundarias 0..10 y que
   devicePub sea un punto válido de P-256. Muestra al operador los datos DESCIFRADOS (no los de las líneas de arriba).
   Precio = base + secundarias × importe del tipo (sin cambios: 6000/30000/50000/90000 y 1000/5000/9000/17000 CUP).
   Descomprimir devicePub en Java (si tu librería no lo hace):
      BigInteger p = new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16);
      BigInteger b = new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16);
      // c = los 33 bytes (Base64url-decode de devicePub); c[0] debe ser 2 o 3
      BigInteger x = new BigInteger(1, Arrays.copyOfRange(c, 1, 33));
      BigInteger rhs = x.pow(3).subtract(x.multiply(BigInteger.valueOf(3))).add(b).mod(p);
      BigInteger y = rhs.modPow(p.add(BigInteger.ONE).shiftRight(2), p);
      if (!y.multiply(y).mod(p).equals(rhs)) throw …;            // no es un punto de la curva
      if (y.testBit(0) != (c[0] == 3)) y = p.subtract(y);
      ECPublicKey pub = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(
          new ECPublicKeySpec(new ECPoint(x, y), paramsDeP256));   // paramsDeP256: de cualquier clave P-256 generada
   (En Python: ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), c).)

3) LICENCIA (GL → SPVI): SOLO la corta cifrada. Texto del código:
      "SPVI2:" + Base64url SIN relleno de ( epk 33 B || ct 44 B || tag 16 B || firma 64 B )
      = 210 caracteres Base64url tras el prefijo (157 B); 216 contando "SPVI2:"
   Cuerpo en claro (44 B, big-endian):
      byte 0       formato = 0x02
      bytes 1–16   licenseId (UUID en 16 B: msb || lsb)
      bytes 17–32  huella = primeros 16 B de SHA-256( UTF-8("SPVI-L2|" + deviceId + "|") || devicePub comprimida 33 B )
      byte 33      tipo: 0 MENSUAL, 1 SEMESTRAL, 2 ANUAL, 3 PERPETUA
      byte 34      estado: 0 ACTIVA, 1 VENCIDA, 2 REVOCADA, 3 PERPETUA
      byte 35      secundarias (0..10)
      bytes 36–39  emitidaEn, segundos Unix (uint32, UTC)
      bytes 40–43  venceEn, segundos Unix (uint32); 0 = sin vencimiento (PERPETUA)
   Cifrado hacia el teléfono:
      efímera = par P-256 NUEVO en cada licencia; epk = su pública comprimida (33 B)
      ikm = ECDH(efímera privada, devicePub)
      okm = HKDF-SHA256(ikm, salt = epk, info = UTF-8("SPVI-L2"), 44 B) → clave AES-256 = okm[0..32), IV = okm[32..44)
      ct || tag = AES-256-GCM(clave, IV, AAD = UTF-8("SPVI-L2|") || epk, cuerpo)
   Firma (cifrar y luego firmar): ECDSA P-256 / SHA-256 con gl-sign-v1 sobre
      UTF-8("SPVI-L2|") || epk || ct || tag
   en formato CRUDO r||s (32 + 32 B, con ceros a la izquierda). En Java: "SHA256withECDSAinP1363Format", o convierte el DER.
   Guarda en tu base de datos id, deviceId, tipo, estado, secundarias, emitidaEn, venceEn (sin milisegundos) como hasta
   ahora; ya no emitas la licencia larga.

4) MENSAJE DE RESPUESTA (características + renglón en blanco + código). Texto EXACTO (fechas dd/MM/yyyy en hora de
   Cuba, America/Havana; sin tildes ni ñ para que por SMS no pase a UCS-2):
      Licencia SPVI
      Tipo: Mensual                (Mensual | Semestral | Anual | Perpetua)
      Apps secundarias: 2
      Emitida: 03/10/2026
      Vence: 02/11/2026            ("Vence: nunca" si es perpetua)
      ID: 7c9e6679-7425-40de-944b-e07fc1f90ae7
      <renglón en blanco>
      SPVI2:…(210 caracteres tras el prefijo)
   - Vía WhatsApp: ese texto, tal cual. NO generes QR ni imágenes: el cliente copia el mensaje y lo pega en SPVI
     (manejar una imagen en el mismo teléfono es difícil para usuarios no avanzados).
   - Vía SMS: el mismo texto (unos 340 caracteres GSM-7 → 3 SMS concatenados; si prefieres 2 SMS, manda solo la línea
     SPVI2).
   En GL: muestra el mensaje en un cuadro de texto de solo lectura con un botón «Copiar mensaje» (copia el texto
   completo, con el renglón en blanco). Si GL ya abre WhatsApp o SMS con el texto, sigue haciéndolo solo con texto.
   SPVI no lee las líneas de arriba: todo lo que cuenta va cifrado y firmado en el código.

5) RENOVAR SIN PERDER DÍAS (sin cambios respecto a 0.22.0): si la solicitud trae "renueva", existe en TUS registros,
   es del MISMO deviceId, no está revocada ni es perpetua → venceEn = max(ahora, venceEn anterior) + duración
   (30/180/365 días), emitidaEn = ahora. Si no: ignora "renueva" y avisa al operador. Nunca uses fechas del cliente.

6) PRUEBAS (añádelas a los tests de GL):
   a) Vector de la licencia (claves de PRUEBA, no las de producción):
      dispositivo de prueba: deviceId = SPVI:golden0000000001
        devicePub comprimida (Base64url) = AiuZGHe7sUgbFLfqufeF1BMCvKuYZQuJKYSxnIoc0zMK
      licenseId = 7c9e6679-7425-40de-944b-e07fc1f90ae7, MENSUAL, ACTIVA, secundarias 2,
      emitidaEn = 2026-10-03T20:00:00Z, venceEn = 2026-11-02T20:00:00Z
      → huella (hex) = fb445359b06179d282c7970ea4adab32
      → cuerpo (hex) = 027c9e6679742540de944be07fc1f90ae7fb445359b06179d282c7970ea4adab320000026ac15ec06ae8ebc0
      efímera de PRUEBA, escalar d (hex) = 0a0b0c0d0e0f10111213141516171819a0b0c0d0e0f1f2f3f4f5f6f7f8f9fafb
      → epk (hex) = 03c36dbaadc01f9f92d0554cd88b70a31e4494cf092e4747d6ff74fd226c17a2b3
      → ct||tag (hex) = 0adcdd15829c196616a17a75442d35f75cec1626c4263329266096c095a390b9d8eca0d37cf0c5c3896bc13af9545a3f75be5ff3834ff507d2b42512
      firma de PRUEBA, escalar d (hex) = 1f2e3d4c5b6a79881f2e3d4c5b6a79881f2e3d4c5b6a79881f2e3d4c5b6a7988
        SPKI (Base64) = MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEvXxzuIsum0ztpiAistqL4TGTpbVu3Cbn33hC4kzQtesGBa2nvag6xqK4DX4xQED6R/8WuDushc7bAURRu3znGg==
      → mensaje válido (la firma ECDSA cambia en cada emisión; verifícala con la SPKI de prueba):
        Licencia SPVI
        Tipo: Mensual
        Apps secundarias: 2
        Emitida: 03/10/2026
        Vence: 02/11/2026
        ID: 7c9e6679-7425-40de-944b-e07fc1f90ae7

        SPVI2:A8Ntuq3AH5-S0FVM2Itwox5ElM8JLkdH1v90_SJsF6KzCtzdFYKcGWYWoXp1RC0191zsFibEJjMpJmCWwJWjkLnY7KDTfPDFw4lrwTr5VFo_db5f84NP9QfStCUSFOSb9HjaQkeWuTWUZGftUUhs0d0y5eIav9Jn59WIyb9WHNln6Ui7Y_Ce6wOVPP9Do3JcgQ-0a--x8-TLCh1g0A
      Con esa efímera, tu epk y tu ct||tag deben ser idénticos byte a byte; tu firma debe verificar con la SPKI.
   b) Solicitud: cifra un JSON v2 de ejemplo hacia una clave ECDH de prueba con el algoritmo del punto 2 y comprueba
      que lo abres; cambia 1 byte de epk, ct o tag → debe fallar.
   c) Renovación: anterior vence en 5 días → la nueva MENSUAL vence a los 35; vencida hace 3 días → 30 desde ahora;
      "renueva" de otro deviceId o revocada → se ignora y se avisa.
   d) Extracción: la solicitud pegada con el código partido en varias líneas se abre igual; un texto sin "SPVIR1:"
      o un envelope JSON antiguo dan el aviso del punto 7 o «No es una solicitud de SPVI 0.23».
   e) El mensaje de respuesta es exactamente el del punto 4 (6 líneas + renglón en blanco + código) y GL no genera
      ningún QR ni imagen.

7) COMPATIBILIDAD: SPVI < 0.23.0 envía la solicitud antigua (envelope JSON v1 con "appName"). Si llega una así, NO la
   rechaces sin explicar: muestra al operador «SPVI desactualizada: pide al cliente que instale SPVI 0.23.1 o posterior»
   (esa versión vieja no puede activar SPVI2). No emitas licencias largas para SPVI.

No cambies nada más. Al terminar, resume los archivos tocados y cómo probar.
```

---

## Cómo probar el resultado (SPVI → GL → SPVI)

1. Solicitud de prueba: `docs/GL_SOLICITUD_PRUEBA.txt` (mensaje completo, cifrado hacia la clave ECDH real de GL).
   Es una renovación de la licencia real d53a020b…, MENSUAL con 2 secundarias, 8 000 CUP.
2. Pégala en GL. Debe mostrar los datos, «Renueva: d53a020b… (vence el 2026-11-02)» y que la nueva vence el
   **2026-12-02**.
3. Guarda la respuesta de GL (mensaje completo o solo la línea `SPVI2:…`) en un archivo y ejecuta:
   ```
   python3 tools/licencia/probar_gl.py verificar ARCHIVO --tipo MENSUAL --secundarias 2 --vence 2026-12-02
   ```
   Verifica la firma con la clave real de GL, descifra con la clave del dispositivo de prueba y comprueba huella, tipo,
   secundarias, fechas y que el texto de arriba coincide con lo cifrado.
4. Comprueba que GL ya no genera QR ni imágenes: la respuesta es solo texto.
5. Prueba real: SPVI 0.23.1 en un teléfono → Solicitar por WhatsApp → copiar el mensaje recibido y pegarlo en GL →
   emitir → «Copiar mensaje» → enviarlo por WhatsApp al cliente → en SPVI, copiar el mensaje → Licencia → Pegar →
   Activar → «Licencia activada».
