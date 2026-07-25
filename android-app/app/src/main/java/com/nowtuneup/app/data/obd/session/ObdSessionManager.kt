package com.nowtuneup.app.data.obd.session

import com.nowtuneup.app.data.obd.command.ObdCommandQueue
import com.nowtuneup.app.data.obd.command.ObdRequest
import com.nowtuneup.app.data.obd.parser.DtcParser
import com.nowtuneup.app.data.obd.parser.ObdResponseParser
import com.nowtuneup.app.data.obd.pid.DerivedPids
import com.nowtuneup.app.data.obd.pid.StandardPids
import com.nowtuneup.app.data.obd.pid.SupportedPidParser
import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.VehicleReading
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ObdSessionManager @Inject constructor(private val transport: ObdTransport) {
    val connectionState = transport.connectionState
    private val queue = ObdCommandQueue(transport)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _readings = MutableStateFlow(defaultReadings())
    val readings: StateFlow<List<VehicleReading>> = _readings
    private var supportedPids: Set<Int> = emptySet()
    private var polling: Job? = null

    @Volatile
    private var refreshIntervalMillis: Long = 500

    suspend fun connect(): Result<Unit> {
        transport.connect().onFailure { return Result.failure(it) }
        val initialization = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")
        for (command in initialization) {
            val raw = queue.execute(ObdRequest(command, timeoutMillis = 3_000, retryLimit = 0))
                .getOrElse {
                    disconnect()
                    return Result.failure(IllegalStateException("Initialization failed at $command", it))
                }
            val upper = raw.uppercase()
            if (listOf("ERROR", "?", "UNABLE TO CONNECT", "CAN ERROR", "BUS INIT ERROR", "STOPPED").any(upper::contains)) {
                disconnect()
                return Result.failure(IllegalStateException("Initialization failed at $command"))
            }
        }
        supportedPids = discoverSupportedPids()
        val turboSupported = DerivedPids.MAP in supportedPids && DerivedPids.BAROMETRIC_PRESSURE in supportedPids
        _readings.update { values ->
            values.map { reading ->
                reading.copy(
                    supported = if (reading.pid == DerivedPids.TURBO_PRESSURE) turboSupported else reading.pid in supportedPids,
                    value = null,
                )
            }
        }
        startPolling()
        return Result.success(Unit)
    }

    private suspend fun discoverSupportedPids(): Set<Int> {
        val discovered = mutableSetOf<Int>()
        for (base in listOf(0x00, 0x20, 0x40, 0x60)) {
            val command = "01%02X".format(base)
            val response = queue.execute(ObdRequest(command, retryLimit = 0)).getOrNull() ?: break
            val frame = ObdResponseParser.normalize(response, command).getOrNull()?.frames
                ?.firstOrNull { it.size >= 6 && it[0] == 0x41 && it[1] == base } ?: break
            discovered += SupportedPidParser.parse(base, frame.drop(2).take(4))
            if (base + 0x20 !in discovered) break
        }
        return discovered
    }

    suspend fun disconnect() {
        polling?.cancel()
        polling = null
        transport.disconnect()
    }

    fun startPolling() {
        if (polling?.isActive == true || connectionState.value != ConnectionState.CONNECTED) return
        val schedule = listOf(
            0x0C,
            0x0D,
            DerivedPids.MAP,
            0x0C,
            0x0D,
            DerivedPids.MAP,
            0x04,
            0x11,
            0x05,
            0x42,
            DerivedPids.BAROMETRIC_PRESSURE,
        )
        polling = scope.launch {
            var tick = 0
            while (isActive && connectionState.value == ConnectionState.CONNECTED) {
                val supportedSchedule = schedule.filter { it in supportedPids }
                if (supportedSchedule.isEmpty()) {
                    delay(1_000)
                    continue
                }
                val pid = supportedSchedule[tick % supportedSchedule.size]
                val command = "01%02X".format(pid)
                queue.execute(ObdRequest(command)).onSuccess { raw ->
                    ObdResponseParser.parseMode1(raw, pid, command).onSuccess { publish(pid, it) }
                }
                tick++
                val fastPid = pid == 0x0C || pid == 0x0D || pid == DerivedPids.MAP
                delay(if (fastPid) refreshIntervalMillis else maxOf(refreshIntervalMillis, 700))
            }
        }
    }

    fun pause() {
        polling?.cancel()
        polling = null
    }

    fun setRefreshInterval(intervalMillis: Long) {
        refreshIntervalMillis = intervalMillis.coerceIn(200, 1_000)
    }

    suspend fun readDtcs() = queue.execute(ObdRequest("03", 4_000)).map(DtcParser::parse)

    fun close() = scope.cancel()

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
