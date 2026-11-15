package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.FacuAiService
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.onGoogleSignInClicked
import com.example.auth.signOut
import com.example.crypto.CryptoManager
import com.example.data.repository.MessengerDataSource
import com.example.data.repository.MessengerRepository
import com.example.media.SocialShareHelper
import com.example.security.GuestCleanupService
import com.example.security.GuestSessionController
import com.example.security.configureAppCheck
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BuildsPortalScreen
import com.example.ui.screens.CallsHistoryScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsListScreen
import com.example.ui.screens.DownloadScreen
import com.example.ui.screens.SecurityProfileScreen
import com.example.ui.screens.VideoCallScreen
import com.example.ui.theme.FacueduMessengerTheme
import com.example.ui.viewmodel.AiStudioViewModel
import com.example.ui.viewmodel.MessengerViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class MainTab(val label: String) {
    CHATS("Chats"),
    CALLS("Llamadas"),
    FACU_IA("FacuIA"),
    DOWNLOAD("Descargar"),
    SECURITY("Seguridad")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels
        configureAppCheck(this, intent)

        // Wipe any leftover temporary guest artifacts on fresh cold launch
        if (savedInstanceState == null && GuestSessionController.activeSession.value == null) {
            GuestSessionController.wipeDiskArtifacts(this)
        }

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by rememberSaveable { mutableStateOf(true || systemDark) }

            FacueduMessengerTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val pendingInvite = remember(intent?.data) {
                        SocialShareHelper.parseE2eeInviteUri(intent?.data)
                    }
                    AppNavigation(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { isDarkTheme = it },
                        pendingInviteUid = pendingInvite?.uid,
                        pendingInviteName = pendingInvite?.displayName
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            GuestSessionController.endAndWipeGuestSession(applicationContext)
        }
        super.onDestroy()
    }
}

@Composable
private fun AppNavigation(
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    pendingInviteUid: String? = null,
    pendingInviteName: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    val guestSession by GuestSessionController.activeSession.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null && GuestSessionController.activeSession.value == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    // If a permanent Google user signs in, immediately wipe any temporary guest session
    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null && GuestSessionController.activeSession.value != null) {
            GuestSessionController.endAndWipeGuestSession(context)
        }
    }

    // Bind GuestCleanupService while Guest Mode is active so task swipe/close deletes all guest data
    DisposableEffect(guestSession?.uid) {
        val active = guestSession
        if (active != null && currentUser == null) {
            val serviceIntent = Intent(context, GuestCleanupService::class.java)
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) = Unit
                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }
            val bound = runCatching {
                context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)
            onDispose {
                if (bound) {
                    runCatching { context.unbindService(connection) }
                }
            }
        } else {
            onDispose {}
        }
    }

    val user = currentUser
    when {
        user != null -> {
            key(user.uid) {
                val repo = remember(user.uid) {
                    MessengerRepository(
                        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
                    )
                }
                val crypto = remember(user.uid) { CryptoManager(context) }
                AuthenticatedMessengerShell(
                    uid = user.uid,
                    displayName = user.displayName ?: "Usuario Facuedu",
                    email = user.email,
                    photoUrl = user.photoUrl?.toString(),
                    repository = repo,
                    cryptoManager = crypto,
                    isGuestMode = false,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    pendingInviteUid = pendingInviteUid,
                    pendingInviteName = pendingInviteName,
                    onConnectGoogle = null,
                    onSignOut = {
                        signOut(credentialManager = credentialManager, scope = scope)
                    }
                )
            }
        }

        guestSession != null -> {
            val session = guestSession!!
            key(session.uid) {
                AuthenticatedMessengerShell(
                    uid = session.uid,
                    displayName = session.displayName,
                    email = null,
                    photoUrl = null,
                    repository = session.repository,
                    cryptoManager = session.cryptoManager,
                    isGuestMode = true,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    pendingInviteUid = pendingInviteUid,
                    pendingInviteName = pendingInviteName,
                    onConnectGoogle = {
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                GuestSessionController.endAndWipeGuestSession(context)
                            },
                            onAuthError = {},
                            scope = scope
                        )
                    },
                    onSignOut = {
                        GuestSessionController.endAndWipeGuestSession(context)
                    }
                )
            }
        }

        else -> {
            AuthScreen(
                onAuthSuccess = {},
                onEnterGuestMode = {
                    GuestSessionController.startGuestSession(context)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedMessengerShell(
    uid: String,
    displayName: String,
    email: String?,
    photoUrl: String?,
    repository: MessengerDataSource,
    cryptoManager: CryptoManager,
    isGuestMode: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    pendingInviteUid: String?,
    pendingInviteName: String?,
    onConnectGoogle: (() -> Unit)?,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current

    val messengerViewModel: MessengerViewModel = viewModel(
        key = "messenger_$uid",
        factory = remember(uid, repository, cryptoManager) {
            MessengerViewModel.provideFactory(
                repository = repository,
                cryptoManager = cryptoManager,
                aiService = FacuAiService(context.applicationContext),
                currentUserId = uid,
                currentUserName = displayName,
                currentUserEmail = email,
                currentUserPhotoUrl = photoUrl
            )
        }
    )

    val aiStudioViewModel: AiStudioViewModel = viewModel(
        key = "ai_studio_$uid",
        factory = remember(uid, repository) {
            AiStudioViewModel.provideFactory(
                aiService = FacuAiService(context.applicationContext),
                repository = repository,
                currentUserId = uid
            )
        }
    )

    val chatsState by messengerViewModel.chatsState.collectAsStateWithLifecycle()
    val contactsState by messengerViewModel.contactsState.collectAsStateWithLifecycle()
    val callsState by messengerViewModel.callsState.collectAsStateWithLifecycle()
    val selectedChatId by messengerViewModel.selectedChatId.collectAsStateWithLifecycle()
    val messagesState by messengerViewModel.activeChatMessages.collectAsStateWithLifecycle()
    val composerText by messengerViewModel.composerText.collectAsStateWithLifecycle()
    val rewriteState by messengerViewModel.phraseRewriteState.collectAsStateWithLifecycle()
    val voiceState by messengerViewModel.voiceTranscriptState.collectAsStateWithLifecycle()
    val docSummaryState by messengerViewModel.docSummaryState.collectAsStateWithLifecycle()
    val activeCallState by messengerViewModel.activeCallState.collectAsStateWithLifecycle()
    val statusBanner by messengerViewModel.statusBanner.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(MainTab.CHATS) }
    var showHiddenBuildsPortal by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var handledInviteUid by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingInviteUid, uid) {
        if (!pendingInviteUid.isNullOrBlank() &&
            pendingInviteUid != uid &&
            pendingInviteUid != handledInviteUid
        ) {
            handledInviteUid = pendingInviteUid
            messengerViewModel.addContactByUid(
                contactUid = pendingInviteUid,
                aliasName = pendingInviteName.orEmpty().ifBlank { "Contacto E2EE" }
            )
        }
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            messengerViewModel.clearStatusBanner()
        }
    }

    val chatsList = (chatsState as? UiState.Success)?.data.orEmpty()
    val selectedChat = chatsList.firstOrNull { it.chat.id == selectedChatId }

    // Fullscreen Call Overlay when a videocall/audio call is active
    val activeCall = activeCallState
    if (activeCall != null) {
        VideoCallScreen(
            callState = activeCall,
            onToggleMute = messengerViewModel::toggleCallMute,
            onToggleCamera = messengerViewModel::toggleCallCamera,
            onSwitchLens = messengerViewModel::switchCallLens,
            onToggleSpeaker = messengerViewModel::toggleCallSpeaker,
            onEndCall = messengerViewModel::endActiveCall
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        // On compact phones, opening a chat shows ChatDetailScreen full-screen
        if (!isWideScreen && selectedChat != null && currentTab == MainTab.CHATS) {
            ChatDetailScreen(
                chatItem = selectedChat,
                messagesState = messagesState,
                currentUserId = uid,
                composerText = composerText,
                phraseRewriteState = rewriteState,
                voiceTranscriptState = voiceState,
                docSummaryState = docSummaryState,
                showBackButton = true,
                onBack = { messengerViewModel.selectChat(null) },
                onComposerChange = messengerViewModel::updateComposerText,
                onSendMessage = { messengerViewModel.sendEncryptedMessage(selectedChat) },
                onSendDocument = { doc -> messengerViewModel.sendEncryptedDocument(selectedChat, doc) },
                onSummarizeDocument = messengerViewModel::summarizeDocumentWithAi,
                onDismissDocSummary = messengerViewModel::clearDocSummary,
                onRewritePhrase = messengerViewModel::rewriteComposerPhrase,
                onApplyRewrite = messengerViewModel::applyRewrittenPhrase,
                onDismissRewrite = messengerViewModel::dismissPhraseRewrite,
                onRecordingStateChanged = messengerViewModel::setVoiceRecordingState,
                onTranscribeAudioFile = messengerViewModel::transcribeAudioToComposer,
                onStartCall = { type -> messengerViewModel.startVideoCall(selectedChat, type) }
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!isWideScreen) {
                        NavigationBar {
                            MainTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        currentTab = tab
                                        if (tab != MainTab.DOWNLOAD) {
                                            showHiddenBuildsPortal = false
                                        }
                                    },
                                    icon = { TabIcon(tab = tab, selected = selected) },
                                    label = { Text(tab.label, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isGuestMode) {
                        GuestModeBanner(
                            onWipeAndExit = onSignOut,
                            onConnectGoogle = onConnectGoogle
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(
                                if (isGuestMode) Modifier.consumeWindowInsets(WindowInsets.statusBars)
                                else Modifier
                            )
                    ) {
                        if (isWideScreen) {
                            NavigationRail {
                                MainTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            currentTab = tab
                                            if (tab != MainTab.DOWNLOAD) {
                                                showHiddenBuildsPortal = false
                                            }
                                        },
                                        icon = { TabIcon(tab = tab, selected = selected) },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (currentTab) {
                                MainTab.CHATS -> {
                                    if (isWideScreen) {
                                        Row(modifier = Modifier.fillMaxSize()) {
                                            ChatsListScreen(
                                                chatsState = chatsState,
                                                contactsState = contactsState,
                                                currentUserName = displayName,
                                                selectedChatId = selectedChatId,
                                                onSelectChat = messengerViewModel::selectChat,
                                                onCreateChat = { title, peer ->
                                                    messengerViewModel.createEncryptedChat(title, peer)
                                                },
                                                onAddContact = messengerViewModel::addContactByUid,
                                                onStartVideoCall = { item ->
                                                    messengerViewModel.startVideoCall(item, "video")
                                                },
                                                onOpenAiAssistant = { currentTab = MainTab.FACU_IA },
                                                modifier = Modifier.weight(0.42f)
                                            )
                                            Box(modifier = Modifier.weight(0.58f)) {
                                                val active = selectedChat ?: chatsList.firstOrNull()
                                                if (active != null) {
                                                    ChatDetailScreen(
                                                        chatItem = active,
                                                        messagesState = messagesState,
                                                        currentUserId = uid,
                                                        composerText = composerText,
                                                        phraseRewriteState = rewriteState,
                                                        voiceTranscriptState = voiceState,
                                                        docSummaryState = docSummaryState,
                                                        showBackButton = false,
                                                        onBack = {},
                                                        onComposerChange = messengerViewModel::updateComposerText,
                                                        onSendMessage = { messengerViewModel.sendEncryptedMessage(active) },
                                                        onSendDocument = { doc -> messengerViewModel.sendEncryptedDocument(active, doc) },
                                                        onSummarizeDocument = messengerViewModel::summarizeDocumentWithAi,
                                                        onDismissDocSummary = messengerViewModel::clearDocSummary,
                                                        onRewritePhrase = messengerViewModel::rewriteComposerPhrase,
                                                        onApplyRewrite = messengerViewModel::applyRewrittenPhrase,
                                                        onDismissRewrite = messengerViewModel::dismissPhraseRewrite,
                                                        onRecordingStateChanged = messengerViewModel::setVoiceRecordingState,
                                                        onTranscribeAudioFile = messengerViewModel::transcribeAudioToComposer,
                                                        onStartCall = { type -> messengerViewModel.startVideoCall(active, type) }
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        ChatsListScreen(
                                            chatsState = chatsState,
                                            contactsState = contactsState,
                                            currentUserName = displayName,
                                            selectedChatId = selectedChatId,
                                            onSelectChat = messengerViewModel::selectChat,
                                            onCreateChat = { title, peer ->
                                                messengerViewModel.createEncryptedChat(title, peer)
                                            },
                                            onAddContact = messengerViewModel::addContactByUid,
                                            onStartVideoCall = { item ->
                                                messengerViewModel.startVideoCall(item, "video")
                                            },
                                            onOpenAiAssistant = { currentTab = MainTab.FACU_IA }
                                        )
                                    }
                                }

                                MainTab.CALLS -> CallsHistoryScreen(
                                    callsState = callsState,
                                    chats = chatsList,
                                    onStartQuickCall = { item, type ->
                                        messengerViewModel.startVideoCall(item, type)
                                    }
                                )

                                MainTab.FACU_IA -> {
                                    val targetChat = selectedChat ?: chatsList.firstOrNull()
                                    AiStudioScreen(
                                        viewModel = aiStudioViewModel,
                                        hasActiveChat = targetChat != null,
                                        onSendTextToChat = { text ->
                                            if (targetChat != null) {
                                                messengerViewModel.sendEncryptedMessage(targetChat, text, "ai_assisted")
                                                messengerViewModel.selectChat(targetChat.chat.id)
                                                currentTab = MainTab.CHATS
                                            }
                                        },
                                        onSendBitmapToChat = { bitmap, label ->
                                            if (targetChat != null) {
                                                messengerViewModel.shareBitmapToChat(targetChat, bitmap, label)
                                                messengerViewModel.selectChat(targetChat.chat.id)
                                                currentTab = MainTab.CHATS
                                            }
                                        }
                                    )
                                }

                                MainTab.DOWNLOAD -> {
                                    if (showHiddenBuildsPortal) {
                                        BuildsPortalScreen(
                                            isGuestMode = isGuestMode,
                                            onBack = { showHiddenBuildsPortal = false }
                                        )
                                    } else {
                                        DownloadScreen(
                                            uid = uid,
                                            displayName = displayName,
                                            publicKeyFingerprint = cryptoManager.keyFingerprint,
                                            publicKeyBase64 = cryptoManager.publicKeyBase64,
                                            onOpenBuildsPortal = { showHiddenBuildsPortal = true }
                                        )
                                    }
                                }

                                MainTab.SECURITY -> SecurityProfileScreen(
                                    uid = uid,
                                    displayName = displayName,
                                    email = email,
                                    photoUrl = photoUrl,
                                    publicKeyFingerprint = cryptoManager.keyFingerprint,
                                    publicKeyBase64 = cryptoManager.publicKeyBase64,
                                    isDarkTheme = isDarkTheme,
                                    onToggleTheme = onToggleTheme,
                                    onSignOut = onSignOut,
                                    isGuestMode = isGuestMode,
                                    onConnectGoogle = onConnectGoogle
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuestModeBanner(
    onWipeAndExit: () -> Unit,
    onConnectGoogle: (() -> Unit)?
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Modo Invitado · Se borra al cerrar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onConnectGoogle != null) {
                    TextButton(onClick = onConnectGoogle) {
                        Text("Google", style = MaterialTheme.typography.labelMedium)
                    }
                }
                TextButton(
                    onClick = onWipeAndExit,
                    modifier = Modifier.testTag("guest_wipe_exit_button")
                ) {
                    Text(
                        text = "Borrar",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: MainTab, selected: Boolean) {
    val icon = when (tab) {
        MainTab.CHATS -> if (selected) Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Outlined.Chat
        MainTab.CALLS -> if (selected) Icons.Filled.Videocam else Icons.Outlined.Videocam
        MainTab.FACU_IA -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
        MainTab.DOWNLOAD -> if (selected) Icons.Filled.Download else Icons.Outlined.Download
        MainTab.SECURITY -> if (selected) Icons.Filled.Security else Icons.Outlined.Security
    }
    Icon(imageVector = icon, contentDescription = tab.label)
}
