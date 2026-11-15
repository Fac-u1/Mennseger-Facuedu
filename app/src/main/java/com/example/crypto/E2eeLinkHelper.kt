package com.example.crypto

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec

data class ParsedE2eeLink(
    val uid: String,
    val displayName: String,
    val fingerprint: String,
    val publicKeyBase64: String
)

object E2eeLinkHelper {

    const val SHARE_BASE_URL = "https://ais-pre-p6nkvdtvnq4jtolxa7hcr3-136784457148.us-east1.run.app/e2ee"
    const val CUSTOM_SCHEME = "facuedumennseger"

    fun buildShareLink(
        uid: String,
        displayName: String,
        fingerprint: String,
        publicKeyBase64: String
    ): String {
        return Uri.parse(SHARE_BASE_URL).buildUpon()
            .appendQueryParameter("uid", uid)
            .appendQueryParameter("name", displayName.take(60))
            .appendQueryParameter("fp", fingerprint)
            .appendQueryParameter("pub", publicKeyBase64)
            .build()
            .toString()
    }

    fun buildShareMessage(
        displayName: String,
        fingerprint: String,
        shareLink: String
    ): String {
        return "Conectate conmigo con cifrado E2EE en Facuedu.uy Mennseger ($displayName · Huella $fingerprint):\n$shareLink"
    }

    fun parseShareLink(rawInput: String): ParsedE2eeLink? {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return null

        // Allow pasting full share message containing the URL
        val urlToken = Regex("""(https://[^\s]+|facuedumennseger://[^\s]+)""")
            .find(trimmed)
            ?.value ?: trimmed

        val uri = runCatching { Uri.parse(urlToken) }.getOrNull() ?: return null
        val uid = uri.getQueryParameter("uid")?.trim().orEmpty()
        if (uid.isBlank()) return null

        val name = uri.getQueryParameter("name")?.trim().orEmpty().ifBlank { "Contacto E2EE" }
        val fp = uri.getQueryParameter("fp")?.trim().orEmpty()
        val pub = uri.getQueryParameter("pub")?.trim().orEmpty()

        val validPub = if (pub.isNotBlank()) {
            val ok = runCatching {
                val decoded = Base64.decode(pub, Base64.NO_WRAP)
                KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(decoded))
            }.isSuccess
            if (ok) pub else ""
        } else {
            ""
        }

        return ParsedE2eeLink(
            uid = uid,
            displayName = name,
            fingerprint = fp,
            publicKeyBase64 = validPub
        )
    }

    fun shareToWhatsApp(context: Context, shareText: String) {
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            if (tryStartTargetedTextShare(context, pkg, shareText)) return
        }
        val webUri = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(shareText)}")
        openUrlOrFallbackChooser(context, webUri, shareText)
    }

    fun shareToX(context: Context, displayName: String, link: String) {
        val xText = "Conectate conmigo con cifrado E2EE en Facuedu.uy Mennseger ($displayName)"
        val fullText = "$xText\n$link"
        if (tryStartTargetedTextShare(context, "com.twitter.android", fullText)) return

        val tweetUri = Uri.parse(
            "https://twitter.com/intent/tweet?text=${Uri.encode(xText)}&url=${Uri.encode(link)}"
        )
        openUrlOrFallbackChooser(context, tweetUri, fullText)
    }

    fun shareToInstagram(context: Context, shareText: String, link: String) {
        copyToClipboard(context, "Vínculo E2EE", shareText)
        if (tryStartTargetedTextShare(context, "com.instagram.android", shareText)) return

        Toast.makeText(
            context,
            "Vínculo E2EE copiado al portapapeles para pegar en Instagram.",
            Toast.LENGTH_SHORT
        ).show()
        shareSystemSheet(context, shareText)
    }

    fun shareToFacebook(context: Context, displayName: String, link: String, shareText: String) {
        val packages = listOf("com.facebook.katana", "com.facebook.orca")
        for (pkg in packages) {
            if (tryStartTargetedTextShare(context, pkg, shareText)) return
        }
        val fbUri = Uri.parse(
            "https://www.facebook.com/sharer/sharer.php?u=${Uri.encode(link)}&quote=${Uri.encode("Clave E2EE de $displayName en Facuedu.uy Mennseger")}"
        )
        openUrlOrFallbackChooser(context, fbUri, shareText)
    }

    fun shareSystemSheet(context: Context, shareText: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Mi clave E2EE · Facuedu.uy Mennseger")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Compartir clave E2EE"))
        }.onFailure {
            copyToClipboard(context, "Vínculo E2EE", shareText)
            Toast.makeText(context, "Vínculo copiado al portapapeles.", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun tryStartTargetedTextShare(
        context: Context,
        packageName: String,
        shareText: String
    ): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(packageName)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun openUrlOrFallbackChooser(
        context: Context,
        uri: Uri,
        fallbackShareText: String
    ) {
        val viewIntent = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(viewIntent)
        } catch (_: Exception) {
            shareSystemSheet(context, fallbackShareText)
        }
    }
}
