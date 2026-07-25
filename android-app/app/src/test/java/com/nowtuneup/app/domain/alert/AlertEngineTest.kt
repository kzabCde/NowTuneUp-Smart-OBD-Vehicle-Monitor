package com.nowtuneup.app.domain.alert

import com.nowtuneup.app.domain.model.AlertSeverity
import com.nowtuneup.app.domain.model.WarningThreshold
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertEngineTest {
    private val threshold = WarningThreshold(warningHigh = 100.0, criticalHigh = 110.0)

    @Test
    fun entersWarningAndCriticalAtThresholds() {
        assertEquals(AlertSeverity.NORMAL, AlertEngine.evaluate(threshold, 99.9))
        assertEquals(AlertSeverity.WARNING, AlertEngine.evaluate(threshold, 100.0))
        assertEquals(AlertSeverity.CRITICAL, AlertEngine.evaluate(threshold, 110.0))
    }

    @Test
    fun hysteresisKeepsWarningUntilValueFallsBelowMargin() {
        assertEquals(
            AlertSeverity.WARNING,
            AlertEngine.evaluate(threshold, 98.5, AlertSeverity.WARNING, hysteresis = 2.0),
        )
        assertEquals(
            AlertSeverity.NORMAL,
            AlertEngine.evaluate(threshold, 97.9, AlertSeverity.WARNING, hysteresis = 2.0),
        )
    }

    @Test
    fun hysteresisKeepsCriticalUntilValueFallsBelowMargin() {
        assertEquals(
            AlertSeverity.CRITICAL,
            AlertEngine.evaluate(threshold, 108.5, AlertSeverity.CRITICAL, hysteresis = 2.0),
        )
        assertEquals(
            AlertSeverity.WARNING,
            AlertEngine.evaluate(threshold, 107.9, AlertSeverity.CRITICAL, hysteresis = 2.0),
        )
    }

    @Test
    fun lowThresholdsUseTheSameHysteresisRule() {
        val low = WarningThreshold(warningLow = 12.0, criticalLow = 11.0)
        assertEquals(AlertSeverity.CRITICAL, AlertEngine.evaluate(low, 10.8))
        assertEquals(
            AlertSeverity.CRITICAL,
            AlertEngine.evaluate(low, 11.4, AlertSeverity.CRITICAL, hysteresis = 0.5),
        )
        assertEquals(
            AlertSeverity.WARNING,
            AlertEngine.evaluate(low, 11.6, AlertSeverity.CRITICAL, hysteresis = 0.5),
        )
    }
}
