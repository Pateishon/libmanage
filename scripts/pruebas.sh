#!/usr/bin/env bash
# Compila LibManage y ejecuta las pruebas JUnit sin Maven (solo JDK 21 + JUnit 4).
# Uso: scripts/pruebas.sh [clases de prueba...]   (sin argumentos ejecuta todas)
set -euo pipefail
cd "$(dirname "$0")/.."
JUNIT="${JUNIT_JAR:-lib/junit-4.13.2.jar}:${HAMCREST_JAR:-lib/hamcrest-core-1.3.jar}"
rm -rf build && mkdir -p build/classes build/test
javac -nowarn -encoding UTF-8 -d build/classes $(find src/main/java -name "*.java")
cp -r src/main/resources/* build/classes/
javac -nowarn -encoding UTF-8 -d build/test -cp "build/classes:$JUNIT" $(find src/test/java -name "*.java")
if [ "$#" -gt 0 ]; then
  CLASES="$*"
else
  CLASES=$(cd src/test/java && find . -name "*Test.java" -o -name "*IT.java" | sed 's|^\./||; s|\.java$||; s|/|.|g' | sort)
fi
java -cp "build/test:build/classes:$JUNIT" org.junit.runner.JUnitCore $CLASES
