package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.ChatModelTier
import com.example.ui.viewmodel.AiStudioMode
import com.example.ui.viewmodel.AiStudioViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun AiStudioScreen(
    viewModel: AiStudioViewModel,
    hasActiveChat: Boolean,
    onSendTextToChat: (String) -> Unit,
    onSendBitmapToChat: (Bitmap, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val selectedTier by viewModel.selectedTier.collectAsStateWithLifecycle()
    val turnsState by viewModel.turnsState.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val isSending by viewModel.isChatSending.collectAsStateWithLifecycle()

    val rewriteState by viewModel.rewriteResult.collectAsStateWithLifecycle()
    val imageState by viewModel.imageGenState.collectAsStateWithLifecycle()
    val videoState by viewModel.videoGenState.collectAsStateWithLifecycle()
    val musicState by viewModel.musicGenState.collectAsStateWithLifecycle()
    val transcriptState by viewModel.transcriptGenState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // FacuIA Studio Banner Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_ai_studio_banner),
                    contentDescription = "Banner de FacuIA Studio",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xE60A1017),
                                    Color(0x990A1017)
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.primary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "FacuIA · Asistente Multimodal",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Chat · Frases · Fotos · Video · Música · Voz",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Mode Tabs
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AiStudioMode.entries) { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { viewModel.selectMode(mode) },
                        label = { Text(mode.label) },
                        modifier = Modifier.testTag("ai_mode_${mode.name.lowercase()}")
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedMode) {
                    AiStudioMode.CHAT -> {
                        val chatTurns = (turnsState as? UiState.Success)?.data.orEmpty()
                            .filter { it.mode == "chat" }
                        AiChatThreadSection(
                            turns = chatTurns,
                            streamingReply = streamingReply,
                            isSending = isSending,
                            errorMessage = chatError,
                            selectedTier = selectedTier,
                            onSelectTier = viewModel::selectModelTier,
                            onSendPrompt = viewModel::sendChatMessage
                        )
                    }
                    AiStudioMode.REWRITE -> PhraseRewriterStudioSection(
                        state = rewriteState,
                        onRewrite = viewModel::rewritePhrase,
                        onSendToActiveChat = if (hasActiveChat) onSendTextToChat else null
                    )
                    AiStudioMode.IMAGE -> ImageStudioSection(
                        state = imageState,
                        onGenerateOrEdit = viewModel::generateOrEditImage,
                        onShareBitmapToChat = if (hasActiveChat) onSendBitmapToChat else null
                    )
                    AiStudioMode.VIDEO -> VideoStudioSection(
                        state = videoState,
                        onAnimateVideo = viewModel::animateImageToVideo
                    )
                    AiStudioMode.MUSIC -> MusicStudioSection(
                        state = musicState,
                        onGenerateMusic = viewModel::generateMusic
                    )
                    AiStudioMode.TRANSCRIBE -> AudioTranscribeStudioSection(
                        state = transcriptState,
                        onTranscribeFile = viewModel::transcribeAudioFile,
                        onSendToActiveChat = if (hasActiveChat) onSendTextToChat else null
                    )
                }
            }
        }
    }
}

@Composable
private fun AiChatThreadSection(
    turns: List<com.example.data.model.AiAssistantTurn>,
    streamingReply: String?,
    isSending: Boolean,
    errorMessage: String?,
    selectedTier: ChatModelTier,
    onSelectTier: (ChatModelTier) -> Unit,
    onSendPrompt: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(turns.size, streamingReply) {
        val total = turns.size + if (streamingReply != null) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ChatModelTier.entries) { tier ->
                FilterChip(
                    selected = selectedTier == tier,
                    onClick = { onSelectTier(tier) },
                    label = { Text(tier.label) }
                )
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (turns.isEmpty() && streamingReply == null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Probá preguntarme algo como en Meta IA:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val starters = listOf(
                            "Escribime un mensaje cordial para reagendar una reunión",
                            "Dame 5 ideas de regalos creativos en Uruguay",
                            "Explicame qué es el cifrado AES-256-GCM en palabras simples"
                        )
                        starters.forEach { suggestion ->
                            AssistChip(
                                onClick = { onSendPrompt(suggestion) },
                                label = { Text(suggestion) }
                            )
                        }
                    }
                }
            }

            items(turns, key = { it.id }) { turn ->
                val isUser = turn.role == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (isUser) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (isUser) "Vos" else "FacuIA (${turn.modelUsed})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(turn.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (streamingReply != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "FacuIA respondiendo...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (streamingReply.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(streamingReply, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Preguntale a FacuIA...") },
                maxLines = 3,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_chat_input")
            )
            IconButton(
                onClick = {
                    val textToSend = input
                    input = ""
                    onSendPrompt(textToSend)
                },
                enabled = input.isNotBlank() && !isSending,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
                    .testTag("ai_chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar a FacuIA",
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
    }
}
