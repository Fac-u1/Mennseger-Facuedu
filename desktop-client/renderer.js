let ephemeralSession = null;
let messages = [];

function toBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary);
}

async function createEphemeralSession() {
  const guestSuffix = crypto.randomUUID().replace(/-/g, '').slice(0, 8);
  const uid = `guest_${guestSuffix}`;
  const keyPair = await crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' },
    true,
    ['deriveKey']
  );
  const spki = await crypto.subtle.exportKey('spki', keyPair.publicKey);
  const digest = await crypto.subtle.digest('SHA-256', spki);
  const fp = Array.from(new Uint8Array(digest).slice(0, 8))
    .map((b) => b.toString(16).padStart(2, '0').toUpperCase())
    .join(':');

  const aesKey = await crypto.subtle.deriveKey(
    { name: 'ECDH', public: keyPair.publicKey },
    keyPair.privateKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );

  ephemeralSession = {
    uid,
    displayName: `Invitado #${guestSuffix}`,
    publicKeyBase64: toBase64(spki),
    fingerprint: fp,
    aesKey
  };
  messages = [];
  document.getElementById('keyFingerprint').textContent = `${ephemeralSession.displayName} · ${fp}`;
  renderMessages();
}

async function encryptAndAddMessage(plaintext) {
  if (!ephemeralSession || !plaintext.trim()) return;
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encoded = new TextEncoder().encode(plaintext.trim());
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    ephemeralSession.aesKey,
    encoded
  );
  messages.push({
    plaintext: plaintext.trim(),
    ciphertextPreview: toBase64(ciphertext).slice(0, 28) + '...',
    ivPreview: toBase64(iv),
    time: new Date().toLocaleTimeString()
  });
  renderMessages();
}

function renderMessages() {
  const container = document.getElementById('messagesList');
  container.innerHTML = '';
  if (messages.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'muted';
    empty.textContent = 'Sesión efímera activa. Enviá un mensaje o adjuntá un archivo para verificar el cifrado AES-256-GCM en RAM.';
    container.appendChild(empty);
    return;
  }
  messages.forEach((m) => {
    const card = document.createElement('div');
    card.className = 'msg-card';
    const text = document.createElement('div');
    text.textContent = m.plaintext;
    const cipher = document.createElement('div');
    cipher.className = 'cipher';
    cipher.textContent = `${m.time} · AES-256-GCM · IV ${m.ivPreview} · ${m.ciphertextPreview}`;
    card.appendChild(text);
    card.appendChild(cipher);
    container.appendChild(card);
  });
  container.scrollTop = container.scrollHeight;
}

document.getElementById('sendBtn').addEventListener('click', () => {
  const input = document.getElementById('msgInput');
  encryptAndAddMessage(input.value);
  input.value = '';
});

document.getElementById('msgInput').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    const input = document.getElementById('msgInput');
    encryptAndAddMessage(input.value);
    input.value = '';
  }
});

document.getElementById('copyLinkBtn').addEventListener('click', async () => {
  if (!ephemeralSession) return;
  const link = `facuedumessenger://connect?uid=${encodeURIComponent(ephemeralSession.uid)}&name=${encodeURIComponent(ephemeralSession.displayName)}&fp=${encodeURIComponent(ephemeralSession.fingerprint)}`;
  await navigator.clipboard.writeText(link);
  document.getElementById('guestStatus').textContent = 'Vínculo E2EE copiado al portapapeles';
});

document.getElementById('wipeGuestBtn').addEventListener('click', () => {
  ephemeralSession = null;
  messages = [];
  createEphemeralSession();
  document.getElementById('guestStatus').textContent = 'Cuenta anterior borrada · Nueva sesión efímera';
});

document.getElementById('docInput').addEventListener('change', async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  const buf = await file.arrayBuffer();
  const hash = await crypto.subtle.digest('SHA-256', buf);
  const hex = Array.from(new Uint8Array(hash))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
    .slice(0, 24);
  await encryptAndAddMessage(`Archivo adjunto: ${file.name} (${file.size} bytes) · SHA-256: ${hex}...`);
  e.target.value = '';
});

if (window.desktopPlatform) {
  document.getElementById('platformMeta').textContent = `Sistema: ${window.desktopPlatform.platform} (${window.desktopPlatform.arch})`;
}

createEphemeralSession();
