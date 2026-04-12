# Architecture Overview

## Product boundary

The product boundary is the combined broker platform, not the sidecar in isolation.

- The broker owns authentication, routing, policy, metrics aggregation, and lifecycle coordination.
- The sidecar owns browser-facing WebTransport termination and forwards accepted streams into the broker.
- Deployments may package both components separately, but they remain one supported runtime pair.

## Deployment modes

### Local development

- Run broker and sidecar as separate processes.
- Prefer `docker compose` for repeatability.
- Allow direct broker launch of the sidecar for developer convenience.

### Kubernetes and cloud

- Run broker and sidecar in the same Pod by default.
- Share lifecycle, logs, readiness, and certificates through the Pod boundary.
- Expose browser-facing UDP service through the sidecar port.
- Keep broker internal loopback ports non-public unless explicitly required.

## Maintainability rules

- Every externally visible setting must be documented in `docs/configuration.md`.
- Every broker-sidecar behavioral contract change requires an ADR or ADR update.
- Broker and sidecar versions must be released from the same Git tag.
- Health and readiness must be machine-readable, not log-scraped only.
- End-to-end browser interop tests are release blockers.

## Observability baseline

- Structured JSON logs with a shared correlation id.
- Prometheus-compatible broker metrics.
- Sidecar health and readiness endpoints.
- Startup event logs for broker version, sidecar version, contract version, and certificate fingerprint.

## Security baseline

- Secrets are injected through environment variables or Kubernetes secrets.
- Certificate material is mounted, not baked into images.
- Sidecar admin surface binds to loopback only.
- Broker validates the sidecar contract version during startup.
