package com.nowtuneup.app.domain.alert

import com.nowtuneup.app.domain.model.AlertSeverity
import com.nowtuneup.app.domain.model.WarningThreshold

object AlertEngine {
    fun evaluate(
        threshold: WarningThreshold,
        value: Double,
        previous: AlertSeverity = AlertSeverity.NORMAL,
        hysteresis: Double = 0.0,
    ): AlertSeverity {
        val margin = hysteresis.coerceAtLeast(0.0)

        if (previous == AlertSeverity.CRITICAL && remainsCritical(threshold, value, margin)) {
            return AlertSeverity.CRITICAL
        }
        if (isCritical(threshold, value)) return AlertSeverity.CRITICAL

        if (previous == AlertSeverity.WARNING && remainsWarning(threshold, value, margin)) {
            return AlertSeverity.WARNING
        }
        if (isWarning(threshold, value)) return AlertSeverity.WARNING

        return AlertSeverity.NORMAL
    }

    private fun isCritical(threshold: WarningThreshold, value: Double): Boolean =
        threshold.criticalLow?.let { value <= it } == true ||
            threshold.criticalHigh?.let { value >= it } == true

    private fun isWarning(threshold: WarningThreshold, value: Double): Boolean =
        threshold.warningLow?.let { value <= it } == true ||
            threshold.warningHigh?.let { value >= it } == true

    private fun remainsCritical(threshold: WarningThreshold, value: Double, margin: Double): Boolean =
        threshold.criticalLow?.let { value <= it + margin } == true ||
            threshold.criticalHigh?.let { value >= it - margin } == true

    private fun remainsWarning(threshold: WarningThreshold, value: Double, margin: Double): Boolean =
        threshold.warningLow?.let { value <= it + margin } == true ||
            threshold.warningHigh?.let { value >= it - margin } == true
}
