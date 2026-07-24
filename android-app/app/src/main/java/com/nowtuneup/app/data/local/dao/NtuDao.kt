package com.nowtuneup.app.data.local.dao
import androidx.room.*
import com.nowtuneup.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow
@Dao interface NtuDao {
 @Insert suspend fun startTrip(trip:TripEntity):Long
 @Query("UPDATE trips SET endTime=:end WHERE id=:id") suspend fun finishTrip(id:Long,end:Long)
 @Insert suspend fun insertSamples(samples:List<SampleEntity>)
 @Query("SELECT * FROM trips ORDER BY startTime DESC") fun trips():Flow<List<TripEntity>>
 @Query("SELECT * FROM samples WHERE tripId=:tripId ORDER BY timestamp") suspend fun samples(tripId:Long):List<SampleEntity>
 @Insert suspend fun insertScan(scan:DiagnosticScanEntity)
 @Query("SELECT * FROM diagnostic_scans ORDER BY readAt DESC") fun scans():Flow<List<DiagnosticScanEntity>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveProfile(profile:DashboardProfileEntity)
 @Query("SELECT * FROM dashboard_profiles ORDER BY name") fun profiles():Flow<List<DashboardProfileEntity>>
 @Query("DELETE FROM dashboard_profiles WHERE name=:name") suspend fun deleteProfile(name:String)
 @Query("DELETE FROM samples") suspend fun clearSamples(); @Query("DELETE FROM trips") suspend fun clearTrips(); @Query("DELETE FROM diagnostic_scans") suspend fun clearScans()
}
