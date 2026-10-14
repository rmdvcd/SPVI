# Pendiente de SPVI

## Principal web independiente

Los pendientes funcionales enumerados en la revisión anterior están implementados en `desktop/`:
correcciones, transferencias/clientes, insumos sueltos, metadatos/fotos, idempotencia y validación
anidada, junto con servidor principal LAN, GL y respaldo compartido. No confundir código escrito
con funcionamiento verificado. La web solo opera como principal y no sustituye el móvil.

**Pendiente:** ejecutar en la PC del propietario las pruebas y compilaciones, corregir los fallos
que aparezcan y verificar interacción Windows/navegador/Android/GL. No se han ejecutado en esta fase,
por instrucción del propietario. Seguir `desktop/VERIFICACION_PC.md`; no reutilizar el resultado
histórico de 45 pruebas como evidencia del código actual.

Límites intencionales: sin API nativa Android; administración web solo en loopback; DNS/certificados
configurados explícitamente; fotos web no importadas por Android; ningún respaldo transfiere
activación ni secretos de emparejamiento; no se inventan consumos ausentes en respaldos históricos.
No incorpora impuestos ni contabilidad de partida doble.

### Escritorio 0.31.0

Interfaz nueva, icono de notificación, inicio con Windows, tema, Tiempo en línea, datos de prueba y
licencia de PC de 16 dígitos y licencias del contrato GL: 123 pruebas Python, autoprueba y recorrido en navegador sin pantalla
correctos en Linux. **Pendiente:** probar en Windows (icono, registro `Run`, `.exe` sin consola) y la
licencia de PC de punta a punta con la app GL real en el teléfono (VERIFICACION_PC.md, pasos 19–23).

**GL:** falta publicar en GL `security/SpviPc16.kt` (port de `pc_code.py`), los campos opcionales
`plataforma`, `transaccion` y `recupera` de `SolicitudSpviV2` y su validación en `Spvi23`, el método
`SpviPuerta.emitirCodigoPc` con su implementación en `CryptoGatewayImpl`, la rama PC de
`EmitirSpvi2UseCase` (vence al final del día UTC, `id` = `license_id`), `SpviResponseMessage.buildPc`,
`EnvelopeValidator.licenciaPc` para `.glreg`, las filas Plataforma/Transacción/Recupera del Generador y
`SpviPc16Test` con los vectores de `desktop/tests/test_escritorio.py`. Hasta entonces GL no puede
responder a una solicitud de PC con un código de 16 dígitos.

### Licencias en las tres apps (13 de octubre de 2026)

El escritorio va por la 0.31.0; el teléfono (0.30.0/51 publicado, 0.30.1 en curso) y la app iOS
recibirán estos cambios en su **próxima versión**: el número lo decide el dueño al publicar. Implementado en las
tres apps el contrato de GL con las mitigaciones 1, 7 y 8 (Opción A):
clave maestra solo en depuración, sub-tokens firmados por la principal y AAD canónica del envelope v1.
Detalle: [LICENSE_CLIENT.md](LICENSE_CLIENT.md) §11–§13.

**Pendiente por no haber toolchain en este entorno (ni JDK, ni Gradle, ni Dart):**

1. **Compilar y probar Android.** `./gradlew spviTests`, `./gradlew spviCheck` y `:app:assembleDebug`.
   Sin ejecutar, no se puede declarar la versión verificada.
2. **Commitear `data/schemas/cu.spvi.data.SpviDatabase/12.json`.** Room lo escribe al compilar
   (`room.schemaLocation`); aquí no se puede generar, y **no se debe inventar**. `tools/verificacion/`
   (etapa 3) y `EsquemaTest` avisan si falta o no coincide.
3. **`flutter pub get` + `flutter test` + build de iOS** (Codemagic ya los corre en cada push;
   en esta máquina pub.dev no es alcanzable). `pubspec.yaml` ya lleva `pointycastle: ^3.9.1`.
4. **Prueba de las tres apps contra GL de verdad**: solicitud `SPVIR1` → `SPVI2:` en el teléfono y la app
   iOS, código de 16 dígitos en la PC, clave maestra en un build de depuración (no debe valer en release)
   y verificar que una secundaria rechaza un permiso manipulado.
5. **Convivencia de versiones:** una principal 0.30 con una secundaria 0.31 (sin `subToken`) debe seguir
   funcionando; la secundaria lo acepta y lo advierte solo en el registro interno.

### Escritorio 0.31.0 (licencias)

Las pruebas nuevas del sub-token y de la clave maestra pasan en Linux (123 pruebas). Falta repetirlas en
Windows y con la app GL real, como el resto del escritorio.

## Android

Conservar los cambios existentes de pago electrónico, exportaciones, Inicio y notificaciones.
Ejecutar pruebas/compilación y revisar visualmente en dispositivos. Ver `Pruebas.md` y
`docs/OPENCODE_DESKTOP.md`. No declarar una APK verificada sin ejecutar el proceso correspondiente.
