package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        MediaAssetEntity::class,
        EditHistoryEntity::class,
        AiProviderProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VoxCutDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun mediaAssetDao(): MediaAssetDao
    abstract fun editHistoryDao(): EditHistoryDao
    abstract fun aiProviderDao(): AiProviderDao

    companion object {
        @Volatile
        private var INSTANCE: VoxCutDatabase? = null

        fun getInstance(context: Context): VoxCutDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VoxCutDatabase::class.java,
                    "voxcut_studio.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
