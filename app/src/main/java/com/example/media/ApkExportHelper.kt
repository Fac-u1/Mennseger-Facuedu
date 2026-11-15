package com.example.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class InstalledApkInfo(
    val fileName: String,
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val sizeBytes: Long,
    val sha256: String,
    val sourceAvailable: Boolean
)

object ApkExportHelper {

    suspend fun inspectInstalledApk(context: Context): InstalledApkInfo = withContext(Dispatchers.IO) {
        val sourcePath = runCatching { context.applicationInfo?.sourceDir }.getOrNull()
        val sourceFile = sourcePath?.let { File(it) }?.takeIf { it.exists() && it.isFile }
        val fileName = "Facuedu-uy-Mennseger-v${BuildConfig.VERSION_NAME}.apk"

        val sizeBytes = sourceFile?.length() ?: 0L
        val sha256 = if (sourceFile != null) {
            runCatching { computeSha256(sourceFile) }.getOrDefault("No disponible")
        } else {
            "No disponible en entorno de pruebas"
        }

        InstalledApkInfo(
            fileName = fileName,
            applicationId = BuildConfig.APPLICATION_ID,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            sourceAvailable = sourceFile != null
        )
    }

    suspend fun saveApkToUri(context: Context, destinationUri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val sourcePath = context.applicationInfo?.sourceDir
                ?: throw IllegalStateException("No se encontró el archivo APK instalado.")
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                throw IllegalStateException("El archivo fuente del APK no está accesible.")
            }

            var copiedBytes = 0L
            val outputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: throw IllegalStateException("No se pudo abrir la ubicación de destino.")

            outputStream.use { out ->
                sourceFile.inputStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        copiedBytes += read
                    }
                    out.flush()
                }
            }
            copiedBytes
        }
    }

    suspend fun createShareApkIntent(context: Context, fileName: String): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val sourcePath = context.applicationInfo?.sourceDir
                ?: throw IllegalStateException("No se encontró el archivo APK instalado.")
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                throw IllegalStateException("El archivo fuente del APK no está accesible.")
            }

            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val exportFile = File(exportsDir, fileName)
            sourceFile.inputStream().use { input ->
                exportFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Instalador Facuedu.uy Mennseger (.apk)")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Te comparto el instalador oficial de Facuedu.uy Mennseger v${BuildConfig.VERSION_NAME}."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(shareIntent, "Compartir instalador .APK")
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
