# Contexto de SPVI

Resumen para trabajar en este repositorio. Las reglas de colaboración están en [AGENTS.md](AGENTS.md); el estado medido de pruebas está en [docs/VERIFICACION.md](docs/VERIFICACION.md). No usar como referencia archivos retirados como `Pruebas.md`, `Pendiente.md` u `opencode.json`.

> Todo el texto de interfaz, comentarios y documentación está en español. No afirmar que un test o build pasó si no se ejecutó y no corresponde al commit actual.

## 1. Producto y versión

SPVI es una app Android nativa para pequeños negocios: inventario, insumos, servicios, caja, ventas y registros. La principal puede sincronizar con hasta cinco aplicaciones secundarias mediante la red local; no hay backend remoto ni sincronización en la nube. Las actualizaciones y la lista de licencias revocadas consultan GitHub por HTTPS; la licencia se solicita a GL y, tras activarse, se verifica offline.

| Elemento | Estado |
|---|---|
| Versión del proyecto | `0.30.0` (`versionCode 51`) |
| Paquete | `cu.spvi.app` (debug: `cu.spvi.app.debug`) |
| Android | minSdk 26 · compileSdk/targetSdk 35 · JDK 17 |
| Base de datos | Room v11, cifrada con SQLCipher; migración 10→11 retira `producto.codigo` sin perder las demás columnas |
| Respaldo | DTO v4; contenedor `.spvi` v4, lectura de v3 |
| Sincronización LAN | Protocolo v1; mantener compatibilidad con secundarias instaladas |
| Licencia | Contrato GL v1, offline tras activación |

## 2. Reglas funcionales relevantes

- Una venta requiere turno abierto.
- **Precio de venta > costo**, nunca igual ni inferior. Para productos normales se compara el costo de compra; para elaborados, el costo de receta; para servicios, el costo de insumos; para insumos vendibles, su costo propio.
- El precio efectivo tras aplicar preajustes/descuentos también debe ser estrictamente mayor que el costo. La venta se valida de nuevo aunque una entidad antigua haya quedado inválida.
- Los campos numéricos filtran y validan la entrada mientras se escribe; un formato inválido no debe convertirse en otro valor ni guardarse. Dinero acepta los formatos de `Money.parse`, incluido `1,450.00 CUP`; agrupaciones mixtas inválidas se rechazan.
- El subsistema de lectura de códigos de barras de producto se retiró. La cámara/ML Kit permanece para leer QR de vinculación; también se puede tomar una foto mediante el selector/cámara del sistema.
- Los datos del negocio y la licencia se almacenan cifrados en el dispositivo. La sincronización con secundarias ocurre por red local.

## 3. Respaldo sin contraseña

La contraseña es opcional y se conserva así. El `.spvi` v4 sin contraseña aún usa AES-GCM, pero la clave procede de un secreto ofuscado incluido en la app/código público: **no ofrece confidencialidad** y cualquiera que consiga el archivo puede leer los datos.

Antes de guardar o compartir sin contraseña, la interfaz presenta una advertencia explícita. Si hay biometría inscrita, requiere `BIOMETRIC_WEAK` sin alternativa de PIN/credencial; si no la hay, se puede continuar solo tras confirmar la advertencia. La biometría autoriza la acción: no añade una clave, no cifra ni protege el archivo. Consulta [FORMATOS.md](FORMATOS.md), [SECURITY.md](SECURITY.md) y [MANUAL_USUARIO.md](MANUAL_USUARIO.md).

## 4. Compilación y releases

```bash
./gradlew spviTests
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew spviPermisos
./gradlew :app:assembleRelease -PspviGithubRepo=rmdvcd/SPVI
./gradlew spviInstrumentedTests  # requiere emulador/teléfono API 26+
```

`assembleRelease`, `bundleRelease` y `spviRelease` exigen `spviGithubRepo=propietario/repositorio` para evitar builds distribuibles sin destino de actualizaciones/revocaciones. La firma requiere el mismo keystore de siempre; CI no recibe secretos y genera release sin firmar. Ver [RELEASE.md](RELEASE.md).

El configuration cache está desactivado en `gradle.properties` y en CI. Mantener así hasta corregir el error de serialización del estado de configuración.

## 5. Estado de verificación

La última corrida remota consultada pasó 5/5 trabajos en el commit `82672fdf`, anterior a los cambios actuales. No se recuperó el conteo de tests de esa corrida. **Los cambios actuales no están compilados ni probados**: en este entorno `java` no está disponible, por lo que no se ejecutaron Gradle, tests, lint ni R8. No distribuir hasta que pase CI y la prueba de humo.

Siguiente verificación cuando haya JDK 17 + SDK 35:

1. `./gradlew spviTests :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin`.
2. `./gradlew :app:lintDebug`.
3. `./gradlew :app:assembleRelease :app:assembleDebug -PspviGithubRepo=rmdvcd/SPVI`.
4. `./gradlew spviPermisos` y `python3 tools/verificacion/api_minima.py`.
5. Instrumentados, capturas Roborazzi actualizadas y prueba manual según [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md).

## 6. Mapa de documentos vigentes

| Documento | Contenido |
|---|---|
| [README.md](README.md) | Características, arquitectura y comandos |
| [AGENTS.md](AGENTS.md) | Reglas para agentes y convenciones |
| [docs/VERIFICACION.md](docs/VERIFICACION.md) | CI medido y comprobaciones pendientes |
| [MANUAL_USUARIO.md](MANUAL_USUARIO.md) | Instrucciones para el usuario final |
| [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md) | Checklist de prueba manual en dispositivo |
| [RELEASE.md](RELEASE.md) | Firma, build y publicación |
| [FORMATOS.md](FORMATOS.md) · [SECURITY.md](SECURITY.md) | Formatos, cifrado y riesgos residuales |
| [CAPTURAS.md](CAPTURAS.md) · [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) · [UI_UX_IX.md](UI_UX_IX.md) | Capturas, componentes e interfaz |
| [docs/HISTORIAL_DESARROLLO.md](docs/HISTORIAL_DESARROLLO.md) | Registro histórico de decisiones y entregas |
