package com.nowtuneup.app.data.obd.session

import kotlin.math.abs
import kotlin.math.exp

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
 * Stabilizes derived boost pressure without hiding real throttle changes.
 * MAP is median-filtered, then a time-based adaptive EMA is applied.
 */
class TurboPressureEstimator(
    private val maximumPairSkewMillis: Long = 2_500L,
    private val staleAfterMillis: Long = 3_500L,
    private val zeroDeadbandKpa: Double = 1.4,
) {
    private data class Sample(val value: Double, val at: Long)

    private val mapWindow = ArrayDeque<Double>(3)
    private var mapSample: Sample? = null
    private var baroSample: Sample? = null
    private var filtered: Double? = null
    private var filteredAt: Long = 0L

    fun reset() {
        mapWindow.clear()
        mapSample = null
        baroSample = null
        filtered = null
        filteredAt = 0L
    }

    fun update(pid: Int, valueKpa: Double, nowMillis: Long, mapPid: Int, baroPid: Int): TurboPressureEstimate {
        if (!valueKpa.isFinite() || valueKpa !in 20.0..300.0) {
            return current(nowMillis).copy(quality = TurboDataQuality.INVALID)
        }
        when (pid) {
            mapPid -> {
                if (mapWindow.size == 3) mapWindow.removeFirst()
                mapWindow.addLast(valueKpa)
                mapSample = Sample(median(mapWindow), nowMillis)
            }
            baroPid -> baroSample = Sample(valueKpa, nowMillis)
        }
        return current(nowMillis)
    }

    fun current(nowMillis: Long): TurboPressureEstimate {
        val map = mapSample ?: return TurboPressureEstimate(null, null, TurboDataQuality.STALE, nowMillis)
        val baro = baroSample ?: return TurboPressureEstimate(null, null, TurboDataQuality.STALE, nowMillis)
        val oldestAt = minOf(map.at, baro.at)
        val age = nowMillis - oldestAt
        val skew = abs(map.at - baro.at)
        if (age > staleAfterMillis) {
            return TurboPressureEstimate(null, null, TurboDataQuality.STALE, oldestAt)
        }

        val raw = map.value - baro.value
        if (!raw.isFinite() || raw !in -120.0..260.0) {
            return TurboPressureEstimate(null, raw, TurboDataQuality.INVALID, oldestAt)
        }

        val previous = filtered
        val dtSeconds = if (filteredAt == 0L) 0.0 else ((nowMillis - filteredAt).coerceAtLeast(1L) / 1_000.0)
        val delta = if (previous == null) 0.0 else abs(raw - previous)
        val tauSeconds = when {
            delta >= 18.0 -> 0.10
            delta >= 7.0 -> 0.18
            else -> 0.42
        }
        val alpha = if (previous == null) 1.0 else (1.0 - exp(-dtSeconds / tauSeconds)).coerceIn(0.08, 0.72)
        var next = if (previous == null) raw else previous + alpha * (raw - previous)
        if (abs(next) <= zeroDeadbandKpa) next = 0.0
        filtered = next
        filteredAt = nowMillis

        return TurboPressureEstimate(
            valueKpa = next,
            rawKpa = raw,
            quality = if (skew <= maximumPairSkewMillis) TurboDataQuality.GOOD else TurboDataQuality.DEGRADED,
            updatedAtMillis = oldestAt,
        )
    }

    private fun median(values: Collection<Double>): Double {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
