package com.geotag.app.ui.screens.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotag.app.data.model.Group
import com.geotag.app.data.model.Invitation
import com.geotag.app.data.repository.LocationRepository
import com.geotag.app.util.AppConstants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GroupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocationRepository(application)
    private val firebase get() = repository.firebase

    val myEmail: String? get() = firebase.currentUserEmail
    val isAdminApp: Boolean get() = AppConstants.isAdminApp(myEmail)

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups

    private val _invitations = MutableStateFlow<List<Invitation>>(emptyList())
    val invitations: StateFlow<List<Invitation>> = _invitations

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val email = myEmail ?: return@launch
                _groups.value = firebase.getMyGroups(email)
                _invitations.value = firebase.getMyInvitations(email)
            } catch (e: Exception) {
                _message.value = "Gagal memuat: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessage() { _message.value = null }

    fun createGroup(name: String, description: String) {
        viewModelScope.launch {
            try {
                val email = myEmail ?: return@launch
                val group = Group(
                    name = name,
                    description = description,
                    createdBy = email,
                    admins = listOf(email),
                    members = listOf(email)
                )
                firebase.createGroup(group)
                _message.value = "Grup \"$name\" berhasil dibuat"
                refresh()
            } catch (e: Exception) {
                _message.value = "Gagal membuat grup: ${e.message}"
            }
        }
    }

    fun inviteUser(group: Group, invitedEmail: String) {
        viewModelScope.launch {
            try {
                val email = myEmail ?: return@launch
                val target = invitedEmail.trim().lowercase()
                if (target.isEmpty()) {
                    _message.value = "Email tidak boleh kosong"
                    return@launch
                }
                if (group.members.any { it.equals(target, ignoreCase = true) }) {
                    _message.value = "$target sudah menjadi member"
                    return@launch
                }
                val invitation = Invitation(
                    groupId = group.id,
                    groupName = group.name,
                    invitedEmail = target,
                    invitedBy = email
                )
                firebase.inviteUser(invitation)
                _message.value = "Undangan terkirim ke $target"
            } catch (e: Exception) {
                _message.value = "Gagal mengundang: ${e.message}"
            }
        }
    }

    fun respondInvitation(invitation: Invitation, accept: Boolean) {
        viewModelScope.launch {
            try {
                val updated = invitation.copy(
                    status = if (accept) "accepted" else "rejected"
                )
                firebase.updateInvitation(updated)
                if (accept) {
                    // Tambahkan ke member grup
                    val group = firebase.getGroup(invitation.groupId)
                    if (group != null) {
                        val email = invitation.invitedEmail
                        if (!group.members.any { it.equals(email, ignoreCase = true) }) {
                            firebase.updateGroup(
                                group.copy(members = group.members + email)
                            )
                        }
                    }
                    _message.value = "Bergabung ke \"${invitation.groupName}\""
                } else {
                    _message.value = "Undangan ditolak"
                }
                refresh()
            } catch (e: Exception) {
                _message.value = "Gagal: ${e.message}"
            }
        }
    }

    fun leaveGroup(group: Group) {
        viewModelScope.launch {
            try {
                val email = myEmail ?: return@launch
                val updated = group.copy(
                    members = group.members.filter { !it.equals(email, ignoreCase = true) },
                    admins = group.admins.filter { !it.equals(email, ignoreCase = true) }
                )
                firebase.updateGroup(updated)
                _message.value = "Keluar dari \"${group.name}\""
                refresh()
            } catch (e: Exception) {
                _message.value = "Gagal keluar: ${e.message}"
            }
        }
    }

    fun removeMember(group: Group, memberEmail: String) {
        viewModelScope.launch {
            try {
                val updated = group.copy(
                    members = group.members.filter { !it.equals(memberEmail, ignoreCase = true) },
                    admins = group.admins.filter { !it.equals(memberEmail, ignoreCase = true) }
                )
                firebase.updateGroup(updated)
                _message.value = "$memberEmail dikeluarkan"
                refresh()
            } catch (e: Exception) {
                _message.value = "Gagal: ${e.message}"
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            try {
                firebase.deleteGroup(groupId)
                _message.value = "Grup dihapus"
                refresh()
            } catch (e: Exception) {
                _message.value = "Gagal menghapus: ${e.message}"
            }
        }
    }
}
