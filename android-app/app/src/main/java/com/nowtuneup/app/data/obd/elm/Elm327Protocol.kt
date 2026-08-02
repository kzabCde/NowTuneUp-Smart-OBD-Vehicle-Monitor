package com.nowtuneup.app.data.obd.elm

import com.nowtuneup.app.domain.model.ObdError

sealed interface Elm327ResponseStatus {
    data object Success : Elm327ResponseStatus
    data object NoData : Elm327ResponseStatus
    data object Searching : Elm327ResponseStatus
    data class Failure(val error: ObdError, val token: String) : Elm327ResponseStatus
}

data class Elm327TextResponse(
    val raw: String,
    val normalizedLines: List<String>,
    val body: String,
    val status: Elm327ResponseStatus,
)

object Elm327ResponseInterpreter {
    private val fatalTokens = linkedMapOf(
        "UNABLE TO CONNECT" to ObdError.EcuNotResponding,
        "BUS INIT: ERROR" to ObdError.EcuNotResponding,
        "BUS INIT ERROR" to ObdError.EcuNotResponding,
        "CAN ERROR" to ObdError.EcuNotResponding,
        "BUFFER FULL" to ObdError.BufferFull,
        "STOPPED" to ObdError.DeviceDisconnected,
        "?" to ObdError.UnsupportedAdapter,
    )

    fun interpret(raw: String, command: String? = null): Elm327TextResponse {
        val expectedEcho = command?.trim()?.uppercase()
        val lines = raw
            .replace('>', '\n')
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { expectedEcho != null && compact(it) == compact(expectedEcho) }
            .toList()

        val upper = lines.map { it.uppercase() }
        val fatal = fatalTokens.entries.firstOrNull { (token, _) ->
            upper.any { line -> line == token || line.contains(token) }
        }
        val status = when {
            fatal != null -> Elm327ResponseStatus.Failure(fatal.value, fatal.key)
            upper.any { it == "NO DATA" } -> Elm327ResponseStatus.NoData
            upper.any { it.startsWith("SEARCHING") } -> Elm327ResponseStatus.Searching
            else -> Elm327ResponseStatus.Success
        }
        return Elm327TextResponse(raw, lines, lines.joinToString("\n"), status)
    }

    fun requireSuccess(raw: String, command: String? = null): Result<Elm327TextResponse> = runCatching {
        val response = interpret(raw, command)
        when (val status = response.status) {
            Elm327ResponseStatus.Success,
            Elm327ResponseStatus.Searching,
            -> response
            Elm327ResponseStatus.NoData -> throw Elm327ProtocolException(ObdError.NoData, "NO DATA")
            is Elm327ResponseStatus.Failure -> throw Elm327ProtocolException(status.error, status.token)
        }
    }

    private fun compact(value: String): String = value.filterNot(Char::isWhitespace).uppercase()
}

class Elm327ProtocolException(
    val obdError: ObdError,
    message: String,
) : IllegalStateException(message)
