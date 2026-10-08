# Contexto actual de SPVI

Resumen técnico para trabajar en el repositorio. Versión del código: **0.30.0** (`versionCode 51`), Android mínimo API 26, Room v11. No sustituye los documentos detallados enlazados al final.

## 1. Producto

SPVI es una app Android de punto de venta e inventario. La app principal guarda el negocio y su licencia; las apps secundarias de empleados se vinculan con QR y sincronizan por red local. Los datos de negocio no se envían a una nube propia.

Funciones: productos, insumos, elaborados con receta, servicios, ventas por turnos, efectivo y registro de transferencias, caja y arqueos, clientes fijos, filtros, informes PDF/Excel, respaldo e importación, licencias GL, migración y actualización opcional desde GitHub. La cámara se usa para leer el QR de vinculación y para fotos. Los detalles para el usuario están en [MANUAL_USUARIO.md](MANUAL_USUARIO.md).

## 2. Arquitectura

- `:core`: dinero, cantidades y resultados.
- `:licencia`: contrato GL v1 y criptografía.
- `:domain`: modelos, reglas, validación, casos de uso e interfaces.
- `:data`: Room/SQLCipher, DataStore cifrado, respaldo y sincronización local.
- `:designsystem`: tokens y componentes Compose.
- `:app`: interfaz, navegación y ViewModels.

No mover lógica de negocio a Compose. No modificar permisos, protocolo v1, claves GL o formato de respaldo sin una tarea explícita. Room v11 tiene migraciones explícitas desde v3. DTO de respaldo y archivo `.spvi` son ambos v4, pero versionan capas diferentes.

## 3. Verificación y límites

El propietario indicó que el agente de Arena solo analiza y modifica archivos. **OpenCode CLI en su PC** ejecuta Gradle, la app, los tests, las capturas y las pruebas de dispositivo. No afirmar que un cambio compila o pasa hasta recibir el resultado del commit exacto.

- Última CI completa confirmada: `3bf4fc1`, run `37834591679`, 5/5 trabajos y 836 tests JVM aprobados. La CI compiló los instrumentados, pero no los ejecutó.
- `43cc5df` implementa T2.4 y T2.5. El resultado final de su run `37836886846` no se observó; no se toma como verde.
- Las ediciones actuales de documentación/limpieza también quedan sin validar hasta que OpenCode CLI procese el commit.
- Pendientes del teléfono: capturas de T2.1/T2.5; tres teléfonos para T2.2; ejecución y medida de T1.5 con Perfetto.

El registro detallado y comandos viven en [docs/VERIFICACION.md](docs/VERIFICACION.md) y [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md). El estado de tareas está en [Pendiente.md](Pendiente.md).

## 4. Reglas de interfaz y datos

- Interfaz y documentos en español, texto sencillo.
- Material 3, claro/oscuro, componentes y tokens compartidos de `:designsystem`.
- No vender sin turno abierto; las transferencias se registran, no se procesan ni se confirman desde SPVI.
- Datos persistentes cifrados; usar datos ficticios en pruebas. No incluir datos de clientes, licencias, contraseñas o claves en logs, capturas o chat.
- Proteger las pantallas sensibles con `FLAG_SECURE` donde ya está definido.
- Mantener advertencias de respaldo: la protección con contraseña está activada por defecto, la contraseña no se recupera y la importación reemplaza los datos.

## 5. Documentos

- [README.md](README.md): funciones, módulos, schema Room y comandos.
- [AGENTS.md](AGENTS.md): reglas de modificación y validación.
- [MANUAL_USUARIO.md](MANUAL_USUARIO.md): guía de la app.
- [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md): procedimiento local y manual.
- [Pendiente.md](Pendiente.md): estado actual y próximos pasos.
- [docs/VERIFICACION.md](docs/VERIFICACION.md): evidencia confirmada.
- [PLAN_CORRECCIONES.md](PLAN_CORRECCIONES.md): alcance y progreso.
- [SECURITY.md](SECURITY.md), [FORMATOS.md](FORMATOS.md), [LICENSE_CLIENT.md](LICENSE_CLIENT.md): seguridad y formatos.
- [docs/VINCULACION.md](docs/VINCULACION.md): protocolo y red local.
- [docs/DECISIONES.md](docs/DECISIONES.md), [docs/HISTORIAL_DESARROLLO.md](docs/HISTORIAL_DESARROLLO.md): decisiones e historial.
