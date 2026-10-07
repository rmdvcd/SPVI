# Plan — prueba que no se reinicia al reinstalar

Estado: **aplicado en la 0.26.0 (48)** con las decisiones del §4 (petición P74). Las secciones 1 y 2 son el análisis previo.

## 1. Qué ya existe (no se duplica)

- `licencia/LicenseManager.evaluate()`: prueba de 7 días (`TRIAL_DAYS`), `trialStart` en `LicenseStore` (filesDir cifrado),
  retroceso de reloj con `lastSeen` → `LicenseState.ClockTampered`, `Trial(n)` / `TrialExpired`, Inicio y Licencia ya
  bloquean la venta al vencer.
- Manifiesto: `allowBackup="false"`, `fullBackupContent="false"` y `dataExtractionRules` ya están. Release: `isMinifyEnabled`
  e `isShrinkResources` ya están en `true`.
- Proyecto: minSdk **26** / targetSdk **35** (la petición dice 24/34).

Hueco real: `trialStart` vive solo en filesDir → al desinstalar se borra y la prueba empieza de cero.

## 2. Problemas de la especificación tal cual

| # | Punto | Problema | Propuesta |
|---|---|---|---|
| A | Leer la copia de Downloads/Documents tras reinstalar (Android 10+) | Android trata la app reinstalada como otra: **no ve ni abre** los archivos de Downloads/Documents creados por la instalación anterior; para leerlos haría falta el selector de archivos (SAF) o `MANAGE_EXTERNAL_STORAGE`. Afecta a casi todos los teléfonos actuales (Android 11+). | En Android 10+ usar la **colisión de nombre**: al reinstalar, intentar crear `.sys_<hash>.bin` en la misma carpeta; si el sistema lo renombra («(1)») o falla por nombre duplicado, ya hubo una instalación → prueba **vencida** (no se conoce la fecha original). Hay que comprobarlo en dispositivo (Android 10, 11, 13, 14, 15). |
| B | Nombre que empieza por «.» | MediaStore puede no indexar o rechazar nombres ocultos en Android 11+. | Probar en dispositivo; si falla, `sys_<hash>.bin` sin punto. |
| C | `WRITE_EXTERNAL_STORAGE maxSdkVersion=28` | Va contra tu regla «sin permisos de almacenamiento amplio». Con minSdk 26 solo afecta a Android 8–9. | Confírmalo como excepción, o sin él (en 8–9 solo quedaría la copia interna). |
| D | «Descifrado falla → Tampered y bloqueo» | Falsos positivos: un archivo copiado de otro teléfono (Zapya, clonado de teléfono, tarjeta SD), o un restablecimiento de fábrica (cambia `ANDROID_ID`) bloquearían a un cliente legítimo. | `Tampered` solo bloquea la **prueba**; una licencia válida manda siempre. Copias que no descifran se ignoran si hay otra válida. |
| E | Estados nuevos `TrialViewModel` / `TrialState` | Duplicaría `LicenseState` y la pantalla de bloqueo actual. | `TrialViewModel` + `StateFlow<TrialState>` como fachada sobre `LicenseManager`; `TrialState.Expired` = pantalla de Licencia ya existente (Activar = Pegar + Activar, Recuperar). |
| F | `SystemClock.elapsedRealtime()` | Se reinicia al apagar el teléfono; solo sirve dentro del mismo arranque. | Se combina con el `lastSeen` actual (no lo sustituye). |
| G | «Ofuscación» de la sal y los nombres | R8 no cifra cadenas; un XOR o similar solo frena a curiosos. `ANDROID_ID` no es secreto. | Se acepta (es para un «usuario promedio»), y lo documento como protección débil. |
| H | minSdk 24 / target 34 | El proyecto es 26/35. | Mantener 26/35. |
| I | Apps secundarias | No tienen prueba (las cubre la licencia de la principal). | Solo la principal escribe y lee el registro. |

## 3. Diseño aplicado

- **Dónde se guarda** (formato en [FORMATOS.md §2.7](../FORMATOS.md)):
  - `filesDir/.sys_<huella>.bin` (copia interna);
  - `Pictures/SPVI/sys_<huella>.png`: imagen de 48 × 48 con el registro en un fragmento PNG privado. Es la vía principal en Android 10+: una app reinstalada **sí** puede leer sus imágenes antiguas con el permiso de fotos;
  - `.sys_<huella>.bin` en Download y Documents (MediaStore en 10+, `File` en 8–9). Solo se pueden leer tras reinstalar en Android 8–9.
- **Cifrado:** `RegistroPrueba` (JSON `firstInstall`, `trialDays`, `version`, `lastSeen` opcional) → AES/GCM/NoPadding, IV de 12 bytes, etiqueta de 128 bits, blob = IV‖ct; clave PBKDF2WithHmacSHA256 (ANDROID_ID + sal fija ofuscada, 10 000 iteraciones, 256 bits). Cadenas y nombres ofuscados (`Ofuscado`).
- **Al evaluar la licencia** (`LicenseManager.evaluate()`, en `Dispatchers.IO`):
  1. `RegistroExterno.sincronizar`: se leen todas las copias; gana el `firstInstall` más antiguo (también frente al `trialStart` guardado) y el `lastSeen` más reciente; se reescriben las que falten, estén desfasadas o no descifren.
  2. Una copia ilegible **se ignora** (no bloquea).
  3. Reloj: se mantiene `lastSeen` → `ClockTampered` (2 h de tolerancia) y se añade `DetectorRetroceso` (`DetectorRelojAndroid`): guarda el par (hora de pared, `elapsedRealtime`) y, dentro del mismo arranque, detecta si la hora retrocedió más que la tolerancia.
  4. Una licencia activada manda siempre: el registro solo afecta a la prueba.
- **Permiso:** `READ_MEDIA_IMAGES` (13+), `READ_EXTERNAL_STORAGE` (`maxSdkVersion` 32), `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion` 28). `PermisoRegistroPrueba` (diálogo «Guardar tu periodo de prueba» → diálogo del sistema) se muestra **una vez**, recién instalada y durante la prueba. Tras responder se vuelve a evaluar con el permiso nuevo.
- **UI:** `TrialViewModel` con `StateFlow<TrialState>` (`FirstInstall`, `Active(días)`, `Expired`, `Tampered`) como fachada sobre `LicenseState`. La pantalla que bloquea al vencer es la puerta de Licencia que ya existía (`BloqueoGate`), sin duplicarla.
- **Tests JVM:** cifrado de ida y vuelta, otra clave → ilegible, PNG válido con el registro dentro, la más antigua gana, copia corrupta ignorada y reescrita, reinstalación con la prueba vencida → `TrialExpired`, licencia activada manda, retroceso → `ClockTampered` (`RegistroPruebaTest`, `EstadoPruebaTest`, `TrialViewModelTest`). La parte MediaStore no se puede probar sin dispositivo (RELEASE.md, pruebas 18 y 19).

## 4. Decisiones del usuario (P74)

1. **No** se usa la colisión de nombre para dar la prueba por vencida.
2. Funcionar en todas las versiones de Android → «ambos»: imagen en Imágenes/SPVI **y** copias `.bin` en Download/Documents **y** copia interna. Se aceptan los permisos de fotos/almacenamiento solo para esto.
3. Una copia que no descifra **no bloquea**: se ignora.
4. `elapsedRealtime` se combina con la detección de reloj que ya existía.
5. Sin respuesta sobre minSdk/target → se mantiene 26/35.

**Limitaciones aceptadas:**
- Si el usuario niega el permiso de fotos al reinstalar (Android 10+), o borra la imagen de la galería, o restablece el teléfono de fábrica (cambia ANDROID_ID), la prueba vuelve a empezar.
- En una app secundaria el diálogo puede aparecer mientras la licencia de la principal esté en prueba (inofensivo: la secundaria no usa su propia prueba).
