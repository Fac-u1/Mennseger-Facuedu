package com.example.data.repository

import com.example.data.model.AiAssistantTurn
import com.example.data.model.DirectoryCard
import com.example.data.model.EncryptedChat
import com.example.data.model.EncryptedMessage
import com.example.data.model.SavedContact
import com.example.data.model.UserProfile
import com.example.data.model.VideoCallSession
import kotlinx.coroutines.flow.Flow

interface MessengerDataSource {
    suspend fun ensureUserProvisioned(
        uid: String,
        displayName: String,
        email: String?,
        photoUrl: String?,
        publicKeyBase64: String,
        keyFingerprint: String
    ): Result<Unit>

    fun observeUserProfile(userId: String): Flow<UserProfile?>

    fun observeChats(userId: String): Flow<List<EncryptedChat>>

    suspend fun createChat(
        title: String,
        chatType: String,
        memberUids: List<String>,
        peerName: String = "",
        peerPublicKey: String = "",
        lastPreviewCipher: String = "",
        lastPreviewIv: String = ""
    ): Result<String>

    fun observeMessages(chatId: String, userId: String): Flow<List<EncryptedMessage>>

    suspend fun sendMessage(
        chatId: String,
        senderName: String,
        memberUids: List<String>,
        ciphertext: String,
        iv: String,
        messageType: String = "text",
        docName: String = "",
        docMime: String = "",
        docSizeBytes: Long = 0L,
        docChecksum: String = "",
        docPayloadCipher: String = "",
        docPayloadIv: String = "",
        previewCipher: String = ciphertext.take(400),
        previewIv: String = iv
    ): Result<String>

    fun observeContacts(userId: String): Flow<List<SavedContact>>

    suspend fun lookupDirectoryCard(targetUid: String): Result<DirectoryCard?>

    suspend fun addContact(
        contactUid: String,
        displayName: String,
        handle: String,
        publicKeyBase64: String = "",
        keyFingerprint: String = ""
    ): Result<String>

    fun observeCalls(userId: String): Flow<List<VideoCallSession>>

    suspend fun startCall(
        callerName: String,
        calleeName: String,
        chatId: String,
        memberUids: List<String>,
        callType: String = "video"
    ): Result<String>

    suspend fun updateCallStatus(
        callId: String,
        status: String,
        durationSeconds: Long
    ): Result<Unit>

    fun observeAiTurns(userId: String): Flow<List<AiAssistantTurn>>

    suspend fun saveAiTurn(
        role: String,
        mode: String,
        text: String,
        modelUsed: String,
        mediaSummary: String = ""
    ): Result<String>
}
