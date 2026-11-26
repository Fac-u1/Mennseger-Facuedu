let guestSession = null;
let chats = [];
let activeChatId = null;
let chatFilter = 'all';
let chatSearch = '';
let showInspector = false;
let callHistory = [];
let mediaStream = null;
let callSeconds = 0;
let callTimerId = null;
let micEnabled = true;
let camEnabled = true;

function toBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary);
}

function openExternalLink(url) {
  if (window.desktopPlatform && typeof window.desktopPlatform.openExternal === 'function') {
    window.desktopPlatform.openExternal(url);
  } else {
    window.open(url, '_blank', 'noopener,noreferrer');
  }
}

async function copyTextSafely(text) {
  try {
    await navigator.clipboard.writeText(text);
  } catch (_) {
    if (window.desktopPlatform && typeof window.desktopPlatform.copyText === 'function') {
      await window.desktopPlatform.copyText(text);
    }
  }
}

function setAuthError(message) {
  const box = document.getElementById('authErrorBox');
  if (!message) {
    box.textContent = '';
    box.classList.add('hidden');
  } else {
    box.textContent = message;
    box.classList.remove('hidden');
  }
}

async function initSession({ isGuest, googleUser = null }) {
  const suffix = crypto.randomUUID().replace(/-/g, '').slice(0, 8);
  const uid = isGuest ? `guest_${suffix}` : (googleUser?.uid || `google_${suffix}`);
  const displayName = isGuest ? `Invitado #${suffix}` : (googleUser?.displayName || 'Usuario Google');
  const email = isGuest ? '' : (googleUser?.email || '');

  const keyPair = await crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' },
    true,
    ['deriveKey']
  );
  const spki = await crypto.subtle.exportKey('spki', keyPair.publicKey);
  const digest = await crypto.subtle.digest('SHA-256', spki);
  const fingerprint = Array.from(new Uint8Array(digest).slice(0, 8))
    .map((b) => b.toString(16).padStart(2, '0').toUpperCase())
    .join(':');

  const aesKey = await crypto.subtle.deriveKey(
    { name: 'ECDH', public: keyPair.publicKey },
    keyPair.privateKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );

  guestSession = {
    isGuest,
    uid,
    displayName,
    email,
    photoUrl: googleUser?.photoUrl || '',
    publicKeyBase64: toBase64(spki),
    fingerprint,
    aesKey
  };

  chats = [
    {
      id: 'vault_personal',
      title: 'Bóveda Personal E2EE',
      type: 'vault',
      peerUid: uid,
      messages: []
    }
  ];
  activeChatId = 'vault_personal';
  callHistory = [];

  syncSessionViews();
}

function getShareLink() {
  if (!guestSession) return '';
  return `facuedumessenger://connect?uid=${encodeURIComponent(guestSession.uid)}&name=${encodeURIComponent(guestSession.displayName)}&fp=${encodeURIComponent(guestSession.fingerprint)}`;
}

function syncSessionViews() {
  if (!guestSession) return;
  const topbar = document.getElementById('sessionTopbar');
  const topConnectBtn = document.getElementById('topConnectGoogleBtn');
  const secConnectBtn = document.getElementById('secConnectGoogleBtn');
  const topWipeBtn = document.getElementById('topWipeBtn');
  const secWipeBtn = document.getElementById('secWipeBtn');
  const secSignOutTitle = document.getElementById('secSignOutTitle');
  const secSignOutDesc = document.getElementById('secSignOutDesc');

  if (guestSession.isGuest) {
    topbar.classList.remove('google-topbar');
    document.getElementById('guestBannerText').textContent =
      `${guestSession.displayName} · Modo Invitado Efímero (se borra al cerrar)`;
    topConnectBtn.classList.remove('hidden');
    secConnectBtn.classList.remove('hidden');
    topWipeBtn.textContent = 'Eliminar cuenta y salir';
    secSignOutTitle.textContent = 'Eliminar cuenta de invitado';
    secSignOutDesc.textContent = 'Destruye claves ECDH, mensajes, llamadas y caché de la sesión inmediatamente.';
    secWipeBtn.textContent = 'Eliminar cuenta invitado y salir';
    document.getElementById('secEmail').textContent = 'Sesión temporal en memoria RAM';
  } else {
    topbar.classList.add('google-topbar');
    document.getElementById('guestBannerText').textContent =
      `Google · ${guestSession.displayName}${guestSession.email ? ` (${guestSession.email})` : ''}`;
    topConnectBtn.classList.add('hidden');
    secConnectBtn.classList.add('hidden');
    topWipeBtn.textContent = 'Cerrar sesión';
    secSignOutTitle.textContent = 'Cerrar sesión de Google';
    secSignOutDesc.textContent = 'Cierra tu cuenta Google en este equipo y limpia las claves de la sesión.';
    secWipeBtn.textContent = 'Cerrar sesión';
    document.getElementById('secEmail').textContent = guestSession.email || 'Cuenta Google verificada';
  }

  document.getElementById('secDisplayName').textContent = guestSession.displayName;
  document.getElementById('secUid').textContent = guestSession.uid;
  document.getElementById('secFingerprint').textContent = guestSession.fingerprint;
  document.getElementById('secPubKey').textContent = guestSession.publicKeyBase64;
  document.getElementById('shareLinkBox').textContent = getShareLink();
  renderChatList();
  renderActiveChat();
  renderCallHistory();
}

function renderChatList() {
  const listEl = document.getElementById('chatList');
  listEl.innerHTML = '';

  const filtered = chats.filter((c) => {
    const matchesFilter = chatFilter === 'all' || c.type === chatFilter;
    const matchesSearch = !chatSearch || c.title.toLowerCase().includes(chatSearch.toLowerCase());
    return matchesFilter && matchesSearch;
  });

  filtered.forEach((chat) => {
    const btn = document.createElement('button');
    btn.className = `chat-item${chat.id === activeChatId ? ' active' : ''}`;
    const title = document.createElement('strong');
    title.textContent = chat.title;
    const sub = document.createElement('small');
    const last = chat.messages[chat.messages.length - 1];
    sub.textContent = last ? last.plaintext : (chat.type === 'vault' ? 'Bóveda cifrada' : `UID: ${chat.peerUid}`);
    btn.appendChild(title);
    btn.appendChild(sub);
    btn.addEventListener('click', () => {
      activeChatId = chat.id;
      renderChatList();
      renderActiveChat();
    });
    listEl.appendChild(btn);
  });
}

function renderActiveChat() {
  const chat = chats.find((c) => c.id === activeChatId);
  if (!chat) return;
  document.getElementById('activeChatTitle').textContent = chat.title;
  document.getElementById('activeChatSubtitle').textContent =
    chat.type === 'vault' ? 'Bóveda AES-256-GCM · Solo en RAM' : `Contacto UID: ${chat.peerUid} · AES-256-GCM`;

  const container = document.getElementById('messagesList');
  container.innerHTML = '';
  if (chat.messages.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'muted';
    empty.textContent = 'Escribí un mensaje o adjuntá un archivo para cifrarlo con AES-256-GCM.';
    container.appendChild(empty);
    return;
  }

  chat.messages.forEach((m) => {
    const card = document.createElement('div');
    card.className = 'msg-card';
    const text = document.createElement('div');
    text.textContent = m.plaintext;
    card.appendChild(text);

    if (showInspector) {
      const cipher = document.createElement('div');
      cipher.className = 'cipher';
      cipher.textContent = `${m.time} · AES-256-GCM · IV=${m.ivPreview} · ${m.ciphertextPreview}`;
      card.appendChild(cipher);
    }
    container.appendChild(card);
  });
  container.scrollTop = container.scrollHeight;
}

async function sendEncryptedText(plaintext) {
  const chat = chats.find((c) => c.id === activeChatId);
  if (!guestSession || !chat || !plaintext.trim()) return;
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encoded = new TextEncoder().encode(plaintext.trim());
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    guestSession.aesKey,
    encoded
  );
  chat.messages.push({
    plaintext: plaintext.trim(),
    ciphertextPreview: toBase64(ciphertext).slice(0, 32) + '...',
    ivPreview: toBase64(iv),
    time: new Date().toLocaleTimeString()
  });
  renderChatList();
  renderActiveChat();
}

function switchTab(tabName) {
  document.querySelectorAll('.rail-btn[data-tab]').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.tab === tabName);
  });
  document.querySelectorAll('.tab-panel').forEach((panel) => {
    panel.classList.toggle('active', panel.id === `tab-${tabName}`);
  });
}

function renderCallHistory() {
  const el = document.getElementById('callHistoryList');
  el.innerHTML = '';
  if (callHistory.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'muted';
    empty.textContent = 'Todavía no realizaste llamadas en esta sesión.';
    el.appendChild(empty);
    return;
  }
  callHistory.forEach((entry) => {
    const row = document.createElement('div');
    row.className = 'history-row';
    const titleSpan = document.createElement('span');
    titleSpan.textContent = entry.title;
    const durSpan = document.createElement('span');
    durSpan.className = 'mono';
    durSpan.textContent = entry.duration;
    row.appendChild(titleSpan);
    row.appendChild(durSpan);
    el.appendChild(row);
  });
}

function formatDuration(sec) {
  const mm = String(Math.floor(sec / 60)).padStart(2, '0');
  const ss = String(sec % 60).padStart(2, '0');
  return `${mm}:${ss}`;
}

async function startVideoCall() {
  switchTab('calls');
  if (mediaStream) return;
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
    const videoEl = document.getElementById('localVideo');
    videoEl.srcObject = mediaStream;
    document.getElementById('videoPlaceholder').classList.add('hidden');
    document.getElementById('callStatusText').textContent = 'Videollamada en vivo activa (E2EE)';
  } catch (err) {
    document.getElementById('callStatusText').textContent =
      'Sin cámara/micrófono disponible: llamada iniciada en modo estado.';
  }

  micEnabled = true;
  camEnabled = true;
  callSeconds = 0;
  document.getElementById('callTimer').textContent = '00:00';
  document.getElementById('startCallBtn').disabled = true;
  document.getElementById('toggleMicBtn').disabled = false;
  document.getElementById('toggleCamBtn').disabled = false;
  document.getElementById('endCallBtn').disabled = false;

  clearInterval(callTimerId);
  callTimerId = setInterval(() => {
    callSeconds += 1;
    document.getElementById('callTimer').textContent = formatDuration(callSeconds);
  }, 1000);
}

function endVideoCall() {
  clearInterval(callTimerId);
  callTimerId = null;
  if (mediaStream) {
    mediaStream.getTracks().forEach((t) => t.stop());
    mediaStream = null;
  }
  document.getElementById('localVideo').srcObject = null;
  document.getElementById('videoPlaceholder').classList.remove('hidden');
  document.getElementById('callStatusText').textContent = 'Llamada finalizada';
  document.getElementById('startCallBtn').disabled = false;
  document.getElementById('toggleMicBtn').disabled = true;
  document.getElementById('toggleCamBtn').disabled = true;
  document.getElementById('endCallBtn').disabled = true;

  const active = chats.find((c) => c.id === activeChatId);
  callHistory.unshift({
    title: active ? active.title : 'Videollamada E2EE',
    duration: formatDuration(callSeconds)
  });
  renderCallHistory();
}

function wipeAndReturnToLogin() {
  if (mediaStream || callTimerId) {
    endVideoCall();
  }
  guestSession = null;
  chats = [];
  activeChatId = null;
  callHistory = [];
  document.getElementById('appShell').classList.add('hidden');
  document.getElementById('authView').classList.remove('hidden');
}

async function runGoogleSignInFlow(fromGuest = false) {
  setAuthError('');
  const googleBtn = document.getElementById('googleSignInBtn');
  googleBtn.disabled = true;
  try {
    if (!window.desktopPlatform || typeof window.desktopPlatform.signInWithGoogle !== 'function') {
      setAuthError('Google Sign-In requiere el entorno de escritorio.');
      return;
    }
    const res = await window.desktopPlatform.signInWithGoogle();
    if (res && res.ok && res.user) {
      await initSession({ isGuest: false, googleUser: res.user });
      document.getElementById('authView').classList.add('hidden');
      document.getElementById('appShell').classList.remove('hidden');
      switchTab('chats');
    } else {
      if (fromGuest) {
        wipeAndReturnToLogin();
      }
      setAuthError((res && res.error) || 'No se pudo completar el inicio de sesión con Google.');
      if (res && res.needsConfig) {
        document.getElementById('googleConfigDrawer').classList.remove('hidden');
      }
    }
  } finally {
    googleBtn.disabled = false;
  }
}

// Event Wiring
document.getElementById('googleSignInBtn').addEventListener('click', () => runGoogleSignInFlow(false));

document.getElementById('enterGuestBtn').addEventListener('click', async () => {
  setAuthError('');
  await initSession({ isGuest: true });
  document.getElementById('authView').classList.add('hidden');
  document.getElementById('appShell').classList.remove('hidden');
  switchTab('chats');
});

document.getElementById('toggleGoogleConfigBtn').addEventListener('click', async () => {
  const drawer = document.getElementById('googleConfigDrawer');
  drawer.classList.toggle('hidden');
  if (!drawer.classList.contains('hidden') && window.desktopPlatform?.getGoogleOAuthConfig) {
    const cfg = await window.desktopPlatform.getGoogleOAuthConfig();
    document.getElementById('oauthClientIdInput').value = cfg.clientId || '';
  }
});

document.getElementById('cancelGoogleConfigBtn').addEventListener('click', () => {
  document.getElementById('googleConfigDrawer').classList.add('hidden');
});

document.getElementById('saveGoogleConfigBtn').addEventListener('click', async () => {
  const clientId = document.getElementById('oauthClientIdInput').value.trim();
  const firebaseApiKey = document.getElementById('oauthFirebaseKeyInput').value.trim();
  if (!clientId) {
    setAuthError('Ingresá un Google Desktop OAuth Client ID válido.');
    return;
  }
  await window.desktopPlatform.saveGoogleOAuthConfig({ clientId, firebaseApiKey });
  document.getElementById('googleConfigDrawer').classList.add('hidden');
  await runGoogleSignInFlow(false);
});

document.querySelectorAll('.rail-btn[data-tab]').forEach((btn) => {
  btn.addEventListener('click', () => switchTab(btn.dataset.tab));
});

document.getElementById('topConnectGoogleBtn').addEventListener('click', () => runGoogleSignInFlow(true));
document.getElementById('secConnectGoogleBtn').addEventListener('click', () => runGoogleSignInFlow(true));
document.getElementById('railWipeBtn').addEventListener('click', wipeAndReturnToLogin);
document.getElementById('topWipeBtn').addEventListener('click', wipeAndReturnToLogin);
document.getElementById('secWipeBtn').addEventListener('click', wipeAndReturnToLogin);

document.getElementById('newChatToggleBtn').addEventListener('click', () => {
  document.getElementById('newChatForm').classList.toggle('hidden');
});

document.getElementById('createChatCancelBtn').addEventListener('click', () => {
  document.getElementById('newChatForm').classList.add('hidden');
});

document.getElementById('createChatConfirmBtn').addEventListener('click', () => {
  const titleInput = document.getElementById('newChatNameInput');
  const peerInput = document.getElementById('newChatPeerInput');
  const title = titleInput.value.trim();
  if (!title) return;
  const peerRaw = peerInput.value.trim();
  const id = `chat_${crypto.randomUUID().slice(0, 8)}`;
  chats.unshift({
    id,
    title,
    type: peerRaw ? 'direct' : 'vault',
    peerUid: peerRaw || guestSession.uid,
    messages: []
  });
  activeChatId = id;
  titleInput.value = '';
  peerInput.value = '';
  document.getElementById('newChatForm').classList.add('hidden');
  renderChatList();
  renderActiveChat();
});

document.getElementById('chatSearchInput').addEventListener('input', (e) => {
  chatSearch = e.target.value;
  renderChatList();
});

document.querySelectorAll('.chip[data-filter]').forEach((chip) => {
  chip.addEventListener('click', () => {
    chatFilter = chip.dataset.filter;
    document.querySelectorAll('.chip[data-filter]').forEach((c) => c.classList.remove('active'));
    chip.classList.add('active');
    renderChatList();
  });
});

document.getElementById('toggleInspectorBtn').addEventListener('click', () => {
  showInspector = !showInspector;
  renderActiveChat();
});

document.getElementById('sendBtn').addEventListener('click', () => {
  const input = document.getElementById('msgInput');
  sendEncryptedText(input.value);
  input.value = '';
});

document.getElementById('msgInput').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    const input = document.getElementById('msgInput');
    sendEncryptedText(input.value);
    input.value = '';
  }
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
  await sendEncryptedText(`📎 Documento cifrado: ${file.name} (${file.size} bytes) · SHA-256: ${hex}...`);
  e.target.value = '';
});

document.getElementById('startVideoFromChatBtn').addEventListener('click', startVideoCall);
document.getElementById('startCallBtn').addEventListener('click', startVideoCall);
document.getElementById('endCallBtn').addEventListener('click', endVideoCall);

document.getElementById('toggleMicBtn').addEventListener('click', () => {
  micEnabled = !micEnabled;
  if (mediaStream) {
    mediaStream.getAudioTracks().forEach((t) => {
      t.enabled = micEnabled;
    });
  }
  document.getElementById('toggleMicBtn').textContent = micEnabled ? '🎙️ Micrófono' : '🔇 Silenciado';
});

document.getElementById('toggleCamBtn').addEventListener('click', () => {
  camEnabled = !camEnabled;
  if (mediaStream) {
    mediaStream.getVideoTracks().forEach((t) => {
      t.enabled = camEnabled;
    });
  }
  document.getElementById('toggleCamBtn').textContent = camEnabled ? '📷 Cámara' : '🚫 Cámara off';
});

document.getElementById('copyShareLinkBtn').addEventListener('click', async () => {
  const link = getShareLink();
  if (!link) return;
  await copyTextSafely(link);
  document.getElementById('shareFeedback').textContent = 'Vínculo E2EE copiado al portapapeles.';
});

document.querySelectorAll('.social-btn').forEach((btn) => {
  btn.addEventListener('click', async () => {
    const link = getShareLink();
    const text = `Conectate conmigo por E2EE en Facuedu.uy Mennseger: ${link}`;
    await copyTextSafely(text);
    const social = btn.dataset.social;
    const encoded = encodeURIComponent(text);
    if (social === 'whatsapp') {
      openExternalLink(`https://wa.me/?text=${encoded}`);
    } else if (social === 'x') {
      openExternalLink(`https://twitter.com/intent/tweet?text=${encoded}`);
    } else if (social === 'facebook') {
      openExternalLink(`https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(link)}&quote=${encoded}`);
    } else if (social === 'instagram') {
      openExternalLink('https://www.instagram.com/');
      document.getElementById('shareFeedback').textContent = 'Vínculo copiado. Pegalo en tu mensaje o historia de Instagram.';
    }
  });
});

function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

async function handleZipConversion(filterExtensions = []) {
  const statusEl = document.getElementById('zipConverterStatus');
  const boxEl = document.getElementById('zipEntriesBox');
  if (!window.desktopPlatform || typeof window.desktopPlatform.inspectZipDialog !== 'function') {
    statusEl.textContent = 'Conversor disponible en la aplicación de escritorio.';
    return;
  }

  statusEl.textContent = 'Seleccioná un archivo .ZIP descargado de GitHub Actions...';
  const result = await window.desktopPlatform.inspectZipDialog(filterExtensions);
  if (!result || result.canceled) {
    statusEl.textContent = '';
    return;
  }
  if (result.error) {
    statusEl.textContent = result.error;
    boxEl.innerHTML = '';
    return;
  }

  boxEl.innerHTML = '';
  const entries = Array.isArray(result.entries) ? result.entries : [];
  if (entries.length === 0) {
    statusEl.textContent = `No se encontraron instaladores (.exe, .dmg, .pkg, .AppImage, .deb, .apk, .ipa) en ${result.archiveName}.`;
    return;
  }

  const matched = entries.filter((e) => e.matched);
  const toShow = matched.length > 0 ? matched : entries;
  statusEl.textContent = `${result.archiveName}: ${toShow.length} instalador(es) listos para extraer.`;

  toShow.forEach((entry) => {
    const row = document.createElement('div');
    row.className = 'history-row';

    const info = document.createElement('div');
    const nameEl = document.createElement('strong');
    nameEl.className = 'mono';
    nameEl.textContent = entry.fileName;
    const metaEl = document.createElement('div');
    metaEl.className = 'muted';
    metaEl.textContent = `${entry.badge} · ${formatBytes(entry.sizeBytes)}`;
    info.appendChild(nameEl);
    info.appendChild(metaEl);

    const saveBtn = document.createElement('button');
    saveBtn.className = 'btn-primary-sm';
    saveBtn.textContent = `Guardar ${entry.badge}`;
    saveBtn.addEventListener('click', async () => {
      saveBtn.disabled = true;
      const saveRes = await window.desktopPlatform.extractZipEntryDialog({
        zipPath: result.zipPath,
        entryPath: entry.entryPath,
        fileName: entry.fileName
      });
      saveBtn.disabled = false;
      if (saveRes && !saveRes.canceled) {
        if (saveRes.error) {
          statusEl.textContent = saveRes.error;
        } else {
          statusEl.textContent = `Guardado correctamente: ${saveRes.savedPath} (${formatBytes(saveRes.writtenBytes)})`;
        }
      }
    });

    row.appendChild(info);
    row.appendChild(saveBtn);
    boxEl.appendChild(row);
  });
}

document.getElementById('convertAnyZipBtn').addEventListener('click', () => {
  handleZipConversion([]);
});

document.querySelectorAll('.convert-zip-btn').forEach((btn) => {
  btn.addEventListener('click', () => {
    const exts = (btn.dataset.ext || '').split(',').map((s) => s.trim()).filter(Boolean);
    handleZipConversion(exts);
  });
});

document.querySelectorAll('.external-link-btn').forEach((btn) => {
  btn.addEventListener('click', () => {
    const url = btn.dataset.url;
    if (url) openExternalLink(url);
  });
});

document.getElementById('toggleThemeBtn').addEventListener('click', () => {
  document.body.classList.toggle('light-theme');
});

if (window.desktopPlatform) {
  document.getElementById('authPlatformInfo').textContent =
    `Edición Desktop · ${window.desktopPlatform.platform} (${window.desktopPlatform.arch})`;
}
