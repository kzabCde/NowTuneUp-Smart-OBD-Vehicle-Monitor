package com.nowtuneup.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nowtuneup.app.data.dashboard.DashboardRepository
import com.nowtuneup.app.data.dashboard.DashboardStorageIssue
import com.nowtuneup.app.data.preferences.DashboardWorkspacePreferences
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.nowtuneup.app.data.local.dao.NtuDao
import com.nowtuneup.app.data.local.entity.DiagnosticScanEntity
import com.nowtuneup.app.data.logging.DiagnosticLogger
import com.nowtuneup.app.data.obd.session.ObdSessionManager
import com.nowtuneup.app.data.preferences.SettingsRepository
import com.nowtuneup.app.data.transport.ObdTransportManager
import com.nowtuneup.app.domain.alert.AlertEngine
import com.nowtuneup.app.domain.connection.ReconnectBackoff
import com.nowtuneup.app.domain.model.AdaptiveLayoutProfile
import com.nowtuneup.app.domain.model.AdapterSelfTestResult
import com.nowtuneup.app.domain.model.AlertSeverity
import com.nowtuneup.app.domain.model.BluetoothDeviceInfo
import com.nowtuneup.app.domain.model.ConnectionPhase
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.ConnectionUiState
import com.nowtuneup.app.domain.model.DashboardAlert
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardPreferences
import com.nowtuneup.app.domain.model.DiagnosticOverview
import com.nowtuneup.app.domain.model.Dtc
import com.nowtuneup.app.domain.model.ObdTransportType
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val session: ObdSessionManager,
    private val transportManager: ObdTransportManager,
    private val dao: NtuDao,
    private val dashboardRepository: DashboardRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: DiagnosticLogger,
    private val liveSessions: com.nowtuneup.app.feature.live.LiveSessionRepository,
) : ViewModel() {
    val connection = session.connectionState
    val initialization = session.initialization
    val readings = session.readings
    val adapterHealth = session.adapterHealth
    val vehicleIdentity = session.vehicleIdentity
    val turboQuality = session.turboQuality
    val diagnosticLogs = logger.entries
    private val workspacePreferences = settingsRepository.dashboardWorkspace.stateIn(
        viewModelScope, SharingStarted.Eagerly, DashboardWorkspacePreferences(),
    )
    private val workspace = dashboardRepository.workspace.stateIn(
        viewModelScope, SharingStarted.Eagerly, com.nowtuneup.app.data.dashboard.DashboardWorkspaceSnapshot(),
    )
    val dashboards = combine(workspace, workspacePreferences) { snapshot, preferences ->
        orderedDashboards(snapshot.dashboards, preferences.orderedIds)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val dashboardStorageIssues = workspace.map { it.issues }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<DashboardStorageIssue>())
    val dashboardLandingId = combine(workspace, workspacePreferences) { snapshot, preferences ->
        preferences.landingId?.takeIf { id -> snapshot.dashboards.any { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val workspaceMutation = Mutex()
    private val _dashboardPreferences = MutableStateFlow(DashboardPreferences())
    val dashboardPreferences = _dashboardPreferences.asStateFlow()
    private val dashboardPreferencesLoaded = MutableStateFlow(false)
    private val _dashboardWorkspaceReady = MutableStateFlow(false)
    val dashboardWorkspaceReady = _dashboardWorkspaceReady.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _dtcs = MutableStateFlow<List<Dtc>>(emptyList())
    val dtcs = _dtcs.asStateFlow()
    private val _diagnosticOverview = MutableStateFlow<DiagnosticOverview?>(null)
    val diagnosticOverview = _diagnosticOverview.asStateFlow()
    private val _adapterSelfTest = MutableStateFlow<AdapterSelfTestResult?>(null)
    val adapterSelfTest = _adapterSelfTest.asStateFlow()
    private val _readingStats = MutableStateFlow<Map<Int, ReadingStats>>(emptyMap())
    val readingStats = _readingStats.asStateFlow()
    private val _activeAlerts = MutableStateFlow<List<DashboardAlert>>(emptyList())
    val activeAlerts = _activeAlerts.asStateFlow()
    private val _alertEvents = MutableSharedFlow<DashboardAlert>(extraBufferCapacity = 8)
    val alertEvents = _alertEvents.asSharedFlow()
    private val pairedDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    private val lastTechnicalError = MutableStateFlow<String?>(null)
    private val reconnectAttempt = MutableStateFlow(0)
    private val _connectionUiState = MutableStateFlow(ConnectionUiState())
    val connectionUiState = _connectionUiState.asStateFlow()

    private val previousAlertSeverity = mutableMapOf<String, AlertSeverity>()
    private val lastAlertEventAt = mutableMapOf<String, Long>()
    private var reconnectJob: Job? = null
    private var manualDisconnect = false
    private var wasEcuConnected = false
    private var autoConnectAttemptedAddress: String? = null
    private var liveDataVisible = false
    private var dashboardDemandPids: Set<Int> = emptySet()

    init {
        viewModelScope.launch {
            settingsRepository.dashboardPreferences.collect { persisted ->
                // Publish actual persisted preferences before signalling that defaults are gone.
                _dashboardPreferences.value = persisted
                dashboardPreferencesLoaded.value = true
            }
        }
        viewModelScope.launch {
            runCatching {
                val initial = combine(dashboardRepository.workspace, settingsRepository.dashboardWorkspace,
                    settingsRepository.dashboardPreferences) { snapshot, workspace, preferences ->
                    Triple(snapshot.dashboards, workspace, preferences)
                }.first()
                val ordered = orderedDashboards(initial.first, initial.second.orderedIds)
                val id = initialDashboardId(ordered, initial.second.landingId, initial.third.selectedDashboardId)
                if (id != initial.third.selectedDashboardId) {
                    settingsRepository.updateDashboardPreferences { it.copy(selectedDashboardId = id) }
                }
                dashboardPreferencesLoaded.first { it }
                dashboardPreferences.first { it.selectedDashboardId == id }
                dashboards.first { current -> current.map { it.id } == ordered.map { it.id } }
                _dashboardWorkspaceReady.value = true
            }.onFailure { _error.value = "Unable to open the saved dashboard workspace: ${it.message}" }
        }
        viewModelScope.launch { liveSessions.recording.collect { applyPollingDemand() } }
        viewModelScope.launch {
            dashboardPreferences.collect { preferences ->
                session.setRefreshInterval(preferences.refreshRate.intervalMillis)
                logger.setEnabled(preferences.diagnosticLogging)
                if (
                    connection.value == ConnectionState.DISCONNECTED &&
                    transportManager.activeType() != preferences.preferredTransport
                ) {
                    transportManager.selectTransport(preferences.preferredTransport)
                }
                if (preferences.preferredTransport == ObdTransportType.BLUETOOTH_CLASSIC) {
                    val address = preferences.lastBluetoothAddress
                    transportManager.selectBluetoothDevice(address)
                    if (
                        preferences.autoConnectLastAdapter &&
                        address != null &&
                        autoConnectAttemptedAddress != address &&
                        connection.value == ConnectionState.DISCONNECTED &&
                        transportManager.bluetoothSupported() &&
                        transportManager.bluetoothPermissionGranted() &&
                        transportManager.bluetoothEnabled()
                    ) {
                        autoConnectAttemptedAddress = address
                        connectSelected()
                    }
                } else {
                    autoConnectAttemptedAddress = null
                }
                rebuildConnectionUiState()
            }
        }
        viewModelScope.launch {
            combine(dashboards, dashboardPreferences) { profiles, preferences ->
                val selected = profiles.firstOrNull { it.id == preferences.selectedDashboardId }
                selected?.let { profile ->
                    (profile.portrait.widgets + profile.landscape.widgets).map { it.pid }.filter { it >= 0 }.toSet()
                }.orEmpty()
            }.collect { pids ->
                dashboardDemandPids = pids
                applyPollingDemand()
            }
        }
        viewModelScope.launch {
            combine(readings, connection) { values, state -> values to state }.collect { (current, state) ->
                liveSessions.ingest(current, state == ConnectionState.CONNECTED)
                updateStats(current)
                evaluateAlerts(current)
            }
        }
        viewModelScope.launch {
            connection.collect(::handleConnectionState)
        }
        viewModelScope.launch {
            combine(
                connection,
                initialization,
                transportManager.transportType,
                pairedDevices,
                reconnectAttempt,
            ) { _, _, _, _, _ -> Unit }.collect {
                rebuildConnectionUiState()
            }
        }
    }

    private fun applyPollingDemand() {
        session.setRequestedPids(if (liveDataVisible || liveSessions.recording.value.active) null else dashboardDemandPids)
    }

    fun setLiveDataVisible(visible: Boolean) {
        liveDataVisible = visible
        applyPollingDemand()
    }

    fun refreshBluetoothState() {
        viewModelScope.launch {
            val result = transportManager.pairedBluetoothDevices()
            pairedDevices.value = result.getOrElse {
                logger.warning("Bluetooth", "Cannot list paired devices: ${it.message}")
                emptyList()
            }
            rebuildConnectionUiState()
        }
    }

    fun onBluetoothPermissionResult(granted: Boolean) {
        logger.info("Permission", "Bluetooth runtime permission granted=$granted")
        if (granted) {
            refreshBluetoothState()
        } else {
            _error.value = "ไม่ได้รับสิทธิ์ Bluetooth กรุณาอนุญาต Nearby devices เพื่อเชื่อมต่อ ELM327"
            lastTechnicalError.value = "BLUETOOTH_SCAN/BLUETOOTH_CONNECT denied"
            rebuildConnectionUiState()
        }
    }

    fun onBluetoothEnableResult() {
        logger.info("Bluetooth", "Bluetooth enable flow returned enabled=${transportManager.bluetoothEnabled()}")
        refreshBluetoothState()
    }

    fun selectTransport(type: ObdTransportType) {
        if (connection.value != ConnectionState.DISCONNECTED && connection.value != ConnectionState.ERROR) {
            _error.value = "กรุณาตัดการเชื่อมต่อก่อนเปลี่ยนประเภทการเชื่อมต่อ"
            return
        }
        transportManager.selectTransport(type).onFailure {
            showConnectionError(it)
            return
        }
        autoConnectAttemptedAddress = null
        updatePreferences { it.copy(preferredTransport = type) }
        if (type == ObdTransportType.BLUETOOTH_CLASSIC) refreshBluetoothState()
    }

    fun selectBluetoothDevice(device: BluetoothDeviceInfo) {
        transportManager.selectBluetoothDevice(device.address)
        autoConnectAttemptedAddress = null
        updatePreferences {
            it.copy(
                preferredTransport = ObdTransportType.BLUETOOTH_CLASSIC,
                lastBluetoothAddress = device.address,
            )
        }
        logger.info("Bluetooth", "Selected ${logger.safeDeviceLabel(device.name, device.address)}")
        rebuildConnectionUiState()
    }

    fun toggleConnection() {
        if (connection.value == ConnectionState.CONNECTED || connection.value == ConnectionState.CONNECTING) {
            disconnectManually()
        } else {
            connectSelected()
        }
    }

    fun connectSelected() = viewModelScope.launch {
        if (connection.value in setOf(ConnectionState.CONNECTING, ConnectionState.INITIALIZING)) return@launch
        val type = transportManager.activeType()
        if (type == ObdTransportType.BLUETOOTH_CLASSIC) {
            when {
                !transportManager.bluetoothSupported() -> {
                    _error.value = "อุปกรณ์ Android นี้ไม่รองรับ Bluetooth"
                    rebuildConnectionUiState()
                    return@launch
                }
                !transportManager.bluetoothPermissionGranted() -> {
                    _error.value = "ต้องอนุญาตสิทธิ์ Bluetooth ก่อนเชื่อมต่อ"
                    rebuildConnectionUiState()
                    return@launch
                }
                !transportManager.bluetoothEnabled() -> {
                    _error.value = "กรุณาเปิด Bluetooth ก่อนเชื่อมต่อ"
                    rebuildConnectionUiState()
                    return@launch
                }
                transportManager.selectedBluetoothAddress() == null -> {
                    _error.value = "กรุณาเลือก ELM327 ที่จับคู่ไว้แล้ว"
                    rebuildConnectionUiState()
                    return@launch
                }
            }
        }
        manualDisconnect = false
        reconnectJob?.cancel()
        reconnectAttempt.value = 0
        lastTechnicalError.value = null
        logger.info("Connection", "Starting OBD connection via ${type.name}")
        session.connect().onSuccess {
            wasEcuConnected = true
            reconnectAttempt.value = 0
            applyPollingDemand()
            logger.info("Connection", "Transport, ELM327 and ECU validation succeeded")
        }.onFailure(::showConnectionError)
    }

    fun disconnectManually() = viewModelScope.launch {
        manualDisconnect = true
        wasEcuConnected = false
        reconnectJob?.cancel()
        reconnectAttempt.value = 0
        logger.info("Connection", "Manual disconnect suppresses automatic reconnect")
        session.disconnect()
    }

    fun cancelReconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        reconnectAttempt.value = 0
        viewModelScope.launch { session.disconnect() }
    }

    fun pause() = session.pause()
    fun resume() = session.startPolling()

    fun onAppBackgrounded() {
        if (!dashboardPreferences.value.continuousMonitoring) session.pause()
    }

    fun onAppForegrounded() {
        if (connection.value == ConnectionState.CONNECTED) session.startPolling()
    }

    fun dismissError() {
        _error.value = null
    }

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
    fun setAutoConnectLastAdapter(value: Boolean) = updatePreferences { it.copy(autoConnectLastAdapter = value) }
    fun setContinuousMonitoring(value: Boolean) = updatePreferences { it.copy(continuousMonitoring = value) }
    fun setDiagnosticLogging(value: Boolean) = updatePreferences { it.copy(diagnosticLogging = value) }
    fun setControlsAutoHideSeconds(value: Int) = updatePreferences { it.copy(controlsAutoHideSeconds = value) }
    fun setAlertCooldownSeconds(value: Int) = updatePreferences { it.copy(alertCooldownSeconds = value) }
    fun setHysteresis(value: Double) = updatePreferences { it.copy(hysteresis = value) }
    fun setStaleAfterMillis(value: Long) = updatePreferences { it.copy(staleAfterMillis = value) }
    fun setReconnectIntervalSeconds(value: Int) = updatePreferences { it.copy(reconnectIntervalSeconds = value) }
    fun setReconnectAttempts(value: Int) = updatePreferences { it.copy(reconnectAttempts = value) }
    fun setReconnectMaxDelaySeconds(value: Int) = updatePreferences { it.copy(reconnectMaxDelaySeconds = value) }
    fun setHudMode(value: Boolean) = updatePreferences {
        it.copy(hudMode = value, focusMode = if (value) true else it.focusMode, touchLock = false)
    }
    fun setHudMirror(value: Boolean) = updatePreferences { it.copy(hudMirror = value) }
    fun setHudBrightnessPercent(value: Int) = updatePreferences { it.copy(hudBrightnessPercent = value) }
    fun setAdaptiveLayoutProfile(value: AdaptiveLayoutProfile) = updatePreferences { it.copy(adaptiveLayoutProfile = value) }
    fun setHeadUnitImmersive(value: Boolean) = updatePreferences { it.copy(headUnitImmersive = value) }

    fun setRefreshRate(value: RefreshRate) {
        session.setRefreshInterval(value.intervalMillis)
        updatePreferences { it.copy(refreshRate = value) }
    }

    private fun updatePreferences(change: (DashboardPreferences) -> DashboardPreferences) = viewModelScope.launch {
        settingsRepository.updateDashboardPreferences(change)
    }

    /** A mutation's source of truth is persisted state, not the asynchronously emitted UI snapshot. */
    private suspend fun savedWorkspaceForMutation(): com.nowtuneup.app.data.dashboard.DashboardWorkspaceSnapshot {
        val saved = dashboardRepository.workspace.first()
        val preferences = settingsRepository.dashboardWorkspace.first()
        return saved.copy(dashboards = orderedDashboards(saved.dashboards, preferences.orderedIds))
    }

    fun saveDashboard(config: DashboardConfig, onSaved: () -> Unit = {}) = viewModelScope.launch {
        workspaceMutation.withLock {
            runCatching {
                if (settingsRepository.dashboardPreferences.first().drivingMode) return@withLock
                val saved = savedWorkspaceForMutation()
                if (saved.issues.any { it.id == config.id && it.unreadable }) {
                    _error.value = "Reset the damaged layout from Manage dashboards before editing. Its original data is still retained."
                    return@withLock
                }
                val isNew = saved.dashboards.none { it.id == config.id }
                dashboardRepository.save(config.copy(isDefault = false))
                if (isNew) settingsRepository.saveDashboardOrder(saved.dashboards.map { it.id } + config.id)
                settingsRepository.updateDashboardPreferences { it.copy(selectedDashboardId = config.id) }
            }.onSuccess { onSaved() }
                .onFailure { _error.value = "Unable to save dashboard: ${it.message}" }
        }
    }

    fun renameDashboard(id: String, name: String) = viewModelScope.launch {
        val title = name.trim().take(60)
        if (title.isBlank()) return@launch
        workspaceMutation.withLock {
            runCatching {
                if (settingsRepository.dashboardPreferences.first().drivingMode) return@withLock
                val saved = savedWorkspaceForMutation()
                if (saved.issues.any { it.id == id && it.unreadable }) return@withLock
                val profile = saved.dashboards.firstOrNull { it.id == id } ?: return@withLock
                dashboardRepository.save(profile.copy(name = title))
            }.onFailure { _error.value = "Unable to rename dashboard: ${it.message}" }
        }
    }

    fun duplicateDashboard(config: DashboardConfig) = viewModelScope.launch {
        workspaceMutation.withLock {
            runCatching {
                if (settingsRepository.dashboardPreferences.first().drivingMode) return@withLock
                val saved = savedWorkspaceForMutation()
                if (saved.issues.any { it.id == config.id && it.unreadable }) return@withLock
                val source = saved.dashboards.firstOrNull { it.id == config.id } ?: return@withLock
                val copy = source.copy(id = "profile-${UUID.randomUUID()}", name = "${source.name} copy", isDefault = false)
                dashboardRepository.save(copy)
                settingsRepository.saveDashboardOrder(saved.dashboards.map { it.id } + copy.id)
                settingsRepository.updateDashboardPreferences { it.copy(selectedDashboardId = copy.id) }
            }.onFailure { _error.value = "Unable to duplicate dashboard: ${it.message}" }
        }
    }

    fun deleteDashboard(id: String) = viewModelScope.launch {
        workspaceMutation.withLock {
            runCatching {
                if (settingsRepository.dashboardPreferences.first().drivingMode) return@withLock
                val remaining = savedWorkspaceForMutation().dashboards.filterNot { it.id == id }
                dashboardRepository.delete(id)
                settingsRepository.removeDashboardMetadata(id)
                settingsRepository.updateDashboardPreferences { current ->
                    if (current.selectedDashboardId == id) current.copy(selectedDashboardId = remaining.firstOrNull()?.id.orEmpty())
                    else current
                }
            }.onFailure { _error.value = "Unable to delete dashboard: ${it.message}" }
        }
    }

    fun moveDashboard(id: String, direction: Int) = viewModelScope.launch {
        workspaceMutation.withLock {
            runCatching {
                if (settingsRepository.dashboardPreferences.first().drivingMode) return@withLock
                val ids = savedWorkspaceForMutation().dashboards.map { it.id }.toMutableList()
                val from = ids.indexOf(id)
                if (from < 0) return@withLock
                val to = (from + direction.coerceIn(-1, 1)).coerceIn(0, ids.lastIndex)
                ids.add(to, ids.removeAt(from))
                settingsRepository.saveDashboardOrder(ids)
            }.onFailure { _error.value = "Unable to reorder dashboards: ${it.message}" }
        }
    }

    fun setLandingDashboard(id: String) = viewModelScope.launch {
        workspaceMutation.withLock {
            runCatching {
                if (savedWorkspaceForMutation().dashboards.any { it.id == id }) settingsRepository.setLandingDashboard(id)
            }.onFailure { _error.value = "Unable to save the preferred dashboard: ${it.message}" }
        }
    }

    fun resetDashboardToTemplate(id: String, template: DashboardConfig) = viewModelScope.launch {
        workspaceMutation.withLock {
            runCatching {
                if (settingsRepository.dashboardPreferences.first().drivingMode) return@withLock
                val profile = savedWorkspaceForMutation().dashboards.firstOrNull { it.id == id } ?: return@withLock
                dashboardRepository.save(template.copy(id = id, name = profile.name, isDefault = false))
            }.onFailure { _error.value = "Unable to reset dashboard: ${it.message}" }
        }
    }

    fun resetReadingStats() {
        _readingStats.value = emptyMap()
    }

    /** Full read-only vehicle health scan: Stored/Pending/Permanent DTC, readiness, freeze frame and VIN. */
    fun scan() = viewModelScope.launch {
        session.readDiagnosticOverview().onSuccess { overview ->
            _diagnosticOverview.value = overview
            _dtcs.value = overview.allDtcs
            dao.insertScan(
                DiagnosticScanEntity(
                    readAt = overview.readAtMillis,
                    codes = overview.allDtcs.joinToString { code -> "${code.status}:${code.code}" },
                    raw = overview.allDtcs.firstOrNull()?.raw.orEmpty(),
                ),
            )
        }.onFailure(::showConnectionError)
    }

    fun refreshVehicleIdentity() = viewModelScope.launch {
        session.readVehicleIdentity().onFailure(::showConnectionError)
    }

    fun runAdapterSelfTest() = viewModelScope.launch {
        session.runAdapterSelfTest().onSuccess {
            _adapterSelfTest.value = it
        }.onFailure(::showConnectionError)
    }

    fun clearDtcsConfirmed() = viewModelScope.launch {
        session.clearDtcs().onSuccess {
            _dtcs.value = emptyList()
            _diagnosticOverview.value = null
        }.onFailure(::showConnectionError)
    }

    fun clearDiagnosticLogs() = logger.clear()
    fun exportDiagnosticLogs(): String = session.diagnosticReport()
    fun exportLastSessionReport(): String = session.lastSessionReport()

    private fun updateStats(current: List<VehicleReading>) {
        val next = _readingStats.value.toMutableMap()
        current.forEach { reading ->
            val value = reading.value ?: return@forEach
            if (!reading.supported || !value.isFinite() || connection.value != ConnectionState.CONNECTED ||
                System.currentTimeMillis() - reading.updatedAt !in 0..dashboardPreferences.value.staleAfterMillis) return@forEach
            val previous = next[reading.pid]
            next[reading.pid] = ReadingStats(
                minimum = previous?.minimum?.let { minOf(it, value) } ?: value,
                maximum = previous?.maximum?.let { maxOf(it, value) } ?: value,
                peak = null,
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
            val fresh = (
                connection.value == ConnectionState.CONNECTED && nativeReading != null &&
                    nativeReading.supported && value?.isFinite() == true &&
                    now - nativeReading.updatedAt in 0..preferences.staleAfterMillis
                )
            if (!fresh) {
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
                if (initialization.value.ecuConnected) {
                    wasEcuConnected = true
                    manualDisconnect = false
                    reconnectJob?.cancel()
                    reconnectAttempt.value = 0
                }
            }
            ConnectionState.ERROR -> scheduleReconnect()
            ConnectionState.DISCONNECTED -> if (wasEcuConnected) scheduleReconnect()
            else -> Unit
        }
        rebuildConnectionUiState()
    }

    private fun scheduleReconnect() {
        val preferences = dashboardPreferences.value
        if (!preferences.autoReconnect || manualDisconnect || reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            repeat(preferences.reconnectAttempts) { index ->
                val attempt = index + 1
                reconnectAttempt.value = attempt
                val delaySeconds = ReconnectBackoff.delaySeconds(
                    baseSeconds = preferences.reconnectIntervalSeconds,
                    maximumSeconds = preferences.reconnectMaxDelaySeconds,
                    attemptIndex = index,
                )
                logger.info("Reconnect", "Attempt $attempt/${preferences.reconnectAttempts} in ${delaySeconds}s")
                delay(delaySeconds * 1_000L)
                val recovered = connection.value == ConnectionState.CONNECTED && initialization.value.ecuConnected
                if (manualDisconnect || recovered) {
                    reconnectAttempt.value = 0
                    return@launch
                }
                session.connect().onSuccess {
                    reconnectAttempt.value = 0
                    wasEcuConnected = true
                    applyPollingDemand()
                    return@launch
                }.onFailure { error ->
                    lastTechnicalError.value = error.message
                    logger.warning("Reconnect", "Attempt $attempt failed: ${error.message}")
                }
            }
            reconnectAttempt.value = 0
            _error.value = "เชื่อมต่ออัตโนมัติไม่สำเร็จหลังลอง ${preferences.reconnectAttempts} ครั้ง"
        }
    }

    private fun rebuildConnectionUiState() {
        val type = transportManager.activeType()
        val bluetoothSupported = transportManager.bluetoothSupported()
        val permissionGranted = transportManager.bluetoothPermissionGranted()
        val bluetoothEnabled = transportManager.bluetoothEnabled()
        val selectedAddress = transportManager.selectedBluetoothAddress()
        val selected = pairedDevices.value.firstOrNull { it.address == selectedAddress }
        val init = initialization.value
        val phase = when {
            type == ObdTransportType.BLE_EXPERIMENTAL -> ConnectionPhase.UNSUPPORTED_ADAPTER
            type == ObdTransportType.BLUETOOTH_CLASSIC && !bluetoothSupported -> ConnectionPhase.BLUETOOTH_UNAVAILABLE
            type == ObdTransportType.BLUETOOTH_CLASSIC && !permissionGranted -> ConnectionPhase.PERMISSION_REQUIRED
            type == ObdTransportType.BLUETOOTH_CLASSIC && !bluetoothEnabled -> ConnectionPhase.BLUETOOTH_DISABLED
            reconnectAttempt.value > 0 -> ConnectionPhase.RECONNECTING
            connection.value == ConnectionState.REQUESTING_PERMISSION -> ConnectionPhase.PERMISSION_REQUIRED
            connection.value == ConnectionState.DEVICE_DETECTED -> ConnectionPhase.CONNECTING
            connection.value == ConnectionState.CONNECTING -> ConnectionPhase.CONNECTING
            connection.value == ConnectionState.INITIALIZING -> ConnectionPhase.INITIALIZING_ADAPTER
            connection.value == ConnectionState.CONNECTED && !init.ecuConnected -> ConnectionPhase.INITIALIZING_ADAPTER
            connection.value == ConnectionState.CONNECTED && init.ecuConnected -> ConnectionPhase.CONNECTED
            connection.value == ConnectionState.ERROR -> ConnectionPhase.CONNECTION_FAILED
            type == ObdTransportType.BLUETOOTH_CLASSIC && selected == null -> ConnectionPhase.DEVICE_SELECTION
            else -> ConnectionPhase.DISCONNECTED
        }
        _connectionUiState.value = ConnectionUiState(
            transportType = type,
            phase = phase,
            bluetoothSupported = bluetoothSupported,
            bluetoothEnabled = bluetoothEnabled,
            permissionGranted = permissionGranted,
            pairedDevices = pairedDevices.value,
            selectedDevice = selected,
            initialization = init,
            lastErrorThai = _error.value,
            technicalError = lastTechnicalError.value,
            reconnectAttempt = reconnectAttempt.value,
        )
    }

    private fun showConnectionError(error: Throwable) {
        lastTechnicalError.value = error.stackTraceToString().take(4_000)
        logger.error("Connection", "Operation failed", error)
        _error.value = thaiErrorMessage(error)
        rebuildConnectionUiState()
    }

    private fun thaiErrorMessage(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("permission", true) -> "ไม่ได้รับสิทธิ์เชื่อมต่ออุปกรณ์ กรุณาอนุญาตสิทธิ์แล้วลองใหม่"
            message.contains("not supported", true) -> "อุปกรณ์นี้ไม่รองรับ Bluetooth หรือรูปแบบการเชื่อมต่อที่เลือก"
            message.contains("disabled", true) -> "Bluetooth ปิดอยู่ กรุณาเปิด Bluetooth แล้วลองใหม่"
            message.contains("paired", true) -> "ยังไม่ได้จับคู่ ELM327 กรุณาจับคู่ในหน้าตั้งค่า Bluetooth ของ Android ก่อน"
            message.contains("identity", true) || message.contains("Unsupported", true) -> "อะแดปเตอร์ไม่ตอบสนองเหมือน ELM327 ที่รองรับ"
            message.contains("4100", true) || message.contains("ECU", true) -> "เชื่อมต่ออะแดปเตอร์ได้ แต่ ECU ไม่ตอบสนอง กรุณาเปิดสวิตช์กุญแจและตรวจพอร์ต OBD-II"
            message.contains("timeout", true) || message.contains("Timed out", true) -> "หมดเวลารอการตอบกลับจาก ELM327 กรุณาตรวจระยะ Bluetooth และสวิตช์กุญแจ"
            message.contains("socket", true) -> "การเชื่อมต่อ Bluetooth หลุดหรืออะแดปเตอร์อยู่นอกระยะ"
            else -> "ดำเนินการกับรถไม่สำเร็จ กรุณาตรวจ ELM327 สวิตช์กุญแจ และลองใหม่"
        }
    }

    override fun onCleared() {
        liveSessions.stop(reason = "Monitoring closed")
        reconnectJob?.cancel()
        session.close()
        super.onCleared()
    }
}

/** Ignore stale/orphan metadata; unlisted legacy dashboards retain a deterministic order. */
internal fun orderedDashboards(profiles: List<DashboardConfig>, orderedIds: List<String>): List<DashboardConfig> {
    val ranks = orderedIds.distinct().withIndex().associate { it.value to it.index }
    return profiles.distinctBy { it.id }.sortedWith(compareBy<DashboardConfig> { ranks[it.id] ?: Int.MAX_VALUE }
        .thenBy { it.name.lowercase() }.thenBy { it.id })
}

internal fun initialDashboardId(profiles: List<DashboardConfig>, landingId: String?, selectedId: String): String =
    landingId?.takeIf { id -> profiles.any { it.id == id } }
        ?: selectedId.takeIf { id -> profiles.any { it.id == id } }
        ?: profiles.firstOrNull()?.id.orEmpty()
