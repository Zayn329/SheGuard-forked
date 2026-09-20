package org.sahara.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "micro_reports")
data class MicroReportEntity(
    @PrimaryKey val reportId: String,
    val anonymousReporterToken: String,
    val category: String,
    val latitude: Double?,
    val longitude: Double?,
    val approximateArea: String,
    val timestamp: Long,
    val contextDescription: String?,
    val syncStatus: String
)
