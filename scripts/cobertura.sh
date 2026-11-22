#!/usr/bin/env bash
# Mide la cobertura de líneas de las pruebas JUnit usando la interfaz de depuración del JDK (JDI).
set -euo pipefail
cd "$(dirname "$0")/.."
JUNIT="lib/junit-4.13.2.jar:lib/hamcrest-core-1.3.jar"
scripts/pruebas.sh cl.libmanage.unitarias.JsonTest > /dev/null   # compila todo
mkdir -p build/herramientas
javac -nowarn -d build/herramientas -cp "$JUNIT" herramientas/Precarga.java
CLASES=$(cd src/test/java && find . -name "*Test.java" | sed 's|^\./||; s|\.java$||; s|/|.|g' | sort)
java herramientas/Cobertura.java "build/herramientas:build/test:build/classes:$JUNIT" $CLASES
