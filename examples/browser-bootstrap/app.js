const brokerBaseUrlInput = document.querySelector('#brokerBaseUrl');
const fetchButton = document.querySelector('#fetchButton');
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

function setStatus(message, type = 'idle') {
  statusNode.textContent = message;
  statusNode.dataset.state = type;
}

function normalizeBaseUrl(value) {
  return value.replace(/\/$/, '');
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
  browserUrlHintNode.textContent = info.browserUrlHint ?? '-';
  brokerCertHashNode.textContent = info.brokerCertHash ?? '-';
  transportPathNode.textContent = info.path ?? '-';
  infoJsonNode.textContent = JSON.stringify(info, null, 2);
}

function renderToken(tokenResponse) {
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
  copyButton.disabled = true;
  setStatus('Fetching broker bootstrap data...', 'loading');

  try {
    const [info, tokenResponse] = await Promise.all([
      fetchJson(`${baseUrl}/broker/wt/info`),
      fetchJson(`${baseUrl}/broker/auth/token`),
    ]);

    renderInfo(info);
    renderToken(tokenResponse);
    renderLaunchUrl(info, tokenResponse);

    copyButton.disabled = !launchUrlNode.value;
    setStatus('Bootstrap data loaded.', 'success');
  } catch (error) {
    console.error(error);
    setStatus(error.message, 'error');
  } finally {
    fetchButton.disabled = false;
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
copyButton.addEventListener('click', copyLaunchUrl);
copyButton.disabled = true;