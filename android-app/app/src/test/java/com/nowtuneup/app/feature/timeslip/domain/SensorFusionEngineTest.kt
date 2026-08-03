package com.nowtuneup.app.feature.timeslip.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorFusionEngineTest {
    @Test
    fun combinesFreshObdAndGpsOnSharedMonotonicTimeline() {
        val fusion = SensorFusionEngine()
        fusion.ingestGps(GpsTelemetry(96.0, 13.0, 100.0, 2.0, 1_000L, false))
        val sample = fusion.ingestObd(ObdSpeedTelemetry(100.0, 1_020L))!!
        assertTrue(sample.obdValid)
        assertTrue(sample.gpsValid)
        assertTrue(sample.fusedSpeedKmh in 96.0..100.0)
    }

    @Test
    fun rejectsDuplicateOrOutOfOrderObdTimestamps() {
        val fusion = SensorFusionEngine()
        assertTrue(fusion.ingestObd(ObdSpeedTelemetry(10.0, 1_000L)) != null)
        assertNull(fusion.ingestObd(ObdSpeedTelemetry(20.0, 1_000L)))
        assertNull(fusion.ingestObd(ObdSpeedTelemetry(20.0, 999L)))
    }

    @Test
    fun gpsPositionDeltaIsConsumedOnlyOnceAcrossMultipleObdSamples() {
        val fusion = SensorFusionEngine()
        fusion.ingestGps(GpsTelemetry(10.0, 13.000000, 100.0, 2.0, 1_000L, false))
        val gpsUpdate = fusion.ingestGps(GpsTelemetry(11.0, 13.000100, 100.0, 2.0, 1_100L, false))!!
        assertTrue((gpsUpdate.gpsDistanceDeltaMeters ?: 0.0) > 0.0)
        val obd = fusion.ingestObd(ObdSpeedTelemetry(12.0, 1_120L))!!
        assertNull(obd.gpsDistanceDeltaMeters)
    }

    @Test
    fun mockGpsIsNotUsedAsValidSource() {
        val fusion = SensorFusionEngine()
        assertNull(fusion.ingestGps(GpsTelemetry(30.0, 13.0, 100.0, 1.0, 1_000L, true)))
        val obd = fusion.ingestObd(ObdSpeedTelemetry(42.0, 1_010L))!!
        assertEquals(42.0, obd.fusedSpeedKmh, 0.001)
        assertTrue(!obd.gpsValid)
    }
}
