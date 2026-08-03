package com.nowtuneup.app.feature.timeslip.domain

import kotlin.math.abs
import kotlin.math.max

object MeasurementQualityCalculator {
    fun calculate(
        requiresGps: Boolean,
        obdRateHz: Double,
        gpsRateHz: Double,
        averageGpsAccuracyMeters: Double?,
        sampleCount: Int,
        droppedSampleCount: Int,
        sensorDisagreementKmh: Double,
        connectionInterruptions: Int,
        gpsInterruptions: Int,
        maximumGapMs: Long,
        distanceCorrectionMeters: Double,
        totalDistanceMeters: Double,
        terminalStatus: TimeSlipStatus,
    ): MeasurementQualityResult {
        if (terminalStatus != TimeSlipStatus.COMPLETED || sampleCount < 3) {
            return MeasurementQualityResult(
                quality = MeasurementQuality.INVALID,
                estimatedTimingErrorMs = max(250.0, maximumGapMs.toDouble()),
                score = 0,
                reasons = listOf("การทดสอบไม่เสร็จสมบูรณ์"),
            )
        }

        var score = 100
        val reasons = mutableListOf<String>()
        when {
            obdRateHz >= 8.0 -> Unit
            obdRateHz >= 5.0 -> { score -= 8; reasons += "อัตรา OBD ต่ำกว่า 8 Hz" }
            obdRateHz >= 2.0 -> { score -= 20; reasons += "อัตรา OBD ต่ำ" }
            else -> { score -= 35; reasons += "ตัวอย่าง OBD ไม่เพียงพอ" }
        }

        if (requiresGps) {
            val accuracy = averageGpsAccuracyMeters ?: Double.POSITIVE_INFINITY
            when {
                accuracy <= 3.0 -> Unit
                accuracy <= TimeSlipConstants.RECOMMENDED_GPS_ACCURACY_METERS -> { score -= 6; reasons += "GPS แม่นยำระดับใช้งาน" }
                accuracy <= TimeSlipConstants.MAX_GPS_ACCURACY_METERS -> { score -= 20; reasons += "GPS คลาดเคลื่อนมากกว่า 5 เมตร" }
                else -> { score -= 50; reasons += "GPS ไม่แม่นยำพอสำหรับระยะทาง" }
            }
            when {
                gpsRateHz >= TimeSlipConstants.HIGH_GPS_RATE_HZ -> Unit
                gpsRateHz >= TimeSlipConstants.MIN_RECOMMENDED_GPS_RATE_HZ -> { score -= 8; reasons += "GPS ต่ำกว่า 10 Hz" }
                gpsRateHz >= 1.0 -> { score -= 25; reasons += "GPS ต่ำกว่า 5 Hz" }
                else -> { score -= 45; reasons += "GPS ขาดช่วง" }
            }
        }

        val dropRatio = if (sampleCount + droppedSampleCount == 0) 1.0
        else droppedSampleCount.toDouble() / (sampleCount + droppedSampleCount)
        if (dropRatio > 0.10) { score -= 25; reasons += "ตัวอย่างหายมากกว่า 10%" }
        else if (dropRatio > 0.03) { score -= 10; reasons += "มีตัวอย่างหายบางส่วน" }

        if (sensorDisagreementKmh > 15.0) { score -= 20; reasons += "ความเร็ว OBD และ GPS ต่างกันมาก" }
        else if (sensorDisagreementKmh > 8.0) { score -= 8; reasons += "ความเร็วจากเซนเซอร์มีความต่าง" }

        if (connectionInterruptions > 0) { score -= 35; reasons += "การเชื่อมต่อ OBD ขาดช่วง" }
        if (gpsInterruptions > 0 && requiresGps) { score -= 30; reasons += "GPS ขาดช่วงระหว่างทดสอบ" }
        if (maximumGapMs > 1_000L) { score -= 20; reasons += "ช่วงเวลาระหว่างตัวอย่างยาวเกิน 1 วินาที" }
        else if (maximumGapMs > 500L) { score -= 8; reasons += "มีช่วงตัวอย่างห่าง" }

        val correctionRatio = if (totalDistanceMeters <= 0.0) 0.0 else abs(distanceCorrectionMeters) / totalDistanceMeters
        if (correctionRatio > 0.15) { score -= 25; reasons += "ต้องแก้ระยะสะสมมาก" }
        else if (correctionRatio > 0.05) { score -= 10; reasons += "มีการแก้ระยะสะสมจาก GPS" }

        score = score.coerceIn(0, 100)
        val quality = when {
            score >= 85 -> MeasurementQuality.HIGH
            score >= 65 -> MeasurementQuality.MEDIUM
            score >= 40 -> MeasurementQuality.LOW
            else -> MeasurementQuality.INVALID
        }

        val sampleIntervalError = when {
            obdRateHz > 0.0 -> 500.0 / obdRateHz
            else -> 250.0
        }
        val gpsError = if (requiresGps) {
            val accuracy = averageGpsAccuracyMeters ?: 10.0
            val representativeSpeedMps = 20.0
            accuracy / representativeSpeedMps * 1_000.0 * 0.35
        } else 0.0
        val gapError = (maximumGapMs * 0.15).coerceAtMost(300.0)
        val interruptionError = (connectionInterruptions + gpsInterruptions) * 120.0
        val estimatedError = (sampleIntervalError + gpsError + gapError + interruptionError)
            .coerceIn(20.0, 1_500.0)

        return MeasurementQualityResult(quality, estimatedError, score, reasons)
    }
}
