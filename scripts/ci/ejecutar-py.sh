#!/usr/bin/env bash
# Ejecuta un script Python del repo con PYTHONPATH=tools (para importar módulos locales).
# Uso: scripts/ci/ejecutar-py.sh ruta/script.py [args...]
set -euo pipefail
SCRIPT="${1:?falta ruta del script .py}"
shift
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export PYTHONPATH="$ROOT/tools:${PYTHONPATH:-}"
python "$ROOT/$SCRIPT" "$@"
