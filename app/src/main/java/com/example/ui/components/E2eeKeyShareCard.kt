package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.media.SocialShareHelper
import com.example.media.SocialShareTarget
import com.example.ui.theme.SocialFacebook
import com.example.ui.theme.SocialInstagram
import com.example.ui.theme.SocialWhatsApp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun E2eeKeyShareCard(
    uid: String,
    displayName: String,
    publicKeyFingerprint: String,
    publicKeyBase64: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val shareLink = remember(uid, displayName, publicKeyFingerprint, publicKeyBase64) {
        SocialShareHelper.buildE2eeShareLink(
            uid = uid,
            displayName = displayName,
            publicKeyFingerprint = publicKeyFingerprint,
            publicKeyBase64 = publicKeyBase64
        )
    }

    val shareMessage = remember(displayName, publicKeyFingerprint, shareLink) {
        SocialShareHelper.buildE2eeShareMessage(
            displayName = displayName,
            publicKeyFingerprint = publicKeyFingerprint,
            link = shareLink
        )
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = "Clave E2EE",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Compartir clave E2EE con vínculo",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Enviá tu enlace cifrado ECDH P-256 para chatear al instante",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Huella: $publicKeyFingerprint",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = shareLink,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(shareLink))
                            Toast.makeText(context, "Vínculo E2EE copiado", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("copy_e2ee_link_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copiar vínculo E2EE"
                        )
                    }
                }
            }

            Text(
                text = "Compartir directamente en:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SocialTargetButton(
                    label = "WhatsApp",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    accentColor = SocialWhatsApp,
                    testTag = "share_e2ee_whatsapp",
                    onClick = {
                        SocialShareHelper.shareE2eeLink(
                            context = context,
                            target = SocialShareTarget.WHATSAPP,
                            link = shareLink,
                            shareMessage = shareMessage
                        )
                    }
                )
                SocialTargetButton(
                    label = "X",
                    icon = Icons.Filled.AlternateEmail,
                    accentColor = MaterialTheme.colorScheme.onSurface,
                    testTag = "share_e2ee_x",
                    onClick = {
                        SocialShareHelper.shareE2eeLink(
                            context = context,
                            target = SocialShareTarget.X,
                            link = shareLink,
                            shareMessage = shareMessage
                        )
                    }
                )
                SocialTargetButton(
                    label = "Instagram",
                    icon = Icons.Filled.CameraAlt,
                    accentColor = SocialInstagram,
                    testTag = "share_e2ee_instagram",
                    onClick = {
                        SocialShareHelper.shareE2eeLink(
                            context = context,
                            target = SocialShareTarget.INSTAGRAM,
                            link = shareLink,
                            shareMessage = shareMessage
                        )
                    }
                )
                SocialTargetButton(
                    label = "Facebook",
                    icon = Icons.Filled.Public,
                    accentColor = SocialFacebook,
                    testTag = "share_e2ee_facebook",
                    onClick = {
                        SocialShareHelper.shareE2eeLink(
                            context = context,
                            target = SocialShareTarget.FACEBOOK,
                            link = shareLink,
                            shareMessage = shareMessage
                        )
                    }
                )
                SocialTargetButton(
                    label = "Más",
                    icon = Icons.Filled.MoreHoriz,
                    accentColor = MaterialTheme.colorScheme.primary,
                    testTag = "share_e2ee_more",
                    onClick = {
                        SocialShareHelper.shareE2eeLink(
                            context = context,
                            target = SocialShareTarget.MORE,
                            link = shareLink,
                            shareMessage = shareMessage
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Button(
                onClick = {
                    SocialShareHelper.openSystemShareSheet(context, shareMessage)
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("share_e2ee_system_sheet_button")
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Abrir hoja de compartir del sistema")
            }
        }
    }
}

@Composable
private fun SocialTargetButton(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
