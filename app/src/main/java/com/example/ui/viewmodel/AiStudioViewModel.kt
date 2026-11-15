package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.ChatModelTier
import com.example.ai.FacuAiService
import com.example.ai.GeneratedAudioResult
import com.example.ai.GeneratedVideoResult
import com.example.ai.PhraseTone
import com.example.data.model.AiAssistantTurn
import com.example.data.repository.MessengerDataSource
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "AiStudioViewModel"

enum class AiStudioMode(val label: String) {
    CHAT("Chat IA"),
    REWRITE("Cambiar Frases"),
    IMAGE("Fotos IA"),
    VIDEO("Video IA"),
    MUSIC("Música IA"),
    TRANSCRIBE("Audio a Texto")
}

sealed interface MediaGenState<out T> {
    data object Idle : MediaGenState<Nothing>
    data object Loading : MediaGenState<Nothing>
    data class Success<T>(val data: T) : MediaGenState<T>
    data class Error(val message: String) : MediaGenState<Nothing>
}

class AiStudioViewModel(
    private val aiService: FacuAiService,
    private val repository: MessengerDataSource,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedMode = MutableStateFlow(AiStudioMode.CHAT)
    val selectedMode: StateFlow<AiStudioMode> = _selectedMode.asStateFlow()

    private val _selectedTier = MutableStateFlow(ChatModelTier.GENERAL)
    val selectedTier: StateFlow<ChatModelTier> = _selectedTier.asStateFlow()

    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _isChatSending = MutableStateFlow(false)
    val isChatSending: StateFlow<Boolean> = _isChatSending.asStateFlow()

    private val _rewriteResult = MutableStateFlow<MediaGenState<String>>(MediaGenState.Idle)
    val rewriteResult: StateFlow<MediaGenState<String>> = _rewriteResult.asStateFlow()

    private val _imageGenState = MutableStateFlow<MediaGenState<Bitmap>>(MediaGenState.Idle)
    val imageGenState: StateFlow<MediaGenState<Bitmap>> = _imageGenState.asStateFlow()

    private val _videoGenState = MutableStateFlow<MediaGenState<GeneratedVideoResult>>(MediaGenState.Idle)
    val videoGenState: StateFlow<MediaGenState<GeneratedVideoResult>> = _videoGenState.asStateFlow()

    private val _musicGenState = MutableStateFlow<MediaGenState<GeneratedAudioResult>>(MediaGenState.Idle)
    val musicGenState: StateFlow<MediaGenState<GeneratedAudioResult>> = _musicGenState.asStateFlow()

    private val _transcriptGenState = MutableStateFlow<MediaGenState<String>>(MediaGenState.Idle)
    val transcriptGenState: StateFlow<MediaGenState<String>> = _transcriptGenState.asStateFlow()

    val turnsState: StateFlow<UiState<List<AiAssistantTurn>>> = repository.observeAiTurns(currentUserId)
        .map<List<AiAssistantTurn>, UiState<List<AiAssistantTurn>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(UiState.Error(root.message ?: "Error al cargar historial de FacuIA"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun selectMode(mode: AiStudioMode) {
        _selectedMode.value = mode
    }

    fun selectModelTier(tier: ChatModelTier) {
        _selectedTier.value = tier
    }

    fun sendChatMessage(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || _isChatSending.value) return
        val tier = _selectedTier.value

        viewModelScope.launch {
            _isChatSending.value = true
            _chatError.value = null
            _streamingReply.value = ""

            val currentTurns = (turnsState.value as? UiState.Success)?.data.orEmpty()
                .filter { it.mode == "chat" }
                .map { it.role to it.text }

            repository.saveAiTurn(
                role = "user",
                mode = "chat",
                text = clean,
                modelUsed = tier.modelId
            )

            try {
                var finalReply = ""
                aiService.streamChatReply(currentTurns, clean, tier).collect { accumulated ->
                    finalReply = accumulated
                    _streamingReply.value = accumulated
                }
                _streamingReply.value = null
                repository.saveAiTurn(
                    role = "model",
                    mode = "chat",
                    text = finalReply,
                    modelUsed = tier.modelId
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat timed out", e)
                _streamingReply.value = null
                _chatError.value = "FacuIA tardó más de 60 segundos. Intentá de nuevo."
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Quota exceeded", e)
                _streamingReply.value = null
                _chatError.value = "Cuota de IA alcanzada temporalmente. Probá en unos momentos."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat failed", e)
                _streamingReply.value = null
                _chatError.value = e.localizedMessage ?: "Error de conexión con FacuIA."
            } finally {
                _isChatSending.value = false
            }
        }
    }

    fun rewritePhrase(text: String, tone: PhraseTone) {
        val clean = text.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _rewriteResult.value = MediaGenState.Loading
            try {
                val rewritten = aiService.rewritePhrase(clean, tone)
                _rewriteResult.value = MediaGenState.Success(rewritten)
                repository.saveAiTurn(
                    role = "model",
                    mode = "rewrite",
                    text = rewritten,
                    modelUsed = ChatModelTier.FAST.modelId,
                    mediaSummary = "Tono: ${tone.label}"
                )
            } catch (e: TimeoutCancellationException) {
                _rewriteResult.value = MediaGenState.Error("Tiempo agotado al reescribir la frase.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Rewrite failed", e)
                _rewriteResult.value = MediaGenState.Error(e.localizedMessage ?: "No se pudo reescribir.")
            }
        }
    }

    fun generateOrEditImage(prompt: String, sourceBitmap: Bitmap?, aspectRatio: AspectRatio) {
        val clean = prompt.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _imageGenState.value = MediaGenState.Loading
            try {
                val result = aiService.generateOrEditImage(clean, sourceBitmap, aspectRatio)
                _imageGenState.value = MediaGenState.Success(result)
                repository.saveAiTurn(
                    role = "model",
                    mode = "image",
                    text = clean,
                    modelUsed = "gemini-nano-banana-2.1",
                    mediaSummary = if (sourceBitmap != null) "Imagen editada" else "Imagen generada"
                )
            } catch (e: TimeoutCancellationException) {
                _imageGenState.value = MediaGenState.Error("Generación de imagen agotó el tiempo (60s).")
            } catch (e: QuotaExceededException) {
                _imageGenState.value = MediaGenState.Error("Límite de generación de imágenes alcanzado (requiere plan Blaze o esperar cuota).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _imageGenState.value = MediaGenState.Error(e.localizedMessage ?: "No se pudo generar la imagen.")
            }
        }
    }

    fun animateImageToVideo(sourceBitmap: Bitmap, motionPrompt: String, aspectRatioLabel: String) {
        val clean = motionPrompt.trim().ifBlank { "Smooth cinematic camera motion with subtle lighting" }
        viewModelScope.launch {
            _videoGenState.value = MediaGenState.Loading
            try {
                val result = aiService.animateImageToVideo(sourceBitmap, clean, aspectRatioLabel)
                _videoGenState.value = MediaGenState.Success(result)
                repository.saveAiTurn(
                    role = "model",
                    mode = "video",
                    text = clean,
                    modelUsed = result.modelUsed,
                    mediaSummary = "Video $aspectRatioLabel"
                )
            } catch (e: TimeoutCancellationException) {
                _videoGenState.value = MediaGenState.Error("La generación de video excedió los 60 segundos.")
            } catch (e: QuotaExceededException) {
                _videoGenState.value = MediaGenState.Error("Cuota de video agotada.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video generation failed", e)
                _videoGenState.value = MediaGenState.Error(e.localizedMessage ?: "Error al animar video.")
            }
        }
    }

    fun generateMusic(prompt: String, fullLength: Boolean) {
        val clean = prompt.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _musicGenState.value = MediaGenState.Loading
            try {
                val result = aiService.generateMusic(clean, fullLength)
                _musicGenState.value = MediaGenState.Success(result)
                repository.saveAiTurn(
                    role = "model",
                    mode = "music",
                    text = clean,
                    modelUsed = result.modelUsed,
                    mediaSummary = if (fullLength) "Pista completa" else "Clip 30s"
                )
            } catch (e: TimeoutCancellationException) {
                _musicGenState.value = MediaGenState.Error("La generación musical excedió el límite de 60s.")
            } catch (e: QuotaExceededException) {
                _musicGenState.value = MediaGenState.Error("Cuota de generación musical alcanzada.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Music generation failed", e)
                _musicGenState.value = MediaGenState.Error(e.localizedMessage ?: "Error al generar música.")
            }
        }
    }

    fun transcribeAudioFile(audioFile: File) {
        viewModelScope.launch {
            _transcriptGenState.value = MediaGenState.Loading
            try {
                val bytes = audioFile.readBytes()
                val (transcript, modelUsed) = aiService.transcribeAudio(bytes, "audio/mp4")
                _transcriptGenState.value = MediaGenState.Success(transcript)
                repository.saveAiTurn(
                    role = "model",
                    mode = "transcribe",
                    text = transcript,
                    modelUsed = modelUsed,
                    mediaSummary = "Transcripción de voz"
                )
            } catch (e: TimeoutCancellationException) {
                _transcriptGenState.value = MediaGenState.Error("La transcripción excedió el tiempo límite.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                _transcriptGenState.value = MediaGenState.Error(e.localizedMessage ?: "Error al transcribir audio.")
            } finally {
                runCatching { audioFile.delete() }
            }
        }
    }

    companion object {
        fun provideFactory(
            aiService: FacuAiService,
            repository: MessengerDataSource,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AiStudioViewModel(
                    aiService = aiService,
                    repository = repository,
                    currentUserId = currentUserId
                )
            }
        }
    }
}
