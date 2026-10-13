package com.geotag.app.data.repository

import android.content.Context
import com.geotag.app.data.local.GeoTagDatabase
import com.geotag.app.data.model.GeoLocation
import com.geotag.app.data.remote.FirebaseService
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
        dao.updateLocation(location)
        try {
            firebase.updateLocation(location)
        } catch (_: Exception) {}
    }

    suspend fun deleteLocation(location: GeoLocation) {
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
                    // Download photo from ImgBB if URL exists
                    var localPath = remote.photoLocalPath
                    if (remote.photoUrl.isNotEmpty()) {
                        try {
                            localPath = downloadPhoto(remote.photoUrl, remote.id)
                            downloadedCount++
                        } catch (e: Exception) {
                            e.printStackTrace()
                            android.util.Log.e("LocationRepository", "Failed to download photo: ${e.message}")
                        }
                    }
                    dao.insertLocation(remote.copy(photoLocalPath = localPath))
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
    
    private suspend fun downloadPhoto(url: String, locationId: String): String {
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url(url)
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Download failed: ${response.code}")
        }
        
        // Save to app storage with ID as filename
        val photoDir = File(context.getExternalFilesDir(null), "photos")
        if (!photoDir.exists()) photoDir.mkdirs()
        
        val photoFile = File(photoDir, "${locationId}.jpg")
        
        // Download and save
        response.body?.byteStream()?.use { input ->
            photoFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        android.util.Log.d("LocationRepository", "Downloaded photo to: ${photoFile.absolutePath}")
        
        return photoFile.absolutePath
    }
}
