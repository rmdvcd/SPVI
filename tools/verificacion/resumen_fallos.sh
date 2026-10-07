#!/usr/bin/env bash
# T0.1 del PLAN_CORRECCIONES.md — resumen legible de un fallo del CI.
#
# Uso:  bash tools/verificacion/resumen_fallos.sh /tmp/salida.log
#
# Por qué existe: los registros del CI se sirven desde un almacenamiento (Azure Blob) que algunos entornos no
# pueden leer. Para que el fallo sea visible desde la API de GitHub (check-runs → annotations y summary) y desde
# la pestaña Actions, este script extrae lo esencial y lo publica en $GITHUB_STEP_SUMMARY y como ::error::.
# Nunca sale con error: su misión es informar, no enmascarar ni añadir fallos.
set -uo pipefail

LOG="${1:-/tmp/salida.log}"
MAX="${MAX_LINEAS:-160}"
SALIDA="${GITHUB_STEP_SUMMARY:-/dev/stdout}"

# --- 1. Errores de compilación y tareas de Gradle que fallaron -----------------------------------------------
{
  printf '\n## Resumen del fallo\n'
  if [ ! -s "$LOG" ]; then
    printf '\nEl log `%s` no existe o está vacío (¿el fallo ocurrió antes de ejecutar el comando?).\n' "$LOG"
  else
    if grep -q -E '^e: |: error: |Execution failed for task' "$LOG"; then
      printf '\n### Compilación y tareas fallidas\n\n```\n'
      grep -E '^e: |: error: |Execution failed for task|^FAILURE: ' "$LOG" | head -60 || true
      printf '```\n'
    fi
    if grep -q -E ' FAILED$' "$LOG"; then
      printf '\n### Tests fallidos (según el log)\n\n```\n'
      grep -E ' FAILED$' "$LOG" | head -40 || true
      printf '```\n'
    fi
  fi
} >> "$SALIDA"

# --- 2. Detalle de los tests fallidos y de lint, desde los XML de resultados ---------------------------------
python3 - >> "$SALIDA" 2>/dev/null <<'PY' || true
import glob
import xml.etree.ElementTree as ET

def primeras_lineas(texto, n=30):
    lineas = (texto or "").splitlines()
    return "\n".join(lineas[:n]) + ("\n[...]" if len(lineas) > n else "")

fallos = []
for xml in glob.glob("**/build/test-results/**/*.xml", recursive=True):
    try:
        raiz = ET.parse(xml).getroot()
    except Exception:
        continue
    for caso in raiz.iter("testcase"):
        problemas = caso.findall("failure") + caso.findall("error")
        if problemas:
            fallos.append((caso.get("classname", "?"), caso.get("name", "?"), primeras_lineas(problemas[0].text)))

if fallos:
    print(f"\n### Tests fallidos (XML de resultados): {len(fallos)}\n")
    for clase, nombre, detalle in fallos[:15]:
        print(f"- `{clase}.{nombre}`\n\n```\n{detalle}\n```\n")

errores_lint = []
for xml in sorted(glob.glob("**/build/reports/lint-results-*.xml", recursive=True)):
    try:
        raiz = ET.parse(xml).getroot()
    except Exception:
        continue
    for issue in raiz.iter("issue"):
        if issue.get("severity") in ("Error", "Fatal"):
            lugar = issue.find("location")
            errores_lint.append(
                f"{issue.get('id')}: {issue.get('message')} "
                f"({lugar.get('file') if lugar is not None else '?'}:{lugar.get('line') if lugar is not None else '?'})"
            )

if errores_lint:
    print(f"\n### Errores de lint: {len(errores_lint)}\n")
    for error in errores_lint[:40]:
        print(f"- {error}")

if not fallos and not errores_lint:
    print("\n(Sin XML de tests ni de lint: el fallo fue antes de generarlos.)\n")
PY

# --- 3. Últimas líneas del log, para el contexto que no captan los extractos ---------------------------------
if [ -s "$LOG" ]; then
  {
    printf '\n### Últimas %s líneas del log\n\n```\n' "$MAX"
    tail -n "$MAX" "$LOG"
    printf '```\n'
  } >> "$SALIDA"
fi

# --- 4. Anotaciones (pestaña Actions y API de check-runs): los mensajes clave, escapados ----------------------
anotar() {
  local linea
  while IFS= read -r linea; do
    printf '::error::%s\n' "${linea//%/%25}"
  done
}
grep -E '^e: |Execution failed for task|^FAILURE: ' "$LOG" 2>/dev/null | head -6 | anotar || true
grep -E ' FAILED$' "$LOG" 2>/dev/null | head -4 | anotar || true

exit 0
