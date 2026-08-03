package com.nowtuneup.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nowtuneup.app.data.local.entity.DashboardProfileEntity
import com.nowtuneup.app.data.local.entity.DiagnosticScanEntity
import com.nowtuneup.app.data.local.entity.SampleEntity
import com.nowtuneup.app.data.local.entity.TimeSlipRecordEntity
import com.nowtuneup.app.data.local.entity.TimeSlipSampleEntity
import com.nowtuneup.app.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NtuDao {
    @Insert suspend fun startTrip(trip: TripEntity): Long
    @Query("UPDATE trips SET endTime=:end WHERE id=:id") suspend fun finishTrip(id: Long, end: Long)
    @Insert suspend fun insertSamples(samples: List<SampleEntity>)
    @Query("SELECT * FROM trips ORDER BY startTime DESC") fun trips(): Flow<List<TripEntity>>
    @Query("SELECT * FROM samples WHERE tripId=:tripId ORDER BY timestamp") suspend fun samples(tripId: Long): List<SampleEntity>

    @Insert suspend fun insertScan(scan: DiagnosticScanEntity)
    @Query("SELECT * FROM diagnostic_scans ORDER BY readAt DESC") fun scans(): Flow<List<DiagnosticScanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProfile(profile: DashboardProfileEntity)
    @Query("SELECT * FROM dashboard_profiles ORDER BY name") fun profiles(): Flow<List<DashboardProfileEntity>>
    @Query("DELETE FROM dashboard_profiles WHERE name=:name") suspend fun deleteProfile(name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeSlipRecord(record: TimeSlipRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeSlipSamples(samples: List<TimeSlipSampleEntity>)

    @Transaction
    suspend fun saveTimeSlip(record: TimeSlipRecordEntity, samples: List<TimeSlipSampleEntity>) {
        insertTimeSlipRecord(record)
        deleteTimeSlipSamples(record.id)
        if (samples.isNotEmpty()) insertTimeSlipSamples(samples)
    }

    @Query("SELECT * FROM time_slip_records ORDER BY startedAtEpochMs DESC")
    fun timeSlipRecords(): Flow<List<TimeSlipRecordEntity>>

    @Query("SELECT * FROM time_slip_records WHERE id=:id LIMIT 1")
    suspend fun timeSlipRecord(id: String): TimeSlipRecordEntity?

    @Query("SELECT * FROM time_slip_samples WHERE recordId=:recordId ORDER BY sequence")
    suspend fun timeSlipSamples(recordId: String): List<TimeSlipSampleEntity>

    @Query("DELETE FROM time_slip_samples WHERE recordId=:recordId")
    suspend fun deleteTimeSlipSamples(recordId: String)

    @Query("DELETE FROM time_slip_records WHERE id=:id")
    suspend fun deleteTimeSlipRecord(id: String)

    @Query("DELETE FROM time_slip_records")
    suspend fun clearTimeSlipRecords()

    @Query("DELETE FROM samples") suspend fun clearSamples()
    @Query("DELETE FROM trips") suspend fun clearTrips()
    @Query("DELETE FROM diagnostic_scans") suspend fun clearScans()
}
