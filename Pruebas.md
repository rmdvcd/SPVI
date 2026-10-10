# Orden de verificación en la PC con OpenCode CLI

1. Leer `AGENTS.md`, `Contexto.md`, `Pendiente.md` y `desktop/VERIFICACION_PC.md`.
2. Conservar todos los cambios Android y Python existentes; no reemplazarlos por la rama base.
3. Python: crear entorno e instalar `desktop/requirements.txt`. Desde `desktop/`, ejecutar:
   `.\.venv\Scripts\python.exe -m unittest discover -s tests -v`.
4. Corregir errores sin eliminar aserciones ni desactivar validaciones. Revisar también JavaScript,
   formularios, reintentos y seguridad en un navegador real.
5. Ejecutar `desktop/build_windows.ps1`, que prueba antes de empaquetar y ejecuta `--self-test` después.
6. Android, desde la raíz y con JDK/SDK instalados: `.\gradlew.bat spviTests`, luego
   `.\gradlew.bat spviCheck`; instrumentados/dispositivos según `docs/OPENCODE_DESKTOP.md`.
7. Interoperabilidad real con móviles y GL: completar los escenarios de `desktop/VERIFICACION_PC.md`.
8. Registrar resultados reales, versiones de herramientas y fallos. El estado actual es **sin
   pruebas ni compilación ejecutadas para esta ampliación**, no “aprobado”.

No usar los datos reales como primera prueba. Conservar copias antes de restaurar, activar licencias
o cambiar configuraciones de certificados/firewall en la PC.
