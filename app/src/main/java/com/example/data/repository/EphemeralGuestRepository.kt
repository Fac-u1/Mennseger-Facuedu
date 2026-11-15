package com.example.data.repository

import com.example.data.model.AiAssistantTurn
import com.example.data.model.DirectoryCard
import com.example.data.model.EncryptedChat
import com.example.data.model.EncryptedMessage
import com.example.data.model.SavedContact
import com.example.data.model.UserProfile
import com.example.data.model.VideoCallSession
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * In-memory-only repository for Modo Prueba / Invitado.
 * Stores zero records on disk or in Cloud Firestore and wipes completely on app close.
 */
class EphemeralGuestRepository(
    private val guestUid: String
) : MessengerDataSource {

    private val profileFlow = MutableStateFlow<UserProfile?>(null)
    private val chatsFlow = MutableStateFlow<List<EncryptedChat>>(emptyList())
    private val messagesFlow = MutableStateFlow<Map<String, List<EncryptedMessage>>>(emptyMap())
    private val contactsFlow = MutableStateFlow<List<SavedContact>>(emptyList())
    private val callsFlow = MutableStateFlow<List<VideoCallSession>>(emptyList())
    private val aiTurnsFlow = MutableStateFlow<List<AiAssistantTurn>>(emptyList())

    override suspend fun ensureUserProvisioned(
        uid: String,
        displayName: String,
        email: String?,
        photoUrl: String?,
        publicKeyBase64: String,
        keyFingerprint: String
    ): Result<Unit> = runCatching {
        val now = Timestamp.now()
        profileFlow.value = UserProfile(
            id = guestUid,
            userId = guestUid,
            displayName = displayName.ifBlank { "Invitado" },
            email = "",
            photoUrl = "",
            publicKeyBase64 = publicKeyBase64,
            keyFingerprint = keyFingerprint,
            statusMessage = "Modo Prueba Efímero (se borra al cerrar)",
            createdAt = now,
            updatedAt = now
        )
    }

    override fun observeUserProfile(userId: String): Flow<UserProfile?> = profileFlow

    override fun observeChats(userId: String): Flow<List<EncryptedChat>> = chatsFlow.map { list ->
        list.sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
    }

    override suspend fun createChat(
        title: String,
        chatType: String,
        memberUids: List<String>,
        peerName: String,
        peerPublicKey: String,
        lastPreviewCipher: String,
        lastPreviewIv: String
    ): Result<String> = runCatching {
        val chatId = UUID.randomUUID().toString()
        val now = Timestamp.now()
        val normalizedMembers = (listOf(guestUid) + memberUids).distinct().take(10)
        val newChat = EncryptedChat(
            id = chatId,
            createdBy = guestUid,
            title = title.trim().ifBlank { "Chat de Prueba E2EE" }.take(100),
            chatType = chatType,
            memberUids = normalizedMembers,
            peerName = peerName.ifBlank { "Sala Invitado" },
            peerPublicKey = peerPublicKey,
            lastPreviewCipher = lastPreviewCipher,
            lastPreviewIv = lastPreviewIv,
            encryptionAlgo = "AES-256-GCM",
            createdAt = now,
            updatedAt = now
        )
        chatsFlow.update { current -> listOf(newChat) + current }
        chatId
    }

    override fun observeMessages(chatId: String, userId: String): Flow<List<EncryptedMessage>> =
        messagesFlow.map { map ->
            map[chatId].orEmpty().sortedBy { it.createdAt ?: Timestamp(0, 0) }
        }

    override suspend fun sendMessage(
        chatId: String,
        senderName: String,
        memberUids: List<String>,
        ciphertext: String,
        iv: String,
        messageType: String,
        docName: String,
        docMime: String,
        docSizeBytes: Long,
        docChecksum: String,
        docPayloadCipher: String,
        docPayloadIv: String,
        previewCipher: String,
        previewIv: String
    ): Result<String> = runCatching {
        val msgId = UUID.randomUUID().toString()
        val now = Timestamp.now()
        val message = EncryptedMessage(
            id = msgId,
            chatId = chatId,
            senderId = guestUid,
            senderName = senderName.ifBlank { "Invitado" },
            memberUids = (listOf(guestUid) + memberUids).distinct(),
            ciphertext = ciphertext,
            iv = iv,
            messageType = messageType,
            docName = docName,
            docMime = docMime,
            docSizeBytes = docSizeBytes,
            docChecksum = docChecksum,
            docPayloadCipher = docPayloadCipher,
            docPayloadIv = docPayloadIv,
            createdAt = now
        )
        messagesFlow.update { current ->
            val updatedList = current[chatId].orEmpty() + message
            current + (chatId to updatedList)
        }
        chatsFlow.update { chats ->
            chats.map { chat ->
                if (chat.id == chatId) {
                    chat.copy(
                        lastPreviewCipher = previewCipher.take(500),
                        lastPreviewIv = previewIv.take(64),
                        updatedAt = now
                    )
                } else {
                    chat
                }
            }
        }
        msgId
    }

    override fun observeContacts(userId: String): Flow<List<SavedContact>> =
        contactsFlow.map { list -> list.sortedBy { it.displayName.lowercase() } }

    override suspend fun lookupDirectoryCard(targetUid: String): Result<DirectoryCard?> =
        Result.success(null)

    override suspend fun addContact(
        contactUid: String,
        displayName: String,
        handle: String,
        publicKeyBase64: String,
        keyFingerprint: String
    ): Result<String> = runCatching {
        val contactId = UUID.randomUUID().toString()
        val now = Timestamp.now()
        val contact = SavedContact(
            id = contactId,
            userId = guestUid,
            contactUid = contactUid.trim(),
            displayName = displayName.trim().ifBlank { "Contacto Prueba" },
            handle = handle.trim().ifBlank { "@prueba" },
            publicKeyBase64 = publicKeyBase64,
            keyFingerprint = keyFingerprint,
            createdAt = now,
            updatedAt = now
        )
        contactsFlow.update { it + contact }
        contactId
    }

    override fun observeCalls(userId: String): Flow<List<VideoCallSession>> =
        callsFlow.map { list ->
            list.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }

    override suspend fun startCall(
        callerName: String,
        calleeName: String,
        chatId: String,
        memberUids: List<String>,
        callType: String
    ): Result<String> = runCatching {
        val callId = UUID.randomUUID().toString()
        val now = Timestamp.now()
        val call = VideoCallSession(
            id = callId,
            callerId = guestUid,
            callerName = callerName.ifBlank { "Invitado" },
            calleeName = calleeName.ifBlank { "Sala Prueba" },
            chatId = chatId,
            memberUids = (listOf(guestUid) + memberUids).distinct(),
            callType = callType,
            status = "active",
            isEncrypted = true,
            durationSeconds = 0L,
            createdAt = now,
            updatedAt = now
        )
        callsFlow.update { listOf(call) + it }
        callId
    }

    override suspend fun updateCallStatus(
        callId: String,
        status: String,
        durationSeconds: Long
    ): Result<Unit> = runCatching {
        val now = Timestamp.now()
        callsFlow.update { calls ->
            calls.map { call ->
                if (call.id == callId && call.status != "ended") {
                    call.copy(
                        status = status,
                        durationSeconds = durationSeconds.coerceIn(0L, 86400L),
                        updatedAt = now
                    )
                } else {
                    call
                }
            }
        }
    }

    override fun observeAiTurns(userId: String): Flow<List<AiAssistantTurn>> =
        aiTurnsFlow.map { list -> list.sortedBy { it.createdAt ?: Timestamp(0, 0) } }

    override suspend fun saveAiTurn(
        role: String,
        mode: String,
        text: String,
        modelUsed: String,
        mediaSummary: String
    ): Result<String> = runCatching {
        val turnId = UUID.randomUUID().toString()
        val turn = AiAssistantTurn(
            id = turnId,
            userId = guestUid,
            role = role,
            mode = mode,
            text = text.take(16000),
            modelUsed = modelUsed.take(80),
            mediaSummary = mediaSummary.take(300),
            createdAt = Timestamp.now()
        )
        aiTurnsFlow.update { it + turn }
        turnId
    }

    fun wipeAllData() {
        profileFlow.value = null
        chatsFlow.value = emptyList()
        messagesFlow.value = emptyMap()
        contactsFlow.value = emptyList()
        callsFlow.value = emptyList()
        aiTurnsFlow.value = emptyList()
    }
}
