# Índice de decisiones técnicas

Los identificadores P37, P68b, P74 y P70 §5.4 se conservaron en comentarios del código aunque sus documentos de petición ya no viven como archivos independientes. Este índice registra la decisión vigente y apunta a su implementación/historial. No sustituye las pruebas ni autoriza cambiar el comportamiento.

| Referencia | Decisión vigente | Implementación y detalle |
|---|---|---|
| **P37 — principal/secundaria** | Un teléfono principal administra el negocio. Las apps secundarias se vinculan con QR de un solo uso y sincronizan por red local. La principal puede asignar permisos, medio de cobro y fondos; el empleado solicita cierre de turno. Las operaciones usan IDs globales e idempotencia. | [docs/VINCULACION.md](VINCULACION.md); protocolo en `data/src/main/kotlin/cu/spvi/data/sync/Protocolo.kt`; Room en `data/src/main/kotlin/cu/spvi/data/db/entity/Entities.kt` |
| **P68b — instalación y desinstalación solicitadas por la app** | SPVI puede ofrecer actualizaciones instalables desde GitHub o la principal y solicitar la desinstalación al transferir una licencia. Android pide confirmación al usuario. Los permisos `REQUEST_INSTALL_PACKAGES` y `REQUEST_DELETE_PACKAGES` permanecen limitados a esos flujos. | Manifiesto de `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/cu/spvi/app/actualizacion/` y [SECURITY.md](../SECURITY.md) |
| **P74 — registro de prueba que sobrevive a reinstalar** | Solo la app principal mantiene el registro cifrado en ubicaciones externas autorizadas y lo comprueba con `lastSeen` y reloj monotónico. Se pide el permiso relacionado una vez; una copia que no se puede descifrar se ignora. La prueba puede reiniciarse si se niega el permiso, se borra el registro o se restablece el teléfono: no se bloquea a un cliente por una copia ilegible. | [docs/PLAN_ANTIREINSTALACION.md](PLAN_ANTIREINSTALACION.md), `data/src/main/kotlin/cu/spvi/data/licencia/prueba/RegistroPruebaAndroid.kt`, `domain/src/main/kotlin/cu/spvi/domain/model/EstadoPrueba.kt` |
| **P70 §5.4 — coexistencia de versiones del protocolo** | Aunque la petición planteaba una versión 2, el protocolo sigue en **v1**. Las extensiones se añaden como campos/mensajes opcionales y las versiones mezcladas ignoran campos desconocidos para que las secundarias anteriores puedan seguir trabajando durante una actualización. No crear una v2 sin una decisión de producto y un plan de compatibilidad. | `data/src/main/kotlin/cu/spvi/data/sync/Protocolo.kt`, `SyncJson` (`ignoreUnknownKeys = true`), [docs/VINCULACION.md](VINCULACION.md) |

## Referencias relacionadas

- Los detalles por versión y las pruebas históricas están en [docs/HISTORIAL_DESARROLLO.md](HISTORIAL_DESARROLLO.md).
- El estado de ejecución actual está en [docs/VERIFICACION.md](VERIFICACION.md); un resultado histórico no valida cambios posteriores.
- Los permisos actuales deben seguir pasando `spviPermisos`; cualquier cambio requiere aprobación explícita.
