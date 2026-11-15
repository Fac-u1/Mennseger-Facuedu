package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.SavedContact
import com.example.ui.viewmodel.DecryptedChatItem
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    chatsState: UiState<List<DecryptedChatItem>>,
    contactsState: UiState<List<SavedContact>>,
    currentUserName: String,
    selectedChatId: String?,
    onSelectChat: (String) -> Unit,
    onCreateChat: (title: String, peerUid: String) -> Unit,
    onAddContact: (uid: String, alias: String) -> Unit,
    onStartVideoCall: (DecryptedChatItem) -> Unit,
    onOpenAiAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("all") }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showAddContactDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Facuedu.uy Mennseger",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Cifrado E2EE · $currentUserName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddContactDialog = true },
                        modifier = Modifier.testTag("add_contact_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = "Agregar contacto por UID"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_chat_fab")
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Nuevo chat cifrado")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Meta-AI style quick spotlight bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onOpenAiAssistant)
                    .testTag("facuia_spotlight_bar"),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
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
                            contentDescription = "FacuIA",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Preguntale a FacuIA",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Chatear, cambiar frases, fotos, videos y música",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Search & Filter row
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar chats cifrados...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("chat_search_input")
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filterType == "all",
                        onClick = { filterType = "all" },
                        label = { Text("Todos") }
                    )
                }
                item {
                    FilterChip(
                        selected = filterType == "direct",
                        onClick = { filterType = "direct" },
                        label = { Text("Directos") }
                    )
                }
                item {
                    FilterChip(
                        selected = filterType == "vault",
                        onClick = { filterType = "vault" },
                        label = { Text("Bóveda / Salas") }
                    )
                }
            }

            when (chatsState) {
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
                        Text(
                            text = chatsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is UiState.Success -> {
                    val filtered = chatsState.data.filter { item ->
                        val matchesFilter = when (filterType) {
                            "direct" -> item.chat.chatType == "direct"
                            "vault" -> item.chat.chatType == "vault" || item.chat.chatType == "group"
                            else -> true
                        }
                        val matchesQuery = searchQuery.isBlank() ||
                            item.chat.title.contains(searchQuery, ignoreCase = true) ||
                            item.lastPreviewPlaintext.contains(searchQuery, ignoreCase = true)
                        matchesFilter && matchesQuery
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(52.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Sin conversaciones cifradas todavía",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tocá + para iniciar un chat cifrado o agregá un contacto por UID.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.chat.id }) { item ->
                                ChatThreadCard(
                                    item = item,
                                    isSelected = item.chat.id == selectedChatId,
                                    onClick = { onSelectChat(item.chat.id) },
                                    onVideoCall = { onStartVideoCall(item) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(72.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showNewChatDialog) {
        val contacts = (contactsState as? UiState.Success)?.data.orEmpty()
        NewEncryptedChatDialog(
            contacts = contacts,
            onDismiss = { showNewChatDialog = false },
            onCreate = { title, peerUid ->
                onCreateChat(title, peerUid)
                showNewChatDialog = false
            }
        )
    }

    if (showAddContactDialog) {
        AddContactDialog(
            onDismiss = { showAddContactDialog = false },
            onConfirm = { uid, alias ->
                onAddContact(uid, alias)
                showAddContactDialog = false
            }
        )
    }
}

@Composable
private fun ChatThreadCard(
    item: DecryptedChatItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onVideoCall: () -> Unit
) {
    val chat = item.chat
    val timeText = remember(chat.updatedAt, chat.createdAt) {
        val date = (chat.updatedAt ?: chat.createdAt)?.toDate()
        if (date != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(date) else "Ahora"
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_item_${chat.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chat.title.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Cifrado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = item.lastPreviewPlaintext,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onVideoCall,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("quick_call_${chat.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = "Iniciar videollamada cifrada",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun NewEncryptedChatDialog(
    contacts: List<SavedContact>,
    onDismiss: () -> Unit,
    onCreate: (title: String, peerUid: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var peerUid by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo chat cifrado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Creá una sala privada o ingresá el UID de otro usuario de Facuedu.uy Mennseger para intercambiar claves ECDH.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nombre del chat o sala") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = peerUid,
                    onValueChange = { peerUid = it },
                    label = { Text("UID del contacto (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (contacts.isNotEmpty()) {
                    Text(
                        text = "Contactos guardados:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(contacts, key = { it.id }) { contact ->
                            FilterChip(
                                selected = peerUid == contact.contactUid,
                                onClick = {
                                    peerUid = contact.contactUid
                                    if (title.isBlank()) title = contact.displayName
                                },
                                label = { Text(contact.displayName) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, peerUid) },
                modifier = Modifier.testTag("confirm_new_chat_button")
            ) {
                Text("Crear E2EE")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (uid: String, alias: String) -> Unit
) {
    var uid by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar contacto E2EE") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Pedile a tu contacto su UID desde la pestaña Seguridad para verificar su clave pública ECDH.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = uid,
                    onValueChange = { uid = it },
                    label = { Text("UID del usuario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Nombre o alias") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(uid, alias) },
                enabled = uid.isNotBlank()
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
