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
import androidx.compose.material.icons.filled.FolderZip
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
import com.example.media.InstallerPlatformFilter
import com.example.media.ZipInstallerEntry
import com.example.media.ZipInstallerExtractor
import com.example.media.ZipScanResult
import com.example.ui.components.ZipConverterCard
import kotlinx.coroutines.launch

private const val PREFS_BUILDS = "facuedu_builds_portal"
private const val KEY_GITHUB_REPO = "github_repo_slug"
private const val DEFAULT_GITHUB_REPO = "Fac-u1/Mennseger-Facuedu"
private const val WINDOWS_LATEST_ARTIFACT_URL =
    "https://github.com/Fac-u1/Mennseger-Facuedu/actions/runs/43668642852/artifacts/14143188459"
private val REPO_SLUG_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

private data class ReleaseAssetSpec(
    val extensionLabel: String,
    val releaseFileName: String
)

private data class PlatformBuildTarget(
    val id: String,
    val title: String,
    val subtitle: String,
    val assets: List<ReleaseAssetSpec>,
    val workflowJob: String,
    val artifactName: String,
    val icon: ImageVector,
    val zipFilter: InstallerPlatformFilter,
    val isLocalApkSupported: Boolean = false,
    val directArtifactUrl: String? = null
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
                DEFAULT_GITHUB_REPO
            } else {
                context.getSharedPreferences(PREFS_BUILDS, Context.MODE_PRIVATE)
                    .getString(KEY_GITHUB_REPO, DEFAULT_GITHUB_REPO)
                    .orEmpty()
                    .ifBlank { DEFAULT_GITHUB_REPO }
            }
        )
    }
    var repoError by remember { mutableStateOf<String?>(null) }

    var apkInfo by remember { mutableStateOf<InstalledApkInfo?>(null) }
    var isExportingApk by remember { mutableStateOf(false) }
    var apkStatusMessage by remember { mutableStateOf<String?>(null) }

    var activeZipFilter by remember { mutableStateOf(InstallerPlatformFilter.ALL) }
    var selectedZipUri by remember { mutableStateOf<Uri?>(null) }
    var zipScanResult by remember { mutableStateOf<ZipScanResult?>(null) }
    var isScanningZip by remember { mutableStateOf(false) }
    var extractingEntryPath by remember { mutableStateOf<String?>(null) }
    var zipStatusMessage by remember { mutableStateOf<String?>(null) }
    var zipErrorMessage by remember { mutableStateOf<String?>(null) }
    var pendingSaveEntry by remember { mutableStateOf<ZipInstallerEntry?>(null) }

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

    val pickZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        if (pickedUri != null) {
            selectedZipUri = pickedUri
            zipScanResult = null
            zipStatusMessage = null
            zipErrorMessage = null
            scope.launch {
                isScanningZip = true
                ZipInstallerExtractor.inspectZipUri(context, pickedUri, activeZipFilter)
                    .onSuccess { result ->
                        zipScanResult = result
                        if (result.allInstallersInZip.isNotEmpty()) {
                            zipStatusMessage =
                                "Se encontraron ${result.allInstallersInZip.size} instalador(es) listos para extraer."
                        }
                    }
                    .onFailure { err ->
                        zipErrorMessage = err.localizedMessage ?: "No se pudo leer el archivo .zip."
                    }
                isScanningZip = false
            }
        }
    }

    val saveExtractedLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { destinationUri ->
        val zipUri = selectedZipUri
        val entry = pendingSaveEntry
        pendingSaveEntry = null
        if (destinationUri != null && zipUri != null && entry != null) {
            scope.launch {
                extractingEntryPath = entry.entryPath
                zipStatusMessage = null
                zipErrorMessage = null
                ZipInstallerExtractor.extractEntryToUri(context, zipUri, entry, destinationUri)
                    .onSuccess { bytes ->
                        val sizeText = DocumentHelper.formatFileSize(bytes)
                        val badge = ZipInstallerExtractor.formatExtensionBadge(entry.extension)
                        zipStatusMessage = "${entry.fileName} ($badge) guardado ($sizeText)."
                        Toast.makeText(context, "$badge guardado ($sizeText)", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { err ->
                        zipErrorMessage = err.localizedMessage ?: "No se pudo extraer ${entry.fileName}."
                    }
                extractingEntryPath = null
            }
        }
    }

    fun launchZipConverter(filter: InstallerPlatformFilter) {
        activeZipFilter = filter
        val existingUri = selectedZipUri
        if (existingUri != null && zipScanResult != null) {
            scope.launch {
                isScanningZip = true
                ZipInstallerExtractor.inspectZipUri(context, existingUri, filter)
                    .onSuccess { zipScanResult = it }
                isScanningZip = false
            }
        }
        pickZipLauncher.launch(
            arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream",
                "*/*"
            )
        )
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
            repoError = "Ingresá tu repositorio GitHub (ej: usuario/Mennseger-Facuedu) para abrir los builds."
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
                subtitle = "Instalador nativo de escritorio (x64) directo sin .ZIP",
                assets = listOf(
                    ReleaseAssetSpec(".exe", "Facuedu.uy-Mennseger-Setup-1.0.0.exe")
                ),
                workflowJob = "build-windows",
                artifactName = "facuedu-mennseger-windows-exe",
                icon = Icons.Filled.LaptopWindows,
                zipFilter = InstallerPlatformFilter.WINDOWS,
                directArtifactUrl = WINDOWS_LATEST_ARTIFACT_URL
            ),
            PlatformBuildTarget(
                id = "macos",
                title = "macOS",
                subtitle = "Imagen de disco e instalador PKG directo sin .ZIP",
                assets = listOf(
                    ReleaseAssetSpec(".dmg", "Facuedu.uy-Mennseger-1.0.0-universal.dmg"),
                    ReleaseAssetSpec(".pkg", "Facuedu.uy-Mennseger-1.0.0-universal.pkg")
                ),
                workflowJob = "build-macos",
                artifactName = "facuedu-mennseger-macos-dmg-pkg",
                icon = Icons.Filled.LaptopMac,
                zipFilter = InstallerPlatformFilter.MACOS
            ),
            PlatformBuildTarget(
                id = "linux_ubuntu",
                title = "Linux y Ubuntu",
                subtitle = "Portable AppImage y paquete Debian/Ubuntu directo",
                assets = listOf(
                    ReleaseAssetSpec(".AppImage", "Facuedu.uy-Mennseger-1.0.0-x86_64.AppImage"),
                    ReleaseAssetSpec(".deb", "Facuedu.uy-Mennseger-1.0.0-amd64.deb")
                ),
                workflowJob = "build-linux-ubuntu",
                artifactName = "facuedu-mennseger-linux-deb-appimage",
                icon = Icons.Filled.Terminal,
                zipFilter = InstallerPlatformFilter.LINUX_UBUNTU
            ),
            PlatformBuildTarget(
                id = "android",
                title = "Android",
                subtitle = "Paquete móvil instalable directo + Release CI",
                assets = listOf(
                    ReleaseAssetSpec(".apk", "Facuedu.uy-Mennseger-Android.apk")
                ),
                workflowJob = "android-apk",
                artifactName = "facuedu-mennseger-android-apk",
                icon = Icons.Filled.Android,
                zipFilter = InstallerPlatformFilter.ANDROID,
                isLocalApkSupported = true
            ),
            PlatformBuildTarget(
                id = "ios",
                title = "iOS (iPhone / iPad)",
                subtitle = "Archivo IPA compilado en macOS con Xcode",
                assets = listOf(
                    ReleaseAssetSpec(".ipa", "Facuedu.uy-Mennseger-iOS.ipa")
                ),
                workflowJob = "build-ios",
                artifactName = "facuedu-mennseger-ios-ipa",
                icon = Icons.Filled.PhoneIphone,
                zipFilter = InstallerPlatformFilter.IOS
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
                            text = "Página oculta · Descargas directas + Conversor .ZIP",
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
                                    text = "Descargá instaladores directos sin .ZIP desde GitHub Releases o convertí cualquier .ZIP de Actions en .exe, .dmg, .pkg, .AppImage, .deb, .apk o .ipa.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // .ZIP -> .EXE / .DMG / .PKG / .AppImage / .DEB / .APK / .IPA Converter
                ZipConverterCard(
                    activeFilter = activeZipFilter,
                    onSelectFilter = { newFilter ->
                        activeZipFilter = newFilter
                        val existingUri = selectedZipUri
                        if (existingUri != null) {
                            scope.launch {
                                isScanningZip = true
                                ZipInstallerExtractor.inspectZipUri(context, existingUri, newFilter)
                                    .onSuccess { zipScanResult = it }
                                isScanningZip = false
                            }
                        }
                    },
                    onPickZip = ::launchZipConverter,
                    selectedZipUri = selectedZipUri,
                    scanResult = zipScanResult,
                    isScanningZip = isScanningZip,
                    extractingEntryPath = extractingEntryPath,
                    statusMessage = zipStatusMessage,
                    errorMessage = zipErrorMessage,
                    onSaveInstaller = { entry ->
                        pendingSaveEntry = entry
                        saveExtractedLauncher.launch(entry.fileName)
                    },
                    onShareInstaller = { entry ->
                        val zipUri = selectedZipUri ?: return@ZipConverterCard
                        scope.launch {
                            extractingEntryPath = entry.entryPath
                            zipErrorMessage = null
                            ZipInstallerExtractor.createShareExtractedIntent(context, zipUri, entry)
                                .onSuccess { chooser ->
                                    runCatching { context.startActivity(chooser) }
                                        .onFailure {
                                            zipErrorMessage = "No se encontró aplicación para compartir ${entry.fileName}."
                                        }
                                }
                                .onFailure { err ->
                                    zipErrorMessage = err.localizedMessage ?: "Error al preparar ${entry.fileName}."
                                }
                            extractingEntryPath = null
                        }
                    }
                )

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
                            text = "Conectá tu repositorio (formato usuario/repo) para abrir las descargas directas sin .ZIP desde Releases o los artefactos de CI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = ::saveRepoPreference,
                            label = { Text("Repositorio GitHub (ej: Fac-u1/Mennseger-Facuedu)") },
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
                                    requireRepoUrl("releases")?.let(::openBuildUrl)
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
                                target.assets.forEach { asset ->
                                    AssistChip(
                                        onClick = {
                                            requireRepoUrl("releases/latest/download/${asset.releaseFileName}")
                                                ?.let(::openBuildUrl)
                                        },
                                        label = { Text(asset.extensionLabel) }
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

                            // Direct release installer buttons (no .ZIP wrapper)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                target.assets.forEach { asset ->
                                    FilledTonalButton(
                                        onClick = {
                                            requireRepoUrl("releases/latest/download/${asset.releaseFileName}")
                                                ?.let(::openBuildUrl)
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("download_direct_${target.id}_${asset.extensionLabel.trimStart('.')}_button")
                                    ) {
                                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Directo ${asset.extensionLabel}")
                                    }
                                }
                            }

                            // ZIP -> Installer conversion button + Artifact / Copy link
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { launchZipConverter(target.zipFilter) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("convert_zip_${target.id}_button")
                                ) {
                                    Icon(Icons.Filled.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(".ZIP → ${target.zipFilter.badgeText}")
                                }

                                val copyTargetUrl = if (
                                    target.directArtifactUrl != null &&
                                    normalizedRepo.equals(DEFAULT_GITHUB_REPO, ignoreCase = true)
                                ) {
                                    target.directArtifactUrl
                                } else {
                                    requireRepoUrl("releases/latest/download/${target.assets.first().releaseFileName}")
                                }

                                if (target.directArtifactUrl != null &&
                                    normalizedRepo.equals(DEFAULT_GITHUB_REPO, ignoreCase = true)
                                ) {
                                    IconButton(
                                        onClick = { openBuildUrl(target.directArtifactUrl) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("download_build_${target.id}_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Abrir artefacto CI de ${target.title}"
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        if (copyTargetUrl != null) {
                                            clipboard.setText(AnnotatedString(copyTargetUrl))
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
