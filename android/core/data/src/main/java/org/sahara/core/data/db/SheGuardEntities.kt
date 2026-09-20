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

@Entity(tableName = "spatio_temporal_patterns")
data class SpatioTemporalPatternEntity(
    @PrimaryKey val patternId: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusMeters: Double,
    val category: String,
    val firstReportedAt: Long,
    val lastReportedAt: Long,
    val reportCount: Int,
    val contributingReportIdsJson: String,
    val trustScore: Float,
    val state: String
)

@Entity(tableName = "rising_pattern_alerts")
data class RisingPatternAlertEntity(
    @PrimaryKey val alertId: String,
    val patternId: String,
    val category: String,
    val approximateLocation: String,
    val timeWindow: String,
    val trustLevel: String,
    val trustScore: Float,
    val createdAt: Long,
    val disclaimer: String,
    val isRelayed: Boolean = false
)
