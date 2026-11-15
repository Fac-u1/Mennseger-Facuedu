const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const INSTALLER_EXTENSIONS = new Set(['exe', 'dmg', 'pkg', 'appimage', 'deb', 'apk', 'ipa']);
const EOCD_SIG = 0x06054b50;
const CD_SIG = 0x02014b50;
const LFH_SIG = 0x04034b50;

function formatExtBadge(ext) {
  return ext.toLowerCase() === 'appimage' ? '.AppImage' : `.${ext.toUpperCase()}`;
}

function sanitizeZipPath(rawName) {
  const normalized = String(rawName || '').replace(/\\/g, '/').replace(/^\/+/, '');
  if (!normalized || normalized.endsWith('/')) return null;
  if (normalized.includes('../') || normalized === '..') return null;
  return normalized;
}

function readExact(fd, buffer, offset, length, position) {
  let total = 0;
  while (total < length) {
    const bytes = fs.readSync(fd, buffer, offset + total, length - total, position + total);
    if (bytes <= 0) break;
    total += bytes;
  }
  return total;
}

function parseCentralDirectory(fd, fileSize) {
  const tailSize = Math.min(fileSize, 65557);
  const tailBuf = Buffer.alloc(tailSize);
  readExact(fd, tailBuf, 0, tailSize, fileSize - tailSize);

  let eocdPos = -1;
  for (let i = tailSize - 22; i >= 0; i--) {
    if (tailBuf.readUInt32LE(i) === EOCD_SIG) {
      const commentLen = tailBuf.readUInt16LE(i + 20);
      if (i + 22 + commentLen === tailSize) {
        eocdPos = i;
        break;
      }
    }
  }
  if (eocdPos === -1) {
    throw new Error('El archivo seleccionado no es un .ZIP válido (EOCD no encontrado).');
  }

  const totalEntries = tailBuf.readUInt16LE(eocdPos + 10);
  const cdSize = tailBuf.readUInt32LE(eocdPos + 12);
  const cdOffset = tailBuf.readUInt32LE(eocdPos + 16);

  if (cdOffset + cdSize > fileSize) {
    throw new Error('Directorio central del archivo .ZIP corrupto.');
  }

  const cdBuf = Buffer.alloc(cdSize);
  readExact(fd, cdBuf, 0, cdSize, cdOffset);

  const entries = [];
  let cursor = 0;
  for (let i = 0; i < totalEntries && cursor + 46 <= cdBuf.length; i++) {
    if (cdBuf.readUInt32LE(cursor) !== CD_SIG) break;

    const flags = cdBuf.readUInt16LE(cursor + 8);
    const method = cdBuf.readUInt16LE(cursor + 10);
    const compressedSize = cdBuf.readUInt32LE(cursor + 20);
    const uncompressedSize = cdBuf.readUInt32LE(cursor + 24);
    const nameLen = cdBuf.readUInt16LE(cursor + 28);
    const extraLen = cdBuf.readUInt16LE(cursor + 30);
    const commentLen = cdBuf.readUInt16LE(cursor + 32);
    const localHeaderOffset = cdBuf.readUInt32LE(cursor + 42);

    const nameStart = cursor + 46;
    const rawName = cdBuf.toString((flags & 0x0800) ? 'utf8' : 'latin1', nameStart, nameStart + nameLen);
    cursor = nameStart + nameLen + extraLen + commentLen;

    const cleanPath = sanitizeZipPath(rawName);
    if (!cleanPath) continue;

    const fileName = path.posix.basename(cleanPath);
    const ext = fileName.includes('.') ? fileName.split('.').pop().toLowerCase() : '';
    if (!INSTALLER_EXTENSIONS.has(ext)) continue;

    entries.push({
      entryPath: rawName,
      fileName,
      ext,
      badge: formatExtBadge(ext),
      sizeBytes: uncompressedSize,
      compressedSize,
      method,
      localHeaderOffset
    });
  }

  return entries;
}

function inspectZipArchive(zipPath, filterExtensions = []) {
  const stats = fs.statSync(zipPath);
  const fd = fs.openSync(zipPath, 'r');
  try {
    const allEntries = parseCentralDirectory(fd, stats.size);
    const wanted = new Set((filterExtensions || []).map((e) => String(e).toLowerCase()));
    const entries = allEntries.map((item) => ({
      entryPath: item.entryPath,
      fileName: item.fileName,
      ext: item.ext,
      badge: item.badge,
      sizeBytes: item.sizeBytes,
      matched: wanted.size === 0 || wanted.has(item.ext)
    }));
    return {
      archiveName: path.basename(zipPath),
      entries
    };
  } finally {
    fs.closeSync(fd);
  }
}

function extractZipInstaller(zipPath, targetEntryPath, destinationPath) {
  const stats = fs.statSync(zipPath);
  const fd = fs.openSync(zipPath, 'r');
  try {
    const entries = parseCentralDirectory(fd, stats.size);
    const target = entries.find((e) => e.entryPath === targetEntryPath);
    if (!target) {
      throw new Error(`No se encontró ${targetEntryPath} dentro del archivo .ZIP.`);
    }

    const lfhBuf = Buffer.alloc(30);
    readExact(fd, lfhBuf, 0, 30, target.localHeaderOffset);
    if (lfhBuf.readUInt32LE(0) !== LFH_SIG) {
      throw new Error('Cabecera local del archivo .ZIP inválida.');
    }

    const lfhNameLen = lfhBuf.readUInt16LE(26);
    const lfhExtraLen = lfhBuf.readUInt16LE(28);
    const dataOffset = target.localHeaderOffset + 30 + lfhNameLen + lfhExtraLen;

    const compressedBuf = Buffer.alloc(target.compressedSize);
    readExact(fd, compressedBuf, 0, target.compressedSize, dataOffset);

    let outputBuf;
    if (target.method === 0) {
      outputBuf = compressedBuf;
    } else if (target.method === 8) {
      outputBuf = zlib.inflateRawSync(compressedBuf);
    } else {
      throw new Error(`Método de compresión ZIP no soportado (${target.method}).`);
    }

    fs.writeFileSync(destinationPath, outputBuf);
    return outputBuf.length;
  } finally {
    fs.closeSync(fd);
  }
}

module.exports = {
  inspectZipArchive,
  extractZipInstaller
};
