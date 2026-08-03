package com.nowtuneup.app.feature.timeslip.domain

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object TimeSlipMath {
    fun interpolateSpeedCrossingTime(
        previousSpeedKmh: Double,
        currentSpeedKmh: Double,
        targetSpeedKmh: Double,
        previousTimeMs: Long,
        currentTimeMs: Long,
    ): Long {
        val speedDelta = currentSpeedKmh - previousSpeedKmh
        if (speedDelta <= 0.0 || currentTimeMs <= previousTimeMs) return currentTimeMs
        val ratio = ((targetSpeedKmh - previousSpeedKmh) / speedDelta).coerceIn(0.0, 1.0)
        return (previousTimeMs + ratio * (currentTimeMs - previousTimeMs)).toLong()
    }

    fun interpolateDistanceCrossingTime(
        previousDistanceMeters: Double,
        currentDistanceMeters: Double,
        targetDistanceMeters: Double,
        previousTimeMs: Long,
        currentTimeMs: Long,
    ): Long {
        val distanceDelta = currentDistanceMeters - previousDistanceMeters
        if (distanceDelta <= 0.0 || currentTimeMs <= previousTimeMs) return currentTimeMs
        val ratio = ((targetDistanceMeters - previousDistanceMeters) / distanceDelta).coerceIn(0.0, 1.0)
        return (previousTimeMs + ratio * (currentTimeMs - previousTimeMs)).toLong()
    }

    fun interpolateValue(
        previousValue: Double,
        currentValue: Double,
        previousTimeMs: Long,
        currentTimeMs: Long,
        targetTimeMs: Long,
    ): Double {
        if (currentTimeMs <= previousTimeMs) return currentValue
        val ratio = ((targetTimeMs - previousTimeMs).toDouble() / (currentTimeMs - previousTimeMs)).coerceIn(0.0, 1.0)
        return previousValue + (currentValue - previousValue) * ratio
    }

    fun integrateDistanceMeters(previousSpeedMps: Double, currentSpeedMps: Double, deltaTimeSeconds: Double): Double =
        ((previousSpeedMps + currentSpeedMps) / 2.0) * deltaTimeSeconds.coerceAtLeast(0.0)

    fun kmhToMps(speedKmh: Double): Double = speedKmh / 3.6
    fun kmhToMph(speedKmh: Double): Double = speedKmh / TimeSlipConstants.MPH_TO_KMH
    fun mphToKmh(speedMph: Double): Double = speedMph * TimeSlipConstants.MPH_TO_KMH

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
            sin(dLon / 2.0) * sin(dLon / 2.0)
        return 2.0 * earthRadius * asin(min(1.0, sqrt(max(0.0, a))))
    }
}
