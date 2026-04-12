#!/usr/bin/env bash

set -euo pipefail

M2_REPO="${M2_REPO:-${HOME}/.m2/repository}"

required_artifacts=(
  "io/rsocket/rsocket-bom/1.2.0/rsocket-bom-1.2.0.pom"
  "io/rsocket/rsocket-transport-h3/1.2.0/rsocket-transport-h3-1.2.0.jar"
  "io/netty/incubator/netty-incubator-codec-http3/0.0.30.Final-nextend-SNAPSHOT/netty-incubator-codec-http3-0.0.30.Final-nextend-SNAPSHOT.jar"
  "io/netty/incubator/netty-incubator-codec-classes-quic/0.0.73.Final-nextend-SNAPSHOT/netty-incubator-codec-classes-quic-0.0.73.Final-nextend-SNAPSHOT.jar"
  "io/netty/incubator/netty-incubator-codec-native-quic/0.0.73.Final/netty-incubator-codec-native-quic-0.0.73.Final-linux-x86_64.jar"
  "io/rsocket/broker/rsocket-broker-spring/0.4.0-SNAPSHOT/rsocket-broker-spring-0.4.0-SNAPSHOT.jar"
  "io/rsocket/broker/rsocket-broker-common/0.4.0-SNAPSHOT/rsocket-broker-common-0.4.0-SNAPSHOT.jar"
  "io/rsocket/broker/rsocket-broker-common-spring/0.4.0-SNAPSHOT/rsocket-broker-common-spring-0.4.0-SNAPSHOT.jar"
  "io/rsocket/broker/rsocket-broker-frames/0.4.0-SNAPSHOT/rsocket-broker-frames-0.4.0-SNAPSHOT.jar"
)

missing=0

for artifact in "${required_artifacts[@]}"; do
  if [[ ! -f "${M2_REPO}/${artifact}" ]]; then
    echo "missing: ${M2_REPO}/${artifact}" >&2
    missing=1
  fi
done

if [[ ${missing} -ne 0 ]]; then
  cat >&2 <<'EOF'
The broker cannot be packaged from a clean machine yet.

This repository currently depends on locally installed RSocket artifacts:
- io.rsocket:rsocket-bom:1.2.0
- io.rsocket:rsocket-transport-h3:1.2.0
- io.netty.incubator:netty-incubator-codec-http3:0.0.30.Final-nextend-SNAPSHOT
- io.netty.incubator:netty-incubator-codec-classes-quic:0.0.73.Final-nextend-SNAPSHOT
- io.netty.incubator:netty-incubator-codec-native-quic:0.0.73.Final:linux-x86_64
- io.rsocket.broker:*:0.4.0-SNAPSHOT

Install or publish those artifacts into your local Maven repository before running:
- bash scripts/bootstrap-broker-prereqs.sh
- mvn package in broker/
- bash scripts/compose-smoke-test.sh

If you want this to work in hosted CI, publish the required artifacts to a reachable Maven repository and update broker/pom.xml accordingly.
EOF
  exit 1
fi

echo "Broker Maven prerequisites are present in ${M2_REPO}."
