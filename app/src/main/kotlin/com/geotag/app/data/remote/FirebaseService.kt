package com.geotag.app.data.remote

import android.net.Uri
import com.geotag.app.data.model.GeoLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

class FirebaseService {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
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
                        okhttp3.MediaType.parse("image/jpeg"),
                        photoFile
                    )
                )
                .build()
            
            val request = okhttp3.Request.Builder()
                .url("https://api.imgbb.com/1/upload")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body()?.string() ?: throw Exception("Empty response")
            
            if (!response.isSuccessful) {
                throw Exception("Upload failed: ${response.code()}")
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
        database.reference
            .child("locations")
            .child(location.userId)
            .child(location.id)
            .setValue(mapOf(
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
        database.reference
            .child("locations")
            .child(userId)
            .child(locationId)
            .removeValue().await()
        try {
            storage.reference.child("photos/$userId/$locationId.jpg").delete().await()
        } catch (_: Exception) {}
    }

    suspend fun getRemoteLocations(userId: String): List<GeoLocation> {
        val snapshot = database.reference
            .child("locations")
            .child(userId)
            .get()
            .await()
        return snapshot.children.mapNotNull { child ->
            try {
                GeoLocation(
                    id = child.child("id").getValue(String::class.java) ?: return@mapNotNull null,
                    userId = child.child("userId").getValue(String::class.java) ?: "",
                    name = child.child("name").getValue(String::class.java) ?: "",
                    phoneNumber = child.child("phoneNumber").getValue(String::class.java) ?: "",
                    photoUrl = child.child("photoUrl").getValue(String::class.java) ?: "",
                    latitude = child.child("latitude").getValue(Double::class.java) ?: 0.0,
                    longitude = child.child("longitude").getValue(Double::class.java) ?: 0.0,
                    address = child.child("address").getValue(String::class.java) ?: "",
                    timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L,
                    isSynced = true
                )
            } catch (_: Exception) { null }
        }
    }

    suspend fun updateLocation(location: GeoLocation) {
        database.reference
            .child("locations")
            .child(location.userId)
            .child(location.id)
            .updateChildren(mapOf(
                "name" to location.name,
                "phoneNumber" to location.phoneNumber,
                "address" to location.address
            )).await()
    }
}
