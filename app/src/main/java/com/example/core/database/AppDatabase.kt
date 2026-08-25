package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    EvidenceEntity::class,
    DetectedSignalEntity::class,
    DiscoveredDeviceEntity::class,
    ReconLogEntity::class
  ],
  version = 3,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun evidenceDao(): EvidenceDao
  abstract fun detectedSignalDao(): DetectedSignalDao
  abstract fun discoveredDeviceDao(): DiscoveredDeviceDao
  abstract fun reconLogDao(): ReconLogDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "redeye_evidence_db"
        ).fallbackToDestructiveMigration(true).build()
        INSTANCE = instance
        instance
      }
    }
  }
}
