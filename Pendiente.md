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
licencia de PC de 16 dígitos y licencias del contrato GL: 126 pruebas Python, autoprueba y recorrido en navegador sin pantalla
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

**Compilación:** el CI del repositorio ya compila Android y pasa `spviTests` con estos cambios
(`Tests JVM y compilación de instrumentados`, `Lint`, `Release (R8) y APK debug`, `Permisos del manifiesto`
y `API mínima 26`, los cinco en verde). Lo que sigue pendiente:

1. **`data/schemas/cu.spvi.data.db.SpviDatabase/12.json`.** Room lo escribe al compilar
   (`room.schemaLocation`); en este entorno no se puede generar y **no se debe inventar** (su
   `identityHash` lo calcula Room). Basta compilar una vez en la PC del dueño y commitear el archivo:
   `tools/verificacion/` (etapa 3) y `EsquemaTest` avisan si falta o no coincide.
2. **Probar Android en dispositivos:** los 136 tests instrumentados (incluido `EsquemaTest` con la
   migración 11→12), capturas Roborazzi y el APK de depuración en un teléfono; después, la prueba con dos
   teléfonos (principal + secundaria) para ver el sub-token firmado de punta a punta.
3. **`flutter pub get` + `flutter test` + build de iOS** (Codemagic los corre en cada push; en esta máquina
   pub.dev no es alcanzable). `pubspec.yaml` ya lleva `pointycastle: ^3.9.1`.
4. **Prueba de las tres apps contra GL de verdad**: solicitud `SPVIR1` → `SPVI2:` en el teléfono y la app
   iOS, código de 16 dígitos en la PC, clave maestra en un build de depuración (no debe valer en release)
   y comprobar que una secundaria rechaza un permiso manipulado.
5. **Convivencia de versiones:** una principal 0.30 con una secundaria 0.31 (sin `subToken`) debe seguir
   funcionando; la secundaria lo acepta y lo advierte solo en el registro interno.

### Escritorio 0.31.0 (licencias)

Las pruebas nuevas del sub-token y de la clave maestra pasan en Linux (126 pruebas en total). Falta repetirlas en
Windows y con la app GL real, como el resto del escritorio.

### Android 0.31.0 (encargo del propietario, 14 de octubre de 2026)

Diez cambios pedidos para el teléfono, implementados en `app/` **sin poder compilar ni probar en este
entorno** (no hay JDK, Android SDK, Gradle ni Dart: `dl.google.com` y `repo1.maven.org` están
bloqueados). Falta que el CI y la PC del propietario lo verifiquen.

1. **Inicio**: «Turno cerrado» y el selector de período en la misma fila, del mismo alto; menos espacio
   bajo la barra «SPVI»; el primer contenedor (turno + período) queda **fijo al desplazar**.
2. **Inventario**: cada fila reordenada: nombre, cantidad que se puede hacer con su unidad, categoría, y
   a la derecha precio de costo y de venta (diferenciados por color).
3. **Registros**: todas las listas (ventas, transferencias, movimientos, turnos y clientes fijos) con
   **casilla de selección**, como Inventario; barra de selección para exportar solo lo marcado.
4. **Licencia**: fuera el botón de llave «Ya tengo el mensaje de licencia»; se avanza con «Paso siguiente».
5. **Migrar**: pasa a llamarse **Migrar** y la copia de datos se hace por **código QR** entre los dos
   teléfonos (Emisor / Receptor) por la red local; antes hay que definir el **acceso con clave**; al
   terminar, el teléfono emisor **se borra solo**. Se cuenta en el **recorrido inicial**.
6. **Actualizar**: «Actualizaciones» pasa a **Actualizar**; al tocarlo sale un cuadro con **barra de
   progreso circular** y el resultado: «Actualizado», «Estas al día» o «Sin conexión».
7. **Ayuda**: manual por **secciones** (Empezar, Vender y cobrar, Inventario, Tus datos y tu licencia,
   Empleados, Ayuda) con **icono por tema** y **esquemas** dibujados con formas del sistema de diseño.
8. **Soporte**: filas centradas en el orden **Icono, Descripción, Texto**.
9. **Primera apertura**: se piden **todos los permisos necesarios de una vez y sin textos** (diálogo del
   sistema): cámara, notificaciones y fotos.
10. **Animaciones**: deslizar = **desplazamiento lateral** hacia la ventana de destino; barra de
    navegación = **fundido**; ventanas y diálogos = **suben desde el centro de abajo**.

**Pendiente de verificación (igual que el resto):** `./gradlew spviTests`, capturas Roborazzi, APK en un
teléfono y la migración real entre dos teléfonos (emisor + receptor en la misma wifi).

**Capturas Roborazzi:** hay que **regenerarlas todas** (`./gradlew :app:recordRoborazziDebug`): cambia la
interfaz de Inicio, Inventario, Registros, Licencia, Ajustes, Ayuda, Soporte y las animaciones. Además
faltan las tres capturas nuevas de Migrar (`09t_migrar_emisor_receptor`, `09u_migrar_emisor_qr` y
`09v_migrar_sin_clave`), que están declaradas en `AjustesCapturas.kt` pero sin PNG de referencia.

## Android

Conservar los cambios existentes de pago electrónico, exportaciones, Inicio y notificaciones.
Ejecutar pruebas/compilación y revisar visualmente en dispositivos. Ver `Pruebas.md` y
`docs/OPENCODE_DESKTOP.md`. No declarar una APK verificada sin ejecutar el proceso correspondiente.
