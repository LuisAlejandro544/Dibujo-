#!/usr/bin/env bash
# Ejecuta un script JavaScript del repo con Node.js.
# Uso: scripts/ci/ejecutar-js.sh ruta/script.js [args...]
set -euo pipefail
SCRIPT="${1:?falta ruta del script .js}"
shift
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
node "$ROOT/$SCRIPT" "$@"
