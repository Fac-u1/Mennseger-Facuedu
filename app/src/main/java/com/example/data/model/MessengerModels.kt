package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val publicKeyBase64: String = "",
    val keyFingerprint: String = "",
    val statusMessage: String = "Disponible en Facuedu.uy Mennseger",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DirectoryCard(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val handle: String = "",
    val publicKeyBase64: String = "",
    val keyFingerprint: String = "",
    val isOnline: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavedContact(
    @DocumentId val id: String = "",
    val userId: String = "",
    val contactUid: String = "",
    val displayName: String = "",
    val handle: String = "",
    val publicKeyBase64: String = "",
    val keyFingerprint: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EncryptedChat(
    @DocumentId val id: String = "",
    val createdBy: String = "",
    val title: String = "",
    val chatType: String = "direct",
    val memberUids: List<String> = emptyList(),
    val peerName: String = "",
    val peerPublicKey: String = "",
    val lastPreviewCipher: String = "",
    val lastPreviewIv: String = "",
    val encryptionAlgo: String = "AES-256-GCM",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EncryptedMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val ciphertext: String = "",
    val iv: String = "",
    val messageType: String = "text",
    val docName: String = "",
    val docMime: String = "",
    val docSizeBytes: Long = 0L,
    val docChecksum: String = "",
    val docPayloadCipher: String = "",
    val docPayloadIv: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VideoCallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val calleeName: String = "",
    val chatId: String = "",
    val memberUids: List<String> = emptyList(),
    val callType: String = "video",
    val status: String = "active",
    val isEncrypted: Boolean = true,
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AiAssistantTurn(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val mode: String = "chat",
    val text: String = "",
    val modelUsed: String = "",
    val mediaSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
