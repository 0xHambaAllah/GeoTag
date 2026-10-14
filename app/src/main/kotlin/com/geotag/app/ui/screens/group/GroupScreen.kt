package com.geotag.app.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geotag.app.data.model.Group
import com.geotag.app.data.model.Invitation
import com.geotag.app.util.AppConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    onBack: () -> Unit,
    onGroupClick: (String) -> Unit,
    viewModel: GroupViewModel = viewModel()
) {
    val groups by viewModel.groups.collectAsState()
    val invitations by viewModel.invitations.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    message?.let {
        LaunchedEffect(it) {
            // tampilkan via snackbar sederhana: pakai Toast di ViewModel? -> tampilkan teks
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grup Saya") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, "Buat Grup")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            message?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(it, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearMessage() }) {
                            Icon(Icons.Default.Close, "Tutup")
                        }
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Undangan masuk
                if (invitations.isNotEmpty()) {
                    item {
                        Text(
                            "Undangan (${invitations.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(invitations, key = { it.id }) { inv ->
                        InvitationCard(
                            invitation = inv,
                            onAccept = { viewModel.respondInvitation(inv, true) },
                            onReject = { viewModel.respondInvitation(inv, false) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                // Daftar grup
                item {
                    Text(
                        "Grup (${groups.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (groups.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Belum ada grup. Buat grup baru dengan tombol +")
                            }
                        }
                    }
                } else {
                    items(groups, key = { it.id }) { group ->
                        GroupCard(
                            group = group,
                            myEmail = viewModel.myEmail,
                            isAdminApp = viewModel.isAdminApp,
                            onClick = { onGroupClick(group.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc ->
                viewModel.createGroup(name, desc)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun GroupCard(
    group: Group,
    myEmail: String?,
    isAdminApp: Boolean,
    onClick: () -> Unit
) {
    val isAdmin = isAdminApp ||
        group.admins.any { it.equals(myEmail, ignoreCase = true) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Group,
                null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isAdmin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    if (AppConstants.isAdminApp(myEmail)) AppConstants.ADMIN_APP_LABEL else "Admin",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
                if (group.description.isNotEmpty()) {
                    Text(
                        group.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Text(
                    "${group.members.size} anggota",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
fun InvitationCard(
    invitation: Invitation,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mail, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        invitation.groupName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Diundang oleh ${invitation.invitedBy}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onReject) {
                    Text("Tolak")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onAccept) {
                    Text("Terima")
                }
            }
        }
    }
}

@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Grup Baru") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama grup") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi (opsional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Data lokasi semua anggota akan otomatis tergabung dan saling terlihat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.trim(), description.trim()) },
                enabled = name.trim().isNotEmpty()
            ) {
                Text("Buat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
