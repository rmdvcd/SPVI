# SPVI — instrucciones para agentes y herramientas

App Android de punto de venta e inventario para pequeños negocios en Cuba. Versión **0.30.0** (`versionCode 51`), paquete `cu.spvi.app`, Android mínimo API 26 y Room v11. El texto de la interfaz y la documentación se escriben en español.

## Documentos de referencia

Antes de cambiar código, revisa el alcance pertinente:

- [README.md](README.md): funciones, módulos, esquema y comandos.
- [MANUAL_USUARIO.md](MANUAL_USUARIO.md): comportamiento explicado para el usuario final.
- [Pendiente.md](Pendiente.md): validaciones pendientes y estado de esta revisión.
- [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md): flujo de pruebas en el PC y teléfonos.
- [docs/VERIFICACION.md](docs/VERIFICACION.md): resultados que sí se han observado; no completar valores por inferencia.
- [SECURITY.md](SECURITY.md), [FORMATOS.md](FORMATOS.md), [LICENSE_CLIENT.md](LICENSE_CLIENT.md): seguridad, datos exportados y contrato GL.
- [docs/VINCULACION.md](docs/VINCULACION.md): sincronización local principal/secundaria.
- [docs/DECISIONES.md](docs/DECISIONES.md) y [docs/HISTORIAL_DESARROLLO.md](docs/HISTORIAL_DESARROLLO.md): decisiones y cambios históricos.
- [docs/OPENCODE_DESKTOP.md](docs/OPENCODE_DESKTOP.md): flujo del propietario con OpenCode CLI en su PC.

## Alcance de esta sesión y validación

El propietario indicó que **el agente de Arena solo analiza y modifica el proyecto**. No ejecutar Gradle, la app, `adb`, emuladores, pruebas JVM, instrumentadas ni capturas desde el agente. El propietario ejecuta compilación y pruebas mediante OpenCode CLI en su PC y comparte los resultados.

- No decir «pasa», «verde», «compila» o «está probado» sin un resultado observado para el commit exacto.
- Una compilación de `androidTest` no es la ejecución de esos tests.
- Las capturas no se consideran revisadas solo porque se generaron.
- Mantener pendientes las pruebas de teléfono, sincronización de varios teléfonos y mediciones hasta recibir su evidencia.
- Se puede hacer análisis estático del código y editar documentación, scripts o workflows sin afirmar que se ejecutaron.

## Arquitectura

| Módulo | Responsabilidad |
|---|---|
| `:core` | Tipos puros de dinero, cantidades y resultados |
| `:licencia` | Formatos y criptografía del contrato GL v1 |
| `:domain` | Modelos, validación, casos de uso e interfaces de repositorio |
| `:data` | Room/SQLCipher, DataStore, respaldo, sincronización local y exportaciones |
| `:designsystem` | Tema, tokens, iconos y componentes Compose compartidos |
| `:app` | Pantallas, navegación, ViewModels e inyección Hilt |

Reutiliza los repositorios y casos de uso existentes. No pongas reglas de negocio en las pantallas. Conserva la estructura de módulos salvo que la tarea pida expresamente cambiarla.

## Reglas de producto y seguridad

1. **Permisos:** no añadas permisos fuera de la lista autorizada por `spviPermisos`. La cámara se usa para leer QR de vinculación o tomar fotos; las consultas de red se limitan a GitHub y sincronización local. Revisa el manifiesto fusionado, no solo el manifiesto de `:app`.
2. **Red:** no añadir Firebase, analítica, telemetría, anuncios ni transmisión de datos del negocio a servicios remotos. Las secundarias se sincronizan por la red local.
3. **Ventas:** no se vende sin turno abierto. Los pagos por transferencia se registran; SPVI no los inicia ni confirma en el banco.
4. **Protocolo:** conservar la versión 1 de sincronización y su compatibilidad. No cambiar mensajes, claves, orden de autenticación ni campos sin petición explícita y revisión de [docs/VINCULACION.md](docs/VINCULACION.md).
5. **Licencias:** SPVI es cliente del contrato GL v1. No copiar almacenamiento interno de GL, publicar secretos ni cambiar claves fijadas sin autorización.
6. **UI:** Material 3 claro/oscuro, tokens de `:designsystem`, contraste AA, áreas táctiles accesibles y descripciones para iconos solos. No inventar pantallas, rutas ni opciones.
7. **Privacidad:** no añadir datos personales a logs, capturas o mensajes de error. Mantener `FLAG_SECURE` donde corresponde.
8. **Respaldo:** DTO v4 y archivo `.spvi` v4 son formatos distintos del esquema Room. No cambiar compatibilidad ni advertencias sin actualizar `FORMATOS.md` y `SECURITY.md`.
9. **Esquema:** la BD actual es Room v11. Un cambio de esquema requiere incrementar versión, migración explícita, esquema exportado y prueba de migración; nunca borrar datos de usuarios.
10. **Datos de ejemplo:** usa datos ficticios en pruebas, documentos y capturas.

## Comandos para el PC del propietario

Estos comandos los ejecuta el propietario con OpenCode CLI desde la raíz del clon, JDK 17 y SDK Android 35 configurados. No los ejecutes desde el agente de Arena.

```bash
./gradlew spviTests
./gradlew spviCheck
./gradlew :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin
./gradlew spviInstrumentedTests
./gradlew :app:recordRoborazziDebug
python3 tools/verificacion/api_minima.py
python3 tools/verificacion/documentacion.py
```

Un test JVM individual puede filtrarse, por ejemplo:

```bash
./gradlew :domain:test --tests "cu.spvi.domain.Version025Test"
```

Las credenciales de firma viven únicamente en el PC del propietario (`keystore.properties` o variables locales); nunca pedirlas por chat ni añadirlas a Git.

## Reglas de modificación

- Mantén los cambios acotados y coherentes con la arquitectura. No migres el esquema, cambies permisos ni modifiques el protocolo como parte de una limpieza documental.
- No elimines datos históricos sin revisar si describen una versión anterior. Diferencia documentación vigente de bitácora histórica.
- Evita `!!`; usa comprobaciones que mantengan el tipo nulo de la función o una condición explícita con mensaje si la invariante no puede cumplirse.
- No uses `local.properties`, credenciales, APK, capturas de datos reales ni resultados de clientes en Git.
- Añade o actualiza pruebas al cambiar lógica, pero deja su ejecución al OpenCode CLI local según la instrucción del propietario.
- Si cambia el comportamiento, sincroniza README, manual de usuario, seguridad/formatos afectados, [docs/VERIFICACION.md](docs/VERIFICACION.md) y el historial.
- Añade cualquier documento nuevo al índice de enlaces del README y a la prueba estática de documentación.
- Usa `.kotlin/` como caché local ignorada; no la versionas.

## Notas técnicas

- Versiones de Kotlin, AGP, Gradle, Compose, Room y dependencias: `gradle/libs.versions.toml` y `gradle/wrapper/gradle-wrapper.properties`.
- Prueba de acceso local: `spviPermisos` valida el manifiesto fusionado.
- Los tests no deben salir a internet. Para red falsa usa `MockWebServer` y los fakes existentes.
- Room exporta esquemas en `data/schemas/`; las entidades están en `data/src/main/kotlin/cu/spvi/data/db/entity/Entities.kt`.
- El recuento y resultado de una ejecución solo se actualizan con evidencia de OpenCode CLI o de una CI identificada por commit.
