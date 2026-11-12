package com.nowtuneup.app.domain.model

/** Thresholds are supplied by the driver; no manufacturer redline is assumed. */
data class ShiftLightConfig(
    val enabled: Boolean = false,
    val thresholdsRpm: List<Double> = emptyList(),
    val redlineRpm: Double? = null,
    val intensity: Float = 0.7f,
) {
    fun valid(): Boolean = intensity.isFinite() && intensity in 0f..1f &&
        thresholdsRpm.size <= 12 && thresholdsRpm.all { it.isFinite() && it > 0 } &&
        thresholdsRpm.zipWithNext().all { (a, b) -> b > a } &&
        (redlineRpm == null || redlineRpm.isFinite() && redlineRpm > 0 &&
            (thresholdsRpm.isEmpty() || redlineRpm >= thresholdsRpm.last())) &&
        (!enabled || thresholdsRpm.isNotEmpty())

    fun stage(rpm: Double?): Int? = if (!enabled || !valid() || rpm == null || !rpm.isFinite() || rpm < 0) null
        else thresholdsRpm.count { rpm >= it }
}

/** Central display gate: missing, unsupported, disconnected and stale readings never retain a value. */
fun displayableReading(reading: VehicleReading?, freshness: DataFreshness): VehicleReading? =
    reading?.takeIf { it.supported && it.value?.isFinite() == true &&
        freshness in setOf(DataFreshness.LIVE, DataFreshness.DELAYED) }
