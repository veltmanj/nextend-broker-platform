# Nextend Broker Platform

Nextend Broker Platform packages the Java broker and the WebTransport sidecar as one deployable product. The broker stays responsible for routing, authentication, and lifecycle management, while the sidecar handles browser-facing QUIC, HTTP/3, TLS, and WebTransport traffic.

The repository is set up for three practical workflows: local development with Docker Compose, Kubernetes deployment through Helm, and CI validation of the broker-sidecar contract.

This standalone repository is the source of truth for broker-platform documentation, release assets, and examples.

## Repository layout

```text
broker/                      Java broker source and packaging
sidecar/                     WebTransport sidecar source and packaging
deploy/helm/                 Helm chart for a broker + sidecar deployment
docs/adr/                    Architecture decisions
docs/release/                Release planning and release notes
docker-compose.yml           Local runtime topology
.env.example                 Environment contract for local runs
scripts/                     Validation, bootstrap, and smoke-test helpers
```

## How the platform is meant to run

The default topology is a broker container plus a sidecar container.

- The broker exposes internal transports and management endpoints.
- The sidecar exposes the browser-facing WebTransport endpoint and bridges accepted sessions into the broker over H3.
- The broker validates the sidecar admin contract before accepting traffic in companion mode.

For local work, `docker compose` starts the same two-container topology on a single network. For Kubernetes, the Helm chart wires the same pairing into one workload.

## Prerequisites

- Docker with Compose support
- Java 17 for local broker packaging
- Maven for local broker packaging
- Node.js if you want to run or build the sidecar outside Docker
- Local producer checkouts for `rsocket-java` and `rsocket-broker` if the required Maven artifacts are not already in `~/.m2`

## Local quick start

1. Copy `.env.example` to `.env`.
2. Set at least `APP_SECURITY_JWT_SKEY` and any path or port overrides you need.
3. Check whether the required broker prerequisites are already installed:

```bash
bash scripts/check-broker-prereqs.sh
```

1. If the prerequisite check fails, publish the required producer artifacts into your local Maven repository:

```bash
bash scripts/bootstrap-broker-prereqs.sh
```

1. Start the stack:

```bash
docker compose up --build
```

1. Validate the runtime:

```bash
curl -fsS http://localhost:6933/actuator/health
curl -fsS http://localhost:9090/readyz
```

1. Run the repository smoke test when you want an end-to-end check:

```bash
bash scripts/compose-smoke-test.sh
```

## Bootstrap behavior

`scripts/bootstrap-broker-prereqs.sh` resolves producer repositories in this order:

1. `RSOCKET_JAVA_REPO` and `RSOCKET_BROKER_REPO` if you set them explicitly
2. Common sibling directories next to this repository, such as `../rsocket-java` and `../rsocket-broker`
3. Common home-directory checkouts, such as `~/Documents`, `~/src`, `~/projects`, and `~/code`

If your checkouts live elsewhere, pass them explicitly:

```bash
RSOCKET_JAVA_REPO=/path/to/rsocket-java \
RSOCKET_BROKER_REPO=/path/to/rsocket-broker \
bash scripts/bootstrap-broker-prereqs.sh
```

Use `DRY_RUN=1 bash scripts/bootstrap-broker-prereqs.sh` to verify detection without publishing artifacts.

## Useful commands

```bash
bash scripts/check-broker-prereqs.sh
bash scripts/bootstrap-broker-prereqs.sh
bash scripts/check-helm-templates.sh
bash scripts/check-compose-config.sh
bash scripts/compose-smoke-test.sh
bash scripts/install-git-hooks.sh
```

`scripts/install-git-hooks.sh` configures a local pre-commit guard that runs the Helm template and Compose validation checks before each commit.

## Runtime defaults

- Broker H3 endpoint: `h3://localhost:7171/rsocket`
- Browser-facing WebTransport endpoint: `https://localhost:7443/broker/wt`
- Broker health endpoint: `http://localhost:6933/actuator/health`
- Sidecar readiness endpoint: `http://localhost:9090/readyz`
- Sidecar info endpoint: `http://localhost:9090/info`

See `docs/configuration.md` for the full environment-variable reference.

## Browser and client integration

The platform exposes two public integration entry points for browser-facing clients:

1. `GET /broker/wt/info` returns JSON describing the active WebTransport endpoint, the broker certificate hash, and a browser URL hint.
2. `GET /broker/auth/token` returns a short-lived JWT payload that browser clients can present during broker setup.

For a local Docker Compose run, a minimal bootstrap flow looks like this:

```bash
curl -fsS http://localhost:6933/broker/wt/info
curl -fsS http://localhost:6933/broker/auth/token
```

The `/broker/wt/info` response includes a `browserUrlHint` similar to `wts://localhost:7443/broker/wt` and a `brokerCertHash` value that can be surfaced to browser code or copied into a local test page.

The `/broker/auth/token` response is JSON with these fields:

```json
{
  "token": "<jwt>",
  "scope": "DASHBOARD_READ",
  "issuedAt": "2026-04-12T12:00:00Z",
  "expiresAt": "2026-04-12T12:15:00Z"
}
```

A simple browser bootstrap looks like this:

```ts
const info = await fetch("http://localhost:6933/broker/wt/info").then(
  (response) => response.json(),
);
const auth = await fetch("http://localhost:6933/broker/auth/token").then(
  (response) => response.json(),
);

const brokerUrl = info.browserUrlHint;
const brokerCertHash = info.brokerCertHash;
const token = auth.token;

console.log({ brokerUrl, brokerCertHash, token });
```

That gives a browser application the three pieces it needs to start a WebTransport-based session against the sidecar: the `wts://` URL, the broker certificate hash, and a broker-issued JWT.

The existing `rsocket-broker-client-js` package in the wider workspace remains the reference for broker metadata conventions and requester interaction models. For browser-facing WebTransport clients, the broker platform itself is the source of truth for the connection hint and auth-token endpoints above.

There is also a runnable static example at `examples/browser-bootstrap` that fetches both endpoints, shows the returned JSON, and builds a launch URL from the broker hint and certificate hash.

## Kubernetes usage

The Helm chart lives under `deploy/helm/nextend-broker-platform`.

There is a sample values file at `deploy/helm/nextend-broker-platform/values.example.yaml` that you can copy and adjust for your environment:

```bash
cp deploy/helm/nextend-broker-platform/values.example.yaml /tmp/nextend-broker-platform.values.yaml
```

Validate the rendered manifests locally with:

```bash
helm template nextend-broker-platform deploy/helm/nextend-broker-platform \
  -f /tmp/nextend-broker-platform.values.yaml
```

Install or upgrade the chart with:

```bash
helm upgrade --install nextend-broker-platform deploy/helm/nextend-broker-platform \
  --namespace nextend-broker-platform \
  --create-namespace \
  -f /tmp/nextend-broker-platform.values.yaml
```

The sample values file covers image coordinates, broker and sidecar ports, JWT issuer settings, and the runtime paths used by the broker-sidecar pair.

The chart currently passes TLS certificate paths into the sidecar container, but your deployment still needs to make the actual certificate files available at those paths through your platform-specific secret and volume wiring.

The Helm service template at `deploy/helm/nextend-broker-platform/templates/service.yaml` is intentionally excluded in `.prettierignore` because formatter drift can corrupt Helm delimiters.

## Documentation index

- Architecture overview: `docs/architecture.md`
- Configuration reference: `docs/configuration.md`
- Broker-sidecar contract ADR: `docs/adr/0001-broker-sidecar-contract.md`
- Local build prerequisites: `docs/local-build-prerequisites.md`
- Release 1.0.0 plan: `docs/release/1.0.0-plan.md`

## Current status

- The broker and sidecar source trees are extracted and runnable from this repository.
- The broker-sidecar admin contract is implemented and validated in code.
- Local bootstrap and Compose smoke scripts validate the end-to-end path.
- Hosted CI validates sidecar syntax and deployment manifest rendering.
- Self-hosted CI handles broker compilation and end-to-end smoke because the broker still depends on locally published producer artifacts.

## Remaining product work

1. Make broker dependency resolution reproducible from a published Maven source instead of local cache bootstrap.
2. Add Kubernetes runtime smoke coverage beyond template rendering.
3. Keep cutting prereleases until the deployment matrix is stable.
4. Release `1.0.0` once the release plan gates are met.
