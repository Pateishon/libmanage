#!/usr/bin/env bash
# Ejecuta la prueba de carga contra una versión compilada de LibManage.
# Uso: carga/ejecutar.sh DIRECTORIO_CLASES ETIQUETA ARCHIVO_CSV [SEGUNDOS] [USUARIOS...]
set -euo pipefail
CLASES="$1"; ETIQUETA="$2"; CSV="$3"; SEGUNDOS="${4:-15}"; shift 4 || true
USUARIOS="${*:-10 50 100 200 400}"
PUERTO=8190
mkdir -p build
LIBMANAGE_PUERTO=$PUERTO LIBMANAGE_LIBROS=20000 LIBMANAGE_LECTORES=2000 LIBMANAGE_ITERACIONES=10000 \
  java -Xmx1g -cp "$CLASES" ${JFR:-} cl.libmanage.web.Servidor > "build/servidor-$ETIQUETA.log" 2>&1 &
SERVIDOR=$!
trap 'kill $SERVIDOR 2>/dev/null || true' EXIT
for _ in $(seq 60); do curl -s -o /dev/null "http://127.0.0.1:$PUERTO/api/salud" && break; sleep 1; done
java -Xmx1g "$(dirname "$0")/PruebaCarga.java" "http://127.0.0.1:$PUERTO" "$ETIQUETA" "$CSV" "$SEGUNDOS" $USUARIOS
