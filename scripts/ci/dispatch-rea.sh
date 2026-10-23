#!/usr/bin/env bash
# Dispara manualmente el workflow REA (ingeniería inversa) en GitHub Actions.
# Uso:
#   scripts/ci/dispatch-rea.sh [modo] [artefacto] [script_py] [script_js]
# Requiere: gh CLI autenticado (GH_TOKEN/PAT_TOKEN).
set -euo pipefail
MODO="${1:-full}"
ARTEFACTO="${2:-}"
PY="${3:-}"
JS="${4:-}"
gh workflow run rea-reverse-engineering.yml \
  -f modo="$MODO" \
  -f artefacto="$ARTEFACTO" \
  -f script_py="$PY" \
  -f script_js="$JS" \
  -f subir_artefactos=true
echo "Workflow REA disparado (modo=$MODO)."
