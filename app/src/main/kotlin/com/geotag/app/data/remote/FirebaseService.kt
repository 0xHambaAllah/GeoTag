package com.geotag.app.data.remote

import android.net.Uri
import com.geotag.app.data.model.GeoLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun uploadPhoto(userId: String, locationId: String, photoFile: File, cloudName: String, uploadPreset: String): String {
        // Upload to Cloudinary (free, no credit card needed)
        return try {
            val client = okhttp3.OkHttpClient()

            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("upload_preset", uploadPreset)
                .addFormDataPart("public_id", locationId)
                .addFormDataPart(
                    "file", photoFile.name,
                    okhttp3.RequestBody.create("image/jpeg".toMediaType(), photoFile)
                )
                .build()

            val request = okhttp3.Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                .post(requestBody)
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (response.isSuccessful) {
                val jsonResponse = org.json.JSONObject(response.body?.string() ?: "{}")
                val imageUrl = jsonResponse.optString("secure_url")
                android.util.Log.d("FirebaseService", "Uploaded to Cloudinary: $imageUrl")
                imageUrl
            } else {
                android.util.Log.e("FirebaseService", "Cloudinary upload failed: ${response.code}")
                ""
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseService", "Failed to upload photo: ${e.message}")
            e.printStackTrace()
            ""
        }
    }

    suspend fun downloadPhoto(photoUrl: String, destinationFile: File): Boolean {
        // Download dari URL langsung Cloudinary
        return try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(photoUrl)
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (response.isSuccessful) {
                response.body?.byteStream()?.use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                android.util.Log.d("FirebaseService", "Downloaded to: ${destinationFile.absolutePath}")
                true
            } else {
                android.util.Log.e("FirebaseService", "Download failed: ${response.code}")
                false
            }
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
        // Cloudinary photos don't need deletion (permanent links)
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
