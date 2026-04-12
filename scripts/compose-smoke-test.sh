#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

bash scripts/check-broker-prereqs.sh

export APP_SECURITY_JWT_SKEY="${APP_SECURITY_JWT_SKEY:-00112233445566778899aabbccddeeff}"
export APP_SECURITY_JWT_ISSUER="${APP_SECURITY_JWT_ISSUER:-nextend-broker}"
export BROKER_IMAGE="${BROKER_IMAGE:-nextend-broker-platform-broker:smoke}"
export SIDECAR_IMAGE="${SIDECAR_IMAGE:-nextend-broker-platform-sidecar:smoke}"
export CERT_PATH="${CERT_PATH:-}"
export KEY_PATH="${KEY_PATH:-}"
export BROKER_SIDECAR_MODE="${BROKER_SIDECAR_MODE:-companion}"
export SPRING_CLOUD_CONFIG_ENABLED="${SPRING_CLOUD_CONFIG_ENABLED:-false}"
export SPRING_CLOUD_DISCOVERY_ENABLED="${SPRING_CLOUD_DISCOVERY_ENABLED:-false}"
export EUREKA_CLIENT_ENABLED="${EUREKA_CLIENT_ENABLED:-false}"

cleanup() {
  local exit_code=$?
  if [[ $exit_code -ne 0 ]]; then
    docker compose logs broker sidecar || true
  fi
  docker compose down -v --remove-orphans >/dev/null 2>&1 || true
  exit $exit_code
}

trap cleanup EXIT

wait_for_exec() {
  local description=$1
  shift

  for _ in $(seq 1 60); do
    if "$@" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  echo "Timed out waiting for ${description}" >&2
  return 1
}

docker compose down -v --remove-orphans >/dev/null 2>&1 || true
(
  cd broker
  mvn -q -DskipTests package
)
docker compose up -d --build sidecar broker

wait_for_exec \
  "sidecar info endpoint" \
  docker compose exec -T sidecar node -e "fetch('http://127.0.0.1:9090/info').then((response)=>process.exit(response.ok ? 0 : 1)).catch(()=>process.exit(1))"

wait_for_exec \
  "sidecar readiness endpoint" \
  docker compose exec -T sidecar node -e "fetch('http://127.0.0.1:9090/readyz').then((response)=>process.exit(response.ok ? 0 : 1)).catch(()=>process.exit(1))"

wait_for_exec \
  "broker actuator health" \
  docker compose exec -T broker curl -fsS http://127.0.0.1:6933/actuator/health

docker compose exec -T sidecar node <<'EOF'
(async () => {
  const info = await fetch('http://127.0.0.1:9090/info').then((response) => response.json());
  const ready = await fetch('http://127.0.0.1:9090/readyz').then((response) => response.json());

  if (info.contractVersion !== 1) {
    throw new Error(`Unexpected sidecar contract version: ${info.contractVersion}`);
  }

  if (ready.status !== 'ready') {
    throw new Error(`Unexpected sidecar status: ${ready.status}`);
  }

  if (!ready.certificateFingerprint) {
    throw new Error('The sidecar did not report a certificate fingerprint.');
  }

  if (ready.brokerUrl !== 'https://broker:7171/rsocket') {
    throw new Error(`Unexpected sidecar brokerUrl: ${ready.brokerUrl}`);
  }
})();
EOF

docker compose exec -T sidecar node --input-type=module <<'EOF'
import { connectAsync } from '@currentspace/http3';

const timeout = setTimeout(() => {
  console.error('Timed out opening the broker H3 relay connection.');
  process.exit(1);
}, 5000);

const session = await connectAsync('broker:7171', {
  rejectUnauthorized: false,
});

clearTimeout(timeout);
await session.close();
EOF

echo "Compose smoke test passed."
