package com.geotag.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "locations")
data class GeoLocation(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val creatorEmail: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val photoLocalPath: String = "",
    val photoUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
