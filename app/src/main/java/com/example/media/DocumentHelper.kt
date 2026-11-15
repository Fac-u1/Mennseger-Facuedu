package com.example.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

data class PickedDocument(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val bytes: ByteArray
)

object DocumentHelper {

    private const val MAX_INLINE_CIPHER_BYTES = 220 * 1024 // ~220KB raw -> ~300KB Base64 inside Firestore 1MB doc limit

    fun readDocument(context: Context, uri: Uri): PickedDocument? {
        val resolver = context.contentResolver
        var fileName = "documento_${System.currentTimeMillis()}"
        var sizeBytes = 0L

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
            }
        }

        val mimeType = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', ""))
            ?: "application/octet-stream"

        val bytes = resolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: return null

        return PickedDocument(
            name = fileName,
            mimeType = mimeType,
            sizeBytes = if (sizeBytes > 0L) sizeBytes else bytes.size.toLong(),
            bytes = bytes
        )
    }

    fun canEmbedInFirestore(bytes: ByteArray): Boolean = bytes.size <= MAX_INLINE_CIPHER_BYTES

    fun saveAndOpenDecryptedDocument(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Intent? {
        return try {
            val docsDir = File(context.filesDir, "docs").apply { mkdirs() }
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            val file = File(docsDir, safeName)
            file.writeBytes(bytes)
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        return "%.1f MB".format(mb)
    }
}
