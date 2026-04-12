# Configuration Reference

## Precedence

Configuration precedence for `1.0.0`:

1. Explicit environment variables
2. Deployment manifest values
3. Product defaults

## Broker settings

| Variable                                      | Default                 | Purpose                                                            |
| --------------------------------------------- | ----------------------- | ------------------------------------------------------------------ |
| `APP_SECURITY_JWT_SKEY`                       | none                    | JWT signing key. Required.                                         |
| `APP_SECURITY_JWT_ISSUER`                     | `nextend-broker`        | Token issuer value.                                                |
| `BROKER_H3_ENABLED`                           | `true`                  | Enables the broker H3 endpoint.                                    |
| `BROKER_H3_BIND_ADDRESS`                      | `0.0.0.0`               | Bind address for broker H3 transport.                              |
| `BROKER_H3_PORT`                              | `7171`                  | Broker H3 port.                                                    |
| `BROKER_H3_PATH`                              | `/rsocket`              | Broker H3 path.                                                    |
| `BROKER_WEBTRANSPORT_ENABLED`                 | `true`                  | Enables browser-facing WebTransport support.                       |
| `BROKER_WEBTRANSPORT_BACKEND`                 | `chromium-quiche`       | Selects the WebTransport backend.                                  |
| `BROKER_WEBTRANSPORT_PORT`                    | `7443`                  | Browser-facing UDP port.                                           |
| `BROKER_WEBTRANSPORT_PATH`                    | `/broker/wt`            | Browser-facing WebTransport path.                                  |
| `BROKER_WEBTRANSPORT_AUTH_TOKEN_PATH`         | `/broker/auth/token`    | Auth token endpoint path.                                          |
| `BROKER_WEBTRANSPORT_ALLOWED_ORIGIN_0`        | empty                   | First allowed browser origin.                                      |
| `BROKER_WEBTRANSPORT_ALLOWED_ORIGIN_1`        | empty                   | Second allowed browser origin.                                     |
| `BROKER_WEBTRANSPORT_STARTUP_TIMEOUT`         | `20s`                   | Broker wait time for sidecar readiness.                            |
| `BROKER_SIDECAR_MODE`                         | `companion`             | `embedded` or `companion`.                                         |
| `BROKER_SIDECAR_ADMIN_URL`                    | `http://127.0.0.1:9090` | Sidecar admin endpoint used for readiness and contract validation. |
| `BROKER_SIDECAR_CONTRACT_VERSION`             | `1`                     | Expected broker-sidecar contract version.                          |
| `BROKER_WEBTRANSPORT_CHROMIUM_QUICHE_COMMAND` | platform-dependent      | Sidecar launch command for `embedded` mode.                        |

## Sidecar settings

| Variable                     | Default                       | Purpose                                          |
| ---------------------------- | ----------------------------- | ------------------------------------------------ |
| `HOST`                       | `0.0.0.0`                     | Sidecar bind address.                            |
| `PORT`                       | `7443`                        | Sidecar public UDP port.                         |
| `SESSION_PATH`               | `/broker/wt`                  | WebTransport session path.                       |
| `BROKER_URL`                 | `h3://127.0.0.1:7171/rsocket` | Internal broker bridge endpoint.                 |
| `BROKER_REJECT_UNAUTHORIZED` | `false`                       | TLS validation for broker bridge.                |
| `CERT_PATH`                  | none                          | TLS certificate file path. Required in TLS mode. |
| `KEY_PATH`                   | none                          | TLS key file path. Required in TLS mode.         |
| `SIDECAR_ADMIN_HOST`         | `127.0.0.1`                   | Loopback-only admin host.                        |
| `SIDECAR_ADMIN_PORT`         | `9090`                        | Admin API port for health and readiness.         |
| `SIDECAR_CONTRACT_VERSION`   | `1`                           | Broker-sidecar contract version.                 |
| `SIDECAR_LOG_FORMAT`         | `json`                        | `json` or `text`.                                |

## Required admin endpoints for `1.0.0`

The sidecar must expose loopback-only admin endpoints:

- `GET /healthz` returns process health.
- `GET /readyz` returns readiness with certificate fingerprint and contract version.
- `GET /info` returns sidecar version, build revision, and supported contract version.

## Supported launch behavior

- `BROKER_SIDECAR_MODE=embedded`: the broker launches the sidecar command and validates the sidecar through its admin URL.
- `BROKER_SIDECAR_MODE=companion`: the broker connects to an already running sidecar and validates `/info` and `/readyz` before accepting traffic.

## Configuration validation rules

- Broker startup fails if JWT signing key is missing.
- Broker startup fails if the sidecar contract version is unsupported.
- Sidecar startup fails if certificate files are missing in TLS mode.
- Kubernetes manifests must wire the same WebTransport path and port into both containers.
