package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.AiAssistantTurn
import com.example.data.model.DirectoryCard
import com.example.data.model.EncryptedChat
import com.example.data.model.EncryptedMessage
import com.example.data.model.SavedContact
import com.example.data.model.UserProfile
import com.example.data.model.VideoCallSession
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MessengerRepository(
    private val db: FirebaseFirestore
) : MessengerDataSource {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    override suspend fun ensureUserProvisioned(
        uid: String,
        displayName: String,
        email: String?,
        photoUrl: String?,
        publicKeyBase64: String,
        keyFingerprint: String
    ): Result<Unit> = runCatching {
        val currentUid = requireUserId()
        require(currentUid == uid) { "Authenticated user mismatch" }
        val safeName = displayName.trim().ifBlank { "Usuario Facuedu" }.take(80)
        val handle = "@" + safeName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "facuedu" }.take(30)

        val userRef = db.collection("users").document(uid)
        val existingUser = userRef.get().await()
        if (!existingUser.exists()) {
            val profilePayload = buildMap<String, Any> {
                put("userId", uid)
                put("displayName", safeName)
                if (!email.isNullOrBlank()) put("email", email.take(120))
                if (!photoUrl.isNullOrBlank()) put("photoUrl", photoUrl.take(500))
                put("publicKeyBase64", publicKeyBase64.take(500))
                put("keyFingerprint", keyFingerprint.take(128))
                put("statusMessage", "Cifrado E2EE activo")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            userRef.set(profilePayload).await()
        } else {
            val updatePayload = buildMap<String, Any> {
                put("displayName", safeName)
                if (!email.isNullOrBlank()) put("email", email.take(120))
                if (!photoUrl.isNullOrBlank()) put("photoUrl", photoUrl.take(500))
                put("publicKeyBase64", publicKeyBase64.take(500))
                put("keyFingerprint", keyFingerprint.take(128))
                put("updatedAt", FieldValue.serverTimestamp())
            }
            userRef.update(updatePayload).await()
        }

        val dirRef = db.collection("directory").document(uid)
        val existingDir = dirRef.get().await()
        if (!existingDir.exists()) {
            val dirPayload = mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "handle" to handle,
                "publicKeyBase64" to publicKeyBase64.take(500),
                "keyFingerprint" to keyFingerprint.take(128),
                "isOnline" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            dirRef.set(dirPayload).await()
        } else {
            dirRef.update(
                mapOf(
                    "displayName" to safeName,
                    "handle" to handle,
                    "publicKeyBase64" to publicKeyBase64.take(500),
                    "keyFingerprint" to keyFingerprint.take(128),
                    "isOnline" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid")
    }

    override fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    override fun observeChats(userId: String): Flow<List<EncryptedChat>> = flow {
        val path = "chats"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(EncryptedChat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
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
        val uid = requireUserId()
        val chatId = UUID.randomUUID().toString()
        val normalizedMembers = (listOf(uid) + memberUids).distinct().take(10)
        val payload = buildMap<String, Any> {
            put("createdBy", uid)
            put("title", title.trim().ifBlank { "Chat Cifrado" }.take(100))
            put("chatType", chatType)
            put("memberUids", normalizedMembers)
            if (peerName.isNotBlank()) put("peerName", peerName.take(80))
            if (peerPublicKey.isNotBlank()) put("peerPublicKey", peerPublicKey.take(500))
            if (lastPreviewCipher.isNotBlank()) put("lastPreviewCipher", lastPreviewCipher.take(500))
            if (lastPreviewIv.isNotBlank()) put("lastPreviewIv", lastPreviewIv.take(64))
            put("encryptionAlgo", "AES-256-GCM")
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        db.collection("chats").document(chatId).set(payload).await()
        chatId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "chats")
    }

    override fun observeMessages(chatId: String, userId: String): Flow<List<EncryptedMessage>> = flow {
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(EncryptedMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(Long.MAX_VALUE / 2, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
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
        val uid = requireUserId()
        val messageId = UUID.randomUUID().toString()
        val normalizedMembers = (listOf(uid) + memberUids).distinct().take(10)
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)

        val payload = buildMap<String, Any> {
            put("chatId", chatId)
            put("senderId", uid)
            put("senderName", senderName.trim().ifBlank { "Usuario" }.take(80))
            put("memberUids", normalizedMembers)
            put("ciphertext", ciphertext.take(16000))
            put("iv", iv.take(64))
            put("messageType", messageType)
            if (docName.isNotBlank()) put("docName", docName.take(200))
            if (docMime.isNotBlank()) put("docMime", docMime.take(120))
            if (docSizeBytes > 0L) put("docSizeBytes", docSizeBytes)
            if (docChecksum.isNotBlank()) put("docChecksum", docChecksum.take(128))
            if (docPayloadCipher.isNotBlank()) put("docPayloadCipher", docPayloadCipher.take(350000))
            if (docPayloadIv.isNotBlank()) put("docPayloadIv", docPayloadIv.take(64))
            put("createdAt", FieldValue.serverTimestamp())
        }

        val chatRef = db.collection("chats").document(chatId)
        val batch = db.batch()
        batch.set(msgRef, payload)
        batch.update(
            chatRef,
            mapOf(
                "lastPreviewCipher" to previewCipher.take(500),
                "lastPreviewIv" to previewIv.take(64),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        messageId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "chats/$chatId/messages")
    }

    override fun observeContacts(userId: String): Flow<List<SavedContact>> = flow {
        val path = "users/$userId/contacts"
        emitAll(
            db.collection("users").document(userId).collection("contacts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SavedContact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    override suspend fun lookupDirectoryCard(targetUid: String): Result<DirectoryCard?> = runCatching {
        requireUserId()
        val trimmed = targetUid.trim()
        require(trimmed.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) { "ID de contacto inválido" }
        val snap = db.collection("directory").document(trimmed).get().await()
        snap.toObject(DirectoryCard::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "directory/$targetUid")
    }

    override suspend fun addContact(
        contactUid: String,
        displayName: String,
        handle: String,
        publicKeyBase64: String,
        keyFingerprint: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val contactId = UUID.randomUUID().toString()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("contactUid", contactUid.trim().take(128))
            put("displayName", displayName.trim().ifBlank { "Contacto" }.take(80))
            put("handle", handle.trim().ifBlank { "@contacto" }.take(40))
            if (publicKeyBase64.isNotBlank()) put("publicKeyBase64", publicKeyBase64.take(500))
            if (keyFingerprint.isNotBlank()) put("keyFingerprint", keyFingerprint.take(128))
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        db.collection("users").document(uid).collection("contacts").document(contactId)
            .set(payload)
            .await()
        contactId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "users/contacts")
    }

    override fun observeCalls(userId: String): Flow<List<VideoCallSession>> = flow {
        val path = "calls"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(VideoCallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    override suspend fun startCall(
        callerName: String,
        calleeName: String,
        chatId: String,
        memberUids: List<String>,
        callType: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val callId = UUID.randomUUID().toString()
        val normalizedMembers = (listOf(uid) + memberUids).distinct().take(10)
        val payload = mapOf(
            "callerId" to uid,
            "callerName" to callerName.trim().ifBlank { "Usuario" }.take(80),
            "calleeName" to calleeName.trim().ifBlank { "Sala Segura" }.take(80),
            "chatId" to chatId.take(128),
            "memberUids" to normalizedMembers,
            "callType" to callType,
            "status" to "active",
            "isEncrypted" to true,
            "durationSeconds" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("calls").document(callId).set(payload).await()
        callId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "calls")
    }

    override suspend fun updateCallStatus(
        callId: String,
        status: String,
        durationSeconds: Long
    ): Result<Unit> = runCatching {
        requireUserId()
        db.collection("calls").document(callId)
            .update(
                mapOf(
                    "status" to status,
                    "durationSeconds" to durationSeconds.coerceIn(0L, 86400L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
    }

    override fun observeAiTurns(userId: String): Flow<List<AiAssistantTurn>> = flow {
        val path = "users/$userId/aiTurns"
        emitAll(
            db.collection("users").document(userId).collection("aiTurns")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AiAssistantTurn::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(Long.MAX_VALUE / 2, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    override suspend fun saveAiTurn(
        role: String,
        mode: String,
        text: String,
        modelUsed: String,
        mediaSummary: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val turnId = UUID.randomUUID().toString()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("role", role)
            put("mode", mode)
            put("text", text.take(16000))
            put("modelUsed", modelUsed.take(80))
            if (mediaSummary.isNotBlank()) put("mediaSummary", mediaSummary.take(300))
            put("createdAt", FieldValue.serverTimestamp())
        }
        db.collection("users").document(uid).collection("aiTurns").document(turnId)
            .set(payload)
            .await()
        turnId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "users/aiTurns")
    }

    private fun logStreamError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && Firebase.auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
