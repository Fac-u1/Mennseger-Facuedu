package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.media.DocumentHelper
import com.example.media.InstallerPlatformFilter
import com.example.media.ZipInstallerEntry
import com.example.media.ZipInstallerExtractor
import com.example.media.ZipScanResult

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZipConverterCard(
    activeFilter: InstallerPlatformFilter,
    onSelectFilter: (InstallerPlatformFilter) -> Unit,
    onPickZip: (InstallerPlatformFilter) -> Unit,
    selectedZipUri: Uri?,
    scanResult: ZipScanResult?,
    isScanningZip: Boolean,
    extractingEntryPath: String?,
    statusMessage: String?,
    errorMessage: String?,
    onSaveInstaller: (ZipInstallerEntry) -> Unit,
    onShareInstaller: (ZipInstallerEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("zip_converter_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Transform,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Conversor .ZIP → Instalador",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Extraé .exe, .dmg, .pkg, .AppImage, .deb, .apk o .ipa desde un .zip descargado de GitHub Actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                InstallerPlatformFilter.entries.forEach { filterOption ->
                    val label = when (filterOption) {
                        InstallerPlatformFilter.ALL -> "Todos"
                        InstallerPlatformFilter.WINDOWS -> ".ZIP → .EXE"
                        InstallerPlatformFilter.MACOS -> ".ZIP → .DMG/.PKG"
                        InstallerPlatformFilter.LINUX_UBUNTU -> ".ZIP → .AppImage/.DEB"
                        InstallerPlatformFilter.ANDROID -> ".ZIP → .APK"
                        InstallerPlatformFilter.IOS -> ".ZIP → .IPA"
                    }
                    FilterChip(
                        selected = activeFilter == filterOption,
                        onClick = { onSelectFilter(filterOption) },
                        label = { Text(label) }
                    )
                }
            }

            Button(
                onClick = { onPickZip(activeFilter) },
                enabled = !isScanningZip && extractingEntryPath == null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("pick_any_zip_button")
            ) {
                if (isScanningZip) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analizando archivo .ZIP...")
                } else {
                    Icon(Icons.Filled.FolderZip, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (activeFilter == InstallerPlatformFilter.ALL) {
                            "Elegir .ZIP y convertir a instalador"
                        } else {
                            "Elegir .ZIP para extraer ${activeFilter.badgeText}"
                        }
                    )
                }
            }

            if (statusMessage != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (scanResult != null && selectedZipUri != null) {
                val displayEntries = scanResult.matchingInstallers.ifEmpty { scanResult.allInstallersInZip }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Archivo: ${scanResult.archiveName} (${scanResult.totalEntriesScanned} archivos analizados)",
                            style = MaterialTheme.typography.labelMedium
                        )

                        if (displayEntries.isEmpty()) {
                            Text(
                                text = "Este .ZIP no contiene instaladores (.exe, .dmg, .pkg, .AppImage, .deb, .apk, .ipa). Si descargaste el código fuente, usá el artefacto compilado de GitHub Actions o el enlace directo de Releases.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            if (scanResult.matchingInstallers.isEmpty()) {
                                Text(
                                    text = "No se encontró ${activeFilter.badgeText}, pero se detectaron otros instaladores en este .ZIP:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            displayEntries.forEach { entry ->
                                val badge = ZipInstallerExtractor.formatExtensionBadge(entry.extension)
                                val isBusy = extractingEntryPath == entry.entryPath
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = entry.fileName,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                )
                                                Text(
                                                    text = "${entry.platformLabel} · ${DocumentHelper.formatFileSize(entry.sizeBytes)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            AssistChip(
                                                onClick = {
                                                    Toast.makeText(
                                                        context,
                                                        "Formato listo: $badge",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                label = { Text(badge) }
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Button(
                                                onClick = { onSaveInstaller(entry) },
                                                enabled = extractingEntryPath == null,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("save_extracted_${entry.extension}_button")
                                            ) {
                                                if (isBusy) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Filled.Download,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Guardar $badge")
                                            }

                                            OutlinedButton(
                                                onClick = { onShareInstaller(entry) },
                                                enabled = extractingEntryPath == null,
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("share_extracted_${entry.extension}_button")
                                            ) {
                                                Icon(
                                                    Icons.Filled.Share,
                                                    contentDescription = "Compartir ${entry.fileName}",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
