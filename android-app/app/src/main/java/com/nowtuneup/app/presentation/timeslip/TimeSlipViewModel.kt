package com.nowtuneup.app.presentation.timeslip

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nowtuneup.app.BuildConfig
import com.nowtuneup.app.data.logging.DiagnosticLogger
import com.nowtuneup.app.data.obd.session.ObdSessionManager
import com.nowtuneup.app.data.preferences.SettingsRepository
import com.nowtuneup.app.data.timeslip.TimeSlipBestResults
import com.nowtuneup.app.data.timeslip.TimeSlipRepository
import com.nowtuneup.app.data.timeslip.TimeSlipSensorController
import com.nowtuneup.app.data.transport.ObdTransportManager
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.feature.timeslip.domain.DistanceTarget
import com.nowtuneup.app.feature.timeslip.domain.GpsStatus
import com.nowtuneup.app.feature.timeslip.domain.MeasurementQuality
import com.nowtuneup.app.feature.timeslip.domain.SensorFusionEngine
import com.nowtuneup.app.feature.timeslip.domain.SpeedMilestoneDefinition
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipConfig
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipEngine
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipEngineSnapshot
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipSimulator
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipStatus
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipTestMode
import com.nowtuneup.app.feature.timeslip.domain.TimeSlipUnitSystem
import com.nowtuneup.app.service.MonitoringService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TimeSlipPage { SETUP, LIVE, RESULT, HISTORY }
enum class TimeSlipHistorySort { DATE, PERFORMANCE }

data class TimeSlipUiState(
    val page: TimeSlipPage = TimeSlipPage.SETUP,
    val config: TimeSlipConfig = TimeSlipConfig(),
    val engine: TimeSlipEngineSnapshot = TimeSlipEngineSnapshot(),
    val gpsStatus: GpsStatus = GpsStatus(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val adapterName: String? = null,
    val currentSpeedKmh: Double = 0.0,
    val obdSampleRateHz: Double = 0.0,
    val gpsSampleRateHz: Double = 0.0,
    val averageGpsAccuracyMeters: Double? = null,
    val safetyAccepted: Boolean = false,
    val locationExplanationVisible: Boolean = false,
    val safetyDialogVisible: Boolean = false,
    val errorMessage: String? = null,
    val records: List<com.nowtuneup.app.feature.timeslip.domain.TimeSlipRecord> = emptyList(),
    val selectedRecord: com.nowtuneup.app.feature.timeslip.domain.TimeSlipRecord? = null,
    val bestResults: TimeSlipBestResults? = null,
    val historyVehicleFilter: String? = null,
    val historyModeFilter: TimeSlipTestMode? = null,
    val historySort: TimeSlipHistorySort = TimeSlipHistorySort.DATE,
    val includeLowConfidenceInBest: Boolean = false,
    val savedRecordIds: Set<String> = emptySet(),
) {
    val active: Boolean get() = engine.status in TimeSlipEngine.activeStatuses
    val running: Boolean get() = engine.status in setOf(TimeSlipStatus.LAUNCH_DETECTED, TimeSlipStatus.RUNNING)
}

@HiltViewModel
class TimeSlipViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: ObdSessionManager,
    private val transportManager: ObdTransportManager,
    private val sensors: TimeSlipSensorController,
    private val repository: TimeSlipRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: DiagnosticLogger,
) : ViewModel() {
    private val engine = TimeSlipEngine()
    private val fusion = SensorFusionEngine()
    private val obdTimes = ArrayDeque<Long>()
    private var gpsUnreliableSince: Long? = null
    private var performanceModeJob: Job? = null
    private var monitoringServiceStarted = false
    private val _uiState = MutableStateFlow(TimeSlipUiState())
    val uiState: StateFlow<TimeSlipUiState> = _uiState.asStateFlow()

    val history = repository.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        sensors.start()
        viewModelScope.launch {
            settingsRepository.timeSlipSafetyAccepted.collect { accepted ->
                _uiState.update { it.copy(safetyAccepted = accepted) }
            }
        }
        viewModelScope.launch {
            repository.records.collect { records ->
                val best = repository.bestResults(records, _uiState.value.includeLowConfidenceInBest)
                _uiState.update { state ->
                    state.copy(
                        records = records,
                        bestResults = best,
                        savedRecordIds = records.map { it.id }.toSet(),
                    )
                }
            }
        }
        viewModelScope.launch {
            session.speedTelemetry.collect { obd ->
                trackObdRate(obd.monotonicTimeMs)
                fusion.ingestObd(obd)?.let(::ingestFused)
            }
        }
        viewModelScope.launch {
            sensors.gpsSamples.collect { gps ->
                fusion.ingestGps(gps)?.let(::ingestFused)
            }
        }
        viewModelScope.launch {
            sensors.accelerometerSamples.collect { sample -> fusion.ingestAcceleration(sample) }
        }
        viewModelScope.launch {
            combine(session.connectionState, session.initialization) { connection, initialization ->
                connection to initialization
            }.collect { (connection, initialization) ->
                val adapter = initialization.adapterIdentity ?: transportManager.activeType().name
                _uiState.update { it.copy(connectionState = connection, adapterName = adapter) }
                val active = _uiState.value.active
                if (active && connection != ConnectionState.CONNECTED &&
                    _uiState.value.engine.status !in setOf(TimeSlipStatus.WAITING_FOR_CONNECTION, TimeSlipStatus.WAITING_FOR_GPS)
                ) {
                    applySnapshot(engine.connectionLost())
                    stopActiveResources()
                } else {
                    val gpsReady = sensors.gpsStatus.value.isUsable(SystemClock.elapsedRealtime())
                    applySnapshot(engine.updateReadiness(connection == ConnectionState.CONNECTED, gpsReady))
                    if (_uiState.value.active && connection == ConnectionState.CONNECTED) {
                        ensurePerformanceMode()
                        ensureMonitoringService()
                    }
                }
            }
        }
        viewModelScope.launch {
            sensors.gpsStatus.collect { gps ->
                _uiState.update {
                    it.copy(
                        gpsStatus = gps,
                        gpsSampleRateHz = gps.sampleRateHz,
                        averageGpsAccuracyMeters = sensors.averageAccuracyMeters(),
                    )
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(250L)
                updateHealthTick()
            }
        }
    }

    fun setPage(page: TimeSlipPage) {
        if (_uiState.value.active && page != TimeSlipPage.LIVE) return
        _uiState.update { it.copy(page = page) }
    }

    fun updateMode(mode: TimeSlipTestMode) = updateConfig { current ->
        current.copy(testMode = mode)
    }

    fun updateDistanceTarget(target: DistanceTarget) = updateConfig { it.copy(selectedDistanceTarget = target) }
    fun updateUnitSystem(system: TimeSlipUnitSystem) = updateConfig { it.copy(unitSystem = system) }
    fun updateVehicleName(name: String) = updateConfig { it.copy(vehicleName = name.take(60)) }
    fun updateRollingStart(value: Double) = updateConfig { it.copy(rollingStartSpeedKmh = value.coerceIn(0.0, 350.0)) }
    fun updateRollingTarget(value: Double) = updateConfig { it.copy(rollingTargetSpeedKmh = value.coerceIn(0.1, 420.0)) }
    fun setCustomSpeedEnabled(value: Boolean) = updateConfig { it.copy(customSpeedEnabled = value) }

    fun toggleMilestone(id: String) = updateConfig { current ->
        current.copy(
            speedMilestones = current.speedMilestones.map { milestone ->
                if (milestone.id == id) milestone.copy(enabled = !milestone.enabled) else milestone
            },
        )
    }

    fun replaceMilestone(definition: SpeedMilestoneDefinition) = updateConfig { current ->
        current.copy(speedMilestones = current.speedMilestones.map { if (it.id == definition.id) definition else it })
    }

    fun requestLocationPermissionExplanation() {
        _uiState.update { it.copy(locationExplanationVisible = true) }
    }

    fun dismissLocationExplanation() {
        _uiState.update { it.copy(locationExplanationVisible = false) }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        sensors.refreshProviderState()
        if (granted) sensors.start()
        _uiState.update {
            it.copy(
                locationExplanationVisible = false,
                errorMessage = if (granted) null else "ไม่ได้รับสิทธิ์ตำแหน่ง การทดสอบระยะทางจะใช้งานไม่ได้",
            )
        }
    }

    fun showSafetyDialog() {
        _uiState.update { it.copy(safetyDialogVisible = true) }
    }

    fun acknowledgeSafety() {
        viewModelScope.launch { settingsRepository.setTimeSlipSafetyAccepted(true) }
        _uiState.update { it.copy(safetyDialogVisible = false, safetyAccepted = true) }
    }

    fun dismissSafetyDialog() {
        _uiState.update { it.copy(safetyDialogVisible = false) }
    }

    fun armTest() {
        val state = _uiState.value
        if (!state.safetyAccepted) {
            showSafetyDialog()
            return
        }
        val config = state.config.normalized()
        config.validate()?.let { message ->
            _uiState.update { it.copy(errorMessage = message) }
            return
        }
        if (config.requiresGps && !sensors.hasLocationPermission()) {
            _uiState.update {
                it.copy(
                    locationExplanationVisible = true,
                    errorMessage = "การทดสอบระยะทางต้องใช้ตำแหน่งที่แม่นยำเพื่อคำนวณระยะและตรวจคุณภาพผล",
                )
            }
            return
        }
        sensors.start().onFailure { error ->
            _uiState.update { it.copy(errorMessage = error.message ?: "เปิดเซนเซอร์ไม่สำเร็จ") }
            return
        }
        val now = SystemClock.elapsedRealtime()
        val connectionReady = session.connectionState.value == ConnectionState.CONNECTED && session.initialization.value.ecuConnected
        val gpsReady = sensors.gpsStatus.value.isUsable(now)
        val result = engine.requestArm(
            requestedConfig = config,
            wallClockEpochMs = System.currentTimeMillis(),
            monotonicTimeMs = now,
            connectionReady = connectionReady,
            gpsReady = gpsReady,
            deviceName = session.initialization.value.adapterIdentity ?: transportManager.activeType().name,
        )
        result.onSuccess { snapshot ->
            fusion.reset()
            obdTimes.clear()
            applySnapshot(snapshot)
            _uiState.update { it.copy(config = config, page = TimeSlipPage.LIVE, errorMessage = null) }
            if (connectionReady) {
                ensurePerformanceMode()
                ensureMonitoringService()
            }
        }.onFailure { error ->
            _uiState.update { it.copy(errorMessage = error.message ?: "เริ่มการทดสอบไม่สำเร็จ") }
        }
    }

    fun cancelTest() {
        applySnapshot(engine.cancel())
        stopActiveResources()
        _uiState.update { it.copy(page = TimeSlipPage.RESULT) }
    }

    fun resetTest() {
        stopActiveResources()
        fusion.reset()
        applySnapshot(engine.reset())
        _uiState.update { it.copy(page = TimeSlipPage.SETUP, selectedRecord = null, errorMessage = null) }
    }

    fun saveResult() {
        val record = _uiState.value.engine.result ?: return
        if (record.status != TimeSlipStatus.COMPLETED || record.measurementQuality == MeasurementQuality.INVALID) {
            _uiState.update { it.copy(errorMessage = "บันทึกได้เฉพาะผลที่ทดสอบเสร็จและไม่เป็น Invalid") }
            return
        }
        viewModelScope.launch {
            runCatching { repository.save(record) }
                .onSuccess { _uiState.update { it.copy(errorMessage = null) } }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = "บันทึกผลไม่สำเร็จ: ${error.message}") } }
        }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            _uiState.update { state ->
                state.copy(selectedRecord = state.selectedRecord?.takeUnless { it.id == id })
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clear()
            _uiState.update { it.copy(selectedRecord = null) }
        }
    }

    fun selectRecord(id: String) {
        viewModelScope.launch {
            val record = repository.getById(id)
            _uiState.update { it.copy(selectedRecord = record, page = TimeSlipPage.RESULT) }
        }
    }

    fun setHistoryVehicleFilter(vehicle: String?) = _uiState.update { it.copy(historyVehicleFilter = vehicle) }
    fun setHistoryModeFilter(mode: TimeSlipTestMode?) = _uiState.update { it.copy(historyModeFilter = mode) }
    fun setHistorySort(sort: TimeSlipHistorySort) = _uiState.update { it.copy(historySort = sort) }

    fun setIncludeLowConfidenceInBest(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                includeLowConfidenceInBest = enabled,
                bestResults = repository.bestResults(state.records, enabled),
            )
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    fun runSimulation() {
        if (!BuildConfig.DEBUG) return
        val simulated = TimeSlipSimulator.run(_uiState.value.config)
        _uiState.update { it.copy(engine = simulated, page = TimeSlipPage.RESULT, selectedRecord = null) }
    }

    private fun updateConfig(change: (TimeSlipConfig) -> TimeSlipConfig) {
        if (_uiState.value.active) return
        _uiState.update { it.copy(config = change(it.config)) }
    }

    private fun ingestFused(sample: com.nowtuneup.app.feature.timeslip.domain.PerformanceSample) {
        _uiState.update { it.copy(currentSpeedKmh = sample.fusedSpeedKmh) }
        val state = _uiState.value
        if (!state.active) return
        engine.updateTelemetryMetrics(
            obdSampleRateHz = state.obdSampleRateHz,
            gpsSampleRateHz = sensors.gpsStatus.value.sampleRateHz,
            averageGpsAccuracyMeters = sensors.averageAccuracyMeters(),
        )
        val snapshot = engine.ingest(sample)
        applySnapshot(snapshot)
        if (snapshot.status in terminalStatuses) {
            stopActiveResources()
            _uiState.update { it.copy(page = TimeSlipPage.RESULT) }
        }
    }

    private fun applySnapshot(snapshot: TimeSlipEngineSnapshot) {
        _uiState.update { it.copy(engine = snapshot) }
    }

    private fun ensurePerformanceMode() {
        if (session.isTimeSlipMode() || performanceModeJob?.isActive == true) return
        performanceModeJob = viewModelScope.launch {
            session.setTimeSlipMode(true)
        }
    }

    private fun ensureMonitoringService() {
        if (monitoringServiceStarted || !_uiState.value.active) return
        runCatching {
            MonitoringService.startTimeSlip(context, _uiState.value.config.requiresGps)
        }.onSuccess {
            monitoringServiceStarted = true
        }.onFailure { error ->
            logger.warning("TimeSlip", "Foreground service could not start: ${error.message}")
            _uiState.update { it.copy(errorMessage = "เปิดโหมดทำงานต่อเนื่องไม่สำเร็จ กรุณาเปิดแอปไว้ระหว่างทดสอบ") }
        }
    }

    private fun stopActiveResources() {
        performanceModeJob?.cancel()
        performanceModeJob = viewModelScope.launch { session.setTimeSlipMode(false) }
        if (monitoringServiceStarted) MonitoringService.stopTimeSlip(context)
        monitoringServiceStarted = false
    }

    private fun trackObdRate(timestampMs: Long) {
        obdTimes.addLast(timestampMs)
        while (obdTimes.size > 1 && timestampMs - obdTimes.first() > RATE_WINDOW_MS) obdTimes.removeFirst()
        val rate = if (obdTimes.size < 2) 0.0 else {
            val elapsed = (obdTimes.last() - obdTimes.first()).coerceAtLeast(1L)
            (obdTimes.size - 1) * 1_000.0 / elapsed
        }
        _uiState.update { it.copy(obdSampleRateHz = rate) }
    }

    private fun updateHealthTick() {
        sensors.refreshProviderState()
        val state = _uiState.value
        val now = SystemClock.elapsedRealtime()
        val gps = sensors.gpsStatus.value
        val gpsUsable = gps.isUsable(now)
        engine.updateTelemetryMetrics(state.obdSampleRateHz, gps.sampleRateHz, sensors.averageAccuracyMeters())
        applySnapshot(engine.updateReadiness(state.connectionState == ConnectionState.CONNECTED, gpsUsable))

        if (state.active && state.config.requiresGps && state.engine.status in setOf(
                TimeSlipStatus.ARMED,
                TimeSlipStatus.LAUNCH_DETECTED,
                TimeSlipStatus.RUNNING,
            )
        ) {
            if (!gpsUsable) {
                val since = gpsUnreliableSince ?: now.also { gpsUnreliableSince = it }
                if (now - since >= GPS_FAILURE_GRACE_MS) {
                    applySnapshot(engine.gpsUnreliable())
                    stopActiveResources()
                    _uiState.update { it.copy(page = TimeSlipPage.RESULT) }
                }
            } else {
                gpsUnreliableSince = null
            }
        }
    }

    override fun onCleared() {
        if (_uiState.value.active) engine.cancel("แอปปิดก่อนการทดสอบเสร็จ")
        MonitoringService.stopTimeSlip(context)
        sensors.stop()
        super.onCleared()
    }

    companion object {
        private const val RATE_WINDOW_MS = 5_000L
        private const val GPS_FAILURE_GRACE_MS = 2_500L
        private val terminalStatuses = setOf(
            TimeSlipStatus.COMPLETED,
            TimeSlipStatus.CANCELLED,
            TimeSlipStatus.CONNECTION_LOST,
            TimeSlipStatus.GPS_UNRELIABLE,
            TimeSlipStatus.INVALID_RUN,
        )
    }
}
