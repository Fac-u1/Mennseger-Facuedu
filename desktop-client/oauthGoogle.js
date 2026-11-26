const http = require('http');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const GOOGLE_AUTH_URL = 'https://accounts.google.com/o/oauth2/v2/auth';
const GOOGLE_TOKEN_URL = 'https://oauth2.googleapis.com/token';
const GOOGLE_USERINFO_URL = 'https://openidconnect.googleapis.com/v1/userinfo';
const FIREBASE_IDP_URL = 'https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp';

function getConfigPath(userDataDir) {
  return path.join(userDataDir, 'google-oauth-config.json');
}

function loadOAuthConfig(userDataDir) {
  let saved = {};
  try {
    const file = getConfigPath(userDataDir);
    if (fs.existsSync(file)) {
      saved = JSON.parse(fs.readFileSync(file, 'utf8')) || {};
    }
  } catch (_) {
    saved = {};
  }

  return {
    clientId: (process.env.GOOGLE_DESKTOP_CLIENT_ID || saved.clientId || '').trim(),
    clientSecret: (process.env.GOOGLE_DESKTOP_CLIENT_SECRET || saved.clientSecret || '').trim(),
    firebaseApiKey: (process.env.FIREBASE_API_KEY || saved.firebaseApiKey || '').trim(),
    fromEnv: Boolean(process.env.GOOGLE_DESKTOP_CLIENT_ID)
  };
}

function saveOAuthConfig(userDataDir, { clientId = '', clientSecret = '', firebaseApiKey = '' }) {
  const clean = {
    clientId: String(clientId || '').trim(),
    clientSecret: String(clientSecret || '').trim(),
    firebaseApiKey: String(firebaseApiKey || '').trim()
  };
  fs.mkdirSync(userDataDir, { recursive: true });
  fs.writeFileSync(getConfigPath(userDataDir), JSON.stringify(clean, null, 2), 'utf8');
  return loadOAuthConfig(userDataDir);
}

function base64Url(buffer) {
  return buffer
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

async function signInWithGoogleDesktop({ userDataDir, openExternal }) {
  const cfg = loadOAuthConfig(userDataDir);
  if (!cfg.clientId) {
    return {
      ok: false,
      needsConfig: true,
      error: 'Configurá tu Google Desktop OAuth Client ID (o variable GOOGLE_DESKTOP_CLIENT_ID) para iniciar sesión con Google.'
    };
  }

  const state = base64Url(crypto.randomBytes(24));
  const codeVerifier = base64Url(crypto.randomBytes(48));
  const codeChallenge = base64Url(crypto.createHash('sha256').update(codeVerifier).digest());

  return new Promise((resolve) => {
    let settled = false;
    let timeoutId = null;

    const server = http.createServer(async (req, res) => {
      try {
        const reqUrl = new URL(req.url || '/', 'http://127.0.0.1');
        if (reqUrl.pathname !== '/callback') {
          res.writeHead(404);
          res.end('Not found');
          return;
        }

        const returnedError = reqUrl.searchParams.get('error');
        const returnedState = reqUrl.searchParams.get('state');
        const code = reqUrl.searchParams.get('code');

        if (returnedError) {
          res.writeHead(400, { 'Content-Type': 'text/html; charset=utf-8' });
          res.end('<h2>Autenticación cancelada o rechazada</h2><p>Podés cerrar esta pestaña y volver a Facuedu.uy Mennseger.</p>');
          finish({ ok: false, error: `Google OAuth rechazado: ${returnedError}` });
          return;
        }

        if (!code || returnedState !== state) {
          res.writeHead(400, { 'Content-Type': 'text/html; charset=utf-8' });
          res.end('<h2>Estado OAuth inválido</h2><p>Cerrá esta pestaña e intentá nuevamente.</p>');
          finish({ ok: false, error: 'Respuesta OAuth inválida o estado de seguridad no coincidente.' });
          return;
        }

        const address = server.address();
        const redirectUri = `http://127.0.0.1:${address.port}/callback`;

        const tokenParams = new URLSearchParams({
          client_id: cfg.clientId,
          code,
          code_verifier: codeVerifier,
          grant_type: 'authorization_code',
          redirect_uri: redirectUri
        });
        if (cfg.clientSecret) {
          tokenParams.set('client_secret', cfg.clientSecret);
        }

        const tokenResp = await fetch(GOOGLE_TOKEN_URL, {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: tokenParams.toString()
        });
        const tokenData = await tokenResp.json();
        if (!tokenResp.ok || !tokenData.access_token) {
          throw new Error(tokenData.error_description || tokenData.error || 'No se pudo obtener el token de Google.');
        }

        const userResp = await fetch(GOOGLE_USERINFO_URL, {
          headers: { Authorization: `Bearer ${tokenData.access_token}` }
        });
        const profile = await userResp.json();
        if (!userResp.ok || !profile.sub) {
          throw new Error('No se pudo leer el perfil de Google.');
        }

        let firebaseUid = null;
        if (cfg.firebaseApiKey && tokenData.id_token) {
          const fbResp = await fetch(`${FIREBASE_IDP_URL}?key=${encodeURIComponent(cfg.firebaseApiKey)}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              postBody: `id_token=${encodeURIComponent(tokenData.id_token)}&providerId=google.com`,
              requestUri: 'http://localhost',
              returnIdpCredential: true,
              returnSecureToken: true
            })
          });
          const fbData = await fbResp.json();
          if (fbResp.ok && fbData.localId) {
            firebaseUid = fbData.localId;
          }
        }

        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end('<h2>¡Sesión iniciada con Google!</h2><p>Ya podés cerrar esta pestaña y volver a Facuedu.uy Mennseger.</p>');

        finish({
          ok: true,
          user: {
            uid: firebaseUid || `google_${profile.sub}`,
            displayName: profile.name || profile.email || 'Usuario Google',
            email: profile.email || '',
            photoUrl: profile.picture || '',
            firebaseLinked: Boolean(firebaseUid)
          }
        });
      } catch (err) {
        res.writeHead(500, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end('<h2>Error iniciando sesión</h2><p>Volvé a la aplicación para reintentar.</p>');
        finish({ ok: false, error: err.message || 'Error al autenticar con Google.' });
      }
    });

    function finish(payload) {
      if (settled) return;
      settled = true;
      clearTimeout(timeoutId);
      try {
        server.close();
      } catch (_) {
        // Ignore close errors
      }
      resolve(payload);
    }

    server.on('error', (err) => {
      finish({ ok: false, error: err.message || 'No se pudo abrir el puerto local OAuth.' });
    });

    server.listen(0, '127.0.0.1', async () => {
      const address = server.address();
      const redirectUri = `http://127.0.0.1:${address.port}/callback`;
      const authParams = new URLSearchParams({
        client_id: cfg.clientId,
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'openid email profile',
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
        state,
        prompt: 'select_account'
      });

      const authUrl = `${GOOGLE_AUTH_URL}?${authParams.toString()}`;
      timeoutId = setTimeout(() => {
        finish({ ok: false, error: 'Tiempo de espera agotado para iniciar sesión con Google.' });
      }, 180000);

      try {
        await openExternal(authUrl);
      } catch (err) {
        finish({ ok: false, error: err.message || 'No se pudo abrir el navegador para Google Sign-In.' });
      }
    });
  });
}

module.exports = {
  loadOAuthConfig,
  saveOAuthConfig,
  signInWithGoogleDesktop
};
