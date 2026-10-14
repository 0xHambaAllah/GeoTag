package com.geotag.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.geotag.app.data.model.GeoLocation

@Database(entities = [GeoLocation::class], version = 2, exportSchema = false)
abstract class GeoTagDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile
        private var INSTANCE: GeoTagDatabase? = null

        fun getDatabase(context: Context): GeoTagDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GeoTagDatabase::class.java,
                    "geotag_database"
                )
                    .fallbackToDestructiveMigration() // v1->v2: tambah creatorEmail (data aman di cloud)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
