package com.nowtuneup.app.feature.timeslip.domain

import kotlin.math.min

object TimeSlipSimulator {
    data class Scenario(
        val name: String,
        val durationMs: Long,
        val targetSpeedKmh: Double,
        val disconnectAtMs: Long? = null,
        val gpsAccuracyMeters: Double = 2.0,
        val gpsEnabled: Boolean = true,
        val obdPeriodMs: Long = 100L,
        val gpsPeriodMs: Long = 100L,
    )

    val quarterMile = Scenario("quarter-mile", 18_000L, 165.0)
    val oneMile = Scenario("one-mile", 50_000L, 225.0)
    val slowVehicle = Scenario("slow", 30_000L, 85.0)

    fun run(
        config: TimeSlipConfig,
        scenario: Scenario = quarterMile,
        startEpochMs: Long = 1_700_000_000_000L,
        startMonotonicMs: Long = 10_000L,
    ): TimeSlipEngineSnapshot {
        val engine = TimeSlipEngine()
        val fusion = SensorFusionEngine()
        engine.requestArm(config, startEpochMs, startMonotonicMs, true, !config.requiresGps || scenario.gpsEnabled, "Simulated ELM327")
        engine.updateTelemetryMetrics(
            obdSampleRateHz = 1_000.0 / scenario.obdPeriodMs,
            gpsSampleRateHz = if (scenario.gpsEnabled) 1_000.0 / scenario.gpsPeriodMs else 0.0,
            averageGpsAccuracyMeters = scenario.gpsAccuracyMeters.takeIf { scenario.gpsEnabled },
        )

        var time = startMonotonicMs
        repeat(12) {
            fusion.ingestObd(ObdSpeedTelemetry(0.0, time))?.let(engine::ingest)
            if (scenario.gpsEnabled) {
                fusion.ingestGps(GpsTelemetry(0.0, 13.7563, 100.5018, scenario.gpsAccuracyMeters, time, false))?.let(engine::ingest)
            }
            time += 100L
        }

        val launchStart = time
        var previousDistance = 0.0
        var latitude = 13.7563
        while (time - launchStart <= scenario.durationMs && engine.snapshot().status in TimeSlipEngine.activeStatuses) {
            val progress = ((time - launchStart).toDouble() / scenario.durationMs).coerceIn(0.0, 1.0)
            val speed = min(scenario.targetSpeedKmh, scenario.targetSpeedKmh * (1.0 - (1.0 - progress) * (1.0 - progress)))
            if (scenario.disconnectAtMs != null && time - launchStart >= scenario.disconnectAtMs) {
                engine.connectionLost()
                break
            }
            fusion.ingestAcceleration(AccelerometerTelemetry(if (progress < 0.15) 3.8 else 1.4, time))
            if ((time - launchStart) % scenario.obdPeriodMs == 0L) {
                fusion.ingestObd(ObdSpeedTelemetry(speed, time))?.let(engine::ingest)
            }
            if (scenario.gpsEnabled && (time - launchStart) % scenario.gpsPeriodMs == 0L) {
                val step = TimeSlipMath.integrateDistanceMeters(
                    TimeSlipMath.kmhToMps(speed),
                    TimeSlipMath.kmhToMps(speed),
                    scenario.gpsPeriodMs / 1_000.0,
                )
                previousDistance += step
                latitude += step / 111_320.0
                fusion.ingestGps(
                    GpsTelemetry(speed, latitude, 100.5018, scenario.gpsAccuracyMeters, time, false),
                )?.let(engine::ingest)
            }
            time += 20L
        }
        return engine.snapshot()
    }
}
