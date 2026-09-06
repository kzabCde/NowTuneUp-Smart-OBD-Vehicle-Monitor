package com.nowtuneup.app.data.vehicle

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nowtuneup.app.data.obd.session.AdapterHealthState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdapterHealthSample(
    val grade: String,
    val recommendedMode: String,
    val averageLatencyMillis: Long,
    val successRate: Double,
    val commandsPerSecond: Double,
    val softRecoveries: Int,
    val recordedAtMillis: Long,
)

data class AdapterConnectionProfile(
    val key: String,
    val adapterIdentity: String? = null,
    val vin: String? = null,
    val recommendedMode: String = "BALANCED",
    val latestGrade: String = "UNKNOWN",
    val averageLatencyMillis: Long = 0L,
    val successRate: Double = 1.0,
    val commandsPerSecond: Double = 0.0,
    val stabilityScore: Int = 0,
    val supportedPidCount: Int = 0,
    val healthHistory: List<AdapterHealthSample> = emptyList(),
    val lastSeenAtMillis: Long = 0L,
)

object ConnectionProfileEvaluator {
    fun stabilityScore(
        successRate: Double,
        averageLatencyMillis: Long,
        softRecoveries: Int,
    ): Int {
        var score = (successRate.coerceIn(0.0, 1.0) * 100.0).toInt()
        score -= when {
            averageLatencyMillis <= 0L -> 10
            averageLatencyMillis <= 120L -> 0
            averageLatencyMillis <= 220L -> 5
            averageLatencyMillis <= 450L -> 15
            else -> 30
        }
        score -= (softRecoveries.coerceAtMost(5) * 3)
        return score.coerceIn(0, 100)
    }
}

@Singleton
class ConnectionProfileRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<AdapterConnectionProfile>>() {}.type
    private val _current = MutableStateFlow<AdapterConnectionProfile?>(null)
    val current: StateFlow<AdapterConnectionProfile?> = _current.asStateFlow()

    fun list(): List<AdapterConnectionProfile> = runCatching {
        val json = preferences.getString(KEY_PROFILES, null) ?: return emptyList()
        gson.fromJson<List<AdapterConnectionProfile>>(json, listType).orEmpty()
            .sortedByDescending { it.lastSeenAtMillis }
    }.getOrDefault(emptyList())

    fun observe(
        adapterIdentity: String?,
        vin: String?,
        supportedPidCount: Int,
        health: AdapterHealthState,
        nowMillis: Long = System.currentTimeMillis(),
    ): AdapterConnectionProfile {
        val key = profileKey(adapterIdentity, vin)
        val previous = list().firstOrNull { it.key == key }
        val sample = AdapterHealthSample(
            grade = health.grade.name,
            recommendedMode = health.recommendedMode.name,
            averageLatencyMillis = health.averageLatencyMillis,
            successRate = health.successRate,
            commandsPerSecond = health.commandsPerSecond,
            softRecoveries = health.softRecoveries,
            recordedAtMillis = nowMillis,
        )
        val shouldAppend = previous?.healthHistory?.lastOrNull()?.let {
            nowMillis - it.recordedAtMillis >= MIN_HISTORY_INTERVAL_MILLIS ||
                it.grade != sample.grade || it.recommendedMode != sample.recommendedMode
        } ?: true
        val history = if (shouldAppend) {
            (previous?.healthHistory.orEmpty() + sample).takeLast(MAX_HEALTH_SAMPLES)
        } else {
            previous?.healthHistory.orEmpty()
        }
        val updated = AdapterConnectionProfile(
            key = key,
            adapterIdentity = adapterIdentity?.take(120),
            vin = vin?.trim()?.uppercase()?.takeIf { it.length == 17 },
            recommendedMode = health.recommendedMode.name,
            latestGrade = health.grade.name,
            averageLatencyMillis = health.averageLatencyMillis,
            successRate = health.successRate,
            commandsPerSecond = health.commandsPerSecond,
            stabilityScore = ConnectionProfileEvaluator.stabilityScore(
                successRate = health.successRate,
                averageLatencyMillis = health.averageLatencyMillis,
                softRecoveries = health.softRecoveries,
            ),
            supportedPidCount = supportedPidCount.coerceAtLeast(0),
            healthHistory = history,
            lastSeenAtMillis = nowMillis,
        )
        save(updated)
        _current.value = updated
        return updated
    }

    private fun save(profile: AdapterConnectionProfile) {
        val updated = (list().filterNot { it.key == profile.key } + profile)
            .sortedByDescending { it.lastSeenAtMillis }
            .take(MAX_PROFILES)
        preferences.edit().putString(KEY_PROFILES, gson.toJson(updated, listType)).apply()
    }

    private fun profileKey(adapterIdentity: String?, vin: String?): String {
        val normalizedVin = vin?.trim()?.uppercase()?.takeIf { it.length == 17 } ?: "NO_VIN"
        val normalizedAdapter = adapterIdentity?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "UNKNOWN_ADAPTER"
        return "$normalizedVin|$normalizedAdapter"
    }

    companion object {
        private const val PREFERENCES_NAME = "ntu_connection_profiles"
        private const val KEY_PROFILES = "profiles_v1"
        private const val MAX_PROFILES = 20
        private const val MAX_HEALTH_SAMPLES = 48
        private const val MIN_HISTORY_INTERVAL_MILLIS = 30_000L
    }
}
