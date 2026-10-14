package com.geotag.app.data.remote

import android.net.Uri
import com.geotag.app.data.model.GeoLocation
import com.geotag.app.data.model.Group
import com.geotag.app.data.model.Invitation
import com.geotag.app.data.model.UserProfile
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

    // ===== GROUP METHODS =====

    suspend fun createGroup(group: Group) {
        firestore.collection("groups")
            .document(group.id)
            .set(group)
            .await()
    }

    suspend fun getMyGroups(email: String): List<Group> {
        return firestore.collection("groups")
            .whereArrayContains("members", email)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Group::class.java) }
    }

    suspend fun getGroup(groupId: String): Group? {
        return firestore.collection("groups")
            .document(groupId)
            .get()
            .await()
            .toObject(Group::class.java)
    }

    suspend fun updateGroup(group: Group) {
        firestore.collection("groups")
            .document(group.id)
            .set(group)
            .await()
    }

    suspend fun deleteGroup(groupId: String) {
        firestore.collection("groups")
            .document(groupId)
            .delete()
            .await()
    }

    suspend fun inviteUser(invitation: Invitation) {
        firestore.collection("invitations")
            .document(invitation.id)
            .set(invitation)
            .await()
    }

    suspend fun getMyInvitations(email: String): List<Invitation> {
        return firestore.collection("invitations")
            .whereEqualTo("invitedEmail", email)
            .whereEqualTo("status", "pending")
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Invitation::class.java) }
    }

    suspend fun updateInvitation(invitation: Invitation) {
        firestore.collection("invitations")
            .document(invitation.id)
            .set(invitation)
            .await()
    }

    /** Ambil lokasi dari beberapa user (untuk tampilan gabungan grup) */
    suspend fun getLocationsByEmails(emails: List<String>): List<GeoLocation> {
        if (emails.isEmpty()) return emptyList()
        val result = mutableListOf<GeoLocation>()
        // Firestore whereIn maksimal 10, batch jika perlu
        emails.chunked(10).forEach { chunk ->
            val docs = firestore.collection("locations")
                .whereIn("creatorEmail", chunk)
                .get()
                .await()
                .documents
            docs.mapNotNullTo(result) { it.toObject(GeoLocation::class.java)?.copy(isSynced = true) }
        }
        return result
    }

    /**
     * Backfill creatorEmail untuk lokasi lama yang belum punya.
     * Dipanggil saat login agar data lama bisa muncul di grup.
     */
    suspend fun backfillCreatorEmail(userId: String, email: String) {
        try {
            val docs = firestore.collection("locations")
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
            for (doc in docs) {
                if (doc.getString("creatorEmail").isNullOrEmpty()) {
                    doc.reference.update("creatorEmail", email).await()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseService", "Backfill gagal: ${e.message}")
        }
    }

    // ===== USER PROFILES (untuk picker undangan) =====

    suspend fun saveUserProfile(uid: String, email: String, displayName: String?) {
        try {
            firestore.collection("users")
                .document(uid)
                .set(
                    mapOf(
                        "email" to email,
                        "displayName" to (displayName ?: ""),
                        "lastSeen" to System.currentTimeMillis()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
        } catch (e: Exception) {
            android.util.Log.e("FirebaseService", "Save profile gagal: ${e.message}")
        }
    }

    suspend fun getAllUsers(): List<UserProfile> {
        return try {
            firestore.collection("users")
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val email = doc.getString("email") ?: return@mapNotNull null
                    UserProfile(
                        uid = doc.id,
                        email = email,
                        displayName = doc.getString("displayName") ?: ""
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
