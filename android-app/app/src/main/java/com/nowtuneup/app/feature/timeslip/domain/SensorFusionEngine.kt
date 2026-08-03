package com.nowtuneup.app.feature.timeslip.domain

import kotlin.math.abs

class SensorFusionEngine {
    private var latestObd: ObdSpeedTelemetry? = null
    private var latestGps: GpsTelemetry? = null
    private var previousGps: GpsTelemetry? = null
    private var latestAcceleration: AccelerometerTelemetry? = null
    private var lastOutputTimeMs: Long = Long.MIN_VALUE
    private var lastGpsDistanceConsumedTimeMs: Long = Long.MIN_VALUE

    fun reset() {
        latestObd = null
        latestGps = null
        previousGps = null
        latestAcceleration = null
        lastOutputTimeMs = Long.MIN_VALUE
        lastGpsDistanceConsumedTimeMs = Long.MIN_VALUE
    }

    fun ingestObd(sample: ObdSpeedTelemetry): PerformanceSample? {
        if (sample.monotonicTimeMs <= latestObd?.monotonicTimeMs ?: Long.MIN_VALUE) return null
        latestObd = sample
        return fusedAt(sample.monotonicTimeMs)
    }

    fun ingestGps(sample: GpsTelemetry): PerformanceSample? {
        if (sample.monotonicTimeMs <= latestGps?.monotonicTimeMs ?: Long.MIN_VALUE) return null
        previousGps = latestGps
        latestGps = sample
        return fusedAt(sample.monotonicTimeMs)
    }

    fun ingestAcceleration(sample: AccelerometerTelemetry) {
        if (sample.monotonicTimeMs > (latestAcceleration?.monotonicTimeMs ?: Long.MIN_VALUE)) latestAcceleration = sample
    }

    private fun fusedAt(nowMs: Long): PerformanceSample? {
        if (nowMs <= lastOutputTimeMs) return null
        val obd = latestObd?.takeIf { nowMs - it.monotonicTimeMs <= 900L && it.speedKmh in 0.0..TimeSlipConstants.MAX_REASONABLE_SPEED_KMH }
        val gps = latestGps?.takeIf {
            nowMs - it.monotonicTimeMs <= TimeSlipConstants.GPS_STALE_AFTER_MS &&
                !it.isMock &&
                it.accuracyMeters <= TimeSlipConstants.MAX_GPS_ACCURACY_METERS &&
                (it.speedKmh == null || it.speedKmh in 0.0..TimeSlipConstants.MAX_REASONABLE_SPEED_KMH)
        }
        if (obd == null && gps?.speedKmh == null) return null

        val fused = when {
            obd != null && gps?.speedKmh != null -> {
                val disagreement = abs(obd.speedKmh - gps.speedKmh)
                val gpsWeight = when {
                    gps.accuracyMeters <= 3.0 && obd.speedKmh < 8.0 -> 0.55
                    gps.accuracyMeters <= 5.0 && disagreement <= 8.0 -> 0.35
                    else -> 0.20
                }
                obd.speedKmh * (1.0 - gpsWeight) + gps.speedKmh * gpsWeight
            }
            obd != null -> obd.speedKmh
            else -> gps!!.speedKmh!!
        }.coerceIn(0.0, TimeSlipConstants.MAX_REASONABLE_SPEED_KMH)

        val previous = previousGps
        val gpsDelta = if (
            gps != null && previous != null &&
            gps.monotonicTimeMs > previous.monotonicTimeMs &&
            gps.monotonicTimeMs > lastGpsDistanceConsumedTimeMs &&
            gps.monotonicTimeMs - previous.monotonicTimeMs <= 2_000L
        ) {
            TimeSlipMath.haversineMeters(
                previous.latitude,
                previous.longitude,
                gps.latitude,
                gps.longitude,
            ).takeIf { it in 0.0..150.0 }.also {
                lastGpsDistanceConsumedTimeMs = gps.monotonicTimeMs
            }
        } else null

        val acceleration = latestAcceleration?.takeIf { nowMs - it.monotonicTimeMs <= 350L }
            ?.longitudinalAccelerationMs2
            ?.takeIf { abs(it) <= TimeSlipConstants.MAX_REASONABLE_ACCELERATION_MS2 }

        lastOutputTimeMs = nowMs
        return PerformanceSample(
            monotonicTimeMs = nowMs,
            obdSpeedKmh = obd?.speedKmh,
            gpsSpeedKmh = gps?.speedKmh,
            fusedSpeedKmh = fused,
            latitude = gps?.latitude,
            longitude = gps?.longitude,
            gpsAccuracyMeters = gps?.accuracyMeters,
            gpsDistanceDeltaMeters = gpsDelta,
            accelerationMs2 = acceleration,
            obdValid = obd != null,
            gpsValid = gps != null,
        )
    }
}
