# ADR 0001: Broker-Sidecar Contract

- Status: Proposed
- Date: 2026-04-12
- Deciders: Nextend broker maintainers

## Context

The current broker-sidecar integration is effective for experimentation but still relies on implementation details that are too weak for a stable product release.

Current weaknesses include:

- sidecar readiness is inferred from log output
- certificate fingerprint exchange is coupled to log parsing
- deployment modes are not explicitly versioned
- broker and sidecar compatibility is not declared as a public contract

The product needs a contract that supports maintainability, cloud deployment, and coordinated releases.

## Decision

For `1.0.0`, broker and sidecar will remain separate runtime components under one product release line with a versioned local contract.

### Contract model

- Product release version and broker version use the same tag.
- Sidecar version is released from the same tag and declared in `/info`.
- Contract version is an integer, starting at `1`.
- Broker refuses startup when the sidecar contract version is missing or unsupported.

### Deployment modes

Two deployment modes are supported:

1. `embedded`
   - Broker launches the sidecar command.
   - Intended for local development and simple VM deployments.
2. `companion`
   - Sidecar runs as a separately managed process or container.
   - Intended for Docker Compose, Kubernetes, and cloud workloads.

Both modes must expose the same admin contract and the same data-plane behavior.

### Data plane

- Sidecar terminates QUIC, TLS, HTTP/3, and WebTransport for browser clients.
- Sidecar forwards accepted streams into the broker through the broker internal bridge endpoint.
- The broker remains authoritative for auth token issuance, routing, and broker semantics.

### Admin plane

The sidecar must expose a loopback-only HTTP admin interface with:

- `GET /healthz`
- `GET /readyz`
- `GET /info`

`GET /readyz` must return machine-readable JSON with at least:

```json
{
  "status": "ready",
  "contractVersion": 1,
  "certificateFingerprint": "...",
  "sessionPath": "/broker/wt",
  "brokerUrl": "ws://127.0.0.1:6934"
}
```

### Configuration contract

- Broker and sidecar configuration is environment-first.
- All supported variables are documented and versioned.
- Breaking configuration changes require a contract version bump or a compatibility bridge.

### Observability contract

- Broker and sidecar logs must include timestamp, level, component, version, and correlation id.
- Broker emits startup logs that record detected sidecar version and contract version.
- Sidecar emits readiness logs after the admin endpoint is ready, not before.

### Security contract

- Sidecar admin interface binds only to loopback.
- Certificate files are mounted from the runtime environment.
- Browser-facing ports are public only through explicit service exposure.

## Consequences

Positive consequences:

- broker-sidecar compatibility becomes testable and supportable
- cloud deployment is simpler because sidecar lifecycle is explicit
- maintainers can reason about behavior without reading implementation internals

Negative consequences:

- additional implementation work is required in the sidecar
- broker startup logic must change from log parsing to admin contract validation
- release automation must publish and verify two images per release

## Follow-up work

1. Implement the sidecar admin endpoints.
2. Update broker startup to validate `/info` and `/readyz`.
3. Add compatibility tests for `embedded` and `companion` modes.
4. Add release automation that publishes broker and sidecar images from one tag.
