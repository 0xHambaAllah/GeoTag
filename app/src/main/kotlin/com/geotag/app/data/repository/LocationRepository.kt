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
            for (remote in remoteLocations) {
                val local = dao.getLocationById(remote.id)
                if (local == null) {
                    dao.insertLocation(remote)
                }
            }
        } catch (_: Exception) {}
    }
}
