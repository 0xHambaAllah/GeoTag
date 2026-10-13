package com.geotag.app.data.remote

import android.content.Context
import androidx.work.*
import com.geotag.app.R
import com.geotag.app.data.local.GeoTagDatabase
import com.geotag.app.util.PhotoUtils
import java.io.File

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val firebase = FirebaseService()
        val userId = firebase.currentUserId ?: return Result.failure()
        val db = GeoTagDatabase.getDatabase(applicationContext)
        val dao = db.locationDao()

        val unsyncedLocations = dao.getUnsyncedLocations(userId)

        for (location in unsyncedLocations) {
            try {
                var photoUrl = location.photoUrl
                if (location.photoLocalPath.isNotEmpty() && photoUrl.isEmpty()) {
                    val file = File(location.photoLocalPath)
                    if (file.exists()) {
                        val compressed = PhotoUtils.compressPhoto(applicationContext, file)
                        // Upload to Firebase Storage (no API key needed)
                        photoUrl = firebase.uploadPhoto(userId, location.id, compressed)
                    }
                }
                val synced = location.copy(photoUrl = photoUrl, isSynced = true)
                firebase.saveLocation(synced)
                dao.updateLocation(synced)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync_locations",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
