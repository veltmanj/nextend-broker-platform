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
    await writer.close();
    writer.releaseLock();
    appendProbeLog('Closed outgoing stream without sending frames.');

    const reader = stream.readable.getReader();
    await reader.cancel('probe complete');
    reader.releaseLock();
    appendProbeLog('Cancelled incoming stream reader.');

    transport.close({ closeCode: 0, reason: 'probe complete' });
    await transport.closed.catch(() => undefined);
    appendProbeLog('Transport closed cleanly.');

    setProbeState(
      'Success. Real session and bidirectional stream opened.',
      latestInfo.browserUrlHint
    );
    setStatus('WebTransport probe succeeded.', 'success');
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