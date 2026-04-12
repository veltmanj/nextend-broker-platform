#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOME_DIR="${HOME:-}"

find_repo() {
  local env_value="$1"
  shift
  local candidate

  if [[ -n "${env_value}" && -d "${env_value}" ]]; then
    printf '%s\n' "${env_value}"
    return 0
  fi

  for candidate in "$@"; do
    if [[ -d "${candidate}" ]]; then
      printf '%s\n' "${candidate}"
      return 0
    fi
  done

  return 1
}

run_gradle_publish() {
  local repo_path="$1"
  local repo_name="$2"

  echo "Publishing ${repo_name} artifacts from ${repo_path}"
  if [[ "${DRY_RUN:-0}" == "1" ]]; then
    echo "DRY_RUN=1, skipping ${repo_name} publish"
    return 0
  fi

  (
    cd "${repo_path}"
    ./gradlew publishToMavenLocal
  )
}

RSOCKET_JAVA_REPO="$({
  find_repo "${RSOCKET_JAVA_REPO:-}" \
    "${ROOT_DIR}/../rsocket-java" \
    "${ROOT_DIR}/../../rsocket-java" \
    "${HOME_DIR:+${HOME_DIR}/Documents/rsocket-java}" \
    "${HOME_DIR:+${HOME_DIR}/src/rsocket-java}" \
    "${HOME_DIR:+${HOME_DIR}/projects/rsocket-java}" \
    "${HOME_DIR:+${HOME_DIR}/code/rsocket-java}"
} || true)"

RSOCKET_BROKER_REPO="$({
  find_repo "${RSOCKET_BROKER_REPO:-}" \
    "${ROOT_DIR}/../nextend-app-components/rsocket-broker" \
    "${ROOT_DIR}/../../nextend-app-components/rsocket-broker" \
    "${ROOT_DIR}/../rsocket-broker" \
    "${ROOT_DIR}/../../rsocket-broker" \
    "${HOME_DIR:+${HOME_DIR}/Documents/rsocket-broker}" \
    "${HOME_DIR:+${HOME_DIR}/src/rsocket-broker}" \
    "${HOME_DIR:+${HOME_DIR}/projects/rsocket-broker}" \
    "${HOME_DIR:+${HOME_DIR}/code/rsocket-broker}"
} || true)"

if [[ -z "${RSOCKET_JAVA_REPO}" ]]; then
  echo "Unable to locate rsocket-java. Set RSOCKET_JAVA_REPO to continue." >&2
  exit 1
fi

if [[ -z "${RSOCKET_BROKER_REPO}" ]]; then
  echo "Unable to locate rsocket-broker. Set RSOCKET_BROKER_REPO to continue." >&2
  exit 1
fi

run_gradle_publish "${RSOCKET_JAVA_REPO}" "rsocket-java"
run_gradle_publish "${RSOCKET_BROKER_REPO}" "rsocket-broker"

bash "${ROOT_DIR}/scripts/check-broker-prereqs.sh"
