package com.nowtuneup.app.feature.timeslip

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class TimeSlipAnalysisTest {
    private fun nanos(seconds: Double) = (seconds * 1e9).toLong()
    private fun sample(seconds: Double, speed: Double) = TimeSlipTelemetrySample(nanos(seconds), 999L, speed)
    private fun record(samples: List<TimeSlipTelemetrySample> = listOf(sample(1.0, 36.0), sample(2.0, 72.0))) = TimeSlipRecord(
        timingStartNanos = 0L, launchTimeNanos = 0L, completionTimeNanos = nanos(2.0), launchSpeedKmh = 0.0,
        elapsedMillis = 2000L, rawSamples = samples, vehicleProfileId = "car-a", measurementQuality = MeasurementQuality.HIGH,
        speedMilestones = listOf(SpeedMilestoneResult("0–72 km/h", 0.0, 72.0, 2000, 20.0)),
    )

    @Test fun derivesSpeedDistanceAndAccelerationFromMonotonicObdSamples() {
        val points = TimeSlipAnalysis.chart(record()).points
        assertEquals(3, points.size)
        assertEquals(0.0, points.first().seconds, 0.0)
        assertEquals(72.0, points.last().speedKmh, 0.0)
        assertEquals(20.0, points.last().distanceMeters!!, 0.0001)
        assertEquals(10.0 / 9.80665, points.last().accelerationG!!, 0.0001)
        assertNull(points.first().accelerationG)
    }

    @Test fun clipsFinalSampleAndIgnoresWaitingBeforeLaunch() {
        val result = record(listOf(sample(-2.0, 0.0), sample(1.0, 36.0), sample(2.0, 72.0)))
            .copy(completionTimeNanos = nanos(1.5), elapsedMillis = 1500L)
        val points = TimeSlipAnalysis.chart(result).points
        assertEquals(1.5, points.last().seconds, 0.0)
        assertEquals(54.0, points.last().speedKmh, 0.0001)
        assertTrue(points.all { it.seconds >= 0.0 && it.seconds <= 1.5 })
    }

    @Test fun rolloutStartsTimeAtZeroButPreservesDistanceFromLaunch() {
        val points = TimeSlipAnalysis.chart(record().copy(timingStartNanos = nanos(0.5))).points
        assertEquals(0.0, points.first().seconds, 0.0)
        assertEquals(18.0, points.first().speedKmh, 0.0001)
        assertTrue(points.first().distanceMeters!! > 0.0)
        assertEquals(1.5, points.last().seconds, 0.0)
    }

    @Test fun telemetryGapDoesNotProduceAccelerationOrInventDistance() {
        val data = record(listOf(sample(1.0, 36.0), sample(3.0, 72.0), sample(4.0, 80.0)))
            .copy(completionTimeNanos = nanos(4.0))
        val points = TimeSlipAnalysis.chart(data).points
        assertTrue(points[2].breakBefore)
        assertNull(points[2].accelerationG)
        assertNull(points[2].distanceMeters)
        assertNull(points.last().distanceMeters)
        assertNotNull(points.last().accelerationG)
    }

    @Test fun doesNotInventFinishInsideSignalGap() {
        val points = TimeSlipAnalysis.chart(record(listOf(sample(1.0, 36.0), sample(4.0, 100.0)))).points
        assertEquals(1.0, points.last().seconds, 0.0)
    }

    @Test fun legacyJsonStillLoadsAndDoesNotCreateMisleadingGraphs() {
        val old = Gson().fromJson("{\"elapsedMillis\":2000,\"rawSamples\":null}", TimeSlipRecord::class.java)
        assertEquals(2000L, old.elapsedMillis)
        assertNull(old.timingStartNanos)
        assertTrue(TimeSlipAnalysis.chart(old).points.isEmpty())
        assertNotNull(TimeSlipAnalysis.chart(old).unavailableReason)
    }

    @Test fun sortsAndDeduplicatesSamplesAndRejectsNonFiniteSpeed() {
        val result = TimeSlipAnalysis.chart(record(listOf(sample(2.0, 72.0), sample(1.0, 36.0),
            sample(1.0, 36.0), sample(1.5, Double.NaN))))
        assertEquals(3, result.points.size)
        assertTrue(result.points.all { it.speedKmh.isFinite() && (it.accelerationG?.isFinite() != false) })
    }

    @Test fun comparisonsRequireSameVehicleTargetsRolloutAndValidResult() {
        val left = record()
        assertTrue(TimeSlipAnalysis.comparable(left, left.copy(id = "other")))
        assertFalse(TimeSlipAnalysis.comparable(left, left.copy(vehicleProfileId = "car-b")))
        assertFalse(TimeSlipAnalysis.comparable(left, left.copy(oneFootRolloutEnabled = true)))
        assertFalse(TimeSlipAnalysis.comparable(left, left.copy(measurementQuality = MeasurementQuality.INVALID)))
        assertFalse(TimeSlipAnalysis.comparable(left, left.copy(speedMilestones = listOf(SpeedMilestoneResult("60–72 km/h", 60.0, 72.0, 1000, 20.0)))))
    }

    @Test fun rollingEnginePersistsInterpolatedStartAndFinishForCharts() {
        val engine = TimeSlipEngine()
        engine.arm(TimeSlipConfig(mode = PerformanceMode.ROLLING_START, selectedDistanceTarget = null,
            rollingStartKmh = 60.0, rollingTargetKmh = 100.0), 50.0, nanos(1.0), 1000L)
        engine.ingestSpeed(70.0, nanos(2.0))
        engine.ingestSpeed(90.0, nanos(3.0))
        val record = engine.ingestSpeed(110.0, nanos(4.0)).record!!
        val points = TimeSlipAnalysis.chart(record).points
        assertEquals(nanos(1.5), record.timingStartNanos)
        assertEquals(60.0, points.first().speedKmh, 0.0)
        assertEquals(100.0, points.last().speedKmh, 0.0)
        assertEquals(record.elapsedMillis / 1000.0, points.last().seconds, 0.00001)
    }

    @Test fun distanceGraphFinishesAtTheEnginesDistanceSplit() {
        val engine = TimeSlipEngine()
        engine.arm(TimeSlipConfig(selectedDistanceTarget = DistanceTarget.SIXTY_FEET), 0.0, nanos(1.0), 1000L)
        engine.ingestSpeed(0.0, nanos(2.0))
        var result: TimeSlipRecord? = null
        for (i in 1..40) {
            val next = engine.ingestSpeed(i * 3.6, nanos(2.0 + i * 0.1))
            if (next.record != null) { result = next.record; break }
        }
        assertNotNull(result)
        assertEquals(DistanceTarget.SIXTY_FEET.meters, TimeSlipAnalysis.chart(result!!).points.last().distanceMeters!!, 0.0001)
    }

    @Test fun extraTargetsAfterFinishDoNotChangeComparisonGroup() {
        val left = record()
        val overshoot = left.copy(speedMilestones = left.speedMilestones + SpeedMilestoneResult("0–100 km/h", 0.0, 100.0, 2300, 30.0))
        assertTrue(TimeSlipAnalysis.comparable(left, overshoot))
        assertEquals(72.0, TimeSlipAnalysis.finishMilestone(overshoot)!!.targetSpeedKmh, 0.0)
    }
}
