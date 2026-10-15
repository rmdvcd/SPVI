# Orden de pruebas de SPVI (Android)

1. Leer `AGENTS.md`, `Contexto.md` y `Pendiente.md`. Revisar el estado medido en [docs/VERIFICACION.md](docs/VERIFICACION.md).
2. En un entorno con JDK 17 y Android SDK 35, ejecutar `./gradlew spviTests`. Las pruebas JVM deben funcionar sin red; Robolectric usa el SDK preparado por Gradle.
3. Ejecutar `./gradlew spviCheck`: incluye tests JVM, lint, compilación debug y revisión de permisos del manifiesto.
4. Ejecutar `./gradlew :app:recordRoborazziDebug` y revisar las capturas de referencia claro/oscuro cuando se modifique la UI.
5. Ejecutar `./gradlew spviInstrumentedTests` en un dispositivo o emulador compatible para cubrir Room y pruebas instrumentadas.
6. Antes de publicar, seguir [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md): probar venta, turnos, respaldos, permisos, actualización y sincronización principal/secundaria en teléfonos reales.
7. Verificar migración por QR entre dos teléfonos Android. Si cambia el cliente de licencias, completar también una prueba de extremo a extremo con GL real y preservar la compatibilidad con licencias heredadas instaladas.
8. Para una publicación, seguir [RELEASE.md](RELEASE.md): firmar con el keystore de release, verificar el APK/AAB y completar la prueba de humo.
9. Anotar en `docs/VERIFICACION.md` el SHA, comandos ejecutados y resultados observados. Diferenciar explícitamente pruebas que pasaron, fallaron, no se pudieron ejecutar o están pendientes.

## En este entorno

No hay JDK ni Android SDK instalados, por lo que no se puede ejecutar Gradle localmente. El último CI observado está descrito en `docs/VERIFICACION.md`; la corrección de Robolectric en esta rama aún debe confirmarse en CI.
