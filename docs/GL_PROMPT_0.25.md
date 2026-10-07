# Prompt para GL — SPVI 0.25.0: recuperación automática de licencias y lista pública de revocadas

Pega el bloque siguiente en opencode (escritorio), dentro del proyecto **GL**. Se aplica **encima** de
`GL_PROMPT_0.23.md` (que sigue vigente: solicitud `SPVIR1:`, respuesta `SPVI2:`, solo texto). No cambia ningún formato
existente; solo añade:
- un campo opcional `recupera` en la solicitud;
- la publicación de un archivo firmado `revocadas.json` en GitHub.

Una GL sin este cambio sigue atendiendo las solicitudes normales de SPVI 0.25.0. Las de recuperación las trataría como
una solicitud nueva (de pago), así que conviene aplicarlo antes de repartir la 0.25.0.

---

```text
Actualiza GL para SPVI 0.25.0. Las claves NO cambian (misma clave ECDH y misma clave de firma gl-sign-v1). Todo lo de
GL_PROMPT_0.23 sigue igual; esto se AÑADE.

1) SOLICITUD DE RECUPERACIÓN (SPVI → GL), solo texto, por WhatsApp o SMS:
      Recuperar licencia SPVI
      Nombre: María Pérez González
      Carné de identidad: 85010112345
      Teléfono: +5352345678
      Licencia anterior: 7c9e6679-7425-40de-944b-e07fc1f90ae7

      SPVIR1:…
   - El código SPVIR1 es el mismo formato de siempre (HKDF "SPVI-R1", AAD "SPVI-R1|"‖epk; los parsers ignoran
     espacios y saltos de línea dentro del código). El payload v2 trae un campo NUEVO, opcional:
        "recupera": "<licenseId en minúsculas>"
     Es excluyente con "renueva" (si vienen los dos: rechazar con «Solicitud no válida»).
   - Lo que cuenta es SIEMPRE lo cifrado; las líneas de arriba son informativas. Si "Licencia anterior" del texto no
     coincide con "recupera" del payload, manda el payload.
   - En una solicitud de recuperación NO se usan "tipo" ni "secundarias" del payload: los decide GL con su registro.

2) COMPROBACIONES AUTOMÁTICAS (sin preguntar al operador), en este orden; si alguna falla, muestra el motivo y NO emite:
   a) el licenseId de "recupera" existe en el historial de GL → si no: «Licencia anterior desconocida»;
   b) no está revocada → si no: «Esa licencia ya se recuperó o se revocó»;
   c) no está vencida (las perpetuas nunca vencen) → si no: «Licencia vencida: hay que comprar una nueva»;
   d) el CI del payload es el mismo que el de la licencia anterior (comparar solo dígitos) → si no: «El carné no
      coincide con el titular de la licencia»;
   e) el deviceId del payload es DISTINTO del de la licencia anterior → si es el mismo: «Es el mismo teléfono: no hace
      falta recuperar (pegue la licencia que ya tiene)».
   Sin límite de recuperaciones por titular (decisión del dueño de SPVI). Cada una queda en el historial.

3) SI TODO CUADRA, en una sola acción:
   a) REVOCA la licencia anterior en el historial (estado REVOCADA, fecha, motivo «Recuperada en <deviceId nuevo>»,
      id de la nueva);
   b) EMITE la nueva licencia SPVI2 (formato de GL_PROMPT_0.23, sin cambios) para el deviceId/devicePub nuevos con:
      - el MISMO tipo;
      - el MISMO venceEn que la anterior (perpetua → perpetua);
      - las MISMAS secundarias;
      - precio 0 CUP;
      - un licenseId NUEVO (UUID v4).
      El texto de la respuesta es el de siempre más una línea «Recuperación de 7c9e6679-…» antes del renglón en
      blanco; precio «0 CUP (recuperación)»;
   c) PUBLICA la lista de revocadas (punto 4);
   d) abre WhatsApp (wa.me/<teléfono del cliente>?text=…) o SMS con la respuesta lista. El operador solo pulsa Enviar.

4) LISTA PÚBLICA DE REVOCADAS («revocadas.json»):
   - Contenido: TODAS las licencias SPVI revocadas (por recuperación o a mano), sin datos personales:
        datos = {"v":1,"emitidaEn":<segundos Unix UTC>,"revocadas":["<32 hex>", …]}
        cada entrada = hex minúsculas de los primeros 16 bytes de SHA-256(UTF-8("SPVI-REV|" + licenseId en minúsculas))
   - Firma: ECDSA P-256 / SHA-256 con la clave gl-sign-v1 sobre los bytes UTF-8 de  "SPVI-REV1|" + datos , donde datos
     es EXACTAMENTE el texto JSON que se guarda en el archivo (no se canonicaliza). Firma en crudo r‖s (64 bytes),
     Base64url sin relleno.
   - Archivo: {"datos":"<datos como TEXTO JSON escapado>","firma":"<Base64url r‖s>"}   (UTF-8, máximo 512 KB)
   - Dónde: recurso «revocadas.json» de la Release con etiqueta «revocaciones» del repositorio público de SPVI en
     GitHub. La URL fija que consulta SPVI es:
        https://github.com/<usuario>/<repositorio>/releases/download/revocaciones/revocadas.json
   - Cómo: API de GitHub con un token de acceso (fine-grained, permiso «Contents: read and write» SOLO sobre ese
     repositorio) guardado en la configuración de GL, nunca en el código ni en los registros:
        1. GET  /repos/{owner}/{repo}/releases/tags/revocaciones   (si 404: POST /repos/{owner}/{repo}/releases con
           tag_name "revocaciones", name "Revocaciones", prerelease false, make_latest "false");
        2. si ya tiene el recurso revocadas.json: DELETE /repos/{owner}/{repo}/releases/assets/{asset_id};
        3. POST https://uploads.github.com/repos/{owner}/{repo}/releases/{id}/assets?name=revocadas.json
           (Content-Type: application/json).
     IMPORTANTE: make_latest "false" para que esa Release no tape a la última versión de la app.
   - Sin token o sin conexión: guarda «publicación pendiente» y reinténtalo al abrir GL y tras cada emisión. Muestra
     en la pantalla principal «Revocaciones sin publicar: N».
   - Añade un botón «Publicar revocaciones ahora» y un «Revocar licencia…» manual (con confirmación y motivo) que
     también la incluya en la lista.

5) HISTORIAL: columna/estado REVOCADA, enlace anterior ↔ nueva y filtro «Recuperaciones».

6) PRUEBAS:
   - Vector de la lista: tools/licencia/vector_0.25.txt (firmado con la clave de PRUEBA de vector_0.23.txt). Con esa
     clave y datos = {"v":1,"emitidaEn":1791000000,"revocadas":["22bc3546ab2eb0ec2a9b68fdf86d474c"]}, la huella de
     7c9e6679-7425-40de-944b-e07fc1f90ae7 debe dar 22bc3546ab2eb0ec2a9b68fdf86d474c y la firma debe verificar.
   - Una lista con otra firma, con una entrada alterada o que no sea JSON debe ser rechazada por GL al releerla.
```

---

## Cómo probar el resultado

1. **Lista de revocadas.** En GL, firma una lista con la clave de prueba y compárala con el vector:
   ```
   python3 tools/licencia/probar_gl.py revocadas firmar 7c9e6679-7425-40de-944b-e07fc1f90ae7
   python3 tools/licencia/probar_gl.py revocadas verificar ARCHIVO_DE_GL --id 7c9e6679-7425-40de-944b-e07fc1f90ae7
   ```
   `verificar` acepta la clave real de GL y la de prueba. Con un archivo bueno imprime «✓ lista válida» y «✓ incluye».
   El test `RecuperacionTest.listaFirmadaPorElScriptDeGlSeAcepta` comprueba que SPVI (Kotlin) acepta lo que firma el
   script (Python).
2. **Solicitud de recuperación.** `docs/GL_SOLICITUD_RECUPERACION_PRUEBA.txt` pide recuperar la licencia real
   `d53a020b…` con el CI `00000000000`:
   - si en GL esa licencia tiene otro carné, GL debe responder **«El carné no coincide…»** y no emitir (prueba del
     rechazo);
   - para probar la emisión, genera una con el carné correcto:
     `python3 tools/licencia/probar_gl.py solicitud --recupera <id>` (cambia `ci` en el script o usa una licencia de
     prueba emitida al carné `00000000000`).
   Verifica la respuesta con `probar_gl.py verificar ARCHIVO --tipo T --secundarias N --vence AAAA-MM-DD` (mismos
   tipo, secundarias y vencimiento que la anterior).
3. **Publicación.** Tras emitir, abre la URL `…/releases/download/revocaciones/revocadas.json` en el navegador: debe
   descargarse el archivo y contener la huella de la licencia anterior.
4. **Prueba real** (dos teléfonos con SPVI 0.25.0 compilada con `-PspviGithubRepo=usuario/repositorio`):
   - teléfono A con licencia → Respaldo → Exportar;
   - teléfono B → importar el respaldo → Licencia → «Recuperar» → enviar por WhatsApp;
   - GL → pegar → Enviar → en B, compartir el mensaje con SPVI → «Licencia activada»;
   - en A: Ajustes → «Buscar actualizaciones» (o esperar a la consulta semanal) → «Licencia transferida», datos
     borrados y diálogo «¿Desinstalar SPVI?».
