# SPVI — compilar y publicar

Guía del estado actual del proyecto: versión **0.30.0** (`versionCode 51`), Room **v11**, respaldo `.spvi` v4. Requiere Android Studio o JDK 17 y Android SDK 35. El repositorio no contiene keystore ni contraseñas.

> **No distribuir sin comprobar este árbol.** La última corrida remota consultada pasó en el commit anterior `82672fdf`; los cambios locales actuales no se han compilado ni probado porque este entorno no tiene Java. El estado medido está en [docs/VERIFICACION.md](docs/VERIFICACION.md).

## 1. Destino público de actualizaciones — obligatorio en release

La app consulta releases y la lista de licencias revocadas en GitHub. Por eso todo `assembleRelease`, `bundleRelease` o `spviRelease` exige `spviGithubRepo` con el formato `propietario/repositorio`. Debe ser el repositorio público que realmente publica esos archivos. Sin él, la compilación falla en `validarRepositorioGitHubRelease`; no se permite generar por accidente un release que no pueda consultar actualizaciones ni revocaciones. En debug puede omitirse y `BuildConfig.GITHUB_REPO` queda vacío.

```bash
# Ejemplo para este repositorio
./gradlew :app:assembleRelease -PspviGithubRepo=rmdvcd/SPVI
./gradlew :app:bundleRelease -PspviGithubRepo=rmdvcd/SPVI
./gradlew spviRelease -PspviGithubRepo=rmdvcd/SPVI
```

También se puede configurar `spviGithubRepo=rmdvcd/SPVI` en un `~/.gradle/gradle.properties` local no versionado. GitHub Actions usa `GITHUB_REPOSITORY` automáticamente. El parámetro no sustituye la publicación real ni comprueba que el repositorio sea público; verifica el formato y evita dejar el destino vacío.

## 2. Keystore de firma

**Firma siempre con el mismo keystore.** `ANDROID_ID` y el `deviceId` de licencia dependen de la firma del APK. Si se pierde o cambia la clave, Android no acepta la actualización como tal y las licencias existentes pueden dejar de corresponder.

Crear una sola vez (fuera del repositorio):

```bash
keytool -genkeypair -v \
  -keystore spvi-release.jks -storetype PKCS12 \
  -alias spvi -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=SPVI, O=<tu nombre o negocio>, C=CU"
```

Guarda copias seguras del `.jks` y de sus contraseñas en más de un sitio. Para verificar la huella:

```bash
keytool -list -v -keystore spvi-release.jks -alias spvi | grep SHA256
```

Gradle lee la firma, en este orden:

1. `keystore.properties` en la raíz del proyecto (está ignorado por Git):
   ```properties
   storeFile=/ruta/absoluta/spvi-release.jks
   storePassword=CAMBIAR
   keyAlias=spvi
   keyPassword=CAMBIAR
   ```
2. Variables de entorno: `SPVI_KEYSTORE`, `SPVI_KEYSTORE_PASSWORD`, `SPVI_KEY_ALIAS` y `SPVI_KEY_PASSWORD`.

Si no se configura firma, Gradle produce un APK **sin firmar**, no instalable como release. El CI construye releases sin firmar intencionalmente porque no recibe el keystore.

## 3. Antes de compilar

1. Revisa que `versionCode` sea mayor que el de la Release anterior y actualiza `versionName` en `app/build.gradle.kts` cuando se vaya a publicar una nueva versión. No cambies ambos solo para probar el árbol actual.
2. Asegúrate de que `spviGithubRepo` apunta al repositorio público correcto (sección 1).
3. Ejecuta con JDK 17 y SDK 35:
   ```bash
   ./gradlew spviCheck spviInstrumentedTests
   python3 tools/verificacion/api_minima.py
   ```
   Los tests instrumentados requieren teléfono o emulador API 26+. Las capturas se comprueban aparte con `./gradlew :app:recordRoborazziDebug`.
4. Si se cambió el esquema Room, versiona el JSON nuevo en `data/schemas/cu.spvi.data.db.SpviDatabase/` y revisa la migración. La versión actual es `11.json`; la migración que retiró `producto.codigo` es `MIGRACION_10_11`.
5. Revisa los informes de lint y R8, así como el resultado de [docs/VERIFICACION.md](docs/VERIFICACION.md). No regeneres un baseline para ocultar avisos sin revisar cada cambio.

En `gradle.properties` el configuration cache está desactivado para que el comportamiento local coincida con CI (`--no-configuration-cache`) mientras se resuelve el error de serialización de tareas/plugins.

## 4. Compilar

```bash
# APK de desarrollo (no necesita repositorio público)
./gradlew :app:assembleDebug

# APK y AAB de release (requieren spviGithubRepo; firmados si se configuró el keystore)
./gradlew :app:assembleRelease -PspviGithubRepo=rmdvcd/SPVI
./gradlew :app:bundleRelease -PspviGithubRepo=rmdvcd/SPVI

# Comprobaciones del proyecto + AAB + APK release
./gradlew spviRelease -PspviGithubRepo=rmdvcd/SPVI
```

| Artefacto | Ruta habitual | Uso |
|---|---|---|
| APK release firmado | `app/build/outputs/apk/release/app-release.apk` | Distribución directa si se firmó con el keystore de siempre |
| APK release sin firmar | `app/build/outputs/apk/release/app-release-unsigned.apk` | Pruebas de build; no distribuir para instalar |
| AAB | `app/build/outputs/bundle/release/app-release.aab` | Subida a Google Play; no se instala directamente |
| Mapping de R8 | `app/build/outputs/mapping/release/mapping.txt` | Archivar con la versión para poder leer trazas ofuscadas |

Google Play puede volver a firmar con Play App Signing; esa instalación puede tener otro `ANDROID_ID` que el APK firmado directamente. Elige un canal por cliente y no mezcles firmas.

## 5. Verificar artefactos

Para APK firmado:

```bash
BT="$ANDROID_HOME/build-tools/35.0.0"
"$BT/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" manifest debuggable app/build/outputs/apk/release/app-release.apk
```

Comprueba que la verificación de firma sea correcta, que la huella coincida con la anotada y que `debuggable` sea `false`. En el manifiesto no deben aparecer permisos nuevos fuera de la lista autorizada que valida `spviPermisos`.

Para publicar, genera también un SHA-256 y súbelo junto con el APK a una GitHub Release pública:

```bash
sha256sum app/build/outputs/apk/release/app-release.apk
```

La app solo ofrece instalar una actualización si puede validar el hash y Android acepta la firma.

## 6. Prueba de humo en dispositivo

La lista de regresión detallada está en [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md). Como mínimo, antes de entregar:

1. Instala/actualiza el APK en Android 8.0 o superior; confirma que los datos anteriores se conservan.
2. Completa el inicio, abre turno, vende un producto y un servicio; confirma que inventario, caja y registros quedan coherentes.
3. Comprueba que no se puede guardar un precio de venta igual o inferior al costo, ni un precio tras preajustes igual o inferior; intenta vender con un descuento que cruce el costo.
4. Prueba inventario, servicios, insumos, fechas, cantidades, filtros monetarios y porcentajes con escritura y pegado, incluidos `1,450.00 CUP`, comas decimales y agrupaciones inválidas.
5. Exporta un respaldo con contraseña, impórtalo en un dispositivo de prueba y confirma que una contraseña incorrecta no altera los datos.
6. Exporta sin contraseña: debe aparecer primero el aviso de que cualquiera que obtenga el archivo podrá leerlo. Con biometría disponible, la acción debe exigir biometría sin alternativa de PIN; sin biometría, debe exigir aceptar el aviso. Cancela el diálogo y confirma que no se inicia la exportación. La biometría autoriza la acción: no cifra ni protege el archivo.
7. Revisa el QR de transferencia, vinculación principal/secundaria, sincronización en red local y actualización firmada si se distribuye ese flujo.
8. Prueba tema claro/oscuro, letra grande, navegación Atrás y contenido sensible en Recientes.

No uses datos reales de clientes en las pruebas; la importación de un respaldo reemplaza los datos locales.

## 7. Publicar y actualizar

1. Aumenta `versionCode` y `versionName` en `app/build.gradle.kts`.
2. Completa las verificaciones de las secciones anteriores y guarda mapping y hash.
3. Publica APK y hash en una Release **pública** del repositorio configurado como `spviGithubRepo`.
4. Instala la versión desde el mismo canal y verifica que se ofrece, que el SHA-256 coincide y que Android reconoce la firma.
5. No edites la Release `revocaciones`: pertenece al servicio GL y contiene la lista de licencias revocadas.

Consulta [docs/VERIFICACION.md](docs/VERIFICACION.md) para separar resultados de CI previos de pruebas pendientes sobre cambios nuevos.
