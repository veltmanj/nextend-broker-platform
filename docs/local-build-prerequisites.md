# Local Build Prerequisites

## Why this exists

The extracted broker currently depends on RSocket artifacts that are available in local development but are not yet resolved from a clean hosted CI environment.

The current broker packaging path expects these artifacts to exist in the local Maven repository:

- `io.rsocket:rsocket-bom:1.2.0`
- `io.rsocket:rsocket-transport-h3:1.2.0`
- `io.netty.incubator:netty-incubator-codec-http3:0.0.30.Final-nextend-SNAPSHOT`
- `io.netty.incubator:netty-incubator-codec-classes-quic:0.0.73.Final-nextend-SNAPSHOT`
- `io.netty.incubator:netty-incubator-codec-native-quic:0.0.73.Final:linux-x86_64`
- `io.rsocket.broker:rsocket-broker-spring:0.4.0-SNAPSHOT`
- `io.rsocket.broker:rsocket-broker-common:0.4.0-SNAPSHOT`
- `io.rsocket.broker:rsocket-broker-common-spring:0.4.0-SNAPSHOT`
- `io.rsocket.broker:rsocket-broker-frames:0.4.0-SNAPSHOT`

## Bootstrap from local source repos

If you have the producer repositories checked out locally, run:

```bash
bash scripts/bootstrap-broker-prereqs.sh
```

The bootstrap script checks these locations in order:

- `RSOCKET_JAVA_REPO` and `RSOCKET_BROKER_REPO` if you set them explicitly
- Repository siblings such as `../rsocket-java`, `../../rsocket-java`, `../rsocket-broker`, and `../../rsocket-broker`
- Common home-directory checkouts such as `~/Documents/rsocket-java`, `~/src/rsocket-java`, `~/projects/rsocket-java`, `~/code/rsocket-java`, and the matching `rsocket-broker` paths

If your local checkout layout differs from those defaults, set the repository paths explicitly:

```bash
RSOCKET_JAVA_REPO=/path/to/rsocket-java \
RSOCKET_BROKER_REPO=/path/to/rsocket-broker \
bash scripts/bootstrap-broker-prereqs.sh
```

Use `DRY_RUN=1 bash scripts/bootstrap-broker-prereqs.sh` to verify the detected paths without publishing artifacts.

## Verification

Run:

```bash
bash scripts/check-broker-prereqs.sh
```

If that script fails, local broker packaging and the Compose smoke test will fail before Docker startup.

## Current workflow

1. Install the required artifacts with `bash scripts/bootstrap-broker-prereqs.sh`, or publish them by some other means.
2. Run `bash scripts/check-broker-prereqs.sh`.
3. Package the broker with `cd broker && mvn -q -DskipTests package`.
4. Run `bash scripts/compose-smoke-test.sh`.

## What needs to happen for hosted CI

To make the broker build reproducible outside local development, one of these needs to happen:

1. Publish the required RSocket broker artifacts to a reachable Maven repository.
2. Replace the snapshot broker dependencies with released coordinates.
3. Vendor or composite-build the required producer projects as part of this repository.

Until then, hosted CI should validate sidecar syntax and deployment manifest rendering, while broker compilation and end-to-end smoke remain a local or self-hosted runner responsibility.
