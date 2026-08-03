package com.nowtuneup.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nowtuneup.app.data.local.dao.NtuDao
import com.nowtuneup.app.data.local.entity.DashboardProfileEntity
import com.nowtuneup.app.data.local.entity.DiagnosticScanEntity
import com.nowtuneup.app.data.local.entity.SampleEntity
import com.nowtuneup.app.data.local.entity.TimeSlipRecordEntity
import com.nowtuneup.app.data.local.entity.TimeSlipSampleEntity
import com.nowtuneup.app.data.local.entity.TripEntity

@Database(
    entities = [
        TripEntity::class,
        SampleEntity::class,
        DiagnosticScanEntity::class,
        DashboardProfileEntity::class,
        TimeSlipRecordEntity::class,
        TimeSlipSampleEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class NtuDatabase : RoomDatabase() {
    abstract fun dao(): NtuDao
}
