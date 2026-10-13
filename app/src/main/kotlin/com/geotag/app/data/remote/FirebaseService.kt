package com.geotag.app.data.remote

import android.net.Uri
import com.geotag.app.data.model.GeoLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
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
        // Upload to Firebase Storage (reliable, unlimited)
        return try {
            val storageRef = storage.reference
                .child("photos/$userId/$locationId.jpg")
            
            // Upload file
            storageRef.putFile(Uri.fromFile(photoFile)).await()
            
            // Get download URL
            val downloadUrl = storageRef.downloadUrl.await()
            downloadUrl.toString()
        } catch (e: Exception) {
            android.util.Log.e("FirebaseService", "Upload failed: ${e.message}")
            throw Exception("Photo upload failed: ${e.message}")
        }
    }
    
    suspend fun downloadPhoto(userId: String, locationId: String, destinationFile: File): Boolean {
        // Download from Firebase Storage
        return try {
            val storageRef = storage.reference
                .child("photos/$userId/$locationId.jpg")
            
            storageRef.getFile(destinationFile).await()
            android.util.Log.d("FirebaseService", "Downloaded to: ${destinationFile.absolutePath}")
            true
        } catch (e: Exception) {
            android.util.Log.e("FirebaseService", "Download failed: ${e.message}")
            false
        }
    }

    suspend fun saveLocation(location: GeoLocation) {
        firestore.collection("locations")
            .document(location.id)
            .set(location)
            .await()
    }

    suspend fun deleteLocation(locationId: String, userId: String) {
        firestore.collection("locations")
            .document(locationId)
            .delete()
            .await()
        try {
            storage.reference.child("photos/$userId/$locationId.jpg").delete().await()
        } catch (_: Exception) {}
    }

    suspend fun getRemoteLocations(userId: String): List<GeoLocation> {
        return firestore.collection("locations")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(GeoLocation::class.java)?.copy(isSynced = true)
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
