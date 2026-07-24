package com.nowtuneup.app.data.obd.session

import com.nowtuneup.app.data.obd.command.ObdCommandQueue
import com.nowtuneup.app.data.obd.command.ObdRequest
import com.nowtuneup.app.data.obd.parser.DtcParser
import com.nowtuneup.app.data.obd.parser.ObdResponseParser
import com.nowtuneup.app.data.obd.pid.StandardPids
import com.nowtuneup.app.data.obd.pid.SupportedPidParser
import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.VehicleReading
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
import javax.inject.Inject

class ObdSessionManager @Inject constructor(private val transport: ObdTransport) {
    val connectionState = transport.connectionState
    private val queue = ObdCommandQueue(transport)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _readings = MutableStateFlow(defaultReadings())
    val readings: StateFlow<List<VehicleReading>> = _readings
    private var supportedPids: Set<Int> = emptySet()
    private var polling: Job? = null

    suspend fun connect(): Result<Unit> {
        transport.connect().onFailure { return Result.failure(it) }
        val initialization = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")
        for (command in initialization) {
            val raw = queue.execute(ObdRequest(command, timeoutMillis = 3_000, retryLimit = 0))
                .getOrElse { disconnect(); return Result.failure(IllegalStateException("Initialization failed at $command", it)) }
            val upper = raw.uppercase()
            if (listOf("ERROR", "?", "UNABLE TO CONNECT", "CAN ERROR", "BUS INIT ERROR", "STOPPED").any(upper::contains)) {
                disconnect()
                return Result.failure(IllegalStateException("Initialization failed at $command"))
            }
        }
        supportedPids = discoverSupportedPids()
        _readings.update { values -> values.map { it.copy(supported = it.pid in supportedPids, value = null) } }
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
        val schedule = listOf(0x0C, 0x0D, 0x0C, 0x0D, 0x04, 0x11, 0x05, 0x42)
        polling = scope.launch {
            var tick = 0
            while (isActive && connectionState.value == ConnectionState.CONNECTED) {
                val supportedSchedule = schedule.filter { it in supportedPids }
                if (supportedSchedule.isEmpty()) { delay(1_000); continue }
                val pid = supportedSchedule[tick % supportedSchedule.size]
                val command = "01%02X".format(pid)
                queue.execute(ObdRequest(command)).onSuccess { raw ->
                    ObdResponseParser.parseMode1(raw, pid, command).onSuccess { publish(pid, it) }
                }
                tick++
                delay(if (pid == 0x0C || pid == 0x0D) 250 else 700)
            }
        }
    }

    fun pause() { polling?.cancel(); polling = null }
    suspend fun readDtcs() = queue.execute(ObdRequest("03", 4_000)).map(DtcParser::parse)
    fun close() = scope.cancel()

    private fun publish(pid: Int, value: Double) {
        _readings.update { readings -> readings.map { if (it.pid == pid) it.copy(value = value, updatedAt = System.currentTimeMillis()) else it } }
    }

    companion object {
        fun defaultReadings() = StandardPids.all.map {
            VehicleReading(it.pid, it.name, null, it.unit, supported = false, minimum = it.minimum, maximum = it.maximum)
        }
    }
}
