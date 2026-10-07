#!/usr/bin/env bash
# Muestras de TODAS las exportaciones de SPVI (PDF, Excel e imágenes; 0.26.0: sin textos) con el código real.
# Requiere haber pasado antes tools/verificacion/verificar.sh (reutiliza su caché de clases y librerías).
# Uso: TC=~/.cache/spvi-tc bash tools/exportaciones/generar.sh <salida>
set -euo pipefail
TC=${TC:-$HOME/.cache/spvi-tc}; OUT=$TC/out; SAL=${1:?salida}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
export JAVA_HOME="$TC/jdk"; export PATH="$JAVA_HOME/bin:$PATH"
cp_de() { ls "$TC/lib/$1"/*.jar | tr '\n' ':'; }
CP="$(cp_de jvm)$(cp_de data)$OUT/core:$OUT/licencia:$OUT/domain:$OUT/data:$OUT/app:$TC/stub/cls:$TC/stub/rtcls:$TC/android-all.jar"
mkdir -p "$TC/out/muestras"
JAVA_OPTS="-Xmx900m" "$TC/kotlinc/bin/kotlinc" -jvm-target 11 -nowarn -cp "$CP" -d "$TC/out/muestras" "$ROOT/tools/exportaciones/Muestras.kt" 2>&1 | grep -v '^warning' || true
java -cp "$TC/out/muestras:$CP:$TC/kotlinc/lib/kotlin-stdlib.jar" cu.spvi.tools.exportaciones.MuestrasKt "$SAL"
python3 "$ROOT/tools/exportaciones/render.py" "$SAL"
