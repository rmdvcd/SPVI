# SPVI — Sistema de Punto de Venta e Inventario

SPVI es una app Android para pequeños negocios. Permite vender por turnos, controlar productos e insumos, registrar servicios, revisar caja y exportar información. Los datos del negocio se guardan cifrados en el teléfono. No hay una cuenta ni una nube propia; si se vinculan teléfonos, la sincronización se hace por la red local.

| Dato | Valor |
|---|---|
| Versión | **0.30.0** (`versionCode 51`) |
| Paquete | `cu.spvi.app` (debug: `cu.spvi.app.debug`) |
| Android | `minSdk 26` · `targetSdk 35` · `compileSdk 35` |
| Base de datos | Room **v11**, cifrada con SQLCipher |
| Respaldo | DTO v4 y archivo `.spvi` v4; la protección con contraseña está activada por defecto |
| Licencias | Contrato GL v1 (`ECIES-P256-AES256GCM-v1`) |

## Documentación

| Documento | Para qué |
|---|---|
| [MANUAL_USUARIO.md](MANUAL_USUARIO.md) | Guía de uso para el dueño y los empleados |
| [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md) | Comandos para OpenCode CLI y checklist en teléfonos |
| [Pendiente.md](Pendiente.md) | Estado actual, tareas sin validar y decisiones pendientes |
| [docs/VERIFICACION.md](docs/VERIFICACION.md) | Resultados medidos de CI y validaciones locales; distingue lo aprobado de lo pendiente |
| [PLAN_CORRECCIONES.md](PLAN_CORRECCIONES.md) | Plan y estado de las correcciones |
| [SECURITY.md](SECURITY.md) | Cifrado, permisos, red y riesgos residuales |
| [LICENSE_CLIENT.md](LICENSE_CLIENT.md) | Cómo se aplica el contrato de licencias GL v1 |
| [FORMATOS.md](FORMATOS.md) | Respaldo `.spvi` y exportaciones PDF, Excel e imágenes |
| [RELEASE.md](RELEASE.md) | Firma y preparación de una versión de distribución |
| [CAPTURAS.md](CAPTURAS.md) | Capturas Roborazzi, casos y estado de los PNG |
| [UI_UX_IX.md](UI_UX_IX.md) y [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) | Criterios de interfaz y componentes visuales |
| [docs/VINCULACION.md](docs/VINCULACION.md) | Funcionamiento técnico de las apps principal y secundarias |
| [docs/HISTORIAL_DESARROLLO.md](docs/HISTORIAL_DESARROLLO.md) | Historial de versiones y decisiones de desarrollo |
| [docs/DECISIONES.md](docs/DECISIONES.md) | Índice de decisiones técnicas duraderas |
| [AGENTS.md](AGENTS.md) | Instrucciones y límites para agentes que modifican el proyecto |

## Qué hace

- **Inicio y turnos:** abrir y cerrar turnos, registrar fondo y arqueo de caja, consultar alertas y gráficos de ventas, inventario, pagos y ganancia.
- **Inventario:** administrar productos, insumos, cantidades, precios, niveles de stock, caducidad, fotos y recetas de productos elaborados. Los insumos de una receta se descuentan al vender el elaborado.
- **Servicios:** registrar servicios y los insumos que consume cada prestación.
- **Ventas:** vender productos o servicios con el turno abierto, cobrar en efectivo o anotar una transferencia. SPVI registra la operación: no inicia ni confirma pagos bancarios.
- **Registros:** consultar ventas, transferencias, movimientos, turnos y clientes fijos; aplicar filtros y compartir o guardar informes.
- **Apps vinculadas:** una app principal administra el negocio y puede vincular las apps secundarias autorizadas por la licencia. Se vinculan con un QR y se sincronizan por la red local. Si una secundaria pierde la conexión, puede seguir trabajando con sus datos locales y sincronizar cuando recupere la red.
- **Respaldo y migración:** exportar e importar una copia completa, o seguir el asistente para cambiar de teléfono. Importar reemplaza los datos existentes.
- **Licencias y actualizaciones:** prueba inicial de 7 días; después se requiere licencia. Si el build tiene un repositorio de GitHub configurado, SPVI consulta versiones y revocaciones. Ajustes muestra la fecha de la última respuesta correcta y avisa si pasan 14 días sin poder confirmarla.
- **Acceso con clave:** opción para pedir la huella, el rostro o el PIN del teléfono al abrir SPVI, si el dispositivo lo admite.

La cámara solo se utiliza para leer el QR de vinculación o tomar una foto. No hace falta para registrar productos.

## Requisitos y configuración

Para compilar se necesita JDK 17, Android SDK Platform 35 y las dependencias del Gradle Wrapper. El teléfono compatible es Android 8.0 (API 26) o posterior. Las instrucciones de configuración y prueba están en [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md).

No se requieren claves de API, `google-services.json` ni variables de entorno para compilar. Las actualizaciones desde GitHub solo se consultan en las compilaciones que tienen configurado `spviGithubRepo`; si queda vacío, esa consulta no se realiza. La firma de distribución requiere el keystore privado descrito en [RELEASE.md](RELEASE.md), que no se guarda en Git.

## Verificación: qué se sabe y qué no

La última CI completa confirmada corresponde a `3bf4fc1` (run [37834591679](https://github.com/rmdvcd/SPVI/actions/runs/37834591679)): 5/5 trabajos verdes y 836 tests JVM sin fallos, errores ni omisiones. Esa CI compiló los tests instrumentados, pero no los ejecutó.

El commit `43cc5df` añadió T2.4 y T2.5. Su corrida `37836886846` tuvo resultados parciales observados, pero se detuvo el seguimiento antes del resultado final; **no se declara verde**. El código, los tests nuevos, las capturas y la documentación posteriores aún requieren la ejecución de OpenCode CLI en el PC del propietario. El detalle y los límites están en [docs/VERIFICACION.md](docs/VERIFICACION.md); no se afirma que esos cambios hayan pasado tests.

En el flujo acordado, el propietario ejecuta compilación, aplicación y pruebas desde OpenCode CLI en su PC. Este repositorio conserva los comandos reproducibles y el registro de los resultados, sin confundir una compilación con la ejecución de pruebas instrumentadas o con una comprobación manual en teléfonos.

## Arquitectura

Clean Architecture en módulos Gradle:

| Módulo | Responsabilidad |
|---|---|
| `:core` | Tipos y utilidades puras, como dinero y cantidades |
| `:licencia` | Formatos y criptografía del contrato GL |
| `:domain` | Modelos, interfaces de repositorio, validación y casos de uso |
| `:data` | Room, SQLCipher, respaldo, sincronización y exportaciones |
| `:designsystem` | Tema, tokens, iconos y componentes Compose |
| `:app` | Pantallas, navegación, ViewModels e inyección Hilt |

### Comandos de verificación

Ejecutar desde la raíz del clon local, mediante OpenCode CLI, con JDK 17 y Android SDK configurados:

```bash
./gradlew spviCheck                         # tests JVM, lint, APK debug y permisos
./gradlew :app:assembleRelease              # R8 y APK release sin firmar si no hay keystore
./gradlew spviInstrumentedTests              # tests que requieren un dispositivo/emulador API 26+
./gradlew :app:recordRoborazziDebug           # regenera las capturas claro/oscuro
python3 tools/verificacion/documentacion.py  # enlaces, versión y tabla de Room del README
```

Una release firmada se prepara con `./gradlew spviRelease` y el keystore local. Los procedimientos completos y el checklist están en [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md).

## Base de datos Room v11

`spvi.db` se cifra con SQLCipher. La tabla siguiente enumera todos los campos declarados por las entidades de Room. Tipos, nulabilidad y restricciones están definidos por `data/src/main/kotlin/cu/spvi/data/db/entity/Entities.kt`.

**Convenciones:** importes en centavos (`…Cent`); cantidades de insumos en milésimas (`…Mil`); instantes en milisegundos UTC; fechas de calendario en días desde epoch; enums como texto. No se usan `TypeConverters`.

| Tabla | Clave primaria | Columnas completas | Relaciones, índices y notas |
|---|---|---|---|
| `producto` | `id` autogenerado | `id`, `categoria`, `nombre`, `descripcion`, `fotoUri`, `fechaCaducidad`, `precioCostoCent`, `precioVentaCent`, `cantidad`, `nivelBajo`, `nivelCritico`, `creadoEn`, `actualizadoEn`, `eliminado` | Índices `categoria`, `eliminado+creadoEn`, `fechaCaducidad`; borrado lógico |
| `insumo` | `id` autogenerado | `id`, `nombre`, `unidad`, `precioCent`, `cantidadMil`, `nivelBajoMil`, `nivelCriticoMil`, `creadoEn`, `actualizadoEn`, `precioVentaCent` | Índices `creadoEn`, `nombre`; `precioVentaCent` nulo si no se vende suelto |
| `receta_linea` | `productoId` + `insumoId` | `productoId`, `insumoId`, `cantidadMil` | FK producto CASCADE; FK insumo RESTRICT; índice `insumoId` |
| `turno` | `id` autogenerado | `id`, `abiertoEn`, `cerradoEn`, `numVentas`, `unidades`, `totalCent`, `efectivoCent`, `transferenciaCent`, `costoCent`, `numMovimientos`, `abiertoPor`, `cerradoPor`, `ventasEfectivo`, `ventasTransferencia`, `movimientosProducto`, `movimientosInsumo`, `uuid`, `empleadoId`, `sincronizado`, `fondoCent`, `contadoCent`, `entradasCent`, `salidasCent`, `numAnuladas` | Índices `abiertoEn`, `cerradoEn`, `uuid` único; los datos del resumen pueden ser nulos mientras está abierto |
| `venta` | `id` autogenerado | `id`, `turnoId`, `fecha`, `metodoPago`, `totalCent`, `costoCent`, `unidades`, `uuid`, `empleadoId`, `sincronizado`, `anuladaEn`, `motivoAnulacion`, `anuladaPor`, `corrigeVentaId`, `bajarCambio` | FK turno RESTRICT; índices `fecha`, `turnoId`, `totalCent`, `uuid` único |
| `movimiento_caja` | `id` autogenerado | `id`, `turnoId`, `fecha`, `tipo`, `importeCent`, `motivo`, `hechoPor`, `uuid`, `sincronizado` | FK turno RESTRICT; índices `turnoId`, `uuid` único |
| `detalle_venta` | `id` autogenerado | `id`, `ventaId`, `productoId`, `nombre`, `categoria`, `cantidad`, `precioBaseCent`, `precioUnitarioCent`, `costoUnitarioCent`, `clase` | FK venta CASCADE; índices `ventaId`, `productoId`; nombre y precios quedan congelados en la venta |
| `transaccion` | `id` autogenerado | `id`, `ventaId`, `fecha`, `importeCent`, `numero`, `clienteNombre`, `clienteCi`, `clienteTelefono`, `tarjetaCobro`, `telefonoCobro`, `clienteFijo` | FK venta CASCADE; `ventaId` único; índices `fecha`, `numero`, `importeCent` |
| `cliente_fijo` | `id` autogenerado | `id`, `nombreApellidos`, `ci`, `telefono`, `creadoEn`, `actualizadoEn` | `ci` único; sus compras se obtienen de las transferencias |
| `movimiento` | `id` autogenerado | `id`, `fecha`, `tipo`, `entidad`, `entidadId`, `nombre`, `delta`, `existencia`, `turnoId`, `ventaId`, `nota`, `hechoPor` | Índices `fecha`, `entidad+entidadId`, `turnoId` |
| `perfil` | `id` fijo = 1 | `id`, `nombre`, `apellidos`, `ci`, `pagoTarjetaId`, `pagoTelefonoId` | Una fila por negocio |
| `tarjeta` | `id` autogenerado | `id`, `numero`, `alias` | `numero` único |
| `telefono` | `id` autogenerado | `id`, `numero`, `alias` | `numero` único |
| `preajuste` | `id` autogenerado | `id`, `nombre`, `puntosBasicos`, `metodoPago`, `importeMinimoCent`, `activo` | Ajustes de precio |
| `preajuste_producto` | `preajusteId` + `productoId` | `preajusteId`, `productoId` | FK preajuste CASCADE; FK producto CASCADE; índice `productoId` |
| `servicio` | `id` autogenerado | `id`, `nombre`, `tipo`, `importeCent`, `descripcion`, `fotoUri`, `creadoEn`, `actualizadoEn`, `eliminado` | Índices `tipo`, `eliminado+creadoEn`; borrado lógico |
| `servicio_insumo` | `servicioId` + `insumoId` | `servicioId`, `insumoId`, `cantidadMil` | FK servicio CASCADE; FK insumo RESTRICT; índice `insumoId` |
| `empleado` | `id` autogenerado | `id`, `nombre`, `permisos`, `creadoEn`, `vinculadoEn`, `ultimaSincronizacion`, `clave`, `codigoToken`, `codigoVence`, `activo`, `tarjetaId`, `telefonoId`, `cierreSolicitadoEn`, `telefono`, `cierrePedidoPorEmpleadoEn`, `fondoAsignadoCent`, `fondoAsignadoEn`, `aperturaSolicitadaEn`, `versionCode` | Datos cifrados por SQLCipher; la fila se conserva al desvincular para reconocer la app |

Las migraciones de usuarios parten de v3 y llegan explícitamente a v11 (`MIGRACION_3_4` a `MIGRACION_10_11`). Las bases v1/v2 solo se recrean porque eran versiones internas de desarrollo. La migración 10→11 conserva los demás datos de `producto` y elimina un campo ya retirado; no se cambia aquí el esquema ni se añade otra migración.

## Respaldo y privacidad

- La copia `.spvi` exporta los datos completos del negocio; las fotos no se incluyen.
- La protección con contraseña está activada por defecto. Se exige una contraseña de 8 caracteres o más, escrita dos veces. Sin contraseña, el archivo no tiene confidencialidad: usa un secreto histórico publicado en el repositorio.
- La contraseña no se puede recuperar. Importar sustituye los datos actuales, por lo que se debe verificar primero el archivo y usar una copia de prueba.
- La licencia instalada no se copia como licencia utilizable: está ligada al dispositivo. El respaldo puede llevar datos para solicitar su recuperación.
- Los PDF y hojas de cálculo no se cifran. Las transferencias pueden contener nombre, carné y teléfono; compártelos con cuidado.
- Lee [SECURITY.md](SECURITY.md) y [FORMATOS.md](FORMATOS.md) antes de distribuir respaldos o exportaciones.
