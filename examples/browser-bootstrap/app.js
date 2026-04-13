import { BrokerClientId, RsocketBrokerClient, Tags } from 'rsocket-broker-client-js';

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
let latestSocket = null;

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

  if (!latestInfo?.browserUrlHint || !latestInfo?.brokerCertHash || !latestTokenResponse?.token) {
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

  try {
    if (latestSocket) {
      latestSocket.close();
      latestSocket = null;
    }

    const brokerClient = new RsocketBrokerClient();
    const brokerClientId = new BrokerClientId();

    appendProbeLog('Connecting with rsocket-broker-client-js@0.0.32.', {
      brokerUrl: latestInfo.browserUrlHint,
    });

    latestSocket = await brokerClient.connect({
      token: latestTokenResponse.token,
      brokerUrl: latestInfo.browserUrlHint,
      brokerClientId,
      brokerClientName: 'browser-webtransport-client',
      connectionTags: new Tags(),
      webTransport: {
        serverCertificateHashes: [
          {
            algorithm: 'sha-256',
            value: parseCertificateHash(latestInfo.brokerCertHash).buffer,
          },
        ],
      },
    });
    appendProbeLog('Package connection established over WebTransport.');

    latestSocket.close();
    latestSocket = null;
    appendProbeLog('Closed RSocket client cleanly.');

    setProbeState(
      'Success. rsocket-broker-client-js connected over WebTransport.',
      latestInfo.browserUrlHint
    );
    setStatus('WebTransport probe succeeded with rsocket-broker-client-js.', 'success');
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

    if (latestSocket) {
      latestSocket.close();
      latestSocket = null;
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