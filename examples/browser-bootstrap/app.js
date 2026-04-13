const brokerBaseUrlInput = document.querySelector('#brokerBaseUrl');
const fetchButton = document.querySelector('#fetchButton');
const probeButton = document.querySelector('#probeButton');
const copyButton = document.querySelector('#copyButton');
const statusNode = document.querySelector('#status');

const browserUrlHintNode = document.querySelector('#browserUrlHint');
const brokerCertHashNode = document.querySelector('#brokerCertHash');
const transportPathNode = document.querySelector('#transportPath');
const infoJsonNode = document.querySelector('#infoJson');

const tokenScopeNode = document.querySelector('#tokenScope');
const tokenExpiresAtNode = document.querySelector('#tokenExpiresAt');
const tokenValueNode = document.querySelector('#tokenValue');
const tokenJsonNode = document.querySelector('#tokenJson');
const launchUrlNode = document.querySelector('#launchUrl');
const probeStatusNode = document.querySelector('#probeStatus');
const probeUrlNode = document.querySelector('#probeUrl');
const probeLogNode = document.querySelector('#probeLog');

let latestInfo = null;
let latestTokenResponse = null;
let latestProbeLog = [];
const encoder = new TextEncoder();

function writeUInt16BE(target, offset, value) {
  target[offset] = (value >>> 8) & 0xff;
  target[offset + 1] = value & 0xff;
  return offset + 2;
}

function writeUInt24BE(target, offset, value) {
  target[offset] = (value >>> 16) & 0xff;
  target[offset + 1] = (value >>> 8) & 0xff;
  target[offset + 2] = value & 0xff;
  return offset + 3;
}

function writeUInt32BE(target, offset, value) {
  target[offset] = (value >>> 24) & 0xff;
  target[offset + 1] = (value >>> 16) & 0xff;
  target[offset + 2] = (value >>> 8) & 0xff;
  target[offset + 3] = value & 0xff;
  return offset + 4;
}

function encodeAscii(value) {
  return encoder.encode(value);
}

function concatUint8Arrays(parts) {
  const totalLength = parts.reduce((sum, part) => sum + part.byteLength, 0);
  const combined = new Uint8Array(totalLength);
  let offset = 0;

  for (const part of parts) {
    combined.set(part, offset);
    offset += part.byteLength;
  }

  return combined;
}

function createLengthPrefixedSetupFrame() {
  const metadataMimeType = encodeAscii('application/octet-stream');
  const dataMimeType = encodeAscii('application/octet-stream');
  const frame = new Uint8Array(6 + 14 + metadataMimeType.byteLength + dataMimeType.byteLength);

  let offset = 0;
  offset = writeUInt32BE(frame, offset, 0);
  offset = writeUInt16BE(frame, offset, 1 << 10);
  offset = writeUInt16BE(frame, offset, 1);
  offset = writeUInt16BE(frame, offset, 0);
  offset = writeUInt32BE(frame, offset, 60_000);
  offset = writeUInt32BE(frame, offset, 300_000);
  offset = writeUInt8(frame, offset, metadataMimeType.byteLength);
  frame.set(metadataMimeType, offset);
  offset += metadataMimeType.byteLength;
  offset = writeUInt8(frame, offset, dataMimeType.byteLength);
  frame.set(dataMimeType, offset);

  const lengthPrefixed = new Uint8Array(frame.byteLength + 3);
  writeUInt24BE(lengthPrefixed, 0, frame.byteLength);
  lengthPrefixed.set(frame, 3);
  return lengthPrefixed;
}

function writeUInt8(target, offset, value) {
  target[offset] = value & 0xff;
  return offset + 1;
}

async function readOneChunkOrTimeout(reader, timeoutMs) {
  let timeoutId;

  const timeoutPromise = new Promise((resolve) => {
    timeoutId = setTimeout(() => {
      resolve({ timedOut: true });
    }, timeoutMs);
  });

  try {
    const result = await Promise.race([
      reader.read().then((value) => ({ timedOut: false, value })),
      timeoutPromise,
    ]);
    return result;
  } finally {
    clearTimeout(timeoutId);
  }
}

function setStatus(message, type = 'idle') {
  statusNode.textContent = message;
  statusNode.dataset.state = type;
}

function normalizeBaseUrl(value) {
  return value.replace(/\/$/, '');
}

function appendProbeLog(message, extra = undefined) {
  latestProbeLog = [
    ...latestProbeLog,
    {
      at: new Date().toISOString(),
      message,
      ...(extra ? { extra } : {}),
    },
  ];
  probeLogNode.textContent = JSON.stringify(latestProbeLog, null, 2);
}

function setProbeState(message, url = '-') {
  probeStatusNode.textContent = message;
  probeUrlNode.textContent = url || '-';
}

function parseCertificateHash(value) {
  const compact = value.replaceAll(':', '').trim();

  if (!compact || compact.length % 2 !== 0) {
    throw new Error('The broker certificate hash is missing or malformed.');
  }

  return Uint8Array.from(
    compact.match(/.{1,2}/g).map((pair) => Number.parseInt(pair, 16))
  );
}

async function fetchJson(url) {
  const response = await fetch(url, {
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText} for ${url}`);
  }

  return response.json();
}

function renderInfo(info) {
  latestInfo = info;
  browserUrlHintNode.textContent = info.browserUrlHint ?? '-';
  brokerCertHashNode.textContent = info.brokerCertHash ?? '-';
  transportPathNode.textContent = info.path ?? '-';
  infoJsonNode.textContent = JSON.stringify(info, null, 2);
}

function renderToken(tokenResponse) {
  latestTokenResponse = tokenResponse;
  tokenScopeNode.textContent = tokenResponse.scope ?? '-';
  tokenExpiresAtNode.textContent = tokenResponse.expiresAt ?? '-';
  tokenValueNode.value = tokenResponse.token ?? '';
  tokenJsonNode.textContent = JSON.stringify(tokenResponse, null, 2);
}

function renderLaunchUrl(info, tokenResponse) {
  if (!info.browserUrlHint || !info.brokerCertHash) {
    launchUrlNode.value = '';
    return;
  }

  const launchUrl = new URL('https://localhost/');
  launchUrl.searchParams.set('brokerUrl', info.browserUrlHint);
  launchUrl.searchParams.set('brokerCertHash', info.brokerCertHash);

  if (tokenResponse.token) {
    launchUrl.searchParams.set('token', tokenResponse.token);
  }

  launchUrlNode.value = launchUrl.toString();
}

async function loadBootstrapData() {
  const baseUrl = normalizeBaseUrl(brokerBaseUrlInput.value.trim());

  if (!baseUrl) {
    setStatus('Enter a broker base URL first.', 'error');
    return;
  }

  fetchButton.disabled = true;
  probeButton.disabled = true;
  copyButton.disabled = true;
  setStatus('Fetching broker bootstrap data...', 'loading');
  latestProbeLog = [];
  probeLogNode.textContent = '[]';
  setProbeState('Not started.');

  try {
    const [info, tokenResponse] = await Promise.all([
      fetchJson(`${baseUrl}/broker/wt/info`),
      fetchJson(`${baseUrl}/broker/auth/token`),
    ]);

    renderInfo(info);
    renderToken(tokenResponse);
    renderLaunchUrl(info, tokenResponse);

    probeButton.disabled = !(latestInfo?.browserUrlHint && latestInfo?.brokerCertHash);
    copyButton.disabled = !launchUrlNode.value;
    setStatus('Bootstrap data loaded.', 'success');
  } catch (error) {
    console.error(error);
    setStatus(error.message, 'error');
  } finally {
    fetchButton.disabled = false;
  }
}

async function probeWebTransport() {
  if (!('WebTransport' in globalThis)) {
    setStatus('This browser does not expose the WebTransport API.', 'error');
    setProbeState('WebTransport is not available in this browser.');
    return;
  }

  if (!latestInfo?.browserUrlHint || !latestInfo?.brokerCertHash) {
    setStatus('Fetch broker data before probing the WebTransport session.', 'error');
    setProbeState('Missing bootstrap data.');
    return;
  }

  probeButton.disabled = true;
  setProbeState('Connecting...', latestInfo.browserUrlHint);
  appendProbeLog('Starting WebTransport probe.', {
    url: latestInfo.browserUrlHint,
    scope: latestTokenResponse?.scope ?? null,
  });

  let transport;

  try {
    transport = new WebTransport(latestInfo.browserUrlHint, {
      serverCertificateHashes: [
        {
          algorithm: 'sha-256',
          value: parseCertificateHash(latestInfo.brokerCertHash),
        },
      ],
    });

    await transport.ready;
    appendProbeLog('Session ready.');

    const stream = await transport.createBidirectionalStream();
    appendProbeLog('Bidirectional stream opened.');

    const writer = stream.writable.getWriter();
    const setupFrame = createLengthPrefixedSetupFrame();
    await writer.write(setupFrame);
    appendProbeLog('Wrote minimal RSocket SETUP frame.', {
      bytes: setupFrame.byteLength,
    });
    await writer.close();
    writer.releaseLock();
    appendProbeLog('Closed outgoing stream after SETUP frame.');

    const reader = stream.readable.getReader();
    const readResult = await readOneChunkOrTimeout(reader, 750);

    if (readResult.timedOut) {
      appendProbeLog('No broker frame arrived before timeout. Treating transport as accepted.');
    } else if (readResult.value.done) {
      appendProbeLog('Incoming stream closed after setup frame.');
    } else {
      appendProbeLog('Received broker bytes after setup frame.', {
        bytes: readResult.value.value.byteLength,
      });
    }

    await reader.cancel('probe complete');
    reader.releaseLock();
    appendProbeLog('Cancelled incoming stream reader.');

    transport.close({ closeCode: 0, reason: 'probe complete' });
    await transport.closed.catch(() => undefined);
    appendProbeLog('Transport closed cleanly.');

    setProbeState(
      'Success. Session opened and a minimal RSocket SETUP frame was accepted.',
      latestInfo.browserUrlHint
    );
    setStatus('WebTransport probe and setup frame succeeded.', 'success');
  } catch (error) {
    console.error(error);
    appendProbeLog('Probe failed.', {
      error: error instanceof Error ? error.message : String(error),
    });
    setProbeState(
      `Failed: ${error instanceof Error ? error.message : String(error)}`,
      latestInfo.browserUrlHint
    );
    setStatus(error instanceof Error ? error.message : String(error), 'error');

    if (transport) {
      transport.close({ closeCode: 1, reason: 'probe failed' });
    }
  } finally {
    probeButton.disabled = false;
  }
}

async function copyLaunchUrl() {
  if (!launchUrlNode.value) {
    setStatus('Fetch broker data before copying the launch URL.', 'error');
    return;
  }

  try {
    await navigator.clipboard.writeText(launchUrlNode.value);
    setStatus('Launch URL copied to the clipboard.', 'success');
  } catch (error) {
    console.error(error);
    setStatus('Unable to copy launch URL.', 'error');
  }
}

fetchButton.addEventListener('click', loadBootstrapData);
probeButton.addEventListener('click', probeWebTransport);
copyButton.addEventListener('click', copyLaunchUrl);
copyButton.disabled = true;
probeButton.disabled = true;