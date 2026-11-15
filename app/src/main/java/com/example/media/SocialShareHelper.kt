package com.example.media

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

enum class SocialShareTarget(val label: String) {
    WHATSAPP("WhatsApp"),
    X("X"),
    INSTAGRAM("Instagram"),
    FACEBOOK("Facebook"),
    MORE("Más")
}

data class ParsedE2eeInvite(
    val uid: String,
    val displayName: String,
    val publicKeyBase64: String,
    val fingerprint: String
)

object SocialShareHelper {

    private const val BASE_SHARE_URL = "https://ais-pre-p6nkvdtvnq4jtolxa7hcr3-136784457148.us-east1.run.app"

    fun buildE2eeShareLink(
        uid: String,
        displayName: String,
        publicKeyFingerprint: String,
        publicKeyBase64: String
    ): String {
        return Uri.parse(BASE_SHARE_URL)
            .buildUpon()
            .appendQueryParameter("connect_uid", uid)
            .appendQueryParameter("name", displayName)
            .appendQueryParameter("fp", publicKeyFingerprint)
            .appendQueryParameter("key", publicKeyBase64)
            .build()
            .toString()
    }

    fun buildE2eeShareMessage(
        displayName: String,
        publicKeyFingerprint: String,
        link: String
    ): String {
        return "Conectate conmigo en Facuedu.uy Mennseger (E2EE).\n" +
            "Usuario: $displayName\n" +
            "Huella ECDH: $publicKeyFingerprint\n" +
            "Vínculo de clave E2EE: $link"
    }

    fun parseE2eeInviteUri(uri: Uri?): ParsedE2eeInvite? {
        if (uri == null) return null
        val uid = uri.getQueryParameter("connect_uid")
            ?: uri.getQueryParameter("uid")
            ?: return null
        if (uid.isBlank()) return null
        return ParsedE2eeInvite(
            uid = uid.trim(),
            displayName = uri.getQueryParameter("name")?.trim().orEmpty().ifBlank { "Contacto E2EE" },
            publicKeyBase64 = uri.getQueryParameter("key")?.trim().orEmpty(),
            fingerprint = uri.getQueryParameter("fp")?.trim().orEmpty()
        )
    }

    fun shareE2eeLink(
        context: Context,
        target: SocialShareTarget,
        link: String,
        shareMessage: String
    ) {
        when (target) {
            SocialShareTarget.WHATSAPP -> {
                val pkg = firstInstalledPackage(context, listOf("com.whatsapp", "com.whatsapp.w4b"))
                if (pkg != null && launchPackageTextShare(context, pkg, shareMessage)) return
                openWebFallback(
                    context = context,
                    url = "https://wa.me/?text=${Uri.encode(shareMessage)}",
                    fallbackShareMessage = shareMessage
                )
            }

            SocialShareTarget.X -> {
                val pkg = firstInstalledPackage(context, listOf("com.twitter.android"))
                if (pkg != null && launchPackageTextShare(context, pkg, shareMessage)) return
                openWebFallback(
                    context = context,
                    url = "https://x.com/intent/tweet?text=${Uri.encode(shareMessage)}",
                    fallbackShareMessage = shareMessage
                )
            }

            SocialShareTarget.INSTAGRAM -> {
                copyToClipboard(context, link)
                val pkg = firstInstalledPackage(context, listOf("com.instagram.android"))
                if (pkg != null && launchPackageTextShare(context, pkg, shareMessage)) {
                    Toast.makeText(context, "Vínculo E2EE copiado por si necesitás pegarlo en Instagram", Toast.LENGTH_SHORT).show()
                    return
                }
                Toast.makeText(context, "Vínculo E2EE copiado. Abrí Instagram para pegarlo.", Toast.LENGTH_SHORT).show()
                openWebFallback(
                    context = context,
                    url = "https://www.instagram.com/",
                    fallbackShareMessage = shareMessage
                )
            }

            SocialShareTarget.FACEBOOK -> {
                copyToClipboard(context, link)
                val pkg = firstInstalledPackage(context, listOf("com.facebook.katana"))
                if (pkg != null && launchPackageTextShare(context, pkg, link)) {
                    Toast.makeText(context, "Vínculo E2EE copiado por si Facebook solicita pegarlo", Toast.LENGTH_SHORT).show()
                    return
                }
                openWebFallback(
                    context = context,
                    url = "https://www.facebook.com/sharer/sharer.php?u=${Uri.encode(link)}",
                    fallbackShareMessage = shareMessage
                )
            }

            SocialShareTarget.MORE -> {
                openSystemShareSheet(context, shareMessage)
            }
        }
    }

    fun openSystemShareSheet(context: Context, shareMessage: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Mi clave E2EE · Facuedu.uy Mennseger")
            putExtra(Intent.EXTRA_TEXT, shareMessage)
        }
        val chooser = Intent.createChooser(sendIntent, "Compartir clave E2EE")
        runCatching { context.startActivity(chooser) }
            .onFailure {
                Toast.makeText(context, "No se encontró hoja para compartir.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun firstInstalledPackage(context: Context, candidates: List<String>): String? {
        val pm = context.packageManager
        return candidates.firstOrNull { pkg ->
            runCatching {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                true
            }.getOrElse {
                @Suppress("DEPRECATION")
                runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
            }
        }
    }

    private fun launchPackageTextShare(context: Context, packageName: String, text: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage(packageName)
        }
        return runCatching {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun openWebFallback(context: Context, url: String, fallbackShareMessage: String) {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val launched = runCatching {
            if (webIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(webIntent)
                true
            } else {
                false
            }
        }.getOrDefault(false)
        if (!launched) {
            openSystemShareSheet(context, fallbackShareMessage)
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Vínculo E2EE", text))
    }
}
