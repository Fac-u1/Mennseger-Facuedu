package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.components.E2eeKeyShareCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityProfileScreen(
    uid: String,
    displayName: String,
    email: String?,
    photoUrl: String?,
    publicKeyFingerprint: String,
    publicKeyBase64: String,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    isGuestMode: Boolean = false,
    onConnectGoogle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Seguridad & Perfil", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Foto de perfil de $displayName",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayName.take(2).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayName, style = MaterialTheme.typography.titleMedium)
                        if (!email.isNullOrBlank()) {
                            Text(
                                email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (isGuestMode) {
                                "Cuenta Invitado Efímera · Se borra al cerrar la app"
                            } else {
                                "Cuenta verificada con Firebase Auth"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isGuestMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Tu UID para compartir chats E2EE", style = MaterialTheme.typography.titleSmall)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uid,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(uid))
                                    Toast.makeText(context, "UID copiado", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar UID")
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text("Huella ECDH P-256 local", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        text = publicKeyFingerprint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Clave pública: ${publicKeyBase64.take(48)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            E2eeKeyShareCard(
                uid = uid,
                displayName = displayName,
                publicKeyFingerprint = publicKeyFingerprint,
                publicKeyBase64 = publicKeyBase64
            )

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.DarkMode, contentDescription = null)
                        Column {
                            Text("Modo oscuro Midnight Signal", style = MaterialTheme.typography.titleSmall)
                            Text("Tema visual recomendado", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onToggleTheme,
                        modifier = Modifier.testTag("theme_toggle_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isGuestMode && onConnectGoogle != null) {
                Button(
                    onClick = onConnectGoogle,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("connect_google_from_guest_button")
                ) {
                    Text("Iniciar con cuenta permanente de Google")
                }
            }

            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isGuestMode) "Eliminar cuenta invitado y salir" else "Cerrar sesión")
            }
        }
    }
}
