#!/usr/bin/env bash
# Flujo EN VIVO escáner → formulario → guardar con los ViewModels reales (requiere internet y un verificar.sh previo:
# usa $TC/out/{app,app-test,…} y los jars de $TC/lib). Uso: bash tools/escaner/probar_flujo.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; TC="${TC:-$HOME/.cache/spvi-tc}"; OUT="$TC/out"; K="$TC/kotlinc"
CP="$(ls "$TC"/lib/jvm/*.jar "$TC"/lib/data/*.jar "$TC"/lib/app/*.jar | tr '\n' ':')$OUT/core:$OUT/licencia:$OUT/domain:$OUT/data:$OUT/designsystem:$OUT/app:$OUT/app-test:$TC/stub/cls:$TC/android-all.jar:$K/lib/kotlin-stdlib.jar"
export JAVA_HOME="$TC/jdk"; export PATH="$JAVA_HOME/bin:$PATH"
D="$TC/escaner-flujo"; rm -rf "$D"; mkdir -p "$D"
JAVA_OPTS="-Xmx1200m" "$K/bin/kotlinc" -nowarn -jvm-target 17 -Xfriend-paths="$OUT/app" -cp "$CP" -d "$D" "$ROOT/tools/escaner/FlujoEnVivoTest.kt"
java -Xmx900m ${JOPTS:-} -Dsalida="$ROOT/tools/escaner/flujo.tsv" -cp "$TC/stub/rtcls:$D:$CP" org.junit.runner.JUnitCore cu.spvi.tools.escaner.FlujoEnVivoTest "$@"
