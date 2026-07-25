package com.nowtuneup.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.data.dashboard.DashboardRepository
import com.nowtuneup.app.data.local.dao.NtuDao
import com.nowtuneup.app.data.local.entity.DiagnosticScanEntity
import com.nowtuneup.app.data.local.entity.SampleEntity
import com.nowtuneup.app.data.local.entity.TripEntity
import com.nowtuneup.app.data.obd.session.ObdSessionManager
import com.nowtuneup.app.data.preferences.SettingsRepository
import com.nowtuneup.app.domain.alert.AlertEngine
import com.nowtuneup.app.domain.model.AdaptiveLayoutProfile
import com.nowtuneup.app.domain.model.AlertSeverity
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.DashboardAlert
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardPreferences
import com.nowtuneup.app.domain.model.Dtc
import com.nowtuneup.app.domain.model.HudColorPreset
import com.nowtuneup.app.domain.model.ReadingStats
import com.nowtuneup.app.domain.model.RefreshRate
import com.nowtuneup.app.domain.model.ThemeConfig
import com.nowtuneup.app.domain.model.VehicleReading
import com.nowtuneup.app.util.DisplayReadingAdapter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val session: ObdSessionManager,
    private val dao: NtuDao,
    private val dashboardRepository: DashboardRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val connection = session.connectionState
    val readings = session.readings
    val trips = dao.trips()
    val dashboards = dashboardRepository.dashboards.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardDefaults.presets,
    )
    val dashboardPreferences = settingsRepository.dashboardPreferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardPreferences(),
    )

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _dtcs = MutableStateFlow<List<Dtc>>(emptyList())
    val dtcs = _dtcs.asStateFlow()
    private val _readingStats = MutableStateFlow<Map<Int, ReadingStats>>(emptyMap())
    val readingStats = _readingStats.asStateFlow()
    private val _activeAlerts = MutableStateFlow<List<DashboardAlert>>(emptyList())
    val activeAlerts = _activeAlerts.asStateFlow()
    private val _alertEvents = MutableSharedFlow<DashboardAlert>(extraBufferCapacity = 8)
    val alertEvents = _alertEvents.asSharedFlow()

    private val previousAlertSeverity = mutableMapOf<String, AlertSeverity>()
    private val lastAlertEventAt = mutableMapOf<String, Long>()
    private var tripId: Long? = null
    private var recorder: Job? = null
    private var reconnectJob: Job? = null
    private var manualDisconnect = false
    private var wasConnected = false

    init {
        viewModelScope.launch {
            dashboardPreferences.collect { preferences ->
                session.setRefreshInterval(preferences.refreshRate.intervalMillis)
            }
        }
        viewModelScope.launch {
            readings.collect { current ->
                updateStats(current)
                evaluateAlerts(current)
            }
        }
        viewModelScope.launch {
            connection.collect(::handleConnectionState)
        }
    }

    fun toggleConnection() = viewModelScope.launch {
        if (connection.value == ConnectionState.CONNECTED) {
            manualDisconnect = true
            reconnectJob?.cancel()
            session.disconnect()
        } else {
            manualDisconnect = false
            session.connect().onFailure {
                _error.value = it.message ?: "Connection failed"
                scheduleReconnect()
            }
        }
    }

    fun pause() = session.pause()
    fun resume() = session.startPolling()
    fun dismissError() { _error.value = null }
    fun selectDashboard(id: String) = updatePreferences { it.copy(selectedDashboardId = id) }
    fun selectTheme(theme: ThemeConfig) = updatePreferences { it.copy(theme = theme) }
    fun setReduceMotion(value: Boolean) = updatePreferences { it.copy(reduceMotion = value) }
    fun setDrivingMode(value: Boolean) = updatePreferences { it.copy(drivingMode = value) }
    fun setFocusMode(value: Boolean) = updatePreferences {
        it.copy(focusMode = value, touchLock = if (value) it.touchLock else false)
    }
    fun setAutoFocusOnConnect(value: Boolean) = updatePreferences { it.copy(autoFocusOnConnect = value) }
    fun setTouchLock(value: Boolean) = updatePreferences { it.copy(touchLock = value) }
    fun setKeepScreenOn(value: Boolean) = updatePreferences { it.copy(keepScreenOn = value) }
    fun setSwipePages(value: Boolean) = updatePreferences { it.copy(swipePages = value) }
    fun setResumeFocusMode(value: Boolean) = updatePreferences { it.copy(resumeFocusMode = value) }
    fun setShowPeakHold(value: Boolean) = updatePreferences { it.copy(showPeakHold = value) }
    fun setShowMinMax(value: Boolean) = updatePreferences { it.copy(showMinMax = value) }
    fun setAlertSound(value: Boolean) = updatePreferences { it.copy(alertSound = value) }
    fun setAlertVibration(value: Boolean) = updatePreferences { it.copy(alertVibration = value) }
    fun setMuteAlerts(value: Boolean) = updatePreferences { it.copy(muteAlerts = value) }
    fun setAutoReconnect(value: Boolean) = updatePreferences { it.copy(autoReconnect = value) }
    fun setControlsAutoHideSeconds(value: Int) = updatePreferences { it.copy(controlsAutoHideSeconds = value) }
    fun setAlertCooldownSeconds(value: Int) = updatePreferences { it.copy(alertCooldownSeconds = value) }
    fun setHysteresis(value: Double) = updatePreferences { it.copy(hysteresis = value) }
    fun setStaleAfterMillis(value: Long) = updatePreferences { it.copy(staleAfterMillis = value) }
    fun setReconnectIntervalSeconds(value: Int) = updatePreferences { it.copy(reconnectIntervalSeconds = value) }
    fun setReconnectAttempts(value: Int) = updatePreferences { it.copy(reconnectAttempts = value) }
    fun setHudMode(value: Boolean) = updatePreferences {
        it.copy(hudMode = value, focusMode = if (value) true else it.focusMode, touchLock = false)
    }
    fun setHudMirror(value: Boolean) = updatePreferences { it.copy(hudMirror = value) }
    fun setHudBurnInProtection(value: Boolean) = updatePreferences { it.copy(hudBurnInProtection = value) }
    fun setHudBrightnessPercent(value: Int) = updatePreferences { it.copy(hudBrightnessPercent = value) }
    fun setHudColorPreset(value: HudColorPreset) = updatePreferences { it.copy(hudColorPreset = value) }
    fun setAdaptiveLayoutProfile(value: AdaptiveLayoutProfile) = updatePreferences { it.copy(adaptiveLayoutProfile = value) }
    fun setHeadUnitImmersive(value: Boolean) = updatePreferences { it.copy(headUnitImmersive = value) }

    fun setRefreshRate(value: RefreshRate) {
        session.setRefreshInterval(value.intervalMillis)
        updatePreferences { it.copy(refreshRate = value) }
    }

    private fun updatePreferences(change: (DashboardPreferences) -> DashboardPreferences) = viewModelScope.launch {
        settingsRepository.saveDashboardPreferences(change(dashboardPreferences.value))
    }

    fun saveDashboard(config: DashboardConfig) = viewModelScope.launch { dashboardRepository.save(config) }

    fun duplicateDashboard(config: DashboardConfig) = viewModelScope.launch {
        val copy = config.copy(
            id = "custom-${System.currentTimeMillis()}",
            name = "${config.name} Copy",
            isDefault = false,
        )
        dashboardRepository.save(copy)
        selectDashboard(copy.id)
    }

    fun resetReadingStats() {
        _readingStats.value = emptyMap()
    }

    fun scan() = viewModelScope.launch {
        session.readDtcs().onSuccess {
            _dtcs.value = it
            dao.insertScan(
                DiagnosticScanEntity(
                    readAt = System.currentTimeMillis(),
                    codes = it.joinToString { code -> code.code },
                    raw = it.firstOrNull()?.raw.orEmpty(),
                ),
            )
        }.onFailure { _error.value = it.message }
    }

    fun toggleTrip() = viewModelScope.launch {
        val active = tripId
        if (active != null) {
            recorder?.cancel()
            recorder = null
            dao.finishTrip(active, System.currentTimeMillis())
            tripId = null
        } else {
            resetReadingStats()
            val id = dao.startTrip(TripEntity(startTime = System.currentTimeMillis()))
            tripId = id
            recorder = viewModelScope.launch {
                while (isActive) {
                    val values = readings.value.associate { it.pid to it.value }
                    dao.insertSamples(
                        listOf(
                            SampleEntity(
                                tripId = id,
                                timestamp = System.currentTimeMillis(),
                                rpm = values[0x0C],
                                speedKmh = values[0x0D],
                                coolantTempC = values[0x05],
                                voltageV = values[0x42],
                                engineLoadPercent = values[0x04],
                                throttlePercent = values[0x11],
                            ),
                        ),
                    )
                    delay(1_000)
                }
            }
        }
    }

    private fun updateStats(current: List<VehicleReading>) {
        val next = _readingStats.value.toMutableMap()
        current.forEach { reading ->
            val value = reading.value ?: return@forEach
            if (!reading.supported) return@forEach
            val previous = next[reading.pid]
            next[reading.pid] = ReadingStats(
                minimum = previous?.minimum?.let { minOf(it, value) } ?: value,
                maximum = previous?.maximum?.let { maxOf(it, value) } ?: value,
                peak = previous?.peak?.let { maxOf(it, value) } ?: value,
                updatedAt = reading.updatedAt,
            )
        }
        _readingStats.value = next
    }

    private fun evaluateAlerts(current: List<VehicleReading>) {
        val preferences = dashboardPreferences.value
        val selected = dashboards.value.firstOrNull { it.id == preferences.selectedDashboardId } ?: return
        val widgets = (selected.portrait.widgets + selected.landscape.widgets).distinctBy { it.id }
        val readingsByPid = current.associateBy { it.pid }
        val now = System.currentTimeMillis()
        val alerts = mutableListOf<DashboardAlert>()

        widgets.forEach { widget ->
            val nativeReading = readingsByPid[widget.pid]
            val displayReading = DisplayReadingAdapter.reading(nativeReading, widget.unit)
            val value = displayReading?.value
            val fresh = nativeReading != null && nativeReading.supported && value != null &&
                now - nativeReading.updatedAt <= preferences.staleAfterMillis
            if (!fresh || value == null) {
                previousAlertSeverity[widget.id] = AlertSeverity.NORMAL
                return@forEach
            }

            val previous = previousAlertSeverity[widget.id] ?: AlertSeverity.NORMAL
            val severity = AlertEngine.evaluate(widget.threshold, value, previous, preferences.hysteresis)
            previousAlertSeverity[widget.id] = severity
            if (severity == AlertSeverity.NORMAL) return@forEach

            val alert = DashboardAlert(
                widgetId = widget.id,
                title = widget.title,
                value = value,
                unit = widget.unit,
                severity = severity,
                timestamp = now,
            )
            alerts += alert

            val lastEvent = lastAlertEventAt[widget.id] ?: 0L
            val severityIncreased = severity.ordinal > previous.ordinal
            val cooldownElapsed = now - lastEvent >= preferences.alertCooldownSeconds * 1_000L
            if (!preferences.muteAlerts && (severityIncreased || cooldownElapsed)) {
                lastAlertEventAt[widget.id] = now
                _alertEvents.tryEmit(alert)
            }
        }

        _activeAlerts.value = alerts.sortedByDescending { it.severity.ordinal }
    }

    private fun handleConnectionState(state: ConnectionState) {
        when (state) {
            ConnectionState.CONNECTED -> {
                wasConnected = true
                manualDisconnect = false
                reconnectJob?.cancel()
            }
            ConnectionState.ERROR -> scheduleReconnect()
            ConnectionState.DISCONNECTED -> if (wasConnected) scheduleReconnect()
            else -> Unit
        }
    }

    private fun scheduleReconnect() {
        val preferences = dashboardPreferences.value
        if (!preferences.autoReconnect || manualDisconnect || reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            repeat(preferences.reconnectAttempts) { attempt ->
                delay(preferences.reconnectIntervalSeconds * 1_000L)
                if (manualDisconnect || connection.value == ConnectionState.CONNECTED) return@launch
                session.connect().onSuccess { return@launch }.onFailure {
                    if (attempt == preferences.reconnectAttempts - 1) {
                        _error.value = "Auto reconnect stopped after ${preferences.reconnectAttempts} attempts"
                    }
                }
            }
        }
    }

    override fun onCleared() {
        reconnectJob?.cancel()
        session.close()
        super.onCleared()
    }
}
