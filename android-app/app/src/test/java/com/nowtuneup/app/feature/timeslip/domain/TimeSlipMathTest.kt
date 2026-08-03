package com.nowtuneup.app.feature.timeslip.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeSlipMathTest {
    @Test
    fun speedCrossingIsInterpolatedBetweenSamples() {
        assertEquals(
            1_150L,
            TimeSlipMath.interpolateSpeedCrossingTime(40.0, 80.0, 60.0, 1_000L, 1_300L),
        )
    }

    @Test
    fun distanceCrossingIsInterpolatedBetweenSamples() {
        assertEquals(
            2_250L,
            TimeSlipMath.interpolateDistanceCrossingTime(90.0, 110.0, 100.0, 2_000L, 2_500L),
        )
    }

    @Test
    fun trapezoidalDistanceIntegrationUsesBothSpeeds() {
        assertEquals(15.0, TimeSlipMath.integrateDistanceMeters(10.0, 20.0, 1.0), 0.0001)
    }

    @Test
    fun sixtyMphIsNotSixtyKmh() {
        assertEquals(96.56064, TimeSlipMath.mphToKmh(60.0), 0.00001)
        assertEquals(60.0, TimeSlipMath.kmhToMph(96.56064), 0.00001)
    }
}
