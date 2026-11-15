const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('desktopPlatform', {
  platform: process.platform,
  arch: process.arch,
  openExternal: (url) => ipcRenderer.invoke('open-external-url', url),
  copyText: (text) => ipcRenderer.invoke('copy-text', text),
  inspectZipDialog: (extensions) => ipcRenderer.invoke('inspect-zip-dialog', extensions),
  extractZipEntryDialog: (payload) => ipcRenderer.invoke('extract-zip-entry-dialog', payload)
});
