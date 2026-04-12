#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { mkdir, readFile, stat } from 'node:fs/promises';
import { createServer as createHttpServer } from 'node:http';
import { createServer as createHttpsServer } from 'node:https';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT_DIR = fileURLToPath(new URL('.', import.meta.url));
const HTTP_PORT = Number(process.env.PORT ?? '8080');
const HTTPS_PORT = Number(process.env.HTTPS_PORT ?? '8443');
const ENABLE_HTTPS = (process.env.ENABLE_HTTPS ?? '1') !== '0';
const BROKER_BASE_URL = (process.env.BROKER_BASE_URL ?? 'http://localhost:6933').replace(/\/$/, '');
const HTTPS_CERT_PATH = process.env.HTTPS_CERT_PATH ?? join(ROOT_DIR, '.cache', 'localhost-cert.pem');
const HTTPS_KEY_PATH = process.env.HTTPS_KEY_PATH ?? join(ROOT_DIR, '.cache', 'localhost-key.pem');

const CONTENT_TYPES = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.md': 'text/markdown; charset=utf-8',
};

function sendJson(response, statusCode, body) {
  response.writeHead(statusCode, {
    'content-type': 'application/json; charset=utf-8',
  });
  response.end(JSON.stringify(body, null, 2));
}

async function proxyBrokerRequest(request, response) {
  const targetUrl = new URL(request.url, `${BROKER_BASE_URL}/`);
  const upstream = await fetch(targetUrl, {
    headers: {
      accept: request.headers.accept ?? '*/*',
      origin: request.headers.origin ?? '',
    },
    method: request.method,
  });

  const headers = {};
  upstream.headers.forEach((value, key) => {
    headers[key] = value;
  });

  response.writeHead(upstream.status, headers);
  const body = Buffer.from(await upstream.arrayBuffer());
  response.end(body);
}

function resolveFilePath(requestUrl) {
  const url = new URL(requestUrl, 'http://localhost');
  const pathname = url.pathname === '/' ? '/index.html' : url.pathname;
  const safePath = normalize(pathname).replace(/^([.][.][/\\])+/, '');
  return join(ROOT_DIR, safePath);
}

async function serveStaticFile(request, response) {
  try {
    const filePath = resolveFilePath(request.url);
    const fileStat = await stat(filePath);

    if (!fileStat.isFile()) {
      sendJson(response, 404, { error: 'not_found' });
      return;
    }

    const body = await readFile(filePath);
    response.writeHead(200, {
      'cache-control': 'no-store',
      'content-type': CONTENT_TYPES[extname(filePath)] ?? 'application/octet-stream',
    });
    response.end(body);
  } catch {
    sendJson(response, 404, { error: 'not_found' });
  }
}

async function requestHandler(request, response) {
  try {
    if ((request.url ?? '').startsWith('/broker/')) {
      await proxyBrokerRequest(request, response);
      return;
    }

    if (request.url === '/__dev-server/config') {
      sendJson(response, 200, {
        brokerBaseUrl: BROKER_BASE_URL,
        httpPort: HTTP_PORT,
        httpsPort: ENABLE_HTTPS ? HTTPS_PORT : null,
      });
      return;
    }

    await serveStaticFile(request, response);
  } catch (error) {
    sendJson(response, 500, {
      error: 'server_error',
      message: error instanceof Error ? error.message : String(error),
    });
  }
}

async function ensureHttpsCertificate() {
  await mkdir(join(ROOT_DIR, '.cache'), { recursive: true });

  try {
    const [cert, key] = await Promise.all([
      readFile(HTTPS_CERT_PATH, 'utf8'),
      readFile(HTTPS_KEY_PATH, 'utf8'),
    ]);
    return { cert, key };
  } catch {
    execFileSync(
      'openssl',
      [
        'req',
        '-x509',
        '-newkey',
        'rsa:2048',
        '-keyout',
        HTTPS_KEY_PATH,
        '-out',
        HTTPS_CERT_PATH,
        '-days',
        '7',
        '-nodes',
        '-subj',
        '/CN=localhost/O=Nextend Broker Platform Example',
      ],
      { stdio: 'ignore' }
    );

    const [cert, key] = await Promise.all([
      readFile(HTTPS_CERT_PATH, 'utf8'),
      readFile(HTTPS_KEY_PATH, 'utf8'),
    ]);
    return { cert, key };
  }
}

async function main() {
  const httpServer = createHttpServer(requestHandler);
  httpServer.listen(HTTP_PORT, () => {
    console.info(`[browser-bootstrap] HTTP server listening on http://localhost:${HTTP_PORT}`);
    console.info(`[browser-bootstrap] Proxying /broker/* to ${BROKER_BASE_URL}`);
  });

  if (!ENABLE_HTTPS) {
    return;
  }

  try {
    const tls = await ensureHttpsCertificate();
    const httpsServer = createHttpsServer(tls, requestHandler);
    httpsServer.listen(HTTPS_PORT, () => {
      console.info(`[browser-bootstrap] HTTPS server listening on https://localhost:${HTTPS_PORT}`);
    });
  } catch (error) {
    console.warn(
      '[browser-bootstrap] HTTPS server disabled:',
      error instanceof Error ? error.message : String(error)
    );
  }
}

try {
  await main();
} catch (error) {
  console.error(error);
  process.exitCode = 1;
}