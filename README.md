# Nextend Broker Platform

Standalone product scaffold for the Nextend broker and WebTransport sidecar.

This repository now contains an extracted broker tree and an extracted WebTransport sidecar tree from the current workspace. It turns the current broker plus native sidecar approach into a product boundary that can be documented, versioned, deployed, and released as `1.0.0`.

## Product scope

- Java broker remains the control plane for routing, auth, and broker lifecycle.
- WebTransport sidecar terminates browser-facing QUIC, TLS, HTTP/3, and WebTransport.
- Both components are versioned and released together as one product line.
- Local development, container deployment, and Kubernetes deployment are first-class use cases.

## Repository layout

```text
broker/                      Java broker source and packaging
sidecar/                     Native/WebTransport sidecar source and packaging
deploy/helm/                 Kubernetes chart for a broker + sidecar pod
docs/adr/                    Architecture decisions
docs/release/                Release planning and release notes
docker-compose.yml           Local and CI deployment topology
.env.example                 Environment contract for local runs
```

## Runtime topology

The default production topology is a single workload with two containers:

- `broker`: Java process exposing internal broker transports and management endpoints.
- `sidecar`: browser-facing WebTransport terminator that forwards accepted streams into the broker over a local contract.

For local development, the same topology can run through `docker compose` with separate containers on one network.

## Design goals for `1.0.0`

- Stable and documented broker-sidecar contract.
- Clear configuration precedence and environment variables.
- Separate broker and sidecar images.
- Kubernetes and Docker Compose deployment assets in-repo.
- Structured health, readiness, metrics, and logs.
- Release automation and version compatibility policy.

## Quick start

1. Copy `.env.example` to `.env` and set secrets.
2. Build the broker and sidecar images with `docker compose build`.
3. Start the stack with `docker compose up`.
4. Verify broker health and sidecar readiness.
5. Run `bash scripts/compose-smoke-test.sh` for the repository smoke test.

The smoke test currently assumes the broker can be packaged from a locally built jar, which means the required custom Maven artifacts must already exist in your local Maven repository.

Run `bash scripts/check-broker-prereqs.sh` first if you want to validate that prerequisite explicitly.
Run `bash scripts/bootstrap-broker-prereqs.sh` if you have local `rsocket-java` and `rsocket-broker` source checkouts and want to publish the required artifacts into `~/.m2` automatically.

The Helm service template at `deploy/helm/nextend-broker-platform/templates/service.yaml` is intentionally excluded in `.prettierignore` because formatter drift kept rewriting Helm delimiters into invalid spaced braces.

Run `bash scripts/install-git-hooks.sh` if you want a local pre-commit guard that runs `bash scripts/check-helm-templates.sh` and `bash scripts/check-compose-config.sh` before each commit.

## Documentation index

- Architecture overview: `docs/architecture.md`
- Configuration reference: `docs/configuration.md`
- Broker-sidecar contract ADR: `docs/adr/0001-broker-sidecar-contract.md`
- Local build prerequisites: `docs/local-build-prerequisites.md`
- Release 1.0.0 plan: `docs/release/1.0.0-plan.md`

## Current state

The main product extraction and contract hardening work is complete:

1. The broker build and source tree live under `broker/`.
2. The sidecar package and runtime source live under `sidecar/`.
3. The broker-sidecar admin contract is implemented and validated in code.
4. Local bootstrap and Compose smoke scripts now validate the end-to-end path.
5. Hosted CI covers compile, syntax, and manifest rendering, while self-hosted smoke is isolated in its own workflow.

The next productization steps are:

1. Make broker dependency resolution reproducible from a published Maven source instead of local cache bootstrap.
2. Add Kubernetes runtime smoke coverage beyond template rendering.
3. Cut pre-release tags until the deployment matrix is green.
4. Release `1.0.0` once the gates in the release plan are met.
