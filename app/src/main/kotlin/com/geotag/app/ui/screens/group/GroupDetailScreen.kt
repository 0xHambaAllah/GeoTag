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
import com.geotag.app.util.AppConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    viewModel: GroupViewModel = viewModel()
) {
    val groups by viewModel.groups.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val group = groups.find { it.id == groupId }
    val myEmail = viewModel.myEmail
    val isAdminApp = viewModel.isAdminApp
    val canManage = isAdminApp ||
        (group?.admins?.any { it.equals(myEmail, ignoreCase = true) } == true)

    var showInviteDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(groupId) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "Detail Grup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        if (group == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Info grup
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (group.description.isNotEmpty()) {
                            Text(group.description, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            "Dibuat oleh ${group.createdBy}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${group.members.size} anggota",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Tombol undang
            if (canManage) {
                item {
                    Button(
                        onClick = { showInviteDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PersonAdd, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Undang Anggota")
                    }
                }
            }

            // Daftar anggota
            item {
                Text("Anggota", style = MaterialTheme.typography.titleMedium)
            }
            items(group.members, key = { it }) { member ->
                MemberRow(
                    email = member,
                    isAdmin = group.admins.any { it.equals(member, ignoreCase = true) },
                    isMe = member.equals(myEmail, ignoreCase = true),
                    canRemove = canManage && !member.equals(myEmail, ignoreCase = true),
                    onRemove = { viewModel.removeMember(group, member) }
                )
            }

            // Aksi bawah
            item {
                Spacer(modifier = Modifier.height(8.dp))
                if (canManage || isAdminApp) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hapus Grup")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { showLeaveConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ExitToApp, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Keluar Grup")
                }
                Text(
                    "Jika keluar, datamu tidak lagi terlihat oleh anggota lain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    if (showInviteDialog && group != null) {
        InviteDialog(
            group = group,
            myEmail = myEmail,
            allUsers = allUsers,
            onDismiss = { showInviteDialog = false },
            onInvite = { email ->
                viewModel.inviteUser(group, email)
                showInviteDialog = false
            }
        )
    }
    if (showDeleteConfirm && group != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus Grup?") },
            text = { Text("Grup \"${group.name}\" akan dihapus permanen.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGroup(group.id)
                        showDeleteConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal") }
            }
        )
    }
    if (showLeaveConfirm && group != null) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Keluar Grup?") },
            text = { Text("Datamu tidak akan lagi terlihat oleh anggota grup ini.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.leaveGroup(group)
                    showLeaveConfirm = false
                    onBack()
                }) { Text("Keluar") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
fun MemberRow(
    email: String,
    isAdmin: Boolean,
    isMe: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        email,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isMe) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "(Saya)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isAdmin) {
                    val label = if (AppConstants.isAdminApp(email)) {
                        AppConstants.ADMIN_APP_LABEL
                    } else {
                        "Admin Grup"
                    }
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.RemoveCircle,
                        "Keluarkan",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun InviteDialog(
    group: Group,
    myEmail: String?,
    allUsers: List<com.geotag.app.data.model.UserProfile>,
    onDismiss: () -> Unit,
    onInvite: (String) -> Unit
) {
    // Filter: bukan diri sendiri, belum jadi member
    val candidates = allUsers.filter { user ->
        !user.email.equals(myEmail, ignoreCase = true) &&
        group.members.none { it.equals(user.email, ignoreCase = true) }
    }
    var selectedEmail by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Undang Anggota") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (candidates.isEmpty()) {
                    Text(
                        "Tidak ada user lain yang terdaftar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Pilih user yang terdaftar:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(candidates, key = { it.uid }) { user ->
                            val selected = selectedEmail == user.email
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectedEmail = user.email },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            user.displayName.ifEmpty { user.email },
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (user.displayName.isNotEmpty()) {
                                            Text(
                                                user.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    RadioButton(
                                        selected = selected,
                                        onClick = { selectedEmail = user.email }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Undangan akan muncul di menu Grup pengguna tersebut.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedEmail?.let { onInvite(it) } },
                enabled = selectedEmail != null
            ) { Text("Kirim Undangan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
