package org.sahara.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MicroReportEntity::class,
        IncidentEntity::class,
        EvidenceEntryEntity::class,
        DetectionEventEntity::class,
        NotifyContactEntity::class,
        AuditEventEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SaharaDatabase : RoomDatabase() {
    abstract fun microReportDao(): MicroReportDao
    abstract fun incidentDao(): IncidentDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun detectionEventDao(): DetectionEventDao
    abstract fun notifyContactDao(): NotifyContactDao
    abstract fun auditEventDao(): AuditEventDao

    companion object {
        @Volatile
        private var INSTANCE: SaharaDatabase? = null

        fun getDatabase(context: Context): SaharaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SaharaDatabase::class.java,
                    "sahara_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
