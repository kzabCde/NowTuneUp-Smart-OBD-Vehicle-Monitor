package com.nowtuneup.app.data.obd.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurboPressureEstimatorTest {
    private val mapPid = 0x0B
    private val baroPid = 0x33

    @Test
    fun medianFilterRejectsSingleMapSpike() {
        val estimator = TurboPressureEstimator()
        estimator.update(baroPid, 100.0, 0L, mapPid, baroPid)
        estimator.update(mapPid, 110.0, 10L, mapPid, baroPid)
        estimator.update(mapPid, 111.0, 100L, mapPid, baroPid)
        val result = estimator.update(mapPid, 220.0, 200L, mapPid, baroPid)

        assertTrue(result.valueKpa!! < 20.0)
        assertTrue(result.rawKpa!! < 20.0)
    }

    @Test
    fun deadbandLocksSmallNoiseToZero() {
        val estimator = TurboPressureEstimator()
        estimator.update(baroPid, 100.0, 0L, mapPid, baroPid)
        val result = estimator.update(mapPid, 100.8, 100L, mapPid, baroPid)

        assertEquals(0.0, result.valueKpa!!, 0.001)
    }

    @Test
    fun stalePairDoesNotPublishZero() {
        val estimator = TurboPressureEstimator(staleAfterMillis = 1_000L)
        estimator.update(baroPid, 100.0, 0L, mapPid, baroPid)
        estimator.update(mapPid, 120.0, 100L, mapPid, baroPid)
        val result = estimator.current(2_000L)

        assertNull(result.valueKpa)
        assertEquals(TurboDataQuality.STALE, result.quality)
    }

    @Test
    fun resetClearsPreviousFilteredState() {
        val estimator = TurboPressureEstimator()
        estimator.update(baroPid, 100.0, 0L, mapPid, baroPid)
        estimator.update(mapPid, 150.0, 100L, mapPid, baroPid)
        estimator.reset()

        assertNull(estimator.current(200L).valueKpa)
    }
}
