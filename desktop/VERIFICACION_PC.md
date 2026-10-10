# Verificación de la principal web con OpenCode CLI

Esta ampliación está sin ejecutar: no se han pasado pruebas, comprobaciones sintácticas,
compilación ni empaquetado después de incorporarla. No usar datos reales como primera prueba.

## Encargo para OpenCode

Desde la raíz del repositorio, pedir:

> Lee AGENTS.md, desktop/README.md y desktop/VERIFICACION_PC.md. Conserva los cambios
> Android y web existentes. La web es independiente y solo principal, no una sustitución
> del móvil. Ejecuta ahora en esta PC las pruebas Python, corrige los fallos sin debilitar
> las aserciones y registra resultados reales. Después empaqueta Windows y verifica el EXE.
> No confundas los resultados anteriores con los de esta ampliación. Antes de declarar
> paridad, revisa los flujos implementados de desktop/README.md y registra las comprobaciones reales.

## Orden de ejecución (PowerShell, Windows)

```powershell
cd desktop
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
$env:PYTHONPATH = "."
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
# Solo después de corregir todos los fallos:
powershell -ExecutionPolicy Bypass -File .\build_windows.ps1
.\dist\SPVI.exe --self-test
```

El script vuelve a ejecutar las pruebas antes de generar el EXE. Registrar versión de Python,
Windows, resultados y errores; no anotar “verificado” cuando solo se haya inspeccionado código.
`test_business.py` añade regresiones de atomicidad, agrupación del carrito, preajustes,
elaborados, devolución con receta modificada y validación básica de cuentas.

## Pruebas de aceptación manual

1. Usar directorio nuevo y datos ficticios. Abrir/cerrar turno, vender varias líneas y provocar
   insuficiencia en la última: no debe persistir ninguna línea ni consumo parcial.
2. Elaborado con stock propio cero y receta suficiente; comprobar costo congelado, consumo,
   edición posterior de receta y anulación. Repetir anulación: no devolver dos veces.
3. Aplicar preajustes acumulados con método y mínimo de carrito. Rechazar precio bajo costo.
4. En HTTPS local, comprobar dominio, certificado confiable, sesión, CSRF y descargas.
   La URL solicitada requiere configuración de hosts/certificado: no es un dominio público
   provisionado automáticamente. Ver instrucciones HTTPS de README.md.
5. Activar servidor LAN en IPv4 privada de la PC (puerto 47811). Abrir firewall solo en perfil
   privado. Vincular Android mediante QR; comprobar vencimiento y rechazo de reutilización.
6. Vender offline en secundaria; reconectar, reenviar lote y reiniciar principal. No duplicar
   ventas ni movimientos. Probar dos conexiones del mismo empleado y revocación en caliente.
7. Solicitar fondo/cierre, aprobar/rechazar, cambiar permisos y cuentas asignadas. Probar licencia
   vencida: no debe impedir recibir registros offline ya pendientes.
8. Exportar/importar `.spvi` con y sin contraseña en ambos sentidos usando copias. Verificar
   centavos, UUID, recetas, turnos, anulaciones y clientes; los turnos deben estar cerrados.
   Una restauración no transfiere licencia ni emparejamientos; vincular de nuevo.
9. Probar `.spvidesk` antiguo/nuevo, contraseña incorrecta, archivo truncado y copia previa.
   Ante fallo, los datos anteriores deben permanecer completos.
10. Comprobar licencia GL real de desarrollo, firmas inválidas, revocaciones antiguas/nuevas,
    recuperación y reloj atrasado. Nunca incluir datos de licencia o QR en logs o capturas.

Se añadieron pruebas de servidor sin sockets, contrato GL con claves de prueba, codecs Android,
restauración canónica, DTO, operaciones HTTP, fotos, cotización, correcciones y configuración local.
**Están escritas, no ejecutadas.** No sustituyen las pruebas con Android/GL real ni la revisión visual.

11. Simular respuesta HTTP perdida y repetir la misma clave: una sola venta, descuento y arqueo.
    Repetir la clave cambiando cuerpo: conflicto. Probar también alta de empleado y catálogo.
12. Corregir venta local y de secundaria: original anulado, reemplazo referenciado, caja local
    inalterada si el turno es remoto. Fallar por stock en la corrección y comprobar rollback total.
13. Cotizar repetidamente: ninguna venta, movimiento, cliente o stock debe persistir.
14. Vender insumo suelto junto a servicio que gasta ese mismo insumo: sumar consumos correctamente.
15. Fotos válidas, formato falso, imagen grande, copia/restore y tarjeta exportada. Confirmar que
    `.spvi` abierto y reexportado en Android no conserva la extensión de fotos web.
16. Asignar cuentas distintas a dos empleados: cada instantánea debe mostrar solo la asignada.
    Probar tarjetas de 12/16/20 cifras, teléfono E.164 y documento alfanumérico admitido por GL.
17. Cerrar SPVI, recuperar clave por consola, reiniciar e intentar usar la cookie anterior: debe
    pedir acceso nuevo. Una segunda instancia no debe abrir la misma carpeta de datos.
18. Revisar teclado, mensajes dentro de diálogos, cabecera fija, botones flotantes, checkbox único,
    cuentas enmascaradas, selección de productos en preajustes y consulta de registros antiguos.

