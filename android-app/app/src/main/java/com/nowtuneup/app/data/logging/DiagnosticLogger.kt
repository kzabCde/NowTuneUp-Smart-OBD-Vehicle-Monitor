package com.nowtuneup.app.data.logging

import android.util.Log
import com.nowtuneup.app.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel { DEBUG, INFO, WARNING, ERROR }

data class DiagnosticLogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val category: String,
    val message: String,
)

@Singleton
class DiagnosticLogger @Inject constructor() {
    private val _entries = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val entries: StateFlow<List<DiagnosticLogEntry>> = _entries.asStateFlow()

    fun debug(category: String, message: String) = append(LogLevel.DEBUG, category, message)
    fun info(category: String, message: String) = append(LogLevel.INFO, category, message)
    fun warning(category: String, message: String) = append(LogLevel.WARNING, category, message)
    fun error(category: String, message: String, throwable: Throwable? = null) {
        append(LogLevel.ERROR, category, buildString {
            append(message)
            throwable?.message?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        })
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun exportText(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return entries.value.joinToString("\n") { entry ->
            "${formatter.format(Date(entry.timestamp))} ${entry.level.name.padEnd(7)} ${entry.category}: ${entry.message}"
        }
    }

    fun safeDeviceLabel(name: String?, address: String?): String {
        val safeName = name?.takeIf { it.isNotBlank() } ?: "Bluetooth device"
        if (BuildConfig.DEBUG) return "$safeName (${address.orEmpty()})"
        val suffix = address?.takeLast(5)?.replace(':', '-') ?: "unknown"
        return "$safeName (…$suffix)"
    }

    private fun append(level: LogLevel, category: String, rawMessage: String) {
        if (level == LogLevel.DEBUG && !BuildConfig.DEBUG) return
        val message = if (BuildConfig.DEBUG) rawMessage else redactAddresses(rawMessage)
        val entry = DiagnosticLogEntry(System.currentTimeMillis(), level, category, message)
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, "[$category] $message")
            LogLevel.INFO -> Log.i(TAG, "[$category] $message")
            LogLevel.WARNING -> Log.w(TAG, "[$category] $message")
            LogLevel.ERROR -> Log.e(TAG, "[$category] $message")
        }
    }

    private fun redactAddresses(message: String): String =
        MAC_ADDRESS.replace(message) { match -> "XX:XX:XX:XX:XX:${match.value.takeLast(2)}" }

    companion object {
        private const val TAG = "NTU"
        private const val MAX_ENTRIES = 500
        private val MAC_ADDRESS = Regex("(?i)(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")
    }
}
