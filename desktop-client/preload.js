const { contextBridge } = require('electron');

contextBridge.exposeInMainWorld('desktopPlatform', {
  platform: process.platform,
  arch: process.arch
});
