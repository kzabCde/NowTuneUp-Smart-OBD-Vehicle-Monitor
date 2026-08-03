package com.nowtuneup.app.feature.timeslip.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSlipEngineTest {
    @Test
    fun normalStandingZeroToOneHundredRunCompletesWithInterpolation() {
        val engine = TimeSlipEngine()
        val config = TimeSlipConfig(
            selectedDistanceTarget = DistanceTarget.SPEED_ONLY,
            speedMilestones = listOf(StandardSpeedMilestones.zeroTo100Kmh),
        )
        engine.requestArm(config, 1_000_000L, 10_000L, true, true, "ELM327")
        engine.updateTelemetryMetrics(10.0, 0.0, null)
        repeat(12) { index -> engine.ingest(sample(10_000L + index * 100L, 0.0)) }
        val speeds = listOf(0.0, 3.0, 20.0, 45.0, 70.0, 92.0, 105.0)
        speeds.forEachIndexed { index, speed -> engine.ingest(sample(11_200L + index * 100L, speed)) }

        val result = engine.snapshot().result
        assertEquals(TimeSlipStatus.COMPLETED, engine.snapshot().status)
        assertNotNull(result)
        val milestone = result!!.speedMilestones.single()
        assertEquals("0-100-kmh", milestone.id)
        assertTrue(milestone.elapsedMs in 450L..650L)
        assertEquals(milestone.elapsedMs, result.elapsedMs)
        assertEquals(MeasurementQuality.HIGH, result.measurementQuality)
    }

    @Test
    fun rollingSixtyToOneHundredStartsAtCrossingNotArmTap() {
        val engine = TimeSlipEngine()
        val config = TimeSlipConfig(
            testMode = TimeSlipTestMode.ROLLING_START,
            selectedDistanceTarget = DistanceTarget.SPEED_ONLY,
            speedMilestones = emptyList(),
            rollingStartSpeedKmh = 60.0,
            rollingTargetSpeedKmh = 100.0,
        )
        engine.requestArm(config, 5_000L, 0L, true, true, "ELM327")
        engine.updateTelemetryMetrics(10.0, 0.0, null)
        listOf(50.0, 55.0, 59.0, 61.0, 70.0, 90.0, 101.0).forEachIndexed { index, speed ->
            engine.ingest(sample(index * 100L, speed))
        }
        val record = engine.snapshot().result!!
        assertEquals(TimeSlipStatus.COMPLETED, record.status)
        assertEquals(250L, record.startMonotonicTimeMs)
        assertEquals(340L, record.speedMilestones.single().elapsedMs)
    }

    @Test
    fun quarterMileRecordsEveryEarlierCheckpoint() {
        val snapshot = TimeSlipSimulator.run(
            TimeSlipConfig(selectedDistanceTarget = DistanceTarget.QUARTER_MILE),
            TimeSlipSimulator.quarterMile,
        )
        assertEquals(TimeSlipStatus.COMPLETED, snapshot.status)
        assertEquals(
            listOf(
                DistanceTarget.SIXTY_FT,
                DistanceTarget.THREE_THIRTY_FT,
                DistanceTarget.EIGHTH_MILE,
                DistanceTarget.QUARTER_MILE,
            ),
            snapshot.distanceSplits.map { it.target },
        )
        assertTrue(snapshot.distanceSplits.all { it.trapSpeedKmh > 0.0 })
        assertTrue(snapshot.distanceSplits.zipWithNext().all { (a, b) -> b.elapsedMs > a.elapsedMs && b.splitMs > 0L })
        assertEquals(snapshot.distanceSplits.last().elapsedMs, snapshot.result?.elapsedMs)
    }

    @Test
    fun oneMileSimulationCapturesAllSixDistanceTargets() {
        val snapshot = TimeSlipSimulator.run(
            TimeSlipConfig(selectedDistanceTarget = DistanceTarget.ONE_MILE),
            TimeSlipSimulator.oneMile,
        )
        assertEquals(TimeSlipStatus.COMPLETED, snapshot.status)
        assertEquals(6, snapshot.distanceSplits.size)
        assertEquals(DistanceTarget.ONE_MILE, snapshot.distanceSplits.last().target)
    }

    @Test
    fun connectionLossCannotProduceValidResult() {
        val engine = TimeSlipEngine()
        engine.requestArm(
            TimeSlipConfig(selectedDistanceTarget = DistanceTarget.SPEED_ONLY, speedMilestones = listOf(StandardSpeedMilestones.zeroTo100Kmh)),
            0L,
            0L,
            true,
            true,
            "ELM327",
        )
        repeat(12) { engine.ingest(sample(it * 100L, 0.0)) }
        engine.ingest(sample(1_300L, 5.0))
        val snapshot = engine.connectionLost()
        assertEquals(TimeSlipStatus.CONNECTION_LOST, snapshot.status)
        assertEquals(MeasurementQuality.INVALID, snapshot.result?.measurementQuality)
    }

    @Test
    fun gpsLossInvalidatesDistanceRunButNotObdSpeedOnlyRun() {
        val distanceEngine = TimeSlipEngine()
        distanceEngine.requestArm(TimeSlipConfig(selectedDistanceTarget = DistanceTarget.QUARTER_MILE), 0, 0, true, true, "x")
        assertEquals(TimeSlipStatus.GPS_UNRELIABLE, distanceEngine.gpsUnreliable().status)

        val speedEngine = TimeSlipEngine()
        speedEngine.requestArm(
            TimeSlipConfig(selectedDistanceTarget = DistanceTarget.SPEED_ONLY, speedMilestones = listOf(StandardSpeedMilestones.zeroTo60Kmh)),
            0,
            0,
            true,
            false,
            "x",
        )
        assertEquals(TimeSlipStatus.ARMED, speedEngine.gpsUnreliable().status)
    }

    @Test
    fun multipleQuickArmRequestsAreRejected() {
        val engine = TimeSlipEngine()
        assertTrue(engine.requestArm(TimeSlipConfig(), 0, 0, true, true, "x").isSuccess)
        assertTrue(engine.requestArm(TimeSlipConfig(), 1, 1, true, true, "x").isFailure)
    }

    @Test
    fun userCancellationProducesCancelledInvalidRecord() {
        val engine = TimeSlipEngine()
        engine.requestArm(TimeSlipConfig(), 0, 0, true, true, "x")
        val snapshot = engine.cancel()
        assertEquals(TimeSlipStatus.CANCELLED, snapshot.status)
        assertEquals(MeasurementQuality.INVALID, snapshot.result?.measurementQuality)
    }

    @Test
    fun outOfOrderSamplesAreDroppedWithoutChangingCurrentTelemetry() {
        val engine = TimeSlipEngine()
        engine.requestArm(TimeSlipConfig(selectedDistanceTarget = DistanceTarget.SPEED_ONLY), 0, 0, true, true, "x")
        engine.ingest(sample(1_000L, 0.0))
        val before = engine.snapshot()
        engine.ingest(sample(900L, 20.0))
        val after = engine.snapshot()
        assertEquals(before.currentSpeedKmh, after.currentSpeedKmh, 0.001)
        assertEquals(before.elapsedMs, after.elapsedMs)
    }

    @Test
    fun targetNotReachedWithinMaximumDurationBecomesInvalid() {
        val engine = TimeSlipEngine()
        val config = TimeSlipConfig(
            selectedDistanceTarget = DistanceTarget.SPEED_ONLY,
            speedMilestones = listOf(StandardSpeedMilestones.zeroTo160Kmh),
        )
        engine.requestArm(config, 0L, 0L, true, true, "x")
        repeat(12) { engine.ingest(sample(it * 100L, 0.0)) }
        engine.ingest(sample(1_300L, 3.0))
        engine.ingest(sample(1_300L + TimeSlipConstants.MAX_RUN_DURATION_MS + 1L, 80.0))
        assertEquals(TimeSlipStatus.INVALID_RUN, engine.snapshot().status)
        assertEquals(MeasurementQuality.INVALID, engine.snapshot().result?.measurementQuality)
    }

    @Test
    fun zeroSpeedNoiseDoesNotLaunchBeforeStationaryWindow() {
        val engine = TimeSlipEngine()
        engine.requestArm(
            TimeSlipConfig(selectedDistanceTarget = DistanceTarget.SPEED_ONLY, speedMilestones = listOf(StandardSpeedMilestones.zeroTo60Kmh)),
            0L,
            0L,
            true,
            true,
            "x",
        )
        listOf(0.2, 0.8, 1.2, 0.4, 0.9).forEachIndexed { index, speed ->
            engine.ingest(sample(index * 200L, speed))
        }
        assertEquals(TimeSlipStatus.ARMED, engine.snapshot().status)
    }

    @Test
    fun rollingNormalizationDisablesUnreachableStandingMilestones() {
        val normalized = TimeSlipConfig(testMode = TimeSlipTestMode.ROLLING_START).normalized()
        assertTrue(normalized.speedMilestones.filterNot { it.id == "custom-range" }.none { it.enabled })
        assertTrue(normalized.speedMilestones.single { it.id == "custom-range" }.enabled)
    }

    private fun sample(timeMs: Long, speedKmh: Double) = PerformanceSample(
        monotonicTimeMs = timeMs,
        fusedSpeedKmh = speedKmh,
        obdSpeedKmh = speedKmh,
        obdValid = true,
        gpsValid = false,
    )

}
