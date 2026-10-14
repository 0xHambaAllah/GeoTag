package com.geotag.app.data.model

/**
 * Profil user terdaftar (untuk picker undangan grup).
 * Disimpan di Firestore collection "users".
 */
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = ""
)
