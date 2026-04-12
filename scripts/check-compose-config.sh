#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

export APP_SECURITY_JWT_SKEY="${APP_SECURITY_JWT_SKEY:-test-secret}"
export BROKER_IMAGE="${BROKER_IMAGE:-ghcr.io/nextend/broker-platform-broker:test}"
export SIDECAR_IMAGE="${SIDECAR_IMAGE:-ghcr.io/nextend/broker-platform-sidecar:test}"
export CERT_PATH="${CERT_PATH:-/certs/tls.crt}"
export KEY_PATH="${KEY_PATH:-/certs/tls.key}"

docker compose config >/dev/null

echo "Docker Compose config validated successfully."
