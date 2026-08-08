package com.nowtuneup.app.data.obd.session

import kotlin.math.abs

enum class TurboDataQuality {
    GOOD,
    DEGRADED,
    STALE,
    INVALID,
}

data class TurboPressureEstimate(
    val valueKpa: Double?,
    val rawKpa: Double?,
    val quality: TurboDataQuality,
    val updatedAtMillis: Long,
)

/**
 * Stable derived boost pressure for low-cost ELM327 adapters.
 *
 * MAP is median-filtered, BARO is a slow baseline, and KOEO (engine speed near zero) may calibrate
 * that baseline from MAP. Unrelated PID reads never advance the filter.
 */
class TurboPressureEstimator(
    private val maximumPairSkewMillis: Long = 5_000L,
    private val staleAfterMillis: Long = 3_000L,
    private val barometricStaleAfterMillis: Long = 15_000L,
    private val zeroDeadbandKpa: Double = 1.8,
) {
    private data class Sample(val value: Double, val at: Long)

    private val mapWindow = ArrayDeque<Double>(5)
    private var mapSample: Sample? = null
    private var baroSample: Sample? = null
    private var filteredValue: Double? = null
    private var lastRawValue: Double? = null

    fun reset() {
        mapWindow.clear()
        mapSample = null
        baroSample = null
        filteredValue = null
        lastRawValue = null
    }

    /** Key-on/engine-off calibration. Safe only when the caller has confirmed RPM is near zero. */
    fun calibrateBarometricBaseline(mapKpa: Double, nowMillis: Long) {
        if (!mapKpa.isFinite() || mapKpa !in 70.0..115.0) return
        baroSample = Sample(mapKpa, nowMillis)
        filteredValue = 0.0
        lastRawValue = 0.0
    }

    fun update(pid: Int, valueKpa: Double, nowMillis: Long, mapPid: Int, baroPid: Int): TurboPressureEstimate {
        if (!valueKpa.isFinite() || valueKpa !in 20.0..300.0) {
            return current(nowMillis).copy(quality = TurboDataQuality.INVALID)
        }

        when (pid) {
            mapPid -> {
                if (mapWindow.size == 5) mapWindow.removeFirst()
                mapWindow.addLast(valueKpa)
                mapSample = Sample(median(mapWindow), nowMillis)
            }

            baroPid -> {
                val previous = baroSample?.value
                val stableBaseline = if (previous == null) valueKpa else previous + 0.12 * (valueKpa - previous)
                baroSample = Sample(stableBaseline, nowMillis)
            }

            else -> return current(nowMillis)
        }

        return recalculate(nowMillis)
    }

    /** Returns the last estimate without re-running smoothing. */
    fun current(nowMillis: Long): TurboPressureEstimate {
        val map = mapSample ?: return unavailable(nowMillis)
        val baro = baroSample ?: return unavailable(nowMillis)
        val mapAge = (nowMillis - map.at).coerceAtLeast(0L)
        val baroAge = (nowMillis - baro.at).coerceAtLeast(0L)
        if (mapAge > staleAfterMillis || baroAge > barometricStaleAfterMillis) {
            return TurboPressureEstimate(
                valueKpa = null,
                rawKpa = lastRawValue,
                quality = TurboDataQuality.STALE,
                updatedAtMillis = minOf(map.at, baro.at),
            )
        }

        val quality = when {
            abs(map.at - baro.at) <= maximumPairSkewMillis && mapWindow.size >= 3 -> TurboDataQuality.GOOD
            else -> TurboDataQuality.DEGRADED
        }
        return TurboPressureEstimate(
            valueKpa = filteredValue,
            rawKpa = lastRawValue,
            quality = quality,
            updatedAtMillis = map.at,
        )
    }

    private fun recalculate(nowMillis: Long): TurboPressureEstimate {
        val map = mapSample ?: return unavailable(nowMillis)
        val baro = baroSample ?: return unavailable(nowMillis)
        val raw = map.value - baro.value
        if (!raw.isFinite() || raw !in -120.0..260.0) {
            lastRawValue = raw
            return TurboPressureEstimate(null, raw, TurboDataQuality.INVALID, minOf(map.at, baro.at))
        }

        lastRawValue = raw
        val previous = filteredValue
        val delta = previous?.let { abs(raw - it) } ?: 0.0
        val alpha = when {
            previous == null -> 1.0
            delta >= 25.0 -> 0.55
            delta >= 10.0 -> 0.40
            delta >= 4.0 -> 0.30
            else -> 0.20
        }
        var next = if (previous == null) raw else previous + alpha * (raw - previous)
        if (abs(next) <= zeroDeadbandKpa) next = 0.0
        filteredValue = next
        return current(nowMillis)
    }

    private fun unavailable(nowMillis: Long) = TurboPressureEstimate(
        valueKpa = null,
        rawKpa = lastRawValue,
        quality = TurboDataQuality.STALE,
        updatedAtMillis = nowMillis,
    )

    private fun median(values: Collection<Double>): Double {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
