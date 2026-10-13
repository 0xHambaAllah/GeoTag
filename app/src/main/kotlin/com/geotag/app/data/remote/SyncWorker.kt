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

        try {
            val unsyncedLocations = dao.getUnsyncedLocations(userId)
            
            if (unsyncedLocations.isEmpty()) {
                android.util.Log.d("SyncWorker", "No unsynced locations")
                return Result.success()
            }
            
            android.util.Log.d("SyncWorker", "Syncing ${unsyncedLocations.size} locations...")
            
            // Show notification: starting
            showNotification("Backup sedang berjalan", "Mengupload ${unsyncedLocations.size} foto ke cloud...")
            
            var successCount = 0

            for (location in unsyncedLocations) {
                try {
                    var photoUrl = location.photoUrl
                    if (location.photoLocalPath.isNotEmpty() && photoUrl.isEmpty()) {
                        val file = File(location.photoLocalPath)
                        if (file.exists()) {
                            val compressed = PhotoUtils.compressPhoto(applicationContext, file)
                            // Upload to Firebase Storage (no API key needed)
                            photoUrl = firebase.uploadPhoto(userId, location.id, compressed)
                            android.util.Log.d("SyncWorker", "Uploaded photo for ${location.id}")
                        }
                    }
                    val synced = location.copy(photoUrl = photoUrl, isSynced = true)
                    firebase.saveLocation(synced)
                    dao.updateLocation(synced)
                    successCount++
                } catch (e: Exception) {
                    android.util.Log.e("SyncWorker", "Failed to sync ${location.id}: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            // Show notification: completed
            if (successCount > 0) {
                showNotification("Backup selesai ✓", "$successCount foto berhasil tersimpan ke cloud")
            }
            
            return Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync worker failed: ${e.message}")
            showNotification("Backup gagal", "Error: ${e.message}")
            return Result.failure()
        }
    }
    
    private fun showNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        // Create channel for Android O+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "geotag_sync",
                "Backup Foto",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, "geotag_sync")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
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
