package com.nowtuneup.app.feature.timeslip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSlipEngineTest {
    @Test
    fun interpolatesSpeedCrossingBetweenSamples() {
        val crossing = TimeSlipEngine.interpolateSpeedCrossingTimeNanos(
            previousSpeedKmh = 50.0,
            currentSpeedKmh = 70.0,
            targetSpeedKmh = 60.0,
            previousTimeNanos = seconds(1.0),
            currentTimeNanos = seconds(2.0),
        )

        assertEquals(seconds(1.5), crossing)
    }

    @Test
    fun integratesDistanceWithTrapezoidalRule() {
        val meters = TimeSlipEngine.integrateDistanceMeters(
            previousSpeedMps = 0.0,
            currentSpeedMps = 20.0,
            deltaTimeSeconds = 2.0,
        )

        assertEquals(20.0, meters, 0.0001)
    }

    @Test
    fun standingStartRecordsZeroToHundred() {
        val engine = TimeSlipEngine()
        engine.arm(
            requestedConfig = TimeSlipConfig(selectedDistanceTarget = null, speedOnlyTargetKmh = 100.0),
            currentSpeedKmh = 0.0,
            nowNanos = seconds(0.0),
            wallClockMillis = 1_000L,
        )
        engine.ingestSpeed(0.0, seconds(1.0))
        engine.ingestSpeed(10.0, seconds(1.1))
        val completed = engine.ingestSpeed(100.0, seconds(5.0))

        assertEquals(TimeSlipStatus.COMPLETED, completed.status)
        assertNotNull(completed.record)
        val record = completed.record!!
        assertTrue(record.speedMilestones.any { it.label == "0–60 km/h" })
        assertTrue(record.speedMilestones.any { it.label == "0–60 mph" })
        assertTrue(record.speedMilestones.any { it.label == "0–100 km/h" })
        assertEquals(4_000L, record.elapsedMillis)
    }

    @Test
    fun rollingStartUsesInterpolatedStartThreshold() {
        val engine = TimeSlipEngine()
        engine.arm(
            requestedConfig = TimeSlipConfig(
                mode = PerformanceMode.ROLLING_START,
                selectedDistanceTarget = null,
                rollingStartKmh = 60.0,
                rollingTargetKmh = 100.0,
            ),
            currentSpeedKmh = 50.0,
            nowNanos = seconds(0.0),
            wallClockMillis = 5_000L,
        )
        engine.ingestSpeed(70.0, seconds(1.0))
        val completed = engine.ingestSpeed(100.0, seconds(3.0))

        assertEquals(TimeSlipStatus.COMPLETED, completed.status)
        val result = completed.record!!.speedMilestones.single()
        assertEquals("60–100 km/h", result.label)
        assertEquals(2_500L, result.elapsedMillis)
    }

    @Test
    fun distanceRunRecordsEveryEarlierSplit() {
        val engine = TimeSlipEngine()
        engine.arm(
            requestedConfig = TimeSlipConfig(selectedDistanceTarget = DistanceTarget.QUARTER_MILE),
            currentSpeedKmh = 0.0,
            nowNanos = seconds(0.0),
            wallClockMillis = 9_000L,
        )
        engine.ingestSpeed(0.0, seconds(1.0))
        var snapshot = engine.ingestSpeed(10.0, seconds(1.1))

        var second = 2
        while (snapshot.status == TimeSlipStatus.RUNNING && second <= 30) {
            snapshot = engine.ingestSpeed(120.0, seconds(second.toDouble()))
            second += 1
        }

        assertEquals(TimeSlipStatus.COMPLETED, snapshot.status)
        val targets = snapshot.record!!.distanceSplits.map { it.target }
        assertEquals(
            listOf(
                DistanceTarget.SIXTY_FEET,
                DistanceTarget.THREE_THIRTY_FEET,
                DistanceTarget.EIGHTH_MILE,
                DistanceTarget.QUARTER_MILE,
            ),
            targets,
        )
        assertEquals(MeasurementQuality.LOW, snapshot.record!!.measurementQuality)
        assertTrue(snapshot.record!!.distanceEstimated)
    }

    @Test
    fun connectionLossNeverProducesValidRecord() {
        val engine = TimeSlipEngine()
        engine.arm(TimeSlipConfig(), 0.0, seconds(0.0), 1_000L)
        val lost = engine.connectionLost()

        assertEquals(TimeSlipStatus.CONNECTION_LOST, lost.status)
        assertEquals(null, lost.record)
    }

    private fun seconds(value: Double): Long = (value * 1_000_000_000L).toLong()
}
