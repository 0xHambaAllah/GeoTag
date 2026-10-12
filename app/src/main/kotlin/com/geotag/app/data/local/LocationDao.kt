package com.geotag.app.data.local

import androidx.room.*
import com.geotag.app.data.model.GeoLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations WHERE userId = :userId ORDER BY timestamp DESC")
    fun getLocationsByUser(userId: String): Flow<List<GeoLocation>>

    @Query("SELECT * FROM locations WHERE userId = :userId AND (name LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchLocations(userId: String, query: String): Flow<List<GeoLocation>>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getLocationById(id: String): GeoLocation?

    @Query("SELECT * FROM locations WHERE isSynced = 0 AND userId = :userId")
    suspend fun getUnsyncedLocations(userId: String): List<GeoLocation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: GeoLocation)

    @Update
    suspend fun updateLocation(location: GeoLocation)

    @Delete
    suspend fun deleteLocation(location: GeoLocation)

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteById(id: String)
}
