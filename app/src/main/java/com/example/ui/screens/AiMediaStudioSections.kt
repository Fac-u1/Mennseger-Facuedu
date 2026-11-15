package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ai.GeneratedAudioResult
import com.example.ai.GeneratedVideoResult
import com.example.ai.PhraseTone
import com.example.media.AudioRecorderHelper
import com.example.ui.viewmodel.MediaGenState
import com.google.firebase.ai.type.AspectRatio
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhraseRewriterStudioSection(
    state: MediaGenState<String>,
    onRewrite: (String, PhraseTone) -> Unit,
    onSendToActiveChat: ((String) -> Unit)?
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var sourceText by remember { mutableStateOf("") }
    var selectedTone by remember { mutableStateOf(PhraseTone.FORMAL) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Cambiador de frases (estilo Meta IA)",
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = sourceText,
            onValueChange = { sourceText = it },
            label = { Text("Escribí o pegá una frase para transformar") },
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rewrite_source_input")
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PhraseTone.entries.forEach { tone ->
                FilterChip(
                    selected = selectedTone == tone,
                    onClick = { selectedTone = tone },
                    label = { Text(tone.label) }
                )
            }
        }
        Button(
            onClick = { onRewrite(sourceText, selectedTone) },
            enabled = sourceText.isNotBlank() && state !is MediaGenState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rewrite_execute_button")
        ) {
            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Transformar frase con Gemini")
        }

        when (state) {
            is MediaGenState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Reescribiendo con gemini-3.5-flash-lite...")
                }
            }
            is MediaGenState.Success -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(state.data, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(state.data))
                                Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Copiar")
                            }
                            if (onSendToActiveChat != null) {
                                Button(onClick = { onSendToActiveChat(state.data) }) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Enviar al chat")
                                }
                            }
                        }
                    }
                }
            }
            is MediaGenState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            MediaGenState.Idle -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageStudioSection(
    state: MediaGenState<Bitmap>,
    onGenerateOrEdit: (String, Bitmap?, AspectRatio) -> Unit,
    onShareBitmapToChat: ((Bitmap, String) -> Unit)?
) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var aspectLabel by remember { mutableStateOf("1:1") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedBitmap = decodeBitmapFromUri(context, uri)
        }
    }

    val aspectRatio = when (aspectLabel) {
        "16:9" -> AspectRatio.LANDSCAPE_16x9
        "9:16" -> AspectRatio.PORTRAIT_9x16
        "4:3" -> AspectRatio.LANDSCAPE_4x3
        else -> AspectRatio.SQUARE_1x1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Crear o editar fotos (gemini-nano-banana-2.1)",
            style = MaterialTheme.typography.titleMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (selectedBitmap == null) "Subir foto base (opcional)" else "Cambiar foto base")
            }
            if (selectedBitmap != null) {
                OutlinedButton(onClick = { selectedBitmap = null }) {
                    Text("Quitar foto")
                }
            }
        }

        if (selectedBitmap != null) {
            Image(
                bitmap = selectedBitmap!!.asImageBitmap(),
                contentDescription = "Foto base seleccionada",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1:1", "16:9", "9:16", "4:3").forEach { ratio ->
                FilterChip(
                    selected = aspectLabel == ratio,
                    onClick = { aspectLabel = ratio },
                    label = { Text(ratio) }
                )
            }
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = {
                Text(
                    if (selectedBitmap == null) "Describe la foto que querés crear"
                    else "Instrucciones de edición sobre la foto"
                )
            },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("image_prompt_input")
        )

        Button(
            onClick = { onGenerateOrEdit(prompt, selectedBitmap, aspectRatio) },
            enabled = prompt.isNotBlank() && state !is MediaGenState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("generate_image_button")
        ) {
            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (selectedBitmap == null) "Generar Foto IA" else "Editar Foto IA")
        }

        when (state) {
            is MediaGenState.Loading -> {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Sintetizando imagen (30–60s)...")
                    }
                }
            }
            is MediaGenState.Success -> {
                Image(
                    bitmap = state.data.asImageBitmap(),
                    contentDescription = "Imagen generada por FacuIA",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Fit
                )
                if (onShareBitmapToChat != null) {
                    Button(
                        onClick = { onShareBitmapToChat(state.data, "FacuIA_${System.currentTimeMillis()}") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enviar foto cifrada al chat activo")
                    }
                }
            }
            is MediaGenState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            MediaGenState.Idle -> Unit
        }
    }
}

@Composable
fun VideoStudioSection(
    state: MediaGenState<GeneratedVideoResult>,
    onAnimateVideo: (Bitmap, String, String) -> Unit
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var motionPrompt by remember { mutableStateOf("") }
    var aspectRatio by remember { mutableStateOf("16:9") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            sourceBitmap = decodeBitmapFromUri(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Animar foto en video (gemini-omni-1.2-flash)",
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedButton(
            onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pick_video_source_button")
        ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (sourceBitmap == null) "Seleccionar foto para animar" else "Cambiar foto seleccionada")
        }

        if (sourceBitmap != null) {
            Image(
                bitmap = sourceBitmap!!.asImageBitmap(),
                contentDescription = "Foto origen para video",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = aspectRatio == "16:9",
                onClick = { aspectRatio = "16:9" },
                label = { Text("Horizontal 16:9") }
            )
            FilterChip(
                selected = aspectRatio == "9:16",
                onClick = { aspectRatio = "9:16" },
                label = { Text("Vertical 9:16") }
            )
        }

        OutlinedTextField(
            value = motionPrompt,
            onValueChange = { motionPrompt = it },
            label = { Text("Describe el movimiento de cámara o escena") },
            minLines = 2,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("video_prompt_input")
        )

        Button(
            onClick = {
                sourceBitmap?.let { onAnimateVideo(it, motionPrompt, aspectRatio) }
            },
            enabled = sourceBitmap != null && state !is MediaGenState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("generate_video_button")
        ) {
            Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generar Video IA")
        }

        when (state) {
            is MediaGenState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text("Renderizando animación de video...")
                }
            }
            is MediaGenState.Success -> {
                val res = state.data
                if (res.file != null) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setVideoURI(Uri.fromFile(res.file))
                                start()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                } else if (res.previewBitmap != null) {
                    Image(
                        bitmap = res.previewBitmap.asImageBitmap(),
                        contentDescription = "Resultado visual de video",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                Text(res.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is MediaGenState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            MediaGenState.Idle -> Unit
        }
    }
}

@Composable
fun MusicStudioSection(
    state: MediaGenState<GeneratedAudioResult>,
    onGenerateMusic: (String, Boolean) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var fullLength by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                player.value?.stop()
                player.value?.release()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Generador de música (Lyria 3)",
            style = MaterialTheme.typography.titleMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = !fullLength,
                onClick = { fullLength = false },
                label = { Text("Clip 30s (lyria-3-clip-preview)") }
            )
            FilterChip(
                selected = fullLength,
                onClick = { fullLength = true },
                label = { Text("Pista Pro (lyria-3-pro-preview)") }
            )
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Estilo, instrumentos, ritmo o estado de ánimo") },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("music_prompt_input")
        )
        Button(
            onClick = { onGenerateMusic(prompt, fullLength) },
            enabled = prompt.isNotBlank() && state !is MediaGenState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("generate_music_button")
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Componer Música IA")
        }

        when (state) {
            is MediaGenState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text("Componiendo pista musical...")
                }
            }
            is MediaGenState.Success -> {
                val audio = state.data
                DisposableEffect(audio.file.absolutePath) {
                    onDispose {
                        runCatching {
                            player.value?.stop()
                            player.value?.release()
                        }
                        player.value = null
                        isPlaying = false
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isPlaying) {
                                    runCatching { player.value?.pause() }
                                    isPlaying = false
                                } else {
                                    runCatching {
                                        if (player.value == null) {
                                            player.value = MediaPlayer().apply {
                                                setDataSource(audio.file.absolutePath)
                                                setOnCompletionListener { isPlaying = false }
                                                prepare()
                                            }
                                        }
                                        player.value?.start()
                                        isPlaying = true
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir"
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pista lista (${audio.modelUsed})", style = MaterialTheme.typography.labelLarge)
                            Text(audio.notes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            is MediaGenState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            MediaGenState.Idle -> Unit
        }
    }
}

@Composable
fun AudioTranscribeStudioSection(
    state: MediaGenState<String>,
    onTranscribeFile: (File) -> Unit,
    onSendToActiveChat: ((String) -> Unit)?
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val recorder = remember { AudioRecorderHelper(context) }
    var isRecording by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { recorder.stopAndRelease() }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runCatching {
                recorder.startRecording()
                isRecording = true
            }
        } else {
            Toast.makeText(context, "Se requiere permiso de micrófono.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Transcribir audio con micrófono (gemini-3.5-transcribe)",
            style = MaterialTheme.typography.titleMedium
        )
        Button(
            onClick = {
                if (isRecording) {
                    isRecording = false
                    val file = recorder.stopRecording()
                    if (file != null) onTranscribeFile(file)
                } else {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        runCatching {
                            recorder.startRecording()
                            isRecording = true
                        }
                    } else {
                        permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("studio_record_transcribe_button")
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isRecording) "Detener y Transcribir" else "Grabar Nota de Voz")
        }

        if (isRecording) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("Escuchando micrófono...", color = MaterialTheme.colorScheme.error)
            }
        }

        when (state) {
            is MediaGenState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Transcribiendo audio...")
                }
            }
            is MediaGenState.Success -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(state.data, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(state.data))
                                Toast.makeText(context, "Transcripción copiada", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Copiar")
                            }
                            if (onSendToActiveChat != null) {
                                Button(onClick = { onSendToActiveChat(state.data) }) {
                                    Text("Enviar al chat")
                                }
                            }
                        }
                    }
                }
            }
            is MediaGenState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            MediaGenState.Idle -> Unit
        }
    }
}

private fun decodeBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}
