package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.FacuAiService
import com.example.ai.PhraseTone
import com.example.crypto.CryptoManager
import com.example.crypto.E2eeLinkHelper
import com.example.data.model.EncryptedChat
import com.example.data.model.EncryptedMessage
import com.example.data.model.SavedContact
import com.example.data.model.UserProfile
import com.example.data.model.VideoCallSession
import com.example.data.repository.MessengerDataSource
import com.example.media.DocumentHelper
import com.example.media.PickedDocument
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

private const val TAG = "MessengerViewModel"

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class DecryptedChatItem(
    val chat: EncryptedChat,
    val lastPreviewPlaintext: String,
    val safetyNumber: String
)

data class DecryptedMessageItem(
    val message: EncryptedMessage,
    val decryptedText: String,
    val decryptedDocBytes: ByteArray? = null,
    val decryptionVerified: Boolean = true
)

sealed interface PhraseRewriteUiState {
    data object Idle : PhraseRewriteUiState
    data object Loading : PhraseRewriteUiState
    data class Ready(val tone: PhraseTone, val original: String, val rewritten: String) : PhraseRewriteUiState
    data class Error(val message: String) : PhraseRewriteUiState
}

sealed interface VoiceTranscriptUiState {
    data object Idle : VoiceTranscriptUiState
    data object Recording : VoiceTranscriptUiState
    data object Transcribing : VoiceTranscriptUiState
    data class Error(val message: String) : VoiceTranscriptUiState
}

data class ActiveCallUiState(
    val callId: String,
    val chatId: String,
    val peerName: String,
    val callType: String,
    val elapsedSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isCameraOn: Boolean = true,
    val isFrontCamera: Boolean = true,
    val isSpeakerOn: Boolean = true
)

class MessengerViewModel(
    private val repository: MessengerDataSource,
    private val cryptoManager: CryptoManager,
    private val aiService: FacuAiService,
    val currentUserId: String,
    val currentUserName: String,
    private val currentUserEmail: String?,
    private val currentUserPhotoUrl: String?
) : ViewModel() {

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    private val _phraseRewriteState = MutableStateFlow<PhraseRewriteUiState>(PhraseRewriteUiState.Idle)
    val phraseRewriteState: StateFlow<PhraseRewriteUiState> = _phraseRewriteState.asStateFlow()

    private val _voiceTranscriptState = MutableStateFlow<VoiceTranscriptUiState>(VoiceTranscriptUiState.Idle)
    val voiceTranscriptState: StateFlow<VoiceTranscriptUiState> = _voiceTranscriptState.asStateFlow()

    private val _activeCallState = MutableStateFlow<ActiveCallUiState?>(null)
    val activeCallState: StateFlow<ActiveCallUiState?> = _activeCallState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _docSummaryState = MutableStateFlow<Pair<String, String>?>(null)
    val docSummaryState: StateFlow<Pair<String, String>?> = _docSummaryState.asStateFlow()

    private var callTimerJob: Job? = null
    private var starterBootstrapped = false

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            if (isRealCancellation(error)) throw error
            Log.w(TAG, "Profile observe error", error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val chatsState: StateFlow<UiState<List<DecryptedChatItem>>> = repository.observeChats(currentUserId)
        .map<List<EncryptedChat>, UiState<List<DecryptedChatItem>>> { chats ->
            if (chats.isEmpty() && !starterBootstrapped) {
                starterBootstrapped = true
                ensureStarterVaultChat()
            }
            val items = chats.map { chat ->
                val preview = if (chat.lastPreviewCipher.isNotBlank() && chat.lastPreviewIv.isNotBlank()) {
                    try {
                        cryptoManager.decryptText(
                            chat.lastPreviewCipher,
                            chat.lastPreviewIv,
                            chat.id,
                            chat.peerPublicKey.ifBlank { null }
                        )
                    } catch (_: Exception) {
                        "Mensaje cifrado (AES-256-GCM)"
                    }
                } else {
                    "Canal E2EE listo"
                }
                val safety = cryptoManager.computeSafetyNumber(chat.id, chat.peerPublicKey.ifBlank { null })
                DecryptedChatItem(chat = chat, lastPreviewPlaintext = preview, safetyNumber = safety)
            }
            UiState.Success(items)
        }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing chats", root)
            emit(UiState.Error(root.message ?: "No se pudieron cargar los chats."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val contactsState: StateFlow<UiState<List<SavedContact>>> = repository.observeContacts(currentUserId)
        .map<List<SavedContact>, UiState<List<SavedContact>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(UiState.Error(root.message ?: "Error al cargar contactos"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val callsState: StateFlow<UiState<List<VideoCallSession>>> = repository.observeCalls(currentUserId)
        .map<List<VideoCallSession>, UiState<List<VideoCallSession>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(UiState.Error(root.message ?: "Error al cargar historial de llamadas"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    @Suppress("OPT_IN_USAGE")
    val activeChatMessages: StateFlow<UiState<List<DecryptedMessageItem>>> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(chatId, currentUserId)
                    .map<List<EncryptedMessage>, UiState<List<DecryptedMessageItem>>> { messages ->
                        val peerPub = (chatsState.value as? UiState.Success)
                            ?.data?.firstOrNull { it.chat.id == chatId }?.chat?.peerPublicKey?.ifBlank { null }
                        val decoded = messages.map { msg ->
                            var verified = true
                            val text = try {
                                cryptoManager.decryptText(msg.ciphertext, msg.iv, chatId, peerPub)
                            } catch (_: Exception) {
                                verified = false
                                "[No se pudo descifrar con la clave local]"
                            }
                            val docBytes = if (msg.docPayloadCipher.isNotBlank() && msg.docPayloadIv.isNotBlank()) {
                                try {
                                    cryptoManager.decryptBytes(msg.docPayloadCipher, msg.docPayloadIv, chatId, peerPub)
                                } catch (_: Exception) {
                                    null
                                }
                            } else {
                                null
                            }
                            DecryptedMessageItem(
                                message = msg,
                                decryptedText = text,
                                decryptedDocBytes = docBytes,
                                decryptionVerified = verified
                            )
                        }
                        UiState.Success(decoded)
                    }
                    .catch { error ->
                        val firestoreError = unwrapFirestoreError(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        val root = firestoreError ?: error
                        emit(UiState.Error(root.message ?: "Error al observar mensajes cifrados"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            repository.ensureUserProvisioned(
                uid = currentUserId,
                displayName = currentUserName,
                email = currentUserEmail,
                photoUrl = currentUserPhotoUrl,
                publicKeyBase64 = cryptoManager.publicKeyBase64,
                keyFingerprint = cryptoManager.keyFingerprint
            ).onFailure { e ->
                _statusBanner.value = "Error de sincronización: ${e.localizedMessage}"
            }
        }
    }

    private fun ensureStarterVaultChat() {
        viewModelScope.launch {
            repository.createChat(
                title = "Bóveda Cifrada Personal",
                chatType = "vault",
                memberUids = listOf(currentUserId),
                peerName = currentUserName,
                peerPublicKey = cryptoManager.publicKeyBase64
            )
        }
    }

    fun selectChat(chatId: String?) {
        _selectedChatId.value = chatId
        _phraseRewriteState.value = PhraseRewriteUiState.Idle
    }

    fun updateComposerText(value: String) {
        _composerText.value = value
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun clearDocSummary() {
        _docSummaryState.value = null
    }

    fun createEncryptedChat(title: String, contactUidInput: String = "", onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            val cleanUid = contactUidInput.trim()
            if (cleanUid.isNotBlank() && cleanUid != currentUserId) {
                val cardResult = repository.lookupDirectoryCard(cleanUid)
                val card = cardResult.getOrNull()
                if (card != null) {
                    val result = repository.createChat(
                        title = title.ifBlank { card.displayName },
                        chatType = "direct",
                        memberUids = listOf(currentUserId, card.userId),
                        peerName = card.displayName,
                        peerPublicKey = card.publicKeyBase64
                    )
                    result.onSuccess { newId ->
                        _selectedChatId.value = newId
                        onCreated(newId)
                    }.onFailure {
                        _statusBanner.value = it.localizedMessage ?: "No se pudo crear el chat."
                    }
                } else {
                    _statusBanner.value = "No se encontró el UID en el directorio público. Se creó como sala cifrada."
                    val result = repository.createChat(
                        title = title.ifBlank { "Sala Segura" },
                        chatType = "group",
                        memberUids = listOf(currentUserId),
                        peerName = title.ifBlank { "Sala Segura" },
                        peerPublicKey = cryptoManager.publicKeyBase64
                    )
                    result.onSuccess { newId ->
                        _selectedChatId.value = newId
                        onCreated(newId)
                    }
                }
            } else {
                val result = repository.createChat(
                    title = title.ifBlank { "Canal Cifrado E2EE" },
                    chatType = "vault",
                    memberUids = listOf(currentUserId),
                    peerName = title.ifBlank { currentUserName },
                    peerPublicKey = cryptoManager.publicKeyBase64
                )
                result.onSuccess { newId ->
                    _selectedChatId.value = newId
                    onCreated(newId)
                }.onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Error al crear el chat."
                }
            }
        }
    }

    fun addContactByUid(contactUid: String, aliasName: String) {
        viewModelScope.launch {
            val cleanUid = contactUid.trim()
            if (cleanUid.isBlank()) return@launch
            val card = repository.lookupDirectoryCard(cleanUid).getOrNull()
            val name = card?.displayName ?: aliasName.ifBlank { "Contacto E2EE" }
            val handle = card?.handle ?: "@${cleanUid.take(8)}"
            val pubKey = card?.publicKeyBase64 ?: ""
            val fp = card?.keyFingerprint ?: ""
            repository.addContact(
                contactUid = cleanUid,
                displayName = name,
                handle = handle,
                publicKeyBase64 = pubKey,
                keyFingerprint = fp
            ).onSuccess {
                _statusBanner.value = "Contacto '$name' guardado con clave E2EE."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "No se pudo agregar el contacto."
            }
        }
    }

    fun importE2eeLinkAndStartChat(
        rawLinkInput: String,
        onChatOpened: (String) -> Unit = {}
    ) {
        val parsed = E2eeLinkHelper.parseShareLink(rawLinkInput)
        if (parsed == null) {
            _statusBanner.value = "El vínculo E2EE no es válido o no contiene un UID."
            return
        }
        if (parsed.uid == currentUserId) {
            _statusBanner.value = "Ese es tu propio vínculo E2EE."
            return
        }

        viewModelScope.launch {
            val directoryCard = repository.lookupDirectoryCard(parsed.uid).getOrNull()
            val resolvedName = directoryCard?.displayName?.ifBlank { parsed.displayName } ?: parsed.displayName
            val resolvedPubKey = parsed.publicKeyBase64.ifBlank { directoryCard?.publicKeyBase64.orEmpty() }
            val resolvedFingerprint = parsed.fingerprint.ifBlank { directoryCard?.keyFingerprint.orEmpty() }
            val resolvedHandle = directoryCard?.handle ?: "@${parsed.uid.take(8)}"

            repository.addContact(
                contactUid = parsed.uid,
                displayName = resolvedName,
                handle = resolvedHandle,
                publicKeyBase64 = resolvedPubKey,
                keyFingerprint = resolvedFingerprint
            )

            val existingDirect = (chatsState.value as? UiState.Success)?.data
                ?.firstOrNull {
                    it.chat.chatType == "direct" && it.chat.memberUids.contains(parsed.uid)
                }

            if (existingDirect != null) {
                _selectedChatId.value = existingDirect.chat.id
                _statusBanner.value = "Clave E2EE verificada. Chat abierto con $resolvedName."
                onChatOpened(existingDirect.chat.id)
            } else {
                repository.createChat(
                    title = resolvedName,
                    chatType = "direct",
                    memberUids = listOf(currentUserId, parsed.uid),
                    peerName = resolvedName,
                    peerPublicKey = resolvedPubKey
                ).onSuccess { newChatId ->
                    _selectedChatId.value = newChatId
                    _statusBanner.value = "Contacto agregado y chat E2EE iniciado con $resolvedName."
                    onChatOpened(newChatId)
                }.onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "No se pudo abrir el chat cifrado."
                }
            }
        }
    }

    fun sendEncryptedMessage(
        chatItem: DecryptedChatItem,
        rawText: String = _composerText.value,
        messageType: String = "text"
    ) {
        val cleanText = rawText.trim()
        if (cleanText.isBlank()) return
        val chat = chatItem.chat
        val peerPub = chat.peerPublicKey.ifBlank { null }

        viewModelScope.launch {
            val encrypted = cryptoManager.encryptText(cleanText, chat.id, peerPub)
            if (rawText == _composerText.value) {
                _composerText.value = ""
            }
            repository.sendMessage(
                chatId = chat.id,
                senderName = currentUserName,
                memberUids = chat.memberUids,
                ciphertext = encrypted.ciphertextBase64,
                iv = encrypted.ivBase64,
                messageType = messageType,
                previewCipher = encrypted.ciphertextBase64,
                previewIv = encrypted.ivBase64
            ).onFailure {
                _statusBanner.value = it.localizedMessage ?: "Error al enviar mensaje cifrado."
            }
        }
    }

    fun sendEncryptedDocument(chatItem: DecryptedChatItem, doc: PickedDocument) {
        val chat = chatItem.chat
        val peerPub = chat.peerPublicKey.ifBlank { null }

        viewModelScope.launch {
            val checksum = cryptoManager.sha256Hex(doc.bytes)
            val caption = "Documento cifrado: ${doc.name} (${DocumentHelper.formatFileSize(doc.sizeBytes)})"
            val msgEnc = cryptoManager.encryptText(caption, chat.id, peerPub)
            val payloadEnc = if (DocumentHelper.canEmbedInFirestore(doc.bytes)) {
                cryptoManager.encryptBytes(doc.bytes, chat.id, peerPub)
            } else {
                null
            }
            repository.sendMessage(
                chatId = chat.id,
                senderName = currentUserName,
                memberUids = chat.memberUids,
                ciphertext = msgEnc.ciphertextBase64,
                iv = msgEnc.ivBase64,
                messageType = "document",
                docName = doc.name,
                docMime = doc.mimeType,
                docSizeBytes = doc.sizeBytes,
                docChecksum = checksum,
                docPayloadCipher = payloadEnc?.ciphertextBase64 ?: "",
                docPayloadIv = payloadEnc?.ivBase64 ?: "",
                previewCipher = msgEnc.ciphertextBase64,
                previewIv = msgEnc.ivBase64
            ).onSuccess {
                _statusBanner.value = if (payloadEnc != null) {
                    "Documento '${doc.name}' cifrado y enviado."
                } else {
                    "Metadatos y SHA-256 de '${doc.name}' enviados (>220KB)."
                }
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Error al compartir documento."
            }
        }
    }

    fun shareBitmapToChat(chatItem: DecryptedChatItem, bitmap: Bitmap, label: String) {
        viewModelScope.launch {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
            val bytes = stream.toByteArray()
            val picked = PickedDocument(
                name = "${label.replace(" ", "_").take(24)}.jpg",
                mimeType = "image/jpeg",
                sizeBytes = bytes.size.toLong(),
                bytes = bytes
            )
            sendEncryptedDocument(chatItem, picked)
        }
    }

    fun summarizeDocumentWithAi(docName: String, mimeType: String, bytes: ByteArray) {
        viewModelScope.launch {
            _docSummaryState.value = docName to "Analizando documento con FacuIA..."
            try {
                val summary = aiService.summarizeDocumentText(docName, mimeType, bytes)
                _docSummaryState.value = docName to summary
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _docSummaryState.value = docName to "Error al resumir: ${e.localizedMessage}"
            }
        }
    }

    fun rewriteComposerPhrase(tone: PhraseTone) {
        val original = _composerText.value.trim()
        if (original.isBlank()) {
            _phraseRewriteState.value = PhraseRewriteUiState.Error("Escribe primero una frase para transformarla.")
            return
        }
        viewModelScope.launch {
            _phraseRewriteState.value = PhraseRewriteUiState.Loading
            try {
                val rewritten = aiService.rewritePhrase(original, tone)
                _phraseRewriteState.value = PhraseRewriteUiState.Ready(tone, original, rewritten)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _phraseRewriteState.value = PhraseRewriteUiState.Error(
                    e.localizedMessage ?: "No se pudo reescribir la frase."
                )
            }
        }
    }

    fun applyRewrittenPhrase() {
        val state = _phraseRewriteState.value
        if (state is PhraseRewriteUiState.Ready) {
            _composerText.value = state.rewritten
        }
        _phraseRewriteState.value = PhraseRewriteUiState.Idle
    }

    fun dismissPhraseRewrite() {
        _phraseRewriteState.value = PhraseRewriteUiState.Idle
    }

    fun setVoiceRecordingState(isRecording: Boolean) {
        _voiceTranscriptState.value = if (isRecording) {
            VoiceTranscriptUiState.Recording
        } else {
            VoiceTranscriptUiState.Idle
        }
    }

    fun transcribeAudioToComposer(audioFile: File) {
        viewModelScope.launch {
            _voiceTranscriptState.value = VoiceTranscriptUiState.Transcribing
            try {
                val bytes = audioFile.readBytes()
                val (transcript, _) = aiService.transcribeAudio(bytes, "audio/mp4")
                val existing = _composerText.value.trim()
                _composerText.value = if (existing.isBlank()) transcript else "$existing $transcript"
                _voiceTranscriptState.value = VoiceTranscriptUiState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _voiceTranscriptState.value = VoiceTranscriptUiState.Error(
                    e.localizedMessage ?: "Error al transcribir audio."
                )
            } finally {
                runCatching { audioFile.delete() }
            }
        }
    }

    fun startVideoCall(chatItem: DecryptedChatItem, callType: String = "video") {
        val chat = chatItem.chat
        viewModelScope.launch {
            val peerName = chat.peerName.ifBlank { chat.title }
            repository.startCall(
                callerName = currentUserName,
                calleeName = peerName,
                chatId = chat.id,
                memberUids = chat.memberUids,
                callType = callType
            ).onSuccess { callId ->
                _activeCallState.value = ActiveCallUiState(
                    callId = callId,
                    chatId = chat.id,
                    peerName = peerName,
                    callType = callType,
                    isCameraOn = callType == "video"
                )
                startCallTimer()
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "No se pudo iniciar la llamada."
            }
        }
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _activeCallState.value ?: break
                _activeCallState.value = current.copy(elapsedSeconds = current.elapsedSeconds + 1L)
            }
        }
    }

    fun toggleCallMute() {
        _activeCallState.value = _activeCallState.value?.let { it.copy(isMuted = !it.isMuted) }
    }

    fun toggleCallCamera() {
        _activeCallState.value = _activeCallState.value?.let { it.copy(isCameraOn = !it.isCameraOn) }
    }

    fun switchCallLens() {
        _activeCallState.value = _activeCallState.value?.let { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun toggleCallSpeaker() {
        _activeCallState.value = _activeCallState.value?.let { it.copy(isSpeakerOn = !it.isSpeakerOn) }
    }

    fun endActiveCall() {
        val current = _activeCallState.value ?: return
        callTimerJob?.cancel()
        _activeCallState.value = null
        viewModelScope.launch {
            repository.updateCallStatus(
                callId = current.callId,
                status = "ended",
                durationSeconds = current.elapsedSeconds
            )
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private fun isRealCancellation(error: Throwable): Boolean {
        return unwrapFirestoreError(error) == null && error is CancellationException
    }

    companion object {
        fun provideFactory(
            repository: MessengerDataSource,
            cryptoManager: CryptoManager,
            aiService: FacuAiService,
            currentUserId: String,
            currentUserName: String,
            currentUserEmail: String?,
            currentUserPhotoUrl: String?
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MessengerViewModel(
                    repository = repository,
                    cryptoManager = cryptoManager,
                    aiService = aiService,
                    currentUserId = currentUserId,
                    currentUserName = currentUserName,
                    currentUserEmail = currentUserEmail,
                    currentUserPhotoUrl = currentUserPhotoUrl
                )
            }
        }
    }
}
