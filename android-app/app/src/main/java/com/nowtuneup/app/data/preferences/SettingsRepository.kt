package com.nowtuneup.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.nowtuneup.app.domain.model.DashboardPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("ntu_settings")

/** Separate metadata avoids changing the serialized dashboard preference schema or Room tables. */
data class DashboardWorkspacePreferences(
    val orderedIds: List<String> = emptyList(),
    val landingId: String? = null,
)

data class AppSettings(
    val imperial: Boolean = false,
    val fahrenheit: Boolean = false,
    val keepAwake: Boolean = true,
    val autoReconnect: Boolean = true,
    val landscape: Boolean = true,
    val debug: Boolean = false,
    val recordingSeconds: Int = 1,
)

class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()

    val settings = context.dataStore.data.map {
        AppSettings(
            imperial = it[booleanPreferencesKey("imperial")] ?: false,
            fahrenheit = it[booleanPreferencesKey("fahrenheit")] ?: false,
            keepAwake = it[booleanPreferencesKey("awake")] ?: true,
            autoReconnect = it[booleanPreferencesKey("reconnect")] ?: true,
            landscape = it[booleanPreferencesKey("landscape")] ?: true,
            debug = it[booleanPreferencesKey("debug")] ?: false,
            recordingSeconds = it[intPreferencesKey("recording_seconds")] ?: 1,
        )
    }

    suspend fun setBoolean(key: String, value: Boolean) = context.dataStore.edit {
        it[booleanPreferencesKey(key)] = value
    }

    val dashboardPreferences = context.dataStore.data.map { values ->
        val json = values[stringPreferencesKey("dashboard_preferences")]
        if (json.isNullOrBlank()) DashboardPreferences()
        else runCatching { migrate(json) }.getOrElse { DashboardPreferences() }
    }

    suspend fun saveDashboardPreferences(value: DashboardPreferences) = context.dataStore.edit {
        it[stringPreferencesKey("dashboard_preferences")] = gson.toJson(value.normalized())
    }

    /** Read-modify-write inside DataStore's transaction avoids losing unrelated preference updates. */
    suspend fun updateDashboardPreferences(change: (DashboardPreferences) -> DashboardPreferences) = context.dataStore.edit { values ->
        val key = stringPreferencesKey("dashboard_preferences")
        val current = values[key]?.let { json -> runCatching { migrate(json) }.getOrNull() } ?: DashboardPreferences()
        values[key] = gson.toJson(change(current).normalized())
    }

    val dashboardWorkspace = context.dataStore.data.map { values ->
        val order = runCatching {
            val json = values[WORKSPACE_ORDER] ?: "[]"
            JsonParser.parseString(json).asJsonArray.mapNotNull { item ->
                item.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            }.filter { it.isNotBlank() }.distinct()
        }.getOrDefault(emptyList())
        DashboardWorkspacePreferences(order, values[WORKSPACE_LANDING]?.takeIf { it.isNotBlank() })
    }

    suspend fun saveDashboardOrder(ids: List<String>) = context.dataStore.edit { values ->
        values[WORKSPACE_ORDER] = gson.toJson(ids.filter { it.isNotBlank() }.distinct())
    }

    suspend fun setLandingDashboard(id: String?) = context.dataStore.edit { values ->
        if (id.isNullOrBlank()) values.remove(WORKSPACE_LANDING) else values[WORKSPACE_LANDING] = id
    }

    suspend fun removeDashboardMetadata(id: String) = context.dataStore.edit { values ->
        val order = runCatching {
            JsonParser.parseString(values[WORKSPACE_ORDER] ?: "[]").asJsonArray.map { it.asString }
        }.getOrDefault(emptyList())
        values[WORKSPACE_ORDER] = gson.toJson(order.filterNot { it == id }.distinct())
        if (values[WORKSPACE_LANDING] == id) values.remove(WORKSPACE_LANDING)
    }

    private fun migrate(json: String): DashboardPreferences {
        val root = JsonParser.parseString(json).asJsonObject
        val parsed = gson.fromJson(root, DashboardPreferences::class.java) ?: DashboardPreferences()
        val defaults = DashboardPreferences()
        return parsed.copy(
            keepScreenOn = if (root.has("keepScreenOn")) parsed.keepScreenOn else defaults.keepScreenOn,
            swipePages = if (root.has("swipePages")) parsed.swipePages else defaults.swipePages,
            resumeFocusMode = if (root.has("resumeFocusMode")) parsed.resumeFocusMode else defaults.resumeFocusMode,
            showPeakHold = if (root.has("showPeakHold")) parsed.showPeakHold else defaults.showPeakHold,
            showMinMax = if (root.has("showMinMax")) parsed.showMinMax else defaults.showMinMax,
            alertSound = if (root.has("alertSound")) parsed.alertSound else defaults.alertSound,
            alertVibration = if (root.has("alertVibration")) parsed.alertVibration else defaults.alertVibration,
            autoReconnect = if (root.has("autoReconnect")) parsed.autoReconnect else defaults.autoReconnect,
            hudMode = if (root.has("hudMode")) parsed.hudMode else defaults.hudMode,
            hudMirror = if (root.has("hudMirror")) parsed.hudMirror else defaults.hudMirror,
            hudBrightnessPercent = if (root.has("hudBrightnessPercent")) parsed.hudBrightnessPercent else defaults.hudBrightnessPercent,
            adaptiveLayoutProfile = if (root.has("adaptiveLayoutProfile")) parsed.adaptiveLayoutProfile else defaults.adaptiveLayoutProfile,
            headUnitImmersive = if (root.has("headUnitImmersive")) parsed.headUnitImmersive else defaults.headUnitImmersive,
            preferredTransport = if (root.has("preferredTransport")) parsed.preferredTransport else defaults.preferredTransport,
            lastBluetoothAddress = if (root.has("lastBluetoothAddress")) parsed.lastBluetoothAddress else defaults.lastBluetoothAddress,
            autoConnectLastAdapter = if (root.has("autoConnectLastAdapter")) parsed.autoConnectLastAdapter else defaults.autoConnectLastAdapter,
            continuousMonitoring = if (root.has("continuousMonitoring")) parsed.continuousMonitoring else defaults.continuousMonitoring,
            diagnosticLogging = if (root.has("diagnosticLogging")) parsed.diagnosticLogging else defaults.diagnosticLogging,
            reconnectMaxDelaySeconds = if (root.has("reconnectMaxDelaySeconds")) {
                parsed.reconnectMaxDelaySeconds
            } else {
                defaults.reconnectMaxDelaySeconds
            },
        ).normalized()
    }

    private fun DashboardPreferences.normalized(): DashboardPreferences {
        val interval = reconnectIntervalSeconds.coerceIn(1, 30)
        return copy(
            controlsAutoHideSeconds = controlsAutoHideSeconds.coerceIn(2, 15),
            alertCooldownSeconds = alertCooldownSeconds.coerceIn(3, 120),
            hysteresis = hysteresis.coerceIn(0.0, 20.0),
            delayedAfterMillis = delayedAfterMillis.coerceIn(500L, 10_000L),
            staleAfterMillis = staleAfterMillis.coerceAtLeast(delayedAfterMillis + 500L).coerceAtMost(30_000L),
            reconnectIntervalSeconds = interval,
            reconnectAttempts = reconnectAttempts.coerceIn(1, 20),
            reconnectMaxDelaySeconds = reconnectMaxDelaySeconds.coerceIn(interval, 120),
            hudBrightnessPercent = hudBrightnessPercent.coerceIn(20, 100),
            lastBluetoothAddress = lastBluetoothAddress?.takeIf(MAC_ADDRESS::matches),
        )
    }

    companion object {
        private val WORKSPACE_ORDER = stringPreferencesKey("dashboard_workspace_order_v1")
        private val WORKSPACE_LANDING = stringPreferencesKey("dashboard_workspace_landing_v1")
        private val MAC_ADDRESS = Regex("(?i)(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")
    }
}
