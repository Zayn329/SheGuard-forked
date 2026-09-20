package org.sahara.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MicroReportEntity::class,
        SpatioTemporalPatternEntity::class,
        IncidentEntity::class,
        EvidenceEntryEntity::class,
        DetectionEventEntity::class,
        NotifyContactEntity::class,
        AuditEventEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SaharaDatabase : RoomDatabase() {
    abstract fun microReportDao(): MicroReportDao
    abstract fun patternDao(): SpatioTemporalPatternDao
    abstract fun incidentDao(): IncidentDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun detectionEventDao(): DetectionEventDao
    abstract fun notifyContactDao(): NotifyContactDao
    abstract fun auditEventDao(): AuditEventDao

    companion object {
        @Volatile
        private var INSTANCE: SaharaDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `micro_reports` (
                        `reportId` TEXT NOT NULL,
                        `anonymousReporterToken` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `approximateArea` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `contextDescription` TEXT,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`reportId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `spatio_temporal_patterns` (
                        `patternId` TEXT NOT NULL,
                        `centerLatitude` REAL NOT NULL,
                        `centerLongitude` REAL NOT NULL,
                        `radiusMeters` REAL NOT NULL,
                        `category` TEXT NOT NULL,
                        `firstReportedAt` INTEGER NOT NULL,
                        `lastReportedAt` INTEGER NOT NULL,
                        `reportCount` INTEGER NOT NULL,
                        `contributingReportIdsJson` TEXT NOT NULL,
                        `trustScore` REAL NOT NULL,
                        `state` TEXT NOT NULL,
                        PRIMARY KEY(`patternId`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): SaharaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SaharaDatabase::class.java,
                    "sahara_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
