package com.example.crypto

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String
)

class CryptoManager(
    context: Context,
    private val ephemeralOnly: Boolean = false
) {

    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val secureRandom = SecureRandom()

    val keyPair: KeyPair by lazy { loadOrCreateEcKeyPair() }

    val publicKeyBase64: String
        get() = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

    val keyFingerprint: String
        get() {
            val digest = MessageDigest.getInstance("SHA-256").digest(keyPair.public.encoded)
            return digest.take(12).joinToString(":") { "%02X".format(it) }
        }

    fun encryptText(
        plaintext: String,
        chatId: String,
        peerPublicKeyBase64: String? = null
    ): EncryptedPayload {
        return encryptBytes(plaintext.toByteArray(StandardCharsets.UTF_8), chatId, peerPublicKeyBase64)
    }

    fun decryptText(
        ciphertextBase64: String,
        ivBase64: String,
        chatId: String,
        peerPublicKeyBase64: String? = null
    ): String {
        val bytes = decryptBytes(ciphertextBase64, ivBase64, chatId, peerPublicKeyBase64)
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun encryptBytes(
        plainBytes: ByteArray,
        chatId: String,
        peerPublicKeyBase64: String? = null
    ): EncryptedPayload {
        val key = deriveChatKey(chatId, peerPublicKeyBase64)
        val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainBytes)
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decryptBytes(
        ciphertextBase64: String,
        ivBase64: String,
        chatId: String,
        peerPublicKeyBase64: String? = null
    ): ByteArray {
        val key = deriveChatKey(chatId, peerPublicKeyBase64)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val cipherBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherBytes)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun computeSafetyNumber(chatId: String, peerPublicKeyBase64: String?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(chatId.toByteArray(StandardCharsets.UTF_8))
        if (!peerPublicKeyBase64.isNullOrBlank()) {
            val keys = listOf(publicKeyBase64, peerPublicKeyBase64).sorted()
            keys.forEach { digest.update(it.toByteArray(StandardCharsets.UTF_8)) }
        } else {
            digest.update(publicKeyBase64.toByteArray(StandardCharsets.UTF_8))
        }
        return digest.digest().take(16).chunked(2).joinToString(" ") { pair ->
            pair.joinToString("") { "%02X".format(it) }
        }
    }

    private fun deriveChatKey(chatId: String, peerPublicKeyBase64: String?): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        if (!peerPublicKeyBase64.isNullOrBlank() && peerPublicKeyBase64 != publicKeyBase64) {
            try {
                val peerKey = decodePublicKey(peerPublicKeyBase64)
                val agreement = KeyAgreement.getInstance("ECDH")
                agreement.init(keyPair.private)
                agreement.doPhase(peerKey, true)
                val sharedSecret = agreement.generateSecret()
                digest.update(sharedSecret)
            } catch (_: Exception) {
                digest.update(keyPair.private.encoded)
            }
        } else {
            digest.update(keyPair.private.encoded)
        }
        digest.update(chatId.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(digest.digest(), "AES")
    }

    private fun decodePublicKey(base64: String): PublicKey {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    fun wipeEphemeralKeys() {
        if (ephemeralOnly) {
            runCatching { keyPair.private.destroy() }
        }
    }

    private fun loadOrCreateEcKeyPair(): KeyPair {
        if (!ephemeralOnly) {
            val privStr = prefs.getString(KEY_PRIVATE, null)
            val pubStr = prefs.getString(KEY_PUBLIC, null)
            if (!privStr.isNullOrBlank() && !pubStr.isNullOrBlank()) {
                try {
                    val kf = KeyFactory.getInstance("EC")
                    val priv: PrivateKey = kf.generatePrivate(
                        PKCS8EncodedKeySpec(Base64.decode(privStr, Base64.NO_WRAP))
                    )
                    val pub: PublicKey = kf.generatePublic(
                        X509EncodedKeySpec(Base64.decode(pubStr, Base64.NO_WRAP))
                    )
                    return KeyPair(pub, priv)
                } catch (_: Exception) {
                    // Regenerate on corruption
                }
            }
        }

        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val kp = gen.generateKeyPair()
        if (!ephemeralOnly) {
            prefs.edit()
                .putString(KEY_PRIVATE, Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP))
                .putString(KEY_PUBLIC, Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP))
                .apply()
        }
        return kp
    }

    private companion object {
        const val PREFS_NAME = "facuedu_e2ee_keys"
        const val KEY_PRIVATE = "ec_private_pkcs8"
        const val KEY_PUBLIC = "ec_public_x509"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
    }
}
