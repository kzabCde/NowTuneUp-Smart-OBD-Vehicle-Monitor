package com.nowtuneup.app.presentation

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.nowtuneup.app.domain.model.VehicleReading
import com.nowtuneup.app.feature.live.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class TelemetryPersistenceTest {
    @Test fun recordingReopensWithSourceTimestampsAndDeletesItsOwnFile(): Unit = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "telemetry-test-${UUID.randomUUID()}"
        val folder = File(base.cacheDir, prefix).apply { mkdirs() }
        val isolated = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$prefix-$name", mode)
            override fun getFilesDir(): File = folder
        }
        val repository = LiveSessionRepository(isolated)
        repository.start(source = "MOCK — instrumentation fixture")
        val sourceTime = SystemClock.elapsedRealtimeNanos()
        val reading = VehicleReading(0x0D, "Speed", 42.0, "km/h", sourceTimeNanos = sourceTime)
        repository.ingest(listOf(reading)); repository.ingest(listOf(reading))
        val summary = repository.stop()!!
        withTimeout(15_000) { repository.history.first { it.any { saved -> saved.recordingId == summary.recordingId } } }
        val reopened = LiveSessionRepository(isolated)
        val raw = reopened.loadRecording(summary.recordingId!!)!!
        assertEquals(1, raw.samples.size)
        assertEquals(sourceTime, raw.samples.single().sourceTimeNanos)
        assertEquals("km/h", raw.samples.single().unit)
        assertTrue(raw.dataSource.contains("MOCK"))
        reopened.delete(summary)
        withTimeout(15_000) { reopened.history.first { it.isEmpty() } }
        assertNull(reopened.loadRecording(summary.recordingId))
        folder.deleteRecursively()
        base.getSharedPreferences("$prefix-ntu_live_sessions", Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }
}
