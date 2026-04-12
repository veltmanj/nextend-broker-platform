# Browser Bootstrap Example

This example is a small static frontend that reads the broker-platform bootstrap endpoints:

- `GET /broker/wt/info`
- `GET /broker/auth/token`

It does not require a build step. Serve the directory with any static file server and open it in a browser.

## Local usage

1. Configure the broker to allow the example origin. For example, if you will serve the page from `http://localhost:8080`, set this in your `.env` file before starting Docker Compose:

```bash
BROKER_WEBTRANSPORT_ALLOWED_ORIGIN_0=http://localhost:8080
```

1. Start the broker platform:

```bash
docker compose up --build
```

1. Serve this example directory:

```bash
cd examples/browser-bootstrap
python3 -m http.server 8080
```

1. Open `http://localhost:8080` in your browser.

The page fetches the broker info and auth-token endpoints from `http://localhost:6933` by default. If your broker runs elsewhere, change the base URL in the form.

## What it shows

- broker browser URL hint
- broker certificate hash
- issued JWT token
- token expiry and scope
- a composed launch URL you can pass into downstream client pages
