# Broker WebTransport Chromium QUICHE Backend

## Goal

Retire the former `nextend-webtransport-edge` relay and keep browser WebTransport termination under the broker-owned deployment boundary.

## Why a new backend is needed

The current broker-native path uses Netty incubator HTTP/3 on top of Cloudflare `quiche`. That stack is good enough for experimental HTTP/3 and custom `CONNECT` handling, but it still does not complete a browser-compatible WebTransport opening handshake.

The important difference is that Chromium QUICHE already has a first-class WebTransport session model. The broker-managed sidecar uses that style of stack today.

## Recommended rollout

1. Start with a sidecar backend.
2. Keep the broker as the product boundary and source of routing/auth decisions.
3. Only move to JNI after the Chromium QUICHE backend is stable and we understand the packaging trade-offs.

The sidecar-first approach is lower risk because:

- it avoids JVM/native ABI work on the critical path
- it is easier to run and debug in Docker
- it allows direct Chromium WebTransport interop tests before we commit to an in-process integration

## Backend boundary added in the broker

The broker now has a backend seam under `nextend.nl.broker.webtransport.backend`:

- `BrokerWebTransportServer`
- `BrokerWebTransportServerFactory`
- `NettyExperimentalBrokerWebTransportServerFactory`
- `ChromiumQuicheBrokerWebTransportServerFactory`

`nextend.broker.webtransport.backend` selects the active backend. The current default remains `netty-experimental`.

## Expected Chromium QUICHE responsibilities

The Chromium QUICHE backend should:

- terminate QUIC + TLS + HTTP/3
- negotiate a real WebTransport session
- accept bidirectional streams on that session
- expose raw stream bytes to the broker transport bridge
- surface close/error events back into the broker lifecycle

## Minimal success target

Before connecting it to RSocket, the backend should pass this progression:

1. Browser `new WebTransport("https://.../broker/wt")` reaches `ready`.
2. The backend accepts one bidirectional stream.
3. The backend echoes bytes over that stream.
4. The broker maps that stream into an RSocket `DuplexConnection`.
5. The frontend talks directly to the broker without a standalone relay service.

## Integration options

### Sidecar

Recommended first implementation.

- A native server process owns Chromium QUICHE.
- The broker launches or connects to it.
- The sidecar relays accepted WebTransport sessions into the broker over a local transport.

Good local transports to consider:

- Unix domain socket on Linux/macOS
- localhost TCP loopback as a simpler first cut

### JNI

Possible later, but not the first move.

- Lower hop count
- Tighter runtime coupling
- More build and packaging complexity

## Current progress

The broker now has:

- a backend selector via `nextend.broker.webtransport.backend`
- a `chromium-quiche` sidecar launch mode
- a local sidecar package at `nextend-broker-webtransport-native`

The current sidecar behavior is:

- listen on `/broker/wt`
- terminate Chromium-QUICHE-backed WebTransport
- relay bytes into the broker's H3 `/rsocket` endpoint
- reuse the broker-provided certificate files

## Suggested next implementation step

Run the broker with:

```text
nextend.broker.webtransport.backend=chromium-quiche
nextend.broker.webtransport.chromium-quiche.mode=sidecar
```

Then validate:

1. The broker launches the sidecar cleanly.
2. Browser `WebTransport.ready` succeeds against the broker-owned endpoint.
3. The relay reaches the broker H3 endpoint.
4. The frontend can connect through the broker-managed sidecar path.
