package com.nowtuneup.app.data.obd.elm

import com.nowtuneup.app.data.logging.DiagnosticLogger
import com.nowtuneup.app.data.obd.command.ObdCommandQueue
import com.nowtuneup.app.data.obd.command.ObdRequest
import com.nowtuneup.app.data.obd.parser.DtcParser
import com.nowtuneup.app.data.obd.parser.ObdResponseParser
import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.domain.model.AdapterInitializationStatus
import com.nowtuneup.app.domain.model.Dtc
import com.nowtuneup.app.domain.model.ObdError
import com.nowtuneup.app.domain.model.VehicleReading
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class Elm327Client @Inject constructor(
    transport: ObdTransport,
    private val logger: DiagnosticLogger,
) {
    private val queue = ObdCommandQueue(transport)
    private val initializationMutex = Mutex()
    private val _initialization = MutableStateFlow(AdapterInitializationStatus())
    val initialization: StateFlow<AdapterInitializationStatus> = _initialization.asStateFlow()

    suspend fun initialize(resetDelayMillis: Long = DEFAULT_RESET_DELAY_MILLIS): Result<Elm327InitializationResult> =
        initializationMutex.withLock {
            runCatching {
                val sequence = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATI", "0100")
                _initialization.value = AdapterInitializationStatus(
                    totalSteps = sequence.size,
                    bluetoothConnected = true,
                )
                var identity: String? = null
                sequence.forEachIndexed { index, command ->
                    _initialization.value = _initialization.value.copy(
                        currentCommand = command,
                        completedSteps = index,
                    )
                    val timeout = when (command) {
                        "ATZ" -> 5_000L
                        "ATSP0", "0100" -> 8_000L
                        else -> 3_000L
                    }
                    val startedAt = System.currentTimeMillis()
                    val response = executeCommand(command, timeout, retryLimit = if (command == "0100") 1 else 0).getOrThrow()
                    logger.info("ELM327", "$command completed in ${System.currentTimeMillis() - startedAt} ms")
                    if (command == "ATZ") delay(resetDelayMillis.coerceIn(500L, 3_000L))
                    if (command == "ATI") {
                        identity = response.normalizedLines
                            .firstOrNull { it.uppercase() != "OK" && !it.uppercase().startsWith("SEARCHING") }
                            ?.take(120)
                        check(!identity.isNullOrBlank()) { "ELM327 identity response was empty" }
                        _initialization.value = _initialization.value.copy(
                            adapterIdentity = identity,
                            adapterInitialized = true,
                        )
                    }
                    if (command == "0100") {
                        val normalized = ObdResponseParser.normalize(response.raw, command).getOrThrow()
                        check(normalized.frames.any { it.size >= 6 && it[0] == 0x41 && it[1] == 0x00 }) {
                            "ECU did not return a valid 4100 supported-PID frame"
                        }
                        _initialization.value = _initialization.value.copy(ecuConnected = true)
                    }
                    _initialization.value = _initialization.value.copy(completedSteps = index + 1)
                }
                _initialization.value = _initialization.value.copy(currentCommand = null)
                Elm327InitializationResult(
                    adapterIdentity = identity.orEmpty(),
                    ecuConnected = true,
                )
            }.onFailure { error ->
                logger.error("ELM327", "Initialization failed at ${_initialization.value.currentCommand}", error)
                _initialization.value = _initialization.value.copy(
                    currentCommand = null,
                    ecuConnected = false,
                )
            }
        }

    suspend fun executeCommand(
        command: String,
        timeoutMillis: Long = 2_000L,
        retryLimit: Int = 1,
    ): Result<Elm327TextResponse> {
        val normalizedCommand = command.trim().uppercase()
        val startedAt = System.currentTimeMillis()
        return queue.execute(ObdRequest(normalizedCommand, timeoutMillis, retryLimit))
            .mapCatching { raw ->
                val interpreted = Elm327ResponseInterpreter.requireSuccess(raw, normalizedCommand).getOrThrow()
                logger.debug("ELM327", "$normalizedCommand duration=${System.currentTimeMillis() - startedAt}ms")
                interpreted
            }
    }

    suspend fun requestPid(
        pid: Int,
        timeoutMillis: Long = LIVE_PID_TIMEOUT_MILLIS,
        retryLimit: Int = 0,
    ): Result<VehicleReading> {
        val command = "01%02X".format(pid)
        return executeCommand(command, timeoutMillis, retryLimit).mapCatching { response ->
            val value = ObdResponseParser.parseMode1(response.raw, pid, command).getOrThrow()
            val definition = com.nowtuneup.app.data.obd.pid.StandardPids.find(pid)
                ?: error("Unsupported PID 0x%02X".format(pid))
            logger.debug("PID", "01%02X decoded %.3f %s".format(pid, value, definition.unit))
            VehicleReading(
                pid = pid,
                name = definition.name,
                value = value,
                unit = definition.unit,
                supported = true,
                minimum = definition.minimum,
                maximum = definition.maximum,
            )
        }
    }

    suspend fun readStoredDtcs(): Result<List<Dtc>> =
        executeCommand("03", timeoutMillis = 5_000L, retryLimit = 0).map { DtcParser.parse(it.raw) }

    suspend fun clearStoredDtcs(): Result<Unit> =
        executeCommand("04", timeoutMillis = 5_000L, retryLimit = 0).mapCatching { response ->
            val compact = response.body.filterNot(Char::isWhitespace).uppercase()
            check(compact.contains("44") || response.normalizedLines.any { it.equals("OK", true) }) {
                "ECU did not acknowledge Mode 04"
            }
        }

    fun resetInitializationState() {
        _initialization.value = AdapterInitializationStatus()
    }

    companion object {
        private const val DEFAULT_RESET_DELAY_MILLIS = 1_000L
        private const val LIVE_PID_TIMEOUT_MILLIS = 1_500L
    }
}

data class Elm327InitializationResult(
    val adapterIdentity: String,
    val ecuConnected: Boolean,
)

fun Throwable.toObdError(): ObdError = when {
    this is Elm327ProtocolException -> obdError
    message?.contains("identity", ignoreCase = true) == true -> ObdError.UnsupportedAdapter
    message?.contains("4100", ignoreCase = true) == true -> ObdError.EcuNotResponding
    message?.contains("timeout", ignoreCase = true) == true -> ObdError.Timeout
    else -> ObdError.Unknown(message ?: this::class.java.simpleName)
}
