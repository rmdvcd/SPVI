# SPVI — Plan de correcciones propuesto

> Estado al 2026-10-08: F0 y T1.1–T1.4 tienen evidencia histórica; la última CI completa confirmada es [37834591679](https://github.com/rmdvcd/SPVI/actions/runs/37834591679), commit `3bf4fc1`, 5/5 trabajos y 836 tests JVM aprobados. La CI [37836886846](https://github.com/rmdvcd/SPVI/actions/runs/37836886846) de `43cc5df` quedó con resultado final desconocido; no contarla como verde.
> T2.1–T2.5 están implementadas en el código, pero T2.4/T2.5 y los cambios posteriores aún requieren ejecutar los tests desde OpenCode CLI en el PC del propietario. T1.5 y las capturas T2.1/T2.5 siguen pendientes. La prueba manual de tres teléfonos T2.2 fue dispensada por solicitud del propietario; no cuenta como validada. Los instrumentados solo se compilaron en la última CI completa.
> La evidencia y las cifras están en [docs/VERIFICACION.md](docs/VERIFICACION.md). Por instrucción del propietario, el agente no ejecuta builds, app, tests, adb ni capturas.
>
> Rama de trabajo de esta sesión: `arena/c64c3dc9-spvi`.

---

## 0. El hallazgo que cambia la prioridad (nuevo, y grave)

El remoto `https://github.com/rmdvcd/SPVI` es un **repositorio público** y contiene el código completo, incluido
`data/src/main/kotlin/cu/spvi/data/respaldo/BackupCipher.kt` con sus constantes:

```kotlin
private val MASCARA = intArrayOf(0x5A, 0x13, 0x7C, 0x29, 0x44, 0x61, 0x0E, 0x38)
private val SECRETO = intArrayOf(0x09, 0x43, 0x2A, 0x60, ...)
const val ITERACIONES_SIN_CONTRASENA = 10_000
```

Consecuencia directa: **el respaldo `.spvi` «sin contraseña» ya no protege de nada frente a nadie**. Lo que
`SECURITY.md` describe como *«riesgo aceptado: cualquiera con SPVI (o que extraiga el secreto del APK) puede
abrir ese archivo»* es hoy una afirmación optimista: **no hace falta el APK, ni SPVI, ni ingeniería inversa; el
secreto está publicado.** Cualquiera con el archivo (WhatsApp reenviado, Drive compartido, tarjeta SD) y veinte
líneas de Python lee ventas, clientes con carné y teléfono, y el ID de la licencia. La exportación actual protege con contraseña **por defecto**, pero el usuario aún puede desactivarla expresamente; los archivos antiguos y las nuevas copias exportadas sin contraseña siguen sin confidencialidad.

Lo bueno, y lo digo claro para que no cunda el pánico:

- **No hay ningún keystore ni credencial publicada** (verificado: `*.jks`, `keystore.properties` y `.env`
  no están en el árbol remoto).
- **El modelo de licencias sigue en pie**: la clave privada de firma de GL (`gl-sign-v1`) y la clave privada
  ECDH de GL **nunca han estado en el repositorio**; publicar las claves **públicas** y el contrato
  (`docs/GL_ESPECIFICACION.md`, `LICENSE_CLIENT.md`) es información, no una brecha: sin la privada no se puede
  emitir una licencia válida ni firmar una lista de revocadas. Un APK reempaquetado seguiría sin poder
  actualizarse (Android exige la misma firma).
- Los vectores de `tools/licencia/vector_*.txt` usan datos sintéticos (`CI 00000000000`, «Prueba Compatibilidad
  Spvi»), no datos reales.

**Traducción a prioridades: la corrección del respaldo sin contraseña sube del puesto 5 al puesto 2**, solo por
detrás de tener una verificación real. Mientras se decide, el riesgo está vivo y es de datos personales de
terceros (los clientes del negocio).

---

## 1. Flujo de validación acordado

El propietario indicó que **OpenCode CLI en su PC** ejecuta compilación, aplicación y pruebas. El agente de Arena solo analiza y modifica el proyecto; no ejecuta Gradle, adb, emuladores, tests ni Roborazzi, y no continúa el seguimiento de una CI remota que haya quedado sin resultado observado.

- No se declara una tarea verificada hasta recibir el resultado de OpenCode CLI para el commit exacto.
- La compilación de instrumentados no demuestra que esos tests se ejecutaron. Generar capturas tampoco sustituye su revisión visual.
- La CI de GitHub sigue configurada; el último run completo confirmado es `37834591679` para `3bf4fc1`.
- Run `37836886846` de `43cc5df`: última observación parcial con algunos trabajos aprobados y Release/R8 en curso; conclusión final desconocida. No se infiere el resultado.
- Los comandos y el formato del informe están en [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md); los resultados, en [docs/VERIFICACION.md](docs/VERIFICACION.md).

## 2. Reglas de trabajo durante todo el plan

1. **Un PR por fase**, con su entrada en `docs/HISTORIAL_DESARROLLO.md` (regla del propio proyecto).
2. **No se declara validado sin evidencia.** El propietario ejecuta el flujo desde OpenCode CLI en su PC; si falta un resultado para el commit exacto, la tarea se marca `NO VERIFICADO` y no se cierra. El agente de Arena no compila ni corre tests.
3. **No se toca** lo que el proyecto declaró intocable: lista de permisos, claves de GL fijadas, ausencia de
   telemetría, protocolo de sincronización **v1** (compatibilidad con secundarias 0.24–0.27 ya instaladas),
   DTO de respaldo v4 y archivo `.spvi` v4 (se sigue leyendo lo anterior).
4. **Cambio de esquema = v12 + `Migration(11, 12)` + `12.json` + test de migración.** Sin excepciones.
5. **El README solo puede afirmar lo que un comando reproducible demuestre**, y la afirmación debe citar el
   comando y la fecha. Esto se convierte en una comprobación automática (T3.6).
6. Trabajar siempre en la rama asignada a esta sesión; en esta continuación es `arena/c64c3dc9-spvi`. Los PR salen
   de esa rama; si uno no está fusionado al empezar el siguiente, el segundo será apilado y se avisará.

---

## 3. Fases

| Fase | Qué | Estado |
|---|---|---|
| F0 | CI y verdad documental | **Evidencia histórica verde** hasta `3bf4fc1` / run `37834591679`; resultado posterior `43cc5df` desconocido. Cambios actuales sin validación |
| F1 | Rendimiento | **T1.1–T1.4 con evidencia**; T1.5 **NO VERIFICADO** (instrumentados compilados, no ejecutados ni medidos); T1.6 opcional |
| F2 | Seguridad | T2.1–T2.3 implementadas; T2.4–T2.5 implementadas en `43cc5df`; tests de los últimos cambios no ejecutados. Faltan capturas T2.1/T2.5. La prueba manual de tres teléfonos T2.2 se dispensó a solicitud del propietario; no se considera validación |
| F3 | Coherencia documental | README/esquema, manual, guía de dispositivo e índices actualizados; script y job de docs añadidos, pendientes de ejecutar |
| F6 | Higiene | `.kotlin/` ignorada, `!!` retirados de Kotlin de producción e índice de referencias duraderas añadido; pruebas instrumentadas siguen pendientes |


Esfuerzos en **días de trabajo**; entre paréntesis, quién verifica en el teléfono cuando hace falta (tú).

### F0 · Red de seguridad y verdad documental — **bloqueante, 2–4 días**

> Objetivo: pasar de «nadie sabe si compila» a «cada push compila y se ve en un registro».

| ID | Tarea | Dónde | Hecho cuando |
|---|---|---|---|
| T0.1 | Workflow de CI con JDK 17, SDK 35 y trabajos independientes de tests JVM/compilación instrumentada, lint, release/R8, permisos, API mínima y documentación | `.github/workflows/ci.yml` | Cinco trabajos originales pasaron en `3bf4fc1`; el job de documentación es nuevo y aún no se ha ejecutado |
| T0.2 | Corregir los instrumentados obsoletos para que vuelvan a compilar | `data/src/androidTest` y `app/src/androidTest` | Compilaron en la CI `37834591679`; no se ejecutaron |
| T0.3 | Resolver fallos de Room/KSP, Hilt, lint, R8 y API mínima que salieran en la primera corrida | varios | Los cinco jobs originales pasaron en `37834591679`; los cambios posteriores aún no se han comprobado |
| T0.4 | Congelar la versión 0.30.0 (`versionCode` 51) | `app/build.gradle.kts`, `README.md` | Configuración fijada; build binario posterior no validado |
| T0.5 | Mantener `docs/VERIFICACION.md` y alinear README/AGENTS con la evidencia | README, `AGENTS.md`, `docs/VERIFICACION.md` | Estado histórico actualizado; job automático de docs nuevo, sin ejecución observada |
| T0.6 | *(tú)* Instalar el APK debug y el release firmado en un teléfono y abrirlo; probar dos teléfonos (principal + secundaria) vendiendo | teléfono | Primera evidencia de que la app **existe** fuera del compilador |

**Riesgo y contingencia:** tras meses sin ejecutarse, la primera corrida puede destapar más de lo previsto.
Reservo 1–2 días extra. Si `spviTests` falla en masa por algo estructural (p. ej. Kotlin 2.0.21 + AGP 8.7.3
vs. la versión de Compose), lo arreglo antes de tocar nada de lógica.

### F1 · Rendimiento: sacar el trabajo del hilo principal — **2–3 días**

> Objetivo: que con 18 meses de datos y el período «Año» la interfaz no se congele. Hoy **no existe un solo
> `flowOn`** en `app`/`domain`/`data`, y `InicioViewModel` agrega miles de ventas en el hilo de UI.

| ID | Tarea | Dónde | Hecho cuando |
|---|---|---|---|
| ✅ T1.1 | `withContext(io)` con `@IoDispatcher` inyectado en los casos de uso de Inicio (`ObtenerGraficosPeriodo`, `ObtenerResumenGeneral`) | `domain/.../InicioUseCases.kt`, `data/di` | Mismo resultado, fuera del hilo de UI |
| ✅ T1.2 | `flowOn(io)` en las cadenas reactivas que hoy transforman en el colector (`ObservarInventario`, `ObservarRegistro`, los `stateIn(viewModelScope)` que mapean) | `domain`, `app` | Ninguna transformación pesada en Main |
| ✅ T1.3 | `debounce(250 ms)` en los buscadores (Inventario, Registros, Precios, ficha) conservando el «sin parpadeo» actual | 4 ViewModels | 8 pulsaciones = 1 consulta |
| ✅ T1.4 | Agregación en SQL por ventanas calculadas en JVM, con `GROUP BY` por cubo/rango; `Estadisticas.serie` se conserva como referencia pura y `ObtenerGraficosPeriodo` conserva su firma pública | `VentaDaos`, `Estadisticas`, `InicioUseCases` | ✅ CI 37766967443: SQL vs memoria idénticos en seed; pruebas Room de rangos/DST, anulaciones, cubos vacíos, centavos y lotes pasan |
| ⏳ T1.5 — **NO VERIFICADO** | El test instrumentado de 18 meses/umbrales ya existe; hace falta ejecutarlo en dispositivo y capturar `Systrace`/`Perfetto` antes y después *(preferible teléfono gama baja)* | `app/src/androidTest` | La última CI solo lo compiló; no hay medición nueva ni trazas en `docs/VERIFICACION.md` |
| ⏭ T1.6 | *(opcional)* `baseline profile` real: el módulo ya trae `profileinstaller` pero no hay perfil generado | nuevo `:baselineprofile` | Arranque en frío medido |

**Riesgo:** mover la agregación a SQL puede cambiar redondeos (céntimos). Contingencia: los tests de
`Estadisticas` son la autoridad; ninguna firma pública cambia.

### F2 · Seguridad — **2–3 días**

| ID | Tarea | Dónde | Estado / condición para cerrar |
|---|---|---|---|
| T2.1 | Respaldo con protección por contraseña activada por defecto; el modo sin contraseña es explícito y muestra una advertencia clara | `respaldo/`, `RespaldoScreen`, `FORMATOS.md`, `SECURITY.md` | Código, textos y pruebas añadidos; decisión del propietario: conservar la opción explícita con advertencia. Falta que OpenCode CLI ejecute los tests y generar/revisar `09m_respaldo_sin_contrasena` |
| T2.2 | Protección de sesiones LAN: no sustituir la sesión previa hasta autenticar la primera trama cifrada; límites y espera creciente; protocolo v1 sin cambios | `data/sync/ServidorSync.kt` | Tests incluidos en la CI completa `37834591679`; la prueba manual de tres teléfonos se dispensó por solicitud del propietario y no cuenta como evidencia |
| T2.3 | Respuesta uniforme ante `Hola`, sin distinguir negocio/empleado desconocido, y límites de intentos en `vincular` | `ServidorSync`, `Protocolo` | Implementado; tests incluidos en `37834591679`. Sin cambio del protocolo |
| T2.4 | Caché en memoria del registro de prueba, TTL de seis horas e invalidación al cambiar día local o contexto del permiso; escrituras propias actualizan la caché | `RegistroPruebaAndroid`, `CacheLecturas` | Implementado en `43cc5df` con cinco tests nuevos; quedan sin validación local hasta recibir OpenCode CLI |
| T2.5 | Recordar la última respuesta correcta de GitHub y advertir en Ajustes después de 14 días, si nunca respondió o si retrocedió el reloj (solo con repositorio configurado) | `actualizacion/`, `ajustes/`, `EstadoApp` | Implementado en `43cc5df` con tests de estado/texto y captura `07t_ajustes_actualizaciones_atrasadas`; tests y captura pendientes de ejecución/revisión local |

### F3 · Coherencia documental y de producto — **2–3 días**

| ID | Tarea | Dónde | Estado / validación pendiente |
|---|---|---|---|
| T3.1 | Retirar las menciones vigentes a la búsqueda de productos por cámara, conservar la cámara solo para QR de vinculación/fotos y quitar el código de entrada sin uso | README, `AGENTS.md`, `SECURITY.md`, `UI_UX_IX.md`, `CAPTURAS.md`, Ayuda, `data/build.gradle.kts`, Design System, pruebas antiguas y herramientas | Cambios aplicados en el árbol. Se eliminó `tools/escaner/` y el filtro muerto, y se retiraron las maquetas de búsqueda por producto de `tools/capturas/generar.py`; se conservan los QR de vinculación y licencias. `EscanerInventarioTest` se renombró `InventarioTest`, conservando sus casos generales; queda sin validación ejecutable local |
| T3.2 | Documentar campo por campo las entidades de Room v11 | README | Tabla reescrita con 18 entidades. El comparador estático de esquema está añadido, aún no ejecutado |
| T3.3 | Revisar la codificación del historial y los documentos actuales | `docs/HISTORIAL_DESARROLLO.md` y docs vigentes | El historial se leyó como UTF-8 válido y no contiene U+FFFD ni secuencias habituales de mojibake; no necesitó reescritura |
| T3.4 | Reponer manual de usuario, pruebas de dispositivo y lista de pendientes; limpiar enlaces inexistentes | `MANUAL_USUARIO.md`, `PRUEBAS_DISPOSITIVO.md`, `Pendiente.md`, README y AGENTS | Añadidos/actualizados. Los enlaces del README esperan la ejecución de `tools/verificacion/documentacion.py` |
| T3.5 | Alinear encabezados de versión | `UI_UX_IX.md`, `SECURITY.md` | Encabezado UI/UX actualizado a 0.30.0; SECURITY ya declaraba 0.30.0 |
| T3.6 | Comprobar enlaces, versión y esquema del README automáticamente | `tools/verificacion/documentacion.py`, `.github/workflows/ci.yml` | Script y job añadidos; no se han ejecutado localmente ni por CI |

### F4 · Accesibilidad, IX y adaptatividad — **3–5 días**

| ID | Tarea |
|---|---|
| T4.1 | **Resuelta:** mantener los cinco destinos con solo iconos, sin etiquetas de texto |
| T4.2 | **Implementada en el árbol; validación pendiente:** `WindowSizeClass` activa lista-detalle desde 600 dp en Inventario, Registros y Servicios; fichas en panel persistente y diálogos con tamaño intrínseco (`IntrinsicSize`) limitado por el viewport. Tres capturas Roborazzi `w800dp` añadidas; OpenCode CLI debe compilar y generar/revisar baselines |
| T4.3 | Auditar los 27 objetos `Textos*` contra la interfaz (ampliando los tests existentes) y mantener la Ayuda alineada. Pasada estática de referencias realizada; se retiraron 8 constantes sin uso y se corrigieron textos/comentarios desfasados en Inventario, Servicios y el formulario de producto, además del comentario de CameraX/ML Kit para conservar explícito su uso en QR de vinculación. Ayuda de Registros alineada con «Exportar» y PDF/Excel, con aserción; falta revisión visual/semántica completa |
| T4.4 | **Implementada en el árbol; validación pendiente:** señal de progreso accesible en Inventario, Servicios y Registros mientras la búsqueda conserva la vista anterior; cobertura unitaria y de UI añadida |

### F5 · Versatilidad y producto — **autorizado por el propietario, implementar por fases**

El propietario autorizó las cuatro áreas. Como son reglas de negocio que afectan importes, impuestos, devoluciones y permisos locales, antes de modificar el esquema o cerrar una implementación hay que acordar el detalle funcional de cada fase; la autorización de alcance no sustituye esas reglas.

| ID | Tarea | Coste |
|---|---|---|
| T5.1 | **Multi-moneda** (CUP/MLC/USD + tasa de cambio + redondeo) con migración v12, en `Money`, gráficos, caja, exportaciones y respaldo v5 | **2–3 semanas** — el cambio más caro del informe |
| T5.2 | Descuentos por línea/venta, impuestos, devoluciones parciales | 1–2 semanas |
| T5.3 | Usuarios locales en el mismo teléfono (vendedor por turno) | 1 semana |
| T5.4 | Textos a `strings.xml` con los `Textos*` como envoltorio (cambiar terminología sin recompilar; i18n futura) | 1 semana |

### F6 · Higiene continua — **1 día, permanente**

- `.kotlin/` está añadido a `.gitignore` y el archivo de sesión rastreado se retiró del índice.
- El índice [docs/DECISIONES.md](docs/DECISIONES.md) reúne las referencias técnicas duraderas P37/P68b/P74/§5.4.
- Se retiraron las aserciones `!!` de Kotlin de producción en `core`, `domain`, `data`, `designsystem` y `app`; las invariantes usan accesos explícitos (`getValue`, `checkNotNull`/`requireNotNull`) o descarte seguro.
- Se quitaron `SpviExpandableFab` y sus helpers por falta de usos, más `exigirDiferenciar`, que solo servía al flujo de escaneo de productos ya retirado. No se ejecutaron pruebas.
- **Pendiente:** ejecutar `./gradlew spviInstrumentedTests` en el PC del propietario con teléfono/emulador; el agente no lo ejecuta.
- `docs/VERIFICACION.md` registra solo resultados observados; la nueva revisión no se añade como aprobada hasta recibir los resultados locales.

---

## 4. Calendario resumido

| Fase | Esfuerzo | Depende de | Verifica |
|---|---|---|---|
| **F0** Verificación y verdad | 2–4 días (+1–2 de contingencia) | Tu aprobación de CI y de la versión | CI + tú (APK) |
| **F1** Rendimiento | 2–3 días | F0 verde | Test de umbral + tú en gama baja |
| **F2** Seguridad | 2–3 días | F0 | Tests; la prueba manual T2.2 fue dispensada por solicitud del propietario |
| **F3** Documentación | 2–3 días | F0 (para el CI de docs) | CI |
| **F4** IX y adaptatividad | 3–5 días | 2 decisiones tuyas | Capturas nuevas |
| **F5** Producto | 3–6 semanas | Decisión de negocio | Tú |
| **F6** Higiene | 1 día | — | CI |

**Total de lo urgente y barato (F0+F1+F2+F3+F6): 9–14 días de trabajo**, de los cuales tú solo tienes que
invertir minutos en T0.6 (probarlo en el teléfono); la prueba manual T2.2 fue dispensada.

---

## 5. Lo que **no** haría (para que el plan no se desmadre)

- **No** reescribir el protocolo de sincronización a v2: funciona, está bien cifrado y es compatible con las
  apps 0.24–0.27 que puedan estar en la calle. Solo cambio el *orden* del cierre de sesión.
- **No** cambiar SQLCipher, Room, Hilt ni OkHttp por alternativas «más modernas».
- **No** meter multi-moneda en el mismo release que las correcciones críticas.
- **No** tocar la lista de permisos ni el modelo de licencias offline.
- **No** reorganizar módulos, paquetes ni nombres: coste alto, beneficio nulo ahora.
- **No** añadir telemetría, analítica ni «crash reporting»: rompería la promesa central del producto.
- **No** publicar la corrección de seguridad del respaldo mezclada con otra cosa: si un `.spvi` sin contraseña
  se ha compartido alguna vez, hay que avisar a quien lo tenga.

---

## 6. Decisiones confirmadas y bloqueos restantes

- **T2.1:** conservar el modo explícito de exportación sin contraseña con advertencia. La protección por contraseña sigue activada por defecto.
- **T4.1:** conservar los cinco iconos sin etiquetas en la barra inferior.
- **F5:** el alcance de las cuatro áreas está autorizado y debe dividirse en fases. Falta especificar las reglas de negocio antes de cambiar precios, impuestos, reembolsos, usuarios o el esquema de Room.
- **Validación física:** T0.6, T1.5, las capturas Roborazzi y el smoke test de release requieren OpenCode CLI y dispositivos del propietario. La prueba manual T2.2 de tres teléfonos fue dispensada por solicitud del propietario y no se contará como realizada. El agente no puede sustituir las observaciones físicas ni ejecutar las pruebas.

El código/documentación pueden quedar implementados mientras sus pruebas sigan marcadas como pendientes; no se cierra una tarea sin evidencia del commit exacto en [docs/VERIFICACION.md](docs/VERIFICACION.md).
