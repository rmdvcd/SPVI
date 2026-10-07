#!/usr/bin/env bash
# Prueba EN VIVO del escáner con códigos reales (requiere internet y haber corrido verificar.sh antes:
# reutiliza las clases compiladas en $TC/out y los jars de $TC/lib). Uso: bash tools/escaner/probar.sh [pausa_ms]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; TC="${TC:-$HOME/.cache/spvi-tc}"; OUT="$TC/out"
K="$TC/kotlinc"; CP="$(ls "$TC"/lib/jvm/*.jar "$TC"/lib/data/*.jar | tr '\n' ':')$OUT/core:$OUT/domain:$OUT/data:$K/lib/kotlin-stdlib.jar"
export JAVA_HOME="$TC/jdk"; export PATH="$JAVA_HOME/bin:$PATH"
D="$TC/escaner"; rm -rf "$D"; mkdir -p "$D"
"$K/bin/kotlinc" -nowarn -cp "$CP" -d "$D" "$ROOT/tools/escaner/ProbarCodigos.kt" 2>&1 | grep -v "^warning" || true
java -cp "$D:$CP" cu.spvi.tools.escaner.ProbarCodigosKt "$ROOT/tools/escaner/codigos.tsv" "$ROOT/tools/escaner/resultados.tsv" "${1:-11000}"
