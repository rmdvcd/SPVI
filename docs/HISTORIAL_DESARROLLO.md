# SPVI — Historial de desarrollo (Prompts 1–15)

Registro **histórico** de lo que se implementó en cada fase y de las suposiciones que se tomaron en ese momento, tal como figuraba en el README hasta la versión 0.12.0. Se conserva para trazabilidad.

> **Prevalece la documentación actual.** Si algo de este historial contradice a [README.md](../README.md), [SECURITY.md](../SECURITY.md), [FORMATOS.md](../FORMATOS.md), [LICENSE_CLIENT.md](../LICENSE_CLIENT.md) o [UI_UX_IX.md](../UI_UX_IX.md), vale lo que dicen esos documentos. Las fases posteriores sustituyen a las anteriores.

## Afirmaciones superadas

| Dónde (abajo) | Decía | Estado actual (0.12.0) |
|---|---|---|
| Suposiciones domain/data, 13 | Exportar como imagen/tarjetas es trabajo de la UI (fase 5) | Implementado: Tarjetas (Prompt 8) e Imagen = lista de precios PNG (Prompt 14) |
| Prompt 5, suposición 1 | Todavía no existe ninguna llamada de red | Desde el Prompt 8 el escáner consulta 4 bases públicas, solo con consentimiento |
| Prompt 5, suposición 3 | La cámara real llega en la fase 3 | CameraX + ML Kit desde el Prompt 8 |
| Prompt 6, suposición 5 | «Nueva venta» con el turno cerrado lo abre | Sustituido en el Prompt 7 (ya indicado allí) |
| Prompt 8, suposición 3 | «Tiempo máximo por base = Ajustes (5 s)» y User-Agent `SPVI/0.7` | El tiempo es la preferencia `timeoutRedSegundos` (5 s por defecto); **no hay control en la pantalla Ajustes** para cambiarlo. El User-Agent actual es `SPVI/0.12 (Android; escaner de codigos)` |
| Prompt 8, Exportar | Imagen = tarjetas PNG de 6 productos | Desde el Prompt 14, Imagen = lista de precios y Tarjetas = tarjetas por producto |
| Prompt 11, suposición 6 | PDF/Excel no están en Registros | Añadido en el Prompt 14 (botón Exportar) |
| Prompt 12, suposición 3 | Importar desde WhatsApp: primero guardar el archivo | Desde el Prompt 14 SPVI recibe el `.spvi` con Compartir / Abrir con (ya indicado allí) |

## Suposiciones de la base (Prompts 1 y 2)

1. **Features como paquetes, no como módulos Gradle.** Con un solo desarrollador, 10 módulos de feature solo añaden tiempo de build. Separarlos después es mecánico.
2. **Hilt en vez de Koin:** errores de grafo en compilación e integración oficial con ViewModel y Navigation.
3. **Transiciones con el `NavHost` estándar:** `AnimatedNavHost` (Accompanist) está obsoleto y sus transiciones ya están integradas en Navigation 2.8.
4. **Sin dynamic color:** se prioriza la marca y el AA verificado.
5. **DataStore cifrado con clave propia en Keystore,** en lugar de EncryptedSharedPreferences (obsoleto) o Tink (dependencia extra innecesaria).
6. **Room + SQLCipher** con passphrase de `:security` (incorporados en el Prompt 3).
7. **Bloqueo solo por licencia** (C9). No hay PIN de app en esta fase.
8. **Los casos de uso de `:domain` no generan fábricas Dagger en su módulo:** Dagger las genera en `:app`, así `:domain` no depende de Dagger.

## Suposiciones de las capas domain y data

Criterio: la opción más segura, simple y accesible, documentada.

**Negocio**
1. **Timeout = red.** Es el tiempo de espera del escáner online (3–30 s, 5 por defecto). No es un bloqueo por inactividad.
2. **El turno activo vive en Room,** no en preferencias: así se verifica en la misma transacción que la venta.
3. **Preajustes de precio:** los aplicables a un producto **se suman**. Filtran por método de pago y por importe mínimo (el total antes de ajustes). El precio resultante nunca baja de 0.
4. **Ajuste permanente de precios:** el precio mínimo resultante es 0.01 CUP.
5. **Insumos con `UnidadMedida`** (u, kg, g, L, ml) y cantidades en milésimas, porque las recetas necesitan fracciones. El precio es el costo por unidad de medida.
6. **Elaborado:** al guardar se anulan foto, caducidad y código. Su costo se calcula desde la receta y sí tiene precio de venta (C4).
7. **CI del cliente:** 11 dígitos.
8. **Licencia en dominio:** `EstadoLicencia`, `TipoLicencia` y `ViaSolicitud` son alias de los tipos de `:licencia`, para no duplicar el contrato.

**Alertas y estadísticas**
9. **"Próximo a caducar"** incluye también los productos ya vencidos.
10. **Top 3:**
    - Rentabilidad: solo cuenta los productos con ganancia > 0.
    - "Lento": incluye los activos con 0 ventas.
    - Empates: se desempata por `creadoEn`.
11. **Agrupación de gráficos:** por hora hasta 48 h, por día hasta 62 días y por mes a partir de ahí.

**Escáner y exportación**
12. **Extracción de datos del SMS de transferencia:** regex heurística. Hay que ajustarla con muestras reales.
13. **Exportar como imagen/tarjetas** es trabajo de la UI (fase 5). La configuración se exporta **solo en PDF**.

**Persistencia**
14. **Respaldo de configuración:** los preajustes que apunten a productos inexistentes en el dispositivo destino pierden esos productos.
15. **Registros:** la búsqueda de texto es literal (se ignoran los comodines `%` y `_`). *Desde el Prompt 11 la pantalla filtra el texto en el dominio, sin tildes ni mayúsculas (ver «Registros (Prompt 11)»).*
16. **Preferencias en un solo JSON** para que cada escritura sea atómica. Restaurar un respaldo no reinicia el onboarding.

## Suposiciones del cliente de licencias (Prompt 4)

1. **"Flujo definido en Prompt 0"** = D3 + D3.1: solo la clave ECDH es configurable; la de firma queda fijada.
2. **Perpetua** no ofrece renovación; cualquier otra licencia instalada (activa, vencida o revocada) ofrece "Renovar o cambiar".
3. **Sincronización Perfil ↔ Licencia:** el panel lee el Perfil en vivo y no pisa lo que el usuario está editando; el Perfil se actualiza al generar la solicitud (antes de enviarla).
4. **Bloqueo en uso:** la re-evaluación usa el reloj del sistema; un retroceso del reloj bloquea (`ClockTampered`) hasta corregir la fecha.
5. **Revocada** se muestra como "Licencia no válida" (sin detallar el motivo).
6. **Versión 0.3.0** (`versionCode 3`).

## Primera ejecución, onboarding y permisos (Prompt 5)

- **Asistente** (`onboarding/`): Bienvenida → Tus datos → Clave del emisor (solo si no viene fijada en el APK) → Avisos de inventario → Periodo de prueba. Cada paso se puede **omitir** y todo el asistente se puede **saltar**; nada bloquea el uso de la app (solo la licencia bloquea, C9).
- **Retomar desde Ajustes**: la entrada «Completar configuración» muestra cuántos pasos faltan y abre solo los pendientes; «Avisos de inventario» abre directamente ese paso.
- **Permisos contextuales**: la cámara se pide **solo** al abrir el escáner y siempre tras una explicación; si se niega, se puede escribir el código a mano o abrir los ajustes del sistema. Internet se usa solo para consultar un código que no está en el catálogo, y solo con el consentimiento del usuario.

### Suposiciones del Prompt 5

1. **INTERNET es un permiso «normal»** de Android (no se pide en tiempo de ejecución). Para cumplir «solo para consultas de códigos» se pide un **consentimiento dentro de la app** (`ConsentimientoRed`: Preguntar / Permitido / Denegado), que se puede cambiar en Ajustes. Todavía no existe ninguna llamada de red: el punto de enganche es `EscanerViewModel.consultarEnLinea` (fase 3).
2. **«Ahora no»** en el diálogo de consentimiento no guarda la decisión: se volverá a preguntar en la próxima consulta. «Sí, buscar» y «No, nunca» sí se guardan.
3. **La cámara real (CameraX + ML Kit) llega en la fase 3**; en esta versión el escáner ya gestiona el permiso y permite escribir el código.
4. **El progreso del asistente** (`setup.v1`: pasos confirmados y si ya se pidió la cámara, para distinguir «denegado para siempre») se guarda cifrado en `SecureDataStore`, igual que las preferencias, y **no entra en los respaldos** (es estado del dispositivo).
5. **Omitir nunca bloquea.** Si se omiten los avisos, se aplican los valores recomendados **5 (bajo) / 1 (crítico)** tanto para productos como para insumos, pero el paso sigue figurando como pendiente en Ajustes.
6. **Datos mínimos**: todos los campos son opcionales; un campo vacío no es un error. El teléfono se normaliza y se añade sin borrar los que ya había.
7. **Pasos «hechos»**: Datos = perfil no vacío; Clave = hay huella del emisor; Avisos y Prueba = confirmados por el usuario. El paso de prueba solo aparece mientras no hay licencia instalada.
8. **Versión 0.4.0** (`versionCode 4`).

## Pantalla Inicio (Prompt 6)

Orden de arriba abajo (`inicio/`): banner de licencia → turno + **Nueva venta** → alertas → accesos a **Pago electrónico** y **Precios** → selector de período → gráficos → Top 3.

| Bloque | Implementación | Regla |
|---|---|---|
| Banner | `bannerInicio` → `bannerText` de `:licencia` | Prueba en días, Mensual en días, Semestral/Anual en meses; **Perpetua: oculto**. |
| Alertas | `ObservarAlertas` + `alertasVisibles` + `SpviAlertCounter` | Stock bajo (amarillo, 5), Stock crítico (rojo, 1), Insumo bajo (verde claro, 5), Insumo crítico (naranja, 1), Próximo a caducar (púrpura claro). **Un contador en 0 no se muestra.** Al pulsar: Inventario (o Elaboración para insumos) filtrado, con chip «Quitar filtro». |
| Pago electrónico | `pagos/` (hoja en Inicio + pantalla en Ajustes) | Elegir o escribir teléfono y tarjeta/cuenta; los números nuevos se añaden a las listas del Perfil (sin duplicar) y quedan elegidos. Lista editable (alta, edición, borrado, elegir) en Ajustes → «Pago electrónico». |
| Precios | `precios/` | Preajustes: subir/bajar un % a uno o varios productos, opcionalmente solo con un método de pago y/o desde un importe mínimo (envergadura). Pausar, editar y borrar. Vista previa del precio resultante. |
| Turno | Interruptor accesible (`toggleable`, `Role.Switch`) | Abrir es inmediato; **cerrar pide confirmación** y muestra el resumen (ventas y total). |
| Nueva venta | Diálogo **Venta / Elaborado** | Si el turno está cerrado, el diálogo lo avisa y se abre al elegir. |
| Período | Turno · Hoy · 7 días · Este mes · Este año (se conserva al girar) | Afecta a **Ventas** y **Ganancia neta**. |
| Gráficos | `designsystem/component/Charts.kt` (Canvas) | Tarjetas con título centrado, **sin líneas de cuadrícula**, animación corta, descripción accesible. Ventas: barras; Inventario: dona por categorías; Métodos de pago: dona Efectivo/Transferencia; Ganancia neta: área venta vs costo. |
| Top 3 | `ObtenerResumenGeneral` | Más vendido, Lento movimiento, Rentabilidad con los desempates del Prompt Maestro. **Cada lista se oculta si no tiene datos.** |

Estados de carga (`EstadoCarga`): Idle → Cargando → Éxito / Vacío / Error (mensaje genérico + «Reintentar»). Al volver a Inicio se recarga (`ON_RESUME`); al refrescar se conservan los datos anteriores en pantalla en vez de mostrar otra vez el indicador.

### Suposiciones del Prompt 6

1. **Gráficos propios en Canvas en lugar de Vico.** Vico no tiene gráfico de dona y habría que mezclar dos librerías; con Canvas no se añade ninguna dependencia, se controla la ausencia de cuadrícula y la accesibilidad (una descripción textual por gráfico). Se quitó `vico` del catálogo. Paleta de gráficos propia, verificada en `ContrastTest` (≥ 3:1 sobre la superficie, claro y oscuro).
2. **Inventario = stock actual por categorías; Métodos de pago y Top 3 = últimos 30 días.** Son independientes del selector (el Prompt Maestro dice que el selector afecta a Ventas y Ganancia neta).
3. **Período «Turno»** = turno abierto o, si no hay, el último cerrado; sin ningún turno se muestran las ventas de hoy y se avisa.
4. **Más de 6 categorías** en una dona: las 5 mayores + «Otras».
5. ~~«Nueva venta» con el turno cerrado lo abre~~ → **sustituido en el Prompt 7**: ya no se abre implícitamente; la pantalla de venta queda bloqueada con mensaje claro y botón «Abrir turno».
6. **Alertas de insumos → Elaboración**, que es donde viven los insumos; las de productos y caducidad → Inventario.
7. **Pago electrónico**: al dar de alta un número ya existente se avisa «ya está en la lista» en vez de duplicarlo; un campo vacío conserva la selección actual.
8. **Nombre del preajuste opcional**: si no se escribe, se genera (p. ej. «+10 % con Transferencia desde 5,000.00 CUP»). Los preajustes **se aplican al cotizar la venta** (`PlanificadorVenta`), no modifican el precio guardado del producto. Límites: subir hasta 100 %, bajar hasta 90 %.
9. **Venta de «Elaborado»** vende unidades ya producidas en Elaboración (el flujo completo de venta llega con la pantalla Venta).
10. **Portapapeles**: solo se lee al pulsar «Pegar» (nunca en segundo plano ni al abrir una pantalla).
11. **Versión 0.5.0** (`versionCode 5`).

## Turno de venta (Prompt 7)

- **Iniciar / Cerrar turno** desde la tarjeta de Inicio (cerrar pide confirmación). Al cerrar aparece «Turno cerrado: N ventas, total» con la acción **Ver**, que abre el registro del turno.
- **No se vende fuera de turno**, en tres niveles: la UI (`ObservarPermisoVenta`: la pantalla Venta muestra el bloqueo «No hay un turno abierto» con el botón «Abrir turno», y se bloquea al instante si el turno se cierra mientras está abierta), el caso de uso `RegistrarVenta` (`TurnoCerrado`) y la transacción de `:data`.
- **Registro del turno al cerrar**, en una sola transacción: apertura y cierre (hora y usuario), número de ventas, unidades, total, costo, ganancia, **métodos de pago** (número de ventas y total en efectivo y en transferencia), movimientos de inventario (desglose productos / insumos). Las ventas y los movimientos quedan vinculados por `turnoId`, así que el detalle muestra productos vendidos, variación neta de insumos, cada movimiento y cada venta.
- **Persistencia cifrada**: todo va en la base Room + SQLCipher existente (no hay ficheros aparte). Base de datos **v2**: `M_1_2` añade 6 columnas **nulables** a `turno` (`abiertoPor`, `cerradoPor`, `ventasEfectivo`, `ventasTransferencia`, `movimientosProducto`, `movimientosInsumo`). Los turnos antiguos se conservan; su desglose aparece como desconocido y sus movimientos cuentan como «de productos». Los respaldos antiguos siguen importándose (campos nuevos con valor por defecto).
- **Registros → Turnos**: lista con el turno abierto primero y el resto del más reciente al más antiguo. El detalle de un turno abierto es **provisional** (se recalcula al volver a la pantalla); el de uno cerrado es el resumen congelado.
- **Inicio** usa el turno abierto y, si no hay, el **último turno cerrado** (período «Turno» de los gráficos).

**Suposiciones del Prompt 7**

1. **Usuario** = nombre y apellidos del perfil de Ajustes; si está vacío, «Titular del dispositivo» (la app es monousuario, sin cuentas).
2. **No se abre el turno implícitamente**: abrirlo es siempre una acción explícita del usuario (Inicio o botón de la pantalla bloqueada).
3. **Un turno a la vez**; cerrar un turno sin ventas está permitido (queda registrado con cero).
4. **Migración**: `MigrationTestHelper` requiere el JSON de esquema que genera KSP en tu equipo (en el sandbox no puede ejecutarse), así que la migración se validó con una prueba JVM (columnas de `SQL_1_2` = campos nuevos de `TurnoEntity`) y con una simulación en SQLite (tabla v1 con un turno cerrado → `SQL_1_2` → `PRAGMA table_info` igual a la entidad, fila conservada). La prueba instrumentada `TurnoRepositoryInstrumentedTest` cubre el cierre sobre Room real.
5. **Versión 0.6.0** (`versionCode 6`).

## Inventario y escáner (Prompt 8)

- **Tabla cronológica** (más reciente primero) con indicador de color por fila (normal / stock bajo o próximo a caducar / crítico o vencido), descripción del estado para TalkBack, **checkbox** por fila (seleccionar ≠ abrir), **buscador** (nombre, categoría o código, sin tildes ni mayúsculas), **filtro** (estado, tipo, categoría; distintivo con el número de filtros), chip «Quitar filtro» y estados Cargando / Vacío / «Sin resultados» / Error con Reintentar.
- **Selección**: barra «N seleccionados» con Marcar todo (lo visible), Exportar, Eliminar y Quitar selección. Sobrevive a búsquedas y filtros; se poda si un producto desaparece.
- **Agregar**: FAB expandible (Escanear / Escribir) y, con el inventario vacío, una hoja con las dos opciones explicadas.
- **Exportar**: la selección o, sin selección, lo que se ve. PDF y Excel → Compartir o **Guardar en el dispositivo** (selector del sistema, SAF); Imagen → tarjetas PNG de 6 productos (solo foto, nombre, categoría y precio de venta: sin costo ni existencias). Compartir usa el selector del sistema (WhatsApp, Telegram, Bluetooth…) vía `FileProvider` limitado a `cache/compartir/` (se limpia a las 24 h). **Sin permisos de almacenamiento.**
- **Ficha** al tocar una fila: foto, aviso de estado, todos los campos, ganancia por unidad y receta. Acciones **Editar**, **Compartir** (PDF = ficha completa de uso interno; Imagen y Texto = para clientes) y **Eliminar** (con confirmación).
- **Formulario de producto** (crear/editar) con los atributos del Prompt Maestro en su orden: categoría (desplegable editable), foto opcional (selector de fotos del sistema, se copia y re-codifica sin EXIF/GPS), nombre, descripción, **receta** (solo Elaborado; costo calculado en vivo), fecha de caducidad, precio de costo, precio de venta, cantidad, niveles bajo/crítico y código (con botón para escanearlo). **Elaborado oculta foto, caducidad y código.** Validación en tiempo real (tras tocar un campo o intentar guardar), mensajes sin jerga, código duplicado detectado al guardar, fecha **recordada por código** propuesta automáticamente. Guardar en la barra superior y al final del formulario.
- **Escáner** (CameraX + ML Kit con modelo **empaquetado**: funciona sin conexión y no descarga módulos): vista de cámara, Escanear, nombre, imagen, fecha, Guardar, Escanear de nuevo. La cámara **solo existe mientras se escanea**: se detiene al detectar el primer código, al pulsar «Detener», al salir o con la app en segundo plano. Orden de búsqueda: inventario local (Room) → Open Food Facts → Open Beauty Facts → Open Products Facts → UPCitemdb (endpoints exactos del Prompt Maestro; se extrae `product.product_name`/`product.image_url` y `items[0].title`/`items[0].images[0]`). Producto nuevo → «Guardar y completar» abre el formulario prellenado (código, nombre, foto, fecha); producto existente → guarda su fecha de caducidad. Entrada manual siempre disponible.
- **Errores de red** explicados y con Reintentar: sin conexión (corta la cadena), bases que no responden, límite diario de UPCitemdb (no se confunde con «no encontrado»), no encontrado (se escribe el nombre a mano).

**Suposiciones del Prompt 8**

1. **Paquete**: integrado en `cu.spvi.app.escaner` / `cu.spvi.app.producto` / `cu.spvi.app.inventario` (decisión O1). `com.ejemplo.escannerproductos` era un ejemplo; integrarlo evita duplicar tema, Hilt, Room y navegación.
2. **CAMERA solo en uso**: se pide al abrir el escáner con explicación previa (Prompt 5) y la cámara se enlaza solo en la fase «Escaneando». En equipos sin cámara queda la entrada manual.
3. **INTERNET solo para consultas**: solo se consultan **GTIN válidos** (EAN-8/UPC-A/EAN-13/GTIN-14 con dígito de control correcto): un QR o un código interno nunca sale del teléfono. Requiere consentimiento (Preguntar / Sí / Nunca, cambiable en Ajustes). Tiempo máximo por base = Ajustes (5 s). HTTPS obligatorio, sin caché, sin cookies, sin reintentos silenciosos; User-Agent `SPVI/0.7` sin datos del usuario. La foto encontrada se descarga una vez (máx. 5 MB), se re-codifica y Coil solo muestra archivos locales (Coil sin módulo de red).
4. **Telemetría de ML Kit eliminada**: ML Kit fusiona `datatransport` (Google), que añade `ACCESS_NETWORK_STATE` y servicios de envío de métricas. Se eliminan con `tools:node="remove"` en el manifiesto (v2: sin telemetría). Verifica en tu equipo con *Merged Manifest* que solo quedan CAMERA e INTERNET.
5. **Fecha de caducidad por código**: al guardar un producto con código y fecha (o desde el escáner) la fecha queda recordada para ese código y se propone la próxima vez; siempre se puede cambiar.
6. **Tarjetas de imagen** = para clientes: nunca incluyen costo ni existencias. El PDF de la ficha sí (uso interno).
7. **Versión 0.7.0** (`versionCode 7`). Base de datos sin cambios (v2).

## Elaboración: insumos y producción (Prompt 10)

- **Pestaña Insumos**: lista **cronológica** (más reciente primero) con indicador de color (normal / bajo / crítico) y su descripción para TalkBack, **checkbox** por fila (seleccionar ≠ abrir), **buscador** (sin tildes ni mayúsculas), **filtro** (existencias: bajo / crítico; uso: en recetas / sin uso; distintivo con el número de filtros), estados Cargando / Vacío / «Sin resultados» / Error con Reintentar. Cada fila muestra cantidad con su unidad, en cuántas recetas se usa y el precio de costo por unidad.
- **Selección**: barra «N seleccionados» con Marcar todo (lo visible), Exportar, Eliminar y Quitar selección; sobrevive a búsquedas y filtros y se poda si un insumo desaparece.
- **Agregar nuevo insumo**: botón flotante `SpviFab` (nuevo en el sistema de diseño: mismos colores y elevación que el FAB de Inventario).
- **Exportar**: la selección o, sin selección, lo que se ve. PDF y Excel → Compartir o Guardar en el dispositivo (SAF); **Texto** → selector del sistema. Sin permisos de almacenamiento.
- **Ficha** al tocar: nivel, cantidad, precio, valor de las existencias, niveles y **«Usado en»** (Elaborados y cuánto gasta cada unidad). Acciones **Editar**, **Compartir (Texto)** y **Eliminar** (con confirmación).
- **Formulario de insumo** con los atributos del Prompt Maestro: **Nombre, Precio (costo), Cantidad, Nivel bajo (opcional), Nivel crítico (opcional)**, más la unidad de medida (u, kg, g, L, ml). Validación en tiempo real y mensajes sin jerga; hasta 3 decimales en cantidades.
- **Pestaña Elaborados** (integración con la **Receta**): cada producto de categoría «Elaborado», su costo por unidad calculado desde la receta y **cuántas unidades alcanzan** con los insumos actuales. «Producir» abre un diálogo con las unidades y la lista «Se descontará: Harina: 2 de 10 kg…»; si no alcanza lo dice y ofrece el máximo. Tocar el Elaborado (o uno sin receta) abre su formulario para editar la receta; «Nuevo Elaborado» abre el formulario con la categoría ya elegida.

**Modelo producir ≠ vender.** Los insumos se descuentan **al producir**, no al vender: `ProducirElaborado` (caso de uso existente) descuenta cada insumo según la receta, suma las unidades al stock del Elaborado y actualiza su costo, todo en **una transacción** con un movimiento **CONSUMO** por insumo y uno **PRODUCCION** para el Elaborado. La venta descuenta después el stock del Elaborado como cualquier producto. Así el inventario de insumos refleja lo que realmente queda en el almacén, y una venta nunca falla por insumos.

**Movimientos de inventario de insumos**: alta (ALTA), edición de cantidad (AJUSTE «Edición»), consumo al producir (CONSUMO) y baja (BAJA). Cambiar el precio de un insumo **recalcula el costo** de los Elaborados que lo usan.

**Suposiciones del Prompt 10**

1. **Reutilización**: repositorios y casos de uso existentes (`ObservarInsumos`, `GuardarInsumo`, `EliminarInsumo`, `ProducirElaborado`, `ExportarInsumos`, `Validadores.insumo`). Se añadieron solo composiciones de dominio (`ObservarElaboracion`, `ObservarProduccion`, `ObtenerFichaInsumo`, `EliminarInsumos`) y **ningún SQL ni cambio de esquema** (BD v2).
2. **Unidad de medida**: el Prompt Maestro dice «solo» 5 atributos, pero sin unidad «Cantidad 2» es ambigua y la receta no puede calcular. Se muestra como selector con «u» (unidades) por defecto; no es un campo más que rellenar.
3. **Un insumo usado en una receta no se puede eliminar** (lo impide `:data` con `EnUso`): la app dice qué recetas lo usan. En un borrado múltiple se eliminan los demás y se informa.
4. **Pestañas con chips** (`SpviChip`): el sistema de diseño no tiene pestañas y los chips ya cumplen tamaño táctil y contraste AA.
5. **Alertas de Inicio**: «Insumo bajo / crítico» abre Elaboración con el filtro puesto (sustituye a la lista provisional `AlertaListaScreen`, eliminada).
6. **Versión 0.8.0** (`versionCode 8`). Base de datos sin cambios (v2).

## Registros (Prompt 11)

- **Una sola pantalla** (barra inferior → Registros) con cuatro pestañas en chips: **Ventas**, **Transferencias** (recibidas), **Movimientos** (de inventario e insumos) y **Turnos** (el registro del Prompt 7, sin cambios). No se añadieron rutas ni pantallas: la información de cada elemento se abre en una **ventana** (hoja inferior) y el detalle de turno sigue siendo el de siempre.
- **Orden cronológico** en todas las tablas (la más reciente arriba) y línea de resumen: «3 ventas · 650.00 CUP», «1 transferencia · 50.00 CUP», «5 movimientos».
- **Buscador** en las tres tablas, sin tildes ni mayúsculas («cafe» encuentra «Café»). Ventas: artículo, categoría, método de pago, cliente o Nº exacto («12» o «#12»). Transferencias: Nº de transacción, cliente, CI, teléfono o Nº de venta. *Desde 0.16.1 ya no se busca por el número interno de la venta (ver «Prompt 27»).* Movimientos: nombre, nota, tipo («consumo», «producción»…) o «producto» / «insumo».
- **Filtro por fecha** en todas: Todo, Hoy, Últimos 7 días, Este mes o Elegir fechas (calendario; «Hasta» incluye el día completo y cualquiera de las dos puede quedar vacía). **Filtro por importe** (desde / hasta, en CUP) solo en Ventas y Transferencias; en Movimientos la hoja lo explica. Distintivo con el nº de filtros y chip «Quitar filtro». Errores claros: fechas invertidas, importe no válido, mínimo mayor que el máximo.
- **Cada pestaña recuerda su búsqueda y su filtro.** Estados Cargando / Vacío («Aún no hay ventas») / «Sin resultados» con Quitar filtros / Error con Reintentar.
- **Ventana del elemento** al tocar una fila: la venta (artículos, precio por unidad, total, costo, ganancia, turno y, si se pagó por transferencia, nº y cliente), la transferencia (nº, fecha, importe, cliente, CI, teléfono, tarjeta o teléfono de cobro, con el botón **Ver la venta**) y el movimiento (tipo, fecha, producto o insumo, cambio y existencia con su unidad, nota, venta y turno).
- **Compartir solo texto**, en tablas y elementos, con el selector del sistema (WhatsApp, Telegram, SMS…). La tabla comparte **lo que se ve** (con búsqueda y filtro, indicados en el título) y su total.

**Suposiciones del Prompt 11**

1. **Reutilización**: `RegistroRepository` (sus consultas por fecha e importe ya existían), `ObtenerVenta`, `TablasExport` y `TurnosViewModel`. Solo se añadió `ObservarRegistro` (compone el repositorio con los insumos para mostrar su unidad) y `RegistrosFiltro` (reglas puras). **Ningún SQL ni cambio de esquema** (BD v2).
2. **Días en la zona del teléfono** (Cuba): «Hoy» va de las 00:00 a las 00:00 del día siguiente, en hora de La Habana, con horario de verano incluido.
3. **El texto se filtra en el dominio** y no con el LIKE de la BD: así el buscador se comporta igual que en Inventario y Elaboración (sin tildes) y busca en más campos. La fecha y el importe sí se filtran en la BD.
4. **Privacidad al compartir**: el texto de **una venta** es un comprobante para el cliente y **no incluye costo ni ganancia**; la tabla de Ventas sí los incluye (informe para el dueño). En los textos de transferencias el **carné de identidad se muestra solo con sus 3 últimas cifras** (empieza por la fecha de nacimiento); en la ventana de la app se ve completo. La ventana avisa de ambas cosas.
5. **Tope de 300 filas** al compartir una tabla como texto (Android y las apps de mensajería rechazan textos enormes). Si hay más, el texto lo dice y sugiere filtrar por fecha; el total sigue contando todas.
6. **Exportar a PDF / Excel** (SPVI.txt, «Exportaciones») no está en esta pantalla porque el Prompt 11 pide «Compartir solo texto». El caso de uso `ExportarRegistros` ya existe y respeta el mismo filtro: añadirlo es solo UI, si lo quieres.
7. **Etiquetas de movimiento con tilde** («Producción») en un solo sitio del dominio (`Estadisticas.etiqueta`), que usan Registros, el detalle de turno y las exportaciones; antes el PDF/Excel mostraba «Produccion».
8. **Versión 0.9.0** (`versionCode 9`). Base de datos sin cambios (v2).

## Ajustes (Prompt 12)

- **Licencia** (ya existía): estado actual, costos (Mensual 6,000 · Semestral 30,000 · Anual 50,000 · Perpetua 90,000 CUP), solicitud o renovación por **WhatsApp o SMS al 51815604** con el payload v1 + `appName`, y activación pegando la respuesta. Los datos salen del Perfil; si está vacío se escriben a mano y se guardan en el Perfil. **Nuevo:** el detalle es seleccionable (mantener pulsado → Copiar), para poder copiar el «ID del dispositivo».
- **Bloqueo total al vencer:** se abre **directamente el panel de Licencia**, al arrancar y también si vence con la app abierta. Desaparece la pantalla intermedia. El aviso de bloqueo ofrece «Contactar soporte». Atrás en Licencia sale de la app; desde Clave o Soporte vuelve a Licencia.
- **Perfil** (antes era un marcador de posición): Nombre, Apellidos y CI con validación (las mismas reglas que GL), y listas de **teléfonos** y de **tarjetas y cuentas bancarias** (agregar, editar, eliminar). Las tarjetas se ven enmascaradas y el número completo solo al editar. Pregunta antes de salir si hay cambios sin guardar.
- **Pago electrónico** y **Precios** (Prompt 6): sin cambios. Las listas de Perfil y de Pago son **las mismas** (mismo ViewModel y misma hoja de edición).
- **Respaldo:** exportar la **Configuración** (Perfil, teléfonos y cuentas, precios, tasas y preferencias) o **Todo**, cifrado con contraseña (mínimo 8 caracteres, repetida). Se puede **guardar en el teléfono** o **enviar a otra app** (WhatsApp, Telegram, Bluetooth, Drive…). **Importar** con el selector de archivos del sistema; la contraseña incorrecta deja reintentar.
- **Migrar a otro teléfono**, en 4 pasos:
  1. Solicitud al desarrollador con el ID del teléfono nuevo.
  2. Respaldo completo.
  3. **Autorización:** pegar la licencia que el desarrollador emitió al teléfono nuevo.
  4. **Borrar este teléfono.** Solo se activa con una autorización válida y escribiendo BORRAR.
- **Ayuda:** manual breve de 10 temas plegables (primeros pasos, productos, vender, transferencias, elaboración, registros, copia, licencia, cambio de teléfono, soporte), un paso por línea.
- **Soporte:** ficha con **Ing. Ronnie Montero Duarte · CI 91040922502 · Teléfono 51815604 · Desarrollo de sistemas y aplicaciones multiplataforma**, botones de WhatsApp y SMS, y la versión de la app.

**Suposiciones del Prompt 12**

1. **Reutilización:** Licencia, Pago electrónico, Precios, el respaldo cifrado (`RespaldoRepository`, PBKDF2 + AES-GCM) y los casos de uso del Perfil ya existían. Perfil reutiliza `PagoElectronicoViewModel` y sus listas en vez de duplicarlas.
2. **Migrar** no existe en el contrato GL v1 (ver `DECISIONES` §O5, v1.5):
   - **La autorización es la licencia del teléfono nuevo.** El teléfono viejo verifica la **firma de GL sin descifrarla** y comprueba que **no** es suya.
   - **Orden: ceder la licencia → borrar → re-evaluar**, sin posibilidad de cancelarse a medias.
   - **Qué se borra:** toda la BD, preferencias, fotos y temporales.
   - **Después:** el teléfono queda bloqueado en Licencia, sin prueba, y rechaza la licencia vieja (el mensaje que siga en WhatsApp). Acepta una licencia emitida **después**.
   - **Contrato GL sin cambios.** La solicitud de migración lleva IDs y nombre, **sin CI**.
3. **Respaldo:**
   - Por defecto «Configuración», que es lo que pide el Prompt. «Todo» existe porque Migrar lo necesita.
   - **Sin permisos de almacenamiento** (SAF y FileProvider). El archivo `.spvi` enviado por otra app queda en `cacheDir/compartir`, cifrado, y se borra a las 24 h.
   - **Importar desde WhatsApp/Telegram:** primero se guarda el archivo y luego se elige. *(Superado en el Prompt 14: ahora SPVI también recibe el `.spvi` desde «Compartir» o «Abrir con»; ver abajo.)*
4. **Sin datos sensibles en vistas recientes:** `FLAG_SECURE` por pantalla en Perfil, Pago electrónico, Licencia, Respaldo, Migrar y, **nuevo**, en Registros mientras se ven transferencias (nombre y carné del cliente). Ayuda y Soporte no muestran datos del usuario.
5. **Sin datos sensibles en logs:** la única llamada de log de la app es un aviso genérico de cámara, y R8 elimina todas en release. El código nuevo no registra nada. Las contraseñas llegan como `CharArray` al caso de uso, que las borra. Viven como `String` solo en el estado de la pantalla, porque el TextField lo exige, y se vacían al terminar.
6. **Ayuda y Soporte no tienen ViewModel:** no tienen estado de negocio, son texto fijo más intents. Su contenido es puro y está testeado (`AyudaContenido`, `SoporteInfo`). Los datos de Soporte vienen de `DeveloperContact`, la misma fuente que Licencia.
7. **Versión 0.10.0** (`versionCode 10`). BD sin cambios (v2). Nueva clave cifrada `lic.migrated_at` en el DataStore de licencia.

## Venta (Prompt 13)

- **Inicio → Vender** abre el diálogo **Venta / Elaborado** (ya existía). Las dos opciones llevan al mismo flujo; cambia el filtro inicial de la selección (Artículos o Elaborados), que el usuario puede cambiar.
- **Selección:** se abre **Inventario en modo venta** (la misma pantalla, sin duplicarla), con casillas, búsqueda y filtros. Los artículos sin existencias aparecen como «Sin existencias: no se puede vender» y no se pueden marcar; los Elaborados sí, porque se elaboran al vender. «Continuar con n» vuelve a la venta. La selección sobrevive a búsquedas y filtros. La barra inferior se oculta durante la venta.
- **Carrito:** los productos elegidos, con **– / +** (tope: existencias o lo producible), «Agregar o quitar productos» y la elección de **Efectivo o Transferencia**.
- **Efectivo → comprobante:** productos, cantidades, importe de cada línea (con preajustes de precio aplicados) y total, con **Confirmar** y **Cancelar**. Si hay producción al vuelo, se indica «Se elaborarán n productos con tus insumos al confirmar».
- **Transferencia → QR:**
  1. QR con los datos de **Pago electrónico** (tarjeta y móvil) y, debajo, el **total en grande**.
  2. Botones «Abrir Transfermóvil» / «Abrir Enzona».
  3. «Ya pagó: pedir datos» pide **Nombre y apellidos, CI, Teléfono y Nº de transacción**. El número se captura con **«Pegar SMS»** o **compartiendo el SMS de PAGOxMOVIL a SPVI** (menú Compartir del sistema). Si el SMS trae importe y no coincide con el total, se avisa sin bloquear.
  4. La venta se registra **solo al confirmar**.
- **Registro atómico** (una transacción Room): venta, detalle, movimientos de inventario **VENTA**, producción al vuelo de Elaborados (**CONSUMO** de insumos + **PRODUCCION**) y datos del cliente si es transferencia. Precios y stock se **recalculan al confirmar** por si cambiaron desde el comprobante.
- **Turno:** sin turno abierto no se vende. Se ve el bloqueo con «Abrir turno», también si el turno se cierra con el comprobante en pantalla. Al terminar se vuelve a Inicio (gráficos al día) con el aviso «Venta registrada: 1,450.00 CUP en efectivo.».
- **ViewModel** con `StateFlow` (pasos CARRITO → COMPROBANTE / QR → CLIENTE), carrito y paso sobreviven a la muerte del proceso (`SavedStateHandle`). Eventos de un solo uso por `Channel`.

**Suposiciones del Prompt 13**

1. **SMS sin permisos (C2):** ni `READ_SMS` ni SMS User Consent API. El portapapeles se lee **solo al tocar «Pegar SMS»**. SPVI recibe texto compartido (`ACTION_SEND text/plain`, `singleTask`) y, si hay una venta esperando el número, lo rellena. Si no lo reconoce, el usuario lo teclea. Formatos reconocidos: «Nro. Transaccion:» y «No. Transaccion:»; si se pega la conversación entera se toma la **última**.
2. **QR (C3, cerrado):** formato **verificado** con el QR real de Transfermóvil: `TRANSFERMOVIL_ETECSA,TRANSFERENCIA,<tarjeta>,<móvil>,`. **El QR oficial no lleva importe**, así que SPVI no inventa un campo: el cliente teclea el total que se muestra bajo el QR. Enzona no publica su formato, solo se ofrece abrirla (`cu.xetid.apk.enzona`). El QR se genera en el dispositivo con **ZXing core**, sin red. Si no hay tarjeta en Pago electrónico se ofrece configurarla.
3. **Elaborado sin existencias:** se **produce al vuelo** en la misma transacción, consumiendo insumos según la receta. Si ni así alcanza, se informa qué falta.
4. **Abrir Transfermóvil/Enzona:** `getLaunchIntentForPackage` con `<queries>` en el manifiesto (Android 11+), sin permisos. Si no está instalada, se avisa.
5. **Sin permisos nuevos:** siguen CAMERA e INTERNET. `FLAG_SECURE` en el paso de datos del cliente (nombre, CI, teléfono).
6. **Versión 0.11.0** (`versionCode 11`). BD sin cambios (v2): las tablas de venta, cliente y movimientos ya existían.

## Exportación e importación (Prompt 14)

- **Base de datos (Ajustes → Respaldo):** «Configuración» o «Todo», cifrado con contraseña. **Guardar en el teléfono** usa el selector del sistema (SAF), donde también aparecen **Google Drive, OneDrive** y cualquier otro proveedor de documentos instalado. **Enviar** usa el menú Compartir (FileProvider): **WhatsApp, Telegram, Zapya, Bluetooth**, correo…
- **Importar**, de tres maneras:
  1. «Importar» → selector de archivos (almacenamiento, Drive, OneDrive…).
  2. **Compartir** el `.spvi` a SPVI desde WhatsApp, Telegram o Zapya.
  3. **Abrir con → SPVI** desde un gestor de archivos o la descarga de Bluetooth.

  En los casos 2 y 3, SPVI copia el archivo a su caché, abre Respaldo y muestra el diálogo de importación.
- **Antes de pedir la contraseña, se comprueba el archivo:** qué es («Respaldo completo del 29/09/2026»), y si llegó completo e intacto. El aviso dice exactamente qué se reemplazará (todo, o solo la configuración). Si el archivo está **cortado** (envío interrumpido) o **dañado**, o no es de SPVI, se explica sin pedir la contraseña. Tus datos no se tocan.
- **Progreso visible**, paso a paso:
  - Exportar: «Paso 1 de 3 · Reuniendo los datos…» → «Cifrando con tu contraseña…» → «Guardando el archivo…».
  - Importar: leer → comprobar que esté completo → comprobar la contraseña → importar.

  Mientras dura, las demás acciones quedan deshabilitadas.
- **Confirmación:** un diálogo final al terminar. Si sale bien, dice qué se guardó o importó (nombre del archivo y conteos). Si falla, explica qué pasó y qué hacer. La contraseña incorrecta deja el diálogo abierto para reintentar.
- **Perfil, preajustes de precios y tasas de cambio → PDF** (tarjeta «Documento PDF» en Respaldo): guardar o enviar. La tarjeta avisa de que el PDF no lleva contraseña.
- **Registros → Ventas, Transferencias y Movimientos → PDF y Excel:** botón **Exportar** en la barra superior. Se exporta **lo que se ve**, con la búsqueda y el filtro aplicados (se indican en el título). Cada formato se puede **enviar** o **guardar**. Nombres sin datos personales: `SPVI_ventas_2026-09-30.pdf`. En Transferencias se avisa de que incluye datos de clientes.
- **Inventario → PDF, Excel, Imagen y Tarjetas promocionales** (selección o todo):
  - **Imagen:** una **lista de precios** en PNG (categoría, producto y precio), 25 filas por imagen, numeradas si hay varias.
  - **Tarjetas:** una por producto, con foto, nombre y precio (como antes).
- **Registros → compartir como texto** (sin cambios): el «Compartir» de cada tabla y de cada ficha sigue enviando solo texto.

**Suposiciones del Prompt 14**

1. **Sin permisos de almacenamiento:** ni `READ/WRITE_EXTERNAL_STORAGE` ni `MANAGE_EXTERNAL_STORAGE`. Todo pasa por SAF (el usuario elige el destino o el archivo) y por FileProvider (URI temporal de solo lectura para la app elegida). Siguen solo CAMERA e INTERNET.
2. **Drive, OneDrive y Bluetooth no se integran por su API:** se usan por el sistema (proveedor de documentos o destino de Compartir). Sin SDK, sin cuentas y sin red propia. Si la app no está instalada, simplemente no aparece.
3. **Recibir archivos:** dos `intent-filter` (`SEND` y `VIEW` con `content://`, tipo `application/octet-stream`, el tipo genérico con el que se envía un archivo de extensión desconocida como `.spvi`). La etiqueta es «Importar respaldo en SPVI». El archivo recibido:
   - se copia **acotado a 128 MB** a `cacheDir/compartir` (se borra a las 24 h);
   - se acepta solo por `content://`, nunca rutas `file://` ajenas;
   - **no se importa solo**: siempre hace falta la contraseña y la confirmación.

   Si no es un respaldo, se explica y no pasa nada más.
4. **Formato de respaldo v2:**
   - Cabecera con tipo, fecha, largo y SHA-256 del cifrado. Así se distingue «cortado / dañado» de «contraseña incorrecta», y se sabe qué contiene **sin** contraseña. Tipo y fecha no son datos personales.
   - La cabecera va autenticada en el AES-GCM: si se altera, falla.
   - **Los respaldos v1 (Prompt 12) se siguen importando.** En ellos «contraseña incorrecta o archivo alterado» no se puede separar, y así se dice.
5. **Importación atómica:** se descifra y se valida todo **antes** de escribir nada, y la escritura de la BD va en una sola transacción de Room. Cualquier error al leer, descifrar o validar deja los datos como estaban.
6. **Exportar Registros usa el carné completo** en PDF y Excel (es el registro del negocio). **El texto compartido lo sigue enmascarando** (••••••••345), porque suele terminar en un chat.
7. **«Imagen» del Inventario = lista de precios para clientes:** sin costo ni existencias. En el Prompt 8, Imagen y Tarjetas eran lo mismo; ahora cada una tiene su formato.
8. **Imagen y Tarjetas solo se envían, no se guardan:** pueden ser varios archivos, y «Guardar como» del sistema crea uno solo. PDF y Excel se pueden enviar y guardar.
9. **Versión 0.12.0** (`versionCode 12`). BD sin cambios (v2).

## Tests (Prompt 15)

Toda la suite se ejecuta **sin red**. Los tests JVM no necesitan dispositivo; los instrumentados usan Room **en memoria** (nunca la base real ni SQLCipher) y fakes en los bordes del dispositivo.

### Cómo ejecutarlos

| Qué | Terminal | Android Studio |
|---|---|---|
| Todos los JVM (core, licencia, domain, data, designsystem, app) | `./gradlew spviTests` | Gradle ▸ spvi ▸ Tasks ▸ verification ▸ `spviTests`, o clic derecho en `src/test` ▸ *Run Tests* |
| Un módulo JVM | `./gradlew :domain:test` · `./gradlew :app:testDebugUnitTest` | Clic derecho en la carpeta `src/test/kotlin` del módulo ▸ *Run* |
| Una clase o un test | `./gradlew :domain:test --tests "cu.spvi.domain.CasosBordeTest"` | Icono ▶ junto a la clase o al método |
| Todos los instrumentados | `./gradlew spviInstrumentedTests` (emulador o teléfono conectado, API 26+) | Clic derecho en `src/androidTest` ▸ *Run Tests* con el dispositivo elegido |
| Solo los flujos de integración | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=cu.spvi.app.integracion` | Clic derecho en el paquete `integracion` ▸ *Run* |

Los informes quedan en `<módulo>/build/reports/tests/` (JVM) y `<módulo>/build/reports/androidTests/connected/` (instrumentados).

**Sin red:** en la raíz del build, cada tarea `Test` JVM manda el tráfico HTTP/HTTPS a un proxy inexistente (`127.0.0.1:9`). Solo se permite el bucle local (`localhost`, `127.*`, `[::1]` y el nombre canónico de `localhost`), que es lo que usa `MockWebServer` en `EscanerRedTest`. Un test que intentara salir a internet falla al momento, en vez de pasar por suerte. Los instrumentados tampoco abren sockets: las bases públicas de productos son `FuenteMem`, en memoria. El dispositivo puede estar en modo avión.

**Huso horario:** los tests JVM fijan `user.timezone=UTC`, el huso con el que se verificó la suite. Los tests que dependen de La Habana lo indican de forma explícita (`ZoneId.of("America/Havana")`).

**Animaciones:** `testOptions.animationsDisabled = true` en `:app` y `:data`. Los tests de Compose no esperan transiciones.

### Qué cubre cada capa

| Capa | Dónde | Qué prueba |
|---|---|---|
| Dominio y casos de uso | `domain/src/test` | Venta (cotizar, registrar, producción al vuelo), turno, inventario, alertas, Top 3 y gráficos, precios, escáner, respaldo, licencia, onboarding, perfil |
| Cifrado GL y licencia | `licencia/src/test` | ECIES P-256/AES-256-GCM, firma ECDSA, AAD, claves fijadas, vectores dorados, estados (prueba, activa, vencida, perpetua), reloj atrasado, migración |
| Validación y formato monetario | `core/src/test` | `Validators`, `Cup`/`Money` («1,450.00 CUP»), `Cantidad` |
| Repositorios (JVM) | `data/src/test` | Mapeos entidad↔dominio, DTO, códecs, `BackupCipher`, exportadores, red del escáner con `MockWebServer` |
| Repositorios con BD en memoria | `data/src/androidTest` | `RepositoriosRoomTest` (inventario, recetas, producir, venta en Efectivo y por Transferencia, todo o nada, producción al vuelo, Registros, Precios), `RespaldoRoomTest` (ida y vuelta; archivo cortado, alterado o con otra contraseña no toca los datos), `TurnoRepositoryInstrumentedTest`, `LicenciaInstrumentedTest` (Keystore real) |
| ViewModels | `app/src/test` | Todas las pantallas con estado: Inicio, Venta, Inventario, Producto, Escáner, Elaboración, Registros, Turno, Precios, Pago electrónico, Perfil, Ajustes, Licencia, Migrar, Onboarding, Respaldo, raíz y entrada compartida. Ayuda y Soporte no tienen ViewModel (ver Ajustes) |
| UI Compose | `app/src/androidTest` | Pantallas sin estado (`*Content`) con datos fijos |
| Integración | `app/src/androidTest/.../integracion` | Pantalla real + ViewModel real + casos de uso + Room en memoria: `FlujoVentaTest` (Efectivo, Transferencia y sin turno), `FlujoTurnoTest` (apertura y cierre desde Inicio), `FlujoEscanerTest`, `FlujoRespaldoTest`, `FlujoLicenciaTest` (bloqueo → activar → Inicio) |

En la integración solo se simulan los bordes que dependen del dispositivo o de terceros (`EntornoIntegracion`): Keystore y DataStore (perfil, preferencias, licencia), cámara (se escribe el código a mano, el mismo camino que un código leído), el selector de archivos del sistema (se entrega la URI al ViewModel, igual que el callback del launcher) y las bases públicas de productos. `MainScaffold` necesita Hilt, así que `FlujoLicenciaTest` reproduce la puerta de `SpviRoot` con `RootViewModel` y un marcador en lugar de Inicio.

### Casos borde

| Caso | Tests |
|---|---|
| Sin turno abierto | `CasosBordeTest` (Efectivo y Transferencia rechazadas sin tocar el stock; cerrar sin turno; turno cerrado entre el comprobante y la confirmación), `TurnoUseCasesTest`, `VentaFlujoTest`, `VentaViewModelTest`, `RepositoriosRoomTest`, `TurnoRepositoryInstrumentedTest`, `TurnoUiTest`, `FlujoVentaTest` |
| Licencia vencida | `CasosBordeLicenciaTest` (mensual vencida bloquea y una renovación la reactiva), `CasosBordeTest` (revisión ≤ 15 min), `LicenseFlowTest`, `RootStateTest`, `RootViewModelTest`, `LicenciaUseCasesTest`, `BannerTest`, `LicenciaUiTest`, `FlujoLicenciaTest` |
| Código no encontrado | `CasosBordeTest` (ni local ni en ninguna base; código interno o con dígito de control erróneo no sale a internet; nombre en blanco), `EscanerInventarioTest`, `EscanerViewModelTest`, `EscanerRedTest`, `FlujoEscanerTest` |
| Archivo corrupto | `CasosBordeTest` (el error se propaga y la contraseña se borra igual), `BackupCipherTest`, `RespaldoTest`, `RespaldoRoomTest`, `FlujoRespaldoTest` |
| Empate en el Top 3 | `CasosBordeTest` (empate total determinista; la ganancia empatada la decide el margen; más de 3 empatados; eliminados fuera de «lento»), `StockEstadisticasTest`, `TurnoUseCasesTest` |
| Licencia perpetua | `CasosBordeLicenciaTest` (sigue desbloqueada 20 años después), `CasosBordeTest`, `LicenseFlowTest`, `RuntimeVerificationTest`, `LicenciaFormTest`, `BannerTest`, `FlujoLicenciaTest` |
| `devicePub` faltante | `CasosBordeLicenciaTest`: una solicitud sin `devicePub` hace que GL cifre hacia su propia clave. SPVI la rechaza, no guarda nada y sigue en prueba. La solicitud de SPVI nunca la omite. También `LicenseFlowTest`, `RuntimeVerificationTest` y `LicenciaInstrumentedTest` |

### Suposiciones

1. **Licencia simulada en la integración de `:app`.** `LicMem` acepta un texto fijo. La criptografía GL real se prueba en `:licencia`, y el Keystore en `:data` (`LicenciaInstrumentedTest`). Así los flujos de UI no dependen de claves del dispositivo.
2. **Sin Hilt en los tests instrumentados.** Las pantallas reciben el ViewModel por parámetro (`viewModel = …`). Es más simple que `HiltTestRunner` y no cambia el código de producción.
3. **`FakeGl.issue` sigue el contrato también sin `devicePub`:** cifra hacia el ECDH de GL, como hace GL de verdad.

## Optimización (Prompt 17) — 0.13.0

Plan completo y decisiones en `PLAN_OPTIMIZACION_P17.md` (§9; retirado en la 0.18.0). Sin clientes instalados ni repositorio remoto, así que se eliminó la compatibilidad con versiones de desarrollo.

| Fase | Qué se hizo |
|---|---|
| A. Limpieza | 20 casos de uso sin uso borrados; fuera `TextoWriter`, `sqlite-ktx` y `compose-animation`; OkHttp directo en lugar de Retrofit; iconos propios (trazados de Material Symbols, Apache 2.0) en lugar de `material-icons-extended`, con `SpviIconsTest`; días de aviso de caducidad (7) y tiempo de espera de red (5 s) pasan a constantes |
| B. Funciones retiradas | *Clave del emisor* (`GlKeyPolicy`, `GlKeyStore`, `ClaveEmisorScreen` y sus tests); botón «Abrir Enzona» y su `<queries>`; caducidad recordada por código; tasas de cambio; respaldo solo «Todo» (formato v3, sin lectura de v1/v2); BD v3 con `fallbackToDestructiveMigrationFrom(1, 2)` y sin `Migraciones.kt` |
| C. Casos de uso que solo reenvían | 20 clases borradas; los ViewModels usan directamente las interfaces de repositorio de `:domain`. Se quedan los que tienen lógica |
| D. Estructura | `:security` se integra en `:data` (`cu.spvi.data.security`), quedan 6 módulos; exportación unificada en `app/common/ExportadorArchivos.kt`; fakes de `:app` compartidos en `app/src/sharedTest` |
| E. Automatización local | `./gradlew spviCheck` (tests JVM + lint con `app/lint-baseline.xml` + APK debug + control de permisos), `EsquemaTest` (instrumentado) y `.github/workflows/ci.yml` preparado e inactivo |
| F. Documentación | 0.13.0 (13) |

**Desviaciones de SPVI.txt (decididas por el usuario):** tasas de cambio (:11), botón Enzona (:6), caducidad por código (:87) y respaldo «Solo configuración» (:132). Detalle en el README.

### Suposiciones del Prompt 17

1. **`data/schemas/cu.spvi.data.db.SpviDatabase/3.json`** lo genera Room (KSP) en la primera compilación en Android Studio; hay que subirlo a git. `EsquemaTest` lo lee desde los assets de `androidTest`.
2. **S6 (fakes compartidos):** se compartieron los 3 fakes que usaban a la vez los tests JVM y los instrumentados; los que solo usa un lado se quedaron donde estaban.
3. **Teléfonos de prueba:** al abrir la 0.13.0, una BD v1 o v2 se recrea vacía. Lo más sencillo es desinstalar y reinstalar.
4. **Baseline de lint vacío:** no se pudo ejecutar lint en este entorno. La primera ejecución de `spviCheck` dirá si hay que regenerarlo con `./gradlew updateLintBaseline`.

## Rediseño UI/UX/IX (Prompt 18) — 0.14.0

Auditoría, plan y especificación por pantalla en `AUDITORIA_UI_UX_IX_P18.md` (hallazgos A01–A24; retirado en la 0.18.0). Sin cambios en lógica de negocio, criptografía, contrato GL, permisos, manifiesto ni arquitectura, y sin dependencias nuevas. El resumen visible para producto está en `UI_UX_IX.md` y la referencia de componentes en `DESIGN_SYSTEM.md`.

| Área | Cambio |
|---|---|
| Design system | `SpviTabs`, `SpviStepper`, `SpviStatusBanner` con `BannerTone` (Info/Aviso/Crítico), `SpviHaptics`; `bodyMedium` 14sp y `bodySmall` 13sp; modo letra grande (`letraGrande()`, escala ≥ 1.3); tokens `fabClearance`, `qr`, `fotoMiniatura`, `campoCantidad`, `contentMaxWidth`, `tonalBar`; motion direccional y de listas; `spviAnimateItem()`, `spviContentWidth()`; `SpviTextField` con acción de teclado; `SpviEmptyState` con Ayuda |
| Navegación | Fundido entre pestañas y deslizamiento direccional hacia los detalles; estado leído con `collectAsStateWithLifecycle` |
| Licencia | Asistente de 3 pasos (datos → solicitud → activación) dentro de la misma pantalla; aviso de caducidad en Inicio con tono según los días restantes |
| Respaldo | Exportar en 2 pasos (contraseña → destino) e importar con «Paso n de 3» |
| Formularios | «¿Salir sin guardar?» al volver con cambios (`rememberSalidaProtegida`) en Producto, Insumo y Perfil, y en las hojas de Precios y Pago electrónico; cadena Siguiente/Listo en el teclado |
| Registros y Elaboración | Pestañas accesibles con contador; Registros recuerda pestaña y filtros (`SavedStateHandle` + `FiltrosGuardados`) |
| Venta y Escáner | Vibración de éxito al terminar la venta y al leer un código; `FLAG_SECURE` también en el paso QR |
| Accesibilidad | Pestañas con rol y posición, regiones vivas en asistentes y banners, valor de `SpviListItem` debajo del título con letra grande, probado con escala 2.0 |

Tests nuevos: `BannerTonoTest`, `FiltrosGuardadosTest`, `LicenciaPasosTest`, `RespaldoPasosTest` (JVM, `:app`), `P18ComponentesTest` (JVM, `:designsystem`) y `DisenoP18UiTest` (instrumentado).

### Suposiciones del Prompt 18

1. **Tipografía +1sp** solo en `bodyMedium` y `bodySmall`; los títulos no cambian.
2. **Sin rutas nuevas:** los asistentes de Licencia y Respaldo viven dentro de sus pantallas.
3. **«Deshacer» = confirmación previa** en las acciones destructivas; no se añadió deshacer posterior.
4. **Exportar respaldo en 2 pasos;** el «Hecho» es el diálogo de resultado que ya existía.
5. **Los botones «Cancelar» de las hojas no preguntan:** son una decisión explícita. Solo deslizar, tocar fuera y atrás piden confirmación. El estado «con cambios» de las hojas se pierde al girar el teléfono.
6. **Vibración:** éxito al terminar la venta y en el escáner; un toque breve con cada snackbar. La producción en Elaboración no vibra porque su resultado es un mensaje genérico.
7. **La Ayuda conserva «Abrir turno»** (lo exige `AyudaSoporteTest`).
8. **Recomposiciones:** no hay test automático; ver `UI_UX_IX.md` §4.
9. **Pendiente opcional:** el asistente de Onboarding y Migrar todavía no usan `SpviStepper`.

## Revisión final y empaquetado (Prompt 19) — 0.14.1

Sin funciones nuevas. El informe completo está en `REVISION_FINAL.md` y el procedimiento de release en `RELEASE.md`.

- **Build:**
  - `spviPermisos` revisa debug y release, y admite el permiso propio `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` de androidx.core; sin esa excepción, `spviCheck` habría fallado.
  - Nueva tarea `spviRelease`.
  - `signingConfig` desde `keystore.properties` o variables `SPVI_*`.
  - `gradlew` ejecutable, también en la CI.
  - Secretos de firma en `.gitignore`.
- **Contrato GL:** el `deviceId` se limita a ASCII `a-z0-9` y a 128 caracteres (`DeviceIdTest`). Con un ANDROID_ID real el valor no cambia.
- **Design system:** trazos de `Charts` y `Buttons` pasan a tokens.
- **Copias de Android:** `data_extraction_rules.xml` con `path="."` explícito.
- **Verificación:**
  - 564 tests JVM correctos.
  - 0 avisos del compilador en producción.
  - SQL de Room contra SQLite: 13 tablas, 69 consultas, sin errores.
  - Instrumentados compilados.

### Suposiciones del Prompt 19

1. **Versión 0.14.1 (15)**, no 1.0.0: el release firmado y los instrumentados aún no se han ejecutado en Android Studio.
2. **APK como canal principal** (distribución directa). El AAB se genera solo por si algún día se publica en Google Play.
3. **Sin firma configurada, el release sale sin firmar** con un aviso, en lugar de fallar. Así el build de debug y la CI no necesitan secretos.

## Cierre del plan UI/UX/IX (Prompt 18, respuestas 1-A, 2-opción 2, 3-opción 1, 4-opción 2) — 0.14.2

Sin cambios en dominio, casos de uso, repositorios, criptografía, contrato GL, permisos, manifiesto ni base de datos. Sin dependencias nuevas.

- **A16, validación homogénea:** `SpviTextField` gana `validarAlSalir` y `forzarError` (lógica pura en `SpviValidacion`). Se aplica a Producto, Insumo, Perfil, Pago electrónico (sustituye la heurística por longitud), Precios, Venta→Cliente, Onboarding (datos y niveles), Licencia, Respaldo y Escáner (el «Escribe el nombre» pasa a ayuda, sin rojo antes de escribir).
- **Pregunta 3, Respaldo:** Exportar 1 Contraseña → 2 Destino → 3 «Respaldo listo» (`RespaldoUiState.exportado`, botón «Hacer otro respaldo»); Importar 1 Elegir → 2 Comprobar → 3 Contraseña → 4 Confirmar. El progreso de la operación pasa a llamarse «Etapa N de M».
- **L9:** Onboarding y Migrar usan `SpviStepper` (`pasoMigrar` deriva el paso en curso). `SpviStepper` sin flechas ya no reserva huecos.
- **A07:** «Siguiente»/«Listo» en el paso Datos del Onboarding («Listo» = Siguiente).
- **A18 / A22:** `spviAnimateItem()` en el carrito de Venta y en las listas de Pago electrónico; `spviContentWidth()` en Onboarding, Perfil, Pago electrónico, Escáner, Venta, Migrar y Soporte.
- **Pregunta 4:** sin «Deshacer» (solo confirmación). **Pregunta 2:** tipografía +1sp ya aplicada, ahora con test.
- **Tests:** +10 JVM (`RespaldoPasosTest` 7, `P18ComponentesTest` +5 entre validación y tipografía, `MigrarTest` +2; `RespaldoTest` amplía 2) y +3 Compose (`DisenoP18UiTest` ×2, `AjustesPantallasUiTest.respaldoPaso3HechoYHacerOtro`); `OnboardingUiTest` y `AjustesPantallasUiTest` actualizados.
- **Verificación:** 219 + 54 + 301 tests JVM correctos; 0 avisos del compilador; instrumentados compilados.

## Prompt 24 — cambios de interfaz (botones solo icono, ventanas centradas, filas con un dato) — 0.15.0

Sin cambios en dominio, casos de uso, repositorios, base de datos, criptografía, contrato GL, permisos ni manifiesto. Sin dependencias nuevas: las ilustraciones se convierten a vectores Compose y viajan en el código.

Respuestas aplicadas: texto solo en «Nueva venta»; ilustraciones libres estilo unDraw adaptadas al color del tema; **todas** las hojas inferiores pasan a ventanas centradas; «viñetas» = pestañas, también en Elaboración y en el detalle del turno.

| # | Pedido | Cómo quedó |
|---|---|---|
| 1–2 | «Nueva venta» centrada; Escanear a su derecha | Inicio: «Nueva venta» (único botón con texto) centrada; Escanear sale de la barra superior y va a su lado. |
| 3, 10 | Botones sin texto; ancho según contenido | `SpviPrimaryButton`/`SpviSecondaryButton`/`SpviTextButton` con `icon` obligatorio; circulares de 48dp; texto como TalkBack + tooltip. Ningún botón ocupa todo el ancho (`fillMaxWidth` retirado en Escáner, Migrar, Respaldo, Soporte, Registros, formularios). |
| 4 | Intensidad de las barras de Ventas | `ChartMath.intensidad`: opacidad 0,30 → 1 según el valor respecto al máximo de la escala Y. |
| 5 | Menos texto en tarjetas | Explicaciones de tarjetas, estados vacíos, ayudas de campos y diálogos reducidas a una frase (Inicio, Inventario, Elaboración, Precios, Registros, Turnos, Venta, Licencia, formularios). |
| 6, 16 | Textos alineados y sin partir | Tipografía `.titulo()`/`.cuerpo()`; `SpviTextoAjustable` (reduce la letra antes de cortar) en los importes de las filas y en las celdas de Inicio. |
| 7 | Icono de Precios «$» | `SpviIcons.Precios` = AttachMoney. |
| 8 | Período en combobox | `SpviComboBox` en Inicio. |
| 9, 12 | Ventanas centradas; Confirmar/Cancelar centrados | `SpviBottomSheet` y `SpviDialog` comparten `SpviVentana` centrada; tocar fuera cierra; pie con `SpviButtonRow` centrado (16dp). El selector de fecha también centra sus botones. `SpviDialog` gana `confirmEnabled`/`confirmLoading`/`confirmTag`/`confirmIcon`. |
| 11 | Fitts, Hick, Jakob, Proximidad, Von Restorff | Tabla en DESIGN_SYSTEM.md («Prompt 24»). Ejemplos: botón flotante en Precios, `SpviBarraAcciones` fija en formularios, un FAB por pestaña en Elaboración, botones juntos a lo que afectan. |
| 13 | Imágenes de «Algo salió mal», «Sin resultados», vacíos | 9 ilustraciones unDraw (npm `undraw-svg` 2.0.0, MIT) en `designsystem/.../ilustracion/SpviIlustraciones.kt`; acento = `primary`, grises invertidos en oscuro. |
| 14 | Campos redondeados | `SpviTextField`: 12dp una línea, 16dp multilínea. |
| 15 | Formato de fecha/hora del dispositivo | `Dates.formato` (core) lo fija `FormatoFechaDispositivo` al arrancar; guía del campo de fecha derivada (`FechasUi.guia()`). |
| 17 | Varias tablas → pestañas | Detalle del turno: Resumen · Ventas · Inventario (`PestanaTurno`). Elaboración y Registros ya las tenían. |
| 18 | Botones repetidos | Guardar arriba + abajo → uno solo en `SpviBarraAcciones` (Insumo, Producto); «Agregar» del estado vacío que repetía el «+» (Inventario, Elaboración, Precios); «Ahora no» duplicado en la consulta en línea del Escáner; «Nuevo elaborado» al final de la lista; editar en la fila de Elaborado sin receta. |
| 19 | Filas con el dato principal | Inventario/Insumos: nombre + existencias; subtítulo solo como aviso (Crítico, Bajo, Vence…). Elaborados: nombre + existencias (aviso «Sin receta» / «Sin insumos suficientes»). Registros: nombre/Nº + fecha + importe. Precios: nombre + porcentaje (el resumen queda para TalkBack). Turno: ventas con hora y total. Carrito: precio c/u y existencias solo al llegar al tope o si se elabora al vender. |

- **Tests:** +3 JVM en core (`FormatoFechaTest`), +4 en app/design system (`P24ComponentesTest`, `SpviIlustracionesTest`, `FechasUiTest`, `VentaLogicTest.existenciasSoloComoAviso`); actualizados `ElaboracionLogicTest`, `RegistrosLogicTest`, `RegistrosViewModelTest`, `InicioLogicTest`. Compose: los clics por texto en botones pasan a `onNodeWithContentDescription`; `ElaboracionUiTest` (un FAB por pestaña, confirmar Producir desactivado), `InventarioUiTest` (vacío sin «Agregar» repetido), `TurnoUiTest` (pestaña Ventas). Capturas: +2 (`04r2`, `04r3`).
- **Verificación:** 222 + 54 + 305 tests JVM correctos; instrumentados y capturas compilados; SQL 0 errores.


## Ajustes tras el Prompt 24 — 0.15.1 (18)

| Petición | Cambio |
|---|---|
| «Nueva venta» solo icono, centrada con Escanear y espacio moderado | `InicioScreen.TurnoCard`: pareja centrada con `SpviSpacing.lg` (24dp); se quita el hueco de compensación. `SpviPrimaryButton` pierde `mostrarTexto`. |
| Combobox de Período acorde a la UI | `SpviComboBox` rediseñado como tarjeta tonal (ver DESIGN_SYSTEM.md). |
| Guardar → Confirmar | `SpviIcons.Guardar = Confirmar`. Afecta a Producto, Insumo, Perfil, Pago electrónico, Precios y Escáner. |
| Ventas, transferencias, turnos y movimientos por fecha | `RegistrosLogic` (título = fecha), `TurnoLogic` (`tituloTurno` = fecha, `tituloDetalleTurno`, `horasTurno`) y `TablasExport` (`tituloVenta/Transaccion/Movimiento`; sin «Nº» de venta, «Venta Nº» ni «Turno #» en fichas, textos compartidos y exportaciones). El buscador de Ventas ya no menciona «Nº». |

- **Tests:** se actualizan RegistrosLogicTest, TurnoLogicTest, RegistrosViewModelTest, RegistrosUiTest, RegistrosTest y TablasExportTest.
- **Verificación:** 222 + 54 + 305 tests JVM correctos; instrumentados y capturas compilados; 0 avisos.

## Prompt 26 — Elaborados sin producción previa — 0.16.0 (19)

> «Vamos a redefinir el proceso de elaboración y venta de productos elaborados: estos no se producen previamente en la app; mientras queden insumos suficientes, se venden los productos elaborados sin ningún proceso intermedio.»

Decisiones del usuario: la fila de un Elaborado muestra **«Alcanza para N»** (manda el insumo más escaso; con 0, «Sin insumos suficientes»); **solo los insumos** generan avisos; se **elimina por completo** la acción Producir, y el formulario de un Elaborado ya no pide cantidad ni niveles.

| Capa | Cambio |
|---|---|
| Dominio | `PlanificadorVenta.cotizar` ignora la `cantidad` de un Elaborado y valida contra `alcanza` (unidades que permiten los insumos). Su costo es el de la receta **en ese momento** (`costosReceta`). `Cotizacion.aProducir` → `elaborados` (todas las unidades). `CotizarVenta` lee la receta de **todos** los Elaborados del carrito. `ProduccionEnVenta` → `ElaboradoEnVenta(productoId, nombre, unidades, consumo)`. Se eliminan `ProducirElaborado` y `ProductoRepository.producir`. `Stock.nivel` devuelve NORMAL para un Elaborado: no cuenta en las alertas de stock bajo/crítico. La dona de unidades por categoría excluye los Elaborados. `GuardarProducto` guarda un Elaborado con cantidad 0 y sin niveles; `Validadores.producto` lo exige. Renombres: `ElaboradoProducible` → `ElaboradoDisponible` (`maximo` → `alcanza`), `ObservarProduccion` → `ObservarElaborados`. Nuevo `alcanceElaborados` (flujo productoId → alcanza), que usan `ObservarInventario` (`ItemInventario.alcanza`) y `ObtenerFichaProducto` (`FichaProducto.alcanza`). |
| Datos | `VentaRepositoryImpl.registrar(venta, elaborados)`: dentro de la misma transacción descuenta los insumos (UPDATE condicional, movimientos CONSUMO ligados a la venta con la nota «Venta de X ×N») y **no toca** la existencia del Elaborado ni crea movimientos VENTA/PRODUCCION para él. Si un insumo no alcanza: StockInsuficiente(insumo) y rollback de todo. Se eliminan `producir` del repositorio y `ProductoDao.actualizarCosto`. `TipoMovimiento.PRODUCCION` se conserva solo para leer respaldos y registros antiguos. Sin migración de BD (no hay clientes instalados): la cantidad guardada de un Elaborado se ignora. |
| App | Elaboración: sin botón ni diálogo Producir (`DialogoProducir`, `ProduccionLogic`, sus etiquetas y acciones del ViewModel). La fila muestra `textoAlcance` y tocarla abre la receta. Inventario: fila y ficha con «Alcanza para N». En modo venta un Elaborado solo se puede elegir si alcanza al menos para 1 (motivo «Sin insumos suficientes: no se puede vender.»). Con 0 la fila se pinta en tono de peligro, y el texto acompaña al color. Venta: el tope del carrito de un Elaborado es lo que alcanza (`VentaViewModel` combina productos y `ObservarElaborados`). La línea muestra siempre «Alcanza para N»; desaparecen «Listos…/se elaborarán…» y el aviso del comprobante/QR. Formulario de producto: un Elaborado oculta Cantidad y niveles. Exportar inventario: celda Cantidad vacía para Elaborados; la ficha pone «Alcanza para». Ayuda de Elaboración reescrita. |

- **Tests:** VentaFlujoTest reescrito (cotizar contra lo que alcanza, costo por receta, venta que descuenta solo los insumos, venta mixta, insumo compartido que no alcanza para dos Elaborados → nada se registra, sin turno). Se actualizan ElaboracionTest (alcance reactivo; los Elaborados no generan alertas), UseCasesTest (Elaborado guardado sin existencias ni niveles), EscanerInventarioTest, ValidadoresTest, RepositoriosRoomTest (venta de Elaborado + rollback), ElaboracionLogicTest, ElaboracionViewModelTest, VentaLogicTest (tope por alcance; `SeleccionVenta` con `ItemInventario`), InventarioLogicTest, ProductoFormTest, ElaboracionUiTest e InventarioUiTest. Capturas: −3 (`03l`, `03l2`, `03m`); la lista de Elaborados añade uno «Sin insumos suficientes».
- **Limpieza:** imports sin uso retirados en varios archivos (sin cambio de comportamiento).
- **Verificación:** 224 + 54 + 305 tests JVM correctos; instrumentados y capturas compilados; 0 avisos; SQL 13 tablas / 68 consultas, 0 errores.

## Prompt 27 — sin búsqueda oculta por Nº de venta — 0.16.1 (20)

Desde el Prompt 25 las ventas se identifican por su fecha y el número interno no aparece en pantalla, en el texto compartido ni en las exportaciones. Aun así, el buscador de Registros seguía encontrando una venta si se escribía su número interno («12» o «#12»), y una transferencia si se escribía el número de su venta. Era una búsqueda que el usuario no podía descubrir, y además hacía aparecer resultados que no se explicaban (por ejemplo, «12» mostraba la venta 12 aunque no contuviera nada con «12»).

| Cambio | Dónde |
|---|---|
| `coincide(Venta)` ya no compara el texto con `venta.id` (con o sin `#`) | `domain/.../service/RegistrosFiltro.kt` |
| `coincide(Transaccion)` ya no compara el texto con `ventaId` | ídem |
| Prueba renombrada a `buscadorSinTildesNiMayusculasYSinIdInterno`: «12» y «#12» no encuentran la venta 12; «#7» no encuentra la transferencia de la venta 7 | `domain/src/test/.../RegistrosTest.kt` |

**Se mantiene:** la búsqueda por el **Nº de transacción bancaria** (el del SMS de PAGOxMOVIL), que sí se ve en pantalla y en la ficha. El texto de ayuda de Transferencias («Nº de transacción, cliente, CI o teléfono») ya no mencionaba el número de venta, así que no cambia.

**Textos largos en filas (detectado al preparar las capturas):** «Sin insumos suficientes» no cabía en el 45 % del ancho de la fila ni con la letra reducida a 11 sp, así que en un móvil de 360 dp se habría cortado con «…» (incumple el punto 16 del Prompt 24). `SpviListItem` tiene ahora `valueMaxLines` y `subtitleMaxLines`, los dos a 1 por defecto, así que el resto de la app no cambia. Se usan así: 2 líneas para el valor de las filas de Elaborados (Inventario, modo venta y Elaboración) y para el subtítulo de la línea del carrito («400.00 CUP c/u · Alcanza para 15»); 4 líneas para los valores de las fichas de Inventario y de Registros (receta, descripción, nota, cliente).

**Capturas:** maquetas HTML de las pantallas de los Prompts 26 y 27 en `CAPTURAS_P26.pdf` (retirado en la 0.18.0, sustituido por `CAPTURAS_P29.pdf`), con el mismo método que en el Prompt 24 (no son capturas de un dispositivo; ver L11/L12 en `REVISION_FINAL.md`).

## Prompt 28 — centrado, total único, sin duplicados — 0.17.0 (21)

Cinco peticiones: (1) en «Cobro por transferencia», quitar «Abrir Transfermóvil», subir y centrar el total y hacer lo mismo en todas las ventanas con total; botones Cancelar/Confirmar centrados, separados y sin texto; (2) centrar o repartir los elementos que van solos en una fila; (3) quitar elementos visuales que repiten la misma información; (4) ocultar el ID del dispositivo y el de la licencia en Licencia; (5) centrar y repartir el asistente inicial.

**1. Venta y totales**

| Cambio | Dónde |
|---|---|
| Fuera el botón «Abrir Transfermóvil», el evento `AbrirApp`, `PagoQr.PAQUETE_TRANSFERMOVIL` y el bloque `<queries>` del manifiesto (SPVI ya no consulta qué apps hay instaladas) | `AndroidManifest.xml`, `venta/*`, `PagoQr.kt` |
| `TotalVenta`: el total va **una sola vez**, centrado arriba, en `headlineSmall` negrita. La etiqueta cambia según el paso: «Total estimado» (carrito, sin cotización), «Total» o «Importe a transferir» (QR). Se quitaron el total inferior, el «Importe a transferir» bajo el QR y la fila Total de los datos del cliente | `venta/VentaScreen.kt`, `TextosVenta.etiquetaTotal` |
| Barra inferior con `SpviBarraAcciones`: ✕ (tonal) y ✓ (relleno), solo iconos, centrados y separados 16dp; el nombre va en TalkBack/tooltip | ídem |
| La tarjeta del comprobante pierde su título (repetía el de la barra superior) | ídem |
| Ficha de Registros: «Total» (venta) o «Importe» (transferencia) arriba y centrado, y fuera de la tabla | `registros/RegistrosScreen.kt` |
| Detalle de turno: «Total vendido» arriba y centrado, fuera de la tarjeta Totales | `registros/TurnoDetalleScreen.kt` |

**2. Centrado**

- `SpviCard` centra su contenido y el título (por defecto) y activa `LocalSpviCentrado`. Con eso, `SpviSecondaryText` se centra dentro de tarjetas y ventanas, pero no dentro de las filas (`SpviListItem` lo desactiva). Lo mismo para el cuerpo de `SpviVentana`. «Paso X de Y» del `SpviStepper` va centrado.
- 25 párrafos sueltos y los títulos de sección (Inicio, Precios, «Método de pago» en Venta, «Se mide en», «Receta») van centrados.
- Contadores con acción («N elegidos» + icono en Inventario, Elaboración, Registros y Precios; barra «Continuar» del modo venta) centrados como grupo. `SeleccionVenta.continuar` dice «1 elegido» / «N elegidos».
- Inicio: una alerta sola va centrada y con el mismo ancho que cuando son dos. En la tarjeta del turno, «Desde las…» sigue alineado a la izquierda, junto a su icono.
- Escáner: la fila «Buscando…» va centrada.

**3. Duplicados eliminados**

| Repetición | Solución |
|---|---|
| Total de la venta en 3–4 sitios | Uno solo, arriba (punto 1) |
| Licencia: tarjeta «Costos» con los mismos precios que los chips de tipo | Fuera la tarjeta. Los precios siguen en los chips |
| Licencia: filas Tipo / Vence / ID que repetían el título del estado | `detalle()` deja solo «Emitida» y, con licencia activa, «Tiempo restante» |
| Asistente: título del paso dentro del contenido y otra vez en la cabecera | Solo en la cabecera (`SpviStepper`) |
| Inicio, «Ganancia Neta»: «Venta» = total de la tarjeta Ventas | Fuera. Quedan Costo y Ganancia |
| Detalle de turno: fila «Abierto/Cerrado · horario» = Apertura + Cierre; «Ventas» y «Movimientos de inventario» = números de las pestañas | Fuera las tres filas |
| Pago electrónico: «En uso» con barra lateral + fondo + etiqueta | Fuera la barra |
| Teléfonos y tarjetas en Perfil, en la pantalla Pago electrónico y en una ventana de Inicio (**opción A** elegida por el usuario) | Solo en la pantalla Pago electrónico, que ahora se abre también desde la tarjeta de Inicio. Perfil queda con nombre, apellidos y carné (subtítulo en Ajustes: «Nombre, apellidos y carné»). Se borran `PagoElectronicoSheet`, `PagoSheetContent`, `BorradorPago`, `abrirBorrador/editarBorrador/guardarBorrador/cerrarBorrador`, `EventoPago.Cerrar` y sus pruebas y capturas (06a, 06b). **Se aparta a propósito de SPVI.txt 119–123** (listas en Perfil). El caso de uso de dominio `IntroducirPagoElectronico` se conserva con sus pruebas, aunque la app ya no lo usa |

**4. Licencia sin identificadores**: no se muestran el ID del dispositivo ni el de la licencia (fuera `idCorto` y el `SelectionContainer`). La solicitud ya los lleva cifrados, así que el usuario no los necesita. El ID del dispositivo solo hace falta para migrar, y se ve (copiable y centrado) en Ajustes → Migrar a otro teléfono; los textos de Migrar apuntan ahí. La lista «Teléfonos de tu Perfil» pasa a «Tus teléfonos», y el resumen dice «De tu Perfil y de Pago electrónico».

**5. Asistente inicial**: cada paso es una columna centrada que ocupa todo el alto, con el mismo espacio entre los elementos y en los bordes (`Equitativo`; si no cabe, 16dp entre ellos y se desplaza). En Bienvenida no hay cabecera, y «Saltar todo» (o «Cerrar») pasa a la barra inferior. Los puntos llevan el icono encima del texto centrado.

**Pruebas:** JVM 224 · dominio/datos 54 · app 303 (se quitan `abrirTransfermovil…` y `introducirNumerosNuevos…`, que probaban funciones eliminadas). Avisos del compilador: 0. Las pruebas instrumentadas compilan. Se añadieron `pagoElectronicoTocarEligeLaTarjeta` y `perfilMuestraSoloDatosPersonales`, y se actualizaron `VentaUiTest` y `LicenciaFormTest`.

## Prompt 29 — Servicios, insumos en Inventario, deslizar, aviso de turno — 0.18.0 (22)

Ocho peticiones. Respuestas del usuario a las dudas: (1) QR solo aviso, (2) tipografía libre parecida a Anurati, (3) insumos con precio de venta opcional y unidades enteras, (4) Tipo libre como las Categorías, venta de servicios separada y pestaña propia en Registros, (5) deslizar solo entre secciones (y entre pestañas dentro de Registros), (8) solo `POST_NOTIFICATIONS`, sin reinicio, sin importes en el texto.

| # | Petición | Resultado |
|---|---|---|
| 1 | «Transfermóvil captura los datos del QR excepto el importe» | **Sin cambios.** El QR que genera SPVI es el de transferencia de persona (`TRANSFERMOVIL_ETECSA,TRANSFERENCIA,<tarjeta>,<móvil>,`), que no lleva importe. Los QR con importe son de comercios registrados en la pasarela. El total sigue arriba y centrado para que el cliente lo escriba |
| 2 | Tipografía «Anurati» en el título | La licencia de Anurati es contradictoria (en unas fuentes es gratuita, en otras solo personal). Se usa **Orbitron** (SIL OFL 1.1), con un estilo geométrico parecido: `designsystem/res/font/orbitron.ttf`, peso 700, en `SpviMarca`. `SpviTopBar` la aplica al título «SPVI». La licencia está en `designsystem/src/main/OFL_Orbitron.txt` |
| 3 | Elaboración → Servicios; insumos en Inventario | Fuera la sección Elaboración (paquete `elaboracion/`). Los insumos aparecen en Inventario como la categoría «Insumos» (id negativo para no mezclarse con productos), con ficha, eliminar, el filtro «Insumos» y la acción «Insumo» en el botón +. El formulario de insumo pasa a `app/…/insumos/` y gana «Precio de venta» (opcional): si lo tiene, se vende en unidades enteras. Ningún producto puede usar la categoría «Insumos» (`NO_PERMITIDO`) |
| 4 | Servicios | Nueva sección (icono: mano que recibe, `VolunteerActivism`). Campos: Nombre, Tipo (libre, con sugerencias), Importe, y opcionales Foto y Descripción. Puede gastar insumos («por vez»): muestra «Alcanza para N» y no se vende si no alcanzan. Venta separada: «Nueva venta» → Productos o Servicios. Un carrito no puede mezclar ambos. Registros tiene la pestaña **Servicios** y la exportación incluye la clase de cada línea. Inicio tiene «Servicios: top ventas» y «Servicios: menos vendidos» («1 vez» / «N veces») |
| 5 | Deslizar | `navigation/Deslizar.kt`: un gesto horizontal de 72dp cambia de sección en la barra inferior. Solo funciona en las 5 secciones, y no en formularios ni en la venta. Se ignoran los toques en las franjas de gestos del sistema y los gestos que ya usa un hijo. En Registros cambia de pestaña y, después de la última, pasa a la sección siguiente. `SpviTabs` se vuelve desplazable con más de 4 pestañas |
| 6 | Barras de navegación de los lanzadores | `consumeWindowInsets(padding)` en 10 pantallas con `Scaffold` + `imePadding` (antes la barra de navegación se contaba dos veces y quedaba un hueco sobre el teclado o los botones). En `MainActivity`, la barra de navegación es transparente en los 3 modos (gestos, 2 y 3 botones), sin el contraste forzado que pinta algunos fabricantes, y hay margen horizontal para el notch en horizontal |
| 7 | Icono transparente | Las PNG `mipmap-*/ic_launcher*` y el fondo adaptativo son transparentes, y solo se ve la figura. Algunos lanzadores (One UI, MIUI) siempre ponen una placa detrás de los iconos adaptativos, y eso SPVI no lo puede evitar. La pantalla de inicio (splash) sigue usando `@color/ic_launcher_background` |
| 8 | Aviso de turno abierto | `notificacion/AvisoTurno.kt`: notificación fija (canal «Turno», importancia baja, sin sonido) con el texto «Turno abierto desde las HH:MM», sin importes. Aparece cuando SPVI pasa a segundo plano o se cierra con el turno abierto, y se quita al volver o al cerrar el turno. Al tocarla, abre SPVI. Si el proceso termina, el aviso desaparece y no se restaura tras reiniciar (no hay `RECEIVE_BOOT_COMPLETED`). El permiso `POST_NOTIFICATIONS` (Android 13+) se pide al abrir un turno. Si se niega, todo funciona igual |

**Datos:** base de datos **v4** (`MIGRACION_3_4`: tablas `servicio` y `servicio_insumo`, columnas `insumo.precioVentaCent` y `detalle_venta.clase`, con `'PRODUCTO'` por defecto). El respaldo sigue en JSON **v3**: añade servicios, la clase de cada línea y el precio de venta de los insumos, todos con valor por defecto, así que los respaldos de la 0.17.x se restauran sin cambios. «Borrar datos» y «Restaurar» incluyen los servicios. Se quitó el filtro de insumos «por uso» (ahora es un filtro de Inventario). Permisos: `CAMERA`, `INTERNET` y `POST_NOTIFICATIONS` (la tarea `spviPermisos` lo admite).

**Ayuda:** «Primeros pasos» (Servicios y deslizar), «Vender» (Productos/Servicios y el aviso), «Insumos y elaborados» y «Servicios» (nuevos).

**Pruebas:** JVM 232 (+8 `ServiciosTest`) · dominio/datos 54 · app 293. Se quitan 33 pruebas de Elaboración (5 de lógica, 12 del ViewModel y 16 capturas) y 8 instrumentadas. Se añaden 7 en `ServiciosAppTest` y se conservan las 8 de `InsumoFormTest`. Capturas Roborazzi: 142 casos (+5 `ServiciosCapturas`, +1 carrito de servicios). Instrumentadas: 96, solo compiladas. Avisos del compilador: 0.

**Capturas:** `CAPTURAS_P29.pdf` (retirado en la 0.18.1, sustituido por `CAPTURAS_0.18.1.pdf`). Son 20 pantallas en modo claro y oscuro: Nueva venta, indicadores de servicios, Servicios (lista, vacío, ficha, formulario, elegir, venta), insumos en Inventario (acción +, filtro, ficha, formulario), Registros → Servicios, deslizar, permiso y aviso de turno, icono antes y después, y barra de 3 botones. Son maquetas HTML con los mismos tokens y textos que el código, no capturas de un dispositivo. Las de Roborazzi (`ServiciosCapturas`) se generan con `./gradlew :app:recordRoborazziDebug`.

**Limpieza:** a petición del usuario se borró todo lo que había fuera de `SPVI/`: entregas `ENTREGA_*`, documentos de trabajo, capturas y PDF anteriores, archivos subidos y herramientas auxiliares. El proyecto no depende de ninguno de ellos.

## Prompt 30 — icono de Servicios, «+» centrado, cantidades centradas, icono de la notificación — 0.18.1 (23)

| Petición | Cambio | Dónde |
|---|---|---|
| Icono de Servicios sin corazón, solo la mano | `SpviIcons.Servicios` usa `MaterialSolido.ManoRecibe`: el trazado de «volunteer_activism» sin el corazón y desplazado 4.5 hacia arriba para centrarlo en el cuadro de 24. Afecta a la barra inferior, «Nueva venta» y Registros. Se elimina `VolunteerActivism` | `designsystem/icon/SpviIcons.kt` |
| Botón «+» centrado abajo | `floatingActionButtonPosition = FabPosition.Center` en Inventario, Servicios y Precios. En `SpviExpandableFab`, cada acción se reparte en dos mitades de igual peso: el mini botón queda sobre el «+» y la etiqueta a su izquierda | `InventarioScreen`, `ServiciosScreen`, `PreciosScreen`, `component/Overlays.kt` |
| Números centrados en los selectores de cantidad | `SpviTextField` acepta `textAlign` (opcional, por defecto sin cambio). Se centra en el carrito de la venta (− [n] +) y en los niveles del asistente inicial | `component/TextFields.kt`, `VentaScreen`, `OnboardingScreen` |
| Icono de la notificación = el del lanzador | El icono pequeño es la silueta del icono de SPVI (`drawable-*dpi/ic_stat_spvi.png`, 24dp, generado del `ic_launcher_monochrome`). Android solo usa su transparencia, así que no puede ir en color. El icono grande es el del lanzador en color (`setLargeIcon`), y `setColor` tiñe la cabecera con el primario de marca. Se elimina `drawable/ic_stat_turno.xml` | `notificacion/AvisoTurno.kt`, `res/drawable-*dpi/` |

**Verificación:** sin compilar. A petición del usuario se borraron las herramientas de compilación del entorno de trabajo en el Prompt 29. Son cambios pequeños, que siguen patrones ya compilados en el proyecto, y ninguna prueba depende de ellos. Hay que comprobarlos con `./gradlew spviCheck` en Android Studio.

**Capturas (0.18.1):** `CAPTURAS_0.18.1.pdf` (retirado en la 0.18.2, sustituido por `CAPTURAS_0.18.2.pdf`). Son 21 pantallas en claro y oscuro, con un anillo naranja discontinuo en lo que cambió: icono de Servicios (solo la mano), «+» centrado en Servicios, Inventario (abierto y cerrado) y Precios, números centrados en «Cantidad» y en los niveles del asistente, y aviso de turno con el icono de SPVI. Son maquetas HTML con los tokens, textos, iconos e ilustraciones del código, no capturas de un dispositivo: el código 0.18.1 aún no se ha compilado.

## Prompt 31 — icono de insumo, aviso sin icono repetido, «+» de Inventario, insumos como categoría — 0.18.2 (24)

**Pedido:** (1) icono de insumo = un saco o bolsa; (2) el icono de la notificación sale duplicado; (3) en Inventario, Escanear, Escribir y Cancelar se ven debajo de la tabla y sin su texto; (4) «Insumo» es una categoría, no un botón.

**Cambios:**
1. `SpviIcons.Insumo` = `MaterialSolido.Saco`, un saco atado dibujado a mano en 24×24 (Material no trae ninguno). Se retira `LocalPizza`, que ya no se usaba. Al quitar el botón «Insumo», el saco se muestra en la opción «Insumos» de Categoría (formulario de producto) y en el chip «Insumos» del filtro de Inventario.
2. `AvisoTurno`: se quita `setLargeIcon` (y `iconoLanzador()`). Android ya pinta el icono pequeño (silueta del lanzador, teñida con `#2B4FA3`) en la cabecera; con el grande a la derecha se veía dos veces.
3. `SpviExpandableFab`: cada acción es `FilaAccionFab`, un `Layout` propio con ancho simétrico respecto al mini botón (etiqueta a la izquierda). Sustituye a `Row(fillMaxWidth)` + `weight`, que dependía de que el hueco del FAB del Scaffold tuviera ancho acotado; si no, la etiqueta quedaba en 0 y solo se veían los iconos. `InventarioContent` añade un velo (`scrim` al 32 %) sobre la tabla mientras el «+» está abierto: las acciones se leen encima de la tabla, y tocar fuera o Atrás lo cierra.
4. Insumos como categoría:
   - Se quita la acción «Insumo» del «+» y de la hoja Agregar (con `TextosInventario.INSUMO`, `InventarioTags.OPCION_INSUMO`, `onAgregarInsumo` y `InventarioViewModel.agregarInsumo`).
   - En el formulario de un producto **nuevo**, «Insumos» es la primera opción de Categoría. Elegirla (o escribirla) emite `EventoForm.EsInsumo(nombre)` y se pasa al formulario de insumo (`Route.InsumoForm(nombre = …)`, sustituyendo al de producto) con el nombre ya escrito.
   - Al **editar** un producto sigue siendo una categoría reservada (regla del dominio sin cambios).
   - Con el filtro «Insumos» puesto, «+ → Escribir» abre directamente el formulario de insumo.
   - Al guardar un insumo se vuelve siempre a Inventario, como con los productos.
5. Textos: ayuda «Insumos y elaborados» (5 pasos), MANUAL §5 y §2, README, DESIGN_SYSTEM. Versión 0.18.2 (24).

**Pruebas nuevas:** `ProductoFormTest` (categoría Insumos en nuevo → evento; al editar → reservada), `InsumoFormTest` (nombre recibido), `InventarioViewModelTest` (Escribir con filtro Insumos → `InsumoForm`).

**Sin verificar:** el código no se ha compilado (no hay toolchain en el entorno de trabajo); compílalo y pasa las pruebas en Android Studio. Lo que se pierde al pasar del formulario de producto al de insumo: foto, código y demás campos ya rellenados (solo se conserva el nombre; los insumos no tienen código ni foto).

**Capturas (0.18.2):** `CAPTURAS_0.18.2.pdf` (retirado en la 0.18.4, sustituido por `CAPTURAS_0.18.4.pdf`). Son 24 pantallas en claro y oscuro: las 21 anteriores al día, más el formulario de producto con «Insumos» como primera categoría, el formulario de insumo con el nombre ya puesto y el filtro de Inventario con el chip del saco. Los cambios de la 0.18.2 llevan un anillo naranja discontinuo. Al montarlas se vio que el desplegable de Categoría desalinearía los textos (solo «Insumos» llevaba icono): las demás opciones reservan ahora el mismo hueco. Son maquetas HTML, no capturas de un dispositivo.

## Prompt 32 — sin icono de insumo, nombre del servicio en Registros, icono de Guardar — 0.18.3 (25)

**Pedido:** eliminar el icono de insumo; mostrar el nombre del servicio en Registros; el icono de Guardar es igual al de Confirmar.

**Cambios:**
1. **Sin icono de insumo:** se quitan `SpviIcons.Insumo` y el trazado `Saco`. El chip «Insumos» del filtro y la opción «Insumos» de Categoría quedan solo con texto, como las demás.
2. **Nombre del servicio en Registros → Servicios:** nueva `subtituloFila(v: Venta)`. Cada fila sigue titulada con la fecha (regla P25) y debajo lleva el servicio: «Corte de pelo», «Corte de pelo y Lavado» o «Corte de pelo, Lavado y 2 más». Las ventas de productos no llevan subtítulo.
3. **Guardar ≠ Confirmar:** `SpviIcons.Guardar` vuelve a ser el disquete (`Save`, Material) en lugar del ✓; revierte la decisión P25. Afecta a los formularios de producto, insumo, servicio, Perfil, Pago electrónico, Precios y escáner, y al diálogo «Consultas en internet» de Ajustes (`confirmIcon = SpviIcons.Guardar`). Los diálogos que confirman una acción siguen con ✓.
4. Textos: MANUAL (§ botones, insumos, servicios), PRUEBAS_DISPOSITIVO y DESIGN_SYSTEM. Versión 0.18.3 (25).

**Pruebas nuevas:** `RegistrosLogicTest.ventaDeServiciosMuestraElNombreDelServicio`.

**Sin verificar:** el código no se ha compilado (no hay toolchain en el entorno de trabajo); compílalo y pasa las pruebas en Android Studio. El PDF `CAPTURAS_0.18.2.pdf` no muestra estos cambios.

## Prompt 33 — Guardar vuelve a ✓; Registros → Servicios muestra la cantidad — 0.18.4 (26)

**Pedido:** «Guardar tiene que ser = ✓»; en Registros, la cantidad de servicios en vez del nombre (los nombres se ven al abrir la información de esa fecha).

**Cambios:**
1. `SpviIcons.Guardar` = `SpviIcons.Confirmar` (✓) otra vez (decisión P25 confirmada). Se retira el trazado `Save` y el `confirmIcon` del diálogo «Consultas en internet». Se deshace el punto 3 del Prompt 32.
2. `subtituloFila(v: Venta)`: en ventas de servicios, «1 servicio» / «N servicios» (suma de cantidades: 2 × Lavado cuenta 2). La ficha de la fecha ya enumera cada servicio («2 × Lavado y peinado»). Las ventas de productos siguen sin subtítulo.
3. MANUAL, PRUEBAS_DISPOSITIVO y DESIGN_SYSTEM ajustados. Versión 0.18.4 (26).

**Pruebas:** `RegistrosLogicTest.ventaDeServiciosMuestraCuantosServicios` (sustituye a la del nombre).

**Sin verificar:** sin compilar (no hay toolchain en el entorno de trabajo).

**Capturas (0.18.4):** `CAPTURAS_0.18.4.pdf`, junto a la carpeta del proyecto. Son 24 pantallas en claro y oscuro, más una portada con la lista de cambios. «Insumos» aparece como texto, sin icono, en el desplegable de Categoría y en el filtro. Registros → Servicios muestra «2 servicios» o «1 servicio» bajo cada fecha, y la ficha de la fecha enumera los servicios (anillo naranja en ambas). Guardar = ✓. Son maquetas HTML hechas con los tokens, textos, iconos e ilustraciones del código, no capturas de un dispositivo, y el código 0.18.4 aún no se ha compilado.


## Prompt 35 — Apps Principal y Secundaria — 0.19.0 (27)

**Pedido:** dos tipos de app, definidos en Ajustes:

- **Principal:** la del gerente, administrador o dueño. Tiene la base de datos general y activa la licencia.
- **Secundarias:** las de los empleados. Se vinculan a la principal con un QR cifrado y sincronizan en tiempo real o cuando el usuario decida.

La sección va en Ajustes/Sistema, debajo de Licencia.

**Respuestas del usuario:**

- Transporte: **solo red local** (wifi del local o zona wifi del teléfono principal).
- Permisos configurables por empleado.
- Sin conexión sigue vendiendo hasta cerrar el turno, pero no puede empezar uno nuevo sin sincronizar.
- Secundarias cubiertas por la licencia de la principal, con un máximo de 5.

**Cambios:**

1. **Dominio:** se añaden:
   - `Vinculacion.kt` (tipos, permisos, licencia de la principal, reglas).
   - `VinculacionRepositories.kt` y `PuertaTurno`, que usa `AbrirTurno`.
   - `Turno.empleadoId`.
   - Los errores `SinPermiso`, `SinPrincipal` y `VinculacionRechazada`.
2. **BD v5** (`MIGRACION_4_5`):
   - `uuid`, `empleadoId` y `sincronizado` en `turno` y `venta`.
   - Nueva tabla `empleado`.
   - El turno activo filtra `empleadoId IS NULL`.
3. **`data/sync`:**
   - Protocolo, canal cifrado (ECDH P-256 + HKDF + AES-GCM con secuencia), `ConfigSync`, `RedLocal` (NSD).
   - `AlmacenSync`, `ServidorSync`, `EjecutorComandos`, `ClienteSync`.
   - Decoradores `…SegunTipo`, implementaciones de repositorios y `ArranqueSync` (en `SpviApplication`).
4. **UI:**
   - Ajustes → **Apps vinculadas**, debajo de Licencia. En una secundaria se ocultan Configurar, Perfil, Licencia, Pago electrónico, Avisos, Respaldo y Migrar, y Precios si no tiene ese permiso.
   - `VinculacionScreen`: lista, agregar con QR, permisos, nuevo código, quitar, usar como secundaria, sincronizar, modo y desvincular.
   - Escáner en modo `VINCULAR`.
   - `RootState.BloqueoSecundaria`.
   - `LocalPermisosApp` oculta exportar y editar según los permisos.
   - Mensajes `mensajeSync` en los formularios, en Inicio, en Venta y en Precios.
5. **Docs:**
   - Nuevos: `docs/VINCULACION.md`, MANUAL §10, PRUEBAS_DISPOSITIVO §4.10 y el tema de Ayuda «Apps de tus empleados».
   - Actualizados: README (BD v5) y SECURITY (red local).
   - Versión 0.19.0 (27).

**Sin permisos nuevos:** `INTERNET` cubre los sockets locales; no hay servicio en primer plano. Es una suposición documentada: la principal atiende mientras la app está abierta.

**Pruebas nuevas:**

- `SyncProtocoloTest` (data):
  - QR de ida y vuelta.
  - La clave ECDH coincide en las dos apps.
  - El canal en orden, y rechazo de mensajes alterados, repetidos, reflejados o con otra clave.
  - Licencia de la principal.
  - Errores remotos.
  - Permisos por acción.
  - La clave no aparece en `toString`.
- `PuertaTurnoTest` (domain).
- `VinculacionLogicTest` (app).
- `RootStateTest.secundariaUsaLaLicenciaDeLaPrincipalYNoTieneOnboarding`.
- Fakes `TipoRepo`, `PrincipalRepoFake` y `SecundariaRepoFake`.

**Sin verificar:**

- Nada se ha compilado (no hay toolchain).
- La sincronización entre dos teléfonos reales se prueba con PRUEBAS_DISPOSITIVO §4.10.
- `5.json` del esquema se genera en el primer build.

> **Nota (0.19.1):** las tres líneas anteriores quedan superadas. Desde el Prompt 36 todo se compila y se verifica con `tools/verificacion/verificar.sh`, y `5.json` ya está versionado.

## Prompt 36 — Verificación sin Gradle y correcciones de la vinculación — 0.19.1 (28)

**Petición:** aplicar las sugerencias de la entrega anterior y proponer soluciones para las limitaciones.

**Verificación (nueva):** `tools/verificacion/verificar.sh` + `resolver_maven.py`. Solo necesitan Linux, bash, python3, curl y unzip; ni Gradle ni Android SDK. El script:
1. Descarga JDK 17, kotlinc 2.0.21, KSP y las dependencias de Maven Central/Google en `~/.cache/spvi-tc`. Se resuelven app y data juntos, con una sola versión por librería.
2. Compila los 6 módulos (el plugin de Compose solo en designsystem/app).
3. Ejecuta Room 2.6.1 por KSP, que valida las consultas y compara el esquema con `5.json`.
4. Ejecuta Hilt 2.52 por KSP, que valida el grafo y compila el Java generado.
5. Aplica `MIGRACION_4_5` en SQLite sobre el esquema v4 y lo compara con `5.json`.
6. Pasa los tests JVM: core 14, licencia 59, domain 162, data 65, designsystem 18, app 285 (**603**).
7. Compila las capturas Roborazzi y los dos juegos de tests instrumentados.

**Errores que solo aparecían al compilar (corregidos):**
- `EscanerUseCases`: smart cast entre módulos.
- `RootViewModel`: tipos explícitos en `flowOf(null)`.
- `TurnoPersistenciaTest` y `EsquemaTest` actualizados a v5 (con una prueba nueva de la migración 4→5).
- `EntornoIntegracion` actualizado por el repositorio de tipo de app.

**Correcciones funcionales:**
- **Permisos en las fichas:** sin «Exportar», las fichas individuales (producto, insumo, servicio, registro) no muestran Compartir. Sin «Editar inventario», no muestran Editar ni Eliminar.
- **Respaldo:** turnos y ventas guardan `uuid`, `empleadoId` y `sincronizado` (campos opcionales; compatible en los dos sentidos).
  - La validación admite un turno abierto **por app** (antes rechazaba los respaldos de una principal con secundarias en turno) y exige `uuid` sin repetir.
  - Prueba nueva en `DtoRoundTripTest`.
- **Red:** el aviso de red de «Apps vinculadas» abre los ajustes de zona wifi (principal) o de wifi (secundaria sin conexión). Lo hace `common/AjustesRed.kt`, que si falta una pantalla usa otra del sistema. Sin permisos nuevos.

**Pendiente con propuesta:**
- Servicio en primer plano para la principal. Necesita permisos nuevos y está a la espera de autorización: ver docs/VINCULACION.md §Limitaciones.
- Gradle, lint, R8 y los tests en dispositivo (REVISION_FINAL L1/L2/L11/L13/L14).

## Prompt 37 — Servicio en primer plano de la principal — 0.19.2 (29)

**Petición:** «A», es decir, la opción A de la entrega anterior: servicio en primer plano para la principal, con los permisos autorizados.

**Hecho:**
- **Permisos** (normales, sin diálogo): `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` y `CHANGE_NETWORK_STATE`. Añadidos al manifiesto y a la lista de `spviPermisos`. `verificar.sh` incluye un paso 8 que compara el manifiesto de `:app` con esa lista.
- **`ServicioSync`** (`app/notificacion`):
  - servicio `connectedDevice`, no exportado y `START_NOT_STICKY`;
  - no hace trabajo propio: solo mantiene vivo el proceso donde corre `ServidorSync`.
- **`PlanAviso`** (puro, con `PlanAvisoTest`):
  - El servicio corre si la app es principal con secundarias **y** está a la vista **o** hay algún turno abierto.
  - Notificación única (ID 29): con servicio, siempre; sin servicio, como en P29.
  - Textos: «Turno abierto desde las HH:MM · N apps conectadas», «1 app conectada», «Esperando a tus apps vinculadas».
- **`AvisoTurno`** aplica el plan:
  - Arranca el servicio (Android 12+ puede negarlo en segundo plano; se reintenta con el siguiente cambio de estado).
  - Lo para solo cuando ya está en primer plano, para evitar el fallo de `startForegroundService`.
  - Al terminar, el servicio suelta la notificación con `STOP_FOREGROUND_DETACH` y `AvisoTurno` la actualiza o la quita, sin carreras.
- **Datos:** `SyncDao.observarTurnosAbiertos()` y `ArranqueSync.atencion` (principal con secundarias, turnos abiertos, secundarias conectadas).

**Sin verificar:** el comportamiento real en teléfonos (PRUEBAS_DISPOSITIVO §4.10 paso 14), en especial con el ahorro de batería de cada fabricante.

## Prompt 38 — La principal también hace todas las funciones — 0.19.3 (30)

**Petición:** «la app principal también debe realizar todas las funciones, no solo funcionar como monitor de las secundarias».

**Revisión:** ya era así desde 0.19.0:
- `PermisosApp.PRINCIPAL` incluye todos los permisos.
- Los repositorios «según tipo» trabajan en local en la principal.
- El turno activo de la principal excluye los de las secundarias.
- Ninguna pantalla se oculta en la principal.

**Fallo encontrado y corregido:** los cambios de stock que una secundaria pide a la principal (ajustar existencias, editar la cantidad, alta o baja) se asociaban al **turno abierto de la principal** y entraban en el resumen de su cierre.
- Ahora `EjecutorComandos` ejecuta con `OrigenMovimiento(empleadoId)` y `turnoParaMovimiento()` los asocia al turno abierto de esa secundaria, o a ninguno.
- Consulta nueva: `TurnoDao.activoDeEmpleado`.

**Pruebas:**
- `PuertaTurnoTest.laPrincipalLoPuedeTodo` (JVM).
- `TurnoRepositoryInstrumentedTest.laPrincipalVendeConSuPropioTurnoAunqueHayaSecundariasEnTurno` (instrumentado, compilado). Abre el turno propio con el de una secundaria abierto, vende, ajusta y cierra. Comprueba que el cierre solo cuenta lo propio y que el turno de la secundaria sigue abierto.
- PRUEBAS_DISPOSITIVO §4.10, paso 15.

**Documentación:** MANUAL §10 (la principal sigue haciendo todo y su turno es solo suyo) y VINCULACION.

## Prompt 44 — Capturas 0.19.3

**Capturas:** `CAPTURAS_0.19.3.pdf` sustituye a `CAPTURAS_0.18.4.pdf`. Tiene 38 páginas: la portada, los nº 1–24 de la 0.18.4 y 13 maquetas nuevas en claro y oscuro (nº 25–37). Las nuevas son:
- Ajustes con «Apps vinculadas», en la principal y en la secundaria;
- Apps vinculadas vacía y con dos empleados;
- Agregar empleado con sus permisos;
- el QR de un solo uso;
- Editar empleado y Quitar;
- «Usar esta app como secundaria»;
- la secundaria conectada y sin conexión;
- el bloqueo por licencia de la principal;
- el aviso «Turno abierto desde las 09:30 · 2 apps conectadas».

Los generadores están en `tools/capturas/` (ver CAPTURAS.md). Son maquetas HTML, no capturas de un dispositivo. No cambia el código de la app.

**Observación al maquetar:** `SpviStatusBanner` con tono Info usa el icono de Licencia (llave). Por eso «Red local activa» y «Conectada con la app principal» llevan una llave. Se deja como está en el código, pendiente de decisión.

## Prompt 45 — 0.20.0 (31): integración de las apps vinculadas

**Pedido:** «procede con la 0.20.0 y actualiza el pdf». Se aplica el plan aprobado de `docs/ANALISIS_IMPACTO_0.19.md` §6 (decisiones de P42 y P43).

**Cambios:**
- **H1 Vendedor:** `Venta.vendedor` (quien abrió el turno). Aparece en la ficha de la venta (no en el texto compartido), en la columna «Vendedor» de Ventas en Excel/PDF y en el filtro «Vendedor» de Registros, que solo sale con 2 o más personas. `FiltrosGuardados` guarda ahora 7 valores y sigue leyendo los de 6.
- **H3:** en una secundaria, el texto bajo «Pegar SMS» explica que el SMS llega al dueño y que el empleado debe escribir el nº.
- **H4 Cobro por empleado:** el dueño elige en la ficha del empleado su tarjeta y su teléfono de cobro. Los combos solo aparecen si tiene más de uno. La secundaria recibe solo esos dos (`Perfil.paraEmpleado`).
- **H5 Pedir cierre:**
  - En la principal, la fila y la ficha del empleado muestran «Turno abierto desde las HH:mm» y el botón «Pedir cierre del turno», con confirmación.
  - El cierre viaja en `SincronizarOk.cerrarTurno`, un campo opcional y compatible con la 0.19.x.
  - La secundaria lo aplica con `CierreRemoto` cuando no hay venta en curso (`SesionVenta`, P43).
  - Mientras está pedido: aviso en Inicio y en la venta, y no se pueden empezar ventas nuevas.
  - Tras cerrarse: aviso descartable.
- **H6:** alerta «Existencias negativas» para productos e insumos por debajo de 0, en Inicio y en el filtro del Inventario.
- **H7:**
  - El respaldo lleva `empleados[]`, sin claves ni QR; al restaurar aparecen «Sin vincular».
  - Migrar → paso 2 avisa de sincronizar antes las apps de los empleados.
- **Ayuda:** «Apps de tus empleados» tiene un 5.º paso.
- **Base de datos v6:** `MIGRACION_5_6` añade `tarjetaId`, `telefonoId` y `cierreSolicitadoEn` a `empleado`. Se exportó `data/schemas/.../6.json`.

**Tests:**
- Nuevos: `Version020Test` (domain, 8) y `Version020SyncTest` (data, 6).
- Ampliados: `TablasExportTest`, `FiltrosGuardadosTest`, `InicioLogicTest` y `VinculacionLogicTest`, más casos nuevos en `VentaViewModelTest` (el cierre espera a la venta) e `InicioViewModelTest`.
- Instrumentados, solo compilados: `EsquemaTest` v6, con su migración 5→6.
- Capturas Roborazzi nuevas: `01g`–`01i`, `04o2`, `04i2` y `VinculacionCapturas` (`07p`–`07s`).
- `verificar.sh`: **627 tests JVM correctos** (core 14, licencia 59, domain 171, data 71, designsystem 18, app 294).
- Los pasos 7–8 (compilación de capturas e instrumentados, y permisos) se terminaron en una segunda pasada sobre las mismas salidas, porque el entorno (2 GB) cortaba el proceso largo. Resultado: «Todo correcto».

**Capturas:** `CAPTURAS_0.20.0.pdf` (48 páginas) sustituye a `CAPTURAS_0.19.3.pdf`.
- Portada nueva.
- Los nº 27 y 30 se actualizaron.
- 10 maquetas nuevas (nº 38–47).
- `tools/capturas/generar.py` ganó los componentes necesarios (combo, chips, tarjeta de turno, alertas, ficha).

**Suposiciones:**
- La garantía de «nunca durante una venta» deja una ventana de milisegundos entre la comprobación y el cierre. Si coincide, el turno cerrado bloquea el «Confirmar» y el carrito se conserva.
- El nombre del vendedor es el `abiertoPor` del turno: si el empleado cambia de nombre, las ventas antiguas conservan el anterior.

**Pendiente:** probar en dos teléfonos reales (PRUEBAS_DISPOSITIVO §4.10, pasos 16–20).

## Prompt 46 — Plan 0.21.0 (propuesta)
Respuestas sobre «Consultas de códigos en internet» y sobre los botones del bloqueo de la secundaria, y el plan `docs/PLAN_0.21.md` (C1–C13). Sin cambios de código.

## Prompt 47 — Precios por secundaria y prompt para GL
- Importe por cada app secundaria: Mensual 1 000, Semestral 5 000, Anual 9 000 y Perpetua 17 000 CUP (`PLAN_0.21.md`).
- `docs/GL_PROMPT_0.21.md`: prompt para opencode desktop que actualiza GL. Añade el campo opcional `secundarias` a la solicitud y a la licencia, sin cambiar el contrato v1 ni el envelope, junto con el precio, el historial y el ID fuera del JSON.
- `tools/licencia/probar_gl.py`: genera la solicitud de prueba (`docs/GL_SOLICITUD_PRUEBA.txt`, MENSUAL con 2 secundarias = 8 000 CUP, dispositivo de `GoldenVectorTest`) y verifica la licencia que devuelva GL como lo hará SPVI. `autoprueba` hace la ida y vuelta con un GL simulado (correcta).
- Suposición: el máximo es 10 secundarias por licencia (constante, fácil de cambiar).

## Prompt 48 — 0.21.0 (32): plan C1–C13
Implementado todo `docs/PLAN_0.21.md` (detalle técnico en `docs/VINCULACION.md` §0.21.0).
- **Licencia (C4/C5):** campo opcional `secundarias` (0–10; sin campo = 5; prueba = 5) en solicitud y licencia; precio = base + n × importe por secundaria; selector en Licencia; una sola principal.
- **Turno (C6):** el empleado solo solicita el cierre; el dueño aprueba (reutiliza `cerrarTurno`, nunca durante una venta) o rechaza. Nada se cierra solo.
- **Teléfono (C2), permisos (C3), Registros (C7/C8), Inicio de la secundaria (C9), filtros (C10), miniaturas (C11), recorrido y módulos (C12), menú + vertical (C13), «Sin existencia» (C1).**
- **Base de datos v7:** `MIGRACION_6_7`, esquema `7.json`.
- **Tests:** nuevos `Version021Test` (domain, 6), `FiltroEntradaTest` (designsystem, 4), `Version021AppTest` (app, 5) e `InicioViewModelTest.secundariaSoloSolicitaElCierre`. `verificar.sh` completo: **644 tests JVM correctos** (core 14, licencia 59, domain 177, data 71, designsystem 22, app 301), «Todo correcto».
- **Después de la verificación**, solo textos: la Ayuda «Apps de tus empleados» se actualizó (5 pasos, ≤ 110 caracteres comprobados). Es un cambio de literales: no se volvió a compilar porque el entorno perdió las herramientas.
- **Docs:** LICENSE_CLIENT, MANUAL_USUARIO, FORMATOS, README, RELEASE, PRUEBAS_DISPOSITIVO (pasos 21–28), REVISION_FINAL, CAPTURAS, VINCULACION.
- **Capturas:** `CAPTURAS_0.21.0.pdf` (60 páginas) sustituye a `CAPTURAS_0.20.0.pdf`; 12 maquetas nuevas (nº 48–59) y nº 28, 30, 45 y 46 actualizados.
- **Pendiente:** GL con `secundarias` (`docs/GL_PROMPT_0.21.md`) y prueba en dos teléfonos reales.

## Prompt 49 — 0.21.1 (33): colores H1–H5
Revisión de la paleta (`docs/PROPUESTA_COLOR.md`) y aplicación de H1–H5:
- **H1:** `L_OUTLINE` `#8A8F96` → `#767B82`: bordes de campos ≥ 3:1 también en diálogos (antes 2.78:1).
- **H2:** `IconActionStyle.Tonal` y mini botones del menú + en `tertiaryContainer`; el naranja (`secondaryContainer`) queda solo para avisos. Desviación documentada: no gris neutro (desaparecía sobre los diálogos).
- **H3:** insumo crítico claro `#B45309` → `#9A3412` (antes igual que el naranja de marca).
- **H4:** `AlertTone.SinExistencia` + `CardTone.Error`: el contador «Sin existencia» va relleno.
- **H5:** paleta de gráficos sin morado/verde/amarillo de las alertas (magenta y oliva; en oscuro, petróleo, rosa y oliva).
- **Tests:** `ContrastTest` amplía `uiComponents` (outline en diálogos, «Sin existencia») y añade `alertasDistintasDeMarcaYGraficos`; `InicioLogicTest` comprueba el tono nuevo. `verificar.sh`: **645 tests JVM correctos** (core 14, licencia 59, domain 177, data 71, designsystem 23, app 301), «Todo correcto». Incluye la Ayuda «Apps de tus empleados» del Prompt 48, ya compilada.
- **Docs:** DESIGN_SYSTEM (roles y contrastes), PROPUESTA_COLOR (estado), README, RELEASE, PRUEBAS_DISPOSITIVO, REVISION_FINAL, CAPTURAS.
- **Capturas:** `CAPTURAS_0.21.1.pdf` (60 páginas) sustituye a `CAPTURAS_0.21.0.pdf`; nº 25–59 rehechos con la paleta nueva (nº 55 con la dona de Inventario). Los nº 1–24 (0.18.4) conservan la paleta anterior.


## Prompt 50 — 0.21.2 (34): H6, «Insumo bajo» sin verde
- «Stock insumo bajo» pasa del verde de SPVI.txt (`#3D7A1F` / `#AED581`, que se confundía con `success`) al azul petróleo del rol terciario (`#2F6F80` / `#7FC4D4`). Cambio pedido por el usuario.
- El tercer color de los gráficos pasa a caramelo (`#8B5E34` / `#D4A373`, ≥ 5:1 sobre las cards) para no repetir el tono de una alerta.
- `ContrastTest.alertasDistintasDeMarcaYGraficos` comprueba además que insumo bajo ≠ éxito y = terciario. `verificar.sh`: **645 tests JVM correctos**, «Todo correcto».
- Docs: DESIGN_SYSTEM, PROPUESTA_COLOR (H6 aplicado; H7 no), README, RELEASE, PRUEBAS_DISPOSITIVO, REVISION_FINAL, CAPTURAS.
- Capturas: `CAPTURAS_0.21.2.pdf` (60 páginas) sustituye a `CAPTURAS_0.21.1.pdf`; el nº 45 muestra «Stock insumo bajo» y el nº 55 la dona con caramelo.

## Prompt 51 — 0.21.3 (35): H7, tema oscuro con tinte de marca
- Los grises del tema oscuro llevan un toque del azul de marca, con la misma escala de luminancia: fondo `#0F1318` (antes `#121212`), surface `#1A1F26` (`#1E1E1E`), containers `#14181E` / `#1F252D` / `#242A33` / `#2A313B` / `#313944`, surfaceVariant `#262C35`, outline `#8C939E`, outlineVariant `#363E4A`, onSurfaceVariant `#B3B9C2`; fondo de ventana (`values-night/colors.xml`) `#0F1318`.
- Contrastes oscuros: onSurface 13.27, onSurfaceVariant 8.39, secundario 65 % ≥ 5.43, outline ≥ 3.8 (también en diálogos), alertas y gráficos ≥ 4.9.
- Test nuevo `ContrastTest.oscuroConTinteDeMarca` (azul > rojo en toda la escala, la elevación sube de luminancia, outline sobre `Highest`). `verificar.sh`: **646 tests JVM correctos** (designsystem 24), «Todo correcto».
- Docs: DESIGN_SYSTEM, PROPUESTA_COLOR (todo aplicado), README, RELEASE, PRUEBAS_DISPOSITIVO, REVISION_FINAL, CAPTURAS.
- Capturas: `CAPTURAS_0.21.3.pdf` (60 páginas) sustituye a `CAPTURAS_0.21.2.pdf`; columna «Oscuro» de los nº 25–59 rehecha.

## Prompt 52 — prueba del escáner con códigos reales (sin cambio de versión)
- 44 códigos públicos (OFF/OBF/OPF/UPCitemdb, Cuba 850, UPC-A, UPC-E, EAN-8, GTIN-14, ISBN, entradas manuales e inválidos) contra las bases en vivo con el código real de dominio/datos (`tools/escaner/ProbarCodigos.kt`, `probar.sh`) y 13 flujos completos escáner → formulario → guardar con los ViewModels reales (`tools/escaner/FlujoEnVivoTest.kt`, `probar_flujo.sh`). Fuera de `verificar.sh` (necesitan internet).
- Resultado: 35 encontrados, 2 no encontrados, 7 no consultables; 12/13 flujos guardan. Hallazgos E1–E10 y propuestas P1–P10 en `docs/PRUEBA_ESCANER.md` (destacan: UPC-E rechazado, códigos con guion que no se reconocen al escanear, fichas de OFF de 154 KB sin `fields`). Código de la app sin cambios: queda pendiente de confirmación.

## Prompt 53 — 0.21.4 (36): arreglos del escáner y prueba repetida
- Aplicadas P1–P10 de `docs/PRUEBA_ESCANER.md`:
  - **UPC-E:** se valida y se consulta como UPC-A; se guarda tal como se leyó.
  - **Guiones:** `normalizar` solo quita guiones en códigos numéricos; `ConsultarCodigo` prueba exacto y sin guiones; el formulario guarda normalizado.
  - **Open Facts:** `fields=product_name,image_url`.
  - **Nombres:** `NombreEnLinea.limpiar` (entidades, espacios, ASIN, corte en palabra), también en el formulario.
  - **Fotos:** http → https y hasta 3 imágenes de UPCitemdb (`ProductoEnLinea.imagenesAlternativas`) dentro de 5 s.
  - **Códigos no comerciales:** prefijos GS1 restringidos y todo ceros no salen a internet.
  - **Filtro de código:** `FiltroEntrada.CODIGO` con 64 caracteres y «_».
  - **QR o URL en modo Producto:** aviso `CODIGO_NO_VALIDO` y formulario sin código.
  - **Nombre antes que la foto:** «Guardar» espera la foto como mucho 5 s; texto «Descargando la imagen…».
  - **User-Agent:** «SPVI/0.21».
- `verificar.sh`: **662 tests JVM correctos** (domain 186, data 72, app 307), «Todo correcto».
- **Prueba en vivo repetida:**
  - UPC-E encontrado; balanza y todo ceros ya no salen a internet;
  - fotos 30/35 (antes 24/35), UPCitemdb 6/10 (antes 1/10); OFF 0,2 KB por consulta (antes hasta 154 KB);
  - 13/13 flujos guardan (antes 12/13).
- Docs: PRUEBA_ESCANER §4–§5, README, RELEASE, PRUEBAS_DISPOSITIVO (casos nuevos en §4.6), REVISION_FINAL.

## Prompt 54 — 0.21.5 (37): ambigüedad EAN-8 / UPC-E y capturas del escáner
- **Dominio:** `SimbologiaCodigo { EAN_8, UPC_E, OTRA }`; `CodigoBarras.candidatosConsulta(codigo, simbologia)` (con simbología, una lectura; sin ella y válido de las dos maneras, EAN-8 y luego UPC-A); `paraConsulta`/`esConsultable` usan el primer candidato; `esAmbiguo`; `completarUpcE` para UPC-E de 6 dígitos.
- **`BuscarProductoEnLinea(codigo, simbologia)`:** recorre la cadena por cada candidato; devuelve el primer «Encontrado»; «Sin conexión» corta; si alguno dio «No encontrado» → `NoEncontrado` (fuentes fallidas acumuladas), si no → `ServicioNoDisponible`.
- **App:** `CamaraEscaner` entrega `Barcode.format` → `SimbologiaCodigo`; `EscanerViewModel.codigoDetectado(raw, simbologia)` la guarda en el estado (sirve para Reintentar y tras el permiso de internet). La búsqueda manual no lleva simbología.
- Suposición documentada: un EAN-8 con prefijo 0 o 2 sigue sin consultarse aunque ML Kit diga EAN-8 (uso restringido GS1).
- Tests nuevos: 4 (dominio 2, app 2). `verificar.sh`: **666 tests JVM correctos** (domain 188, app 309), «Todo correcto».
- Capturas: nº 60–62 del escáner en `tools/capturas/generar.py`; portada 0.21.5; `CAPTURAS_0.21.5.pdf` (63 páginas) sustituye a `CAPTURAS_0.21.3.pdf`.
- Docs: PRUEBA_ESCANER (limitación → resuelta), README, RELEASE, PRUEBAS_DISPOSITIVO §4.6, REVISION_FINAL, CAPTURAS.

## Prompt 55 — 0.21.6 (38): depuración exhaustiva
- Petición: «Realiza un debugging y depuración exhaustiva del proyecto». Opción elegida: corregir lo claro con tests y dejar lo dudoso como propuesta. Informe completo en `docs/DEPURACION_0.21.6.md`.
- Línea base: `verificar.sh` sobre 0.21.5 (666 tests) + detekt sin Gradle + lectura dirigida (corrutinas, fechas, SQL, concurrencia ficha/ventas, protocolo, claves en memoria, Compose).
- **F1** `FiltrosEntrada.decimal()`: la coma se escribe como punto. **F2** `core.result.runCatchingCancelable` (relanza `CancellationException`) en escáner, formularios, inventario, licencia y avisos del servidor. **F4** `Estadisticas.rango/serie` e `InicioUseCases`: días y meses por fecha local (el cambio de hora de Cuba es a las 00:00). **F5** búsqueda literal en Registros (`escaparLike` + `ESCAPE '\'`). **F6** `ClienteSync.pedir`: sin respuesta = `IOException`, no cancelación. **F7** `sumarStock`: sumar siempre se permite. **F8** la ficha abierta no deshace ventas: `Stock.cantidadAlGuardar(leida, enFormulario, actual)` dentro de la transacción; sobrecargas `actualizar(…, cantidadLeida)` en los repositorios (también en `…SegunTipo`); campos opcionales `GuardarProducto.cantidadLeida` / `GuardarInsumo.cantidadLeidaMil` (compatibles con 0.21.5). **F3** clave de `ClienteSync.abrir` borrada en `finally`; `rememberUpdatedState` en `VinculacionScreen`.
- Propuestas sin aplicar: P-a `stopSelf` tras fallo de `startForeground`; P-b reintento de `CierreRemoto`; P-c separar «Caducado» de «Próximo a caducar»; P-d tope de sockets en `ServidorSync`; P-e `Mutex` en abrir/cerrar sesión del cliente.
- Tests nuevos: 6 (core 2, domain 2, data 2) + aserción nueva en `FiltroEntradaTest`. `verificar.sh`: **672 tests JVM correctos** (core 16, licencia 59, domain 190, data 74, designsystem 24, app 309), «Todo correcto». Base de datos v7 sin cambios; sin cambios de interfaz (sigue `CAPTURAS_0.21.5.pdf`).
- Docs: DEPURACION_0.21.6, README, RELEASE, PRUEBAS_DISPOSITIVO §4.11, REVISION_FINAL.

## Prompt 56 — 0.21.7 (39): P-b aplicada y P-e explicada
- Decisión del usuario sobre las propuestas de la depuración: P-a no, **P-b sí**, P-c no, P-d no, P-e «explica detalladamente el fallo».
- **P-b** (`CierreRemoto`): `collectLatest` + `reintentar()` con `esperaReintento` 5 s, 10 s, 20 s… máx. 5 min; una venta que empieza cancela la espera (P43); excepciones = fallo reintentable (`runCatchingCancelable`); `aplicar()` devuelve `Resultado { CERRADO, ESPERA, OLVIDADA, FALLO }`. Antes, un `Err` dejaba el turno abierto hasta otra venta o reabrir la app (el flujo con `distinctUntilChanged` no volvía a emitir).
- **P-e**: explicación en `docs/DEPURACION_0.21.6.md` §6 (`cerrarSesion()` cierra la sesión vigente y no la que falló → puede cerrar una sesión nueva y sana; un comando ya aplicado se informa como fallido y, si se repite, se aplica dos veces). Corrección propuesta, sin aplicar: compare-and-set por sesión y, opcionalmente, comandos idempotentes.
- Test nuevo: 1 (data). `verificar.sh`: **673 tests JVM correctos** (core 16, licencia 59, domain 190, data 75, designsystem 24, app 309), «Todo correcto». Docs: DEPURACION_0.21.6 §3/§5/§6, README, RELEASE, PRUEBAS_DISPOSITIVO §4.11, REVISION_FINAL.

## Prompt 57 — 0.21.8 (40): P-e aplicada (sesión correcta + comandos idempotentes)
- Petición: «P e 2». Interpretación documentada: se aplican **los dos puntos** de la propuesta (el 2 depende del 1).
- **Punto 1** (`ClienteSync`): sesión vigente en `AtomicReference`. `cerrarSesion(s)` cierra solo la sesión usada y cambia el estado solo si seguía vigente (compare-and-set); el lector hace lo mismo. `cerrarSesion()` queda para cierres intencionados.
- **Punto 2:** `Comando.clave` (UUID) y `HolaOk.comandosUnicos`, opcionales y compatibles; `VERSION_PROTOCOLO` sigue en 1. Nueva `MemoriaComandos` en la principal (200 resultados por empleado+clave, en RAM, con `Mutex`); `EjecutorComandos.resultado(comando, empleado)`. La secundaria reenvía una vez con la misma clave por una sesión nueva, y solo si la principal lo anuncia.
- Tests nuevos: 4 (`ComandosUnicosTest`). `verificar.sh`: **677 tests JVM correctos** (core 16, licencia 59, domain 190, data 79, designsystem 24, app 309), «Todo correcto».
- Docs: DEPURACION_0.21.6 §6/§7, VINCULACION §0.21.8, README, RELEASE, PRUEBAS_DISPOSITIVO §4.11 (paso 7), REVISION_FINAL. Sin cambios de interfaz (sigue `CAPTURAS_0.21.5.pdf`).

## Prompt 58 — 0.21.9 (41): pruebas ampliadas
- Petición: «prueba todas las demás funcionalidades y elementos del proyecto exhaustivamente». El usuario eligió **ampliar** (tests JVM nuevos para lo que no tenía prueba, sin Robolectric/Roborazzi) y **corregir** los fallos claros (lo dudoso, como propuesta).
- **Room en la JVM** (`tools/verificacion/jvm-room`, paso 8 de `verificar.sh`): adaptador `SupportSQLite` → `sqlite-jdbc` delante de Room en el classpath. Así se ejecutan por primera vez los tests instrumentados de `:data` que usan Room en memoria, más `MigracionesJvmTest` (versión JVM de `EsquemaTest`, con control negativo). El proyecto Gradle no cambia.
- Tests nuevos: `InventarioRoomTest` 10, `SincronizacionRoomTest` 6, `MigracionesJvmTest` 5, `LicenseValidatorTest` 7, `ServiciosUseCasesTest` 5, `ServiciosViewModelTest` 5, `VinculacionViewModelTest` 8. Los 15 tests de Room que ya existían pasan.
- **B1 corregido** (`VentaRepositoryImpl`): un producto borrado mientras estaba en el carrito daba «sin existencias». Ahora da «ya no existe», igual que los Elaborados y servicios.
- `verificar.sh`: **702 tests JVM correctos** (core 16, licencia 66, domain 195, data 79, designsystem 24, app 322) y **36 tests de Room en la JVM**, «Todo correcto».
- Docs: `docs/PRUEBAS_0.21.9.md` (nuevo), README, RELEASE, PRUEBAS_DISPOSITIVO, REVISION_FINAL (L2 parcialmente cubierta). Sin cambios de interfaz (sigue `CAPTURAS_0.21.5.pdf`).

## Prompts 59–60 — solicitud de prueba y licencia emitida por GL (sin cambios de código)
- Solicitud: `docs/GL_SOLICITUD_PRUEBA.txt` (MENSUAL, 2 secundarias, dispositivo de prueba `SPVI:golden0000000001`).
- Respuesta de GL guardada en `tools/licencia/respuestas/licencia_gl_mensual_2.txt` (ID d53a020b-4062-4825-8219-024f997173f8).
- `probar_gl.py verificar … --tipo MENSUAL --secundarias 2` → **ACEPTADA**: firma `gl-sign-v1` válida, descifrado correcto, `estado` ACTIVA, emitida 2026-10-03T20:03:02.754Z, vence 2026-11-02T20:03:02.754Z (30 días), `secundarias` 2 (GL ya copia el campo), datos de la solicitud intactos.
- Controles negativos: iv alterado → firma inválida; ID cambiado → no abre; esperar 3 secundarias → rechazada.

## Prompt 61 — propuesta de licencia más fluida (sin cambios de código)
- Pregunta: «¿existe algún mecanismo o flujo de licencia más fluido, pero sin perder seguridad?».
- Propuestas: L-a «Compartir → SPVI» con activación directa; L-b licencia corta de un SMS; L-c QR; L-d nº de transacción en la solicitud; L-e renovar sin perder días + «Renovar igual».
- Descartadas por seguridad o por las reglas: servidor de activación, códigos no atados al teléfono, claves simétricas en el APK, leer SMS o portapapeles.

## Prompt 62 — 0.22.0 (42): licencia corta, QR y renovación sin perder días
- Petición: «implementa todas las recomendaciones menos la a y la d» → **L-b, L-c, L-e**. Detalle y análisis de seguridad en `docs/LICENCIA_0.22.md`.
- **L-b** `contract/LicenciaCorta.kt`: `SPVI1:` + Base64url de cuerpo 44 B ‖ firma cruda 64 B (150 caracteres, un SMS). La firma es la de `gl-sign-v1`, con dominio `SPVI-L1|`. La huella es SHA-256(deviceId + devicePub)[0..16]. En la activación también se comprueba que el teléfono posee la clave privada (ECDH de prueba). `LicenseManager`: activación corta/larga, re-verificación al arrancar y Migrar con licencia corta. `EcP256.rawToDer/derToRaw`. Sin cambios de almacenamiento.
- **L-c** `LectorQrLicencia.kt`: diálogo con cámara (solo QR, también en el bloqueo) y lectura desde imagen con `PickVisualMedia` + ML Kit. Sin permisos nuevos. `CamaraEscaner` acepta `formatos` y `descripcion`. `LicenciaViewModel` recibe `ConfiguracionInicialRepository` (permiso ya pedido) y gana `qrLeido`.
- **L-e** `RequestPayload.renueva` (no se escribe si es null) y `licenciaCorta = true`. `LicenseManager.licenciaRenovable`, que `LicenciaRepositoryImpl` añade a la solicitud. Botón «Renovar igual» y frase «no pierdes días» en el paso 2.
- GL: `docs/GL_PROMPT_0.22.md` (especificación, respuesta por SMS/WhatsApp/QR, cálculo de la renovación con sus registros, vector de referencia) y nueva `docs/GL_SOLICITUD_PRUEBA.txt` (renovación de d53a020b…, debe vencer el 2026-12-02). `probar_gl.py`: `--renueva`, verificación de la corta (y de las dos formas a la vez) y autoprueba con corta, QR y renovación.
- Tests: `LicenciaCortaTest` 12 (incluye un vector escrito en Python y un clon con deviceId y clave pública copiados). `GoldenVectorTest.verifyRealLicense` ya usa la **licencia real de GL**. `LicenciaViewModelTest` +4 y `LicenciaFormTest` +3.
- Docs: LICENSE_CLIENT §2/§3.1/§6/§8, MANUAL_USUARIO §9, PRUEBAS_DISPOSITIVO §4.8 punto 6b, Ayuda (Licencia), README, RELEASE, REVISION_FINAL.
- `verificar.sh` (v20): «Todo correcto»: 721 tests JVM (core 16, licencia 78, domain 195, data 79, designsystem 24, app 329) + 36 de Room en la JVM. La v19 encontró un fallo: el tema «Licencia» de la Ayuda tenía 6 pasos (máximo 5); se condensó.
- Maquetas: 4 páginas nuevas (nº 63–66) → `~/CAPTURAS_0.22.0.pdf`, que sustituye a 0.21.5.

## Prompt 64 — 0.23.0 (43): solicitud y licencia cifradas, mensaje «datos + código», QR por WhatsApp
- Petición: «siempre licencia corta, QR de la solicitud solo vía WhatsApp; formato: texto introductorio con datos + renglón + solicitud cifrada; características de la licencia + renglón + licencia cifrada; pruebas, prompt para GL, actualizar PDF». Respuestas del usuario: la corta, **cifrada** (2 SMS está bien); QR en los dos sentidos solo por WhatsApp; el texto de la solicitud con todos los datos; solo se activa la corta. Detalle en `docs/LICENCIA_0.23.md`.
- `:licencia`:
  - `contract/SolicitudCifrada.kt`: `SPVIR1:` (ECDH + HKDF `SPVI-R1` + GCM, epk comprimida, sin firma) y `MensajesLicencia` (solicitud y licencia).
  - `LicenciaCorta.kt` reescrita a `SPVI2:` (cuerpo cifrado hacia la clave del teléfono + firma de GL encima, 216 caracteres).
  - `EcP256.comprimir/descomprimir`.
  - `RequestPayload` v2: `devicePub` comprimida, sin `appName` ni `licenciaCorta`.
  - `LicenseManager`: `buildRequest` → `SolicitudGenerada(texto, codigo)`; activar solo `SPVI2`; la larga instalada se sigue re-verificando.
  - Quitados el sellador del envelope de la solicitud y `LicenseMessageParser`.
- `:domain` / `:data`: `construirSolicitud` devuelve `SolicitudGenerada`.
- `:app`:
  - evento `Enviar(via, texto, codigo)`;
  - `PngQr` (PNG en JVM puro) y `Contacto.whatsappConImagen` (`ACTION_SEND` a WhatsApp/Business con FileProvider y `jid`; si falla, `wa.me`);
  - textos del paso 2 y Ayuda.
  - Sin permisos nuevos.
- Tests:
  - `LicenciaCortaTest` reescrito (13);
  - `RuntimeVerificationTest` reescrito;
  - `FakeGl` con el emisor `SPVI2` y la larga v1 para la compatibilidad;
  - `LicenseFlowTest`: formato de la solicitud, la larga ya no se activa, solo cuenta el código;
  - `GoldenVectorTest`: la larga real de GL instalada sigue activa;
  - `PngQrTest` (2).
  - `TestEmisor` (androidTest) pasa a `SPVIR1`/`SPVI2`.
- GL:
  - `docs/GL_PROMPT_0.23.md`: especificación, descompresión en Java, texto exacto del mensaje, vector con efímera y firma de prueba, compatibilidad.
  - `docs/GL_SOLICITUD_PRUEBA.txt` + `.png` (renovación de d53a020b…, debe vencer el 2026-12-02).
  - `probar_gl.py` reescrito (`solicitud --qr`, `verificar`, `autoprueba`, `vector`); el vector queda en `tools/licencia/vector_0.23.txt`.
- `verificar.sh` (v21/v22): «Todo correcto». 722 tests JVM (core 16, licencia 77, domain 195, data 79, designsystem 24, app 331) + 36 de Room en la JVM.
- Maquetas: nº 64–65 actualizadas y 66–68 nuevas (solicitud por WhatsApp con QR, respuesta de GL por WhatsApp, licencia por SMS) → `~/CAPTURAS_0.23.0.pdf` (69 páginas), que sustituye a 0.22.0.

## Prompt 65 — 0.23.1 (44): licencia solo como texto (sin QR)
- Petición: «el manejo de una imagen de QR en el mismo teléfono puede ser complicado para un usuario no avanzado; elimina la licencia en QR, solo se enviará como texto». El usuario eligió quitar también el lector (cámara e imagen) del paso Activar y el QR de la solicitud.
- `:app`:
  - borrados `LectorQrLicencia.kt` (diálogo de cámara y lectura desde imagen) y `PngQr.kt`;
  - `Contacto.whatsappConImagen` quitado: WhatsApp vuelve a `wa.me` con el texto;
  - `LicenciaViewModel` sin `qrLeido`, `camaraSolicitada` ni `ConfiguracionInicialRepository`;
  - evento `Enviar(via, texto)`;
  - `TextosQrLicencia` → `TextosActivacion.INSTRUCCION`;
  - Ayuda (Licencia) y texto del paso 2 sin QR.
  - `:licencia` sin cambios (formato `SPVIR1`/`SPVI2` igual).
- Tests: quitados `PngQrTest` (2) y los del QR/cámara de Licencia (3). Nuevo `soloSePegaElMensajeElCodigoBastaAunqueVengaSolo`.
- GL:
  - `docs/GL_PROMPT_0.23.md`: solo texto, sin QR ni imágenes, con botón «Copiar mensaje»;
  - borrado `docs/GL_SOLICITUD_PRUEBA.png`;
  - `probar_gl.py` sin `--qr`.
- `verificar.sh` (v23): «Todo correcto». 718 tests JVM (core 16, licencia 77, domain 195, data 79, designsystem 24, app 327) + 36 de Room en la JVM.
- Maquetas: licencia nº 63–67 (sin el diálogo «Escanear licencia» ni QR en los chats) → `~/CAPTURAS_0.23.1.pdf` (68 páginas), que sustituye a 0.23.0.
- Prompt 66: `docs/GL_PROMPT_0.23.md` revisado (punto 0: quitar SPVI1, QR y licencia larga de GL; extracción tolerante a saltos de línea; validaciones; botón «Copiar mensaje»; pruebas d y e). `GL_PROMPT_0.22.md` marcado como obsoleto.

## Prompt 67 — 0.24.0 (45): la Descripción distingue artículos con el mismo nombre
- Petición: «Descripción debe utilizarse para describir de forma resumida características de un producto que faciliten su identificación… ejemplo Nombre (Cerveza Cristal) Descripción (Lata 350 ml Superior); cuando 2 o más productos tienen el mismo nombre se le exige al usuario que los diferencie mediante la descripción antes de ingresarlos».
- Decisiones del usuario (ask_user):
  - se muestra en todas partes;
  - máximo 40 caracteres en una línea;
  - los repetidos existentes se marcan y se exige diferenciarlos al editar;
  - alcance: productos, Elaborados y servicios (insumos sin cambios).
- `:domain`:
  - `model/Identificacion.kt`: `nombreCompleto` («Nombre · Descripción»), `clave` (sin mayúsculas, tildes ni espacios repetidos), `conflicto`, `porDiferenciar`.
  - `Validadores.MAX_DESCRIPCION` = 40 (antes 500) y `Validadores.descripcion` (una línea).
  - `GuardarProducto` / `GuardarServicio` aplican la regla: falta → `Validacion(descripcion, REQUERIDO)`; repetida → `Duplicado(descripcion)`. Parámetro `exigirDiferenciar` (solo el escáner lo apaga al cambiar la caducidad).
  - `TipoAlerta.NOMBRE_REPETIDO`, `ConteoAlertas.nombreRepetido`, filtro en `Stock` e `InventarioFiltro`, `ItemInventario.nombreRepetido`, `VistaServicios.porDiferenciar`.
  - La venta (`PlanificadorVenta`, servicios en `VentaUseCases`), el Top 3, «Usado en» y la lista de precios usan `nombreCompleto`.
  - Ficha: título y primera línea con nombre completo.
  - Inventario en Excel/PDF: nueva columna «Descripción».
- `:data`:
  - movimientos y «sin existencias» con el nombre completo;
  - `EjecutorComandos` repite la regla para los comandos de las secundarias.
- `:app`:
  - formularios de producto y servicio: Descripción de una línea con ejemplo, contador «N/40» y aviso en vivo (con las descripciones existentes); mensajes REQUERIDO / FORMATO / RANGO / repetida;
  - filas de Inventario, Venta, Servicios, Precios, tarjetas y diálogos con el nombre completo;
  - alerta «Nombre repetido: añade descripción» en Inicio (`AlertTone.Revisar`, número en `primary`), chip «Nombre repetido» en el filtro y subtítulo en las filas;
  - Ayuda: «Agregar productos» con un 5.º paso.
- Sin cambios en la base de datos (v7), el respaldo ni el protocolo. Las ventas antiguas conservan el nombre congelado entonces.
- Tests:
  - `IdentificacionTest` (9);
  - `ProductoFormTest`: `nombreRepetidoExigeDescripcionDistintaEnVivoYAlGuardar` y `descripcionResumidaConAyudaYMensajes`;
  - `InicioLogicTest`: tono `Revisar`.
- `verificar.sh` (v24): «Todo correcto». 729 tests JVM (core 16, licencia 77, domain 204, data 79, designsystem 24, app 329) + 36 de Room en la JVM.

## Prompt 70 — 0.25.0 (46): todo lo pendiente del PLAN_0.25
- Petición: «aplicar todo lo pendiente». Orden pedido: primero los pendientes pequeños, después los tests nuevos y luego `verificar.sh` hasta «Todo correcto». Suposiciones 1–4 de PLAN_0.25 aceptadas.
- Especificación de GL:
  - redacción de los puntos 210/216 de `GL_PROMPT_0.23.md`;
  - los parsers de `SPVIR1` toleran espacios (Kotlin y `probar_gl.py`);
  - nuevo `docs/GL_ESPECIFICACION.md`.
- `:licencia`:
  - `RequestPayload.recupera` y texto «Recuperar licencia SPVI»;
  - `ListaRevocaciones` (firma `SPVI-REV1|`, huellas `SPVI-REV|`) e `IdLicencia`;
  - `LicenseManager.aplicarRevocaciones` y marca de revocación (`Revoked`).
- `:domain`:
  - `EstadoApp` (recordatorio de respaldo, actualizaciones, licencia recuperable);
  - `Arqueo`, `MovimientoCaja`, fondo y contado obligatorios;
  - `AnularVenta`, `ModificarVenta` (precios originales, `Devueltos`), `ComprobarActualizaciones`, `FondoSugerido`;
  - las estadísticas usan solo ventas válidas; columna Estado y campos de arqueo en las exportaciones.
- `:data`:
  - BD v8 (`MIGRACION_7_8`) y respaldo v4, que importa también v3;
  - sincronización con campos opcionales (protocolo v1): conteo de la secundaria, cambios de ventas y APK por la red local (`ApkLocal`, bloques de 256 KB);
  - `ActualizacionesRepositoryImpl` (GitHub Releases, SHA-256);
  - `EstadoAppRepositoryImpl`.
- `:app`:
  - diálogos de caja (`CajaUi`/`CajaViewModel`), pestaña Caja del turno;
  - Anular/Modificar en la ficha de venta (`VentaAcciones`), marcas ANULADA / «Corrige #N»;
  - aviso de respaldo;
  - tarjeta de actualización (Inicio/Ajustes) con `PackageInstaller`, y «Buscar actualizaciones» en Ajustes;
  - «Licencia transferida» con `ACTION_DELETE`;
  - campo de recuperación en Licencia (rellenado desde el respaldo);
  - activación automática al compartir `SPVI2:` a SPVI.
- Manifiesto: `REQUEST_INSTALL_PACKAGES` y `REQUEST_DELETE_PACKAGES` (autorizados en P68b; añadidos a `permitidos`).
- Tests nuevos:
  - `Version025Test` (domain, 20): arqueo, caja, anular/modificar, recordatorio, versiones, comprobación y revocación;
  - `RecuperacionTest` (licencia, 7): `recupera`, extracción del ID, firma de la lista (válida, ajena, alterada), aplicación solo a la licencia instalada, y compatibilidad con la lista firmada por `probar_gl.py`;
  - `Version025AppTest` (app, 5).
  - Ajustados `TablasExportTest` (Estado), `TurnoPersistenciaTest` (v8) y `Version020SyncTest` (respaldo v4).
- Correcciones de compilación halladas por la verificación:
  - `SpviSpacing.sm` no existe (→ `xs`);
  - `spviAnimateItem()` fuera de una lista;
  - un parámetro de constructor que tapaba la propiedad `licencia` en `EntornoIntegracion`.
- Herramientas: `probar_gl.py` con `solicitud --recupera` y `revocadas firmar|verificar`, y la autoprueba ampliada; `vector_0.25.txt`; `docs/GL_SOLICITUD_RECUPERACION_PRUEBA.txt`.
- Documentación:
  - `docs/GL_PROMPT_0.25.md`;
  - README, RELEASE (§8 publicar en GitHub), SECURITY (§4 bis), MANUAL_USUARIO (caja, anular/modificar, recordatorio, recuperar, actualizar) y FORMATOS (respaldo v4, columnas).
- Desviaciones (README «Novedades de la 0.25.0»):
  - `PackageInstaller` sin FileProvider;
  - Modificar sin cambiar el método ni añadir artículos;
  - el conteo de un cierre pedido no se puede descartar;
  - la tarjeta de actualización solo aparece en Inicio/Ajustes y no se descarga en segundo plano;
  - el campo de recuperación solo aparece sin licencia;
  - el arqueo se ve en la pestaña Caja, sin PDF/Excel del turno;
  - una venta de servicios anulada llega a la secundaria en su siguiente sincronización.
- `verificar.sh` (v9): «Todo correcto». 761 tests JVM (core 16, licencia 84, domain 224, data 79, designsystem 24, app 334) + 37 de Room en la JVM.
- Sin ejecutar: Gradle/lint/R8, Roborazzi, el teléfono real y la prueba con dos teléfonos. El PDF de capturas no se ha regenerado para las pantallas nuevas.

## Prompt 71: PDF de capturas 0.25.0 y limpieza

- `CAPTURAS_0.25.0.pdf` (90 páginas): portada nueva, nº 1–67 de la entrega anterior y 22 maquetas nuevas (`tools/capturas/generar.py`): nº 68–69 Descripción (0.24.0); nº 70–76 arqueo de caja; 77–80 anular/modificar ventas; 81 aviso de respaldo; 82–85 actualizaciones; 86–89 recuperación de licencia y «Licencia transferida».
- Eliminados por estar sustituidos o ya aplicados: `CAPTURAS_0.23.1.pdf`, `docs/GL_PROMPT_0.21.md`, `docs/GL_PROMPT_0.22.md`, `docs/LICENCIA_0.22.md`, `docs/PLAN_0.21.md`, `docs/ANALISIS_IMPACTO_0.19.md`, `docs/PROPUESTA_COLOR.md`, la carpeta temporal `rv/` y las cachés. Se corrigieron las referencias en README, REVISION_FINAL, LICENSE_CLIENT, VINCULACION, LICENCIA_0.23 y GL_PROMPT_0.23.

## Prompt 72 — 0.25.1 (47): exportaciones revisadas, turno compartible y desviaciones de la 0.25.0 resueltas

- Petición: primero capturas de todas las exportaciones (textos, PDF, Excel, imágenes); después aplicar todo `PLAN_0.25.1` (A, B, C, D) más las correcciones de exportación halladas (E, aprobadas con «procede»). Entregar al final un PDF único con pantallas y exportaciones e índice, los .md para compilar y retocar con OpenCode Desktop, y borrar los PDF y .md obsoletos.
- Revisión de exportaciones (0.25.0, `tools/exportaciones/`: `Muestras.kt` con el código real, `render.py` que replica PdfWriter/ImagenTabla/Tarjetas, `paginas.py`):
  - Excel dañado (formato `"CUP"` sin escapar en `styles.xml`);
  - columnas cortadas en los PDF de 7–9 columnas;
  - «SPVI · SPVI · Servicios»;
  - el texto del inventario lleva costos sin avisarlo.
- **A. Compartir el turno:** `TablasExport.turno()` (Resumen, Arqueo de caja, Caja, Ventas, Movimientos) y `nombreArchivoTurno`; icono Compartir en el detalle del turno; hoja «Compartir turno» con PDF/Excel → enviar o guardar (`TurnosViewModel`, `TurnoDetalleScreen`).
- **B. Modificar venta a pantalla completa** (`registros/ModificarVenta.kt`): cantidades, quitar, añadir artículos (precio actual; los originales conservan el suyo), método de pago con los datos de la transferencia obligatorios, y motivo. `ModificarVenta` (dominio) acepta líneas nuevas y método.
- **C.** Campo de recuperación también con la licencia instalada vencida (`Licencia.permiteRecuperar()`).
- **D.** Conteo de la secundaria con «Ahora no»: el aviso de Inicio ofrece «Contar»; vuelve al regresar a Inicio o a los 15 minutos; nada se cierra sin conteo.
- **E. Exportaciones:**
  - formato Excel `#,##0.00 \C\U\P`;
  - `DisenoPdf.kt` (hoja horizontal con más de 5 columnas, anchos naturales para columnas compactas, números a la derecha) usado por `PdfWriter`;
  - título «Servicios»;
  - Inventario → Exportar ofrece Texto y marca cada formato «Uso interno (incluye costos)» / «Para clientes (sin costos)»;
  - nombres de hoja con fecha legible (`/`→`-`, `HH:mm`→`HH.mm`, espacios juntados).
- Tests nuevos o ampliados:
  - `ModificarVentaTest` (5);
  - `DisenoPdfTest`;
  - `EscritoresTest` (styles.xml bien formado, nombres de hoja);
  - `TablasExportTest` (turno completo con arqueo);
  - `TurnoViewModelsTest`, `LicenciaFormTest`, `InicioViewModelTest`, `InventarioLogicTest`;
  - `Version025AppTest` (Servicios sin «SPVI ·» duplicado).
- `verificar.sh` (v11–v13): «Todo correcto». 779 tests JVM (core 16, licencia 84, domain 225, data 84, designsystem 24, app 346) + 37 de Room en la JVM. La v12 detectó que el test del Excel esperaba el nombre de hoja antiguo; se ajustó junto con la regla de espacios.
- Documentación:
  - README (Novedades de la 0.25.1, permisos en `spviPermisos`), MANUAL_USUARIO, FORMATOS (§2.1, §2.3, §2.4), RELEASE (pruebas 13–16), SECURITY (versión);
  - nuevos `AGENTS.md`, `docs/OPENCODE_DESKTOP.md` y `.opencode/commands/` (compilar, probar, capturas, release, retoque).
- Entrega: `SPVI_0.25.1_capturas_y_exportaciones.pdf` (portada, índice enlazado con marcadores, pantallas nº 1–96 con 8 maquetas nuevas de la 0.25.1, exportaciones E1–E22). `tools/capturas/ensamblar.py` lo arma.
- Eliminados por estar sustituidos o ya aplicados: `CAPTURAS_0.25.0.pdf`, `EXPORTACIONES_0.25.0.pdf`, `docs/PLAN_0.25.1.md` (aplicado entero). `docs/PLAN_0.25.md` se conserva porque el código cita sus apartados (§3, §6).

## Prompt 73 — 0.26.0 (48): PLAN_0.26 (respaldo, botón +, sin texto, fondo asignado, seminegrita, actualizaciones obligatorias)

Plan confirmado: [docs/PLAN_0.26.md](PLAN_0.26.md).
- **§1 Respaldo:** solo el archivo `.spvi` cifrado (se quita el PDF de configuración).
- **§2 Botón +:** abajo a la derecha (`FabPosition.End`) en Inventario, Precios y Servicios.
- **§3 Sin exportaciones de texto:** se quitan `texto*` y `Compartir.texto`. Quedan la solicitud de licencia, el contacto de Soporte y el pegado/compartido de SMS.
- **§4 Fondo asignado:** BD v9 (`MIGRACION_8_9`) y campos opcionales del protocolo ([VINCULACION.md](VINCULACION.md) §0.26.0). La principal asigna el fondo de cada turno de una secundaria; sin él, la secundaria no abre. Una secundaria 0.25.x abre como antes y la principal pide «Actualiza la app de X».
- **§5 Seminegrita:** `SpviTextos.dato`/`datoEn`/`PESO_DATO` para los datos en la UI; seminegrita en el PDF y negrita en Excel para importes, cantidades y fechas (sin códigos de barras ni teléfonos).
- **§6 Actualizaciones obligatorias:** aplazables 30 días desde la primera detección; luego `BloqueoActualizacion` en toda la app, con Actualizar, Exportar respaldo y Cerrar turno. Se quitan el interruptor y la versión descartada.
- **§8 Excel:** cada columna mide lo que su contenido, de 4 a 80 unidades, con ajuste de texto.
- **Tests:** `Version026Test`, `SeminegritaTest`, migración 8→9, fondo, plazos y anchos.

## Prompt 74 — 0.26.0 (48): la prueba no se reinicia al reinstalar

Ver [PLAN_ANTIREINSTALACION.md](PLAN_ANTIREINSTALACION.md).
- **Registro cifrado** (`licencia/prueba/RegistroPrueba.kt`): AES-GCM con clave PBKDF2 a partir de ANDROID_ID. Se guarda en cuatro sitios: imagen PNG en Imágenes/SPVI, `.bin` en Download y en Documents, y copia interna. Gana la fecha más antigua y una copia ilegible se ignora.
- **`LicenseManager`:** usa `RegistroExterno` y `DetectorRetroceso` (`elapsedRealtime` dentro del mismo arranque, combinado con `lastSeen`).
- **Implementación Android:** `RegistroPruebaAndroid` / `DetectorRelojAndroid` (MediaStore en 10+, `File` en 8–9).
- **Permisos nuevos**, autorizados solo para esto: `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` (≤ 32) y `WRITE_EXTERNAL_STORAGE` (≤ 28). El diálogo «Guardar tu periodo de prueba» se muestra una vez, recién instalada.
- **UI:** `TrialViewModel` + `TrialState` como fachada; la pantalla que bloquea al vencer sigue siendo la puerta de Licencia.
- **Rechazado por el usuario:** dar la prueba por vencida por colisión de nombre.
- **Tests:** `RegistroPruebaTest` (14), `EstadoPruebaTest` (3), `TrialViewModelTest` (4).
- **Verificación (P73 + P74):** `verificar.sh` «Todo correcto». 823 tests JVM (core 16, licencia 98, domain 238, data 92, designsystem 26, app 353) + 38 de Room; los permisos están dentro de la lista ampliada. Se entrega `SPVI_0.26.0_capturas_y_exportaciones.pdf` (127 páginas: nº 1–108, E1–E16) y se borra el de la 0.25.1.

## Limpieza para OpenCode (0.26.0)

Se retiraron los documentos de trabajo antiguos: `docs/PLAN_0.25.md`, `docs/DEPURACION_0.21.6.md`, `docs/PRUEBAS_0.21.9.md`, `docs/PRUEBA_ESCANER.md` y `REVISION_FINAL.md`. Su contenido vigente está en este historial (Prompts 53, 55, 58 y 70), en `Contexto.md`, `Pruebas.md` y `Pendiente.md`. Las menciones que quedan arriba son históricas.

## 0.27.0 · Claves de GL del 05/10/2026

- GL regeneró su par de claves al desinstalarse y reinstalarse ([GL_CONTEXTO_LICENCIAS.md](GL_CONTEXTO_LICENCIAS.md)). `LicenseTrust`: ECDH **sustituida** (`sha256:2a3f:9fce:…:666d`); firma vigente **añadida** (`sha256:58d4:3aac:…:8c4e`) en primer lugar, conservando la del 30/09/2026 (`sha256:8a91:…`) para que las licencias ya emitidas sigan verificando.
- Las solicitudes hechas con SPVI anterior a la 0.27.0 van cifradas hacia la ECDH vieja: GL no puede abrirlas; el cliente actualiza SPVI y vuelve a pedirla.
- Las huellas `sha256:4ea0:…` / `sha256:219e:…` que GL da como invalidadas nunca se fijaron en SPVI (SPVI pasó de `7b51`/`8a91` a las nuevas).
- `tools/licencia/probar_gl.py` usa las claves nuevas (y acepta la firma anterior).

## 0.27.0 (49) · Pulido de interfaz, acceso con clave, respaldo sin contraseña y clientes fijos

Especificación: [PROMPT_0.27.0.md](PROMPT_0.27.0.md) (T1–T14) más cuatro pedidos del dueño en P78 (N1–N4). Implementado en la copia propia de la 0.26.0, no en la de OpenCode: **los arreglos que OpenCode hiciera para compilar la 0.26.0 hay que volver a aplicarlos** si siguen haciendo falta.

| Tarea | Qué se hizo |
|---|---|
| T1 | `SpviTopBar` = `CenterAlignedTopAppBar` sin logo (`showLogo` eliminado, `marca` para «SPVI» en Inicio); diálogos y hojas sin logo, título centrado |
| T2 | Fuera `maxLines = 1` en textos de Inicio (turno, accesos, datos), leyendas, selectores, cabeceras de Inventario; `SpviListItem` hasta 3 líneas. Capturas con `LetraGrande` (fontScale 2) de Inicio, ficha, Licencia y arqueo |
| T3 | `SpviMedalla` + tokens `MEDALLA_*` y `ContrastTest` |
| T4 | `SpviMotion` FAST 150 / LONG 250, `muelle()`; botón con `animateContentSize(muelle)`; barra inferior con muelle; `profileinstaller` |
| T5 | `Deslizar.kt`: regla pura (ruta raíz exacta + sin diálogo, selección, búsqueda ni foco) con `DeslizarTest` |
| T6 | Placeholder centrado en `SpviTextField` |
| T7 | `SpviIcons.Pdf`/`Excel`, `iconoFormato()` en todas las hojas de exportar |
| T8 | Textos llanos (tabla abajo). Ningún `e.message` llega al usuario |
| T9 | `Vinculacion.puedeVincularseComoSecundaria`; botón oculto en la principal; `PRINCIPAL_NO_SECUNDARIA` en el ViewModel |
| T10 | `BackupCipher` v4 con indicador; sin contraseña, clave de un secreto interno (10 000 iteraciones); lee v3. Interruptor «Proteger con contraseña» apagado por defecto; Migrar mantiene 4 pasos |
| T11 | `androidx.biometric` 1.1.0, `MainActivity` → `FragmentActivity`, `AccesoClave.kt` (regla pura de 10 min con `elapsedRealtime`, overlay con `FLAG_SECURE`), paso opcional del recorrido y fila en Ajustes. Preferencia en `AjustesDispositivoRepository` (claves `disp.*`, fuera del respaldo) |
| T12 | Soporte con iconos (`SpviListItem`), pie nuevo |
| T13 | `SelectorFoto` (foto primero, Cámara con `TakePicture` + `FileProvider` `cache/fotos/`, Galería con Photo Picker, Quitar). Los Elaborados no tienen foto (como antes). `LADO_MAX` = 1024 px y JPEG 85 ya cumplen «≤ 1080 px» |
| T14 | Reparto de alertas por filas (regla pura con test) y contenido centrado |
| N1 | Dona de Inventario: los insumos cuentan en su medida (milésimas → unidades de su medida, sin «u»), números neutros (formato `Cantidad`: `12.5`, punto decimal como en el resto de la app) |
| N2 | **Clientes fijos**: BD v10 (`cliente_fijo`, `transaccion.clienteFijo`, `MIGRACION_9_10`), alta/actualización por carné en la misma transacción de la venta (también al recibir ventas de una secundaria), sugerencias (hasta 3, sin tildes ni mayúsculas, primero las que empiezan igual), pestaña Registros → Clientes (ficha, quitar, sin exportar, `FLAG_SECURE`), respaldo con `clientesFijos` opcional (sin subir la versión). La secundaria no recibe la lista de la principal (solo la suya y lo que envía) |
| N3 | Logo con fondo transparente; splash con el color de ventana del tema |
| N4 | Diálogos y hojas suben desde abajo al centro y bajan al cerrarse (`ventanaEntra`/`ventanaSale`). Los cierres que decide el código (no el usuario) siguen siendo inmediatos |
| GL | Claves de GL del 05/10/2026 (ver la entrada anterior) |

**T8 · textos antes → después**

| Dónde | Antes | Después |
|---|---|---|
| Permiso de fotos (prueba) | «SPVI guarda en Imágenes una imagen pequeña y cifrada con la fecha de inicio… para que SPVI la encuentre si reinstalas…» | «Permite el acceso a fotos para que SPVI pueda conservar los datos de tu periodo de prueba.» |
| Turno sin arqueo | «Sin arqueo (turno anterior a 0.25.0)» | «Sin arqueo (turno de una versión anterior de SPVI)» |
| Secundaria antigua | «Su app es anterior a la 0.26.0 y escribe su propio fondo: actualízala» | «Su app necesita actualizarse para recibir el fondo que le asignas» |
| Respaldo antiguo | «…versión de prueba de SPVI anterior a la 0.13.0 y esta versión ya no lo abre.» | «Se creó con una versión muy antigua de SPVI que esta versión ya no abre.» |
| Licencia (envío) | «Se abrirá WhatsApp/Mensajes con tus datos y la solicitud cifrada.» | «…con tu solicitud de licencia.» |
| Manual, paso 4 | Explicación del mecanismo de la imagen y la reinstalación | Solo que el acceso a fotos sirve para conservar los datos de la prueba |

**Verificación** (`verificar.sh`, «Todo correcto»): 846 tests JVM (core 16, licencia 99, domain 242, data 96, designsystem 27, app 366) y 41 de Room; `10.json` = `MIGRACION_9_10`. Dos tests antiguos ajustados: `UseCasesTest` (cuerpo que devolvía valor) y `RespaldoPasosTest` (sin contraseña el formulario ya es válido).

**Suposiciones:** el placeholder centrado no cambia el texto escrito ni la etiqueta; el acceso con clave usa `BIOMETRIC_WEAK | DEVICE_CREDENTIAL` en API 30+ y en 26–29 la combinación que admite la librería; las cantidades siguen el formato de `Cantidad` (Locale.US, «12.5»), no «12,5»; la lista de clientes fijos no se distribuye a las secundarias (protocolo sin cambios de versión: solo un campo opcional en la venta).


## 0.27.1 (versionCode 50) · P79: pruebas pendientes con Gradle real

Primera vez que el proyecto se compila con Gradle real (JDK 17, SDK 35, Gradle 8.11.1, AGP 8.7.3) en vez de solo con `verificar.sh`. No hay emulador ni teléfono en el entorno (sin `/dev/kvm`): las pruebas en dispositivo y entre dos teléfonos siguen pendientes.

**Ejecutado:**
- `assembleDebug`;
- `spviCheck` (tests, lint y permisos);
- `assembleRelease` (R8);
- `recordRoborazziDebug`: 154 capturas × 2 temas;
- `aapt2` sobre los APK debug y release;
- `tools/verificacion/api_minima.py` (nuevo).

**Errores reales encontrados y corregidos:**

| # | Problema | Corrección |
|---|---|---|
| 1 | `LocalDate.ofInstant` es API 34: Inicio se cerraba en Android 8–13 | `Dates.localDate(instant, zona)` en `core` (9 llamadas) |
| 2 | `BigInteger.longValueExact` es API 31: convertir dinero y cantidades cerraba la app en Android 8–11 | `BigDecimal.movePointRight(n).longValueExact()` en `Money` y `Cantidad`; `ApiMinimaTest` |
| 3 | Letra al 200 %: las alertas de Inicio partían «inven·tario» y las tarjetas Pago/Precios se cortaban | `alertasPorFila` (ancho mínimo de 96 dp por alerta) y accesos apilados si no caben |
| 4 | Letra al 200 %: el importe del arqueo salía «12,35 / 0.00 / CUP» | `CajaUi.Fila` con `FlowRow`: el importe baja entero a la línea siguiente |
| 5 | Subtítulos de lista cortados con «…» (Ajustes, Exportar) | `SpviListItem.subtitleMaxLines` pasa de 1 a 3. En Exportar, «Incluye costos» en vez de «Uso interno (incluye costos)» |
| 6 | Ajustes: Respaldo decía «y PDF» y Permisos solo hablaba del escáner | «Copia cifrada de todo e importar». «Cámara: se pide solo al escanear o al hacer una foto» |
| 7 | Apps vinculadas: las opciones Automática/Manual quedaban pegadas | Separación y altura táctil mínima de 48 dp |
| 8 | Arnés de capturas: la barra superior de la actividad de prueba tapaba la parte de arriba de las capturas (solo en los tests) | `capturar()` la oculta con `LaunchedEffect`. Lint rechazó la primera versión, que usaba `remember` |

**Resultado:**
- 850 tests JVM sin fallos (core 19, licencia 99, domain 242, data 96, designsystem 27, app 367);
- lint sin errores;
- permisos del APK dentro de la lista;
- R8 sin clases ausentes;
- `api_minima.py`: 0 llamadas no permitidas.

**Límites:**
- Los tests de Room en la JVM (41) se ejecutaron por última vez con `verificar.sh` en la 0.27.0; esta versión no cambia la base de datos.
- Los diálogos con letra grande no se pueden capturar con `LetraGrande` (ver `CAPTURAS.md`).
- Android 14 con acceso parcial a fotos («Seleccionar fotos») no deja leer el registro de la prueba tras reinstalar.

## 0.28.0 · P80: muelles físicos, Registros sin gestos, licencia apilada y seminegrita en importes/fechas

1. **Animaciones con muelles físicos:** en `SpviMotion`, los deslizamientos de pantalla y de la barra inferior, los giros (flechas, FAB) y el progreso de los gráficos usan `spring()` (rígidez/damping según la pieza) en vez de duraciones fijas; los fundidos y los cambios de color siguen con `tween` porque no tienen desplazamiento.
2. **Registros sin gestos:** eliminados `InterceptorDeslizar`, `LocalInterceptorDeslizar` y `RegistrarDeslizar` (`navigation/Deslizar.kt`); el desplazamiento con el dedo solo cambia de sección (Inicio–Inventario–Servicios–Registros–Ajustes). Las pestañas de Registros se cambian tocándolas (MANUAL_USUARIO.md §navegación actualizado).
3. **Licencia apilada:** los 4 tipos (Mensual, Semestral, Anual, Perpetua) van en `Column` a ancho completo, uno debajo del otro; el precio de cada chip usa `supportingEsDato` (nuevo parámetro de `SpviChip`).
4. **Seminegrita en importes y fechas:** `SpviTextos.resaltar(texto, vararg datos)` (marca cada ocurrencia dentro de frases) y `SpviTextos.datoTexto(dato)` (dato solo en huecos `AnnotatedString`), con tests en `SeminegritaTest`; `SpviListItem` acepta `titleResaltado`/`subtitleResaltado`. Aplicado en Registros, TurnoDetalle, ModificarVenta, ClientesFijos, Venta, Caja, Vinculación, Inicio, Inventario, Onboarding y Licencia. Criterio: solo UI persistente (fuera: snackbar, SMS/compartir, accesibilidad, PDF/Excel, lienzos de gráficos, filtros, placeholders, campos editables y conteos); solo importes (`Money`/`Cup`) y fechas (`Dates`).

**Nota:** `spviTests` en Windows deja 1 fallo preexistente y ajeno a esta versión (`EscanerRedTest.almacenSoloBorraDentroDeSuCarpeta`: `uriDe` construye `file://C:\…` y el test espera `file:///`; en Android las rutas empiezan por `/` y funciona). En Linux pasa (850 tests en la 0.27.1).

## 0.28.1 · Animaciones apreciables en el teléfono

El dueño reportó que las animaciones seguían imperceptibles con el teléfono en la mano. Causas encontradas probando en el dispositivo (grabación de pantalla + fotogramas con `ffmpeg`):
1. El teléfono tenía las tres escalas de animación del sistema a `0.0` (se pusieron a `1.0` por `adb`; en MIUI: Ajustes → Accesibilidad → Visión → «Quitar animaciones» apagado, o las tres «Escala de animación» a 1x en Opciones de desarrollador). Con ellas a cero, Compose ejecuta todo al instante y ningún parámetro se ve.
2. El deslizamiento de ventanas recorría solo 1/10 del ancho y el cambio de pestaña era solo un fundido de 200–250 ms (entre pantallas oscuras no se nota).

**Cambios** (todo en `SpviMotion`, `designsystem/token/Tokens.kt`):
- Nuevo `muelleEntrada()` (rigidez 350, amortiguación 0,85: blando, con leve asentamiento de ~0,4 s) para las ENTRADAS: navegación adelante/atrás, `enterVertical`, `ventanaEntra` (ventanas emergentes) y aparición de la barra inferior. Las salidas siguen inmediatas con `muelle()`.
- Navegación con dirección: recorrido 1/10 → 1/4 del ancho.
- Cambio de pestaña: fundido + zoom sutil 0,96 (`ZOOM_ENTRADA`, sin dirección porque no hay avance ni retroceso).
- Comentarios de `MainScaffold` actualizados. Test nuevo `MovimientoApreciableTest` (fija rigidez, amortiguación y zoom).

**Verificado en el teléfono** (23021RAAEG, Android 15, build debug reinstalada): transición de pestaña con fundido progresivo en 3 fotogramas; deslizamiento Servicios → Nuevo servicio y Servicios → Ayuda capturados a mitad de recorrido (viaje largo + fantasma de la saliente); sin FATAL en logcat. No se creó ningún dato (solo navegación; la app quedó en Inicio). Nota: el codificador de `screenrecord` del equipo pierde cuadros en movimientos rápidos a 1x (avisa `err=-22` y graba a 720p); con cámara lenta a 5x el recorrido se ve completo.

## 0.29.0 · Semilla de datos de prueba (Bodega cubana, 18 meses)

Generador de base de datos de prueba **solo en debug** que satura la app con un historial coherente de 1 año y medio de ventas, para probar rendimiento, filtros y reportes sin cargar datos a mano.

1. **Arquitectura:** paquete nuevo `cu.spvi.domain.seed` en `:domain` con tres piezas — `CatalogoBodega` (catálogo fijo: 11 insumos, 32 productos con 2 elaborados y receta, 8 servicios con 3 que consumen insumos, 12 clientes fijos con CI válido, 2 tarjetas y 2 teléfonos de pago, vendedores María Fernández y Jorge Pérez por rachas de 2 semanas), `GeneradorSeed` (plan determinista con semilla 2907L: ~470 turnos de 6 días/semana, 6–14 ventas por turno, 22 % servicios, 30 % transferencia solo con cliente, caja semanal, descuadres ocasionales, ~1,5 % de ventas anuladas) y `OrquestadorSeed` (ejecuta el plan contra los repositorios públicos con fechas explícitas de La Habana, 8:00–20:00; fondo 2000 CUP encadenado entre turnos).
2. **UI debug:** fila «Generar datos de prueba» en Ajustes, visible solo con `BuildConfig.DEBUG` (precedente `mostrarCatalogo`), con diálogo de progreso por día (`SeedViewModel` + `DialogoSeed` en `app/.../ajustes/seed/`). Borra todo primero (avisa en el diálogo) y re-completa el onboarding con los datos del catálogo. En release no hay rastro: la fila no existe con `mostrarSeed = false`.
3. **Decisiones (R1–R6 del ledger):** sin git en el entorno se omiten los commits; generador determinista en `:domain` por repositorios (rechazados DAO directo y respaldo `.spvi`); el plan truncado se reconstruyó desde la spec como autoridad; `clienteClave` como `claveFija`/`EVT:` + base-26 (los nombres solo admiten letras); `numeroTransaccion` con relleno a 6 (el validador exige 6–30); anuladas por día (15 % de días ≈ 1,5 % de ventas, como pide la spec); `EjecutorSeed` como `fun interface` con `operator fun invoke` (el `typealias` de función no genera binding Hilt) más `ioSeed` para tests deterministas.

**Límites:**
- Los movimientos de inventario ALTA/AJUSTE/BAJA llevan la fecha actual (`clock.now()` dentro de `ProductoRepositoryImpl.mov()`), no la histórica; el stock final sí queda coherente porque las salidas por venta usan la fecha del turno.
- Sin fotos en los datos generados.
- `FakeVentas.anular` no devuelve los insumos consumidos; el colchón del +20 % en la compra inicial lo absorbe.
- Los tests de `:app` usan fakes locales propios (no ven los `Fakes` de `:domain`).

## 0.29.1 · Escáner: solo nombre y caducidad, sin fotos

El dueño reportó que el escáner funcionaba mal: los nombres en línea llegaban con basura (texto de mercadeo de UPCitemdb) y se cortaban a los 80 sin que el resto fuera a ningún lado.
1. **Dividir en vez de truncar:** nuevo `NombreEnLinea.dividir()` (nombre ≤ 80 en palabra completa + resto a la descripción ≤ 40); `BuscarProductoEnLinea` lo usa y `ProductoEnLinea` gana el campo `descripcion`, que viaja por `Route.ProductoForm` hasta el formulario.
2. **Sin fotos en el escáner:** se elimina la descarga (`DescargarFoto` deja de usarse en el escáner; la clase sigue para uso futuro), el `AsyncImage` del resultado y los estados `fotoUri`/`cargandoFoto`. La foto del producto se sigue poniendo a mano en el formulario.
3. La fecha de caducidad sigue poniéndola el usuario (o sale de Room si el producto ya existe): ninguna base pública da caducidad.

**Nota:** `DescargarFoto` y sus tests en `:domain` se conservan (código muerto a propósito, sin wiring en `:app`).

## 0.29.2 · Tops de empleado y cliente en Inicio

Dos listas Top 3 en el resumen general de Inicio (ventana fija de 30 días, como el Top 3 actual; no siguen al selector de período): **Empleado** (vendedores con mayor importe vendido, por el nombre congelado de la venta) y **Cliente** (clientes con mayor importe comprado, solo por transferencia: el efectivo no registra cliente; se distingue por nombre + CI). Solo ventas válidas; empate → alfabético; ocultas si no hay datos. Cálculos puros `Estadisticas.topEmpleados/topClientes` sobre la misma lista que ya carga `ObtenerResumenGeneral` (nuevo `TopPersona` + campos en `ResumenGeneral`); tarjetas con medalla y el importe con seminegrita. Tests en `TopsInicioTest`; fixture y capturas de `InicioCapturas` re-grabadas. (MANUAL_USUARIO.md §5.4)

## 0.29.3 · Escáner solo-nombre sin fecha + Top Empleados visible

1. **Escáner simplificado** (a petición del dueño: del número escaneado solo se extrae el nombre; la fecha sale del escáner): `BuscarProductoEnLinea` devuelve el nombre limpio cortado a 80 (el resto se descarta); se eliminan `NombreEnLinea.dividir` y `ProductoEnLinea.descripcion`. En la app salen `descripcion`/`caducidad` del estado del escáner, el campo de fecha del resultado y el botón Guardar para producto existente (la tarjeta queda informativa); el formulario abre solo con código + nombre. `Route.ProductoForm` pierde los params `descripcion`/`caducidad` (el formulario conserva su propio campo de fecha). Tests reescritos (`EscanerViewModelTest`, `ProductoFormTest`, capturas `08i` re-grabada) y `MANUAL_USUARIO.md` §2 actualizado.
2. **Top Empleados no salía (bug):** `VentaRepositoryImpl.entre`/`deTurno` no rellenaban `vendedor` (solo lo hacía `obtener`), así que `ObtenerResumenGeneral` recibía las ventas con vendedor vacío y `topEmpleados` las filtraba todas. Nueva consulta por lote `VentaDao.vendedoresDe(ids)` + helper `conVendedores` (test `VendedoresVentaTest`); de paso, las exportaciones con columna Vendedor vuelven a mostrarlo.

## 0.30.0 · Sin código de barras (QR solo para vinculación)

A petición del dueño se elimina el código de barras del producto de todo el proyecto; la cámara queda solo para el QR de vinculación y la foto del producto.

1. **Dominio:** fuera `Producto.codigo`, `InventarioRepository.porCodigo`, `ConsultarCodigo`, `BuscarProductoEnLinea`, `DescargarFoto`, `FuenteProductos`/`ProductoEnLinea`/`CodigoBarras`/`SimbologiaCodigo`/`ResultadoBusquedaEnLinea`, `PoliticaConsultaEnLinea` y `consultasEnLinea` de `Preferencias` (con su `guardar` y el consentimiento de Ajustes y onboarding). La búsqueda del Inventario y los preajustes filtran por nombre/descripción/categoría.
2. **Datos:** fuera `codigo` de la entidad, el DAO, los mappers, `ProductoDto` y el respaldo (lector tolerante: los respaldos viejos con `codigo`/`consultasEnLinea` se importan igual); fuera `FuentesRed`/`ScannerDtos` y su test (con él desaparece el fallo Windows de `EscanerRedTest`). Migración 10→11 (recrear tabla sin `codigo`, test `Migracion1011Test`); User-Agent `SPVI/0.27 (Android)`.
3. **App:** borrado `app/.../escaner/`; nuevo lector mínimo `vinculacion/qr/` (`QrVinculacionScreen` + VM + `CamaraEscaner` solo-QR). Sin botón Escanear en Inicio ni hoja Escanear/Escribir en Inventario (FAB único +); sin campo código en el formulario; sin fila de internet en Ajustes.
4. **Registros:** la ficha de venta muestra el unitario debajo (`lineasVenta`: «2 × Pan» + «10.00 CUP c/u», test `RegistrosTest`).
5. Docs actualizados (`README`, `MANUAL_USUARIO` §2, `FORMATOS`, `SECURITY`, Ayuda) y capturas re-grabadas.


## 0.30.0 — Verificación real (F0 del plan de correcciones, 2026-10-07)

Primera vez que el repositorio se verifica solo: GitHub Actions ejecuta Gradle real en cada push
(`.github/workflows/ci.yml`, cinco trabajos independientes). Corridas en verde:
[37656256703](https://github.com/rmdvcd/SPVI/actions/runs/37656256703) y
[37657849682](https://github.com/rmdvcd/SPVI/actions/runs/37657849682); las cifras coinciden entre corridas.

- **825 tests JVM, 0 fallos** — y los 3 archivos de tests instrumentados que no compilaban (`RepositoriosRoomTest`,
  `ConfiguracionInicialInstrumentedTest`, `RespaldoRoomTest`) vuelven a compilar.
- **Lint: 0 errores, 95 avisos** con el baseline vacío (se decidió no tapar los avisos con el baseline).
- **R8 sin clases ausentes**: APK release sin firmar 45,4 MB, APK debug 58,7 MB.
- **Permisos** del manifiesto fusionado dentro de la lista autorizada y **API mínima 26**: 3423 clases, 0 llamadas
  no permitidas.
- `gradlew` y los guiones de `tools/` estaban guardados sin permiso de ejecución (modo 644): el primer intento de CI
  murió con «Permission denied» (exit 126). Corregido.
- Versión a **0.30.0 (`versionCode` 51)**. El historial de verificaciones ya no vive en el README: vive en
  [VERIFICACION.md](VERIFICACION.md).
- **Pendiente (T0.6):** instalar el APK en un teléfono real y probar dos teléfonos (principal + secundaria).

## 0.30.1 — F1 (1/2): trabajo pesado fuera del hilo principal (2026-10-07)

- **T1.1** `@IoDispatcher` se traslada de `cu.spvi.data.di` a `cu.spvi.domain.di` (antes no se podía usar desde
  los casos de uso). `ObtenerGraficosPeriodo` y `ObtenerResumenGeneral` envuelven todo su cuerpo en
  `withContext(io)`: con el período «Año» ya no cargan, mapean y agregan miles de ventas en el hilo de UI.
- **T1.2** `flowOn(io)` en las seis cadenas reactivas que transformaban listas completas en el colector
  (`ObservarAlertas`, `ObservarInventario`, `ObservarRegistro`, `ObservarElaboracion`, `ObservarElaborados`,
  `ObservarServicios`).
- **T1.3** `debounce(250 ms)` en los tres buscadores que consultan Room por texto (Inventario, Registros y
  Servicios); el texto espera el retardo y el resto de filtros pasan al instante; con el texto vacío el
  retardo es 0 (abrir la pantalla no espera). El filtro que ve la UI sigue siendo inmediato. **Los buscadores
  de Precios y ModificarVenta NO** llevan debounce: filtran en memoria sobre un catálogo ya cargado, sin
  consulta que ahorrar (el plan decía 4 ViewModels; la realidad del código son 3).
- **T1.5 (esqueleto)** `RendimientoInicioTest` (instrumentado) que genera los 548 días del seed real y medirá
  la agregación de un año (umbral 250 ms) y los dos casos de uso de Inicio ya con `withContext(io)` (umbral
  3000 ms). Necesita teléfono: los números aparecerán en `docs/VERIFICACION.md`.
- **T1.4 (pendiente)** la agregación sigue siendo en memoria sobre la lista completa (la serie del año
  sigue haciendo `.entre(…).validas()`). Siguiente paso: `@Query` con `GROUP BY` por cubo y rango, manteniendo
  `Estadisticas` como autoridad en los tests y comparando SQL vs memoria.
- CI: **826 tests JVM, 0 fallos** (+1, el test nuevo del debounce); lint 0/95; R8 limpio; permisos OK; API 26 OK.
  Corrida verde: [37704972362](https://github.com/rmdvcd/SPVI/actions/runs/37704972362).

## 0.30.0 — Corrección del seed: recetas en gramos/mL, no en kg/L (2026-10-08)

- La BD de prueba mostraba Ganancia Neta muy negativa (p. ej. costo 544 550 CUP contra ventas de 10 130 CUP
  en un turno): las recetas de `CatalogoBodega` estaban escritas ×1000 (200_000 milésimas = 200 kg de harina
  por pan con lechón, 500 L de gasolina por entrega a domicilio). `LineaRecetaSeed` documenta «cantidad en
  milésimas de su unidad» (1000 = 1 kg/L/unidad) y el costo real sale de la receta
  (`Recetas.costo`: Σ precio × milésimas / 1000), no del costo declarado.
- Recetas corregidas: pan con lechón 200 g harina / 150 g carne / 20 mL aceite / 5 g sal (levadura 10 g, ya
  estaba bien porque su unidad es el gramo); dulce de coco 60/50/20 g (además era antieconómico: costaba 320
  contra venta de 150; ahora 64); entrega a domicilio 0,3 L de gasolina (90 < 150); lavado 100 mL de
  detergente (40 < 120). El hilo (1 bobina por arreglo, 150 < 250) no se tocó.
- `CatalogoBodegaTest` nuevo: todos los productos con costo declarado < venta, y toda receta de elaborado o
  servicio con costo de receta < venta/importe (matemática entera en milésimas-pesos, sin redondeo).
- Solo afecta al seed de debug («Generar datos de prueba»): sin cambio de esquema, versión ni permisos.
  Suite local: **828 tests JVM, 0 fallos** (+2).
