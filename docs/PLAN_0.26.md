# PLAN 0.26.0 (48) — ocho cambios pedidos tras la 0.25.1

Estado: **propuesta, sin aplicar**. No se toca código hasta tu confirmación (regla de P17).

## Respuestas ya dadas

- Respaldo: queda solo el archivo `.spvi` cifrado actual. Se quita el «Documento PDF».
- Fondo de caja: la principal asigna el fondo **de cada turno** de cada secundaria.
- Actualización obligatoria: al vencer el mes se bloquea **toda la app**, pero solo si de verdad hay una actualización.
- Seminegrita: en las pantallas **y** en los PDF y Excel.

---

## 1. Respaldo: una sola forma de exportar

- Ajustes → Respaldo deja solo «Exportar respaldo (.spvi, con contraseña)» e «Importar».
- Se elimina la tarjeta «Documento PDF» (perfil y preajustes):
  - textos `PDF_*` de `RespaldoLogic.kt`;
  - la acción del ViewModel;
  - el uso de `TablasExport.perfil`/`preajustes` en Respaldo.
- El formato del respaldo no cambia: sigue siendo v4.
- Afecta a `respaldo/RespaldoScreen.kt`, `RespaldoViewModel.kt` y `RespaldoLogic.kt`, sus tests, `FORMATOS.md` §2.1 y `MANUAL_USUARIO.md`.

## 2. Botón «+» en la esquina inferior derecha

- `FabPosition.Center` → `FabPosition.End` en Inventario, Servicios, Precios y Apps vinculadas.
- El menú desplegable de Inventario (Escanear / Escribir) se abre hacia arriba desde la esquina, con las etiquetas a la izquierda; el `EjeFab` ya lo admite.
- Se revisa que el «+» no tape la última fila: se añade relleno inferior a cada lista.
- **Se revierte la decisión de P24/P25** («+» centrado). Nueva venta y Escanear en Inicio no son «+» y siguen centrados.

## 3. Nada se exporta como texto (solo la licencia)

**Se elimina:**

| Dónde | Qué se quita |
|---|---|
| Registros (Ventas, Servicios, Transferencias, Movimientos) | «Compartir como texto» de la lista. El icono Compartir pasa a ofrecer solo PDF y Excel |
| Ficha de venta, de transferencia y de movimiento | Compartir texto (la ficha queda solo para ver; en la principal conserva Modificar y Anular) |
| Inventario → Exportar | Opción **Texto** (añadida en la 0.25.1) |
| Ficha de producto | Compartir → Texto (quedan PDF e Imagen) |
| Ficha de insumo | Compartir texto |
| Servicios → Exportar | Opción Texto |

- En código desaparecen:
  - `TablasExport.texto`, `textoVenta`, `textoTransaccion`, `textoFicha` y `textoFichaInsumo`;
  - los eventos `CompartirTexto`;
  - `FormatoSalida.TEXTO`;
  - `Compartir.texto` (si ya nadie lo usa);
  - sus tests.

**Se conserva:**
- Licencia: solicitud, renovación y recuperación por WhatsApp o SMS.
- Soporte → contacto: no es una exportación.
- Pegar o compartir el SMS de Transfermóvil **hacia** SPVI: es entrada, no salida.

**Suposición 1:** PDF, Excel, Imagen (lista de precios) y Tarjetas siguen compartiéndose como **archivo** con la hoja de Android, que puede incluir WhatsApp. No son texto. Si tampoco las quieres por WhatsApp, dímelo.

## 4. Fondo de caja: lo asigna la principal en cada turno de una secundaria

**Flujo:**
1. **Secundaria:**
   - Al tocar «Abrir turno» sin fondo asignado, ve «Pide al encargado el fondo de caja» y el botón **Pedir fondo**.
   - La petición viaja en la siguiente sincronización. Ya hace falta sincronizar para abrir un turno nuevo (P38).
2. **Principal:**
   - Inicio muestra «Luis pide abrir turno» (como las solicitudes de cierre).
   - En Apps vinculadas → Luis: **Asignar fondo** (importe, puede ser 0, propuesto con lo contado en su último cierre) → ✓.
   - También se puede asignar sin que lo pida.
3. **Secundaria:**
   - Al sincronizar recibe el fondo.
   - El diálogo «Abrir turno» lo muestra **sin poder editarlo** («Fondo asignado por el encargado: 1,500.00 CUP») → ✓.
   - El fondo se gasta en ese turno: el siguiente necesita una asignación nueva.
4. **La propia principal** sigue escribiendo su fondo como ahora.

**Técnico:**
- BD **v9** (`MIGRACION_8_9`):
  - `empleado.fondoAsignadoCent` (NULL = sin asignar);
  - `empleado.aperturaSolicitadaEn`.
- Sincronización (protocolo v1, campos opcionales):
  - `SincronizarOk.fondoAsignado`;
  - `Sincronizar.pideFondo`.
- En la secundaria el fondo recibido se guarda en `EstadoApp.fondoAsignado`.
- Respaldo: los dos campos nuevos son opcionales; el respaldo sigue en v4 y se lee igual.

**Suposición 2:** una secundaria 0.25.x (sin actualizar) seguiría pidiendo el fondo como antes. Con el punto 6 todas acaban en la 0.26.0, y la principal muestra «actualiza la app de Luis» mientras tanto.

## 5. Seminegrita en precios, importes, fechas y datos importantes

**Pantallas:**
- `:designsystem` define un estilo único `SpviTextos.dato` (`FontWeight.SemiBold`, 600) para no repetir estilos sueltos.
- Se aplica en:
  - el valor de `SpviListItem`;
  - los valores de las fichas (`Fila`/clave-valor);
  - los totales;
  - el arqueo (esperado, contado, diferencia);
  - precios en Inventario, Venta y Servicios;
  - las fechas de las filas de Registros;
  - los contadores de alertas;
  - la licencia (vencimiento, ID).
- `DESIGN_SYSTEM.md` lo documenta.
- Se revisa el contraste: no cambia, porque es el mismo color.

**PDF:**
- Las columnas que `DisenoPdf` ya reconoce como importe/número o fecha, y la fila de totales, se dibujan en seminegrita: `Typeface.create(…, 600, false)` en Android 9+ y negrita en 8.x.
- En las tablas Dato/Valor (resumen, arqueo, ficha), la columna Valor.

**Excel:**
- Excel no tiene seminegrita: las mismas columnas y los totales van en **negrita**.
- Siguen siendo números sumables.

## 6. Actualizaciones obligatorias, aplazables hasta un mes

**Comportamiento:**
- Solo se activa si existe una versión nueva:
  - en la principal: encontrada en GitHub;
  - en una secundaria: ofrecida por su principal.
- **Fecha límite** = el día que se detectó + 30 días.
- Hasta esa fecha:
  - la tarjeta de Inicio dice «Versión 0.26.1 disponible. Obligatoria desde el 03/11/2026»;
  - «Más tarde» la oculta hasta la próxima apertura de la app (como mucho una vez al día).
- Al llegar la fecha:
  - pantalla de bloqueo en **toda la app** con «Actualizar» (descarga + SHA-256 + instalación);
  - en la secundaria, «Actualizar desde la principal».
  - Nunca aparece en mitad de una venta: se muestra al terminarla o al volver a abrir la app.
- Si sale una versión aún más nueva, el plazo **no** se reinicia: cuenta desde la primera pendiente.
- Se quita el interruptor Ajustes → «Buscar actualizaciones». La consulta semanal (≥ 7 días, al abrir la app) es fija. «Buscar ahora» se queda.
- Sin `GITHUB_REPO` configurado no se consulta nada, así que nunca hay bloqueo.

**Riesgo que debes aceptar:**
- Si al vencer el plazo el teléfono no tiene datos móviles ni wifi con internet (o la secundaria no ve a la principal), la app queda bloqueada hasta conseguir conexión.
- **Suposición 3:** la pantalla de bloqueo permite además **Exportar respaldo**, para no perder datos.
- **Suposición 4:** un turno abierto se puede cerrar desde esa pantalla, para no dejar la caja sin arqueo.
- Si no quieres esas dos salidas, dímelo.

**Técnico:**
- `EstadoApp`: se añade `obligatoriaDesde: Instant?` (primera detección) y `aplazadaHasta`.
- Se quitan `versionDescartada` y `buscarActualizaciones`.
- Nuevas piezas:
  - `BloqueoActualizacion` en el `root` (como `BloqueoSecundaria`);
  - caso de uso `EstadoActualizacionObligatoria`;
  - tests de plazos: el día 29 avisa, el día 30 bloquea, una versión nueva no reinicia el plazo y sin versión no bloquea.

## 7. «✕ = Ahora no» en «Cuenta la caja para cerrar»

Ese texto **no está en la app**: era una nota mía en la maqueta nº 94. En la app, el ✕ solo lleva la descripción de accesibilidad «Ahora no» para TalkBack, que no se ve. Se quita de la maqueta. No hay cambio de código.

## 8. Ancho de columnas del Excel ajustado al texto

**Hoy:** ancho = nº de caracteres + 2, entre 8 y 60. Por eso:
- la cabecera en negrita y los textos con mayúsculas o letras anchas se quedan justos;
- las columnas cortas reciben 8 aunque sobre espacio;
- «Artículos» se corta en 60.

**Propuesta (`XlsxWriter`):**
- Se mide cada celda con una tabla de anchos por carácter (Calibri 11, la fuente de fastexcel): estrechas `i l . , ' |`; anchas `M W m w @`; mayúsculas y dígitos.
- Factor 1.1 para la negrita (cabecera, importes y totales).
- El importe se mide con el texto que se ve («1,450.00 CUP»), no con el número.
- Ancho = máximo + 1.5, con mínimo = el de la cabecera.
- Las celdas de más de 80 caracteres («Artículos» con muchas líneas) usan **ajuste de texto** con ancho 80, en vez de cortarse.
- Test: anchos esperados para cabecera, importe, fecha y texto largo.

---

## Lo que no cambia

Permisos (ninguno nuevo), licencia, protocolo (v1 con campos opcionales), respaldo v4 y lo demás de la 0.25.1.

## Entregables al aplicar

- Código y tests de los puntos 1–6 y 8, y `verificar.sh` hasta «Todo correcto».
- Maquetas actualizadas:
  - «+» en la esquina;
  - fondo asignado (secundaria y principal);
  - bloqueo por actualización;
  - Respaldo sin PDF;
  - fichas sin compartir texto;
  - seminegrita.
- Exportaciones regeneradas: PDF y Excel con seminegrita y anchos nuevos, sin textos.
- PDF único nuevo con índice. Se actualizan README, MANUAL, FORMATOS, DESIGN_SYSTEM, RELEASE, `AGENTS.md` y `OPENCODE_DESKTOP.md`, y se añade la entrada en HISTORIAL.
- Versión 0.26.0 (48).
