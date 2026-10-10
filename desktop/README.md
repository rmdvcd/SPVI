# SPVI de escritorio — principal web independiente

Aplicación web Python que corre en la PC del dueño, **solo como principal**. No usa cámara, permisos,
SMS, servicios ni otras funciones de Android. **No sustituye la aplicación móvil: se despliega de forma independiente.**

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

**Estado real:** el código de esta ampliación no se ha compilado ni probado aquí, por indicación
 del propietario. Los resultados de pruebas de la fase anterior no validan esta ampliación.
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
.\.venv\Scripts\python.exe launcher.py --local
```

El primer inicio solicita una clave de al menos 12 caracteres en la consola. La interfaz se abre en
`http://127.0.0.1:8765`. `--local` es un modo de evaluación explícito; no simula HTTPS.
Cierra con **Ctrl+C** en la consola. Cerrar la pestaña no detiene el proceso.
No ejecutar dos instancias sobre los mismos datos. Si el puerto está ocupado, detén la anterior.

En Linux/macOS: `.venv/bin/python launcher.py --local`.

## Portable Windows

```powershell
.\build_windows.ps1
.\dist\SPVI.exe --local
.\dist\SPVI.exe --local --browser "C:\Program Files\Mozilla Firefox\firefox.exe"
```

Compilar **en Windows**, no en Linux: PyInstaller no realiza compilación cruzada.
El script ejecuta las pruebas, genera un ejecutable único con Python y recursos incluidos, ejecuta
`SPVI.exe --self-test` y escribe `SPVI.exe.sha256`. No requiere instalar Python
para ejecutarlo. Se mantiene la consola para crear la clave y detener el servidor de forma explícita.
Debe vivir en una carpeta escribible. `--data-dir "D:\Mi negocio\SPVI"` permite elegir los datos.
**El `.exe` no se ha generado ni probado en este entorno Linux.** El workflow manual
`.github/workflows/desktop.yml` prepara un runner Windows y publica el artefacto
`SPVI-Windows-portable` solo si pasa las pruebas. Aún no se ha ejecutado en GitHub con estos cambios.
`--self-test` comprueba recursos, PDF/XLSX/PNG y respaldo sin abrir el navegador ni tocar datos reales.
Los resultados de empaquetado/autoprueba de fases anteriores no verifican el código actual.

## URL `https://SPVI.minegocio.cu`

Un ejecutable portable no puede apropiarse de un dominio ni instalar silenciosamente confianza TLS.
Para usar exactamente esta URL, sin advertencias, hacen falta:

1. Resolución local en la PC: `127.0.0.1 spvi.minegocio.cu` en el archivo `hosts` de Windows
   (`C:\Windows\System32\drivers\etc\hosts`), o DNS local equivalente. Es un cambio administrativo manual.
2. Certificado PEM con SAN `spvi.minegocio.cu` y su clave privada. Puede emitirlo una CA local del negocio
   cuya raíz se instale de forma explícita en los almacenes de confianza de Windows y del navegador.
   No basta con renombrar un certificado; no se desactiva la verificación TLS.
3. Puerto 443 libre. Ejecutar:

```powershell
SPVI.exe --cert "D:\SPVI-TLS\cert.pem" --key "D:\SPVI-TLS\key.pem"
```

Sin `--local`, el lanzador exige esta configuración y abre `https://SPVI.minegocio.cu`.
El servidor web administrativo **solo escucha en loopback**; no se publica en Internet ni en la LAN.
Los certificados no se empaquetan ni se suben al repositorio. No se exige ser propietario del dominio
para una resolución estrictamente local con CA privada; para certificados públicos o acceso público sí
hay que controlar el dominio. No expongas la administración públicamente.

## Vincular secundarias Android

1. Activa la licencia propia de esta instalación, o utiliza su período de prueba.
2. En **Red local**, escribe la IPv4 privada de esta PC y un puerto TCP (por defecto 47811).
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

`datos/config.json` contiene el hash de la clave y el secreto de sesión; `datos/spvi.sqlite3` contiene el
negocio. SQLite en esta primera versión **no está cifrada**, a diferencia de Android. Protege la carpeta
con permisos de Windows y cifrado de disco. La clave web no cifra los archivos. No uses carpetas públicas
ni memorias USB sin cifrar para datos reales. Tras detener la aplicación, copia **toda la carpeta** para
un respaldo manual coherente, conservando también los archivos WAL/SHM si existen. No copiar la base
abierta como método de respaldo. Para cambiar o recuperar la clave desde la PC del dueño, detén SPVI y ejecuta
`SPVI.exe --reset-password --data-dir "D:\Mi negocio\SPVI"`. La consola pide la nueva clave dos veces;
no se pasa por argumentos ni por chat. Conserva licencia y secretos y deja inválidas las sesiones anteriores.
El lanzador impide abrir dos instancias con la misma carpeta de datos.

## Pruebas

```powershell
$env:PYTHONPATH = "."
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

Las pruebas no necesitan red. Cubren caja, stock, concurrencia, persistencia, dinero decimal,
autenticación, CSRF, validación, Host y cabeceras. Falta validación visual, Windows/TLS y Android LAN.

## Exportar desde el navegador

En **Exportar**, elige el contenido y formato. Los PDF y Excel contienen informes internos, incluidos
costos. Las imágenes y promociones contienen solo nombre y precio, sin costos ni existencias.
Las tarjetas incluyen la foto local del artículo, si está cargada. El encabezado admite
160 caracteres y se dibuja completo encima de cada tarjeta. Si hay varias tarjetas/páginas, el ZIP
contiene PNG separados (no se genera un collage).

La búsqueda de catálogo se aplica a los informes de inventario/servicios y a imágenes. Turnos exporta
el historial completo, no solo los diez mostrados en Inicio. Máximos: 10000 filas por informe,
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

## Estado de comprobación (2026-10-09)

- Las 45 pruebas y autoprueba correctas pertenecen a la fase anterior, no a esta ampliación.
- Ampliación canónica/LAN/GL/respaldo: código escrito, **sin pruebas ni comprobación sintáctica ejecutadas**.
- Pruebas de regresión nuevas: escritas para ejecutar en PC; sin resultado aún.
- Windows CI/EXE, validación visual y pruebas con Android real: pendientes.
- Guía de verificación y pendientes: [VERIFICACION_PC.md](VERIFICACION_PC.md).

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
