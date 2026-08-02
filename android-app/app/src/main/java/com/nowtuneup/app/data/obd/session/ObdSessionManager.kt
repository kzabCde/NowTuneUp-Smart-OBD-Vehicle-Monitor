package com.nowtuneup.app.data.obd.session

import com.nowtuneup.app.data.logging.DiagnosticLogger
import com.nowtuneup.app.data.obd.elm.Elm327Client
import com.nowtuneup.app.data.obd.elm.toObdError
import com.nowtuneup.app.data.obd.parser.ObdResponseParser
import com.nowtuneup.app.data.obd.pid.DerivedPids
import com.nowtuneup.app.data.obd.pid.StandardPids
import com.nowtuneup.app.data.obd.pid.SupportedPidParser
import com.nowtuneup.app.data.obd.polling.PidPollingScheduler
import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.ObdError
import com.nowtuneup.app.domain.model.VehicleReading
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
    private var polling: Job? = null

    @Volatile
    private var refreshIntervalMillis: Long = 500

    suspend fun connect(): Result<Unit> {
        pause()
        elm327.resetInitializationState()
        transport.connect().onFailure { return Result.failure(it) }
        return elm327.initialize().mapCatching { result ->
            check(result.ecuConnected) { "ECU communication was not confirmed" }
            val discovered = discoverSupportedPids()
            check(discovered.isNotEmpty()) { "Vehicle reported no supported Mode 01 PIDs" }
            _supportedPids.value = discovered
            updateSupportFlags(discovered)
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
        _supportedPids.value = emptySet()
        elm327.resetInitializationState()
        transport.disconnect()
        _readings.update { readings -> readings.map { it.copy(value = null) } }
    }

    fun startPolling() {
        if (polling?.isActive == true || connectionState.value != ConnectionState.CONNECTED) return
        val scheduler = PidPollingScheduler(_supportedPids.value)
        if (scheduler.isEmpty()) return
        polling = scope.launch {
            var consecutiveTransportFailures = 0
            var successfulCommands = 0L
            val startedAt = System.currentTimeMillis()
            logger.info(
                "Polling",
                "Realtime polling started with ${scheduler.snapshot().size} interleaved slots",
            )

            while (isActive && connectionState.value == ConnectionState.CONNECTED) {
                val slot = scheduler.next()
                if (slot == null) {
                    delay(250L)
                    continue
                }

                val commandStartedAt = System.currentTimeMillis()
                elm327.requestPid(slot.pid).onSuccess { reading ->
                    consecutiveTransportFailures = 0
                    successfulCommands += 1
                    publish(reading.pid, reading.value ?: return@onSuccess)
                }.onFailure { error ->
                    if (isTransportHealthFailure(error)) {
                        consecutiveTransportFailures += 1
                    } else {
                        // NO DATA or an individual malformed PID still proves that the adapter link is alive.
                        consecutiveTransportFailures = 0
                    }
                    logger.warning(
                        "Polling",
                        "PID 01%02X failed (%d/%d transport failures): %s".format(
                            slot.pid,
                            consecutiveTransportFailures,
                            MAX_CONSECUTIVE_TRANSPORT_FAILURES,
                            error.message.orEmpty(),
                        ),
                    )

                    if (consecutiveTransportFailures >= MAX_CONSECUTIVE_TRANSPORT_FAILURES) {
                        logger.error(
                            "Polling",
                            "ELM327 transport is unhealthy after $consecutiveTransportFailures consecutive failures; reconnecting",
                            error,
                        )
                    }
                }

                if (consecutiveTransportFailures >= MAX_CONSECUTIVE_TRANSPORT_FAILURES) {
                    transport.disconnect()
                    break
                }

                val elapsed = System.currentTimeMillis() - commandStartedAt
                val pacing = commandPacingMillis()
                if (elapsed < pacing) delay(pacing - elapsed)

                if (successfulCommands > 0 && successfulCommands % HEALTH_LOG_EVERY_COMMANDS == 0L) {
                    val totalElapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                    val commandsPerSecond = successfulCommands * 1_000.0 / totalElapsed
                    logger.info("Polling", "Live rate %.2f successful commands/s".format(commandsPerSecond))
                }
            }
            logger.info("Polling", "Realtime polling stopped")
        }
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

    fun close() {
        pause()
        scope.cancel()
    }

    private fun commandPacingMillis(): Long = when {
        refreshIntervalMillis <= 250L -> 15L
        refreshIntervalMillis <= 600L -> 50L
        else -> 150L
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

    private fun publish(pid: Int, value: Double) {
        val now = System.currentTimeMillis()
        _readings.update { readings ->
            val updated = readings.map { reading ->
                if (reading.pid == pid) reading.copy(value = value, updatedAt = now) else reading
            }
            val map = updated.firstOrNull { it.pid == DerivedPids.MAP }
            val barometric = updated.firstOrNull { it.pid == DerivedPids.BAROMETRIC_PRESSURE }
            val supported = map?.supported == true && barometric?.supported == true
            val turboValue = if (supported && map?.value != null && barometric?.value != null) {
                DerivedPids.turboPressureKpa(map.value, barometric.value)
            } else {
                null
            }
            val derivedTimestamp = if (turboValue != null) minOf(map!!.updatedAt, barometric!!.updatedAt) else now
            updated.map { reading ->
                if (reading.pid == DerivedPids.TURBO_PRESSURE) {
                    reading.copy(value = turboValue, supported = supported, updatedAt = derivedTimestamp)
                } else {
                    reading
                }
            }
        }
    }

    companion object {
        private const val MAX_CONSECUTIVE_TRANSPORT_FAILURES = 4
        private const val HEALTH_LOG_EVERY_COMMANDS = 50L

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
