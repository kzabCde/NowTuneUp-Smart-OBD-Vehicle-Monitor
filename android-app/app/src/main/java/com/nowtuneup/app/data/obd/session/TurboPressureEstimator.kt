package com.nowtuneup.app.data.obd.session

import com.nowtuneup.app.data.obd.pid.DerivedPids
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.exp

/**
 * Produces a stable gauge-pressure value from asynchronous MAP and BARO samples.
 *
 * MAP changes quickly and is polled more often, while barometric pressure changes slowly. The
 * estimator therefore timestamps the derived value from MAP, accepts a longer BARO age, removes
 * isolated MAP spikes with a three-sample median, and applies a time-based adaptive EMA. The
 * deadband prevents the gauge from flickering around zero boost.
 */
class TurboPressureEstimator(
    private val mapMaximumAgeMillis: Long = 1_500L,
    private val barometricMaximumAgeMillis: Long = 60_000L,
    private val zeroDeadbandKpa: Double = 1.5,
) {
    data class Estimate(
        val valueKpa: Double?,
        val rawKpa: Double?,
        val timestampMillis: Long,
        val quality: Quality,
    )

    enum class Quality {
        GOOD,
        WAITING,
        STALE,
        INVALID,
    }

    private data class PressureSample(val valueKpa: Double, val timestampMillis: Long)

    private val mapSamples = ArrayDeque<PressureSample>(MAP_WINDOW_SIZE)
    private var latestBarometric: PressureSample? = null
    private var filteredKpa: Double? = null
    private var lastFilterTimestampMillis: Long? = null

    fun update(pid: Int, valueKpa: Double, timestampMillis: Long): Estimate {
        if (!valueKpa.isFinite() || timestampMillis <= 0L) {
            return Estimate(null, null, timestampMillis.coerceAtLeast(0L), Quality.INVALID)
        }

        when (pid) {
            DerivedPids.MAP -> {
                if (valueKpa !in VALID_MAP_RANGE_KPA) {
                    return Estimate(null, null, timestampMillis, Quality.INVALID)
                }
                if (mapSamples.isNotEmpty() && timestampMillis <= mapSamples.last.timestampMillis) {
                    return Estimate(filteredKpa, null, mapSamples.last.timestampMillis, Quality.INVALID)
                }
                mapSamples.addLast(PressureSample(valueKpa, timestampMillis))
                while (mapSamples.size > MAP_WINDOW_SIZE) mapSamples.removeFirst()
            }

            DerivedPids.BAROMETRIC_PRESSURE -> {
                if (valueKpa !in VALID_BAROMETRIC_RANGE_KPA) {
                    return Estimate(null, null, timestampMillis, Quality.INVALID)
                }
                val previous = latestBarometric
                if (previous != null && timestampMillis <= previous.timestampMillis) {
                    return Estimate(filteredKpa, null, previous.timestampMillis, Quality.INVALID)
                }
                latestBarometric = PressureSample(valueKpa, timestampMillis)
            }

            else -> return current(timestampMillis)
        }

        return calculate(timestampMillis)
    }

    fun current(nowMillis: Long): Estimate = calculate(nowMillis)

    fun reset() {
        mapSamples.clear()
        latestBarometric = null
        filteredKpa = null
        lastFilterTimestampMillis = null
    }

    private fun calculate(nowMillis: Long): Estimate {
        val latestMap = mapSamples.lastOrNull()
            ?: return Estimate(null, null, nowMillis, Quality.WAITING)
        val barometric = latestBarometric
            ?: return Estimate(null, null, latestMap.timestampMillis, Quality.WAITING)

        if (nowMillis - latestMap.timestampMillis > mapMaximumAgeMillis) {
            return Estimate(null, null, latestMap.timestampMillis, Quality.STALE)
        }
        if (nowMillis - barometric.timestampMillis > barometricMaximumAgeMillis) {
            return Estimate(null, null, latestMap.timestampMillis, Quality.STALE)
        }

        val medianMapKpa = median(mapSamples.map { it.valueKpa })
        val rawKpa = (medianMapKpa - barometric.valueKpa).coerceIn(MIN_GAUGE_KPA, MAX_GAUGE_KPA)
        val previousFiltered = filteredKpa
        val filtered = if (previousFiltered == null) {
            rawKpa
        } else {
            val elapsedMillis = (latestMap.timestampMillis - (lastFilterTimestampMillis ?: latestMap.timestampMillis))
                .coerceAtLeast(1L)
            val responseTimeMillis = if (abs(rawKpa - previousFiltered) >= FAST_RESPONSE_DELTA_KPA) {
                FAST_RESPONSE_TIME_MILLIS
            } else {
                SMOOTH_RESPONSE_TIME_MILLIS
            }
            val alpha = (1.0 - exp(-elapsedMillis.toDouble() / responseTimeMillis))
                .coerceIn(MIN_ALPHA, MAX_ALPHA)
            previousFiltered + alpha * (rawKpa - previousFiltered)
        }

        lastFilterTimestampMillis = latestMap.timestampMillis
        filteredKpa = if (abs(filtered) <= zeroDeadbandKpa) 0.0 else filtered
        return Estimate(
            valueKpa = filteredKpa,
            rawKpa = rawKpa,
            timestampMillis = latestMap.timestampMillis,
            quality = Quality.GOOD,
        )
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        return when {
            sorted.isEmpty() -> 0.0
            sorted.size % 2 == 1 -> sorted[sorted.size / 2]
            else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
    }

    companion object {
        private const val MAP_WINDOW_SIZE = 3
        private const val FAST_RESPONSE_DELTA_KPA = 8.0
        private const val FAST_RESPONSE_TIME_MILLIS = 180.0
        private const val SMOOTH_RESPONSE_TIME_MILLIS = 650.0
        private const val MIN_ALPHA = 0.12
        private const val MAX_ALPHA = 0.85
        private const val MIN_GAUGE_KPA = -100.0
        private const val MAX_GAUGE_KPA = 200.0
        private val VALID_MAP_RANGE_KPA = 10.0..255.0
        private val VALID_BAROMETRIC_RANGE_KPA = 55.0..115.0
    }
}
