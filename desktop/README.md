# SPVI de escritorio — principal web independiente

Aplicación web Python que corre en la PC del dueño, **solo como principal**. No usa cámara, permisos,
SMS, servicios ni otras funciones de Android. **No sustituye la aplicación móvil: se despliega de forma independiente.**

> **Comparación con el móvil (14/10/2026):** [INFORME_FEEDBACK_MOVIL.md](INFORME_FEEDBACK_MOVIL.md) lista lo que
> tienen las apps móviles y esta web no, descartando lo que no tiene sentido en una PC (migrar por QR entre
> teléfonos, actualización en caliente, permisos del sistema, biometría) y señalando lo que sí conviene
> llevar aquí. **Las cinco mejoras que proponía están implementadas** (ver la sección 6 del informe):
> selector de período en Inicio, fila de inventario con el costo, casillas de selección en Registros,
> Ayuda y Soporte, y fundido entre secciones con ventanas desde el centro-abajo.

## Novedades de la 0.31.0 (14/10/2026)

- **Inicio:** el estado del turno y el **selector de período** (Turno / Hoy / 7 / 30 / 90 días / Todo) están
  en la misma fila, y esa barra se queda fija al desplazar. Los KPIs y los dos gráficos (formas de cobro y
  ganancia por día) se leen para el período elegido. Registros usa ahora los mismos chips.
- **Inventario:** cada fila muestra a la derecha **costo y venta diferenciados**; en los elaborados se
  añade cuántos se pueden hacer con las existencias actuales de sus insumos.
- **Registros:** casilla de selección en todas las listas y barra con «Marcar todo lo filtrado» (marca todo
  el filtro, no solo la página visible), «Quitar selección» y «Exportar selección».
- **Ayuda y Soporte:** dos secciones nuevas en el menú lateral. Ayuda reúne nueve temas con icono; Soporte
  muestra los datos del desarrollador (teléfono y WhatsApp) en filas centradas.
- **Animaciones:** al cambiar de sección del menú lateral la vista anterior se desvanece y la nueva aparece
  con fundido; las ventanas emergentes entran desde el centro-abajo. Se respeta «reducir movimiento».
- **Exportar:** alcance nuevo **Ventas** (PDF y Excel) y posibilidad de exportar solo lo seleccionado en
  Registros (máximo 1000 elementos).

## Novedades de la 0.31.0 (10/10/2026)

- **Interfaz nueva** con el mismo estilo que el móvil: menú lateral (hamburguesa), tarjetas, listas, diálogos
  centrados con el icono de SPVI, iconos de `SpviIcons` y las fuentes Roboto/Orbitron de la app. Se adapta
  a pantallas anchas (menú fijo), medianas y estrechas (menú desplegable). Tema **Claro/Oscuro/Sistema**
  en Ajustes › Tema. Pantallas: Inicio, Venta, Inventario (e insumos), Servicios, Registros, Apps vinculadas,
  Tiempo en línea, Ajustes, Ayuda y Soporte. Las funciones propias del teléfono (SMS, cámara, huella,
  notificaciones de Android) no están.
- **Icono de notificación** junto al reloj en lugar de la consola. Clic derecho: Pantalla principal, Apps
  conectadas, Tiempo en línea, Ajustes, Reiniciar, Salir. Si SPVI ya está abierto, abrirlo otra vez solo
  muestra la pantalla principal.
- **Clave de acceso opcional:** sin clave la pantalla principal abre directamente (el servidor solo escucha
  en `127.0.0.1`). Se crea, cambia o quita en Ajustes › Clave de acceso (8–256 caracteres).
- **Ajustes › Inicio de SPVI:** iniciar con Windows (valor `SPVI` en `HKCU\…\Run`, sin permisos de administrador),
  retraso de inicio (0, 15, 30, 60, 120 o 300 s) y abrir o no la pantalla principal al iniciar con Windows.
- **Tiempo en línea:** tiempo que lleva abierto SPVI en la PC y la red local, y por cada app vinculada si
  está conectada, desde cuándo, el tiempo conectada en esta sesión y la última sincronización.
- **Licencia de PC por código de 16 dígitos** (ver [LICENSE_CLIENT.md](../LICENSE_CLIENT.md), sección 10):
  la solicitud se envía por WhatsApp con un QR que se escanea con el teléfono, con los datos, la
  plataforma (PC) y el número de transacción del pago. El desarrollador responde con
  `XXXX-XXXX-XXXX-XXXX`, que se escribe en Ajustes › Licencia.
- **Permiso firmado de las apps secundarias** ([LICENSE_CLIENT.md](../LICENSE_CLIENT.md) §12): la PC, como
  principal, anota el `deviceId` de cada app que se vincula (al vincular y en cada saludo de la sesión) y le
  firma su **sub-token** de ranura con `licensing/subtokens.py`; el permiso viaja dentro de la licencia que
  se manda en la instantánea. Sin `deviceId` conocido se manda solo el estado, como antes.
- **Clave maestra de desarrollo** (mitigación 1, Opción A): `GLMK-PERM-MAXS-9999` solo vale con
  `SPVI_DESARROLLO=1` (o `true`/`si`/`on`) en el entorno; en cualquier otro caso la comprobación devuelve
  `false` (fail-closed). Instala una licencia PERPETUA con 10 secundarias, sin pasar por GL.
- **Datos de prueba** «Bodega La Esquina» (18 meses de turnos y ventas, parecidos a los del móvil, no
  idénticos): `SPVI.exe --seed` (borra todo) o, con `--dev`, Ajustes › Datos de prueba.
- La red local se reanuda al abrir SPVI si estaba iniciada.
- Iconos de pestaña, ventana, notificación y `.exe` a partir del logo de la app móvil.

## Implementación actual (pendiente de verificación en PC)

- Puesto Python independiente, exclusivamente principal; no sustituye la aplicación móvil.
- Sesiones, CSRF, catálogo editable, carrito múltiple, turnos y movimientos de caja.
- Modelo canónico compatible con DTO Android, recetas, insumos y preajustes de productos.
- Administración de empleados, permisos, fondos, cuentas de cobro y QR de vinculación LAN.
- Servidor TCP separado del navegador; sesiones cifradas y comandos remotos con deduplicación.
- Cliente de licencias GL, prueba local, activación y carga manual de revocaciones firmadas.
- Respaldo Android `.spvi` v4 (lectura v3/v4) y escritorio `.spvidesk` v2 (lectura v1/v2).
- Cotización del carrito, anulación y corrección atómica de ventas locales o de secundarias en turno abierto.
- Venta directa de insumos en unidades enteras, transferencia con número de operación y clientes fijos editables.
- Editor de categorías, descripciones, caducidad, umbrales, precios, unidades y fotos; restauración de artículos archivados.
- Preajustes editables por selección de productos, perfil, avisos y consulta de movimientos de caja/inventario/turnos.
- Reintentos HTTP idempotentes y validación anidada de DTO, relaciones y fotos.
- PDF/Excel solo de inventario, servicios y turnos; imágenes/tarjetas de productos y servicios.

**Estado real (0.31.0 + licencias):** 129 pruebas Python y la autoprueba pasan en Linux, y la interfaz se recorrió en
un navegador sin pantalla (Chromium) a 1440×900, 800×1000 y 390×844, en claro y oscuro, sin errores. **No
se ha probado en Windows** (icono de notificación, inicio con Windows, `.exe`) ni con teléfonos reales.
Sigue [VERIFICACION_PC.md](VERIFICACION_PC.md) antes de usar datos reales.

**Pendiente de verificación, no de ejecución aquí:** pruebas Python/JavaScript, empaquetado Windows,
validación visual e interoperabilidad real con Android/GL. Los pendientes funcionales enumerados en
la revisión anterior están implementados; esto no equivale a certificar su funcionamiento.
No incluye impuestos, cuentas por cobrar/pagar ni contabilidad de partida doble: no forman parte de
esta ampliación y el margen mostrado sigue siendo bruto, no utilidad neta.

## Ejecutar desde código

Python 3.11 o posterior. En Windows, desde PowerShell:

```powershell
cd desktop
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\pythonw.exe launcher.py
```

SPVI aparece como icono junto al reloj y abre la pantalla principal en `https://spvi.io` (requiere el
certificado y el archivo `hosts` descritos más abajo). `--local` sirve solo en `http://127.0.0.1:8765`
sin HTTPS, útil para desarrollo.
Cerrar la pestaña no detiene SPVI: se cierra con **Salir** en el menú del icono (o en Ajustes).
Abrirlo otra vez con la misma carpeta de datos solo vuelve a mostrar la pantalla principal.
Solo puede haber una pestaña a la vez: si ya hay una abierta, la nueva muestra un aviso con
**Usar aquí** para tomar el control (la anterior queda bloqueada).
`--console` funciona sin icono (se cierra con **Ctrl+C**); `--dev` muestra Ajustes › Datos de prueba.

En Linux/macOS: `.venv/bin/python launcher.py` (el icono necesita un escritorio con bandeja del sistema;
si no lo hay, sigue funcionando como `--console`).

## Portable Windows

```powershell
.\build_windows.ps1
.\dist\SPVI.exe
.\dist\SPVI.exe --browser "C:\Program Files\Mozilla Firefox\firefox.exe"
```

Compilar **en Windows**, no en Linux: PyInstaller no realiza compilación cruzada.
El script ejecuta las pruebas, genera un ejecutable único con Python y recursos incluidos, ejecuta
`SPVI.exe --self-test` y escribe `SPVI.exe.sha256`. No requiere instalar Python
para ejecutarlo. Se genera sin ventana de consola (`--noconsole`) y con el icono de SPVI.
Debe vivir en una carpeta escribible. `--data-dir "D:\Mi negocio\SPVI"` permite elegir los datos.
**El `.exe` no se ha generado ni probado en este entorno Linux.** El workflow manual
`.github/workflows/desktop.yml` prepara un runner Windows y publica el artefacto
`SPVI-Windows-portable` solo si pasa las pruebas. Aún no se ha ejecutado en GitHub con estos cambios.
`--self-test` comprueba recursos, PDF/XLSX/PNG y respaldo sin abrir el navegador ni tocar datos reales.
Los resultados de empaquetado/autoprueba de fases anteriores no verifican el código actual.

## URL `https://spvi.io`

Un ejecutable portable no puede apropiarse de un dominio ni instalar silenciosamente confianza TLS.
Para usar exactamente esta URL, sin advertencias, hacen falta:

1. Resolución local en la PC: `127.0.0.1 spvi.io` en el archivo `hosts` de Windows
   (`C:\Windows\System32\drivers\etc\hosts`), o DNS local equivalente. Es un cambio administrativo manual.
2. Certificado PEM con SAN `spvi.io` y su clave privada. Puede emitirlo una CA local del negocio
   cuya raíz se instale de forma explícita en los almacenes de confianza de Windows y del navegador.
   No basta con renombrar un certificado; no se desactiva la verificación TLS.
3. Puerto 443 libre. Ejecutar:

```powershell
SPVI.exe --cert "D:\SPVI-TLS\cert.pem" --key "D:\SPVI-TLS\key.pem"
```

HTTPS es el modo por defecto: el lanzador exige esta configuración y abre `https://spvi.io`.
El servidor web administrativo **solo escucha en loopback**; no se publica en Internet ni en la LAN.
Los certificados no se empaquetan ni se suben al repositorio. No se exige ser propietario del dominio
para una resolución estrictamente local con CA privada; para certificados públicos o acceso público sí
hay que controlar el dominio. No expongas la administración públicamente.

## Vincular secundarias Android

1. Activa la licencia propia de esta instalación, o utiliza su período de prueba.
2. En **Apps vinculadas › Red local**, elige la IPv4 privada de esta PC y un puerto TCP (por defecto 47811).
   También puedes iniciar con `--lan-host 192.168.1.10 --lan-port 47811`.
3. Autoriza ese puerto en el firewall **solo para redes privadas**. No abrir puertos en el router.
4. Agrega un empleado, asigna permisos y cuentas de cobro, y muestra su QR.
5. Escanéalo desde la secundaria móvil en la misma red. No es un QR de URL: utiliza TCP v1,
   ECDH P-256, HMAC/HKDF y AES-GCM del protocolo Android. Caduca en diez minutos y sirve una vez.
6. Atiende solicitudes de fondo/cierre. Sincroniza ventas pendientes antes de desvincular.

Las confirmaciones se guardan junto con las operaciones. Reenviar el mismo UUID no descuenta dos
veces; cambiar su contenido provoca rechazo. Los cambios de permisos no eliminan ventas offline
ya realizadas. Una corrección se envía al empleado de origen con su relación a la venta anterior.
La instantánea del empleado incluye solo sus cuentas de cobro, no el resto del perfil del dueño.
La red muestra contadores de rechazos sin registrar claves, QR ni datos de clientes.

**Estas rutas están implementadas, pero deben contrastarse con Android real en tu PC.** No se
ha certificado la interoperabilidad ni se ha generado un ejecutable durante esta ampliación.

## Datos y seguridad

`datos/config.json` contiene el hash de la clave (si hay), el secreto de sesión y las preferencias de la PC; `datos/spvi.sqlite3` contiene el
negocio. SQLite en esta primera versión **no está cifrada**, a diferencia de Android. Protege la carpeta
con permisos de Windows y cifrado de disco. La clave web no cifra los archivos. No uses carpetas públicas
ni memorias USB sin cifrar para datos reales. Tras detener la aplicación, copia **toda la carpeta** para
un respaldo manual coherente, conservando también los archivos WAL/SHM si existen. No copiar la base
abierta como método de respaldo. La clave se cambia en Ajustes › Clave de acceso. Si se olvidó, sal de SPVI y
ejecuta `SPVI.exe --reset-password --data-dir "D:\Mi negocio\SPVI"`: quita la clave (después puedes crear
otra en Ajustes), conserva licencia y secretos y deja inválidas las sesiones anteriores.
El lanzador impide abrir dos instancias con la misma carpeta de datos.

## Pruebas

```powershell
$env:PYTHONPATH = "."
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

Las pruebas no necesitan red. Cubren caja, stock, concurrencia, persistencia, dinero decimal,
autenticación, CSRF, validación, Host y cabeceras, y desde la 0.31.0 el código de licencia de 16 dígitos
(con los mismos vectores que GL), preferencias, inicio automático, datos de prueba, Registros, Tiempo en
línea y que cada icono usado por la interfaz exista en `icons.svg`. Falta Windows/TLS y Android LAN.

`static/icons.svg` se genera desde `SpviIcons.kt` de la app móvil con `python tools/generar_iconos.py`;
una prueba falla si queda desactualizado.

## Exportar desde el navegador

En **Exportar**, elige el contenido y formato. Los PDF y Excel contienen informes internos, incluidos
costos. Las imágenes y promociones contienen solo nombre y precio, sin costos ni existencias.
Las tarjetas incluyen la foto local del artículo, si está cargada. El encabezado admite
160 caracteres y se dibuja completo encima de cada tarjeta. Si hay varias tarjetas/páginas, el ZIP
contiene PNG separados (no se genera un collage).

La búsqueda de catálogo se aplica a los informes de inventario/servicios y a imágenes. Turnos exporta
el historial completo, no solo los diez mostrados en Inicio. **Ventas** exporta el historial completo o,
desde Registros, solo lo que tengas seleccionado (máximo 1000 elementos). Máximos: 10000 filas por informe,
300 artículos por exportación de imágenes. Al excederlos, se rechaza la operación, sin truncar datos.
Excel guarda importes numéricos y trata los nombres como texto, nunca como fórmulas.

## Respaldo cifrado de escritorio

En **Respaldo de escritorio**, escribe una contraseña de 12–256 caracteres y descarga `.spvidesk`.
No se incluye la configuración de acceso, contraseñas ni secretos de sesión. Al restaurar:

1. Cierra el turno; la copia de origen también debe tener sus turnos cerrados.
2. Selecciona el archivo y su contraseña, y escribe `RESTAURAR` para confirmar el reemplazo.
3. Antes de borrar datos se validan DTO, relaciones y fotos. La migración del formato v1 usa una base aislada.
4. Se guarda `antes_de_restaurar.spvidesk` en la carpeta de datos, cifrado con **la misma contraseña**
   de la copia que restauras. Conserva esa contraseña para poder recuperar el estado anterior.
5. El reemplazo es una única transacción: un fallo revierte los cambios. La copia automática conserva
   solo el estado anterior a la última restauración; descarga copias adicionales si necesitas historial.

La sección **Intercambio de respaldo con Android** usa `.spvi`; la importación Android solicita una contraseña separada para la copia previa. El cifrado del respaldo no cifra la base activa.

## Preparar HTTPS sin desactivar verificaciones

Con el entorno de desarrollo instalado, ejecuta manualmente:

```powershell
.\.venv\Scripts\python.exe tools\setup_https.py --output tls
```

Genera `spvi-ca.crt`, `spvi.crt` y `spvi.key`; no sobrescribe archivos existentes. La clave privada de la CA
se descarta después de firmar el único certificado. El certificado del servidor dura 365 días. Para renovar,
genera un conjunto nuevo en otra carpeta e instala la nueva raíz de forma explícita.

**Solo después de revisar que son tus archivos locales**, instala la CA para tu usuario Windows:

```powershell
certutil -user -addstore Root .\tls\spvi-ca.crt
```

Esto modifica la confianza del usuario de Windows; algunos navegadores requieren importar la raíz por
separado. No importes certificados de origen desconocido. Configura manualmente el archivo `hosts`
como se indicó arriba y ejecuta:

```powershell
.\dist\SPVI.exe --cert .\tls\spvi.crt --key .\tls\spvi.key
```

La herramienta no modifica DNS, firewall ni almacenes de confianza. No compartas la clave del servidor.
Si retiras la aplicación, puedes quitar la CA local desde el administrador de certificados de Windows.

## Estado de comprobación (2026-10-09, superado por la 0.31.0 arriba)

- Las 45 pruebas y autoprueba correctas pertenecen a la fase anterior, no a esta ampliación.
- Ampliación canónica/LAN/GL/respaldo: código escrito, **sin pruebas ni comprobación sintáctica ejecutadas**.
- Pruebas de regresión nuevas: escritas para ejecutar en PC; sin resultado aún.
- Windows CI/EXE, validación visual y pruebas con Android real: pendientes.
- Guía de verificación y pendientes: [VERIFICACION_PC.md](VERIFICACION_PC.md).

## Estado de comprobación (2026-10-10, PC del dueño, main @ d2f51b4)

- `unittest discover -s tests`: **84 pruebas OK** (Python 3.14.7). Primera ejecución real de la ampliación; sin correcciones.
- `build_windows.ps1`: EXE `dist/SPVI.exe` (29,3 MB + `.sha256`) generado y `--self-test` interno pasado.
- EXE con `--local` y datos temporales: HTTP 200 en `http://127.0.0.1:8765` (acceso, CSRF presente). Temporal borrado.
- Pendientes: navegador real, HTTPS `spvi.io`, LAN con Android real y GL real (ver VERIFICACION_PC.md, puntos 4–10 y 15–18).

## Estado de comprobación (2026-10-11, PC del dueño, main @ 328cdfa, 0.31)

- `unittest discover -s tests`: **103 pruebas OK (1 omitida)**. El venv de la sesión anterior quedó viejo; bastó reinstalar el `requirements.txt` nuevo (ya trae `tzdata`, `pystray`, `six`).
- `build_windows.ps1`: EXE `dist/SPVI.exe` (30,1 MB) regenerado con `--self-test` pasado; `.sha256` reescrito a mano (el script no lo actualizó).
- Aceptación en Chrome real contra el EXE con datos temporales (borrados después): turno (fondo 1000), producto, venta en efectivo, corrección con motivo, anulación con motivo, doble anulación bloqueada, PDF/XLSX válidos, Tiempo en línea y cierre con **Cuadra**. Detalle en `docs/HISTORIAL_DESARROLLO.md` (entrada 2026-10-11).
- Observación menor: tarjeta sin foto provoca 404 en `/api/photo/...` (consola). Pendientes: LAN con Android real, GL real, HTTPS, licencia PC de 16 dígitos y recuperación de clave.

## Flujos añadidos

- **Corregir:** abre Historial, selecciona una venta de turno abierto, modifica su carrito, método y
  transferencia, escribe el motivo y confirma. Se conserva el original anulado y una venta nueva
  que lo referencia. Si falla cualquier validación, no cambia ninguna de las dos ni el stock.
- **Cotización:** el diálogo muestra el total ajustado sin registrar ventas ni consumir insumos.
  Al confirmar se recalcula contra el catálogo y existencias actuales.
- **Insumos:** establece su precio de venta en Editar artículo para venderlos sueltos; vacío significa
  no vender. La cantidad vendida es entera; el stock y las recetas se guardan en milésimas.
- **Transferencia:** registra número de transacción y, opcionalmente, cliente. Marcar Cliente fijo
  exige identidad y teléfono válidos. Las sugerencias no eligen entre clientes homónimos.
- **Fotos:** carga JPEG/PNG/WebP de hasta 5 MiB y 16 megapíxeles. Se normalizan a JPEG de hasta 1600 px,
  sin EXIF, y se guardan en SQLite; límite conjunto de 64 MiB. No se abren rutas o URL del DTO.
- **Respaldo de fotos:** `.spvidesk` v2 conserva las fotos. El campo adicional `webFotos` de `.spvi`
  permite una ida/vuelta entre instalaciones web; Android no importa ni vuelve a exportar ese campo.
  Los URI de fotos Android no contienen la imagen y no son accesibles desde esta PC.
- **Reintentos:** la interfaz conserva una clave de operación en la pestaña mientras no recibe
  confirmación. Reintenta sin cambiar los datos ante una respuesta perdida. El servidor persiste el
  resultado junto con la mutación; rechaza reutilizar una clave con otros datos. Una restauración
  invalida las solicitudes del negocio anterior. No borres el almacenamiento de la pestaña antes de
  resolver una operación cuyo resultado sea incierto.
- **Históricos sin consumos:** no se reconstruye una receta antigua usando la receta actual.
  Al anular se devuelven los movimientos originales disponibles; revisa manualmente el inventario
  si el origen del respaldo no registraba consumos. No se inventa trazabilidad inexistente.
