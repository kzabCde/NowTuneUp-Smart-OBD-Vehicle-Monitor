package com.nowtuneup.app.domain.model

/** One telemetry contract for both gauge renderers. This never generates or records samples. */
data class TelemetryGaugeBinding(
    val label: String,
    val value: Double?,
    val unit: DisplayUnit,
    val minimum: Double,
    val maximum: Double,
    val freshness: DataFreshness,
    val severity: AlertSeverity,
    val sessionMinimum: Double?,
    val sessionMaximum: Double?,
)

fun bindTelemetryGauge(
    config: DashboardWidgetConfig,
    reading: VehicleReading?,
    stats: ReadingStats? = null,
    freshness: DataFreshness? = null,
): TelemetryGaugeBinding {
    val state = freshness ?: when {
        reading == null -> DataFreshness.NO_DATA
        !reading.supported -> DataFreshness.UNSUPPORTED
        reading.value?.isFinite() != true -> DataFreshness.NO_DATA
        else -> DataFreshness.LIVE
    }
    val value = displayableReading(reading, state)?.value
    val minimum = config.scaleMinimum?.takeIf { it.isFinite() }
        ?: reading?.minimum?.takeIf { it.isFinite() } ?: 0.0
    val maximum = (config.scaleMaximum?.takeIf { it.isFinite() }
        ?: reading?.maximum?.takeIf { it.isFinite() } ?: when (config.unit) {
            DisplayUnit.RPM -> 7000.0
            DisplayUnit.KMH -> 200.0
            DisplayUnit.MPH -> 125.0
            DisplayUnit.PERCENT -> 100.0
            DisplayUnit.CELSIUS -> 130.0
            DisplayUnit.FAHRENHEIT -> 266.0
            DisplayUnit.VOLT -> 18.0
            else -> 100.0
        }).coerceAtLeast(minimum + 1.0)
    val threshold = config.threshold
    val severity = when {
        value == null -> AlertSeverity.NORMAL
        threshold.criticalLow?.let { value <= it } == true || threshold.criticalHigh?.let { value >= it } == true -> AlertSeverity.CRITICAL
        threshold.warningLow?.let { value <= it } == true || threshold.warningHigh?.let { value >= it } == true -> AlertSeverity.WARNING
        else -> AlertSeverity.NORMAL
    }
    return TelemetryGaugeBinding(config.title, value, config.unit, minimum, maximum, state, severity,
        stats?.minimum?.takeIf { it.isFinite() }, stats?.maximum?.takeIf { it.isFinite() })
}
