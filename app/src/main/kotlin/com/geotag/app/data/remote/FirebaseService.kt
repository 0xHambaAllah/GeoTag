package com.geotag.app.data.remote

import android.net.Uri
import com.geotag.app.data.model.GeoLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

class FirebaseService {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val currentUserEmail: String?
        get() = auth.currentUser?.email

    val currentUserName: String?
        get() = auth.currentUser?.displayName

    val isSignedIn: Boolean
        get() = auth.currentUser != null

    fun signOut() {
        auth.signOut()
    }

    suspend fun uploadPhoto(userId: String, locationId: String, photoFile: File): String {
        val ref = storage.reference.child("photos/$userId/$locationId.jpg")
        ref.putFile(Uri.fromFile(photoFile)).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun saveLocation(location: GeoLocation) {
        firestore.collection("locations")
            .document(location.id)
            .set(mapOf(
                "id" to location.id,
                "userId" to location.userId,
                "name" to location.name,
                "phoneNumber" to location.phoneNumber,
                "photoUrl" to location.photoUrl,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "address" to location.address,
                "timestamp" to location.timestamp
            )).await()
    }

    suspend fun deleteLocation(locationId: String, userId: String) {
        firestore.collection("locations").document(locationId).delete().await()
        try {
            storage.reference.child("photos/$userId/$locationId.jpg").delete().await()
        } catch (_: Exception) {}
    }

    suspend fun getRemoteLocations(userId: String): List<GeoLocation> {
        val snapshot = firestore.collection("locations")
            .whereEqualTo("userId", userId)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            try {
                GeoLocation(
                    id = doc.getString("id") ?: return@mapNotNull null,
                    userId = doc.getString("userId") ?: "",
                    name = doc.getString("name") ?: "",
                    phoneNumber = doc.getString("phoneNumber") ?: "",
                    photoUrl = doc.getString("photoUrl") ?: "",
                    latitude = doc.getDouble("latitude") ?: 0.0,
                    longitude = doc.getDouble("longitude") ?: 0.0,
                    address = doc.getString("address") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L,
                    isSynced = true
                )
            } catch (_: Exception) { null }
        }
    }

    suspend fun updateLocation(location: GeoLocation) {
        firestore.collection("locations")
            .document(location.id)
            .update(mapOf(
                "name" to location.name,
                "phoneNumber" to location.phoneNumber,
                "address" to location.address
            )).await()
    }
}
