# Browser Bootstrap Example

This example is a small browser frontend that reads the broker-platform bootstrap endpoints and then connects with `rsocket-broker-client-js@0.0.32` over WebTransport:

- `GET /broker/wt/info`
- `GET /broker/auth/token`
- `wts://.../broker/wt` session probe driven by `rsocket-broker-client-js@0.0.32`

It does not require a separate frontend build command. A tiny Node server is included so you can serve the page locally over HTTP and HTTPS, proxy `/broker/*` requests back to the broker management port, and bundle the browser app from npm dependencies on demand with `esbuild`.

## Local usage

1. Configure the broker to allow the example origin. For example, if you will serve the page from `http://localhost:8080` or `https://localhost:8443`, set these in your `.env` file before starting Docker Compose:

```bash
BROKER_WEBTRANSPORT_ALLOWED_ORIGIN_0=http://localhost:8080
BROKER_WEBTRANSPORT_ALLOWED_ORIGIN_1=https://localhost:8443
```

1. Start the broker platform:

```bash
docker compose up --build
```

1. Install the example dependencies:

```bash
cd examples/browser-bootstrap
npm install
```

1. Start the local example server:

```bash
node server.mjs
```

1. Open `http://localhost:8080` or `https://localhost:8443` in your browser.

The local example server proxies `/broker/*` requests to `http://localhost:6933` by default, so the page works on either origin without mixed-content issues. If your broker runs elsewhere, set `BROKER_BASE_URL` when you start the server:

```bash
BROKER_BASE_URL=http://localhost:16933 node server.mjs
```

## What it shows

- broker browser URL hint
- broker certificate hash
- issued JWT token
- token expiry and scope
- a composed launch URL you can pass into downstream client pages
- a live WebTransport connectivity probe that connects with `rsocket-broker-client-js@0.0.32`

## Notes

- The session probe validates that `rsocket-broker-client-js@0.0.32` can open and close a WebTransport-backed RSocket connection against the broker platform.
- `server.mjs` tries to generate a local self-signed certificate with `openssl` for the HTTPS listener if you do not provide `HTTPS_CERT_PATH` and `HTTPS_KEY_PATH`.
- If you only want the HTTP listener, start the server with `ENABLE_HTTPS=0 node server.mjs`.
