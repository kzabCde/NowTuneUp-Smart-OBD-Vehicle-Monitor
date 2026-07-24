package com.nowtuneup.app.data.local.database
import androidx.room.Database
import androidx.room.RoomDatabase
import com.nowtuneup.app.data.local.dao.NtuDao
import com.nowtuneup.app.data.local.entity.*
@Database(entities=[TripEntity::class,SampleEntity::class,DiagnosticScanEntity::class,DashboardProfileEntity::class],version=1,exportSchema=true)
abstract class NtuDatabase:RoomDatabase(){abstract fun dao():NtuDao}
