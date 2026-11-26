const { app, BrowserWindow, clipboard, dialog, ipcMain, session, shell } = require('electron');
const path = require('path');
const { inspectZipArchive, extractZipInstaller } = require('./zipExtractor');
const { loadOAuthConfig, saveOAuthConfig, signInWithGoogleDesktop } = require('./oauthGoogle');

if (process.platform === 'win32') {
  app.setAppUserModelId('com.aistudio.facuedumennseger.kqzmxp');
}

let mainWindow = null;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1180,
    height: 780,
    minWidth: 860,
    minHeight: 620,
    backgroundColor: '#0A1017',
    title: 'Facuedu.uy Mennseger',
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  mainWindow.removeMenu();
  mainWindow.loadFile(path.join(__dirname, 'index.html'));

  session.defaultSession.setPermissionRequestHandler((_, permission, callback) => {
    if (permission === 'media' || permission === 'clipboard-sanitized-write') {
      callback(true);
    } else {
      callback(false);
    }
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://') || url.startsWith('http://')) {
      shell.openExternal(url);
    }
    return { action: 'deny' };
  });

  mainWindow.on('closed', async () => {
    try {
      await session.defaultSession.clearStorageData();
    } catch (_) {
      // Ignore cleanup errors on shutdown
    }
    mainWindow = null;
  });
}

ipcMain.handle('open-external-url', async (_, url) => {
  if (typeof url === 'string' && (url.startsWith('https://') || url.startsWith('http://'))) {
    await shell.openExternal(url);
    return true;
  }
  return false;
});

ipcMain.handle('copy-text', (_, text) => {
  if (typeof text === 'string') {
    clipboard.writeText(text);
    return true;
  }
  return false;
});

ipcMain.handle('get-google-oauth-config', () => {
  const cfg = loadOAuthConfig(app.getPath('userData'));
  return {
    configured: Boolean(cfg.clientId),
    clientId: cfg.clientId,
    hasFirebaseKey: Boolean(cfg.firebaseApiKey),
    fromEnv: cfg.fromEnv
  };
});

ipcMain.handle('save-google-oauth-config', (_, payload) => {
  const updated = saveOAuthConfig(app.getPath('userData'), payload || {});
  return {
    configured: Boolean(updated.clientId),
    clientId: updated.clientId,
    hasFirebaseKey: Boolean(updated.firebaseApiKey),
    fromEnv: updated.fromEnv
  };
});

ipcMain.handle('sign-in-google', async () => {
  return signInWithGoogleDesktop({
    userDataDir: app.getPath('userData'),
    openExternal: (url) => shell.openExternal(url)
  });
});

ipcMain.handle('inspect-zip-dialog', async (_, filterExtensions) => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: 'Seleccionar archivo .ZIP de GitHub Actions',
    properties: ['openFile'],
    filters: [{ name: 'Archivos ZIP', extensions: ['zip'] }]
  });
  if (result.canceled || !result.filePaths || result.filePaths.length === 0) {
    return { canceled: true };
  }
  const zipPath = result.filePaths[0];
  try {
    const scanned = inspectZipArchive(zipPath, filterExtensions);
    return {
      canceled: false,
      zipPath,
      archiveName: scanned.archiveName,
      entries: scanned.entries
    };
  } catch (err) {
    return {
      canceled: false,
      error: err.message || 'No se pudo analizar el archivo .ZIP.'
    };
  }
});

ipcMain.handle('extract-zip-entry-dialog', async (_, payload) => {
  if (!payload || !payload.zipPath || !payload.entryPath || !payload.fileName) {
    return { canceled: true, error: 'Datos incompletos para extraer.' };
  }
  const saveResult = await dialog.showSaveDialog(mainWindow, {
    title: `Guardar ${payload.fileName}`,
    defaultPath: payload.fileName
  });
  if (saveResult.canceled || !saveResult.filePath) {
    return { canceled: true };
  }
  try {
    const writtenBytes = extractZipInstaller(payload.zipPath, payload.entryPath, saveResult.filePath);
    return {
      canceled: false,
      savedPath: saveResult.filePath,
      writtenBytes
    };
  } catch (err) {
    return {
      canceled: false,
      error: err.message || 'No se pudo extraer el instalador del .ZIP.'
    };
  }
});

app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
