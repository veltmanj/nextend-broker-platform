#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE_DIR="${ROOT_DIR}/deploy/helm/nextend-broker-platform/templates"

if command -v rg >/dev/null 2>&1; then
  SEARCH_CMD=(rg -n '\{ \{|\} \}' "${TEMPLATE_DIR}")
else
  SEARCH_CMD=(grep -RniE '\{ \{|\} \}' "${TEMPLATE_DIR}")
fi

if "${SEARCH_CMD[@]}" >/dev/null; then
  echo "Found malformed Helm template delimiters in ${TEMPLATE_DIR}." >&2
  echo "Use '{{' and '}}' without spaces inside the braces." >&2
  "${SEARCH_CMD[@]}" >&2
  exit 1
fi

helm template nextend-broker-platform "${ROOT_DIR}/deploy/helm/nextend-broker-platform" >/dev/null

echo "Helm templates validated successfully."
