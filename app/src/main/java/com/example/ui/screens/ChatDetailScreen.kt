package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.PhraseTone
import com.example.media.AudioRecorderHelper
import com.example.media.DocumentHelper
import com.example.media.PickedDocument
import com.example.ui.viewmodel.DecryptedChatItem
import com.example.ui.viewmodel.DecryptedMessageItem
import com.example.ui.viewmodel.PhraseRewriteUiState
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.VoiceTranscriptUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatItem: DecryptedChatItem,
    messagesState: UiState<List<DecryptedMessageItem>>,
    currentUserId: String,
    composerText: String,
    phraseRewriteState: PhraseRewriteUiState,
    voiceTranscriptState: VoiceTranscriptUiState,
    docSummaryState: Pair<String, String>?,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onComposerChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSendDocument: (PickedDocument) -> Unit,
    onSummarizeDocument: (String, String, ByteArray) -> Unit,
    onDismissDocSummary: () -> Unit,
    onRewritePhrase: (PhraseTone) -> Unit,
    onApplyRewrite: () -> Unit,
    onDismissRewrite: () -> Unit,
    onRecordingStateChanged: (Boolean) -> Unit,
    onTranscribeAudioFile: (File) -> Unit,
    onStartCall: (String) -> Unit
) {
    if (showBackButton) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showToneBar by remember { mutableStateOf(false) }
    var showE2eeInspector by remember { mutableStateOf(false) }
    var inspectedMessage by remember { mutableStateOf<DecryptedMessageItem?>(null) }

    val audioRecorder = remember { AudioRecorderHelper(context) }
    DisposableEffect(Unit) {
        onDispose { audioRecorder.stopAndRelease() }
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runCatching {
                audioRecorder.startRecording()
                onRecordingStateChanged(true)
            }.onFailure {
                Toast.makeText(context, "No se pudo iniciar el micrófono.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permiso de micrófono requerido para dictar.", Toast.LENGTH_SHORT).show()
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val doc = DocumentHelper.readDocument(context, uri)
            if (doc != null) {
                onSendDocument(doc)
            } else {
                Toast.makeText(context, "No se pudo leer el documento.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val doc = DocumentHelper.readDocument(context, uri)
            if (doc != null) {
                onSendDocument(doc)
            }
        }
    }

    val messagesList = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver a chats"
                            )
                        }
                    }
                },
                title = {
                    Column(
                        modifier = Modifier.clickable { showE2eeInspector = true }
                    ) {
                        Text(
                            text = chatItem.chat.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = "Verificar cifrado",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "E2EE AES-256-GCM · Tocar para verificar",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onStartCall("audio") },
                        modifier = Modifier.testTag("start_audio_call_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Call, contentDescription = "Llamada de voz")
                    }
                    IconButton(
                        onClick = { onStartCall("video") },
                        modifier = Modifier.testTag("start_video_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = "Videollamada",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // Meta-IA Phrase Rewrite Preview Card
                AnimatedVisibility(visible = phraseRewriteState !is PhraseRewriteUiState.Idle) {
                    RewritePreviewCard(
                        state = phraseRewriteState,
                        onApply = onApplyRewrite,
                        onDismiss = onDismissRewrite
                    )
                }

                // Tone Chips Bar for "Cambiar frases"
                AnimatedVisibility(visible = showToneBar) {
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(PhraseTone.entries) { tone ->
                            AssistChip(
                                onClick = { onRewritePhrase(tone) },
                                label = { Text(tone.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.AutoFixHigh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                // Voice Transcription Status Indicator
                if (voiceTranscriptState is VoiceTranscriptUiState.Recording ||
                    voiceTranscriptState is VoiceTranscriptUiState.Transcribing
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = if (voiceTranscriptState is VoiceTranscriptUiState.Recording) {
                                    "Grabando voz... Tocá Stop para transcribir con FacuIA"
                                } else {
                                    "Transcribiendo audio con Gemini..."
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { docPickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("attach_document_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = "Adjuntar documento cifrado"
                        )
                    }
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("attach_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = "Enviar foto cifrada"
                        )
                    }
                    IconButton(
                        onClick = { showToneBar = !showToneBar },
                        modifier = Modifier.testTag("rewrite_phrase_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoFixHigh,
                            contentDescription = "Cambiar frase con FacuIA",
                            tint = if (showToneBar) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = composerText,
                        onValueChange = onComposerChange,
                        placeholder = { Text("Mensaje cifrado...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_composer_input")
                    )

                    if (composerText.isBlank()) {
                        val isRecording = voiceTranscriptState is VoiceTranscriptUiState.Recording
                        IconButton(
                            onClick = {
                                if (isRecording) {
                                    onRecordingStateChanged(false)
                                    val file = audioRecorder.stopRecording()
                                    if (file != null) {
                                        onTranscribeAudioFile(file)
                                    }
                                } else {
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        runCatching {
                                            audioRecorder.startRecording()
                                            onRecordingStateChanged(true)
                                        }
                                    } else {
                                        audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRecording) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.secondaryContainer
                                )
                                .testTag("voice_transcribe_button")
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = if (isRecording) "Detener grabación" else "Dictar mensaje por voz",
                                tint = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onSendMessage,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar mensaje cifrado",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (messagesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = messagesState.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is UiState.Success -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Los mensajes y documentos de este chat se cifran localmente con AES-256-GCM antes de subir a Firebase. Código: ${chatItem.safetyNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        items(messagesList, key = { it.message.id }) { item ->
                            val isMine = item.message.senderId == currentUserId
                            EncryptedMessageBubble(
                                item = item,
                                isMine = isMine,
                                onInspectCipher = { inspectedMessage = item },
                                onOpenDocument = { docBytes ->
                                    val intent = DocumentHelper.saveAndOpenDecryptedDocument(
                                        context,
                                        item.message.docName,
                                        item.message.docMime.ifBlank { "*/*" },
                                        docBytes
                                    )
                                    if (intent != null) {
                                        runCatching { context.startActivity(intent) }
                                            .onFailure {
                                                Toast.makeText(
                                                    context,
                                                    "No hay app instalada para abrir ${item.message.docMime}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    }
                                },
                                onSummarizeDocument = { docBytes ->
                                    onSummarizeDocument(
                                        item.message.docName,
                                        item.message.docMime.ifBlank { "text/plain" },
                                        docBytes
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showE2eeInspector) {
        val latest = messagesList.lastOrNull()
        AlertDialog(
            onDismissRequest = { showE2eeInspector = false },
            title = { Text("Seguridad E2EE verificada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Algoritmo: ${chatItem.chat.encryptionAlgo}", style = MaterialTheme.typography.labelLarge)
                    Text("Número de seguridad:", style = MaterialTheme.typography.labelMedium)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = chatItem.safetyNumber,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    if (latest != null) {
                        Text("Último mensaje en Firestore (cifrado):", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "IV: ${latest.message.iv}\nCipher: ${latest.message.ciphertext.take(90)}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showE2eeInspector = false }) { Text("Cerrar") }
            }
        )
    }

    if (inspectedMessage != null) {
        val msg = inspectedMessage!!
        AlertDialog(
            onDismissRequest = { inspectedMessage = null },
            title = { Text("Inspector Criptográfico") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Texto descifrado:", style = MaterialTheme.typography.labelMedium)
                    Text(msg.decryptedText, style = MaterialTheme.typography.bodyMedium)
                    Text("IV (12 bytes Base64):", style = MaterialTheme.typography.labelMedium)
                    Text(msg.message.iv, style = MaterialTheme.typography.labelSmall)
                    Text("Ciphertext almacenado:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        msg.message.ciphertext.take(180),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedMessage = null }) { Text("Entendido") }
            }
        )
    }

    if (docSummaryState != null) {
        AlertDialog(
            onDismissRequest = onDismissDocSummary,
            title = { Text("FacuIA · ${docSummaryState.first}") },
            text = { Text(docSummaryState.second, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = onDismissDocSummary) { Text("Listo") }
            }
        )
    }
}

@Composable
private fun RewritePreviewCard(
    state: PhraseRewriteUiState,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        when (state) {
            is PhraseRewriteUiState.Loading -> {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("FacuIA está transformando tu frase...", style = MaterialTheme.typography.bodySmall)
                }
            }
            is PhraseRewriteUiState.Ready -> {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Sugerencia (${state.tone.label}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = state.rewritten, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onApply,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Usar frase")
                        }
                        TextButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Descartar")
                        }
                    }
                }
            }
            is PhraseRewriteUiState.Error -> {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            }
            PhraseRewriteUiState.Idle -> Unit
        }
    }
}

@Composable
private fun EncryptedMessageBubble(
    item: DecryptedMessageItem,
    isMine: Boolean,
    onInspectCipher: () -> Unit,
    onOpenDocument: (ByteArray) -> Unit,
    onSummarizeDocument: (ByteArray) -> Unit
) {
    val msg = item.message
    val timeText = remember(msg.createdAt) {
        val date = msg.createdAt?.toDate()
        if (date != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(date) else "..."
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isMine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isMine) 20.dp else 6.dp,
                bottomEnd = if (isMine) 6.dp else 20.dp
            ),
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clickable(onClick = onInspectCipher)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isMine) {
                    Text(
                        text = msg.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                if (msg.messageType == "document") {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Description,
                                    contentDescription = "Documento cifrado",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = msg.docName.ifBlank { "Documento cifrado" },
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${DocumentHelper.formatFileSize(msg.docSizeBytes)} · ${msg.docMime}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (msg.docChecksum.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "SHA-256: ${msg.docChecksum.take(16)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (item.decryptedDocBytes != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onOpenDocument(item.decryptedDocBytes) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Abrir", style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = { onSummarizeDocument(item.decryptedDocBytes) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Resumir IA", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = item.decryptedText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
