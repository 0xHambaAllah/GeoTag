package com.geotag.app.data.model

/**
 * Posisi GPS realtime anggota grup.
 * Disimpan di Firestore collection "live_locations".
 */
data class LiveLocation(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val updatedAt: Long = 0L
)
