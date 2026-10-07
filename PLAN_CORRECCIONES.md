# SPVI — Plan de correcciones propuesto

> Estado: **F0 ejecutada y en verde** (2026-10-07). Corrida del CI:
> [37656256703](https://github.com/rmdvcd/SPVI/actions/runs/37656256703) — 5/5 trabajos, con el detalle en
> [docs/VERIFICACION.md](docs/VERIFICACION.md). Fases F1–F3 y F6 pendientes de empezar.
>
> Lo empujado a `arena/3c116bea-spvi`: `8c48aa3` (auditoría) → `f480e50`/`531cab3` (CI, tests rotos, versión
> 0.30.0, VERIFICACION.md). Sin PR abierto todavía.

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
líneas de Python lee ventas, clientes con carné y teléfono, y el ID de la licencia. Y está **activado por
defecto** (la casilla «Proteger con contraseña» viene apagada).

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

## 1. Cómo se verifica (esto define la forma del plan)

Hecho comprobado en este entorno: **solo tengo salida a GitHub, npm y PyPI**. `dl.google.com`, Maven Central y
Adoptium responden `HTTP 000` (bloqueados), y no hay JDK ni Android SDK instalados. Es decir: **ya no puedo
limitar el trabajo a leer código; pero tampoco puedo compilar ni ejecutar un test aquí.** Hay tres formas de
cerrar esa brecha, y la elección cambia el calendario:

| Opción | Cómo | Ventaja | Coste / riesgo |
|---|---|---|---|
| **A. CI en GitHub Actions** (recomendada) | Añado `.github/workflows/ci.yml` al repo (el README **ya dice** que existe y no existe), empujo a `arena/3c116bea-spvi` y leo los registros con `gh run view --log` | Repositorio **público** → minutos gratis e ilimitados; yo itero solo (push → log → arreglo → push) sin molestarte; queda para siempre como red de seguridad | El token que tengo autenticado puede **no** tener permiso para subir archivos de `.github/workflows/` (GitHub lo exige aparte). Si falla, ese único archivo lo creas tú desde la web (1 minuto) y el resto sigue igual |
| **B. Tú ejecutas y me pegas el log** | `./gradlew spviCheck` en tu equipo con Android Studio | Cero dependencias de mi entorno | Fricción en cada iteración; el bucle de corrección pasa por ti |
| **C. Me habilitas red** | Abrir `dl.google.com`, `repo1.maven.org` y `api.adoptium.net` | `tools/verificacion/verificar.sh` compila los 6 módulos y corre los tests JVM aquí (~15–25 min por pasada) | No cubre lint, R8 ni APK; sigue sin sustituir a A |

**Recomendación: A, y C si es fácil (A cubre todo, C da iteración rápida en los tests JVM).**

---

## 2. Reglas de trabajo durante todo el plan

1. **Un PR por fase**, con su entrada en `docs/HISTORIAL_DESARROLLO.md` (regla del propio proyecto).
2. **Nada avanza sin CI verde.** Si un paso no se puede verificar, se marca `NO VERIFICADO` y no se cierra.
3. **No se toca** lo que el proyecto declaró intocable: lista de permisos, claves de GL fijadas, ausencia de
   telemetría, protocolo de sincronización **v1** (compatibilidad con secundarias 0.24–0.27 ya instaladas),
   DTO de respaldo v4 y archivo `.spvi` v4 (se sigue leyendo lo anterior).
4. **Cambio de esquema = v12 + `Migration(11, 12)` + `12.json` + test de migración.** Sin excepciones.
5. **El README solo puede afirmar lo que un comando reproducible demuestre**, y la afirmación debe citar el
   comando y la fecha. Esto se convierte en una comprobación automática (T3.6).
6. Yo trabajo **siempre** en `arena/3c116bea-spvi` (esta sesión está fijada a esa rama): los PR salen de ahí,
   uno detrás de otro. Si un PR no está fusionado cuando empiezo el siguiente, el segundo PR incluirá los
   commits del primero (apilado) y lo avisaré.

---

## 3. Fases

Esfuerzos en **días de trabajo**; entre paréntesis, quién verifica en el teléfono cuando hace falta (tú).

### F0 · Red de seguridad y verdad documental — **bloqueante, 2–4 días**

> Objetivo: pasar de «nadie sabe si compila» a «cada push compila y se ve en un registro».

| ID | Tarea | Dónde | Hecho cuando |
|---|---|---|---|
| T0.1 | Workflow de CI: JDK 17 Temurin + SDK 35 + caché de Gradle; trabajos separados: `tests` (`spviTests` + compilar los instrumentados de `:data` y `:app`), `lint` (`:app:lintDebug`), `release` (`:app:assembleRelease` sin firmar + comprobar que `app/build/outputs/mapping/release/missing_rules.txt` está **vacío** = «R8 sin clases ausentes»), `permisos` (`spviPermisos`), `api-min` (`tools/verificacion/api_minima.py`), `docs` (T3.6 cuando exista) | Los 5 trabajos en verde sobre `arena/3c116bea-spvi` |
| T0.2 | **Arreglar los 3 archivos de test que no compilan** (D2): `RepositoriosRoomTest` (quitar `codigo`/`porCodigo`), `ConfiguracionInicialInstrumentedTest` y `RespaldoRoomTest` (quitar `ConsentimientoRed`/`guardarConsultasEnLinea`/`consultasEnLinea`) | `data/src/androidTest` | `:data:compileDebugAndroidTestKotlin` pasa |
| T0.3 | Arreglar lo que destape la primera corrida: Room/KSP, Hilt, **lint** (regenerar `lint-baseline.xml` **una vez**, revisando el diff: se corrigen los *errores* — `NewApi`, `WrongThread` y similares — y se basilinan solo los avisos estilísticos), reglas R8 que falten para `kotlinx.serialization`, `api_minima.py` | varios | `spviCheck` verde |
| T0.4 | Congelar la versión: decidir si esto se publica como **0.30.0** (lo que el código ya contiene) y subir `versionCode` 50 → 51, o revertir el trabajo no publicado | `app/build.gradle.kts` | `versionName` y README coinciden con el binario |
| T0.5 | `docs/VERIFICACION.md`: estado real, comando, salida y fecha de cada comprobación (la única fuente de verdad), y corregir la contradicción README ↔ `AGENTS.md` | 3 archivos | Un solo relato verificable |
| T0.6 | *(tú)* Instalar el APK debug y el release firmado en un teléfono y abrirlo; probar dos teléfonos (principal + secundaria) vendiendo | teléfono | Primera evidencia de que la app **existe** fuera del compilador |

**Riesgo y contingencia:** tras meses sin ejecutarse, la primera corrida puede destapar más de lo previsto.
Reservo 1–2 días extra. Si `spviTests` falla en masa por algo estructural (p. ej. Kotlin 2.0.21 + AGP 8.7.3
vs. la versión de Compose), lo arreglo antes de tocar nada de lógica.

### F1 · Rendimiento: sacar el trabajo del hilo principal — **2–3 días**

> Objetivo: que con 18 meses de datos y el período «Año» la interfaz no se congele. Hoy **no existe un solo
> `flowOn`** en `app`/`domain`/`data`, y `InicioViewModel` agrega miles de ventas en el hilo de UI.

| ID | Tarea | Dónde | Hecho cuando |
|---|---|---|---|
| T1.1 | `withContext(io)` con `@IoDispatcher` inyectado en los casos de uso de Inicio (`ObtenerGraficosPeriodo`, `ObtenerResumenGeneral`) | `domain/.../InicioUseCases.kt`, `data/di` | Mismo resultado, fuera del hilo de UI |
| T1.2 | `flowOn(io)` en las cadenas reactivas que hoy transforman en el colector (`ObservarInventario`, `ObservarRegistro`, los `stateIn(viewModelScope)` que mapean) | `domain`, `app` | Ninguna transformación pesada en Main |
| T1.3 | `debounce(250 ms)` en los buscadores (Inventario, Registros, Precios, ficha) conservando el «sin parpadeo» actual | 4 ViewModels | 8 pulsaciones = 1 consulta |
| T1.4 | Agregación en SQL: nuevas `@Query` con `GROUP BY` por cubo y rango para `Estadisticas.serie`; `Estadisticas` se conserva como función pura (es la autoridad de los tests) y se usa para verificar el SQL | `VentaDaos`, `Estadisticas`, `InicioUseCases` | Test que compara **SQL vs memoria** sobre el seed → resultados idénticos |
| T1.5 | Prueba de verdad: test de instrumentación con el `GeneradorSeed` de 18 meses que mida `ObtenerResumenGeneral` y falle por encima de un umbral (p. ej. 250 ms), más una captura de `Systrace`/`Perfetto` antes y después *(tú, en un gama baja si es posible)* | `app/src/androidTest` | Número antes y después en `docs/VERIFICACION.md` |
| T1.6 | *(opcional)* `baseline profile` real: el módulo ya trae `profileinstaller` pero no hay perfil generado | nuevo `:baselineprofile` | Arranque en frío medido |

**Riesgo:** mover la agregación a SQL puede cambiar redondeos (céntimos). Contingencia: los tests de
`Estadisticas` son la autoridad; ninguna firma pública cambia.

### F2 · Seguridad — **2–3 días**

| ID | Tarea | Dónde | Hecho cuando |
|---|---|---|---|
| T2.1 | **Respaldo sin contraseña**: pasa a «Proteger con contraseña» **activado por defecto**; el modo sin contraseña queda como acción explícita con advertencia que ya no puede ser tibia («cualquiera que consiga este archivo puede leer tus ventas y los datos de tus clientes»). **Decisión tuya** si se elimina del todo en la próxima versión y se deja solo para *importar* | `respaldo/`, `RespaldoScreen` | Tests de `BackupCipher` actualizados + captura nueva; `FORMATOS.md`/`SECURITY.md` al día |
| T2.2 | **DoS de la sesión LAN** (D5): la sesión anterior solo se cierra cuando la nueva **descifra su primer mensaje**; añado límite de conexiones por empleado/IP y espera creciente. **Sin cambiar el protocolo** (siguen conectando las secundarias 0.24–0.27) | `data/sync/ServidorSync.kt` | Test `sesionNoSeCierraSinPruebaDeClave` + prueba real de 3 teléfonos *(tú)* |
| T2.3 | Menos oráculo: respuesta uniforme ante `Hola`, sin distinguir «negocio desconocido» de «empleado desconocido», y límite de intentos en `vincular` | `ServidorSync`, `Protocolo` | Tests del protocolo actualizados |
| T2.4 | **Cache del registro de prueba**: no consultar `MediaStore` 3 veces en cada `onResume`; invalidar solo si cambió el día, es instalación nueva o pasaron N horas | `RegistroPruebaAndroid`, `LicenseManager` | Test que cuente las lecturas por evaluación |
| T2.5 | Visibilidad de la revocación: recordar la fecha de la última consulta correcta y avisar en Ajustes si llevan N días sin poder comprobarla (hoy un bloqueo silencioso de la descarga pasa inadvertido) | `actualizacion/`, `ajustes/` | Texto nuevo + captura |

### F3 · Coherencia documental y de producto — **2–3 días**

| ID | Tarea | Dónde |
|---|---|---|
| T3.1 | Quitar el **escáner de códigos de barras** de todo lo que lo menciona: README, `SECURITY.md`, `AGENTS.md`, **`AyudaContenido`** (es lo que ve el cliente) y el comentario de `data/build.gradle.kts` | 6 archivos |
| T3.2 | Repasar la tabla del esquema contra `Entities.kt` **campo por campo** (hoy: Room **v11**, sin `codigo`, con `cliente_fijo`, `movimiento_caja`, `servicio`) | README |
| T3.3 | Reparar el UTF-8 de `docs/HISTORIAL_DESARROLLO.md` (mojibake desde 0.29.2) y comprobar que no haya más archivos dañados | 1–N archivos |
| T3.4 | Reponer los documentos que el README enlaza y no existen: **`MANUAL_USUARIO.md`** (el que ve tu cliente, lo escribo yo a partir de la app real), `PRUEBAS_DISPOSITIVO.md`, `Pendiente.md`; y quitar del README `Pruebas.md`, `opencode.json` si ya no usas OpenCode | README + 2–3 docs nuevos |
| T3.5 | Actualizar el encabezado de versión de `UI_UX_IX.md` (dice 0.19.3) y `SECURITY.md` (dice 0.26.0) | 2 docs |
| T3.6 | **Prueba automática de documentación** (`tools/verificacion/docs.sh`, en el CI): falla si el README enlaza un archivo inexistente, si la versión del README ≠ `versionName`, o si la tabla del esquema no coincide con `Entities.kt` | nuevo + CI |

### F4 · Accesibilidad, IX y adaptatividad — **3–5 días** (requiere 2 decisiones tuyas)

| ID | Tarea |
|---|---|
| T4.1 | **Etiquetas en la barra inferior** (texto bajo el icono) o no: es la decisión que más afecta a la adopción con personal nuevo |
| T4.2 | `WindowSizeClass`: patrón lista-detalle en ≥ 600 dp (Inventario, Registros, Servicios), diálogos dimensionados por contenido (`IntrinsicSize`) y no por fracción de pantalla (`Overlays.kt`), capturas Roborazzi con cualificador de tablet |
| T4.3 | Revisar los 27 objetos `Textos*` contra la interfaz (extendiendo los tests de textos que ya existen) y **arreglar la Ayuda**, que hoy describe funciones eliminadas |
| T4.4 | Señal de progreso en la búsqueda (hoy solo se conserva la lista anterior, sin indicar que está trabajando) |

### F5 · Versatilidad y producto — **a decidir, no lo toco sin ti**

| ID | Tarea | Coste |
|---|---|---|
| T5.1 | **Multi-moneda** (CUP/MLC/USD + tasa de cambio + redondeo) con migración v12, en `Money`, gráficos, caja, exportaciones y respaldo v5 | **2–3 semanas** — el cambio más caro del informe |
| T5.2 | Descuentos por línea/venta, impuestos, devoluciones parciales | 1–2 semanas |
| T5.3 | Usuarios locales en el mismo teléfono (vendedor por turno) | 1 semana |
| T5.4 | Textos a `strings.xml` con los `Textos*` como envoltorio (cambiar terminología sin recompilar; i18n futura) | 1 semana |

### F6 · Higiene continua — **1 día, permanente**

- `.kotlin/` a `.gitignore` y fuera del control de versiones.
- Mover las referencias «P37/P68b/P74/§5.4» a un `docs/DECISIONES.md` indexado.
- Reducir los `!!` de UI y ViewModels sustituyéndolos por comprobaciones con mensaje.
- **Ejecutar los 136 tests instrumentados** (emulador en CI o tu teléfono) al menos en cada release; hoy no se
  ejecutan nunca.
- Registrar en `docs/VERIFICACION.md` la comprobación de cada release (lo que hoy es la línea 122 del README).

---

## 4. Calendario resumido

| Fase | Esfuerzo | Depende de | Verifica |
|---|---|---|---|
| **F0** Verificación y verdad | 2–4 días (+1–2 de contingencia) | Tu aprobación de CI y de la versión | CI + tú (APK) |
| **F1** Rendimiento | 2–3 días | F0 verde | Test de umbral + tú en gama baja |
| **F2** Seguridad | 2–3 días | F0 | Tests + tú (3 teléfonos) |
| **F3** Documentación | 2–3 días | F0 (para el CI de docs) | CI |
| **F4** IX y adaptatividad | 3–5 días | 2 decisiones tuyas | Capturas nuevas |
| **F5** Producto | 3–6 semanas | Decisión de negocio | Tú |
| **F6** Higiene | 1 día | — | CI |

**Total de lo urgente y barato (F0+F1+F2+F3+F6): 9–14 días de trabajo**, de los cuales tú solo tienes que
invertir minutos en T0.6 y T2.2 (probarlos en los teléfonos).

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

## 6. Decisiones que necesito de ti antes de empezar

1. **CI en GitHub Actions** (repo público, minutos gratis): ¿lo añado yo y empujo a `arena/3c116bea-spvi`, o no
   se toca el remoto y verificas tú?
2. **Versión**: el código ya contiene 0.28–0.30 (sin el escáner). ¿El próximo release es **0.30.0** con lo que
   hay dentro, o se revierte lo no publicado y se sigue desde 0.27.1?
3. **Respaldo sin contraseña**: ¿lo dejo con «Proteger con contraseña» activado por defecto, o **lo elimino** en
   la próxima versión (dejando solo el *poder importarlos*)? Mi recomendación: eliminarlo para exportar.
4. **Alcance de este ciclo**: ¿hago F0–F3 + F6 (lo urgente y barato) y dejamos F4/F5 para después, o quieres
   F4 incluido ya?
5. **Barra inferior**: ¿etiquetas visibles bajo los iconos, o mantenemos los 5 iconos desnudos?
6. **Multi-moneda (MLC/USD)**: ¿entra en la hoja de ruta de este trimestre? Cambia por completo el orden de F5.

Con tus respuestas a 1–4 empiezo por F0 y te enseño el primer registro de CI (verde o rojo, con lo que salga)
antes de tocar una sola línea de lógica.
