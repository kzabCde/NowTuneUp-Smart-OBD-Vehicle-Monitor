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
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.ObdError
import com.nowtuneup.app.domain.model.VehicleReading
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
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

    private val turboEstimator = TurboPressureEstimator()
    private val speedWindow = ArrayDeque<ObdSpeedSample>()
    private var polling: Job? = null
    private var performanceIndex = 0

    @Volatile
    private var refreshIntervalMillis: Long = 500

    suspend fun connect(): Result<Unit> {
        pause()
        resetPerformanceTelemetry()
        turboEstimator.reset()
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
        pause()
        setPerformanceSampling(false)
        resetPerformanceTelemetry()
        turboEstimator.reset()
        _supportedPids.value = emptySet()
        elm327.resetInitializationState()
        transport.disconnect()
        _readings.update { readings -> readings.map { it.copy(value = null) } }
        updateSpeedReadiness()
    }

    fun startPolling() {
        if (polling?.isActive == true || connectionState.value != ConnectionState.CONNECTED) return
        val standardScheduler = PidPollingScheduler(_supportedPids.value)
        if (standardScheduler.isEmpty()) return

        polling = scope.launch {
            var consecutiveTransportFailures = 0
            var failedSoftRecoveries = 0
            var successfulCommands = 0L
            val startedAt = System.currentTimeMillis()
            logger.info("Polling", "Realtime polling started with ${standardScheduler.snapshot().size} priority slots")

            while (isActive && connectionState.value == ConnectionState.CONNECTED) {
                val slot = if (_performanceSampling.value) nextPerformanceSlot() else standardScheduler.next()
                if (slot == null) {
                    delay(100L)
                    continue
                }

                val commandStartedAtMillis = System.currentTimeMillis()
                val commandSentAtNanos = SystemClock.elapsedRealtimeNanos()
                var hardTransportFailure = false
                elm327.requestPid(
                    pid = slot.pid,
                    timeoutMillis = timeoutFor(slot.group),
                    retryLimit = 0,
                ).onSuccess { reading ->
                    val responseReceivedAtNanos = SystemClock.elapsedRealtimeNanos()
                    consecutiveTransportFailures = 0
                    failedSoftRecoveries = 0
                    successfulCommands += 1
                    publish(
                        pid = reading.pid,
                        value = reading.value ?: return@onSuccess,
                        commandSentAtNanos = commandSentAtNanos,
                        responseReceivedAtNanos = responseReceivedAtNanos,
                    )
                }.onFailure { error ->
                    if (isTransportHealthFailure(error)) {
                        consecutiveTransportFailures += 1
                        hardTransportFailure = isHardTransportFailure(error)
                    } else {
                        consecutiveTransportFailures = 0
                        failedSoftRecoveries = 0
                    }
                    logger.warning(
                        "Polling",
                        "PID 01%02X failed (%d/%d before soft recovery): %s".format(
                            slot.pid,
                            consecutiveTransportFailures,
                            SOFT_RECOVERY_THRESHOLD,
                            error.message.orEmpty(),
                        ),
                    )
                }

                if (hardTransportFailure) {
                    logger.error("Polling", "Transport closed; handing off to automatic reconnect", null)
                    transport.disconnect()
                    break
                }

                if (consecutiveTransportFailures >= SOFT_RECOVERY_THRESHOLD) {
                    logger.warning("Polling", "ELM327 response stream stalled; attempting soft resync")
                    val recovered = elm327.recoverLiveSession().isSuccess
                    consecutiveTransportFailures = 0
                    if (recovered) {
                        failedSoftRecoveries = 0
                        delay(POST_RECOVERY_SETTLE_MILLIS)
                    } else {
                        failedSoftRecoveries += 1
                        logger.warning("Polling", "Soft resync failed ($failedSoftRecoveries/$MAX_FAILED_SOFT_RECOVERIES)")
                    }
                }

                if (failedSoftRecoveries >= MAX_FAILED_SOFT_RECOVERIES) {
                    logger.error("Polling", "ELM327 stream could not be recovered; reconnecting transport", null)
                    transport.disconnect()
                    break
                }

                val elapsed = System.currentTimeMillis() - commandStartedAtMillis
                val pacing = commandPacingMillis()
                if (elapsed < pacing) delay(pacing - elapsed)

                if (successfulCommands > 0 && successfulCommands % HEALTH_LOG_EVERY_COMMANDS == 0L) {
                    val totalElapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                    val commandsPerSecond = successfulCommands * 1_000.0 / totalElapsed
                    logger.info(
                        "Polling",
                        "Live rate %.2f commands/s • performance=%s".format(
                            commandsPerSecond,
                            _performanceSampling.value,
                        ),
                    )
                }
            }
            logger.info("Polling", "Realtime polling stopped")
        }
    }

    /**
     * Prioritizes vehicle speed for Time Slip while still sampling RPM often enough for graphs.
     * The normal scheduler resumes immediately when disabled.
     */
    fun setPerformanceSampling(enabled: Boolean) {
        if (_performanceSampling.value == enabled) return
        _performanceSampling.value = enabled
        performanceIndex = 0
        logger.info("TimeSlip", if (enabled) "Dedicated speed sampling enabled" else "Normal PID scheduler restored")
    }

    private fun nextPerformanceSlot(): PollingSlot? {
        val supported = _supportedPids.value
        val sequence = buildList {
            if (VEHICLE_SPEED_PID in supported) {
                add(PollingSlot(VEHICLE_SPEED_PID, PollingGroup.FAST))
                add(PollingSlot(VEHICLE_SPEED_PID, PollingGroup.FAST))
            }
            if (ENGINE_RPM_PID in supported) add(PollingSlot(ENGINE_RPM_PID, PollingGroup.FAST))
            if (VEHICLE_SPEED_PID in supported) add(PollingSlot(VEHICLE_SPEED_PID, PollingGroup.FAST))
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
        refreshIntervalMillis = intervalMillis.coerceIn(200L, 1_500L)
    }

    suspend fun readDtcs() = elm327.readStoredDtcs()

    suspend fun clearDtcs(): Result<Unit> {
        pause()
        return elm327.clearStoredDtcs().onSuccess {
            logger.warning("DTC", "Mode 04 clear command acknowledged")
        }.also {
            if (connectionState.value == ConnectionState.CONNECTED) startPolling()
        }
    }

    fun diagnosticReport(): String = buildString {
        val init = initialization.value
        val readiness = currentSpeedReadiness()
        appendLine("NOWTUNEUP HARDWARE DIAGNOSTIC REPORT")
        appendLine("Generated: ${java.util.Date()}")
        appendLine("Connection: ${connectionState.value}")
        appendLine("ELM identity: ${init.adapterIdentity ?: "unknown"}")
        appendLine("Adapter initialized: ${init.adapterInitialized}")
        appendLine("ECU connected: ${init.ecuConnected}")
        appendLine("Supported Mode 01 PIDs: ${_supportedPids.value.sorted().joinToString { "0x%02X".format(it) }}")
        appendLine("Performance sampling: ${_performanceSampling.value}")
        appendLine("Speed supported: ${readiness.supported}")
        appendLine("Speed sample rate: ${"%.2f".format(readiness.sampleRateHz)} Hz")
        appendLine("Speed latency: ${_speedTelemetry.value?.transportLatencyMillis ?: -1L} ms")
        appendLine("Speed readiness: ${readiness.reasonThai}")
        appendLine()
        appendLine("DIAGNOSTIC LOG")
        append(logger.exportText())
    }

    /** MainViewModel may be recreated while a foreground service still owns the singleton session. */
    fun close() {
        pause()
    }

    fun shutdown() {
        pause()
        scope.cancel()
    }

    private fun commandPacingMillis(): Long = when {
        _performanceSampling.value -> 4L
        refreshIntervalMillis <= 250L -> 8L
        refreshIntervalMillis <= 600L -> 25L
        else -> 80L
    }

    private fun timeoutFor(group: PollingGroup): Long = when (group) {
        PollingGroup.FAST -> if (_performanceSampling.value) 1_000L else if (refreshIntervalMillis <= 250L) 1_100L else 1_300L
        PollingGroup.NORMAL -> 1_500L
        PollingGroup.SLOW -> 1_800L
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
        return error.toObdError() in setOf(ObdError.DeviceDisconnected, ObdError.BufferFull) ||
            message.contains("socket", ignoreCase = true) ||
            message.contains("closed", ignoreCase = true) ||
            message.contains("broken pipe", ignoreCase = true)
    }

    private fun publish(
        pid: Int,
        value: Double,
        commandSentAtNanos: Long,
        responseReceivedAtNanos: Long,
    ) {
        val now = System.currentTimeMillis()
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

        _readings.update { readings ->
            val updated = readings.map { reading ->
                if (reading.pid == pid) reading.copy(value = value, updatedAt = now) else reading
            }
            val turboSupported = updated.firstOrNull { it.pid == DerivedPids.MAP }?.supported == true &&
                updated.firstOrNull { it.pid == DerivedPids.BAROMETRIC_PRESSURE }?.supported == true
            updated.map { reading ->
                if (reading.pid == DerivedPids.TURBO_PRESSURE) {
                    reading.copy(
                        value = if (turboSupported) estimate.valueKpa else null,
                        supported = turboSupported,
                        updatedAt = estimate.updatedAtMillis,
                    )
                } else {
                    reading
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
        val stable = speedWindow.size
        val fresh = ageMillis <= SPEED_FRESH_MILLIS
        val ready = supported && latest != null && fresh && stable >= MIN_READY_SAMPLES && rate >= MIN_READY_RATE_HZ
        val reason = when {
            !supported -> "รถไม่รองรับ PID ความเร็ว 010D"
            latest == null -> "กำลังรอค่าความเร็วครั้งแรก"
            !fresh -> "ข้อมูลความเร็วล่าช้า กรุณารอการเชื่อมต่อให้เสถียร"
            stable < MIN_READY_SAMPLES -> "กำลังสะสมตัวอย่างความเร็ว $stable/$MIN_READY_SAMPLES"
            rate < MIN_READY_RATE_HZ -> "อัตราข้อมูลต่ำ ${"%.1f".format(rate)} Hz ต้องการอย่างน้อย ${"%.1f".format(MIN_READY_RATE_HZ)} Hz"
            else -> "พร้อมทดสอบ • ${"%.1f".format(rate)} Hz"
        }
        return SpeedPidReadiness(
            supported = supported,
            hasValue = latest != null,
            fresh = fresh,
            stableSamples = stable,
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
        private const val SOFT_RECOVERY_THRESHOLD = 3
        private const val MAX_FAILED_SOFT_RECOVERIES = 2
        private const val POST_RECOVERY_SETTLE_MILLIS = 120L
        private const val HEALTH_LOG_EVERY_COMMANDS = 40L
        private const val SPEED_WINDOW_SIZE = 10
        private const val MIN_READY_SAMPLES = 5
        private const val MIN_READY_RATE_HZ = 2.0
        private const val SPEED_FRESH_MILLIS = 1_000L

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
