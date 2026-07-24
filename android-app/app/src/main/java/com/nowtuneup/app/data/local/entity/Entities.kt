package com.nowtuneup.app.data.local.entity
import androidx.room.*
@Entity(tableName="trips") data class TripEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val startTime:Long,val endTime:Long?=null)
@Entity(tableName="samples",foreignKeys=[ForeignKey(entity=TripEntity::class,parentColumns=["id"],childColumns=["tripId"],onDelete=ForeignKey.CASCADE)],indices=[Index("tripId"),Index(value=["tripId","timestamp"])]) data class SampleEntity(@PrimaryKey(autoGenerate=true)val id:Long=0,val tripId:Long,val timestamp:Long,val rpm:Double?,val speedKmh:Double?,val coolantTempC:Double?,val voltageV:Double?,val engineLoadPercent:Double?,val throttlePercent:Double?)
@Entity(tableName="diagnostic_scans",indices=[Index("readAt")]) data class DiagnosticScanEntity(@PrimaryKey(autoGenerate=true)val id:Long=0,val readAt:Long,val codes:String,val raw:String)
@Entity(tableName="dashboard_profiles",indices=[Index(value=["name"],unique=true)]) data class DashboardProfileEntity(@PrimaryKey(autoGenerate=true)val id:Long=0,val name:String,val widgetsJson:String)
