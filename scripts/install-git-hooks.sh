#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v git >/dev/null 2>&1; then
  echo "git is required to install repository hooks." >&2
  exit 1
fi

cd "${ROOT_DIR}"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "${ROOT_DIR} is not inside a git work tree." >&2
  exit 1
fi

chmod +x .githooks/pre-commit
git config core.hooksPath .githooks

echo "Installed repository hooks from .githooks/."
echo "The pre-commit hook now runs scripts/check-helm-templates.sh and scripts/check-compose-config.sh."
