package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.viewmodel.ActiveCallUiState

private const val TAG = "VideoCallScreen"

@Composable
fun VideoCallScreen(
    callState: ActiveCallUiState,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchLens: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit
) {
    BackHandler(onBack = onEndCall)

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
    }

    LaunchedEffect(Unit) {
        val needed = buildList {
            if (!hasCameraPermission) add(Manifest.permission.CAMERA)
            if (!hasAudioPermission) add(Manifest.permission.RECORD_AUDIO)
        }
        if (needed.isNotEmpty()) {
            permLauncher.launch(needed.toTypedArray())
        }
    }

    val formattedTime = remember(callState.elapsedSeconds) {
        val mins = callState.elapsedSeconds / 60
        val secs = callState.elapsedSeconds % 60
        "%02d:%02d".format(mins, secs)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF070D14), Color(0xFF0F1E2E), Color(0xFF070D14))
                )
            )
    ) {
        // Real CameraX Live Feed when video enabled & permission granted
        if (callState.isCameraOn && hasCameraPermission) {
            CameraXLivePreview(
                useFrontCamera = callState.isFrontCamera,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(58.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = callState.peerName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (!hasCameraPermission && callState.callType == "video") {
                        "Permiso de cámara requerido para video en vivo"
                    } else {
                        "Cámara desactivada · Audio cifrado activo"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f)
                )
                if (!hasCameraPermission && callState.callType == "video") {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            permLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            )
                        }
                    ) {
                        Text("Habilitar cámara")
                    }
                }
            }
        }

        // Top Call Status Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "DTLS-SRTP Cifrado E2EE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = callState.peerName,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Bottom Frosted Call Control Dock
        Surface(
            color = Color(0xCC0D1622),
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 22.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlCircle(
                    icon = if (callState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (callState.isMuted) "Silenciado" else "Mic",
                    active = callState.isMuted,
                    onClick = onToggleMute,
                    tag = "call_mute_button"
                )
                CallControlCircle(
                    icon = if (callState.isCameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    label = "Cámara",
                    active = !callState.isCameraOn,
                    onClick = onToggleCamera,
                    tag = "call_camera_button"
                )
                CallControlCircle(
                    icon = Icons.Filled.Cameraswitch,
                    label = "Girar",
                    active = false,
                    onClick = onSwitchLens,
                    tag = "call_flip_button"
                )
                CallControlCircle(
                    icon = if (callState.isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    label = "Altavoz",
                    active = !callState.isSpeakerOn,
                    onClick = onToggleSpeaker,
                    tag = "call_speaker_button"
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                            .testTag("end_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = "Finalizar videollamada",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cortar",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControlCircle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    if (active) Color.White else Color.White.copy(alpha = 0.16f)
                )
                .testTag(tag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color(0xFF0A1017) else Color.White
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun CameraXLivePreview(
    useFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(useFrontCamera) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
            } catch (e: Exception) {
                Log.w(TAG, "CameraX bind failed (device may lack camera): ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
