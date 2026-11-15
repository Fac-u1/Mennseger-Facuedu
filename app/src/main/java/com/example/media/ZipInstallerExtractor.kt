package com.example.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

enum class InstallerPlatformFilter(
    val id: String,
    val label: String,
    val badgeText: String,
    val extensions: Set<String>
) {
    ALL(
        id = "all",
        label = "Todos (.exe, .dmg, .pkg, .AppImage, .deb, .apk, .ipa)",
        badgeText = ".EXE / .DMG / .PKG / .AppImage / .DEB / .APK / .IPA",
        extensions = setOf("exe", "dmg", "pkg", "appimage", "deb", "apk", "ipa")
    ),
    WINDOWS(
        id = "windows",
        label = "Windows (.exe)",
        badgeText = ".EXE",
        extensions = setOf("exe")
    ),
    MACOS(
        id = "macos",
        label = "macOS (.dmg / .pkg)",
        badgeText = ".DMG / .PKG",
        extensions = setOf("dmg", "pkg")
    ),
    LINUX_UBUNTU(
        id = "linux_ubuntu",
        label = "Linux y Ubuntu (.AppImage / .deb)",
        badgeText = ".AppImage / .DEB",
        extensions = setOf("appimage", "deb")
    ),
    ANDROID(
        id = "android",
        label = "Android (.apk)",
        badgeText = ".APK",
        extensions = setOf("apk")
    ),
    IOS(
        id = "ios",
        label = "iOS (.ipa)",
        badgeText = ".IPA",
        extensions = setOf("ipa")
    )
}

data class ZipInstallerEntry(
    val entryPath: String,
    val fileName: String,
    val extension: String,
    val sizeBytes: Long,
    val platformLabel: String,
    val mimeType: String
)

data class ZipScanResult(
    val archiveName: String,
    val totalEntriesScanned: Int,
    val matchingInstallers: List<ZipInstallerEntry>,
    val allInstallersInZip: List<ZipInstallerEntry>
)

object ZipInstallerExtractor {

    private val SUPPORTED_EXTENSIONS = setOf("exe", "dmg", "pkg", "appimage", "deb", "apk", "ipa")

    fun inspectZipStream(
        archiveName: String,
        inputStream: InputStream,
        filter: InstallerPlatformFilter = InstallerPlatformFilter.ALL
    ): ZipScanResult {
        val allInstallers = mutableListOf<ZipInstallerEntry>()
        var totalEntries = 0
        val buffer = ByteArray(16 * 1024)

        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory) {
                    totalEntries++
                    val normalizedPath = sanitizeEntryPath(entry.name)
                    if (normalizedPath != null) {
                        val fileName = normalizedPath.substringAfterLast('/')
                        val ext = fileName.substringAfterLast('.', "").lowercase()
                        if (ext in SUPPORTED_EXTENSIONS) {
                            var size = entry.size
                            if (size < 0L) {
                                var counted = 0L
                                while (true) {
                                    val read = zis.read(buffer)
                                    if (read <= 0) break
                                    counted += read
                                }
                                size = counted
                            }
                            allInstallers += ZipInstallerEntry(
                                entryPath = entry.name,
                                fileName = fileName,
                                extension = ext,
                                sizeBytes = size.coerceAtLeast(0L),
                                platformLabel = platformForExtension(ext),
                                mimeType = mimeTypeForExtension(ext)
                            )
                        }
                    }
                }
                zis.closeEntry()
            }
        }

        val matching = allInstallers.filter { it.extension in filter.extensions }
        return ZipScanResult(
            archiveName = archiveName,
            totalEntriesScanned = totalEntries,
            matchingInstallers = matching,
            allInstallersInZip = allInstallers
        )
    }

    fun extractEntryFromStream(
        inputStream: InputStream,
        targetEntryPath: String,
        outputStream: OutputStream
    ): Long {
        val buffer = ByteArray(32 * 1024)
        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory && entry.name == targetEntryPath) {
                    var written = 0L
                    while (true) {
                        val read = zis.read(buffer)
                        if (read <= 0) break
                        outputStream.write(buffer, 0, read)
                        written += read
                    }
                    outputStream.flush()
                    zis.closeEntry()
                    return written
                }
                zis.closeEntry()
            }
        }
        throw IllegalStateException("No se encontró $targetEntryPath dentro del archivo .zip.")
    }

    suspend fun inspectZipUri(
        context: Context,
        zipUri: Uri,
        filter: InstallerPlatformFilter = InstallerPlatformFilter.ALL
    ): Result<ZipScanResult> = withContext(Dispatchers.IO) {
        runCatching {
            val archiveName = resolveDisplayName(context, zipUri) ?: "archivo.zip"
            val input = context.contentResolver.openInputStream(zipUri)
                ?: throw IllegalStateException("No se pudo abrir el archivo .zip seleccionado.")
            input.use { stream ->
                inspectZipStream(archiveName, stream, filter)
            }
        }
    }

    suspend fun extractEntryToUri(
        context: Context,
        zipUri: Uri,
        entry: ZipInstallerEntry,
        destinationUri: Uri
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(zipUri)
                ?: throw IllegalStateException("No se pudo leer el archivo .zip de origen.")
            val output = context.contentResolver.openOutputStream(destinationUri)
                ?: throw IllegalStateException("No se pudo crear el archivo de destino.")

            input.use { zipIn ->
                output.use { destOut ->
                    extractEntryFromStream(zipIn, entry.entryPath, destOut)
                }
            }
        }
    }

    suspend fun createShareExtractedIntent(
        context: Context,
        zipUri: Uri,
        entry: ZipInstallerEntry
    ): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = entry.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val outFile = File(exportsDir, safeName)

            val input = context.contentResolver.openInputStream(zipUri)
                ?: throw IllegalStateException("No se pudo abrir el archivo .zip de origen.")
            input.use { zipIn ->
                outFile.outputStream().use { fileOut ->
                    extractEntryFromStream(zipIn, entry.entryPath, fileOut)
                }
            }

            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = entry.mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, "Instalador ${entry.fileName}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Instalador ${entry.platformLabel} (${entry.fileName}) extraído con Facuedu.uy Mennseger."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(sendIntent, "Compartir ${entry.fileName}")
        }
    }

    fun formatExtensionBadge(extension: String): String {
        return when (extension.lowercase()) {
            "appimage" -> ".AppImage"
            else -> ".${extension.uppercase()}"
        }
    }

    private fun sanitizeEntryPath(rawPath: String): String? {
        val normalized = rawPath.replace('\\', '/').trimStart('/')
        if (normalized.isBlank() || normalized.contains("../") || normalized == "..") {
            return null
        }
        return normalized
    }

    private fun platformForExtension(ext: String): String = when (ext.lowercase()) {
        "exe" -> "Windows"
        "dmg", "pkg" -> "macOS"
        "appimage", "deb" -> "Linux / Ubuntu"
        "apk" -> "Android"
        "ipa" -> "iOS"
        else -> "Instalador"
    }

    private fun mimeTypeForExtension(ext: String): String = when (ext.lowercase()) {
        "apk" -> "application/vnd.android.package-archive"
        "deb" -> "application/vnd.debian.binary-package"
        "exe" -> "application/vnd.microsoft.portable-executable"
        "dmg" -> "application/x-apple-diskimage"
        else -> "application/octet-stream"
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }
}
