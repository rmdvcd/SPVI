# SPVI para iOS (Flutter)

Versión idéntica y con paridad funcional total del **Sistema de Punto de Venta e Inventario (SPVI)**, diseñada y optimizada específicamente para **iOS (iPhone e iPad)** siguiendo los **Apple Human Interface Guidelines (HIG)**.

Desarrollada con **Flutter** y componentes nativos **Cupertino**, no requiere disponer de una computadora Mac para compilar o probar, gracias a la integración continua con **Codemagic**.

---

## 📱 Características y Paridad Funcional

- **Inicio**:
  - Banner de licencia offline y días restantes de prueba.
  - Gestión de turno con fondo inicial obligatorio, control de ventas en tiempo real y arqueo de caja con botón rápido de cuadre.
  - Botones de acción rápida: **Nueva Venta** y **Escanear** accesibles y ergonómicos.
  - Alertas visuales de stock bajo y crítico tanto para productos terminados como para insumos.
- **Venta**:
  - Catálogo interactivo con botones de incremento/decremento nativos.
  - Métodos de pago duales: **Efectivo** o **Transferencia QR** (Transfermóvil / EnZona).
  - Selector y buscador predictivo de **Clientes Fijos** por Carné de Identidad (CI) o nombre.
  - Descuento automático de stock en inventario y registro de transacciones.
- **Caja (Arqueo)**:
  - Registro de Entradas y Salidas de efectivo con justificación de motivo.
  - Cálculo instantáneo de efectivo esperado = fondo inicial + ventas efectivo + entradas − salidas.
- **Inventario & Insumos**:
  - Gestión de Productos terminados, Elaborados (recetas) e Insumos (unidades, kg, lb).
  - Filtros segmentados iOS y buscador en tiempo real.
- **Servicios**:
  - Registro y cobro de servicios con rubro libre, tarifa e importe.
- **Registros**:
  - Historial cronológico de Ventas con desglose y posibilidad de **anulación de venta** (solo con turno abierto).
  - Directorio de Clientes Fijos con carné, teléfono y dirección.
  - Historial de movimientos de caja.
- **Ajustes**:
  - Configuración de perfil del negocio (titular, carné, razón comercial).
  - Configuración de teléfonos y tarjetas para pago por transferencia QR.
  - Control de acceso con clave / Face ID.
  - Activación de licencia offline permanente.
  - Asistente de migración de 4 pasos y respaldo cifrado `.spvi`.

---

## 🎨 Experiencia Nativa iOS (Human Interface Guidelines)

- **Widgets Cupertino**: Uso integral de `CupertinoPageScaffold`, `CupertinoNavigationBar` (con Large Titles dinámicos), `CupertinoTabScaffold`, `CupertinoSlidingSegmentedControl`, `CupertinoActionSheet`, `CupertinoAlertDialog` y `CupertinoListTile`.
- **Feedback Háptico (`SpviHaptics`)**: Integración con `HapticFeedback.lightImpact()`, `mediumImpact()`, `heavyImpact()` y `selectionClick()` para cada interacción de usuario.
- **Sin Overscroll Glow**: Eliminación del estiramiento o glow tipo Android, implementando rebote inercial nativo (`BouncingScrollPhysics`).
- **Safe Areas**: Adaptación completa a Dynamic Island, Notch y barra de inicio inferior.
- **Identidad de Marca**: Conservación exacta de los colores oficiales de SPVI (`#2B4FA3` Navy, `#66D1EF` Sky, `#478EA1` Teal, `#EE7F3B` Orange, `#F6B645` Amber) en modo claro y oscuro.

---

## ☁️ Compilación y Pruebas en la Nube con Codemagic (Sin Mac)

Al no disponer de un Mac local, el proyecto está completamente preparado para compilarse en la nube en máquinas macOS con Apple Silicon mediante **Codemagic**.

### Pasos para Configurar y Compilar:

1. **Crear cuenta o Iniciar sesión en Codemagic**:
   - Entra en [https://codemagic.io](https://codemagic.io) y conéctate con tu cuenta de GitHub/GitLab.

2. **Añadir el Repositorio**:
   - Selecciona el repositorio de SPVI y escoge la opción **Flutter App**.

3. **Detección Automática de `codemagic.yaml`**:
   - Codemagic detectará automáticamente el archivo `codemagic.yaml` ubicado en la raíz del repositorio o dentro de `flutter_app/`.
   - El flujo configurado es `ios-simulator-and-release` utilizando máquinas virtuales `mac_mini_m2`.

4. **Probar con Codemagic App Preview (Simulador en el Navegador)**:
   - El script compila el target `flutter build ios --simulator --debug`.
   - Empaqueta el archivo `SPVI-Simulator.zip`.
   - Codemagic te permite probar la aplicación directamente en una ventana de simulador virtual transmitida a tu navegador, o descargar el `.app` para ejecutarlo en cualquier simulador de iOS.

5. **Firma Automática de Desarrollo y Distribución (Opcional)**:
   - En los ajustes de Codemagic (*Settings -> Code signing -> iOS*), puedes conectar tu cuenta de desarrollador de Apple (App Store Connect API Key) para generar certificados de desarrollo automáticos y compilar archivos `.ipa` instalables en dispositivos físicos.
   - Si no configuras firma, el pipeline compilará el `.app` para simulador y el `.ipa` no firmado (`--no-codesign`) listo para testing.

---

## 🛠️ Arquitectura del Código

- **Estado y Lógica**: [Flutter Riverpod](https://riverpod.dev/) (`StateNotifierProvider`) para reactividad pura, inmutabilidad y separación limpia de capas (Presentation, Domain, Data).
- **Null Safety**: 100% estricto con Dart 3.x.
- **Persistencia y Modelos**: Modelos con tipos inmutables, `Money` en centavos para precisión contable sin pérdidas por punto flotante.
