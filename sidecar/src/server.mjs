#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdir, readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { dirname, join } from 'node:path';

import { connectAsync } from '@currentspace/http3';
import { Http3Server } from '@fails-components/webtransport';

const LOG_PREFIX = '[broker-webtransport-native]';
const HOST = process.env.HOST ?? '0.0.0.0';
const PORT = Number(process.env.PORT ?? '7443');
const SESSION_PATH = normalizePath(process.env.SESSION_PATH ?? '/broker/wt');
const BROKER_URL = new URL(normalizeBrokerUrl(process.env.BROKER_URL));
const BROKER_REJECT_UNAUTHORIZED =
  (process.env.BROKER_REJECT_UNAUTHORIZED ?? 'false').toLowerCase() !== 'false';
const SIDECAR_ADMIN_HOST = process.env.SIDECAR_ADMIN_HOST ?? '127.0.0.1';
const SIDECAR_ADMIN_PORT = Number(process.env.SIDECAR_ADMIN_PORT ?? '9090');
const SIDECAR_CONTRACT_VERSION = Number(process.env.SIDECAR_CONTRACT_VERSION ?? '1');
const SIDECAR_BUILD_REVISION = process.env.BUILD_REVISION ?? 'dev';
const PROVIDED_CERT_PATH = process.env.CERT_PATH || null;
const PROVIDED_KEY_PATH = process.env.KEY_PATH || null;
const CERT_DIR = process.env.CERT_DIR ?? './.data/certs';
const CERT_PATH = PROVIDED_CERT_PATH ?? join(CERT_DIR, 'localhost-cert.pem');
const KEY_PATH = PROVIDED_KEY_PATH ?? join(CERT_DIR, 'localhost-key.pem');

async function main() {
  const sidecarVersion = await loadSidecarVersion();
  const certificate = await loadOrCreateCertificate();
  const adminState = createAdminState(certificate.hash, sidecarVersion);
  const adminServer = createAdminServer(adminState);
  await listenAdminServer(adminServer);

  const server = new Http3Server({
    cert: certificate.cert,
    host: HOST,
    port: PORT,
    privKey: certificate.key,
    secret: process.env.WEBTRANSPORT_SECRET ?? 'nextend-broker-webtransport-native',
  });

  logStartup(certificate.hash, sidecarVersion);

  const sessionStream = server.sessionStream(SESSION_PATH);
  server.startServer();
  server.ready
    .then(() => {
      adminState.ready = true;
      adminState.status = 'ready';
      console.info(`${LOG_PREFIX} ready`, {
        adminUrl: `http://${SIDECAR_ADMIN_HOST}:${SIDECAR_ADMIN_PORT}`,
        brokerUrl: BROKER_URL.toString(),
        contractVersion: SIDECAR_CONTRACT_VERSION,
        host: HOST,
        path: SESSION_PATH,
        port: PORT,
      });
    })
    .catch((error) => {
      adminState.lastError = error instanceof Error ? error.message : String(error);
      adminState.ready = false;
      adminState.status = 'error';
      console.error(`${LOG_PREFIX} transport failed after startup`, error);
      process.exitCode = 1;
    });

  consumeSessions(sessionStream).catch((error) => {
    adminState.lastError = error instanceof Error ? error.message : String(error);
    adminState.ready = false;
    adminState.status = 'error';
    console.error(`${LOG_PREFIX} session loop failed`, error);
    process.exitCode = 1;
  });

  const shutdown = () => {
    adminState.ready = false;
    adminState.status = 'stopping';
    console.info(`${LOG_PREFIX} shutting down`);
    server.stopServer();
    adminServer.close();
  };

  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

function createAdminState(certificateFingerprint, version) {
  return {
    brokerUrl: BROKER_URL.toString(),
    buildRevision: SIDECAR_BUILD_REVISION,
    certificateFingerprint,
    contractVersion: SIDECAR_CONTRACT_VERSION,
    lastError: null,
    ready: false,
    sessionPath: SESSION_PATH,
    startedAt: new Date().toISOString(),
    status: 'starting',
    version,
  };
}

function createAdminServer(state) {
  return createServer((request, response) => {
    if (request.method !== 'GET') {
      writeJson(response, 405, { status: 'method_not_allowed' });
      return;
    }

    switch (request.url) {
      case '/healthz':
        writeJson(response, state.status === 'error' ? 500 : 200, {
          lastError: state.lastError,
          startedAt: state.startedAt,
          status: state.status === 'error' ? 'error' : 'ok',
          version: state.version,
        });
        return;
      case '/info':
        writeJson(response, 200, {
          buildRevision: state.buildRevision,
          contractVersion: state.contractVersion,
          startedAt: state.startedAt,
          version: state.version,
        });
        return;
      case '/readyz':
        writeJson(response, state.ready ? 200 : 503, {
          brokerUrl: state.brokerUrl,
          certificateFingerprint: state.certificateFingerprint,
          contractVersion: state.contractVersion,
          sessionPath: state.sessionPath,
          status: state.ready ? 'ready' : state.status,
          version: state.version,
        });
        return;
      default:
        writeJson(response, 404, { status: 'not_found' });
    }
  });
}

async function listenAdminServer(adminServer) {
  await new Promise((resolve, reject) => {
    adminServer.once('error', reject);
    adminServer.listen(SIDECAR_ADMIN_PORT, SIDECAR_ADMIN_HOST, () => {
      adminServer.off('error', reject);
      resolve(undefined);
    });
  });
}

function writeJson(response, statusCode, payload) {
  response.writeHead(statusCode, { 'content-type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify(payload));
}

async function consumeSessions(sessionStream) {
  const reader = sessionStream.getReader();

  while (true) {
    const { done, value } = await reader.read();

    if (done) {
      return;
    }

    handleSession(value).catch((error) => {
      console.error(`${LOG_PREFIX} session failed`, error);
      try {
        value.close({
          closeCode: 1,
          reason: sanitizeReason(error instanceof Error ? error.message : String(error)),
        });
      } catch {
        // Ignore follow-up close errors.
      }
    });
  }
}

async function handleSession(session) {
  await session.ready;

  const incomingStreams = session.incomingBidirectionalStreams.getReader();
  const incomingStream = await incomingStreams.read();

  if (incomingStream.done || !incomingStream.value) {
    throw new Error('The browser opened a WebTransport session without a bidirectional stream.');
  }

  const browserStream = incomingStream.value;
  const browserReader = browserStream.readable.getReader();
  const browserWriter = browserStream.writable.getWriter();
  const brokerBridge = await connectToBroker(browserWriter);

  let closed = false;
  const closeAll = async (error) => {
    if (closed) {
      return;
    }

    closed = true;

    try {
      await browserReader.cancel(error?.message).catch(() => undefined);
      await browserWriter.abort(error).catch(() => undefined);
      await brokerBridge.close(error);
      session.close({
        closeCode: error ? 1 : 0,
        reason: sanitizeReason(error?.message ?? 'closed'),
      });
    } catch {
      // Ignore close races.
    }
  };

  const browserToBroker = (async () => {
    let buffer = Buffer.alloc(0);

    while (true) {
      const { done, value } = await browserReader.read();

      if (done) {
        await brokerBridge.finishOutgoing();
        return;
      }

      if (!value || value.byteLength === 0) {
        continue;
      }

      buffer = Buffer.concat([
        buffer,
        Buffer.from(value.buffer, value.byteOffset, value.byteLength),
      ]);

      while (buffer.length >= 3) {
        const frameLength = readUInt24BE(buffer, 0);
        const frameEnd = frameLength + 3;

        if (buffer.length < frameEnd) {
          break;
        }

        const frame = buffer.subarray(3, frameEnd);
        await brokerBridge.sendFrame(frame);
        buffer = buffer.subarray(frameEnd);
      }
    }
  })();

  try {
    await Promise.race([browserToBroker, brokerBridge.incomingDone, session.closed]);
    await closeAll();
  } catch (error) {
    await closeAll(toError(error));
    throw error;
  }
}

async function connectToBroker(browserWriter) {
  if (isWebSocketBrokerUrl(BROKER_URL)) {
    return connectToBrokerOverWebSocket(browserWriter);
  }

  return connectToBrokerOverHttp3(browserWriter);
}

async function connectToBrokerOverHttp3(browserWriter) {
  const h3Session = await connectAsync(buildAuthority(BROKER_URL), {
    rejectUnauthorized: BROKER_REJECT_UNAUTHORIZED,
  });
  const h3Stream = h3Session.request(
    {
      ':authority': buildAuthority(BROKER_URL),
      ':method': 'POST',
      ':path': normalizePath(BROKER_URL.pathname),
      ':scheme': 'https',
      'content-type': 'application/rsocket',
    },
    {
      endStream: false,
    }
  );

  await waitForBrokerResponse(h3Stream);

  return {
    async close(error) {
      h3Stream.destroy(error);
      await h3Session.close().catch(() => undefined);
    },
    async finishOutgoing() {
      h3Stream.end();
    },
    incomingDone: new Promise((resolve, reject) => {
      h3Stream.on('data', (chunk) => {
        browserWriter.write(asUint8Array(chunk)).catch(reject);
      });
      h3Stream.on('end', resolve);
      h3Stream.on('close', resolve);
      h3Stream.on('error', reject);
      h3Session.on('error', reject);
      h3Session.on('close', resolve);
    }),
    async sendFrame(frame) {
      h3Stream.write(frame);
    },
  };
}

async function connectToBrokerOverWebSocket(browserWriter) {
  const websocket = new WebSocket(BROKER_URL);
  websocket.binaryType = 'arraybuffer';

  await new Promise((resolve, reject) => {
    const cleanup = () => {
      websocket.removeEventListener('open', onOpen);
      websocket.removeEventListener('error', onError);
    };

    const onOpen = () => {
      cleanup();
      resolve(undefined);
    };

    const onError = (event) => {
      cleanup();
      reject(
        toError(
          event.error ?? new Error('The broker WebSocket relay failed before it opened.')
        )
      );
    };

    websocket.addEventListener('open', onOpen);
    websocket.addEventListener('error', onError);
  });

  return {
    async close() {
      if (websocket.readyState === WebSocket.CLOSING || websocket.readyState === WebSocket.CLOSED) {
        return;
      }

      websocket.close();
      await Promise.resolve();
    },
    async finishOutgoing() {
      if (websocket.readyState === WebSocket.OPEN) {
        websocket.close();
      }
    },
    incomingDone: new Promise((resolve, reject) => {
      websocket.addEventListener('message', (event) => {
        const frame = toBuffer(event.data);
        const lengthPrefixedFrame = Buffer.allocUnsafe(frame.length + 3);
        writeUInt24BE(lengthPrefixedFrame, frame.length, 0);
        frame.copy(lengthPrefixedFrame, 3);
        browserWriter.write(asUint8Array(lengthPrefixedFrame)).catch(reject);
      });
      websocket.addEventListener('error', (event) => {
        reject(
          toError(
            event.error ?? new Error('The broker WebSocket relay failed unexpectedly.')
          )
        );
      });
      websocket.addEventListener('close', resolve);
    }),
    async sendFrame(frame) {
      websocket.send(frame);
    },
  };
}

async function waitForBrokerResponse(stream) {
  await new Promise((resolve, reject) => {
    const cleanup = () => {
      stream.off('response', onResponse);
      stream.off('error', onError);
      stream.off('close', onClose);
      stream.off('end', onClose);
    };

    const onResponse = (headers) => {
      const statusValue = headers[':status'];
      const status = Array.isArray(statusValue) ? statusValue[0] : statusValue;

      if (status?.charAt(0) !== '2') {
        cleanup();
        reject(
          new Error(
            `The broker rejected the HTTP/3 relay request with status ${status ?? 'unknown'}.`
          )
        );
        return;
      }

      cleanup();
      resolve(undefined);
    };

    const onError = (error) => {
      cleanup();
      reject(error);
    };

    const onClose = () => {
      cleanup();
      reject(new Error('The broker closed the HTTP/3 stream before sending headers.'));
    };

    stream.on('response', onResponse);
    stream.on('error', onError);
    stream.on('close', onClose);
    stream.on('end', onClose);
  });
}

async function loadOrCreateCertificate() {
  if (PROVIDED_CERT_PATH && PROVIDED_KEY_PATH) {
    const [cert, key] = await Promise.all([
      readFile(CERT_PATH, 'utf8'),
      readFile(KEY_PATH, 'utf8'),
    ]);

    return {
      cert,
      hash: hashCertificate(cert),
      key,
    };
  }

  try {
    if (await hasBrowserCompatibleCertificate()) {
      const [cert, key] = await Promise.all([
        readFile(CERT_PATH, 'utf8'),
        readFile(KEY_PATH, 'utf8'),
      ]);

      return {
        cert,
        hash: hashCertificate(cert),
        key,
      };
    }
  } catch {
    // Generate a new certificate below.
  }

  await createBrowserCompatibleCertificate();

  const [cert, key] = await Promise.all([
    readFile(CERT_PATH, 'utf8'),
    readFile(KEY_PATH, 'utf8'),
  ]);

  return {
    cert,
    hash: hashCertificate(cert),
    key,
  };
}

function hashCertificate(pemCertificate) {
  const base64 = pemCertificate
    .replaceAll('-----BEGIN CERTIFICATE-----', '')
    .replaceAll('-----END CERTIFICATE-----', '')
    .replaceAll(/\s+/g, '');

  const digest = createHash('sha256')
    .update(Buffer.from(base64, 'base64'))
    .digest('hex');

  return digest.match(/.{1,2}/g).join(':');
}

function logStartup(hash, version) {
  const browserHost = resolveBrowserHost(HOST);

  console.info(`${LOG_PREFIX} certificate hash`, {
    brokerCertHash: hash,
    brokerUrlHint: `wts://${browserHost}:${PORT}${SESSION_PATH}`,
    contractVersion: SIDECAR_CONTRACT_VERSION,
    exampleUrl: `https://localhost/?brokerUrl=wts://${browserHost}:${PORT}${SESSION_PATH}&brokerCertHash=${hash}`,
    version,
  });
}

async function loadSidecarVersion() {
  try {
    const packageJson = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
    return packageJson.version ?? '0.0.0-dev';
  } catch {
    return process.env.SIDECAR_VERSION ?? '0.0.0-dev';
  }
}

function buildAuthority(url) {
  const port = url.port || '443';
  return `${url.hostname}:${port}`;
}

function normalizeBrokerUrl(url) {
  if (!url) {
    return 'https://localhost:7171/rsocket';
  }

  if (url.startsWith('h3://')) {
    return `https://${url.slice('h3://'.length)}`;
  }

  return url;
}

function isWebSocketBrokerUrl(url) {
  return url.protocol === 'ws:' || url.protocol === 'wss:';
}

function normalizePath(path) {
  if (!path || path === '/') {
    return '/';
  }

  return path.startsWith('/') ? path : `/${path}`;
}

function resolveBrowserHost(host) {
  if (!host || host === '0.0.0.0' || host === '::') {
    return '127.0.0.1';
  }

  return host;
}

function asUint8Array(buffer) {
  return new Uint8Array(buffer.buffer, buffer.byteOffset, buffer.byteLength);
}

function readUInt24BE(buffer, offset) {
  return (buffer[offset] << 16) | (buffer[offset + 1] << 8) | buffer[offset + 2];
}

function writeUInt24BE(buffer, value, offset) {
  buffer[offset] = (value >>> 16) & 0xff;
  buffer[offset + 1] = (value >>> 8) & 0xff;
  buffer[offset + 2] = value & 0xff;
}

function toBuffer(data) {
  if (Buffer.isBuffer(data)) {
    return data;
  }

  if (data instanceof ArrayBuffer) {
    return Buffer.from(data);
  }

  if (ArrayBuffer.isView(data)) {
    return Buffer.from(data.buffer, data.byteOffset, data.byteLength);
  }

  throw new Error(`Unsupported broker relay payload type: ${typeof data}`);
}

function sanitizeReason(reason) {
  return (reason || 'closed').slice(0, 1024);
}

function toError(error) {
  return error instanceof Error ? error : new Error(String(error));
}

async function hasBrowserCompatibleCertificate() {
  try {
    await Promise.all([readFile(CERT_PATH, 'utf8'), readFile(KEY_PATH, 'utf8')]);
  } catch {
    return false;
  }

  try {
    const certificateText = execFileSync('openssl', ['x509', '-in', CERT_PATH, '-text', '-noout'], {
      encoding: 'utf8',
    });

    return (
      certificateText.includes('Public Key Algorithm: id-ecPublicKey') &&
      certificateText.includes('ASN1 OID: prime256v1') &&
      certificateText.includes('DNS:localhost') &&
      certificateText.includes('IP Address:127.0.0.1') &&
      certificateValidityDays(certificateText) <= 14
    );
  } catch (error) {
    console.warn(`${LOG_PREFIX} failed to validate cached certificate`, {
      error: error instanceof Error ? error.message : String(error),
    });
    return false;
  }
}

async function createBrowserCompatibleCertificate() {
  await mkdir(dirname(CERT_PATH), { recursive: true });

  execFileSync(
    'openssl',
    ['ecparam', '-name', 'prime256v1', '-genkey', '-noout', '-out', KEY_PATH],
    { stdio: 'ignore' }
  );
  execFileSync(
    'openssl',
    [
      'req',
      '-new',
      '-x509',
      '-key',
      KEY_PATH,
      '-out',
      CERT_PATH,
      '-days',
      '13',
      '-sha256',
      '-subj',
      '/CN=localhost/O=Nextend Broker WebTransport Native',
      '-addext',
      'basicConstraints=critical,CA:false',
      '-addext',
      'keyUsage=critical,digitalSignature',
      '-addext',
      'extendedKeyUsage=serverAuth',
      '-addext',
      'subjectAltName=DNS:localhost,IP:127.0.0.1',
    ],
    {
      stdio: 'ignore',
    }
  );
}

function certificateValidityDays(certificateText) {
  const notBeforeMatch = certificateText.match(/Not Before:\s+(.+)/);
  const notAfterMatch = certificateText.match(/Not After ?:\s+(.+)/);

  if (!notBeforeMatch || !notAfterMatch) {
    return Number.POSITIVE_INFINITY;
  }

  const notBefore = Date.parse(notBeforeMatch[1].trim());
  const notAfter = Date.parse(notAfterMatch[1].trim());

  if (!Number.isFinite(notBefore) || !Number.isFinite(notAfter)) {
    return Number.POSITIVE_INFINITY;
  }

  return (notAfter - notBefore) / (24 * 60 * 60 * 1000);
}

try {
  await main();
} catch (error) {
  console.error(`${LOG_PREFIX} fatal startup error`, error);
  process.exitCode = 1;
}
