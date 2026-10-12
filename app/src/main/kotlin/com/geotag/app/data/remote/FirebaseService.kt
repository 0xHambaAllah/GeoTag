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

    suspend fun uploadPhoto(userId: String, locationId: String, photoFile: File, apiKey: String): String {
        // Upload to ImgBB API instead of Firebase Storage
        return try {
            val client = okhttp3.OkHttpClient()
            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("key", apiKey)
                .addFormDataPart(
                    "image",
                    photoFile.name,
                    okhttp3.RequestBody.create(
                        "image/jpeg".toMediaType(),
                        photoFile
                    )
                )
                .build()
            
            val request = okhttp3.Request.Builder()
                .url("https://api.imgbb.com/1/upload")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            
            if (!response.isSuccessful) {
                throw Exception("Upload failed: ${response.code}")
            }
            
            // Parse JSON response to extract image URL
            val json = org.json.JSONObject(responseBody)
            if (json.getBoolean("success")) {
                json.getJSONObject("data").getString("url")
            } else {
                throw Exception("ImgBB upload failed")
            }
        } catch (e: Exception) {
            // Fallback: return empty string or throw
            throw Exception("Photo upload failed: ${e.message}")
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
