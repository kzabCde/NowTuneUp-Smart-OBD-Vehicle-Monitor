package com.nowtuneup.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nowtuneup.app.data.local.dao.NtuDao
import com.nowtuneup.app.data.local.database.NtuDatabase
import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.data.transport.ObdTransportManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): NtuDatabase =
        Room.databaseBuilder(context, NtuDatabase::class.java, "ntu.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun dao(database: NtuDatabase): NtuDao = database.dao()

    @Provides
    @Singleton
    fun transport(manager: ObdTransportManager): ObdTransport = manager

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `time_slip_records` (
                    `id` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `testMode` TEXT NOT NULL,
                    `selectedDistanceTarget` TEXT,
                    `startedAtEpochMs` INTEGER NOT NULL,
                    `completedAtEpochMs` INTEGER,
                    `startMonotonicTimeMs` INTEGER NOT NULL,
                    `completedMonotonicTimeMs` INTEGER,
                    `elapsedMs` INTEGER NOT NULL,
                    `totalDistanceMeters` REAL NOT NULL,
                    `maximumSpeedKmh` REAL NOT NULL,
                    `maximumAccelerationMs2` REAL,
                    `dataSource` TEXT NOT NULL,
                    `obdDeviceName` TEXT,
                    `obdSampleRateHz` REAL,
                    `gpsSampleRateHz` REAL,
                    `averageGpsAccuracyMeters` REAL,
                    `sampleCount` INTEGER NOT NULL,
                    `droppedSampleCount` INTEGER NOT NULL,
                    `connectionInterruptions` INTEGER NOT NULL,
                    `gpsInterruptions` INTEGER NOT NULL,
                    `measurementQuality` TEXT NOT NULL,
                    `estimatedTimingErrorMs` REAL,
                    `vehicleName` TEXT,
                    `notes` TEXT,
                    `distanceCorrectionMeters` REAL NOT NULL,
                    `speedMilestonesJson` TEXT NOT NULL,
                    `distanceSplitsJson` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_time_slip_records_startedAtEpochMs` ON `time_slip_records` (`startedAtEpochMs`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_time_slip_records_testMode` ON `time_slip_records` (`testMode`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_time_slip_records_measurementQuality` ON `time_slip_records` (`measurementQuality`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_time_slip_records_vehicleName` ON `time_slip_records` (`vehicleName`)")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `time_slip_samples` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `recordId` TEXT NOT NULL,
                    `sequence` INTEGER NOT NULL,
                    `monotonicTimeMs` INTEGER NOT NULL,
                    `elapsedMs` INTEGER NOT NULL,
                    `obdSpeedKmh` REAL,
                    `gpsSpeedKmh` REAL,
                    `fusedSpeedKmh` REAL NOT NULL,
                    `accumulatedDistanceMeters` REAL NOT NULL,
                    `gpsAccuracyMeters` REAL,
                    `accelerationMs2` REAL,
                    `obdValid` INTEGER NOT NULL,
                    `gpsValid` INTEGER NOT NULL,
                    FOREIGN KEY(`recordId`) REFERENCES `time_slip_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_time_slip_samples_recordId` ON `time_slip_samples` (`recordId`)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_time_slip_samples_recordId_sequence` ON `time_slip_samples` (`recordId`, `sequence`)")
        }
    }
}
