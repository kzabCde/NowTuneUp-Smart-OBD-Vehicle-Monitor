package com.nowtuneup.app.data.obd.session

import android.os.SystemClock
import com.nowtuneup.app.data.logging.DiagnosticLogger
import com.nowtuneup.app.data.obd.elm.Elm327Client
import com.nowtuneup.app.data.obd.elm.toObdError
import com.nowtuneup.app.data.obd.parser.ObdResponseParser
import com.nowtuneup.app.data.obd.pid.DerivedPids
import com.nowtuneup.app.data.obd.pid.StandardPids
import com.nowtuneup.app.data.obd.pid.SupportedPidParser
import com.nowtuneup.app.data.obd.polling.PidPollingScheduler
import com.nowtuneup.app.data.obd.polling.PollingGroup
import com.nowtuneup.app.data.obd.polling.PollingSlot
import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.data.vehicle.VehicleProfileRepository
import com.nowtuneup.app.domain.model.AdapterSelfTestResult
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.DiagnosticOverview
import com.nowtuneup.app.domain.model.ObdError
import com.nowtuneup.app.domain.model.VehicleIdentity
import com.nowtuneup.app.domain.model.VehicleProfileRecord
import com.nowtuneup.app.domain.model.VehicleReading
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class ObdSessionManager @Inject constructor(
    private val transport: ObdTransport,
    private val elm327: Elm327Client,
    private val logger: DiagnosticLogger,
    private val vehicleProfiles: VehicleProfileRepository,
) {
    val connectionState = transport.connectionState
    val initialization = elm327.initialization

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _readings = MutableStateFlow(defaultReadings())
    val readings: StateFlow<List<VehicleReading>> = _readings.asStateFlow()
    private val _supportedPids = MutableStateFlow<Set<Int>>(emptySet())
    val supportedPids: StateFlow<Set<Int>> = _supportedPids.asStateFlow()
    private val _speedTelemetry = MutableStateFlow<ObdSpeedSample?>(null)
    val speedTelemetry: StateFlow<ObdSpeedSample?> = _speedTelemetry.asStateFlow()
    private val _speedReadiness = MutableStateFlow(SpeedPidReadiness())
    val speedReadiness: StateFlow<SpeedPidReadiness> = _speedReadiness.asStateFlow()
    private val _performanceSampling = MutableStateFlow(false)
    val performanceSampling: StateFlow<Boolean> = _performanceSampling.asStateFlow()
    private val _adapterHealth = MutableStateFlow(AdapterHealthState())
    val adapterHealth: StateFlow<AdapterHealthState> = _adapterHealth.asStateFlow()
    private val _vehicleIdentity = MutableStateFlow(VehicleIdentity())
    val vehicleIdentity: StateFlow<VehicleIdentity> = _vehicleIdentity.asStateFlow()
    private val _turboQuality = MutableStateFlow(TurboDataQuality.STALE)
    val turboQuality: StateFlow<TurboDataQuality> = _turboQuality.asStateFlow()

    private val turboEstimator = TurboPressureEstimator()
    private val healthTracker = AdapterHealthTracker()
    private val speedWindow = ArrayDeque<ObdSpeedSample>()
    private var polling: Job? = null
    private var identityProbe: Job? = null
    private var performanceIndex = 0
    private var latestRpm: Double? = null

    @Volatile
    private var refreshIntervalMillis: Long = 500L

    @Volatile
    private var requestedPids: Set<Int>? = DEFAULT_CORE_DEMAND

    suspend fun connect(): Result<Unit> {
        pause()
        identityProbe?.cancel()
        resetPerformanceTelemetry()
        turboEstimator.reset()
        _turboQuality.value = TurboDataQuality.STALE
        latestRpm = null
        healthTracker.reset()
        _adapterHealth.value = healthTracker.snapshot()
        elm327.resetInitializationState()
        transport.connect().onFailure { return Result.failure(it) }
        return elm327.initialize().mapCatching { result ->
            check(result.ecuConnected) { "ECU communication was not confirmed" }
            val discovered = discoverSupportedPids()
            check(discovered.isNotEmpty()) { "Vehicle reported no supported Mode 01 PIDs" }
            _supportedPids.value = discovered
            updateSupportFlags(discovered)
            updateSpeedReadiness()
            logger.info("OBD", "ECU ready with ${discovered.size} supported Mode 01 PIDs")
            startPolling()
            scheduleVehicleIdentityProbe()
        }.onFailure { error ->
            logger.error("OBD", "Connection or initialization failed", error)
            disconnect()
        }
    }

    private suspend fun discoverSupportedPids(): Set<Int> {
        val discovered = mutableSetOf<Int>()
        for (base in listOf(0x00, 0x20, 0x40, 0x60)) {
            val command = "01%02X".format(base)
            val response = elm327.executeCommand(command, timeoutMillis = 5_000L, retryLimit = 0).getOrNull() ?: break
            val frame = ObdResponseParser.normalize(response.raw, command).getOrNull()?.frames
                ?.firstOrNull { it.size >= 6 && it[0] == 0x41 && it[1] == base } ?: break
            discovered += SupportedPidParser.parse(base, frame.drop(2).take(4))
            if (base + 0x20 !in discovered) break
        }
        return discovered
    }

    private fun updateSupportFlags(discovered: Set<Int>) {
        val turboSupported = DerivedPids.MAP in discovered && DerivedPids.BAROMETRIC_PRESSURE in discovered
        _readings.update { values ->
            values.map { reading ->
                reading.copy(
                    supported = if (reading.pid == DerivedPids.TURBO_PRESSURE) turboSupported else reading.pid in discovered,
                    value = null,
                )
            }
        }
    }

    suspend fun disconnect() {
        if (initialization.value.ecuConnected) {
            vehicleProfiles.saveLastSessionReport(diagnosticReport())
        }
        pause()
        identityProbe?.cancel()
        identityProbe = null
        setPerformanceSampling(false)
        resetPerformanceTelemetry()
        turboEstimator.reset()
        _turboQuality.value = TurboDataQuality.STALE
        latestRpm = null
        _supportedPids.value = emptySet()
        elm327.resetInitializationState()
        transport.disconnect()
        _readings.update { readings -> readings.map { it.copy(value = null) } }
        updateSpeedReadiness()
    }

    /** Null requests all supported PIDs; a set requests only values needed by the current UX. */
    fun setRequestedPids(pids: Set<Int>?) {
        requestedPids = expandRequestedPids(pids)
        val label = requestedPids?.sorted()?.joinToString { "0x%02X".format(it) } ?: "ALL"
        logger.info("Polling", "Demand updated: $label")
    }

    private fun expandRequestedPids(pids: Set<Int>?): Set<Int>? {
        if (pids == null) return null
        val expanded = pids.toMutableSet()
        if (DerivedPids.TURBO_PRESSURE in expanded) {
            expanded.remove(DerivedPids.TURBO_PRESSURE)
            expanded += DerivedPids.MAP
            expanded += DerivedPids.BAROMETRIC_PRESSURE
        }
        return expanded
    }

    fun startPolling() {
        if (polling?.isActive == true || connectionState.value != ConnectionState.CONNECTED) return
        val standardScheduler = PidPollingScheduler(_supportedPids.value, requestedPids)
        if (standardScheduler.isEmpty()) return

        polling = scope.launch {
            var consecutiveHealthFailures = 0
            var failedSoftRecoveries = 0
            var successfulCommands = 0L
            logger.info("Polling", "Realtime polling started with adaptive demand-based pacing")

            try {
                while (isActive && connectionState.value == ConnectionState.CONNECTED) {
                    standardScheduler.updateDemand(requestedPids)
                    val slot = if (_performanceSampling.value) nextPerformanceSlot() else standardScheduler.next()
                    if (slot == null) {
                        delay(120L)
                        continue
                    }

                    val commandStartedAtMillis = System.currentTimeMillis()
                    val commandSentAtNanos = SystemClock.elapsedRealtimeNanos()
                    var hardFailure: Throwable? = null

                    try {
                        elm327.requestPid(
                            pid = slot.pid,
                            timeoutMillis = timeoutFor(slot.group),
                            retryLimit = 0,
                        ).onSuccess { reading ->
                            val value = reading.value ?: return@onSuccess
                            val responseReceivedAtNanos = SystemClock.elapsedRealtimeNanos()
                            val latencyMillis = ((responseReceivedAtNanos - commandSentAtNanos) / 1_000_000L).coerceAtLeast(0L)
                            consecutiveHealthFailures = 0
                            failedSoftRecoveries = 0
                            successfulCommands += 1
                            val health = healthTracker.recordSuccess(latencyMillis)
                            _adapterHealth.value = health
                            publish(
                                pid = reading.pid,
                                value = value,
                                commandSentAtNanos = commandSentAtNanos,
                                responseReceivedAtNanos = responseReceivedAtNanos,
                            )
                            if (health.totalCommands > 0 && health.totalCommands % PROFILE_HEALTH_SAVE_INTERVAL == 0L) {
                                persistVehicleProfile(health.recommendedMode.name)
                            }
                        }.onFailure { error ->
                            _adapterHealth.value = healthTracker.recordFailure()
                            when {
                                isHardTransportFailure(error) -> hardFailure = error
                                isTransportHealthFailure(error) -> consecutiveHealthFailures += 1
                                else -> consecutiveHealthFailures = 0
                            }
                            logger.warning(
                                "Polling",
                                "PID 01%02X failed • health=%d • %s".format(
                                    slot.pid,
                                    consecutiveHealthFailures,
                                    error.message.orEmpty(),
                                ),
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (unexpected: Throwable) {
                        _adapterHealth.value = healthTracker.recordFailure()
                        logger.error("Polling", "Unexpected PID loop error; keeping session alive", unexpected)
                        consecutiveHealthFailures += 1
                    }

                    if (hardFailure != null) {
                        logger.error("Polling", "Transport socket closed; automatic reconnect required", hardFailure)
                        transport.disconnect()
                        break
                    }

                    if (consecutiveHealthFailures >= SOFT_RECOVERY_THRESHOLD) {
                        logger.warning("Polling", "ELM327 response stream delayed; running soft resync")
                        val recovered = runCatching { elm327.recoverLiveSession().isSuccess }.getOrDefault(false)
                        _adapterHealth.value = healthTracker.recordRecovery()
                        consecutiveHealthFailures = 0
                        if (recovered) {
                            failedSoftRecoveries = 0
                            delay(POST_RECOVERY_SETTLE_MILLIS)
                        } else {
                            failedSoftRecoveries += 1
                            logger.warning("Polling", "Soft resync did not complete • attempt=$failedSoftRecoveries")
                            delay(RECOVERY_BACKOFF_MILLIS)
                            if (failedSoftRecoveries >= MAX_FAILED_SOFT_RECOVERIES) {
                                logger.warning("Polling", "Adapter remains slow; adaptive mode will stay conservative")
                                failedSoftRecoveries = 0
                            }
                        }
                    }

                    val elapsed = System.currentTimeMillis() - commandStartedAtMillis
                    val pacing = commandPacingMillis()
                    if (elapsed < pacing) delay(pacing - elapsed)

                    if (successfulCommands > 0 && successfulCommands % HEALTH_LOG_EVERY_COMMANDS == 0L) {
                        val health = _adapterHealth.value
                        logger.info(
                            "Polling",
                            "Health=%s • %.2f cmd/s • %d ms • %.1f%% success • demand=%s".format(
                                health.grade.name,
                                health.commandsPerSecond,
                                health.averageLatencyMillis,
                                health.successRate * 100.0,
                                if (_performanceSampling.value) "TIME_SLIP" else "NORMAL",
                            ),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unexpected: Throwable) {
                logger.error("Polling", "Realtime loop stopped unexpectedly", unexpected)
            } finally {
                logger.info("Polling", "Realtime polling stopped")
            }
        }
    }

    /** Uses a small, adapter-safe speed/RPM schedule only while the Time Slip screen is active. */
    fun setPerformanceSampling(enabled: Boolean) {
        if (_performanceSampling.value == enabled) return
        _performanceSampling.value = enabled
        performanceIndex = 0
        logger.info("TimeSlip", if (enabled) "Speed-priority sampling enabled" else "Demand scheduler restored")
    }

    private fun nextPerformanceSlot(): PollingSlot? {
        val supported = _supportedPids.value
        val sequence = buildList {
            if (VEHICLE_SPEED_PID in supported) add(PollingSlot(VEHICLE_SPEED_PID, PollingGroup.FAST))
            if (VEHICLE_SPEED_PID in supported) add(PollingSlot(VEHICLE_SPEED_PID, PollingGroup.FAST))
            if (ENGINE_RPM_PID in supported) add(PollingSlot(ENGINE_RPM_PID, PollingGroup.FAST))
        }
        if (sequence.isEmpty()) return null
        val slot = sequence[performanceIndex % sequence.size]
        performanceIndex = (performanceIndex + 1) % sequence.size
        return slot
    }

    fun pause() {
        polling?.cancel()
        polling = null
    }

    fun setRefreshInterval(intervalMillis: Long) {
        refreshIntervalMillis = intervalMillis.coerceIn(250L, 1_500L)
    }

    suspend fun readDtcs() = withPollingPaused { elm327.readStoredDtcs() }

    suspend fun readVehicleIdentity(): Result<String> = withPollingPaused {
        elm327.readVin().onSuccess { vin -> updateVehicleIdentity(vin) }
    }

    suspend fun readDiagnosticOverview(): Result<DiagnosticOverview> = withPollingPaused {
        val stored = elm327.readStoredDtcs().getOrElse { emptyList() }
        val pending = elm327.readPendingDtcs().getOrElse { emptyList() }
        val permanent = elm327.readPermanentDtcs().getOrElse { emptyList() }
        val readiness = elm327.readReadiness().getOrNull()
        val freezeFrame = elm327.readFreezeFrameSummary().getOrNull()
        val vin = elm327.readVin().getOrNull() ?: _vehicleIdentity.value.vin
        if (vin != null) updateVehicleIdentity(vin)
        Result.success(
            DiagnosticOverview(
                stored = stored,
                pending = pending,
                permanent = permanent,
                readiness = readiness,
                freezeFrame = freezeFrame,
                vin = vin,
                readAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun runAdapterSelfTest(): Result<AdapterSelfTestResult> = withPollingPaused {
        val details = mutableListOf<String>()
        val latencies = mutableListOf<Long>()
        var passed = 0
        var total = 0
        val identity = initialization.value.adapterIdentity

        total += 1
        if (!identity.isNullOrBlank()) {
            passed += 1
            details += "ELM identity: $identity"
        } else {
            details += "ELM identity: unavailable"
        }

        suspend fun check(label: String, action: suspend () -> Boolean) {
            total += 1
            val started = SystemClock.elapsedRealtime()
            val ok = runCatching { action() }.getOrDefault(false)
            latencies += (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
            if (ok) passed += 1
            details += "$label: ${if (ok) "PASS" else "NO DATA"}"
        }

        var voltage: Double? = null
        check("Adapter voltage") {
            voltage = elm327.readVoltage().getOrNull()
            voltage != null
        }
        if (ENGINE_RPM_PID in _supportedPids.value) check("RPM PID 010C") { elm327.requestPid(ENGINE_RPM_PID).isSuccess }
        if (VEHICLE_SPEED_PID in _supportedPids.value) check("Speed PID 010D") { elm327.requestPid(VEHICLE_SPEED_PID).isSuccess }
        if (DerivedPids.MAP in _supportedPids.value) check("MAP PID 010B") { elm327.requestPid(DerivedPids.MAP).isSuccess }
        if (DerivedPids.BAROMETRIC_PRESSURE in _supportedPids.value) {
            check("BARO PID 0133") { elm327.requestPid(DerivedPids.BAROMETRIC_PRESSURE).isSuccess }
        }

        val averageLatency = latencies.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L
        val passRate = if (total > 0) passed.toDouble() / total else 0.0
        val recommended = when {
            passRate < 0.85 || averageLatency > 450L -> AdaptivePollingMode.STABLE
            passRate >= 0.98 && averageLatency in 1..120 -> AdaptivePollingMode.FAST
            else -> AdaptivePollingMode.BALANCED
        }
        persistVehicleProfile(recommended.name)
        Result.success(
            AdapterSelfTestResult(
                adapterIdentity = identity,
                voltage = voltage,
                passedChecks = passed,
                totalChecks = total,
                averageLatencyMillis = averageLatency,
                timeSlipSupported = VEHICLE_SPEED_PID in _supportedPids.value,
                turboSupported = DerivedPids.MAP in _supportedPids.value && DerivedPids.BAROMETRIC_PRESSURE in _supportedPids.value,
                recommendedMode = recommended.name,
                details = details,
            ),
        )
    }

    suspend fun clearDtcs(): Result<Unit> = withPollingPaused {
        elm327.clearStoredDtcs().onSuccess {
            logger.warning("DTC", "Mode 04 clear command acknowledged")
        }
    }

    private suspend fun <T> withPollingPaused(block: suspend () -> Result<T>): Result<T> {
        val shouldResume = connectionState.value == ConnectionState.CONNECTED
        pause()
        return try {
            block()
        } finally {
            if (shouldResume && connectionState.value == ConnectionState.CONNECTED) startPolling()
        }
    }

    private fun scheduleVehicleIdentityProbe() {
        identityProbe?.cancel()
        identityProbe = scope.launch {
            delay(1_200L)
            readVehicleIdentity().onFailure {
                logger.info("Vehicle", "Mode 09 VIN not available: ${it.message}")
            }
        }
    }

    private fun updateVehicleIdentity(vin: String) {
        val normalized = vin.trim().uppercase()
        if (normalized.length != 17) return
        _vehicleIdentity.value = VehicleIdentity(normalized, System.currentTimeMillis())
        persistVehicleProfile(_adapterHealth.value.recommendedMode.name)
    }

    private fun persistVehicleProfile(recommendedMode: String) {
        val vin = _vehicleIdentity.value.vin ?: return
        val previous = vehicleProfiles.find(vin)
        vehicleProfiles.save(
            VehicleProfileRecord(
                vin = vin,
                displayName = previous?.displayName ?: vin,
                adapterIdentity = initialization.value.adapterIdentity,
                supportedPids = _supportedPids.value,
                recommendedPollingMode = recommendedMode,
                lastSeenAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun diagnosticReport(): String = buildString {
        val init = initialization.value
        val readiness = currentSpeedReadiness()
        val health = _adapterHealth.value
        appendLine("NOWTUNEUP HARDWARE DIAGNOSTIC REPORT")
        appendLine("Generated: ${java.util.Date()}")
        appendLine("Connection: ${connectionState.value}")
        appendLine("ELM identity: ${init.adapterIdentity ?: "unknown"}")
        appendLine("VIN: ${_vehicleIdentity.value.vin ?: "unavailable"}")
        appendLine("Adapter initialized: ${init.adapterInitialized}")
        appendLine("ECU connected: ${init.ecuConnected}")
        appendLine("Supported Mode 01 PIDs: ${_supportedPids.value.sorted().joinToString { "0x%02X".format(it) }}")
        appendLine("Requested PIDs: ${requestedPids?.sorted()?.joinToString { "0x%02X".format(it) } ?: "ALL"}")
        appendLine("Adapter health: ${health.grade} / ${health.recommendedMode}")
        appendLine("Average latency: ${health.averageLatencyMillis} ms")
        appendLine("Success rate: ${"%.1f".format(health.successRate * 100.0)} %")
        appendLine("Command rate: ${"%.2f".format(health.commandsPerSecond)} commands/s")
        appendLine("Soft recoveries: ${health.softRecoveries}")
        appendLine("Turbo quality: ${_turboQuality.value}")
        appendLine("Time Slip sampling: ${_performanceSampling.value}")
        appendLine("Speed supported: ${readiness.supported}")
        appendLine("Speed sample rate: ${"%.2f".format(readiness.sampleRateHz)} Hz")
        appendLine("Speed latency: ${_speedTelemetry.value?.transportLatencyMillis ?: -1L} ms")
        appendLine("Speed readiness: ${readiness.reasonThai}")
        appendLine()
        appendLine("DIAGNOSTIC LOG")
        append(logger.exportText())
    }

    fun lastSessionReport(): String = vehicleProfiles.lastSessionReport().ifBlank { diagnosticReport() }

    fun close() {
        pause()
    }

    fun shutdown() {
        pause()
        identityProbe?.cancel()
        scope.cancel()
    }

    private fun commandPacingMillis(): Long {
        val base = when {
            _performanceSampling.value -> 45L
            refreshIntervalMillis <= 300L -> 55L
            refreshIntervalMillis <= 700L -> 80L
            else -> 120L
        }
        val adaptiveFloor = when (_adapterHealth.value.recommendedMode) {
            AdaptivePollingMode.FAST -> 45L
            AdaptivePollingMode.BALANCED -> 75L
            AdaptivePollingMode.STABLE -> 140L
        }
        return maxOf(base, adaptiveFloor)
    }

    private fun timeoutFor(group: PollingGroup): Long {
        val adaptiveExtra = when (_adapterHealth.value.recommendedMode) {
            AdaptivePollingMode.FAST -> 0L
            AdaptivePollingMode.BALANCED -> 300L
            AdaptivePollingMode.STABLE -> 800L
        }
        return when (group) {
            PollingGroup.FAST -> (if (_performanceSampling.value) 1_500L else 1_700L) + adaptiveExtra
            PollingGroup.NORMAL -> 2_000L + adaptiveExtra
            PollingGroup.SLOW -> 2_400L + adaptiveExtra
        }
    }

    private fun isTransportHealthFailure(error: Throwable): Boolean = when (error.toObdError()) {
        ObdError.Timeout,
        ObdError.DeviceDisconnected,
        ObdError.BufferFull,
        ObdError.EcuNotResponding,
        -> true

        else -> {
            val message = error.message.orEmpty()
            message.contains("socket", ignoreCase = true) ||
                message.contains("closed", ignoreCase = true) ||
                message.contains("broken pipe", ignoreCase = true)
        }
    }

    private fun isHardTransportFailure(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return error.toObdError() == ObdError.DeviceDisconnected ||
            message.contains("socket closed", ignoreCase = true) ||
            message.contains("broken pipe", ignoreCase = true) ||
            message.contains("connection reset", ignoreCase = true)
    }

    private fun publish(
        pid: Int,
        value: Double,
        commandSentAtNanos: Long,
        responseReceivedAtNanos: Long,
    ) {
        val now = System.currentTimeMillis()
        if (pid == ENGINE_RPM_PID) latestRpm = value
        if (pid == DerivedPids.MAP && (latestRpm ?: Double.MAX_VALUE) <= KOEO_MAX_RPM) {
            turboEstimator.calibrateBarometricBaseline(value, now)
        }
        val estimate = if (pid == DerivedPids.MAP || pid == DerivedPids.BAROMETRIC_PRESSURE) {
            turboEstimator.update(
                pid = pid,
                valueKpa = value,
                nowMillis = now,
                mapPid = DerivedPids.MAP,
                baroPid = DerivedPids.BAROMETRIC_PRESSURE,
            )
        } else {
            turboEstimator.current(now)
        }
        _turboQuality.value = estimate.quality

        _readings.update { readings ->
            readings.map { reading ->
                when (reading.pid) {
                    pid -> reading.copy(value = value, updatedAt = now)
                    DerivedPids.TURBO_PRESSURE -> {
                        val turboSupported = DerivedPids.MAP in _supportedPids.value &&
                            DerivedPids.BAROMETRIC_PRESSURE in _supportedPids.value
                        reading.copy(
                            value = if (turboSupported && estimate.quality == TurboDataQuality.GOOD) estimate.valueKpa else null,
                            supported = turboSupported,
                            updatedAt = estimate.updatedAtMillis,
                        )
                    }

                    else -> reading
                }
            }
        }

        if (pid == VEHICLE_SPEED_PID) {
            val sample = ObdSpeedSample(
                speedKmh = value,
                commandSentAtNanos = commandSentAtNanos,
                responseReceivedAtNanos = responseReceivedAtNanos,
                wallClockMillis = now,
                transportLatencyMillis = ((responseReceivedAtNanos - commandSentAtNanos) / 1_000_000L).coerceAtLeast(0L),
            )
            _speedTelemetry.value = sample
            speedWindow.addLast(sample)
            while (speedWindow.size > SPEED_WINDOW_SIZE) speedWindow.removeFirst()
            updateSpeedReadiness()
        }
    }

    private fun currentSpeedReadiness(): SpeedPidReadiness {
        val supported = VEHICLE_SPEED_PID in _supportedPids.value
        val latest = _speedTelemetry.value
        val ageMillis = latest?.let {
            ((SystemClock.elapsedRealtimeNanos() - it.responseReceivedAtNanos) / 1_000_000L).coerceAtLeast(0L)
        } ?: Long.MAX_VALUE
        val rate = if (speedWindow.size >= 2) {
            val first = speedWindow.first.responseReceivedAtNanos
            val last = speedWindow.last.responseReceivedAtNanos
            val durationSeconds = (last - first) / 1_000_000_000.0
            if (durationSeconds > 0.0) (speedWindow.size - 1) / durationSeconds else 0.0
        } else {
            0.0
        }
        val sampleCount = speedWindow.size
        val fresh = ageMillis <= SPEED_FRESH_MILLIS
        val ready = supported && latest != null && fresh && sampleCount >= MIN_READY_SAMPLES && rate >= MIN_READY_RATE_HZ
        val reason = when {
            !supported -> "รถไม่รองรับ PID ความเร็ว 010D"
            latest == null -> "กำลังรอค่าความเร็วจาก ECU"
            !fresh -> "ข้อมูลความเร็วล่าช้า กำลังปรับการเชื่อมต่อ"
            sampleCount < MIN_READY_SAMPLES -> "กำลังเตรียมข้อมูลความเร็ว $sampleCount/$MIN_READY_SAMPLES"
            rate < MIN_READY_RATE_HZ -> "ข้อมูลยังช้า ${"%.1f".format(rate)} Hz กรุณารอสักครู่"
            else -> "พร้อมทดสอบ • ${"%.1f".format(rate)} Hz"
        }
        return SpeedPidReadiness(
            supported = supported,
            hasValue = latest != null,
            fresh = fresh,
            stableSamples = sampleCount,
            sampleRateHz = rate,
            lastSampleAgeMillis = ageMillis,
            ready = ready,
            reasonThai = reason,
        )
    }

    private fun updateSpeedReadiness() {
        _speedReadiness.value = currentSpeedReadiness()
    }

    private fun resetPerformanceTelemetry() {
        speedWindow.clear()
        _speedTelemetry.value = null
        _speedReadiness.value = SpeedPidReadiness()
    }

    companion object {
        private const val VEHICLE_SPEED_PID = 0x0D
        private const val ENGINE_RPM_PID = 0x0C
        private const val SOFT_RECOVERY_THRESHOLD = 5
        private const val MAX_FAILED_SOFT_RECOVERIES = 6
        private const val POST_RECOVERY_SETTLE_MILLIS = 220L
        private const val RECOVERY_BACKOFF_MILLIS = 350L
        private const val HEALTH_LOG_EVERY_COMMANDS = 50L
        private const val PROFILE_HEALTH_SAVE_INTERVAL = 50L
        private const val SPEED_WINDOW_SIZE = 8
        private const val MIN_READY_SAMPLES = 3
        private const val MIN_READY_RATE_HZ = 0.8
        private const val SPEED_FRESH_MILLIS = 1_800L
        private const val KOEO_MAX_RPM = 50.0
        private val DEFAULT_CORE_DEMAND = setOf(0x0C, 0x0D, 0x05)

        fun defaultReadings(): List<VehicleReading> = StandardPids.all.map { definition ->
            VehicleReading(
                pid = definition.pid,
                name = definition.name,
                value = null,
                unit = definition.unit,
                supported = false,
                minimum = definition.minimum,
                maximum = definition.maximum,
            )
        } + VehicleReading(
            pid = DerivedPids.TURBO_PRESSURE,
            name = "Turbo pressure",
            value = null,
            unit = "kPa",
            supported = false,
            minimum = -100.0,
            maximum = 250.0,
        )
    }
}
