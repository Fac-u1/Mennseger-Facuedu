package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID

private const val TAG = "FacuAiService"

enum class ChatModelTier(val modelId: String, val label: String, val subtitle: String) {
    GENERAL("gemini-3.8-flash", "FacuIA Rápida", "gemini-3.8-flash · Uso diario"),
    FAST("gemini-3.5-flash-lite", "Ultra Ligera", "gemini-3.5-flash-lite · Frases instantáneas"),
    DEEP("gemini-4-argon", "Razonamiento Profundo", "gemini-4-argon · Análisis complejo")
}

enum class PhraseTone(val label: String, val instruction: String) {
    FORMAL("Profesional", "Reescribe el mensaje en español con un tono formal, respetuoso y profesional, listo para enviar en un chat laboral."),
    CASUAL("Cercano / Rioplatense", "Reescribe el mensaje en español rioplatense natural, cálido y amigable, manteniendo el significado."),
    CONCISE("Más corto", "Resume y acorta el mensaje al mínimo indispensable sin perder la idea clave."),
    PERSUASIVE("Convincente", "Reescribe el mensaje para que suene claro, seguro y persuasivo."),
    ENGLISH("Traducir a Inglés", "Translate this message accurately into natural, idiomatic English."),
    FIX_GRAMMAR("Corregir ortografía", "Corrige ortografía, tildes y puntuación manteniendo exactamente el tono original.")
}

data class GeneratedAudioResult(
    val file: File,
    val mimeType: String,
    val modelUsed: String,
    val notes: String
)

data class GeneratedVideoResult(
    val file: File?,
    val previewBitmap: Bitmap?,
    val mimeType: String,
    val modelUsed: String,
    val notes: String
)

class FacuAiService(private val context: Context) {

    private val systemPrompt = content {
        text(
            "Sos FacuIA, el asistente inteligente integrado dentro de Facuedu.uy Mennseger. " +
                "Ayudás a los usuarios a chatear, reescribir frases, resumir documentos, traducir idiomas, " +
                "redactar respuestas inteligentes y crear prompts o contenido multimedia. " +
                "Respondé siempre en español claro y amable (salvo que pidan otro idioma), de forma concisa y útil."
        )
    }

    private val generalModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ChatModelTier.GENERAL.modelId,
            systemInstruction = systemPrompt
        )
    }

    private val fastModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ChatModelTier.FAST.modelId,
            systemInstruction = systemPrompt
        )
    }

    private val deepModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ChatModelTier.DEEP.modelId,
            systemInstruction = systemPrompt
        )
    }

    fun streamChatReply(
        history: List<Pair<String, String>>,
        userMessage: String,
        tier: ChatModelTier = ChatModelTier.GENERAL
    ): Flow<String> = flow {
        val model = when (tier) {
            ChatModelTier.GENERAL -> generalModel
            ChatModelTier.FAST -> fastModel
            ChatModelTier.DEEP -> deepModel
        }
        val chatHistory = history.takeLast(12).map { (role, text) ->
            content(role = role) { text(text) }
        }
        val chat = model.startChat(history = chatHistory)
        val buffer = StringBuilder()
        withTimeout(60_000L) {
            chat.sendMessageStream(userMessage).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    buffer.append(part)
                    emit(buffer.toString())
                }
            }
        }
        if (buffer.isEmpty()) {
            throw IllegalStateException("El asistente no devolvió texto en la respuesta.")
        }
    }

    suspend fun rewritePhrase(
        originalPhrase: String,
        tone: PhraseTone
    ): String {
        val prompt = "${tone.instruction}\n\nDevuelve únicamente la frase reescrita, sin comillas ni explicaciones.\n\nMensaje original:\n$originalPhrase"
        return withTimeout(45_000L) {
            val response = fastModel.generateContent(prompt)
            response.text?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("No se pudo generar la variación de la frase.")
        }
    }

    suspend fun summarizeDocumentText(
        fileName: String,
        mimeType: String,
        rawBytes: ByteArray
    ): String {
        return withTimeout(60_000L) {
            val response = if (mimeType.startsWith("text/") || mimeType == "application/json") {
                val preview = String(rawBytes.take(30_000).toByteArray(), Charsets.UTF_8)
                generalModel.generateContent(
                    "Resume en 3 puntos clave este documento ($fileName):\n\n$preview"
                )
            } else {
                generalModel.generateContent(
                    content {
                        inlineData(rawBytes, mimeType)
                        text("Analiza y resume los puntos principales de este documento llamado '$fileName'.")
                    }
                )
            }
            response.text?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("No se pudo analizar el documento.")
        }
    }

    suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String = "audio/mp4"): Pair<String, String> {
        val primaryModelName = "gemini-3.5-transcribe"
        val fallbackModelName = ChatModelTier.GENERAL.modelId
        val promptContent = content {
            inlineData(audioBytes, mimeType)
            text("Transcribe exactamente lo que se dice en este audio en su idioma original. Devuelve solo la transcripción.")
        }
        return withTimeout(60_000L) {
            try {
                val model = Firebase.ai.generativeModel(primaryModelName)
                val response = model.generateContent(promptContent)
                val text = response.text?.trim().orEmpty()
                if (text.isBlank()) throw IllegalStateException("Transcripción vacía")
                text to primaryModelName
            } catch (e: CancellationException) {
                throw e
            } catch (primaryErr: Exception) {
                Log.w(TAG, "Primary transcribe model ($primaryModelName) failed, trying $fallbackModelName", primaryErr)
                val fallback = Firebase.ai.generativeModel(fallbackModelName)
                val response = fallback.generateContent(promptContent)
                val text = response.text?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException(primaryErr.localizedMessage ?: "Error al transcribir el audio.")
                text to fallbackModelName
            }
        }
    }

    suspend fun generateOrEditImage(
        prompt: String,
        sourceBitmap: Bitmap? = null,
        aspectRatio: AspectRatio = AspectRatio.SQUARE_1x1
    ): Bitmap {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(aspectRatio)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }
        val imageModel = Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = config
        )
        return withTimeout(60_000L) {
            val response = if (sourceBitmap != null) {
                imageModel.generateContent(
                    content {
                        image(sourceBitmap)
                        text(prompt)
                    }
                )
            } else {
                imageModel.generateContent(prompt)
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            imagePart?.image ?: throw IllegalStateException(
                response.text?.takeIf { it.isNotBlank() }
                    ?: "El modelo de imagen no devolvió ninguna imagen."
            )
        }
    }

    suspend fun animateImageToVideo(
        sourceBitmap: Bitmap,
        motionPrompt: String,
        aspectRatioLabel: String
    ): GeneratedVideoResult {
        val modelName = "gemini-omni-1.2-flash"
        val videoModel = Firebase.ai.generativeModel(modelName = modelName)
        return withTimeout(60_000L) {
            val response = videoModel.generateContent(
                content {
                    image(sourceBitmap)
                    text(
                        "Animate this image into a smooth $aspectRatioLabel video clip. " +
                            "Motion instruction: $motionPrompt"
                    )
                }
            )
            val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
            val videoPart = parts.filterIsInstance<InlineDataPart>()
                .firstOrNull { it.mimeType.startsWith("video/") }
            val imageFallback = parts.filterIsInstance<ImagePart>().firstOrNull()?.image
            val textNotes = parts.filterIsInstance<TextPart>().joinToString("\n") { it.text }.trim()

            if (videoPart != null) {
                val outFile = File(context.cacheDir, "facuia_video_${UUID.randomUUID()}.mp4")
                outFile.writeBytes(videoPart.inlineData)
                GeneratedVideoResult(
                    file = outFile,
                    previewBitmap = imageFallback ?: sourceBitmap,
                    mimeType = videoPart.mimeType,
                    modelUsed = modelName,
                    notes = textNotes.ifBlank { "Video generado ($aspectRatioLabel) con $modelName" }
                )
            } else if (imageFallback != null) {
                GeneratedVideoResult(
                    file = null,
                    previewBitmap = imageFallback,
                    mimeType = "image/png",
                    modelUsed = modelName,
                    notes = textNotes.ifBlank { "Fotograma clave generado por $modelName" }
                )
            } else {
                throw IllegalStateException(
                    textNotes.ifBlank { "El modelo $modelName no devolvió flujo de video binario." }
                )
            }
        }
    }

    suspend fun generateMusic(
        musicPrompt: String,
        fullLength: Boolean
    ): GeneratedAudioResult {
        val modelName = if (fullLength) "lyria-3-pro-preview" else "lyria-3-clip-preview"
        val musicModel = Firebase.ai.generativeModel(modelName = modelName)
        return withTimeout(60_000L) {
            val response = musicModel.generateContent(
                content {
                    text(
                        if (fullLength) {
                            "Compose a full-length instrumental music track: $musicPrompt"
                        } else {
                            "Compose a 30-second music clip: $musicPrompt"
                        }
                    )
                }
            )
            val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
            val audioPart = parts.filterIsInstance<InlineDataPart>()
                .firstOrNull { it.mimeType.startsWith("audio/") }
            val textNotes = parts.filterIsInstance<TextPart>().joinToString("\n") { it.text }.trim()

            if (audioPart != null) {
                val ext = when {
                    audioPart.mimeType.contains("wav") -> "wav"
                    audioPart.mimeType.contains("ogg") -> "ogg"
                    else -> "mp3"
                }
                val outFile = File(context.cacheDir, "facuia_music_${UUID.randomUUID()}.$ext")
                outFile.writeBytes(audioPart.inlineData)
                GeneratedAudioResult(
                    file = outFile,
                    mimeType = audioPart.mimeType,
                    modelUsed = modelName,
                    notes = textNotes.ifBlank { "Pista musical generada con $modelName" }
                )
            } else {
                throw IllegalStateException(
                    textNotes.ifBlank { "El modelo $modelName no devolvió audio generado." }
                )
            }
        }
    }
}
