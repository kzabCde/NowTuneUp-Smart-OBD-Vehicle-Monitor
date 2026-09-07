package com.nowtuneup.app.data.diagnostics

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nowtuneup.app.domain.model.DiagnosticOverview
import com.nowtuneup.app.domain.model.Mode06Summary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiagnosticHistoryRecord(
    val readAtMillis: Long,
    val vin: String? = null,
    val milOn: Boolean? = null,
    val storedCodes: List<String> = emptyList(),
    val pendingCodes: List<String> = emptyList(),
    val permanentCodes: List<String> = emptyList(),
    val freezeFrameTrigger: String? = null,
    val mode06Supported: Boolean? = null,
    val mode06FrameCount: Int = 0,
) {
    val totalDtcCount: Int
        get() = storedCodes.size + pendingCodes.size + permanentCodes.size
}

@Singleton
class DiagnosticHistoryRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val historyType = object : TypeToken<List<DiagnosticHistoryRecord>>() {}.type
    private val _history = MutableStateFlow(readHistory())
    val history: StateFlow<List<DiagnosticHistoryRecord>> = _history.asStateFlow()

    fun save(overview: DiagnosticOverview, mode06: Mode06Summary? = null) {
        val previous = _history.value.firstOrNull { it.readAtMillis == overview.readAtMillis }
        val record = DiagnosticHistoryRecord(
            readAtMillis = overview.readAtMillis,
            vin = overview.vin,
            milOn = overview.readiness?.milOn,
            storedCodes = overview.stored.map { it.code },
            pendingCodes = overview.pending.map { it.code },
            permanentCodes = overview.permanent.map { it.code },
            freezeFrameTrigger = overview.freezeFrame?.triggerDtc,
            mode06Supported = mode06?.supported ?: previous?.mode06Supported,
            mode06FrameCount = mode06?.monitorFrameCount ?: previous?.mode06FrameCount ?: 0,
        )
        val updated = (listOf(record) + _history.value.filterNot { it.readAtMillis == record.readAtMillis })
            .sortedByDescending { it.readAtMillis }
            .take(MAX_HISTORY)
        preferences.edit().putString(KEY_HISTORY, gson.toJson(updated, historyType)).apply()
        _history.value = updated
    }

    fun clear() {
        preferences.edit().remove(KEY_HISTORY).apply()
        _history.value = emptyList()
    }

    private fun readHistory(): List<DiagnosticHistoryRecord> = runCatching {
        val json = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        gson.fromJson<List<DiagnosticHistoryRecord>>(json, historyType).orEmpty()
            .sortedByDescending { it.readAtMillis }
            .take(MAX_HISTORY)
    }.getOrDefault(emptyList())

    companion object {
        private const val PREFERENCES_NAME = "ntu_diagnostic_history"
        private const val KEY_HISTORY = "history_v1"
        private const val MAX_HISTORY = 30
    }
}
