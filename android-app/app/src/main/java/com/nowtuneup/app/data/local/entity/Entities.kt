package com.nowtuneup.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
)

@Entity(
    tableName = "samples",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId"), Index(value = ["tripId", "timestamp"])],
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val timestamp: Long,
    val rpm: Double?,
    val speedKmh: Double?,
    val coolantTempC: Double?,
    val voltageV: Double?,
    val engineLoadPercent: Double?,
    val throttlePercent: Double?,
)

@Entity(tableName = "diagnostic_scans", indices = [Index("readAt")])
data class DiagnosticScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val readAt: Long,
    val codes: String,
    val raw: String,
)

@Entity(tableName = "dashboard_profiles", indices = [Index(value = ["name"], unique = true)])
data class DashboardProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val widgetsJson: String,
)

@Entity(
    tableName = "time_slip_records",
    indices = [
        Index("startedAtEpochMs"),
        Index("testMode"),
        Index("measurementQuality"),
        Index("vehicleName"),
    ],
)
data class TimeSlipRecordEntity(
    @PrimaryKey val id: String,
    val status: String,
    val testMode: String,
    val selectedDistanceTarget: String?,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val startMonotonicTimeMs: Long,
    val completedMonotonicTimeMs: Long?,
    val elapsedMs: Long,
    val totalDistanceMeters: Double,
    val maximumSpeedKmh: Double,
    val maximumAccelerationMs2: Double?,
    val dataSource: String,
    val obdDeviceName: String?,
    val obdSampleRateHz: Double?,
    val gpsSampleRateHz: Double?,
    val averageGpsAccuracyMeters: Double?,
    val sampleCount: Int,
    val droppedSampleCount: Int,
    val connectionInterruptions: Int,
    val gpsInterruptions: Int,
    val measurementQuality: String,
    val estimatedTimingErrorMs: Double?,
    val vehicleName: String?,
    val notes: String?,
    val distanceCorrectionMeters: Double,
    val speedMilestonesJson: String,
    val distanceSplitsJson: String,
)

@Entity(
    tableName = "time_slip_samples",
    foreignKeys = [
        ForeignKey(
            entity = TimeSlipRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recordId"), Index(value = ["recordId", "sequence"], unique = true)],
)
data class TimeSlipSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: String,
    val sequence: Int,
    val monotonicTimeMs: Long,
    val elapsedMs: Long,
    val obdSpeedKmh: Double?,
    val gpsSpeedKmh: Double?,
    val fusedSpeedKmh: Double,
    val accumulatedDistanceMeters: Double,
    val gpsAccuracyMeters: Double?,
    val accelerationMs2: Double?,
    val obdValid: Boolean,
    val gpsValid: Boolean,
)
