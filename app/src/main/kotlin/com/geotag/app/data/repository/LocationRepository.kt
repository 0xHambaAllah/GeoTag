package com.geotag.app.data.repository

import android.content.Context
import com.geotag.app.data.local.GeoTagDatabase
import com.geotag.app.data.model.GeoLocation
import com.geotag.app.data.remote.FirebaseService
import com.geotag.app.util.AppConstants
import com.geotag.app.data.remote.SyncWorker
import kotlinx.coroutines.flow.Flow
import java.io.File

class LocationRepository(private val context: Context) {
    private val db = GeoTagDatabase.getDatabase(context)
    private val dao = db.locationDao()
    val firebase = FirebaseService()

    fun getLocations(userId: String): Flow<List<GeoLocation>> {
        return dao.getLocationsByUser(userId)
    }

    fun searchLocations(userId: String, query: String): Flow<List<GeoLocation>> {
        return dao.searchLocations(userId, query)
    }

    suspend fun saveLocation(location: GeoLocation) {
        dao.insertLocation(location)
        SyncWorker.enqueue(context)
    }

    suspend fun updateLocation(location: GeoLocation) {
        if (!canManage(location)) return
        dao.updateLocation(location)
        try {
            firebase.updateLocation(location)
        } catch (_: Exception) {}
    }

    /**
     * Hanya pemilik data atau Admin App yang boleh edit/hapus.
     * Data grup selalu punya creatorEmail (query grup pakai field itu),
     * jadi creatorEmail kosong = data lokal sendiri.
     */
    private fun canManage(location: GeoLocation): Boolean {
        val email = firebase.currentUserEmail ?: return false
        if (AppConstants.isAdminApp(email)) return true
        if (location.creatorEmail.isEmpty()) return true
        return location.creatorEmail.equals(email, ignoreCase = true)
    }

    suspend fun deleteLocation(location: GeoLocation) {
        if (!canManage(location)) return
        dao.deleteLocation(location)
        try {
            firebase.deleteLocation(location.id, location.userId)
        } catch (_: Exception) {}
        // Delete local photo
        if (location.photoLocalPath.isNotEmpty()) {
            File(location.photoLocalPath).delete()
        }
    }

    suspend fun syncFromRemote(userId: String) {
        try {
            val remoteLocations = firebase.getRemoteLocations(userId)
            var downloadedCount = 0
            
            for (remote in remoteLocations) {
                val local = dao.getLocationById(remote.id)
                if (local == null) {
                    // Download photo from Firebase Storage
                    var localPath = ""
                    if (remote.photoUrl.isNotEmpty()) {
                        try {
                            val photoDir = File(context.getExternalFilesDir(null), "photos")
                            if (!photoDir.exists()) photoDir.mkdirs()
                            
                            val photoFile = File(photoDir, "${remote.id}.jpg")
                            
                            // Download dari URL langsung Cloudinary
                            val success = firebase.downloadPhoto(remote.photoUrl, photoFile)
                            if (success) {
                                localPath = photoFile.absolutePath
                                downloadedCount++
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            android.util.Log.e("LocationRepository", "Failed to download photo: ${e.message}")
                        }
                    }
                    dao.insertLocation(remote.copy(photoLocalPath = localPath, isSynced = true))
                }
            }
            
            if (downloadedCount > 0) {
                android.widget.Toast.makeText(
                    context,
                    "Restore selesai: $downloadedCount foto berhasil didownload",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationRepository", "Sync failed: ${e.message}")
            android.widget.Toast.makeText(
                context,
                "Restore gagal: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}
