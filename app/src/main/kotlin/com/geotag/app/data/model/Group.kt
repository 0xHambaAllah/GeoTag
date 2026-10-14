package com.geotag.app.data.model

import java.util.UUID

/**
 * Model grup untuk berbagi data lokasi antar user.
 * Disimpan di Firestore collection "groups".
 */
data class Group(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val createdBy: String = "", // email pembuat
    val admins: List<String> = emptyList(), // email admin grup
    val members: List<String> = emptyList(), // email semua member (termasuk admin)
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Model undangan grup.
 * Disimpan di Firestore collection "invitations".
 */
data class Invitation(
    val id: String = UUID.randomUUID().toString(),
    val groupId: String = "",
    val groupName: String = "",
    val invitedEmail: String = "",
    val invitedBy: String = "", // email pengundang
    val status: String = "pending", // pending, accepted, rejected
    val createdAt: Long = System.currentTimeMillis()
)
