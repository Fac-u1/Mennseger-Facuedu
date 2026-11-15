package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.LaptopWindows
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.media.ApkExportHelper
import com.example.media.DocumentHelper
import com.example.media.InstalledApkInfo
import kotlinx.coroutines.launch

private const val PREFS_BUILDS = "facuedu_builds_portal"
private const val KEY_GITHUB_REPO = "github_repo_slug"
private val REPO_SLUG_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

private data class PlatformBuildTarget(
    val id: String,
    val title: String,
    val subtitle: String,
    val formats: List<String>,
    val workflowJob: String,
    val artifactName: String,
    val icon: ImageVector,
    val isLocalApkSupported: Boolean = false
)

private fun normalizeRepoSlug(raw: String): String {
    return raw.trim()
        .removePrefix("https://github.com/")
        .removePrefix("http://github.com/")
        .removeSuffix(".git")
        .trim('/')
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BuildsPortalScreen(
    isGuestMode: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var repoInput by remember {
        mutableStateOf(
            if (isGuestMode) {
                ""
            } else {
                context.getSharedPreferences(PREFS_BUILDS, Context.MODE_PRIVATE)
                    .getString(KEY_GITHUB_REPO, "")
                    .orEmpty()
            }
        )
    }
    var repoError by remember { mutableStateOf<String?>(null) }

    var apkInfo by remember { mutableStateOf<InstalledApkInfo?>(null) }
    var isExportingApk by remember { mutableStateOf(false) }
    var apkStatusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        apkInfo = ApkExportHelper.inspectInstalledApk(context)
    }

    val createApkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { targetUri ->
        if (targetUri != null) {
            scope.launch {
                isExportingApk = true
                apkStatusMessage = null
                ApkExportHelper.saveApkToUri(context, targetUri)
                    .onSuccess { bytes ->
                        val sizeText = DocumentHelper.formatFileSize(bytes)
                        apkStatusMessage = "APK exportado correctamente ($sizeText)."
                        Toast.makeText(context, "APK guardado ($sizeText)", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { err ->
                        apkStatusMessage = err.localizedMessage ?: "Error al guardar el .apk."
                    }
                isExportingApk = false
            }
        }
    }

    val normalizedRepo = normalizeRepoSlug(repoInput)
    val hasValidRepo = REPO_SLUG_REGEX.matches(normalizedRepo)

    fun saveRepoPreference(value: String) {
        repoInput = value
        repoError = null
        val cleaned = normalizeRepoSlug(value)
        if (!isGuestMode && REPO_SLUG_REGEX.matches(cleaned)) {
            context.getSharedPreferences(PREFS_BUILDS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_GITHUB_REPO, cleaned)
                .apply()
        }
    }

    fun openBuildUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "No se encontró navegador para abrir el enlace.", Toast.LENGTH_SHORT).show()
        }
    }

    fun requireRepoUrl(pathSuffix: String): String? {
        if (!hasValidRepo) {
            repoError = "Ingresá tu repositorio GitHub (ej: usuario/facuedu-uy-mennseger) para abrir los builds."
            return null
        }
        repoError = null
        return "https://github.com/$normalizedRepo/$pathSuffix"
    }

    val platformTargets = remember {
        listOf(
            PlatformBuildTarget(
                id = "windows",
                title = "Windows",
                subtitle = "Instalador nativo de escritorio (x64)",
                formats = listOf(".exe"),
                workflowJob = "build-windows",
                artifactName = "facuedu-mennseger-windows-exe",
                icon = Icons.Filled.LaptopWindows
            ),
            PlatformBuildTarget(
                id = "macos",
                title = "macOS",
                subtitle = "Imagen de disco e instalador PKG",
                formats = listOf(".dmg", ".pkg"),
                workflowJob = "build-macos",
                artifactName = "facuedu-mennseger-macos-dmg-pkg",
                icon = Icons.Filled.LaptopMac
            ),
            PlatformBuildTarget(
                id = "linux_ubuntu",
                title = "Linux y Ubuntu",
                subtitle = "Portable AppImage y paquete Debian/Ubuntu",
                formats = listOf(".AppImage", ".deb"),
                workflowJob = "build-linux-ubuntu",
                artifactName = "facuedu-mennseger-linux-deb-appimage",
                icon = Icons.Filled.Terminal
            ),
            PlatformBuildTarget(
                id = "android",
                title = "Android",
                subtitle = "Paquete móvil instalable directo + Release CI",
                formats = listOf(".apk"),
                workflowJob = "android-apk",
                artifactName = "facuedu-mennseger-android-apk",
                icon = Icons.Filled.Android,
                isLocalApkSupported = true
            ),
            PlatformBuildTarget(
                id = "ios",
                title = "iOS (iPhone / iPad)",
                subtitle = "Archivo IPA compilado en macOS con Xcode",
                formats = listOf(".ipa"),
                workflowJob = "build-ios",
                artifactName = "facuedu-mennseger-ios-ipa",
                icon = Icons.Filled.PhoneIphone
            )
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Portal de Builds", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Página oculta · Instaladores multiplataforma",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("hidden_builds_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver a Descargar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hidden portal banner
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VisibilityOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Builds Multiplataforma (Oculto)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Esta vista no aparece en la barra principal. Desde acá descargás el APK local y los builds de Windows, macOS, Linux/Ubuntu e iOS generados por GitHub Actions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // GitHub repository connector card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Origen de Releases (GitHub Actions)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Conectá tu repositorio (formato usuario/repo) para abrir las descargas directas de .exe, .dmg, .pkg, .AppImage, .deb, .apk e .ipa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = ::saveRepoPreference,
                            label = { Text("Repositorio GitHub (ej: usuario/facuedu-uy-mennseger)") },
                            singleLine = true,
                            isError = repoError != null,
                            supportingText = repoError?.let { err -> { Text(err) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("builds_repo_input")
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    requireRepoUrl("releases/latest")?.let(::openBuildUrl)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("open_all_releases_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Releases")
                            }
                            OutlinedButton(
                                onClick = {
                                    requireRepoUrl("actions/workflows/build.yml")?.let(::openBuildUrl)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("open_actions_workflow_button")
                            ) {
                                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Actions CI")
                            }
                        }
                    }
                }

                // Individual OS build cards
                platformTargets.forEach { target ->
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("build_card_${target.id}")
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
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = target.icon,
                                        contentDescription = target.title,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = target.title,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = target.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                target.formats.forEach { ext ->
                                    AssistChip(
                                        onClick = {
                                            requireRepoUrl("releases/latest")?.let(::openBuildUrl)
                                        },
                                        label = { Text(ext) }
                                    )
                                }
                                AssistChip(
                                    onClick = {
                                        requireRepoUrl("actions/workflows/build.yml")?.let(::openBuildUrl)
                                    },
                                    label = {
                                        Text(
                                            text = target.workflowJob,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            )
                                        )
                                    }
                                )
                            }

                            Text(
                                text = "Artefacto CI: ${target.artifactName}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (target.isLocalApkSupported) {
                                if (apkStatusMessage != null) {
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
                                            text = apkStatusMessage!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            val name = apkInfo?.fileName ?: "Facuedu-uy-Mennseger.apk"
                                            createApkLauncher.launch(name)
                                        },
                                        enabled = !isExportingApk && (apkInfo?.sourceAvailable == true),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("portal_export_local_apk_button")
                                    ) {
                                        if (isExportingApk) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Guardar .APK")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                isExportingApk = true
                                                val fileName = apkInfo?.fileName ?: "Facuedu-uy-Mennseger.apk"
                                                ApkExportHelper.createShareApkIntent(context, fileName)
                                                    .onSuccess { chooser ->
                                                        runCatching { context.startActivity(chooser) }
                                                    }
                                                isExportingApk = false
                                            }
                                        },
                                        enabled = !isExportingApk && (apkInfo?.sourceAvailable == true),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("portal_share_local_apk_button")
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Compartir")
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        requireRepoUrl("releases/latest")?.let(::openBuildUrl)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("download_build_${target.id}_button")
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Descargar ${target.formats.joinToString(" / ")}")
                                }

                                IconButton(
                                    onClick = {
                                        val releaseUrl = requireRepoUrl("releases/latest")
                                        if (releaseUrl != null) {
                                            clipboard.setText(AnnotatedString(releaseUrl))
                                            Toast.makeText(
                                                context,
                                                "Enlace de ${target.title} copiado",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("copy_build_link_${target.id}_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copiar enlace de descarga ${target.title}"
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
