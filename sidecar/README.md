# nextend-broker-webtransport-native

Chromium-QUICHE-based WebTransport sidecar for the broker.

Current role:

- terminate browser WebTransport sessions
- relay raw bytes to the broker's H3 `/rsocket` endpoint
- reuse the broker-provided certificate so the browser hash matches the broker hint

Run locally:

```bash
cd sidecar
npm install
npm start
```

Useful environment variables:

- `HOST`
- `PORT`
- `SESSION_PATH`
- `BROKER_URL`
- `BROKER_REJECT_UNAUTHORIZED`
- `CERT_PATH`
- `KEY_PATH`
- `SIDECAR_ADMIN_HOST`
- `SIDECAR_ADMIN_PORT`
- `SIDECAR_CONTRACT_VERSION`

The sidecar now exposes a loopback-friendly admin contract:

- `GET /healthz`
- `GET /info`
- `GET /readyz`

The process still prints a readiness line containing:

```text
[broker-webtransport-native] ready
```

That log line is now informational only.

The broker validates the admin contract instead of relying on log output for readiness.
