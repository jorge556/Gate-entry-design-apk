package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PlateRecord::class], version = 1, exportSchema = false)
abstract class PlateDatabase : RoomDatabase() {
    abstract fun plateDao(): PlateDao

    companion object {
        @Volatile
        private var INSTANCE: PlateDatabase? = null

        fun getDatabase(context: Context): PlateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PlateDatabase::class.java,
                    "plate_sentinel_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
