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

    suspend fun uploadPhoto(userId: String, locationId: String, photoFile: File, apiKey: String): String {
        // Upload to ImgBB (free, no credit card needed)
        return try {
            val client = okhttp3.OkHttpClient()
            val base64 = android.util.Base64.encodeToString(
                photoFile.readBytes(),
                android.util.Base64.NO_WRAP
            )
            
            val requestBody = okhttp3.FormBody.Builder()
                .add("key", apiKey)
                .add("image", base64)
                .add("name", locationId)
                .build()
            
            val request = okhttp3.Request.Builder()
                .url("https://api.imgbb.com/1/upload")
                .post(requestBody)
                .build()
            
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            
            if (response.isSuccessful) {
                val jsonResponse = org.json.JSONObject(response.body?.string() ?: "{}")
                val data = jsonResponse.getJSONObject("data")
                // PENTING: data.url adalah halaman viewer (HTML), bukan file gambar.
                // Pakai display_url (link langsung ke gambar) agar bisa dibuka Coil.
                val imageUrl = data.optString("display_url").ifEmpty {
                    data.optJSONObject("image")?.optString("url").orEmpty()
                }
                android.util.Log.d("FirebaseService", "Uploaded to ImgBB: $imageUrl")
                imageUrl
            } else {
                android.util.Log.e("FirebaseService", "ImgBB upload failed: ${response.code}")
                ""
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseService", "Failed to upload photo: ${e.message}")
            e.printStackTrace()
            ""
        }
    }

    suspend fun downloadPhoto(photoUrl: String, destinationFile: File): Boolean {
        // Download dari URL langsung ImgBB (display_url)
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
        // ImgBB photos don't need deletion (permanent links)
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
