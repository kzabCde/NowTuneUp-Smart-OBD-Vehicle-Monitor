package com.nowtuneup.app.data.obd.session

import com.nowtuneup.app.data.obd.pid.DerivedPids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurboPressureEstimatorTest {
    @Test
    fun deadbandKeepsAtmosphericPressureAtZeroBoost() {
        val estimator = TurboPressureEstimator()
        estimator.update(DerivedPids.BAROMETRIC_PRESSURE, 101.0, 1_000L)
        val estimate = estimator.update(DerivedPids.MAP, 101.8, 1_100L)

        assertEquals(TurboPressureEstimator.Quality.GOOD, estimate.quality)
        assertEquals(0.0, estimate.valueKpa ?: Double.NaN, 0.0001)
    }

    @Test
    fun medianFilterRejectsSingleMapSpike() {
        val estimator = TurboPressureEstimator()
        estimator.update(DerivedPids.BAROMETRIC_PRESSURE, 100.0, 1_000L)
        estimator.update(DerivedPids.MAP, 102.0, 1_100L)
        estimator.update(DerivedPids.MAP, 103.0, 1_300L)
        val estimate = estimator.update(DerivedPids.MAP, 220.0, 1_500L)

        assertEquals(TurboPressureEstimator.Quality.GOOD, estimate.quality)
        assertTrue((estimate.rawKpa ?: 999.0) < 10.0)
        assertTrue((estimate.valueKpa ?: 999.0) < 10.0)
    }

    @Test
    fun mapTimestampDrivesDerivedReadingFreshness() {
        val estimator = TurboPressureEstimator()
        estimator.update(DerivedPids.BAROMETRIC_PRESSURE, 100.0, 1_000L)
        val estimate = estimator.update(DerivedPids.MAP, 130.0, 20_000L)

        assertEquals(20_000L, estimate.timestampMillis)
        assertEquals(TurboPressureEstimator.Quality.GOOD, estimate.quality)
    }

    @Test
    fun staleBarometricBaselineDoesNotProduceBoost() {
        val estimator = TurboPressureEstimator(barometricMaximumAgeMillis = 2_000L)
        estimator.update(DerivedPids.BAROMETRIC_PRESSURE, 100.0, 1_000L)
        val estimate = estimator.update(DerivedPids.MAP, 140.0, 4_000L)

        assertEquals(TurboPressureEstimator.Quality.STALE, estimate.quality)
        assertNull(estimate.valueKpa)
    }

    @Test
    fun resetClearsPreviousFilterState() {
        val estimator = TurboPressureEstimator()
        estimator.update(DerivedPids.BAROMETRIC_PRESSURE, 100.0, 1_000L)
        estimator.update(DerivedPids.MAP, 150.0, 1_100L)
        estimator.reset()

        val estimate = estimator.update(DerivedPids.MAP, 120.0, 2_000L)
        assertEquals(TurboPressureEstimator.Quality.WAITING, estimate.quality)
        assertNull(estimate.valueKpa)
    }
}
